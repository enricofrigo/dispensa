package eu.frigo.dispensa.sync.drive;

import android.accounts.Account;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import eu.frigo.dispensa.sync.core.SyncTransport;
import eu.frigo.dispensa.sync.core.TransportRegistry;
import eu.frigo.dispensa.sync.core.engine.CrDtSyncManager;
import eu.frigo.dispensa.sync.core.engine.SyncManager;

/**
 * Factory to conditionally instantiate the Google Drive SyncTransport.
 */
public class DriveTransportFactory implements TransportRegistry.TransportFactory {

    public static final String PREF_DRIVE_SYNC_ENABLED = "sync_drive_enabled";

    /**
     * Creates a GoogleDriveSyncTransport instance if Drive sync is enabled and configured.
     *
     * @param context     Android context.
     * @param syncManager The sync manager instance.
     * @return A SyncTransport implementation or null if not enabled/configured.
     */
    @Override
    public SyncTransport create(Context context, CrDtSyncManager syncManager) {
        if (syncManager == null) {
            return null;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        
        boolean enabled = prefs.getBoolean(PREF_DRIVE_SYNC_ENABLED, false);
        if (!enabled) {
            return null;
        }

        GoogleSignInAccount signInAccount = GoogleSignIn.getLastSignedInAccount(context);
        if (signInAccount == null) {
            return null;
        }

        Account account = signInAccount.getAccount();
        if (account == null) {
            return null;
        }

        String folderId = HouseholdManager.getFolderId(context);
        if (folderId != null) {
            String deviceId = syncManager.getLocalDeviceId();
            String friendlyName = prefs.getString(CrDtSyncManager.PREF_DEVICE_NAME, "Android Device");
            return new GoogleDriveSyncTransport(context, account, folderId, deviceId, friendlyName);
        } else {
            return new GoogleDriveSyncTransport(context, account);
        }
    }
}
