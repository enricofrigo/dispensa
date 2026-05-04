package eu.frigo.dispensa.sync.core.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.UUID;

public class InstallationIdProvider {
    public static final String PREF_INSTALLATION_ID = "app_installation_id";
    private static String cachedId;

    public static synchronized String getOrCreateInstallationId(Context context) {
        if (cachedId != null) {
            return cachedId;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        cachedId = prefs.getString(PREF_INSTALLATION_ID, null);

        if (cachedId == null) {
            cachedId = UUID.randomUUID().toString();
            prefs.edit().putString(PREF_INSTALLATION_ID, cachedId).apply();
        }

        return cachedId;
    }

    /**
     * Determine whether the current installation is the one that originally created the group.
     */
    public static boolean isCurrentInstallationGroupOwner(Context context, String createdByDevice) {
        if (createdByDevice == null) return false;
        return getOrCreateInstallationId(context).equals(createdByDevice);
    }

    /**
     * Determine whether the current installation is already part of the shared pantry group
     * by checking if its device ID exists in the list of registered devices.
     */
    public static boolean isCurrentInstallationAlreadyLinked(Context context, java.util.List<String> registeredDeviceIds) {
        if (registeredDeviceIds == null) return false;
        String currentId = getOrCreateInstallationId(context);
        return registeredDeviceIds.contains(currentId);
    }
}
