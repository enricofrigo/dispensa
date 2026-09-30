package eu.frigo.dispensa.sync.sharing;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.data.sync.RoomPantryDataBridge;
import eu.frigo.dispensa.sync.core.engine.FolderSyncEngine;
import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.store.SharedFolderStore;
import eu.frigo.dispensa.sync.core.store.SyncCursorStore;
import eu.frigo.dispensa.sync.core.store.SyncCursorStoreImpl;
import io.reactivex.rxjava3.core.Completable;

public class PantrySharingService {
    private static final String TAG = "PantrySharingService";
    private static PantrySharingService instance;

    private final Map<String, SharingProvider> providers = new LinkedHashMap<>();

    public PantrySharingService() {
    }

    public static synchronized PantrySharingService getInstance() {
        if (instance == null) {
            instance = new PantrySharingService();
        }
        return instance;
    }

    public static synchronized void setInstance(PantrySharingService customInstance) {
        instance = customInstance;
    }

    public void registerProvider(SharingProvider provider) {
        providers.put(provider.getProviderId(), provider);
    }

    public SharingProvider getProvider(String providerId) {
        return providers.get(providerId);
    }

    public List<SharingProvider> getAvailableProviders(Context context) {
        List<SharingProvider> available = new ArrayList<>();
        for (SharingProvider p : providers.values()) {
            if (p.isConfigured(context)) {
                available.add(p);
            }
        }
        return available;
    }

    public Completable sharePantry(Context context, Dispensa dispensa, String providerId) {
        return Completable.fromAction(() -> {
            SharingProvider provider = getProvider(providerId);
            if (provider == null) {
                throw new IllegalArgumentException("Unknown sharing provider: " + providerId);
            }

            if (dispensa.remoteId == null || dispensa.remoteId.trim().isEmpty()) {
                dispensa.remoteId = UUID.randomUUID().toString();
            }

            String currentDeviceId = InstallationIdProvider.getOrCreateInstallationId(context);
            dispensa.deviceOwnerId = currentDeviceId;

            AppDatabase db = AppDatabase.getDatabase(context);
            db.dispensaDao().update(dispensa);

            SharedFolderStore store = provider.createStore(context, dispensa);
            String pantryPath = provider.getPantryPath(context, dispensa);

            RoomPantryDataBridge dataBridge = new RoomPantryDataBridge(db);
            SyncCursorStore cursorStore = new SyncCursorStoreImpl(context);
            String deviceName = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(SyncManager.KEY_DEVICE_NAME, Build.MODEL);

            FolderSyncEngine engine = new FolderSyncEngine(
                    store,
                    dataBridge,
                    cursorStore,
                    currentDeviceId,
                    deviceName,
                    pantryPath,
                    dispensa.id
            );

            // Inizializza la struttura remota sul provider (cartelle, manifest.json, registrazione device)
            engine.initializeRemoteStructure().blockingAwait();

            // Salva la configurazione della dispensa condivisa
            JoinedPantryConfig config = provider.createJoinedConfig(context, dispensa);
            if (config != null) {
                db.joinedPantryConfigDao().insert(config);
            }

            // Aggiorna la lista delle dispense sincronizzate nelle preferenze
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String syncedIdsStr = prefs.getString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, "");
            List<String> syncedIds = new ArrayList<>(Arrays.asList(syncedIdsStr.split(",")));
            if (!syncedIds.contains(String.valueOf(dispensa.id))) {
                if (syncedIdsStr.isEmpty()) {
                    syncedIdsStr = String.valueOf(dispensa.id);
                } else {
                    syncedIdsStr += "," + dispensa.id;
                }
                prefs.edit()
                        .putString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, syncedIdsStr)
                        .putString(SyncManager.SYNC_WEBDAV_PANTRY_NAME + "_" + dispensa.id, dispensa.getName())
                        .apply();
            }
            Log.i(TAG, "Pantry '" + dispensa.getName() + "' successfully shared via provider '" + providerId + "'.");
        });
    }

    public Completable deleteRemotePantry(Context context, Dispensa dispensa) {
        return Completable.fromAction(() -> {
            AppDatabase db = AppDatabase.getDatabase(context);
            JoinedPantryConfig config = db.joinedPantryConfigDao().getConfigByDispensaId(dispensa.id);

            SharedFolderStore store = null;
            String pantryPath = null;

            if (config != null && providers.containsKey(config.providerId)) {
                SharingProvider provider = providers.get(config.providerId);
                store = provider.createStore(context, dispensa);
                pantryPath = provider.getPantryPath(context, dispensa);
            } else {
                // Fallback to WebDAV if no config found (legacy behavior)
                SharingProvider fallback = providers.get("webdav");
                if (fallback != null && fallback.isConfigured(context)) {
                    store = fallback.createStore(context, dispensa);
                    pantryPath = fallback.getPantryPath(context, dispensa);
                }
            }

            if (store != null && pantryPath != null) {
                try {
                    store.deleteFolder(pantryPath).blockingAwait();
                } catch (Exception e) {
                    Log.w(TAG, "Failed to delete remote folder for pantry: " + pantryPath, e);
                }
            }

            if (config != null) {
                db.joinedPantryConfigDao().delete(config);
            }

            // Rimuovi dai synced IDs
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String syncedIdsStr = prefs.getString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, "");
            List<String> syncedIds = new ArrayList<>(Arrays.asList(syncedIdsStr.split(",")));
            syncedIds.remove(String.valueOf(dispensa.id));
            prefs.edit()
                    .putString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, TextUtils.join(",", syncedIds))
                    .apply();
            Log.i(TAG, "Remote pantry deleted for: " + dispensa.getName());
        });
    }
}
