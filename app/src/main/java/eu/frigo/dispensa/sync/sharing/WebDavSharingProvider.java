package eu.frigo.dispensa.sync.sharing;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.store.SharedFolderStore;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import eu.frigo.dispensa.sync.webdav.client.WebDavClientFactory;
import eu.frigo.dispensa.sync.webdav.store.WebDavFolderStore;

public class WebDavSharingProvider implements SharingProvider {

    @Override
    public String getProviderId() {
        return "webdav";
    }

    @Override
    public String getDisplayName(Context context) {
        return context.getString(R.string.provider_webdav);
    }

    @Override
    public boolean isConfigured(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String url = prefs.getString(SyncManager.KEY_WEBDAV_URL, "");
        String user = prefs.getString(SyncManager.KEY_WEBDAV_USER, "");
        String pass = prefs.getString(SyncManager.KEY_WEBDAV_PASS, "");
        boolean isShared = prefs.getBoolean(SyncManager.KEY_WEBDAV_MODE_SHARED, false);

        return !url.isEmpty() && (!user.isEmpty() || isShared) && !pass.isEmpty();
    }

    @Override
    public SharedFolderStore createStore(Context context, Dispensa dispensa) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String url = prefs.getString(SyncManager.KEY_WEBDAV_URL, "");
        String user = prefs.getString(SyncManager.KEY_WEBDAV_USER, "");
        String pass = prefs.getString(SyncManager.KEY_WEBDAV_PASS, "");

        WebDavClient client = WebDavClientFactory.getInstance().getClient(url, user, pass);
        return new WebDavFolderStore(client);
    }

    @Override
    public String getPantryPath(Context context, Dispensa dispensa) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String path = prefs.getString(SyncManager.KEY_WEBDAV_PATH, SyncManager.DEFAULT_PATH);
        String base = (path == null) ? "" : (path.endsWith("/") ? path : path + "/");
        if (base.startsWith("/")) base = base.substring(1);
        String remoteId = (dispensa.remoteId != null && !dispensa.remoteId.trim().isEmpty())
                ? dispensa.remoteId.trim()
                : String.valueOf(dispensa.id);
        return base + SyncManager.getSyncPath(remoteId);
    }

    @Override
    public JoinedPantryConfig createJoinedConfig(Context context, Dispensa dispensa) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String url = prefs.getString(SyncManager.KEY_WEBDAV_URL, "");
        String user = prefs.getString(SyncManager.KEY_WEBDAV_USER, "");
        String pass = prefs.getString(SyncManager.KEY_WEBDAV_PASS, "");
        String path = prefs.getString(SyncManager.KEY_WEBDAV_PATH, SyncManager.DEFAULT_PATH);
        boolean isShared = prefs.getBoolean(SyncManager.KEY_WEBDAV_MODE_SHARED, false);
        String pantryKey = prefs.getString(SyncManager.SYNC_WEBDAV_PANTRY_KEY, "");

        return new JoinedPantryConfig(
                dispensa.id,
                url,
                user,
                pass,
                path,
                isShared,
                pantryKey
        );
    }
}
