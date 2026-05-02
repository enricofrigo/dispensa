package eu.frigo.dispensa.sync.webdav;

import java.util.HashMap;
import java.util.Map;
import eu.frigo.dispensa.sync.core.pairing.PairingPayload;

public class WebDavPairingHandler {
    
    public static PairingPayload createPayload(String deviceName, WebDavConfig config) {
        Map<String, String> data = new HashMap<>();
        data.put("url", config.url);
        data.put("user", config.username);
        data.put("pass", config.password);
        data.put("path", config.path);
        
        return new PairingPayload("webdav", deviceName, data);
    }
    
    public static WebDavConfig parsePayload(PairingPayload payload) {
        if (!"webdav".equals(payload.providerId)) {
            throw new IllegalArgumentException("Invalid provider ID");
        }
        
        return new WebDavConfig(
            payload.data.get("url"),
            payload.data.get("user"),
            payload.data.get("pass"),
            payload.data.get("path")
        );
    }
}
