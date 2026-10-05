package eu.frigo.dispensa.data.sync;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "joined_pantry_configs")
public class JoinedPantryConfig {
    @PrimaryKey
    @ColumnInfo(name = "dispensa_id")
    public int dispensaId;

    @NonNull
    @ColumnInfo(name = "provider_id", defaultValue = "webdav")
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

    @ColumnInfo(name = "config_payload")
    public String configPayload;

    @ColumnInfo(name = "remote_pantry_id")
    public String remotePantryId;

    public JoinedPantryConfig() {
    }

    @Ignore
    public JoinedPantryConfig(int dispensaId, String url, String username, String password, String path, boolean isShared, String pantryKey) {
        this.dispensaId = dispensaId;
        this.providerId = "webdav";
        this.url = url;
        this.username = username;
        this.password = password;
        this.path = path;
        this.isShared = isShared;
        this.pantryKey = pantryKey;
    }

    @Ignore
    public JoinedPantryConfig(int dispensaId, @NonNull String providerId, String configPayload, String remotePantryId) {
        this.dispensaId = dispensaId;
        this.providerId = providerId;
        this.configPayload = configPayload;
        this.remotePantryId = remotePantryId;
    }
}
