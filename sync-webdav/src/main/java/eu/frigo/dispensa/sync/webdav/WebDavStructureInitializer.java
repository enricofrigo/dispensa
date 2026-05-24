package eu.frigo.dispensa.sync.webdav;

import android.util.Log;
import com.google.gson.JsonObject;
import com.google.gson.Gson;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import eu.frigo.dispensa.sync.webdav.model.WebDavSnapshot;
import okhttp3.Response;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Initializes WebDAV folder structure for Dispensa sync on first setup.
 * 
 * Creates the required directory hierarchy:
 * - /basePath/dispensa-sync/pantries/main_pantry/events/
 * - /basePath/dispensa-sync/pantries/main_pantry/snapshots/
 * - /basePath/dispensa-sync/pantries/main_pantry/devices/
 * 
 * And creates manifest.json.
 */
public class WebDavStructureInitializer {
    
    private static final String TAG = "WebDavStructInitializer";
    private final WebDavClient client;
    private final String basePath;
    private final Gson gson;
    private final AppDatabase db;
    private String initialSnapshotId;

    public WebDavStructureInitializer(WebDavClient client, String basePath, AppDatabase db) {
        this.client = client;
        // Normalize base path: ensure ends with "/" but no leading "/"
        this.basePath = normalizePath(basePath);
        this.db=db;
        this.gson = new Gson();
        Log.d(TAG, "Initialized with basePath: " + this.basePath);
    }
    
    /**
     * Main initialization method.
     * Called when user enables WebDAV sync.
     * 
     * @return true if structure created successfully, false on error
     */
    public boolean initializeStructure() {
        try {
            Log.d(TAG, "Starting WebDAV structure initialization");
            
            // 1. Verify connectivity to server
            if (!verifyConnectivity()) {
                Log.e(TAG, "Failed to verify connectivity to WebDAV server");
                return false;
            }
            Log.d(TAG, "✓ Connectivity verified");

            // 2. Create base sync folder
            if (!ensureFolderExists(SyncManager.DEFAULT_SYNC_PATH)) {
                Log.e(TAG, "Failed to create base sync folder");
                return false;
            }
            Log.d(TAG, "✓ Base sync folder ready: " + basePath + SyncManager.DEFAULT_SYNC_PATH);
            
            // 3. Create pantries folder
            if (!ensureFolderExists(SyncManager.DEFAULT_PANTRY_PATH)) {
                Log.e(TAG, "Failed to create pantries folder");
                return false;
            }
            Log.d(TAG, "✓ Pantries folder ready: " + basePath + SyncManager.DEFAULT_PANTRY_PATH);
            
            // 4. Create main_pantry folder
            String mainPantryPath = SyncManager.DEFAULT_PANTRY_PATH + SyncManager.DEFAULT_MAIN_PANTRY + "/";
            if (!ensureFolderExists(mainPantryPath)) {
                Log.e(TAG, "Failed to create main_pantry folder");
                return false;
            }
            Log.d(TAG, "✓ Main pantry folder ready: " + mainPantryPath);
            
            // 5. Create sub-folders (events, snapshots, devices)
            if (!ensureFolderExists(mainPantryPath + SyncManager.DEFAULT_EVENTS_FOLDER)) {
                Log.e(TAG, "Failed to create events folder");
                return false;
            }
            Log.d(TAG, "✓ Events folder ready");
            
            if (!ensureFolderExists(mainPantryPath + SyncManager.DEFAULT_SNAPSHOTS_FOLDER)) {
                Log.e(TAG, "Failed to create snapshots folder");
                return false;
            }
            Log.d(TAG, "✓ Snapshots folder ready");

            if (!createInitialSnapshot(mainPantryPath)) {
                Log.w(TAG, "Warning: Failed to create initial snapshot");
            }
            Log.d(TAG, "✓ Initial snapshot created");

            if (!ensureFolderExists(mainPantryPath + SyncManager.DEFAULT_DEVICES_FOLDER)) {
                Log.e(TAG, "Failed to create devices folder");
                return false;
            }
            Log.d(TAG, "✓ Devices folder ready");
            
            // 6. Create manifest.json at pantry root
            if (!createManifestFile(mainPantryPath)) {
                Log.w(TAG, "Warning: Failed to create manifest.json (may already exist)");
                // Don't fail completely - manifest might already exist
            }
            Log.d(TAG, "✓ Manifest file ready");
            
            Log.i(TAG, "✓ WebDAV structure initialization completed successfully!");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during initialization", e);
            return false;
        }
    }

