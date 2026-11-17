package eu.frigo.dispensa.sync.core.store;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SyncCursorStoreImpl implements SyncCursorStore {
    public static final String PREF_NAME = "sync_cursor_prefs";
    public static final String KEY_LAST_SYNC = "last_sync_timestamp";
    public static final String KEY_APPLIED_EVENTS = "applied_event_files";
    public static final String KEY_LAST_SNAPSHOT = "last_applied_snapshot_id";
    private final SharedPreferences prefs;
    private final int dispensaId;

    public SyncCursorStoreImpl(Context context) {
        this(context, 0);
    }

    public SyncCursorStoreImpl(Context context, int dispensaId) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.dispensaId = dispensaId;
    }

    private String getKey(int id) {
        return id > 0 ? (KEY_LAST_SYNC + "_" + id) : KEY_LAST_SYNC;
    }

    private String getEventsKey(int id) {
        int effectiveId = id > 0 ? id : dispensaId;
        return effectiveId > 0 ? (KEY_APPLIED_EVENTS + "_" + effectiveId) : KEY_APPLIED_EVENTS;
    }

    private String getSnapshotKey(int id) {
        int effectiveId = id > 0 ? id : dispensaId;
        return effectiveId > 0 ? (KEY_LAST_SNAPSHOT + "_" + effectiveId) : KEY_LAST_SNAPSHOT;
    }

    @Override
    public long getLastSyncTimestamp() {
        return prefs.getLong(getKey(dispensaId), 0);
    }

    @Override
    public long getLastSyncTimestamp(int id) {
        return prefs.getLong(getKey(id > 0 ? id : dispensaId), 0);
    }

    @Override
    public void updateLastSyncTimestamp(long timestamp) {
        prefs.edit().putLong(getKey(dispensaId), timestamp).apply();
    }

    @Override
    public void updateLastSyncTimestamp(long timestamp, int id) {
        prefs.edit().putLong(getKey(id > 0 ? id : dispensaId), timestamp).apply();
    }

    @Override
    public Set<String> getAppliedEventFiles(int id) {
        Set<String> stored = prefs.getStringSet(getEventsKey(id), Collections.emptySet());
        return stored != null ? new HashSet<>(stored) : new HashSet<>();
    }

    @Override
    public void markEventFileApplied(String eventFile, int id) {
        if (eventFile == null) return;
        Set<String> current = getAppliedEventFiles(id);
        current.add(eventFile);
        prefs.edit().putStringSet(getEventsKey(id), current).apply();
    }

    @Override
    public void retainAppliedEventFiles(Collection<String> activeEventFiles, int id) {
        Set<String> current = getAppliedEventFiles(id);
        if (activeEventFiles == null || activeEventFiles.isEmpty()) {
            current.clear();
        } else {
            current.retainAll(activeEventFiles);
        }
        prefs.edit().putStringSet(getEventsKey(id), current).apply();
    }

    @Override
    public String getLastAppliedSnapshotId(int id) {
        return prefs.getString(getSnapshotKey(id), null);
    }

    @Override
    public void updateLastAppliedSnapshotId(String snapshotId, int id) {
        if (snapshotId == null) {
            prefs.edit().remove(getSnapshotKey(id)).apply();
        } else {
            prefs.edit().putString(getSnapshotKey(id), snapshotId).apply();
        }
    }

    @Override
    public void clear() {
        clear(dispensaId);
    }

    @Override
    public void clear(int id) {
        int effectiveId = id > 0 ? id : dispensaId;
        if (effectiveId > 0) {
            prefs.edit()
                    .remove(getKey(effectiveId))
                    .remove(getEventsKey(effectiveId))
                    .remove(getSnapshotKey(effectiveId))
                    .apply();
        } else {
            prefs.edit().clear().apply();
        }
    }
}
