// IvaCategory.java
package eu.frigo.dispensa.network.tosano.model;

import com.google.gson.annotations.SerializedName;

public class IvaCategory {

    @SerializedName("ivaCategoryId")
    private int ivaCategoryId;

    @SerializedName("code")
    private String code;

    @SerializedName("descr")
    private String description;

    @SerializedName("ivaPct")
    private String ivaPercentage; // Mantenuto come String

    // Getters e Setters
    public int getIvaCategoryId() {
        return ivaCategoryId;
    }

    public void setIvaCategoryId(int ivaCategoryId) {
        this.ivaCategoryId = ivaCategoryId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIvaPercentage() {
        return ivaPercentage;
    }

    public void setIvaPercentage(String ivaPercentage) {
        this.ivaPercentage = ivaPercentage;
    }

    @Override
    public String toString() {
        return "IvaCategory{" +
                "ivaCategoryId=" + ivaCategoryId +
                ", code='" + code + '\'' +
                ", description='" + description + '\'' +
                ", ivaPercentage='" + ivaPercentage + '\'' +
                '}';
    }
}