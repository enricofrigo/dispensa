package eu.frigo.dispensa.ui.sync;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;

/**
 * Detailed WebDAV configuration fragment.
 * Allows user to:
 * - Enter WebDAV server credentials
 * - Test connection
 * - Save configuration
 */
public class WebDavConfigFragment extends Fragment {
    
    private static final String TAG = "WebDavConfigFragment";
    
    private EditText editUrl, editUser, editPass, editPath;
    private Button btnTest, btnSave;
    private ProgressBar progressTest;
    private TextView textTestResult;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_webdav_config, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        editUrl = view.findViewById(R.id.edit_webdav_url);
        editUser = view.findViewById(R.id.edit_webdav_user);
        editPass = view.findViewById(R.id.edit_webdav_pass);
        editPath = view.findViewById(R.id.edit_webdav_path);
        btnTest = view.findViewById(R.id.btn_webdav_test);
        btnSave = view.findViewById(R.id.btn_webdav_save);
        progressTest = view.findViewById(R.id.progress_webdav_test);
        textTestResult = view.findViewById(R.id.text_webdav_test_result);
        
        loadSavedConfiguration();
        setupListeners();
    }
    
    private void loadSavedConfiguration() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        editUrl.setText(prefs.getString("pref_webdav_url", ""));
        editUser.setText(prefs.getString("pref_webdav_user", ""));
        editPass.setText(prefs.getString("pref_webdav_pass", ""));
        editPath.setText(prefs.getString("pref_webdav_path", "/dispensa/"));
    }
    
    private void setupListeners() {
        btnTest.setOnClickListener(v -> testConnection());
        btnSave.setOnClickListener(v -> saveConfiguration());
    }
    
    private void testConnection() {
        String url = editUrl.getText().toString().trim();
        String user = editUser.getText().toString().trim();
        String pass = editPass.getText().toString().trim();
        
        if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            showError("All fields are required");
            return;
        }
        
        progressTest.setVisibility(View.VISIBLE);
        textTestResult.setVisibility(View.GONE);
        btnTest.setEnabled(false);
        
        // Test connection on background thread
        new Thread(() -> {
            try {
                WebDavClient client = new WebDavClient(url, user, pass);
                // Attempt a simple PROPFIND to verify credentials
                boolean success = client.testConnection();
                
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        progressTest.setVisibility(View.GONE);
                        btnTest.setEnabled(true);
                        textTestResult.setVisibility(View.VISIBLE);
                        
                        if (success) {
                            textTestResult.setTextColor(
                                    android.graphics.Color.parseColor("#4CAF50")); // Green
                            textTestResult.setText("✓ Connection successful!");
                        } else {
                            textTestResult.setTextColor(
                                    android.graphics.Color.parseColor("#F44336")); // Red
                            textTestResult.setText("✗ Connection failed");
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Connection test failed", e);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        progressTest.setVisibility(View.GONE);
                        btnTest.setEnabled(true);
                        textTestResult.setVisibility(View.VISIBLE);
                        textTestResult.setTextColor(
                                android.graphics.Color.parseColor("#F44336"));
                        textTestResult.setText("✗ Error: " + e.getMessage());
                    });
                }
            }
        }).start();
    }
    
    private void saveConfiguration() {
        String url = editUrl.getText().toString().trim();
        String user = editUser.getText().toString().trim();
        String pass = editPass.getText().toString().trim();
        String path = editPath.getText().toString().trim();
        
        if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            showError("URL, username, and password are required");
            return;
        }
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        prefs.edit()
                .putString("pref_webdav_url", url)
                .putString("pref_webdav_user", user)
                .putString("pref_webdav_pass", pass)
                .putString("pref_webdav_path", path)
                .putBoolean("sync_webdav_enabled", true)
                .apply();
        
        showSuccess("WebDAV configuration saved");
        Log.d(TAG, "WebDAV configuration saved: " + url);
    }
    
    private void showError(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
    
    private void showSuccess(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
}
