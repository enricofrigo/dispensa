package eu.frigo.dispensa.sync.gdrive;

import android.content.Context;
import android.util.Log;
import com.google.api.services.drive.model.File;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItem;
import eu.frigo.dispensa.data.storage.StorageLocation;
import eu.frigo.dispensa.sync.core.engine.SyncEngine;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.policy.SyncPolicy;
import eu.frigo.dispensa.sync.core.store.OutboxRepository;
import eu.frigo.dispensa.sync.core.store.SyncCursorStore;
import eu.frigo.dispensa.sync.core.store.SyncPayload;
import eu.frigo.dispensa.sync.gdrive.client.GDriveClient;
import eu.frigo.dispensa.sync.gdrive.model.GDriveEvent;
import eu.frigo.dispensa.sync.gdrive.model.GDriveManifest;
import eu.frigo.dispensa.sync.gdrive.model.GDriveSnapshot;
import io.reactivex.rxjava3.core.Completable;

public class GDriveSyncEngine implements SyncEngine {
    private final Context context;
    private final GDriveClient client;
    private final Gson gson;
    private final SyncCursorStore cursorStore;
    private final OutboxRepository outbox;
    private final String deviceId;
    private final String pantryFolderId;
    private final AppDatabase db;

    public GDriveSyncEngine(Context context, GDriveClient client, SyncCursorStore cursorStore, OutboxRepository outbox, String deviceId, String pantryFolderId, AppDatabase db) {
        this.context = context;
        this.client = client;
        this.cursorStore = cursorStore;
        this.outbox = outbox;
        this.deviceId = deviceId;
        this.pantryFolderId = pantryFolderId;
        this.db = db;
        this.gson = new Gson();
    }

    @Override
    public Completable performSync(SyncPolicy policy) {
        return Completable.fromAction(() -> {
            if (!policy.canSyncNow()) return;

            Log.d("GDriveSync", "--- Inizio sessione di sincronizzazione GDrive ---");

            // 1. Pull
            GDriveManifest manifest = fetchManifest();
            if (manifest != null) {
                if (!deviceId.equals(manifest.createdByDevice)) {
                    if (!checkDeviceRegistration()) {
                        Log.w("GDriveSync", "Dispositivo rimosso dall'owner. Disconnessione...");
                        SyncManager.getInstance().disconnect(context);
                        context.sendBroadcast(new android.content.Intent(SyncManager.ACTION_SYNC_REMOVED));
                        return;
                    }
                }
                processRemoteChanges(manifest);
            }

            // 2. Push local events
            pushLocalChanges();

            // 3. Refresh manifest
            manifest = fetchManifest();
            if (manifest == null) manifest = new GDriveManifest();

            // 4. Compaction
            if (manifest.latestSnapshotId == null) {
                performCompaction();
            } else if (manifest.activeEventFiles.size() >= 50) {
                performCompaction();
            }

            Log.d("GDriveSync", "--- Sessione GDrive completata ---");
        });
    }

    private GDriveManifest fetchManifest() throws IOException {
        File file = client.getFileByName("manifest.json", pantryFolderId);
        if (file != null) {
            byte[] content = client.downloadFile(file.getId());
            GDriveManifest m = gson.fromJson(new String(content), GDriveManifest.class);
            m.fileId = file.getId();
            return m;
        }
        return null;
    }

    private boolean checkDeviceRegistration() throws IOException {
        String devicesFolderId = client.getOrCreateFolder(SyncManager.DEFAULT_DEVICES_FOLDER.replace("/", ""), pantryFolderId);
        File file = client.getFileByName(deviceId + ".json", devicesFolderId);
        return file != null;
    }

    private void updateManifest(java.util.function.Consumer<GDriveManifest> updater) throws IOException {
        GDriveManifest manifest = fetchManifest();
        if (manifest == null) {
            manifest = new GDriveManifest();
            manifest.createdByDevice = deviceId;
            manifest.createdAt = System.currentTimeMillis();
        }

        updater.accept(manifest);
        String json = gson.toJson(manifest);
        if (manifest.fileId == null) {
            client.createFile("manifest.json", "application/json", pantryFolderId, json.getBytes());
        } else {
            client.updateFile(manifest.fileId, json.getBytes());
        }
    }

    private void processRemoteChanges(GDriveManifest manifest) throws IOException {
        long lastSync = cursorStore.getLastSyncTimestamp();

        if (lastSync == 0 && manifest.latestSnapshotId != null) {
            downloadAndApplySnapshot(manifest.latestSnapshotId);
        }

        for (String eventFileName : manifest.activeEventFiles) {
            long eventTs = extractTimestampFromFilename(eventFileName);
            if (eventTs > lastSync) {
                downloadAndApplyEvent(eventFileName);
            }
        }

        cursorStore.updateLastSyncTimestamp(manifest.lastGlobalTimestamp);
    }

