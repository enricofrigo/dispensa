package eu.frigo.dispensa.screenshots;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.activity.AddProductActivity;
import eu.frigo.dispensa.activity.ConsumeScannerActivity;
import eu.frigo.dispensa.activity.DispensaManagerActivity;
import eu.frigo.dispensa.activity.MainActivity;
import eu.frigo.dispensa.activity.ManageDevicesActivity;
import eu.frigo.dispensa.activity.SettingsActivity;
import eu.frigo.dispensa.activity.SharedDispenseActivity;
import eu.frigo.dispensa.activity.ShoppingListActivity;
import eu.frigo.dispensa.activity.SyncLocalSafConfigActivity;
import eu.frigo.dispensa.activity.SyncOnboardingActivity;
import eu.frigo.dispensa.activity.SyncWebDavConfigActivity;
import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.Repository;
import eu.frigo.dispensa.data.category.CategoryDefinition;
import eu.frigo.dispensa.data.category.ProductCategoryLink;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItem;
import eu.frigo.dispensa.data.storage.PredefinedData;
import eu.frigo.dispensa.data.storage.StorageLocation;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.sync.QrCodeGenerator;
import eu.frigo.dispensa.sync.SyncFlavorInitializer;
import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.model.PantryDevice;
import eu.frigo.dispensa.sync.local.LocalSafSyncProviderLoader;
import eu.frigo.dispensa.ui.ProductListFragment;
import eu.frigo.dispensa.ui.SettingsFragment;
import eu.frigo.dispensa.util.LocaleHelper;
import eu.frigo.dispensa.util.ThemeHelper;

