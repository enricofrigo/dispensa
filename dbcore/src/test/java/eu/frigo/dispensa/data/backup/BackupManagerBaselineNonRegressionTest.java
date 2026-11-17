package eu.frigo.dispensa.data.backup;

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
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.category.CategoryDefinition;
import eu.frigo.dispensa.data.category.ProductCategoryLink;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItem;
import eu.frigo.dispensa.data.storage.StorageLocation;

/**
 * Fase 0 Baseline Non-Regression Test Suite for BackupManager (exportData, peekBackupData, importData)
 * and PreMigrationBackupHelper.
 */
@RunWith(RobolectricTestRunner.class)
public class BackupManagerBaselineNonRegressionTest {

    private AppDatabase sourceDb;
    private AppDatabase targetDb;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        sourceDb = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        targetDb = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void tearDown() {
        if (sourceDb != null) sourceDb.close();
        if (targetDb != null) targetDb.close();
    }

    @Test
    public void testExportPeekAndImportFullPantryRoundTrip() throws Exception {
        // 1. Populate source database
        Dispensa sourceDisp = new Dispensa("Dispensa Vacanze", true);
        int sourceDispId = (int) sourceDb.dispensaDao().insert(sourceDisp);

        StorageLocation locFridge = new StorageLocation("Frigorifero", "fridge", 0, true, true);
        locFridge.dispensaId = sourceDispId;
        locFridge.lastModified = 1700000000000L;
        sourceDb.storageLocationDao().insert(locFridge);

        StorageLocation locCellar = new StorageLocation("Cantina", "cellar", 1, false, false);
        locCellar.dispensaId = sourceDispId;
        locCellar.lastModified = 1700000001000L;
        sourceDb.storageLocationDao().insert(locCellar);

        CategoryDefinition catDairy = new CategoryDefinition("en:dairies");
        catDairy.displayNameIt = "Latticini";
        catDairy.languageCode = "en";
        int catDairyId = (int) sourceDb.categoryDefinitionDao().insert(catDairy);

        Product prod1 = new Product("800123000111", 3, 1750000000000L, "Yogurt Greco", "https://example.com/yogurt.jpg", "fridge", 0L, 5, "Senza lattosio");
        prod1.dispensaId = sourceDispId;
        prod1.remainingPercentage = 80;
        prod1.lastModified = 1700000002000L;
        int prod1Id = (int) sourceDb.productDao().insert(prod1);

        sourceDb.productCategoryLinkDao().insert(new ProductCategoryLink(prod1Id, catDairyId));

        ShoppingItem shop1 = new ShoppingItem("Pane Integrale", 2, false);
        shop1.dispensaId = sourceDispId;
        shop1.lastModified = 1700000003000L;
        sourceDb.shoppingItemDao().insert(shop1);

        // 2. Export to JSON bytes
        BackupManager sourceBackupManager = new BackupManager(sourceDb);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        sourceBackupManager.exportData(baos, 28, sourceDispId);

        byte[] backupBytes = baos.toByteArray();
        String backupJson = new String(backupBytes, StandardCharsets.UTF_8);
        Assert.assertTrue(backupJson.contains("Yogurt Greco"));
        Assert.assertTrue(backupJson.contains("Pane Integrale"));

        // 3. Peek backup metadata
        BackupManager targetBackupManager = new BackupManager(targetDb);
        BackupData peeked = targetBackupManager.peekBackupData(new ByteArrayInputStream(backupBytes));
        Assert.assertNotNull(peeked);
        Assert.assertEquals(28, peeked.appVersion);
        Assert.assertNotNull(peeked.dispensa);
        Assert.assertEquals("Dispensa Vacanze", peeked.dispensa.getName());
        Assert.assertEquals(1, peeked.products.size());

        // 4. Import into target database (into targetDispensaId = 5)
        Dispensa targetDisp = new Dispensa("Destinazione", true);
        targetDisp.id = 5;
        targetDb.dispensaDao().insert(targetDisp);

        targetBackupManager.importData(new ByteArrayInputStream(backupBytes), 5);

        // 5. Verify imported records
        List<StorageLocation> importedLocs = targetDb.storageLocationDao().getAllLocationsSortedSync(5);
        Assert.assertEquals(2, importedLocs.size());

        List<Product> importedProducts = targetDb.productDao().getAllProductsListStatic(5);
        Assert.assertEquals(1, importedProducts.size());
        Product impProd = importedProducts.get(0);
        Assert.assertEquals("800123000111", impProd.getBarcode());
        Assert.assertEquals("Yogurt Greco", impProd.getProductName());
        Assert.assertEquals(3, impProd.getQuantity());
        Assert.assertEquals(80, impProd.getRemainingPercentage());
        Assert.assertEquals("Senza lattosio", impProd.getNote());

        List<ProductCategoryLink> importedLinks = targetDb.productCategoryLinkDao().getAllProductCategoryLinksForDispensaSync(5);
        Assert.assertEquals(1, importedLinks.size());
        Assert.assertEquals(impProd.getId(), importedLinks.get(0).productIdFk);

        List<ShoppingItem> importedShop = targetDb.shoppingItemDao().getAllItemsSync(5);
        Assert.assertEquals(1, importedShop.size());
        Assert.assertEquals("Pane Integrale", importedShop.get(0).getName());
        Assert.assertEquals(2, importedShop.get(0).getQuantity());
    }

    @Test
    public void testPreMigrationBackupHelperCreatesBackupWhenVersionIncreases() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String testDbName = "test_premig_db";
        File dbFile = context.getDatabasePath(testDbName);
        if (dbFile.getParentFile() != null) {
            dbFile.getParentFile().mkdirs();
        }

        // Create a real on-disk SQLite DB at version 21
        try (android.database.sqlite.SQLiteDatabase sqliteDb =
                     android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)) {
            sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS products (id INTEGER PRIMARY KEY, product_name TEXT)");
            sqliteDb.execSQL("INSERT INTO products (id, product_name) VALUES (1, 'Farina 00')");
            sqliteDb.setVersion(21);
        }

        File backupDir = new File(context.getFilesDir(), "backups/pre_migration");
        if (backupDir.exists() && backupDir.listFiles() != null) {
            for (File f : backupDir.listFiles()) {
                f.delete();
            }
        }

        PreMigrationBackupHelper.checkAndBackup(context, testDbName, 22);

        File[] backups = backupDir.listFiles();
        Assert.assertNotNull(backups);
        Assert.assertEquals(1, backups.length);
        Assert.assertTrue(backups[0].getName().startsWith("backup_v21_to_v22_"));

        dbFile.delete();
    }
}
