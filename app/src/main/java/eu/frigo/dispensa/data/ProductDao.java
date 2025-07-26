package eu.frigo.dispensa.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Delete;

import java.util.List;

@Dao
public interface ProductDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE) // Se un prodotto con lo stesso ID esiste, viene rimpiazzato
    void insert(Product product); // Per inserire un singolo prodotto

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Product> products); // Per inserire una lista di prodotti

    @Update
    void update(Product product);

    @Delete
    void delete(Product product);

    @Query("DELETE FROM products")
    void deleteAllProducts();
    @Query("SELECT * FROM products ORDER BY expiry_date ASC")
    List<Product> getAllProductsListStatic();
    @Query("SELECT * FROM products ORDER BY expiry_date ASC") // Ordina per data di scadenza (come stringa)
    LiveData<List<Product>> getAllProducts(); // LiveData per osservare i cambiamenti

    @Query("SELECT * FROM products WHERE id = :productId")
    LiveData<Product> getProductById(int productId);

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    Product getProductByBarcode(String barcode); // Metodo sincrono, da chiamare in background
}
