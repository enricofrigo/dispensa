package eu.frigo.dispensa.data.storage;

import android.content.Context;

import androidx.media3.common.util.Log;

import java.util.ArrayList;
import java.util.List;

import eu.frigo.dispensa.R;

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
    public static String getDisplayLocationName(Context context, String locationKey) {
        Log.d("locale","getDisplayLocationName "+context.getResources().getConfiguration().getLocales().get(0).getLanguage());
        return switch (locationKey) {
            case LOCATION_FRIDGE -> {
                String r = context.getString(R.string.default_location_fridge_entry);
                Log.d("locale", "getDisplayLocationTabName: "+locationKey +" "+ r);
                yield r;
            }
            case LOCATION_FREEZER -> {
                String r = context.getString(R.string.default_location_freezer_entry);
                Log.d("locale", "getDisplayLocationTabName: "+locationKey +" " + r);
                yield r;
            }
            case LOCATION_PANTRY -> {
                String r = context.getString(R.string.default_location_pantry_entry);
                Log.d("locale", "getDisplayLocationTabName: "+locationKey +" " + r);
                yield r;
            }
            case LOCATION_ALL -> {
                String r = context.getString(R.string.tab_title_all_products);
                Log.d("locale", "getDisplayLocationTabName: " +locationKey+" "+ r);
                yield r;
            }
            default -> null;
        };
    }

    public static Integer getDisplayLocationIcon(String internalKey) {
        return switch (internalKey) {
            case PredefinedData.LOCATION_ALL -> R.drawable.location_home_24px ;
            case PredefinedData.LOCATION_PANTRY -> R.drawable.pantry_24px;
            case PredefinedData.LOCATION_FRIDGE -> R.drawable.ic_fridge;
            case PredefinedData.LOCATION_FREEZER -> R.drawable.freezer_24;
            default -> null;
        };
    }

}