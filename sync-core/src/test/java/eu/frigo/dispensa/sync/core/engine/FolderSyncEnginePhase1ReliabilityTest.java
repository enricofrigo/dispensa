package eu.frigo.dispensa.sync.core.engine;

import androidx.annotation.Nullable;
import com.google.gson.Gson;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import eu.frigo.dispensa.sync.core.event.SyncBus;
import eu.frigo.dispensa.sync.core.event.SyncEvent;
import eu.frigo.dispensa.sync.core.model.PantryEvent;
import eu.frigo.dispensa.sync.core.model.PantryManifest;
import eu.frigo.dispensa.sync.core.policy.SyncPolicy;
import eu.frigo.dispensa.sync.core.store.InMemorySharedFolderStore;
import eu.frigo.dispensa.sync.core.store.SharedFolderStore;
import eu.frigo.dispensa.sync.core.store.SyncCursorStore;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * Fase 1 Reliability & Big Bang V3 Migration Test Suite.
 */
@RunWith(RobolectricTestRunner.class)
public class FolderSyncEnginePhase1ReliabilityTest {

    private Map<String, byte[]> rawStorage;
    private Set<String> rawFolders;
    private InMemorySharedFolderStore baseStore;
    private final Gson gson = new Gson();

    private static final SyncPolicy FORCE_POLICY = new SyncPolicy() {
        @Override public boolean canSyncNow() { return true; }
        @Override public long getRetryIntervalMillis() { return 0; }
    };

    private static class SimpleCursorStore implements SyncCursorStore {
        private long lastSync = 0L;
        @Override public long getLastSyncTimestamp() { return lastSync; }
        @Override public void updateLastSyncTimestamp(long timestamp) { this.lastSync = timestamp; }
        @Override public void clear() { this.lastSync = 0L; }
    }

    private static class TestBridge implements PantryDataBridge {
        boolean isOwner;
        final List<PantryOutboxItem> outbox = new ArrayList<>();
        final List<String> appliedPayloads = new ArrayList<>();
        String snapshotJson = "{\"products\":[{\"name\":\"Initial\"}]}";

        TestBridge(boolean isOwner) {
            this.isOwner = isOwner;
        }

        @Override
        public List<PantryOutboxItem> getPendingOutboxEvents(int dispensaId) {
            return new ArrayList<>(outbox);
        }

        @Override
        public void markEventsAsSynced(List<String> syncIds) {
            outbox.removeIf(i -> syncIds.contains(i.syncId));
        }

        @Override
        public String createSnapshotJson(int dispensaId) {
            return snapshotJson;
        }

        @Override
        public void applySnapshotJson(String json, int dispensaId) {
            this.snapshotJson = json;
        }

        @Override
        public void applyEvent(String action, String payloadJson, long eventTimestamp, int dispensaId) {
            appliedPayloads.add(action + ":" + payloadJson);
        }

        @Override
        public String getPantryName(int dispensaId) {
            return "Dispensa Test";
        }

        @Override
        public boolean isPantryOwner(int dispensaId, String currentDeviceId) {
            return isOwner;
        }
    }

    @Before
    public void setUp() {
        rawStorage = new ConcurrentHashMap<>();
        rawFolders = new HashSet<>();
        baseStore = new InMemorySharedFolderStore("test_store", rawStorage, rawFolders);
    }

