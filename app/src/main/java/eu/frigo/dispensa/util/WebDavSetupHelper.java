package eu.frigo.dispensa.util;

import android.content.Context;
import android.os.Build;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;

import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.model.PantryDevice;
import eu.frigo.dispensa.sync.core.model.PantryManifest;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import io.reactivex.rxjava3.core.Single;
import okhttp3.Response;

public class WebDavSetupHelper {
    private static final String TAG = "WebDavSetupHelper";

    public static Single<Boolean> preparePantryOnServer(Context context, WebDavClient client, String pantryName, String remoteId, String basePath) {
        return Single.fromCallable(() -> {
            String deviceId = InstallationIdProvider.getOrCreateInstallationId(context);
            String syncPath = SyncManager.getSyncPath(remoteId);
            
            String base = (basePath == null) ? "" : (basePath.endsWith("/") ? basePath : basePath + "/");
            if (base.startsWith("/")) base = base.substring(1);
            String fullPath = base + syncPath;

            // 1. Create main sync folder
            if (!ensureFolderExists(client, fullPath)) return false;

            // 2. Create subfolders
            if (!ensureFolderExists(client, fullPath + SyncManager.DEFAULT_EVENTS_FOLDER)) return false;
            if (!ensureFolderExists(client, fullPath + SyncManager.DEFAULT_DEVICES_FOLDER)) return false;
            if (!ensureFolderExists(client, fullPath + SyncManager.DEFAULT_SNAPSHOTS_FOLDER)) return false;

            // 3. Create manifest.json
            String manifestPath = fullPath + SyncManager.MANIFEST_JSON;
            PantryManifest manifest = new PantryManifest();
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
                    .getString(SyncManager.KEY_DEVICE_NAME, Build.MODEL);
            PantryDevice device = new PantryDevice();
            device.deviceId = deviceId;
            device.deviceName = deviceName;
            device.lastSeen = System.currentTimeMillis();

            String devicePath = fullPath + SyncManager.DEFAULT_DEVICES_FOLDER + deviceId + ".json";
            String deviceJson = new Gson().toJson(device);
            try (Response response = client.put(devicePath, deviceJson.getBytes(), null)) {
                return response.isSuccessful();
            }
        });
    }

    private static boolean ensureFolderExists(WebDavClient client, String folderPath) throws Exception {
        String clean = folderPath.startsWith("/") ? folderPath.substring(1) : folderPath;
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (clean.isEmpty()) return true;

        String[] parts = clean.split("/");
        StringBuilder currentPath = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (currentPath.length() > 0) {
                currentPath.append("/");
            }
            currentPath.append(part);
            String pathStr = currentPath.toString();

            try (Response response = client.propfind(pathStr + "/")) {
                if (response.isSuccessful() || response.code() == 207) {
                    continue;
                }
            }

            try (Response response = client.mkcol(pathStr)) {
                if (!response.isSuccessful() && response.code() != 201 && response.code() != 405) {
                    return false;
                }
            }
        }
        return true;
    }
}
