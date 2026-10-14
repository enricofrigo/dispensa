package eu.frigo.dispensa.sync.webdav.store;

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
import java.util.concurrent.ConcurrentHashMap;

import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

@RunWith(RobolectricTestRunner.class)
public class WebDavFolderStoreContractTest {

    private MockWebServer server;
    private final Map<String, byte[]> remoteStorage = new ConcurrentHashMap<>();
    private final java.util.Set<String> remoteFolders = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private WebDavFolderStore folderStore;

    @Before
    public void setup() throws IOException {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path == null) path = "";
                if (path.startsWith("/")) path = path.substring(1);
                String method = request.getMethod();

                if ("HEAD".equals(method)) {
                    if (remoteStorage.containsKey(path)) {
                        return new MockResponse().setResponseCode(200);
                    }
                    return new MockResponse().setResponseCode(404);
                }
                if ("MKCOL".equals(method)) {
                    remoteFolders.add(path.endsWith("/") ? path : path + "/");
                    return new MockResponse().setResponseCode(201);
                }
                if ("PROPFIND".equals(method)) {
                    String folderPath = path.endsWith("/") ? path : path + "/";
                    String depth = request.getHeader("Depth");
                    if ("1".equals(depth)) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("<d:multistatus xmlns:d=\"DAV:\">");
                        sb.append("<d:response><d:href>/").append(folderPath).append("</d:href></d:response>");
                        for (String key : remoteStorage.keySet()) {
                            if (key.startsWith(folderPath)) {
                                String relative = key.substring(folderPath.length());
                                if (!relative.contains("/")) {
                                    sb.append("<d:response><d:href>/").append(key).append("</d:href></d:response>");
                                }
                            }
                        }
                        sb.append("</d:multistatus>");
                        return new MockResponse().setResponseCode(207).setBody(sb.toString());
                    }
                    if (remoteStorage.containsKey(path) || remoteFolders.contains(folderPath) || path.isEmpty()) {
                        return new MockResponse().setResponseCode(207).setBody("<d:multistatus><d:response><d:status>HTTP/1.1 200 OK</d:status></d:response></d:multistatus>");
                    }
                    return new MockResponse().setResponseCode(404);
                }
                if ("PUT".equals(method)) {
                    byte[] body = request.getBody().readByteArray();
                    remoteStorage.put(path, body);
                    return new MockResponse().setResponseCode(200).setHeader("ETag", "\"tag_" + System.currentTimeMillis() + "\"");
                }
                if ("GET".equals(method)) {
                    byte[] data = remoteStorage.get(path);
                    if (data != null) {
                        return new MockResponse().setResponseCode(200).setBody(new okio.Buffer().write(data));
                    }
                    return new MockResponse().setResponseCode(404);
                }
                if ("DELETE".equals(method)) {
                    remoteStorage.remove(path);
                    remoteFolders.remove(path.endsWith("/") ? path : path + "/");
                    return new MockResponse().setResponseCode(204);
                }
                return new MockResponse().setResponseCode(400);
            }
        });
        server.start();

        WebDavClient client = new WebDavClient(server.url("/").toString(), "user", "pass");
        folderStore = new WebDavFolderStore(client);
    }

    @After
    public void teardown() throws IOException {
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    public void testFolderCreationAndExistence() {
        folderStore.ensureFolder("pantry-webdav-test/events/").blockingAwait();
        Assert.assertTrue(folderStore.exists("pantry-webdav-test/events/").blockingGet());
    }

    @Test
    public void testAtomicWriteAndRead() {
        byte[] data = "{\"version\": 2, \"pantryName\": \"Cucina WebDAV\"}".getBytes(StandardCharsets.UTF_8);
        folderStore.writeAtomic("pantry-webdav-test/manifest.json", data, null).blockingAwait();

        Assert.assertTrue(folderStore.exists("pantry-webdav-test/manifest.json").blockingGet());
        byte[] read = folderStore.read("pantry-webdav-test/manifest.json").blockingGet();
        Assert.assertArrayEquals(data, read);
    }

    @Test
    public void testDeleteFileAndFolder() {
        folderStore.writeAtomic("pantry-webdav-test/snapshots/snap_1.json", "s1".getBytes(StandardCharsets.UTF_8), null).blockingAwait();
        Assert.assertTrue(folderStore.exists("pantry-webdav-test/snapshots/snap_1.json").blockingGet());

        folderStore.deleteFile("pantry-webdav-test/snapshots/snap_1.json").blockingAwait();
        Assert.assertFalse(folderStore.exists("pantry-webdav-test/snapshots/snap_1.json").blockingGet());
    }

    @Test
    public void testListFiles() {
        byte[] data = "{}".getBytes(StandardCharsets.UTF_8);
        folderStore.writeAtomic("pantry-webdav-test/devices/dev1.json", data, null).blockingAwait();
        folderStore.writeAtomic("pantry-webdav-test/devices/dev2.json", data, null).blockingAwait();

        List<String> files = folderStore.listFiles("pantry-webdav-test/devices").blockingGet();
        Assert.assertEquals(2, files.size());
        Assert.assertTrue(files.contains("dev1.json"));
        Assert.assertTrue(files.contains("dev2.json"));
    }
}
