// ProductClassRuleMax.java
package eu.frigo.dispensa.network.tosano.model;

import com.google.gson.annotations.SerializedName;

public class ProductClassRuleMax {

    @SerializedName("productClassName")
    private String productClassName;

    @SerializedName("max")
    private int max;

    @SerializedName("unit")
    private String unit;

    // Getters e Setters
    public String getProductClassName() {
        return productClassName;
    }

    public void setProductClassName(String productClassName) {
        this.productClassName = productClassName;
    }

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        this.max = max;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    @Override
    public String toString() {
        return "ProductClassRuleMax{" +
                "productClassName='" + productClassName + '\'' +
                ", max=" + max +
                ", unit='" + unit + '\'' +
                '}';
    }
}
