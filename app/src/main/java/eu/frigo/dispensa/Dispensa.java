package eu.frigo.dispensa;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import androidx.lifecycle.ProcessLifecycleOwner;

import eu.frigo.dispensa.sync.core.engine.SyncCoordinatorImpl;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.lifecycle.AppLifecycleObserver;
import eu.frigo.dispensa.sync.webdav.WebDavSyncProviderLoader;
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

        // Initialize Sync
        SyncManager.getInstance().registerLoader(new WebDavSyncProviderLoader());
        SyncCoordinatorImpl coordinator = SyncCoordinatorImpl.getInstance(this);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(
                new AppLifecycleObserver(coordinator)
        );
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