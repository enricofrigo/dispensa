package eu.frigo.dispensa.sync.core.engine;

import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import eu.frigo.dispensa.sync.core.model.PantryManifest;
import eu.frigo.dispensa.sync.core.policy.SyncPolicy;
import eu.frigo.dispensa.sync.core.store.InMemorySharedFolderStore;
import eu.frigo.dispensa.sync.core.store.SyncCursorStore;

@RunWith(RobolectricTestRunner.class)
public class FolderSyncEngineTest {

    private InMemorySharedFolderStore sharedStore;
    private MockPantryDataBridge bridgeDeviceA;
    private MockPantryDataBridge bridgeDeviceB;
    private InMemoryCursorStore cursorStoreA;
    private InMemoryCursorStore cursorStoreB;
    private final Gson gson = new Gson();

    private static final SyncPolicy FORCE_POLICY = new SyncPolicy() {
        @Override public boolean canSyncNow() { return true; }
        @Override public long getRetryIntervalMillis() { return 0; }
    };

    @Before
    public void setup() {
        Map<String, byte[]> sharedStorage = new ConcurrentHashMap<>();
        HashSet<String> sharedFolders = new HashSet<>();
        sharedStore = new InMemorySharedFolderStore("test_shared", sharedStorage, sharedFolders);

        bridgeDeviceA = new MockPantryDataBridge("Dispensa Condivisa");
        bridgeDeviceB = new MockPantryDataBridge("Dispensa Condivisa");

        cursorStoreA = new InMemoryCursorStore();
        cursorStoreB = new InMemoryCursorStore();
    }

    private static class InMemoryCursorStore implements SyncCursorStore {
        private long lastSync = 0L;
        @Override public long getLastSyncTimestamp() { return lastSync; }
        @Override public void updateLastSyncTimestamp(long timestamp) { this.lastSync = timestamp; }
        @Override public void clear() { this.lastSync = 0L; }
    }

    private static class MockPantryDataBridge implements PantryDataBridge {
        final String pantryName;
        boolean isOwner = true;
        final List<PantryOutboxItem> pendingOutbox = new ArrayList<>();
        final List<String> markedSyncedIds = new ArrayList<>();
        final Map<String, String> appliedEvents = new HashMap<>();
        String latestSnapshotJson = "{\"products\":[]}";

        MockPantryDataBridge(String pantryName) {
            this.pantryName = pantryName;
        }

        @Override
        public List<PantryOutboxItem> getPendingOutboxEvents(int dispensaId) {
            return new ArrayList<>(pendingOutbox);
        }

        @Override
        public void markEventsAsSynced(List<String> syncIds) {
            markedSyncedIds.addAll(syncIds);
            pendingOutbox.removeIf(item -> syncIds.contains(item.syncId));
        }

        @Override
        public String createSnapshotJson(int dispensaId) {
            return latestSnapshotJson;
        }

        @Override
        public void applySnapshotJson(String snapshotJson, int dispensaId) {
            this.latestSnapshotJson = snapshotJson;
        }

        @Override
        public void applyEvent(String action, String payloadJson, long eventTimestamp, int dispensaId) {
            appliedEvents.put(action, payloadJson);
        }

        @Override
        public String getPantryName(int dispensaId) {
            return pantryName;
        }

        @Override
        public boolean isPantryOwner(int dispensaId, String currentDeviceId) {
            return isOwner;
        }
    }

    @Test
    public void testOwnerSilentlyRecreatesRemoteStructureWhenMissingOrDeleted() {
        String pantryPath = "pantry-owner-silent-recreate/";
        bridgeDeviceA.isOwner = true;
        bridgeDeviceA.latestSnapshotJson = "{\"products\":[{\"name\":\"Caffè\"}]}";

        FolderSyncEngine ownerEngine = new FolderSyncEngine(
                sharedStore, bridgeDeviceA, cursorStoreA, "device-A", "Pixel 8", pantryPath, 1
        );

        // Before sync, storage is completely empty
        Assert.assertFalse(sharedStore.exists(pantryPath).blockingGet());

        // Sync by owner: should silently recreate all folders and manifest
        ownerEngine.performSync(FORCE_POLICY).blockingAwait();

        // Verify remote structure was recreated silently
        Assert.assertTrue(sharedStore.exists(pantryPath).blockingGet());
        Assert.assertTrue(sharedStore.exists(pantryPath + "devices/").blockingGet());
        Assert.assertTrue(sharedStore.exists(pantryPath + "events/").blockingGet());
        Assert.assertTrue(sharedStore.exists(pantryPath + "snapshots/").blockingGet());
        Assert.assertTrue(sharedStore.exists(pantryPath + "manifest.json").blockingGet());
        Assert.assertTrue(sharedStore.exists(pantryPath + "devices/device-A.json").blockingGet());

        // Delete snapshots folder, owner syncs again: should recreate it silently
        sharedStore.deleteFolder(pantryPath + "snapshots/").blockingAwait();
        Assert.assertFalse(sharedStore.exists(pantryPath + "snapshots/").blockingGet());

        ownerEngine.performSync(FORCE_POLICY).blockingAwait();
        Assert.assertTrue(sharedStore.exists(pantryPath + "snapshots/").blockingGet());
    }

