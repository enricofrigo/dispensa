package eu.frigo.dispensa.sync.core.engine;

import android.content.Context;
import androidx.lifecycle.LifecycleOwner;
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
        // 1. Store credentials in EncryptedSharedPreferences (persistent)
        // 2. Initialize the correct SyncProvider
        // 3. Trigger initial sync
    }
}
