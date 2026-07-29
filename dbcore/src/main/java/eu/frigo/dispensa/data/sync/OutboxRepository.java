package eu.frigo.dispensa.data.sync;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

public interface OutboxRepository {
    Single<List<SyncPayload>> getPendingChanges(int dispensaId);
    Completable markAsSynced(List<String> syncIds);
}
