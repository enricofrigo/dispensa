package eu.frigo.dispensa.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;
import eu.frigo.dispensa.BuildConfig;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.ActionMenuView;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.Log;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.badge.BadgeUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.skydoves.balloon.ArrowOrientation;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.adapter.LocationViewPagerAdapter;
import eu.frigo.dispensa.adapter.ProductListAdapter;
import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.backup.BackupManager;
import eu.frigo.dispensa.data.category.ProductWithCategoryDefinitions;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.storage.StorageLocation;
import eu.frigo.dispensa.ui.ProductListFragment;
import eu.frigo.dispensa.ui.SettingsFragment;
import eu.frigo.dispensa.util.LocaleHelper;
import eu.frigo.dispensa.util.LocationFormatter;
import eu.frigo.dispensa.viewmodel.LocationViewModel;
import eu.frigo.dispensa.viewmodel.ProductViewModel;
import eu.frigo.dispensa.viewmodel.ShoppingListViewModel;

public class MainActivity extends AppCompatActivity
        implements ProductListAdapter.OnProductInteractionListener {

    private ProductViewModel productViewModel;
    private SearchView searchView;
    private boolean isInitialTabSet = false;
    private FloatingActionButton fab;
    private FloatingActionButton fabConsume;
    private View fabContainer;
    private CoordinatorLayout mainCoordinatorLayout;
    private int originalFabBottomMargin;
    private MenuItem layoutToggleMenuItem;
    private ViewPager2 viewPager;
    private LocationViewPagerAdapter locationViewPagerAdapter;
    private LocationViewModel locationViewModel;
    private ShoppingListViewModel shoppingListViewModel;
    private BadgeDrawable shoppingBadge;
    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(this, getString(R.string.notify_permission_accpeted), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, getString(R.string.notify_permission_denied), Toast.LENGTH_LONG).show();
                }
            });
    private final ActivityResultLauncher<Intent> addProductActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                String message = null;
                if (result.getResultCode() == AppCompatActivity.RESULT_OK && result.getData() != null) {
                    String productName = result.getData().getStringExtra(AddProductActivity.NEW_PRODUCT_NAME);
                    boolean isEdit = result.getData().getBooleanExtra(AddProductActivity.NEW_PRODUCT_EDIT, false);
                    if (productName != null) {
                        message = isEdit ? String.format(getString(R.string.notify_update_product), productName)
                                : String.format(getString(R.string.notify_add_product), productName);
                    } else {
                        message = getString(R.string.notify_add_new_product);
                    }
                } else if (result.getResultCode() == AppCompatActivity.RESULT_CANCELED) {
                    message = getString(R.string.canceled);
                }
                if (message != null)
                    showSnackbarWithUndo(message, null);
            });

    private final ActivityResultLauncher<Intent> consumeScannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == AppCompatActivity.RESULT_OK && result.getData() != null) {
                    String barcode = result.getData().getStringExtra(ConsumeScannerActivity.EXTRA_SCANNED_BARCODE);
                    Long scannedDate = null;
                    if (result.getData().hasExtra(ConsumeScannerActivity.EXTRA_SCANNED_DATE_MATCH)) {
                        scannedDate = result.getData().getLongExtra(ConsumeScannerActivity.EXTRA_SCANNED_DATE_MATCH, -1);
                        if (scannedDate == -1) scannedDate = null;
                    }
                    if (barcode != null) {
                        handleScannedBarcodeForConsume(barcode, scannedDate);
                    }
                }
            });

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/octet-stream"), uri -> {
                if (uri != null) {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                            BackupManager backupManager = new BackupManager(this);
                            backupManager.exportData(os, BuildConfig.VERSION_CODE);
                            runOnUiThread(
                                    () -> Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show());
                        } catch (Exception e) {
                            Log.e("MainActivity", "Export failed", e);
                            runOnUiThread(() -> Toast
                                    .makeText(this, getString(R.string.export_error, e.getMessage()), Toast.LENGTH_LONG)
                                    .show());
                        }
                    });
                }
            });

    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.import_confirm_title)
                            .setMessage(R.string.import_confirm_message)
                            .setPositiveButton(R.string.ok, (dialog, which) -> performImport(uri))
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                }
            });

    private void performImport(android.net.Uri uri) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                BackupManager backupManager = new BackupManager(this);
                backupManager.importData(is);
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.import_success, Toast.LENGTH_LONG).show();
                    // Restart app to refresh all data and viewmodels
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    Runtime.getRuntime().exit(0);
                });
            } catch (Exception e) {
                Log.e("MainActivity", "Import failed", e);
                runOnUiThread(() -> Toast
                        .makeText(this, getString(R.string.import_error, e.getMessage()), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.notify_permission_title))
                            .setMessage(getString(R.string.notify_permission_description))
                            .setPositiveButton(getString(R.string.ok), (dialog, which) -> requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS))
                            .setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.dismiss())
                            .create().show();
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                }
            }
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        askForNotificationPermission();
        eu.frigo.dispensa.data.Repository.cleanOrphanImages(this, null);
        EdgeToEdge.enable(this);
        LocaleHelper.applyLocaleOnCreate(this);
        setContentView(R.layout.activity_main);
        mainCoordinatorLayout = findViewById(R.id.main_coordinator_layout);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.app_name);
        setSupportActionBar(toolbar);

        productViewModel = new ViewModelProvider(this).get(ProductViewModel.class);
        locationViewModel = new ViewModelProvider(this).get(LocationViewModel.class);
        productViewModel.getAllProducts().observe(this, products -> showHintsIfNeeded());

        viewPager = findViewById(R.id.viewPager);
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        ImageButton btnManageLocations = findViewById(R.id.button_manage_locations);
        if (btnManageLocations != null) {
            btnManageLocations.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                intent.putExtra("OPEN_MANAGE_LOCATIONS", true);
                startActivity(intent);
            });
        }

        // Shopping list button
        ImageButton btnShoppingList = findViewById(R.id.button_shopping_list);
        if (btnShoppingList != null) {
            btnShoppingList.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, ShoppingListActivity.class);
                startActivity(intent);
            });
        }

        // ShoppingList ViewModel + badge
        shoppingListViewModel = new ViewModelProvider(this).get(ShoppingListViewModel.class);

        // Osserva il conteggio non comprati per il badge
        shoppingListViewModel.getUncheckedCount().observe(this, count -> {
            updateShoppingBadge(Objects.requireNonNullElse(count, 0));
        });

        locationViewModel = new ViewModelProvider(this).get(LocationViewModel.class);
        locationViewPagerAdapter = new LocationViewPagerAdapter(this);
        viewPager.setAdapter(locationViewPagerAdapter);
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            StorageLocation currentLocation = locationViewPagerAdapter.getLocationAt(position);
            if (currentLocation != null) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                boolean showPredefinedAsIcon = prefs.getBoolean(SettingsFragment.KEY_DEFUALT_ICON, false);
                if (currentLocation.isPredefined) {
                    if (showPredefinedAsIcon) {
                        tab.setIcon(LocationFormatter.getIcon(currentLocation));
                    } else {
                        tab.setText(LocationFormatter.getLocalizedName(this, currentLocation));
                    }
                } else {
                    tab.setText(currentLocation.getName());
                }
            }
        }).attach();
        observeLocationsForTabs();

        locationViewModel.getAllLocationsSorted().observe(this, newLocations -> {
            if (newLocations != null) {
                locationViewPagerAdapter.setLocations(newLocations);
            }
        });
        fab = findViewById(R.id.fab);
        fabConsume = findViewById(R.id.fab_consume);
        fabContainer = findViewById(R.id.fab_container);

        if (fabContainer.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            originalFabBottomMargin = ((ViewGroup.MarginLayoutParams) fabContainer.getLayoutParams()).bottomMargin;
        } else {
            originalFabBottomMargin = 0;
        }
        ViewCompat.setOnApplyWindowInsetsListener(mainCoordinatorLayout, (v, windowInsets) -> {
            Insets navigationBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets statusBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime()); // Per la tastiera

            v.setPadding(
                    navigationBarsInsets.left,
                    statusBarsInsets.top,
                    navigationBarsInsets.right,
                    0);
            ViewGroup.MarginLayoutParams containerParams = (ViewGroup.MarginLayoutParams) fabContainer.getLayoutParams();
            int bottomInset = Math.max(navigationBarsInsets.bottom, imeInsets.bottom);
            containerParams.bottomMargin = originalFabBottomMargin + bottomInset;
            fabContainer.setLayoutParams(containerParams);
            if (viewPager != null) {
                viewPager.registerOnPageChangeCallback(pageChangeCallback);
            }
            return windowInsets;
        });

        fab.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, AddProductActivity.class);
            if (locationViewPagerAdapter != null && locationViewPagerAdapter.getItemCount() > 0) {
                int currentItemPosition = viewPager.getCurrentItem();
                String currentLocationInternalKey = locationViewPagerAdapter
                        .getLocationInternalKeyAt(currentItemPosition);
                intent.putExtra(AddProductActivity.PRESELECTED_LOCATION_INTERNAL_KEY, currentLocationInternalKey);
            }
            addProductActivityLauncher.launch(intent);
        });

        fabConsume.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, ConsumeScannerActivity.class);
            consumeScannerLauncher.launch(intent);
        });

    }

    private void showHintsIfNeeded() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean hintsShown = prefs.getBoolean("main_hints_shown", false);
        if(!Objects.requireNonNull(productViewModel.getAllProducts().getValue()).isEmpty())
            prefs.edit().putBoolean("main_hints_shown", true).apply();
        if (!hintsShown) {
            prefs.edit().putBoolean("main_hints_shown", true).apply();

            ImageButton btnManageLocations = findViewById(R.id.button_manage_locations);
            showHintBalloon(fab, getString(R.string.hint_add_product), ArrowOrientation.END, 0.5f);
            showHintBalloon(fabConsume, getString(R.string.hint_consume_product), ArrowOrientation.BOTTOM, 0.8f);
            showHintBalloon(btnManageLocations, getString(R.string.hint_manage_locations), ArrowOrientation.TOP, 0.1f);

            Toolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                toolbar.post(() -> {
                    for (int i = 0; i < toolbar.getChildCount(); i++) {
                        View child = toolbar.getChildAt(i);
                        if (child instanceof ActionMenuView menuView) {
                            View overflowIcon = menuView.getChildAt(menuView.getChildCount() - 1);
                            if (overflowIcon != null) {
                                // Rimuoviamo il post() extra qui poiché siamo già all'interno di un target handler garantito
                                com.skydoves.balloon.Balloon balloonLoc = new com.skydoves.balloon.Balloon.Builder(this)
                                        .setArrowSize(10)
                                        .setArrowOrientation(ArrowOrientation.TOP)
                                        .setArrowPosition(0.9f)
                                        .setPadding(8)
                                        .setCornerRadius(8f)
                                        .setAlpha(0.9f)
                                        .setText(getString(R.string.hint_manage_settings))
                                        .setTextColorResource(R.color.white)
                                        .setBackgroundColorResource(R.color.purple_500)
                                        .setBalloonAnimation(com.skydoves.balloon.BalloonAnimation.FADE)
                                        .setLifecycleOwner(this)
                                        .setDismissWhenTouchOutside(true)
                                        .build();
                                balloonLoc.showAlignBottom(overflowIcon);
                            }
                            break;
                        }
                    }
                });
            }
        }
    }

    private void showHintBalloon(View anchor, String text, ArrowOrientation orientation, float arrowPosition) {
        if (anchor == null) return;
        anchor.post(() -> {
            com.skydoves.balloon.Balloon balloon = new com.skydoves.balloon.Balloon.Builder(this)
                    .setArrowSize(10)
                    .setArrowOrientation(orientation)
                    .setArrowPosition(arrowPosition)
                    .setPadding(8)
                    .setCornerRadius(8f)
                    .setAlpha(0.9f)
                    .setText(text)
                    .setTextColorResource(R.color.white)
                    .setBackgroundColorResource(R.color.purple_500)
                    .setBalloonAnimation(com.skydoves.balloon.BalloonAnimation.FADE)
                    .setLifecycleOwner(this)
                    .setDismissWhenTouchOutside(true)
                    .build();

            if (orientation == ArrowOrientation.BOTTOM) {
                balloon.showAlignTop(anchor);
            } else if (orientation == ArrowOrientation.END) {
                balloon.showAlignStart(anchor);
            } else {
                balloon.showAlignBottom(anchor);
            }
        });
    }

    private void observeLocationsForTabs() {
        locationViewModel.getLocationsForTabs().observe(this, locations -> {
            if (locations != null) {
                locationViewPagerAdapter.setLocations(locations);
                if (!isInitialTabSet) {
                    isInitialTabSet = true;
                    int defaultIndex = 0; // Default to 'All' tab
                    for (int i = 0; i < locations.size(); i++) {
                        if (locations.get(i).isDefault()) {
                            defaultIndex = i;
                            break;
                        }
                    }
                    final int indexToSelect = defaultIndex;
                    viewPager.post(() -> viewPager.setCurrentItem(indexToSelect, false));
                }
            }
        });
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateLayoutToggleIcon();
        restoreSearchViewQuery();
        return super.onPrepareOptionsMenu(menu);
    }

    private void restoreSearchViewQuery() {
        if (searchView != null && productViewModel != null && productViewModel.getSearchQuery().getValue() != null) {
            String currentQueryInViewModel = productViewModel.getSearchQuery().getValue();
            if (!searchView.getQuery().toString().equals(currentQueryInViewModel)) {
                Log.d("SearchViewRestore", "Ripristino query nella SearchView: '" + currentQueryInViewModel + "'");
                searchView.setQuery(currentQueryInViewModel, false);
            }

            if (!currentQueryInViewModel.isEmpty()) {
                if (searchView.isIconified()) {
                    searchView.setIconified(false);
                }
            } else {
                if (!searchView.isIconified() && !searchView.hasFocus()) {
                    searchView.setIconified(true);
                }
            }
        } else if (searchView != null && !searchView.isIconified() && !searchView.hasFocus()) {
            searchView.setIconified(true);
        }
    }

    public void updateLayoutToggleIcon() {
        if (layoutToggleMenuItem == null)
            return;

        Fragment currentFragmentRaw = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
        if (!(currentFragmentRaw instanceof ProductListFragment)) {
            return;
        }
        // layoutToggleMenuItem.setVisible(true);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String layoutPreferenceKey = ProductListFragment.PREF_LAYOUT_MANAGER_KEY;
        Log.d("MainActivity", "layoutPreferenceKey: " + layoutPreferenceKey);
        String currentLayout = prefs.getString(layoutPreferenceKey, ProductListFragment.LAYOUT_LIST);
        if (currentLayout.equals(ProductListFragment.LAYOUT_GRID)) {
            layoutToggleMenuItem.setIcon(R.drawable.ic_view_list);
            layoutToggleMenuItem.setTitle(getString(R.string.layout_list_title));
        } else {
            layoutToggleMenuItem.setIcon(R.drawable.ic_view_grid);
            layoutToggleMenuItem.setTitle(getString(R.string.layout_grid_title));
        }
    }

    @Override
    public void onEditActionClicked(ProductWithCategoryDefinitions productWithCategoryDefinitions) {
        Product product = productWithCategoryDefinitions.product;
        Intent intent = new Intent(MainActivity.this, AddProductActivity.class);
        intent.putExtra(AddProductActivity.EXTRA_PRODUCT_ID, product.getId());
        intent.putExtra(AddProductActivity.EXTRA_PRODUCT_BARCODE, product.getBarcode());
        intent.putExtra(AddProductActivity.EXTRA_PRODUCT_QUANTITY, product.getQuantity());
        intent.putExtra(AddProductActivity.EXTRA_PRODUCT_EXPIRY_DATE, product.getExpiryDateString());
        intent.putExtra(AddProductActivity.EXTRA_PRODUCT_NAME, product.getProductName());
        intent.putExtra(AddProductActivity.EXTRA_PRODUCT_IMAGE, product.getImageUrl());
        intent.putExtra(AddProductActivity.PRESELECTED_LOCATION_INTERNAL_KEY, product.getStorageLocation());
        addProductActivityLauncher.launch(intent);
    }

    @Override
    public void onDeleteActionClicked(ProductWithCategoryDefinitions productWithCategoryDefinitions) {
        Product product = productWithCategoryDefinitions.product;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_product_title))
                .setMessage(String.format(getString(R.string.delete_product_descritpion), product.getProductName()))
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                    productViewModel.delete(product);
                    showSnackbarWithUndo(
                            String.format(getString(R.string.notify_delete_product), product.getProductName()),
                            () -> productViewModel.insert(product)
                    );
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    @Override
    public void onProductMoveClicked(ProductWithCategoryDefinitions productWithDefs) {
        if (productWithDefs == null || productWithDefs.product == null) {
            return;
        }
        Product productToMove = productWithDefs.product;

        // Recupera le possibili locazioni
        // Assumendo che LocationViewModel esponga LiveData<List<LocationEntity>>
        if (locationViewModel == null) { // Fallback se locationViewModel non è inizializzato
            Log.e("MainActivity", "LocationViewModel is null, cannot show move dialog.");
            Toast.makeText(this, "Impossibile caricare le locazioni.", Toast.LENGTH_SHORT).show();
            return;
        }

        locationViewModel.getAllLocationsSorted().observe(this, locations -> {
            if (locations == null || locations.isEmpty()) {
                Toast.makeText(this, "Nessuna locazione disponibile per lo spostamento.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // Filtra la locazione corrente
            List<StorageLocation> otherLocations = new ArrayList<>();
            for (StorageLocation loc : locations) {
                if (!loc.getInternalKey().equals(productToMove.getStorageLocation())) {
                    otherLocations.add(loc);
                }
            }

            if (otherLocations.isEmpty()) {
                Toast.makeText(this, "Nessun'altra locazione disponibile per lo spostamento.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            CharSequence[] locationNames = new CharSequence[otherLocations.size()];
            for (int i = 0; i < otherLocations.size(); i++) {
                locationNames[i] = eu.frigo.dispensa.util.LocationFormatter.getLocalizedName(this, otherLocations.get(i)); // O un nome più
                                                                                                    // user-friendly
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.move_product_to_title, productToMove.getProductName()));
            builder.setItems(locationNames, (dialog, which) -> {
                StorageLocation selectedLocation = otherLocations.get(which);
                String oldLocationKey = productToMove.getStorageLocation();
                String newLocationKey = selectedLocation.getInternalKey();

                Log.d("MainActivity", "Spostamento prodotto '" + productToMove.getProductName() +
                        "' da " + oldLocationKey + " a " + newLocationKey);

                productToMove.setStorageLocation(newLocationKey);
                productViewModel.update(productToMove);

                // Opzionale: Mostra un messaggio di conferma
                Toast.makeText(this,
                        String.format(getString(R.string.product_moved_toast), productToMove.getProductName(),
                                eu.frigo.dispensa.util.LocationFormatter.getLocalizedName(this, selectedLocation)),
                        Toast.LENGTH_SHORT).show();

                // L'aggiornamento del LiveData dovrebbe far sì che il prodotto scompaia da
                // questo tab
                // e appaia nel nuovo tab (se i tab osservano LiveData filtrati per locazione).
            });
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.show();

            // IMPORTANTE: Rimuovi l'observer dopo che è stato usato per evitare chiamate
            // multiple
            // se l'utente clicca "sposta" più volte prima che il dialogo precedente sia
            // chiuso.
            // Questo è un punto delicato con LiveData dentro un handler di evento.
            // Una soluzione migliore potrebbe essere avere la lista di locazioni già
            // disponibile
            // come campo nel Fragment, aggiornato una sola volta.
            // Per ora, lo lasciamo così, ma considera alternative se noti problemi.
            // O, meglio ancora, recupera le locazioni una volta e passale al metodo del
            // dialogo.
        });
    }

    @Override
    public void onProductItemClickedForQuantity(ProductWithCategoryDefinitions productWithCategoryDefinitions) {
        Log.d("MainActivity", "onProductItemClickedForQuantity called with product: " + productWithCategoryDefinitions);
        if (productWithCategoryDefinitions == null)
            return;
        Product product = productWithCategoryDefinitions.product;

        int currentQuantity = product.getQuantity();
        if (currentQuantity > 1) {
            Product updatedProduct = product.copyWithNewQuantity(currentQuantity - 1); // Assicurati che Product abbia
                                                                                       // questo metodo
            productViewModel.update(updatedProduct);
        } else if (currentQuantity == 1) {
            productViewModel.delete(product);
            showSnackbarWithUndo(
                    String.format(getString(R.string.notify_used), product.getProductName()),
                    () -> productViewModel.insert(product));
        }
    }

    @Override
    public void onShoppingCartToggled(ProductWithCategoryDefinitions product, boolean currentlyInList) {
        String productName = product.product.getProductName();
        if (productName == null || productName.isEmpty()) return;
        if (currentlyInList) {
            shoppingListViewModel.removeItem(productName);
        } else {
            shoppingListViewModel.addItem(productName);
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void updateShoppingBadge(int count) {
        ImageButton btnShoppingList = findViewById(R.id.button_shopping_list);
        if (btnShoppingList == null) return;

        if (shoppingBadge == null) {
            shoppingBadge = BadgeDrawable.create(this);
            shoppingBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.product_expired_stroke));
            shoppingBadge.setBadgeTextColor(ContextCompat.getColor(this, R.color.white));
            shoppingBadge.setHorizontalOffset(12);
            shoppingBadge.setVerticalOffset(12);
        }
        shoppingBadge.setVisible(false);

        if (count > 0) {
            shoppingBadge.setNumber(count);
            btnShoppingList.post(() -> BadgeUtils.attachBadgeDrawable(shoppingBadge, btnShoppingList));
        } else {
            shoppingBadge.setVisible(false);
            shoppingBadge.clearNumber();
            BadgeUtils.detachBadgeDrawable(shoppingBadge, btnShoppingList);
        }
    }

    public void showSnackbarWithUndo(String message, Runnable undoAction) {
        View targetLayout = mainCoordinatorLayout != null ? mainCoordinatorLayout : findViewById(R.id.main_coordinator_layout);
        if (targetLayout != null) {
            Snackbar snackbar = Snackbar.make(targetLayout, message, Snackbar.LENGTH_LONG);
            if (fabContainer != null) {
                snackbar.setAnchorView(fabContainer);
            }
            if (undoAction != null) {
                snackbar.setAction(getString(R.string.cancel).toUpperCase(), v -> undoAction.run());
            }
            snackbar.show();
        } else {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    private void handleScannedBarcodeForConsume(String barcode, Long scannedDate) {
        productViewModel.getProductsByBarcodeAsync(barcode, products -> runOnUiThread(() -> {
            if (products == null || products.isEmpty()) {
                Toast.makeText(this, R.string.consume_product_not_found, Toast.LENGTH_LONG).show();
            } else if (products.size() == 1) {
                confirmAndConsume(products.get(0));
            } else {
                int selectedIndex = -1;
                if (scannedDate != null) {
                    for (int i = 0; i < products.size(); i++) {
                        if (products.get(i).getExpiryDate() != null && products.get(i).getExpiryDate().equals(scannedDate)) {
                            selectedIndex = i;
                            break;
                        }
                    }
                }
                showDuplicateBarcodeSelectionDialog(products, selectedIndex);
            }
        }));
    }

    private void confirmAndConsume(Product product) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.use)
                .setMessage(String.format(getString(R.string.consume_product_confirm),
                        product.getProductName(), product.getExpiryDateString()))
                .setPositiveButton(R.string.ok, (dialog, which) -> consumeOne(product))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDuplicateBarcodeSelectionDialog(List<Product> products, int selectedIndex) {
        String[] items = new String[products.size()];
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            items[i] = String.format("%s (Scad: %s) - Qta: %d",
                    p.getProductName(), p.getExpiryDateString(), p.getQuantity());
        }

        final int[] currentSelected = {selectedIndex};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.consume_duplicate_title)
                .setSingleChoiceItems(items, selectedIndex, (d, which) -> {
                    currentSelected[0] = which;
                    ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                })
                .setPositiveButton(R.string.ok, (d, which) -> {
                    if (currentSelected[0] != -1) {
                        consumeOne(products.get(currentSelected[0]));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        
        dialog.setOnShowListener(d -> {
            if (currentSelected[0] == -1) {
                ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            }
        });
        dialog.show();
    }

    private void consumeOne(Product product) {
        int currentQuantity = product.getQuantity();
        if (currentQuantity > 1) {
            Product updatedProduct = product.copyWithNewQuantity(currentQuantity - 1);
            productViewModel.update(updatedProduct);
            showSnackbarWithUndo(getString(R.string.consume_success), () -> productViewModel.update(product));
        } else {
            productViewModel.delete(product);
            showSnackbarWithUndo(
                    String.format(getString(R.string.notify_used), product.getProductName()),
                    () -> productViewModel.insert(product));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                setupSearchView();
                restoreSearchViewQuery();
            } else {
                Log.e("MainActivity", "SearchView non trovato nell'item di menu!");
            }
        } else {
            Log.e("MainActivity", "Item di menu action_search non trovato!");
        }
        layoutToggleMenuItem = menu.findItem(R.id.action_toggle_layout);
        updateLayoutToggleIcon();
        return true;
    }

    private void setupSearchView() {
        searchView.setQueryHint(getString(R.string.action_search_hint));

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                productViewModel.setSearchQuery(query);
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                productViewModel.setSearchQuery(newText);
                return true;
            }
        });

        searchView.setOnCloseListener(() -> {
            if (productViewModel != null) {
                productViewModel.setSearchQuery("");
            }
            return false;
        });

        searchView.setOnQueryTextFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && searchView.getQuery().length() == 0) {
                if (productViewModel != null && !"".equals(productViewModel.getSearchQuery().getValue())) {
                    productViewModel.setSearchQuery("");
                }
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_toggle_layout) {
            Fragment currentFragment = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
            if (currentFragment instanceof ProductListFragment) {
                ((ProductListFragment) currentFragment).toggleLayoutManager();
            }
            return true;
        } else if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_export) {
            String fileName = "dispensa_backup_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".dsp";
            exportLauncher.launch(fileName);
            return true;
        } else if (id == R.id.action_import) {
            importLauncher.launch(new String[] { "*/*" });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (viewPager != null) {
            viewPager.registerOnPageChangeCallback(pageChangeCallback);
        }
        if (locationViewPagerAdapter != null) {
            locationViewPagerAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (viewPager != null) {
            viewPager.unregisterOnPageChangeCallback(pageChangeCallback);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (viewPager != null) {
            viewPager.unregisterOnPageChangeCallback(pageChangeCallback);
        }
    }

    private final ViewPager2.OnPageChangeCallback pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageSelected(int position) {
            super.onPageSelected(position);
            invalidateOptionsMenu();
        }
    };

    @Override
    protected void onStop() {
        super.onStop();
        if (searchView != null && !searchView.isIconified()) {
            searchView.setIconified(true);
            searchView.onActionViewCollapsed();
        }
    }
}