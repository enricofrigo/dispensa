package eu.frigo.dispensa.data.backup;

import com.google.gson.annotations.SerializedName;
import java.util.List;

import eu.frigo.dispensa.data.category.CategoryDefinition;
import eu.frigo.dispensa.data.category.ProductCategoryLink;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.storage.StorageLocation;

public class BackupData {
    @SerializedName("dbVersion")
    public int dbVersion;
    @SerializedName("products")
    public List<Product> products;
    @SerializedName("locations")
    public List<StorageLocation> locations;
    @SerializedName("categories")
    public List<CategoryDefinition> categories;
    @SerializedName("categoryLinks")
    public List<ProductCategoryLink> categoryLinks;

    public BackupData() {
    }

    public BackupData(int dbVersion, List<Product> products, List<StorageLocation> locations,
            List<CategoryDefinition> categories, List<ProductCategoryLink> categoryLinks) {
        this.dbVersion = dbVersion;
        this.products = products;
        this.locations = locations;
        this.categories = categories;
        this.categoryLinks = categoryLinks;
    }
}
