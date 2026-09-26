package eu.frigo.dispensa.sync.webdav;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.google.gson.Gson;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.data.sync.OutboxRepositoryImpl;
import eu.frigo.dispensa.data.sync.SyncOutbox;
import eu.frigo.dispensa.sync.core.pairing.PairingPayload;
import eu.frigo.dispensa.sync.core.pairing.PairingPayloadCodecImpl;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.policy.SyncPolicy;
import eu.frigo.dispensa.sync.core.store.SyncCursorStore;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import eu.frigo.dispensa.sync.webdav.model.WebDavManifest;
import eu.frigo.dispensa.sync.webdav.model.WebDavSnapshot;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

@RunWith(RobolectricTestRunner.class)
public class PantrySharingSyncTest {

    private MockWebServer server;
    private AppDatabase dbOwner;
    private AppDatabase dbGuest;
    private final Map<String, byte[]> remoteStorage = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();

    @Before
    public void setup() throws IOException {
        server = new MockWebServer();
        server.setDispatcher(new WebDavDispatcher());
        server.start();

        Context context = ApplicationProvider.getApplicationContext();
        dbOwner = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dbGuest = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void teardown() throws IOException {
        if (server != null) {
            server.shutdown();
        }
        if (dbOwner != null) {
            dbOwner.close();
        }
        if (dbGuest != null) {
            dbGuest.close();
        }
    }

    private final java.util.Set<String> remoteFolders = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    private class WebDavDispatcher extends Dispatcher {
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
    }

    private static class InMemoryCursorStore implements SyncCursorStore {
        private long lastSync = 0L;

        @Override
        public long getLastSyncTimestamp() {
            return lastSync;
        }

        @Override
        public void updateLastSyncTimestamp(long timestamp) {
            this.lastSync = timestamp;
        }

        @Override
        public void clear() {
            this.lastSync = 0L;
        }
    }

    @Test
    public void testPairingPayloadEncodingAndDecoding() throws Exception {
        WebDavConfig config = new WebDavConfig(
                server.url("/").toString(),
                "testUser",
                "testPass",
                "/remote/dav",
                "pantrySecretKey123",
                "Dispensa Famiglia",
                "device_owner_abc",
                "remote_uuid_12345",
                false
        );

        PairingPayload payload = WebDavPairingHandler.createPayload("Pixel 8 Pro", config);
        Assert.assertEquals("webdav", payload.providerId);
        Assert.assertEquals("Pixel 8 Pro", payload.deviceName);
        Assert.assertEquals("remote_uuid_12345", payload.data.get("remoteId"));
        Assert.assertEquals("device_owner_abc", payload.data.get("ownerDeviceId"));
        Assert.assertEquals("Dispensa Famiglia", payload.data.get("pantryName"));

        PairingPayloadCodecImpl codec = new PairingPayloadCodecImpl("CODE12");
        String wireData = codec.encode(payload);
        Assert.assertNotNull(wireData);
        Assert.assertTrue(wireData.startsWith("v1|"));

        PairingPayload decoded = codec.decode(wireData);
        Assert.assertNotNull(decoded);
        WebDavConfig parsedConfig = WebDavPairingHandler.parsePayload(decoded);

        Assert.assertEquals(config.url, parsedConfig.url);
        Assert.assertEquals(config.username, parsedConfig.username);
        Assert.assertEquals(config.password, parsedConfig.password);
        Assert.assertEquals(config.pantryKey, parsedConfig.pantryKey);
        Assert.assertEquals(config.pantryName, parsedConfig.pantryName);
        Assert.assertEquals(config.ownerDeviceId, parsedConfig.ownerDeviceId);
        Assert.assertEquals(config.remoteId, parsedConfig.remoteId);
        Assert.assertEquals(config.isShared, parsedConfig.isShared);
    }

    private static final SyncPolicy FORCE_POLICY = new SyncPolicy() {
        @Override
        public boolean canSyncNow() { return true; }
        @Override
        public long getRetryIntervalMillis() { return 0; }
    };

    @Test
    public void testEndToEndProductSharingAddUpdateDelete() {
        Context context = ApplicationProvider.getApplicationContext();
        String serverUrl = server.url("/").toString();

        String ownerDeviceId = "owner_device_001";
        String guestDeviceId = "guest_device_002";

        int ownerPantryId = 1;
        int guestPantryId = 10; // Local SQLite auto-increment ID on Guest
        String remoteId = "pantry_uuid_shared_999";

        String pantryPath = eu.frigo.dispensa.sync.core.engine.SyncManager.getSyncPath(remoteId);

        // 1. Setup Owner Dispensa in Owner DB
        Dispensa ownerDisp = new Dispensa("Dispensa Condivisa", true);
        ownerDisp.id = ownerPantryId;
        ownerDisp.deviceOwnerId = ownerDeviceId;
        ownerDisp.remoteId = remoteId;
        dbOwner.dispensaDao().insert(ownerDisp);

        // 2. Setup Guest Dispensa & Joined Config in Guest DB
        Dispensa guestDisp = new Dispensa("Dispensa Condivisa", false);
        guestDisp.id = guestPantryId;
        guestDisp.deviceOwnerId = ownerDeviceId;
        guestDisp.remoteId = remoteId;
        dbGuest.dispensaDao().insert(guestDisp);

        JoinedPantryConfig guestConfig = new JoinedPantryConfig(
                guestPantryId, serverUrl, "user", "pass", "/", false, "key"
        );
        dbGuest.joinedPantryConfigDao().insert(guestConfig);

        WebDavClient clientOwner = new WebDavClient(serverUrl, "user", "pass");
        WebDavClient clientGuest = new WebDavClient(serverUrl, "user", "pass");

        SyncCursorStore cursorStoreOwner = new InMemoryCursorStore();
        SyncCursorStore cursorStoreGuest = new InMemoryCursorStore();

        WebDavSyncEngine syncEngineOwner = new WebDavSyncEngine(
                clientOwner, cursorStoreOwner, new OutboxRepositoryImpl(dbOwner),
                ownerDeviceId, pantryPath, ownerPantryId, dbOwner, context
        );

        WebDavSyncEngine syncEngineGuest = new WebDavSyncEngine(
                clientGuest, cursorStoreGuest, new OutboxRepositoryImpl(dbGuest),
                guestDeviceId, pantryPath, guestPantryId, dbGuest, context
        );

        // Prepare remote pantry on server
        syncEngineOwner.initializeRemoteStructure().blockingAwait();

        // ==========================================
        // PHASE 1: OWNER ADDS A PRODUCT & SYNCS
        // ==========================================
        Product product = new Product(
                "8001122334455",
                4,
                System.currentTimeMillis() + 864000000L,
                "Biscotti Integrali",
                null,
                "PANTRY",
                0L,
                -1
        );
        product.dispensaId = ownerPantryId;
        product.lastModified = System.currentTimeMillis();
        long pId = dbOwner.productDao().insert(product);
        product.setId((int) pId);

        // Record outbox event for owner
        SyncOutbox outboxAdd = new SyncOutbox();
        outboxAdd.syncId = "evt-add-001";
        outboxAdd.dispensaId = ownerPantryId;
        outboxAdd.dataType = "UPSERT_PRODUCT";
        outboxAdd.payload = gson.toJson(product);
        outboxAdd.timestamp = product.lastModified;
        dbOwner.syncOutboxDao().insert(outboxAdd);

        // Owner performs sync (PUSH)
        syncEngineOwner.performSync(FORCE_POLICY).blockingAwait();

        // Verify outbox on owner is marked synced
        List<SyncOutbox> pendingOwner = dbOwner.syncOutboxDao().getPendingChangesSync(ownerPantryId);
        Assert.assertEquals(0, pendingOwner.size());

        // Verify remote manifest contains event file or snapshot
        byte[] manifestBytes = remoteStorage.get(pantryPath + "manifest.json");
        Assert.assertNotNull(manifestBytes);
        WebDavManifest remoteManifest = gson.fromJson(new String(manifestBytes), WebDavManifest.class);
        Assert.assertNotNull(remoteManifest);

        // ==========================================
        // PHASE 2: GUEST PULLS & RECEIVES THE PRODUCT
        // ==========================================
        List<Product> guestProductsBefore = dbGuest.productDao().getAllProductsListStatic(guestPantryId);
        Assert.assertEquals(0, guestProductsBefore.size());

        // Guest performs sync (PULL)
        syncEngineGuest.performSync(FORCE_POLICY).blockingAwait();

        // Guest DB should now have the product in their pantry
        List<Product> guestProductsAfter = dbGuest.productDao().getAllProductsListStatic(guestPantryId);
        Assert.assertEquals(1, guestProductsAfter.size());
        Product receivedByGuest = guestProductsAfter.get(0);
        Assert.assertEquals("8001122334455", receivedByGuest.getBarcode());
        Assert.assertEquals("Biscotti Integrali", receivedByGuest.getProductName());
        Assert.assertEquals(4, receivedByGuest.getQuantity());
        Assert.assertEquals(guestPantryId, receivedByGuest.dispensaId);

        // ==========================================
        // PHASE 3: GUEST CONSUMES/UPDATES PRODUCT & OWNER PULLS
        // ==========================================
        receivedByGuest.setQuantity(1); // 3 consumed, 1 left
        receivedByGuest.lastModified = System.currentTimeMillis() + 1000L;
        dbGuest.productDao().update(receivedByGuest);

        // Guest records outbox event
        SyncOutbox outboxUpdate = new SyncOutbox();
        outboxUpdate.syncId = "evt-upd-002";
        outboxUpdate.dispensaId = guestPantryId;
        outboxUpdate.dataType = "UPSERT_PRODUCT";
        outboxUpdate.payload = gson.toJson(receivedByGuest);
        outboxUpdate.timestamp = receivedByGuest.lastModified;
        dbGuest.syncOutboxDao().insert(outboxUpdate);

        // Guest performs sync (PUSH)
        syncEngineGuest.performSync(FORCE_POLICY).blockingAwait();

        // Owner performs sync (PULL)
        syncEngineOwner.performSync(FORCE_POLICY).blockingAwait();

        Product ownerProductAfterUpdate = dbOwner.productDao().getProductByIdSync((int) pId);
        Assert.assertNotNull(ownerProductAfterUpdate);
        Assert.assertEquals(1, ownerProductAfterUpdate.getQuantity());

        // ==========================================
        // PHASE 4: OWNER DELETES PRODUCT & GUEST PULLS
        // ==========================================
        dbOwner.productDao().delete(ownerProductAfterUpdate);
        SyncOutbox outboxDelete = new SyncOutbox();
        outboxDelete.syncId = "evt-del-003";
        outboxDelete.dispensaId = ownerPantryId;
        outboxDelete.dataType = "DELETE_PRODUCT";
        outboxDelete.payload = gson.toJson(ownerProductAfterUpdate);
        outboxDelete.timestamp = System.currentTimeMillis() + 2000L;
        dbOwner.syncOutboxDao().insert(outboxDelete);

        // Owner pushes delete
        syncEngineOwner.performSync(FORCE_POLICY).blockingAwait();

        // Guest pulls delete
        syncEngineGuest.performSync(FORCE_POLICY).blockingAwait();

        List<Product> guestProductsFinal = dbGuest.productDao().getAllProductsListStatic(guestPantryId);
        Assert.assertEquals(0, guestProductsFinal.size());
    }

    @Test
    public void testOwnerSilentlyRecreatesRemoteStructureAndGuestFailsWhenMissing() throws Exception {
        // Ensure remote storage is completely empty (no folders, no manifest, no files)
        remoteStorage.clear();

        Context context = ApplicationProvider.getApplicationContext();
        String remoteId = "uuid-init-test";
        String ownerDeviceId = "device-owner-1";
        String guestDeviceId = "device-guest-2";

        // 1. Setup Guest Dispensa (not owner)
        Dispensa guestDisp = new Dispensa("Dispensa Guest", false);
        guestDisp.remoteId = remoteId;
        guestDisp.deviceOwnerId = ownerDeviceId;
        int guestPantryId = (int) dbGuest.dispensaDao().insert(guestDisp);

        String pantryPath = "pantries/pantry-" + remoteId + "-sync/";
        WebDavClient client = new WebDavClient(server.url("/").toString(), "user", "pass");

        WebDavSyncEngine guestSyncEngine = new WebDavSyncEngine(
                client,
                new InMemoryCursorStore(),
                new OutboxRepositoryImpl(dbGuest),
                guestDeviceId,
                pantryPath,
                guestPantryId,
                dbGuest,
                context
        );

        // Guest sync must fail when remote structure does not exist
        try {
            guestSyncEngine.performSync(FORCE_POLICY).blockingAwait();
            Assert.fail("Expected RemoteStructureNotFoundException for guest when remote structure does not exist");
        } catch (Exception e) {
            Assert.assertTrue(e.getCause() instanceof eu.frigo.dispensa.sync.core.engine.RemoteStructureNotFoundException || e instanceof eu.frigo.dispensa.sync.core.engine.RemoteStructureNotFoundException);
        }

        // 2. Setup Owner Dispensa
        Dispensa ownerDisp = new Dispensa("Dispensa Inizializzata", true);
        ownerDisp.remoteId = remoteId;
        ownerDisp.deviceOwnerId = ownerDeviceId;
        int ownerPantryId = (int) dbOwner.dispensaDao().insert(ownerDisp);

        // Add a local product in the owner pantry
        Product product = new Product(
                "800000000001",
                5,
                1750000000000L,
                "Prodotto Esistente",
                null,
                "PANTRY",
                0L,
                -1
        );
        product.dispensaId = ownerPantryId;
        product.lastModified = System.currentTimeMillis();
        dbOwner.productDao().insert(product);

        WebDavSyncEngine ownerSyncEngine = new WebDavSyncEngine(
                client,
                new InMemoryCursorStore(),
                new OutboxRepositoryImpl(dbOwner),
                ownerDeviceId,
                pantryPath,
                ownerPantryId,
                dbOwner,
                context
        );

        // 3. Owner performs sync: should silently recreate all folders and manifest and create initial snapshot
        ownerSyncEngine.performSync(FORCE_POLICY).blockingAwait();

        // 4. Verify manifest.json was created
        byte[] manifestData = remoteStorage.get(pantryPath + "manifest.json");
        Assert.assertNotNull("Manifest should be created on server", manifestData);
        WebDavManifest manifest = gson.fromJson(new String(manifestData), WebDavManifest.class);
        Assert.assertEquals(SyncManager.CURRENT_SYNC_VERSION, manifest.version);
        Assert.assertEquals("Dispensa Inizializzata", manifest.pantryName);
        Assert.assertEquals(ownerDeviceId, manifest.createdByDevice);
        Assert.assertNotNull("Initial snapshot should be created during compaction", manifest.latestSnapshotId);

        // 5. Verify device registration file was created
        byte[] deviceData = remoteStorage.get(pantryPath + "devices/" + ownerDeviceId + ".json");
        Assert.assertNotNull("Device registration file should exist", deviceData);

        // 6. Verify snapshot was saved containing local products
        byte[] snapshotData = remoteStorage.get(pantryPath + "snapshots/" + manifest.latestSnapshotId);
        Assert.assertNotNull("Snapshot file should exist in storage", snapshotData);
        WebDavSnapshot snapshot = gson.fromJson(new String(snapshotData), WebDavSnapshot.class);
        Assert.assertNotNull(snapshot.products);
        Assert.assertEquals(1, snapshot.products.size());
        Assert.assertEquals("Prodotto Esistente", snapshot.products.get(0).getProductName());

        // 7. Now Guest can sync successfully
        guestSyncEngine.performSync(FORCE_POLICY).blockingAwait();
        List<Product> guestProducts = dbGuest.productDao().getAllProductsListStatic(guestPantryId);
        Assert.assertEquals(1, guestProducts.size());
        Assert.assertEquals("Prodotto Esistente", guestProducts.get(0).getProductName());
    }
}


