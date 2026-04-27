package eu.frigo.dispensa.data.shoppinglist;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ShoppingItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ShoppingItem item);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ShoppingItem> items);

    @Update
    void update(ShoppingItem item);

    @Delete
    void delete(ShoppingItem item);

    @Query("DELETE FROM shopping_items")
    void deleteAll();

    @Query("DELETE FROM shopping_items WHERE checked = 1")
    void deleteChecked();

    @Query("SELECT * FROM shopping_items ORDER BY checked ASC, name ASC")
    LiveData<List<ShoppingItem>> getAllItems();

    @Query("SELECT * FROM shopping_items ORDER BY checked ASC, name ASC")
    List<ShoppingItem> getAllItemsSync();

    @Query("SELECT * FROM shopping_items WHERE name = :name LIMIT 1")
    ShoppingItem getItemByNameSync(String name);

    @Query("SELECT COUNT(*) FROM shopping_items WHERE checked = 0")
    LiveData<Integer> getUncheckedCount();

    @Query("SELECT name FROM shopping_items")
    LiveData<List<String>> getAllItemNames();

    @Query("DELETE FROM shopping_items WHERE name = :name")
    void deleteByName(String name);
}
