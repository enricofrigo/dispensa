package eu.frigo.dispensa.data.product;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import androidx.room.Delete;

import java.util.List;

import eu.frigo.dispensa.data.category.ProductWithCategoryDefinitions;

@Dao
public interface ProductDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Product product);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Product> products);

    @Update
    void update(Product product);

    /**
     * Upserts a Product without triggering ON DELETE CASCADE on product_category_links
     * when updating an existing row (id > 0).
     */
    @Transaction
    default long upsertPreservingLinks(Product product) {
        if (product.syncUuid == null || product.syncUuid.trim().isEmpty()) {
            product.syncUuid = java.util.UUID.randomUUID().toString();
        }
        if (product.id > 0) {
            Product existing = getProductByIdSync(product.id);
            if (existing != null) {
                update(product);
                return product.id;
            }
        }
        return insert(product);
    }

    @Delete
    void delete(Product product);

    @Query("DELETE FROM products")
    void deleteAllProducts();
    @Query("DELETE FROM products WHERE dispensa_id = :dispensaId")
    void deleteAllProducts(int dispensaId);

    @Query("DELETE FROM products WHERE dispensa_id NOT IN (SELECT id FROM dispense)")
    void deleteOrphans();

    @Query("SELECT * FROM products ORDER BY expiry_date ASC")
    List<Product> getAllProductsListStatic();
    @Query("SELECT * FROM products WHERE dispensa_id = :dispensaId ORDER BY expiry_date ASC")
    List<Product> getAllProductsListStatic(int dispensaId);
    @Query("SELECT * FROM products ORDER BY expiry_date ASC")
    LiveData<List<Product>> getAllProducts();

    @Query("SELECT * FROM products WHERE id = :productId")
    LiveData<Product> getProductById(int productId);

    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    Product getProductByIdSync(int productId);

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    Product getProductByBarcode(String barcode);

    @Query("SELECT * FROM products WHERE barcode = :barcode ORDER BY expiry_date ASC")
    List<Product> getProductsByBarcode(String barcode);

    @Query("SELECT * FROM products WHERE barcode IS :barcode AND expiry_date IS :expiryDate AND storage_location IS :storageLocation AND dispensa_id = :dispensaId LIMIT 1")
    Product getProductByLotKeySync(String barcode, Long expiryDate, String storageLocation, int dispensaId);

    @Query("SELECT * FROM products WHERE sync_uuid = :syncUuid AND dispensa_id = :dispensaId LIMIT 1")
    Product getProductBySyncUuidSync(String syncUuid, int dispensaId);

    @Query("SELECT * FROM products WHERE storage_location = :storageLocation AND dispensa_id = :dispensaId")
    List<Product> getProductsByLocationSync(String storageLocation, int dispensaId);

    @Transaction
    @Query("SELECT * FROM products WHERE id = :productId")
    public LiveData<ProductWithCategoryDefinitions> getProductWithFullCategoriesById(int productId);
    @Transaction
    @Query("SELECT * FROM products WHERE storage_location = :storageLocation AND dispensa_id = :dispensaId ORDER BY expiry_date ASC")
    public LiveData<List<ProductWithCategoryDefinitions>> getProductWithFullCategoriesByLocation(String storageLocation, int dispensaId);
    @Transaction
    @Query("SELECT * FROM products WHERE dispensa_id = :dispensaId ORDER BY expiry_date ASC")
    public LiveData<List<ProductWithCategoryDefinitions>> getAllProductsWithFullCategories(int dispensaId);
    @Query("UPDATE products SET storage_location = :defaultLocationInternalKey WHERE storage_location = :deleteLocationInternalKey AND dispensa_id = :dispensaId")
    void updateProductLocation(String deleteLocationInternalKey, String defaultLocationInternalKey, int dispensaId);
    @Transaction
    @Query("SELECT * FROM products WHERE storage_location = :locationInternalKeyFilter AND dispensa_id = :dispensaId ORDER BY expiry_date ASC")
    LiveData<List<ProductWithCategoryDefinitions>> getProductWithFullCategoriesByLocationInternalKey(String locationInternalKeyFilter, int dispensaId);
}
