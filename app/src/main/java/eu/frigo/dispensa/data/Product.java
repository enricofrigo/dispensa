package eu.frigo.dispensa.data; // Crea un package 'data' o simile

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@Entity(tableName = "products")
public class Product {

    @PrimaryKey(autoGenerate = true)
    public int id;
    @ColumnInfo(name = "barcode")
    public String barcode;
    @ColumnInfo(name = "quantity")
    public int quantity;
    @ColumnInfo(name = "expiry_date")
    public Long expiryDate;
    @ColumnInfo(name = "product_name")
    private String productName;
    @ColumnInfo(name = "image_url")
    private String imageUrl;
    @ColumnInfo(name = "storage_location")
    private String storageLocation;
    public Product() {}

    public Product(String barcode, int quantity, Long expiryDate,String productName, String imageUrl,String storageLocation) {
        this.barcode = barcode;
        this.quantity = quantity;
        this.expiryDate = expiryDate;
        this.productName = productName;
        this.imageUrl = imageUrl;
        this.storageLocation = storageLocation;
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

    public Long getExpiryDate() {return expiryDate;}
    public String getExpiryDateString() {
        String myFormat = "dd/MM/yyyy"; // Scegli il formato che preferisci
        SimpleDateFormat sdf = new SimpleDateFormat(myFormat, Locale.ITALY);
        return sdf.format(new Date(expiryDate));
    }
    public void setExpiryDate(Long expiryDate) {
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

    public String getStorageLocation() {return storageLocation;}

    public void setStorageLocation(String storageLocation) {this.storageLocation = storageLocation;}

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
                ", storageLocation='" + storageLocation + '\'' +
                '}';
    }

    public Product copyWithNewQuantity(int i) {
        Product copy = new Product();
        copy.id = this.id;
        copy.barcode = this.barcode;
        copy.quantity = i;
        copy.expiryDate = this.expiryDate;
        copy.productName = this.productName;
        copy.imageUrl = this.imageUrl;
        copy.storageLocation = this.storageLocation;
        return copy;
    }
}