    private void pushLocalChanges() throws Exception {
        List<SyncPayload> pending = outbox.getPendingChanges().blockingGet();
        if (pending.isEmpty()) return;

        String eventsFolderId = client.getOrCreateFolder(SyncManager.DEFAULT_EVENTS_FOLDER.replace("/", ""), pantryFolderId);
        List<String> uploadedFiles = new ArrayList<>();
        List<String> syncedIds = new ArrayList<>();
        long maxTs = 0;

        for (SyncPayload payload : pending) {
            GDriveEvent event = new GDriveEvent();
            event.eventId = payload.getSyncId();
            event.deviceId = deviceId;
            event.timestamp = payload.getTimestamp();
            event.action = payload.getDataType();
            event.payload = gson.fromJson(payload.getContent(), java.util.Map.class);

            String fileName = "ev_" + deviceId + "_" + event.timestamp + ".json";
            client.createFile(fileName, "application/json", eventsFolderId, gson.toJson(event).getBytes());
            uploadedFiles.add(SyncManager.DEFAULT_EVENTS_FOLDER + fileName);
            syncedIds.add(payload.getSyncId());
            maxTs = Math.max(maxTs, event.timestamp);
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
        GDriveSnapshot snapshot = new GDriveSnapshot();
        snapshot.timestamp = System.currentTimeMillis();
        snapshot.products = db.productDao().getAllProductsListStatic();
        snapshot.locations = db.storageLocationDao().getAllLocationsSortedSync();
        snapshot.shoppingItems = db.shoppingItemDao().getAllItemsSync();

        String snapshotsFolderId = client.getOrCreateFolder(SyncManager.DEFAULT_SNAPSHOTS_FOLDER.replace("/", ""), pantryFolderId);
        String snapshotName = "snap_" + snapshot.timestamp + ".json";

        client.createFile(snapshotName, "application/json", snapshotsFolderId, gson.toJson(snapshot).getBytes());
        updateManifest(m -> {
            m.latestSnapshotId = snapshotName;
            m.activeEventFiles.clear();
            m.lastGlobalTimestamp = Math.max(m.lastGlobalTimestamp, snapshot.timestamp);
        });
    }

    private void downloadAndApplySnapshot(String snapshotName) throws IOException {
        String snapshotsFolderId = client.getOrCreateFolder(SyncManager.DEFAULT_SNAPSHOTS_FOLDER.replace("/", ""), pantryFolderId);
        File file = client.getFileByName(snapshotName, snapshotsFolderId);
        if (file != null) {
            byte[] content = client.downloadFile(file.getId());
            GDriveSnapshot snapshot = gson.fromJson(new String(content), GDriveSnapshot.class);
            applySnapshot(snapshot);
        }
    }

    private void downloadAndApplyEvent(String eventPath) throws IOException {
        String fileName = eventPath.substring(eventPath.lastIndexOf("/") + 1);
        String eventsFolderId = client.getOrCreateFolder(SyncManager.DEFAULT_EVENTS_FOLDER.replace("/", ""), pantryFolderId);
        File file = client.getFileByName(fileName, eventsFolderId);
        if (file != null) {
            byte[] content = client.downloadFile(file.getId());
            GDriveEvent event = gson.fromJson(new String(content), GDriveEvent.class);
            applyRemoteEvent(event);
        }
    }

    private void applySnapshot(GDriveSnapshot snapshot) {
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

    private void applyRemoteEvent(GDriveEvent event) {
        db.runInTransaction(() -> {
            if (event.payload == null) return;
            String jsonPayload = gson.toJson(event.payload);
            switch (event.action) {
                case GDriveEvent.ACTION_UPSERT_PRODUCT:
                    Product remoteP = gson.fromJson(jsonPayload, Product.class);
                    Product localP = db.productDao().getProductByLotKeySync(remoteP.barcode, remoteP.expiryDate, remoteP.getStorageLocation());
                    if (localP == null || remoteP.lastModified > localP.lastModified) {
                        if (localP != null) remoteP.id = localP.id;
                        db.productDao().insert(remoteP);
                    }
                    break;
                case GDriveEvent.ACTION_DELETE_PRODUCT:
                    Product toDelete = gson.fromJson(jsonPayload, Product.class);
                    Product localDel = db.productDao().getProductByLotKeySync(toDelete.barcode, toDelete.expiryDate, toDelete.getStorageLocation());
                    if (localDel != null && event.timestamp > localDel.lastModified) {
                        db.productDao().delete(localDel);
                    }
                    break;
                case GDriveEvent.ACTION_UPSERT_LOCATION:
                    StorageLocation remoteL = gson.fromJson(jsonPayload, StorageLocation.class);
                    StorageLocation localL = db.storageLocationDao().getLocationByInternalKeySync(remoteL.internalKey);
                    if (localL == null || remoteL.lastModified > localL.lastModified) {
                        if (localL != null) remoteL.id = localL.id;
                        db.storageLocationDao().insert(remoteL);
                    }
                    break;
                case GDriveEvent.ACTION_UPSERT_SHOPPING_ITEM:
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
