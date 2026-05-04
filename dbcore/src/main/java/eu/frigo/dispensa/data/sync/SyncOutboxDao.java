package eu.frigo.dispensa.data.sync;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SyncOutboxDao {
    @Insert
    void insert(eu.frigo.dispensa.data.sync.SyncOutbox entry);

    @Query("SELECT * FROM sync_outbox WHERE isSynced = 0 ORDER BY timestamp ASC")
    List<eu.frigo.dispensa.data.sync.SyncOutbox> getPendingChangesSync();

    @Query("UPDATE sync_outbox SET isSynced = 1 WHERE syncId IN (:syncIds)")
    void markAsSynced(List<String> syncIds);

    @Query("DELETE FROM sync_outbox WHERE isSynced = 1")
    void deleteSynced();
}
