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

/**
 * Local Network (mDNS) sync configuration as PreferenceFragmentCompat.
 */
public class LocalNetworkPreferenceFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    
    private static final String TAG = "LocalNetworkPreferenceFragment";
    
    private SwitchPreferenceCompat switchLocal;
    private EditTextPreference editDeviceName;
    private Preference scanPeersPref;
    
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_local_network, rootKey);
        
        setupLocalSwitch();
        setupDeviceNameField();
        setupScanButton();
        
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
    
    private void setupLocalSwitch() {
        switchLocal = findPreference("sync_local_network_enabled");
        if (switchLocal != null) {
            switchLocal.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                updateLocalFieldsVisibility(enabled);
                Log.d(TAG, "Local Network sync " + (enabled ? "enabled" : "disabled"));
                // TODO: Start/stop mDNS discovery
                return true;
            });
            
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            boolean enabled = prefs.getBoolean("sync_local_network_enabled", false);
            updateLocalFieldsVisibility(enabled);
        }
    }
    
    private void setupDeviceNameField() {
        editDeviceName = findPreference("pref_local_device_name");
        if (editDeviceName != null) {
            editDeviceName.setOnPreferenceChangeListener((preference, newValue) -> {
                String deviceName = (String) newValue;
                if (deviceName.isEmpty()) {
                    showError("Device name cannot be empty");
                    return false;
                }
                Log.d(TAG, "Device name set to: " + deviceName);
                return true;
            });
        }
    }
    
    private void setupScanButton() {
        scanPeersPref = findPreference("pref_local_scan_peers");
        if (scanPeersPref != null) {
            scanPeersPref.setOnPreferenceClickListener(preference -> {
                scanForPeers();
                return true;
            });
        }
    }
    
    private void updateLocalFieldsVisibility(boolean visible) {
        if (editDeviceName != null) editDeviceName.setVisible(visible);
        if (scanPeersPref != null) scanPeersPref.setVisible(visible);
        Preference pairedPref = findPreference("pref_local_paired_devices");
        if (pairedPref != null) pairedPref.setVisible(visible);
    }
    
    private void scanForPeers() {
        showToast("Scanning for peers...");
        Log.d(TAG, "Starting mDNS peer discovery");
        
        // TODO: Integrate with LocalNetworkSyncTransport.startDiscovery()
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if ("sync_local_network_enabled".equals(key)) {
            boolean enabled = sharedPreferences.getBoolean(key, false);
            updateLocalFieldsVisibility(enabled);
        }
    }
    
    private void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
    
    private void showError(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
}
