package eu.frigo.dispensa.sync.core.engine;

import eu.frigo.dispensa.sync.core.policy.SyncPolicy;
import io.reactivex.rxjava3.core.Completable;

public interface SyncEngine {
    Completable performSync(SyncPolicy policy);
}