    @Test
    public void testTransientNetworkFailureOnManifestReadNeverOverwritesRemoteManifest() throws Exception {
        String pantryPath = "pantry-net-fail/";
        TestBridge ownerBridge = new TestBridge(true);
        SimpleCursorStore ownerCursor = new SimpleCursorStore();

        AtomicBoolean failManifestRead = new AtomicBoolean(false);
        SharedFolderStore faultInjectableStore = new SharedFolderStore() {
            @Override public String getProviderId() { return baseStore.getProviderId(); }
            @Override public Single<Boolean> isAvailable() { return baseStore.isAvailable(); }
            @Override public Completable ensureFolder(String r) { return baseStore.ensureFolder(r); }
            @Override public Completable writeAtomic(String r, byte[] d, @Nullable String e) { return baseStore.writeAtomic(r, d, e); }
            @Override public Single<byte[]> read(String r) {
                if (failManifestRead.get() && r.endsWith("manifest.json")) {
                    return Single.error(new IOException("Simulated network timeout reading manifest"));
                }
                return baseStore.read(r);
            }
            @Override public Single<FileContent> readWithMeta(String r) {
                if (failManifestRead.get() && r.endsWith("manifest.json")) {
                    return Single.error(new IOException("Simulated network timeout reading manifest"));
                }
                return baseStore.readWithMeta(r);
            }
            @Override public Single<Boolean> exists(String r) { return baseStore.exists(r); }
            @Override public Single<List<String>> listFiles(String r) { return baseStore.listFiles(r); }
            @Override public Completable deleteFile(String r) { return baseStore.deleteFile(r); }
            @Override public Completable deleteFolder(String r) { return baseStore.deleteFolder(r); }
        };

        FolderSyncEngine ownerEngine = new FolderSyncEngine(
                faultInjectableStore, ownerBridge, ownerCursor, "owner-1", "Owner", pantryPath, 1
        );
        ownerEngine.initializeRemoteStructure().blockingAwait();
        // Initial sync creates initial snapshot
        ownerEngine.performSync(FORCE_POLICY).blockingAwait();
        // Second sync pushes an incremental event into activeEventFiles
        ownerBridge.outbox.add(new PantryOutboxItem("e1", "UPSERT_PRODUCT", "{\"name\":\"Latte\"}", 1000L));
        ownerEngine.performSync(FORCE_POLICY).blockingAwait();

        byte[] manifestBeforeFailure = rawStorage.get(pantryPath + "manifest.json").clone();
        PantryManifest parsedBefore = gson.fromJson(new String(manifestBeforeFailure, StandardCharsets.UTF_8), PantryManifest.class);
        Assert.assertEquals(1, parsedBefore.activeEventFiles.size());

        // Now queue another event on a second device, but inject network failure on manifest read
        TestBridge guestBridge = new TestBridge(false);
        guestBridge.outbox.add(new PantryOutboxItem("e2", "UPSERT_PRODUCT", "{\"name\":\"Pane\"}", 2000L));
        FolderSyncEngine guestEngine = new FolderSyncEngine(
                faultInjectableStore, guestBridge, new SimpleCursorStore(), "guest-2", "Guest", pantryPath, 1
        );

        failManifestRead.set(true);
        try {
            guestEngine.performSync(FORCE_POLICY).blockingAwait();
            Assert.fail("Sync must fail when manifest read fails with IOException");
        } catch (Exception expected) {
            // Expected failure
        }

        // Verify remote manifest was NOT overwritten or reset!
        byte[] manifestAfterFailure = rawStorage.get(pantryPath + "manifest.json");
        Assert.assertArrayEquals(manifestBeforeFailure, manifestAfterFailure);
        PantryManifest parsedAfter = gson.fromJson(new String(manifestAfterFailure, StandardCharsets.UTF_8), PantryManifest.class);
        Assert.assertEquals("owner-1", parsedAfter.createdByDevice);
        Assert.assertEquals(1, parsedAfter.activeEventFiles.size());
    }

