package eu.frigo.dispensa.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.media3.common.util.Log;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Product.class, CategoryDefinition.class,
        ProductCategoryLink.class, StorageLocation.class },
        version = 6, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract ProductDao productDao();
    public abstract CategoryDefinitionDao categoryDefinitionDao();
    public abstract ProductCategoryLinkDao productCategoryLinkDao();
    public abstract StorageLocationDao storageLocationDao();

    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `storage_locations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `internal_key` TEXT, `order_index` INTEGER NOT NULL, `is_default` INTEGER NOT NULL DEFAULT 0, `is_predefined` INTEGER NOT NULL DEFAULT 0)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_storage_locations_internal_key` ON `storage_locations` (`internal_key`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_storage_locations_order_index` ON `storage_locations` (`order_index`)");

            // Qui potresti anche voler popolare le location predefinite
            // se non lo fai nel RoomDatabase.Callback
        }
    };
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "dispensa_database")
                            .addMigrations(MIGRATION_5_6)
                            .addCallback(sRoomDatabaseCallback)
                            //.fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
    private static RoomDatabase.Callback sRoomDatabaseCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            // Questo viene chiamato solo la PRIMA volta che il DB viene creato.
            // Non viene chiamato dopo una migrazione se il DB esisteva già.
            Executors.newSingleThreadExecutor().execute(() -> {
                Log.d("AppDatabase", "Database onCreate - Prepopolamento StorageLocations");
                StorageLocationDao dao = INSTANCE.storageLocationDao();
                if (dao.countLocations() == 0) { // Controlla se è veramente vuoto
                    dao.insertAll(PredefinedData.getInitialStorageLocations());
                    Log.d("AppDatabase", "Prepopolamento StorageLocations completato.");
                }
            });
        }
        @Override
        public void onOpen(@NonNull SupportSQLiteDatabase db) {
            super.onOpen(db);
            // Questo viene chiamato ogni volta che il DB viene aperto.
            // Potresti usarlo per il prepopolamento se `onCreate` non è sufficiente
            // (es. se le location predefinite potessero essere cancellate e vuoi ricrearle)
            // Ma per ora, `onCreate` va bene per l'inizializzazione.
            // Se vuoi assicurarti che le predefinite esistano sempre, puoi fare un controllo qui:
            Executors.newSingleThreadExecutor().execute(() -> {
                Log.d("AppDatabase", "Database onOpen - Verifica/Aggiornamento StorageLocations predefinite");
                StorageLocationDao dao = INSTANCE.storageLocationDao();

                // Logica per inserire le predefinite se non esistono (più robusta):
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
}
