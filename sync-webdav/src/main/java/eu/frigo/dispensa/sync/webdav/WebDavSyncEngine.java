package eu.frigo.dispensa.sync.webdav;

import com.google.gson.Gson;
import android.util.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import eu.frigo.dispensa.sync.core.engine.SyncEngine;
import eu.frigo.dispensa.sync.core.policy.SyncPolicy;
import eu.frigo.dispensa.sync.core.store.OutboxRepository;
import eu.frigo.dispensa.sync.core.store.SyncCursorStore;
import eu.frigo.dispensa.sync.core.store.SyncPayload;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import eu.frigo.dispensa.sync.webdav.model.WebDavEvent;
import eu.frigo.dispensa.sync.webdav.model.WebDavManifest;
import eu.frigo.dispensa.sync.webdav.model.WebDavSnapshot;
import io.reactivex.rxjava3.core.Completable;
import okhttp3.Response;

public class WebDavSyncEngine implements SyncEngine {
    private final WebDavClient client;
    private final Gson gson;
    private final SyncCursorStore cursorStore;
    private final OutboxRepository outbox;
    private final String deviceId;
    private final String pantryPath;

    public WebDavSyncEngine(WebDavClient client, SyncCursorStore cursorStore, OutboxRepository outbox, String deviceId, String pantryPath) {
        this.client = client;
        this.cursorStore = cursorStore;
        this.outbox = outbox;
        this.deviceId = deviceId;
        this.pantryPath = pantryPath.endsWith("/") ? pantryPath : pantryPath + "/";
        this.gson = new Gson();
    }

    @Override
    public Completable performSync(SyncPolicy policy) {
        return Completable.fromAction(() -> {
            if (!policy.canSyncNow()) return;
            
            // 1. Pull
            WebDavManifest manifest = fetchManifest();
            if (manifest != null) {
                processRemoteChanges(manifest);
            }

            // 2. Push
            pushLocalChanges();

            // 3. Optional Compaction
            if (manifest != null && manifest.activeEventFiles.size() >= 50) {
                performCompaction(manifest);
            }
        });
    }

    private WebDavManifest fetchManifest() throws IOException {
        try (Response response = client.get(pantryPath + "manifest.json")) {
            if (response.isSuccessful() && response.body() != null) {
                WebDavManifest manifest = gson.fromJson(response.body().string(), WebDavManifest.class);
                manifest.etag = response.header("ETag");
                return manifest;
            }
            return null;
        }
    }

    private void processRemoteChanges(WebDavManifest manifest) throws IOException {
        long lastSync = cursorStore.getLastSyncTimestamp();
        
        // Handle Snapshot if last sync is very old or non-existent
        if (lastSync == 0 && manifest.latestSnapshotId != null) {
            downloadAndApplySnapshot(manifest.latestSnapshotId);
        }

        // Catch up on events
        for (String eventFile : manifest.activeEventFiles) {
            long eventTs = extractTimestampFromFilename(eventFile);
            if (eventTs > lastSync) {
                downloadAndApplyEvent(eventFile);
            }
        }
        
        cursorStore.updateLastSyncTimestamp(manifest.lastGlobalTimestamp);
    }

    private void pushLocalChanges() throws Exception {
        List<SyncPayload> pending = outbox.getPendingChanges().blockingGet();
        if (pending.isEmpty()) return;

        Log.d("SyncFlow", "Trovati " + pending.size() + " eventi locali pronti per il push su WebDAV.");

        for (SyncPayload payload : pending) {
            WebDavEvent event = new WebDavEvent();
            event.eventId = payload.getSyncId();
            event.deviceId = deviceId;
            event.timestamp = payload.getTimestamp();
            event.action = payload.getDataType();
            // payload.getContent() is already JSON
            event.payload = gson.fromJson(payload.getContent(), java.util.Map.class);

            String fileName = pantryPath + "events/ev_" + deviceId + "_" + event.timestamp + ".json";
            Log.d("SyncFlow", "Preparazione push evento: " + event.action + " -> " + fileName);

            try (Response response = client.put(fileName, gson.toJson(event).getBytes(), null)) {
                if (response.isSuccessful()) {
                    Log.d("SyncFlow", "Evento pushato con successo: " + fileName);
                    updateManifestWithNewEvent("events/ev_" + deviceId + "_" + event.timestamp + ".json", event.timestamp);
                } else {
                    Log.e("SyncFlow", "Errore nel push dell'evento " + fileName + ": " + response.code());
                }
            }
        }
        
        List<String> ids = new ArrayList<>();
        for (SyncPayload p : pending) ids.add(p.getSyncId());
        outbox.markAsSynced(ids).blockingAwait();
        Log.d("SyncFlow", "Tutti gli eventi pendenti sono stati marcati come sincronizzati localmente.");
    }

    private void updateManifestWithNewEvent(String eventFileName, long timestamp) throws IOException {
        Log.d("SyncFlow", "Aggiornamento manifest.json con il nuovo evento: " + eventFileName);
        WebDavManifest manifest = fetchManifest();
        if (manifest == null) {
            manifest = new WebDavManifest();
        }
        manifest.activeEventFiles.add(eventFileName);
        manifest.lastGlobalTimestamp = Math.max(manifest.lastGlobalTimestamp, timestamp);
        
        try (Response response = client.put(pantryPath + "manifest.json", gson.toJson(manifest).getBytes(), manifest.etag)) {
            if (response.isSuccessful()) {
                Log.d("SyncFlow", "Manifest aggiornato con successo.");
            } else {
                Log.e("SyncFlow", "Errore nell'aggiornamento del manifest: " + response.code());
            }
        }
    }

    private void performCompaction(WebDavManifest manifest) throws IOException {
        // Implementation: Download current snapshot, download all active events, 
        // merge them, upload new snapshot, update manifest to clear activeEventFiles
    }

    private void downloadAndApplySnapshot(String snapshotId) throws IOException {
        // Implementation for downloading and merging snapshot data
    }

    private void downloadAndApplyEvent(String eventFile) throws IOException {
        // Implementation for downloading and applying a single event
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
}
