package eu.frigo.dispensa.sync.core.crypto;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts and decrypts credentials at rest using AndroidKeyStore AES-GCM.
 * Includes a deterministic in-memory fallback when running under JVM/Robolectric tests
 * where AndroidKeyStore hardware provider is unavailable.
 */
public final class CredentialCipher {
    private static final String TAG = "CredentialCipher";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "dispensa_credential_master_key_v1";
    public static final String PREFIX = "enc:v1:";
    private static final int GCM_TAG_BITS = 128;

    private static SecretKey fallbackTestKey;

    private CredentialCipher() {}

    public static String encryptIfNeeded(String value) {
        if (value == null || value.isEmpty() || value.startsWith(PREFIX)) {
            return value;
        }
        try {
            SecretKey secretKey = getOrCreateSecretKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return PREFIX
                    + Base64.encodeToString(iv, Base64.NO_WRAP)
                    + ":"
                    + Base64.encodeToString(ciphertext, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.w(TAG, "Unable to encrypt credential, keeping value", e);
            return value;
        }
    }

    public static String decryptIfNeeded(String storedValue) {
        if (storedValue == null || storedValue.isEmpty() || !storedValue.startsWith(PREFIX)) {
            return storedValue;
        }
        try {
            String payload = storedValue.substring(PREFIX.length());
            int sep = payload.indexOf(':');
            if (sep <= 0) {
                return storedValue;
            }
            byte[] iv = Base64.decode(payload.substring(0, sep), Base64.NO_WRAP);
            byte[] ciphertext = Base64.decode(payload.substring(sep + 1), Base64.NO_WRAP);

            SecretKey secretKey = getOrCreateSecretKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(ciphertext);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.w(TAG, "Unable to decrypt credential", e);
            return storedValue;
        }
    }

    private static synchronized SecretKey getOrCreateSecretKey() throws Exception {
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);
            if (ks.containsAlias(KEY_ALIAS)) {
                KeyStore.Entry entry = ks.getEntry(KEY_ALIAS, null);
                if (entry instanceof KeyStore.SecretKeyEntry) {
                    return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
                }
            }
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
            );
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
            )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build();
            keyGenerator.init(spec);
            return keyGenerator.generateKey();
        } catch (Exception keystoreUnavailable) {
            // JVM / Robolectric unit test fallback
            if (fallbackTestKey == null) {
                byte[] raw = "DispensaTestFallbackKey_32Bytes!".getBytes(StandardCharsets.UTF_8);
                fallbackTestKey = new SecretKeySpec(raw, "AES");
            }
            return fallbackTestKey;
        }
    }
}
