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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.backup.BackupData;
import eu.frigo.dispensa.data.backup.BackupManager;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.sync.core.engine.PantryOutboxItem;
import eu.frigo.dispensa.sync.core.model.PantryEvent;

@RunWith(RobolectricTestRunner.class)
public class ImageSanitizationTest {

    private AppDatabase db;
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    public void testProductIsCustomLocalImageDetection() {
        Assert.assertFalse(Product.isCustomLocalImage(null));
        Assert.assertFalse(Product.isCustomLocalImage(""));
        Assert.assertFalse(Product.isCustomLocalImage("   "));
        Assert.assertFalse(Product.isCustomLocalImage("http://example.com/image.png"));
        Assert.assertFalse(Product.isCustomLocalImage("https://images.openfoodfacts.org/images/products/123/front.jpg"));

        Assert.assertTrue(Product.isCustomLocalImage("file:///data/user/0/eu.frigo.dispensa/files/product_images/photo.jpg"));
        Assert.assertTrue(Product.isCustomLocalImage("content://media/external/images/media/42"));
        Assert.assertTrue(Product.isCustomLocalImage("/storage/emulated/0/Android/data/photo.jpg"));
    }

    @Test
    public void testProductCreateExportCopyClearsLocalImageOnly() {
        Product localImgProd = new Product("111", 2, 1000L, "Pane", "file:///data/photo.jpg", "Pantry", 0L, -1);
        Product onlineImgProd = new Product("222", 1, 2000L, "Latte", "https://images.openfoodfacts.org/img.jpg", "Fridge", 0L, -1);
        Product noImgProd = new Product("333", 5, 3000L, "Mela", null, "Basement", 0L, -1);

        Product localCopy = localImgProd.createExportCopy();
        Assert.assertNull(localCopy.getImageUrl());
        Assert.assertEquals("Pane", localCopy.getProductName());
        Assert.assertEquals("111", localCopy.getBarcode());

        Product onlineCopy = onlineImgProd.createExportCopy();
        Assert.assertEquals("https://images.openfoodfacts.org/img.jpg", onlineCopy.getImageUrl());
        Assert.assertEquals("Latte", onlineCopy.getProductName());

        Product noImgCopy = noImgProd.createExportCopy();
        Assert.assertNull(noImgCopy.getImageUrl());
    }

    @Test
    public void testBackupExportAndImportSanitization() throws Exception {
        Dispensa disp = new Dispensa("Casa", true);
        int dispId = (int) db.dispensaDao().insert(disp);

        Product p1 = new Product("123", 2, 1000L, "Pasta", "file:///data/user/0/eu.frigo.dispensa/files/pasta.jpg", "Dispensa", 0L, -1);
        p1.dispensaId = dispId;
        db.productDao().insert(p1);

        Product p2 = new Product("456", 1, 2000L, "Salsa", "https://images.openfoodfacts.org/salsa.jpg", "Dispensa", 0L, -1);
        p2.dispensaId = dispId;
        db.productDao().insert(p2);

        BackupManager backupManager = new BackupManager(db);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        backupManager.exportData(out, 1, dispId);

        String json = new String(out.toByteArray(), StandardCharsets.UTF_8);
        Assert.assertFalse("Exported backup must not contain local file URI", json.contains("file:///data/user/0"));
        Assert.assertTrue("Exported backup must preserve online image URL", json.contains("https://images.openfoodfacts.org/salsa.jpg"));

        // Now peek backup and verify
        BackupData peeked = backupManager.peekBackupData(new ByteArrayInputStream(out.toByteArray()));
        Assert.assertNotNull(peeked);
        Assert.assertEquals(2, peeked.products.size());
        Product peekedP1 = peeked.products.stream().filter(p -> "123".equals(p.barcode)).findFirst().orElse(null);
        Product peekedP2 = peeked.products.stream().filter(p -> "456".equals(p.barcode)).findFirst().orElse(null);
        Assert.assertNotNull(peekedP1);
        Assert.assertNotNull(peekedP2);
        Assert.assertNull(peekedP1.getImageUrl());
        Assert.assertEquals("https://images.openfoodfacts.org/salsa.jpg", peekedP2.getImageUrl());

        // Test import into a new dispensa
        Dispensa disp2 = new Dispensa("Casa 2", false);
        int disp2Id = (int) db.dispensaDao().insert(disp2);
        backupManager.importData(new ByteArrayInputStream(out.toByteArray()), disp2Id);

        List<Product> imported = db.productDao().getAllProductsListStatic(disp2Id);
        Assert.assertEquals(2, imported.size());
        Product importedP1 = imported.stream().filter(p -> "123".equals(p.barcode)).findFirst().orElse(null);
        Product importedP2 = imported.stream().filter(p -> "456".equals(p.barcode)).findFirst().orElse(null);
        Assert.assertNotNull(importedP1);
        Assert.assertNotNull(importedP2);
        Assert.assertNull(importedP1.getImageUrl());
        Assert.assertEquals("https://images.openfoodfacts.org/salsa.jpg", importedP2.getImageUrl());
    }

