package eu.frigo.dispensa.sync.core.engine;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

import eu.frigo.dispensa.sync.core.provider.SyncProvider;
import eu.frigo.dispensa.sync.core.provider.SyncProviderLoader;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

public class SyncManager {
    public static final String KEY_WEBDAV_URL = "sync_webdav_url";
    public static final String KEY_WEBDAV_USER = "sync_webdav_user";
    public static final String KEY_WEBDAV_PASS = "sync_webdav_pass";
    public static final String KEY_WEBDAV_PATH = "sync_webdav_path";
    public static final String KEY_WEBDAV_MODE_SHARED = "sync_webdav_mode_shared";
    public static final String KEY_SYNC_ENABLED = "pref_sync_enabled";
    public static final String SYNC_WEBDAV_PANTRY_KEY = "sync_webdav_pantry_key";
    public static final String SYNC_WEBDAV_PANTRY_NAME = "sync_webdav_pantry_name";
    public static final String SYNC_WEBDAV_DISPENSA_ID = "sync_webdav_dispensa_id";
    public static final String SYNC_WEBDAV_SYNCED_IDS = "sync_webdav_synced_ids";
    public static final String DEFAULT_PATH = "/";
    
    public static final int CURRENT_SYNC_VERSION = 2;
    public static final String LEGACY_SYNC_PATH = "dispensa-sync/";
    
    public static String getSyncPath(String pantryName) {
        String name = (pantryName == null || pantryName.isEmpty()) ? "Dispensa" : pantryName;
        return name + "-sync/";
    }

    public static final String DEFAULT_EVENTS_FOLDER = "events/";
    public static final String DEFAULT_DEVICES_FOLDER = "devices/";
    public static final String DEFAULT_SNAPSHOTS_FOLDER = "snapshots/";
    public static final String MANIFEST_JSON = "manifest.json";

    private static SyncManager instance;
    private final BehaviorSubject<SyncProvider> currentProvider = BehaviorSubject.create();
    private final Map<String, SyncProviderLoader> loaders = new HashMap<>();

    private SyncManager() {}

    public static synchronized SyncManager getInstance() {
        if (instance == null) {
            instance = new SyncManager();
        }
        return instance;
    }

    public void registerLoader(SyncProviderLoader loader) {
        loaders.put(loader.getProviderType(), loader);
    }

    public void setProvider(SyncProvider provider) {
        currentProvider.onNext(provider);
    }

    public BehaviorSubject<SyncProvider> getCurrentProvider() {
        return currentProvider;
    }

    public boolean isCurrentDeviceOwner(Context context, String createdByDevice) {
        return InstallationIdProvider.isCurrentInstallationGroupOwner(context, createdByDevice);
    }

    public boolean isLinkedToRemoteGroup(Context context) {
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.contains(SYNC_WEBDAV_PANTRY_KEY);
    }

    public SyncProvider getOrInitProvider(Context context) {
        SyncProvider active = currentProvider.getValue();
        if (active != null) return active;

        for (SyncProviderLoader loader : loaders.values()) {
            SyncProvider provider = loader.load(context);
            if (provider != null) {
                setProvider(provider);
                return provider;
            }
        }

        return null;
    }
}
