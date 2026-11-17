package eu.frigo.dispensa.data.shoppinglist;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import androidx.room.Index;

import com.google.gson.annotations.SerializedName;
import java.util.UUID;

@Entity(tableName = "shopping_items", indices = {
        @Index(value = "dispensa_id"),
        @Index(value = { "dispensa_id", "sync_uuid" })
})
public class ShoppingItem {

    @SerializedName("id")
    @PrimaryKey(autoGenerate = true)
    public int id;

    @SerializedName("sync_uuid")
    @ColumnInfo(name = "sync_uuid")
    public String syncUuid;

    @SerializedName("dispensa_id")
    @ColumnInfo(name = "dispensa_id", defaultValue = "1")
    public int dispensaId;

    @SerializedName("name")
    @ColumnInfo(name = "name")
    public String name;

    @SerializedName("quantity")
    @ColumnInfo(name = "quantity")
    public int quantity;

    @SerializedName("checked")
    @ColumnInfo(name = "checked")
    public boolean checked;

    @SerializedName("last_modified")
    @ColumnInfo(name = "last_modified", defaultValue = "0")
    public long lastModified = 0L;

    public ShoppingItem() {
        this.syncUuid = UUID.randomUUID().toString();
    }

    @Ignore
    public ShoppingItem(String name, int quantity, boolean checked) {
        this.syncUuid = UUID.randomUUID().toString();
        this.name = name;
        this.quantity = quantity;
        this.checked = checked;
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

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    @NonNull
    @Override
    public String toString() {
        return "ShoppingItem{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", quantity=" + quantity +
                ", checked=" + checked +
                '}';
    }
}
