package eu.frigo.dispensa.sync.webdav.client;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Objects;
import androidx.preference.PreferenceManager;
import eu.frigo.dispensa.sync.core.crypto.CredentialCipher;
import eu.frigo.dispensa.sync.core.engine.SyncManager;

/**
 * Singleton provider for WebDavClient.
 * Manages a single instance initialized from preferences.
 */
public class WebDavClientFactory {
    private static WebDavClientFactory instance;
    private WebDavClient client;
    private String lastUrl;
    private String lastUser;
    private String lastPass;

    private WebDavClientFactory() {}

    public static synchronized WebDavClientFactory getInstance() {
        if (instance == null) {
            instance = new WebDavClientFactory();
        }
        return instance;
    }

    /**
     * Returns the singleton WebDavClient instance.
     * Recreates it if not initialized or if preferences changed.
     */
    public synchronized WebDavClient getClient(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String url = prefs.getString(SyncManager.KEY_WEBDAV_URL, "");
        String user = prefs.getString(SyncManager.KEY_WEBDAV_USER, "");
        String rawPass = prefs.getString(SyncManager.KEY_WEBDAV_PASS, "");
        if (rawPass != null && !rawPass.isEmpty() && !rawPass.startsWith(CredentialCipher.PREFIX)) {
            String encrypted = CredentialCipher.encryptIfNeeded(rawPass);
            prefs.edit().putString(SyncManager.KEY_WEBDAV_PASS, encrypted).apply();
        }
        String pass = CredentialCipher.decryptIfNeeded(rawPass);

        if (client == null || !Objects.equals(url, lastUrl) || !Objects.equals(user, lastUser) || !Objects.equals(pass, lastPass)) {
            lastUrl = url;
            lastUser = user;
            lastPass = pass;
            client = new WebDavClient(url, user, pass);
        }
        return client;
    }

    /**
     * Explicitly initializes or updates the client with specific credentials.
     * Useful during onboarding or when settings change.
     */
    public synchronized WebDavClient getClient(String url, String user, String pass) {
        String plainPass = CredentialCipher.decryptIfNeeded(pass);
        lastUrl = url;
        lastUser = user;
        lastPass = plainPass;
        client = new WebDavClient(url, user, plainPass);
        return client;
    }

    /**
     * Resets the client instance so it will be recreated on next access.
     */
    public synchronized void reset() {
        client = null;
        lastUrl = null;
        lastUser = null;
        lastPass = null;
    }
}
