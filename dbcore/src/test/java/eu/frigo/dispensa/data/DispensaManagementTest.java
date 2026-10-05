package eu.frigo.dispensa.data;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.dispensa.DispensaDao;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.product.ProductDao;
import eu.frigo.dispensa.data.storage.PredefinedData;
import eu.frigo.dispensa.data.storage.StorageLocation;
import eu.frigo.dispensa.data.storage.StorageLocationDao;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.data.sync.JoinedPantryConfigDao;
import eu.frigo.dispensa.data.sync.SyncOutbox;
import eu.frigo.dispensa.data.sync.SyncOutboxDao;

@RunWith(RobolectricTestRunner.class)
public class DispensaManagementTest {

    private AppDatabase db;
    private DispensaDao dispensaDao;
    private ProductDao productDao;
    private StorageLocationDao storageLocationDao;
    private SyncOutboxDao syncOutboxDao;
    private JoinedPantryConfigDao joinedPantryConfigDao;

    @Before
    public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dispensaDao = db.dispensaDao();
        productDao = db.productDao();
        storageLocationDao = db.storageLocationDao();
        syncOutboxDao = db.syncOutboxDao();
        joinedPantryConfigDao = db.joinedPantryConfigDao();
    }

    @After
    public void teardown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    public void testAddDispensaAndInitialLocations() {
        Dispensa dispensa = new Dispensa("Casa Montagna", false);
        dispensa.deviceOwnerId = "dev_12345";
        dispensa.deviceOwnerName = "Pixel 8";

        long dispId = dispensaDao.insert(dispensa);
        Assert.assertTrue(dispId > 0);

        // Predefined storage locations for the new pantry
        List<StorageLocation> locations = PredefinedData.getInitialStorageLocations((int) dispId);
        storageLocationDao.insertAll(locations);

        Dispensa retrieved = dispensaDao.getDispensaByIdSync((int) dispId);
        Assert.assertNotNull(retrieved);
        Assert.assertEquals("Casa Montagna", retrieved.getName());
        Assert.assertFalse(retrieved.isDefault());
        Assert.assertEquals("dev_12345", retrieved.deviceOwnerId);
        Assert.assertEquals("Pixel 8", retrieved.deviceOwnerName);

        List<StorageLocation> savedLocations = storageLocationDao.getAllLocationsSortedSync((int) dispId);
        Assert.assertEquals(3, savedLocations.size());
    }

    @Test
    public void testUpdateDispensa() {
        Dispensa dispensa = new Dispensa("Vecchia Dispensa", false);
        long dispId = dispensaDao.insert(dispensa);
        dispensa.setId((int) dispId);

        dispensa.setName("Nuova Dispensa Rinominata");
        dispensa.lastModified = System.currentTimeMillis();
        dispensaDao.update(dispensa);

        Dispensa updated = dispensaDao.getDispensaByIdSync((int) dispId);
        Assert.assertNotNull(updated);
        Assert.assertEquals("Nuova Dispensa Rinominata", updated.getName());
    }

    @Test
    public void testSetAsDefaultDispensa() {
        Dispensa d1 = new Dispensa("Dispensa 1", true);
        Dispensa d2 = new Dispensa("Dispensa 2", false);
        Dispensa d3 = new Dispensa("Dispensa 3", false);

        long id1 = dispensaDao.insert(d1);
        long id2 = dispensaDao.insert(d2);
        long id3 = dispensaDao.insert(d3);

        Dispensa currentDefault = dispensaDao.getDefaultDispensaSync();
        Assert.assertNotNull(currentDefault);
        Assert.assertEquals((int) id1, currentDefault.getId());

        // Set dispensa 2 as default
        dispensaDao.setAsDefault((int) id2);

        Dispensa newDefault = dispensaDao.getDefaultDispensaSync();
        Assert.assertNotNull(newDefault);
        Assert.assertEquals((int) id2, newDefault.getId());

        // Dispensa 1 and 3 should not be default anymore
        Assert.assertFalse(dispensaDao.getDispensaByIdSync((int) id1).isDefault());
        Assert.assertTrue(dispensaDao.getDispensaByIdSync((int) id2).isDefault());
        Assert.assertFalse(dispensaDao.getDispensaByIdSync((int) id3).isDefault());
    }

    @Test
    public void testDeleteDispensaAndCascadeCleanup() {
        Dispensa dispensa = new Dispensa("Dispensa Da Rimuovere", false);
        long dispId = dispensaDao.insert(dispensa);
        dispensa.setId((int) dispId);

        // Add child records for this pantry
        Product product = new Product("123", 2, 1000L, "Mela", null, "loc", 0L, -1);
        product.dispensaId = (int) dispId;
        productDao.insert(product);

        StorageLocation loc = new StorageLocation("FRIDGE", "FRIDGE", 0, true, true);
        loc.dispensaId = (int) dispId;
        storageLocationDao.insert(loc);

        SyncOutbox outbox = new SyncOutbox();
        outbox.syncId = "sync-test-1";
        outbox.dispensaId = (int) dispId;
        outbox.dataType = "UPSERT_PRODUCT";
        outbox.payload = "{}";
        outbox.timestamp = System.currentTimeMillis();
        syncOutboxDao.insert(outbox);

        JoinedPantryConfig config = new JoinedPantryConfig((int) dispId, "https://dav.test", "user", "pass", "/", false, "key123");
        joinedPantryConfigDao.insert(config);

        // Verify entities exist before deletion
        Assert.assertEquals(1, productDao.getAllProductsListStatic((int) dispId).size());
        Assert.assertEquals(1, storageLocationDao.getAllLocationsSortedSync((int) dispId).size());
        Assert.assertEquals(1, syncOutboxDao.getPendingChangesSync((int) dispId).size());
        Assert.assertNotNull(joinedPantryConfigDao.getConfigByDispensaId((int) dispId));

        // Perform cleanup matching Repository.deleteDispensa
        productDao.deleteAllProducts((int) dispId);
        storageLocationDao.deleteAllLocations((int) dispId);
        syncOutboxDao.deleteAllByDispensaId((int) dispId);
        joinedPantryConfigDao.deleteByDispensaId((int) dispId);
        dispensaDao.delete(dispensa);

        // Verify all associated items are removed
        Assert.assertNull(dispensaDao.getDispensaByIdSync((int) dispId));
        Assert.assertEquals(0, productDao.getAllProductsListStatic((int) dispId).size());
        Assert.assertEquals(0, storageLocationDao.getAllLocationsSortedSync((int) dispId).size());
        Assert.assertEquals(0, syncOutboxDao.getPendingChangesSync((int) dispId).size());
        Assert.assertNull(joinedPantryConfigDao.getConfigByDispensaId((int) dispId));
    }

    @Test
    public void testDeleteOrphans() {
        Dispensa validDisp = new Dispensa("Valida", true);
        long validId = dispensaDao.insert(validDisp);

        int orphanId = 9999;

        // Insert orphan entities (associated with non-existing dispensa id 9999)
        Product orphanProduct = new Product("999", 1, 1000L, "Orfano", null, "loc", 0L, -1);
        orphanProduct.dispensaId = orphanId;
        productDao.insert(orphanProduct);

        StorageLocation orphanLoc = new StorageLocation("ORPHAN", "ORPHAN", 0, true, true);
        orphanLoc.dispensaId = orphanId;
        storageLocationDao.insert(orphanLoc);

        SyncOutbox orphanOutbox = new SyncOutbox();
        orphanOutbox.syncId = "orphan-sync-1";
        orphanOutbox.dispensaId = orphanId;
        orphanOutbox.dataType = "UPSERT_PRODUCT";
        orphanOutbox.payload = "{}";
        orphanOutbox.timestamp = System.currentTimeMillis();
        syncOutboxDao.insert(orphanOutbox);

        JoinedPantryConfig orphanConfig = new JoinedPantryConfig(orphanId, "https://dav.test", "user", "pass", "/", false, "key");
        joinedPantryConfigDao.insert(orphanConfig);

        // Clean orphans
        productDao.deleteOrphans();
        storageLocationDao.deleteOrphans();
        syncOutboxDao.deleteOrphans();
        joinedPantryConfigDao.deleteOrphans();

        // Verify orphan records are cleaned up
        Assert.assertEquals(0, productDao.getAllProductsListStatic(orphanId).size());
        Assert.assertEquals(0, storageLocationDao.getAllLocationsSortedSync(orphanId).size());
        Assert.assertEquals(0, syncOutboxDao.getPendingChangesSync(orphanId).size());
        Assert.assertNull(joinedPantryConfigDao.getConfigByDispensaId(orphanId));
    }
}
