package eu.frigo.dispensa.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.sync.core.engine.SyncCoordinatorImpl;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.local.LocalSafSyncProviderLoader;

public class SyncLocalSafConfigActivity extends AppCompatActivity {
    private static final String TAG = "SyncLocalSafConfig";

    private TextView statusTextView;
    private TextView folderPathTextView;
    private Button selectFolderButton;
    private Button testPermissionsButton;
    private Button clearFolderButton;
    private Button saveButton;
    private ProgressBar progressBar;

    private Uri selectedTreeUri;

    private final ActivityResultLauncher<Uri> openDocumentTreeLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),
            uri -> {
                if (uri != null) {
                    try {
                        getContentResolver().takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        );
                    } catch (SecurityException e) {
                        Log.w(TAG, "Failed to take persistable URI permission", e);
                    }
                    selectedTreeUri = uri;
                    updateUi();
                    Toast.makeText(this, getString(R.string.sync_local_saf_selected_folder, uri.getPath()), Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_local_saf_config);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_saf_config);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.sync_local_saf_config_title);
        }

        statusTextView = findViewById(R.id.tv_saf_status);
        folderPathTextView = findViewById(R.id.tv_saf_folder_path);
        selectFolderButton = findViewById(R.id.btn_saf_select_folder);
        testPermissionsButton = findViewById(R.id.btn_saf_test_permissions);
        clearFolderButton = findViewById(R.id.btn_saf_clear_folder);
        saveButton = findViewById(R.id.btn_save_saf_config);
        progressBar = findViewById(R.id.progress_saf_config);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String savedUriStr = prefs.getString(LocalSafSyncProviderLoader.PREF_KEY_SAF_URI, null);
        if (savedUriStr != null && !savedUriStr.trim().isEmpty()) {
            selectedTreeUri = Uri.parse(savedUriStr);
        }

        selectFolderButton.setOnClickListener(v -> openDocumentTreeLauncher.launch(selectedTreeUri));

        testPermissionsButton.setOnClickListener(v -> testPermissions());

        clearFolderButton.setOnClickListener(v -> {
            selectedTreeUri = null;
            updateUi();
            Toast.makeText(this, R.string.sync_local_saf_no_folder, Toast.LENGTH_SHORT).show();
        });

        saveButton.setOnClickListener(v -> saveConfiguration());

        updateUi();
    }

    private void updateUi() {
        if (selectedTreeUri != null) {
            statusTextView.setText(R.string.sync_status_connected);
            statusTextView.setTextColor(getColor(R.color.teal_700));
            folderPathTextView.setText(selectedTreeUri.toString());
            testPermissionsButton.setEnabled(true);
            clearFolderButton.setEnabled(true);
        } else {
            statusTextView.setText(R.string.sync_local_saf_no_folder);
            statusTextView.setTextColor(getColor(android.R.color.darker_gray));
            folderPathTextView.setText("");
            testPermissionsButton.setEnabled(false);
            clearFolderButton.setEnabled(false);
        }
    }

    private void testPermissions() {
        if (selectedTreeUri == null) return;
        try {
            DocumentFile docFile = DocumentFile.fromTreeUri(this, selectedTreeUri);
            if (docFile != null && docFile.canRead() && docFile.canWrite()) {
                Toast.makeText(this, R.string.sync_local_saf_permission_ok, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.sync_local_saf_permission_err, "Cannot read/write folder"), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Permission test failed", e);
            Toast.makeText(this, getString(R.string.sync_local_saf_permission_err, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void saveConfiguration() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (selectedTreeUri == null) {
            prefs.edit().remove(LocalSafSyncProviderLoader.PREF_KEY_SAF_URI).apply();
            Toast.makeText(this, R.string.sync_setup_saved, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        prefs.edit()
                .putString(LocalSafSyncProviderLoader.PREF_KEY_SAF_URI, selectedTreeUri.toString())
                .putBoolean(SyncManager.KEY_SYNC_ENABLED, true)
                .apply();

        SyncCoordinatorImpl.getInstance(this).triggerManualSync();
        Toast.makeText(this, R.string.sync_setup_saved, Toast.LENGTH_SHORT).show();
        finish();
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
