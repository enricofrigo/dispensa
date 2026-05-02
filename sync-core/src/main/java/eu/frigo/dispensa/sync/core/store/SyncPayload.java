package eu.frigo.dispensa.sync.core.store;

public class SyncPayload {
    private final String syncId;
    private final String dataType;
    private final String content; // JSON representation
    private final long timestamp;

    public SyncPayload(String syncId, String dataType, String content, long timestamp) {
        this.syncId = syncId;
        this.dataType = dataType;
        this.content = content;
        this.timestamp = timestamp;
    }

    public String getSyncId() {
        return syncId;
    }

    public String getDataType() {
        return dataType;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
