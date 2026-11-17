package eu.frigo.dispensa.sync.core.engine;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.frigo.dispensa.sync.core.event.SyncBus;
import eu.frigo.dispensa.sync.core.event.SyncEvent;
import eu.frigo.dispensa.sync.core.model.PantryDevice;
import eu.frigo.dispensa.sync.core.model.PantryEvent;
import eu.frigo.dispensa.sync.core.model.PantryManifest;
import eu.frigo.dispensa.sync.core.policy.SyncPolicy;
import eu.frigo.dispensa.sync.core.store.SharedFolderStore;
import eu.frigo.dispensa.sync.core.store.SyncCursorStore;
import io.reactivex.rxjava3.core.Completable;

/**
 * Universal sync engine operating over any SharedFolderStore implementation.
 */
public class FolderSyncEngine implements SyncEngine {
    private static final String TAG = "FolderSyncEngine";

    private final SharedFolderStore store;
    private final PantryDataBridge dataBridge;
    private final SyncCursorStore cursorStore;
    private final String deviceId;
    private final String deviceName;
    private final String pantryPath;
    private final int dispensaId;
    private final Gson gson;

    public FolderSyncEngine(
            SharedFolderStore store,
            PantryDataBridge dataBridge,
            SyncCursorStore cursorStore,
            String deviceId,
            String deviceName,
            String pantryPath,
            int dispensaId
    ) {
        this.store = store;
        this.dataBridge = dataBridge;
        this.cursorStore = cursorStore;
        this.deviceId = deviceId;
        this.deviceName = (deviceName != null && !deviceName.trim().isEmpty()) ? deviceName : "Android Device";
        this.pantryPath = (pantryPath == null || pantryPath.isEmpty()) ? "" : (pantryPath.endsWith("/") ? pantryPath : pantryPath + "/");
        this.dispensaId = dispensaId;
        this.gson = new Gson();
    }

    public SharedFolderStore getStore() {
        return store;
    }

    public String getPantryPath() {
        return pantryPath;
    }

    public int getDispensaId() {
        return dispensaId;
    }

    public Completable sync() {
        return performSync(new SyncPolicy() {
            @Override public boolean canSyncNow() { return true; }
            @Override public long getRetryIntervalMillis() { return 0; }
        });
    }

    @Override
    public Completable performSync(SyncPolicy policy) {
        return Completable.fromAction(() -> {
            if (!policy.canSyncNow()) return;

            Boolean isAvailable = store.isAvailable().blockingGet();
            if (isAvailable == null || !isAvailable) {
                Log.w(TAG, "Storage store is currently not available: " + store.getProviderId());
                return;
            }

            Log.d(TAG, "--- Inizio sessione sincronizzazione (" + store.getProviderId() + ") path=" + pantryPath + " ---");

            // 0. Verify remote folders & manifest exist (fail if missing or deleted; throws IOException on network error)
            verifyRemoteStructureExists();

            // 0b. Version and migration check (Big Bang V3 owner-only migration)
            if (!checkVersionAndMigrate()) {
                Log.w(TAG, "Sincronizzazione interrotta per incompatibilità di versione.");
                return;
            }

            // 1. Pull
            PantryManifest manifest = fetchManifest();
            if (manifest != null) {
                processRemoteChanges(manifest);
            }

            // 2. Push local events
            pushLocalChanges();

            // 2b. Update device registration
            updateDeviceRegistration();

            // 3. Reload manifest post-push (throws IOException on transient failure instead of resetting manifest)
            manifest = fetchManifest();
            if (manifest == null) {
                throw new IOException("Manifest unexpectedly missing after push on " + pantryPath);
            }

            // 4. Snapshot / Compaction
            boolean snapshotExists = false;
            if (manifest.latestSnapshotId != null) {
                Boolean exists = store.exists(pantryPath + SyncManager.DEFAULT_SNAPSHOTS_FOLDER + manifest.latestSnapshotId).blockingGet();
                snapshotExists = (exists != null && exists);
            }

            if (manifest.latestSnapshotId == null || !snapshotExists) {
                Log.d(TAG, "Nessuno snapshot valido sul server. Creazione snapshot...");
                performCompaction();
            } else if (manifest.activeEventFiles != null && manifest.activeEventFiles.size() >= 50) {
                Log.d(TAG, "Raggiunta soglia eventi (" + manifest.activeEventFiles.size() + "). Compattazione in corso...");
                performCompaction();
            }

            Log.d(TAG, "--- Sessione sincronizzazione completata ---");
        });
    }