    private boolean createInitialSnapshot(String pantryPath) {
        try {
            // Crea snapshot VUOTO (da DB, che è empty al primo avvio)
            WebDavSnapshot initialSnapshot = new WebDavSnapshot();
            initialSnapshot.timestamp = System.currentTimeMillis();
            initialSnapshot.products = db.productDao().getAllProductsListStatic();
            initialSnapshot.locations = db.storageLocationDao().getAllLocationsSortedSync();
            initialSnapshot.shoppingItems = db.shoppingItemDao().getAllItemsSync();

            String snapshotName = "snap_INIT_" + initialSnapshot.timestamp + ".json";
            byte[] snapshotBytes = gson.toJson(initialSnapshot).getBytes("UTF-8");

            Response response = client.put(pantryPath + "snapshots/" + snapshotName, snapshotBytes, null);
            int code = response.code();
            response.close();

            if (code >= 200 && code < 300) {
                this.initialSnapshotId = snapshotName; // Save per usare in manifest
                Log.d(TAG, "Created initial snapshot: " + snapshotName);
                return true;
            } else {
                Log.e(TAG, "Failed to create initial snapshot (HTTP " + code + ")");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating initial snapshot", e);
            return false;
        }
    }

    /**
     * Verifies connectivity to WebDAV server.
     * Uses PROPFIND to test credentials and access.
     * 
     * @return true if server responds (2xx or 405 code), false otherwise
     */
    private boolean verifyConnectivity() {
        try {
            Response response = client.propfind("");
            int code = response.code();
            response.close();
            
            // 2xx = success, 405 = Method Not Allowed (still means we can connect)
            boolean canConnect = (code >= 200 && code < 300) || code == 405;
            Log.d(TAG, "Connectivity check: HTTP " + code + " → " + (canConnect ? "OK" : "FAIL"));
            return canConnect;
            
        } catch (Exception e) {
            Log.e(TAG, "Connectivity verification failed", e);
            return false;
        }
    }
    
    /**
     * Creates a WebDAV collection (folder) if it doesn't exist.
     * Uses MKCOL HTTP method.
     * Ignores 405 (Method Not Allowed) error as it indicates folder already exists.
     * 
     * @param folderPath path to create (e.g., "dispensa-sync/pantries/")
     * @return true if created or already exists, false on real error
     */
    private boolean ensureFolderExists(String folderPath) {
        try {
            Response response = client.mkcol(folderPath);
            int code = response.code();
            response.close();
            
            // 201 Created = newly created
            // 204 No Content = success (varies by server)
            // 405 Method Not Allowed = folder already exists (our use case)
            // 409 Conflict = parent doesn't exist (we create parents first, so shouldn't happen)
            
            if (code == 201 || code == 204) {
                Log.d(TAG, "Created folder: " + folderPath + " (HTTP " + code + ")");
                return true;
            } else if (code == 405) {
                Log.d(TAG, "Folder already exists (idempotent): " + folderPath);
                return true;
            } else {
                Log.e(TAG, "Failed to create folder: " + folderPath + " (HTTP " + code + ")");
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating folder: " + folderPath, e);
            return false;
        }
    }
    
    /**
     * Creates manifest.json file at pantry root.
     * Contains metadata about sync configuration.
     * 
     * @param pantryPath path to pantry root (must end with /)
     * @return true if created, false on error
     */
    private boolean createManifestFile(String pantryPath) {
        try {
            JsonObject manifest = new JsonObject();
            manifest.addProperty("version", "1.1");
            manifest.addProperty("createdAt", System.currentTimeMillis());
            manifest.addProperty("appVersion", "0.1.11"); 
            manifest.addProperty("syncFormat", "crdt");
            manifest.addProperty("deviceInitiatedAt", System.currentTimeMillis());
            
            String manifestJson = gson.toJson(manifest);
            byte[] manifestBytes = manifestJson.getBytes(StandardCharsets.UTF_8);
            
            String manifestPath = pantryPath + SyncManager.MANIFEST_JSON;
            Response response = client.put(manifestPath, manifestBytes, null);
            int code = response.code();
            response.close();
            
            if (code >= 200 && code < 300) {
                Log.d(TAG, "Created manifest.json at: " + manifestPath);
                return true;
            } else if (code == 412 || code == 409) {
                // 412 Precondition Failed = file exists (we don't care, continue)
                // 409 Conflict = shouldn't happen
                Log.d(TAG, "Manifest already exists or conflict (HTTP " + code + "), continuing");
                return true;
            } else {
                Log.e(TAG, "Failed to create manifest.json (HTTP " + code + ")");
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating manifest.json", e);
            return false;
        }
    }
    
    /**
     * Normalize folder path:
     * - Remove leading "/" if present
     * - Ensure ends with "/"
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        
        String result = path;
        // Remove leading slash
        if (result.startsWith("/")) {
            result = result.substring(1);
        }
        
        // Ensure ends with slash
        if (!result.isEmpty() && !result.endsWith("/")) {
            result = result + "/";
        }
        
        return result;
    }
    
    /**
     * Get human-readable status message for UI.
     */
    public String getStatusMessage() {
        return "WebDAV structure initialized at: " + basePath;
    }
}
