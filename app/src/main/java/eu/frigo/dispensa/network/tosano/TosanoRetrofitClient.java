// File: RetrofitClient.java
package eu.frigo.dispensa.network.tosano;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import eu.frigo.dispensa.ui.SettingsFragment;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class TosanoRetrofitClient {

    private static final String TOSANO_BASE_URL = "https://www.latuaspesa.com/";
    private static Retrofit retrofitInstance = null;

    public static boolean isTosanoApiEnabled(Context context) {
        if (context == null) {
            // Fallback nel caso il contesto non sia disponibile, considera se abilitare o disabilitare di default
            return true; // O false, a seconda del comportamento desiderato in questo caso limite
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(SettingsFragment.KEY_PREF_ENABLE_TOSANO_API, false);
    }
    public static Retrofit getClient() {
        if (retrofitInstance == null) {
            retrofitInstance = new Retrofit.Builder()
                    .baseUrl(TOSANO_BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofitInstance;
    }

    public static TosanoApiService getApiService(Context context) {
        if (!isTosanoApiEnabled(context)) {
            return null;
        }
        return getClient().create(TosanoApiService.class);
    }
}
