package eu.frigo.dispensa.activity;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import eu.frigo.dispensa.BuildConfig;
import eu.frigo.dispensa.R;
import eu.frigo.dispensa.adapter.SyncConfigPagerAdapter;

/**
 * Activity that hosts sync configuration UI.
 * Contains tabs for:
 * - WebDAV
 * - Local Network
 * - Google Drive (play flavor)
 * - Sync Status
 */
public class SyncConfigActivity extends AppCompatActivity {
    
    private static final String TAG = "SyncConfigActivity";
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private SyncConfigPagerAdapter adapter;
    private FrameLayout rootLayout;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_config);
        
        rootLayout = findViewById(R.id.sync_config_root_layout);
        Toolbar toolbar = findViewById(R.id.toolbar_sync_config);
        setSupportActionBar(toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.pref_sync_config_title));
        }
        
        viewPager = findViewById(R.id.viewpager_sync_config);
        tabLayout = findViewById(R.id.tablayout_sync_config);
        
        setupViewPager();
        setupTabs();
        setupWindowInsets();
    }
    
    private void setupViewPager() {
        adapter = new SyncConfigPagerAdapter(this);
        viewPager.setAdapter(adapter);
    }
    
    private void setupTabs() {
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (isPlayFlavor()) {
                switch (position) {
                    case 0:
                        tab.setText(R.string.tab_webdav);
                        tab.setIcon(R.drawable.ic_cloud);
                        break;
                    case 1:
                        tab.setText(R.string.tab_local_network);
                        tab.setIcon(R.drawable.ic_wifi);
                        break;
                    case 2:
                        tab.setText(R.string.tab_google_drive);
                        tab.setIcon(R.drawable.ic_google_drive);
                        break;
                    case 3:
                        tab.setText(R.string.tab_sync_status);
                        tab.setIcon(R.drawable.ic_info);
                        break;
                }
            } else {
                // fdroid flavor
                switch (position) {
                    case 0:
                        tab.setText(R.string.tab_webdav);
                        tab.setIcon(R.drawable.ic_cloud);
                        break;
                    case 1:
                        tab.setText(R.string.tab_local_network);
                        tab.setIcon(R.drawable.ic_wifi);
                        break;
                    case 2:
                        tab.setText(R.string.tab_sync_status);
                        tab.setIcon(R.drawable.ic_info);
                        break;
                }
            }
        }).attach();
    }
    
    private void setupWindowInsets() {
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
                Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return windowInsets;
            });
        }
    }
    
    private boolean isPlayFlavor() {
        return "play".equals(BuildConfig.FLAVOR);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
