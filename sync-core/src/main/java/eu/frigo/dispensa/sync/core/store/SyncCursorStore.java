package eu.frigo.dispensa.sync.core.store;

public interface SyncCursorStore {
    long getLastSyncTimestamp();
    void updateLastSyncTimestamp(long timestamp);
    void clear();

    default long getLastSyncTimestamp(int dispensaId) {
        return getLastSyncTimestamp();
    }

    default void updateLastSyncTimestamp(long timestamp, int dispensaId) {
        updateLastSyncTimestamp(timestamp);
    }

    default void clear(int dispensaId) {
        clear();
    }
}
