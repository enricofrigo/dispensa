package eu.frigo.dispensa.sync.webdav.store;

import android.util.Log;
import androidx.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern STATUS_404_PATTERN =
            Pattern.compile("<(?:[a-zA-Z0-9_-]+:)?status>\\s*HTTP/\\d\\.\\d\\s+404\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RESPONSE_BLOCK_PATTERN =
            Pattern.compile("<(?:[a-zA-Z0-9_-]+:)?response\\b[^>]*>(.*?)</(?:[a-zA-Z0-9_-]+:)?response>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern HREF_PATTERN =
            Pattern.compile("<(?:[a-zA-Z0-9_-]+:)?href>([^<]+)</(?:[a-zA-Z0-9_-]+:)?href>", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLLECTION_PATTERN =
            Pattern.compile("<(?:[a-zA-Z0-9_-]+:)?collection\\s*/?>", Pattern.CASE_INSENSITIVE);

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

                boolean folderExists = checkResourceExists(pathStr + "/", true);
                if (!folderExists) {
                    try (Response response = client.mkcol(pathStr)) {
                        if (!response.isSuccessful() && response.code() != 201 && response.code() != 405) {
                            throw new IOException("MKCOL " + pathStr + " failed with HTTP " + response.code());
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
        return readWithMeta(relativePath).map(fc -> fc.data);
    }

    @Override
    public Single<FileContent> readWithMeta(String relativePath) {
        return Single.fromCallable(() -> {
            String path = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
            try (Response response = client.get(path)) {
                if (response.isSuccessful() && response.body() != null) {
                    String etag = response.header("ETag");
                    return new FileContent(response.body().bytes(), etag);
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

            boolean endsWithSlash = clean.endsWith("/");
            if (!endsWithSlash) {
                try (Response response = client.head(clean)) {
                    if (response.isSuccessful()) {
                        return true;
                    }
                    if (response.code() != 404 && response.code() != 405 && response.code() != 501) {
                        throw new IOException("HTTP " + response.code() + " during HEAD on " + clean);
                    }
                }
                // Check via PROPFIND (supports both files and folders without trailing slash)
                if (checkResourceExists(clean, false)) {
                    return true;
                }
                return checkResourceExists(clean + "/", true);
            } else {
                return checkResourceExists(clean, true);
            }
        });
    }

    private boolean checkResourceExists(String path, boolean isFolder) throws IOException {
        try (Response response = client.propfind(path, "0")) {
            int code = response.code();
            if (code == 404) {
                return false;
            }
            if (code == 207) {
                String body = response.body() != null ? response.body().string() : "";
                return !STATUS_404_PATTERN.matcher(body).find();
            }
            if (response.isSuccessful()) {
                return true;
            }
            throw new IOException("HTTP " + code + " during PROPFIND on " + path + " (isFolder=" + isFolder + ")");
        }
    }

    @Override
    public Single<List<String>> listFiles(String folderRelativePath) {
        return Single.fromCallable(() -> {
            String path = folderRelativePath.startsWith("/") ? folderRelativePath.substring(1) : folderRelativePath;
            if (!path.endsWith("/") && !path.isEmpty()) {
                path = path + "/";
            }
            List<String> results = new ArrayList<>();
            try (Response response = client.propfind(path, "1")) {
                if (response.code() == 404) {
                    return results;
                }
                if (!response.isSuccessful() && response.code() != 207) {
                    throw new IOException("Failed to list files for " + path + ": HTTP " + response.code());
                }
                if (response.body() != null) {
                    String xml = response.body().string();
                    String cleanFolder = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
                    String folderName = cleanFolder.contains("/") ? cleanFolder.substring(cleanFolder.lastIndexOf("/") + 1) : cleanFolder;

                    Matcher blockMatcher = RESPONSE_BLOCK_PATTERN.matcher(xml);
                    boolean foundBlocks = false;
                    while (blockMatcher.find()) {
                        foundBlocks = true;
                        String block = blockMatcher.group(1);
                        if (block == null) continue;
                        if (COLLECTION_PATTERN.matcher(block).find() || STATUS_404_PATTERN.matcher(block).find()) {
                            continue;
                        }
                        Matcher hrefMatcher = HREF_PATTERN.matcher(block);
                        if (hrefMatcher.find()) {
                            addHrefFileIfValid(hrefMatcher.group(1), folderName, results);
                        }
                    }

                    if (!foundBlocks) {
                        Matcher hrefMatcher = HREF_PATTERN.matcher(xml);
                        while (hrefMatcher.find()) {
                            addHrefFileIfValid(hrefMatcher.group(1), folderName, results);
                        }
                    }
                }
            }
            return results;
        });
    }

    private void addHrefFileIfValid(String href, String folderName, List<String> results) throws Exception {
        if (href == null) return;
        String decoded = java.net.URLDecoder.decode(href, "UTF-8");
        if (decoded.endsWith("/")) return;

        String fileName = decoded;
        if (fileName.contains("/")) {
            fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
        }
        if (fileName.equals(folderName)) return;

        if (!fileName.isEmpty() && !results.contains(fileName)) {
            results.add(fileName);
        }
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
        String clean = folderRelativePath.startsWith("/") ? folderRelativePath.substring(1) : folderRelativePath;
        if (!clean.isEmpty() && !clean.endsWith("/")) {
            clean = clean + "/";
        }
        return deleteFile(clean);
    }
}
