package eu.frigo.dispensa.data;

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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import eu.frigo.dispensa.data.backup.BackupManager;
import eu.frigo.dispensa.data.category.CategoryDefinition;
import eu.frigo.dispensa.data.category.ProductCategoryLink;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItem;
import eu.frigo.dispensa.data.sync.RoomPantryDataBridge;
import eu.frigo.dispensa.data.sync.SyncOutbox;
import eu.frigo.dispensa.sync.core.engine.FolderSyncEngine;
import eu.frigo.dispensa.sync.core.policy.SyncPolicy;
import eu.frigo.dispensa.sync.core.store.InMemorySharedFolderStore;
import eu.frigo.dispensa.sync.core.store.SyncCursorStore;

/**
 * Fase 3 Database & Sync Integrity Test Suite:
 * - Stable sync_uuid identity when changing barcode, expiryDate, or storageLocation
 * - Two barcode-less products with same expiry and location do not collapse
 * - Category links preserved across sync upserts (no ON DELETE CASCADE wipe) and synced across devices
 * - ShoppingItem rename and delete propagation via sync_uuid
 * - BackupManager.importData category link remapping with overlapping oldId -> newId
 */
@RunWith(RobolectricTestRunner.class)
public class Phase3DatabaseAndSyncIntegrityTest {

    private AppDatabase dbA;
    private AppDatabase dbB;
    private InMemorySharedFolderStore sharedStore;
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

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        dbA = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        dbB = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();

        Map<String, byte[]> storage = new ConcurrentHashMap<>();
        HashSet<String> folders = new HashSet<>();
        sharedStore = new InMemorySharedFolderStore("phase3_store", storage, folders);

        Dispensa dA = new Dispensa("Casa", true);
        dA.id = 1;
        dA.deviceOwnerId = "dev_A";
        dbA.dispensaDao().insert(dA);

