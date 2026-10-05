package eu.frigo.dispensa.sync.sharing;

import android.content.Context;
import android.content.SharedPreferences;

import android.content.Intent;

import androidx.preference.PreferenceManager;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.store.InMemorySharedFolderStore;
import eu.frigo.dispensa.sync.core.store.SharedFolderStore;
import eu.frigo.dispensa.sync.local.LocalSafSyncProviderLoader;

@RunWith(RobolectricTestRunner.class)
public class PantrySharingServiceTest {

    private Context context;
    private AppDatabase database;
    private SharedPreferences prefs;

    @Before
    public void setup() {
        context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        AppDatabase.setTestInstance(database);

        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().clear().apply();
    }

    @After
    public void teardown() {
        if (database != null) {
            database.close();
        }
        AppDatabase.setTestInstance(null);
    }

    @Test
    public void testAvailableProviders_NoneConfigured() {
        PantrySharingService service = new PantrySharingService();
        service.registerProvider(new WebDavSharingProvider());
        service.registerProvider(new LocalSafSharingProvider());
        List<SharingProvider> available = service.getAvailableProviders(context);
        Assert.assertTrue(available.isEmpty());
    }

    @Test
    public void testAvailableProviders_WebDavConfigured() {
        prefs.edit()
                .putString(SyncManager.KEY_WEBDAV_URL, "https://example.com/remote.php/dav/files/user/")
                .putString(SyncManager.KEY_WEBDAV_USER, "user")
                .putString(SyncManager.KEY_WEBDAV_PASS, "password")
                .apply();

        PantrySharingService service = new PantrySharingService();
        service.registerProvider(new WebDavSharingProvider());
        service.registerProvider(new LocalSafSharingProvider());
        List<SharingProvider> available = service.getAvailableProviders(context);
        Assert.assertEquals(1, available.size());
        Assert.assertEquals("webdav", available.get(0).getProviderId());
    }

    @Test
    public void testAvailableProviders_LocalSafConfigured() {
        prefs.edit()
                .putString(LocalSafSyncProviderLoader.PREF_KEY_SAF_URI, "content://com.android.externalstorage.documents/tree/primary%3ASync")
                .apply();

        PantrySharingService service = new PantrySharingService();
        service.registerProvider(new WebDavSharingProvider());
        service.registerProvider(new LocalSafSharingProvider());
        List<SharingProvider> available = service.getAvailableProviders(context);
        Assert.assertEquals(1, available.size());
        Assert.assertEquals("local_saf", available.get(0).getProviderId());
    }

    @Test
    public void testAvailableProviders_BothConfigured() {
        prefs.edit()
                .putString(SyncManager.KEY_WEBDAV_URL, "https://example.com/dav/")
                .putString(SyncManager.KEY_WEBDAV_USER, "user")
                .putString(SyncManager.KEY_WEBDAV_PASS, "password")
                .putString(LocalSafSyncProviderLoader.PREF_KEY_SAF_URI, "content://com.android.externalstorage.documents/tree/primary%3ASync")
                .apply();

        PantrySharingService service = new PantrySharingService();
        service.registerProvider(new WebDavSharingProvider());
        service.registerProvider(new LocalSafSharingProvider());
        List<SharingProvider> available = service.getAvailableProviders(context);
        Assert.assertEquals(2, available.size());
    }

