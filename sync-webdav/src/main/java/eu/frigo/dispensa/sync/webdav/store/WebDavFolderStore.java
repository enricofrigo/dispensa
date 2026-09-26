package eu.frigo.dispensa.sync.webdav.store;

import android.util.Log;
import androidx.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import eu.frigo.dispensa.sync.core.store.SharedFolderStore;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import okhttp3.Response;

/**
 * WebDAV implementation of SharedFolderStore.
 */
public class WebDavFolderStore implements SharedFolderStore {
    private static final String TAG = "WebDavFolderStore";
    private final WebDavClient client;

    public WebDavFolderStore(WebDavClient client) {
        this.client = client;
    }

    @Override
    public String getProviderId() {
        return "webdav";
    }

    @Override
    public Single<Boolean> isAvailable() {
        return Single.fromCallable(() -> {
            try (Response response = client.propfind("")) {
                return response.isSuccessful() || response.code() == 207 || response.code() == 404;
            } catch (Exception e) {
                Log.w(TAG, "WebDAV availability check failed", e);
                return false;
            }
        });
    }

    @Override
    public Completable ensureFolder(String relativePath) {
        return Completable.fromAction(() -> {
            String clean = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
            if (clean.endsWith("/")) {
                clean = clean.substring(0, clean.length() - 1);
            }
            if (clean.isEmpty()) return;

            String[] parts = clean.split("/");
            StringBuilder currentPath = new StringBuilder();
            for (String part : parts) {
                if (part.isEmpty()) continue;
                if (currentPath.length() > 0) {
                    currentPath.append("/");
                }
                currentPath.append(part);
                String pathStr = currentPath.toString();

                boolean folderExists = false;
                try (Response response = client.propfind(pathStr + "/", "0")) {
                    if (response.code() == 207) {
                        String body = response.body().string();
                        if (!body.contains("NotFound") && !body.contains("<d:status>HTTP/1.1 404")) {
                            folderExists = true;
                        }
                    } else if (response.isSuccessful()) {
                        folderExists = true;
                    }
                } catch (Exception ignored) {
                    Log.e(TAG, "Failed to check if folder exists", ignored);
                }

                if (!folderExists) {
                    try (Response response = client.mkcol(pathStr)) {
                        if (!response.isSuccessful() && response.code() != 201 && response.code() != 405) {
                            Log.w(TAG, "MKCOL " + pathStr + " returned " + response.code());
                        }
                    }
                }
            }
        });
    }

    @Override
    public Completable writeAtomic(String relativePath, byte[] data, @Nullable String expectedEtag) {
        return Completable.fromAction(() -> {
            String path = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
            try (Response response = client.put(path, data, expectedEtag)) {
                if (!response.isSuccessful()) {
                    if (response.code() == 412) {
                        throw new IOException("Concurrency conflict (412 Precondition Failed) on " + path);
                    }
                    throw new IOException("Failed to write file " + path + ": HTTP " + response.code());
                }
            }
        });
    }

    @Override
    public Single<byte[]> read(String relativePath) {
        return Single.fromCallable(() -> {
            String path = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
            try (Response response = client.get(path)) {
                if (response.isSuccessful() && response.body() != null) {
                    return response.body().bytes();
                } else if (response.code() == 404) {
                    throw new FileNotFoundException("File not found on WebDAV: " + path);
                } else {
                    throw new IOException("Failed to read " + path + ": HTTP " + response.code());
                }
            }
        });
    }

    @Override
    public Single<Boolean> exists(String relativePath) {
        return Single.fromCallable(() -> {
            String clean = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
            if (clean.isEmpty()) return true;

            boolean isDirectory = clean.endsWith("/") || (!clean.contains(".") && !clean.contains("snap_") && !clean.contains("manifest"));

            if (!isDirectory) {
                // It's a file -> use HEAD request first
                try (Response response = client.head(clean)) {
                    if (response.isSuccessful()) {
                        return true;
                    }
                    if (response.code() == 404) {
                        return false;
                    }
                } catch (Exception ignored) {}

                // Fallback: PROPFIND with Depth 0
                try (Response response = client.propfind(clean, "0")) {
                    if (response.code() == 404) return false;
                    if (response.code() == 207) {
                        String body = response.body().string();
                        return !body.contains("404 Not Found") && !body.contains("<d:status>HTTP/1.1 404");
                    }
                    return response.isSuccessful();
                } catch (Exception e) {
                    return false;
                }
            } else {
                // It's a directory -> use PROPFIND with Depth 0
                String folderPath = clean.endsWith("/") ? clean : clean + "/";
                try (Response response = client.propfind(folderPath, "0")) {
                    if (response.code() == 404) return false;
                    if (response.code() == 207) {
                        String body = response.body().string();
                        Log.d(TAG, "PROPFIND response: " + body);
                        return !body.contains("NotFound") && !body.contains("<d:status>HTTP/1.1 404");
                    }
                    return response.isSuccessful();
                } catch (Exception e) {
                    return false;
                }
            }
        });
    }

    @Override
    public Single<List<String>> listFiles(String folderRelativePath) {
        return Single.fromCallable(() -> {
            // Simplified: list files via PROPFIND if implemented by server, or return empty list
            String path = folderRelativePath.startsWith("/") ? folderRelativePath.substring(1) : folderRelativePath;
            if (!path.endsWith("/") && !path.isEmpty()) {
                path = path + "/";
            }
            try (Response response = client.propfind(path)) {
                return new ArrayList<>();
            }
        });
    }

    @Override
    public Completable deleteFile(String relativePath) {
        return Completable.fromAction(() -> {
            String path = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
            try (Response response = client.delete(path)) {
                if (!response.isSuccessful() && response.code() != 404) {
                    throw new IOException("Failed to delete " + path + ": HTTP " + response.code());
                }
            }
        });
    }

    @Override
    public Completable deleteFolder(String folderRelativePath) {
        return deleteFile(folderRelativePath);
    }
}
