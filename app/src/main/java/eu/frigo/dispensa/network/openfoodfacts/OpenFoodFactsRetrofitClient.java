package eu.frigo.dispensa.network.openfoodfacts;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import eu.frigo.dispensa.data.openfoodfacts.OpenFoodFactCacheManager;
import eu.frigo.dispensa.ui.SettingsFragment;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class OpenFoodFactsRetrofitClient {

    private static final String OPEN_FOOD_FACTS_BASE_URL = "https://world.openfoodfacts.org/";
    private static Retrofit retrofitInstance = null;

    public static synchronized Retrofit getClient() {
        if (retrofitInstance == null) {
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .addInterceptor(chain -> {
                        Request original = chain.request();
                        Request requestWithUserAgent = original.newBuilder()
                                .header("User-Agent", OpenFoodFactCacheManager.USER_AGENT)
                                .build();
                        return chain.proceed(requestWithUserAgent);
                    })
                    .build();

            retrofitInstance = new Retrofit.Builder()
                    .baseUrl(OPEN_FOOD_FACTS_BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofitInstance;
    }

    public static String getLocalizedFieldsToFetch() {
        String lang = Locale.getDefault().getLanguage();
        if (lang == null || lang.trim().isEmpty()) {
            lang = "it";
        }
        return "product_name_" + lang + ",product_name_it,product_name,image_front_url,image_url,categories_tags";
    }

    public static boolean isOpenFoodFactsApiEnabled(Context context) {
        if (context == null) {
            return true;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(SettingsFragment.KEY_PREF_ENABLE_OFF_API, true);
    }

    public static OpenFoodFactsApiService getApiService(Context context) {
        if (!isOpenFoodFactsApiEnabled(context)) return null;
        return getClient().create(OpenFoodFactsApiService.class);
    }
}
