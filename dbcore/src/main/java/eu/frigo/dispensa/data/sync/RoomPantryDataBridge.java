package eu.frigo.dispensa.data.sync;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.category.CategoryDefinition;
import eu.frigo.dispensa.data.category.ProductCategoryLink;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItem;
import eu.frigo.dispensa.data.storage.PredefinedData;
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
                    if (p != null) {
                        if (p.categoryTags == null && p.id > 0) {
                            p.categoryTags = loadCategoryTagsForProduct(p.id);
                        }
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
                Product exportCopy = p.createExportCopy();
                exportCopy.categoryTags = loadCategoryTagsForProduct(p.id);
                snapshotProducts.add(exportCopy);
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
                        remote.id = (local != null) ? local.id : 0;
                        db.storageLocationDao().insert(remote);
                    }
                }
            }
            if (snapshot.products != null) {
                for (Product p : snapshot.products) {
                    upsertRemoteProduct(p, dispensaId);
                }
            }
            if (snapshot.shoppingItems != null) {
                for (ShoppingItem s : snapshot.shoppingItems) {
                    upsertRemoteShoppingItem(s, dispensaId);
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
                    if (remoteP != null) {
                        upsertRemoteProduct(remoteP, dispensaId);
                    }
                    break;
                case PantryEvent.ACTION_DELETE_PRODUCT:
                    Product toDelete = gson.fromJson(payloadJson, Product.class);
                    if (toDelete != null) {
                        Product localDel = findLocalProduct(toDelete, dispensaId);
                        if (localDel != null && eventTimestamp >= localDel.lastModified) {
                            db.productDao().delete(localDel);
                        }
                    }
                    break;
                case PantryEvent.ACTION_UPSERT_LOCATION:
                    StorageLocation remoteL = gson.fromJson(payloadJson, StorageLocation.class);
                    if (remoteL != null) {
                        remoteL.dispensaId = dispensaId;
                        StorageLocation localL = db.storageLocationDao().getLocationByInternalKeySync(remoteL.internalKey, dispensaId);
                        if (localL == null || remoteL.lastModified > localL.lastModified) {
                            remoteL.id = (localL != null) ? localL.id : 0;
                            db.storageLocationDao().insert(remoteL);
                        }
                    }
                    break;
                case PantryEvent.ACTION_DELETE_LOCATION:
                    StorageLocation locToDelete = gson.fromJson(payloadJson, StorageLocation.class);
                    if (locToDelete != null && locToDelete.internalKey != null) {
                        StorageLocation localLoc = db.storageLocationDao().getLocationByInternalKeySync(locToDelete.internalKey, dispensaId);
                        if (localLoc != null && eventTimestamp >= localLoc.lastModified) {
                            StorageLocation defaultLoc = db.storageLocationDao().getDefaultLocationSync(dispensaId);
                            String fallbackKey = defaultLoc != null ? defaultLoc.internalKey : PredefinedData.LOCATION_ALL;
                            db.productDao().updateProductLocation(localLoc.internalKey, fallbackKey, dispensaId);
                            db.storageLocationDao().delete(localLoc);
                        }
                    }
                    break;
                case PantryEvent.ACTION_UPSERT_SHOPPING_ITEM:
                    ShoppingItem remoteS = gson.fromJson(payloadJson, ShoppingItem.class);
                    if (remoteS != null) {
                        upsertRemoteShoppingItem(remoteS, dispensaId);
                    }
                    break;
                case PantryEvent.ACTION_DELETE_SHOPPING_ITEM:
                    ShoppingItem shopToDelete = gson.fromJson(payloadJson, ShoppingItem.class);
                    if (shopToDelete != null) {
                        ShoppingItem localShop = findLocalShoppingItem(shopToDelete, dispensaId);
                        if (localShop != null && eventTimestamp >= localShop.lastModified) {
                            db.shoppingItemDao().delete(localShop);
                        }
                    }
                    break;
            }
        });
    }

    private Product findLocalProduct(Product remote, int dispensaId) {
        boolean remoteHasUuid = remote.syncUuid != null && !remote.syncUuid.trim().isEmpty();
        if (remoteHasUuid) {
            Product byUuid = db.productDao().getProductBySyncUuidSync(remote.syncUuid.trim(), dispensaId);
            if (byUuid != null) {
                return byUuid;
            }
        }
        Product byLot = db.productDao().getProductByLotKeySync(remote.barcode, remote.expiryDate, remote.getStorageLocation(), dispensaId);
        if (byLot != null) {
            boolean localHasRealUuid = byLot.syncUuid != null
                    && !byLot.syncUuid.trim().isEmpty()
                    && !byLot.syncUuid.startsWith("prod_");
            if (remoteHasUuid && localHasRealUuid && !remote.syncUuid.trim().equals(byLot.syncUuid.trim())) {
                return null;
            }
            if ((remote.barcode == null || remote.barcode.trim().isEmpty())
                    && remote.getProductName() != null
                    && byLot.getProductName() != null
                    && !remote.getProductName().equalsIgnoreCase(byLot.getProductName())) {
                return null;
            }
        }
        return byLot;
    }

    private void upsertRemoteProduct(Product remoteP, int dispensaId) {
        remoteP.dispensaId = dispensaId;
        if (remoteP.hasCustomLocalImage()) {
            remoteP.setImageUrl(null);
        }
        remoteP.validateImageUrlExistence();

        Product localP = findLocalProduct(remoteP, dispensaId);
        if (localP == null || remoteP.lastModified > localP.lastModified) {
            remoteP.id = (localP != null) ? localP.id : 0;
            if (remoteP.syncUuid == null || remoteP.syncUuid.trim().isEmpty()) {
                remoteP.syncUuid = (localP != null && localP.syncUuid != null && !localP.syncUuid.trim().isEmpty())
                        ? localP.syncUuid
                        : UUID.randomUUID().toString();
            }
            long savedId = db.productDao().upsertPreservingLinks(remoteP);
            int targetProductId = (int) savedId;
            if (remoteP.categoryTags != null) {
                syncCategoryLinksForProduct(targetProductId, remoteP.categoryTags);
            }
        }
    }

    private ShoppingItem findLocalShoppingItem(ShoppingItem remote, int dispensaId) {
        boolean remoteHasUuid = remote.syncUuid != null && !remote.syncUuid.trim().isEmpty();
        if (remoteHasUuid) {
            ShoppingItem byUuid = db.shoppingItemDao().getItemBySyncUuidSync(remote.syncUuid.trim(), dispensaId);
            if (byUuid != null) {
                return byUuid;
            }
        }
        if (remote.name != null) {
            ShoppingItem byName = db.shoppingItemDao().getItemByNameSync(remote.name, dispensaId);
            if (byName != null) {
                boolean localHasRealUuid = byName.syncUuid != null
                        && !byName.syncUuid.trim().isEmpty()
                        && !byName.syncUuid.startsWith("shop_");
                if (remoteHasUuid && localHasRealUuid && !remote.syncUuid.trim().equals(byName.syncUuid.trim())) {
                    return null;
                }
            }
            return byName;
        }
        return null;
    }

    private void upsertRemoteShoppingItem(ShoppingItem remoteS, int dispensaId) {
        remoteS.dispensaId = dispensaId;
        ShoppingItem localS = findLocalShoppingItem(remoteS, dispensaId);
        if (localS == null || remoteS.lastModified > localS.lastModified) {
            remoteS.id = (localS != null) ? localS.id : 0;
            if (remoteS.syncUuid == null || remoteS.syncUuid.trim().isEmpty()) {
                remoteS.syncUuid = (localS != null && localS.syncUuid != null && !localS.syncUuid.trim().isEmpty())
                        ? localS.syncUuid
                        : UUID.randomUUID().toString();
            }
            if (remoteS.id > 0) {
                db.shoppingItemDao().update(remoteS);
            } else {
                db.shoppingItemDao().insert(remoteS);
            }
        }
    }

    private List<String> loadCategoryTagsForProduct(int productId) {
        List<String> tags = new ArrayList<>();
        List<ProductCategoryLink> allLinks = db.productCategoryLinkDao().getAllProductCategoryLinksSync();
        if (allLinks != null) {
            for (ProductCategoryLink link : allLinks) {
                if (link.productIdFk == productId) {
                    CategoryDefinition def = db.categoryDefinitionDao().getCategoryDefinitionById(link.categoryIdFk);
                    if (def != null && def.tagName != null && !def.tagName.trim().isEmpty()) {
                        tags.add(def.tagName.trim());
                    }
                }
            }
        }
        return tags;
    }

    private void syncCategoryLinksForProduct(int productId, List<String> categoryTags) {
        db.productCategoryLinkDao().deleteByProductId(productId);
        if (categoryTags == null || categoryTags.isEmpty()) {
            return;
        }
        List<ProductCategoryLink> linksToInsert = new ArrayList<>();
        for (String tag : categoryTags) {
            if (tag == null || tag.trim().isEmpty()) continue;
            String cleanTag = tag.trim();
            CategoryDefinition existing = db.categoryDefinitionDao().getCategoryByTagName(cleanTag);
            int catId;
            if (existing == null) {
                CategoryDefinition newDef = new CategoryDefinition(cleanTag);
                if (cleanTag.contains(":")) {
                    newDef.setLanguageCode(cleanTag.split(":")[0]);
                }
                catId = (int) db.categoryDefinitionDao().insert(newDef);
            } else {
                catId = existing.categoryId;
            }
            linksToInsert.add(new ProductCategoryLink(productId, catId));
        }
        if (!linksToInsert.isEmpty()) {
            db.productCategoryLinkDao().insertAll(linksToInsert);
        }
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
                    JoinedPantryConfig config = db.joinedPantryConfigDao().getConfigByDispensaId(dispensaId);
                    if (config != null) {
                        return false;
                    }
                    return true;
                }
                return disp.deviceOwnerId.equals(currentDeviceId);
            }
        } catch (Exception ignored) {}
        return false;
    }
}