    @Test
    public void testOptimisticConcurrencyETagRetryMergesConcurrentManifestUpdates() {
        String pantryPath = "pantry-etag-concurrency/";
        TestBridge bridgeA = new TestBridge(true);
        TestBridge bridgeB = new TestBridge(false);

        AtomicInteger manifestPutAttempts = new AtomicInteger(0);
        SharedFolderStore interleavingStore = new SharedFolderStore() {
            @Override public String getProviderId() { return baseStore.getProviderId(); }
            @Override public Single<Boolean> isAvailable() { return baseStore.isAvailable(); }
            @Override public Completable ensureFolder(String r) { return baseStore.ensureFolder(r); }
            @Override
            public Completable writeAtomic(String r, byte[] d, @Nullable String expectedEtag) {
                if (r.endsWith("manifest.json") && expectedEtag != null && manifestPutAttempts.incrementAndGet() == 2) {
                    // Simulate another device modifying manifest right before this writeAtomic executes!
                    return Completable.fromAction(() -> {
                        FileContent current = baseStore.readWithMeta(r).blockingGet();
                        PantryManifest concurrentManifest = gson.fromJson(new String(current.data, StandardCharsets.UTF_8), PantryManifest.class);
                        concurrentManifest.activeEventFiles.add("events/ev_concurrent_other_999_sync999.json");
                        byte[] concurrentBytes = gson.toJson(concurrentManifest).getBytes(StandardCharsets.UTF_8);
                        baseStore.writeAtomic(r, concurrentBytes, current.etag).blockingAwait();
                        // Now execute the original writeAtomic with the stale expectedEtag -> triggers 412!
                        baseStore.writeAtomic(r, d, expectedEtag).blockingAwait();
                    });
                }
                return baseStore.writeAtomic(r, d, expectedEtag);
            }
            @Override public Single<byte[]> read(String r) { return baseStore.read(r); }
            @Override public Single<FileContent> readWithMeta(String r) { return baseStore.readWithMeta(r); }
            @Override public Single<Boolean> exists(String r) { return baseStore.exists(r); }
            @Override public Single<List<String>> listFiles(String r) { return baseStore.listFiles(r); }
            @Override public Completable deleteFile(String r) { return baseStore.deleteFile(r); }
            @Override public Completable deleteFolder(String r) { return baseStore.deleteFolder(r); }
        };

        // Pre-populate the concurrent event file so subsequent pulls succeed
        PantryEvent concurrentEv = new PantryEvent("sync999", "other", 999L, "UPSERT_PRODUCT", Map.of("name", "Burro"));
        baseStore.writeAtomic(
                pantryPath + "events/ev_concurrent_other_999_sync999.json",
                gson.toJson(concurrentEv).getBytes(StandardCharsets.UTF_8),
                null
        ).blockingAwait();

        FolderSyncEngine engineA = new FolderSyncEngine(
                interleavingStore, bridgeA, new SimpleCursorStore(), "dev-A", "Device A", pantryPath, 1
        );
        engineA.initializeRemoteStructure().blockingAwait();
        // Attempt 1 of manifest write happens during initial compaction
        engineA.performSync(FORCE_POLICY).blockingAwait();

        // Now queue an event on Device A -> Attempt 2 of manifest write will collide with concurrent update, retry, and merge both!
        bridgeA.outbox.add(new PantryOutboxItem("syncA1", "UPSERT_PRODUCT", "{\"name\":\"Uova\"}", 2000L));
        engineA.performSync(FORCE_POLICY).blockingAwait();

        byte[] finalManifestBytes = rawStorage.get(pantryPath + "manifest.json");
        PantryManifest finalManifest = gson.fromJson(new String(finalManifestBytes, StandardCharsets.UTF_8), PantryManifest.class);
        Assert.assertEquals("Both concurrent event and Device A event must be preserved", 2, finalManifest.activeEventFiles.size());
        Assert.assertTrue(finalManifest.activeEventFiles.contains("events/ev_concurrent_other_999_sync999.json"));
    }

