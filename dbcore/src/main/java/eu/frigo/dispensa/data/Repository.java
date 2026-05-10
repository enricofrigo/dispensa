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
import eu.frigo.dispensa.sync.core.event.SyncBus;

public class Repository {
    private final ProductDao productDao;
    private final CategoryDefinitionDao categoryDefinitionDao;
    private final ProductCategoryLinkDao productCategoryLinkDao;
    private final StorageLocationDao storageLocationDao;
    private final ShoppingItemDao shoppingItemDao;
    private final LiveData<List<ProductWithCategoryDefinitions>> allProducts;

    public Repository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        productDao = db.productDao();
        categoryDefinitionDao = db.categoryDefinitionDao();
        productCategoryLinkDao = db.productCategoryLinkDao();
        storageLocationDao = db.storageLocationDao();
        shoppingItemDao = db.shoppingItemDao();
        allProducts = productDao.getAllProductsWithFullCategories();
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
            product.lastModified = System.currentTimeMillis();
            productDao.insert(product);
            SyncBus.getInstance().post(new SyncBus.LocalChangeDetected());
        });
    }

    public void delete(Product selectedProduct) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            deleteLocalImageIfAny(selectedProduct.getImageUrl());
            selectedProduct.lastModified = System.currentTimeMillis();
            productDao.delete(selectedProduct);
            SyncBus.getInstance().post(new SyncBus.LocalChangeDetected());
        });
    }

    public void update(Product product) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            product.lastModified = System.currentTimeMillis();
            Product oldProduct = productDao.getProductByIdSync(product.getId());
            if (oldProduct != null && oldProduct.getImageUrl() != null 
                    && !oldProduct.getImageUrl().equals(product.getImageUrl())) {
                deleteLocalImageIfAny(oldProduct.getImageUrl());
            }
            productDao.update(product);
            SyncBus.getInstance().post(new SyncBus.LocalChangeDetected());
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
            product.lastModified = System.currentTimeMillis();
            long productIdLong = productDao.insert(product);
            int productId = (int) productIdLong;
            product.setId(productId);
            // ... (tags logic)

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
            SyncBus.getInstance().post(new SyncBus.LocalChangeDetected());
        });
    }

    public void updateProductWithApiTags(Product product, List<String> apiTags) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            product.lastModified = System.currentTimeMillis();
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
            SyncBus.getInstance().post(new SyncBus.LocalChangeDetected());
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
            location.lastModified = System.currentTimeMillis();
            storageLocationDao.insert(location);
            SyncBus.getInstance().post(new SyncBus.LocalChangeDetected());
        });
    }

    public void updateLocation(StorageLocation location) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            location.lastModified = System.currentTimeMillis();
            storageLocationDao.update(location);
            SyncBus.getInstance().post(new SyncBus.LocalChangeDetected());
        });
    }
    public void deleteLocation(StorageLocation location) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            location.lastModified = System.currentTimeMillis();
            StorageLocation defaultLoc = storageLocationDao.getDefaultLocationSync();
            String fallbackKey = defaultLoc != null ? defaultLoc.internalKey : eu.frigo.dispensa.data.storage.PredefinedData.LOCATION_ALL;
            productDao.updateProductLocation(location.internalKey, fallbackKey);
            storageLocationDao.delete(location);
            SyncBus.getInstance().post(new SyncBus.LocalChangeDetected());
        });
    }

    public void setLocationAsDefault(String internalKey) {
        AppDatabase.databaseWriteExecutor.execute(() -> storageLocationDao.setAsDefault(internalKey));
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
            long now = System.currentTimeMillis();
            if (existing != null) {
                existing.lastModified = now;
                existing.setQuantity(existing.getQuantity() + quantity);
                shoppingItemDao.update(existing);
                SyncBus.getInstance().post(new SyncBus.LocalChangeDetected());
            } else {
                ShoppingItem newItem = new ShoppingItem(productName, quantity, false);
                newItem.lastModified = now;
                shoppingItemDao.insert(newItem);
                SyncBus.getInstance().post(new SyncBus.LocalChangeDetected());
            }
        });
    }

    public void removeFromShoppingList(String productName) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            ShoppingItem item = shoppingItemDao.getItemByNameSync(productName);
            if (item != null) {
                item.lastModified = System.currentTimeMillis();
                shoppingItemDao.deleteByName(productName);
                SyncBus.getInstance().post(new SyncBus.LocalChangeDetected());
            }
        });
    }

    public void updateShoppingItem(ShoppingItem item) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            item.lastModified = System.currentTimeMillis();
            shoppingItemDao.update(item);
            SyncBus.getInstance().post(new SyncBus.LocalChangeDetected());
        });
    }

    public void deleteShoppingItem(ShoppingItem item) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            item.lastModified = System.currentTimeMillis();
            shoppingItemDao.delete(item);
            SyncBus.getInstance().post(new SyncBus.LocalChangeDetected());
        });
    }

    public void clearCheckedShoppingItems() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<ShoppingItem> checked = shoppingItemDao.getCheckedItemsSync();
            if (checked != null) {
                long now = System.currentTimeMillis();
                for (ShoppingItem item : checked) {
                    item.lastModified = now;
                    SyncBus.getInstance().post(new SyncBus.LocalChangeDetected());
                }
            }
            shoppingItemDao.deleteChecked();
        });
    }
}
