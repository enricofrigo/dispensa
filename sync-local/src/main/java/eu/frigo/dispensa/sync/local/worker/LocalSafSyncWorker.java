package eu.frigo.dispensa.sync.local.worker;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import eu.frigo.dispensa.sync.core.engine.FolderSyncEngine;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.policy.SyncPolicy;
import eu.frigo.dispensa.sync.core.provider.SyncProvider;
import eu.frigo.dispensa.sync.local.LocalSafSyncProvider;

public class LocalSafSyncWorker extends Worker {
    private static final String TAG = "LocalSafSyncWorker";

    public LocalSafSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "LocalSafSyncWorker iniziato.");

        SyncProvider provider = null;
        try {
            provider = SyncManager.getInstance().getOrInitProvider(getApplicationContext()).blockingGet();
        } catch (Exception e) {
            Log.e(TAG, "Failed to init provider in worker", e);
        }

        if (provider instanceof LocalSafSyncProvider) {
            LocalSafSyncProvider safProvider = (LocalSafSyncProvider) provider;
            try {
                for (FolderSyncEngine engine : safProvider.getEngines(getApplicationContext())) {
                    engine.performSync(new SyncPolicy() {
                        @Override public boolean canSyncNow() { return true; }
                        @Override public long getRetryIntervalMillis() { return 0; }
                    }).blockingAwait();
                }
                Log.d(TAG, "LocalSafSyncWorker completato con successo.");
                return Result.success();
            } catch (Exception e) {
                Log.e(TAG, "Errore durante il sync SAF", e);
                return Result.retry();
            }
        }

        return Result.success();
    }
}
