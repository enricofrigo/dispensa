package eu.frigo.dispensa.sync.core.provider;

import java.util.List;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

public interface RemoteStore {
    Completable upload(String path, byte[] data);
    Single<byte[]> download(String path);
    Single<List<String>> listFiles(String path);
    Completable delete(String path);
}
