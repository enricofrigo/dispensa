package eu.frigo.dispensa.work;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import eu.frigo.dispensa.ui.SettingsFragment;

public class ExpiryCheckWorkerScheduler {

    public static final String EXPIRY_CHECK_WORK_TAG = "expiryCheckWork";

    public static void scheduleWorker(Context context) {
        enqueuePeriodicWork(context, ExistingPeriodicWorkPolicy.KEEP);
    }

    public static void rescheduleWorker(Context context) {
        enqueuePeriodicWork(context, ExistingPeriodicWorkPolicy.UPDATE);
    }

    private static void enqueuePeriodicWork(Context context, ExistingPeriodicWorkPolicy policy) {
        WorkManager workManager = WorkManager.getInstance(context.getApplicationContext());

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        int preferredHour = prefs.getInt(SettingsFragment.KEY_NOTIFICATION_TIME_HOUR, 9);
        int preferredMinute = prefs.getInt(SettingsFragment.KEY_NOTIFICATION_TIME_MINUTE, 0);

        Calendar now = Calendar.getInstance();
        Calendar preferredTimeToday = (Calendar) now.clone();
        preferredTimeToday.set(Calendar.HOUR_OF_DAY, preferredHour);
        preferredTimeToday.set(Calendar.MINUTE, preferredMinute);
        preferredTimeToday.set(Calendar.SECOND, 0);
        preferredTimeToday.set(Calendar.MILLISECOND, 0);

        if (preferredTimeToday.before(now)) {
            preferredTimeToday.add(Calendar.DAY_OF_YEAR, 1);
        }
        long initialDelayMillis = preferredTimeToday.getTimeInMillis() - now.getTimeInMillis();

        Constraints constraints = new Constraints.Builder().build();

        PeriodicWorkRequest expiryCheckRequest =
                new PeriodicWorkRequest.Builder(ExpiryCheckWorker.class, 1, TimeUnit.DAYS)
                        .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                        .setConstraints(constraints)
                        .addTag(EXPIRY_CHECK_WORK_TAG)
                        .build();

        workManager.enqueueUniquePeriodicWork(
                EXPIRY_CHECK_WORK_TAG,
                policy,
                expiryCheckRequest);

        android.util.Log.d("Scheduler", "Worker schedulato (" + policy + ") per le " + preferredHour + ":" + preferredMinute +
                " con ritardo iniziale di " + initialDelayMillis / (1000 * 60) + " minuti.");
    }

    public static void cancelWorker(Context context) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(EXPIRY_CHECK_WORK_TAG);
        android.util.Log.d("Scheduler", "Worker cancellato.");
    }
}
