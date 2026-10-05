package eu.frigo.dispensa.sync.webdav;

import android.content.Context;
import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.sync.core.provider.SyncProvider;
import eu.frigo.dispensa.sync.core.store.SyncCursorStoreImpl;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import io.reactivex.rxjava3.core.Single;

import java.util.ArrayList;
import java.util.List;

public class WebDavSyncProvider implements SyncProvider {
    private final String id = "webdav";
    private final WebDavClient client;
    private final String deviceId;
    
    public static class SyncScope {
        public final int dispensaId;
        public final String pantryPath;
        public final WebDavClient client;

        public SyncScope(int dispensaId, String pantryPath) {
            this(dispensaId, pantryPath, null);
        }

        public SyncScope(int dispensaId, String pantryPath, WebDavClient client) {
            this.dispensaId = dispensaId;
            this.pantryPath = pantryPath;
            this.client = client;
        }
    }

    private final List<SyncScope> scopes;
    private final List<WebDavSyncEngine> engines = new ArrayList<>();

    public WebDavSyncProvider(WebDavClient client, String deviceId, List<SyncScope> scopes) {
        this.client = client;
        this.deviceId = deviceId;
        this.scopes = scopes;
    }

    @Override
    public String getId() { return id; }

    @Override
    public Single<Boolean> isAvailable() { return Single.just(true); }

    @Override
    public Class<? extends androidx.work.ListenableWorker> getWorkerClass() {
        return eu.frigo.dispensa.sync.webdav.worker.WebDavSyncWorker.class;
    }

    public List<WebDavSyncEngine> getEngines(Context context) {
        if (engines.isEmpty()) {
            AppDatabase db = AppDatabase.getDatabase(context);
            for (SyncScope scope : scopes) {
                WebDavClient engineClient = scope.client != null ? scope.client : client;
                engines.add(new WebDavSyncEngine(
                        engineClient,
                        new SyncCursorStoreImpl(context),
                        deviceId,
                        scope.pantryPath,
                        scope.dispensaId,
                        db,
                        context
                ));
            }
        }
        return engines;
    }
}
