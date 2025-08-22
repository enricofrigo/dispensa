// DataContainer.java
package eu.frigo.dispensa.network.tosano.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class DataContainer {

    @SerializedName("page")
    private PageInfo page;

    @SerializedName("products")
    private List<Product> products;

    // Getters e Setters
    public PageInfo getPage() {
        return page;
    }

    public void setPage(PageInfo page) {
        this.page = page;
    }

    public List<Product> getProducts() {
        return products;
    }

    public void setProducts(List<Product> products) {
        this.products = products;
    }

    @Override
    public String toString() {
        return "DataContainer{" +
                "page=" + page +
                ", products=" + products +
                '}';
    }
}
