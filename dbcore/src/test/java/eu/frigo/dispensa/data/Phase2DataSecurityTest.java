package eu.frigo.dispensa.data;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import eu.frigo.dispensa.data.backup.PreMigrationBackupHelper;
import eu.frigo.dispensa.data.openfoodfacts.OpenFoodFactCacheManager;

/**
 * Fase 2 Data & Network Security Test Suite:
 * - Path traversal protection on Open Food Facts barcode image cache
 * - Pre-migration backup credential exclusion & rotation (max 2 files)
 */
@RunWith(RobolectricTestRunner.class)
public class Phase2DataSecurityTest {

    @Test
    public void testBarcodePathTraversalSanitization() {
        Assert.assertEquals("8001234567890", OpenFoodFactCacheManager.sanitizeBarcodeForFilename("8001234567890"));
        Assert.assertEquals("pwned", OpenFoodFactCacheManager.sanitizeBarcodeForFilename("../../pwned"));
        Assert.assertEquals("", OpenFoodFactCacheManager.sanitizeBarcodeForFilename("../../.."));

        Context context = ApplicationProvider.getApplicationContext();
        // Attempting to download with a traversal-only barcode returns null immediately without network
        String result = OpenFoodFactCacheManager.downloadAndSaveImage(context, "https://example.com/img.jpg", "../../../");
        Assert.assertNull(result);
    }

    @Test
    public void testPreMigrationBackupExcludesCredentialsAndRotatesOldFiles() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String dbName = "test_phase2_premig_security_db";
        File dbFile = context.getDatabasePath(dbName);
        if (dbFile.getParentFile() != null) {
            dbFile.getParentFile().mkdirs();
        }

        File backupDir = new File(context.getFilesDir(), "backups/pre_migration");
        if (backupDir.exists() && backupDir.listFiles() != null) {
            for (File f : backupDir.listFiles()) {
                f.delete();
            }
        }

        // Simulate 3 consecutive migrations (v19->20, v20->21, v21->22) with a table containing passwords
        for (int v = 19; v <= 21; v++) {
            try (android.database.sqlite.SQLiteDatabase sqliteDb =
                         android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)) {
                sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS products (id INTEGER PRIMARY KEY, product_name TEXT)");
                sqliteDb.execSQL("CREATE TABLE IF NOT EXISTS joined_pantry_configs (dispensa_id INTEGER PRIMARY KEY, password TEXT)");
                sqliteDb.execSQL("INSERT OR REPLACE INTO products (id, product_name) VALUES (1, 'Pasta')");
                sqliteDb.execSQL("INSERT OR REPLACE INTO joined_pantry_configs (dispensa_id, password) VALUES (1, 'TOP_SECRET_PASSWORD_123')");
                sqliteDb.setVersion(v);
            }
            PreMigrationBackupHelper.checkAndBackup(context, dbName, v + 1);
            Thread.sleep(15);
        }

        File[] backups = backupDir.listFiles((d, name) -> name.endsWith(".json"));
        Assert.assertNotNull(backups);
        Assert.assertEquals("At most 2 pre-migration backups must be retained",
                PreMigrationBackupHelper.MAX_BACKUP_FILES, backups.length);

        for (File backup : backups) {
            String content = new String(Files.readAllBytes(backup.toPath()), StandardCharsets.UTF_8);
            Assert.assertFalse("Backup JSON must never contain plaintext WebDAV password",
                    content.contains("TOP_SECRET_PASSWORD_123"));
            Assert.assertFalse("Backup JSON must exclude joined_pantry_configs table",
                    content.contains("joined_pantry_configs"));
        }

        dbFile.delete();
    }
}
