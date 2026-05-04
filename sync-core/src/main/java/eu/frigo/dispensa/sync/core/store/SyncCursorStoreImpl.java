package eu.frigo.dispensa.sync.core.store;

import android.content.Context;
import android.content.SharedPreferences;

public class SyncCursorStoreImpl implements SyncCursorStore {
    private static final String PREF_NAME = "sync_cursor_prefs";
    private static final String KEY_LAST_SYNC = "last_sync_timestamp";
    private final SharedPreferences prefs;

    public SyncCursorStoreImpl(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public long getLastSyncTimestamp() {
        return prefs.getLong(KEY_LAST_SYNC, 0);
    }

    @Override
    public void updateLastSyncTimestamp(long timestamp) {
        prefs.edit().putLong(KEY_LAST_SYNC, timestamp).apply();
    }

    @Override
    public void clear() {
        prefs.edit().clear().apply();
    }
}
