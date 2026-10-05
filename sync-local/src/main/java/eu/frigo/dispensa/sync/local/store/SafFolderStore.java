package eu.frigo.dispensa.sync.local.store;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import eu.frigo.dispensa.sync.core.store.SharedFolderStore;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * Storage Access Framework (SAF) / Local shared folder implementation of SharedFolderStore.
 * Allows syncing with any local or synchronized directory (e.g. Syncthing, Nextcloud Client, NAS).
 */
public class SafFolderStore implements SharedFolderStore {
    private static final String TAG = "SafFolderStore";

    private final Context context;
    private final Uri rootTreeUri;
    private final DocumentFile rootDocumentFile;

    public SafFolderStore(Context context, Uri rootTreeUri) {
        this.context = context.getApplicationContext();
        this.rootTreeUri = rootTreeUri;
        this.rootDocumentFile = DocumentFile.fromTreeUri(this.context, rootTreeUri);
    }

    public SafFolderStore(Context context, DocumentFile rootDocumentFile) {
        this.context = context.getApplicationContext();
        this.rootDocumentFile = rootDocumentFile;
        this.rootTreeUri = rootDocumentFile != null ? rootDocumentFile.getUri() : null;
    }

    @Override
    public String getProviderId() {
        return "local_saf";
    }

    @Override
    public Single<Boolean> isAvailable() {
        return Single.fromCallable(() -> rootDocumentFile != null && rootDocumentFile.exists() && rootDocumentFile.canWrite());
    }

    @Override
    public Completable ensureFolder(String relativePath) {
        return Completable.fromAction(() -> {
            getOrCreateDirectory(relativePath);
        });
    }

    @Override
    public Completable writeAtomic(String relativePath, byte[] data, @Nullable String expectedEtag) {
        return Completable.fromAction(() -> {
            String clean = normalize(relativePath);
            if (clean.isEmpty()) throw new IOException("Invalid file path");

            int lastSlash = clean.lastIndexOf('/');
            String folderPath = (lastSlash >= 0) ? clean.substring(0, lastSlash) : "";
            String fileName = (lastSlash >= 0) ? clean.substring(lastSlash + 1) : clean;

            DocumentFile parentDir = getOrCreateDirectory(folderPath);
            if (parentDir == null || !parentDir.exists()) {
                throw new IOException("Unable to create or access directory: " + folderPath);
            }

            DocumentFile targetFile = parentDir.findFile(fileName);
            if (targetFile == null) {
                targetFile = parentDir.createFile("application/json", fileName);
            }

            if (targetFile == null) {
                throw new IOException("Failed to create file: " + fileName);
            }

            try (OutputStream os = context.getContentResolver().openOutputStream(targetFile.getUri(), "wt")) {
                if (os == null) throw new IOException("Failed to open output stream for " + fileName);
                os.write(data != null ? data : new byte[0]);
                os.flush();
            }
        });
    }

    @Override
    public Single<byte[]> read(String relativePath) {
        return Single.fromCallable(() -> {
            DocumentFile file = findFile(relativePath);
            if (file == null || !file.exists()) {
                throw new FileNotFoundException("File not found: " + relativePath);
            }

            try (InputStream is = context.getContentResolver().openInputStream(file.getUri())) {
                if (is == null) throw new FileNotFoundException("Cannot open stream for: " + relativePath);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] temp = new byte[4096];
                int read;
                while ((read = is.read(temp)) != -1) {
                    buffer.write(temp, 0, read);
                }
                return buffer.toByteArray();
            }
        });
    }

    @Override
    public Single<Boolean> exists(String relativePath) {
        return Single.fromCallable(() -> {
            DocumentFile doc = findFile(relativePath);
            return doc != null && doc.exists();
        });
    }

    @Override
    public Single<List<String>> listFiles(String folderRelativePath) {
        return Single.fromCallable(() -> {
            DocumentFile dir = findFile(folderRelativePath);
            List<String> list = new ArrayList<>();
            if (dir != null && dir.isDirectory()) {
                for (DocumentFile child : dir.listFiles()) {
                    if (child.isFile() && child.getName() != null) {
                        list.add(child.getName());
                    }
                }
            }
            return list;
        });
    }

    @Override
    public Completable deleteFile(String relativePath) {
        return Completable.fromAction(() -> {
            DocumentFile file = findFile(relativePath);
            if (file != null && file.exists()) {
                file.delete();
            }
        });
    }

    @Override
    public Completable deleteFolder(String folderRelativePath) {
        return deleteFile(folderRelativePath);
    }

    private DocumentFile getOrCreateDirectory(String relativePath) {
        String clean = normalize(relativePath);
        if (clean.isEmpty()) return rootDocumentFile;

        String[] parts = clean.split("/");
        DocumentFile current = rootDocumentFile;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            DocumentFile next = current.findFile(part);
            if (next == null || !next.isDirectory()) {
                next = current.createDirectory(part);
            }
            if (next == null) {
                Log.e(TAG, "Failed to create directory segment: " + part);
                return null;
            }
            current = next;
        }
        return current;
    }

    private DocumentFile findFile(String relativePath) {
        String clean = normalize(relativePath);
        if (clean.isEmpty()) return rootDocumentFile;

        String[] parts = clean.split("/");
        DocumentFile current = rootDocumentFile;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;
            if (current == null) return null;
            current = current.findFile(part);
        }
        return current;
    }

    private String normalize(String path) {
        if (path == null) return "";
        String p = path.trim();
        while (p.startsWith("/")) p = p.substring(1);
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }
}
