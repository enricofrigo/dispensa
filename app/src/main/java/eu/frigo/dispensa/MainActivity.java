package eu.frigo.dispensa;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.Log;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import eu.frigo.dispensa.adapter.ProductListAdapter;
import eu.frigo.dispensa.data.Product;
import eu.frigo.dispensa.databinding.ActivityMainBinding;
import eu.frigo.dispensa.ui.SettingsActivity;
import eu.frigo.dispensa.viewmodel.MainViewModel;

import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements ProductListAdapter.OnItemInteractionListener{

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private MainViewModel mainViewModel;
    private ProductListAdapter adapter;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FloatingActionButton fab;
    private CoordinatorLayout mainCoordinatorLayout;
    private int originalFabBottomMargin;

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
                    String newBarcode = result.getData().getStringExtra("NEW_PRODUCT_BARCODE");
                    if (newBarcode != null) {
                        message = "Prodotto aggiunto: " + newBarcode;
                    } else {
                        message ="Nuovo prodotto aggiunto!";
                    }
                } else if (result.getResultCode() == AppCompatActivity.RESULT_CANCELED) {
                    message = "Aggiunta prodotto annullata.";
                }
                if (message != null) showProductSavedSnackbar(message);
            });

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
        adapter = new ProductListAdapter(new ProductListAdapter.ProductDiff(),this);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setOnCreateContextMenuListener(this);

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        // Inizializza il ViewModel
        mainViewModel = new ViewModelProvider(this).get(MainViewModel.class);

        // Osserva i LiveData dal ViewModel
        mainViewModel.getAllProducts().observe(this, products -> {
            // Aggiorna la copia cache dei prodotti nell'adapter.
            // ListAdapter gestirà l'animazione e gli aggiornamenti della UI.
            adapter.submitList(products);
        });

        swipeRefreshLayout.setOnRefreshListener(() -> {
                    Log.d("MainActivity", "Pull to refresh triggered!");
                    mainViewModel.refreshProducts();
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
            originalFabBottomMargin = 0; // O un valore di default appropriato
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
    private void askForNotificationPermission() {
        // Questo è necessario solo da Android 13 (API 33) in su
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED) {
                // Il permesso è già concesso
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // Mostra una UI educativa all'utente prima di richiedere di nuovo il permesso
                // Ad esempio, un AlertDialog che spiega perché hai bisogno delle notifiche
                new AlertDialog.Builder(this)
                        .setTitle("Permesso Notifiche Richiesto")
                        .setMessage("L'app ha bisogno del permesso per inviarti notifiche sui prodotti in scadenza.")
                        .setPositiveButton("OK", (dialog, which) -> {
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                        })
                        .setNegativeButton("Annulla", (dialog, which) -> dialog.dismiss())
                        .create().show();
            } else {
                // Richiedi direttamente il permesso
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }
    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        Product selectedProduct = adapter.getSelectedProduct();

        if (selectedProduct == null) {
            return super.onContextItemSelected(item);
        }

        int itemId = item.getItemId();
        if (itemId == R.id.action_edit_product) {
            // Azione Modifica
            Intent intent = new Intent(MainActivity.this, AddProductActivity.class);
            intent.putExtra("PRODUCT_ID", selectedProduct.getId());
            intent.putExtra("PRODUCT_BARCODE", selectedProduct.getBarcode());
            intent.putExtra("PRODUCT_QUANTITY", selectedProduct.getQuantity());
            intent.putExtra("PRODUCT_EXPIRY_DATE", selectedProduct.getExpiryDateString());
            intent.putExtra("PRODUCT_NAME", selectedProduct.getProductName());
            intent.putExtra("PRODUCT_IMAGE", selectedProduct.getImageUrl());
            addProductActivityLauncher.launch(intent);
            return true;
        } else if (itemId == R.id.action_delete_product) {
            // Azione Cancella - Mostra un dialogo di conferma
            new AlertDialog.Builder(this)
                    .setTitle("Conferma Cancellazione")
                    .setMessage("Sei sicuro di voler cancellare il prodotto: " + selectedProduct.getProductName() + "?")
                    .setPositiveButton("Cancella", (dialog, which) -> {
                        mainViewModel.delete(selectedProduct); // Chiama il metodo delete nel ViewModel
                        showProductSavedSnackbar("Prodotto cancellato: " + selectedProduct.getProductName());
                    })
                    .setNegativeButton("Annulla", null)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
            return true;
        } else {
            return super.onContextItemSelected(item);
        }
    }
    public void showProductSavedSnackbar(String message) {
        if (mainCoordinatorLayout != null && fab != null) {
            Snackbar snackbar = Snackbar.make(mainCoordinatorLayout, message, Snackbar.LENGTH_LONG);
            snackbar.setAnchorView(fab); // ANCORA QUI
            snackbar.show();
        } else {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    @Override
    public void onEditProduct(Product product) {
        Intent intent = new Intent(MainActivity.this, AddProductActivity.class);
        intent.putExtra("PRODUCT_ID", product.getId());
        intent.putExtra("PRODUCT_BARCODE", product.getBarcode());
        intent.putExtra("PRODUCT_QUANTITY", product.getQuantity());
        intent.putExtra("PRODUCT_EXPIRY_DATE", product.getExpiryDateString());
        addProductActivityLauncher.launch(intent);
    }

    @Override
    public void onDeleteProduct(Product product) {
        new AlertDialog.Builder(this)
                .setTitle("Conferma Cancellazione")
                .setMessage("Sei sicuro di voler cancellare il prodotto: " + product.getBarcode() + "?")
                .setPositiveButton("Cancella", (dialog, which) -> {
                    mainViewModel.delete(product);
                    showProductSavedSnackbar("Prodotto cancellato: " + product.getBarcode());
                })
                .setNegativeButton("Annulla", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
}