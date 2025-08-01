package eu.frigo.dispensa.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Transaction; // Importa Transaction

import java.util.List;

@Dao
public interface StorageLocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(StorageLocation location);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<StorageLocation> locations); // Per l'inizializzazione

    @Update
    void update(StorageLocation location);

    @Delete
    void delete(StorageLocation location);

    @Query("DELETE FROM storage_locations WHERE internal_key = :internalKey AND is_predefined = 0") // Non permette di cancellare quelle predefinite tramite questo metodo
    int deleteByInternalKeyIfNotPredefined(String internalKey);

    @Query("SELECT * FROM storage_locations WHERE id = :id")
    LiveData<StorageLocation> getLocationById(int id);

    @Query("SELECT * FROM storage_locations WHERE internal_key = :internalKey")
    LiveData<StorageLocation> getLocationByInternalKey(String internalKey);

    @Query("SELECT * FROM storage_locations WHERE internal_key = :internalKey")
    StorageLocation getLocationByInternalKeySync(String internalKey); // Versione sincrona

    @Query("SELECT * FROM storage_locations ORDER BY order_index ASC")
    LiveData<List<StorageLocation>> getAllLocationsSorted();

    @Query("SELECT * FROM storage_locations ORDER BY order_index ASC")
    List<StorageLocation> getAllLocationsSortedSync(); // Versione sincrona

    @Query("SELECT * FROM storage_locations WHERE is_default = 1 LIMIT 1")
    LiveData<StorageLocation> getDefaultLocation();

    @Query("SELECT * FROM storage_locations WHERE is_default = 1 LIMIT 1")
    StorageLocation getDefaultLocationSync(); // Versione sincrona

    @Query("UPDATE storage_locations SET is_default = 0 WHERE internal_key != :newDefaultInternalKey")
    void clearOtherDefaults(String newDefaultInternalKey);

    @Transaction // Esegui queste due operazioni come una singola transazione
    default void setAsDefault(String internalKey) {
        clearOtherDefaults(internalKey);
        StorageLocation loc = getLocationByInternalKeySync(internalKey);
        if (loc != null) {
            loc.isDefault = true;
            update(loc);
        }
    }

    @Query("SELECT COUNT(*) FROM storage_locations")
    int countLocations();
    @Query("SELECT MAX(order_index) FROM storage_locations")
    int getMaxOrderIndex();
}
