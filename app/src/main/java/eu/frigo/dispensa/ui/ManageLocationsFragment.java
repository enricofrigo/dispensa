package eu.frigo.dispensa.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.adapter.ReorderLocationsAdapter;
import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.storage.StorageLocation;
import eu.frigo.dispensa.util.SimpleItemTouchHelperCallback;
import eu.frigo.dispensa.viewmodel.LocationViewModel;

public class ManageLocationsFragment extends Fragment implements
        ReorderLocationsAdapter.OnStartDragListener,
        ReorderLocationsAdapter.OnLocationInteractionListener,
        SimpleItemTouchHelperCallback.ItemTouchHelperListener {

    private static final String TAG = "ManageLocationsFragment";
    private RecyclerView recyclerViewLocations;
    private ReorderLocationsAdapter adapter;
    private LocationViewModel locationViewModel;
    private ItemTouchHelper itemTouchHelper;
    private FloatingActionButton fabAddLocation;
    private MaterialToolbar toolbar;
    private SwitchMaterial switchTabStyle;

    public ManageLocationsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationViewModel = new ViewModelProvider(this).get(LocationViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manage_locations, container, false);
        toolbar = view.findViewById(R.id.toolbar_manage_locations);
        recyclerViewLocations = view.findViewById(R.id.recyclerView_locations);
        fabAddLocation = view.findViewById(R.id.fab_add_location);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        locationViewModel = new ViewModelProvider(requireActivity()).get(LocationViewModel.class);

        switchTabStyle = view.findViewById(R.id.switch_tab_style);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        switchTabStyle.setChecked(prefs.getBoolean("pref_predefined_tab_icon", false));
        switchTabStyle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("pref_predefined_tab_icon", isChecked).apply();
        });

        setupRecyclerView();
        observeLocations();

        fabAddLocation.setOnClickListener(v -> showAddLocationDialog());

        toolbar.setTitle(getString(R.string.title_manage_locations));
    }

    private void setupRecyclerView() {
        adapter = new ReorderLocationsAdapter(new ArrayList<>(), this, this);
        recyclerViewLocations.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewLocations.setAdapter(adapter);

        SimpleItemTouchHelperCallback callback = new SimpleItemTouchHelperCallback(adapter, this);
        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recyclerViewLocations);
    }

    private void observeLocations() {
        locationViewModel.getAllLocationsSorted().observe(getViewLifecycleOwner(), locations -> {
            if (locations != null) {
                boolean hasDefault = false;
                for (StorageLocation loc : locations) {
                    if (loc.isDefault()) {
                        hasDefault = true;
                        break;
                    }
                }

                List<StorageLocation> listWithAll = new ArrayList<>();
                StorageLocation allTab = new StorageLocation();
                allTab.setInternalKey(LocationViewModel.ALL_PRODUCTS_INTERNAL_KEY);
                allTab.setId(LocationViewModel.ALL_PRODUCTS_TAB_ID);
                allTab.setPredefined(true);
                allTab.setDefault(!hasDefault); // Se non c'è nessun default esplicitato, "Tutti" è il default
                allTab.setName(getString(R.string.tab_title_all_products));
                listWithAll.add(allTab);
                
                listWithAll.addAll(locations);
                adapter.submitList(listWithAll);
            }
        });
    }

    @Override
    public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
        if (itemTouchHelper != null) {
            itemTouchHelper.startDrag(viewHolder);
        }
    }

    @Override
    public void onEditLocation(StorageLocation location) {
        showEditLocationDialog(location);
    }

    @Override
    public void onDeleteLocation(StorageLocation location) {
        if (location.isPredefined) {
            Toast.makeText(getContext(), getString(R.string.error_delete_default_location), Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.delete_location_confirmation_title))
                .setMessage(String.format(getString(R.string.error_delete_location_confirmation), location.name))
                .setPositiveButton(getString(R.string.reomve), (dialog, which) -> {
                    locationViewModel.delete(location);
                    Toast.makeText(getContext(),
                            String.format(getString(R.string.notify_delete_location), location.name),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    public void onOrderChanged(List<StorageLocation> orderedLocations) {
        for (int i = 0; i < orderedLocations.size(); i++) {
            orderedLocations.get(i).setOrderIndex(i);
        }
        locationViewModel.updateOrder(orderedLocations);
        Toast.makeText(getContext(), getString(R.string.notify_saved), Toast.LENGTH_SHORT).show();
    }

    private void showAddLocationDialog() {
        showLocationDialog(null);
    }

    private void showEditLocationDialog(StorageLocation location) {
        showLocationDialog(location);
    }

    @Override
    public void onSetAsDefault(StorageLocation locationToSetAsDefault) {
        Log.d(TAG, "onSetAsDefault: " + locationToSetAsDefault);
        locationViewModel.setAsDefault(locationToSetAsDefault);
    }

    private void showLocationDialog(@Nullable final StorageLocation existingLocation) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        String dialogTitle = existingLocation == null ? getString(R.string.add_new_location)
                : getString(R.string.edit_location);
        builder.setTitle(dialogTitle);

        final EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final int MAX_LOCATION_NAME_LENGTH = 25;
        InputFilter[] filters = new InputFilter[1];
        filters[0] = new InputFilter.LengthFilter(MAX_LOCATION_NAME_LENGTH);
        input.setFilters(filters);
        if (existingLocation != null) {
            input.setText(eu.frigo.dispensa.util.LocationFormatter.getLocalizedName(getContext(), existingLocation));
            if (existingLocation.isPredefined()) {
                input.setEnabled(false);
            }
        }
        builder.setView(input);
        builder.setPositiveButton(existingLocation == null ? getString(R.string.add) : getString(R.string.save),
                (dialog, which) -> {
                    String locationName = input.getText().toString().trim();
                    if (locationName.isEmpty()) {
                        Toast.makeText(getContext(), getString(R.string.error_empty_location), Toast.LENGTH_SHORT)
                                .show();
                        return;
                    }

                    if (existingLocation == null) { // Aggiungi Nuova
                        locationViewModel.insert(new StorageLocation(), maxIndex -> {
                            requireActivity().runOnUiThread(() -> {
                                StorageLocation newLocation = new StorageLocation(
                                        locationName,
                                        "custom_" + UUID.randomUUID().toString().substring(0, 8), // internalKey univoco
                                        maxIndex + 1, // orderIndex
                                        false, // isDefault
                                        false // isPredefined
                                );
                                AppDatabase.databaseWriteExecutor.execute(() -> {
                                    AppDatabase.getDatabase(requireContext()).storageLocationDao().insert(newLocation);
                                });

                                Toast.makeText(getContext(),
                                        String.format(getString(R.string.notify_add_location), locationName),
                                        Toast.LENGTH_SHORT).show();
                            });
                        });
                    } else {
                        if (!existingLocation.isPredefined()) {
                            existingLocation.setName(locationName);
                        }
                        locationViewModel.update(existingLocation);
                        Toast.makeText(getContext(), getString(R.string.notify_update_location), Toast.LENGTH_SHORT)
                                .show();
                    }
                });
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }
}