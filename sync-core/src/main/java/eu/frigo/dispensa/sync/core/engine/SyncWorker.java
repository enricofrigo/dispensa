package eu.frigo.dispensa.sync.core.engine;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.RoomDatabase;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import eu.frigo.dispensa.sync.core.DatabaseRegistry;
import eu.frigo.dispensa.sync.core.SyncTransport;
import eu.frigo.dispensa.sync.core.TransportRegistry;

/**
 * WorkManager Worker for multi-transport sync orchestration.
 *
 * Uses TransportRegistry (service locator) to get active transports.
 * Uses DatabaseRegistry to get database instance without direct coupling.
 */
public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";
    private static final long TRANSPORT_TIMEOUT_MS = 30_000; // 30 seconds per transport

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "doWork: starting sync");

        try {
            Context context = getApplicationContext();

            // Get database via Registry (No reflection)
            RoomDatabase database = null;
            if (DatabaseRegistry.getProvider() != null) {
                database = DatabaseRegistry.getProvider().getDatabase(context);
            }

            if (database == null) {
                Log.e(TAG, "Sync aborted: Database provider not initialized");
                return Result.failure();
            }

            CrDtSyncManager syncManager = new CrDtSyncManager(database, context);

            // Get registered transports from registry (No reflection)
            TransportRegistry registry = TransportRegistry.getInstance();
            List<SyncTransport> transports = registry.getActiveTransports(context, syncManager);

            if (transports.isEmpty()) {
                Log.d(TAG, "No transports configured");
                return Result.success();
            }

            Log.d(TAG, "Running sync with " + transports.size() + " transport(s)");

            // Run sync with each transport
            for (SyncTransport transport : transports) {
                syncWithTransport(syncManager, transport);
            }

            // Update last synced version
            syncManager.persistLastSyncVersion(syncManager.getMaxSyncClock());

            // Log last sync time to shared prefs for UI
            context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE)
                    .edit().putLong("sync_last_epoch_ms", System.currentTimeMillis()).apply();

            Log.d(TAG, "doWork: sync complete");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "doWork: sync failed", e);
            return Result.retry();
        }
    }

    private void syncWithTransport(CrDtSyncManager syncManager, SyncTransport transport) {
        try {
            // Export local changes
            long lastVersion = syncManager.getLastSyncVersion();
            byte[] localBlob = syncManager.exportChanges(lastVersion);
            Log.d(TAG, "Exported " + (localBlob != null ? localBlob.length : 0) + " bytes for " + transport.getClass().getSimpleName());

            // Push/pull with timeout
            CountDownLatch latch = new CountDownLatch(1);
            final byte[][] remoteBlob = new byte[1][]; // Mutable holder
            final Exception[] error = new Exception[1];

            transport.push(localBlob, new SyncTransport.SyncCallback() {
                @Override
                public void onSuccess(byte[] blobBytes) {
                    remoteBlob[0] = blobBytes;
                    latch.countDown();
                }

                @Override
                public void onError(Exception e) {
                    error[0] = e;
                    latch.countDown();
                }
            });

            boolean completed = latch.await(TRANSPORT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if (!completed) {
                Log.w(TAG, "Transport timeout: " + transport.getClass().getSimpleName());
                return;
            }

            if (error[0] != null) {
                Log.w(TAG, "Transport error: " + transport.getClass().getSimpleName(), error[0]);
                return;
            }

            // Import remote changes
            if (remoteBlob[0] != null) {
                syncManager.importChanges(remoteBlob[0]);
                Log.d(TAG, "Imported peer changes from " + transport.getClass().getSimpleName());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Sync interrupted", e);
        }
    }
}