    private boolean checkVersionAndMigrate() throws Exception {
        PantryManifest currentManifest = fetchManifest();
        if (currentManifest != null) {
            if (currentManifest.version < SyncManager.CURRENT_SYNC_VERSION) {
                boolean isOwner = (currentManifest.createdByDevice != null && deviceId.equals(currentManifest.createdByDevice.trim()))
                        || ((currentManifest.createdByDevice == null || currentManifest.createdByDevice.trim().isEmpty())
                        && dataBridge.isPantryOwner(dispensaId, deviceId));
                if (isOwner) {
                    Log.i(TAG, "Migrazione Big Bang manifest da V" + currentManifest.version + " a V" + SyncManager.CURRENT_SYNC_VERSION + " da parte dell'owner");
                    // 1. Pull any pending V2 changes before wiping legacy files
                    processRemoteChanges(currentManifest);
                    // 2. Push any pending local changes
                    pushLocalChanges();
                    // 3. Wipe legacy events and snapshots ("cancella il pregresso")
                    wipeRemoteFolderContents(pantryPath + SyncManager.DEFAULT_EVENTS_FOLDER);
                    wipeRemoteFolderContents(pantryPath + SyncManager.DEFAULT_SNAPSHOTS_FOLDER);
                    // 4. Create fresh V3 snapshot and update manifest to V3
                    long now = System.currentTimeMillis();
                    String snapshotJson = dataBridge.createSnapshotJson(dispensaId);
                    String snapshotName = "snap_" + now + ".json";
                    String fullSnapPath = pantryPath + SyncManager.DEFAULT_SNAPSHOTS_FOLDER + snapshotName;
                    store.writeAtomic(fullSnapPath, snapshotJson.getBytes(StandardCharsets.UTF_8), null).blockingAwait();

                    updateManifest(m -> {
                        m.version = SyncManager.CURRENT_SYNC_VERSION;
                        m.latestSnapshotId = snapshotName;
                        if (m.activeEventFiles != null) {
                            m.activeEventFiles.clear();
                        }
                        m.lastGlobalTimestamp = Math.max(m.lastGlobalTimestamp, now);
                    });

                    cursorStore.retainAppliedEventFiles(Collections.emptyList(), dispensaId);
                    cursorStore.updateLastAppliedSnapshotId(snapshotName, dispensaId);
                    cursorStore.updateLastSyncTimestamp(now, dispensaId);
                } else {
                    SyncBus.getInstance().post(new SyncEvent.VersionMismatch(SyncManager.CURRENT_SYNC_VERSION, currentManifest.version, false));
                    return false;
                }
            } else if (currentManifest.version > SyncManager.CURRENT_SYNC_VERSION) {
                Log.e(TAG, "Versione remota superiore alla locale (" + currentManifest.version + " > " + SyncManager.CURRENT_SYNC_VERSION + ")");
                SyncBus.getInstance().post(new SyncEvent.VersionMismatch(SyncManager.CURRENT_SYNC_VERSION, currentManifest.version, false));
                return false;
            }
        }
        return true;
    }

