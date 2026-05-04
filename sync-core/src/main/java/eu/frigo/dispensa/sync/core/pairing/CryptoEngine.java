package eu.frigo.dispensa.sync.core.pairing;

import android.util.Base64;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoEngine {
    private static final int ITERATIONS = 10000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    public static class EncryptedResult {
        public final String salt;
        public final String iv;
        public final String ciphertext;

        public EncryptedResult(String salt, String iv, String ciphertext) {
            this.salt = salt;
            this.iv = iv;
            this.ciphertext = ciphertext;
        }
    }

    public static EncryptedResult encrypt(String passphrase, String plaintext) throws Exception {
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);

        SecretKey key = deriveKey(passphrase, salt);

        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));

        return new EncryptedResult(
                Base64.encodeToString(salt, Base64.NO_WRAP),
                Base64.encodeToString(iv, Base64.NO_WRAP),
                Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        );
    }

    public static String decrypt(String passphrase, EncryptedResult encrypted) throws Exception {
        byte[] salt = Base64.decode(encrypted.salt, Base64.NO_WRAP);
        byte[] iv = Base64.decode(encrypted.iv, Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(encrypted.ciphertext, Base64.NO_WRAP);

        SecretKey key = deriveKey(passphrase, salt);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, "UTF-8");
    }

    private static SecretKey deriveKey(String passphrase, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }
}
