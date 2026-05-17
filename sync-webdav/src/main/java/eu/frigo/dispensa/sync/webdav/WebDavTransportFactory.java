package eu.frigo.dispensa.sync.webdav;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.sync.OutboxRepositoryImpl;
import eu.frigo.dispensa.sync.core.SyncTransport;
import eu.frigo.dispensa.sync.core.TransportRegistry;
import eu.frigo.dispensa.sync.core.engine.CrDtSyncManager;
import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.store.SyncCursorStoreImpl;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;

/**
 * Factory for WebDAV transport.
 * Implements TransportFactory for registration in TransportRegistry.
 */
public class WebDavTransportFactory implements TransportRegistry.TransportFactory {

    private static final String TAG = "WebDavTransportFactory";

    private static final String PREF_WEBDAV_ENABLED = SyncManager.KEY_SYNC_ENABLED;
    private static final String PREF_WEBDAV_URL = SyncManager.KEY_WEBDAV_URL;
    private static final String PREF_WEBDAV_USER = SyncManager.KEY_WEBDAV_USER;
    private static final String PREF_WEBDAV_PASS = SyncManager.KEY_WEBDAV_PASS;
    private static final String PREF_WEBDAV_PATH = SyncManager.KEY_WEBDAV_PATH;

    @Override
    public SyncTransport create(Context context, CrDtSyncManager syncManager) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (!prefs.getBoolean(PREF_WEBDAV_ENABLED, false)) {
            Log.d(TAG, "WebDAV sync disabled");
            return null;
        }

        String url = prefs.getString(PREF_WEBDAV_URL, "");
        String user = prefs.getString(PREF_WEBDAV_USER, "");
        String pass = prefs.getString(PREF_WEBDAV_PASS, "");
        String path = prefs.getString(PREF_WEBDAV_PATH, SyncManager.DEFAULT_PATH);
        boolean isShared = prefs.getBoolean(SyncManager.KEY_WEBDAV_MODE_SHARED, false);

        if (url.isEmpty() || (!isShared && (user.isEmpty() || pass.isEmpty()))) {
            Log.d(TAG, "WebDAV credentials incomplete");
            return null;
        }

        try {
            String pantryKey = prefs.getString(SyncManager.SYNC_WEBDAV_PANTRY_KEY, SyncManager.DEFAULT_MAIN_PANTRY);
            String normalizedBase = path.endsWith("/") ? path : path + "/";
            if (normalizedBase.startsWith("/")) {
                normalizedBase = normalizedBase.substring(1);
            }
            String pantryPath = normalizedBase + SyncManager.DEFAULT_PANTRY_PATH + pantryKey + "/";

            String deviceId = InstallationIdProvider.getOrCreateInstallationId(context);
            WebDavClient client = new WebDavClient(url, user, pass);
            AppDatabase db = AppDatabase.getDatabase(context);

            WebDavSyncEngine engine = new WebDavSyncEngine(
                    client,
                    new SyncCursorStoreImpl(context),
                    new OutboxRepositoryImpl(db),
                    deviceId,
                    pantryPath,
                    db,
                    context
            );

            Log.d(TAG, "WebDAV transport created");
            return engine;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create WebDAV transport", e);
            return null;
        }
    }
}
