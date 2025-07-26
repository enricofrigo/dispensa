package eu.frigo.dispensa.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.media3.common.util.Log;

import java.util.List;
import eu.frigo.dispensa.data.Product;
import eu.frigo.dispensa.data.ProductRepository; // Assumendo che tu abbia un Repository

public class MainViewModel extends AndroidViewModel {

    private ProductRepository repository;
    private LiveData<List<Product>> allProducts;

    public MainViewModel(@NonNull Application application) {
        super(application);
        repository = new ProductRepository(application); // Usa il tuo Repository
        allProducts = repository.getAllProducts();
    }

    public LiveData<List<Product>> getAllProducts() {
        return allProducts;
    }

    public void delete(Product selectedProduct) {
        repository.delete(selectedProduct);
    }

    public void refreshProducts() {
        Log.d("MainViewModel", "refreshProducts() chiamato.");
        repository.triggerDataRefresh();
    }
}