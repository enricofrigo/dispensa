package eu.frigo.dispensa.sync.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.preference.PreferenceManager;
import androidx.work.ListenableWorker;

import java.util.ArrayList;
import java.util.List;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.provider.SyncProvider;
import eu.frigo.dispensa.sync.core.provider.SyncProviderLoader;
import eu.frigo.dispensa.sync.local.store.SafFolderStore;
import eu.frigo.dispensa.sync.local.worker.LocalSafSyncWorker;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class LocalSafSyncProviderLoader implements SyncProviderLoader {

    public static final String PREF_KEY_SAF_URI = "pref_sync_saf_uri";

    @Override
    public String getProviderType() {
        return "local_saf";
    }

    @Override
    public Single<SyncProvider> load(Context context) {
        return Single.<SyncProvider>fromCallable(() -> {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean enabled = prefs.getBoolean(SyncManager.KEY_SYNC_ENABLED, false);

            if (enabled) {
                String deviceId = InstallationIdProvider.getOrCreateInstallationId(context);
                String syncedIdsStr = prefs.getString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, "");
                List<LocalSafSyncProvider.SyncScope> scopes = new ArrayList<>();
                AppDatabase db = AppDatabase.getDatabase(context);

                String defaultUriStr = prefs.getString(PREF_KEY_SAF_URI, null);

                if (!syncedIdsStr.isEmpty()) {
                    String[] ids = syncedIdsStr.split(",");
                    for (String idStr : ids) {
                        try {
                            int id = Integer.parseInt(idStr);
                            JoinedPantryConfig config = db.joinedPantryConfigDao().getConfigByDispensaId(id);

                            String uriStr = defaultUriStr;
                            if (config != null && "local_saf".equals(config.providerId) && config.url != null) {
                                uriStr = config.url;
                            }

                            if (uriStr != null && !uriStr.isEmpty()) {
                                Uri uri = Uri.parse(uriStr);
                                Dispensa disp = db.dispensaDao().getDispensaByIdSync(id);
                                String remoteId = (disp != null && disp.remoteId != null && !disp.remoteId.trim().isEmpty()) ? disp.remoteId.trim() : String.valueOf(id);
                                String syncPath = SyncManager.getSyncPath(remoteId);

                                SafFolderStore store = new SafFolderStore(context, uri);
                                scopes.add(new LocalSafSyncProvider.SyncScope(id, syncPath, store));
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }

                if (scopes.isEmpty()) return null;
                return new LocalSafSyncProvider(deviceId, scopes);
            }
            return null;
        }).subscribeOn(Schedulers.io());
    }

    @Override
    public Class<? extends ListenableWorker> getWorkerClass() {
        return LocalSafSyncWorker.class;
    }
}
