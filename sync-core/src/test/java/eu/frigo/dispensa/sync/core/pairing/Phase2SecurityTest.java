package eu.frigo.dispensa.sync.core.pairing;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;
import java.util.Map;

import eu.frigo.dispensa.sync.core.crypto.CredentialCipher;

/**
 * Fase 2 Security Test Suite:
 * - Mandatory 6-character PIN for pairing (no public static fallback key)
 * - Hyphen/case normalization for PIN input (e.g. "ab-cdef" == "ABCDEF")
 * - PBKDF2 120,000 iterations
 * - CredentialCipher encryption at rest
 */
@RunWith(RobolectricTestRunner.class)
public class Phase2SecurityTest {

    @Test(expected = IllegalArgumentException.class)
    public void testNullPinIsRejectedWithoutFallbackToPublicKey() {
        new PairingPayloadCodecImpl(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyPinIsRejectedWithoutFallbackToPublicKey() {
        new PairingPayloadCodecImpl("   -  ");
    }

    @Test
    public void testFormattedPinWithHyphenAndLowercaseDecodesCorrectly() throws Exception {
        String generatedPin = OnboardingCoordinator.generatePairingCode();
        Assert.assertEquals(6, generatedPin.length());
        Assert.assertFalse("Ambiguous chars 0, 1, I, O must not be used",
                generatedPin.contains("0") || generatedPin.contains("1")
                        || generatedPin.contains("I") || generatedPin.contains("O"));

        Map<String, String> data = new HashMap<>();
        data.put("url", "https://cloud.example.com/remote.php/dav");
        data.put("user", "enrico");
        data.put("pass", "SuperSecretAppPass!");

        PairingPayload payload = new PairingPayload("webdav", "Pixel 9", data);
        String wire = new PairingPayloadCodecImpl(generatedPin).encode(payload);

        // User enters "xxx-xxx" in lowercase on joining device
        String userFormattedPin = (generatedPin.substring(0, 3) + "-" + generatedPin.substring(3)).toLowerCase();
        PairingPayload decoded = new PairingPayloadCodecImpl(userFormattedPin).decode(wire);

        Assert.assertNotNull(decoded);
        Assert.assertEquals("SuperSecretAppPass!", decoded.data.get("pass"));
        Assert.assertEquals(120000, CryptoEngine.ITERATIONS);
    }

    @Test
    public void testCredentialCipherEncryptsAndDecryptsIdempotently() {
        String plain = "MyWebDavSecretPassword!2026";
        String encrypted = CredentialCipher.encryptIfNeeded(plain);

        Assert.assertNotNull(encrypted);
        Assert.assertTrue(encrypted.startsWith(CredentialCipher.PREFIX));
        Assert.assertNotEquals(plain, encrypted);
        Assert.assertFalse(encrypted.contains(plain));

        // Idempotent when called again on already encrypted value
        Assert.assertEquals(encrypted, CredentialCipher.encryptIfNeeded(encrypted));

        // Decrypts back to original plaintext
        String decrypted = CredentialCipher.decryptIfNeeded(encrypted);
        Assert.assertEquals(plain, decrypted);

        // Legacy plaintext is returned as-is by decryptIfNeeded
        Assert.assertEquals("legacy_plain", CredentialCipher.decryptIfNeeded("legacy_plain"));
    }
}
