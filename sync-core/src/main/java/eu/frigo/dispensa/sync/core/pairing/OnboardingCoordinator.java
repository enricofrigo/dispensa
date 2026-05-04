package eu.frigo.dispensa.sync.core.pairing;

import android.util.Log;

import io.reactivex.rxjava3.core.Single;

public class OnboardingCoordinator {
    
    public static String generatePairingCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    
    public Single<PairingPayload> joinPantry(String pairingCode, String qrData) {
        return Single.fromCallable(() -> {
            PairingPayloadCodec codec = new PairingPayloadCodecImpl(pairingCode.trim());
            return codec.decode(qrData);
        });
    }
}