    @Test
    public void testOfflineDelayedEventsWithOlderTimestampAreApplied() {
        String pantryPath = "pantry-offline-delayed/";
        TestBridge bridgeA = new TestBridge(true);
        TestBridge bridgeB = new TestBridge(false);
        SimpleCursorStore cursorA = new SimpleCursorStore();
        SimpleCursorStore cursorB = new SimpleCursorStore();

        FolderSyncEngine engineA = new FolderSyncEngine(baseStore, bridgeA, cursorA, "dev-A", "A", pantryPath, 1);
        FolderSyncEngine engineB = new FolderSyncEngine(baseStore, bridgeB, cursorB, "dev-B", "B", pantryPath, 1);

        engineA.initializeRemoteStructure().blockingAwait();
        engineA.performSync(FORCE_POLICY).blockingAwait();
        engineB.performSync(FORCE_POLICY).blockingAwait();

        // 1. Device B is offline at T=1000 and records an event in its local outbox
        bridgeB.outbox.add(new PantryOutboxItem("offline-evt-1", "UPSERT_PRODUCT", "{\"name\":\"OfflineMilk\"}", 1000L));

        // 2. Device A is online at T=5000 and pushes a newer event (advancing global timestamp to 5000)
        bridgeA.outbox.add(new PantryOutboxItem("online-evt-2", "UPSERT_PRODUCT", "{\"name\":\"OnlineBread\"}", 5000L));
        engineA.performSync(FORCE_POLICY).blockingAwait();
        Assert.assertTrue(cursorA.getLastSyncTimestamp() >= 5000L);

        // 3. Device B comes back online and syncs (pulls OnlineBread, pushes OfflineMilk with timestamp=1000)
        engineB.performSync(FORCE_POLICY).blockingAwait();
        Assert.assertEquals(1, bridgeB.appliedPayloads.size());
        Assert.assertTrue(bridgeB.appliedPayloads.get(0).contains("OnlineBread"));

        // 4. Device A syncs again: even though OfflineMilk has timestamp 1000 < cursorA (5000), Device A MUST apply it!
        engineA.performSync(FORCE_POLICY).blockingAwait();
        Assert.assertEquals("Device A must apply delayed offline event from Device B", 1, bridgeA.appliedPayloads.size());
        Assert.assertTrue(bridgeA.appliedPayloads.get(0).contains("OfflineMilk"));
    }

    @Test
    public void testSameMillisecondEventsDoNotCollideAndAllAreApplied() {
        String pantryPath = "pantry-same-ms/";
        TestBridge bridgeA = new TestBridge(true);
        TestBridge bridgeB = new TestBridge(false);

        FolderSyncEngine engineA = new FolderSyncEngine(baseStore, bridgeA, new SimpleCursorStore(), "dev-A", "A", pantryPath, 1);
        FolderSyncEngine engineB = new FolderSyncEngine(baseStore, bridgeB, new SimpleCursorStore(), "dev-B", "B", pantryPath, 1);

        engineA.initializeRemoteStructure().blockingAwait();
        engineA.performSync(FORCE_POLICY).blockingAwait();

        long sameTimestamp = 1700000000999L;
        for (int i = 1; i <= 5; i++) {
            bridgeA.outbox.add(new PantryOutboxItem(
                    "sync-id-" + i,
                    "DELETE_SHOPPING_ITEM",
                    "{\"name\":\"Item" + i + "\"}",
                    sameTimestamp
            ));
        }

        engineA.performSync(FORCE_POLICY).blockingAwait();
        engineB.performSync(FORCE_POLICY).blockingAwait();

        Assert.assertEquals("All 5 same-millisecond events must be applied without filename collision", 5, bridgeB.appliedPayloads.size());
    }

