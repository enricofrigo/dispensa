package eu.frigo.dispensa.sync.gdrive;

import android.content.Context;
import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.sync.OutboxRepositoryImpl;
import eu.frigo.dispensa.sync.core.provider.RemoteStore;
import eu.frigo.dispensa.sync.core.provider.SyncProvider;
import eu.frigo.dispensa.sync.core.store.SyncCursorStoreImpl;
import eu.frigo.dispensa.sync.gdrive.client.GDriveClient;
import io.reactivex.rxjava3.core.Single;

public class GDriveSyncProvider implements SyncProvider {
    private final String id;
    private final RemoteStore remoteStore;
    private final GDriveClient client;
    private final String deviceId;
    private final String pantryFolderId;
    private GDriveSyncEngine engine;

    public GDriveSyncProvider(String id, RemoteStore remoteStore, GDriveClient client, String deviceId, String pantryFolderId) {
        this.id = id;
        this.remoteStore = remoteStore;
        this.client = client;
        this.deviceId = deviceId;
        this.pantryFolderId = pantryFolderId;
    }

    @Override
    public String getId() { return id; }

    @Override
    public Single<Boolean> isAvailable() { return Single.just(true); }

    @Override
    public RemoteStore getRemoteStore() { return remoteStore; }

    @Override
    public Class<? extends androidx.work.ListenableWorker> getWorkerClass() {
        return eu.frigo.dispensa.sync.gdrive.worker.GDriveSyncWorker.class;
    }

    public GDriveSyncEngine getEngine(Context context) {
        if (engine == null) {
            AppDatabase db = AppDatabase.getDatabase(context);
            engine = new GDriveSyncEngine(
                    context,
                    client,
                    new SyncCursorStoreImpl(context),
                    new OutboxRepositoryImpl(db),
                    deviceId,
                    pantryFolderId,
                    db
            );
        }
        return engine;
    }
}
