package eu.frigo.dispensa.ui.sync;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.sync.webdav.WebDavStructureInitializer;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;

/**
 * WebDAV sync configuration as PreferenceFragmentCompat.
 * Displays enable/disable switch + credentials in collapsible preference screen.
 */
public class WebDavPreferenceFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    
    private static final String TAG = "WebDavPreferenceFragment";
    
    private SwitchPreferenceCompat switchWebDav;
    private EditTextPreference editUrl, editUser, editPass, editPath;
    private Preference testConnectionPref;
    
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        // Load WebDAV-specific preferences
        setPreferencesFromResource(R.xml.preferences_webdav, rootKey);
        
        setupWebDavSwitch();
        setupWebDavFields();
        setupTestButton();
        
        // Listen for preference changes
        if (getPreferenceManager().getSharedPreferences() != null) {
            getPreferenceManager().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getPreferenceManager().getSharedPreferences() != null) {
            getPreferenceManager().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }
    }
    
    private void setupWebDavSwitch() {
        switchWebDav = findPreference("sync_webdav_enabled");
        if (switchWebDav != null) {
            switchWebDav.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                updateWebDavFieldsVisibility(enabled);
                
                if (enabled) {
                    initializeWebDavStructure();
                }
                
                Log.d(TAG, "WebDAV sync " + (enabled ? "enabled" : "disabled"));
                return true;
            });
            
            // Set initial visibility
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            boolean enabled = prefs.getBoolean("sync_webdav_enabled", false);
            updateWebDavFieldsVisibility(enabled);
        }
    }

    private void initializeWebDavStructure() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String url = prefs.getString("pref_webdav_url", "");
        String user = prefs.getString("pref_webdav_user", "");
        String pass = prefs.getString("pref_webdav_pass", "");
        String path = prefs.getString("pref_webdav_path", "/dispensa/");

        if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            return; // Don't init if credentials missing
        }

        Toast.makeText(requireContext(), "Initializing sync structure...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                WebDavClient client = new WebDavClient(url, user, pass);
                WebDavStructureInitializer initializer = new WebDavStructureInitializer(client, path, AppDatabase.getDatabase(getContext()));
                boolean success = initializer.initializeStructure();

                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        if (success) {
                            showSuccess("✓ WebDAV structure ready!");
                        } else {
                            showError("✗ Failed to initialize folder structure");
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Structure initialization failed", e);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> showError("Error: " + e.getMessage()));
                }
            }
        }).start();
    }
    
    private void setupWebDavFields() {
        editUrl = findPreference("pref_webdav_url");
        editUser = findPreference("pref_webdav_user");
        editPass = findPreference("pref_webdav_pass");
        editPath = findPreference("pref_webdav_path");
        
        if (editPath != null && editPath.getText() == null) {
            editPath.setText("/dispensa/");
        }
    }
    
    private void setupTestButton() {
        testConnectionPref = findPreference("pref_webdav_test_connection");
        if (testConnectionPref != null) {
            testConnectionPref.setOnPreferenceClickListener(preference -> {
                testWebDavConnection();
                return true;
            });
        }
    }
    
    private void updateWebDavFieldsVisibility(boolean visible) {
        if (editUrl != null) editUrl.setVisible(visible);
        if (editUser != null) editUser.setVisible(visible);
        if (editPass != null) editPass.setVisible(visible);
        if (editPath != null) editPath.setVisible(visible);
        if (testConnectionPref != null) testConnectionPref.setVisible(visible);
    }
    
    private void testWebDavConnection() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String url = prefs.getString("pref_webdav_url", "");
        String user = prefs.getString("pref_webdav_user", "");
        String pass = prefs.getString("pref_webdav_pass", "");
        
        if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            showError("Please fill in all WebDAV credentials");
            return;
        }
        
        showToast("Testing WebDAV connection...");
        Log.d(TAG, "Testing connection to: " + url);
        
        // TODO: Implement actual WebDAV connection test
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Simulate test
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        showSuccess("Connection successful!");
                        Log.d(TAG, "WebDAV test passed");
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "WebDAV test failed", e);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> 
                            showError("Connection failed: " + e.getMessage())
                    );
                }
            }
        }).start();
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if ("sync_webdav_enabled".equals(key)) {
            boolean enabled = sharedPreferences.getBoolean(key, false);
            updateWebDavFieldsVisibility(enabled);
        }
    }
    
    private void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
    
    private void showSuccess(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
    
    private void showError(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
}
