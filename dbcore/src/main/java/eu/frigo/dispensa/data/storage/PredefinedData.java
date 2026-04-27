package eu.frigo.dispensa.data.storage;

import java.util.ArrayList;
import java.util.List;

public class PredefinedData {

    public static final String LOCATION_FRIDGE = "FRIDGE";
    public static final String LOCATION_FREEZER = "FREEZER";
    public static final String LOCATION_PANTRY = "PANTRY";
    public static final String LOCATION_ALL = "all_products_key";

    public static List<StorageLocation> getInitialStorageLocations() {
        List<StorageLocation> locations = new ArrayList<>();

        locations.add(new StorageLocation(LOCATION_FRIDGE, LOCATION_FRIDGE, 1, false, true));
        locations.add(new StorageLocation(LOCATION_FREEZER, LOCATION_FREEZER, 2, false, true));
        locations.add(new StorageLocation(LOCATION_PANTRY, LOCATION_PANTRY, 0, true, true)); // Dispensa come default, ordine 0

        return locations;
    }
}