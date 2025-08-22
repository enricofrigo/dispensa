// File: RetrofitClient.java
package eu.frigo.dispensa.network.tosano;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class TosanoRetrofitClient {

    private static final String TOSANO_BASE_URL = "https://www.latuaspesa.com/";
    private static Retrofit retrofitInstance = null;

    public static Retrofit getClient() {
        if (retrofitInstance == null) {
            retrofitInstance = new Retrofit.Builder()
                    .baseUrl(TOSANO_BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofitInstance;
    }

    public static TosanoApiService getApiService() {
        return getClient().create(TosanoApiService.class);
    }
}
