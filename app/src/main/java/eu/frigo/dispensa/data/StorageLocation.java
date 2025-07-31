package eu.frigo.dispensa.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "storage_locations",
        indices = {@Index(value = {"internal_key"}, unique = true),
                @Index(value = {"order_index"})})
public class StorageLocation {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "name")
    public String name; // Nome visualizzato dall'utente (es. "Frigo", "Dispensa", "Cantina")

    @ColumnInfo(name = "internal_key")
    public String internalKey; // Chiave univoca interna (es. "FRIDGE", "PANTRY", "CUSTOM_CELLAR_01")

    @ColumnInfo(name = "order_index")
    public int orderIndex; // Per l'ordinamento dei tab

    @ColumnInfo(name = "is_default", defaultValue = "0") // Default a false (0 per SQLite boolean)
    public boolean isDefault;

    @ColumnInfo(name = "is_predefined", defaultValue = "0") // Per marcare le location predefinite
    public boolean isPredefined; // es. FRIDGE, FREEZER, PANTRY iniziali

    public StorageLocation() {}

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

    public String getInternalKey() {
        return internalKey;
    }

    public void setInternalKey(String internalKey) {
        this.internalKey = internalKey;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public boolean isPredefined() {
        return isPredefined;
    }

    public void setPredefined(boolean predefined) {
        isPredefined = predefined;
    }

    public StorageLocation(String name, String internalKey, int orderIndex, boolean isDefault, boolean isPredefined) {
        this.name = name;
        this.internalKey = internalKey;
        this.orderIndex = orderIndex;
        this.isDefault = isDefault;
        this.isPredefined = isPredefined;
    }

    @Override
    public String toString() {
        return "StorageLocation{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", internalKey='" + internalKey + '\'' +
                ", orderIndex=" + orderIndex +
                ", isDefault=" + isDefault +
                ", isPredefined=" + isPredefined +
                '}';
    }
}
