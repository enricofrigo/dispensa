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
    public int getIconResId() {
        return R.drawable.ic_provider_webdav;
    }

    @Override
    public android.content.Intent getConfigIntent(Context context) {
        return new android.content.Intent(context, eu.frigo.dispensa.activity.SyncWebDavConfigActivity.class);
    }

    @Override
    public String getSummary(Context context) {
        if (!isConfigured(context)) {
            return context.getString(R.string.sync_not_configured);
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String url = prefs.getString(SyncManager.KEY_WEBDAV_URL, "");
        return url;
    }

    @Override
    public SharedFolderStore createStore(Context context, Dispensa dispensa) {
        return createStore(context, dispensa, null);
    }

    @Override
    public SharedFolderStore createStore(Context context, Dispensa dispensa, JoinedPantryConfig config) {
        if (config != null && config.url != null && !config.url.isEmpty()) {
            WebDavClient client = WebDavClientFactory.getInstance().getClient(config.url, config.username, config.password);
            return new WebDavFolderStore(client);
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String url = prefs.getString(SyncManager.KEY_WEBDAV_URL, "");
        String user = prefs.getString(SyncManager.KEY_WEBDAV_USER, "");
        String pass = prefs.getString(SyncManager.KEY_WEBDAV_PASS, "");

        WebDavClient client = WebDavClientFactory.getInstance().getClient(url, user, pass);
        return new WebDavFolderStore(client);
    }

    @Override
    public String getPantryPath(Context context, Dispensa dispensa) {
        return getPantryPath(context, dispensa, null);
    }

    @Override
    public String getPantryPath(Context context, Dispensa dispensa, JoinedPantryConfig config) {
        String path = (config != null && config.path != null)
                ? config.path
                : PreferenceManager.getDefaultSharedPreferences(context).getString(SyncManager.KEY_WEBDAV_PATH, SyncManager.DEFAULT_PATH);
        String base = (path == null) ? "" : (path.endsWith("/") ? path : path + "/");
        if (base.startsWith("/")) base = base.substring(1);
        String remoteId = (dispensa != null && dispensa.remoteId != null && !dispensa.remoteId.trim().isEmpty())
                ? dispensa.remoteId.trim()
                : (config != null && config.remotePantryId != null ? config.remotePantryId : (dispensa != null ? String.valueOf(dispensa.id) : "default"));
        String syncPath = SyncManager.getSyncPath(remoteId);
        String standardPath = base.endsWith(SyncManager.APP_FOLDER_NAME + "/")
                ? base + syncPath
                : base + SyncManager.APP_FOLDER_NAME + "/" + syncPath;

        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            try {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                String url = (config != null && config.url != null) ? config.url : prefs.getString(SyncManager.KEY_WEBDAV_URL, "");
                String user = (config != null && config.username != null) ? config.username : prefs.getString(SyncManager.KEY_WEBDAV_USER, "");
                String pass = (config != null && config.password != null) ? config.password : prefs.getString(SyncManager.KEY_WEBDAV_PASS, "");
                if (!url.isEmpty()) {
                    WebDavClient client = WebDavClientFactory.getInstance().getClient(url, user, pass);
                    try (okhttp3.Response checkResp = client.head(standardPath + SyncManager.MANIFEST_JSON)) {
                        if (!checkResp.isSuccessful()) {
                            try (okhttp3.Response legacyResp = client.head(base + syncPath + SyncManager.MANIFEST_JSON)) {
                                if (legacyResp.isSuccessful()) {
                                    return base + syncPath;
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        return standardPath;
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
        String remoteId = (dispensa != null && dispensa.remoteId != null && !dispensa.remoteId.trim().isEmpty())
                ? dispensa.remoteId.trim()
                : (dispensa != null ? String.valueOf(dispensa.id) : "default");

        JoinedPantryConfig config = new JoinedPantryConfig(
                dispensa != null ? dispensa.id : 0,
                url,
                user,
                pass,
                path,
                isShared,
                pantryKey
        );
        config.remotePantryId = remoteId;
        return config;
    }
}
