package eu.frigo.dispensa.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.frigo.dispensa.data.category.CategoryDefinition;
import eu.frigo.dispensa.data.category.CategoryDefinitionDao;
import eu.frigo.dispensa.data.category.ProductCategoryLink;
import eu.frigo.dispensa.data.category.ProductCategoryLinkDao;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.product.ProductDao;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItem;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItemDao;
import eu.frigo.dispensa.data.storage.PredefinedData;
import eu.frigo.dispensa.data.storage.StorageLocation;
import eu.frigo.dispensa.data.storage.StorageLocationDao;

import eu.frigo.dispensa.data.sync.SyncOutbox;
import eu.frigo.dispensa.data.sync.SyncOutboxDao;

import eu.frigo.dispensa.data.openfoodfacts.OpenFoodFactCacheDao;
import eu.frigo.dispensa.data.openfoodfacts.OpenFoodFactCacheEntity;

@Database(entities = {Product.class, CategoryDefinition.class,
        ProductCategoryLink.class, StorageLocation.class, OpenFoodFactCacheEntity.class,
        ShoppingItem.class, SyncOutbox.class },
        version = 13)
public abstract class AppDatabase extends RoomDatabase {

    public abstract ProductDao productDao();
    public abstract CategoryDefinitionDao categoryDefinitionDao();
    public abstract ProductCategoryLinkDao productCategoryLinkDao();
    public abstract StorageLocationDao storageLocationDao();
    public abstract OpenFoodFactCacheDao openFoodFactCacheDao();
    public abstract ShoppingItemDao shoppingItemDao();
    public abstract SyncOutboxDao syncOutboxDao();

