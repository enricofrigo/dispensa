package eu.frigo.dispensa.sync.webdav;

import eu.frigo.dispensa.sync.core.provider.RemoteStore;
import eu.frigo.dispensa.sync.core.provider.SyncProvider;
import io.reactivex.rxjava3.core.Single;

public class WebDavSyncProvider implements SyncProvider {
    private final String id;
    private final RemoteStore remoteStore;

    public WebDavSyncProvider(String id, RemoteStore remoteStore) {
        this.id = id;
        this.remoteStore = remoteStore;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Single<Boolean> isAvailable() {
        // Implementation could check network or perform a simple PROPFIND /
        return Single.just(true);
    }

    @Override
    public RemoteStore getRemoteStore() {
        return remoteStore;
    }
}