    @Test
    public void testBigBangMigrationV2ToV3OwnerOnlyAndLegacyCleanup() {
        String pantryPath = "pantry-bigbang-v2-v3/";
        TestBridge ownerBridge = new TestBridge(true);
        ownerBridge.snapshotJson = "{\"products\":[{\"name\":\"V3_Owner_Product\",\"categories\":[\"it:latticini\"]}]}";
        TestBridge guestBridge = new TestBridge(false);

        FolderSyncEngine ownerEngine = new FolderSyncEngine(baseStore, ownerBridge, new SimpleCursorStore(), "owner-dev", "Owner", pantryPath, 1);
        FolderSyncEngine guestEngine = new FolderSyncEngine(baseStore, guestBridge, new SimpleCursorStore(), "guest-dev", "Guest", pantryPath, 1);

        // Setup legacy V2 structure on remote storage
        ownerEngine.initializeRemoteStructure().blockingAwait();
        PantryManifest legacyV2Manifest = new PantryManifest();
        legacyV2Manifest.version = 2;
        legacyV2Manifest.createdByDevice = "owner-dev";
        legacyV2Manifest.pantryName = "Dispensa V2";
        legacyV2Manifest.latestSnapshotId = "snap_legacy_v2.json";
        legacyV2Manifest.activeEventFiles.add("events/ev_owner-dev_1000.json");

        baseStore.writeAtomic(pantryPath + "manifest.json", gson.toJson(legacyV2Manifest).getBytes(StandardCharsets.UTF_8), null).blockingAwait();
        baseStore.writeAtomic(pantryPath + "snapshots/snap_legacy_v2.json", "{\"products\":[]}".getBytes(StandardCharsets.UTF_8), null).blockingAwait();
        PantryEvent legacyEvent = new PantryEvent("legacy1", "owner-dev", 1000L, "UPSERT_PRODUCT", Map.of("name", "LegacyItem"));
        baseStore.writeAtomic(pantryPath + "events/ev_owner-dev_1000.json", gson.toJson(legacyEvent).getBytes(StandardCharsets.UTF_8), null).blockingAwait();

        // 1. Guest (non-owner) tries to sync first against V2 manifest -> must emit VersionMismatch and NOT migrate
        AtomicReference<SyncEvent.VersionMismatch> guestMismatch = new AtomicReference<>();
        Disposable sub = SyncBus.getInstance().observe()
                .filter(e -> e instanceof SyncEvent.VersionMismatch)
                .subscribe(e -> guestMismatch.set((SyncEvent.VersionMismatch) e));
        try {
            guestEngine.performSync(FORCE_POLICY).blockingAwait();
        } finally {
            sub.dispose();
        }
        Assert.assertNotNull("Guest must emit VersionMismatch when manifest is still V2", guestMismatch.get());
        Assert.assertEquals(2, guestMismatch.get().remoteVersion);
        Assert.assertTrue("Legacy snapshot must still exist before owner migrates", baseStore.exists(pantryPath + "snapshots/snap_legacy_v2.json").blockingGet());

        // 2. Owner syncs -> performs Big Bang migration to V3, wipes legacy events & snapshots, creates V3 snapshot
        ownerEngine.performSync(FORCE_POLICY).blockingAwait();

        PantryManifest migratedManifest = gson.fromJson(
                new String(rawStorage.get(pantryPath + "manifest.json"), StandardCharsets.UTF_8),
                PantryManifest.class
        );
        Assert.assertEquals(3, migratedManifest.version);
        Assert.assertEquals(0, migratedManifest.activeEventFiles.size());
        Assert.assertNotNull(migratedManifest.latestSnapshotId);
        Assert.assertNotEquals("snap_legacy_v2.json", migratedManifest.latestSnapshotId);

        // Verify legacy files were deleted from server ("cancella il pregresso")
        Assert.assertFalse("Old V2 event must be deleted", baseStore.exists(pantryPath + "events/ev_owner-dev_1000.json").blockingGet());
        Assert.assertFalse("Old V2 snapshot must be deleted", baseStore.exists(pantryPath + "snapshots/snap_legacy_v2.json").blockingGet());

        // 3. Guest syncs now that manifest is V3 -> succeeds and applies the new V3 snapshot!
        guestEngine.performSync(FORCE_POLICY).blockingAwait();
        Assert.assertEquals(ownerBridge.snapshotJson, guestBridge.snapshotJson);
    }
}
