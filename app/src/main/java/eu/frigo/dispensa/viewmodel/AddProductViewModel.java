package eu.frigo.dispensa.viewmodel; // Crea un package viewmodel

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.Repository;
import eu.frigo.dispensa.data.category.ProductWithCategoryDefinitions;
import eu.frigo.dispensa.data.storage.StorageLocation;

public class AddProductViewModel extends AndroidViewModel {
    private Repository repository;
    private LiveData<List<StorageLocation>> allSelectableLocations;

    public AddProductViewModel (Application application) {
        super(application);
        repository = new Repository(application);
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
