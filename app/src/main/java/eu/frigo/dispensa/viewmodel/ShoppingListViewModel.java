package eu.frigo.dispensa.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

import eu.frigo.dispensa.data.Repository;
import eu.frigo.dispensa.data.shoppinglist.ShoppingItem;

public class ShoppingListViewModel extends AndroidViewModel {

    private final Repository repository;
    private final LiveData<List<ShoppingItem>> allItems;
    private final LiveData<Integer> uncheckedCount;
    private final LiveData<List<String>> allItemNames;

    public ShoppingListViewModel(@NonNull Application application) {
        super(application);
        repository = new Repository(application);
        allItems = repository.getAllShoppingItems();
        uncheckedCount = repository.getUncheckedShoppingCount();
        allItemNames = repository.getAllShoppingItemNames();
    }

    public LiveData<List<ShoppingItem>> getAllItems() {
        return allItems;
    }

    public LiveData<Integer> getUncheckedCount() {
        return uncheckedCount;
    }

    public LiveData<List<String>> getAllItemNames() {
        return allItemNames;
    }

    public void addItem(String productName) {
        repository.addToShoppingList(productName);
    }

    public void removeItem(String productName) {
        repository.removeFromShoppingList(productName);
    }

    public void toggleChecked(ShoppingItem item) {
        item.setChecked(!item.isChecked());
        repository.updateShoppingItem(item);
    }

    public void deleteItem(ShoppingItem item) {
        repository.deleteShoppingItem(item);
    }

    public void clearChecked() {
        repository.clearCheckedShoppingItems();
    }
}
