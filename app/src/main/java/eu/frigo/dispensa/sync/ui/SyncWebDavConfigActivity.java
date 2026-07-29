package eu.frigo.dispensa.sync.ui;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.provider.SyncProvider;
import eu.frigo.dispensa.sync.webdav.WebDavRemoteStoreImpl;
import eu.frigo.dispensa.sync.webdav.WebDavSyncProvider;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import eu.frigo.dispensa.sync.webdav.client.WebDavClientFactory;
import eu.frigo.dispensa.viewmodel.DispensaViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.Response;

public class SyncWebDavConfigActivity extends AppCompatActivity {

    private TextInputEditText urlEdit, userEdit, passEdit, pathEdit;
    private com.google.android.material.textfield.TextInputLayout userLayout, passLayout;
    private com.google.android.material.materialswitch.MaterialSwitch sharedModeSwitch;
    private LinearLayout dispenseContainer;
    private Button saveBtn;
    private ProgressBar progressBar;
    private String savedPassword;
    private DispensaViewModel dispensaViewModel;
    private final List<CheckBox> checkBoxes = new ArrayList<>();

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
        dispenseContainer = findViewById(R.id.container_sync_dispense);
        saveBtn = findViewById(R.id.btn_save_sync_config);
        progressBar = findViewById(R.id.progress_sync_config);

        dispensaViewModel = new ViewModelProvider(this).get(DispensaViewModel.class);
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        urlEdit.setText(prefs.getString(SyncManager.KEY_WEBDAV_URL, ""));
        userEdit.setText(prefs.getString(SyncManager.KEY_WEBDAV_USER, ""));
        
        savedPassword = prefs.getString(SyncManager.KEY_WEBDAV_PASS, "");
        passEdit.setText(savedPassword);
        
        pathEdit.setText(prefs.getString(SyncManager.KEY_WEBDAV_PATH, SyncManager.DEFAULT_PATH));

