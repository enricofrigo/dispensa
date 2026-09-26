package eu.frigo.dispensa.sync.core.engine;

import android.util.Log;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

            // 0. Verify remote folders & manifest exist (fail if missing or deleted)
            verifyRemoteStructureExists();

            // 0b. Version and migration check
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

            // 3. Reload manifest post-push
            manifest = fetchManifest();
            if (manifest == null) manifest = new PantryManifest();

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
                if (deviceId.equals(currentManifest.createdByDevice)) {
                    Log.i(TAG, "Migrazione manifest da V" + currentManifest.version + " a V" + SyncManager.CURRENT_SYNC_VERSION);
                    updateManifest(m -> m.version = SyncManager.CURRENT_SYNC_VERSION);
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

    private PantryManifest fetchManifest() {
        try {
            String manifestPath = pantryPath + SyncManager.MANIFEST_JSON;
            Boolean exists = store.exists(manifestPath).blockingGet();
            if (exists == null || !exists) return null;

            byte[] data = store.read(manifestPath).blockingGet();
            if (data == null || data.length == 0) return null;

            String json = new String(data, StandardCharsets.UTF_8);
            return gson.fromJson(json, PantryManifest.class);
        } catch (Exception e) {
            Log.w(TAG, "Error fetching manifest", e);
            return null;
        }
    }

    private void updateManifest(java.util.function.Consumer<PantryManifest> updater) throws Exception {
        int retries = 3;
        while (retries > 0) {
            PantryManifest manifest = fetchManifest();
            if (manifest == null) {
                manifest = new PantryManifest();
                manifest.createdByDevice = deviceId;
                manifest.createdAt = System.currentTimeMillis();
                manifest.version = SyncManager.CURRENT_SYNC_VERSION;
                manifest.provider = store.getProviderId();
                manifest.pantryName = dataBridge.getPantryName(dispensaId);
            }

            updater.accept(manifest);
            String json = gson.toJson(manifest);
            byte[] data = json.getBytes(StandardCharsets.UTF_8);

            try {
                store.writeAtomic(pantryPath + SyncManager.MANIFEST_JSON, data, manifest.etag).blockingAwait();
                Log.d(TAG, "Manifest salvato con successo.");
                return;
            } catch (Exception e) {
                retries--;
                if (retries == 0) throw e;
                Log.w(TAG, "Riprovo salvataggio manifest (tentativi rimasti: " + retries + ")", e);
            }
        }
    }

    private void processRemoteChanges(PantryManifest manifest) throws Exception {
        long lastSync = cursorStore.getLastSyncTimestamp();

        if (lastSync == 0 && manifest.latestSnapshotId != null) {
            downloadAndApplySnapshot(manifest.latestSnapshotId);
        }

        if (manifest.activeEventFiles != null) {
            for (String eventFile : manifest.activeEventFiles) {
                long eventTs = extractTimestampFromFilename(eventFile);
                if (eventTs > lastSync) {
                    downloadAndApplyEvent(eventFile);
                }
            }
        }

        cursorStore.updateLastSyncTimestamp(manifest.lastGlobalTimestamp);
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

            String fileName = SyncManager.DEFAULT_EVENTS_FOLDER + "ev_" + deviceId + "_" + event.timestamp + ".json";
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

        dataBridge.markEventsAsSynced(syncedIds);
    }

    private void performCompaction() throws Exception {
        Log.d(TAG, "Esecuzione compattazione...");

        long now = System.currentTimeMillis();
        String snapshotJson = dataBridge.createSnapshotJson(dispensaId);
        String snapshotName = "snap_" + now + ".json";
        String fullPath = pantryPath + SyncManager.DEFAULT_SNAPSHOTS_FOLDER + snapshotName;

        byte[] snapData = snapshotJson.getBytes(StandardCharsets.UTF_8);
        store.writeAtomic(fullPath, snapData, null).blockingAwait();

        updateManifest(m -> {
            m.latestSnapshotId = snapshotName;
            if (m.activeEventFiles != null) {
                m.activeEventFiles.clear();
            }
            m.lastGlobalTimestamp = Math.max(m.lastGlobalTimestamp, now);
        });
        Log.d(TAG, "Snapshot creato e manifest compattato: " + snapshotName);
    }

    private void downloadAndApplySnapshot(String snapshotId) {
        try {
            Log.d(TAG, "Download snapshot: " + snapshotId);
            String snapPath = pantryPath + SyncManager.DEFAULT_SNAPSHOTS_FOLDER + snapshotId;
            byte[] data = store.read(snapPath).blockingGet();
            if (data != null && data.length > 0) {
                String json = new String(data, StandardCharsets.UTF_8);
                dataBridge.applySnapshotJson(json, dispensaId);
            }
        } catch (Exception e) {
            Log.w(TAG, "Snapshot file non trovato o non leggibile: " + snapshotId, e);
        }
    }

    private void downloadAndApplyEvent(String eventFile) {
        try {
            String fullPath = pantryPath + eventFile;
            byte[] data = store.read(fullPath).blockingGet();
            if (data != null && data.length > 0) {
                PantryEvent event = gson.fromJson(new String(data, StandardCharsets.UTF_8), PantryEvent.class);
                if (event != null && event.payload != null) {
                    String payloadJson = gson.toJson(event.payload);
                    dataBridge.applyEvent(event.action, payloadJson, event.timestamp, dispensaId);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Event file non trovato o non leggibile: " + eventFile, e);
        }
    }

    private long extractTimestampFromFilename(String filename) {
        try {
            String[] parts = filename.split("_");
            String tsPart = parts[parts.length - 1].replace(".json", "");
            return Long.parseLong(tsPart);
        } catch (Exception e) {
            return 0;
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

    public boolean isRemoteStructureComplete() {
        try {
            // 1. Pantry root folder
            Boolean pantryExists = store.exists(pantryPath).blockingGet();
            if (pantryExists == null || !pantryExists) return false;

            // 2. Devices subfolder
            String devicesFolder = pantryPath + SyncManager.DEFAULT_DEVICES_FOLDER;
            Boolean devicesExists = store.exists(devicesFolder).blockingGet();
            Log.d(TAG, "Devices folder exists: " + devicesExists);
            if (devicesExists == null || !devicesExists) return false;

            // 3. Events subfolder
            String eventsFolder = pantryPath + SyncManager.DEFAULT_EVENTS_FOLDER;
            Boolean eventsExists = store.exists(eventsFolder).blockingGet();
            Log.d(TAG, "Events folder exists: " + eventsExists);
            if (eventsExists == null || !eventsExists) return false;

            // 4. Snapshots subfolder
            String snapshotsFolder = pantryPath + SyncManager.DEFAULT_SNAPSHOTS_FOLDER;
            Boolean snapshotsExists = store.exists(snapshotsFolder).blockingGet();
            Log.d(TAG, "Snapshots folder exists: " + snapshotsExists);
            if (snapshotsExists == null || !snapshotsExists) return false;

            // 5. Manifest file
            String manifestPath = pantryPath + SyncManager.MANIFEST_JSON;
            Boolean manifestExists = store.exists(manifestPath).blockingGet();
            if (manifestExists == null || !manifestExists) return false;

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void verifyRemoteStructureExists() throws IOException {
        if (!isRemoteStructureComplete()) {
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
}

