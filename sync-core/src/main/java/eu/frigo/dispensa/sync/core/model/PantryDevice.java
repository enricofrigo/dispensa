package eu.frigo.dispensa.sync.core.model;

import com.google.gson.annotations.SerializedName;

public class PantryDevice {
    @SerializedName("device_id")
    public String deviceId;

    @SerializedName("device_name")
    public String deviceName;

    @SerializedName("last_seen")
    public long lastSeen;

    public PantryDevice() {
        this.lastSeen = System.currentTimeMillis();
    }

    public PantryDevice(String deviceId, String deviceName) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.lastSeen = System.currentTimeMillis();
    }
}
