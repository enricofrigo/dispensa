package eu.frigo.dispensa.sync.webdav.client;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import eu.frigo.dispensa.sync.core.engine.SyncManager;

/**
 * Singleton provider for WebDavClient.
 * Manages a single instance initialized from preferences.
 */
public class WebDavClientFactory {
    private static WebDavClientFactory instance;
    private WebDavClient client;

    private WebDavClientFactory() {}

    public static synchronized WebDavClientFactory getInstance() {
        if (instance == null) {
            instance = new WebDavClientFactory();
        }
        return instance;
    }

    /**
     * Returns the singleton WebDavClient instance.
     * If not already initialized, it creates it using the current SharedPreferences.
     */
    public synchronized WebDavClient getClient(Context context) {
        if (client == null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String url = prefs.getString(SyncManager.KEY_WEBDAV_URL, "");
            String user = prefs.getString(SyncManager.KEY_WEBDAV_USER, "");
            String pass = prefs.getString(SyncManager.KEY_WEBDAV_PASS, "");
            
            // Note: Even if empty, we create the client. 
            // The caller should check if sync is enabled/configured.
            client = new WebDavClient(url, user, pass);
        }
        return client;
    }

    /**
     * Explicitly initializes or updates the client with specific credentials.
     * Useful during onboarding or when settings change.
     */
    public synchronized WebDavClient getClient(String url, String user, String pass) {
        client = new WebDavClient(url, user, pass);
        return client;
    }

    /**
     * Resets the client instance so it will be recreated on next access.
     */
    public synchronized void reset() {
        client = null;
    }
}
