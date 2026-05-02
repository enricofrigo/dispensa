package eu.frigo.dispensa.data;

import android.app.Application;
import androidx.lifecycle.LiveData;
import androidx.media3.common.util.Log;

import java.util.ArrayList;
import java.util.List;

import eu.frigo.dispensa.data.category.CategoryDefinition;
import eu.frigo.dispensa.data.category.CategoryDefinitionDao;
import eu.frigo.dispensa.data.category.ProductCategoryLink;
import eu.frigo.dispensa.data.category.ProductCategoryLinkDao;
import eu.frigo.dispensa.data.category.ProductWithCategoryDefinitions;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.product.ProductDao;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItem;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItemDao;
import eu.frigo.dispensa.data.storage.StorageLocation;
import eu.frigo.dispensa.data.storage.StorageLocationDao;
import eu.frigo.dispensa.data.sync.SyncOutbox;
import eu.frigo.dispensa.data.sync.SyncOutboxDao;
import eu.frigo.dispensa.sync.core.event.SyncBus;

import com.google.gson.Gson;
import java.util.UUID;

public class Repository {
    private final ProductDao productDao;
    private final CategoryDefinitionDao categoryDefinitionDao;
    private final ProductCategoryLinkDao productCategoryLinkDao;
    private final StorageLocationDao storageLocationDao;
    private final ShoppingItemDao shoppingItemDao;
    private final SyncOutboxDao syncOutboxDao;
    private final Gson gson;
    private final LiveData<List<ProductWithCategoryDefinitions>> allProducts;

