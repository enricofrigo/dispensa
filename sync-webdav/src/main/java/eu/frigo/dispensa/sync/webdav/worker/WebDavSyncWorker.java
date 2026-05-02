package eu.frigo.dispensa.sync.webdav.worker;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import eu.frigo.dispensa.sync.core.engine.SyncEngine;
import eu.frigo.dispensa.sync.core.policy.SyncPolicy;
import eu.frigo.dispensa.sync.core.engine.SyncManager;

public class WebDavSyncWorker extends Worker {

    public WebDavSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // In a real implementation, we would get the engine from SyncManager
        // or a DI container. For now, we'll assume it can be instantiated or retrieved.
        
        // SyncEngine engine = SyncManager.getInstance().getCurrentEngine();
        // if (engine != null) {
        //     engine.performSync(new DefaultSyncPolicy()).blockingAwait();
        // }
        
        return Result.success();
    }
}
