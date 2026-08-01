package eu.frigo.dispensa.data.dispensa;

import com.google.gson.annotations.SerializedName;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.io.Serializable;

@Entity(tableName = "dispense")
public class Dispensa implements Serializable {

    @SerializedName("id")
    @PrimaryKey(autoGenerate = true)
    public int id;

    @SerializedName("name")
    @ColumnInfo(name = "name")
    public String name;

    @SerializedName("is_default")
    @ColumnInfo(name = "is_default", defaultValue = "0")
    public boolean isDefault;

    @SerializedName("last_modified")
    @ColumnInfo(name = "last_modified", defaultValue = "0")
    public long lastModified = 0L;

    @SerializedName("remote_id")
    @ColumnInfo(name = "remote_id")
    public String remoteId; // Per futura condivisione separata

    @SerializedName("device_owner_id")
    @ColumnInfo(name = "device_owner_id")
    public String deviceOwnerId;

    public Dispensa() {
    }

    public Dispensa(String name, boolean isDefault) {
        this.name = name;
        this.isDefault = isDefault;
        this.lastModified = System.currentTimeMillis();
    }

    public Dispensa(Dispensa other) {
        this.id = other.id;
        this.name = other.name;
        this.isDefault = other.isDefault;
        this.lastModified = other.lastModified;
        this.remoteId = other.remoteId;
        this.deviceOwnerId = other.deviceOwnerId;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public String getRemoteId() {
        return remoteId;
    }

    public void setRemoteId(String remoteId) {
        this.remoteId = remoteId;
    }
}
