package eu.frigo.dispensa;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import eu.frigo.dispensa.work.ExpiryCheckWorker;
import eu.frigo.dispensa.work.ExpiryCheckWorkerScheduler;

public class Dispensa extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        ExpiryCheckWorkerScheduler.scheduleWorker(this);
    }

    private void createNotificationChannel() {
        CharSequence name = getString(R.string.expiry_notification_channel_name);
        String description = getString(R.string.expiry_notification_channel_description);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(ExpiryCheckWorker.CHANNEL_ID, name, importance);
        channel.setDescription(description);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }
    private void scheduleExpiryCheckWorker() {
        PeriodicWorkRequest expiryCheckRequest =
                new PeriodicWorkRequest.Builder(ExpiryCheckWorker.class, 1, TimeUnit.DAYS)
                        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "expiryCheckWork",
                ExistingPeriodicWorkPolicy.KEEP,
                expiryCheckRequest);
    }
}