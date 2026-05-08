package eu.frigo.dispensa.sync.gdrive.worker;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.policy.SyncPolicy;
import eu.frigo.dispensa.sync.core.provider.SyncProvider;
import eu.frigo.dispensa.sync.gdrive.GDriveSyncProvider;

public class GDriveSyncWorker extends Worker {

    public GDriveSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d("GDriveSync", "GDriveSyncWorker iniziato.");

        SyncProvider provider = SyncManager.getInstance().getOrInitProvider(getApplicationContext());

        if (provider instanceof GDriveSyncProvider) {
            GDriveSyncProvider gdriveProvider = (GDriveSyncProvider) provider;
            try {
                gdriveProvider.getEngine(getApplicationContext())
                        .performSync(new SyncPolicy() {
                            @Override public boolean canSyncNow() { return true; }
                            @Override public long getRetryIntervalMillis() { return 0; }
                        }).blockingAwait();

                Log.d("GDriveSync", "GDriveSyncWorker completato con successo.");
                return Result.success();
            } catch (Exception e) {
                Log.e("GDriveSync", "Errore durante il sync nel worker", e);
                return Result.retry();
            }
        }

        Log.w("GDriveSync", "Nessun provider GDrive configurato o abilitato. Abort.");
        return Result.success();
    }
}
