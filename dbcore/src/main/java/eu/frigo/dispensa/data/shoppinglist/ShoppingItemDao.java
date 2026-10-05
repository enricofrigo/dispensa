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

    @Query("DELETE FROM shopping_items WHERE dispensa_id = :dispensaId")
    void deleteAllItems(int dispensaId);

    @Query("DELETE FROM shopping_items WHERE checked = 1 AND dispensa_id = :dispensaId")
    void deleteChecked(int dispensaId);

    @Query("SELECT * FROM shopping_items WHERE dispensa_id = :dispensaId ORDER BY checked ASC, name ASC")
    LiveData<List<ShoppingItem>> getAllItems(int dispensaId);

    @Query("SELECT * FROM shopping_items WHERE dispensa_id = :dispensaId ORDER BY checked ASC, name ASC")
    List<ShoppingItem> getAllItemsSync(int dispensaId);

    @Query("SELECT * FROM shopping_items WHERE name = :name AND dispensa_id = :dispensaId LIMIT 1")
    ShoppingItem getItemByNameSync(String name, int dispensaId);

    @Query("SELECT * FROM shopping_items WHERE checked = 1 AND dispensa_id = :dispensaId")
    List<ShoppingItem> getCheckedItemsSync(int dispensaId);

    @Query("SELECT COUNT(*) FROM shopping_items WHERE checked = 0 AND dispensa_id = :dispensaId")
    LiveData<Integer> getUncheckedCount(int dispensaId);

    @Query("SELECT name FROM shopping_items WHERE dispensa_id = :dispensaId")
    LiveData<List<String>> getAllItemNames(int dispensaId);

    @Query("DELETE FROM shopping_items WHERE name = :name AND dispensa_id = :dispensaId")
    void deleteByName(String name, int dispensaId);
}
