package eu.frigo.dispensa.sync.gdrive.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class GDriveClient {
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=UTF-8");
    private static final MediaType OCTET_STREAM_MEDIA_TYPE = MediaType.parse("application/octet-stream");
    public static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

    private final OkHttpClient okHttpClient;
    private final GDriveTokenProvider tokenProvider;
    private final String apiBaseUrl;
    private final String uploadBaseUrl;
    private final Gson gson = new Gson();

    public static class DriveFile {
        public String id;
        public String name;
        public String mimeType;
        public String md5Checksum;
        public String version;

        public DriveFile(String id, String name, String mimeType) {
            this.id = id;
            this.name = name;
            this.mimeType = mimeType;
        }

        public boolean isFolder() {
            return FOLDER_MIME_TYPE.equals(mimeType);
        }
    }

    public GDriveClient(GDriveTokenProvider tokenProvider) {
        this(tokenProvider, "https://www.googleapis.com/drive/v3/", "https://www.googleapis.com/upload/drive/v3/");
    }

    public GDriveClient(GDriveTokenProvider tokenProvider, String apiBaseUrl, String uploadBaseUrl) {
        this.tokenProvider = tokenProvider;
        this.apiBaseUrl = apiBaseUrl.endsWith("/") ? apiBaseUrl : apiBaseUrl + "/";
        this.uploadBaseUrl = uploadBaseUrl.endsWith("/") ? uploadBaseUrl : uploadBaseUrl + "/";
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private Request.Builder newAuthenticatedRequest(String url) throws IOException {
        String token = tokenProvider != null ? tokenProvider.getAccessToken() : null;
        Request.Builder builder = new Request.Builder().url(url);
        if (token != null && !token.trim().isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private Response executeWithRetry(Request request) throws IOException {
        Response response = okHttpClient.newCall(request).execute();
        if (response.code() == 401 && tokenProvider != null) {
            response.close();
            String oldAuth = request.header("Authorization");
            if (oldAuth != null && oldAuth.startsWith("Bearer ")) {
                tokenProvider.invalidateToken(oldAuth.substring(7));
            }
            String newToken = tokenProvider.getAccessToken();
            Request retryRequest = request.newBuilder()
                    .header("Authorization", "Bearer " + newToken)
                    .build();
            return okHttpClient.newCall(retryRequest).execute();
        }
        return response;
    }

    public boolean testConnection() {
        try {
            HttpUrl url = HttpUrl.parse(apiBaseUrl + "about").newBuilder()
                    .addQueryParameter("fields", "user")
                    .build();
            Request request = newAuthenticatedRequest(url.toString()).get().build();
            try (Response response = executeWithRetry(request)) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            return false;
        }
    }

    public DriveFile findFile(String parentId, String name) throws IOException {
        String escapedName = name.replace("'", "\\'");
        String query = "'" + parentId + "' in parents and name = '" + escapedName + "' and trashed = false";
        HttpUrl url = HttpUrl.parse(apiBaseUrl + "files").newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("fields", "files(id, name, mimeType, md5Checksum, version)")
                .addQueryParameter("pageSize", "10")
                .addQueryParameter("supportsAllDrives", "true")
                .addQueryParameter("includeItemsFromAllDrives", "true")
                .build();

        Request request = newAuthenticatedRequest(url.toString()).get().build();
        try (Response response = executeWithRetry(request)) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to query file " + name + " in parent " + parentId + ": HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) return null;
            JsonObject json = gson.fromJson(body.string(), JsonObject.class);
            JsonArray files = json.getAsJsonArray("files");
            if (files != null && files.size() > 0) {
                JsonObject first = files.get(0).getAsJsonObject();
                DriveFile df = new DriveFile(
                        first.get("id").getAsString(),
                        first.get("name").getAsString(),
                        first.has("mimeType") ? first.get("mimeType").getAsString() : null
                );
                if (first.has("md5Checksum")) df.md5Checksum = first.get("md5Checksum").getAsString();
                if (first.has("version")) df.version = first.get("version").getAsString();
                return df;
            }
            return null;
        }
    }

    public List<DriveFile> listFiles(String parentId) throws IOException {
        String query = "'" + parentId + "' in parents and trashed = false";
        HttpUrl url = HttpUrl.parse(apiBaseUrl + "files").newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("fields", "files(id, name, mimeType, md5Checksum, version)")
                .addQueryParameter("pageSize", "1000")
                .addQueryParameter("supportsAllDrives", "true")
                .addQueryParameter("includeItemsFromAllDrives", "true")
                .build();

        Request request = newAuthenticatedRequest(url.toString()).get().build();
        try (Response response = executeWithRetry(request)) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to list files in folder " + parentId + ": HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) return Collections.emptyList();
            JsonObject json = gson.fromJson(body.string(), JsonObject.class);
            JsonArray files = json.getAsJsonArray("files");
            if (files == null || files.size() == 0) return Collections.emptyList();

            List<DriveFile> result = new ArrayList<>();
            for (JsonElement el : files) {
                JsonObject obj = el.getAsJsonObject();
                DriveFile df = new DriveFile(
                        obj.get("id").getAsString(),
                        obj.get("name").getAsString(),
                        obj.has("mimeType") ? obj.get("mimeType").getAsString() : null
                );
                if (obj.has("md5Checksum")) df.md5Checksum = obj.get("md5Checksum").getAsString();
                if (obj.has("version")) df.version = obj.get("version").getAsString();
                result.add(df);
            }
            return result;
        }
    }

    public DriveFile createFolder(String parentId, String folderName) throws IOException {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("name", folderName);
        metadata.addProperty("mimeType", FOLDER_MIME_TYPE);
        if (parentId != null && !parentId.isEmpty()) {
            JsonArray parents = new JsonArray();
            parents.add(parentId);
            metadata.add("parents", parents);
        }

        RequestBody body = RequestBody.create(metadata.toString(), JSON_MEDIA_TYPE);
        HttpUrl url = HttpUrl.parse(apiBaseUrl + "files").newBuilder()
                .addQueryParameter("supportsAllDrives", "true")
                .build();

        Request request = newAuthenticatedRequest(url.toString()).post(body).build();
        try (Response response = executeWithRetry(request)) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to create folder " + folderName + ": HTTP " + response.code());
            }
            ResponseBody resBody = response.body();
            if (resBody == null) throw new IOException("Empty response creating folder " + folderName);
            JsonObject resJson = gson.fromJson(resBody.string(), JsonObject.class);
            return new DriveFile(
                    resJson.get("id").getAsString(),
                    resJson.get("name").getAsString(),
                    FOLDER_MIME_TYPE
            );
        }
    }

    public DriveFile createFile(String parentId, String name, byte[] content, String mimeType) throws IOException {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("name", name);
        if (mimeType != null) {
            metadata.addProperty("mimeType", mimeType);
        }
        if (parentId != null && !parentId.isEmpty()) {
            JsonArray parents = new JsonArray();
            parents.add(parentId);
            metadata.add("parents", parents);
        }

        MediaType mediaType = mimeType != null ? MediaType.parse(mimeType) : OCTET_STREAM_MEDIA_TYPE;
        RequestBody metadataPart = RequestBody.create(metadata.toString(), JSON_MEDIA_TYPE);
        RequestBody mediaPart = RequestBody.create(content, mediaType);

        MultipartBody multipartBody = new MultipartBody.Builder()
                .setType(MediaType.parse("multipart/related"))
                .addPart(metadataPart)
                .addPart(mediaPart)
                .build();

        HttpUrl url = HttpUrl.parse(uploadBaseUrl + "files").newBuilder()
                .addQueryParameter("uploadType", "multipart")
                .addQueryParameter("supportsAllDrives", "true")
                .build();

        Request request = newAuthenticatedRequest(url.toString()).post(multipartBody).build();
        try (Response response = executeWithRetry(request)) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to create file " + name + ": HTTP " + response.code());
            }
            ResponseBody resBody = response.body();
            if (resBody == null) throw new IOException("Empty response creating file " + name);
            JsonObject resJson = gson.fromJson(resBody.string(), JsonObject.class);
            return new DriveFile(
                    resJson.get("id").getAsString(),
                    resJson.get("name").getAsString(),
                    mimeType
            );
        }
    }

    public void updateFile(String fileId, byte[] content, String mimeType) throws IOException {
        MediaType mediaType = mimeType != null ? MediaType.parse(mimeType) : OCTET_STREAM_MEDIA_TYPE;
        RequestBody body = RequestBody.create(content, mediaType);

        HttpUrl url = HttpUrl.parse(uploadBaseUrl + "files/" + fileId).newBuilder()
                .addQueryParameter("uploadType", "media")
                .addQueryParameter("supportsAllDrives", "true")
                .build();

        Request request = newAuthenticatedRequest(url.toString()).patch(body).build();
        try (Response response = executeWithRetry(request)) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to update file " + fileId + ": HTTP " + response.code());
            }
        }
    }

    public byte[] readFile(String fileId) throws IOException {
        HttpUrl url = HttpUrl.parse(apiBaseUrl + "files/" + fileId).newBuilder()
                .addQueryParameter("alt", "media")
                .addQueryParameter("supportsAllDrives", "true")
                .build();

        Request request = newAuthenticatedRequest(url.toString()).get().build();
        try (Response response = executeWithRetry(request)) {
            if (response.code() == 404) {
                throw new FileNotFoundException("File not found on Google Drive: " + fileId);
            }
            if (!response.isSuccessful()) {
                throw new IOException("Failed to read file " + fileId + ": HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) return new byte[0];
            return body.bytes();
        }
    }

    public void delete(String fileId) throws IOException {
        HttpUrl url = HttpUrl.parse(apiBaseUrl + "files/" + fileId).newBuilder()
                .addQueryParameter("supportsAllDrives", "true")
                .build();

        Request request = newAuthenticatedRequest(url.toString()).delete().build();
        try (Response response = executeWithRetry(request)) {
            if (!response.isSuccessful() && response.code() != 404) {
                throw new IOException("Failed to delete file " + fileId + ": HTTP " + response.code());
            }
        }
    }
}
