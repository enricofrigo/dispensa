package eu.frigo.dispensa.util;

import android.content.Context;
import androidx.media3.common.util.Log;
import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.storage.PredefinedData;
import eu.frigo.dispensa.data.storage.StorageLocation;

public class LocationFormatter {

    public static String getDisplayLocationName(Context context, String locationKey) {
        return switch (locationKey) {
            case PredefinedData.LOCATION_FRIDGE -> context.getString(R.string.default_location_fridge_entry);
            case PredefinedData.LOCATION_FREEZER -> context.getString(R.string.default_location_freezer_entry);
            case PredefinedData.LOCATION_PANTRY -> context.getString(R.string.default_location_pantry_entry);
            case PredefinedData.LOCATION_ALL -> context.getString(R.string.tab_title_all_products);
            default -> null;
        };
    }

    public static Integer getDisplayLocationIcon(String internalKey) {
        return switch (internalKey) {
            case PredefinedData.LOCATION_ALL -> R.drawable.location_home_24px;
            case PredefinedData.LOCATION_PANTRY -> R.drawable.pantry_24px;
            case PredefinedData.LOCATION_FRIDGE -> R.drawable.ic_fridge;
            case PredefinedData.LOCATION_FREEZER -> R.drawable.freezer_24;
            default -> null;
        };
    }

    public static String getLocalizedName(Context context, StorageLocation location) {
        if (location.isPredefined()) {
            return getDisplayLocationName(context, location.getInternalKey());
        } else {
            return location.getName();
        }
    }

    public static Integer getIcon(StorageLocation location) {
        if (location.isPredefined()) {
            return getDisplayLocationIcon(location.getInternalKey());
        } else {
            return null;
        }
    }
}
