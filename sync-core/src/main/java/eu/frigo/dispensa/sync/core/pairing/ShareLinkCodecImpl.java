package eu.frigo.dispensa.sync.core.pairing;

public class ShareLinkCodecImpl implements ShareLinkCodec {
    private static final String SCHEME = "dispensa://sync/join?data=";
    private final PairingPayloadCodec codec;

    public ShareLinkCodecImpl(PairingPayloadCodec codec) {
        this.codec = codec;
    }

    @Override
    public String generateLink(PairingPayload payload) {
        String encoded = codec.encode(payload);
        return SCHEME + encoded;
    }

    @Override
    public PairingPayload parseLink(String link) {
        if (!link.startsWith(SCHEME)) {
            throw new IllegalArgumentException("Invalid link scheme");
        }
        String encoded = link.substring(SCHEME.length());
        try {
            return codec.decode(encoded);
        } catch (Exception e) {
            throw new RuntimeException("Decoding failed", e);
        }
    }
}
