package eu.frigo.dispensa.sync.gdrive.store;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import eu.frigo.dispensa.sync.gdrive.client.GDriveClient;
import eu.frigo.dispensa.sync.gdrive.client.GDriveTokenProvider;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

@RunWith(RobolectricTestRunner.class)
public class GDriveFolderStoreContractTest {

    private MockWebServer server;
    private final Gson gson = new Gson();

    static class MockFile {
        String id;
        String name;
        String mimeType;
        String parentId;
        byte[] content;

        MockFile(String id, String name, String mimeType, String parentId, byte[] content) {
            this.id = id;
            this.name = name;
            this.mimeType = mimeType;
            this.parentId = parentId;
            this.content = content;
        }
    }

    private final Map<String, MockFile> filesById = new ConcurrentHashMap<>();
    private GDriveFolderStore folderStore;

    @Before
    public void setup() throws IOException {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path == null) path = "";
                String method = request.getMethod();

                if (path.startsWith("/drive/v3/about")) {
                    JsonObject about = new JsonObject();
                    JsonObject user = new JsonObject();
                    user.addProperty("emailAddress", "test@gmail.com");
                    about.add("user", user);
                    return new MockResponse().setResponseCode(200).setBody(gson.toJson(about));
                }

                if (path.startsWith("/drive/v3/files")) {
                    if ("GET".equals(method)) {
                        if (path.contains("alt=media")) {
                            // Read file content
                            String fileId = path.substring("/drive/v3/files/".length(), path.indexOf("?"));
                            MockFile mf = filesById.get(fileId);
                            if (mf != null && mf.content != null) {
                                return new MockResponse().setResponseCode(200).setBody(new okio.Buffer().write(mf.content));
                            }
                            return new MockResponse().setResponseCode(404);
                        } else if (path.contains("?q=")) {
                            // Query
                            String q = request.getRequestUrl().queryParameter("q");
                            JsonArray filesArr = new JsonArray();
                            if (q != null) {
                                // Extract parent and name if present
                                String parentId = null;
                                String name = null;
                                for (String part : q.split(" and ")) {
                                    if (part.contains("in parents")) {
                                        parentId = part.replace("'", "").replace(" in parents", "").trim();
                                    } else if (part.contains("name =")) {
                                        name = part.substring(part.indexOf("name =") + 6).replace("'", "").trim();
                                    }
                                }

                                for (MockFile mf : filesById.values()) {
                                    boolean matchParent = (parentId == null || parentId.equals(mf.parentId));
                                    boolean matchName = (name == null || name.equals(mf.name));
                                    if (matchParent && matchName) {
                                        JsonObject obj = new JsonObject();
                                        obj.addProperty("id", mf.id);
                                        obj.addProperty("name", mf.name);
                                        obj.addProperty("mimeType", mf.mimeType);
                                        filesArr.add(obj);
                                    }
                                }
                            }
                            JsonObject result = new JsonObject();
                            result.add("files", filesArr);
                            return new MockResponse().setResponseCode(200).setBody(gson.toJson(result));
                        }
                    } else if ("POST".equals(method)) {
                        // Create folder
                        String body = request.getBody().readUtf8();
                        JsonObject reqJson = gson.fromJson(body, JsonObject.class);
                        String name = reqJson.get("name").getAsString();
                        String parentId = "root";
                        if (reqJson.has("parents") && reqJson.getAsJsonArray("parents").size() > 0) {
                            parentId = reqJson.getAsJsonArray("parents").get(0).getAsString();
                        }
                        String id = UUID.randomUUID().toString();
                        MockFile folder = new MockFile(id, name, GDriveClient.FOLDER_MIME_TYPE, parentId, null);
                        filesById.put(id, folder);

                        JsonObject resJson = new JsonObject();
                        resJson.addProperty("id", id);
                        resJson.addProperty("name", name);
                        resJson.addProperty("mimeType", GDriveClient.FOLDER_MIME_TYPE);
                        return new MockResponse().setResponseCode(200).setBody(gson.toJson(resJson));
                    } else if ("DELETE".equals(method)) {
                        String fileId = path.substring("/drive/v3/files/".length());
                        int queryIdx = fileId.indexOf("?");
                        if (queryIdx >= 0) fileId = fileId.substring(0, queryIdx);
                        filesById.remove(fileId);
                        return new MockResponse().setResponseCode(204);
                    }
                }

