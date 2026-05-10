package eu.frigo.dispensa.sync.drive;

import android.accounts.Account;
import android.content.Context;
import android.util.Log;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import eu.frigo.dispensa.sync.core.SyncTransport;
import eu.frigo.dispensa.sync.core.model.SyncChange;

/**
 * Implementation of SyncTransport using Google Drive.
 * Supports both single-user (AppData folder) and household (Shared Folder) modes.
 */
public class GoogleDriveSyncTransport implements SyncTransport {
    private static final String TAG = "GDriveTransport";

    public static final String DRIVE_FILE_NAME = ".dispensa_sync_changes.json";
    public static final String DRIVE_MIME_TYPE = "application/json";
    public static final String APP_DATA_FOLDER = "appDataFolder";
    private static final int MAX_RETRIES = 3;

    private final DriveOperations driveOps;
    private final long backoffBaseMs;

    interface DriveOperations {
        byte[] downloadSyncFile() throws IOException;
        void uploadSyncFile(byte[] content) throws IOException;
    }

    public static class AuthException extends IOException {
        public AuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Single-user AppData constructor.
     */
    public GoogleDriveSyncTransport(Context context, Account account) {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(DriveScopes.DRIVE_APPDATA));
        credential.setSelectedAccount(account);

        Drive driveService = new Drive.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential)
                .setApplicationName("Dispensa")
                .build();

