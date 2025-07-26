package eu.frigo.dispensa.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Product.class}, version = 2, exportSchema = false)
// entities = {Product.class, AnotherEntity.class, ...} se hai più entità
// version = 1 (incrementa quando modifichi lo schema, e fornisci una Migration)
// exportSchema = false (impostalo a true se vuoi esportare lo schema per il version control)
public abstract class AppDatabase extends RoomDatabase {

    public abstract ProductDao productDao(); // Metodo astratto per ottenere il DAO

    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS); // Per operazioni in background

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE products ADD COLUMN product_name TEXT");
            database.execSQL("ALTER TABLE products ADD COLUMN image_url TEXT");
        }
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "dispensa_database")
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
