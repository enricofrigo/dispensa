package eu.frigo.dispensa.sync.webdav;

import com.google.gson.Gson;

import android.content.Context;
import android.util.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import eu.frigo.dispensa.sync.core.engine.CrDtSyncManager;
import eu.frigo.dispensa.sync.core.SyncTransport;
import eu.frigo.dispensa.sync.webdav.migration.WebDavSyncMigration;
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
import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.storage.StorageLocation;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItem;

public class WebDavSyncEngine implements SyncEngine, SyncTransport {
    private static final String TAG = "WebDavSyncEngine";

    private final WebDavClient client;
    private final Gson gson;
    private final SyncCursorStore cursorStore;
    private final OutboxRepository outbox;
    private final String deviceId;
    private final String pantryPath;
    private final AppDatabase db;
    private final CrDtSyncManager syncManager;
    private final Context context;

    public WebDavSyncEngine(WebDavClient client, SyncCursorStore cursorStore, OutboxRepository outbox, String deviceId, String pantryPath, AppDatabase db, Context context) {
        this.client = client;
        this.cursorStore = cursorStore;
        this.outbox = outbox;
        this.deviceId = deviceId;
        this.pantryPath = pantryPath.endsWith("/") ? pantryPath : pantryPath + "/";
        this.db = db;
        this.context = context;
        this.gson = new Gson();
        this.syncManager = new CrDtSyncManager(db, context);
    }

    @Override
    public Completable performSync(SyncPolicy policy) {
        return Completable.fromAction(() -> {
            if (!policy.canSyncNow()) return;
            
            Log.d("SyncFlow", "--- Inizio sessione di sincronizzazione (v1.1 CRDT) ---");

            // 0. Migration (one-time)
            WebDavSyncMigration.migrateOutboxToSyncChanges(db, context);

            // 1. Pull changes
            pull(new SyncCallback() {
                @Override
                public void onSuccess(byte[] blobBytes) {
                    if (blobBytes != null) {
                        syncManager.importChanges(blobBytes);
                    }
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Pull failed during performSync", e);
                }
            });

            // 2. Export and Push local changes
            long lastVer = syncManager.getLastSyncVersion();
            byte[] localBlob = syncManager.exportChanges(lastVer);
            
            if (localBlob != null) {
                push(localBlob, new SyncCallback() {
                    @Override
                    public void onSuccess(byte[] blobBytes) {
                        // Update local cursor upon success
                        syncManager.persistLastSyncVersion(syncManager.getMaxSyncClock());
                        Log.d("SyncFlow", "Push successful, cursor updated.");
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Push failed during performSync", e);
                    }
                });
            }

            // 3. Optional: Compaction / Snapshots (Legacy logic still applicable if needed)
            WebDavManifest manifest = fetchManifest();
            if (manifest != null && manifest.activeEventFiles.size() >= 50) {
                 Log.d("SyncFlow", "Compacting legacy events...");
                 performCompaction();
            }
            
            Log.d("SyncFlow", "--- Sessione CRDT completata ---");
        });
    }

    @Override
    public void push(byte[] data, SyncCallback callback) {
        try {
            String blobId = "sync/blob_" + deviceId + "_" + System.currentTimeMillis() + ".json";
            try (Response response = client.put(pantryPath + blobId, data, null)) {
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("Failed to upload sync blob: " + response.code()));
                    return;
                }
            }

