package eu.frigo.dispensa.data.dispensa;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface DispensaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Dispensa dispensa);

    @Update
    void update(Dispensa dispensa);

    @Delete
    void delete(Dispensa dispensa);

    @Query("SELECT * FROM dispense ORDER BY name ASC")
    LiveData<List<Dispensa>> getAllDispense();

    @Query("SELECT * FROM dispense WHERE id = :id")
    LiveData<Dispensa> getDispensaById(int id);

    @Query("SELECT * FROM dispense WHERE id = :id")
    Dispensa getDispensaByIdSync(int id);

    @Query("SELECT * FROM dispense WHERE is_default = 1 LIMIT 1")
    Dispensa getDefaultDispensaSync();

    @Query("UPDATE dispense SET is_default = 0")
    void clearDefaults();

    @Transaction
    default void setAsDefault(int dispensaId) {
        clearDefaults();
        Dispensa dispensa = getDispensaByIdSync(dispensaId);
        if (dispensa != null) {
            dispensa.isDefault = true;
            update(dispensa);
        }
    }

    @Query("SELECT COUNT(*) FROM dispense")
    int countDispense();
}