    public Repository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        productDao = db.productDao();
        categoryDefinitionDao = db.categoryDefinitionDao();
        productCategoryLinkDao = db.productCategoryLinkDao();
        storageLocationDao = db.storageLocationDao();
        shoppingItemDao = db.shoppingItemDao();
        syncOutboxDao = db.syncOutboxDao();
        gson = new Gson();
        allProducts = productDao.getAllProductsWithFullCategories();
    }

    private void recordSyncEvent(String action, Object payload) {
        SyncOutbox entry = new SyncOutbox();
        entry.syncId = UUID.randomUUID().toString();
        entry.dataType = action;
        entry.payload = gson.toJson(payload);
        entry.timestamp = System.currentTimeMillis();
        syncOutboxDao.insert(entry);
        
        Log.d("SyncFlow", "Event creato: " + action + " [ID: " + entry.syncId + "]");
        
        // Signal that a local change happened
        try {
            Class<?> syncBusClass = Class.forName("eu.frigo.dispensa.sync.core.event.SyncBus");
            Object syncBusInstance = syncBusClass.getMethod("getInstance").invoke(null);
            Object eventInstance = Class.forName("eu.frigo.dispensa.sync.core.event.SyncBus$LocalChangeDetected").newInstance();
            syncBusClass.getMethod("post", Class.forName("eu.frigo.dispensa.sync.core.event.SyncEvent")).invoke(syncBusInstance, eventInstance);
        } catch (Exception ignored) {
            // Sync module might not be present or initialized
        }
    }

    public LiveData<List<ProductWithCategoryDefinitions>> getAllProducts() {
        return allProducts;
    }

    private void deleteLocalImageIfAny(String imageUrl) {
        if (imageUrl != null && imageUrl.startsWith("file://")) {
            try {
                String path = android.net.Uri.parse(imageUrl).getPath();
                if (path != null) {
                    java.io.File file = new java.io.File(path);
                    if (file.exists()) {
                        file.delete();
                    }
                }
            } catch (Exception e) {
                Log.e("Repository", "Error deleting image file", e);
            }
        }
    }

    public void insert(Product product) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            productDao.insert(product);
            recordSyncEvent("UPSERT_PRODUCT", product);
        });
    }

    public void delete(Product selectedProduct) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            deleteLocalImageIfAny(selectedProduct.getImageUrl());
            productDao.delete(selectedProduct);
            recordSyncEvent("DELETE_PRODUCT", selectedProduct);
        });
    }

    public void update(Product product) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Product oldProduct = productDao.getProductByIdSync(product.getId());
            if (oldProduct != null && oldProduct.getImageUrl() != null 
                    && !oldProduct.getImageUrl().equals(product.getImageUrl())) {
                deleteLocalImageIfAny(oldProduct.getImageUrl());
            }
            productDao.update(product);
            recordSyncEvent("UPSERT_PRODUCT", product);
        });
    }

    public void triggerDataRefresh() {
        Log.d("ProductRepository", "triggerDataRefresh() chiamato.");
    }

    public List<Product> getProductsByBarcodeSync(String barcode) {
        return productDao.getProductsByBarcode(barcode);
    }

    public LiveData<ProductWithCategoryDefinitions> getProductById(int currentProductId) {
        return productDao.getProductWithFullCategoriesById(currentProductId);
    }
    public LiveData<List<ProductWithCategoryDefinitions>> getProductByStorageLocation(String storageLocation) {
        return productDao.getProductWithFullCategoriesByLocation(storageLocation);
    }
    public void insertProductWithApiTags(Product product, List<String> apiTags) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long productIdLong = productDao.insert(product);
            int productId = (int) productIdLong;
            product.setId(productId);

            if (apiTags != null && !apiTags.isEmpty()) {
                List<ProductCategoryLink> linksToInsert = new ArrayList<>();
                for (String apiTag : apiTags) {
                    CategoryDefinition existingCategory = categoryDefinitionDao.getCategoryByTagName(apiTag);
                    int categoryId;
                    if (existingCategory == null) {
                        CategoryDefinition newCategoryDef = new CategoryDefinition(apiTag);
                        newCategoryDef.setLanguageCode(apiTag.split(":")[0]);
                        long newCategoryIdLong = categoryDefinitionDao.insert(newCategoryDef);
                        categoryId = (int) newCategoryIdLong;
                    } else {
                        categoryId = existingCategory.categoryId;
                    }
                    linksToInsert.add(new ProductCategoryLink(productId, categoryId));
                }
                if (!linksToInsert.isEmpty()) {
                    productCategoryLinkDao.insertAll(linksToInsert);
                }
            }
            recordSyncEvent("UPSERT_PRODUCT", product);
        });
    }

    public void updateProductWithApiTags(Product product, List<String> apiTags) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Product oldProduct = productDao.getProductByIdSync(product.getId());
            if (oldProduct != null && oldProduct.getImageUrl() != null 
                    && !oldProduct.getImageUrl().equals(product.getImageUrl())) {
                deleteLocalImageIfAny(oldProduct.getImageUrl());
            }
            
            productDao.update(product);
            int productId = product.getId();

            if (apiTags != null && !apiTags.isEmpty()) {
                List<ProductCategoryLink> linksToInsert = new ArrayList<>();
                for (String apiTag : apiTags) {
                    CategoryDefinition existingCategory = categoryDefinitionDao.getCategoryByTagName(apiTag);
                    int categoryId;
                    if (existingCategory == null) {
                        CategoryDefinition newCategoryDef = new CategoryDefinition(apiTag);
                        newCategoryDef.setLanguageCode(apiTag.split(":")[0]);
                        long newCategoryIdLong = categoryDefinitionDao.insert(newCategoryDef);
                        categoryId = (int) newCategoryIdLong;
                    } else {
                        categoryId = existingCategory.categoryId;
                    }
                    linksToInsert.add(new ProductCategoryLink(productId, categoryId));
                }
                productCategoryLinkDao.deleteByProductId(productId);
                if (!linksToInsert.isEmpty()) {
                    productCategoryLinkDao.insertAll(linksToInsert);
                }
            }
            recordSyncEvent("UPSERT_PRODUCT", product);
        });
    }
    public LiveData<List<StorageLocation>> getAllLocationsSorted() {
        return storageLocationDao.getAllLocationsSorted();
    }

    public LiveData<StorageLocation> getDefaultLocation() {
        return storageLocationDao.getDefaultLocation();
    }

    public void insertLocation(StorageLocation location) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            storageLocationDao.insert(location);
            recordSyncEvent("UPSERT_LOCATION", location);
        });
    }

    public void updateLocation(StorageLocation location) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            storageLocationDao.update(location);
            recordSyncEvent("UPSERT_LOCATION", location);
        });
    }
    public void deleteLocation(StorageLocation location) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            StorageLocation defaultLoc = storageLocationDao.getDefaultLocationSync();
            String fallbackKey = defaultLoc != null ? defaultLoc.internalKey : eu.frigo.dispensa.data.storage.PredefinedData.LOCATION_ALL;
            productDao.updateProductLocation(location.internalKey, fallbackKey);
            storageLocationDao.delete(location);
            recordSyncEvent("DELETE_LOCATION", location);
        });
    }

    public void setLocationAsDefault(String internalKey) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            storageLocationDao.setAsDefault(internalKey);
        });
    }

    public void updateLocationOrder(List<StorageLocation> orderedLocations) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            for (int i = 0; i < orderedLocations.size(); i++) {
                StorageLocation loc = orderedLocations.get(i);
                loc.orderIndex = i;
                storageLocationDao.update(loc);
            }
        });
    }

    public LiveData<List<ProductWithCategoryDefinitions>> getProductsByLocationInternalKey(String locationInternalKeyFilter) {
        return productDao.getProductWithFullCategoriesByLocationInternalKey(locationInternalKeyFilter);
    }
    public LiveData<List<StorageLocation>> getAllSelectableLocations() {
        return storageLocationDao.getAllLocationsSorted();
    }

    public static void cleanOrphanImages(android.content.Context context, java.util.function.Consumer<Integer> onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            java.io.File imagesDir = new java.io.File(context.getExternalFilesDir(null), "product_images");
            if (!imagesDir.exists()) {
                if (onComplete != null) onComplete.accept(0);
                return;
            }

            java.util.List<eu.frigo.dispensa.data.product.Product> products = 
                    AppDatabase.getDatabase(context).productDao().getAllProductsListStatic();
            
            java.util.Set<String> validPaths = new java.util.HashSet<>();
            for (eu.frigo.dispensa.data.product.Product p : products) {
                if (p.getImageUrl() != null && p.getImageUrl().startsWith("file://")) {
                    String path = android.net.Uri.parse(p.getImageUrl()).getPath();
                    if (path != null) validPaths.add(path);
                }
            }

            int countDeleted = 0;
            java.io.File[] files = imagesDir.listFiles();
            if (files != null) {
                for (java.io.File file : files) {
                    if (!validPaths.contains(file.getAbsolutePath())) {
                        if (file.delete()) countDeleted++;
                    }
                }
            }
            
            if (onComplete != null) {
                onComplete.accept(countDeleted);
            }
        });
    }

    // ---- Shopping List ----

    public LiveData<List<ShoppingItem>> getAllShoppingItems() {
        return shoppingItemDao.getAllItems();
    }

    public LiveData<Integer> getUncheckedShoppingCount() {
        return shoppingItemDao.getUncheckedCount();
    }

    public LiveData<List<String>> getAllShoppingItemNames() {
        return shoppingItemDao.getAllItemNames();
    }

    public void addToShoppingList(String productName) {
        addToShoppingList(productName, 1);
    }

    public void addToShoppingList(String productName, int quantity) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            ShoppingItem existing = shoppingItemDao.getItemByNameSync(productName);
            if (existing != null) {
                existing.setQuantity(existing.getQuantity() + quantity);
                shoppingItemDao.update(existing);
                recordSyncEvent("UPSERT_SHOPPING_ITEM", existing);
            } else {
                ShoppingItem newItem = new ShoppingItem(productName, quantity, false);
                shoppingItemDao.insert(newItem);
                recordSyncEvent("UPSERT_SHOPPING_ITEM", newItem);
            }
        });
    }

    public void removeFromShoppingList(String productName) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            shoppingItemDao.deleteByName(productName);
        });
    }

    public void updateShoppingItem(ShoppingItem item) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            shoppingItemDao.update(item);
            recordSyncEvent("UPSERT_SHOPPING_ITEM", item);
        });
    }

    public void deleteShoppingItem(ShoppingItem item) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            shoppingItemDao.delete(item);
            recordSyncEvent("DELETE_SHOPPING_ITEM", item);
        });
    }

    public void clearCheckedShoppingItems() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            shoppingItemDao.deleteChecked();
        });
    }
}
