package eu.frigo.dispensa.data.openfoodfacts;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "openfoodfact_cache")
public class OpenFoodFactCacheEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "barcode")
    public String barcode;

    @ColumnInfo(name = "product_name")
    public String productName;

    @ColumnInfo(name = "image_local_path")
    public String imageLocalPath;

    @ColumnInfo(name = "categories_tags")
    public String categoriesTags;

    @ColumnInfo(name = "timestamp_ms")
    public long timestampMs;

    public OpenFoodFactCacheEntity(@NonNull String barcode, String productName, String imageLocalPath, String categoriesTags, long timestampMs) {
        this.barcode = barcode;
        this.productName = productName;
        this.imageLocalPath = imageLocalPath;
        this.categoriesTags = categoriesTags;
        this.timestampMs = timestampMs;
    }
}