                if (path.startsWith("/upload/drive/v3/files")) {
                    if ("POST".equals(method)) {
                        // Multipart create file
                        String id = UUID.randomUUID().toString();
                        byte[] bodyBytes = request.getBody().readByteArray();
                        // For simplicity in mock: parse filename or use default
                        String parentId = "root";
                        String name = "file";
                        String bodyStr = new String(bodyBytes, StandardCharsets.UTF_8);
                        if (bodyStr.contains("\"name\":\"")) {
                            int start = bodyStr.indexOf("\"name\":\"") + 8;
                            int end = bodyStr.indexOf("\"", start);
                            name = bodyStr.substring(start, end);
                        }
                        if (bodyStr.contains("\"parents\":[\"")) {
                            int start = bodyStr.indexOf("\"parents\":[\"") + 12;
                            int end = bodyStr.indexOf("\"", start);
                            parentId = bodyStr.substring(start, end);
                        }

                        // Extract JSON content if present
                        byte[] contentBytes = bodyBytes;
                        if (bodyStr.contains("application/json\r\n\r\n")) {
                            int start = bodyStr.indexOf("application/json\r\n\r\n") + 20;
                            int end = bodyStr.lastIndexOf("\r\n--");
                            if (end > start) {
                                contentBytes = bodyStr.substring(start, end).getBytes(StandardCharsets.UTF_8);
                            }
                        }

                        MockFile mf = new MockFile(id, name, "application/json", parentId, contentBytes);
                        filesById.put(id, mf);

                        JsonObject res = new JsonObject();
                        res.addProperty("id", id);
                        res.addProperty("name", name);
                        res.addProperty("mimeType", "application/json");
                        return new MockResponse().setResponseCode(200).setBody(gson.toJson(res));
                    } else if ("PATCH".equals(method)) {
                        // Media update
                        String fileId = path.substring("/upload/drive/v3/files/".length());
                        int queryIdx = fileId.indexOf("?");
                        if (queryIdx >= 0) fileId = fileId.substring(0, queryIdx);
                        MockFile mf = filesById.get(fileId);
                        if (mf != null) {
                            mf.content = request.getBody().readByteArray();
                            return new MockResponse().setResponseCode(200);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                }

                return new MockResponse().setResponseCode(404);
            }
        });
        server.start();

        String baseUrl = server.url("/drive/v3/").toString();
        String uploadUrl = server.url("/upload/drive/v3/").toString();
        GDriveTokenProvider tokenProvider = new GDriveTokenProvider() {
            @Override
            public String getAccessToken() {
                return "mock-token";
            }

            @Override
            public void invalidateToken(String token) {}
        };
        GDriveClient client = new GDriveClient(tokenProvider, baseUrl, uploadUrl);
        folderStore = new GDriveFolderStore(client, "root");
    }

    @After
    public void teardown() throws IOException {
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    public void testIsAvailable() {
        Assert.assertTrue(folderStore.isAvailable().blockingGet());
    }

    @Test
    public void testFolderCreationAndExistence() {
        folderStore.ensureFolder("pantry-test/events/").blockingAwait();
        Assert.assertTrue(folderStore.exists("pantry-test/events/").blockingGet());
    }

    @Test
    public void testAtomicWriteAndRead() {
        byte[] data = "{\"version\": 2, \"pantryName\": \"Cucina GDrive\"}".getBytes(StandardCharsets.UTF_8);
        folderStore.writeAtomic("pantry-test/manifest.json", data, null).blockingAwait();

        Assert.assertTrue(folderStore.exists("pantry-test/manifest.json").blockingGet());
        byte[] readBytes = folderStore.read("pantry-test/manifest.json").blockingGet();
        Assert.assertNotNull(readBytes);
        Assert.assertEquals(new String(data, StandardCharsets.UTF_8), new String(readBytes, StandardCharsets.UTF_8));
    }

    @Test
    public void testListFiles() {
        byte[] data = "{}".getBytes(StandardCharsets.UTF_8);
        folderStore.writeAtomic("pantry-test/events/event1.json", data, null).blockingAwait();
        folderStore.writeAtomic("pantry-test/events/event2.json", data, null).blockingAwait();

        List<String> files = folderStore.listFiles("pantry-test/events").blockingGet();
        Assert.assertEquals(2, files.size());
        Assert.assertTrue(files.contains("event1.json"));
        Assert.assertTrue(files.contains("event2.json"));
    }

    @Test
    public void testDeleteFileAndFolder() {
        byte[] data = "{}".getBytes(StandardCharsets.UTF_8);
        folderStore.writeAtomic("pantry-test/events/event1.json", data, null).blockingAwait();
        Assert.assertTrue(folderStore.exists("pantry-test/events/event1.json").blockingGet());

        folderStore.deleteFile("pantry-test/events/event1.json").blockingAwait();
        Assert.assertFalse(folderStore.exists("pantry-test/events/event1.json").blockingGet());

        folderStore.deleteFolder("pantry-test/events").blockingAwait();
        Assert.assertFalse(folderStore.exists("pantry-test/events").blockingGet());
    }
}
