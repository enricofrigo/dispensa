package eu.frigo.dispensa.adapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;

import eu.frigo.dispensa.data.StorageLocation;
import eu.frigo.dispensa.ui.ProductListFragment; // Il fragment che mostra i prodotti per una location

public class LocationViewPagerAdapter extends FragmentStateAdapter {

    private List<StorageLocation> storageLocations = new ArrayList<>();

    public LocationViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    // Costruttore alternativo se usato dentro un Fragment
    // public LocationViewPagerAdapter(@NonNull Fragment fragment) {
    //     super(fragment);
    // }

    public void setLocations(List<StorageLocation> newLocations) {
        this.storageLocations.clear();
        if (newLocations != null) {
            this.storageLocations.addAll(newLocations);
        }
        notifyDataSetChanged(); // Molto importante! Notifica al ViewPager2 che i dati sono cambiati.
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        StorageLocation currentLocation = storageLocations.get(position);
        return ProductListFragment.newInstance(currentLocation.getInternalKey());
    }

    @Override
    public int getItemCount() {
        return storageLocations.size();
    }

    public String getPageTitle(int position) {
        if (position >= 0 && position < storageLocations.size()) {
            return storageLocations.get(position).getName(); // Assumendo che StorageLocation abbia getName()
        }
        return null; // O una stringa di fallback
    }

    @Nullable
    public String getLocationInternalKeyAt(int position) {
        if (position >= 0 && position < storageLocations.size()) {
            return storageLocations.get(position).getInternalKey();
        }
        return null;
    }

    public StorageLocation getLocationAt(int position) {
        if (position >= 0 && position < storageLocations.size()) {
            return storageLocations.get(position);
        }
        return null;
    }
}
