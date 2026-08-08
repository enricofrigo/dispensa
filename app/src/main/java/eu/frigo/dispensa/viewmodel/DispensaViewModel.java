package eu.frigo.dispensa.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

import eu.frigo.dispensa.data.Repository;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;

public class DispensaViewModel extends AndroidViewModel {

    private final Repository repository;
    private final LiveData<List<Dispensa>> allDispense;
    private final LiveData<Integer> currentDispensaId;
    private final LiveData<String> currentDispensaName;
    private final MutableLiveData<Boolean> pantryCreatedEvent = new MutableLiveData<>(false);

    public DispensaViewModel(@NonNull Application application) {
        super(application);
        repository = Repository.getInstance(application);
        allDispense = repository.getAllDispense();
        currentDispensaId = repository.getCurrentDispensaId();
        currentDispensaName = repository.getCurrentDispensaName();
    }

    public LiveData<List<Dispensa>> getAllDispense() {
        return allDispense;
    }

    public LiveData<Integer> getCurrentDispensaId() {
        return currentDispensaId;
    }
    public LiveData<String> getCurrentDispensaName() {
        return currentDispensaName;
    }

    public LiveData<Dispensa> getCurrentDispensa() {
        return repository.getCurrentDispensa();
    }

    public LiveData<Boolean> getPantryCreatedEvent() {
        return pantryCreatedEvent;
    }

    public void setCurrentDispensaId(int id) {
        repository.setCurrentDispensaId(id);
    }
    public void insert(Dispensa dispensa, boolean setAsCurrent) {
        repository.insertDispensa(dispensa, setAsCurrent);
        pantryCreatedEvent.postValue(true);
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

    public void insertJoinedPantryConfig(JoinedPantryConfig config) {
        repository.insertJoinedPantryConfig(config);
    }

    public io.reactivex.rxjava3.core.Single<Boolean> isPantryJoined(int dispensaId) {
        return io.reactivex.rxjava3.core.Single.fromCallable(() -> 
                repository.getJoinedPantryConfigSync(dispensaId) != null);
    }
}
