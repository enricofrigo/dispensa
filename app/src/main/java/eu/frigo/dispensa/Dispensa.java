package eu.frigo.dispensa;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.util.Log;

import androidx.lifecycle.ProcessLifecycleOwner;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.sync.SyncInitializer;
import eu.frigo.dispensa.sync.core.DatabaseRegistry;
import eu.frigo.dispensa.sync.core.engine.SyncCoordinatorImpl;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.engine.SyncWorkerScheduler;
import eu.frigo.dispensa.sync.core.lifecycle.AppLifecycleObserver;
import eu.frigo.dispensa.sync.webdav.WebDavSyncProviderLoader;
import eu.frigo.dispensa.util.ThemeHelper;
import eu.frigo.dispensa.work.ExpiryCheckWorker;
import eu.frigo.dispensa.work.ExpiryCheckWorkerScheduler;

public class Dispensa extends Application {

    private static final String TAG = "Dispensa";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Application onCreate");
        ThemeHelper.applyTheme(this);
        createNotificationChannel();
        ExpiryCheckWorkerScheduler.scheduleWorker(this);

        // 1. Register Database Provider (Avoids reflection in sync-core)
        DatabaseRegistry.setProvider(AppDatabase::getDatabase);

        // 2. Initialize Sync Transports via flavor-specific initializers (No reflection)
        SyncInitializer.init(this);

        // 3. Schedule periodic background sync
        SyncWorkerScheduler.schedulePeriodicSync(this);

        // Legacy Sync setup (kept for transition)
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
