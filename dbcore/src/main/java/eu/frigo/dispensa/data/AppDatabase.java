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

import eu.frigo.dispensa.data.backup.PreMigrationBackupHelper;
import eu.frigo.dispensa.data.category.CategoryDefinition;
import eu.frigo.dispensa.data.category.CategoryDefinitionDao;
import eu.frigo.dispensa.data.category.ProductCategoryLink;
import eu.frigo.dispensa.data.category.ProductCategoryLinkDao;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.dispensa.DispensaDao;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.product.ProductDao;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItem;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItemDao;
import eu.frigo.dispensa.data.storage.PredefinedData;
import eu.frigo.dispensa.data.storage.StorageLocation;
import eu.frigo.dispensa.data.storage.StorageLocationDao;

import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.data.sync.JoinedPantryConfigDao;
import eu.frigo.dispensa.data.sync.SyncOutbox;
import eu.frigo.dispensa.data.sync.SyncOutboxDao;

import eu.frigo.dispensa.data.openfoodfacts.OpenFoodFactCacheDao;
import eu.frigo.dispensa.data.openfoodfacts.OpenFoodFactCacheEntity;
import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;

@Database(entities = {Product.class, CategoryDefinition.class,
        ProductCategoryLink.class, StorageLocation.class, OpenFoodFactCacheEntity.class,
        ShoppingItem.class, SyncOutbox.class, Dispensa.class, JoinedPantryConfig.class },
        version = 18)
public abstract class AppDatabase extends RoomDatabase {

    public abstract ProductDao productDao();
    public abstract CategoryDefinitionDao categoryDefinitionDao();
    public abstract ProductCategoryLinkDao productCategoryLinkDao();
    public abstract StorageLocationDao storageLocationDao();
    public abstract OpenFoodFactCacheDao openFoodFactCacheDao();
    public abstract ShoppingItemDao shoppingItemDao();
    public abstract SyncOutboxDao syncOutboxDao();
    public abstract DispensaDao dispensaDao();
    public abstract JoinedPantryConfigDao joinedPantryConfigDao();

    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

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

    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            long now = System.currentTimeMillis();
            // 1. Crea tabella dispense
            database.execSQL("CREATE TABLE IF NOT EXISTS `dispense` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `is_default` INTEGER NOT NULL DEFAULT 0, `last_modified` INTEGER NOT NULL DEFAULT 0, `remote_id` TEXT)");
            
            // 2. Inserisci la dispensa di default iniziale
            database.execSQL("INSERT INTO dispense (id, name, is_default, last_modified) VALUES (1, 'Dispensa', 1, " + now + ")");
            
            // 3. Aggiungi dispensa_id alle tabelle esistenti con default 1
            database.execSQL("ALTER TABLE products ADD COLUMN dispensa_id INTEGER DEFAULT 1 NOT NULL");
            database.execSQL("ALTER TABLE storage_locations ADD COLUMN dispensa_id INTEGER DEFAULT 1 NOT NULL");
            database.execSQL("ALTER TABLE shopping_items ADD COLUMN dispensa_id INTEGER DEFAULT 1 NOT NULL");

            // 4. Crea indici
            database.execSQL("CREATE INDEX IF NOT EXISTS index_products_dispensa_id ON products(dispensa_id)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_storage_locations_dispensa_id ON storage_locations(dispensa_id)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_shopping_items_dispensa_id ON shopping_items(dispensa_id)");

            // 5. Aggiorna indice univoco di storage_locations
            database.execSQL("DROP INDEX IF EXISTS index_storage_locations_internal_key");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_storage_locations_internal_key_dispensa_id ON storage_locations(internal_key, dispensa_id)");
        }
    };

    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE sync_outbox ADD COLUMN dispensa_id INTEGER DEFAULT 0 NOT NULL");
        }
    };

    static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Fix index name mismatch: Room expects index_products_storage_location
            // but MIGRATION_7_8 created index_products_location_internal_key
            database.execSQL("DROP INDEX IF EXISTS index_products_location_internal_key");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_products_storage_location ON products(storage_location)");
        }
    };

    static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE dispense ADD COLUMN device_owner_id TEXT");
        }
    };

    static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE dispense ADD COLUMN device_owner_name TEXT");
        }
    };

    static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `joined_pantry_configs` (`dispensa_id` INTEGER NOT NULL, `provider_id` TEXT NOT NULL, `url` TEXT, `username` TEXT, `password` TEXT, `path` TEXT, `is_shared` INTEGER NOT NULL, `pantry_key` TEXT, PRIMARY KEY(`dispensa_id`))");
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
            database.execSQL("CREATE INDEX IF NOT EXISTS index_products_storage_location ON products(storage_location)");
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
        }
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    // Esegui backup preventivo se è prevista una migrazione
                    PreMigrationBackupHelper.checkAndBackup(context.getApplicationContext(), "dispensa_database", 17);

                    RoomDatabase.Callback sRoomDatabaseCallback = new RoomDatabase.Callback() {
                        @UnstableApi
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            super.onCreate(db);
                            Executors.newSingleThreadExecutor().execute(() -> {
                                Log.d("AppDatabase", "Database onCreate - Prepopolamento Dispensa e StorageLocations");
                                AppDatabase database = INSTANCE;
                                if (database == null) return;

                                DispensaDao dispensaDao = database.dispensaDao();
                                StorageLocationDao storageDao = database.storageLocationDao();

                                if (dispensaDao.countDispense() == 0) {
                                    Dispensa defaultDispensa = new Dispensa("Dispensa", true);
                                    defaultDispensa.deviceOwnerId = InstallationIdProvider.getOrCreateInstallationId(context);
                                    long dispensaId = dispensaDao.insert(defaultDispensa);

                                    if (storageDao.countAllLocations() == 0) {
                                        storageDao.insertAll(PredefinedData.getInitialStorageLocations((int) dispensaId));
                                        Log.d("AppDatabase", "Prepopolamento Dispensa e StorageLocations completato.");
                                    }
                                }
                            });
                        }

                        @UnstableApi
                        @Override
                        public void onOpen(@NonNull SupportSQLiteDatabase db) {
                            super.onOpen(db);
                            // Rimosso prepopolamento ridondante che causava doppia inizializzazione
                        }
                    };

                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "dispensa_database")
                            .addMigrations(MIGRATION_6_7)
                            .addMigrations(MIGRATION_7_8)
                            .addMigrations(MIGRATION_8_9)
                            .addMigrations(MIGRATION_9_10)
                            .addMigrations(MIGRATION_10_11)
                            .addMigrations(MIGRATION_11_12)
                            .addMigrations(MIGRATION_12_13)
                            .addMigrations(MIGRATION_13_14)
                            .addMigrations(MIGRATION_14_15)
                            .addMigrations(MIGRATION_15_16)
                            .addMigrations(MIGRATION_16_17)
                            .addMigrations(MIGRATION_17_18)
                            .addCallback(sRoomDatabaseCallback)
                            //.fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
