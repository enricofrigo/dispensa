package eu.frigo.dispensa.sync.webdav;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;

import eu.frigo.dispensa.sync.core.model.PantryDevice;
import eu.frigo.dispensa.sync.core.model.PantryEvent;
import eu.frigo.dispensa.sync.core.model.PantryManifest;
import eu.frigo.dispensa.sync.core.model.PantrySnapshot;
import eu.frigo.dispensa.sync.core.provider.SyncProvider;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;

@RunWith(RobolectricTestRunner.class)
public class Phase3ModelUnificationTest {

    private final Gson gson = new Gson();

    @Test
    public void testPantryDeviceDeserializationFromSnakeCase() {
        String json = "{\"device_id\":\"dev_123\",\"device_name\":\"Pixel 8\",\"last_seen\":1700000000000}";
        PantryDevice device = gson.fromJson(json, PantryDevice.class);

        Assert.assertNotNull(device);
        Assert.assertEquals("dev_123", device.deviceId);
        Assert.assertEquals("Pixel 8", device.deviceName);
        Assert.assertEquals(1700000000000L, device.lastSeen);
    }

    @Test
    public void testPantryDeviceDeserializationFromCamelCase() {
        String json = "{\"deviceId\":\"dev_456\",\"deviceName\":\"Galaxy S24\",\"lastSeen\":1700000050000}";
        PantryDevice device = gson.fromJson(json, PantryDevice.class);

        Assert.assertNotNull(device);
        Assert.assertEquals("dev_456", device.deviceId);
        Assert.assertEquals("Galaxy S24", device.deviceName);
        Assert.assertEquals(1700000050000L, device.lastSeen);
    }

    @Test
    public void testPantryDeviceSerialization() {
        PantryDevice device = new PantryDevice("dev_789", "OnePlus 12");
        device.lastSeen = 1710000000000L;

        String json = gson.toJson(device);
        JsonObject obj = gson.fromJson(json, JsonObject.class);

        Assert.assertTrue(obj.has("device_id"));
        Assert.assertTrue(obj.has("device_name"));
        Assert.assertTrue(obj.has("last_seen"));
        Assert.assertEquals("dev_789", obj.get("device_id").getAsString());
        Assert.assertEquals("OnePlus 12", obj.get("device_name").getAsString());
        Assert.assertEquals(1710000000000L, obj.get("last_seen").getAsLong());
    }

    @Test
    public void testPantryManifestSerializationAndDeserialization() {
        PantryManifest manifest = new PantryManifest();
        manifest.version = 2;
        manifest.pantryKey = "secret-key-123";
        manifest.pantryName = "Dispensa Casa";
        manifest.createdByDevice = "dev_master";
        manifest.provider = "webdav";
        manifest.latestSnapshotId = "snap_1000.json";
        manifest.lastGlobalTimestamp = 1715000000000L;
        manifest.activeEventFiles = Arrays.asList("ev_1.json", "ev_2.json");

        String json = gson.toJson(manifest);
        PantryManifest decoded = gson.fromJson(json, PantryManifest.class);

        Assert.assertNotNull(decoded);
        Assert.assertEquals(2, decoded.version);
        Assert.assertEquals("secret-key-123", decoded.pantryKey);
        Assert.assertEquals("Dispensa Casa", decoded.pantryName);
        Assert.assertEquals("dev_master", decoded.createdByDevice);
        Assert.assertEquals("webdav", decoded.provider);
        Assert.assertEquals("snap_1000.json", decoded.latestSnapshotId);
        Assert.assertEquals(1715000000000L, decoded.lastGlobalTimestamp);
        Assert.assertEquals(2, decoded.activeEventFiles.size());
        Assert.assertTrue(decoded.activeEventFiles.contains("ev_1.json"));
    }

    @Test
    public void testPantrySnapshotShoppingItemsCompatibility() {
        // Test camelCase "shoppingItems"
        String jsonCamel = "{\"timestamp\":1700000000000,\"products\":[],\"locations\":[],\"shoppingItems\":[{\"name\":\"Latte\"}]}";
        PantrySnapshot snapshotCamel = gson.fromJson(jsonCamel, PantrySnapshot.class);
        Assert.assertNotNull(snapshotCamel.shoppingItems);
        Assert.assertEquals(1, snapshotCamel.shoppingItems.size());

        // Test snake_case "shopping_items" (legacy webdav snapshot format)
        String jsonSnake = "{\"timestamp\":1700000000000,\"products\":[],\"locations\":[],\"shopping_items\":[{\"name\":\"Pane\"}]}";
        PantrySnapshot snapshotSnake = gson.fromJson(jsonSnake, PantrySnapshot.class);
        Assert.assertNotNull(snapshotSnake.shoppingItems);
        Assert.assertEquals(1, snapshotSnake.shoppingItems.size());
    }

    @Test
    public void testPantryEventActionConstants() {
        Assert.assertEquals("UPSERT_PRODUCT", PantryEvent.ACTION_UPSERT_PRODUCT);
        Assert.assertEquals("DELETE_PRODUCT", PantryEvent.ACTION_DELETE_PRODUCT);
        Assert.assertEquals("UPSERT_LOCATION", PantryEvent.ACTION_UPSERT_LOCATION);
        Assert.assertEquals("DELETE_LOCATION", PantryEvent.ACTION_DELETE_LOCATION);
        Assert.assertEquals("UPSERT_SHOPPING_ITEM", PantryEvent.ACTION_UPSERT_SHOPPING_ITEM);
        Assert.assertEquals("DELETE_SHOPPING_ITEM", PantryEvent.ACTION_DELETE_SHOPPING_ITEM);
    }

    @Test
    public void testWebDavSyncProviderInterface() {
        WebDavClient client = new WebDavClient("https://example.com/webdav", "user", "pass");
        WebDavSyncProvider.SyncScope scope = new WebDavSyncProvider.SyncScope(1, "pantries/pantry-1-sync/");
        WebDavSyncProvider provider = new WebDavSyncProvider(client, "dev_1", Collections.singletonList(scope));

        Assert.assertTrue(provider instanceof SyncProvider);
        Assert.assertEquals("webdav", provider.getId());
        Assert.assertTrue(provider.isAvailable().blockingGet());
        Assert.assertEquals(eu.frigo.dispensa.sync.webdav.worker.WebDavSyncWorker.class, provider.getWorkerClass());
    }
}
