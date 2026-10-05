package eu.frigo.dispensa.sync.core.model;

import com.google.gson.annotations.SerializedName;

public class PantryDevice {
    @SerializedName(value = "device_id", alternate = {"deviceId"})
    public String deviceId;

    @SerializedName(value = "device_name", alternate = {"deviceName"})
    public String deviceName;

    @SerializedName(value = "last_seen", alternate = {"lastSeen"})
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
