package eu.frigo.dispensa.data.sync;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItem;
import eu.frigo.dispensa.data.storage.StorageLocation;
import eu.frigo.dispensa.sync.core.engine.FolderSyncEngine;
import eu.frigo.dispensa.sync.core.policy.SyncPolicy;
import eu.frigo.dispensa.sync.core.store.InMemorySharedFolderStore;
import eu.frigo.dispensa.sync.core.store.SyncCursorStore;

@RunWith(RobolectricTestRunner.class)
public class FolderSyncEngineMultiDeviceTest {

    private AppDatabase dbOwner;
    private AppDatabase dbGuest;
    private InMemorySharedFolderStore sharedStore;

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
    public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        dbOwner = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dbGuest = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();

        Map<String, byte[]> storage = new ConcurrentHashMap<>();
        HashSet<String> folders = new HashSet<>();
        sharedStore = new InMemorySharedFolderStore("shared_test_store", storage, folders);
    }

    @After
    public void teardown() {
        if (dbOwner != null) dbOwner.close();
        if (dbGuest != null) dbGuest.close();
    }

    @Test
    public void testFullProductSharingLifecycleAcrossDevices() {
        String ownerDeviceId = "device_owner_1";
        String guestDeviceId = "device_guest_2";
        int ownerPantryId = 1;
        int guestPantryId = 10;
        String pantryPath = "pantry-shared-lifecycle-sync/";

        // 1. Setup Owner Dispensa
        Dispensa ownerDisp = new Dispensa("Dispensa Casa", true);
        ownerDisp.id = ownerPantryId;
        ownerDisp.deviceOwnerId = ownerDeviceId;
        dbOwner.dispensaDao().insert(ownerDisp);

        // 2. Setup Guest Dispensa
        Dispensa guestDisp = new Dispensa("Dispensa Casa", false);
        guestDisp.id = guestPantryId;
        guestDisp.deviceOwnerId = ownerDeviceId;
        dbGuest.dispensaDao().insert(guestDisp);

        RoomPantryDataBridge bridgeOwner = new RoomPantryDataBridge(dbOwner);
        RoomPantryDataBridge bridgeGuest = new RoomPantryDataBridge(dbGuest);

        SyncCursorStore cursorOwner = new InMemoryCursorStore();
        SyncCursorStore cursorGuest = new InMemoryCursorStore();

        FolderSyncEngine engineOwner = new FolderSyncEngine(
                sharedStore, bridgeOwner, cursorOwner, ownerDeviceId, "Pixel 8", pantryPath, ownerPantryId
        );
        FolderSyncEngine engineGuest = new FolderSyncEngine(
                sharedStore, bridgeGuest, cursorGuest, guestDeviceId, "iPhone 15", pantryPath, guestPantryId
        );

        // ==========================================
        // PHASE 1: OWNER ADDS PRODUCT AND SYNCS
        // ==========================================
        Product product = new Product("800123456789", 6, System.currentTimeMillis() + 86400000L, "Latte Intero", null, "FRIDGE", 0L, -1);
        product.dispensaId = ownerPantryId;
        product.lastModified = System.currentTimeMillis();
        long pId = dbOwner.productDao().insert(product);
        product.setId((int) pId);

        SyncOutbox outboxAdd = new SyncOutbox();
        outboxAdd.syncId = "evt-add-01";
        outboxAdd.dispensaId = ownerPantryId;
        outboxAdd.dataType = "UPSERT_PRODUCT";
        outboxAdd.payload = new com.google.gson.Gson().toJson(product);
        outboxAdd.timestamp = product.lastModified;
        dbOwner.syncOutboxDao().insert(outboxAdd);

        // Owner initializes remote structure & pushes changes
        engineOwner.initializeRemoteStructure().blockingAwait();
        engineOwner.performSync(FORCE_POLICY).blockingAwait();

        // Guest pulls changes
        engineGuest.performSync(FORCE_POLICY).blockingAwait();

        List<Product> guestProducts = dbGuest.productDao().getAllProductsListStatic(guestPantryId);
        Assert.assertEquals(1, guestProducts.size());
        Product guestP = guestProducts.get(0);
        Assert.assertEquals("800123456789", guestP.getBarcode());
        Assert.assertEquals("Latte Intero", guestP.getProductName());
        Assert.assertEquals(6, guestP.getQuantity());

        // ==========================================
        // PHASE 2: GUEST UPDATES (CONSUMES 2 PACKS)
        // ==========================================
        guestP.setQuantity(4);
        guestP.lastModified = System.currentTimeMillis() + 1000L;
        dbGuest.productDao().update(guestP);

        SyncOutbox outboxUpd = new SyncOutbox();
        outboxUpd.syncId = "evt-upd-02";
        outboxUpd.dispensaId = guestPantryId;
        outboxUpd.dataType = "UPSERT_PRODUCT";
        outboxUpd.payload = new com.google.gson.Gson().toJson(guestP);
        outboxUpd.timestamp = guestP.lastModified;
        dbGuest.syncOutboxDao().insert(outboxUpd);

        engineGuest.performSync(FORCE_POLICY).blockingAwait();
        engineOwner.performSync(FORCE_POLICY).blockingAwait();

        Product ownerP = dbOwner.productDao().getProductByIdSync((int) pId);
        Assert.assertNotNull(ownerP);
        Assert.assertEquals(4, ownerP.getQuantity());

        // ==========================================
        // PHASE 3: LOCATIONS AND SHOPPING ITEMS SYNC
        // ==========================================
        StorageLocation newLoc = new StorageLocation("Cantina", "cantina_key", 5, false, false);
        newLoc.dispensaId = ownerPantryId;
        newLoc.lastModified = System.currentTimeMillis() + 2000L;
        dbOwner.storageLocationDao().insert(newLoc);

        SyncOutbox outboxLoc = new SyncOutbox();
        outboxLoc.syncId = "evt-loc-03";
        outboxLoc.dispensaId = ownerPantryId;
        outboxLoc.dataType = "UPSERT_LOCATION";
        outboxLoc.payload = new com.google.gson.Gson().toJson(newLoc);
        outboxLoc.timestamp = newLoc.lastModified;
        dbOwner.syncOutboxDao().insert(outboxLoc);

        ShoppingItem shoppingItem = new ShoppingItem("Caffè", 2, false);
        shoppingItem.dispensaId = ownerPantryId;
        shoppingItem.lastModified = System.currentTimeMillis() + 2000L;
        dbOwner.shoppingItemDao().insert(shoppingItem);

        SyncOutbox outboxShop = new SyncOutbox();
        outboxShop.syncId = "evt-shop-04";
        outboxShop.dispensaId = ownerPantryId;
        outboxShop.dataType = "UPSERT_SHOPPING_ITEM";
        outboxShop.payload = new com.google.gson.Gson().toJson(shoppingItem);
        outboxShop.timestamp = shoppingItem.lastModified;
        dbOwner.syncOutboxDao().insert(outboxShop);

        engineOwner.performSync(FORCE_POLICY).blockingAwait();
        engineGuest.performSync(FORCE_POLICY).blockingAwait();

        StorageLocation guestLoc = dbGuest.storageLocationDao().getLocationByInternalKeySync("cantina_key", guestPantryId);
        Assert.assertNotNull(guestLoc);
        Assert.assertEquals("Cantina", guestLoc.getName());

        ShoppingItem guestShop = dbGuest.shoppingItemDao().getItemByNameSync("Caffè", guestPantryId);
        Assert.assertNotNull(guestShop);
        Assert.assertEquals(2, guestShop.quantity);
    }

    @Test
    public void testEagerDeviceRegistrationAndInitialSnapshotPullOnJoin() throws Exception {
        String ownerDeviceId = "device_owner_abc";
        String guestDeviceId = "device_guest_xyz";
        int ownerPantryId = 1;
        int guestPantryId = 2;
        String pantryPath = ""; // tests direct folder / root pantry path

        // 1. Owner dispensa in local DB
        Dispensa ownerDisp = new Dispensa("Dispensa Famiglia", true);
        ownerDisp.id = ownerPantryId;
        ownerDisp.deviceOwnerId = ownerDeviceId;
        dbOwner.dispensaDao().insert(ownerDisp);

        // Prepopulate products on owner
        Product p1 = new Product("800111222333", 3, System.currentTimeMillis() + 86400000L, "Pasta Barilla", null, "PANTRY", 0L, -1);
        p1.dispensaId = ownerPantryId;
        p1.lastModified = System.currentTimeMillis();
        dbOwner.productDao().insert(p1);

        RoomPantryDataBridge bridgeOwner = new RoomPantryDataBridge(dbOwner);
        SyncCursorStore cursorOwner = new InMemoryCursorStore();
        FolderSyncEngine engineOwner = new FolderSyncEngine(
                sharedStore, bridgeOwner, cursorOwner, ownerDeviceId, "Owner Phone", pantryPath, ownerPantryId
        );

        // Owner shares: initializes remote structure and forces compaction (creates snapshot)
        engineOwner.initializeRemoteStructure().blockingAwait();
        engineOwner.forceCompaction();

        // Verify remote store has manifest and snapshot
        Assert.assertTrue(sharedStore.exists("manifest.json").blockingGet());
        Assert.assertTrue(sharedStore.exists("devices/" + ownerDeviceId + ".json").blockingGet());

        // 2. Guest joins:
        // Guest registers device eagerly on remote storage
        eu.frigo.dispensa.sync.core.model.PantryDevice guestDevice = new eu.frigo.dispensa.sync.core.model.PantryDevice(guestDeviceId, "Guest Phone");
        String guestDeviceJson = new com.google.gson.Gson().toJson(guestDevice);
        sharedStore.writeAtomic("devices/" + guestDeviceId + ".json", guestDeviceJson.getBytes(java.nio.charset.StandardCharsets.UTF_8), null).blockingAwait();

        // Verify device file exists eagerly on remote storage BEFORE sync!
        Assert.assertTrue(sharedStore.exists("devices/" + guestDeviceId + ".json").blockingGet());

        // Guest sets up local Dispensa with deviceOwnerId = ownerDeviceId
        Dispensa guestDisp = new Dispensa("Dispensa Famiglia", false);
        guestDisp.id = guestPantryId;
        guestDisp.deviceOwnerId = ownerDeviceId;
        dbGuest.dispensaDao().insert(guestDisp);

        JoinedPantryConfig guestConfig = new JoinedPantryConfig(guestPantryId, "gdrive", null, "remote_pantry_1");
        dbGuest.joinedPantryConfigDao().insert(guestConfig);

        RoomPantryDataBridge bridgeGuest = new RoomPantryDataBridge(dbGuest);
        // Verify bridgeGuest does NOT consider guest as owner
        Assert.assertFalse(bridgeGuest.isPantryOwner(guestPantryId, guestDeviceId));

        // Guest performs initial eager sync pull
        SyncCursorStore cursorGuest = new InMemoryCursorStore();
        FolderSyncEngine engineGuest = new FolderSyncEngine(
                sharedStore, bridgeGuest, cursorGuest, guestDeviceId, "Guest Phone", pantryPath, guestPantryId
        );
        engineGuest.performSync(FORCE_POLICY).blockingAwait();

        // Verify guest immediately has the product in DB!
        List<Product> guestProducts = dbGuest.productDao().getAllProductsListStatic(guestPantryId);
        Assert.assertEquals(1, guestProducts.size());
        Assert.assertEquals("Pasta Barilla", guestProducts.get(0).getProductName());
        Assert.assertEquals(3, guestProducts.get(0).getQuantity());
    }

    @Test
    public void testSnapshotDoesNotOverwriteExistingProductsInOtherPantry() throws Exception {
        // 1. Guest already has a product in Dispensa 1 (local)
        Dispensa guestLocalDisp = new Dispensa("Dispensa Locale", true);
        guestLocalDisp.id = 1;
        guestLocalDisp.deviceOwnerId = "guest_dev";
        dbGuest.dispensaDao().insert(guestLocalDisp);

        Product existingProd = new Product();
        existingProd.id = 1;
        existingProd.dispensaId = 1;
        existingProd.barcode = "BARCODE_LOCAL";
        existingProd.setProductName("Latte Locale");
        existingProd.quantity = 2;
        existingProd.lastModified = System.currentTimeMillis();
        dbGuest.productDao().insert(existingProd);

        // 2. Owner has a product with id=1 in their pantry
        int ownerPantryId = 1;
        int guestPantryId = 2;
        String ownerDeviceId = "owner_dev";
        String guestDeviceId = "guest_dev";

        Dispensa ownerDisp = new Dispensa("Owner Pantry", false);
        ownerDisp.id = ownerPantryId;
        ownerDisp.deviceOwnerId = ownerDeviceId;
        dbOwner.dispensaDao().insert(ownerDisp);

        Product remoteProd = new Product();
        remoteProd.id = 1; // Same ID as guest's local product!
        remoteProd.dispensaId = ownerPantryId;
        remoteProd.barcode = "BARCODE_REMOTE";
        remoteProd.setProductName("Pasta Remota");
        remoteProd.quantity = 5;
        remoteProd.lastModified = System.currentTimeMillis();
        dbOwner.productDao().insert(remoteProd);

        RoomPantryDataBridge bridgeOwner = new RoomPantryDataBridge(dbOwner);
        SyncCursorStore cursorOwner = new InMemoryCursorStore();
        FolderSyncEngine engineOwner = new FolderSyncEngine(
                sharedStore, bridgeOwner, cursorOwner, ownerDeviceId, "Owner Phone", "", ownerPantryId
        );
        engineOwner.initializeRemoteStructure().blockingAwait();
        engineOwner.forceCompaction();

        // 3. Guest joins pantry 2
        Dispensa guestJoinedDisp = new Dispensa("Dispensa Famiglia", false);
        guestJoinedDisp.id = guestPantryId;
        guestJoinedDisp.deviceOwnerId = ownerDeviceId;
        dbGuest.dispensaDao().insert(guestJoinedDisp);

        RoomPantryDataBridge bridgeGuest = new RoomPantryDataBridge(dbGuest);
        SyncCursorStore cursorGuest = new InMemoryCursorStore();
        FolderSyncEngine engineGuest = new FolderSyncEngine(
                sharedStore, bridgeGuest, cursorGuest, guestDeviceId, "Guest Phone", "", guestPantryId
        );
        engineGuest.performSync(FORCE_POLICY).blockingAwait();

        // 4. Verify guest local product in Dispensa 1 was NOT overwritten
        Product localAfter = dbGuest.productDao().getProductByIdSync(1);
        Assert.assertNotNull(localAfter);
        Assert.assertEquals(1, localAfter.dispensaId);
        Assert.assertEquals("Latte Locale", localAfter.getProductName());

        // 5. Verify guest joined product in Dispensa 2 exists with its own new ID!
        List<Product> joinedProducts = dbGuest.productDao().getAllProductsListStatic(guestPantryId);
        Assert.assertEquals(1, joinedProducts.size());
        Assert.assertEquals("Pasta Remota", joinedProducts.get(0).getProductName());
        Assert.assertEquals(guestPantryId, joinedProducts.get(0).dispensaId);
        Assert.assertNotEquals(1, joinedProducts.get(0).id);
    }

    @Test
    public void testNullExpiryDateLotKeyMatching() {
        Product p = new Product();
        p.dispensaId = 1;
        p.barcode = "123456";
        p.expiryDate = null;
        p.setStorageLocation("pantry");
        p.setProductName("Sale");
        p.quantity = 1;
        p.lastModified = System.currentTimeMillis();
        dbOwner.productDao().insert(p);

        Product match = dbOwner.productDao().getProductByLotKeySync("123456", null, "pantry", 1);
        Assert.assertNotNull("Product with null expiryDate should match via IS operator", match);
        Assert.assertEquals("Sale", match.getProductName());
    }

    @Test
    public void testPerDispensaCursorStoreImplIsolation() {
        Context context = ApplicationProvider.getApplicationContext();
        eu.frigo.dispensa.sync.core.store.SyncCursorStoreImpl cursorPantry1 =
                new eu.frigo.dispensa.sync.core.store.SyncCursorStoreImpl(context, 1);
        eu.frigo.dispensa.sync.core.store.SyncCursorStoreImpl cursorPantry2 =
                new eu.frigo.dispensa.sync.core.store.SyncCursorStoreImpl(context, 2);

        cursorPantry1.clear();
        cursorPantry2.clear();

        Assert.assertEquals(0L, cursorPantry1.getLastSyncTimestamp());
        Assert.assertEquals(0L, cursorPantry2.getLastSyncTimestamp());

        cursorPantry1.updateLastSyncTimestamp(1000L);
        Assert.assertEquals(1000L, cursorPantry1.getLastSyncTimestamp());
        Assert.assertEquals(0L, cursorPantry2.getLastSyncTimestamp()); // Must stay 0!

        cursorPantry2.updateLastSyncTimestamp(2000L);
        Assert.assertEquals(1000L, cursorPantry1.getLastSyncTimestamp());
        Assert.assertEquals(2000L, cursorPantry2.getLastSyncTimestamp());

        cursorPantry1.clear();
        Assert.assertEquals(0L, cursorPantry1.getLastSyncTimestamp());
        Assert.assertEquals(2000L, cursorPantry2.getLastSyncTimestamp());
    }
}
