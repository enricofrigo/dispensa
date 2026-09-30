package eu.frigo.dispensa.sync.gdrive;

import android.content.Context;
import android.content.SharedPreferences;
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
import eu.frigo.dispensa.sync.gdrive.auth.GDriveAuthManager;
import eu.frigo.dispensa.sync.gdrive.client.GDriveClient;
import eu.frigo.dispensa.sync.gdrive.store.GDriveFolderStore;
import eu.frigo.dispensa.sync.gdrive.worker.GDriveSyncWorker;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class GDriveSyncProviderLoader implements SyncProviderLoader {

    @Override
    public String getProviderType() {
        return "gdrive";
    }

    @Override
    public Single<SyncProvider> load(Context context) {
        return Single.<SyncProvider>fromCallable(() -> {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean enabled = prefs.getBoolean(SyncManager.KEY_SYNC_ENABLED, false);

            GDriveAuthManager authManager = new GDriveAuthManager(context);
            if (enabled && authManager.isSignedIn()) {
                String deviceId = InstallationIdProvider.getOrCreateInstallationId(context);
                String syncedIdsStr = prefs.getString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, "");
                List<GDriveSyncProvider.SyncScope> scopes = new ArrayList<>();
                AppDatabase db = AppDatabase.getDatabase(context);
                GDriveClient client = new GDriveClient(authManager);

                if (!syncedIdsStr.isEmpty()) {
                    String[] ids = syncedIdsStr.split(",");
                    for (String idStr : ids) {
                        try {
                            int id = Integer.parseInt(idStr);
                            JoinedPantryConfig config = db.joinedPantryConfigDao().getConfigByDispensaId(id);

                            if (config != null && "gdrive".equals(config.providerId)) {
                                Dispensa disp = db.dispensaDao().getDispensaByIdSync(id);
                                String remoteId = (disp != null && disp.remoteId != null && !disp.remoteId.trim().isEmpty())
                                        ? disp.remoteId.trim()
                                        : (config.remotePantryId != null ? config.remotePantryId : String.valueOf(id));
                                String syncPath = SyncManager.getSyncPath(remoteId);

                                String rootFolderId = (config.url != null && !config.url.isEmpty()) ? config.url : "root";
                                GDriveFolderStore store = new GDriveFolderStore(client, rootFolderId);
                                scopes.add(new GDriveSyncProvider.SyncScope(id, syncPath, store));
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }

                if (scopes.isEmpty()) return null;
                return new GDriveSyncProvider(deviceId, scopes);
            }
            return null;
        }).subscribeOn(Schedulers.io());
    }

    @Override
    public Class<? extends ListenableWorker> getWorkerClass() {
        return GDriveSyncWorker.class;
    }
}
