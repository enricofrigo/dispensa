package eu.frigo.dispensa.sync.webdav.client;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WebDavClient {
    private final OkHttpClient okHttpClient;
    private final String baseUrl;
    private final String authHeader;

    public WebDavClient(String baseUrl, String username, String password) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.authHeader = Credentials.basic(username, password);
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public Response get(String path) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", authHeader)
                .get()
                .build();
        return okHttpClient.newCall(request).execute();
    }

    public Response put(String path, byte[] content, String eTag) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", authHeader)
                .put(RequestBody.create(content, MediaType.parse("application/json")));

        if (eTag != null) {
            //fix per weak ETag
            builder.header("If-Match", eTag.replace("W/", ""));
        }

        return okHttpClient.newCall(builder.build()).execute();
    }

    public Response propfind(String path) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", authHeader)
                .method("PROPFIND", null)
                .header("Depth", "1")
                .build();
        return okHttpClient.newCall(request).execute();
    }

    public Response mkcol(String path) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", authHeader)
                .method("MKCOL", null)
                .build();
        return okHttpClient.newCall(request).execute();
    }

    public Response delete(String path) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", authHeader)
                .delete()
                .build();
        return okHttpClient.newCall(request).execute();
    }
}
