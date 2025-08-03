package eu.frigo.dispensa.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.widget.SearchView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.Log;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.widget.ViewPager2;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.adapter.LocationViewPagerAdapter;
import eu.frigo.dispensa.adapter.ProductListAdapter;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.category.ProductWithCategoryDefinitions;
import eu.frigo.dispensa.data.storage.StorageLocation;
import eu.frigo.dispensa.ui.ProductListFragment;
import eu.frigo.dispensa.ui.SettingsActivity;
import eu.frigo.dispensa.viewmodel.LocationViewModel;
import eu.frigo.dispensa.viewmodel.ProductViewModel;

import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SearchView.OnQueryTextListener, ProductListAdapter.OnProductInteractionListener{

    private ProductViewModel productViewModel;
    private List<ProductWithCategoryDefinitions> allProductsList = new ArrayList<>();
    private SearchView searchView;
    private FloatingActionButton fab;
    private CoordinatorLayout mainCoordinatorLayout;
    private int originalFabBottomMargin;
    private MenuItem layoutToggleMenuItem;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private LocationViewPagerAdapter locationViewPagerAdapter;
    private LocationViewModel locationViewModel;
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(this, "Permesso notifiche concesso!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Permesso notifiche negato. Non riceverai avvisi di scadenza.", Toast.LENGTH_LONG).show();
                }
            });
    private final ActivityResultLauncher<Intent> addProductActivityLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                String message = null ;
                if (result.getResultCode() == AppCompatActivity.RESULT_OK && result.getData() != null) {
                    String productName = result.getData().getStringExtra("NEW_PRODUCT_NAME");
                    boolean isEdit = result.getData().getBooleanExtra("NEW_PRODUCT_EDIT", false);
                    if (productName != null) {
                        message = isEdit?"Prodotto aggiornato: " + productName:"Prodotto aggiunto: " + productName;
                    } else {
                        message ="Nuovo prodotto aggiunto!";
                    }
                } else if (result.getResultCode() == AppCompatActivity.RESULT_CANCELED) {
                    message = "Azione annullata.";
                }
                if (message != null) showProductSavedSnackbar(message);
            });
    private void askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    new AlertDialog.Builder(this)
                            .setTitle("Permesso Notifiche Richiesto")
                            .setMessage("L'app ha bisogno del permesso per inviarti notifiche sui prodotti in scadenza.")
                            .setPositiveButton("OK", (dialog, which) -> {
                                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                            })
                            .setNegativeButton("Annulla", (dialog, which) -> dialog.dismiss())
                            .create().show();
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                }
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        askForNotificationPermission();
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main); // Il tuo layout con RecyclerView
        mainCoordinatorLayout = findViewById(R.id.main_coordinator_layout);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.app_name);
        setSupportActionBar(toolbar);

        productViewModel = new ViewModelProvider(this).get(ProductViewModel.class);
        locationViewModel = new ViewModelProvider(this).get(LocationViewModel.class);

        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);

        locationViewModel = new ViewModelProvider(this).get(LocationViewModel.class);
        locationViewPagerAdapter = new LocationViewPagerAdapter(this);
        viewPager.setAdapter(locationViewPagerAdapter);
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            // Il titolo viene preso dall'adapter
            StorageLocation currentLocation = locationViewPagerAdapter.getLocationAt(position);
            if (currentLocation != null) {
                tab.setText(currentLocation.getName());
                // Qui potresti anche impostare icone per i tab se lo desideri
            }
        }).attach();
        observeLocationsForTabs();

        locationViewModel.getAllLocationsSorted().observe(this, newLocations -> {
            if (newLocations != null) {
                locationViewPagerAdapter.setLocations(newLocations);
            }
        });
        fab = findViewById(R.id.fab);
        if (fab.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            originalFabBottomMargin = ((ViewGroup.MarginLayoutParams) fab.getLayoutParams()).bottomMargin;
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
                    0
            );
            ViewGroup.MarginLayoutParams fabParams = (ViewGroup.MarginLayoutParams) fab.getLayoutParams();
            int bottomInset = Math.max(navigationBarsInsets.bottom, imeInsets.bottom);
            fabParams.bottomMargin = originalFabBottomMargin + bottomInset;
            fab.setLayoutParams(fabParams);
            if (viewPager != null) {
                viewPager.registerOnPageChangeCallback(pageChangeCallback);
            }
            return windowInsets;
        });

        fab.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, AddProductActivity.class);
            if (locationViewPagerAdapter != null && locationViewPagerAdapter.getItemCount() > 0) {
                int currentItemPosition = viewPager.getCurrentItem();
                String currentLocationInternalKey = locationViewPagerAdapter.getLocationInternalKeyAt(currentItemPosition);
                intent.putExtra("PRESELECTED_LOCATION_INTERNAL_KEY", currentLocationInternalKey);
            }
            addProductActivityLauncher.launch(intent);
        });

    }
    private void observeLocationsForTabs() {
        locationViewModel.getLocationsForTabs().observe(this, locations -> {
            if (locations != null) {
                locationViewPagerAdapter.setLocations(locations);
            }
        });
    }
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateLayoutToggleIcon();
        restoreSearchViewQuery(); // Assicurati che la SearchView mostri la query corretta
        return super.onPrepareOptionsMenu(menu);
    }
    private void restoreSearchViewQuery() {
        if (searchView != null && productViewModel != null && productViewModel.getSearchQuery().getValue() != null) {
            String currentQueryInViewModel = productViewModel.getSearchQuery().getValue();
            // Evita di resettare la query se l'utente sta attivamente scrivendo,
            // confrontando con la query attuale della SearchView.
            if (!searchView.getQuery().toString().equals(currentQueryInViewModel)) {
                Log.d("SearchViewRestore", "Ripristino query nella SearchView: '" + currentQueryInViewModel + "'");
                // Il parametro 'submit' a false evita di triggerare onQueryTextSubmit inutilmente,
                // poiché l'observer nel fragment dovrebbe già gestire l'aggiornamento della lista.
                searchView.setQuery(currentQueryInViewModel, false);
            }

            if (!currentQueryInViewModel.isEmpty()) {
                if (searchView.isIconified()) { // Solo se è iconificata, espandila
                    searchView.setIconified(false);
                }
            } else {
                // Se la query nel ViewModel è vuota, assicurati che la SearchView sia iconificata
                // a meno che non abbia il focus (l'utente potrebbe stare per scrivere)
                if (!searchView.isIconified() && !searchView.hasFocus()) {
                    searchView.setIconified(true);
                }
            }
        } else if (searchView != null && !searchView.isIconified() && !searchView.hasFocus()) {
            // Se non c'è query nel ViewModel, iconifica la SearchView (se non ha focus)
            searchView.setIconified(true);
        }
    }
    public void updateLayoutToggleIcon() {
        if (layoutToggleMenuItem == null) return;

        Fragment currentFragmentRaw = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
        if (!(currentFragmentRaw instanceof ProductListFragment)) {
            return;
        }
        //layoutToggleMenuItem.setVisible(true);

        ProductListFragment currentFragment = (ProductListFragment) currentFragmentRaw;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String layoutPreferenceKey = ProductListFragment.PREF_LAYOUT_MANAGER_KEY;
        Log.d("MainActivity", "layoutPreferenceKey: " + layoutPreferenceKey);
        String currentLayout = prefs.getString(layoutPreferenceKey, ProductListFragment.LAYOUT_LIST);
        if (currentLayout.equals(ProductListFragment.LAYOUT_GRID)) {
            layoutToggleMenuItem.setIcon(R.drawable.ic_view_list);
            layoutToggleMenuItem.setTitle("Visualizza Lista");
        } else {
            layoutToggleMenuItem.setIcon(R.drawable.ic_view_grid);
            layoutToggleMenuItem.setTitle("Visualizza Griglia");
        }
    }
    @Override
    public void onEditActionClicked(ProductWithCategoryDefinitions productWithCategoryDefinitions) {
        Product product = productWithCategoryDefinitions.product;
        Intent intent = new Intent(MainActivity.this, AddProductActivity.class);
        intent.putExtra("PRODUCT_ID", product.getId());
        intent.putExtra("PRODUCT_BARCODE", product.getBarcode());
        intent.putExtra("PRODUCT_QUANTITY", product.getQuantity());
        intent.putExtra("PRODUCT_EXPIRY_DATE", product.getExpiryDateString());
        intent.putExtra("PRODUCT_NAME", product.getProductName());
        intent.putExtra("PRODUCT_IMAGE", product.getImageUrl());
        intent.putExtra("PRESELECTED_LOCATION_INTERNAL_KEY", product.getStorageLocation());
        addProductActivityLauncher.launch(intent);
    }
    @Override
    public void onDeleteActionClicked(ProductWithCategoryDefinitions productWithCategoryDefinitions) {
        Product product = productWithCategoryDefinitions.product;
        new AlertDialog.Builder(this)
                .setTitle("Conferma Cancellazione")
                .setMessage("Sei sicuro di voler cancellare il prodotto: " + product.getProductName() + "?")
                .setPositiveButton("Cancella", (dialog, which) -> {
                    productViewModel.delete(product);
                    showProductSavedSnackbar("Prodotto cancellato: " + product.getProductName());
                })
                .setNegativeButton("Annulla", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
    @Override
    public void onProductItemClickedForQuantity(ProductWithCategoryDefinitions productWithCategoryDefinitions) {
        Log.d("MainActivity", "onProductItemClickedForQuantity called with product: " + productWithCategoryDefinitions);
        if (productWithCategoryDefinitions == null) return;
        Product product = productWithCategoryDefinitions.product;

        int currentQuantity = product.getQuantity();
        if (currentQuantity > 1) {
            Product updatedProduct = product.copyWithNewQuantity(currentQuantity - 1); // Assicurati che Product abbia questo metodo
            productViewModel.update(updatedProduct);
        } else if (currentQuantity == 1) {
            productViewModel.delete(product);
            Snackbar.make(findViewById(R.id.main_coordinator_layout), // O la tua view radice
                            "\"" + product.getProductName() + "\" rimosso.",
                            Snackbar.LENGTH_LONG)
                    .setAction("ANNULLA", v -> productViewModel.insert(product)) // Azione ANNULLA opzionale
                    .show();
        }
    }
    public void showProductSavedSnackbar(String message) {
        if (mainCoordinatorLayout != null && fab != null) {
            Snackbar snackbar = Snackbar.make(mainCoordinatorLayout, message, Snackbar.LENGTH_LONG);
            snackbar.setAnchorView(fab);
            snackbar.show();
        } else {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
        searchView.setQueryHint("Cerca prodotto...");

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                productViewModel.setSearchQuery(query); // Anche se la ricerca è live, l'invio può essere esplicito
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
            if (productViewModel != null) { // Aggiungi controllo null per sicurezza
                productViewModel.setSearchQuery("");
            }
            return false;
        });

        searchView.setOnQueryTextFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && searchView.getQuery().length() == 0) {
                // Se la SearchView perde focus e la query è vuota,
                // assicurati che il ViewModel rifletta questo (se non già gestito da onQueryTextChange con testo vuoto)
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
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    public boolean onQueryTextSubmit(String query) {
        filter(query);
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        filter(newText);
        return true;
    }

    private void filter(String text) {
        List<ProductWithCategoryDefinitions> filteredList = new ArrayList<>();
        String filterPattern = text.toLowerCase(Locale.getDefault()).trim();
        if (filterPattern.isEmpty()) {
            filteredList.addAll(allProductsList);
        } else {
            for (ProductWithCategoryDefinitions product : allProductsList) {
                boolean nameMatches = product.product.getProductName() != null &&
                        product.product.getProductName().toLowerCase(Locale.getDefault()).contains(filterPattern);
                if (nameMatches) {
                    filteredList.add(product);
                }
            }
        }
        Fragment currentFragment = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
        if (currentFragment instanceof ProductListFragment) {
            ((ProductListFragment) currentFragment).productListAdapter.submitList(filteredList);
            ;
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (viewPager != null) {
            viewPager.registerOnPageChangeCallback(pageChangeCallback);
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
    private ViewPager2.OnPageChangeCallback pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
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