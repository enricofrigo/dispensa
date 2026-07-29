package eu.frigo.dispensa.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

import eu.frigo.dispensa.data.Repository;
import eu.frigo.dispensa.data.dispensa.Dispensa;

public class DispensaViewModel extends AndroidViewModel {

    private final Repository repository;
    private final LiveData<List<Dispensa>> allDispense;
    private final LiveData<Integer> currentDispensaId;

    public DispensaViewModel(@NonNull Application application) {
        super(application);
        repository = Repository.getInstance(application);
        allDispense = repository.getAllDispense();
        currentDispensaId = repository.getCurrentDispensaId();
    }

    public LiveData<List<Dispensa>> getAllDispense() {
        return allDispense;
    }

    public LiveData<Integer> getCurrentDispensaId() {
        return currentDispensaId;
    }

    public void setCurrentDispensaId(int id) {
        repository.setCurrentDispensaId(id);
    }

    public void insert(Dispensa dispensa, boolean setAsCurrent) {
        repository.insertDispensa(dispensa, setAsCurrent);
    }

    public void update(Dispensa dispensa) {
        repository.updateDispensa(dispensa);
    }

    public void delete(Dispensa dispensa) {
        repository.deleteDispensa(dispensa);
    }

    public void setAsDefault(int id) {
        repository.setDispensaAsDefault(id);
    }
}
