package eu.frigo.dispensa.ui;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.adapter.ReorderLocationsAdapter;
import eu.frigo.dispensa.adapter.StorageLocationAdapter;
import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.StorageLocation;
import eu.frigo.dispensa.helper.SimpleItemTouchHelperCallback;
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

        setupRecyclerView();
        observeLocations();

        fabAddLocation.setOnClickListener(v -> showAddLocationDialog());

        // Configura il titolo o il pulsante di navigazione sulla toolbar
        toolbar.setTitle("Store location management");
        // Se hai un menu nella toolbar:
        // toolbar.setOnMenuItemClickListener(item -> { ... });
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
                adapter.submitList(new ArrayList<>(locations));
            }
        });
    }

    @Override
    public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
        if (itemTouchHelper != null) {
            itemTouchHelper.startDrag(viewHolder);
        }
    }

    private void showAddEditLocationDialog(@Nullable final StorageLocation locationToEdit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(locationToEdit == null ? "Aggiungi Nuova Location" : "Modifica Location");

        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        if (locationToEdit != null) {
            input.setText(locationToEdit.name);
            input.setSelection(input.getText().length()); // Posiziona cursore alla fine
        } else {
            input.setHint("Nome della location");
        }
        builder.setView(input);

        builder.setPositiveButton(locationToEdit == null ? "Aggiungi" : "Salva", (dialog, which) -> {
            String locationName = input.getText().toString().trim();
            if (locationName.isEmpty()) {
                Toast.makeText(getContext(), "Il nome non può essere vuoto", Toast.LENGTH_SHORT).show();
                return;
            }

            if (locationToEdit == null) { // Aggiungi nuova
                // Genera una chiave interna semplice (potresti volerla più robusta/univoca)
                String internalKey = "CUSTOM_" + locationName.toUpperCase().replaceAll("\\s+", "_") + "_" + System.currentTimeMillis() % 10000;
                // L'orderIndex sarà gestito dal DB o impostato al massimo + 1
                // Per ora, assumiamo che le nuove vengano aggiunte in fondo.
                // Il ViewModel/Repository dovrebbe gestire l'orderIndex corretto all'inserimento.
                int currentMaxOrder = adapter.getItemCount(); // Stima semplice, potrebbe non essere precisa se la lista è filtrata
                StorageLocation newLocation = new StorageLocation(locationName, internalKey, currentMaxOrder, false, false);
                locationViewModel.insert(newLocation);
                Toast.makeText(getContext(), "Location '" + locationName + "' aggiunta", Toast.LENGTH_SHORT).show();
            } else { // Modifica esistente
                if (!locationToEdit.name.equals(locationName)) {
                    locationToEdit.name = locationName;
                    locationViewModel.update(locationToEdit);
                    Toast.makeText(getContext(), "Location aggiornata", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("Annulla", (dialog, which) -> dialog.cancel());
        builder.show();
    }


    @Override
    public void onEditLocation(StorageLocation location) {
        showEditLocationDialog(location);
    }

    @Override
    public void onDeleteLocation(StorageLocation location) {
        if (location.isPredefined) {
            Toast.makeText(getContext(), "Le location predefinite non possono essere eliminate.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (location.isDefault) {
            Toast.makeText(getContext(), "Non puoi eliminare la location di default. Impostane un'altra prima.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Conferma Eliminazione")
                .setMessage("Sei sicuro di voler eliminare la location '" + location.name + "'?\nI prodotti in questa location potrebbero dover essere riassegnati.")
                .setPositiveButton("Elimina", (dialog, which) -> {
                    locationViewModel.delete(location);
                    Toast.makeText(getContext(), "Location '" + location.name + "' eliminata", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    public void onOrderChanged(List<StorageLocation> orderedLocations) {
        // Assegna il nuovo orderIndex
        for (int i = 0; i < orderedLocations.size(); i++) {
            orderedLocations.get(i).setOrderIndex(i);
        }
        locationViewModel.updateOrder(orderedLocations);
        Toast.makeText(getContext(), "Salvataggio effettuato", Toast.LENGTH_SHORT).show();
    }

    private void showAddLocationDialog() {
        showLocationDialog(null);
    }

    private void showEditLocationDialog(StorageLocation location) {
        showLocationDialog(location);
    }
    @Override
    public void onSetAsDefault(StorageLocation locationToSetAsDefault) {
        Log.d(TAG, "onSetAsDefault: "+locationToSetAsDefault);
        List<StorageLocation> currentList = adapter.getCurrentOrder(); // o osserva un LiveData non filtrato se necessario
        StorageLocation oldDefault = null;
        for (StorageLocation loc : currentList) {
            if (loc.isDefault() && loc.getId() != locationToSetAsDefault.getId()) {
                oldDefault = loc;
                break;
            }
        }

        List<StorageLocation> locationsToUpdate = new ArrayList<>();

        if (oldDefault != null) {
            StorageLocation modifiedOldDefault = findLocationById(currentList, oldDefault.getId());
            if(modifiedOldDefault != null) {
                modifiedOldDefault.setDefault(false);
                locationsToUpdate.add(modifiedOldDefault);
            }
        }
        StorageLocation modifiedNewDefault = findLocationById(currentList, locationToSetAsDefault.getId());
        if(modifiedNewDefault != null) {
            modifiedNewDefault.setDefault(true);
            locationsToUpdate.add(modifiedNewDefault);
        }

        if (!locationsToUpdate.isEmpty()) {
            if (oldDefault != null) {
                locationViewModel.update(oldDefault);
            }
            locationViewModel.update(locationToSetAsDefault);
            Toast.makeText(getContext(), "Location di default impostata "+locationToSetAsDefault.getName(), Toast.LENGTH_SHORT).show();
        }
    }
    private StorageLocation findLocationById(List<StorageLocation> locations, int id) {
        for (StorageLocation loc : locations) {
            if (loc.getId() == id) {
                return loc;
            }
        }
        return null;
    }
    private void showLocationDialog(@Nullable final StorageLocation existingLocation) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        String dialogTitle = existingLocation == null ? "Nuova Location" : "Edit Location";
        builder.setTitle(dialogTitle);

        final EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final int MAX_LOCATION_NAME_LENGTH = 25; // Scegli la lunghezza massima desiderata
        InputFilter[] filters = new InputFilter[1];
        filters[0] = new InputFilter.LengthFilter(MAX_LOCATION_NAME_LENGTH);
        input.setFilters(filters);
        if (existingLocation != null) {
            input.setText(existingLocation.getName());
            if (existingLocation.isPredefined()) { // Non permettere la modifica del nome delle predefinite
                input.setEnabled(false);
            }
        }
        builder.setView(input);
        builder.setPositiveButton(existingLocation == null ? "Aggiungi" : "Salva", (dialog, which) -> {
            String locationName = input.getText().toString().trim();
            if (locationName.isEmpty()) {
                Toast.makeText(getContext(), "Location non può essere vuota", Toast.LENGTH_SHORT).show();
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
                                false  // isPredefined
                        );
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            AppDatabase.getDatabase(requireContext()).storageLocationDao().insert(newLocation);
                        });

                        Toast.makeText(getContext(), "Location '" + locationName + "' aggiunta", Toast.LENGTH_SHORT).show();
                    });
                });
            } else { // Modifica Esistente
                if (!existingLocation.isPredefined()) { // Modifica il nome solo se non è predefinita
                    existingLocation.setName(locationName);
                }
                // Altri campi (isDefault) potrebbero essere modificati qui se necessario
                locationViewModel.update(existingLocation);
                Toast.makeText(getContext(), "Location '" + locationName + "' aggiornata", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }
}