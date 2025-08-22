// UnitMeasure.java
package eu.frigo.dispensa.network.tosano.model;

import com.google.gson.annotations.SerializedName;

public class UnitMeasure {

    @SerializedName("umId")
    private int unitMeasureId;

    @SerializedName("um")
    private String unit;

    @SerializedName("umDes")
    private String unitDescription;

    // Getters e Setters
    public int getUnitMeasureId() {
        return unitMeasureId;
    }

    public void setUnitMeasureId(int unitMeasureId) {
        this.unitMeasureId = unitMeasureId;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getUnitDescription() {
        return unitDescription;
    }

    public void setUnitDescription(String unitDescription) {
        this.unitDescription = unitDescription;
    }

    @Override
    public String toString() {
        return "UnitMeasure{" +
                "unitMeasureId=" + unitMeasureId +
                ", unit='" + unit + '\'' +
                ", unitDescription='" + unitDescription + '\'' +
                '}';
    }
}
