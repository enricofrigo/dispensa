package eu.frigo.dispensa.sync.gdrive;

import android.content.Context;
import androidx.work.ListenableWorker;

import java.util.ArrayList;
import java.util.List;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.sync.RoomPantryDataBridge;
import eu.frigo.dispensa.sync.core.engine.FolderSyncEngine;
import eu.frigo.dispensa.sync.core.provider.RemoteStore;
import eu.frigo.dispensa.sync.core.provider.SyncProvider;
import eu.frigo.dispensa.sync.core.store.SyncCursorStoreImpl;
import eu.frigo.dispensa.sync.gdrive.store.GDriveFolderStore;
import eu.frigo.dispensa.sync.gdrive.worker.GDriveSyncWorker;
import io.reactivex.rxjava3.core.Single;

public class GDriveSyncProvider implements SyncProvider {
    private final String deviceId;
    private final List<SyncScope> scopes;
    private final List<FolderSyncEngine> engines = new ArrayList<>();

    public static class SyncScope {
        public final int dispensaId;
        public final String pantryPath;
        public final GDriveFolderStore store;

        public SyncScope(int dispensaId, String pantryPath, GDriveFolderStore store) {
            this.dispensaId = dispensaId;
            this.pantryPath = pantryPath;
            this.store = store;
        }
    }

    public GDriveSyncProvider(String deviceId, List<SyncScope> scopes) {
        this.deviceId = deviceId;
        this.scopes = scopes;
    }

    @Override
    public String getId() {
        return "gdrive";
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
        return GDriveSyncWorker.class;
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
