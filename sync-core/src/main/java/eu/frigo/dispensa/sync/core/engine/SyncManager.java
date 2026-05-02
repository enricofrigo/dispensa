package eu.frigo.dispensa.sync.core.engine;

import eu.frigo.dispensa.sync.core.provider.SyncProvider;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

public class SyncManager {
    private static SyncManager instance;
    private final BehaviorSubject<SyncProvider> currentProvider = BehaviorSubject.create();

    private SyncManager() {}

    public static synchronized SyncManager getInstance() {
        if (instance == null) {
            instance = new SyncManager();
        }
        return instance;
    }

    public void setProvider(SyncProvider provider) {
        currentProvider.onNext(provider);
    }

    public BehaviorSubject<SyncProvider> getCurrentProvider() {
        return currentProvider;
    }
}
