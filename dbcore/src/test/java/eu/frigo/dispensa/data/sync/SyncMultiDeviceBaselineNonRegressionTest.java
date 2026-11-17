package eu.frigo.dispensa.data.sync;

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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItem;
import eu.frigo.dispensa.sync.core.engine.FolderSyncEngine;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.event.SyncBus;
import eu.frigo.dispensa.sync.core.event.SyncEvent;
import eu.frigo.dispensa.sync.core.model.PantryManifest;
import eu.frigo.dispensa.sync.core.policy.SyncPolicy;
import eu.frigo.dispensa.sync.core.store.InMemorySharedFolderStore;
import eu.frigo.dispensa.sync.core.store.SyncCursorStore;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * Fase 0 Baseline Non-Regression Test Suite for Multi-Device Sync:
 * Delete propagation, Compaction + New Device Join, and VersionMismatch handling.
 */
@RunWith(RobolectricTestRunner.class)
public class SyncMultiDeviceBaselineNonRegressionTest {

    private AppDatabase dbOwner;
    private AppDatabase dbGuest1;
    private AppDatabase dbGuest2;
    private InMemorySharedFolderStore sharedStore;
    private final Gson gson = new Gson();

    private static final SyncPolicy FORCE_POLICY = new SyncPolicy() {
        @Override public boolean canSyncNow() { return true; }
        @Override public long getRetryIntervalMillis() { return 0; }
    };

    private static class InMemoryCursorStore implements SyncCursorStore {
        private long lastSync = 0L;
        @Override public long getLastSyncTimestamp() { return lastSync; }
        @Override public void updateLastSyncTimestamp(long timestamp) { this.lastSync = timestamp; }
        @Override public void clear() { this.lastSync = 0L; }
    }

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        dbOwner = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        dbGuest1 = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        dbGuest2 = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();