    @Test
    public void testSharePantry_InitializesRemoteStructureAndPersistsConfig() {
        // Create test dispensa in DB
        Dispensa dispensa = new Dispensa("Dispensa Famiglia", false);
        long dispId = database.dispensaDao().insert(dispensa);
        dispensa.id = (int) dispId;

        // Custom in-memory store for verification
        Map<String, byte[]> storage = new ConcurrentHashMap<>();
        HashSet<String> folders = new HashSet<>();
        InMemorySharedFolderStore memoryStore = new InMemorySharedFolderStore("custom_provider", storage, folders);

        SharingProvider mockProvider = new SharingProvider() {
            @Override public String getProviderId() { return "custom_provider"; }
            @Override public String getDisplayName(Context context) { return "Mock Provider"; }
            @Override public int getIconResId() { return 0; }
            @Override public boolean isConfigured(Context context) { return true; }
            @Override public Intent getConfigIntent(Context context) { return null; }
            @Override public String getSummary(Context context) { return "Mock Summary"; }
            @Override public SharedFolderStore createStore(Context context, Dispensa dispensa) { return memoryStore; }
            @Override public String getPantryPath(Context context, Dispensa dispensa) {
                return SyncManager.getSyncPath(dispensa.remoteId);
            }
            @Override public JoinedPantryConfig createJoinedConfig(Context context, Dispensa dispensa) {
                return new JoinedPantryConfig(dispensa.id, "custom_provider", "{\"token\":\"abc\"}", dispensa.remoteId);
            }
        };

        PantrySharingService service = new PantrySharingService();
        service.registerProvider(mockProvider);

        // Execute sharing
        service.sharePantry(context, dispensa, "custom_provider").blockingAwait();

        // 1. Verify Dispensa state
        Dispensa updatedDisp = database.dispensaDao().getDispensaByIdSync((int) dispId);
        Assert.assertNotNull(updatedDisp.remoteId);
        Assert.assertFalse(updatedDisp.remoteId.isEmpty());
        String deviceId = InstallationIdProvider.getOrCreateInstallationId(context);
        Assert.assertEquals(deviceId, updatedDisp.deviceOwnerId);

        // 2. Verify Remote structure created in store
        String pantryPath = SyncManager.getSyncPath(updatedDisp.remoteId);
        Assert.assertTrue(folders.contains(pantryPath));
        Assert.assertTrue(folders.contains(pantryPath + SyncManager.DEFAULT_EVENTS_FOLDER));
        Assert.assertTrue(folders.contains(pantryPath + SyncManager.DEFAULT_DEVICES_FOLDER));
        Assert.assertTrue(folders.contains(pantryPath + SyncManager.DEFAULT_SNAPSHOTS_FOLDER));
        Assert.assertTrue(storage.containsKey(pantryPath + SyncManager.MANIFEST_JSON));
        Assert.assertTrue(storage.containsKey(pantryPath + SyncManager.DEFAULT_DEVICES_FOLDER + deviceId + ".json"));

        // 3. Verify JoinedPantryConfig saved
        JoinedPantryConfig config = database.joinedPantryConfigDao().getConfigByDispensaId((int) dispId);
        Assert.assertNotNull(config);
        Assert.assertEquals("custom_provider", config.providerId);
        Assert.assertEquals("{\"token\":\"abc\"}", config.configPayload);

        // 4. Verify SharedPreferences synced IDs updated
        String syncedIdsStr = prefs.getString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, "");
        Assert.assertTrue(syncedIdsStr.contains(String.valueOf(dispId)));
    }

    @Test
    public void testDeleteRemotePantry_DeletesFolderAndRemovesConfig() {
        // Create test dispensa in DB
        Dispensa dispensa = new Dispensa("Dispensa Da Cancellare", false);
        dispensa.remoteId = "remote-12345";
        long dispId = database.dispensaDao().insert(dispensa);
        dispensa.id = (int) dispId;

        // Custom in-memory store
        Map<String, byte[]> storage = new ConcurrentHashMap<>();
        HashSet<String> folders = new HashSet<>();
        InMemorySharedFolderStore memoryStore = new InMemorySharedFolderStore("custom_provider", storage, folders);

        SharingProvider mockProvider = new SharingProvider() {
            @Override public String getProviderId() { return "custom_provider"; }
            @Override public String getDisplayName(Context context) { return "Mock Provider"; }
            @Override public int getIconResId() { return 0; }
            @Override public boolean isConfigured(Context context) { return true; }
            @Override public Intent getConfigIntent(Context context) { return null; }
            @Override public String getSummary(Context context) { return "Mock Summary"; }
            @Override public SharedFolderStore createStore(Context context, Dispensa dispensa) { return memoryStore; }
            @Override public String getPantryPath(Context context, Dispensa dispensa) {
                return SyncManager.getSyncPath(dispensa.remoteId);
            }
            @Override public JoinedPantryConfig createJoinedConfig(Context context, Dispensa dispensa) {
                return new JoinedPantryConfig(dispensa.id, "custom_provider", "{}", dispensa.remoteId);
            }
        };

        PantrySharingService service = new PantrySharingService();
        service.registerProvider(mockProvider);

        // Setup shared pantry
        service.sharePantry(context, dispensa, "custom_provider").blockingAwait();

        String pantryPath = SyncManager.getSyncPath(dispensa.remoteId);
        Assert.assertTrue(folders.contains(pantryPath));
        Assert.assertNotNull(database.joinedPantryConfigDao().getConfigByDispensaId((int) dispId));

        // Now delete remote pantry
        service.deleteRemotePantry(context, dispensa).blockingAwait();

        // 1. Verify remote folder deleted
        Assert.assertFalse(folders.contains(pantryPath));

        // 2. Verify JoinedPantryConfig deleted from DB
        Assert.assertNull(database.joinedPantryConfigDao().getConfigByDispensaId((int) dispId));

        // 3. Verify ID removed from SharedPreferences
        String syncedIdsStr = prefs.getString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, "");
        Assert.assertFalse(syncedIdsStr.contains(String.valueOf(dispId)));
    }
}
