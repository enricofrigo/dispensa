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
    public static final String KEY_SYNC_ENABLED = "pref_sync_enabled";
    public static final String SYNC_WEBDAV_PANTRY_KEY = "sync_webdav_pantry_key";
    public static final String DEFAULT_PATH = "/dispensa/";
    public static final String DEFAULT_MAIN_PANTRY = "main_pantry";
    public static final String DEFAULT_PANTRY_PATH = "dispensa-sync/pantries/";
    
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
