package eu.frigo.dispensa.data.sync;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItem;
import eu.frigo.dispensa.data.storage.StorageLocation;
import eu.frigo.dispensa.sync.core.engine.PantryDataBridge;
import eu.frigo.dispensa.sync.core.engine.PantryOutboxItem;
import eu.frigo.dispensa.sync.core.model.PantryEvent;

public class RoomPantryDataBridge implements PantryDataBridge {
    private final AppDatabase db;
    private final Gson gson;

    public RoomPantryDataBridge(AppDatabase db) {
        this.db = db;
        this.gson = new Gson();
    }

    private static class SnapshotDto {
        @com.google.gson.annotations.SerializedName("timestamp")
        public long timestamp;
        @com.google.gson.annotations.SerializedName("products")
        public List<Product> products;
        @com.google.gson.annotations.SerializedName("locations")
        public List<StorageLocation> locations;
        @com.google.gson.annotations.SerializedName(value = "shoppingItems", alternate = {"shopping_items"})
        public List<ShoppingItem> shoppingItems;
    }

    @Override
    public List<PantryOutboxItem> getPendingOutboxEvents(int dispensaId) {
        List<SyncOutbox> entries = db.syncOutboxDao().getPendingChangesSync(dispensaId);
        List<PantryOutboxItem> items = new ArrayList<>();
        for (SyncOutbox entry : entries) {
            String payload = entry.payload;
            if (PantryEvent.ACTION_UPSERT_PRODUCT.equals(entry.dataType) && payload != null) {
                try {
                    Product p = gson.fromJson(payload, Product.class);
                    if (p != null && p.hasCustomLocalImage()) {
                        payload = gson.toJson(p.createExportCopy());
                    }
                } catch (Exception ignored) {}
            }
            items.add(new PantryOutboxItem(entry.syncId, entry.dataType, payload, entry.timestamp));
        }
        return items;
    }

    @Override
    public void markEventsAsSynced(List<String> syncIds) {
        db.syncOutboxDao().markAsSynced(syncIds);
    }

    @Override
    public String createSnapshotJson(int dispensaId) {
        SnapshotDto dto = new SnapshotDto();
        dto.timestamp = System.currentTimeMillis();
        List<Product> rawProducts = db.productDao().getAllProductsListStatic(dispensaId);
        List<Product> snapshotProducts = new ArrayList<>();
        if (rawProducts != null) {
            for (Product p : rawProducts) {
                snapshotProducts.add(p.createExportCopy());
            }
        }
        dto.products = snapshotProducts;
        dto.locations = db.storageLocationDao().getAllLocationsSortedSync(dispensaId);
        dto.shoppingItems = db.shoppingItemDao().getAllItemsSync(dispensaId);
        return gson.toJson(dto);
    }

    @Override
    public void applySnapshotJson(String snapshotJson, int dispensaId) {
        SnapshotDto snapshot = gson.fromJson(snapshotJson, SnapshotDto.class);
        if (snapshot == null) return;

        db.runInTransaction(() -> {
            if (snapshot.locations != null) {
                for (StorageLocation remote : snapshot.locations) {
                    remote.dispensaId = dispensaId;
                    StorageLocation local = db.storageLocationDao().getLocationByInternalKeySync(remote.internalKey, dispensaId);
                    if (local == null || remote.lastModified > local.lastModified) {
                        if (local != null) remote.id = local.id;
                        db.storageLocationDao().insert(remote);
                    }
                }
            }
            if (snapshot.products != null) {
                for (Product p : snapshot.products) {
                    p.dispensaId = dispensaId;
                    if (p.hasCustomLocalImage()) {
                        p.setImageUrl(null);
                    }
                    p.validateImageUrlExistence();
                    Product local = db.productDao().getProductByLotKeySync(p.barcode, p.expiryDate, p.getStorageLocation(), dispensaId);
                    if (local == null || p.lastModified > local.lastModified) {
                        if (local != null) p.id = local.id;
                        db.productDao().insert(p);
                    }
                }
            }
            if (snapshot.shoppingItems != null) {
                for (ShoppingItem s : snapshot.shoppingItems) {
                    s.dispensaId = dispensaId;
                    ShoppingItem local = db.shoppingItemDao().getItemByNameSync(s.name, dispensaId);
                    if (local == null || s.lastModified > local.lastModified) {
                        if (local != null) s.id = local.id;
                        db.shoppingItemDao().insert(s);
                    }
                }
            }
        });
    }

