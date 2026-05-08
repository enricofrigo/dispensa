package eu.frigo.dispensa.sync.gdrive;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import androidx.work.ListenableWorker;

import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.provider.SyncProvider;
import eu.frigo.dispensa.sync.core.provider.SyncProviderLoader;
import eu.frigo.dispensa.sync.gdrive.client.GDriveClient;
import eu.frigo.dispensa.sync.gdrive.worker.GDriveSyncWorker;

public class GDriveSyncProviderLoader implements SyncProviderLoader {

    public static final String KEY_GDRIVE_ACCOUNT = "sync_gdrive_account";
    public static final String KEY_GDRIVE_PANTRY_FOLDER_ID = "sync_gdrive_pantry_folder_id";

    @Override
    public String getProviderType() {
        return "gdrive";
    }

    @Override
    public SyncProvider load(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String accountName = prefs.getString(KEY_GDRIVE_ACCOUNT, "");
        String pantryFolderId = prefs.getString(KEY_GDRIVE_PANTRY_FOLDER_ID, "");
        boolean enabled = prefs.getBoolean(SyncManager.KEY_SYNC_ENABLED, false);

        if (enabled && !accountName.isEmpty() && !pantryFolderId.isEmpty()) {
            String deviceId = InstallationIdProvider.getOrCreateInstallationId(context);
            GDriveClient client = new GDriveClient(context, accountName);
            // RemoteStore implementation for GDrive will be needed
            // For now, passing null or a dummy implementation
            return new GDriveSyncProvider("gdrive", null, client, deviceId, pantryFolderId);
        }
        return null;
    }

    @Override
    public Class<? extends ListenableWorker> getWorkerClass() {
        return GDriveSyncWorker.class;
    }
}
