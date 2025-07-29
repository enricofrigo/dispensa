package eu.frigo.dispensa.ui; // o il tuo package ui

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList; // Aggiunto import
import java.util.Collection;

import eu.frigo.dispensa.MainActivity;
import eu.frigo.dispensa.R;
import eu.frigo.dispensa.adapter.ProductListAdapter;
import eu.frigo.dispensa.data.Product; // Per il tipo di adapter se non usi ProductWithCategoryDefinitions
import eu.frigo.dispensa.data.ProductWithCategoryDefinitions; // Se il tuo adapter usa questo
import eu.frigo.dispensa.viewmodel.ProductViewModel;

public class ProductListFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String ARG_STORAGE_LOCATION = "storage_location";
    public String storageLocationFilter;

    private ProductViewModel productViewModel;
    public ProductListAdapter productListAdapter;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    public static final String PREF_LAYOUT_MANAGER_KEY = "layout_manager_type";
    private String currentLayoutPreferenceKey;

    public static final String LAYOUT_GRID = "grid";
    public static final String LAYOUT_LIST = "list";


    public static ProductListFragment newInstance(@Nullable String storageLocation) {
        ProductListFragment fragment = new ProductListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_STORAGE_LOCATION, storageLocation);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            storageLocationFilter = getArguments().getString(ARG_STORAGE_LOCATION);
        }
        currentLayoutPreferenceKey = PREF_LAYOUT_MANAGER_KEY;
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
    }
    private void setupRecyclerViewLayout() {
        if (recyclerView == null || productListAdapter == null) return; // Assicurati che siano inizializzati

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String layoutType = prefs.getString(currentLayoutPreferenceKey, LAYOUT_LIST); // Default a lista

        if (layoutType.equals(LAYOUT_GRID)) {
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            float dpWidth = displayMetrics.widthPixels / displayMetrics.density;
            int desiredColumnWidthDp = 180; // Larghezza desiderata per colonna in DP
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
        if (storageLocationFilter == null) {
            productViewModel.getAllProducts().observe(getViewLifecycleOwner(), products -> {
                if (products != null) {
                    productListAdapter.submitList(new ArrayList<>(products));
                }
            });
        } else {
            productViewModel.getProductsByLocation(storageLocationFilter).observe(getViewLifecycleOwner(), products -> {
                if (products != null) {
                    productListAdapter.submitList(new ArrayList<>(products));
                }
            });
        }
    }
}
