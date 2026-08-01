package eu.frigo.dispensa.data;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.media3.common.util.Log;

import java.util.ArrayList;
import java.util.List;

import eu.frigo.dispensa.data.category.CategoryDefinition;
import eu.frigo.dispensa.data.category.CategoryDefinitionDao;
import eu.frigo.dispensa.data.category.ProductCategoryLink;
import eu.frigo.dispensa.data.category.ProductCategoryLinkDao;
import eu.frigo.dispensa.data.category.ProductWithCategoryDefinitions;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.dispensa.DispensaDao;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.product.ProductDao;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItem;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItemDao;
import eu.frigo.dispensa.data.storage.PredefinedData;
import eu.frigo.dispensa.data.storage.StorageLocation;
import eu.frigo.dispensa.data.storage.StorageLocationDao;
import eu.frigo.dispensa.data.sync.SyncOutbox;
import eu.frigo.dispensa.data.sync.SyncOutboxDao;
import eu.frigo.dispensa.sync.core.event.SyncBus;

import com.google.gson.Gson;
import java.util.UUID;

public class Repository {
    public static final String KEY_CURRENT_DISPENSA_ID = "current_dispensa_id";
    
    private static Repository INSTANCE;
    
    private final ProductDao productDao;
    private final CategoryDefinitionDao categoryDefinitionDao;
    private final ProductCategoryLinkDao productCategoryLinkDao;
    private final StorageLocationDao storageLocationDao;
    private final ShoppingItemDao shoppingItemDao;
    private final SyncOutboxDao syncOutboxDao;
    private final DispensaDao dispensaDao;
    private final Gson gson;
    private final SharedPreferences sharedPreferences;

    private final MutableLiveData<Integer> currentDispensaId = new MutableLiveData<>();
    private final LiveData<String> currentDispensaName;
    private final LiveData<List<ProductWithCategoryDefinitions>> allProducts;

