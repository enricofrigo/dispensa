package eu.frigo.dispensa.data.sync;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "joined_pantry_configs")
public class JoinedPantryConfig {
    @PrimaryKey
    @ColumnInfo(name = "dispensa_id")
    public int dispensaId;

    @NonNull
    @ColumnInfo(name = "provider_id")
    public String providerId = "webdav";

    @ColumnInfo(name = "url")
    public String url;

    @ColumnInfo(name = "username")
    public String username;

    @ColumnInfo(name = "password")
    public String password;

    @ColumnInfo(name = "path")
    public String path;

    @ColumnInfo(name = "is_shared")
    public boolean isShared;

    @ColumnInfo(name = "pantry_key")
    public String pantryKey;

    public JoinedPantryConfig() {
    }

    public JoinedPantryConfig(int dispensaId, String url, String username, String password, String path, boolean isShared, String pantryKey) {
        this.dispensaId = dispensaId;
        this.url = url;
        this.username = username;
        this.password = password;
        this.path = path;
        this.isShared = isShared;
        this.pantryKey = pantryKey;
    }
}