    @Test
    public void testSyncSnapshotAndEventSanitization() {
        Dispensa disp = new Dispensa("Sync Pantry", true);
        int dispId = (int) db.dispensaDao().insert(disp);

        Product pLocal = new Product("888", 2, 1000L, "Biscotti", "file:///local/path/img.jpg", "Scaffale", 0L, -1);
        pLocal.dispensaId = dispId;
        pLocal.lastModified = 100L;
        db.productDao().insert(pLocal);

        Product pOnline = new Product("999", 1, 2000L, "Acqua", "https://example.com/water.jpg", "Scaffale", 0L, -1);
        pOnline.dispensaId = dispId;
        pOnline.lastModified = 100L;
        db.productDao().insert(pOnline);

        RoomPantryDataBridge bridge = new RoomPantryDataBridge(db);

        // 1. Snapshot creation
        String snapshotJson = bridge.createSnapshotJson(dispId);
        Assert.assertFalse("Snapshot must not contain local file URIs", snapshotJson.contains("file:///local/path"));
        Assert.assertTrue("Snapshot must contain online image URL", snapshotJson.contains("https://example.com/water.jpg"));

        // 2. Outbox pending events sanitization
        SyncOutbox outboxEntry = new SyncOutbox();
        outboxEntry.syncId = "sync-1";
        outboxEntry.dispensaId = dispId;
        outboxEntry.dataType = PantryEvent.ACTION_UPSERT_PRODUCT;
        outboxEntry.payload = new com.google.gson.Gson().toJson(pLocal);
        outboxEntry.timestamp = System.currentTimeMillis();
        db.syncOutboxDao().insert(outboxEntry);

        List<PantryOutboxItem> pending = bridge.getPendingOutboxEvents(dispId);
        Assert.assertEquals(1, pending.size());
        Assert.assertFalse("Pending event payload must sanitize local image", pending.get(0).payloadJson.contains("file:///local/path"));

        // 3. Applying remote snapshot on another pantry
        Dispensa dispRemote = new Dispensa("Remote Pantry", false);
        int dispRemoteId = (int) db.dispensaDao().insert(dispRemote);

        bridge.applySnapshotJson(snapshotJson, dispRemoteId);
        List<Product> remoteProducts = db.productDao().getAllProductsListStatic(dispRemoteId);
        Assert.assertEquals(2, remoteProducts.size());
        Product remotePLocal = remoteProducts.stream().filter(p -> "888".equals(p.barcode)).findFirst().orElse(null);
        Product remotePOnline = remoteProducts.stream().filter(p -> "999".equals(p.barcode)).findFirst().orElse(null);
        Assert.assertNotNull(remotePLocal);
        Assert.assertNotNull(remotePOnline);
        Assert.assertNull(remotePLocal.getImageUrl());
        Assert.assertEquals("https://example.com/water.jpg", remotePOnline.getImageUrl());

        // 4. Applying remote event containing a local image path (e.g. from an older version)
        Product incomingWithLocalPath = new Product("777", 4, 3000L, "Caffè", "file:///other_phone/img.jpg", "Scaffale", 0L, -1);
        incomingWithLocalPath.lastModified = 500L;
        String eventPayload = new com.google.gson.Gson().toJson(incomingWithLocalPath);

        bridge.applyEvent(PantryEvent.ACTION_UPSERT_PRODUCT, eventPayload, 500L, dispRemoteId);
        Product appliedP = db.productDao().getProductByLotKeySync("777", 3000L, "Scaffale", dispRemoteId);
        Assert.assertNotNull(appliedP);
        Assert.assertNull("Incoming event with local image must be sanitized to null", appliedP.getImageUrl());
    }

