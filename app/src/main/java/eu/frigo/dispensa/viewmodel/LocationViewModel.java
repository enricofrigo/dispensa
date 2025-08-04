package eu.frigo.dispensa.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.Repository;
import eu.frigo.dispensa.data.storage.StorageLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocationViewModel extends AndroidViewModel {

    private Repository repository;
    private MediatorLiveData<List<StorageLocation>> locationsForTabs;
    private LiveData<List<StorageLocation>> dbRealLocationsSorted;
    private LiveData<StorageLocation> defaultLocation;
    public static final String ALL_PRODUCTS_INTERNAL_KEY = "all_products_key";
    public static final int ALL_PRODUCTS_TAB_ID = -1;

    public LocationViewModel(@NonNull Application application) {
        super(application);
        repository = new Repository(application);
        defaultLocation = repository.getDefaultLocation();
        locationsForTabs = new MediatorLiveData<>();
        dbRealLocationsSorted = repository.getAllLocationsSorted();
        locationsForTabs.addSource(dbRealLocationsSorted, realLocations -> {
            List<StorageLocation> displayList = new ArrayList<>();

            StorageLocation allTab = new StorageLocation();
            allTab.setInternalKey(ALL_PRODUCTS_INTERNAL_KEY);
            allTab.setId(ALL_PRODUCTS_TAB_ID);
            allTab.setPredefined(true);
            allTab.setName(application.getString(R.string.tab_title_all_products));
            displayList.add(allTab);
            if (realLocations != null) {
                displayList.addAll(realLocations);
            }

            locationsForTabs.setValue(displayList);
        });
    }
    public LiveData<List<StorageLocation>> getLocationsForTabs() {
        return locationsForTabs;
    }
    public LiveData<List<StorageLocation>> getAllLocationsSorted() {
        return dbRealLocationsSorted;
    }

    public LiveData<StorageLocation> getDefaultLocation() {
        return defaultLocation;
    }

    public void insert(StorageLocation location) {
        repository.insertLocation(location);
    }

    public void update(StorageLocation location) {
        repository.updateLocation(location);
    }

    public void delete(StorageLocation location) {
        repository.deleteLocation(location);
    }

    public void setAsDefault(StorageLocation location) {
        if (location != null) {
            repository.setLocationAsDefault(location.internalKey);
        }
    }
    public void insert(StorageLocation storageLocation, final OnMaxOrderIndexRetrievedListener listener) {
        // Ottieni il max order index in background
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            int maxIndex = AppDatabase.getDatabase(getApplication()).storageLocationDao().getMaxOrderIndex();
            // Esegui sul thread principale per aggiornare l'UI o continuare la logica
            // (qui passiamo il risultato al listener che a sua volta lo passerà al repository)
            if (listener != null) {
                listener.onMaxOrderIndexRetrieved(maxIndex);
            }
        });
    }
    public interface OnMaxOrderIndexRetrievedListener {
        void onMaxOrderIndexRetrieved(int maxIndex);
    }
    public void updateOrder(List<StorageLocation> orderedLocations) {
        List<StorageLocation> realLocationsToOrder = new ArrayList<>();
        if (orderedLocations != null) {
            for (StorageLocation loc : orderedLocations) {
                if (loc.getId() != ALL_PRODUCTS_TAB_ID) {
                    realLocationsToOrder.add(loc);
                }
            }
        }
        if (!realLocationsToOrder.isEmpty()) {
            repository.updateLocationOrder(realLocationsToOrder);
        }
    }
}