    @Test
    public void testGuestThrowsWhenStructureMissingOrDeleted() {
        String pantryPath = "pantry-guest-fail-on-missing/";
        bridgeDeviceB.isOwner = false;

        FolderSyncEngine guestEngine = new FolderSyncEngine(
                sharedStore, bridgeDeviceB, cursorStoreB, "device-B", "Galaxy S24", pantryPath, 2
        );

        // 1. Completely missing structure -> guest fails
        try {
            guestEngine.performSync(FORCE_POLICY).blockingAwait();
            Assert.fail("Expected RemoteStructureNotFoundException for guest on missing structure");
        } catch (Exception e) {
            Assert.assertTrue(e.getCause() instanceof RemoteStructureNotFoundException || e instanceof RemoteStructureNotFoundException);
        }

        // 2. Initialize by owner first
        bridgeDeviceA.isOwner = true;
        FolderSyncEngine ownerEngine = new FolderSyncEngine(
                sharedStore, bridgeDeviceA, cursorStoreA, "device-A", "Pixel 8", pantryPath, 1
        );
        ownerEngine.initializeRemoteStructure().blockingAwait();

        // Now guest can sync successfully
        guestEngine.performSync(FORCE_POLICY).blockingAwait();

        // 3. Delete events folder -> guest must fail with exception
        sharedStore.deleteFolder(pantryPath + "events/").blockingAwait();
        try {
            guestEngine.performSync(FORCE_POLICY).blockingAwait();
            Assert.fail("Expected failure for guest when events/ is deleted");
        } catch (Exception e) {
            Assert.assertTrue(e.getCause() instanceof RemoteStructureNotFoundException || e instanceof RemoteStructureNotFoundException);
        }

        // 4. Delete manifest.json -> guest must fail
        ownerEngine.initializeRemoteStructure().blockingAwait();
        sharedStore.deleteFile(pantryPath + "manifest.json").blockingAwait();
        try {
            guestEngine.performSync(FORCE_POLICY).blockingAwait();
            Assert.fail("Expected failure for guest when manifest.json is deleted");
        } catch (Exception e) {
            Assert.assertTrue(e.getCause() instanceof RemoteStructureNotFoundException || e instanceof RemoteStructureNotFoundException);
        }
    }

    @Test
    public void testFolderSyncEngineEndToEndPushAndPull() {
        String pantryPath = "pantry-test-1-sync/";
        FolderSyncEngine engineA = new FolderSyncEngine(
                sharedStore, bridgeDeviceA, cursorStoreA, "device-A", "Pixel 8", pantryPath, 1
        );
        FolderSyncEngine engineB = new FolderSyncEngine(
                sharedStore, bridgeDeviceB, cursorStoreB, "device-B", "Galaxy S24", pantryPath, 2
        );

        // Explicitly initialize pantry structure
        engineA.initializeRemoteStructure().blockingAwait();

        // 1. Initial Sync by Device A (Creates initial snapshot)
        bridgeDeviceA.latestSnapshotJson = "{\"products\":[{\"name\":\"Biscotti\"}]}";
        engineA.performSync(FORCE_POLICY).blockingAwait();

        // Verify initial snapshot created on server
        byte[] manifestData1 = sharedStore.read(pantryPath + "manifest.json").blockingGet();
        PantryManifest manifest1 = gson.fromJson(new String(manifestData1, StandardCharsets.UTF_8), PantryManifest.class);
        Assert.assertNotNull(manifest1.latestSnapshotId);

        // 2. Device B joins/pulls initial state (applies snapshot)
        engineB.performSync(FORCE_POLICY).blockingAwait();
        Assert.assertEquals("{\"products\":[{\"name\":\"Biscotti\"}]}", bridgeDeviceB.latestSnapshotJson);

        // 3. Device A adds an incremental event (e.g., adds Pasta)
        bridgeDeviceA.pendingOutbox.add(new PantryOutboxItem(
                "evt-100", "UPSERT_PRODUCT", "{\"barcode\":\"12345\",\"name\":\"Pasta\"}", System.currentTimeMillis() + 1000L
        ));
        engineA.performSync(FORCE_POLICY).blockingAwait();
        Assert.assertTrue(bridgeDeviceA.markedSyncedIds.contains("evt-100"));

        // 4. Device B syncs and receives the incremental event
        engineB.performSync(FORCE_POLICY).blockingAwait();
        Assert.assertTrue(bridgeDeviceB.appliedEvents.containsKey("UPSERT_PRODUCT"));
        Assert.assertEquals("{\"barcode\":\"12345\",\"name\":\"Pasta\"}", bridgeDeviceB.appliedEvents.get("UPSERT_PRODUCT"));
    }

    @Test
    public void testCompactionWhenActiveEventsThresholdReached() {
        String pantryPath = "pantry-test-compaction/";
        FolderSyncEngine engine = new FolderSyncEngine(
                sharedStore, bridgeDeviceA, cursorStoreA, "device-A", "Pixel 8", pantryPath, 1
        );

        engine.initializeRemoteStructure().blockingAwait();

        // Queue 50 events
        for (int i = 0; i < 50; i++) {
            bridgeDeviceA.pendingOutbox.add(new PantryOutboxItem(
                    "evt-" + i, "UPSERT_PRODUCT", "{\"id\":" + i + "}", 1000L + i
            ));
        }

        engine.performSync(FORCE_POLICY).blockingAwait();

        // Verify manifest snapshot was created and activeEventFiles cleared
        byte[] manifestData = sharedStore.read(pantryPath + "manifest.json").blockingGet();
        PantryManifest manifest = gson.fromJson(new String(manifestData, StandardCharsets.UTF_8), PantryManifest.class);
        Assert.assertNotNull(manifest.latestSnapshotId);
        Assert.assertEquals(0, manifest.activeEventFiles.size());
        Assert.assertTrue(sharedStore.exists(pantryPath + "snapshots/" + manifest.latestSnapshotId).blockingGet());
    }
}

