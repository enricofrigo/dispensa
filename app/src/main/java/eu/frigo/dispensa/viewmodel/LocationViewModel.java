package eu.frigo.dispensa.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.ProductRepository;
import eu.frigo.dispensa.data.StorageLocation;

import java.util.ArrayList;
import java.util.List;

public class LocationViewModel extends AndroidViewModel {

    private ProductRepository repository;
    private MediatorLiveData<List<StorageLocation>> locationsForTabs;
    private LiveData<StorageLocation> defaultLocation;
    public static final String ALL_PRODUCTS_INTERNAL_KEY = "all_products_key"; // Scegli una chiave univoca
    public static final int ALL_PRODUCTS_TAB_ID = -1;

    public LocationViewModel(@NonNull Application application) {
        super(application);
        repository = new ProductRepository(application);
        defaultLocation = repository.getDefaultLocation();
        locationsForTabs = new MediatorLiveData<>();
        LiveData<List<StorageLocation>> dbRealLocationsSorted = repository.getAllLocationsSorted();
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

    public LiveData<List<StorageLocation>> getAllLocationsSorted() {
        return locationsForTabs;
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
