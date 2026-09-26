package eu.frigo.dispensa.sync.core.model;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class PantryEvent {
    public static final String ACTION_UPSERT_PRODUCT = "UPSERT_PRODUCT";
    public static final String ACTION_DELETE_PRODUCT = "DELETE_PRODUCT";
    public static final String ACTION_UPSERT_LOCATION = "UPSERT_LOCATION";
    public static final String ACTION_DELETE_LOCATION = "DELETE_LOCATION";
    public static final String ACTION_UPSERT_SHOPPING_ITEM = "UPSERT_SHOPPING_ITEM";
    public static final String ACTION_DELETE_SHOPPING_ITEM = "DELETE_SHOPPING_ITEM";

    @SerializedName("event_id")
    public String eventId;

    @SerializedName("device_id")
    public String deviceId;

    @SerializedName("timestamp")
    public long timestamp;

    @SerializedName("action")
    public String action;

    @SerializedName("payload")
    public Map<String, Object> payload;

    public PantryEvent() {}

    public PantryEvent(String eventId, String deviceId, long timestamp, String action, Map<String, Object> payload) {
        this.eventId = eventId;
        this.deviceId = deviceId;
        this.timestamp = timestamp;
        this.action = action;
        this.payload = payload;
    }
}
