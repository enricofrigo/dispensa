package eu.frigo.dispensa.sync.core.pairing;

import com.google.gson.Gson;

public class PairingPayloadCodecImpl implements PairingPayloadCodec {
    private final Gson gson = new Gson();
    private final String pairingCode;

    public PairingPayloadCodecImpl(String pairingCode) {
        this.pairingCode = pairingCode;
    }

    @Override
    public String encode(PairingPayload payload) {
        try {
            String json = gson.toJson(payload);
            CryptoEngine.EncryptedResult encrypted = CryptoEngine.encrypt(pairingCode, json);
            // Format: v1|salt|iv|ciphertext
            return "v1|" + encrypted.salt + "|" + encrypted.iv + "|" + encrypted.ciphertext;
        } catch (Exception e) {
            throw new RuntimeException("Encoding failed", e);
        }
    }

    @Override
    public PairingPayload decode(String wireData) throws Exception {
        if (wireData == null) {
            throw new IllegalArgumentException("Data is null");
        }

        String dataToProcess = wireData;
        if (wireData.contains("%")) {
            try {
                dataToProcess = java.net.URLDecoder.decode(wireData, java.nio.charset.StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                // Ignore decoding error and try with raw data
            }
        }

        // Fix potential space-instead-of-plus issue from URL decoding
        String sanitizedData = dataToProcess.replace(" ", "+");
        
        if (!sanitizedData.startsWith("v1|")) {
            throw new IllegalArgumentException("Unsupported payload version");
        }
        
        String[] parts = sanitizedData.split("\\|");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid payload format");
        }

        CryptoEngine.EncryptedResult encrypted = new CryptoEngine.EncryptedResult(parts[1], parts[2], parts[3]);
        String json = CryptoEngine.decrypt(pairingCode, encrypted);
        return gson.fromJson(json, PairingPayload.class);
    }
}
