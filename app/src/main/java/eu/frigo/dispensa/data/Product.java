package eu.frigo.dispensa.data; // Crea un package 'data' o simile

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "products") // Nome della tabella nel database
public class Product {

    @PrimaryKey(autoGenerate = true)
    public int id;
    @ColumnInfo(name = "barcode")
    public String barcode;
    @ColumnInfo(name = "quantity")
    public int quantity;
    @ColumnInfo(name = "expiry_date")
    public String expiryDate;
    @ColumnInfo(name = "product_name")
    private String productName;
    @ColumnInfo(name = "image_url")
    private String imageUrl;

    public Product() {}

    public Product(String barcode, int quantity, String expiryDate,String productName, String imageUrl) {
        this.barcode = barcode;
        this.quantity = quantity;
        this.expiryDate = expiryDate;
        this.productName = productName;
        this.imageUrl = imageUrl;
    }

    // Getters e Setters (opzionali se i campi sono pubblici, ma buona pratica includerli)
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
    @NonNull
    @Override
    public String toString() {
        return "Product{" +
                "id=" + id +
                ", barcode='" + barcode + '\'' +
                ", quantity=" + quantity +
                ", expiryDate='" + expiryDate + '\'' +
                ", productName='" + productName + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                '}';
    }
}
