package eu.frigo.dispensa.data;

import android.content.Context;
import android.database.Cursor;
import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.List;

import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;

@RunWith(RobolectricTestRunner.class)
public class DatabaseMigrationTest {

    private static final String TEST_DB_NAME = "test_migration_dispensa.db";
    private Context context;
    private File dbFile;

    @Before
    public void setup() {
        context = ApplicationProvider.getApplicationContext();
        dbFile = context.getDatabasePath(TEST_DB_NAME);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }

    @After
    public void teardown() {
        if (dbFile != null && dbFile.exists()) {
            dbFile.delete();
        }
    }

    private SupportSQLiteDatabase createDatabase(int version) {
        SupportSQLiteOpenHelper.Configuration configuration =
                SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name(TEST_DB_NAME)
                        .callback(new SupportSQLiteOpenHelper.Callback(version) {
                            @Override
                            public void onCreate(SupportSQLiteDatabase db) {}
                            @Override
                            public void onUpgrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {}
                        })
                        .build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(configuration);
        return helper.getWritableDatabase();
    }

    @Test
    public void testMigration12To21_preservesDataAndPassesRoomValidation() throws Exception {
        // 1. Prepara schema v12
        SupportSQLiteDatabase db = createDatabase(12);

        // products a v12
        db.execSQL("CREATE TABLE IF NOT EXISTS `products` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`product_name` TEXT, " +
                "`barcode` TEXT, " +
                "`quantity` INTEGER NOT NULL, " +
                "`expiry_date` INTEGER, " +
                "`image_url` TEXT, " +
                "`storage_location` TEXT, " +
                "`opened_date` INTEGER NOT NULL DEFAULT 0, " +
                "`shelf_life_after_opening_days` INTEGER NOT NULL DEFAULT -1, " +
                "`last_modified` INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_location_internal_key` ON `products` (`storage_location`)");

        // storage_locations a v12
        db.execSQL("CREATE TABLE IF NOT EXISTS `storage_locations` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT, " +
                "`internal_key` TEXT, " +
                "`order_index` INTEGER NOT NULL, " +
                "`is_default` INTEGER NOT NULL DEFAULT 0, " +
                "`is_predefined` INTEGER NOT NULL DEFAULT 0, " +
                "`last_modified` INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_storage_locations_internal_key` ON `storage_locations` (`internal_key`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_storage_locations_order_index` ON `storage_locations` (`order_index`)");

        // shopping_items a v12
        db.execSQL("CREATE TABLE IF NOT EXISTS `shopping_items` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT, " +
                "`quantity` INTEGER NOT NULL, " +
                "`checked` INTEGER NOT NULL, " +
                "`last_modified` INTEGER NOT NULL DEFAULT 0)");

        // sync_outbox a v12
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_outbox` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`syncId` TEXT, " +
                "`dataType` TEXT, " +
                "`payload` TEXT, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`isSynced` INTEGER NOT NULL)");

        // categories_definitions a v12
        db.execSQL("CREATE TABLE IF NOT EXISTS `categories_definitions` (" +
                "`category_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`tag_name` TEXT, " +
                "`display_name_it` TEXT, " +
                "`language_code` TEXT, " +
                "`color_hex` TEXT)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_definitions_tag_name` ON `categories_definitions` (`tag_name`)");

        // product_category_links a v12
        db.execSQL("CREATE TABLE IF NOT EXISTS `product_category_links` (" +
                "`product_id_fk` INTEGER NOT NULL, " +
                "`category_id_fk` INTEGER NOT NULL, " +
                "PRIMARY KEY(`product_id_fk`, `category_id_fk`), " +
                "FOREIGN KEY(`product_id_fk`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`category_id_fk`) REFERENCES `categories_definitions`(`category_id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_category_links_product_id_fk` ON `product_category_links` (`product_id_fk`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_category_links_category_id_fk` ON `product_category_links` (`category_id_fk`)");

        // openfoodfact_cache a v12
        db.execSQL("CREATE TABLE IF NOT EXISTS `openfoodfact_cache` (" +
                "`barcode` TEXT NOT NULL, " +
                "`product_name` TEXT, " +
                "`image_local_path` TEXT, " +
                "`categories_tags` TEXT, " +
                "`timestamp_ms` INTEGER NOT NULL, " +
                "PRIMARY KEY(`barcode`))");

