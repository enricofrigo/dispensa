package eu.frigo.dispensa.data.sync;

import java.util.ArrayList;
import java.util.List;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.sync.core.store.OutboxRepository;
import eu.frigo.dispensa.sync.core.store.SyncPayload;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

public class OutboxRepositoryImpl implements OutboxRepository {
    private final SyncOutboxDao dao;

    public OutboxRepositoryImpl(AppDatabase db) {
        this.dao = db.syncOutboxDao();
    }

    @Override
    public Single<List<SyncPayload>> getPendingChanges() {
        return Single.fromCallable(() -> {
            List<eu.frigo.dispensa.data.sync.SyncOutbox> entries = dao.getPendingChangesSync();
            List<SyncPayload> payloads = new ArrayList<>();
            for (SyncOutbox entry : entries) {
                payloads.add(new SyncPayload(entry.syncId, entry.dataType, entry.payload, entry.timestamp));
            }
            return payloads;
        });
    }

    @Override
    public Completable markAsSynced(List<String> syncIds) {
        return Completable.fromAction(() -> dao.markAsSynced(syncIds));
    }
}
