// File: OpenFoodFactsApiService.java
package eu.frigo.dispensa.network.tosano; // Crea questo package se non esiste

import eu.frigo.dispensa.network.tosano.model.TosanoApiResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface TosanoApiService {

    // Esempio: https://www.latuaspesa.com/ebsn/api/products?barcode=8006890758467
    @GET("ebsn/api/products")
    Call<TosanoApiResponse> getProductByBarcode(
            @Query("barcode") String barcode
    );
}