        // Inserisci dati di test a v12
        db.execSQL("INSERT INTO products (id, product_name, barcode, quantity, storage_location) " +
                "VALUES (101, 'Pasta Barilla', '80768001', 3, 'PANTRY')");
        db.execSQL("INSERT INTO storage_locations (id, name, internal_key, order_index) " +
                "VALUES (1, 'Dispensa Cucina', 'PANTRY', 0)");
        db.execSQL("INSERT INTO shopping_items (id, name, quantity, checked) " +
                "VALUES (1, 'Latte', 2, 0)");

        db.setVersion(12);
        db.close();

        // 2. Apri con Room fornendo le migrazioni necessarie fino alla versione corrente
        AppDatabase roomDb = Room.databaseBuilder(context, AppDatabase.class, TEST_DB_NAME)
                .addMigrations(AppDatabase.MIGRATION_12_21, AppDatabase.MIGRATION_21_22)
                .allowMainThreadQueries()
                .build();

        // 3. Verifica dati e schema
        List<Product> products = roomDb.productDao().getAllProductsListStatic();
        Assert.assertEquals(1, products.size());
        Product p = products.get(0);
        Assert.assertEquals(101, p.getId());
        Assert.assertEquals("Pasta Barilla", p.getProductName());
        Assert.assertEquals(1, p.dispensaId); // Default valorizzato correttamente
        Assert.assertNull(p.getNote()); // Colonna note aggiunta
        Assert.assertEquals(100, p.getRemainingPercentage()); // Default v22

        Dispensa defaultDispensa = roomDb.dispensaDao().getDefaultDispensaSync();
        Assert.assertNotNull(defaultDispensa);
        Assert.assertEquals(1, defaultDispensa.getId());
        Assert.assertTrue(defaultDispensa.isDefault);

        // Verifica joined_pantry_configs
        JoinedPantryConfig config = roomDb.joinedPantryConfigDao().getConfigByDispensaId(1);
        Assert.assertNull(config); // tabella vuota ma interrogabile senza errori

