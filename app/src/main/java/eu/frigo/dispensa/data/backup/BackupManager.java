package eu.frigo.dispensa.data.backup;

import android.content.Context;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.category.CategoryDefinition;
import eu.frigo.dispensa.data.category.CategoryDefinitionDao;
import eu.frigo.dispensa.data.category.ProductCategoryLink;
import eu.frigo.dispensa.data.category.ProductCategoryLinkDao;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.product.ProductDao;
import eu.frigo.dispensa.data.storage.StorageLocation;
import eu.frigo.dispensa.data.storage.StorageLocationDao;

public class BackupManager {

    private final AppDatabase db;
    private final Gson gson;

    public BackupManager(Context context) {
        this.db = AppDatabase.getDatabase(context);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void exportData(OutputStream outputStream) throws Exception {
        ProductDao productDao = db.productDao();
        StorageLocationDao locationDao = db.storageLocationDao();
        CategoryDefinitionDao categoryDao = db.categoryDefinitionDao();
        ProductCategoryLinkDao linkDao = db.productCategoryLinkDao();

        int version = db.getOpenHelper().getReadableDatabase().getVersion();
        List<Product> products = productDao.getAllProductsListStatic();
        List<StorageLocation> locations = locationDao.getAllLocationsSortedSync();
        List<CategoryDefinition> categories = categoryDao.getAllCategoryDefinitionsSync();
        List<ProductCategoryLink> links = linkDao.getAllProductCategoryLinksSync();

        BackupData backupData = new BackupData(version, products, locations, categories, links);

        try (OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {
            gson.toJson(backupData, writer);
        }
    }

    public void importData(InputStream inputStream) throws Exception {
        BackupData backupData;
        try (InputStreamReader reader = new InputStreamReader(inputStream)) {
            backupData = gson.fromJson(reader, BackupData.class);
        }

        if (backupData == null) {
            throw new Exception("Invalid backup file");
        }

        // Basic validation
        if (backupData.dbVersion <= 0) {
            throw new Exception("Invalid database version in backup file");
        }

        // Room handles migrations, but let's check if the backup version is newer than
        // ours
        int currentVersion = db.getOpenHelper().getReadableDatabase().getVersion();
        if (backupData.dbVersion > currentVersion) {
            throw new Exception("Backup version (" + backupData.dbVersion + ") is newer than app database version ("
                    + currentVersion + ")");
        }

        db.runInTransaction(() -> {
            db.productDao().deleteAllProducts();
            db.productCategoryLinkDao().deleteAllProductCategoryLink();
            db.categoryDefinitionDao().deleteAllCategoryDefinitions();
            // We don't delete predefined locations, we replace/update them
            // but for a full restore, let's clear them all if possible (StorageLocationDao
            // has no deleteAll but we can clear and insertAll)
            SupportSQLiteDatabase sdb = db.getOpenHelper().getWritableDatabase();
            sdb.execSQL("DELETE FROM storage_locations");

            if (backupData.locations != null)
                db.storageLocationDao().insertAll(backupData.locations);
            if (backupData.categories != null)
                db.categoryDefinitionDao().insertAll(backupData.categories);
            if (backupData.products != null)
                db.productDao().insertAll(backupData.products);
            if (backupData.categoryLinks != null)
                db.productCategoryLinkDao().insertAll(backupData.categoryLinks);
        });
    }
}
