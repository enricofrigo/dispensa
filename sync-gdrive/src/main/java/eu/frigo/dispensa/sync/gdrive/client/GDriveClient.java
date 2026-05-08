package eu.frigo.dispensa.sync.gdrive.client;

import android.content.Context;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class GDriveClient {
    private final Drive driveService;
    private final Context context;

    public GDriveClient(Context context, String accountName) {
        this.context = context;
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(DriveScopes.DRIVE_FILE));
        credential.setSelectedAccountName(accountName);

        HttpTransport transport = new NetHttpTransport();
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
        driveService = new Drive.Builder(transport, jsonFactory, credential)
                .setApplicationName("Dispensa")
                .build();
    }

    public List<File> listFiles(String folderId) throws IOException {
        FileList result = driveService.files().list()
                .setQ("'" + folderId + "' in parents and trashed = false")
                .setSpaces("drive")
                .setFields("nextPageToken, files(id, name, mimeType)")
                .execute();
        return result.getFiles();
    }

    public byte[] downloadFile(String fileId) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream);
        return outputStream.toByteArray();
    }

    public File createFile(String name, String mimeType, String parentId, byte[] content) throws IOException {
        File fileMetadata = new File();
        fileMetadata.setName(name);
        fileMetadata.setMimeType(mimeType);
        if (parentId != null) {
            fileMetadata.setParents(Collections.singletonList(parentId));
        }

        com.google.api.client.http.ByteArrayContent mediaContent = new com.google.api.client.http.ByteArrayContent(mimeType, content);
        return driveService.files().create(fileMetadata, mediaContent)
                .setFields("id, name")
                .execute();
    }

    public File updateFile(String fileId, byte[] content) throws IOException {
        com.google.api.client.http.ByteArrayContent mediaContent = new com.google.api.client.http.ByteArrayContent(null, content);
        return driveService.files().update(fileId, null, mediaContent)
                .setFields("id, name")
                .execute();
    }

    public void deleteFile(String fileId) throws IOException {
        driveService.files().delete(fileId).execute();
    }

    public String getOrCreateFolder(String name, String parentId) throws IOException {
        String query = "name = '" + name + "' and mimeType = 'application/vnd.google-apps.folder' and trashed = false";
        if (parentId != null) {
            query += " and '" + parentId + "' in parents";
        }
        FileList result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id)")
                .execute();

        if (!result.getFiles().isEmpty()) {
            return result.getFiles().get(0).getId();
        }

        File fileMetadata = new File();
        fileMetadata.setName(name);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        if (parentId != null) {
            fileMetadata.setParents(Collections.singletonList(parentId));
        }

        File folder = driveService.files().create(fileMetadata)
                .setFields("id")
                .execute();
        return folder.getId();
    }
    
    public File getFileByName(String name, String parentId) throws IOException {
        String query = "name = '" + name + "' and trashed = false";
        if (parentId != null) {
            query += " and '" + parentId + "' in parents";
        }
        FileList result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name, headRevisionId)")
                .execute();

        if (result.getFiles().isEmpty()) {
            return null;
        }
        return result.getFiles().get(0);
    }
}
