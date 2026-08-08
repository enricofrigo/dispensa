package eu.frigo.dispensa.activity;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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
import eu.frigo.dispensa.util.WebDavSetupHelper;
import eu.frigo.dispensa.viewmodel.DispensaViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
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

        saveBtn.setOnClickListener(v -> startSetupFlow());
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
    private void startSetupFlow() {
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

        setUILocked(true);
        
        WebDavClient client = WebDavClientFactory.getInstance().getClient(url, user, pass);
        
        // 1. Validazione credenziali (semplice propfind sulla root o path base)
        Single.fromCallable(() -> {
            try (Response response = client.propfind("")) {
                return response.isSuccessful() || response.code() == 207 || response.code() == 404; // 404 is ok, means path doesn't exist yet but credentials work
            }
        })
        .flatMap(valid -> {
            save(url, user, pass, path, selectedDispense, isShared);
            if (!valid) return Single.error(new Exception("Credenziali non valide o server non raggiungibile"));
            
            if (selectedDispense.isEmpty()) return Single.just(new ArrayList<Boolean>());

            // 2. Preparazione server per ogni dispensa selezionata
            return Observable.fromIterable(selectedDispense)
                    .concatMapSingle(d -> WebDavSetupHelper.preparePantryOnServer(this, client, d.getName()))
                    .toList();
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(results -> {
            finish(url, user, pass, path, selectedDispense, isShared);
        }, throwable -> {
            setUILocked(false);
            Log.e("SyncConfig", "Setup failed", throwable);
            Toast.makeText(this, "Errore: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void finish(String url, String user, String pass, String path, List<Dispensa> selectedDispense, boolean isShared) {

        SyncManager.getInstance().getOrInitProvider(this);
        eu.frigo.dispensa.sync.core.engine.SyncCoordinatorImpl.getInstance(this).triggerManualSync();

        Toast.makeText(this, "Sincronizzazione configurata correttamente", Toast.LENGTH_SHORT).show();
        finish();
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
}

