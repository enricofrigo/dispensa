package eu.frigo.dispensa.sync.core.engine;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import eu.frigo.dispensa.sync.core.SyncManager;

/**
 * Manages the scheduling of SyncWorker for both manual and periodic synchronization.
 */
public class SyncWorkerScheduler {
    private static final String TAG = "SyncWorkerScheduler";
    private static final String PERIODIC_WORK_NAME = "PERIODIC_SYNC_WORK";
    private static final String ONE_TIME_WORK_NAME = "MANUAL_SYNC_WORK";

    /**
     * Triggers a manual synchronization immediately.
     */
    public static void triggerManualSync(Context context) {
        Log.d(TAG, "Triggering manual sync...");
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .addTag("SYNC_WORKER")
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
        );
    }

    /**
     * Triggers a debounced sync (useful for rapid database changes).
     */
    public static void triggerDebouncedSync(Context context) {
        // For now, same as manual sync, but could implement actual debouncing logic
        triggerManualSync(context);
    }

    /**
     * Schedules periodic background synchronization.
     */
    public static void schedulePeriodicSync(Context context) {
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        long intervalMinutes = Long.parseLong(prefs.getString("sync_interval_minutes", "30"));

        Log.d(TAG, "Scheduling periodic sync every " + intervalMinutes + " minutes");

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                SyncWorker.class, intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .addTag("SYNC_WORKER")
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
        );
    }

    /**
     * Cancels any scheduled periodic synchronization.
     */
    public static void cancelPeriodicSync(Context context) {
        Log.d(TAG, "Canceling periodic sync");
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME);
    }
}
