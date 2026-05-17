package eu.frigo.dispensa.ui.sync;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreferenceCompat;
import eu.frigo.dispensa.R;

/**
 * Main synchronization configuration fragment.
 * Displays collapsible sections for WebDAV, Local Network, and Google Drive.
 * Hosted by PreferenceScreen in preferences.xml
 */
public class SyncConfigurationFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    
    private static final String TAG = "SyncConfigurationFragment";
    
    // Preference keys
    private static final String PREF_WEBDAV_ENABLED = "sync_webdav_enabled";
    private static final String PREF_LOCAL_NETWORK_ENABLED = "sync_local_network_enabled";
    private static final String PREF_DRIVE_ENABLED = "sync_drive_enabled";
    
    // Collapsible preference categories
    private PreferenceGroup webdavCategory;
    private PreferenceGroup localNetworkCategory;
    private PreferenceGroup driveCategory;
    
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_sync, rootKey);
        
        // Get collapsible categories
        webdavCategory = findPreference("pref_cat_webdav");
        localNetworkCategory = findPreference("pref_cat_local_network");
        driveCategory = findPreference("pref_cat_drive");
        
        setupWebDavSection();
        setupLocalNetworkSection();
        setupGoogleDriveSection();
        
        // Listen for preference changes
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getPreferenceManager().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }
    
    private void setupWebDavSection() {
        SwitchPreferenceCompat switchWebDav = findPreference(PREF_WEBDAV_ENABLED);
        if (switchWebDav != null) {
            switchWebDav.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                Log.d(TAG, "WebDAV sync " + (enabled ? "enabled" : "disabled"));
                // Toggle visibility of WebDAV fields
                updateWebDavFieldsVisibility(enabled);
                return true;
            });
            
            // Set initial visibility
            SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
            boolean webdavEnabled = prefs.getBoolean(PREF_WEBDAV_ENABLED, false);
            updateWebDavFieldsVisibility(webdavEnabled);
        }
        
        // Setup WebDAV test button
        Preference testWebDavPref = findPreference("pref_webdav_test_connection");
        if (testWebDavPref != null) {
            testWebDavPref.setOnPreferenceClickListener(preference -> {
                testWebDavConnection();
                return true;
            });
        }
    }
    
    private void setupLocalNetworkSection() {
        SwitchPreferenceCompat switchLocal = findPreference(PREF_LOCAL_NETWORK_ENABLED);
        if (switchLocal != null) {
            switchLocal.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                Log.d(TAG, "Local Network sync " + (enabled ? "enabled" : "disabled"));
                // TODO: Start/stop NSD discovery
                return true;
            });
        }
        
        // Scan for peers button
        Preference scanPeersPref = findPreference("pref_local_scan_peers");
        if (scanPeersPref != null) {
            scanPeersPref.setOnPreferenceClickListener(preference -> {
                scanForPeers();
                return true;
            });
        }
    }
    
    private void setupGoogleDriveSection() {
        // Google Drive configuration (only available in play flavor)
        SwitchPreferenceCompat switchDrive = findPreference(PREF_DRIVE_ENABLED);
        if (switchDrive != null) {
            switchDrive.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                Log.d(TAG, "Google Drive sync " + (enabled ? "enabled" : "disabled"));
                if (enabled) {
                    // TODO: Launch Google Sign-In
                }
                return true;
            });
        }
    }
    
    private void updateWebDavFieldsVisibility(boolean visible) {
        if (webdavCategory == null) return;
        
        for (int i = 0; i < webdavCategory.getPreferenceCount(); i++) {
            Preference pref = webdavCategory.getPreference(i);
            if (!(pref instanceof SwitchPreferenceCompat)) {
                pref.setVisible(visible);
            }
        }
    }
    
    private void testWebDavConnection() {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        String url = prefs.getString("pref_webdav_url", "");
        String user = prefs.getString("pref_webdav_user", "");
        String pass = prefs.getString("pref_webdav_pass", "");
        
        if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            showToast("Please fill in all WebDAV credentials");
            return;
        }
        
        // TODO: Test WebDAV connection asynchronously
        showToast("Testing WebDAV connection...");
        Log.d(TAG, "Testing connection to: " + url);
    }
    
    private void scanForPeers() {
        // TODO: Implement peer discovery via LocalNetworkSyncTransport
        showToast("Scanning for peers...");
        Log.d(TAG, "Scanning for local network peers");
    }
    
    private void showToast(String message) {
        android.widget.Toast.makeText(requireContext(), message, 
                android.widget.Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        if (PREF_WEBDAV_ENABLED.equals(key)) {
            boolean enabled = sharedPreferences.getBoolean(key, false);
            updateWebDavFieldsVisibility(enabled);
        }
    }
}
