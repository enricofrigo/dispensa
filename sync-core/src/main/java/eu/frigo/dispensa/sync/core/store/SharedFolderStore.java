package eu.frigo.dispensa.sync.core.store;

import java.util.List;
import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * Common abstraction for any shared folder storage provider
 * (e.g. WebDAV, Android SAF DocumentFile, Google Drive, OneDrive, etc.).
 */
public interface SharedFolderStore {
    
    /**
     * Unique identifier for the provider (e.g. "webdav", "local_saf", "gdrive", "onedrive").
     */
    String getProviderId();

    /**
     * Checks if the storage provider is available and accessible.
     */
    Single<Boolean> isAvailable();

    /**
     * Ensures that the specified folder hierarchy exists.
     * @param relativePath relative path to folder (e.g., "pantry-xyz-sync/events/")
     */
    Completable ensureFolder(String relativePath);

    /**
     * Atomically writes data to the specified file path.
     * If expectedEtag is non-null, the implementation may perform concurrency validation (e.g., HTTP 412 / ETag).
     */
    Completable writeAtomic(String relativePath, byte[] data, @Nullable String expectedEtag);

    /**
     * Reads the entire byte content of the file.
     */
    Single<byte[]> read(String relativePath);

    /**
     * Checks if a file exists at the given relative path.
     */
    Single<Boolean> exists(String relativePath);

    /**
     * Lists file names/relative paths inside the given folder path.
     */
    Single<List<String>> listFiles(String folderRelativePath);

    /**
     * Deletes the specified file.
     */
    Completable deleteFile(String relativePath);

    /**
     * Deletes the specified folder and its contents.
     */
    Completable deleteFolder(String folderRelativePath);
}
