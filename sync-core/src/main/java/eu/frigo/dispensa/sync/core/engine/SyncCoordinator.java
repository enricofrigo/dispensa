package eu.frigo.dispensa.sync.core.engine;

import androidx.lifecycle.LifecycleOwner;
import eu.frigo.dispensa.sync.core.event.SyncEvent;
import io.reactivex.rxjava3.core.Observable;

public interface SyncCoordinator {
    void observeLifecycle(LifecycleOwner lifecycleOwner);
    void triggerManualSync();
    Observable<SyncEvent> getSyncEvents();
}
