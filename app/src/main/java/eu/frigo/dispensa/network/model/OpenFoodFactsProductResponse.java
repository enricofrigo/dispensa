// File: OpenFoodFactsProductResponse.java
package eu.frigo.dispensa.network.model; // Crea questo package se non esiste

import com.google.gson.annotations.SerializedName;

public class OpenFoodFactsProductResponse {

    @SerializedName("product")
    private ProductData product;
    @SerializedName("status")
    private int status;

    public ProductData getProduct() {
        return product;
    }

    public int getStatus() {
        return status;
    }

    public static class ProductData {

        @SerializedName("product_name_it")
        private String productNameIt;
        @SerializedName("product_name")
        private String productName;
        @SerializedName("image_front_url")
        private String imageFrontUrl;
        @SerializedName("image_url")
        private String imageUrl;

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
