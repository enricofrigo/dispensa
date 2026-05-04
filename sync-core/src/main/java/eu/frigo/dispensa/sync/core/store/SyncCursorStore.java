package eu.frigo.dispensa.sync.core.store;

public interface SyncCursorStore {
    long getLastSyncTimestamp();
    void updateLastSyncTimestamp(long timestamp);
    void clear();
}
