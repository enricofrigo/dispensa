package eu.frigo.dispensa.ui;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import eu.frigo.dispensa.R;
import eu.frigo.dispensa.adapter.StorageLocationAdapter;
import eu.frigo.dispensa.data.StorageLocation;
import eu.frigo.dispensa.viewmodel.LocationViewModel;

public class ManageLocationsFragment extends Fragment implements StorageLocationAdapter.OnLocationInteractionListener {

    private static final String TAG = "ManageLocationsFragment";
    private LocationViewModel locationViewModel;
    private StorageLocationAdapter adapter;
    private RecyclerView recyclerView;

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

        recyclerView = view.findViewById(R.id.recyclerView_locations);
        FloatingActionButton fabAddLocation = view.findViewById(R.id.fab_add_location);

        setupRecyclerView();

        fabAddLocation.setOnClickListener(v -> showAddEditLocationDialog(null));

        locationViewModel.getAllLocationsSorted().observe(getViewLifecycleOwner(), locations -> {
            if (locations != null) {
                Log.d(TAG, "Locations aggiornate: " + locations.size());
                adapter.submitList(locations);
            }
        });

        return view;
    }

    private void setupRecyclerView() {
        adapter = new StorageLocationAdapter(new StorageLocationAdapter.StorageLocationDiff(), this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        // Qui aggiungeremo ItemTouchHelper per il drag and drop più avanti
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
        if (location.isPredefined) {
            Toast.makeText(getContext(), "Le location predefinite non possono essere modificate qui.", Toast.LENGTH_SHORT).show();
            return;
        }
        showAddEditLocationDialog(location);
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

    @Override
    public void onSetAsDefault(StorageLocation location) {
        if (!location.isDefault) {
            locationViewModel.setAsDefault(location);
            Toast.makeText(getContext(), "'" + location.name + "' impostata come default.", Toast.LENGTH_SHORT).show();
        }
    }
}
