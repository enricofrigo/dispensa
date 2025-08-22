// File: RetrofitClient.java
package eu.frigo.dispensa.network.openfoodfacts;

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

    public static OpenFoodFactsApiService getApiService() {
        return getClient().create(OpenFoodFactsApiService.class);
    }
}
