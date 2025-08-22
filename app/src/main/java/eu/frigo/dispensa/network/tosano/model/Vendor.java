// Vendor.java
package eu.frigo.dispensa.network.tosano.model;

import com.google.gson.annotations.SerializedName;

public class Vendor {

    @SerializedName("vendorId")
    private int vendorId;

    @SerializedName("name")
    private String name;

    // Getters e Setters
    public int getVendorId() {
        return vendorId;
    }

    public void setVendorId(int vendorId) {
        this.vendorId = vendorId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Vendor{" +
                "vendorId=" + vendorId +
                ", name='" + name + '\'' +
                '}';
    }
}