    private void wipeRemoteFolderContents(String folderPath) {
        try {
            List<String> files = store.listFiles(folderPath).blockingGet();
            if (files != null) {
                String normalizedFolder = folderPath.endsWith("/") ? folderPath : folderPath + "/";
                for (String file : files) {
                    if (file != null && !file.isEmpty()) {
                        store.deleteFile(normalizedFolder + file).blockingAwait();
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Impossibile pulire cartella remota: " + folderPath, e);
        }
    }

    /**
     * Fetches and parses the remote manifest.
     * Returns null ONLY if manifest.json genuinely does not exist (404).
     * Throws IOException on any network error, HTTP 5xx/4xx error, empty body, or corrupted JSON.
     */
    public PantryManifest fetchManifest() throws IOException {
        String manifestPath = pantryPath + SyncManager.MANIFEST_JSON;
        Boolean exists;
        try {
            exists = store.exists(manifestPath).blockingGet();
        } catch (RuntimeException e) {
            throw unwrapIOException("Error checking manifest existence: " + manifestPath, e);
        }
        if (exists == null || !exists) {
            return null;
        }

        SharedFolderStore.FileContent meta;
        try {
            meta = store.readWithMeta(manifestPath).blockingGet();
        } catch (RuntimeException e) {
            if (isFileNotFound(e)) {
                return null;
            }
            throw unwrapIOException("Error reading manifest: " + manifestPath, e);
        }

        if (meta == null || meta.data == null || meta.data.length == 0) {
            throw new IOException("Manifest is empty or truncated at " + manifestPath);
        }

        try {
            String json = new String(meta.data, StandardCharsets.UTF_8);
            PantryManifest manifest = gson.fromJson(json, PantryManifest.class);
            if (manifest == null) {
                throw new IOException("Parsed manifest is null at " + manifestPath);
            }
            manifest.etag = meta.etag;
            return manifest;
        } catch (JsonSyntaxException e) {
            throw new IOException("Corrupted manifest JSON at " + manifestPath, e);
        }
    }

    private void updateManifest(java.util.function.Consumer<PantryManifest> updater) throws Exception {
        int retries = 5;
        while (retries > 0) {
            try {
                PantryManifest manifest = fetchManifest();
                String expectedEtag = null;
                if (manifest == null) {
                    manifest = new PantryManifest();
                    manifest.createdByDevice = deviceId;
                    manifest.createdAt = System.currentTimeMillis();
                    manifest.version = SyncManager.CURRENT_SYNC_VERSION;
                    manifest.provider = store.getProviderId();
                    manifest.pantryName = dataBridge.getPantryName(dispensaId);
                } else {
                    expectedEtag = manifest.etag;
                }

                updater.accept(manifest);
                String json = gson.toJson(manifest);
                byte[] data = json.getBytes(StandardCharsets.UTF_8);

                store.writeAtomic(pantryPath + SyncManager.MANIFEST_JSON, data, expectedEtag).blockingAwait();
                Log.d(TAG, "Manifest salvato con successo.");
                return;
            } catch (Exception e) {
                retries--;
                if (retries == 0) {
                    if (e instanceof RuntimeException && e.getCause() instanceof Exception) {
                        throw (Exception) e.getCause();
                    }
                    throw e;
                }
                Log.w(TAG, "Riprovo salvataggio manifest (tentativi rimasti: " + retries + ")", e);
            }
        }
    }

    private void processRemoteChanges(PantryManifest manifest) throws Exception {
        long lastSync = cursorStore.getLastSyncTimestamp(dispensaId);
        String lastAppliedSnapshot = cursorStore.getLastAppliedSnapshotId(dispensaId);

        if (manifest.latestSnapshotId != null
                && (lastSync == 0 || lastAppliedSnapshot == null || !lastAppliedSnapshot.equals(manifest.latestSnapshotId))) {
            downloadAndApplySnapshot(manifest.latestSnapshotId);
            cursorStore.updateLastAppliedSnapshotId(manifest.latestSnapshotId, dispensaId);
        }

        Set<String> appliedEvents = cursorStore.getAppliedEventFiles(dispensaId);
        if (manifest.activeEventFiles != null) {
            for (String eventFile : manifest.activeEventFiles) {
                if (eventFile == null || eventFile.isEmpty()) continue;
                if (!appliedEvents.contains(eventFile)) {
                    downloadAndApplyEvent(eventFile);
                    cursorStore.markEventFileApplied(eventFile, dispensaId);
                }
            }
        }

        cursorStore.retainAppliedEventFiles(manifest.activeEventFiles, dispensaId);
        cursorStore.updateLastSyncTimestamp(Math.max(lastSync, manifest.lastGlobalTimestamp), dispensaId);
    }

    private void updateDeviceRegistration() {
        try {
            PantryDevice device = new PantryDevice(deviceId, deviceName);
            String devicePath = pantryPath + SyncManager.DEFAULT_DEVICES_FOLDER + deviceId + ".json";
            byte[] data = gson.toJson(device).getBytes(StandardCharsets.UTF_8);
            store.writeAtomic(devicePath, data, null).blockingAwait();
            Log.d(TAG, "Device registration aggiornata: " + deviceName);
        } catch (Exception e) {
            Log.e(TAG, "Errore aggiornamento registrazione dispositivo", e);
        }
    }

    private void pushLocalChanges() throws Exception {
        List<PantryOutboxItem> pending = dataBridge.getPendingOutboxEvents(dispensaId);
        if (pending == null || pending.isEmpty()) return;

        Log.d(TAG, "Push di " + pending.size() + " eventi locali...");

        List<String> uploadedFiles = new ArrayList<>();
        List<String> syncedIds = new ArrayList<>();
        long maxTs = 0;

        for (PantryOutboxItem item : pending) {
            PantryEvent event = new PantryEvent();
            event.eventId = item.syncId;
            event.deviceId = deviceId;
            event.timestamp = item.timestamp;
            event.action = item.dataType;
            event.payload = gson.fromJson(item.payloadJson, Map.class);

            String safeSyncId = (item.syncId != null && !item.syncId.trim().isEmpty())
                    ? item.syncId.trim().replaceAll("[^a-zA-Z0-9_-]", "")
                    : UUID.randomUUID().toString();
            String fileName = SyncManager.DEFAULT_EVENTS_FOLDER + "ev_" + deviceId + "_" + event.timestamp + "_" + safeSyncId + ".json";
            String fullFilePath = pantryPath + fileName;
            byte[] eventData = gson.toJson(event).getBytes(StandardCharsets.UTF_8);

            store.writeAtomic(fullFilePath, eventData, null).blockingAwait();
            uploadedFiles.add(fileName);
            syncedIds.add(item.syncId);
            maxTs = Math.max(maxTs, event.timestamp);
        }

        long finalMaxTs = maxTs;
        updateManifest(m -> {
            if (m.activeEventFiles == null) m.activeEventFiles = new ArrayList<>();
            for (String file : uploadedFiles) {
                if (!m.activeEventFiles.contains(file)) {
                    m.activeEventFiles.add(file);
                }
            }
            m.lastGlobalTimestamp = Math.max(m.lastGlobalTimestamp, finalMaxTs);
        });

        for (String file : uploadedFiles) {
            cursorStore.markEventFileApplied(file, dispensaId);
        }
        dataBridge.markEventsAsSynced(syncedIds);
    }

    public void forceCompaction() throws Exception {
        performCompaction();
    }

    private void performCompaction() throws Exception {
        Log.d(TAG, "Esecuzione compattazione...");

        long now = System.currentTimeMillis();
        String snapshotJson = dataBridge.createSnapshotJson(dispensaId);
        String snapshotName = "snap_" + now + ".json";
        String fullPath = pantryPath + SyncManager.DEFAULT_SNAPSHOTS_FOLDER + snapshotName;

        byte[] snapData = snapshotJson.getBytes(StandardCharsets.UTF_8);
        store.writeAtomic(fullPath, snapData, null).blockingAwait();

        Set<String> compactedEvents = new HashSet<>();
        List<String> oldSnapshots = new ArrayList<>();

        updateManifest(m -> {
            if (m.latestSnapshotId != null && !m.latestSnapshotId.equals(snapshotName)) {
                oldSnapshots.add(m.latestSnapshotId);
            }
            if (m.activeEventFiles != null) {
                compactedEvents.addAll(m.activeEventFiles);
                m.activeEventFiles.clear();
            }
            m.latestSnapshotId = snapshotName;
            m.lastGlobalTimestamp = Math.max(m.lastGlobalTimestamp, now);
        });

        cursorStore.updateLastAppliedSnapshotId(snapshotName, dispensaId);
        cursorStore.retainAppliedEventFiles(Collections.emptyList(), dispensaId);
        cursorStore.updateLastSyncTimestamp(now, dispensaId);

        // Delete compacted event files and superseded snapshots from server
        for (String evFile : compactedEvents) {
            try {
                store.deleteFile(pantryPath + evFile).blockingAwait();
            } catch (Exception e) {
                Log.w(TAG, "Impossibile eliminare evento compattato: " + evFile, e);
            }
        }
        for (String oldSnap : oldSnapshots) {
            try {
                store.deleteFile(pantryPath + SyncManager.DEFAULT_SNAPSHOTS_FOLDER + oldSnap).blockingAwait();
            } catch (Exception e) {
                Log.w(TAG, "Impossibile eliminare vecchio snapshot: " + oldSnap, e);
            }
        }

        Log.d(TAG, "Snapshot creato e manifest compattato: " + snapshotName);
    }

    private void downloadAndApplySnapshot(String snapshotId) throws IOException {
        Log.d(TAG, "Download snapshot: " + snapshotId);
        String snapPath = pantryPath + SyncManager.DEFAULT_SNAPSHOTS_FOLDER + snapshotId;
        Boolean exists;
        try {
            exists = store.exists(snapPath).blockingGet();
        } catch (RuntimeException e) {
            throw unwrapIOException("Errore verifica esistenza snapshot: " + snapshotId, e);
        }
        if (exists == null || !exists) {
            Log.w(TAG, "Snapshot referenziato dal manifest non presente sullo storage (verrà rigenerato): " + snapshotId);
            return;
        }
        byte[] data;
        try {
            data = store.read(snapPath).blockingGet();
        } catch (RuntimeException e) {
            if (isFileNotFound(e)) {
                Log.w(TAG, "Snapshot non trovato durante la lettura: " + snapshotId);
                return;
            }
            throw unwrapIOException("Impossibile scaricare snapshot: " + snapshotId, e);
        }
        if (data == null || data.length == 0) {
            throw new IOException("Snapshot file vuoto o illeggibile: " + snapshotId);
        }
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            dataBridge.applySnapshotJson(json, dispensaId);
        } catch (Exception e) {
            throw new IOException("Errore durante l'applicazione dello snapshot: " + snapshotId, e);
        }
    }

    private void downloadAndApplyEvent(String eventFile) throws IOException {
        String fullPath = pantryPath + eventFile;
        byte[] data;
        try {
            data = store.read(fullPath).blockingGet();
        } catch (RuntimeException e) {
            throw unwrapIOException("Impossibile scaricare event file: " + eventFile, e);
        }
        if (data == null || data.length == 0) {
            throw new IOException("Event file vuoto o illeggibile: " + eventFile);
        }
        try {
            PantryEvent event = gson.fromJson(new String(data, StandardCharsets.UTF_8), PantryEvent.class);
            if (event == null || event.payload == null) {
                throw new IOException("Event JSON non valido o privo di payload: " + eventFile);
            }
            String payloadJson = gson.toJson(event.payload);
            dataBridge.applyEvent(event.action, payloadJson, event.timestamp, dispensaId);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Errore durante l'applicazione dell'evento: " + eventFile, e);
        }
    }

    @Override
    public Completable initializeRemoteStructure() {
        return Completable.fromAction(() -> {
            Log.i(TAG, "Inizializzazione struttura remota per: " + pantryPath);

            // 1. Folders
            store.ensureFolder(pantryPath).blockingAwait();
            store.ensureFolder(pantryPath + SyncManager.DEFAULT_EVENTS_FOLDER).blockingAwait();
            store.ensureFolder(pantryPath + SyncManager.DEFAULT_DEVICES_FOLDER).blockingAwait();
            store.ensureFolder(pantryPath + SyncManager.DEFAULT_SNAPSHOTS_FOLDER).blockingAwait();

            // 2. Manifest check & init
            PantryManifest manifest = fetchManifest();
            if (manifest == null) {
                Log.i(TAG, "Manifest assente per " + pantryPath + ". Creazione...");
                manifest = new PantryManifest();
                manifest.version = SyncManager.CURRENT_SYNC_VERSION;
                manifest.pantryName = dataBridge.getPantryName(dispensaId);
                manifest.createdAt = System.currentTimeMillis();
                manifest.createdByDevice = deviceId;
                manifest.provider = store.getProviderId();

                byte[] manifestBytes = gson.toJson(manifest).getBytes(StandardCharsets.UTF_8);
                store.writeAtomic(pantryPath + SyncManager.MANIFEST_JSON, manifestBytes, null).blockingAwait();
            }

            // 3. Register current device
            updateDeviceRegistration();
        });
    }

    public boolean isRemoteStructureComplete() throws IOException {
        try {
            // 1. Pantry root folder
            Boolean pantryExists = store.exists(pantryPath).blockingGet();
            if (pantryExists == null || !pantryExists) return false;

            // 2. Devices subfolder
            String devicesFolder = pantryPath + SyncManager.DEFAULT_DEVICES_FOLDER;
            Boolean devicesExists = store.exists(devicesFolder).blockingGet();
            if (devicesExists == null || !devicesExists) return false;

            // 3. Events subfolder
            String eventsFolder = pantryPath + SyncManager.DEFAULT_EVENTS_FOLDER;
            Boolean eventsExists = store.exists(eventsFolder).blockingGet();
            if (eventsExists == null || !eventsExists) return false;

            // 4. Snapshots subfolder
            String snapshotsFolder = pantryPath + SyncManager.DEFAULT_SNAPSHOTS_FOLDER;
            Boolean snapshotsExists = store.exists(snapshotsFolder).blockingGet();
            if (snapshotsExists == null || !snapshotsExists) return false;

            // 5. Manifest file
            String manifestPath = pantryPath + SyncManager.MANIFEST_JSON;
            Boolean manifestExists = store.exists(manifestPath).blockingGet();
            if (manifestExists == null || !manifestExists) return false;

            return true;
        } catch (RuntimeException e) {
            throw unwrapIOException("Network or storage error verifying remote structure for " + pantryPath, e);
        }
    }

    public void verifyRemoteStructureExists() throws IOException {
        if (!isRemoteStructureComplete()) {
            PantryManifest manifest = fetchManifest();
            if (manifest != null && manifest.createdByDevice != null && !manifest.createdByDevice.trim().isEmpty()) {
                if (!deviceId.equals(manifest.createdByDevice.trim())) {
                    throw new RemoteStructureNotFoundException(pantryPath,
                            "Remote pantry structure is incomplete on storage and current device is not the owner (" + manifest.createdByDevice + "): " + pantryPath);
                }
            }
            if (dataBridge.isPantryOwner(dispensaId, deviceId)) {
                Log.i(TAG, "Struttura remota mancante o incompleta per " + pantryPath + ". Ricreazione silenziosa automatica per l'owner (" + deviceId + ")...");
                try {
                    initializeRemoteStructure().blockingAwait();
                } catch (Exception e) {
                    throw new IOException("Failed to silently recreate remote structure for pantry: " + pantryPath, e);
                }
            } else {
                throw new RemoteStructureNotFoundException(pantryPath,
                        "Remote pantry structure is missing or deleted on storage and current device is not the owner: " + pantryPath);
            }
        }
    }

    private static boolean isFileNotFound(Throwable t) {
        Throwable curr = t;
        while (curr != null) {
            if (curr instanceof FileNotFoundException) return true;
            curr = curr.getCause();
        }
        return false;
    }

    private static IOException unwrapIOException(String message, RuntimeException e) {
        Throwable cause = e.getCause();
        if (cause instanceof IOException) {
            return (IOException) cause;
        }
        return new IOException(message, cause != null ? cause : e);
    }
}
