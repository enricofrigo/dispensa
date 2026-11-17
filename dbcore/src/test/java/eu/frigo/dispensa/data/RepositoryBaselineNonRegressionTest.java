package eu.frigo.dispensa.data;

import android.app.Application;
import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import eu.frigo.dispensa.data.category.CategoryDefinition;
import eu.frigo.dispensa.data.category.ProductCategoryLink;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItem;
import eu.frigo.dispensa.data.storage.StorageLocation;
import eu.frigo.dispensa.data.sync.SyncOutbox;

/**
 * Fase 0 Baseline Non-Regression Test Suite for Repository CRUD, Categories,
 * Locations, Shopping List, Dispensa isolation, and SyncOutbox event generation.
 */
@RunWith(RobolectricTestRunner.class)
public class RepositoryBaselineNonRegressionTest {

    private AppDatabase db;
    private Repository repository;
    private int defaultDispensaId;

    @Before
    public void setUp() throws Exception {
        Application app = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        AppDatabase.setTestInstance(db);

        repository = new Repository(app);
        Dispensa defaultDispensa = new Dispensa("Casa", true);
        defaultDispensaId = (int) repository.insertDispensaSync(defaultDispensa, true);
        flushAsyncTasks();
    }

    @After
    public void tearDown() {
        if (db != null) {
            db.close();
        }
        AppDatabase.setTestInstance(null);
    }

