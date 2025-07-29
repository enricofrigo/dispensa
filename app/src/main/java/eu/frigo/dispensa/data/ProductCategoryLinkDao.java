package eu.frigo.dispensa.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ProductCategoryLinkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ProductCategoryLink productCategoryLink);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ProductCategoryLink> productCategoryLink);

    @Update
    void update(ProductCategoryLink productCategoryLink);

    @Delete
    void delete(ProductCategoryLink productCategoryLink);

    @Query("DELETE FROM product_category_links")
    void deleteAllProductCategoryLink();
    @Query("SELECT * FROM product_category_links ")
    LiveData<List<ProductCategoryLink>> getAllProductCategoryLink();
    @Query("SELECT * FROM product_category_links WHERE product_id_fk = :id")
    LiveData<List<ProductCategoryLink>> getProductCategoryLinkByProductId(int id);
    @Query("DELETE FROM product_category_links WHERE product_id_fk = :id")
    void deleteByProductId(int id);
}
