package eu.frigo.dispensa.viewmodel; // Crea un package viewmodel

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

import eu.frigo.dispensa.data.Product;
import eu.frigo.dispensa.data.ProductRepository;
import eu.frigo.dispensa.data.ProductWithCategoryDefinitions;
import eu.frigo.dispensa.data.StorageLocation;

public class AddProductViewModel extends AndroidViewModel {
    private ProductRepository repository;
    private LiveData<List<StorageLocation>> allSelectableLocations;

    public AddProductViewModel (Application application) {
        super(application);
        repository = new ProductRepository(application);
        allSelectableLocations = repository.getAllSelectableLocations();
    }

    public void insert(Product product, List<String> tagsToSave) {
        repository.insertProductWithApiTags(product,tagsToSave);
    }

    public void update(Product product, List<String> tagsToSave) {
        repository.updateProductWithApiTags(product,tagsToSave);
    }
    public LiveData<ProductWithCategoryDefinitions> getProductById(int currentProductId) {
        return repository.getProductById(currentProductId);
    }
    public LiveData<List<StorageLocation>> getAllSelectableLocations() {
        return allSelectableLocations;
    }
}
