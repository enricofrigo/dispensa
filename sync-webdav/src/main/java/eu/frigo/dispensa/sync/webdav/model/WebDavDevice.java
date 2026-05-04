package eu.frigo.dispensa.sync.webdav.model;

import com.google.gson.annotations.SerializedName;

public class WebDavDevice {
    @SerializedName("deviceId")
    public String deviceId;

    @SerializedName("deviceName")
    public String deviceName;

    @SerializedName("lastSeen")
    public long lastSeen;
}
