package eu.frigo.dispensa.sync.core.store;

import java.util.List;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

public interface OutboxRepository {
    Single<List<SyncPayload>> getPendingChanges();
    Completable markAsSynced(List<String> syncIds);
}
