package eu.frigo.dispensa.adapter; // o il tuo package adapter

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import eu.frigo.dispensa.data.Product; // Per le costanti di posizione
import eu.frigo.dispensa.ui.ProductListFragment;

public class SectionsPagerAdapter extends FragmentStateAdapter {

    private static final int NUM_TABS = 4; // Tutti, Frigo, Freezer, Dispensa

    public SectionsPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: // Tutti
                return ProductListFragment.newInstance(null); // null per nessun filtro di posizione
            case 1: // Frigo
                return ProductListFragment.newInstance(Product.LOCATION_FRIDGE);
            case 2: // Freezer
                return ProductListFragment.newInstance(Product.LOCATION_FREEZER);
            case 3: // Dispensa
                return ProductListFragment.newInstance(Product.LOCATION_PANTRY);
            default:
                return ProductListFragment.newInstance(null); // Fallback
        }
    }

    @Override
    public int getItemCount() {
        return NUM_TABS;
    }
}

