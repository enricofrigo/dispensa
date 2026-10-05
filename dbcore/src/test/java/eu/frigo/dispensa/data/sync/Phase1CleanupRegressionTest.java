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

import java.util.List;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.storage.StorageLocation;

@RunWith(RobolectricTestRunner.class)
public class Phase1CleanupRegressionTest {

    private AppDatabase db;

    @Before
    public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void teardown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    public void testDatabaseIntegrityAndDaosWithoutDeadConflictResolver() {
        // Verify Dispensa DAO
        Dispensa disp = new Dispensa("Dispensa Principale", true);
        long dispId = db.dispensaDao().insert(disp);
        Assert.assertTrue(dispId > 0);

        // Verify StorageLocation DAO
        StorageLocation location = new StorageLocation("Frigorifero", "FRIDGE", 0, false, true);
        location.dispensaId = (int) dispId;
        long locId = db.storageLocationDao().insert(location);
        Assert.assertTrue(locId > 0);

        // Verify Product DAO
        Product product = new Product("8001234567890", 2, System.currentTimeMillis() + 86400000L, "Latte Intero", "", "FRIDGE", 0L, -1);
        product.dispensaId = (int) dispId;
        long prodId = db.productDao().insert(product);
        Assert.assertTrue(prodId > 0);

        // Verify JoinedPantryConfig DAO
        JoinedPantryConfig config = new JoinedPantryConfig((int) dispId, "https://dav.test", "user", "pass", "/pantry", false, "key123");
        db.joinedPantryConfigDao().insert(config);
        JoinedPantryConfig loadedConfig = db.joinedPantryConfigDao().getConfigByDispensaId((int) dispId);
        Assert.assertNotNull(loadedConfig);
        Assert.assertEquals("https://dav.test", loadedConfig.url);

        // Verify SyncOutbox DAO
        SyncOutbox outboxEntry = new SyncOutbox();
        outboxEntry.syncId = "sync-001";
        outboxEntry.dispensaId = (int) dispId;
        outboxEntry.dataType = "PRODUCT";
        outboxEntry.payload = "{\"name\":\"Latte\"}";
        outboxEntry.timestamp = System.currentTimeMillis();
        db.syncOutboxDao().insert(outboxEntry);
        List<SyncOutbox> pending = db.syncOutboxDao().getPendingChangesSync((int) dispId);
        Assert.assertEquals(1, pending.size());
        Assert.assertEquals("sync-001", pending.get(0).syncId);
    }
}
