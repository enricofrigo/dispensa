package eu.frigo.dispensa.sync.webdav;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.sync.OutboxRepository;
import eu.frigo.dispensa.data.sync.RoomPantryDataBridge;
import eu.frigo.dispensa.sync.core.engine.FolderSyncEngine;
import eu.frigo.dispensa.sync.core.engine.SyncEngine;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.policy.SyncPolicy;
import eu.frigo.dispensa.sync.core.store.SyncCursorStore;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import eu.frigo.dispensa.sync.webdav.store.WebDavFolderStore;
import io.reactivex.rxjava3.core.Completable;

/**
 * WebDAV adapter for FolderSyncEngine.
 */
public class WebDavSyncEngine implements SyncEngine {
    private final FolderSyncEngine delegate;

    public WebDavSyncEngine(
            WebDavClient client,
            SyncCursorStore cursorStore,
            OutboxRepository outbox,
            String deviceId,
            String pantryPath,
            int dispensaId,
            AppDatabase db,
            Context context
    ) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String deviceName = prefs.getString(SyncManager.KEY_DEVICE_NAME, android.os.Build.MODEL);

        WebDavFolderStore folderStore = new WebDavFolderStore(client);
        RoomPantryDataBridge dataBridge = new RoomPantryDataBridge(db);

        this.delegate = new FolderSyncEngine(
                folderStore,
                dataBridge,
                cursorStore,
                deviceId,
                deviceName,
                pantryPath,
                dispensaId
        );
    }

    @Override
    public Completable performSync(SyncPolicy policy) {
        return delegate.performSync(policy);
    }

    @Override
    public Completable initializeRemoteStructure() {
        return delegate.initializeRemoteStructure();
    }
}