        roomDb.close();
    }

    @Test
    public void testMigration20To21_fixesJoinedPantryConfigsAndPassesValidation() throws Exception {
        // 1. Prepara schema v20 (con joined_pantry_configs priva di DEFAULT 'webdav' come in 0.1.14)
        SupportSQLiteDatabase db = createDatabase(20);

        db.execSQL("CREATE TABLE IF NOT EXISTS `dispense` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT, " +
                "`is_default` INTEGER NOT NULL DEFAULT 0, " +
                "`last_modified` INTEGER NOT NULL DEFAULT 0, " +
                "`remote_id` TEXT, " +
                "`device_owner_id` TEXT, " +
                "`device_owner_name` TEXT)");
        db.execSQL("INSERT INTO dispense (id, name, is_default, last_modified) VALUES (1, 'Dispensa', 1, 1000)");

        db.execSQL("CREATE TABLE IF NOT EXISTS `products` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`dispensa_id` INTEGER NOT NULL DEFAULT 1, " +
                "`product_name` TEXT, " +
                "`barcode` TEXT, " +
                "`quantity` INTEGER NOT NULL, " +
                "`expiry_date` INTEGER, " +
                "`image_url` TEXT, " +
                "`storage_location` TEXT, " +
                "`opened_date` INTEGER NOT NULL DEFAULT 0, " +
                "`shelf_life_after_opening_days` INTEGER NOT NULL DEFAULT -1, " +
                "`note` TEXT, " +
                "`last_modified` INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_dispensa_id` ON `products` (`dispensa_id`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_storage_location` ON `products` (`storage_location`)");

        db.execSQL("CREATE TABLE IF NOT EXISTS `storage_locations` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`dispensa_id` INTEGER NOT NULL DEFAULT 1, " +
                "`name` TEXT, " +
                "`internal_key` TEXT, " +
                "`order_index` INTEGER NOT NULL, " +
                "`is_default` INTEGER NOT NULL DEFAULT 0, " +
                "`is_predefined` INTEGER NOT NULL DEFAULT 0, " +
                "`last_modified` INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_storage_locations_internal_key_dispensa_id` ON `storage_locations` (`internal_key`, `dispensa_id`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_storage_locations_order_index` ON `storage_locations` (`order_index`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_storage_locations_dispensa_id` ON `storage_locations` (`dispensa_id`)");

        db.execSQL("CREATE TABLE IF NOT EXISTS `shopping_items` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`dispensa_id` INTEGER NOT NULL DEFAULT 1, " +
                "`name` TEXT, " +
                "`quantity` INTEGER NOT NULL, " +
                "`checked` INTEGER NOT NULL, " +
                "`last_modified` INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shopping_items_dispensa_id` ON `shopping_items` (`dispensa_id`)");

        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_outbox` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`syncId` TEXT, " +
                "`dispensa_id` INTEGER NOT NULL DEFAULT 0, " +
                "`dataType` TEXT, " +
                "`payload` TEXT, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`isSynced` INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE IF NOT EXISTS `categories_definitions` (" +
                "`category_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`tag_name` TEXT, " +
                "`display_name_it` TEXT, " +
                "`language_code` TEXT, " +
                "`color_hex` TEXT)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_definitions_tag_name` ON `categories_definitions` (`tag_name`)");

        db.execSQL("CREATE TABLE IF NOT EXISTS `product_category_links` (" +
                "`product_id_fk` INTEGER NOT NULL, " +
                "`category_id_fk` INTEGER NOT NULL, " +
                "PRIMARY KEY(`product_id_fk`, `category_id_fk`), " +
                "FOREIGN KEY(`product_id_fk`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`category_id_fk`) REFERENCES `categories_definitions`(`category_id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_category_links_product_id_fk` ON `product_category_links` (`product_id_fk`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_category_links_category_id_fk` ON `product_category_links` (`category_id_fk`)");

        db.execSQL("CREATE TABLE IF NOT EXISTS `openfoodfact_cache` (" +
                "`barcode` TEXT NOT NULL, " +
                "`product_name` TEXT, " +
                "`image_local_path` TEXT, " +
                "`categories_tags` TEXT, " +
                "`timestamp_ms` INTEGER NOT NULL, " +
                "PRIMARY KEY(`barcode`))");

        // Creazione v20 difettosa (senza DEFAULT 'webdav' su provider_id)
        db.execSQL("CREATE TABLE IF NOT EXISTS `joined_pantry_configs` (" +
                "`dispensa_id` INTEGER NOT NULL, " +
                "`provider_id` TEXT NOT NULL, " +
                "`url` TEXT, `username` TEXT, `password` TEXT, `path` TEXT, " +
                "`is_shared` INTEGER NOT NULL, `pantry_key` TEXT, " +
                "`config_payload` TEXT, `remote_pantry_id` TEXT, " +
                "PRIMARY KEY(`dispensa_id`))");

        // Inserisci un record di prova in joined_pantry_configs
        db.execSQL("INSERT INTO joined_pantry_configs (dispensa_id, provider_id, url, is_shared) " +
                "VALUES (1, 'webdav', 'https://example.com/webdav', 1)");

        db.setVersion(20);
        db.close();

        // 2. Apri con Room fornendo MIGRATION_20_21 e MIGRATION_21_22
        AppDatabase roomDb = Room.databaseBuilder(context, AppDatabase.class, TEST_DB_NAME)
                .addMigrations(AppDatabase.MIGRATION_20_21, AppDatabase.MIGRATION_21_22)
                .allowMainThreadQueries()
                .build();

        // 3. Verifica che i dati siano stati preservati e la validazione sia superata
        JoinedPantryConfig config = roomDb.joinedPantryConfigDao().getConfigByDispensaId(1);
        Assert.assertNotNull(config);
        Assert.assertEquals("webdav", config.providerId);
        Assert.assertEquals("https://example.com/webdav", config.url);
        Assert.assertTrue(config.isShared);

        roomDb.close();
    }

    @Test
    public void testMigration21To22_addsRemainingPercentage() throws Exception {
        // 1. Crea DB a v21 con tabella products
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory()
                .create(SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name(TEST_DB_NAME)
                        .callback(new SupportSQLiteOpenHelper.Callback(21) {
                            @Override
                            public void onCreate(SupportSQLiteDatabase db) {}
                            @Override
                            public void onUpgrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {}
                        }).build());
        SupportSQLiteDatabase db = helper.getWritableDatabase();

        // Creazione schema essenziale v21
        db.execSQL("CREATE TABLE IF NOT EXISTS `products` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`dispensa_id` INTEGER NOT NULL DEFAULT 1, " +
                "`barcode` TEXT, `quantity` INTEGER NOT NULL, " +
                "`expiry_date` INTEGER, `product_name` TEXT, `image_url` TEXT, " +
                "`storage_location` TEXT, `opened_date` INTEGER NOT NULL DEFAULT 0, " +
                "`shelf_life_after_opening_days` INTEGER NOT NULL DEFAULT -1, " +
                "`note` TEXT, `last_modified` INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_storage_location` ON `products` (`storage_location`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_dispensa_id` ON `products` (`dispensa_id`)");

        db.execSQL("CREATE TABLE IF NOT EXISTS `dispense` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `is_default` INTEGER NOT NULL DEFAULT 0, `last_modified` INTEGER NOT NULL DEFAULT 0, `remote_id` TEXT, `device_owner_id` TEXT, `device_owner_name` TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `storage_locations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dispensa_id` INTEGER NOT NULL DEFAULT 1, `name` TEXT, `internal_key` TEXT, `order_index` INTEGER NOT NULL, `is_default` INTEGER NOT NULL DEFAULT 0, `is_predefined` INTEGER NOT NULL DEFAULT 0, `last_modified` INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_storage_locations_internal_key_dispensa_id` ON `storage_locations` (`internal_key`, `dispensa_id`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_storage_locations_dispensa_id` ON `storage_locations` (`dispensa_id`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_storage_locations_order_index` ON `storage_locations` (`order_index`)");

        db.execSQL("CREATE TABLE IF NOT EXISTS `shopping_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dispensa_id` INTEGER NOT NULL DEFAULT 1, `name` TEXT, `quantity` INTEGER NOT NULL, `checked` INTEGER NOT NULL, `last_modified` INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shopping_items_dispensa_id` ON `shopping_items` (`dispensa_id`)");

        db.execSQL("CREATE TABLE IF NOT EXISTS `categories_definitions` (`category_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tag_name` TEXT, `display_name_it` TEXT, `language_code` TEXT, `color_hex` TEXT)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_definitions_tag_name` ON `categories_definitions` (`tag_name`)");

        db.execSQL("CREATE TABLE IF NOT EXISTS `product_category_links` (`product_id_fk` INTEGER NOT NULL, `category_id_fk` INTEGER NOT NULL, PRIMARY KEY(`product_id_fk`, `category_id_fk`), FOREIGN KEY(`product_id_fk`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`category_id_fk`) REFERENCES `categories_definitions`(`category_id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_category_links_product_id_fk` ON `product_category_links` (`product_id_fk`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_category_links_category_id_fk` ON `product_category_links` (`category_id_fk`)");

        db.execSQL("CREATE TABLE IF NOT EXISTS `openfoodfact_cache` (`barcode` TEXT NOT NULL, `product_name` TEXT, `image_local_path` TEXT, `categories_tags` TEXT, `timestamp_ms` INTEGER NOT NULL, PRIMARY KEY(`barcode`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_outbox` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `syncId` TEXT, `dataType` TEXT, `payload` TEXT, `timestamp` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, `dispensa_id` INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `joined_pantry_configs` (`dispensa_id` INTEGER NOT NULL, `provider_id` TEXT NOT NULL DEFAULT 'webdav', `url` TEXT, `username` TEXT, `password` TEXT, `path` TEXT, `is_shared` INTEGER NOT NULL, `pantry_key` TEXT, `config_payload` TEXT, `remote_pantry_id` TEXT, PRIMARY KEY(`dispensa_id`))");

        db.execSQL("INSERT INTO products (id, product_name, barcode, quantity, storage_location) " +
                "VALUES (101, 'Farina 00', '8001234', 1, 'PANTRY')");

        db.setVersion(21);
        db.close();

        // 2. Apri con Room fornendo MIGRATION_21_22
        AppDatabase roomDb = Room.databaseBuilder(context, AppDatabase.class, TEST_DB_NAME)
                .addMigrations(AppDatabase.MIGRATION_21_22)
                .allowMainThreadQueries()
                .build();

        // 3. Verifica colonna e valore di default
        Product p = roomDb.productDao().getProductByIdSync(101);
        Assert.assertNotNull(p);
        Assert.assertEquals("Farina 00", p.getProductName());
        Assert.assertEquals(100, p.getRemainingPercentage());

        roomDb.close();
    }
}
