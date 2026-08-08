package eu.frigo.dispensa.sync.core.provider;

import android.content.Context;
import io.reactivex.rxjava3.core.Single;

public interface SyncProviderLoader {
    String getProviderType();
    Single<SyncProvider> load(Context context);
    Class<? extends androidx.work.ListenableWorker> getWorkerClass();
}
