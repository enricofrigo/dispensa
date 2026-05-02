package eu.frigo.dispensa.sync.core.engine;

import android.content.Context;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public class SyncScheduler {
    public static void enqueueOneTimeSync(Context context) {
        // We'll need to define the worker class name, which might be in another module
        // But for now, we can use a generic approach or assume it exists.
        // Given the instructions, it should be WebDavSyncWorker.
        
        try {
            Class<? extends androidx.work.ListenableWorker> workerClass = 
                (Class<? extends androidx.work.ListenableWorker>) Class.forName("eu.frigo.dispensa.sync.webdav.worker.WebDavSyncWorker");

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
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
