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
}
