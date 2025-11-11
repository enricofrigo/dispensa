// File: RetrofitClient.java
package eu.frigo.dispensa.network.openfoodfacts;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import eu.frigo.dispensa.ui.SettingsFragment;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class OpenFoodFactsRetrofitClient {

    private static final String OPEN_FOOD_FACTS_BASE_URL = "https://world.openfoodfacts.org/";
    private static Retrofit retrofitInstance = null;

    public static Retrofit getClient() {
        if (retrofitInstance == null) {
            retrofitInstance = new Retrofit.Builder()
                    .baseUrl(OPEN_FOOD_FACTS_BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofitInstance;
    }
    public static boolean isOpenFoodFactsApiEnabled(Context context) {
        if (context == null) {
            // Fallback nel caso il contesto non sia disponibile, considera se abilitare o disabilitare di default
            return true; // O false, a seconda del comportamento desiderato in questo caso limite
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(SettingsFragment.KEY_PREF_ENABLE_OFF_API, true);
    }
    public static OpenFoodFactsApiService getApiService(Context context) {
        if(!isOpenFoodFactsApiEnabled(context)) return null;
        return getClient().create(OpenFoodFactsApiService.class);
    }
}
