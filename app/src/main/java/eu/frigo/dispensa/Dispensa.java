package eu.frigo.dispensa;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import eu.frigo.dispensa.util.LocaleHelper;
import eu.frigo.dispensa.util.ThemeHelper;
import eu.frigo.dispensa.work.ExpiryCheckWorker;
import eu.frigo.dispensa.work.ExpiryCheckWorkerScheduler;

public class Dispensa extends Application {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }
    @Override
    public void onCreate() {
        super.onCreate();
        ThemeHelper.applyTheme(this);
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
}