    private void flushAsyncTasks() throws Exception {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        AppDatabase.databaseWriteExecutor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    @Test
    public void testProductCrudAndSyncOutboxGeneration() throws Exception {
        Product p = new Product("8001122334455", 2, 1750000000000L, "Biscotti Integrali", null, "pantry", 0L, -1);
        repository.insert(p);
        flushAsyncTasks();

        List<Product> products = db.productDao().getAllProductsListStatic(defaultDispensaId);
        Assert.assertEquals(1, products.size());
        Product saved = products.get(0);
        Assert.assertEquals("Biscotti Integrali", saved.getProductName());
        Assert.assertEquals(2, saved.getQuantity());

        List<SyncOutbox> outboxAfterInsert = db.syncOutboxDao().getPendingChangesSync(defaultDispensaId);
        Assert.assertFalse("Insert must record UPSERT_PRODUCT in SyncOutbox", outboxAfterInsert.isEmpty());
        Assert.assertEquals("UPSERT_PRODUCT", outboxAfterInsert.get(outboxAfterInsert.size() - 1).dataType);

        // Update product quantity and percentage
        saved.setQuantity(1);
        saved.setRemainingPercentage(50);
        saved.setOpenedDate(1740000000000L);
        repository.update(saved);
        flushAsyncTasks();

        Product updated = db.productDao().getProductByIdSync(saved.getId());
        Assert.assertNotNull(updated);
        Assert.assertEquals(1, updated.getQuantity());
        Assert.assertEquals(50, updated.getRemainingPercentage());
        Assert.assertTrue(updated.isOpened());

        // Delete product
        repository.delete(updated);
        flushAsyncTasks();

        Assert.assertNull(db.productDao().getProductByIdSync(saved.getId()));
        List<SyncOutbox> outboxAfterDelete = db.syncOutboxDao().getPendingChangesSync(defaultDispensaId);
        Assert.assertEquals("DELETE_PRODUCT", outboxAfterDelete.get(outboxAfterDelete.size() - 1).dataType);
    }

    @Test
    public void testInsertAndUpdateProductWithApiCategoryTags() throws Exception {
        Product p = new Product("8005566778899", 1, 1750000000000L, "Mozzarella", null, "fridge", 0L, 3);
        List<String> initialTags = Arrays.asList("en:dairies", "it:formaggi");
        repository.insertProductWithApiTags(p, initialTags);
        flushAsyncTasks();

        List<Product> products = db.productDao().getAllProductsListStatic(defaultDispensaId);
        Assert.assertEquals(1, products.size());
        int prodId = products.get(0).getId();

        List<ProductCategoryLink> links = db.productCategoryLinkDao().getAllProductCategoryLinksForDispensaSync(defaultDispensaId);
        Assert.assertEquals(2, links.size());

        List<CategoryDefinition> categories = db.categoryDefinitionDao().getAllCategoryDefinitionsSync();
        Assert.assertEquals(2, categories.size());

        // Update tags replacing with a new set
        Product toUpdate = products.get(0);
        toUpdate.setQuantity(3);
        repository.updateProductWithApiTags(toUpdate, Arrays.asList("it:latticini-freschi"));
        flushAsyncTasks();

        List<ProductCategoryLink> updatedLinks = db.productCategoryLinkDao().getAllProductCategoryLinksForDispensaSync(defaultDispensaId);
        Assert.assertEquals(1, updatedLinks.size());
        CategoryDefinition linkedCat = db.categoryDefinitionDao().getCategoryByTagName("it:latticini-freschi");
        Assert.assertNotNull(linkedCat);
        Assert.assertEquals(linkedCat.categoryId, updatedLinks.get(0).categoryIdFk);
    }

    @Test
    public void testLocationLifecycleAndFallbackOnDelete() throws Exception {
        StorageLocation customLoc = new StorageLocation("Cantina Vini", "cantina_vini", 10, false, false);
        repository.insertLocation(customLoc);
        flushAsyncTasks();

        StorageLocation savedLoc = db.storageLocationDao().getLocationByInternalKeySync("cantina_vini", defaultDispensaId);
        Assert.assertNotNull(savedLoc);
        Assert.assertEquals("Cantina Vini", savedLoc.getName());

        // Add product in custom location
        Product wine = new Product("800999000111", 4, null, "Chianti", null, "cantina_vini", 0L, -1);
        repository.insert(wine);
        flushAsyncTasks();

        // Delete custom location -> product must move to default location
        StorageLocation defaultLoc = db.storageLocationDao().getDefaultLocationSync(defaultDispensaId);
        Assert.assertNotNull(defaultLoc);

        repository.deleteLocation(savedLoc);
        flushAsyncTasks();

        Assert.assertNull(db.storageLocationDao().getLocationByInternalKeySync("cantina_vini", defaultDispensaId));
        List<Product> products = db.productDao().getAllProductsListStatic(defaultDispensaId);
        Assert.assertEquals(1, products.size());
        Assert.assertEquals(defaultLoc.internalKey, products.get(0).getStorageLocation());
    }

    @Test
    public void testShoppingListLifecycle() throws Exception {
        repository.addToShoppingList("Olio EVO", 2);
        flushAsyncTasks();

        ShoppingItem oil = db.shoppingItemDao().getItemByNameSync("Olio EVO", defaultDispensaId);
        Assert.assertNotNull(oil);
        Assert.assertEquals(2, oil.getQuantity());
        Assert.assertFalse(oil.isChecked());

        // Adding same item increments quantity
        repository.addToShoppingList("Olio EVO", 3);
        flushAsyncTasks();

        oil = db.shoppingItemDao().getItemByNameSync("Olio EVO", defaultDispensaId);
        Assert.assertEquals(5, oil.getQuantity());

        // Add second item and mark first as checked
        repository.addToShoppingList("Sale Grosso", 1);
        flushAsyncTasks();

        oil.setChecked(true);
        repository.updateShoppingItem(oil);
        flushAsyncTasks();

        // Clear checked items
        repository.clearCheckedShoppingItems();
        flushAsyncTasks();

        Assert.assertNull(db.shoppingItemDao().getItemByNameSync("Olio EVO", defaultDispensaId));
        Assert.assertNotNull(db.shoppingItemDao().getItemByNameSync("Sale Grosso", defaultDispensaId));
    }

    @Test
    public void testMultiDispensaIsolationAndDeleteDispensa() throws Exception {
        Dispensa secondDisp = new Dispensa("Ufficio", false);
        int secondDispId = (int) repository.insertDispensaSync(secondDisp, true);
        flushAsyncTasks();

        Product officeCoffee = new Product("800111000222", 1, null, "Caffè Ufficio", null, "pantry", 0L, -1);
        repository.insert(officeCoffee);
        flushAsyncTasks();

        Assert.assertEquals(0, db.productDao().getAllProductsListStatic(defaultDispensaId).size());
        Assert.assertEquals(1, db.productDao().getAllProductsListStatic(secondDispId).size());

        // Add a shopping item in second dispensa to verify deleteDispensa cleans up shopping_items too
        repository.addToShoppingList("Zucchero Ufficio", 2);
        flushAsyncTasks();
        Assert.assertEquals(1, db.shoppingItemDao().getAllItemsSync(secondDispId).size());

        // Delete second dispensa
        Dispensa toDelete = db.dispensaDao().getDispensaByIdSync(secondDispId);
        repository.deleteDispensa(toDelete);
        flushAsyncTasks();

        Assert.assertNull(db.dispensaDao().getDispensaByIdSync(secondDispId));
        Assert.assertEquals(0, db.productDao().getAllProductsListStatic(secondDispId).size());
        Assert.assertEquals("deleteDispensa must also delete shopping_items for that dispensa",
                0, db.shoppingItemDao().getAllItemsSync(secondDispId).size());
        Assert.assertEquals("currentDispensaId must fall back to valid dispensa",
                Integer.valueOf(defaultDispensaId), repository.getCurrentDispensaId().getValue());
    }

    @Test
    public void testCleanOrphanDataRemovesOrphanShoppingItemsAndInsertResolvesDefaultDispensa() throws Exception {
        // Insert an orphan shopping item pointing to non-existent dispensaId = 999
        ShoppingItem orphanItem = new ShoppingItem("Orphan Item", 1, false);
        orphanItem.dispensaId = 999;
        db.shoppingItemDao().insert(orphanItem);
        Assert.assertEquals(1, db.shoppingItemDao().getAllItemsSync(999).size());

        repository.cleanOrphanData();
        flushAsyncTasks();

        Assert.assertEquals("cleanOrphanData must remove orphan shopping_items",
                0, db.shoppingItemDao().getAllItemsSync(999).size());
    }
}