    public static Repository getInstance(Application application) {
        if (INSTANCE == null) {
            synchronized (Repository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new Repository(application);
                }
            }
        }
        return INSTANCE;
    }

    public Repository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        productDao = db.productDao();
        categoryDefinitionDao = db.categoryDefinitionDao();
        productCategoryLinkDao = db.productCategoryLinkDao();
        storageLocationDao = db.storageLocationDao();
        shoppingItemDao = db.shoppingItemDao();
        syncOutboxDao = db.syncOutboxDao();
        dispensaDao = db.dispensaDao();
        gson = new Gson();
        sharedPreferences = application.getSharedPreferences("dispensa_prefs", Context.MODE_PRIVATE);

        // Inizializza sempre currentDispensaId con quella di default dal DB all'avvio
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Dispensa def = dispensaDao.getDefaultDispensaSync();
            if (def != null) {
                currentDispensaId.postValue(def.id);
                sharedPreferences.edit().putInt(KEY_CURRENT_DISPENSA_ID, def.id).apply();
            }
        });

        allProducts = Transformations.switchMap(currentDispensaId, id -> 
                productDao.getAllProductsWithFullCategories(id));
        
        currentDispensaName = Transformations.switchMap(currentDispensaId, id -> 
                Transformations.map(dispensaDao.getDispensaById(id), d -> d != null ? d.getName() : "Dispensa"));
    }

    public void setCurrentDispensaId(int id) {
        currentDispensaId.postValue(id);
        sharedPreferences.edit().putInt(KEY_CURRENT_DISPENSA_ID, id).apply();
    }

    public LiveData<Integer> getCurrentDispensaId() {
        return currentDispensaId;
    }

    public LiveData<String> getCurrentDispensaName() {
        return currentDispensaName;
    }

    public io.reactivex.rxjava3.core.Single<String> getCurrentDispensaNameSingle() {
        return io.reactivex.rxjava3.core.Single.fromCallable(() -> {
            Integer id = currentDispensaId.getValue();
            if (id != null) {
                Dispensa d = dispensaDao.getDispensaByIdSync(id);
                if (d != null) {
                    return d.getName();
                }
            }
            return "Dispensa";
        });
    }

    public LiveData<List<Dispensa>> getAllDispense() {
        return dispensaDao.getAllDispense();
    }

    public void insertDispensa(Dispensa dispensa, boolean setAsCurrent) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            insertDispensaSync(dispensa, setAsCurrent);
        });
    }

    public long insertDispensaSync(Dispensa dispensa, boolean setAsCurrent) {
        dispensa.lastModified = System.currentTimeMillis();
        long id = dispensaDao.insert(dispensa);
        // Quando crei una dispensa, crea le locazioni predefinite
        storageLocationDao.insertAll(PredefinedData.getInitialStorageLocations((int) id));
        recordSyncEvent("UPSERT_DISPENSA", dispensa);
        if (setAsCurrent) {
            setCurrentDispensaId((int) id);
        }
        return id;
    }

    public void updateDispensa(Dispensa dispensa) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            dispensa.lastModified = System.currentTimeMillis();
            dispensaDao.update(dispensa);
            recordSyncEvent("UPSERT_DISPENSA", dispensa);
        });
    }

    public void deleteDispensa(Dispensa dispensa) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // Qui dovresti decidere se cancellare tutto il contenuto della dispensa
            // o impedire la cancellazione se non è vuota.
            // Per ora cancelliamo la dispensa e i suoi dati associati (andrebbe fatto a cascata nel DB idealmente)
            dispensaDao.delete(dispensa);
            // Se cancelliamo la corrente, torniamo a quella di default
            if (currentDispensaId.getValue() != null && currentDispensaId.getValue() == dispensa.id) {
                Dispensa def = dispensaDao.getDefaultDispensaSync();
                if (def != null) {
                    setCurrentDispensaId(def.id);
                }
            }
            recordSyncEvent("DELETE_DISPENSA", dispensa);
        });
    }

    public void setDispensaAsDefault(int id) {
        AppDatabase.databaseWriteExecutor.execute(() -> dispensaDao.setAsDefault(id));
    }

    private void recordSyncEvent(String action, Object payload) {
        SyncOutbox entry = new SyncOutbox();
        entry.syncId = UUID.randomUUID().toString();
        Integer dispId = currentDispensaId.getValue();
        entry.dispensaId = dispId != null ? dispId : 0;
        entry.dataType = action;
        entry.payload = gson.toJson(payload);
        entry.timestamp = System.currentTimeMillis();
        syncOutboxDao.insert(entry);
        
        Log.d("SyncFlow", "Event creato: " + action + " [ID: " + entry.syncId + "]");
        
        // Signal that a local change happened
        SyncBus.getInstance().post(new SyncBus.LocalChangeDetected());
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
            Integer dispId = currentDispensaId.getValue();
            product.dispensaId = dispId != null ? dispId : 0;
            product.lastModified = System.currentTimeMillis();
            productDao.insert(product);
            recordSyncEvent("UPSERT_PRODUCT", product);
        });
    }

    public void delete(Product selectedProduct) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            deleteLocalImageIfAny(selectedProduct.getImageUrl());
            selectedProduct.lastModified = System.currentTimeMillis();
            productDao.delete(selectedProduct);
            recordSyncEvent("DELETE_PRODUCT", selectedProduct);
        });
    }

    public void update(Product product) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Integer dispId = currentDispensaId.getValue();
            product.dispensaId = dispId != null ? dispId : 0;
            product.lastModified = System.currentTimeMillis();
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
        return Transformations.switchMap(currentDispensaId, id -> 
                productDao.getProductWithFullCategoriesByLocation(storageLocation, id));
    }
    public void insertProductWithApiTags(Product product, List<String> apiTags) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Integer dispId = currentDispensaId.getValue();
            product.dispensaId = dispId != null ? dispId : 0;
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
            recordSyncEvent("UPSERT_PRODUCT", product);
        });
    }

    public void updateProductWithApiTags(Product product, List<String> apiTags) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Integer dispId = currentDispensaId.getValue();
            product.dispensaId = dispId != null ? dispId : 0;
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
            recordSyncEvent("UPSERT_PRODUCT", product);
        });
    }
    public LiveData<List<StorageLocation>> getAllLocationsSorted() {
        return Transformations.switchMap(currentDispensaId, id -> 
                storageLocationDao.getAllLocationsSorted(id));
    }

    public LiveData<StorageLocation> getDefaultLocation() {
        return Transformations.switchMap(currentDispensaId, id -> 
                storageLocationDao.getDefaultLocation(id));
    }

    public void insertLocation(StorageLocation location) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Integer dispId = currentDispensaId.getValue();
            location.dispensaId = dispId != null ? dispId : 0;
            location.lastModified = System.currentTimeMillis();
            storageLocationDao.insert(location);
            recordSyncEvent("UPSERT_LOCATION", location);
        });
    }

    public void updateLocation(StorageLocation location) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Integer dispId = currentDispensaId.getValue();
            location.dispensaId = dispId != null ? dispId : 0;
            location.lastModified = System.currentTimeMillis();
            storageLocationDao.update(location);
            recordSyncEvent("UPSERT_LOCATION", location);
        });
    }
    public void deleteLocation(StorageLocation location) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Integer dispId = currentDispensaId.getValue();
            int currentId = dispId != null ? dispId : 0;
            location.lastModified = System.currentTimeMillis();
            StorageLocation defaultLoc = storageLocationDao.getDefaultLocationSync(currentId);
            String fallbackKey = defaultLoc != null ? defaultLoc.internalKey : eu.frigo.dispensa.data.storage.PredefinedData.LOCATION_ALL;
            productDao.updateProductLocation(location.internalKey, fallbackKey, currentId);
            storageLocationDao.delete(location);
            recordSyncEvent("DELETE_LOCATION", location);
        });
    }

    public void setLocationAsDefault(String internalKey) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Integer dispId = currentDispensaId.getValue();
            if (dispId != null) {
                storageLocationDao.setAsDefault(internalKey, dispId);
            }
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
        return Transformations.switchMap(currentDispensaId, id -> 
                productDao.getProductWithFullCategoriesByLocationInternalKey(locationInternalKeyFilter, id));
    }
    public LiveData<List<StorageLocation>> getAllSelectableLocations() {
        return Transformations.switchMap(currentDispensaId, id -> 
                storageLocationDao.getAllLocationsSorted(id));
    }

    public static void cleanOrphanImages(android.content.Context context, java.util.function.Consumer<Integer> onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            java.io.File imagesDir = new java.io.File(context.getExternalFilesDir(null), "product_images");
            
            AppDatabase db = AppDatabase.getDatabase(context);
            java.util.List<eu.frigo.dispensa.data.product.Product> products = 
                    db.productDao().getAllProductsListStatic();
            
            java.util.Set<String> validPaths = new java.util.HashSet<>();
            int countDbCleaned = 0;

            for (eu.frigo.dispensa.data.product.Product p : products) {
                if (p.getImageUrl() != null && p.getImageUrl().startsWith("file://")) {
                    String path = android.net.Uri.parse(p.getImageUrl()).getPath();
                    if (path != null) {
                        java.io.File file = new java.io.File(path);
                        if (file.exists()) {
                            validPaths.add(file.getAbsolutePath());
                        } else {
                            // Riferimento nel DB presente ma file rimosso dal disco
                            p.setImageUrl(null);
                            p.lastModified = System.currentTimeMillis();
                            db.productDao().update(p);
                            countDbCleaned++;
                        }
                    }
                }
            }

            int countFilesDeleted = 0;
            if (imagesDir.exists()) {
                java.io.File[] files = imagesDir.listFiles();
                if (files != null) {
                    for (java.io.File file : files) {
                        if (!validPaths.contains(file.getAbsolutePath())) {
                            if (file.delete()) countFilesDeleted++;
                        }
                    }
                }
            }
            
            if (onComplete != null) {
                onComplete.accept(countFilesDeleted + countDbCleaned);
            }
        });
    }

    // ---- Shopping List ----

    public LiveData<List<ShoppingItem>> getAllShoppingItems() {
        return Transformations.switchMap(currentDispensaId, id -> 
                shoppingItemDao.getAllItems(id));
    }

    public LiveData<Integer> getUncheckedShoppingCount() {
        return Transformations.switchMap(currentDispensaId, id -> 
                shoppingItemDao.getUncheckedCount(id));
    }

    public LiveData<List<String>> getAllShoppingItemNames() {
        return Transformations.switchMap(currentDispensaId, id -> 
                shoppingItemDao.getAllItemNames(id));
    }

    public void addToShoppingList(String productName) {
        addToShoppingList(productName, 1);
    }

    public void addToShoppingList(String productName, int quantity) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Integer dispId = currentDispensaId.getValue();
            int currentId = dispId != null ? dispId : 0;
            ShoppingItem existing = shoppingItemDao.getItemByNameSync(productName, currentId);
            long now = System.currentTimeMillis();
            if (existing != null) {
                existing.lastModified = now;
                existing.setQuantity(existing.getQuantity() + quantity);
                shoppingItemDao.update(existing);
                recordSyncEvent("UPSERT_SHOPPING_ITEM", existing);
            } else {
                ShoppingItem newItem = new ShoppingItem(productName, quantity, false);
                newItem.dispensaId = currentId;
                newItem.lastModified = now;
                shoppingItemDao.insert(newItem);
                recordSyncEvent("UPSERT_SHOPPING_ITEM", newItem);
            }
        });
    }

    public void removeFromShoppingList(String productName) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Integer dispId = currentDispensaId.getValue();
            int currentId = dispId != null ? dispId : 0;
            ShoppingItem item = shoppingItemDao.getItemByNameSync(productName, currentId);
            if (item != null) {
                item.lastModified = System.currentTimeMillis();
                shoppingItemDao.deleteByName(productName, currentId);
                recordSyncEvent("DELETE_SHOPPING_ITEM", item);
            }
        });
    }

    public void updateShoppingItem(ShoppingItem item) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Integer dispId = currentDispensaId.getValue();
            item.dispensaId = dispId != null ? dispId : 0;
            item.lastModified = System.currentTimeMillis();
            shoppingItemDao.update(item);
            recordSyncEvent("UPSERT_SHOPPING_ITEM", item);
        });
    }

    public void deleteShoppingItem(ShoppingItem item) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            item.lastModified = System.currentTimeMillis();
            shoppingItemDao.delete(item);
            recordSyncEvent("DELETE_SHOPPING_ITEM", item);
        });
    }

    public void clearCheckedShoppingItems() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Integer dispId = currentDispensaId.getValue();
            int currentId = dispId != null ? dispId : 0;
            List<ShoppingItem> checked = shoppingItemDao.getCheckedItemsSync(currentId);
            if (checked != null) {
                long now = System.currentTimeMillis();
                for (ShoppingItem item : checked) {
                    item.lastModified = now;
                    recordSyncEvent("DELETE_SHOPPING_ITEM", item);
                }
            }
            shoppingItemDao.deleteChecked(currentId);
        });
    }
}