/**
 * Generates localized Fastlane phone screenshots (it-IT and en-US) in both Light and Dark themes
 * for ALL defined Activities with realistic fake data on the JVM using Robolectric Native Graphics.
 *
 * Run with:
 * JAVA_HOME=/Users/alicevangelista/Library/Java/JavaVirtualMachines/openjdk-23.0.1/Contents/Home \
 *   ./gradlew :app:testFdroidDebugUnitTest --tests "eu.frigo.dispensa.screenshots.FastlaneScreenshotsTest" -Pfastlane.screenshots=true
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(application = Application.class, sdk = 34)
public class FastlaneScreenshotsTest {

    private static final int SCREEN_WIDTH_PX = 1080;
    private static final int SCREEN_HEIGHT_PX = 2340;

    private AppDatabase db;

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue(
                "Skipping Fastlane screenshot generation (pass -Pfastlane.screenshots=true to enable)",
                "true".equalsIgnoreCase(System.getProperty("fastlane.screenshots", "false"))
        );
        resetRepositorySingleton();
    }

    @After
    public void tearDown() throws Exception {
        resetRepositorySingleton();
        if (db != null) {
            db.close();
            AppDatabase.setTestInstance(null);
        }
    }

    private void resetRepositorySingleton() throws Exception {
        Field instanceField = Repository.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    private void initializeMlKitIfAvailable(Context context) {
        try {
            Class<?> mlKitContextClass = Class.forName("com.google.mlkit.common.sdkinternal.MlKitContext");
            Method zza = mlKitContextClass.getDeclaredMethod("zza", Context.class);
            zza.setAccessible(true);
            zza.invoke(null, context);
        } catch (Throwable ignored) {
            // ML Kit is only present in the 'play' flavor
        }
    }

    private void prepareEnvironment(String languageCode, boolean isItalian, boolean isDark) {
        Application app = ApplicationProvider.getApplicationContext();
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        LocaleHelper.setLocale(app, languageCode);
        AppCompatDelegate.setDefaultNightMode(
                isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );

        SyncFlavorInitializer.initialize(app);
        initializeMlKitIfAvailable(app);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        prefs.edit()
                .putBoolean("main_hints_shown", true)
                .putBoolean(SettingsFragment.KEY_DEFUALT_ICON, true)
                .putString(app.getString(R.string.pref_key_theme), isDark ? ThemeHelper.DARK_MODE : ThemeHelper.LIGHT_MODE)
                .putString(ProductListFragment.PREF_LAYOUT_MANAGER_KEY, ProductListFragment.LAYOUT_GRID)
                .putString(SyncManager.KEY_WEBDAV_URL, "https://nextcloud.example.com/remote.php/dav/files/mario/")
                .putString(SyncManager.KEY_WEBDAV_USER, "mario.rossi")
                .putString(SyncManager.KEY_WEBDAV_PASS, "SuperSecretPassword123")
                .putString(SyncManager.KEY_WEBDAV_PATH, "/DispensaSync")
                .putBoolean(SyncManager.KEY_WEBDAV_MODE_SHARED, false)
                .putString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, "1,2")
                .putString(LocalSafSyncProviderLoader.PREF_KEY_SAF_URI,
                        "content://com.android.externalstorage.documents/tree/primary%3ADispensaBackup")
                .commit();

        db = Room.inMemoryDatabaseBuilder(app, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        AppDatabase.setTestInstance(db);

        seedSampleData(app, isItalian);

        Repository repo = Repository.getInstance(app);
        repo.setCurrentDispensaId(1);
        waitForAsyncTasks();
    }

    private void seedSampleData(Context context, boolean isItalian) {
        String ownerId = InstallationIdProvider.getOrCreateInstallationId(context);

        // 1. Dispense (Pantries)
        Dispensa homeDispensa = new Dispensa(isItalian ? "Dispensa Casa" : "Home Pantry", true);
        homeDispensa.id = 1;
        homeDispensa.deviceOwnerId = ownerId;
        homeDispensa.deviceOwnerName = "Google Pixel 8 Pro";
        db.dispensaDao().insert(homeDispensa);

        Dispensa sharedDispensa = new Dispensa(isItalian ? "Casa al Mare (Condivisa)" : "Beach House (Shared)", false);
        sharedDispensa.id = 2;
        sharedDispensa.deviceOwnerId = "remote-owner-galaxy-s24";
        sharedDispensa.deviceOwnerName = "Samsung Galaxy S24";
        db.dispensaDao().insert(sharedDispensa);

        Dispensa officeDispensa = new Dispensa(isItalian ? "Snack Ufficio" : "Office Snacks", false);
        officeDispensa.id = 3;
        officeDispensa.deviceOwnerId = ownerId;
        officeDispensa.deviceOwnerName = "Google Pixel 8 Pro";
        db.dispensaDao().insert(officeDispensa);

        // 2. Joined Pantry Configs (WebDAV & Local SAF)
        JoinedPantryConfig webdavConfig = new JoinedPantryConfig();
        webdavConfig.dispensaId = 2;
        webdavConfig.providerId = "webdav";
        webdavConfig.isShared = true;
        webdavConfig.url = "https://nextcloud.example.com/remote.php/dav/files/mario/";
        webdavConfig.username = "mario.rossi";
        webdavConfig.path = "/DispensaSync/CasaAlMare";
        db.joinedPantryConfigDao().insert(webdavConfig);

        JoinedPantryConfig safConfig = new JoinedPantryConfig();
        safConfig.dispensaId = 3;
        safConfig.providerId = "local_saf";
        safConfig.isShared = false;
        safConfig.path = "content://com.android.externalstorage.documents/tree/primary%3ADispensaBackup";
        db.joinedPantryConfigDao().insert(safConfig);

        // 3. Storage Locations (Predefined + Custom)
        List<StorageLocation> locations = PredefinedData.getInitialStorageLocations(1);
        for (StorageLocation loc : locations) {
            loc.isDefault = PredefinedData.LOCATION_PANTRY.equals(loc.getInternalKey());
        }
        StorageLocation customFridge = new StorageLocation(
                isItalian ? "Frigo Taverna" : "Cellar Fridge",
                "CUSTOM_FRIGO_TAVERNA", 3, false, false
        );
        customFridge.dispensaId = 1;
        StorageLocation customWine = new StorageLocation(
                isItalian ? "Cantina Vini" : "Wine Cellar",
                "CUSTOM_CANTINA_VINI", 4, false, false
        );
        customWine.dispensaId = 1;
        locations.add(customFridge);
        locations.add(customWine);
        db.storageLocationDao().insertAll(locations);

        // 4. Products + Categories
        long dayMs = 24L * 60L * 60L * 1000L;
        long now = System.currentTimeMillis();

        Product p1 = new Product(
                "8001120001234", 2, now + (2 * dayMs),
                isItalian ? "Latte Fresco Intero" : "Fresh Whole Milk",
                null, PredefinedData.LOCATION_PANTRY, now - dayMs, 3,
                isItalian ? "Biologico, consumare entro 3gg dall'apertura" : "Organic, consume within 3d of opening"
        );
        p1.id = 1;
        p1.dispensaId = 1;

        Product p2 = new Product(
                "8001120005678", 4, now + (120 * dayMs),
                isItalian ? "Rigatoni Trafilati al Bronzo" : "Bronze-Die Rigatoni Pasta",
                null, PredefinedData.LOCATION_PANTRY, 0L, -1,
                isItalian ? "Cottura 12 minuti" : "Cooking time 12 mins"
        );
        p2.id = 2;
        p2.dispensaId = 1;

        Product p3 = new Product(
                "8001120009012", 3, now + (240 * dayMs),
                isItalian ? "Passata di Pomodoro Rustica" : "Rustic Tomato Passata",
                null, PredefinedData.LOCATION_PANTRY, 0L, 5,
                isItalian ? "Pomodori 100% italiani" : "100% Italian tomatoes"
        );
        p3.id = 3;
        p3.dispensaId = 1;

        Product p4 = new Product(
                "8001120003456", 1, now + (45 * dayMs),
                isItalian ? "Bevanda Avena Barista" : "Oat Milk Barista Edition",
                null, PredefinedData.LOCATION_PANTRY, 0L, 5,
                isItalian ? "Senza zuccheri aggiunti" : "No added sugars"
        );
        p4.id = 4;
        p4.dispensaId = 1;

        db.productDao().insertAll(Arrays.asList(p1, p2, p3, p4));

        CategoryDefinition cat1 = new CategoryDefinition(isItalian ? "Latticini" : "Dairy");
        long cat1Id = db.categoryDefinitionDao().insert(cat1);
        CategoryDefinition cat2 = new CategoryDefinition(isItalian ? "Biologico" : "Organic");
        long cat2Id = db.categoryDefinitionDao().insert(cat2);
        db.productCategoryLinkDao().insert(new ProductCategoryLink(1, (int) cat1Id));
        db.productCategoryLinkDao().insert(new ProductCategoryLink(1, (int) cat2Id));

        // 5. Shopping List Items
        ShoppingItem s1 = new ShoppingItem(isItalian ? "Olio Extravergine d'Oliva" : "Extra Virgin Olive Oil", 1, false);
        s1.dispensaId = 1;
        ShoppingItem s2 = new ShoppingItem(isItalian ? "Uova Biologiche (x6)" : "Organic Eggs (6 pack)", 2, false);
        s2.dispensaId = 1;
        ShoppingItem s3 = new ShoppingItem(isItalian ? "Farina Tipo 00 (1kg)" : "All-Purpose Flour (1kg)", 1, false);
        s3.dispensaId = 1;
        ShoppingItem s4 = new ShoppingItem(isItalian ? "Caffè Macinato Moka" : "Ground Espresso Coffee", 2, true);
        s4.dispensaId = 1;
        ShoppingItem s5 = new ShoppingItem(isItalian ? "Parmigiano Reggiano 24m" : "Parmesan Cheese 24m", 1, true);
        s5.dispensaId = 1;

        db.shoppingItemDao().insertAll(Arrays.asList(s1, s2, s3, s4, s5));
    }

    @Test
    @Config(qualifiers = "it-rIT-w411dp-h891dp-notnight-xxhdpi")
    public void captureItalianLightScreenshots() throws Exception {
        prepareEnvironment("it", true, false);
        captureAllActivitiesForLocale("it-IT", true, "light");
    }

    @Test
    @Config(qualifiers = "it-rIT-w411dp-h891dp-night-xxhdpi")
    public void captureItalianDarkScreenshots() throws Exception {
        prepareEnvironment("it", true, true);
        captureAllActivitiesForLocale("it-IT", true, "dark");
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-notnight-xxhdpi")
    public void captureEnglishLightScreenshots() throws Exception {
        prepareEnvironment("en", false, false);
        captureAllActivitiesForLocale("en-US", false, "light");
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
    public void captureEnglishDarkScreenshots() throws Exception {
        prepareEnvironment("en", false, true);
        captureAllActivitiesForLocale("en-US", false, "dark");
    }

    private void captureAllActivitiesForLocale(String fastlaneLocaleDir, boolean isItalian, String themeSuffix) throws Exception {
        // 1. MainActivity
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
            controller.setup();
            waitForAsyncTasks();
            ViewPager2 vp = controller.get().findViewById(R.id.viewPager);
            if (vp != null) {
                vp.setCurrentItem(1, false); // Select Dispensa/Pantry tab where sample products are stored
                waitForAsyncTasks();
            }
            captureActivity(controller.get(), fastlaneLocaleDir, "01_main_activity_" + themeSuffix + ".jpeg");
        }

        // 2. AddProductActivity (Populated with fake product data & category chips)
        Intent addIntent = new Intent(ApplicationProvider.getApplicationContext(), AddProductActivity.class);
        addIntent.putExtra(AddProductActivity.EXTRA_PRODUCT_ID, 1);
        try (ActivityController<AddProductActivity> controller = Robolectric.buildActivity(AddProductActivity.class, addIntent)) {
            controller.setup();
            waitForAsyncTasks();
            AddProductActivity act = controller.get();
            EditText etName = act.findViewById(R.id.editTextProductName);
            EditText etQty = act.findViewById(R.id.editTextQuantity);
            EditText etExpiry = act.findViewById(R.id.editTextExpiryDate);
            EditText etShelf = act.findViewById(R.id.editTextShelfLifeAfterOpening);
            EditText etNote = act.findViewById(R.id.editTextNote);
            ChipGroup chipGroup = act.findViewById(R.id.chipGroupCategories);

            if (etName != null) etName.setText(isItalian ? "Latte Fresco Intero Bio" : "Organic Fresh Whole Milk");
            if (etQty != null) etQty.setText("2");
            if (etExpiry != null) {
                String dateStr = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        .format(new Date(System.currentTimeMillis() + 5L * 86400000L));
                etExpiry.setText(dateStr);
            }
            if (etShelf != null) etShelf.setText("3");
            if (etNote != null) {
                etNote.setText(isItalian
                        ? "Conservare in frigo a +4°C dopo l'apertura"
                        : "Keep refrigerated at +4°C after opening");
            }
            if (chipGroup != null && chipGroup.getChildCount() == 0) {
                for (String tag : (isItalian ? new String[]{"Latticini", "Biologico", "Colazione"} : new String[]{"Dairy", "Organic", "Breakfast"})) {
                    Chip chip = new Chip(act);
                    chip.setText(tag);
                    chip.setCloseIconVisible(true);
                    chipGroup.addView(chip);
                }
            }
            waitForAsyncTasks();
            captureActivity(act, fastlaneLocaleDir, "02_add_product_activity_" + themeSuffix + ".jpeg");
        }

        // 3. ShoppingListActivity
        try (ActivityController<ShoppingListActivity> controller = Robolectric.buildActivity(ShoppingListActivity.class)) {
            controller.setup();
            waitForAsyncTasks();
            EditText etNewItem = controller.get().findViewById(R.id.editTextItemName);
            EditText etNewQty = controller.get().findViewById(R.id.editTextItemQuantity);
            if (etNewItem != null) etNewItem.setText(isItalian ? "Mozzarella di Bufala" : "Mozzarella Cheese");
            if (etNewQty != null) etNewQty.setText("2");
            captureActivity(controller.get(), fastlaneLocaleDir, "03_shopping_list_activity_" + themeSuffix + ".jpeg");
        }

        // 4. DispensaManagerActivity
        try (ActivityController<DispensaManagerActivity> controller = Robolectric.buildActivity(DispensaManagerActivity.class)) {
            controller.setup();
            waitForAsyncTasks();
            captureActivity(controller.get(), fastlaneLocaleDir, "04_dispensa_manager_activity_" + themeSuffix + ".jpeg");
        }

        // 5. SharedDispenseActivity
        try (ActivityController<SharedDispenseActivity> controller = Robolectric.buildActivity(SharedDispenseActivity.class)) {
            controller.setup();
            waitForAsyncTasks();
            captureActivity(controller.get(), fastlaneLocaleDir, "05_shared_dispense_activity_" + themeSuffix + ".jpeg");
        }

        // 6. ManageDevicesActivity (Injected with fake connected devices)
        Dispensa homeDispensa = db.dispensaDao().getDispensaByIdSync(1);
        Intent devicesIntent = new Intent(ApplicationProvider.getApplicationContext(), ManageDevicesActivity.class);
        devicesIntent.putExtra(ManageDevicesActivity.PANTRY_ID, homeDispensa);
        try (ActivityController<ManageDevicesActivity> controller = Robolectric.buildActivity(ManageDevicesActivity.class, devicesIntent)) {
            controller.setup();
            waitForAsyncTasks();
            ManageDevicesActivity act = controller.get();
            injectFakeDevices(act, isItalian);
            waitForAsyncTasks();
            captureActivity(act, fastlaneLocaleDir, "06_manage_devices_activity_" + themeSuffix + ".jpeg");
        }

        // 7. SyncOnboardingActivity (Share mode with generated QR code)
        Intent onboardingIntent = new Intent(ApplicationProvider.getApplicationContext(), SyncOnboardingActivity.class);
        onboardingIntent.putExtra(SyncOnboardingActivity.EXTRA_MODE, SyncOnboardingActivity.MODE_SHARE);
        onboardingIntent.putExtra(ManageDevicesActivity.PANTRY_ID, homeDispensa);
        try (ActivityController<SyncOnboardingActivity> controller = Robolectric.buildActivity(SyncOnboardingActivity.class, onboardingIntent)) {
            controller.setup();
            waitForAsyncTasks();
            SyncOnboardingActivity act = controller.get();
            ImageView qrView = act.findViewById(R.id.iv_qr_code);
            if (qrView != null) {
                Bitmap qrBitmap = QrCodeGenerator.generate(
                        "https://enricofrigo.github.io/dispensa/syncjoin?data=DEMO_ENCRYPTED_PAIRING_TOKEN",
                        512
                );
                qrView.setVisibility(View.VISIBLE);
                qrView.setImageBitmap(qrBitmap);
            }
            waitForAsyncTasks();
            captureActivity(act, fastlaneLocaleDir, "07_sync_onboarding_activity_" + themeSuffix + ".jpeg");
        }

        // 8. SyncWebDavConfigActivity
        try (ActivityController<SyncWebDavConfigActivity> controller = Robolectric.buildActivity(SyncWebDavConfigActivity.class)) {
            controller.setup();
            waitForAsyncTasks();
            captureActivity(controller.get(), fastlaneLocaleDir, "08_sync_webdav_config_activity_" + themeSuffix + ".jpeg");
        }

        // 9. SyncLocalSafConfigActivity
        try (ActivityController<SyncLocalSafConfigActivity> controller = Robolectric.buildActivity(SyncLocalSafConfigActivity.class)) {
            controller.setup();
            waitForAsyncTasks();
            captureActivity(controller.get(), fastlaneLocaleDir, "09_sync_local_saf_config_activity_" + themeSuffix + ".jpeg");
        }

        // 10. SettingsActivity (Main Settings)
        try (ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class)) {
            controller.setup();
            waitForAsyncTasks();
            captureActivity(controller.get(), fastlaneLocaleDir, "10_settings_activity_" + themeSuffix + ".jpeg");
        }

        // 11. SettingsActivity (ManageLocationsFragment)
        Intent locationsIntent = new Intent(ApplicationProvider.getApplicationContext(), SettingsActivity.class);
        locationsIntent.putExtra("OPEN_MANAGE_LOCATIONS", true);
        try (ActivityController<SettingsActivity> controller = Robolectric.buildActivity(SettingsActivity.class, locationsIntent)) {
            controller.setup();
            waitForAsyncTasks();
            captureActivity(controller.get(), fastlaneLocaleDir, "11_manage_locations_activity_" + themeSuffix + ".jpeg");
        }

        // 12. ConsumeScannerActivity
        try (ActivityController<ConsumeScannerActivity> controller = Robolectric.buildActivity(ConsumeScannerActivity.class)) {
            controller.setup();
            waitForAsyncTasks();
            captureActivity(controller.get(), fastlaneLocaleDir, "12_consume_scanner_activity_" + themeSuffix + ".jpeg");
        }

        // 13. SyncGDriveConfigActivity (Play flavor only)
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Activity> gdriveClass = (Class<? extends Activity>) Class.forName(
                    "eu.frigo.dispensa.activity.SyncGDriveConfigActivity"
            );
            try (ActivityController<? extends Activity> controller = Robolectric.buildActivity(gdriveClass)) {
                controller.setup();
                waitForAsyncTasks();
                Activity act = controller.get();
                TextView tvStatus = act.findViewById(R.id.tv_gdrive_status);
                View btnSignIn = act.findViewById(R.id.btn_gdrive_sign_in);
                View btnSignOut = act.findViewById(R.id.btn_gdrive_sign_out);
                View btnSave = act.findViewById(R.id.btn_save_gdrive_config);
                if (tvStatus != null) {
                    tvStatus.setText(act.getString(R.string.sync_gdrive_connected_as, "mario.rossi@gmail.com"));
                }
                if (btnSignIn != null) btnSignIn.setVisibility(View.GONE);
                if (btnSignOut != null) btnSignOut.setVisibility(View.VISIBLE);
                if (btnSave != null) btnSave.setEnabled(true);
                waitForAsyncTasks();
                captureActivity(act, fastlaneLocaleDir, "13_sync_gdrive_config_activity_" + themeSuffix + ".jpeg");
            }
        } catch (ClassNotFoundException ignored) {
            // Expected when running on the fdroid flavor
        }
    }

    @SuppressWarnings("unchecked")
    private void injectFakeDevices(ManageDevicesActivity activity, boolean isItalian) throws Exception {
        String selfId = InstallationIdProvider.getOrCreateInstallationId(activity);

        Field listField = ManageDevicesActivity.class.getDeclaredField("deviceList");
        listField.setAccessible(true);
        List<PantryDevice> deviceList = (List<PantryDevice>) listField.get(activity);
        deviceList.clear();
        deviceList.add(new PantryDevice(selfId, "Google Pixel 8 Pro"));
        deviceList.add(new PantryDevice("dev-galaxy-s24-99a1", "Samsung Galaxy S24"));
        deviceList.add(new PantryDevice("dev-tablet-cucina-44b2", isItalian ? "Tablet Cucina" : "Kitchen Tablet"));

        Field adapterField = ManageDevicesActivity.class.getDeclaredField("adapter");
        adapterField.setAccessible(true);
        Object adapter = adapterField.get(activity);
        Method setMaster = adapter.getClass().getDeclaredMethod("setMaster", boolean.class);
        setMaster.setAccessible(true);
        setMaster.invoke(adapter, true);

        Method notifyDataSetChanged = adapter.getClass().getMethod("notifyDataSetChanged");
        notifyDataSetChanged.invoke(adapter);

        View progress = activity.findViewById(R.id.progress_devices);
        if (progress != null) progress.setVisibility(View.GONE);
        TextView tvEmpty = activity.findViewById(R.id.tv_empty_devices);
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
    }

    private void waitForAsyncTasks() {
        for (int i = 0; i < 8; i++) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
    }

    private void captureActivity(Activity activity, String fastlaneLocaleDir, String fileName) throws Exception {
        View rootView = activity.getWindow().getDecorView();

        int widthSpec = View.MeasureSpec.makeMeasureSpec(SCREEN_WIDTH_PX, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(SCREEN_HEIGHT_PX, View.MeasureSpec.EXACTLY);
        rootView.measure(widthSpec, heightSpec);
        rootView.layout(0, 0, SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX);
        waitForAsyncTasks();
        rootView.measure(widthSpec, heightSpec);
        rootView.layout(0, 0, SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX);

        Bitmap bitmap = Bitmap.createBitmap(SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        rootView.draw(canvas);

        String baseOutputDir = System.getProperty("fastlane.output.dir", "fastlane/metadata/android");
        File targetDir = new File(baseOutputDir, fastlaneLocaleDir + "/images/phoneScreenshots");
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IllegalStateException("Cannot create directory: " + targetDir.getAbsolutePath());
        }

        File outFile = new File(targetDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos);
        }
        System.out.println("Saved Fastlane screenshot: " + outFile.getAbsolutePath());
    }
}
