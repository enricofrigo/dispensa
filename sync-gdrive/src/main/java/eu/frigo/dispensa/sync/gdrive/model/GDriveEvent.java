package eu.frigo.dispensa.sync.gdrive.model;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class GDriveEvent {
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

    public static final String ACTION_UPSERT_PRODUCT = "UPSERT_PRODUCT";
    public static final String ACTION_DELETE_PRODUCT = "DELETE_PRODUCT";
    public static final String ACTION_UPSERT_LOCATION = "UPSERT_LOCATION";
    public static final String ACTION_UPSERT_SHOPPING_ITEM = "UPSERT_SHOPPING_ITEM";
}
