package eu.frigo.dispensa.ui; // o il tuo package ui

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import eu.frigo.dispensa.MainActivity;
import eu.frigo.dispensa.R;
import eu.frigo.dispensa.adapter.ProductListAdapter;
import eu.frigo.dispensa.data.ProductWithCategoryDefinitions;
import eu.frigo.dispensa.viewmodel.LocationViewModel;
import eu.frigo.dispensa.viewmodel.ProductViewModel;

public class ProductListFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String ARG_LOCATION_INTERNAL_KEY = "location_internal_key";
    private static final String ARG_IS_ALL_PRODUCTS_MODE = "is_all_products_mode";
    public String storageLocationFilter;
    private ProductViewModel productViewModel;
    public ProductListAdapter productListAdapter;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    public static final String PREF_LAYOUT_MANAGER_KEY = "layout_manager_type";
    private String currentLayoutPreferenceKey;
    public static final String LAYOUT_GRID = "grid";
    public static final String LAYOUT_LIST = "list";
    private List<ProductWithCategoryDefinitions> originalProductList = new ArrayList<>();
    private LiveData<List<ProductWithCategoryDefinitions>> productsLiveData = new MutableLiveData<>();
    private String currentSearchQuery = "";
    private boolean isAllProductsMode = true;
    private long specificLocationId = -1L;
    private String locationInternalKeyFilter;

    public static ProductListFragment newInstance(@Nullable String internalKey) {
        ProductListFragment fragment = new ProductListFragment();
        Bundle args = new Bundle();
        if (internalKey != null) {
            args.putString(ARG_LOCATION_INTERNAL_KEY, internalKey);
        }
        args.putBoolean(ARG_IS_ALL_PRODUCTS_MODE, false);
        fragment.setArguments(args);
        Log.d("PLF_Factory", "newInstanceForLocation created with ID: " + internalKey);
        return fragment;
    }
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            locationInternalKeyFilter = getArguments().getString(ARG_LOCATION_INTERNAL_KEY);
            if (locationInternalKeyFilter == null) {
                locationInternalKeyFilter = LocationViewModel.ALL_PRODUCTS_INTERNAL_KEY;
                Log.w("ProductListFragment", "onCreate: locationInternalKeyFilter is null after getting from args.");
            }
        } else {
            // locationInternalKeyFilter = LocationViewModel.ALL_PRODUCTS_INTERNAL_KEY;
            Log.w("ProductListFragment", "onCreate: getArguments() is null.");
        }        currentLayoutPreferenceKey = PREF_LAYOUT_MANAGER_KEY;
        productViewModel = new ViewModelProvider(requireActivity()).get(ProductViewModel.class);
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .registerOnSharedPreferenceChangeListener(this);
        Log.d("ProductListFragment", "onCreate called with storageLocationFilter: " + storageLocationFilter);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_product_list, container, false); // Crea questo layout
        recyclerView = view.findViewById(R.id.recyclerViewProductsFragment);
        productListAdapter = new ProductListAdapter(new ProductListAdapter.ProductDiff(), (ProductListAdapter.OnProductInteractionListener) getActivity());
        recyclerView.setAdapter(productListAdapter);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            productViewModel.refreshProducts();
            swipeRefreshLayout.setRefreshing(false);
        });
        swipeRefreshLayout.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);

        setupRecyclerViewLayout();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        observeProducts();
        observeSearchQuery();
        productsLiveData.observe(getViewLifecycleOwner(), products -> {
            if (products != null) {
                originalProductList = new ArrayList<>(products); // AGGIORNAMENTO
                Log.d("ProductListFragment", "Fragment (" + getUniqueKeyPart() + "): Prodotti originali ricevuti e originalProductList aggiornata. Dimensione: " + originalProductList.size());
            } else {
                originalProductList = new ArrayList<>();
                Log.d("ProductListFragment", "Fragment (" + getUniqueKeyPart() + "): Prodotti ricevuti sono null. originalProductList svuotata.");
            }
            filterAndSubmitList();
        });
    }
    private void setupRecyclerViewLayout() {
        if (recyclerView == null || productListAdapter == null) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String layoutType = prefs.getString(currentLayoutPreferenceKey, LAYOUT_LIST);

        if (layoutType.equals(LAYOUT_GRID)) {
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            float dpWidth = displayMetrics.widthPixels / displayMetrics.density;
            int desiredColumnWidthDp = 180;
            int spanCount = Math.max(1, (int) (dpWidth / desiredColumnWidthDp));
            recyclerView.setLayoutManager(new GridLayoutManager(getContext(), spanCount));
        } else {
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        }
    }
    public void toggleLayoutManager() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String currentLayout = prefs.getString(currentLayoutPreferenceKey, LAYOUT_LIST);
        String newLayout = currentLayout.equals(LAYOUT_LIST) ? LAYOUT_GRID : LAYOUT_LIST;
        Log.d("ProductListFragment", "Toggling layout to: " + currentLayoutPreferenceKey);
        prefs.edit().putString(currentLayoutPreferenceKey, newLayout).apply();
        // setupRecyclerViewLayout() sarà chiamato da onSharedPreferenceChanged
        // o puoi chiamarlo direttamente se preferisci un aggiornamento immediato
        //setupRecyclerViewLayout(); // Rimuovilo se usi onSharedPreferenceChanged
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d("ProductListFragment", "onSharedPreferenceChanged called with key: " + key);
        if (key != null && key.equals(currentLayoutPreferenceKey)) {
            setupRecyclerViewLayout(); // Aggiorna il layout quando la preferenza cambia
            // Potrebbe essere necessario invalidare l'icona del menu in MainActivity
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).invalidateOptionsMenu();
            }
        }
    }
    public void onDestroy() {
        super.onDestroy();
        Log.d("ProductListFragment", "onDestroy called");
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .unregisterOnSharedPreferenceChangeListener(this);
    }
    private void observeProducts() {

        if (locationInternalKeyFilter == null || locationInternalKeyFilter.equals(LocationViewModel.ALL_PRODUCTS_INTERNAL_KEY)) {
            Log.d("ProductListFragment", "Observing ALL products for " + getUniqueKeyPart());
            productsLiveData = productViewModel.getAllProductsWithCategories();
        } else {
            Log.d("ProductListFragment", "Observing products for location internalKey: " + locationInternalKeyFilter + " for " + getUniqueKeyPart());
            productsLiveData = productViewModel.getProductsByLocationInternalKey(locationInternalKeyFilter); //  <<<< DEVI CREARE QUESTO METODO NEL VIEWMODEL
        }
        if (productsLiveData == null) {
            Log.e("ProductListFragment", "productsLiveData is NULL for " + getUniqueKeyPart() + ". Mode: All=" + isAllProductsMode + ", LocID=" + specificLocationId);
            originalProductList = new ArrayList<>();
            filterAndSubmitList();
            return;
        }
        productsLiveData.observe(getViewLifecycleOwner(), products -> {
            if (products != null) {
                originalProductList = new ArrayList<>(products); // Copia la lista ricevuta in originalProductList
                Log.d("ProductListFragment", "Fragment (" + getUniqueKeyPart() + "): Prodotti originali ricevuti e originalProductList aggiornata. Dimensione: " + originalProductList.size());
            } else {
                originalProductList = new ArrayList<>(); // Se i prodotti sono null, inizializza a lista vuota
                Log.d("ProductListFragment", "Fragment (" + getUniqueKeyPart() + "): Prodotti ricevuti sono null. originalProductList svuotata.");
            }
            filterAndSubmitList(); // Applica comunque il filtro (che risulterà in una lista vuota per l'adapter)
        });
    }
    private void observeSearchQuery() {
        productViewModel.getSearchQuery().observe(getViewLifecycleOwner(), query -> {
            currentSearchQuery = (query == null) ? "" : query.toLowerCase(Locale.getDefault());
            Log.d("ProductListFragment", "Fragment (" + getUniqueKeyPart() + "): Nuova query di ricerca: '" + currentSearchQuery + "'");
            filterAndSubmitList(); // Riapplica il filtro quando la query cambia
        });
    }
    private void filterAndSubmitList() {
        if (originalProductList == null) {
            if (productListAdapter != null) {
                productListAdapter.submitList(new ArrayList<>()); // Svuota la lista se originalProductList è null
            }
            return;
        }

        List<ProductWithCategoryDefinitions> filteredList = new ArrayList<>();
        if (currentSearchQuery.isEmpty()) {
            filteredList.addAll(originalProductList);
        } else {
            for (ProductWithCategoryDefinitions productWithDefs : originalProductList) {
                String productName = productWithDefs.product.getProductName() != null ? productWithDefs.product.getProductName().toLowerCase(Locale.getDefault()) : "";
                String barcode = productWithDefs.product.getBarcode() != null ? productWithDefs.product.getBarcode().toLowerCase(Locale.getDefault()) : "";

                if (productName.contains(currentSearchQuery) || barcode.contains(currentSearchQuery)) {
                    filteredList.add(productWithDefs);
                }
            }
        }
        Log.d("ProductListFragment", "Fragment (" + getUniqueKeyPart() + "): Lista filtrata (" + currentSearchQuery + "): " + filteredList.size() + " elementi.");
        if (productListAdapter != null) {
            productListAdapter.submitList(filteredList);
        } else {
            Log.e("ProductListFragment", "ProductListAdapter è null in filterAndSubmitList!");
        }
    }
    private String getUniqueKeyPart() {
        return (locationInternalKeyFilter == null) ? "unknown_key" : locationInternalKeyFilter;
    }
}
