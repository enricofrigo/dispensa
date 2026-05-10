package eu.frigo.dispensa.sync.core.engine;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import android.util.Log;
import androidx.room.RoomDatabase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import eu.frigo.dispensa.sync.core.SyncManager;
import eu.frigo.dispensa.sync.core.SyncTransport;

public class SyncWorker extends Worker {
    private static final String TAG = "SYNC_WORKER";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        Log.d(TAG, "Starting sync orchestration...");

        RoomDatabase database = null;
        try {
            Class<?> dbClass = Class.forName("eu.frigo.dispensa.data.AppDatabase");
            database = (RoomDatabase) dbClass.getMethod("getDatabase", Context.class).invoke(null, context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get AppDatabase", e);
        }

        if (database == null) {
            return Result.failure();
        }

        SyncManager syncManager = new SyncManager(database, context);
        List<SyncTransport> transports = new ArrayList<>();

        // 1. WebDAV
        try {
            Class<?> factory = Class.forName("eu.frigo.dispensa.sync.webdav.WebDavTransportFactory");
            SyncTransport transport = (SyncTransport) factory.getMethod("create", Context.class).invoke(null, context);
            if (transport != null) transports.add(transport);
        } catch (Exception ignored) {}

        // 2. Local Network
        SyncTransport localTransport = null;
        try {
            Class<?> factory = Class.forName("eu.frigo.dispensa.sync.local.LocalTransportFactory");
            localTransport = (SyncTransport) factory.getMethod("create", Context.class, SyncManager.class).invoke(null, context, syncManager);
            if (localTransport != null) {
                transports.add(localTransport);
                // Start local discovery/server
                try { localTransport.getClass().getMethod("start").invoke(localTransport); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // 3. Google Drive
        try {
            Class<?> factory = Class.forName("eu.frigo.dispensa.sync.drive.DriveTransportFactory");
            SyncTransport transport = (SyncTransport) factory.getMethod("create", Context.class, SyncManager.class).invoke(null, context, syncManager);
            if (transport != null) transports.add(transport);
        } catch (Exception ignored) {}

        long lastVer = syncManager.getLastSyncVersion();
        byte[] exported = syncManager.exportChanges(lastVer);

        for (SyncTransport transport : transports) {
            CountDownLatch latch = new CountDownLatch(1);
            Log.d(TAG, "Syncing with transport: " + transport.getClass().getSimpleName());

            transport.push(exported, new SyncTransport.SyncCallback() {
                @Override
                public void onSuccess(byte[] blobBytes) {
                    if (blobBytes != null) {
                        syncManager.importChanges(blobBytes);
                    }
                    latch.countDown();
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Transport sync failed", e);
                    latch.countDown();
                }
            });

            try {
                if (!latch.await(30, TimeUnit.SECONDS)) {
                    Log.w(TAG, "Transport sync timed out");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Final state update
        syncManager.persistLastSyncVersion(syncManager.getMaxSyncClock());
        context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE)
                .edit().putLong("sync_last_epoch_ms", System.currentTimeMillis()).apply();

        // Cleanup
        if (localTransport != null) {
            try { localTransport.getClass().getMethod("stop").invoke(localTransport); } catch (Exception ignored) {}
        }

        Log.d(TAG, "Sync orchestration completed.");
        return Result.success();
    }
}
