package eu.frigo.dispensa.sync.core.pairing;

public interface ShareLinkCodec {
    String generateLink(PairingPayload payload);
    PairingPayload parseLink(String link);
}
