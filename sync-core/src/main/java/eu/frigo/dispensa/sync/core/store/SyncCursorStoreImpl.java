package eu.frigo.dispensa.sync.core.store;

import android.content.Context;
import android.content.SharedPreferences;

public class SyncCursorStoreImpl implements SyncCursorStore {
    public static final String PREF_NAME = "sync_cursor_prefs";
    public static final String KEY_LAST_SYNC = "last_sync_timestamp";
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

    @Override
    public long getLastSyncTimestamp() {
        return prefs.getLong(getKey(dispensaId), 0);
    }

    @Override
    public long getLastSyncTimestamp(int id) {
        return prefs.getLong(getKey(id), 0);
    }

    @Override
    public void updateLastSyncTimestamp(long timestamp) {
        prefs.edit().putLong(getKey(dispensaId), timestamp).apply();
    }

    @Override
    public void updateLastSyncTimestamp(long timestamp, int id) {
        prefs.edit().putLong(getKey(id), timestamp).apply();
    }

    @Override
    public void clear() {
        if (dispensaId > 0) {
            prefs.edit().remove(getKey(dispensaId)).apply();
        } else {
            prefs.edit().clear().apply();
        }
    }

    @Override
    public void clear(int id) {
        if (id > 0) {
            prefs.edit().remove(getKey(id)).apply();
        } else {
            prefs.edit().clear().apply();
        }
    }
}
