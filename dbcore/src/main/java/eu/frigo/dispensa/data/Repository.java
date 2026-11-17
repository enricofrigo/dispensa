package eu.frigo.dispensa.data;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

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
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.data.sync.JoinedPantryConfigDao;
import eu.frigo.dispensa.data.sync.SyncOutbox;
import eu.frigo.dispensa.data.sync.SyncOutboxDao;
import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.event.SyncBus;

import com.google.gson.Gson;
import java.util.UUID;

public class Repository {
    public static final String KEY_CURRENT_DISPENSA_ID = "current_dispensa_id";
    
    private static Repository INSTANCE;
    
    private final AppDatabase db;
    private final ProductDao productDao;
    private final CategoryDefinitionDao categoryDefinitionDao;
    private final ProductCategoryLinkDao productCategoryLinkDao;
    private final StorageLocationDao storageLocationDao;
    private final ShoppingItemDao shoppingItemDao;
    private final SyncOutboxDao syncOutboxDao;
    private final DispensaDao dispensaDao;
    private final JoinedPantryConfigDao joinedPantryConfigDao;
    private final Gson gson;
    private final SharedPreferences sharedPreferences;
    private final Application application;

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
        this.db = AppDatabase.getDatabase(application);
        productDao = db.productDao();
        categoryDefinitionDao = db.categoryDefinitionDao();
        productCategoryLinkDao = db.productCategoryLinkDao();
        storageLocationDao = db.storageLocationDao();
        shoppingItemDao = db.shoppingItemDao();
        syncOutboxDao = db.syncOutboxDao();
        dispensaDao = db.dispensaDao();
        joinedPantryConfigDao = db.joinedPantryConfigDao();
        gson = new Gson();
        this.application = application;
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

    public LiveData<Dispensa> getCurrentDispensa() {
        return Transformations.switchMap(currentDispensaId, dispensaDao::getDispensaById);
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

    public io.reactivex.rxjava3.core.Single<Dispensa> getCurrentDispensaSingle() {
        return io.reactivex.rxjava3.core.Single.fromCallable(() -> {
            Integer id = currentDispensaId.getValue();
            if (id != null) {
                return dispensaDao.getDispensaByIdSync(id);
            }
            return dispensaDao.getDefaultDispensaSync();
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
        if (dispensa.remoteId == null || dispensa.remoteId.trim().isEmpty()) {
            dispensa.remoteId = UUID.randomUUID().toString();
        }
        if (dispensa.deviceOwnerId == null) {
            dispensa.deviceOwnerId = InstallationIdProvider.getOrCreateInstallationId(application);
        }
        long id = dispensaDao.insert(dispensa);
        // Quando crei una dispensa, crea le locazioni predefinite
        storageLocationDao.insertAll(PredefinedData.getInitialStorageLocations((int) id));
        if (setAsCurrent) {
            setCurrentDispensaId((int) id);
        }
        return id;
    }

    public void updateDispensa(Dispensa dispensa) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            dispensa.lastModified = System.currentTimeMillis();
            dispensaDao.update(dispensa);
        });
    }

