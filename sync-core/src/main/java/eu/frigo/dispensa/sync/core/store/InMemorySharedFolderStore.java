package eu.frigo.dispensa.sync.core.store;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import androidx.annotation.Nullable;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

public class InMemorySharedFolderStore implements SharedFolderStore {
    private final String providerId;
    private final Map<String, byte[]> storage;
    private final Set<String> folders;
    private boolean available = true;

    public InMemorySharedFolderStore() {
        this("in_memory", new ConcurrentHashMap<>(), new HashSet<>());
    }

    public InMemorySharedFolderStore(String providerId, Map<String, byte[]> sharedStorage, Set<String> sharedFolders) {
        this.providerId = providerId;
        this.storage = sharedStorage;
        this.folders = sharedFolders;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public String getProviderId() {
        return providerId;
    }

    @Override
    public Single<Boolean> isAvailable() {
        return Single.just(available);
    }

    @Override
    public Completable ensureFolder(String relativePath) {
        return Completable.fromAction(() -> {
            String path = normalizePath(relativePath);
            if (!path.isEmpty()) {
                String[] parts = path.split("/");
                StringBuilder current = new StringBuilder();
                for (String part : parts) {
                    if (part.isEmpty()) continue;
                    if (current.length() > 0) {
                        current.append("/");
                    }
                    current.append(part);
                    folders.add(current.toString() + "/");
                }
            }
        });
    }

    @Override
    public Completable writeAtomic(String relativePath, byte[] data, @Nullable String expectedEtag) {
        return Completable.fromAction(() -> {
            String path = normalizePath(relativePath);
            storage.put(path, data != null ? data.clone() : new byte[0]);
        });
    }

    @Override
    public Single<byte[]> read(String relativePath) {
        return Single.fromCallable(() -> {
            String path = normalizePath(relativePath);
            byte[] data = storage.get(path);
            if (data == null) {
                throw new FileNotFoundException("File not found: " + path);
            }
            return data.clone();
        });
    }

    @Override
    public Single<Boolean> exists(String relativePath) {
        return Single.fromCallable(() -> {
            String path = normalizePath(relativePath);
            return storage.containsKey(path) || folders.contains(path.endsWith("/") ? path : path + "/");
        });
    }

    @Override
    public Single<List<String>> listFiles(String folderRelativePath) {
        return Single.fromCallable(() -> {
            String folder = normalizePath(folderRelativePath);
            if (!folder.isEmpty() && !folder.endsWith("/")) {
                folder = folder + "/";
            }
            List<String> results = new ArrayList<>();
            for (String key : storage.keySet()) {
                if (key.startsWith(folder)) {
                    String relative = key.substring(folder.length());
                    // Include direct files
                    if (!relative.contains("/") && !relative.isEmpty()) {
                        results.add(relative);
                    }
                }
            }
            return results;
        });
    }

    @Override
    public Completable deleteFile(String relativePath) {
        return Completable.fromAction(() -> {
            String path = normalizePath(relativePath);
            storage.remove(path);
        });
    }

    @Override
    public Completable deleteFolder(String folderRelativePath) {
        return Completable.fromAction(() -> {
            String folder = normalizePath(folderRelativePath);
            if (!folder.isEmpty() && !folder.endsWith("/")) {
                folder = folder + "/";
            }
            final String finalFolder = folder;
            folders.remove(finalFolder);
            storage.keySet().removeIf(k -> k.startsWith(finalFolder));
        });
    }

    private String normalizePath(String path) {
        if (path == null) return "";
        String p = path.trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        return p;
    }
}
