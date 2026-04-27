package eu.frigo.dispensa.data.shoppinglist;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

@Entity(tableName = "shopping_items")
public class ShoppingItem {

    @SerializedName("id")
    @PrimaryKey(autoGenerate = true)
    public int id;

    @SerializedName("name")
    @ColumnInfo(name = "name")
    public String name;

    @SerializedName("quantity")
    @ColumnInfo(name = "quantity")
    public int quantity;

    @SerializedName("checked")
    @ColumnInfo(name = "checked")
    public boolean checked;

    public ShoppingItem() {
    }

    public ShoppingItem(String name, int quantity, boolean checked) {
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
