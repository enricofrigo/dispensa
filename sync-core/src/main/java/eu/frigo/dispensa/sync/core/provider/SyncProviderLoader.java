package eu.frigo.dispensa.sync.core.provider;

import android.content.Context;
import androidx.work.ListenableWorker;

public interface SyncProviderLoader {
    String getProviderType();
    SyncProvider load(Context context);
    Class<? extends ListenableWorker> getWorkerClass();
}
