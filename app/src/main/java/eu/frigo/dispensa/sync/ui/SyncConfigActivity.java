package eu.frigo.dispensa.sync.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Objects;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.webdav.WebDavRemoteStoreImpl;
import eu.frigo.dispensa.sync.webdav.WebDavSyncProvider;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import eu.frigo.dispensa.sync.webdav.client.WebDavClientFactory;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.Response;

public class SyncConfigActivity extends AppCompatActivity {

    private TextInputEditText urlEdit, userEdit, passEdit, pathEdit;
    private com.google.android.material.textfield.TextInputLayout userLayout, passLayout;
    private com.google.android.material.materialswitch.MaterialSwitch sharedModeSwitch;
    private Button saveBtn;
    private ProgressBar progressBar;
    private String savedPassword;

    private static final int RESULT_SUCCESS = 0;
    private static final int RESULT_MANIFEST_EXISTS = 1;
    private static final int RESULT_FAILED = 2;
    private static final int RESULT_DEVICE_EXISTS = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_config);

        urlEdit = findViewById(R.id.edit_webdav_url);
        userEdit = findViewById(R.id.edit_webdav_user);
        userLayout = findViewById(R.id.til_webdav_user);
        sharedModeSwitch = findViewById(R.id.switch_webdav_shared_mode);
        passEdit = findViewById(R.id.edit_webdav_pass);
        passLayout = findViewById(R.id.til_webdav_pass);
        pathEdit = findViewById(R.id.edit_webdav_path);
        saveBtn = findViewById(R.id.btn_save_sync_config);
        progressBar = findViewById(R.id.progress_sync_config);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        urlEdit.setText(prefs.getString(SyncManager.KEY_WEBDAV_URL, ""));
        userEdit.setText(prefs.getString(SyncManager.KEY_WEBDAV_USER, ""));
        
        savedPassword = prefs.getString(SyncManager.KEY_WEBDAV_PASS, "");
        passEdit.setText(savedPassword);
        
        pathEdit.setText(prefs.getString(SyncManager.KEY_WEBDAV_PATH, SyncManager.DEFAULT_PATH));

        boolean isShared = prefs.getBoolean(SyncManager.KEY_WEBDAV_MODE_SHARED, false);
        sharedModeSwitch.setChecked(isShared);
        userLayout.setVisibility(isShared ? View.GONE : View.VISIBLE);

        sharedModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            userLayout.setVisibility(isChecked ? View.GONE : View.VISIBLE);
        });

        updatePasswordToggle(savedPassword);
        passEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePasswordToggle(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        saveBtn.setOnClickListener(v -> startSetupFlow(false));
    }

    private void updatePasswordToggle(String currentText) {
        if (currentText.equals(savedPassword) && !currentText.isEmpty()) {
            passLayout.setEndIconMode(TextInputLayout.END_ICON_NONE);
        } else {
            passLayout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        }
    }

    private void setUILocked(boolean locked) {
        urlEdit.setEnabled(!locked);
        userEdit.setEnabled(!locked);
        passEdit.setEnabled(!locked);
        pathEdit.setEnabled(!locked);
        saveBtn.setEnabled(!locked);
        progressBar.setVisibility(locked ? View.VISIBLE : View.GONE);
    }

    private void startSetupFlow(boolean forceOverwrite) {
        String url = Objects.requireNonNull(urlEdit.getText()).toString().trim();
        boolean isShared = sharedModeSwitch.isChecked();
        String user = isShared ? "" : Objects.requireNonNull(userEdit.getText()).toString().trim();
        String pass = Objects.requireNonNull(passEdit.getText()).toString().trim();
        String path = pathEdit.getText() != null ? pathEdit.getText().toString().trim() : "";

        if (url.isEmpty() || (!isShared && user.isEmpty()) || pass.isEmpty()) {
            Toast.makeText(this, "Compila tutti i campi obbligatori", Toast.LENGTH_SHORT).show();
            return;
        }

        setUILocked(true);
        verifyAndSetup(url, user, pass, path, forceOverwrite)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (result.status == RESULT_SUCCESS) {
                        saveAndFinish(url, user, pass, path, result.pantryKey, isShared);
                    } else if (result.status == RESULT_MANIFEST_EXISTS) {
                        setUILocked(false);
                        showOverwriteDialog();
                    } else if (result.status == RESULT_DEVICE_EXISTS) {
                        setUILocked(false);
                        Toast.makeText(this, "Questo dispositivo è già registrato in questa dispensa.", Toast.LENGTH_LONG).show();
                    } else {
                        setUILocked(false);
                        Toast.makeText(this, "Connessione o configurazione fallita.", Toast.LENGTH_LONG).show();
                    }
                }, throwable -> {
                    setUILocked(false);
                    Toast.makeText(this, "Errore: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void showOverwriteDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.sync_webdav_warn_existing))
                .setMessage(getString(R.string.sync_webdav_warn_existing_desc))
                .setPositiveButton(getString(R.string.overwrite), (dialog, which) -> startSetupFlow(true))
                .setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void saveAndFinish(String url, String user, String pass, String path, String pantryKey, boolean isShared) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit()
                .putString(SyncManager.KEY_WEBDAV_URL, url)
                .putString(SyncManager.KEY_WEBDAV_USER, user)
                .putString(SyncManager.KEY_WEBDAV_PASS, pass)
                .putString(SyncManager.KEY_WEBDAV_PATH, path)
                .putString(SyncManager.SYNC_WEBDAV_PANTRY_KEY, pantryKey)
                .putBoolean(SyncManager.KEY_WEBDAV_MODE_SHARED, isShared)
                .putBoolean(SyncManager.KEY_SYNC_ENABLED, true)
                .apply();

        String deviceId = eu.frigo.dispensa.sync.core.engine.InstallationIdProvider.getOrCreateInstallationId(this);
        String normalizedBase = path.endsWith("/") ? path : path + "/";
        if (normalizedBase.startsWith("/")) normalizedBase = normalizedBase.substring(1);
        String pantryPath = normalizedBase + SyncManager.DEFAULT_PANTRY_PATH + pantryKey + "/";

        WebDavClient client = WebDavClientFactory.getInstance().getClient(url, user, pass);
        WebDavRemoteStoreImpl remoteStore = new WebDavRemoteStoreImpl(client);
        WebDavSyncProvider provider = new WebDavSyncProvider("webdav", remoteStore, client, deviceId, pantryPath);

        SyncManager.getInstance().setProvider(provider);
        eu.frigo.dispensa.sync.core.engine.SyncCoordinatorImpl.getInstance(this).triggerManualSync();

        Toast.makeText(this, "Configurazione salvata con successo", Toast.LENGTH_SHORT).show();
        finish();
    }

    private static class SetupResult {
        final int status;
        final String pantryKey;
        SetupResult(int status, String pantryKey) {
            this.status = status;
            this.pantryKey = pantryKey;
        }
    }

    private Single<SetupResult> verifyAndSetup(String url, String user, String pass, String basePath, boolean forceOverwrite) {
        return Single.fromCallable(() -> {
            WebDavClient client = WebDavClientFactory.getInstance().getClient(url, user, pass);
            String deviceId = InstallationIdProvider.getOrCreateInstallationId(this);
            
            // Append group ID to pantry key
            String pantryKey = SyncManager.DEFAULT_MAIN_PANTRY;

            String normalizedBase = basePath.endsWith("/") ? basePath : basePath + "/";
            if (normalizedBase.startsWith("/")) normalizedBase = normalizedBase.substring(1);

            // 1. Verify/Create Folder Structure
            if (!ensureFolderExists(client, normalizedBase + SyncManager.DEFAULT_SYNC_PATH )) return new SetupResult(RESULT_FAILED, null);
            if (!ensureFolderExists(client, normalizedBase + SyncManager.DEFAULT_PANTRY_PATH )) return new SetupResult(RESULT_FAILED, null);
            String pantryPath = normalizedBase + SyncManager.DEFAULT_PANTRY_PATH  + pantryKey + "/";
            if (!ensureFolderExists(client, pantryPath)) return new SetupResult(RESULT_FAILED, null);
            if (!ensureFolderExists(client, pantryPath + SyncManager.DEFAULT_EVENTS_FOLDER)) return new SetupResult(RESULT_FAILED, null);
            if (!ensureFolderExists(client, pantryPath + SyncManager.DEFAULT_DEVICES_FOLDER)) return new SetupResult(RESULT_FAILED, null);
            if (!ensureFolderExists(client, pantryPath + SyncManager.DEFAULT_SNAPSHOTS_FOLDER)) return new SetupResult(RESULT_FAILED, null);

            // 1b. Check if device is already registered
            String devicePath = pantryPath + SyncManager.DEFAULT_DEVICES_FOLDER + deviceId + ".json";
            try (Response response = client.propfind(devicePath)) {
                if (response.isSuccessful() || response.code() == 207) {
                    if (!forceOverwrite) {
                        return new SetupResult(RESULT_DEVICE_EXISTS, null);
                    }
                }
            }

            // 2. Check manifest.json
            String manifestPath = pantryPath + SyncManager.MANIFEST_JSON;
            try (Response response = client.propfind(manifestPath)) {
                if (response.isSuccessful() || response.code() == 207) {
                    if (!forceOverwrite) {
                        return new SetupResult(RESULT_MANIFEST_EXISTS, null);
                    }
                } else if (response.code() != 404) {
                    return new SetupResult(RESULT_FAILED, null);
                }
            }

            // 3. Create/Overwrite manifest.json
            eu.frigo.dispensa.sync.webdav.model.WebDavManifest manifest = new eu.frigo.dispensa.sync.webdav.model.WebDavManifest();
            manifest.version = 1;
            manifest.pantryKey = pantryKey;
            manifest.createdAt = System.currentTimeMillis();
            manifest.createdByDevice = deviceId;
            manifest.provider = "webdav";
            manifest.latestSnapshotId = null;
            manifest.lastGlobalTimestamp = 0;

            String json = new com.google.gson.Gson().toJson(manifest);
            try (Response putResp = client.put(manifestPath, json.getBytes(), null)) {
                if (!putResp.isSuccessful()) return new SetupResult(RESULT_FAILED, null);
            }

            // 4. Register current device
            eu.frigo.dispensa.sync.webdav.model.WebDavDevice device = new eu.frigo.dispensa.sync.webdav.model.WebDavDevice();
            device.deviceId = deviceId;
            device.deviceName = android.os.Build.MODEL;
            device.lastSeen = System.currentTimeMillis();
            
            String deviceJson = new com.google.gson.Gson().toJson(device);
            try (Response devResp = client.put(devicePath, deviceJson.getBytes(), null)) {
                return devResp.isSuccessful() ? new SetupResult(RESULT_SUCCESS, pantryKey) : new SetupResult(RESULT_FAILED, null);
            }
        });
    }

    private boolean ensureFolderExists(WebDavClient client, String folderPath) throws Exception {
        String cleanPath = folderPath.endsWith("/") ? folderPath.substring(0, folderPath.length() - 1) : folderPath;
        try (Response response = client.propfind(cleanPath + "/")) {
            if (response.isSuccessful() || response.code() == 207) return true;
            if (response.code() != 404) return false;
        }
        try (Response response = client.mkcol(cleanPath)) {
            return response.isSuccessful() || response.code() == 201;
        }
    }
}
