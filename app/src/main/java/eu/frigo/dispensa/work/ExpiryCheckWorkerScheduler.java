package eu.frigo.dispensa.work;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;
import eu.frigo.dispensa.ui.SettingsFragment;

public class ExpiryCheckWorkerScheduler {

    private static final String EXPIRY_CHECK_WORK_TAG = "expiryCheckWork";

    public static void scheduleWorker(Context context) {
        WorkManager workManager = WorkManager.getInstance(context.getApplicationContext());

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        int preferredHour = prefs.getInt(SettingsFragment.KEY_NOTIFICATION_TIME_HOUR, 9); // Default 9 AM
        int preferredMinute = prefs.getInt(SettingsFragment.KEY_NOTIFICATION_TIME_MINUTE, 0); // Default 00 minutes

        // Calcola il ritardo iniziale per far partire il worker all'ora desiderata
        Calendar now = Calendar.getInstance();
        Calendar preferredTimeToday = (Calendar) now.clone();
        preferredTimeToday.set(Calendar.HOUR_OF_DAY, preferredHour);
        preferredTimeToday.set(Calendar.MINUTE, preferredMinute);
        preferredTimeToday.set(Calendar.SECOND, 0);
        preferredTimeToday.set(Calendar.MILLISECOND, 0);

        long initialDelayMillis;
        if (preferredTimeToday.before(now)) {
            // L'ora preferita per oggi è già passata, schedula per domani a quell'ora
            preferredTimeToday.add(Calendar.DAY_OF_YEAR, 1);
        }
        initialDelayMillis = preferredTimeToday.getTimeInMillis() - now.getTimeInMillis();

        Constraints constraints = new Constraints.Builder()
                // Puoi aggiungere vincoli come .setRequiredNetworkType(NetworkType.CONNECTED)
                // .setRequiresCharging(true)
                .build();

        PeriodicWorkRequest expiryCheckRequest =
                new PeriodicWorkRequest.Builder(ExpiryCheckWorker.class, 1, TimeUnit.DAYS) // Ripeti ogni giorno
                        .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                        .setConstraints(constraints)
                        .addTag(EXPIRY_CHECK_WORK_TAG)
                        .build();

        workManager.enqueueUniquePeriodicWork(
                EXPIRY_CHECK_WORK_TAG,
                ExistingPeriodicWorkPolicy.REPLACE, // REPLACE per aggiornare con il nuovo orario/ritardo
                expiryCheckRequest);

        android.util.Log.d("Scheduler", "Worker schedulato per le " + preferredHour + ":" + preferredMinute +
                " con ritardo iniziale di " + initialDelayMillis / (1000 * 60) + " minuti.");
    }

    public static void cancelWorker(Context context) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(EXPIRY_CHECK_WORK_TAG);
        android.util.Log.d("Scheduler", "Worker cancellato.");
    }
}
