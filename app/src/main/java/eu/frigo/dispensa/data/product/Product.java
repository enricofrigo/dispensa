package eu.frigo.dispensa.data.product; // Crea un package 'data' o simile

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

@Entity(tableName = "products",
        indices = { // 6. Definizione degli indici
                @Index(value = {"storage_location"})
        })
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
    @ColumnInfo(name = "opened_date", defaultValue = "0")
    public long openedDate = 0L;
    @ColumnInfo(name = "shelf_life_after_opening_days", defaultValue = "-1")
    public int shelfLifeAfterOpeningDays = -1;
    @Ignore
    private String myFormat = "dd/MM/yyyy";
    @Ignore
    private String notDefined = "N/D";

    public Product() {}

    public Product(String barcode, int quantity, Long expiryDate,String productName, String imageUrl,String storageLocation, Long openedDate, int shelfLifeAfterOpeningDays) {
        this.barcode = barcode;
        this.quantity = quantity;
        this.expiryDate = expiryDate;
        this.productName = productName;
        this.imageUrl = imageUrl;
        this.storageLocation = storageLocation;
        this.openedDate = openedDate;
        this.shelfLifeAfterOpeningDays = shelfLifeAfterOpeningDays;
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

    public Long getOpenedDate() {return openedDate; }

    public void setOpenedDate(Long openedDate) {this.openedDate = openedDate;}

    public int getShelfLifeAfterOpeningDays() {return shelfLifeAfterOpeningDays;}

    public void setShelfLifeAfterOpeningDays(int shelfLifeAfterOpeningDays) {this.shelfLifeAfterOpeningDays = shelfLifeAfterOpeningDays;}

    @Ignore
    public Long getActualExpiryTimestamp() {
        if (openedDate > 0 && shelfLifeAfterOpeningDays > 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(openedDate);
            calendar.add(Calendar.DAY_OF_YEAR, shelfLifeAfterOpeningDays);
            long expiryAfterOpening = calendar.getTimeInMillis();

            // Il prodotto scade prima tra la sua data di scadenza originale
            // e quella calcolata dopo l'apertura.
            if (expiryDate != null && expiryDate > 0) {
                return Math.min(expiryDate, expiryAfterOpening);
            } else {
                return expiryAfterOpening;
            }
        }
        // Se non aperto o senza durata specifica dopo l'apertura, usa la data di scadenza originale
        return expiryDate != null ? expiryDate : 0L;
    }
    @Ignore
    public String getActualExpiryDateString() {
        Long actualTimestamp = getActualExpiryTimestamp();
        if (actualTimestamp == null || actualTimestamp <= 0) {
            return notDefined;
        }
        SimpleDateFormat sdf = new SimpleDateFormat(myFormat, Locale.ITALY);
        return sdf.format(new Date(actualTimestamp));
    }
    @Ignore
    public boolean isOpened() {
        return this.openedDate > 0;
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
                ", storageLocation='" + storageLocation + '\'' +
                ", openedDate=" + openedDate +
                ", shelfLifeAfterOpeningDays=" + shelfLifeAfterOpeningDays +
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
        copy.openedDate = this.openedDate;
        copy.shelfLifeAfterOpeningDays = this.shelfLifeAfterOpeningDays;
        return copy;
    }
}
