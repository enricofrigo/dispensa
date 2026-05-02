package eu.frigo.dispensa.sync.core.pairing;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class PairingPayload {
    @SerializedName("v")
    public int version = 1;

    @SerializedName("pid")
    public String providerId;

    @SerializedName("iat")
    public long issuedAt;

    @SerializedName("dname")
    public String deviceName;

    @SerializedName("data")
    public Map<String, String> data;

    public PairingPayload() {}

    public PairingPayload(String providerId, String deviceName, Map<String, String> data) {
        this.providerId = providerId;
        this.deviceName = deviceName;
        this.data = data;
        this.issuedAt = System.currentTimeMillis() / 1000;
    }
}
