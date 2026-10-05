package eu.frigo.dispensa.sync.sharing;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.store.SharedFolderStore;
import eu.frigo.dispensa.sync.gdrive.auth.GDriveAuthManager;
import eu.frigo.dispensa.sync.gdrive.client.GDriveClient;
import eu.frigo.dispensa.sync.gdrive.store.GDriveFolderStore;

public class GDriveSharingProvider implements SharingProvider {

    @Override
    public String getProviderId() {
        return "gdrive";
    }

    @Override
    public String getDisplayName(Context context) {
        return context.getString(R.string.provider_gdrive);
    }

    @Override
    public boolean isConfigured(Context context) {
        GDriveAuthManager authManager = new GDriveAuthManager(context);
        return authManager.isSignedIn();
    }

    @Override
    public int getIconResId() {
        return R.drawable.ic_provider_gdrive;
    }

    @Override
    public android.content.Intent getConfigIntent(Context context) {
        return new android.content.Intent(context, eu.frigo.dispensa.activity.SyncGDriveConfigActivity.class);
    }

    @Override
    public String getSummary(Context context) {
        GDriveAuthManager authManager = new GDriveAuthManager(context);
        if (authManager.isSignedIn()) {
            String email = authManager.getAccountEmail();
            return email != null ? email : context.getString(R.string.sync_status_connected, "");
        }
        return context.getString(R.string.sync_gdrive_not_connected);
    }

    @Override
    public SharedFolderStore createStore(Context context, Dispensa dispensa) {
        GDriveAuthManager authManager = new GDriveAuthManager(context);
        GDriveClient client = new GDriveClient(authManager);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String rootFolderId = prefs.getString(GDriveAuthManager.PREF_KEY_GDRIVE_ROOT_FOLDER_ID, "root");
        return new GDriveFolderStore(client, rootFolderId);
    }

    @Override
    public String getPantryPath(Context context, Dispensa dispensa) {
        String remoteId = (dispensa.remoteId != null && !dispensa.remoteId.trim().isEmpty())
                ? dispensa.remoteId.trim()
                : String.valueOf(dispensa.id);
        return SyncManager.getSyncPath(remoteId);
    }

    @Override
    public JoinedPantryConfig createJoinedConfig(Context context, Dispensa dispensa) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String rootFolderId = prefs.getString(GDriveAuthManager.PREF_KEY_GDRIVE_ROOT_FOLDER_ID, "root");
        String remoteId = (dispensa.remoteId != null && !dispensa.remoteId.trim().isEmpty())
                ? dispensa.remoteId.trim()
                : String.valueOf(dispensa.id);

        JoinedPantryConfig config = new JoinedPantryConfig(dispensa.id, "gdrive", null, remoteId);
        config.url = rootFolderId;
        return config;
    }
}
