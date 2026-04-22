package eu.frigo.dispensa;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import eu.frigo.dispensa.util.ThemeHelper;
import eu.frigo.dispensa.work.ExpiryCheckWorker;
import eu.frigo.dispensa.work.ExpiryCheckWorkerScheduler;

public class Dispensa extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeHelper.applyTheme(this);
        createNotificationChannel();
        ExpiryCheckWorkerScheduler.scheduleWorker(this);
    }
    private void createNotificationChannel() {
        CharSequence name = getString(R.string.expiry_notification_channel_name);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(ExpiryCheckWorker.CHANNEL_ID, name, importance);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }
}