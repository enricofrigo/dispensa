// ProductInfos.java
package eu.frigo.dispensa.network.tosano.model;

import com.google.gson.annotations.SerializedName;

public class ProductInfos {

    @SerializedName("ENABLE_ACQ_BOX")
    private String enableAcquisitionBox;

    @SerializedName("QTY_LOCKED")
    private String quantityLocked;

    @SerializedName("WEIGHT_SELLING")
    private String weightSelling;

    @SerializedName("WEB_ENABLED")
    private String webEnabled;

    @SerializedName("CHECKED")
    private String checked;

    @SerializedName("MAIL_TO")
    private String mailTo;

    @SerializedName("MINACQ")
    private String minAcquisition;

    @SerializedName("LOGISTIC_PACKAGING_ITEMS")
    private String logisticPackagingItems;

    @SerializedName("WEIGHT_VARIABLE")
    private String weightVariable;

    @SerializedName("TIPOLOGIA")
    private String typology;

    @SerializedName("STOCK_DAYS")
    private String stockDays;

    @SerializedName("WEIGHT_UNIT_BASE")
    private String weightUnitBase;

    // Getters e Setters (generati per brevità)
    public String getEnableAcquisitionBox() { return enableAcquisitionBox; }
    public void setEnableAcquisitionBox(String enableAcquisitionBox) { this.enableAcquisitionBox = enableAcquisitionBox; }
    public String getQuantityLocked() { return quantityLocked; }
    public void setQuantityLocked(String quantityLocked) { this.quantityLocked = quantityLocked; }
    public String getWeightSelling() { return weightSelling; }
    public void setWeightSelling(String weightSelling) { this.weightSelling = weightSelling; }
    public String getWebEnabled() { return webEnabled; }
    public void setWebEnabled(String webEnabled) { this.webEnabled = webEnabled; }
    public String getChecked() { return checked; }
    public void setChecked(String checked) { this.checked = checked; }
    public String getMailTo() { return mailTo; }
    public void setMailTo(String mailTo) { this.mailTo = mailTo; }
    public String getMinAcquisition() { return minAcquisition; }
    public void setMinAcquisition(String minAcquisition) { this.minAcquisition = minAcquisition; }
    public String getLogisticPackagingItems() { return logisticPackagingItems; }
    public void setLogisticPackagingItems(String logisticPackagingItems) { this.logisticPackagingItems = logisticPackagingItems; }
    public String getWeightVariable() { return weightVariable; }
    public void setWeightVariable(String weightVariable) { this.weightVariable = weightVariable; }
    public String getTypology() { return typology; }
    public void setTypology(String typology) { this.typology = typology; }
    public String getStockDays() { return stockDays; }
    public void setStockDays(String stockDays) { this.stockDays = stockDays; }
    public String getWeightUnitBase() { return weightUnitBase; }
    public void setWeightUnitBase(String weightUnitBase) { this.weightUnitBase = weightUnitBase; }

    @Override
    public String toString() {
        return "ProductInfos{" +
                "enableAcquisitionBox='" + enableAcquisitionBox + '\'' +
                ", quantityLocked='" + quantityLocked + '\'' +
                ", weightSelling='" + weightSelling + '\'' +
                ", webEnabled='" + webEnabled + '\'' +
                ", checked='" + checked + '\'' +
                ", mailTo='" + mailTo + '\'' +
                ", minAcquisition='" + minAcquisition + '\'' +
                ", logisticPackagingItems='" + logisticPackagingItems + '\'' +
                ", weightVariable='" + weightVariable + '\'' +
                ", typology='" + typology + '\'' +
                ", stockDays='" + stockDays + '\'' +
                ", weightUnitBase='" + weightUnitBase + '\'' +
                '}';
    }
}
