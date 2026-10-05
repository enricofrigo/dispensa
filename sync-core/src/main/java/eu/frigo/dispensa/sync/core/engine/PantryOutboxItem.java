package eu.frigo.dispensa.sync.core.engine;

public class PantryOutboxItem {
    public final String syncId;
    public final String dataType;
    public final String payloadJson;
    public final long timestamp;

    public PantryOutboxItem(String syncId, String dataType, String payloadJson, long timestamp) {
        this.syncId = syncId;
        this.dataType = dataType;
        this.payloadJson = payloadJson;
        this.timestamp = timestamp;
    }
}