    public void deleteDispensa(Dispensa dispensa) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.runInTransaction(() -> {
                // Cancelliamo la dispensa e tutti i dati associati (inclusi link categorie e lista della spesa)
                productCategoryLinkDao.deleteByDispensaId(dispensa.id);
                productDao.deleteAllProducts(dispensa.id);
                storageLocationDao.deleteAllLocations(dispensa.id);
                shoppingItemDao.deleteAllItems(dispensa.id);
                syncOutboxDao.deleteAllByDispensaId(dispensa.id);
                joinedPantryConfigDao.deleteByDispensaId(dispensa.id);
                dispensaDao.delete(dispensa);

                // Se cancelliamo la corrente, torniamo a quella di default o alla prima disponibile
                Integer curId = currentDispensaId.getValue();
                int prefId = sharedPreferences.getInt(KEY_CURRENT_DISPENSA_ID, 0);
                if ((curId != null && curId == dispensa.id) || prefId == dispensa.id) {
                    Dispensa next = dispensaDao.getDefaultDispensaSync();
                    if (next == null) {
                        next = dispensaDao.getFirstAvailableDispensaSync();
                    }
                    if (next != null) {
                        setCurrentDispensaId(next.id);
                    }
                }
            });
        });
    }

    public void setDispensaAsDefault(int id) {
        AppDatabase.databaseWriteExecutor.execute(() -> dispensaDao.setAsDefault(id));
    }

    public void insertJoinedPantryConfig(JoinedPantryConfig config) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (config != null && config.password != null) {
                config.password = eu.frigo.dispensa.sync.core.crypto.CredentialCipher.encryptIfNeeded(config.password);
            }
            joinedPantryConfigDao.insert(config);
        });
    }

    public JoinedPantryConfig getJoinedPantryConfigSync(int dispensaId) {
        return joinedPantryConfigDao.getConfigByDispensaId(dispensaId);
    }

    public LiveData<List<JoinedPantryConfig>> getAllJoinedPantryConfigs() {
        return joinedPantryConfigDao.getAllConfigsLive();
    }

    public LiveData<JoinedPantryConfig> getJoinedPantryConfig(int dispensaId) {
        return joinedPantryConfigDao.getConfigByDispensaIdLive(dispensaId);
    }

    public void deleteJoinedPantryConfig(JoinedPantryConfig config) {
        AppDatabase.databaseWriteExecutor.execute(() -> joinedPantryConfigDao.delete(config));
    }

    public void deleteJoinedPantryConfigByDispensaId(int dispensaId) {
        AppDatabase.databaseWriteExecutor.execute(() -> joinedPantryConfigDao.deleteByDispensaId(dispensaId));
    }

    public void cleanOrphanData() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.runInTransaction(() -> {
                Log.d("Repository", "Avvio pulizia dati orfani...");
                productDao.deleteOrphans();
                storageLocationDao.deleteOrphans();
                shoppingItemDao.deleteOrphans();
                syncOutboxDao.deleteOrphans();
                joinedPantryConfigDao.deleteOrphans();
                Log.d("Repository", "Pulizia dati orfani completata.");
            });
        });
    }

    private void recordSyncEvent(String action, Object payload) {
        SyncOutbox entry = new SyncOutbox();
        entry.syncId = UUID.randomUUID().toString();
        entry.dispensaId = resolveEffectiveDispensaId();
        entry.dataType = action;
        Object payloadToSerialize = payload;
        if (payload instanceof Product) {
            Product p = (Product) payload;
            if (p.hasCustomLocalImage()) {
                payloadToSerialize = p.createExportCopy();
            }
        }
        entry.payload = gson.toJson(payloadToSerialize);
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

    private int resolveEffectiveDispensaId() {
        Integer dispId = currentDispensaId.getValue();
        if (dispId != null && dispId > 0) {
            return dispId;
        }
        int savedPrefId = sharedPreferences.getInt(KEY_CURRENT_DISPENSA_ID, 0);
        if (savedPrefId > 0) {
            return savedPrefId;
        }
        Dispensa def = dispensaDao.getDefaultDispensaSync();
        return def != null ? def.id : 1;
    }

    private List<String> getCategoryTagsForProductSync(int productId) {
        List<String> tags = new ArrayList<>();
        if (productId <= 0) return tags;
        List<ProductCategoryLink> allLinks = productCategoryLinkDao.getAllProductCategoryLinksSync();
        if (allLinks != null) {
            for (ProductCategoryLink link : allLinks) {
                if (link.productIdFk == productId) {
                    CategoryDefinition def = categoryDefinitionDao.getCategoryDefinitionById(link.categoryIdFk);
                    if (def != null && def.tagName != null && !def.tagName.trim().isEmpty()) {
                        tags.add(def.tagName.trim());
                    }
                }
            }
        }
        return tags;
    }

    public void insert(Product product) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.runInTransaction(() -> {
                product.dispensaId = resolveEffectiveDispensaId();
                if (product.syncUuid == null || product.syncUuid.trim().isEmpty()) {
                    product.syncUuid = UUID.randomUUID().toString();
                }
                product.lastModified = System.currentTimeMillis();
                long id = productDao.insert(product);
                product.setId((int) id);
                recordSyncEvent("UPSERT_PRODUCT", product);
            });
        });
    }

    public void delete(Product selectedProduct) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            deleteLocalImageIfAny(selectedProduct.getImageUrl());
            db.runInTransaction(() -> {
                selectedProduct.lastModified = System.currentTimeMillis();
                productDao.delete(selectedProduct);
                recordSyncEvent("DELETE_PRODUCT", selectedProduct);
            });
        });
    }

    public void update(Product product) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.runInTransaction(() -> {
                product.dispensaId = product.dispensaId > 0 ? product.dispensaId : resolveEffectiveDispensaId();
                product.lastModified = System.currentTimeMillis();
                Product oldProduct = productDao.getProductByIdSync(product.getId());
                if (oldProduct != null) {
                    if (product.syncUuid == null || product.syncUuid.trim().isEmpty()) {
                        product.syncUuid = oldProduct.syncUuid != null ? oldProduct.syncUuid : UUID.randomUUID().toString();
                    }
                    if (oldProduct.getImageUrl() != null && !oldProduct.getImageUrl().equals(product.getImageUrl())) {
                        deleteLocalImageIfAny(oldProduct.getImageUrl());
                    }
                } else if (product.syncUuid == null || product.syncUuid.trim().isEmpty()) {
                    product.syncUuid = UUID.randomUUID().toString();
                }
                productDao.update(product);
                product.categoryTags = getCategoryTagsForProductSync(product.getId());
                recordSyncEvent("UPSERT_PRODUCT", product);
            });
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
            db.runInTransaction(() -> {
                product.dispensaId = resolveEffectiveDispensaId();
                if (product.syncUuid == null || product.syncUuid.trim().isEmpty()) {
                    product.syncUuid = UUID.randomUUID().toString();
                }
                product.lastModified = System.currentTimeMillis();
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
                    product.categoryTags = new ArrayList<>(apiTags);
                } else {
                    product.categoryTags = new ArrayList<>();
                }
                recordSyncEvent("UPSERT_PRODUCT", product);
            });
        });
    }

    public void updateProductWithApiTags(Product product, List<String> apiTags) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.runInTransaction(() -> {
                product.dispensaId = product.dispensaId > 0 ? product.dispensaId : resolveEffectiveDispensaId();
                product.lastModified = System.currentTimeMillis();
                Product oldProduct = productDao.getProductByIdSync(product.getId());
                if (oldProduct != null) {
                    if (product.syncUuid == null || product.syncUuid.trim().isEmpty()) {
                        product.syncUuid = oldProduct.syncUuid != null ? oldProduct.syncUuid : UUID.randomUUID().toString();
                    }
                    if (oldProduct.getImageUrl() != null && !oldProduct.getImageUrl().equals(product.getImageUrl())) {
                        deleteLocalImageIfAny(oldProduct.getImageUrl());
                    }
                } else if (product.syncUuid == null || product.syncUuid.trim().isEmpty()) {
                    product.syncUuid = UUID.randomUUID().toString();
                }

                productDao.update(product);
                int productId = product.getId();

                productCategoryLinkDao.deleteByProductId(productId);
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
                    product.categoryTags = new ArrayList<>(apiTags);
                } else {
                    product.categoryTags = new ArrayList<>();
                }
                recordSyncEvent("UPSERT_PRODUCT", product);
            });
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
            db.runInTransaction(() -> {
                int currentId = resolveEffectiveDispensaId();
                location.dispensaId = currentId;
                location.lastModified = System.currentTimeMillis();

                if (location.orderIndex <= 0) {
                    Integer maxIndex = storageLocationDao.getMaxOrderIndex(currentId);
                    location.orderIndex = (maxIndex != null ? maxIndex : -1) + 1;
                }

                storageLocationDao.insert(location);
                recordSyncEvent("UPSERT_LOCATION", location);
            });
        });
    }

    public void updateLocation(StorageLocation location) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.runInTransaction(() -> {
                location.dispensaId = location.dispensaId > 0 ? location.dispensaId : resolveEffectiveDispensaId();
                location.lastModified = System.currentTimeMillis();
                storageLocationDao.update(location);
                recordSyncEvent("UPSERT_LOCATION", location);
            });
        });
    }
    public void deleteLocation(StorageLocation location) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.runInTransaction(() -> {
                int currentId = resolveEffectiveDispensaId();
                long now = System.currentTimeMillis();
                location.lastModified = now;
                StorageLocation defaultLoc = storageLocationDao.getDefaultLocationSync(currentId);
                String fallbackKey = defaultLoc != null ? defaultLoc.internalKey : eu.frigo.dispensa.data.storage.PredefinedData.LOCATION_ALL;

                List<Product> affectedProducts = productDao.getProductsByLocationSync(location.internalKey, currentId);
                productDao.updateProductLocation(location.internalKey, fallbackKey, currentId);
                if (affectedProducts != null) {
                    for (Product moved : affectedProducts) {
                        moved.setStorageLocation(fallbackKey);
                        moved.lastModified = now;
                        if (moved.syncUuid == null || moved.syncUuid.trim().isEmpty()) {
                            moved.syncUuid = UUID.randomUUID().toString();
                        }
                        productDao.update(moved);
                        moved.categoryTags = getCategoryTagsForProductSync(moved.getId());
                        recordSyncEvent("UPSERT_PRODUCT", moved);
                    }
                }
                storageLocationDao.delete(location);
                recordSyncEvent("DELETE_LOCATION", location);
            });
        });
    }

    public void setLocationAsDefault(String internalKey) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            int dispId = resolveEffectiveDispensaId();
            storageLocationDao.setAsDefault(internalKey, dispId);
        });
    }

    public void updateLocationOrder(List<StorageLocation> orderedLocations) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.runInTransaction(() -> {
                long now = System.currentTimeMillis();
                for (int i = 0; i < orderedLocations.size(); i++) {
                    StorageLocation loc = orderedLocations.get(i);
                    loc.orderIndex = i;
                    loc.lastModified = now;
                    storageLocationDao.update(loc);
                    recordSyncEvent("UPSERT_LOCATION", loc);
                }
            });
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
        final android.content.Context appContext = context.getApplicationContext();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            java.io.File imagesDir = new java.io.File(appContext.getExternalFilesDir(null), "product_images");
            AppDatabase db = AppDatabase.getDatabase(appContext);
            cleanOrphanImages(db, imagesDir, appContext.getContentResolver(), onComplete);
        });
    }

    public static void cleanOrphanImages(AppDatabase db, java.io.File imagesDir, android.content.ContentResolver contentResolver, java.util.function.Consumer<Integer> onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            java.util.List<eu.frigo.dispensa.data.product.Product> products = 
                    db.productDao().getAllProductsListStatic();
            
            java.util.Set<String> validPaths = new java.util.HashSet<>();
            int countDbCleaned = 0;

            for (eu.frigo.dispensa.data.product.Product p : products) {
                if (p.getImageUrl() != null && p.hasCustomLocalImage()) {
                    String imgUrl = p.getImageUrl();
                    boolean exists = false;
                    try {
                        String filePath = null;
                        if (imgUrl.startsWith("file://")) {
                            filePath = android.net.Uri.parse(imgUrl).getPath();
                        } else if (imgUrl.startsWith("/")) {
                            filePath = imgUrl;
                        } else if (imgUrl.startsWith("content://") && contentResolver != null) {
                            try (java.io.InputStream is = contentResolver.openInputStream(android.net.Uri.parse(imgUrl))) {
                                if (is != null) exists = true;
                            } catch (Exception ignored) {}
                        }

                        if (filePath != null) {
                            java.io.File file = new java.io.File(filePath);
                            if (file.exists()) {
                                validPaths.add(file.getAbsolutePath());
                                exists = true;
                            }
                        }
                    } catch (Exception ignored) {}

                    if (!exists) {
                        // Riferimento nel DB presente ma file inesistente sul disco: resetta a default (null)
                        p.setImageUrl(null);
                        p.lastModified = System.currentTimeMillis();
                        db.productDao().update(p);
                        countDbCleaned++;
                    }
                }
            }

            int countFilesDeleted = 0;
            if (imagesDir != null && imagesDir.exists()) {
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
            db.runInTransaction(() -> {
                int currentId = resolveEffectiveDispensaId();
                ShoppingItem existing = shoppingItemDao.getItemByNameSync(productName, currentId);
                long now = System.currentTimeMillis();
                if (existing != null) {
                    if (existing.syncUuid == null || existing.syncUuid.trim().isEmpty()) {
                        existing.syncUuid = UUID.randomUUID().toString();
                    }
                    existing.lastModified = now;
                    existing.setQuantity(existing.getQuantity() + quantity);
                    shoppingItemDao.update(existing);
                    recordSyncEvent("UPSERT_SHOPPING_ITEM", existing);
                } else {
                    ShoppingItem newItem = new ShoppingItem(productName, quantity, false);
                    newItem.dispensaId = currentId;
                    newItem.lastModified = now;
                    long id = shoppingItemDao.insert(newItem);
                    newItem.setId((int) id);
                    recordSyncEvent("UPSERT_SHOPPING_ITEM", newItem);
                }
            });
        });
    }

    public void removeFromShoppingList(String productName) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.runInTransaction(() -> {
                int currentId = resolveEffectiveDispensaId();
                ShoppingItem item = shoppingItemDao.getItemByNameSync(productName, currentId);
                if (item != null) {
                    item.lastModified = System.currentTimeMillis();
                    shoppingItemDao.deleteByName(productName, currentId);
                    recordSyncEvent("DELETE_SHOPPING_ITEM", item);
                }
            });
        });
    }

    public void updateShoppingItem(ShoppingItem item) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.runInTransaction(() -> {
                item.dispensaId = item.dispensaId > 0 ? item.dispensaId : resolveEffectiveDispensaId();
                if (item.syncUuid == null || item.syncUuid.trim().isEmpty()) {
                    ShoppingItem existing = item.getId() > 0 ? null : shoppingItemDao.getItemByNameSync(item.getName(), item.dispensaId);
                    item.syncUuid = (existing != null && existing.syncUuid != null) ? existing.syncUuid : UUID.randomUUID().toString();
                }
                item.lastModified = System.currentTimeMillis();
                shoppingItemDao.update(item);
                recordSyncEvent("UPSERT_SHOPPING_ITEM", item);
            });
        });
    }

    public void deleteShoppingItem(ShoppingItem item) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.runInTransaction(() -> {
                item.lastModified = System.currentTimeMillis();
                shoppingItemDao.delete(item);
                recordSyncEvent("DELETE_SHOPPING_ITEM", item);
            });
        });
    }

    public void clearCheckedShoppingItems() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.runInTransaction(() -> {
                int currentId = resolveEffectiveDispensaId();
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
        });
    }
}
