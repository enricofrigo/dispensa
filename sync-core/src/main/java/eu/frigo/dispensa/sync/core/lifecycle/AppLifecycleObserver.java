package eu.frigo.dispensa.sync.core.lifecycle;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import eu.frigo.dispensa.sync.core.engine.SyncCoordinatorImpl;
import eu.frigo.dispensa.sync.core.policy.SyncPolicy;

public class AppLifecycleObserver implements DefaultLifecycleObserver {
    private final SyncCoordinatorImpl coordinator;

    public AppLifecycleObserver(SyncCoordinatorImpl coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        // App Foreground: Trigger an immediate sync session
        coordinator.triggerSync(new SyncPolicy() {
            @Override
            public boolean canSyncNow() { return true; }
            @Override
            public long getRetryIntervalMillis() { return 5000; }
        });
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        // App Background: Enqueue a background task to finish pending syncs
        coordinator.enqueueBackgroundSync();
    }
}
