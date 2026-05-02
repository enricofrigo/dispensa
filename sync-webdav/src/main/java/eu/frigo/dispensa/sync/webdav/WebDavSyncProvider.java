package eu.frigo.dispensa.sync.webdav;

import android.content.Context;
import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.sync.OutboxRepositoryImpl;
import eu.frigo.dispensa.sync.core.provider.RemoteStore;
import eu.frigo.dispensa.sync.core.provider.SyncProvider;
import eu.frigo.dispensa.sync.core.store.SyncCursorStoreImpl;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import io.reactivex.rxjava3.core.Single;

public class WebDavSyncProvider implements SyncProvider {
    private final String id;
    private final RemoteStore remoteStore;
    private final WebDavClient client;
    private final String deviceId;
    private final String pantryPath;
    private WebDavSyncEngine engine;

    public WebDavSyncProvider(String id, RemoteStore remoteStore, WebDavClient client, String deviceId, String pantryPath) {
        this.id = id;
        this.remoteStore = remoteStore;
        this.client = client;
        this.deviceId = deviceId;
        this.pantryPath = pantryPath;
    }

    @Override
    public String getId() { return id; }

    @Override
    public Single<Boolean> isAvailable() { return Single.just(true); }

    @Override
    public RemoteStore getRemoteStore() { return remoteStore; }

    public WebDavSyncEngine getEngine(Context context) {
        if (engine == null) {
            engine = new WebDavSyncEngine(
                    client,
                    new SyncCursorStoreImpl(context),
                    new OutboxRepositoryImpl(AppDatabase.getDatabase(context)),
                    deviceId,
                    pantryPath
            );
        }
        return engine;
    }
}