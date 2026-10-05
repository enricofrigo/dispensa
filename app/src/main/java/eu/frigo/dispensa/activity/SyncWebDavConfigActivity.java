package eu.frigo.dispensa.activity;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Objects;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.sync.core.engine.SyncCoordinatorImpl;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import eu.frigo.dispensa.sync.webdav.client.WebDavClientFactory;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.Response;

public class SyncWebDavConfigActivity extends AppCompatActivity {

    private TextInputEditText urlEdit, userEdit, passEdit, pathEdit;
    private TextInputLayout userLayout, passLayout;
    private MaterialSwitch sharedModeSwitch;
    private Button saveBtn;
    private ProgressBar progressBar;
    private String savedPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_config);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_sync_config);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }

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
        sharedModeSwitch.setEnabled(!locked);
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

        if (url.isEmpty() || (!isShared && user.isEmpty()) || pass.isEmpty()) {
            Toast.makeText(this, R.string.warn_mandatory_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        setUILocked(true);

        WebDavClient client = WebDavClientFactory.getInstance().getClient(url, user, pass);

        // Validazione credenziali (semplice propfind sulla root o path base)
        Single.fromCallable(() -> {
            try (Response response = client.propfind("")) {
                return response.isSuccessful() || response.code() == 207 || response.code() == 404; // 404 is ok, means path doesn't exist yet but credentials work
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(valid -> {
            if (valid) {
                save(url, user, pass, path, isShared);
                SyncManager.getInstance().getOrInitProvider(this)
                        .subscribe(provider -> {
                            SyncCoordinatorImpl.getInstance(this).triggerManualSync();
                            Toast.makeText(this, "Sincronizzazione WebDAV configurata correttamente", Toast.LENGTH_SHORT).show();
                            finish();
                        }, throwable -> {
                            Log.e("SyncConfig", "Failed to init provider", throwable);
                            Toast.makeText(this, "Configurazione salvata con successo", Toast.LENGTH_SHORT).show();
                            finish();
                        });
            } else {
                setUILocked(false);
                Toast.makeText(this, "Credenziali non valide o server WebDAV non raggiungibile", Toast.LENGTH_LONG).show();
            }
        }, throwable -> {
            setUILocked(false);
            Log.e("SyncConfig", "Setup failed", throwable);
            Toast.makeText(this, "Errore di connessione: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void save(String url, String user, String pass, String path, boolean isShared) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit()
                .putString(SyncManager.KEY_WEBDAV_URL, url)
                .putString(SyncManager.KEY_WEBDAV_USER, user)
                .putString(SyncManager.KEY_WEBDAV_PASS, pass)
                .putString(SyncManager.KEY_WEBDAV_PATH, path)
                .putBoolean(SyncManager.KEY_WEBDAV_MODE_SHARED, isShared)
                .putBoolean(SyncManager.KEY_SYNC_ENABLED, true)
                .apply();
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

