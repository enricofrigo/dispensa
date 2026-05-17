package eu.frigo.dispensa.adapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import eu.frigo.dispensa.ui.sync.LocalNetworkConfigFragment;
import eu.frigo.dispensa.ui.sync.SyncStatusFragment;
import eu.frigo.dispensa.ui.sync.WebDavConfigFragment;

/**
 * ViewPager2 adapter for sync configuration tabs.
 */
public class SyncConfigPagerAdapter extends FragmentStateAdapter {
    
    private static final int TAB_WEBDAV = 0;
    private static final int TAB_LOCAL_NETWORK = 1;
    private static final int TAB_GOOGLE_DRIVE = 2;
    private static final int TAB_SYNC_STATUS = 3;
    
    private final boolean isPlayFlavor;
    
    public SyncConfigPagerAdapter(@NonNull AppCompatActivity activity) {
        super(activity);
        // Detect if play flavor
        this.isPlayFlavor = activity.getPackageName().contains("play");
    }
    
    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case TAB_WEBDAV:
                return new WebDavConfigFragment();
            
            case TAB_LOCAL_NETWORK:
                return new LocalNetworkConfigFragment();
            
            case TAB_GOOGLE_DRIVE:
                if (isPlayFlavor) {
                    try {
                        Class<?> clazz = Class.forName(
                                "eu.frigo.dispensa.ui.sync.GoogleDriveConfigFragment");
                        return (Fragment) clazz.getDeclaredConstructor().newInstance();
                    } catch (Exception e) {
                        // Fallback if class not available (fdroid flavor)
                        return new SyncStatusFragment();
                    }
                }
                // For fdroid, position 2 is the status tab
                return new SyncStatusFragment();
            
            case TAB_SYNC_STATUS:
            default:
                return new SyncStatusFragment();
        }
    }
    
    @Override
    public int getItemCount() {
        // WebDAV + Local Network + Status = 3 tabs (fdroid)
        // WebDAV + Local Network + Drive + Status = 4 tabs (play)
        return isPlayFlavor ? 4 : 3;
    }
}
