package eu.frigo.dispensa.data.storage;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.media3.common.util.Log;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.ui.SettingsFragment;
import eu.frigo.dispensa.util.LocaleHelper;

public class PredefinedData {

    public static final String LOCATION_FRIDGE = "FRIDGE";
    public static final String LOCATION_FREEZER = "FREEZER";
    public static final String LOCATION_PANTRY = "PANTRY";
    public static final String LOCATION_ALL = "all_products_key";

    public static List<StorageLocation> getInitialStorageLocations(Context context) {
        List<StorageLocation> locations = new ArrayList<>();

        locations.add(new StorageLocation(context.getString(R.string.default_location_fridge_entry), LOCATION_FRIDGE, 1, false, true));
        locations.add(new StorageLocation(context.getString(R.string.default_location_freezer_entry), LOCATION_FREEZER, 2, false, true));
        locations.add(new StorageLocation(context.getString(R.string.default_location_pantry_entry), LOCATION_PANTRY, 0, true, true)); // Dispensa come default, ordine 0

        return locations;
    }
    public static String getDisplayLocationName(Context context, String locationKey) {
        String s = context.getResources().getConfiguration().getLocales().toLanguageTags();
        Log.d("locale", "getDisplayLocationName: "+s);
        switch (locationKey) {
            case LOCATION_FRIDGE:
                Log.d("locale", "getDisplayLocationTabName: "+context.getString(R.string.default_location_fridge_entry));
               return context.getString(R.string.default_location_fridge_entry);
            case LOCATION_FREEZER:
                Log.d("locale", "getDisplayLocationTabName: "+context.getString(R.string.default_location_freezer_entry));
                return context.getString(R.string.default_location_freezer_entry);
            case LOCATION_PANTRY:
                Log.d("locale", "getDisplayLocationTabName: "+context.getString(R.string.default_location_pantry_entry));
                return context.getString(R.string.default_location_pantry_entry);
            case LOCATION_ALL:
                Log.d("locale", "getDisplayLocationTabName: "+context.getString(R.string.tab_title_all_products));
                return context.getString(R.string.tab_title_all_products);
            default:
                return null;
        }
    }
}