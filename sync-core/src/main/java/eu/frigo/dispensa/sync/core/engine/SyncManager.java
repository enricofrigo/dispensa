package eu.frigo.dispensa.sync.core.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import eu.frigo.dispensa.sync.core.provider.SyncProvider;
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

    private SyncManager() {}

    public static synchronized SyncManager getInstance() {
        if (instance == null) {
            instance = new SyncManager();
        }
        return instance;
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

        // Try to reconstruct from SharedPreferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String url = prefs.getString(KEY_WEBDAV_URL, "");
        String user = prefs.getString(KEY_WEBDAV_USER, "");
        String pass = prefs.getString(KEY_WEBDAV_PASS, "");
        boolean enabled = prefs.getBoolean(KEY_SYNC_ENABLED, false);

        if (enabled && !url.isEmpty() && !user.isEmpty()) {
            try {
                // Reflection/Manual init to avoid circular dependency if needed, 
                // but here we can just instantiate WebDavSyncProvider as it's known.
                // Note: In a pure agnostic core, this would be a factory.
                Class<?> clazz = Class.forName("eu.frigo.dispensa.sync.webdav.WebDavSyncProvider");
                java.lang.reflect.Constructor<?> constructor = clazz.getConstructor(String.class, eu.frigo.dispensa.sync.core.provider.RemoteStore.class, 
                        Class.forName("eu.frigo.dispensa.sync.webdav.client.WebDavClient"), String.class, String.class);
                
                String deviceId = android.provider.Settings.Secure.getString(context.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
                String path = prefs.getString(KEY_WEBDAV_PATH, DEFAULT_PATH);
                String pantryKey = prefs.getString(SYNC_WEBDAV_PANTRY_KEY, DEFAULT_MAIN_PANTRY);
                
                String normalizedBase = path.endsWith("/") ? path : path + "/";
                if (normalizedBase.startsWith("/")) normalizedBase = normalizedBase.substring(1);
                String pantryPath = normalizedBase + DEFAULT_PANTRY_PATH + pantryKey + "/";

                Object client = Class.forName("eu.frigo.dispensa.sync.webdav.client.WebDavClient")
                        .getConstructor(String.class, String.class, String.class)
                        .newInstance(url, user, pass);
                
                Object remoteStore = Class.forName("eu.frigo.dispensa.sync.webdav.WebDavRemoteStoreImpl")
                        .getConstructor(Class.forName("eu.frigo.dispensa.sync.webdav.client.WebDavClient"))
                        .newInstance(client);

                SyncProvider provider = (SyncProvider) constructor.newInstance("webdav", remoteStore, client, deviceId, pantryPath);
                setProvider(provider);
                return provider;
            } catch (Exception e) {
                android.util.Log.e("SyncManager", "Failed to auto-init provider", e);
            }
        }
        return null;
    }
}
