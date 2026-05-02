package eu.frigo.dispensa.sync.core.pairing;

public interface PairingPayloadCodec {
    String encode(PairingPayload payload);
    PairingPayload decode(String encryptedData) throws Exception;
}