        Dispensa dB = new Dispensa("Casa", false);
        dB.id = 1;
        dB.deviceOwnerId = "dev_A";
        dbB.dispensaDao().insert(dB);
    }

    @After
    public void tearDown() {
        if (dbA != null) dbA.close();
        if (dbB != null) dbB.close();
    }

    @Test
    public void testChangingExpiryLocationOrBarcodeUpdatesInPlaceWithoutDuplicating() {
        String pantryPath = "pantry-uuid-identity/";
        FolderSyncEngine engineA = new FolderSyncEngine(sharedStore, new RoomPantryDataBridge(dbA), new SimpleCursorStore(), "dev_A", "A", pantryPath, 1);
        FolderSyncEngine engineB = new FolderSyncEngine(sharedStore, new RoomPantryDataBridge(dbB), new SimpleCursorStore(), "dev_B", "B", pantryPath, 1);

        engineA.initializeRemoteStructure().blockingAwait();

        // 1. Device A adds product
        Product prod = new Product("800111", 2, 1750000000000L, "Latte Fresco", null, "fridge", 0L, -1);
        prod.dispensaId = 1;
        prod.lastModified = 1000L;
        prod.id = (int) dbA.productDao().insert(prod);
        enqueueOutbox(dbA, "e1", "UPSERT_PRODUCT", gson.toJson(prod), 1000L);

        engineA.performSync(FORCE_POLICY).blockingAwait();
        engineB.performSync(FORCE_POLICY).blockingAwait();

        List<Product> bProducts1 = dbB.productDao().getAllProductsListStatic(1);
        Assert.assertEquals(1, bProducts1.size());
        Assert.assertEquals(prod.syncUuid, bProducts1.get(0).syncUuid);

        // 2. Device A changes barcode, expiryDate, AND storageLocation on the SAME product!
        prod.setBarcode("800999");
        prod.setExpiryDate(1760000000000L);
        prod.setStorageLocation("pantry");
        prod.setProductName("Latte UHT");
        prod.lastModified = 2000L;
        dbA.productDao().update(prod);
        enqueueOutbox(dbA, "e2", "UPSERT_PRODUCT", gson.toJson(prod), 2000L);

        engineA.performSync(FORCE_POLICY).blockingAwait();
        engineB.performSync(FORCE_POLICY).blockingAwait();

        List<Product> bProducts2 = dbB.productDao().getAllProductsListStatic(1);
        Assert.assertEquals("Must update existing product in place without creating duplicate", 1, bProducts2.size());
        Product updatedB = bProducts2.get(0);
        Assert.assertEquals("800999", updatedB.getBarcode());
        Assert.assertEquals(Long.valueOf(1760000000000L), updatedB.getExpiryDate());
        Assert.assertEquals("pantry", updatedB.getStorageLocation());
        Assert.assertEquals("Latte UHT", updatedB.getProductName());
    }

    @Test
    public void testTwoBarcodeLessProductsWithSameExpiryAndLocationDoNotCollapse() {
        String pantryPath = "pantry-no-barcode/";
        FolderSyncEngine engineA = new FolderSyncEngine(sharedStore, new RoomPantryDataBridge(dbA), new SimpleCursorStore(), "dev_A", "A", pantryPath, 1);
        FolderSyncEngine engineB = new FolderSyncEngine(sharedStore, new RoomPantryDataBridge(dbB), new SimpleCursorStore(), "dev_B", "B", pantryPath, 1);

        engineA.initializeRemoteStructure().blockingAwait();

        long sameExpiry = 1750000000000L;
        Product apples = new Product("", 4, sameExpiry, "Mele Golden", null, "fridge", 0L, -1);
        apples.dispensaId = 1;
        apples.lastModified = 1000L;
        apples.id = (int) dbA.productDao().insert(apples);
        enqueueOutbox(dbA, "e-apple", "UPSERT_PRODUCT", gson.toJson(apples), 1000L);

        Product pears = new Product("", 3, sameExpiry, "Pere Abate", null, "fridge", 0L, -1);
        pears.dispensaId = 1;
        pears.lastModified = 1001L;
        pears.id = (int) dbA.productDao().insert(pears);
        enqueueOutbox(dbA, "e-pear", "UPSERT_PRODUCT", gson.toJson(pears), 1001L);

        engineA.performSync(FORCE_POLICY).blockingAwait();
        engineB.performSync(FORCE_POLICY).blockingAwait();

        List<Product> bProducts = dbB.productDao().getAllProductsListStatic(1);
        Assert.assertEquals("Two distinct barcode-less products must not collapse into one", 2, bProducts.size());
    }

    @Test
    public void testCategoriesArePropagatedAndPreservedOnSyncUpsert() {
        String pantryPath = "pantry-categories-sync/";
        FolderSyncEngine engineA = new FolderSyncEngine(sharedStore, new RoomPantryDataBridge(dbA), new SimpleCursorStore(), "dev_A", "A", pantryPath, 1);
        FolderSyncEngine engineB = new FolderSyncEngine(sharedStore, new RoomPantryDataBridge(dbB), new SimpleCursorStore(), "dev_B", "B", pantryPath, 1);

        engineA.initializeRemoteStructure().blockingAwait();

        // 1. Device A creates a product with 2 categories
        int cat1 = (int) dbA.categoryDefinitionDao().insert(new CategoryDefinition("en:dairies"));
        int cat2 = (int) dbA.categoryDefinitionDao().insert(new CategoryDefinition("it:formaggi"));

        Product cheese = new Product("800555", 2, 1750000000000L, "Parmigiano", null, "fridge", 0L, -1);
        cheese.dispensaId = 1;
        cheese.lastModified = 1000L;
        cheese.id = (int) dbA.productDao().insert(cheese);
        dbA.productCategoryLinkDao().insert(new ProductCategoryLink(cheese.id, cat1));
        dbA.productCategoryLinkDao().insert(new ProductCategoryLink(cheese.id, cat2));

        enqueueOutbox(dbA, "e-cheese-1", "UPSERT_PRODUCT", gson.toJson(cheese), 1000L);

        engineA.performSync(FORCE_POLICY).blockingAwait();
        engineB.performSync(FORCE_POLICY).blockingAwait();

        // Verify Device B received both the product AND the 2 category links!
        List<ProductCategoryLink> bLinks = dbB.productCategoryLinkDao().getAllProductCategoryLinksForDispensaSync(1);
        Assert.assertEquals("Device B must receive the 2 category links via sync", 2, bLinks.size());

        // 2. Now Device B updates the product quantity (e.g. without sending categoryTags in raw payload)
        Product bCheese = dbB.productDao().getAllProductsListStatic(1).get(0);
        bCheese.setQuantity(1);
        bCheese.lastModified = 2000L;
        bCheese.categoryTags = null; // Simulate an update payload without categoryTags
        dbB.productDao().upsertPreservingLinks(bCheese);
        enqueueOutbox(dbB, "e-cheese-2", "UPSERT_PRODUCT", gson.toJson(bCheese), 2000L);

        engineB.performSync(FORCE_POLICY).blockingAwait();
        engineA.performSync(FORCE_POLICY).blockingAwait();

        // Verify Device A updated quantity to 1 AND still has both category links (not wiped by REPLACE / ON DELETE CASCADE!)
        Product aCheeseAfter = dbA.productDao().getProductByIdSync(cheese.id);
        Assert.assertEquals(1, aCheeseAfter.getQuantity());
        List<ProductCategoryLink> aLinksAfter = dbA.productCategoryLinkDao().getAllProductCategoryLinksForDispensaSync(1);
        Assert.assertEquals("Category links on Device A must not be wiped by sync upsert", 2, aLinksAfter.size());
    }

    @Test
    public void testShoppingItemRenameAndDeleteSyncsViaSyncUuid() {
        String pantryPath = "pantry-shopping-uuid/";
        FolderSyncEngine engineA = new FolderSyncEngine(sharedStore, new RoomPantryDataBridge(dbA), new SimpleCursorStore(), "dev_A", "A", pantryPath, 1);
        FolderSyncEngine engineB = new FolderSyncEngine(sharedStore, new RoomPantryDataBridge(dbB), new SimpleCursorStore(), "dev_B", "B", pantryPath, 1);

        engineA.initializeRemoteStructure().blockingAwait();

        ShoppingItem item = new ShoppingItem("Pane", 1, false);
        item.dispensaId = 1;
        item.lastModified = 1000L;
        item.id = (int) dbA.shoppingItemDao().insert(item);
        enqueueOutbox(dbA, "s1", "UPSERT_SHOPPING_ITEM", gson.toJson(item), 1000L);

        engineA.performSync(FORCE_POLICY).blockingAwait();
        engineB.performSync(FORCE_POLICY).blockingAwait();
        Assert.assertEquals(1, dbB.shoppingItemDao().getAllItemsSync(1).size());

        // Rename "Pane" -> "Pane Integrale" on Device A
        item.setName("Pane Integrale");
        item.lastModified = 2000L;
        dbA.shoppingItemDao().update(item);
        enqueueOutbox(dbA, "s2", "UPSERT_SHOPPING_ITEM", gson.toJson(item), 2000L);

        engineA.performSync(FORCE_POLICY).blockingAwait();
        engineB.performSync(FORCE_POLICY).blockingAwait();

        List<ShoppingItem> bItems = dbB.shoppingItemDao().getAllItemsSync(1);
        Assert.assertEquals("Renaming shopping item must not create duplicate", 1, bItems.size());
        Assert.assertEquals("Pane Integrale", bItems.get(0).getName());

        // Delete item on Device B and sync back to Device A
        ShoppingItem bItem = bItems.get(0);
        bItem.lastModified = 3000L;
        dbB.shoppingItemDao().delete(bItem);
        enqueueOutbox(dbB, "s3", "DELETE_SHOPPING_ITEM", gson.toJson(bItem), 3000L);

        engineB.performSync(FORCE_POLICY).blockingAwait();
        engineA.performSync(FORCE_POLICY).blockingAwait();

        Assert.assertEquals("DELETE_SHOPPING_ITEM must remove item on Device A", 0, dbA.shoppingItemDao().getAllItemsSync(1).size());
    }

    @Test
    public void testBackupImportWithOverlappingProductIdsPreservesExactCategoryMapping() throws Exception {
        // 1. In dbA (source), Product 1 (id=1) has Category "en:milk", Product 2 (id=2) has Category "en:meat"
        int catMilkId = (int) dbA.categoryDefinitionDao().insert(new CategoryDefinition("en:milk"));
        int catMeatId = (int) dbA.categoryDefinitionDao().insert(new CategoryDefinition("en:meat"));

        Product p1 = new Product("8001", 1, null, "Latte", null, "fridge", 0L, -1);
        p1.dispensaId = 1;
        int p1Id = (int) dbA.productDao().insert(p1); // id = 1
        Assert.assertEquals(1, p1Id);

        Product p2 = new Product("8002", 1, null, "Bistecca", null, "fridge", 0L, -1);
        p2.dispensaId = 1;
        int p2Id = (int) dbA.productDao().insert(p2); // id = 2
        Assert.assertEquals(2, p2Id);

        dbA.productCategoryLinkDao().insert(new ProductCategoryLink(p1Id, catMilkId));
        dbA.productCategoryLinkDao().insert(new ProductCategoryLink(p2Id, catMeatId));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new BackupManager(dbA).exportData(baos, 28, 1);
        byte[] backupBytes = baos.toByteArray();

        // 2. In dbB (target), advance SQLite AUTOINCREMENT by inserting & deleting 1 dummy product
        // so that when p1 (oldId=1) is imported, it gets newId=2 (which overlaps with p2's oldId=2!),
        // and p2 (oldId=2) gets newId=3!
        Product dummy = new Product("000", 1, null, "Dummy", null, "pantry", 0L, -1);
        dummy.dispensaId = 1;
        long dummyId = dbB.productDao().insert(dummy);
        Assert.assertEquals(1L, dummyId);
        dbB.productDao().deleteAllProducts(1);

        // 3. Import backup into dbB
        new BackupManager(dbB).importData(new ByteArrayInputStream(backupBytes), 1);

        List<Product> importedProducts = dbB.productDao().getAllProductsListStatic(1);
        Assert.assertEquals(2, importedProducts.size());

        Product impMilk = null;
        Product impMeat = null;
        for (Product ip : importedProducts) {
            if ("Latte".equals(ip.getProductName())) impMilk = ip;
            if ("Bistecca".equals(ip.getProductName())) impMeat = ip;
        }
        Assert.assertNotNull(impMilk);
        Assert.assertNotNull(impMeat);
        Assert.assertEquals("Latte should have been assigned overlapping newId=2", 2, impMilk.getId());
        Assert.assertEquals("Bistecca should have been assigned newId=3", 3, impMeat.getId());

        CategoryDefinition targetMilkCat = dbB.categoryDefinitionDao().getCategoryByTagName("en:milk");
        CategoryDefinition targetMeatCat = dbB.categoryDefinitionDao().getCategoryByTagName("en:meat");

        List<ProductCategoryLink> targetLinks = dbB.productCategoryLinkDao().getAllProductCategoryLinksForDispensaSync(1);
        Assert.assertEquals(2, targetLinks.size());

        boolean milkLinkedCorrectly = false;
        boolean meatLinkedCorrectly = false;
        for (ProductCategoryLink link : targetLinks) {
            if (link.productIdFk == impMilk.getId() && link.categoryIdFk == targetMilkCat.categoryId) {
                milkLinkedCorrectly = true;
            }
            if (link.productIdFk == impMeat.getId() && link.categoryIdFk == targetMeatCat.categoryId) {
                meatLinkedCorrectly = true;
            }
        }
        Assert.assertTrue("Latte (oldId=1 -> newId=2) must stay linked to en:milk, not overwritten to Bistecca!", milkLinkedCorrectly);
        Assert.assertTrue("Bistecca (oldId=2 -> newId=3) must be linked to en:meat!", meatLinkedCorrectly);
    }

    private void enqueueOutbox(AppDatabase db, String syncId, String dataType, String payload, long ts) {
        SyncOutbox out = new SyncOutbox();
        out.syncId = syncId;
        out.dispensaId = 1;
        out.dataType = dataType;
        out.payload = payload;
        out.timestamp = ts;
        db.syncOutboxDao().insert(out);
    }
}
