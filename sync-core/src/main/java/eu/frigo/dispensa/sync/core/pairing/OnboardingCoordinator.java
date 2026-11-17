package eu.frigo.dispensa.sync.core.pairing;

import java.security.SecureRandom;
import io.reactivex.rxjava3.core.Single;

public class OnboardingCoordinator {
    private static final String PIN_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int PIN_LENGTH = 6;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static String generatePairingCode() {
        StringBuilder sb = new StringBuilder(PIN_LENGTH);
        for (int i = 0; i < PIN_LENGTH; i++) {
            sb.append(PIN_ALPHABET.charAt(SECURE_RANDOM.nextInt(PIN_ALPHABET.length())));
        }
        return sb.toString();
    }

    public static String normalizePairingCode(String rawCode) {
        if (rawCode == null) return "";
        return rawCode.trim().toUpperCase(java.util.Locale.ROOT)
                .replace("-", "")
                .replace(" ", "");
    }

    public Single<PairingPayload> joinPantry(String pairingCode, String qrData) {
        return Single.fromCallable(() -> {
            PairingPayloadCodec codec = new PairingPayloadCodecImpl(pairingCode);
            return codec.decode(qrData);
        });
    }
}