    @Test
    public void testValidateImageUrlExistenceForMissingAndExistingFiles() throws Exception {
        java.io.File tempFile = java.io.File.createTempFile("test_prod", ".jpg");
        tempFile.deleteOnExit();

        Product pExisting = new Product("111", 1, 1000L, "P1", "file://" + tempFile.getAbsolutePath(), "L1", 0L, -1);
        pExisting.validateImageUrlExistence();
        Assert.assertNotNull(pExisting.getImageUrl());

        Product pMissing = new Product("222", 1, 1000L, "P2", "file:///non/existent/path/image.jpg", "L1", 0L, -1);
        pMissing.validateImageUrlExistence();
        Assert.assertNull(pMissing.getImageUrl());

        Product pOnline = new Product("333", 1, 1000L, "P3", "https://images.openfoodfacts.org/1.jpg", "L1", 0L, -1);
        pOnline.validateImageUrlExistence();
        Assert.assertEquals("https://images.openfoodfacts.org/1.jpg", pOnline.getImageUrl());
    }

    @Test
    public void testCleanOrphanImagesResetsMissingPathsInDatabase() throws Exception {
        Dispensa disp = new Dispensa("Pantry Clean", true);
        int dispId = (int) db.dispensaDao().insert(disp);

        java.io.File tempImagesDir = new java.io.File(context.getCacheDir(), "test_product_images_" + System.currentTimeMillis());
        tempImagesDir.mkdirs();

        java.io.File validImgFile = new java.io.File(tempImagesDir, "product_valid.jpg");
        validImgFile.createNewFile();

        java.io.File orphanImgFile = new java.io.File(tempImagesDir, "product_orphan.jpg");
        orphanImgFile.createNewFile();

        Product pValid = new Product("111", 1, 1000L, "Valid", "file://" + validImgFile.getAbsolutePath(), "Loc", 0L, -1);
        pValid.dispensaId = dispId;
        db.productDao().insert(pValid);

        Product pMissing = new Product("222", 1, 1000L, "Missing", "file:///data/non_existent_folder/missing.jpg", "Loc", 0L, -1);
        pMissing.dispensaId = dispId;
        db.productDao().insert(pMissing);

        Product pOnline = new Product("333", 1, 1000L, "Online", "https://example.com/online.jpg", "Loc", 0L, -1);
        pOnline.dispensaId = dispId;
        db.productDao().insert(pOnline);

        // Execute cleanOrphanImages synchronous wrapper
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        eu.frigo.dispensa.data.Repository.cleanOrphanImages(db, tempImagesDir, null, count -> latch.countDown());
        latch.await(5, java.util.concurrent.TimeUnit.SECONDS);

        // Verify database records
        List<Product> products = db.productDao().getAllProductsListStatic(dispId);
        Product checkedValid = products.stream().filter(p -> "111".equals(p.barcode)).findFirst().orElse(null);
        Product checkedMissing = products.stream().filter(p -> "222".equals(p.barcode)).findFirst().orElse(null);
        Product checkedOnline = products.stream().filter(p -> "333".equals(p.barcode)).findFirst().orElse(null);

        Assert.assertNotNull(checkedValid);
        Assert.assertEquals("file://" + validImgFile.getAbsolutePath(), checkedValid.getImageUrl());

        Assert.assertNotNull(checkedMissing);
        Assert.assertNull("Missing image path must be replaced with default (null)", checkedMissing.getImageUrl());

        Assert.assertNotNull(checkedOnline);
        Assert.assertEquals("https://example.com/online.jpg", checkedOnline.getImageUrl());

        // Verify orphan file was deleted from disk
        Assert.assertTrue(validImgFile.exists());
        Assert.assertFalse(orphanImgFile.exists());
    }
}
