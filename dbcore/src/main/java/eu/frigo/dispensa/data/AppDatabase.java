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

import eu.frigo.dispensa.data.openfoodfacts.OpenFoodFactCacheDao;
import eu.frigo.dispensa.data.openfoodfacts.OpenFoodFactCacheEntity;

@Database(entities = {Product.class, CategoryDefinition.class,
        ProductCategoryLink.class, StorageLocation.class, OpenFoodFactCacheEntity.class,
        ShoppingItem.class },
        version = 10)
public abstract class AppDatabase extends RoomDatabase {

    public abstract ProductDao productDao();
    public abstract CategoryDefinitionDao categoryDefinitionDao();
    public abstract ProductCategoryLinkDao productCategoryLinkDao();
    public abstract StorageLocationDao storageLocationDao();
    public abstract OpenFoodFactCacheDao openFoodFactCacheDao();
    public abstract ShoppingItemDao shoppingItemDao();

    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

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
                            .addCallback(sRoomDatabaseCallback)
                            //.fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}