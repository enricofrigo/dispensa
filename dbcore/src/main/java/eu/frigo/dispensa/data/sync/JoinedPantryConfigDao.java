package eu.frigo.dispensa.data.sync;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface JoinedPantryConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(JoinedPantryConfig config);

    @Update
    void update(JoinedPantryConfig config);

    @Delete
    void delete(JoinedPantryConfig config);

    @Query("SELECT * FROM joined_pantry_configs WHERE dispensa_id = :dispensaId")
    JoinedPantryConfig getConfigByDispensaId(int dispensaId);

    @Query("SELECT * FROM joined_pantry_configs")
    List<JoinedPantryConfig> getAllConfigs();

    @Query("SELECT * FROM joined_pantry_configs")
    androidx.lifecycle.LiveData<List<JoinedPantryConfig>> getAllConfigsLive();

    @Query("SELECT * FROM joined_pantry_configs WHERE dispensa_id = :dispensaId")
    androidx.lifecycle.LiveData<JoinedPantryConfig> getConfigByDispensaIdLive(int dispensaId);

    @Query("DELETE FROM joined_pantry_configs WHERE dispensa_id = :dispensaId")
    void deleteByDispensaId(int dispensaId);

    @Query("DELETE FROM joined_pantry_configs WHERE dispensa_id NOT IN (SELECT id FROM dispense)")
    void deleteOrphans();
}
