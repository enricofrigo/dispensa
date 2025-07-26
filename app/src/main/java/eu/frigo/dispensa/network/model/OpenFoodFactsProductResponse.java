// File: OpenFoodFactsProductResponse.java
package eu.frigo.dispensa.network.model; // Crea questo package se non esiste

import com.google.gson.annotations.SerializedName;

public class OpenFoodFactsProductResponse {

    @SerializedName("product")
    private ProductData product;

    @SerializedName("status")
    private int status; // 1 se il prodotto è stato trovato, 0 altrimenti

    public ProductData getProduct() {
        return product;
    }

    public int getStatus() {
        return status;
    }

    public static class ProductData {
        @SerializedName("product_name_it") // Nome del prodotto in italiano
        private String productNameIt;

        @SerializedName("product_name") // Nome generico del prodotto (fallback)
        private String productName;

        @SerializedName("image_front_url") // URL dell'immagine frontale
        private String imageFrontUrl;

        @SerializedName("image_url") // Altro URL immagine (fallback)
        private String imageUrl;

        // Aggiungi altri campi se ti servono (es. brands, quantity, ecc.)

        public String getProductNameIt() {
            return productNameIt;
        }

        public String getProductName() {
            return productName;
        }

        public String getImageFrontUrl() {
            return imageFrontUrl;
        }

        public String getImageUrl() { return imageUrl; }
    }
}
