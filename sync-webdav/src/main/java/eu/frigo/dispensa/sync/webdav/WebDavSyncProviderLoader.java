package eu.frigo.dispensa.sync.webdav;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import androidx.work.ListenableWorker;

import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.provider.SyncProvider;
import eu.frigo.dispensa.sync.core.provider.SyncProviderLoader;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import eu.frigo.dispensa.sync.webdav.worker.WebDavSyncWorker;

public class WebDavSyncProviderLoader implements SyncProviderLoader {

    @Override
    public String getProviderType() {
        return "webdav";
    }

    @Override
    public SyncProvider load(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String url = prefs.getString(SyncManager.KEY_WEBDAV_URL, "");
        String user = prefs.getString(SyncManager.KEY_WEBDAV_USER, "");
        String pass = prefs.getString(SyncManager.KEY_WEBDAV_PASS, "");
        boolean enabled = prefs.getBoolean(SyncManager.KEY_SYNC_ENABLED, false);

        if (enabled && !url.isEmpty() && !user.isEmpty()) {
            String deviceId = InstallationIdProvider.getOrCreateInstallationId(context);
            String path = prefs.getString(SyncManager.KEY_WEBDAV_URL, "");
            path = prefs.getString(SyncManager.KEY_WEBDAV_PATH, SyncManager.DEFAULT_PATH);
            String pantryKey = prefs.getString(SyncManager.SYNC_WEBDAV_PANTRY_KEY, SyncManager.DEFAULT_MAIN_PANTRY);

            String normalizedBase = path.endsWith("/") ? path : path + "/";
            if (normalizedBase.startsWith("/")) normalizedBase = normalizedBase.substring(1);
            String pantryPath = normalizedBase + SyncManager.DEFAULT_PANTRY_PATH + pantryKey + "/";

            WebDavClient client = new WebDavClient(url, user, pass);
            WebDavRemoteStoreImpl remoteStore = new WebDavRemoteStoreImpl(client);

            return new WebDavSyncProvider("webdav", remoteStore, client, deviceId, pantryPath);
        }
        return null;
    }

    @Override
    public Class<? extends ListenableWorker> getWorkerClass() {
        return WebDavSyncWorker.class;
    }
}
