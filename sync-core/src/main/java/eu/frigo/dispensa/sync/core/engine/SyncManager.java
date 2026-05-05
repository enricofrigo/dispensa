package eu.frigo.dispensa.sync.core.engine;

import android.content.Context;

import androidx.work.ListenableWorker;

import java.util.HashMap;
import java.util.Map;

import eu.frigo.dispensa.sync.core.provider.RemoteStore;
import eu.frigo.dispensa.sync.core.provider.SyncProvider;
import eu.frigo.dispensa.sync.core.provider.SyncProviderLoader;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

public class SyncManager {
    public static final String KEY_WEBDAV_URL = "sync_webdav_url";
    public static final String KEY_WEBDAV_USER = "sync_webdav_user";
    public static final String KEY_WEBDAV_PASS = "sync_webdav_pass";
    public static final String KEY_WEBDAV_PATH = "sync_webdav_path";
    public static final String KEY_WEBDAV_MODE_SHARED = "sync_webdav_mode_shared";
    public static final String KEY_SYNC_ENABLED = "pref_sync_enabled";
    public static final String SYNC_WEBDAV_PANTRY_KEY = "sync_webdav_pantry_key";
    public static final String DEFAULT_PATH = "/dispensa/";
    public static final String DEFAULT_MAIN_PANTRY = "main_pantry";
    public static final String DEFAULT_SYNC_PATH = "dispensa-sync/";
    public static final String DEFAULT_PANTRY_PATH = DEFAULT_SYNC_PATH+"pantries/";
    public static final String DEFAULT_EVENTS_FOLDER = "events/";
    public static final String DEFAULT_DEVICES_FOLDER = "devices/";
    public static final String DEFAULT_SNAPSHOTS_FOLDER = "snapshots/";
    public static final String MANIFEST_JSON = "manifest.json";
    public static final String ACTION_SYNC_REMOVED = "eu.frigo.dispensa.sync.ACTION_REMOVED";

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

    public void disconnect(Context context) {
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit()
                .remove(KEY_WEBDAV_URL)
                .remove(KEY_WEBDAV_USER)
                .remove(KEY_WEBDAV_PASS)
                .remove(KEY_WEBDAV_PATH)
                .remove(KEY_WEBDAV_MODE_SHARED)
                .remove(SYNC_WEBDAV_PANTRY_KEY)
                .putBoolean(KEY_SYNC_ENABLED, false)
                .apply();
        setProvider(new SyncProvider() {
            @Override
            public String getId() {
                return "";
            }

            @Override
            public Single<Boolean> isAvailable() {
                return null;
            }

            @Override
            public RemoteStore getRemoteStore() {
                return null;
            }

            @Override
            public Class<? extends ListenableWorker> getWorkerClass() {
                return null;
            }
        });
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
