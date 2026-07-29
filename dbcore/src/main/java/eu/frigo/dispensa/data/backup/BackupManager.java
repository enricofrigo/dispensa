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
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.dispensa.DispensaDao;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.product.ProductDao;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItem;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItemDao;
import eu.frigo.dispensa.data.storage.StorageLocation;
import eu.frigo.dispensa.data.storage.StorageLocationDao;

public class BackupManager {

    private final AppDatabase db;
    private final Gson gson;

    private final int MIN_APP_VERSION = 1;

    public BackupManager(Context context) {
        this.db = AppDatabase.getDatabase(context);
        this.gson = new GsonBuilder().setPrettyPrinting().
                create();
    }

    public void exportData(OutputStream outputStream, int appVersion, int dispensaId) throws Exception {
        ProductDao productDao = db.productDao();
        StorageLocationDao locationDao = db.storageLocationDao();
        CategoryDefinitionDao categoryDao = db.categoryDefinitionDao();
        ProductCategoryLinkDao linkDao = db.productCategoryLinkDao();
        ShoppingItemDao shoppingItemDao = db.shoppingItemDao();
        DispensaDao dispensaDao = db.dispensaDao();

        int version = db.getOpenHelper().getReadableDatabase().getVersion();
        Dispensa dispensa = dispensaDao.getDispensaByIdSync(dispensaId);
        List<Product> products = productDao.getAllProductsListStatic(dispensaId);
        List<StorageLocation> locations = locationDao.getAllLocationsSortedSync(dispensaId);
        List<CategoryDefinition> categories = categoryDao.getAllCategoryDefinitionsSync();
        List<ProductCategoryLink> links = linkDao.getAllProductCategoryLinksForDispensaSync(dispensaId);
        List<ShoppingItem> shoppingItems = shoppingItemDao.getAllItemsSync(dispensaId);

        try (OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {
            gson.toJson(new BackupData(version, appVersion, dispensa, products, locations, categories, links, shoppingItems), writer);
        }
    }

    private boolean isCompatible(int curVersion, int version){
        return version <= curVersion;
    }

    private void migrateDataIfNeeded(BackupData backupData, int currentVersion) {
        if (backupData.dbVersion < currentVersion) {
            long now = System.currentTimeMillis();
            
            // Migration for versions < 12 (added last_modified)
            if (backupData.dbVersion < 12) {
                if (backupData.products != null) {
                    for (Product p : backupData.products) {
                        if (p.lastModified == 0) p.lastModified = now;
                    }
                }
                if (backupData.locations != null) {
                    for (StorageLocation loc : backupData.locations) {
                        if (loc.lastModified == 0) loc.lastModified = now;
                    }
                }
                if (backupData.shoppingItems != null) {
                    for (ShoppingItem item : backupData.shoppingItems) {
                        if (item.lastModified == 0) item.lastModified = now;
                    }
                }
            }
            
            // Add more specific migrations here if versions are very old
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

        if(backupData.appVersion < MIN_APP_VERSION)
            throw new Exception("Import from app version (" + backupData.appVersion + ") is not supported.");

        int currentVersion = db.getOpenHelper().getReadableDatabase().getVersion();

        if (!isCompatible(currentVersion,backupData.dbVersion)) {
            throw new Exception("Backup version (" + backupData.dbVersion + ") is higher than database version ("
                    + currentVersion + "). Please update the app.");
        }

        migrateDataIfNeeded(backupData, currentVersion);

        db.runInTransaction(() -> {
            db.productDao().deleteAllProducts();
            db.productCategoryLinkDao().deleteAllProductCategoryLink();
            db.categoryDefinitionDao().deleteAllCategoryDefinitions();
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
            if (backupData.shoppingItems != null)
                db.shoppingItemDao().insertAll(backupData.shoppingItems);
        });
    }
}