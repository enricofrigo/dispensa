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
import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import eu.frigo.dispensa.sync.webdav.client.WebDavClientFactory;
import eu.frigo.dispensa.sync.webdav.worker.WebDavSyncWorker;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class WebDavSyncProviderLoader implements SyncProviderLoader {

    @Override
    public String getProviderType() {
        return "webdav";
    }

    @Override
    public Single<SyncProvider> load(Context context) {
        return Single.<SyncProvider>fromCallable(() -> {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean enabled = prefs.getBoolean(SyncManager.KEY_SYNC_ENABLED, false);

            if (enabled) {
                String deviceId = InstallationIdProvider.getOrCreateInstallationId(context);
                String syncedIdsStr = prefs.getString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, "");
                List<WebDavSyncProvider.SyncScope> scopes = new ArrayList<>();
                AppDatabase db = AppDatabase.getDatabase(context);

                if (!syncedIdsStr.isEmpty()) {
                    String[] ids = syncedIdsStr.split(",");
                    for (String idStr : ids) {
                        try {
                            int id = Integer.parseInt(idStr);
                            JoinedPantryConfig config = db.joinedPantryConfigDao().getConfigByDispensaId(id);
                            
                            String url, user, pass, path;
                            boolean isShared;
                            String pantryName;

                            if (config != null) {
                                url = config.url;
                                user = config.username;
                                pass = config.password;
                                path = config.path;
                                isShared = config.isShared;
                                pantryName = prefs.getString(SyncManager.SYNC_WEBDAV_PANTRY_NAME + "_" + id, "Dispensa");
                            } else {
                                // Fallback to global prefs if no specific config exists
                                url = prefs.getString(SyncManager.KEY_WEBDAV_URL, "");
                                user = prefs.getString(SyncManager.KEY_WEBDAV_USER, "");
                                pass = prefs.getString(SyncManager.KEY_WEBDAV_PASS, "");
                                path = prefs.getString(SyncManager.KEY_WEBDAV_PATH, SyncManager.DEFAULT_PATH);
                                isShared = prefs.getBoolean(SyncManager.KEY_WEBDAV_MODE_SHARED, false);
                                pantryName = prefs.getString(SyncManager.SYNC_WEBDAV_PANTRY_NAME + "_" + id, "Dispensa");
                            }

                            if (!url.isEmpty() && (!user.isEmpty() || isShared)) {
                                String base = (path == null) ? "" : (path.endsWith("/") ? path : path + "/");
                                if (base.startsWith("/")) base = base.substring(1);
                                String pantryPath = base + SyncManager.getSyncPath(pantryName);
                                
                                // Create a temporary client for this scope's credentials
                                WebDavClient scopeClient = new WebDavClient(url, user, pass);
                                scopes.add(new WebDavSyncProvider.SyncScope(id, pantryPath, scopeClient));
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
                
                if (scopes.isEmpty()) return null;

                // The main client is still used for general purposes, but engines use scope-specific clients
                WebDavClient mainClient = WebDavClientFactory.getInstance().getClient(context);
                WebDavRemoteStoreImpl remoteStore = new WebDavRemoteStoreImpl(mainClient);

                return new WebDavSyncProvider(remoteStore, mainClient, deviceId, scopes);
            }
            return null;
        }).subscribeOn(Schedulers.io());
    }

    @Override
    public Class<? extends ListenableWorker> getWorkerClass() {
        return WebDavSyncWorker.class;
    }
}
