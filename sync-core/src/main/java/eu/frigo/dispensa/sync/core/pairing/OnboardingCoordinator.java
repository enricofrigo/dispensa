package eu.frigo.dispensa.sync.core.pairing;

import io.reactivex.rxjava3.core.Single;

public class OnboardingCoordinator {
    
    public static String generatePairingCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
            if (i == 1) sb.append("-"); // Format: AB-CDEF
        }
        return sb.toString();
    }
    
    public Single<PairingPayload> joinPantry(String pairingCode, String qrData) {
        return Single.fromCallable(() -> {
            // Remove dash if present for decryption
            String cleanCode = pairingCode.replace("-", "").toUpperCase();
            PairingPayloadCodec codec = new PairingPayloadCodecImpl(cleanCode);
            return codec.decode(qrData);
        });
    }
}
