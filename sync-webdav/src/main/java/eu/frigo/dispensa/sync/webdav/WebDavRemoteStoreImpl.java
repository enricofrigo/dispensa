package eu.frigo.dispensa.sync.webdav;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import eu.frigo.dispensa.sync.core.provider.RemoteStore;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import okhttp3.Response;

public class WebDavRemoteStoreImpl implements RemoteStore {
    private final WebDavClient client;

    public WebDavRemoteStoreImpl(WebDavClient client) {
        this.client = client;
    }

    @Override
    public Completable upload(String path, byte[] data) {
        return Completable.fromAction(() -> {
            try (Response response = client.put(path, data, null)) {
                if (!response.isSuccessful()) {
                    throw new IOException("Failed to upload: " + response.code());
                }
            }
        });
    }

    @Override
    public Single<byte[]> download(String path) {
        return Single.fromCallable(() -> {
            try (Response response = client.get(path)) {
                if (response.isSuccessful() && response.body() != null) {
                    return response.body().bytes();
                }
                throw new IOException("Failed to download: " + response.code());
            }
        });
    }

    @Override
    public Single<List<String>> listFiles(String path) {
        return Single.fromCallable(() -> {
            // Simplified: In a real WebDAV impl, this would parse the XML from PROPFIND
            try (Response response = client.propfind(path)) {
                if (response.isSuccessful()) {
                    return new ArrayList<>(); // Parsing logic omitted for brevity
                }
                throw new IOException("Failed to list: " + response.code());
            }
        });
    }

    @Override
    public Completable delete(String path) {
        return Completable.error(new UnsupportedOperationException("Delete not implemented for append-only sync"));
    }
}
