package eu.frigo.dispensa.data.sync;

import java.util.List;

import eu.frigo.dispensa.data.product.Product;

public interface ConflictResolver {
    /**
     * Resolves conflicts for products based on the V1 rule: 
     * barcode + expirationDate + pantry.
     * If the same item is added, it should increment quantity, not duplicate.
     */
    Product resolveProductConflict(Product local, Product remote);
    
    /**
     * Merge lists of products, applying the resolution logic.
     */
    List<Product> mergeProducts(List<Product> localList, List<Product> remoteList);
}
