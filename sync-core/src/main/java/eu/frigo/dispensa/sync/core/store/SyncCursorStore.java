package eu.frigo.dispensa.sync.core.store;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
        FallbackState.clear(this, dispensaId);
    }

    default Set<String> getAppliedEventFiles(int dispensaId) {
        return FallbackState.getAppliedEventFiles(this, dispensaId);
    }

    default void markEventFileApplied(String eventFile, int dispensaId) {
        FallbackState.markEventFileApplied(this, eventFile, dispensaId);
    }

    default void retainAppliedEventFiles(Collection<String> activeEventFiles, int dispensaId) {
        FallbackState.retainAppliedEventFiles(this, activeEventFiles, dispensaId);
    }

    default String getLastAppliedSnapshotId(int dispensaId) {
        return FallbackState.getLastAppliedSnapshotId(this, dispensaId);
    }

    default void updateLastAppliedSnapshotId(String snapshotId, int dispensaId) {
        FallbackState.updateLastAppliedSnapshotId(this, snapshotId, dispensaId);
    }

    /**
     * In-memory fallback state for custom/test SyncCursorStore implementations
     * that only implement the legacy 3 methods.
     */
    final class FallbackState {
        private static final Map<String, Set<String>> APPLIED_EVENTS = new ConcurrentHashMap<>();
        private static final Map<String, String> LAST_SNAPSHOT = new ConcurrentHashMap<>();

        private static String key(SyncCursorStore store, int dispensaId) {
            return System.identityHashCode(store) + ":" + dispensaId;
        }

        static Set<String> getAppliedEventFiles(SyncCursorStore store, int dispensaId) {
            if (store.getLastSyncTimestamp(dispensaId) == 0L && !LAST_SNAPSHOT.containsKey(key(store, dispensaId))) {
                APPLIED_EVENTS.remove(key(store, dispensaId));
            }
            Set<String> set = APPLIED_EVENTS.get(key(store, dispensaId));
            return set != null ? new HashSet<>(set) : Collections.emptySet();
        }

        static void markEventFileApplied(SyncCursorStore store, String eventFile, int dispensaId) {
            if (eventFile == null) return;
            APPLIED_EVENTS.computeIfAbsent(
                    key(store, dispensaId),
                    k -> Collections.newSetFromMap(new ConcurrentHashMap<>())
            ).add(eventFile);
        }

        static void retainAppliedEventFiles(SyncCursorStore store, Collection<String> activeEventFiles, int dispensaId) {
            Set<String> set = APPLIED_EVENTS.get(key(store, dispensaId));
            if (set != null) {
                if (activeEventFiles == null || activeEventFiles.isEmpty()) {
                    set.clear();
                } else {
                    set.retainAll(activeEventFiles);
                }
            }
        }

        static String getLastAppliedSnapshotId(SyncCursorStore store, int dispensaId) {
            if (store.getLastSyncTimestamp(dispensaId) == 0L) {
                LAST_SNAPSHOT.remove(key(store, dispensaId));
                return null;
            }
            return LAST_SNAPSHOT.get(key(store, dispensaId));
        }

        static void updateLastAppliedSnapshotId(SyncCursorStore store, String snapshotId, int dispensaId) {
            String k = key(store, dispensaId);
            if (snapshotId == null) {
                LAST_SNAPSHOT.remove(k);
            } else {
                LAST_SNAPSHOT.put(k, snapshotId);
            }
        }

        static void clear(SyncCursorStore store, int dispensaId) {
            String k = key(store, dispensaId);
            APPLIED_EVENTS.remove(k);
            LAST_SNAPSHOT.remove(k);
        }
    }
}
