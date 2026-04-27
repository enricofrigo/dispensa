package eu.frigo.dispensa.data.category;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;

import java.util.List;

import eu.frigo.dispensa.data.product.Product;

public class ProductWithCategoryDefinitions {
    @Embedded
    public Product product;

    @Relation(
            entity = CategoryDefinition.class, // Entità target della relazione
            parentColumn = "id",               // Colonna in Product (sorgente)
            entityColumn = "category_id",      // Colonna in CategoryDefinition (target)
            associateBy = @Junction(           // Specifica la tabella di giunzione
                    value = ProductCategoryLink.class,
                    parentColumn = "product_id_fk",    // Colonna in ProductCategoryLink che si riferisce a Product
                    entityColumn = "category_id_fk"    // Colonna in ProductCategoryLink che si riferisce a CategoryDefinition
            )
    )
    public List<CategoryDefinition> categoryDefinitions;

    @Override
    public String toString() {
        return "ProductWithCategoryDefinitions{" +
                "product=" + product +
                ", categoryDefinitions=" + categoryDefinitions +
                '}';
    }
}
