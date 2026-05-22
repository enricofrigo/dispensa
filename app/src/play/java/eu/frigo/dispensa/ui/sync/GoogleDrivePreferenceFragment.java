package eu.frigo.dispensa.ui.sync;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import eu.frigo.dispensa.R;

/**
 * Google Drive sync configuration as PreferenceFragmentCompat (play flavor only).
 */
public class GoogleDrivePreferenceFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    
    private static final String TAG = "GoogleDrivePreferenceFragment";
    
    private Preference signInPref;
    private ListPreference modePref;
    
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_google_drive, rootKey);
        
        setupDriveSwitch();
        setupSignInButton();
        setupModeSelector();
        
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
    
    private void setupDriveSwitch() {
        SwitchPreferenceCompat switchDrive = findPreference("sync_drive_enabled");
        if (switchDrive != null) {
            switchDrive.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                updateDriveFieldsVisibility(enabled);
                if (enabled) {
                    checkGoogleSignIn();
                }
                Log.d(TAG, "Google Drive sync " + (enabled ? "enabled" : "disabled"));
                return true;
            });
            
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            boolean enabled = prefs.getBoolean("sync_drive_enabled", false);
            updateDriveFieldsVisibility(enabled);
        }
    }
    
    private void setupSignInButton() {
        signInPref = findPreference("pref_drive_sign_in");
        if (signInPref != null) {
            signInPref.setOnPreferenceClickListener(preference -> {
                signInWithGoogle();
                return true;
            });
        }
    }
    
    private void setupModeSelector() {
        modePref = findPreference("pref_drive_mode");
        if (modePref != null) {
            modePref.setOnPreferenceChangeListener((preference, newValue) -> {
                String mode = (String) newValue;
                Log.d(TAG, "Google Drive mode set to: " + mode);
                return true;
            });
        }
    }
    
    private void updateDriveFieldsVisibility(boolean visible) {
        if (signInPref != null) signInPref.setVisible(visible);
        if (modePref != null) modePref.setVisible(visible);
        Preference folderIdPref = findPreference("pref_drive_household_folder_id");
        if (folderIdPref != null) folderIdPref.setVisible(visible);
        Preference createPref = findPreference("pref_drive_create_household");
        if (createPref != null) createPref.setVisible(visible);
        Preference joinPref = findPreference("pref_drive_join_household");
        if (joinPref != null) joinPref.setVisible(visible);
    }
    
    private void checkGoogleSignIn() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireActivity());
        if (account != null) {
            showSuccess("Already signed in as: " + account.getDisplayName());
        } else {
            showToast("Please sign in with Google");
        }
    }
    
    private void signInWithGoogle() {
        showToast("Google Sign-In - TODO");
        Log.d(TAG, "Starting Google Sign-In");
        // TODO: Implement Google Sign-In flow
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if ("sync_drive_enabled".equals(key)) {
            boolean enabled = sharedPreferences.getBoolean(key, false);
            updateDriveFieldsVisibility(enabled);
        }
    }
    
    private void showToast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
    
    private void showSuccess(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
}
