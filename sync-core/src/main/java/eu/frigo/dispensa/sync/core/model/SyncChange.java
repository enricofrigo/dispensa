package eu.frigo.dispensa.sync.core.model;

import androidx.annotation.NonNull;

/**
 * Data transfer object representing a database change for CRDT synchronization.
 */
public class SyncChange {
    public String tbl;
    public String pkVal;
    public String op;
    public String rowJson;
    public long clock;
    public String deviceId;

    public SyncChange() {
        // Required for Gson
    }

    @NonNull
    @Override
    public String toString() {
        return "SyncChange{" +
                "tbl='" + tbl + '\'' +
                ", pkVal='" + pkVal + '\'' +
                ", op='" + op + '\'' +
                ", rowJson='" + (rowJson != null ? "[JSON]" : "null") + '\'' +
                ", clock=" + clock +
                ", deviceId='" + deviceId + '\'' +
                '}';
    }
}
