package eu.frigo.dispensa.util;

import android.content.Context;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;

import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import eu.frigo.dispensa.sync.webdav.model.WebDavDevice;
import eu.frigo.dispensa.sync.webdav.model.WebDavManifest;
import io.reactivex.rxjava3.core.Single;
import okhttp3.Response;

public class WebDavSetupHelper {
    private static final String TAG = "WebDavSetupHelper";

    public static Single<Boolean> preparePantryOnServer(Context context, WebDavClient client, String pantryName) {
        return Single.fromCallable(() -> {
            String deviceId = InstallationIdProvider.getOrCreateInstallationId(context);
            String syncPath = SyncManager.getSyncPath(pantryName);

            // 1. Create main sync folder
            if (!ensureFolderExists(client, syncPath)) return false;

            // 2. Create subfolders
            if (!ensureFolderExists(client, syncPath + SyncManager.DEFAULT_EVENTS_FOLDER)) return false;
            if (!ensureFolderExists(client, syncPath + SyncManager.DEFAULT_DEVICES_FOLDER)) return false;
            if (!ensureFolderExists(client, syncPath + SyncManager.DEFAULT_SNAPSHOTS_FOLDER)) return false;

            // 3. Create manifest.json
            String manifestPath = syncPath + SyncManager.MANIFEST_JSON;
            WebDavManifest manifest = new WebDavManifest();
            manifest.version = SyncManager.CURRENT_SYNC_VERSION;
            manifest.pantryName = pantryName;
            manifest.createdAt = System.currentTimeMillis();
            manifest.createdByDevice = deviceId;
            manifest.provider = "webdav";

            String json = new Gson().toJson(manifest);
            try (Response response = client.put(manifestPath, json.getBytes(), null)) {
                if (!response.isSuccessful()) return false;
            }

            // 4. Register current device
            String deviceName = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(SyncManager.KEY_DEVICE_NAME, android.os.Build.MODEL);
            WebDavDevice device = new WebDavDevice();
            device.deviceId = deviceId;
            device.deviceName = deviceName;
            device.lastSeen = System.currentTimeMillis();

            String devicePath = syncPath + SyncManager.DEFAULT_DEVICES_FOLDER + deviceId + ".json";
            String deviceJson = new Gson().toJson(device);
            try (Response response = client.put(devicePath, deviceJson.getBytes(), null)) {
                return response.isSuccessful();
            }
        });
    }

    private static boolean ensureFolderExists(WebDavClient client, String folderPath) throws Exception {
        String cleanPath = folderPath.endsWith("/") ? folderPath.substring(0, folderPath.length() - 1) : folderPath;
        try (Response response = client.propfind(cleanPath + "/")) {
            if (response.isSuccessful() || response.code() == 207) return true;
            if (response.code() != 404) return false;
        }
        try (Response response = client.mkcol(cleanPath)) {
            return response.isSuccessful() || response.code() == 201;
        }
    }
}
