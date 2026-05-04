package eu.frigo.dispensa.sync.webdav.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.storage.StorageLocation;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItem;

public class WebDavSnapshot {
    @SerializedName("timestamp")
    public long timestamp;

    @SerializedName("products")
    public List<Product> products;

    @SerializedName("locations")
    public List<StorageLocation> locations;

    @SerializedName("shopping_items")
    public List<ShoppingItem> shoppingItems;
}
