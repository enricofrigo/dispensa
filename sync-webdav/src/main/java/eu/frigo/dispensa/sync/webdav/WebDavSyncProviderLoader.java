package eu.frigo.dispensa.sync.webdav;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import androidx.work.ListenableWorker;

import java.util.ArrayList;
import java.util.List;

import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.provider.SyncProvider;
import eu.frigo.dispensa.sync.core.provider.SyncProviderLoader;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import eu.frigo.dispensa.sync.webdav.client.WebDavClientFactory;
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
        boolean isShared = prefs.getBoolean(SyncManager.KEY_WEBDAV_MODE_SHARED, false);
        boolean enabled = prefs.getBoolean(SyncManager.KEY_SYNC_ENABLED, false);

        if (enabled && !url.isEmpty() && (!user.isEmpty() || isShared)) {
            String deviceId = InstallationIdProvider.getOrCreateInstallationId(context);
            String path = prefs.getString(SyncManager.KEY_WEBDAV_PATH, SyncManager.DEFAULT_PATH);
            
            String syncedIdsStr = prefs.getString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, "");
            List<WebDavSyncProvider.SyncScope> scopes = new ArrayList<>();
            
            if (!syncedIdsStr.isEmpty()) {
                String[] ids = syncedIdsStr.split(",");
                for (String idStr : ids) {
                    try {
                        int id = Integer.parseInt(idStr);
                        String pantryName = prefs.getString(SyncManager.SYNC_WEBDAV_PANTRY_NAME + "_" + id, "Dispensa");
                        
                        String normalizedBase = path.endsWith("/") ? path : path + "/";
                        if (normalizedBase.startsWith("/")) normalizedBase = normalizedBase.substring(1);
                        String pantryPath = normalizedBase + SyncManager.getSyncPath(pantryName);
                        
                        scopes.add(new WebDavSyncProvider.SyncScope(id, pantryPath));
                    } catch (NumberFormatException ignored) {}
                }
            } else {
                // Fallback retrocompatibilità
                String pantryName = prefs.getString(SyncManager.SYNC_WEBDAV_PANTRY_NAME, "Dispensa");
                int dispensaId = prefs.getInt(SyncManager.SYNC_WEBDAV_DISPENSA_ID, 1);
                
                String normalizedBase = path.endsWith("/") ? path : path + "/";
                if (normalizedBase.startsWith("/")) normalizedBase = normalizedBase.substring(1);
                String pantryPath = normalizedBase + SyncManager.getSyncPath(pantryName);
                
                scopes.add(new WebDavSyncProvider.SyncScope(dispensaId, pantryPath));
            }

            WebDavClient client = WebDavClientFactory.getInstance().getClient(context);
            WebDavRemoteStoreImpl remoteStore = new WebDavRemoteStoreImpl(client);

            return new WebDavSyncProvider(remoteStore, client, deviceId, scopes);
        }
        return null;
    }

    @Override
    public Class<? extends ListenableWorker> getWorkerClass() {
        return WebDavSyncWorker.class;
    }
}
