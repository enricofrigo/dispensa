package eu.frigo.dispensa.sync.webdav;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.sync.OutboxRepositoryImpl;
import eu.frigo.dispensa.sync.core.SyncTransport;
import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.store.SyncCursorStoreImpl;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import eu.frigo.dispensa.sync.webdav.client.WebDavClientFactory;

// Import legacy SyncManager for configuration constants
import eu.frigo.dispensa.sync.core.engine.SyncManager;

/**
 * Factory to conditionally instantiate the WebDAV SyncTransport.
 */
public class WebDavTransportFactory {

    /**
     * Creates a SyncTransport instance if WebDAV is enabled and configured.
     *
     * @param context Android context.
     * @return A SyncTransport implementation (WebDavSyncEngine) or null if not configured.
     */
    public static SyncTransport create(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        
        // 1. Check if sync is enabled
        boolean enabled = prefs.getBoolean(SyncManager.KEY_SYNC_ENABLED, false);
        if (!enabled) {
            return null;
        }

        // 2. Load WebDAV credentials
        String url = prefs.getString(SyncManager.KEY_WEBDAV_URL, "");
        String user = prefs.getString(SyncManager.KEY_WEBDAV_USER, "");
        String pass = prefs.getString(SyncManager.KEY_WEBDAV_PASS, "");
        boolean isShared = prefs.getBoolean(SyncManager.KEY_WEBDAV_MODE_SHARED, false);

        // 3. Validate configuration
        if (url.isEmpty() || (!isShared && (user.isEmpty() || pass.isEmpty()))) {
            return null;
        }

        // 4. Resolve pantry path (consistent with WebDavSyncProviderLoader)
        String path = prefs.getString(SyncManager.KEY_WEBDAV_PATH, SyncManager.DEFAULT_PATH);
        String pantryKey = prefs.getString(SyncManager.SYNC_WEBDAV_PANTRY_KEY, SyncManager.DEFAULT_MAIN_PANTRY);

        String normalizedBase = path.endsWith("/") ? path : path + "/";
        if (normalizedBase.startsWith("/")) {
            normalizedBase = normalizedBase.substring(1);
        }
        String pantryPath = normalizedBase + SyncManager.DEFAULT_PANTRY_PATH + pantryKey + "/";

        // 5. Get device ID
        String deviceId = InstallationIdProvider.getOrCreateInstallationId(context);

        // 6. Instantiate dependencies
        WebDavClient client = WebDavClientFactory.getInstance().getClient(context);
        AppDatabase db = AppDatabase.getDatabase(context);

        // 7. Create engine (which implements SyncTransport)
        return new WebDavSyncEngine(
                client,
                new SyncCursorStoreImpl(context),
                new OutboxRepositoryImpl(db),
                deviceId,
                pantryPath,
                db,
                context
        );
    }
}
