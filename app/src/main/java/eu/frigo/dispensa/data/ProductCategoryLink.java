package eu.frigo.dispensa.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(tableName = "product_category_links",
        primaryKeys = {"product_id_fk", "category_id_fk"}, // Chiave primaria composta
        foreignKeys = {
                @ForeignKey(entity = Product.class,
                        parentColumns = "id",
                        childColumns = "product_id_fk",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = CategoryDefinition.class,
                        parentColumns = "category_id",
                        childColumns = "category_id_fk",
                        onDelete = ForeignKey.CASCADE) // O RESTRICT se vuoi impedire la cancellazione di categorie usate
        },
        indices = { @Index(value = "product_id_fk"), @Index(value = "category_id_fk")}
)
public class ProductCategoryLink {
    @ColumnInfo(name = "product_id_fk")
    public int productIdFk;

    @ColumnInfo(name = "category_id_fk")
    public int categoryIdFk;

    public ProductCategoryLink(int productIdFk, int categoryIdFk) {
        this.productIdFk = productIdFk;
        this.categoryIdFk = categoryIdFk;
    }

    public int getProductIdFk() {
        return productIdFk;
    }

    public void setProductIdFk(int productIdFk) {
        this.productIdFk = productIdFk;
    }

    public int getCategoryIdFk() {
        return categoryIdFk;
    }

    public void setCategoryIdFk(int categoryIdFk) {
        this.categoryIdFk = categoryIdFk;
    }

    @Override
    public String toString() {
        return "ProductCategoryLink{" +
                "productIdFk=" + productIdFk +
                ", categoryIdFk=" + categoryIdFk +
                '}';
    }
}
