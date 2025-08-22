// File: OpenFoodFactsApiService.java
package eu.frigo.dispensa.network.openfoodfacts; // Crea questo package se non esiste

import eu.frigo.dispensa.network.openfoodfacts.model.OpenFoodFactsProductResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface OpenFoodFactsApiService {

    // Esempio: https://world.openfoodfacts.org/api/v2/product/3017620422003.json?fields=product_name_it,product_name,image_front_url,image_url
    @GET("api/v2/product/{barcode}.json")
    Call<OpenFoodFactsProductResponse> getProductByBarcode(
            @Path("barcode") String barcode,
            @Query("fields") String fields // Per specificare solo i campi che ci interessano
    );
}