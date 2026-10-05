package eu.frigo.dispensa.sync.gdrive.store;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import eu.frigo.dispensa.sync.core.store.SharedFolderStore;
import eu.frigo.dispensa.sync.gdrive.client.GDriveClient;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * Google Drive implementation of SharedFolderStore.
 */
public class GDriveFolderStore implements SharedFolderStore {
    private static final String TAG = "GDriveFolderStore";

    private final GDriveClient client;
    private final String rootFolderId;
    private final Map<String, String> folderIdCache = new ConcurrentHashMap<>();

    public GDriveFolderStore(@NonNull GDriveClient client) {
        this(client, "root");
    }

    public GDriveFolderStore(@NonNull GDriveClient client, @Nullable String rootFolderId) {
        this.client = client;
        this.rootFolderId = (rootFolderId != null && !rootFolderId.trim().isEmpty()) ? rootFolderId.trim() : "root";
        this.folderIdCache.put("", this.rootFolderId);
    }

    @Override
    public String getProviderId() {
        return "gdrive";
    }

    @Override
    public Single<Boolean> isAvailable() {
        return Single.fromCallable(() -> {
            try {
                return client.testConnection();
            } catch (Exception e) {
                Log.w(TAG, "GDrive availability check failed", e);
                return false;
            }
        });
    }

    private String cleanPath(String path) {
        if (path == null) return "";
        String clean = path.trim();
        if (clean.startsWith("/")) clean = clean.substring(1);
        if (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        return clean;
    }

    @Nullable
    private String resolveFolderId(String folderRelativePath, boolean createIfMissing) throws IOException {
        String path = cleanPath(folderRelativePath);
        if (path.isEmpty()) {
            return rootFolderId;
        }

        String cached = folderIdCache.get(path);
        if (cached != null) {
            return cached;
        }

        String[] segments = path.split("/");
        String currentParentId = rootFolderId;
        StringBuilder currentPathBuilder = new StringBuilder();

        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            if (currentPathBuilder.length() > 0) {
                currentPathBuilder.append("/");
            }
            currentPathBuilder.append(segment);
            String currentPath = currentPathBuilder.toString();

            String segmentFolderId = folderIdCache.get(currentPath);
            if (segmentFolderId == null) {
                GDriveClient.DriveFile found = client.findFile(currentParentId, segment);
                if (found != null && found.isFolder()) {
                    segmentFolderId = found.id;
                } else if (createIfMissing) {
                    GDriveClient.DriveFile created = client.createFolder(currentParentId, segment);
                    segmentFolderId = created.id;
                } else {
                    return null;
                }
                folderIdCache.put(currentPath, segmentFolderId);
            }
            currentParentId = segmentFolderId;
        }

        return currentParentId;
    }

    @Nullable
    private String resolveFileId(String relativePath) throws IOException {
        String path = cleanPath(relativePath);
        int lastSlash = path.lastIndexOf('/');
        String parentPath = lastSlash >= 0 ? path.substring(0, lastSlash) : "";
        String fileName = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;

        String parentFolderId = resolveFolderId(parentPath, false);
        if (parentFolderId == null) {
            return null;
        }

        GDriveClient.DriveFile file = client.findFile(parentFolderId, fileName);
        return file != null ? file.id : null;
    }

    @Override
    public Completable ensureFolder(String relativePath) {
        return Completable.fromAction(() -> {
            resolveFolderId(relativePath, true);
        });
    }

    @Override
    public Completable writeAtomic(String relativePath, byte[] data, @Nullable String expectedEtag) {
        return Completable.fromAction(() -> {
            String path = cleanPath(relativePath);
            int lastSlash = path.lastIndexOf('/');
            String parentPath = lastSlash >= 0 ? path.substring(0, lastSlash) : "";
            String fileName = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;

            String parentFolderId = resolveFolderId(parentPath, true);
            if (parentFolderId == null) {
                throw new IOException("Failed to resolve or create parent folder for " + relativePath);
            }

            GDriveClient.DriveFile existing = client.findFile(parentFolderId, fileName);
            if (existing != null) {
                client.updateFile(existing.id, data, "application/json");
            } else {
                client.createFile(parentFolderId, fileName, data, "application/json");
            }
        });
    }

    @Override
    public Single<byte[]> read(String relativePath) {
        return Single.fromCallable(() -> {
            String fileId = resolveFileId(relativePath);
            if (fileId == null) {
                throw new FileNotFoundException("File not found on Google Drive: " + relativePath);
            }
            return client.readFile(fileId);
        });
    }

    @Override
    public Single<Boolean> exists(String relativePath) {
        return Single.fromCallable(() -> {
            try {
                String fileId = resolveFileId(relativePath);
                if (fileId != null) return true;
                String folderId = resolveFolderId(relativePath, false);
                return folderId != null;
            } catch (Exception e) {
                return false;
            }
        });
    }

    @Override
    public Single<List<String>> listFiles(String folderRelativePath) {
        return Single.fromCallable(() -> {
            String folderId = resolveFolderId(folderRelativePath, false);
            if (folderId == null) {
                return Collections.emptyList();
            }
            List<GDriveClient.DriveFile> driveFiles = client.listFiles(folderId);
            List<String> fileNames = new ArrayList<>();
            for (GDriveClient.DriveFile df : driveFiles) {
                fileNames.add(df.name);
            }
            return fileNames;
        });
    }

    @Override
    public Completable deleteFile(String relativePath) {
        return Completable.fromAction(() -> {
            String fileId = resolveFileId(relativePath);
            if (fileId != null) {
                client.delete(fileId);
            }
        });
    }

    @Override
    public Completable deleteFolder(String folderRelativePath) {
        return Completable.fromAction(() -> {
            String clean = cleanPath(folderRelativePath);
            String folderId = resolveFolderId(clean, false);
            if (folderId != null && !folderId.equals(rootFolderId)) {
                client.delete(folderId);
                folderIdCache.entrySet().removeIf(entry -> entry.getKey().equals(clean) || entry.getKey().startsWith(clean + "/"));
            }
        });
    }

    public String getRootFolderId() {
        return rootFolderId;
    }
}
