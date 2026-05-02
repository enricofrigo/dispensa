package eu.frigo.dispensa.sync.core.event;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

public class SyncBus {
    private static SyncBus instance;
    private final PublishSubject<SyncEvent> subject = PublishSubject.create();

    private SyncBus() {}

    public static synchronized SyncBus getInstance() {
        if (instance == null) {
            instance = new SyncBus();
        }
        return instance;
    }

    public void post(SyncEvent event) {
        subject.onNext(event);
    }

    public Observable<SyncEvent> observe() {
        return subject;
    }

    public static class LocalChangeDetected extends SyncEvent {}
}
