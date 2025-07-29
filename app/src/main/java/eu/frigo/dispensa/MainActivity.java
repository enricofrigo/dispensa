package eu.frigo.dispensa;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

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
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.Log;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import eu.frigo.dispensa.adapter.ProductListAdapter;
import eu.frigo.dispensa.data.Product;
import eu.frigo.dispensa.data.ProductWithCategoryDefinitions;
import eu.frigo.dispensa.databinding.ActivityMainBinding;
import eu.frigo.dispensa.ui.SettingsActivity;
import eu.frigo.dispensa.viewmodel.ProductViewModel;

import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SearchView.OnQueryTextListener, ProductListAdapter.OnProductInteractionListener{

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private ProductViewModel productViewModel;
    private ProductListAdapter adapter;
    private List<ProductWithCategoryDefinitions> allProductsList = new ArrayList<>();
    private SearchView searchView;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FloatingActionButton fab;
    private CoordinatorLayout mainCoordinatorLayout;
    private int originalFabBottomMargin;
    private static final String PREF_LAYOUT_MANAGER_KEY = "layout_manager_preference";
    private static final int LAYOUT_MANAGER_LIST = 0;
    private static final int LAYOUT_MANAGER_GRID = 1;
    private int currentLayoutManagerType;
    private MenuItem layoutToggleMenuItem;

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

        recyclerView = findViewById(R.id.recyclerViewProducts);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        currentLayoutManagerType = prefs.getInt(PREF_LAYOUT_MANAGER_KEY, LAYOUT_MANAGER_LIST); // Default a lista

        adapter = new ProductListAdapter(new ProductListAdapter.ProductDiff(),this);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setOnCreateContextMenuListener(this);
        setLayoutManager(currentLayoutManagerType);

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        productViewModel = new ViewModelProvider(this).get(ProductViewModel.class);

        productViewModel.getAllProducts().observe(this, products -> {
            allProductsList.clear();
            if (products != null) {
                allProductsList.addAll(products);
            }
            if (searchView != null && searchView.getQuery() != null && !searchView.getQuery().toString().isEmpty()) {
                filter(searchView.getQuery().toString());
            } else {
                adapter.submitList(new ArrayList<>(allProductsList)); // Mostra tutti se non c'è query
            }
        });

        productViewModel.getAllProducts().observe(this, products -> {
            adapter.submitList(products);
        });

        swipeRefreshLayout.setOnRefreshListener(() -> {
                    productViewModel.refreshProducts();
                    swipeRefreshLayout.setRefreshing(false);
                });
        swipeRefreshLayout.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);

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

            return windowInsets;
        });

        fab.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, AddProductActivity.class);
            addProductActivityLauncher.launch(intent);
        });

        registerForContextMenu(recyclerView);
    }
    private int calculateSpanCount() {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        float dpWidth = displayMetrics.widthPixels / displayMetrics.density;
        int itemWidthDp = 180;
        int spanCount = (int) (dpWidth / itemWidthDp);
        return Math.max(2, spanCount);
    }
    private void setLayoutManager(int layoutType) {
        if (layoutType == LAYOUT_MANAGER_GRID) {
            int spanCount = calculateSpanCount();
            recyclerView.setLayoutManager(new GridLayoutManager(this, spanCount));
            currentLayoutManagerType = LAYOUT_MANAGER_GRID;
        } else {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            currentLayoutManagerType = LAYOUT_MANAGER_LIST;
        }
        // adapter.notifyDataSetChanged(); // Usalo con cautela, ListAdapter gestisce meglio gli aggiornamenti.
    }
    private void updateLayoutToggleIcon() {
        if (layoutToggleMenuItem != null) {
            if (currentLayoutManagerType == LAYOUT_MANAGER_GRID) {
                layoutToggleMenuItem.setIcon(R.drawable.ic_view_list);
                layoutToggleMenuItem.setTitle("Visualizza come Lista");
            } else {
                layoutToggleMenuItem.setIcon(R.drawable.ic_view_grid);
                layoutToggleMenuItem.setTitle("Visualizza come Griglia");
            }
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
        layoutToggleMenuItem = menu.findItem(R.id.action_toggle_layout);
        updateLayoutToggleIcon();
        searchView = (SearchView) searchItem.getActionView();

        if (searchView != null) {
            searchView.setOnQueryTextListener(this);
            searchView.setQueryHint("Cerca prodotti..."); // Hint opzionale

            searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    // Nascondi altri item di menu se vuoi (es. Impostazioni)
                    // setMenuItemsVisibility(menu, item, false);
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    // Mostra di nuovo gli altri item di menu
                    // setMenuItemsVisibility(menu, null, true);
                    invalidateOptionsMenu();
                    return true;
                }
            });
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_toggle_layout) {
            toggleLayoutManager();
            return true;
        } else if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    private void toggleLayoutManager() {
        if (currentLayoutManagerType == LAYOUT_MANAGER_LIST) {
            setLayoutManager(LAYOUT_MANAGER_GRID);
        } else {
            setLayoutManager(LAYOUT_MANAGER_LIST);
        }
        updateLayoutToggleIcon();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putInt(PREF_LAYOUT_MANAGER_KEY, currentLayoutManagerType).apply();
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
        adapter.submitList(filteredList);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (searchView != null && !searchView.isIconified()) {
            searchView.setIconified(true);
            searchView.onActionViewCollapsed();
        }
    }
}