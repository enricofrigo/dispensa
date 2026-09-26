package eu.frigo.dispensa.sync.sharing;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.preference.PreferenceManager;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.store.SharedFolderStore;
import eu.frigo.dispensa.sync.local.LocalSafSyncProviderLoader;
import eu.frigo.dispensa.sync.local.store.SafFolderStore;

public class LocalSafSharingProvider implements SharingProvider {

    @Override
    public String getProviderId() {
        return "local_saf";
    }

    @Override
    public String getDisplayName(Context context) {
        return context.getString(R.string.provider_local_saf);
    }

    @Override
    public boolean isConfigured(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String uriStr = prefs.getString(LocalSafSyncProviderLoader.PREF_KEY_SAF_URI, null);
        return uriStr != null && !uriStr.trim().isEmpty();
    }

    @Override
    public SharedFolderStore createStore(Context context, Dispensa dispensa) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String uriStr = prefs.getString(LocalSafSyncProviderLoader.PREF_KEY_SAF_URI, null);
        if (uriStr == null || uriStr.trim().isEmpty()) {
            throw new IllegalStateException("Local SAF folder URI is not configured");
        }
        Uri treeUri = Uri.parse(uriStr);
        return new SafFolderStore(context, treeUri);
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
        String uriStr = prefs.getString(LocalSafSyncProviderLoader.PREF_KEY_SAF_URI, "");
        String remoteId = (dispensa.remoteId != null && !dispensa.remoteId.trim().isEmpty())
                ? dispensa.remoteId.trim()
                : String.valueOf(dispensa.id);

        JoinedPantryConfig config = new JoinedPantryConfig(dispensa.id, "local_saf", null, remoteId);
        config.url = uriStr;
        return config;
    }
}