            updateManifest(m -> {
                m.latestSyncBlobId = blobId;
                m.lastGlobalTimestamp = System.currentTimeMillis();
            });
            callback.onSuccess(null);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    @Override
    public void pull(SyncCallback callback) {
        try {
            WebDavManifest manifest = fetchManifest();
            if (manifest == null) {
                callback.onSuccess(null);
                return;
            }

            // Priority: New CRDT Blob
            if (manifest.latestSyncBlobId != null) {
                try (Response response = client.get(pantryPath + manifest.latestSyncBlobId)) {
                    if (response.isSuccessful() && response.body() != null) {
                        callback.onSuccess(response.body().bytes());
                        return;
                    }
                }
            }

            // Fallback: Legacy Events
            if (!manifest.activeEventFiles.isEmpty()) {
                Log.d(TAG, "Processing legacy events for pull...");
                processRemoteChanges(manifest);
            }
            
            callback.onSuccess(null);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    private WebDavManifest fetchManifest() throws IOException {
        try (Response response = client.get(pantryPath + "manifest.json")) {
            if (response.isSuccessful() && response.body() != null) {
                WebDavManifest m = gson.fromJson(response.body().string(), WebDavManifest.class);
                m.etag = response.header("ETag");
                return m;
            }
            return null;
        }
    }

    private void updateManifest(java.util.function.Consumer<WebDavManifest> updater) throws IOException {
        int retries = 3;
        while (retries > 0) {
            WebDavManifest manifest = fetchManifest();
            if (manifest == null) {
                manifest = new WebDavManifest();
                manifest.createdByDevice = deviceId;
                manifest.createdAt = System.currentTimeMillis();
            }
            
            updater.accept(manifest);
            String json = gson.toJson(manifest);
            try (Response response = client.put(pantryPath + "manifest.json", json.getBytes(), manifest.etag)) {
                if (response.isSuccessful()) {
                    Log.d("SyncFlow", "Manifest aggiornato con successo.");
                    return;
                } else if (response.code() == 412) {
                    Log.w("SyncFlow", "Concorrenza nel salvataggio manifest (412). Riprovo...");
                    retries--;
                } else {
                    throw new IOException("Errore salvataggio manifest: " + response.code() + " " + response.message());
                }
            }
        }
        throw new IOException("Troppi tentativi falliti (412) per il salvataggio del manifest");
    }

    private void processRemoteChanges(WebDavManifest manifest) throws IOException {
        long lastSync = cursorStore.getLastSyncTimestamp();
        
        if (lastSync == 0 && manifest.latestSnapshotId != null) {
            downloadAndApplySnapshot(manifest.latestSnapshotId);
        }

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

        Log.d("SyncFlow", "Push di " + pending.size() + " eventi locali...");
        
        List<String> uploadedFiles = new ArrayList<>();
        List<String> syncedIds = new ArrayList<>();
        long maxTs = 0;

        for (SyncPayload payload : pending) {
            WebDavEvent event = new WebDavEvent();
            event.eventId = payload.getSyncId();
            event.deviceId = deviceId;
            event.timestamp = payload.getTimestamp();
            event.action = payload.getDataType();
            event.payload = gson.fromJson(payload.getContent(), java.util.Map.class);

            String fileName = "events/ev_" + deviceId + "_" + event.timestamp + ".json";
            try (Response response = client.put(pantryPath + fileName, gson.toJson(event).getBytes(), null)) {
                if (response.isSuccessful()) {
                    uploadedFiles.add(fileName);
                    syncedIds.add(payload.getSyncId());
                    maxTs = Math.max(maxTs, event.timestamp);
                } else {
                    throw new IOException("Fallito caricamento evento: " + fileName + " (" + response.code() + ")");
                }
            }
        }
        
        long finalMaxTs = maxTs;
        updateManifest(m -> {
            for (String file : uploadedFiles) {
                if (!m.activeEventFiles.contains(file)) {
                    m.activeEventFiles.add(file);
                }
            }
            m.lastGlobalTimestamp = Math.max(m.lastGlobalTimestamp, finalMaxTs);
        });

        outbox.markAsSynced(syncedIds).blockingAwait();
    }

    private void performCompaction() throws IOException {
        Log.d("SyncFlow", "Esecuzione compattazione...");
        
        WebDavSnapshot snapshot = new WebDavSnapshot();
        snapshot.timestamp = System.currentTimeMillis();
        snapshot.products = db.productDao().getAllProductsListStatic();
        snapshot.locations = db.storageLocationDao().getAllLocationsSortedSync();
        snapshot.shoppingItems = db.shoppingItemDao().getAllItemsSync();

        String snapshotName = "snap_" + snapshot.timestamp + ".json";
        
        try (Response response = client.put(pantryPath + "snapshots/" + snapshotName, gson.toJson(snapshot).getBytes(), null)) {
            if (response.isSuccessful()) {
                updateManifest(m -> {
                    m.latestSnapshotId = snapshotName;
                    m.activeEventFiles.clear();
                    m.lastGlobalTimestamp = Math.max(m.lastGlobalTimestamp, snapshot.timestamp);
                });
                Log.d("SyncFlow", "Snapshot creato e manifest aggiornato: " + snapshotName);
            } else {
                throw new IOException("Fallito caricamento snapshot: " + response.code());
            }
        }
    }

    private void downloadAndApplySnapshot(String snapshotId) throws IOException {
        Log.d("SyncFlow", "Download snapshot: " + snapshotId);
        try (Response response = client.get(pantryPath + "snapshots/" + snapshotId)) {
            if (response.isSuccessful() && response.body() != null) {
                WebDavSnapshot snapshot = gson.fromJson(response.body().string(), WebDavSnapshot.class);
                applySnapshot(snapshot);
            }
        }
    }

    private void downloadAndApplyEvent(String eventFile) throws IOException {
        try (Response response = client.get(pantryPath + eventFile)) {
            if (response.isSuccessful() && response.body() != null) {
                WebDavEvent event = gson.fromJson(response.body().string(), WebDavEvent.class);
                applyRemoteEvent(event);
            }
        }
    }

    private void applySnapshot(WebDavSnapshot snapshot) {
        db.runInTransaction(() -> {
            if (snapshot.locations != null) {
                for (StorageLocation remote : snapshot.locations) {
                    StorageLocation local = db.storageLocationDao().getLocationByInternalKeySync(remote.internalKey);
                    if (local == null || remote.lastModified > local.lastModified) {
                        if (local != null) remote.id = local.id;
                        db.storageLocationDao().insert(remote);
                    }
                }
            }
            if (snapshot.products != null) {
                for (Product p : snapshot.products) {
                    Product local = db.productDao().getProductByLotKeySync(p.barcode, p.expiryDate, p.getStorageLocation());
                    if (local == null || p.lastModified > local.lastModified) {
                        if (local != null) p.id = local.id;
                        db.productDao().insert(p);
                    }
                }
            }
            if (snapshot.shoppingItems != null) {
                for (ShoppingItem s : snapshot.shoppingItems) {
                    ShoppingItem local = db.shoppingItemDao().getItemByNameSync(s.name);
                    if (local == null || s.lastModified > local.lastModified) {
                        if (local != null) s.id = local.id;
                        db.shoppingItemDao().insert(s);
                    }
                }
            }
        });
    }

    private void applyRemoteEvent(WebDavEvent event) {
        db.runInTransaction(() -> {
            if (event.payload == null) return;
            String jsonPayload = gson.toJson(event.payload);
            switch (event.action) {
                case WebDavEvent.ACTION_UPSERT_PRODUCT:
                    Product remoteP = gson.fromJson(jsonPayload, Product.class);
                    Product localP = db.productDao().getProductByLotKeySync(remoteP.barcode, remoteP.expiryDate, remoteP.getStorageLocation());
                    if (localP == null || remoteP.lastModified > localP.lastModified) {
                        if (localP != null) remoteP.id = localP.id;
                        db.productDao().insert(remoteP);
                    }
                    break;
                case WebDavEvent.ACTION_DELETE_PRODUCT:
                    Product toDelete = gson.fromJson(jsonPayload, Product.class);
                    Product localDel = db.productDao().getProductByLotKeySync(toDelete.barcode, toDelete.expiryDate, toDelete.getStorageLocation());
                    if (localDel != null && event.timestamp > localDel.lastModified) {
                        db.productDao().delete(localDel);
                    }
                    break;
                case WebDavEvent.ACTION_UPSERT_LOCATION:
                    StorageLocation remoteL = gson.fromJson(jsonPayload, StorageLocation.class);
                    StorageLocation localL = db.storageLocationDao().getLocationByInternalKeySync(remoteL.internalKey);
                    if (localL == null || remoteL.lastModified > localL.lastModified) {
                        if (localL != null) remoteL.id = localL.id;
                        db.storageLocationDao().insert(remoteL);
                    }
                    break;
                case WebDavEvent.ACTION_UPSERT_SHOPPING_ITEM:
                    ShoppingItem remoteS = gson.fromJson(jsonPayload, ShoppingItem.class);
                    ShoppingItem localS = db.shoppingItemDao().getItemByNameSync(remoteS.name);
                    if (localS == null || remoteS.lastModified > localS.lastModified) {
                        if (localS != null) remoteS.id = localS.id;
                        db.shoppingItemDao().insert(remoteS);
                    }
                    break;
            }
        });
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