        String syncedIdsStr = prefs.getString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, "");
        Set<Integer> syncedIds = new HashSet<>();
        if (!syncedIdsStr.isEmpty()) {
            for (String id : syncedIdsStr.split(",")) {
                try { syncedIds.add(Integer.parseInt(id)); } catch (Exception ignored) {}
            }
        }

        dispensaViewModel.getAllDispense().observe(this, dispense -> {
            dispenseContainer.removeAllViews();
            checkBoxes.clear();
            for (Dispensa d : dispense) {
                CheckBox cb = new CheckBox(this);
                cb.setText(d.getName());
                cb.setTag(d);
                if (syncedIds.contains(d.id)) {
                    cb.setChecked(true);
                } else if (syncedIds.isEmpty() && d.isDefault()) {
                    cb.setChecked(true);
                }
                dispenseContainer.addView(cb);
                checkBoxes.add(cb);
            }
        });

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

    @SuppressLint("CheckResult")
    private void startSetupFlow(boolean forceOverwrite) {
        String url = Objects.requireNonNull(urlEdit.getText()).toString().trim();
        boolean isShared = sharedModeSwitch.isChecked();
        String user = isShared ? "" : Objects.requireNonNull(userEdit.getText()).toString().trim();
        String pass = Objects.requireNonNull(passEdit.getText()).toString().trim();
        String path = pathEdit.getText() != null ? pathEdit.getText().toString().trim() : "";

        List<Dispensa> selectedDispense = new ArrayList<>();
        for (CheckBox cb : checkBoxes) {
            if (cb.isChecked()) {
                selectedDispense.add((Dispensa) cb.getTag());
            }
        }

        if (url.isEmpty() || (!isShared && user.isEmpty()) || pass.isEmpty()) {
            Toast.makeText(this, R.string.warn_mandatory_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedDispense.isEmpty()) {
            Toast.makeText(this, "Seleziona almeno una dispensa", Toast.LENGTH_SHORT).show();
            return;
        }

        setUILocked(true);
        
        // Eseguiamo il setup per tutte le dispense selezionate
        List<Single<SetupResult>> setups = new ArrayList<>();
        for (Dispensa d : selectedDispense) {
            setups.add(verifyAndSetup(url, user, pass, path, d, forceOverwrite));
        }

        Single.concat(setups)
                .toList()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(results -> {
                    boolean allSuccess = true;
                    boolean manifestExists = false;
                    for (SetupResult r : results) {
                        if (r.status == RESULT_MANIFEST_EXISTS) manifestExists = true;
                        if (r.status != RESULT_SUCCESS) allSuccess = false;
                    }

                    if (allSuccess) {
                        saveAndFinish(url, user, pass, path, selectedDispense, isShared);
                    } else if (manifestExists) {
                        setUILocked(false);
                        showOverwriteDialog();
                    } else {
                        setUILocked(false);
                        Toast.makeText(this, R.string.sync_setup_err, Toast.LENGTH_LONG).show();
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

    private void save(String url, String user, String pass, String path, List<Dispensa> selectedDispense, boolean isShared){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < selectedDispense.size(); i++) {
            Dispensa d = selectedDispense.get(i);
            ids.append(d.id);
            if (i < selectedDispense.size() - 1) ids.append(",");
            editor.putString(SyncManager.SYNC_WEBDAV_PANTRY_NAME + "_" + d.id, d.getName());
        }

        editor.putString(SyncManager.KEY_WEBDAV_URL, url)
                .putString(SyncManager.KEY_WEBDAV_USER, user)
                .putString(SyncManager.KEY_WEBDAV_PASS, pass)
                .putString(SyncManager.KEY_WEBDAV_PATH, path)
                .putString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, ids.toString())
                .putBoolean(SyncManager.KEY_WEBDAV_MODE_SHARED, isShared)
                .putBoolean(SyncManager.KEY_SYNC_ENABLED, true)
                .apply();
    }

    private void saveAndFinish(String url, String user, String pass, String path, List<Dispensa> selectedDispense, boolean isShared) {
        save(url, user, pass, path, selectedDispense, isShared);
        String deviceId = eu.frigo.dispensa.sync.core.engine.InstallationIdProvider.getOrCreateInstallationId(this);
        String normalizedBase = path.endsWith("/") ? path : path + "/";
        if (normalizedBase.startsWith("/")) normalizedBase = normalizedBase.substring(1);
        
        List<WebDavSyncProvider.SyncScope> scopes = new ArrayList<>();
        for (Dispensa d : selectedDispense) {
            String pantryPath = normalizedBase + SyncManager.getSyncPath(d.getName());
            scopes.add(new WebDavSyncProvider.SyncScope(d.id, pantryPath));
        }

        WebDavClient client = WebDavClientFactory.getInstance().getClient(url, user, pass);
        WebDavRemoteStoreImpl remoteStore = new WebDavRemoteStoreImpl(client);
        SyncProvider provider = new WebDavSyncProvider(remoteStore, client, deviceId, scopes);

        SyncManager.getInstance().setProvider(provider);
        eu.frigo.dispensa.sync.core.engine.SyncCoordinatorImpl.getInstance(this).triggerManualSync();

        Toast.makeText(this, "Sincronizzazione configurata per " + selectedDispense.size() + " dispense", Toast.LENGTH_SHORT).show();
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

    private Single<SetupResult> verifyAndSetup(String url, String user, String pass, String basePath, Dispensa dispensa, boolean forceOverwrite) {
        return Single.fromCallable(() -> {
            WebDavClient client = WebDavClientFactory.getInstance().getClient(url, user, pass);
            String deviceId = InstallationIdProvider.getOrCreateInstallationId(this);
            
            String normalizedBase = basePath.endsWith("/") ? basePath : basePath + "/";
            if (normalizedBase.startsWith("/")) normalizedBase = normalizedBase.substring(1);

            String pantryPath = normalizedBase + SyncManager.getSyncPath(dispensa.getName());

            // 1. Verify/Create Folder Structure
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
            manifest.pantryKey = "";
            manifest.pantryName = dispensa.getName();
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
                return devResp.isSuccessful() ? new SetupResult(RESULT_SUCCESS, "") : new SetupResult(RESULT_FAILED, null);
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

