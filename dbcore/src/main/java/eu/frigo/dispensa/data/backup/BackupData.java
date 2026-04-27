package eu.frigo.dispensa.data.backup;

import java.util.List;

import eu.frigo.dispensa.data.category.CategoryDefinition;
import eu.frigo.dispensa.data.category.ProductCategoryLink;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.storage.StorageLocation;

public class BackupData {
    public int dbVersion;
    public int appVersion;
    public List<Product> products;
    public List<StorageLocation> locations;
    public List<CategoryDefinition> categories;
    public List<ProductCategoryLink> categoryLinks;

    public BackupData() {
    }

    public BackupData(int dbVersion, int appVersion, List<Product> products, List<StorageLocation> locations,
                      List<CategoryDefinition> categories, List<ProductCategoryLink> categoryLinks) {
        this.dbVersion = dbVersion;
        this.appVersion = appVersion;
        this.products = products;
        this.locations = locations;
        this.categories = categories;
        this.categoryLinks = categoryLinks;
    }
}