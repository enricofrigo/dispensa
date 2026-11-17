package eu.frigo.dispensa.network.openfoodfacts.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Locale;

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
        @SerializedName("product_name_en")
        private String productNameEn;
        @SerializedName("product_name_de")
        private String productNameDe;
        @SerializedName("product_name_el")
        private String productNameEl;
        @SerializedName("product_name_pt")
        private String productNamePt;
        @SerializedName("product_name")
        private String productName;
        @SerializedName("image_front_url")
        private String imageFrontUrl;
        @SerializedName("image_url")
        private String imageUrl;
        @SerializedName("categories_tags")
        private List<String> categoriesTags;

        public String getProductNameIt() {
            return productNameIt;
        }

        public String getLocalizedProductName() {
            String lang = Locale.getDefault().getLanguage();
            String candidate = null;
            if ("it".equalsIgnoreCase(lang)) candidate = productNameIt;
            else if ("en".equalsIgnoreCase(lang)) candidate = productNameEn;
            else if ("de".equalsIgnoreCase(lang)) candidate = productNameDe;
            else if ("el".equalsIgnoreCase(lang)) candidate = productNameEl;
            else if ("pt".equalsIgnoreCase(lang)) candidate = productNamePt;

            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate;
            }
            if (productName != null && !productName.trim().isEmpty()) {
                return productName;
            }
            return productNameIt;
        }

        public String getProductName() {
            return productName;
        }
        public String getImageFrontUrl() {
            return imageFrontUrl;
        }
        public String getImageUrl() { return imageUrl; }
        public List<String> getCategoriesTags() { return categoriesTags; }
        public void setCategoriesTags(List<String> categoriesTags) { this.categoriesTags = categoriesTags; }

    }
}
