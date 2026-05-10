package eu.frigo.dispensa.sync.local;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import eu.frigo.dispensa.sync.core.SyncManager;
import eu.frigo.dispensa.sync.core.SyncTransport;

/**
 * Factory to conditionally instantiate the Local Network SyncTransport.
 */
public class LocalTransportFactory {

    public static final String PREF_LOCAL_SYNC_ENABLED = "sync_local_network_enabled";

    /**
     * Creates a LocalNetworkSyncTransport instance if local sync is enabled.
     *
     * @param context     Android context.
     * @param syncManager The sync manager instance.
     * @return A SyncTransport implementation or null if not enabled or syncManager is null.
     */
    public static SyncTransport create(Context context, SyncManager syncManager) {
        if (syncManager == null) {
            return null;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean enabled = prefs.getBoolean(PREF_LOCAL_SYNC_ENABLED, false);

        if (!enabled) {
            return null;
        }

        return new LocalNetworkSyncTransport(context, syncManager);
    }
}
