package eu.frigo.dispensa.sync.core.provider;

import androidx.work.ListenableWorker;
import io.reactivex.rxjava3.core.Single;

public interface SyncProvider {
    String getId();
    Single<Boolean> isAvailable();
    RemoteStore getRemoteStore();
    Class<? extends ListenableWorker> getWorkerClass();
}
