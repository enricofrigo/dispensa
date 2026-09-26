package eu.frigo.dispensa.sync.core.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class PantrySnapshot {
    @SerializedName("timestamp")
    public long timestamp;

    @SerializedName("products")
    public List<Object> products = new ArrayList<>();

    @SerializedName("locations")
    public List<Object> locations = new ArrayList<>();

    @SerializedName("shoppingItems")
    public List<Object> shoppingItems = new ArrayList<>();

    public PantrySnapshot() {
        this.timestamp = System.currentTimeMillis();
    }
}
