package eu.frigo.dispensa.sync.core.pairing;

import org.junit.Assert;
import org.junit.Test;
import java.util.HashMap;
import java.util.Map;

public class OnboardingTest {

    @Test
    public void testFullPairingCycle() throws Exception {
        // 1. Setup sample data
        String pairingCode = OnboardingCoordinator.generatePairingCode();
        Assert.assertEquals(6, pairingCode.length());
        Assert.assertTrue(pairingCode.matches("[a-zA-Z0-9]+"));

        Map<String, String> data = new HashMap<>();
        data.put("url", "https://example.com/dav");
        data.put("user", "alice");
        data.put("pass", "secret123");
        
        PairingPayload payload = new PairingPayload("webdav", "Test Device", data);

        // 2. Encode
        PairingPayloadCodecImpl codec = new PairingPayloadCodecImpl(pairingCode);
        String wireData = codec.encode(payload);
        
        Assert.assertTrue(wireData.startsWith("v1|"));

        // 3. Decode
        PairingPayload decoded = codec.decode(wireData);

        // 4. Verify
        Assert.assertEquals(payload.providerId, decoded.providerId);
        Assert.assertEquals(payload.deviceName, decoded.deviceName);
        Assert.assertEquals(payload.data.get("url"), decoded.data.get("url"));
        Assert.assertEquals(payload.data.get("user"), decoded.data.get("user"));
        Assert.assertEquals(payload.data.get("pass"), decoded.data.get("pass"));
    }

    @Test(expected = Exception.class)
    public void testWrongCodeFails() throws Exception {
        String correctCode = "ABC123";
        String wrongCode = "XYZ789";

        Map<String, String> data = new HashMap<>();
        data.put("key", "value");
        PairingPayload payload = new PairingPayload("pid", "dev", data);

        String wireData = new PairingPayloadCodecImpl(correctCode).encode(payload);
        
        // This should throw an exception (likely during AES-GCM tag verification)
        new PairingPayloadCodecImpl(wrongCode).decode(wireData);
    }
}
