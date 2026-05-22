package eu.frigo.dispensa.adapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import eu.frigo.dispensa.ui.sync.LocalNetworkPreferenceFragment;
import eu.frigo.dispensa.ui.sync.SyncStatusFragment;
import eu.frigo.dispensa.ui.sync.WebDavPreferenceFragment;

/**
 * ViewPager2 adapter for sync configuration tabs.
 * Each tab is a PreferenceFragmentCompat with settings.
 */
public class SyncConfigPagerAdapter extends FragmentStateAdapter {
    
    private static final int TAB_WEBDAV = 0;
    private static final int TAB_LOCAL_NETWORK = 1;
    private static final int TAB_GOOGLE_DRIVE = 2;
    private static final int TAB_SYNC_STATUS = 3;
    
    private final boolean isPlayFlavor;
    
    public SyncConfigPagerAdapter(@NonNull AppCompatActivity activity) {
        super(activity);
        // Detect if play flavor via package name
        this.isPlayFlavor = activity.getPackageName().contains(".play");
    }
    
    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case TAB_WEBDAV:
                return new WebDavPreferenceFragment();
            
            case TAB_LOCAL_NETWORK:
                return new LocalNetworkPreferenceFragment();
            
            case TAB_GOOGLE_DRIVE:
                if (isPlayFlavor) {
                    try {
                        // Try to load Google Drive fragment (play flavor only)
                        Class<?> clazz = Class.forName(
                                "eu.frigo.dispensa.ui.sync.GoogleDrivePreferenceFragment");
                        return (Fragment) clazz.getDeclaredConstructor().newInstance();
                    } catch (Exception e) {
                        // Fallback to status (fdroid or if class not found)
                        return new SyncStatusFragment();
                    }
                }
                // fdroid: show status instead of drive
                return new SyncStatusFragment();
            
            case TAB_SYNC_STATUS:
            default:
                return new SyncStatusFragment();
        }
    }
    
    @Override
    public int getItemCount() {
        // fdroid: WebDAV + Local Network + Status = 3 tabs
        // play: WebDAV + Local Network + Google Drive + Status = 4 tabs
        return isPlayFlavor ? 4 : 3;
    }
}