        this.driveOps = new RealDriveOperations(driveService, APP_DATA_FOLDER);
        this.backoffBaseMs = 1_000L;
    }

    /**
     * Household Shared Folder constructor.
     */
    public GoogleDriveSyncTransport(Context context, Account account,
                                   String householdFolderId, String deviceId, String friendlyName) {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(DriveScopes.DRIVE_FILE));
        credential.setSelectedAccount(account);

        Drive driveService = new Drive.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential)
                .setApplicationName("Dispensa")
                .build();

        this.driveOps = new HouseholdDriveOperations(driveService, householdFolderId, deviceId, friendlyName);
        this.backoffBaseMs = 1_000L;
    }

    /**
     * Test constructor.
     */
    GoogleDriveSyncTransport(DriveOperations driveOps) {
        this.driveOps = driveOps;
        this.backoffBaseMs = 0L;
    }

    @Override
    public void push(byte[] data, SyncCallback callback) {
        try {
            // 1. Download existing to ensure we have current remote state (if needed for merge)
            // LWW handles merge, but pulling first ensures we provide response to caller
            byte[] remoteData = driveOps.downloadSyncFile();
            
            // 2. Upload new data with retry
            uploadWithRetry(data);
            
            callback.onSuccess(remoteData);
        } catch (AuthException e) {
            callback.onError(e);
        } catch (IOException e) {
            callback.onError(e);
        }
    }

    @Override
    public void pull(SyncCallback callback) {
        try {
            byte[] data = driveOps.downloadSyncFile();
            callback.onSuccess(data);
        } catch (IOException e) {
            callback.onError(e);
        }
    }

    private void uploadWithRetry(byte[] data) throws IOException {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                driveOps.uploadSyncFile(data);
                return;
            } catch (GoogleJsonResponseException e) {
                int code = e.getStatusCode();
                if (code == 401) {
                    throw new AuthException("Google Drive authentication expired", e);
                } else if (code == 429 || (code >= 500 && code < 600)) {
                    if (attempt == MAX_RETRIES - 1) throw e;
                    sleepBackoff(attempt);
                } else {
                    throw e;
                }
            } catch (IOException e) {
                if (attempt == MAX_RETRIES - 1) throw e;
                sleepBackoff(attempt);
            }
        }
    }

    private void sleepBackoff(int attempt) {
        if (backoffBaseMs <= 0) return;
        try {
            Thread.sleep(backoffBaseMs * (1L << (attempt + 1)));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Standard implementation using the app's hidden data folder.
     */
    private static class RealDriveOperations implements DriveOperations {
        private final Drive drive;
        private final String folderId;

        RealDriveOperations(Drive drive, String folderId) {
            this.drive = drive;
            this.folderId = folderId;
        }

        @Override
        public byte[] downloadSyncFile() throws IOException {
            String fileId = findFileId();
            if (fileId == null) return null;

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            drive.files().get(fileId).executeMediaAndDownloadTo(outputStream);
            return outputStream.toByteArray();
        }

        @Override
        public void uploadSyncFile(byte[] content) throws IOException {
            String fileId = findFileId();
            File metadata = new File();
            metadata.setName(DRIVE_FILE_NAME);
            metadata.setMimeType(DRIVE_MIME_TYPE);

            ByteArrayContent mediaContent = new ByteArrayContent(DRIVE_MIME_TYPE, content);

            if (fileId == null) {
                metadata.setParents(Collections.singletonList(folderId));
                drive.files().create(metadata, mediaContent).execute();
            } else {
                drive.files().update(fileId, null, mediaContent).execute();
            }
        }

        private String findFileId() throws IOException {
            FileList result = drive.files().list()
                    .setQ("name = '" + DRIVE_FILE_NAME + "' and '" + folderId + "' in parents")
                    .setSpaces(folderId.equals(APP_DATA_FOLDER) ? "appDataFolder" : "drive")
                    .setFields("files(id)")
                    .execute();
            List<File> files = result.getFiles();
            return (files != null && !files.isEmpty()) ? files.get(0).getId() : null;
        }
    }

    /**
     * Implementation for Household Shared Folder mode.
     */
    private static class HouseholdDriveOperations implements DriveOperations {
        private final Drive drive;
        private final String folderId;
        private final String deviceId;
        private final String friendlyName;
        private static final Gson GSON = new Gson();

        HouseholdDriveOperations(Drive drive, String folderId, String deviceId, String friendlyName) {
            this.drive = drive;
            this.folderId = folderId;
            this.deviceId = deviceId;
            this.friendlyName = friendlyName;
        }

        @Override
        public byte[] downloadSyncFile() throws IOException {
            FileList result = drive.files().list()
                    .setQ("'" + folderId + "' in parents and name contains 'dispensa_' and name contains '.json'")
                    .setFields("files(id, name)")
                    .execute();

            List<File> files = result.getFiles();
            if (files == null || files.isEmpty()) return null;

            List<SyncChange> allChanges = new ArrayList<>();
            String ownFileName = deviceFileName();
            String legacyOwnFileName = legacyDeviceFileName();

            for (File file : files) {
                if (file.getName().equals(ownFileName) || file.getName().equals(legacyOwnFileName)) {
                    continue;
                }

                try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    drive.files().get(file.getId()).executeMediaAndDownloadTo(outputStream);
                    SyncBlob blob = GSON.fromJson(outputStream.toString("UTF-8"), SyncBlob.class);
                    if (blob != null && blob.changes != null) {
                        allChanges.addAll(blob.changes);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error downloading peer file: " + file.getName(), e);
                }
            }

            if (allChanges.isEmpty()) return null;

            SyncBlob mergedBlob = new SyncBlob();
            mergedBlob.changes = allChanges;

            return GSON.toJson(mergedBlob).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void uploadSyncFile(byte[] content) throws IOException {
            String fileName = deviceFileName();
            String fileId = findDeviceFileId(fileName);

            File metadata = new File();
            metadata.setName(fileName);
            metadata.setMimeType(DRIVE_MIME_TYPE);
            ByteArrayContent mediaContent = new ByteArrayContent(DRIVE_MIME_TYPE, content);

            if (fileId == null) {
                metadata.setParents(Collections.singletonList(folderId));
                drive.files().create(metadata, mediaContent).execute();
            } else {
                drive.files().update(fileId, null, mediaContent).execute();
            }

            // Cleanup legacy file if it exists
            String legacyName = legacyDeviceFileName();
            String legacyId = findDeviceFileId(legacyName);
            if (legacyId != null) {
                try {
                    drive.files().delete(legacyId).execute();
                } catch (IOException e) {
                    Log.w(TAG, "Failed to delete legacy file: " + legacyName, e);
                }
            }
        }

        private String deviceFileName() {
            return "dispensa_" + sanitizeForFilename(friendlyName) + "_" + deviceId + "_dispensa_sync_changes.json";
        }

        private String legacyDeviceFileName() {
            return "dispensa_" + deviceId + ".dispensa_sync_changes.json";
        }

        private String findDeviceFileId(String fileName) throws IOException {
            FileList result = drive.files().list()
                    .setQ("name = '" + fileName + "' and '" + folderId + "' in parents")
                    .setFields("files(id)")
                    .execute();
            List<File> files = result.getFiles();
            return (files != null && !files.isEmpty()) ? files.get(0).getId() : null;
        }

        private static String sanitizeForFilename(String name) {
            if (name == null || name.isEmpty()) return "device";
            String sanitized = name.replaceAll("\\s+", "-").replaceAll("[^a-zA-Z0-9-]", "");
            if (sanitized.length() > 30) sanitized = sanitized.substring(0, 30);
            return sanitized.isEmpty() ? "device" : sanitized;
        }
    }

    private static class SyncBlob {
        String senderDeviceId;
        long senderLastSyncVersion;
        List<SyncChange> changes;
    }
}