    @Override
    public void applyEvent(String action, String payloadJson, long eventTimestamp, int dispensaId) {
        db.runInTransaction(() -> {
            if (payloadJson == null) return;
            switch (action) {
                case PantryEvent.ACTION_UPSERT_PRODUCT:
                    Product remoteP = gson.fromJson(payloadJson, Product.class);
                    remoteP.dispensaId = dispensaId;
                    if (remoteP.hasCustomLocalImage()) {
                        remoteP.setImageUrl(null);
                    }
                    remoteP.validateImageUrlExistence();
                    Product localP = db.productDao().getProductByLotKeySync(remoteP.barcode, remoteP.expiryDate, remoteP.getStorageLocation(), dispensaId);
                    if (localP == null || remoteP.lastModified > localP.lastModified) {
                        if (localP != null) remoteP.id = localP.id;
                        db.productDao().insert(remoteP);
                    }
                    break;
                case PantryEvent.ACTION_DELETE_PRODUCT:
                    Product toDelete = gson.fromJson(payloadJson, Product.class);
                    Product localDel = db.productDao().getProductByLotKeySync(toDelete.barcode, toDelete.expiryDate, toDelete.getStorageLocation(), dispensaId);
                    if (localDel != null && eventTimestamp > localDel.lastModified) {
                        db.productDao().delete(localDel);
                    }
                    break;
                case PantryEvent.ACTION_UPSERT_LOCATION:
                    StorageLocation remoteL = gson.fromJson(payloadJson, StorageLocation.class);
                    remoteL.dispensaId = dispensaId;
                    StorageLocation localL = db.storageLocationDao().getLocationByInternalKeySync(remoteL.internalKey, dispensaId);
                    if (localL == null || remoteL.lastModified > localL.lastModified) {
                        if (localL != null) remoteL.id = localL.id;
                        db.storageLocationDao().insert(remoteL);
                    }
                    break;
                case PantryEvent.ACTION_UPSERT_SHOPPING_ITEM:
                    ShoppingItem remoteS = gson.fromJson(payloadJson, ShoppingItem.class);
                    remoteS.dispensaId = dispensaId;
                    ShoppingItem localS = db.shoppingItemDao().getItemByNameSync(remoteS.name, dispensaId);
                    if (localS == null || remoteS.lastModified > localS.lastModified) {
                        if (localS != null) remoteS.id = localS.id;
                        db.shoppingItemDao().insert(remoteS);
                    }
                    break;
            }
        });
    }

    @Override
    public String getPantryName(int dispensaId) {
        try {
            Dispensa disp = db.dispensaDao().getDispensaByIdSync(dispensaId);
            if (disp != null && disp.getName() != null && !disp.getName().trim().isEmpty()) {
                return disp.getName();
            }
        } catch (Exception ignored) {}
        return "Dispensa";
    }

    @Override
    public boolean isPantryOwner(int dispensaId, String currentDeviceId) {
        try {
            Dispensa disp = db.dispensaDao().getDispensaByIdSync(dispensaId);
            if (disp != null) {
                if (disp.deviceOwnerId == null || disp.deviceOwnerId.trim().isEmpty()) {
                    return true;
                }
                return disp.deviceOwnerId.equals(currentDeviceId);
            }
        } catch (Exception ignored) {}
        return true;
    }
}

