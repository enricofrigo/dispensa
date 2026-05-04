package eu.frigo.dispensa.sync.core.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceManager;
import eu.frigo.dispensa.sync.core.event.SyncBus;
import eu.frigo.dispensa.sync.core.event.SyncEvent;
import eu.frigo.dispensa.sync.core.pairing.PairingPayload;
import eu.frigo.dispensa.sync.core.policy.SyncPolicy;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class SyncCoordinatorImpl implements SyncCoordinator {
    private static SyncCoordinatorImpl instance;
    private final Context context;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private SyncCoordinatorImpl(Context context) {
        this.context = context.getApplicationContext();
        
        disposables.add(SyncBus.getInstance().observe()
                .filter(event -> event instanceof SyncBus.LocalChangeDetected)
                .subscribe(event -> triggerManualSync()));
    }

    public static synchronized SyncCoordinatorImpl getInstance(Context context) {
        if (instance == null) {
            instance = new SyncCoordinatorImpl(context);
        }
        return instance;
    }

    @Override
    public void observeLifecycle(LifecycleOwner lifecycleOwner) {
        // Handled by AppLifecycleObserver usually, but can be used for specific owners
    }

    public void triggerSync(SyncPolicy policy) {
        SyncScheduler.enqueueOneTimeSync(context);
    }

    @Override
    public void triggerManualSync() {
        Log.d("SyncFlow", "Trigger sync manuale o richiesto da cambiamento locale.");
        SyncScheduler.enqueueOneTimeSync(context);
    }

    @Override
    public Observable<SyncEvent> getSyncEvents() {
        return SyncBus.getInstance().observe();
    }
    
    public void enqueueBackgroundSync() {
        SyncScheduler.enqueueOneTimeSync(context);
    }

    public void applyOnboarding(PairingPayload payload) {
        String providerId = payload.providerId != null ? payload.providerId : payload.data.get("providerId");
        Log.d("SyncFlow", "Applicazione onboarding per provider: " + providerId);
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();

        if ("webdav".equals(providerId)) {
            editor.putString(SyncManager.KEY_WEBDAV_URL, payload.data.get("url"));
            editor.putString(SyncManager.KEY_WEBDAV_USER, payload.data.get("user"));
            editor.putString(SyncManager.KEY_WEBDAV_PASS, payload.data.get("pass"));
            editor.putString(SyncManager.KEY_WEBDAV_PATH, payload.data.get("path"));
            editor.putString(SyncManager.SYNC_WEBDAV_PANTRY_KEY, payload.data.get("pantryKey"));
        } else {
            Log.e("SyncFlow", "Provider non supportato per onboarding: " + providerId);
            return;
        }

        editor.putBoolean(SyncManager.KEY_SYNC_ENABLED, true);
        editor.apply();

        // Resetta il cursore per forzare il download completo dal cloud
        new eu.frigo.dispensa.sync.core.store.SyncCursorStoreImpl(context).clear();
        Log.d("SyncFlow", "Cursore resettato per nuovo onboarding.");

        // 2. Initialize the correct SyncProvider
        SyncManager.getInstance().getOrInitProvider(context);

        // 3. Trigger initial sync
        triggerManualSync();
    }
}
