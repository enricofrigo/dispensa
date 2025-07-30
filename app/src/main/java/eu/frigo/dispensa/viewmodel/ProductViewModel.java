package eu.frigo.dispensa.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.util.Log;

import java.util.List;
import eu.frigo.dispensa.data.Product;
import eu.frigo.dispensa.data.ProductRepository;
import eu.frigo.dispensa.data.ProductWithCategoryDefinitions;

public class ProductViewModel extends AndroidViewModel {

    private ProductRepository repository;
    private LiveData<List<ProductWithCategoryDefinitions>> allProducts;
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>(""); // Inizializza con stringa vuota


    public ProductViewModel(@NonNull Application application) {
        super(application);
        repository = new ProductRepository(application); // Usa il tuo Repository
        allProducts = repository.getAllProducts();
    }

    public LiveData<List<ProductWithCategoryDefinitions>> getAllProducts() {
        return allProducts;
    }

    public void delete(Product selectedProduct) {
        repository.delete(selectedProduct);
    }

    public void refreshProducts() {
        Log.d("MainViewModel", "refreshProducts() chiamato.");
        repository.triggerDataRefresh();
    }

    public void update(Product updatedProduct) {
        repository.update(updatedProduct);
    }

    public void insert(Product product) {
        repository.insert(product);
    }
    public LiveData<List<ProductWithCategoryDefinitions>> getProductsByLocation(String storageLocationFilter) {
        return repository.getProductByStorageLocation(storageLocationFilter);
    }
    public void setSearchQuery(String query) {searchQuery.setValue(query);}
    public LiveData<String> getSearchQuery() {return searchQuery;}

    public LiveData<List<ProductWithCategoryDefinitions>> getAllProductsWithCategories() {
        return repository.getAllProducts();
    }
}