    public static void createSyncTablesAndTriggers(SupportSQLiteDatabase database) {
        database.execSQL("CREATE TABLE IF NOT EXISTS sync_changes (" +
                "tbl TEXT NOT NULL, " +
                "pk_val TEXT NOT NULL, " +
                "op TEXT NOT NULL, " +
                "row_json TEXT, " +
                "clock INTEGER NOT NULL, " +
                "PRIMARY KEY (tbl, pk_val));");

        database.execSQL("CREATE TABLE IF NOT EXISTS sync_import_lock (" +
                "locked INTEGER DEFAULT 0);");

        database.execSQL("INSERT INTO sync_import_lock (locked) " +
                "SELECT 0 WHERE NOT EXISTS (SELECT 1 FROM sync_import_lock);");

        // Triggers for products
        database.execSQL("CREATE TRIGGER IF NOT EXISTS sync_products_insert AFTER INSERT ON products FOR EACH ROW " +
                "WHEN (SELECT locked FROM sync_import_lock LIMIT 1) = 0 BEGIN " +
                "INSERT OR REPLACE INTO sync_changes (tbl, pk_val, op, row_json, clock) VALUES " +
                "('products', CAST(NEW.id AS TEXT), 'UPSERT', json_object('id', NEW.id, 'barcode', NEW.barcode, " +
                "'quantity', NEW.quantity, 'expiry_date', NEW.expiry_date, 'product_name', NEW.product_name, " +
                "'image_url', NEW.image_url, 'storage_location', NEW.storage_location, 'opened_date', NEW.opened_date, " +
                "'shelf_life_after_opening_days', NEW.shelf_life_after_opening_days), " +
                "(SELECT COALESCE(MAX(clock),0)+1 FROM sync_changes WHERE tbl='products')); END;");

        database.execSQL("CREATE TRIGGER IF NOT EXISTS sync_products_update AFTER UPDATE ON products FOR EACH ROW " +
                "WHEN (SELECT locked FROM sync_import_lock LIMIT 1) = 0 BEGIN " +
                "INSERT OR REPLACE INTO sync_changes (tbl, pk_val, op, row_json, clock) VALUES " +
                "('products', CAST(NEW.id AS TEXT), 'UPSERT', json_object('id', NEW.id, 'barcode', NEW.barcode, " +
                "'quantity', NEW.quantity, 'expiry_date', NEW.expiry_date, 'product_name', NEW.product_name, " +
                "'image_url', NEW.image_url, 'storage_location', NEW.storage_location, 'opened_date', NEW.opened_date, " +
                "'shelf_life_after_opening_days', NEW.shelf_life_after_opening_days), " +
                "(SELECT COALESCE(MAX(clock),0)+1 FROM sync_changes WHERE tbl='products')); END;");

        database.execSQL("CREATE TRIGGER IF NOT EXISTS sync_products_delete AFTER DELETE ON products FOR EACH ROW " +
                "WHEN (SELECT locked FROM sync_import_lock LIMIT 1) = 0 BEGIN " +
                "INSERT OR REPLACE INTO sync_changes (tbl, pk_val, op, row_json, clock) VALUES " +
                "('products', CAST(OLD.id AS TEXT), 'DELETE', NULL, " +
                "(SELECT COALESCE(MAX(clock),0)+1 FROM sync_changes WHERE tbl='products')); END;");

        // Triggers for categories_definitions
        database.execSQL("CREATE TRIGGER IF NOT EXISTS sync_categories_insert AFTER INSERT ON categories_definitions FOR EACH ROW " +
                "WHEN (SELECT locked FROM sync_import_lock LIMIT 1) = 0 BEGIN " +
                "INSERT OR REPLACE INTO sync_changes (tbl, pk_val, op, row_json, clock) VALUES " +
                "('categories_definitions', CAST(NEW.category_id AS TEXT), 'UPSERT', json_object('category_id', NEW.category_id, " +
                "'tag_name', NEW.tag_name, 'display_name_it', NEW.display_name_it, 'language_code', NEW.language_code, 'color_hex', NEW.color_hex), " +
                "(SELECT COALESCE(MAX(clock),0)+1 FROM sync_changes WHERE tbl='categories_definitions')); END;");

        database.execSQL("CREATE TRIGGER IF NOT EXISTS sync_categories_update AFTER UPDATE ON categories_definitions FOR EACH ROW " +
                "WHEN (SELECT locked FROM sync_import_lock LIMIT 1) = 0 BEGIN " +
                "INSERT OR REPLACE INTO sync_changes (tbl, pk_val, op, row_json, clock) VALUES " +
                "('categories_definitions', CAST(NEW.category_id AS TEXT), 'UPSERT', json_object('category_id', NEW.category_id, " +
                "'tag_name', NEW.tag_name, 'display_name_it', NEW.display_name_it, 'language_code', NEW.language_code, 'color_hex', NEW.color_hex), " +
                "(SELECT COALESCE(MAX(clock),0)+1 FROM sync_changes WHERE tbl='categories_definitions')); END;");

        database.execSQL("CREATE TRIGGER IF NOT EXISTS sync_categories_delete AFTER DELETE ON categories_definitions FOR EACH ROW " +
                "WHEN (SELECT locked FROM sync_import_lock LIMIT 1) = 0 BEGIN " +
                "INSERT OR REPLACE INTO sync_changes (tbl, pk_val, op, row_json, clock) VALUES " +
                "('categories_definitions', CAST(OLD.category_id AS TEXT), 'DELETE', NULL, " +
                "(SELECT COALESCE(MAX(clock),0)+1 FROM sync_changes WHERE tbl='categories_definitions')); END;");

        // Triggers for product_category_links
        database.execSQL("CREATE TRIGGER IF NOT EXISTS sync_links_insert AFTER INSERT ON product_category_links FOR EACH ROW " +
                "WHEN (SELECT locked FROM sync_import_lock LIMIT 1) = 0 BEGIN " +
                "INSERT OR REPLACE INTO sync_changes (tbl, pk_val, op, row_json, clock) VALUES " +
                "('product_category_links', CAST(NEW.product_id_fk AS TEXT) || '|' || CAST(NEW.category_id_fk AS TEXT), 'UPSERT', " +
                "json_object('product_id_fk', NEW.product_id_fk, 'category_id_fk', NEW.category_id_fk), " +
                "(SELECT COALESCE(MAX(clock),0)+1 FROM sync_changes WHERE tbl='product_category_links')); END;");

        database.execSQL("CREATE TRIGGER IF NOT EXISTS sync_links_update AFTER UPDATE ON product_category_links FOR EACH ROW " +
                "WHEN (SELECT locked FROM sync_import_lock LIMIT 1) = 0 BEGIN " +
                "INSERT OR REPLACE INTO sync_changes (tbl, pk_val, op, row_json, clock) VALUES " +
                "('product_category_links', CAST(NEW.product_id_fk AS TEXT) || '|' || CAST(NEW.category_id_fk AS TEXT), 'UPSERT', " +
                "json_object('product_id_fk', NEW.product_id_fk, 'category_id_fk', NEW.category_id_fk), " +
                "(SELECT COALESCE(MAX(clock),0)+1 FROM sync_changes WHERE tbl='product_category_links')); END;");

        database.execSQL("CREATE TRIGGER IF NOT EXISTS sync_links_delete AFTER DELETE ON product_category_links FOR EACH ROW " +
                "WHEN (SELECT locked FROM sync_import_lock LIMIT 1) = 0 BEGIN " +
                "INSERT OR REPLACE INTO sync_changes (tbl, pk_val, op, row_json, clock) VALUES " +
                "('product_category_links', CAST(OLD.product_id_fk AS TEXT) || '|' || CAST(OLD.category_id_fk AS TEXT), 'DELETE', NULL, " +
                "(SELECT COALESCE(MAX(clock),0)+1 FROM sync_changes WHERE tbl='product_category_links')); END;");

        // Triggers for storage_locations
        database.execSQL("CREATE TRIGGER IF NOT EXISTS sync_locations_insert AFTER INSERT ON storage_locations FOR EACH ROW " +
                "WHEN (SELECT locked FROM sync_import_lock LIMIT 1) = 0 BEGIN " +
                "INSERT OR REPLACE INTO sync_changes (tbl, pk_val, op, row_json, clock) VALUES " +
                "('storage_locations', CAST(NEW.id AS TEXT), 'UPSERT', json_object('id', NEW.id, 'name', NEW.name, " +
                "'internal_key', NEW.internal_key, 'order_index', NEW.order_index, 'is_default', NEW.is_default, " +
                "'is_predefined', NEW.is_predefined), " +
                "(SELECT COALESCE(MAX(clock),0)+1 FROM sync_changes WHERE tbl='storage_locations')); END;");

        database.execSQL("CREATE TRIGGER IF NOT EXISTS sync_locations_update AFTER UPDATE ON storage_locations FOR EACH ROW " +
                "WHEN (SELECT locked FROM sync_import_lock LIMIT 1) = 0 BEGIN " +
                "INSERT OR REPLACE INTO sync_changes (tbl, pk_val, op, row_json, clock) VALUES " +
                "('storage_locations', CAST(NEW.id AS TEXT), 'UPSERT', json_object('id', NEW.id, 'name', NEW.name, " +
                "'internal_key', NEW.internal_key, 'order_index', NEW.order_index, 'is_default', NEW.is_default, " +
                "'is_predefined', NEW.is_predefined), " +
                "(SELECT COALESCE(MAX(clock),0)+1 FROM sync_changes WHERE tbl='storage_locations')); END;");

        database.execSQL("CREATE TRIGGER IF NOT EXISTS sync_locations_delete AFTER DELETE ON storage_locations FOR EACH ROW " +
                "WHEN (SELECT locked FROM sync_import_lock LIMIT 1) = 0 BEGIN " +
                "INSERT OR REPLACE INTO sync_changes (tbl, pk_val, op, row_json, clock) VALUES " +
                "('storage_locations', CAST(OLD.id AS TEXT), 'DELETE', NULL, " +
                "(SELECT COALESCE(MAX(clock),0)+1 FROM sync_changes WHERE tbl='storage_locations')); END;");
    }

    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            createSyncTablesAndTriggers(database);
        }
    };

    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `sync_outbox` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `syncId` TEXT, `dataType` TEXT, `payload` TEXT, `timestamp` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL)");
        }
    };

    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE products ADD COLUMN last_modified INTEGER DEFAULT 0 NOT NULL");
            database.execSQL("ALTER TABLE storage_locations ADD COLUMN last_modified INTEGER DEFAULT 0 NOT NULL");
            database.execSQL("ALTER TABLE shopping_items ADD COLUMN last_modified INTEGER DEFAULT 0 NOT NULL");
        }
    };

    static final Migration MIGRATION_6_7 = new Migration(6,7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE products ADD COLUMN opened_date INTEGER DEFAULT 0 NOT NULL");
            database.execSQL("ALTER TABLE products ADD COLUMN shelf_life_after_opening_days INTEGER DEFAULT -1 NOT NULL");
        }
    };

    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE INDEX IF NOT EXISTS index_products_location_internal_key ON products(storage_location)");
        }
    };

    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `openfoodfact_cache` (`barcode` TEXT NOT NULL, `product_name` TEXT, `image_local_path` TEXT, `categories_tags` TEXT, `timestamp_ms` INTEGER NOT NULL, PRIMARY KEY(`barcode`))");
        }
    };

    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `shopping_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `quantity` INTEGER NOT NULL, `checked` INTEGER NOT NULL)");
            createSyncTablesAndTriggers(database);
        }
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                RoomDatabase.Callback sRoomDatabaseCallback = new RoomDatabase.Callback() {
                    @UnstableApi
                    @Override
                    public void onCreate(@NonNull SupportSQLiteDatabase db) {
                        super.onCreate(db);
                        createSyncTablesAndTriggers(db);
                        Executors.newSingleThreadExecutor().execute(() -> {
                            Log.d("AppDatabase", "Database onCreate - Prepopolamento StorageLocations");
                            StorageLocationDao dao = INSTANCE.storageLocationDao();
                            if (dao.countLocations() == 0) { // Controlla se è veramente vuoto
                                dao.insertAll(PredefinedData.getInitialStorageLocations());
                                Log.d("AppDatabase", "Prepopolamento StorageLocations completato.");
                            }
                        });
                    }

                    @UnstableApi
                    @Override
                    public void onOpen(@NonNull SupportSQLiteDatabase db) {
                        super.onOpen(db);
                        createSyncTablesAndTriggers(db);
                        Executors.newSingleThreadExecutor().execute(() -> {
                            Log.d("AppDatabase", "Database onOpen - Verifica/Aggiornamento StorageLocations predefinite");
                            StorageLocationDao dao = INSTANCE.storageLocationDao();
                            List<StorageLocation> predefined = PredefinedData.getInitialStorageLocations();
                            for (StorageLocation loc : predefined) {
                                StorageLocation existing = dao.getLocationByInternalKeySync(loc.internalKey);
                                if (existing == null) {
                                    dao.insert(loc);
                                    Log.d("AppDatabase", "Inserita location predefinita mancante: " + loc.name);
                                }
                            }
                        });
                    }
                };

                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "dispensa_database")
                            .addMigrations(MIGRATION_6_7)
                            .addMigrations(MIGRATION_7_8)
                            .addMigrations(MIGRATION_8_9)
                            .addMigrations(MIGRATION_9_10)
                            .addMigrations(MIGRATION_10_11)
                            .addMigrations(MIGRATION_11_12)
                            .addMigrations(MIGRATION_12_13)
                            .addCallback(sRoomDatabaseCallback)
                            //.fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}