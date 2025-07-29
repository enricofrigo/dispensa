package eu.frigo.dispensa.data;

import android.app.Application;
import androidx.lifecycle.LiveData;
import androidx.media3.common.util.Log;

import java.util.List;

public class ProductRepository {
    private final ProductDao productDao;
    private final LiveData<List<Product>> allProducts;

    public ProductRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        productDao = db.productDao();
        allProducts = productDao.getAllProducts();
    }

    public LiveData<List<Product>> getAllProducts() {
        return allProducts;
    }

    public void insert(Product product) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            productDao.insert(product);
        });
    }

    public void delete(Product selectedProduct) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            productDao.delete(selectedProduct);
        });
    }

    public void update(Product product) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            productDao.update(product);
        });
    }

    public void triggerDataRefresh() {
        Log.d("ProductRepository", "triggerDataRefresh() chiamato.");
    }
}
