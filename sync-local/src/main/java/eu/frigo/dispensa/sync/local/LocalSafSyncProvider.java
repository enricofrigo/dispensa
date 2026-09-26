package eu.frigo.dispensa.sync.local;

import android.content.Context;
import androidx.work.ListenableWorker;

import java.util.ArrayList;
import java.util.List;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.sync.RoomPantryDataBridge;
import eu.frigo.dispensa.sync.core.engine.FolderSyncEngine;
import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.provider.RemoteStore;
import eu.frigo.dispensa.sync.core.provider.SyncProvider;
import eu.frigo.dispensa.sync.core.store.SyncCursorStoreImpl;
import eu.frigo.dispensa.sync.local.store.SafFolderStore;
import eu.frigo.dispensa.sync.local.worker.LocalSafSyncWorker;
import io.reactivex.rxjava3.core.Single;

public class LocalSafSyncProvider implements SyncProvider {
    private final String deviceId;
    private final List<SyncScope> scopes;
    private final List<FolderSyncEngine> engines = new ArrayList<>();

    public static class SyncScope {
        public final int dispensaId;
        public final String pantryPath;
        public final SafFolderStore store;

        public SyncScope(int dispensaId, String pantryPath, SafFolderStore store) {
            this.dispensaId = dispensaId;
            this.pantryPath = pantryPath;
            this.store = store;
        }
    }

    public LocalSafSyncProvider(String deviceId, List<SyncScope> scopes) {
        this.deviceId = deviceId;
        this.scopes = scopes;
    }

    @Override
    public String getId() {
        return "local_saf";
    }

    @Override
    public Single<Boolean> isAvailable() {
        return Single.just(true);
    }

    @Override
    public RemoteStore getRemoteStore() {
        return null;
    }

    @Override
    public Class<? extends ListenableWorker> getWorkerClass() {
        return LocalSafSyncWorker.class;
    }

    public List<FolderSyncEngine> getEngines(Context context) {
        if (engines.isEmpty()) {
            AppDatabase db = AppDatabase.getDatabase(context);
            RoomPantryDataBridge dataBridge = new RoomPantryDataBridge(db);
            String deviceName = android.os.Build.MODEL;

            for (SyncScope scope : scopes) {
                engines.add(new FolderSyncEngine(
                        scope.store,
                        dataBridge,
                        new SyncCursorStoreImpl(context),
                        deviceId,
                        deviceName,
                        scope.pantryPath,
                        scope.dispensaId
                ));
            }
        }
        return engines;
    }
}
