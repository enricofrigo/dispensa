package eu.frigo.dispensa.data.backup;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import eu.frigo.dispensa.data.sync.SyncOutbox;
import eu.frigo.dispensa.sync.core.event.SyncBus;

public class BackupManager {

    private final AppDatabase db;
    private final Gson gson;

    private final int MIN_APP_VERSION = 1;

    public BackupManager(Context context) {
        this(AppDatabase.getDatabase(context));
    }

    public BackupManager(AppDatabase db) {
        this.db = db;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
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
        List<Product> rawProducts = productDao.getAllProductsListStatic(dispensaId);
        List<Product> products = new java.util.ArrayList<>();
        if (rawProducts != null) {
            for (Product p : rawProducts) {
                products.add(p.createExportCopy());
            }
        }
        List<StorageLocation> locations = locationDao.getAllLocationsSortedSync(dispensaId);
        List<CategoryDefinition> categories = categoryDao.getAllCategoryDefinitionsSync();
        List<ProductCategoryLink> links = linkDao.getAllProductCategoryLinksForDispensaSync(dispensaId);
        List<ShoppingItem> shoppingItems = shoppingItemDao.getAllItemsSync(dispensaId);

        try (OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {
            gson.toJson(new BackupData(version, appVersion, dispensa, products, locations, categories, links, shoppingItems), writer);
        }
    }

    private boolean isCompatible(int curVersion, int version) {
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
        }
    }

    public BackupData peekBackupData(InputStream inputStream) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(inputStream)) {
            return gson.fromJson(reader, BackupData.class);
        }
    }

    public void importData(InputStream inputStream, int targetDispensaId) throws Exception {
        BackupData backupData;
        try (InputStreamReader reader = new InputStreamReader(inputStream)) {
            backupData = gson.fromJson(reader, BackupData.class);
        }

        if (backupData == null) {
            throw new Exception("Invalid backup file");
        }

        if (backupData.appVersion < MIN_APP_VERSION)
            throw new Exception("Import from app version (" + backupData.appVersion + ") is not supported.");

        int currentVersion = db.getOpenHelper().getReadableDatabase().getVersion();

        if (!isCompatible(currentVersion, backupData.dbVersion)) {
            throw new Exception("Backup version (" + backupData.dbVersion + ") is higher than database version ("
                    + currentVersion + "). Please update the app.");
        }

        migrateDataIfNeeded(backupData, currentVersion);

        db.runInTransaction(() -> {
            // Delete existing data for the target pantry
            db.productCategoryLinkDao().deleteByDispensaId(targetDispensaId);
            db.productDao().deleteAllProducts(targetDispensaId);
            db.storageLocationDao().deleteAllLocations(targetDispensaId);
            db.shoppingItemDao().deleteAllItems(targetDispensaId);
            db.syncOutboxDao().deleteAllByDispensaId(targetDispensaId);

            // 1. Import categories (global) and build oldCategoryId -> newCategoryId map
            Map<Integer, Integer> oldToNewCategoryId = new HashMap<>();
            if (backupData.categories != null) {
                for (CategoryDefinition cat : backupData.categories) {
                    int oldCatId = cat.categoryId;
                    CategoryDefinition existing = (cat.tagName != null)
                            ? db.categoryDefinitionDao().getCategoryByTagName(cat.tagName)
                            : null;
                    int effectiveCatId;
                    if (existing != null) {
                        effectiveCatId = existing.categoryId;
                    } else {
                        cat.categoryId = 0;
                        effectiveCatId = (int) db.categoryDefinitionDao().insert(cat);
                    }
                    if (oldCatId > 0) {
                        oldToNewCategoryId.put(oldCatId, effectiveCatId);
                    }
                }
            }

            // 2. Import locations
            if (backupData.locations != null) {
                for (StorageLocation loc : backupData.locations) {
                    loc.id = 0; // Force auto-generation
                    loc.dispensaId = targetDispensaId;
                    db.storageLocationDao().insert(loc);
                    enqueueSyncOutbox(targetDispensaId, "UPSERT_LOCATION", loc, loc.lastModified);
                }
            }

            // 3. Import products and build oldProductId -> newProductId map (without mutating categoryLinks in-place!)
            Map<Integer, Integer> oldToNewProductId = new HashMap<>();
            if (backupData.products != null) {
                for (Product p : backupData.products) {
                    int oldProductId = p.id;
                    p.id = 0; // Force auto-generation
                    p.dispensaId = targetDispensaId;
                    if (p.syncUuid == null || p.syncUuid.trim().isEmpty()) {
                        p.syncUuid = UUID.randomUUID().toString();
                    }
                    if (p.hasCustomLocalImage()) {
                        p.setImageUrl(null);
                    }
                    p.validateImageUrlExistence();

                    long newProductId = db.productDao().insert(p);
                    p.id = (int) newProductId;
                    oldToNewProductId.put(oldProductId, (int) newProductId);
                }
            }

            // 4. Second pass: re-link categories using the immutable oldToNewProductId & oldToNewCategoryId maps
            if (backupData.categoryLinks != null) {
                for (ProductCategoryLink link : backupData.categoryLinks) {
                    Integer mappedProductId = oldToNewProductId.get(link.productIdFk);
                    if (mappedProductId != null) {
                        int mappedCategoryId = oldToNewCategoryId.getOrDefault(link.categoryIdFk, link.categoryIdFk);
                        db.productCategoryLinkDao().insert(new ProductCategoryLink(mappedProductId, mappedCategoryId));
                    }
                }
            }

            // Enqueue outbox events for imported products (with their category tags)
            if (backupData.products != null) {
                for (Product p : backupData.products) {
                    enqueueSyncOutbox(targetDispensaId, "UPSERT_PRODUCT", p.createExportCopy(), p.lastModified);
                }
            }

            // 5. Import shopping items
            if (backupData.shoppingItems != null) {
                for (ShoppingItem item : backupData.shoppingItems) {
                    item.id = 0; // Force auto-generation
                    item.dispensaId = targetDispensaId;
                    if (item.syncUuid == null || item.syncUuid.trim().isEmpty()) {
                        item.syncUuid = UUID.randomUUID().toString();
                    }
                    db.shoppingItemDao().insert(item);
                    enqueueSyncOutbox(targetDispensaId, "UPSERT_SHOPPING_ITEM", item, item.lastModified);
                }
            }
        });

        SyncBus.getInstance().post(new SyncBus.LocalChangeDetected());
    }

    private void enqueueSyncOutbox(int dispensaId, String dataType, Object payload, long ts) {
        SyncOutbox entry = new SyncOutbox();
        entry.syncId = UUID.randomUUID().toString();
        entry.dispensaId = dispensaId;
        entry.dataType = dataType;
        entry.payload = gson.toJson(payload);
        entry.timestamp = ts > 0 ? ts : System.currentTimeMillis();
        db.syncOutboxDao().insert(entry);
    }
}
