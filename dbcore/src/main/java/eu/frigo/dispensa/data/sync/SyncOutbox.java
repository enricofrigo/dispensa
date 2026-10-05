package eu.frigo.dispensa.data.sync;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sync_outbox")
public class SyncOutbox {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String syncId;
    @ColumnInfo(name = "dispensa_id", defaultValue = "0")
    public int dispensaId;
    public String dataType;
    public String payload;
    public long timestamp;
    public boolean isSynced = false;
}
