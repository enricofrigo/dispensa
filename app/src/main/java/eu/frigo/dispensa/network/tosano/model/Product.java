// Product.java
package eu.frigo.dispensa.network.tosano.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Product {

    @SerializedName("id")
    private int id;

    @SerializedName("productId")
    private int productId;

    @SerializedName("codInt")
    private String internalCode;

    @SerializedName("codVar")
    private String variantCode;

    @SerializedName("code")
    private String code;

    @SerializedName("codeVariant")
    private String codeVariant;

    @SerializedName("name")
    private String name;

    @SerializedName("shortDescr")
    private String shortDescription;

    @SerializedName("description")
    private String description;

    @SerializedName("pubState")
    private int publicationState;

    @SerializedName("slug")
    private String slug;

    @SerializedName("itemUrl")
    private String itemUrl;

    @SerializedName("barcode")
    private String barcode;

    @SerializedName("productNatureId")
    private int productNatureId;

    @SerializedName("productHierarchyLevelId")
    private int productHierarchyLevelId;

    @SerializedName("vendor")
    private Vendor vendor;

    @SerializedName("categoryId")
    private int categoryId;

    @SerializedName("ivaCategory")
    private IvaCategory ivaCategory;

    @SerializedName("unitMeasureBaseSelling")
    private UnitMeasure unitMeasureBaseSelling;

    @SerializedName("productInfos")
    private ProductInfos productInfos;

    @SerializedName("dayLock")
    private String dayLock;

    @SerializedName("productClasses")
    private List<Object> productClasses; // Sostituisci Object con una classe specifica se nota

    @SerializedName("mediaURL")
    private String mediaURL;

    @SerializedName("mediaURLMedium")
    private String mediaURLMedium;

    @SerializedName("productClassRuleMax")
    private ProductClassRuleMax productClassRuleMax;

    @SerializedName("available")
    private int available;

    @SerializedName("selectOptions")
    private List<Object> selectOptions; // Sostituisci Object con una classe specifica se nota


    // Getters e Setters (generati per brevità, puoi aggiungerli tu)
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }
    public String getInternalCode() { return internalCode; }
    public void setInternalCode(String internalCode) { this.internalCode = internalCode; }
    public String getVariantCode() { return variantCode; }
    public void setVariantCode(String variantCode) { this.variantCode = variantCode; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getCodeVariant() { return codeVariant; }
    public void setCodeVariant(String codeVariant) { this.codeVariant = codeVariant; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getPublicationState() { return publicationState; }
    public void setPublicationState(int publicationState) { this.publicationState = publicationState; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getItemUrl() { return itemUrl; }
    public void setItemUrl(String itemUrl) { this.itemUrl = itemUrl; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public int getProductNatureId() { return productNatureId; }
    public void setProductNatureId(int productNatureId) { this.productNatureId = productNatureId; }
    public int getProductHierarchyLevelId() { return productHierarchyLevelId; }
    public void setProductHierarchyLevelId(int productHierarchyLevelId) { this.productHierarchyLevelId = productHierarchyLevelId; }
    public Vendor getVendor() { return vendor; }
    public void setVendor(Vendor vendor) { this.vendor = vendor; }
    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }
    public IvaCategory getIvaCategory() { return ivaCategory; }
    public void setIvaCategory(IvaCategory ivaCategory) { this.ivaCategory = ivaCategory; }
    public UnitMeasure getUnitMeasureBaseSelling() { return unitMeasureBaseSelling; }
    public void setUnitMeasureBaseSelling(UnitMeasure unitMeasureBaseSelling) { this.unitMeasureBaseSelling = unitMeasureBaseSelling; }
    public ProductInfos getProductInfos() { return productInfos; }
    public void setProductInfos(ProductInfos productInfos) { this.productInfos = productInfos; }
    public String getDayLock() { return dayLock; }
    public void setDayLock(String dayLock) { this.dayLock = dayLock; }
    public List<Object> getProductClasses() { return productClasses; }
    public void setProductClasses(List<Object> productClasses) { this.productClasses = productClasses; }
    public String getMediaURL() { return mediaURL; }
    public void setMediaURL(String mediaURL) { this.mediaURL = mediaURL; }
    public String getMediaURLMedium() { return mediaURLMedium; }
    public void setMediaURLMedium(String mediaURLMedium) { this.mediaURLMedium = mediaURLMedium; }
    public ProductClassRuleMax getProductClassRuleMax() { return productClassRuleMax; }
    public void setProductClassRuleMax(ProductClassRuleMax productClassRuleMax) { this.productClassRuleMax = productClassRuleMax; }
    public int getAvailable() { return available; }
    public void setAvailable(int available) { this.available = available; }
    public List<Object> getSelectOptions() { return selectOptions; }
    public void setSelectOptions(List<Object> selectOptions) { this.selectOptions = selectOptions; }

    @Override
    public String toString() {
        return "Product{" +
                "id=" + id +
                ", productId=" + productId +
                ", internalCode='" + internalCode + '\'' +
                ", variantCode='" + variantCode + '\'' +
                ", code='" + code + '\'' +
                ", codeVariant='" + codeVariant + '\'' +
                ", name='" + name + '\'' +
                ", shortDescription='" + shortDescription + '\'' +
                ", description='" + description + '\'' +
                ", publicationState=" + publicationState +
                ", slug='" + slug + '\'' +
                ", itemUrl='" + itemUrl + '\'' +
                ", barcode='" + barcode + '\'' +
                ", productNatureId=" + productNatureId +
                ", productHierarchyLevelId=" + productHierarchyLevelId +
                ", vendor=" + vendor +
                ", categoryId=" + categoryId +
                ", ivaCategory=" + ivaCategory +
                ", unitMeasureBaseSelling=" + unitMeasureBaseSelling +
                ", productInfos=" + productInfos +
                ", dayLock='" + dayLock + '\'' +
                ", productClasses=" + productClasses +
                ", mediaURL='" + mediaURL + '\'' +
                ", mediaURLMedium='" + mediaURLMedium + '\'' +
                ", productClassRuleMax=" + productClassRuleMax +
                ", available=" + available +
                ", selectOptions=" + selectOptions +
                '}';
    }
}
