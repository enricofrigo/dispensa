package eu.frigo.dispensa.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import androidx.room.Delete;

import java.util.List;

@Dao
public interface ProductDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Product product);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Product> products);

    @Update
    void update(Product product);

    @Delete
    void delete(Product product);

    @Query("DELETE FROM products")
    void deleteAllProducts();
    @Query("SELECT * FROM products ORDER BY expiry_date ASC")
    List<Product> getAllProductsListStatic();
    @Query("SELECT * FROM products ORDER BY expiry_date ASC")
    LiveData<List<Product>> getAllProducts();

    @Query("SELECT * FROM products WHERE id = :productId")
    LiveData<Product> getProductById(int productId);

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    Product getProductByBarcode(String barcode);

    @Transaction
    @Query("SELECT * FROM products WHERE id = :productId")
    public LiveData<ProductWithCategoryDefinitions> getProductWithFullCategoriesById(int productId);
    @Transaction
    @Query("SELECT * FROM products WHERE storage_location = :storageLocation ORDER BY expiry_date ASC")
    public LiveData<List<ProductWithCategoryDefinitions>> getProductWithFullCategoriesByLocation(String storageLocation);
    @Transaction
    @Query("SELECT * FROM products ORDER BY expiry_date ASC")
    public LiveData<List<ProductWithCategoryDefinitions>> getAllProductsWithFullCategories();
    @Query("UPDATE products SET storage_location = :defaultLocationInternalKey WHERE storage_location = :deleteLocationInternalKey")
    void updateProductLocation(String deleteLocationInternalKey, String defaultLocationInternalKey);
    @Transaction
    @Query("SELECT * FROM products WHERE  storage_location = :locationInternalKeyFilter ORDER BY expiry_date ASC")
    LiveData<List<ProductWithCategoryDefinitions>> getProductWithFullCategoriesByLocationInternalKey(String locationInternalKeyFilter);
}
