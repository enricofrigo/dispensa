package eu.frigo.dispensa.sync.webdav.worker;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.policy.SyncPolicy;
import eu.frigo.dispensa.sync.core.provider.SyncProvider;
import eu.frigo.dispensa.sync.webdav.WebDavSyncProvider;

public class WebDavSyncWorker extends Worker {

    public WebDavSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d("SyncFlow", "WebDavSyncWorker iniziato.");

        // Recupera il provider dal manager (che lo inizializza se necessario dalle preferenze)
        SyncProvider provider = SyncManager.getInstance().getOrInitProvider(getApplicationContext());

        if (provider instanceof WebDavSyncProvider) {
            WebDavSyncProvider webDavProvider = (WebDavSyncProvider) provider;
            try {
                Log.d("SyncFlow", "Esecuzione SyncEngine tramite provider...");
                webDavProvider.getEngine(getApplicationContext())
                        .performSync(new SyncPolicy() {
                            @Override public boolean canSyncNow() { return true; }
                            @Override public long getRetryIntervalMillis() { return 0; }
                        }).blockingAwait();

                Log.d("SyncFlow", "WebDavSyncWorker completato con successo.");
                return Result.success();
            } catch (Exception e) {
                Log.e("SyncFlow", "Errore durante il sync nel worker", e);
                return Result.retry();
            }
        }

        Log.w("SyncFlow", "Nessun provider WebDAV configurato o abilitato. Abort.");
        return Result.success();
    }
}