        Map<String, byte[]> storage = new ConcurrentHashMap<>();
        HashSet<String> folders = new HashSet<>();
        sharedStore = new InMemorySharedFolderStore("baseline_sync_store", storage, folders);
    }

    @After
    public void tearDown() {
        if (dbOwner != null) dbOwner.close();
        if (dbGuest1 != null) dbGuest1.close();
        if (dbGuest2 != null) dbGuest2.close();
    }

    @Test
    public void testDeleteProductPropagationAndThirdDeviceJoinAfterCompaction() throws Exception {
        String ownerDev = "owner_dev";
        String guest1Dev = "guest_1_dev";
        String guest2Dev = "guest_2_dev";
        String pantryPath = "pantry-baseline/";

        Dispensa dOwner = new Dispensa("Casa", true);
        dOwner.id = 1;
        dOwner.deviceOwnerId = ownerDev;
        dbOwner.dispensaDao().insert(dOwner);

        Dispensa dGuest1 = new Dispensa("Casa", false);
        dGuest1.id = 1;
        dGuest1.deviceOwnerId = ownerDev;
        dbGuest1.dispensaDao().insert(dGuest1);

        Dispensa dGuest2 = new Dispensa("Casa", false);
        dGuest2.id = 1;
        dGuest2.deviceOwnerId = ownerDev;
        dbGuest2.dispensaDao().insert(dGuest2);

        FolderSyncEngine engOwner = new FolderSyncEngine(
                sharedStore, new RoomPantryDataBridge(dbOwner), new InMemoryCursorStore(), ownerDev, "Owner", pantryPath, 1
        );
        FolderSyncEngine engGuest1 = new FolderSyncEngine(
                sharedStore, new RoomPantryDataBridge(dbGuest1), new InMemoryCursorStore(), guest1Dev, "Guest1", pantryPath, 1
        );
        FolderSyncEngine engGuest2 = new FolderSyncEngine(
                sharedStore, new RoomPantryDataBridge(dbGuest2), new InMemoryCursorStore(), guest2Dev, "Guest2", pantryPath, 1
        );

        engOwner.initializeRemoteStructure().blockingAwait();

        // Owner adds 2 products
        long t1 = 1700000010000L;
        Product p1 = new Product("111", 2, 1750000000000L, "Riso", null, "pantry", 0L, -1);
        p1.dispensaId = 1;
        p1.lastModified = t1;
        p1.id = (int) dbOwner.productDao().insert(p1);
        enqueueOutbox(dbOwner, 1, "evt-1", "UPSERT_PRODUCT", gson.toJson(p1), t1);

        long t2 = 1700000020000L;
        Product p2 = new Product("222", 1, 1750000000000L, "Tonno", null, "pantry", 0L, -1);
        p2.dispensaId = 1;
        p2.lastModified = t2;
        p2.id = (int) dbOwner.productDao().insert(p2);
        enqueueOutbox(dbOwner, 1, "evt-2", "UPSERT_PRODUCT", gson.toJson(p2), t2);

        engOwner.performSync(FORCE_POLICY).blockingAwait();
        engGuest1.performSync(FORCE_POLICY).blockingAwait();
        Assert.assertEquals(2, dbGuest1.productDao().getAllProductsListStatic(1).size());

        // Guest1 deletes p1 ("Riso")
        long t3 = 1700000030000L;
        Product guestP1 = dbGuest1.productDao().getProductByLotKeySync("111", 1750000000000L, "pantry", 1);
        Assert.assertNotNull(guestP1);
        dbGuest1.productDao().delete(guestP1);
        enqueueOutbox(dbGuest1, 1, "evt-3", "DELETE_PRODUCT", gson.toJson(guestP1), t3);

        engGuest1.performSync(FORCE_POLICY).blockingAwait();
        engOwner.performSync(FORCE_POLICY).blockingAwait();

        // Owner now only has "Tonno"
        List<Product> ownerRemaining = dbOwner.productDao().getAllProductsListStatic(1);
        Assert.assertEquals(1, ownerRemaining.size());
        Assert.assertEquals("Tonno", ownerRemaining.get(0).getProductName());

        // Owner forces compaction, then Guest2 joins from scratch
        engOwner.forceCompaction();
        engGuest2.performSync(FORCE_POLICY).blockingAwait();

        List<Product> guest2Products = dbGuest2.productDao().getAllProductsListStatic(1);
        Assert.assertEquals(1, guest2Products.size());
        Assert.assertEquals("Tonno", guest2Products.get(0).getProductName());
    }

    @Test
    public void testVersionMismatchEmittedWhenRemoteManifestVersionIsHigher() throws Exception {
        String pantryPath = "pantry-version-check/";
        Dispensa dGuest = new Dispensa("Casa", false);
        dGuest.id = 1;
        dGuest.deviceOwnerId = "owner_dev";
        dbGuest1.dispensaDao().insert(dGuest);

        FolderSyncEngine engOwner = new FolderSyncEngine(
                sharedStore, new RoomPantryDataBridge(dbOwner), new InMemoryCursorStore(), "owner_dev", "Owner", pantryPath, 1
        );
        engOwner.initializeRemoteStructure().blockingAwait();

        // Simulate remote manifest having a future version (CURRENT_SYNC_VERSION + 10)
        byte[] raw = sharedStore.read(pantryPath + "manifest.json").blockingGet();
        PantryManifest manifest = gson.fromJson(new String(raw, StandardCharsets.UTF_8), PantryManifest.class);
        manifest.version = SyncManager.CURRENT_SYNC_VERSION + 10;
        sharedStore.writeAtomic(pantryPath + "manifest.json", gson.toJson(manifest).getBytes(StandardCharsets.UTF_8), null).blockingAwait();

        AtomicReference<SyncEvent.VersionMismatch> capturedMismatch = new AtomicReference<>();
        Disposable sub = SyncBus.getInstance().observe()
                .filter(e -> e instanceof SyncEvent.VersionMismatch)
                .subscribe(e -> capturedMismatch.set((SyncEvent.VersionMismatch) e));

        try {
            FolderSyncEngine engGuest = new FolderSyncEngine(
                    sharedStore, new RoomPantryDataBridge(dbGuest1), new InMemoryCursorStore(), "guest_dev", "Guest", pantryPath, 1
            );
            engGuest.performSync(FORCE_POLICY).blockingAwait();
        } finally {
            sub.dispose();
        }

        Assert.assertNotNull("Expected SyncEvent.VersionMismatch to be emitted", capturedMismatch.get());
        Assert.assertEquals(SyncManager.CURRENT_SYNC_VERSION + 10, capturedMismatch.get().remoteVersion);
    }

    private void enqueueOutbox(AppDatabase db, int dispensaId, String syncId, String dataType, String payload, long ts) {
        SyncOutbox out = new SyncOutbox();
        out.syncId = syncId;
        out.dispensaId = dispensaId;
        out.dataType = dataType;
        out.payload = payload;
        out.timestamp = ts;
        db.syncOutboxDao().insert(out);
    }
}
