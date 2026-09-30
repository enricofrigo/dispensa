package eu.frigo.dispensa.sync.gdrive.worker;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import eu.frigo.dispensa.sync.core.engine.FolderSyncEngine;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.policy.SyncPolicy;
import eu.frigo.dispensa.sync.core.provider.SyncProvider;
import eu.frigo.dispensa.sync.gdrive.GDriveSyncProvider;

public class GDriveSyncWorker extends Worker {
    private static final String TAG = "GDriveSyncWorker";

    public GDriveSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "GDriveSyncWorker iniziato.");

        SyncProvider provider = null;
        try {
            provider = SyncManager.getInstance().getOrInitProvider(getApplicationContext()).blockingGet();
        } catch (Exception e) {
            Log.e(TAG, "Failed to init provider in worker", e);
        }

        if (provider instanceof GDriveSyncProvider) {
            GDriveSyncProvider gdriveProvider = (GDriveSyncProvider) provider;
            try {
                for (FolderSyncEngine engine : gdriveProvider.getEngines(getApplicationContext())) {
                    engine.performSync(new SyncPolicy() {
                        @Override public boolean canSyncNow() { return true; }
                        @Override public long getRetryIntervalMillis() { return 0; }
                    }).blockingAwait();
                }
                Log.d(TAG, "GDriveSyncWorker completato con successo.");
                return Result.success();
            } catch (Exception e) {
                Log.e(TAG, "Errore durante il sync GDrive", e);
                return Result.retry();
            }
        }

        return Result.success();
    }
}
