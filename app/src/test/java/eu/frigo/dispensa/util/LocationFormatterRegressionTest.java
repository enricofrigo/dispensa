package eu.frigo.dispensa.util;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.storage.PredefinedData;
import eu.frigo.dispensa.data.storage.StorageLocation;

@RunWith(RobolectricTestRunner.class)
public class LocationFormatterRegressionTest {

    private Context context;

    @Before
    public void setup() {
        Context baseContext = ApplicationProvider.getApplicationContext();
        Resources mockResources = new Resources(baseContext.getAssets(), baseContext.getResources().getDisplayMetrics(), baseContext.getResources().getConfiguration()) {
            @Override
            public String getString(int id) {
                if (id == R.string.default_location_fridge_entry) return "Fridge";
                if (id == R.string.default_location_freezer_entry) return "Freezer";
                if (id == R.string.default_location_pantry_entry) return "Pantry";
                if (id == R.string.tab_title_all_products) return "All Products";
                return "Unknown";
            }

            @Override
            public CharSequence getText(int id) {
                return getString(id);
            }
        };

        context = new ContextWrapper(baseContext) {
            @Override
            public Resources getResources() {
                return mockResources;
            }
        };
    }

    @Test
    public void testLocationFormatting_PredefinedLocations() {
        String fridgeName = LocationFormatter.getDisplayLocationName(context, PredefinedData.LOCATION_FRIDGE);
        Assert.assertEquals("Fridge", fridgeName);

        String freezerName = LocationFormatter.getDisplayLocationName(context, PredefinedData.LOCATION_FREEZER);
        Assert.assertEquals("Freezer", freezerName);

        String pantryName = LocationFormatter.getDisplayLocationName(context, PredefinedData.LOCATION_PANTRY);
        Assert.assertEquals("Pantry", pantryName);

        String allName = LocationFormatter.getDisplayLocationName(context, PredefinedData.LOCATION_ALL);
        Assert.assertEquals("All Products", allName);

        Assert.assertNull(LocationFormatter.getDisplayLocationName(context, "UNKNOWN_CUSTOM_KEY"));
    }

    @Test
    public void testLocationIcons_PredefinedLocations() {
        Assert.assertEquals((Integer) R.drawable.ic_fridge, LocationFormatter.getDisplayLocationIcon(PredefinedData.LOCATION_FRIDGE));
        Assert.assertEquals((Integer) R.drawable.freezer_24, LocationFormatter.getDisplayLocationIcon(PredefinedData.LOCATION_FREEZER));
        Assert.assertEquals((Integer) R.drawable.pantry_24px, LocationFormatter.getDisplayLocationIcon(PredefinedData.LOCATION_PANTRY));
        Assert.assertEquals((Integer) R.drawable.location_home_24px, LocationFormatter.getDisplayLocationIcon(PredefinedData.LOCATION_ALL));
        Assert.assertNull(LocationFormatter.getDisplayLocationIcon("NON_EXISTING"));
    }

    @Test
    public void testLocalizedNameAndIcon_CustomAndPredefinedEntities() {
        StorageLocation predefined = new StorageLocation("Frigorifero Predefinito", PredefinedData.LOCATION_FRIDGE, 0, false, true);
        String localizedPredefined = LocationFormatter.getLocalizedName(context, predefined);
        Assert.assertEquals("Fridge", localizedPredefined);
        Assert.assertEquals((Integer) R.drawable.ic_fridge, LocationFormatter.getIcon(predefined));

        StorageLocation custom = new StorageLocation("Cantina Vini", "CUSTOM_CANTINA", 1, false, false);
        String localizedCustom = LocationFormatter.getLocalizedName(context, custom);
        Assert.assertEquals("Cantina Vini", localizedCustom);
        Assert.assertNull(LocationFormatter.getIcon(custom));
    }
}
