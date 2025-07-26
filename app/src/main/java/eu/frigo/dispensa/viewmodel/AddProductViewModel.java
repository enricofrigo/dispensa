package eu.frigo.dispensa.viewmodel; // Crea un package viewmodel

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import eu.frigo.dispensa.data.Product;
import eu.frigo.dispensa.data.ProductRepository;

public class AddProductViewModel extends AndroidViewModel {
    private ProductRepository repository;

    public AddProductViewModel (Application application) {
        super(application);
        repository = new ProductRepository(application);
    }

    public void insert(Product product) {
        repository.insert(product);
    }

    public void update(Product product) {
        repository.update(product);
    }
}
