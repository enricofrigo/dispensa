package eu.frigo.dispensa.data;

import android.app.Application;
import androidx.lifecycle.LiveData;
import androidx.media3.common.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductRepository {
    private final ProductDao productDao;
    private final CategoryDefinitionDao categoryDefinitionDao;
    private final ProductCategoryLinkDao productCategoryLinkDao;
    private final StorageLocationDao storageLocationDao;
    private final LiveData<List<ProductWithCategoryDefinitions>> allProducts;

    public ProductRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        productDao = db.productDao();
        categoryDefinitionDao = db.categoryDefinitionDao();
        productCategoryLinkDao = db.productCategoryLinkDao();
        storageLocationDao = db.storageLocationDao();
        allProducts = productDao.getAllProductsWithFullCategories();
    }

    public LiveData<List<ProductWithCategoryDefinitions>> getAllProducts() {
        return allProducts;
    }

    public void insert(Product product) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            productDao.insert(product);
        });
    }

    public void delete(Product selectedProduct) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            productDao.delete(selectedProduct);
        });
    }

    public void update(Product product) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            productDao.update(product);
        });
    }

    public void triggerDataRefresh() {
        Log.d("ProductRepository", "triggerDataRefresh() chiamato.");
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
        });
    }

    public void updateProductWithApiTags(Product product, List<String> apiTags) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long productIdLong = productDao.insert(product);
            int productId = (int) productIdLong;

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
        });
    }

    public void updateLocation(StorageLocation location) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            storageLocationDao.update(location);
        });
    }
    public void deleteLocation(StorageLocation location) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            productDao.updateProductLocation(location.internalKey, storageLocationDao.getDefaultLocationSync().internalKey);
            storageLocationDao.delete(location);
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
}
