package eu.frigo.dispensa.sync.core.engine;

import android.content.Context;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.ListenableWorker;

import eu.frigo.dispensa.sync.core.provider.SyncProvider;

public class SyncScheduler {
    public static void enqueueOneTimeSync(Context context) {
        SyncProvider provider = SyncManager.getInstance().getOrInitProvider(context);
        if (provider == null) return;

        Class<? extends ListenableWorker> workerClass = provider.getWorkerClass();
        if (workerClass == null) return;

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(workerClass)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .addTag("SYNC_WORKER")
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                "SESSION_SYNC",
                ExistingWorkPolicy.REPLACE,
                syncRequest
        );
    }
}
