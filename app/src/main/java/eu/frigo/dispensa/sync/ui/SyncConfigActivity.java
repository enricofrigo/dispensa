package eu.frigo.dispensa.sync.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.webdav.WebDavRemoteStoreImpl;
import eu.frigo.dispensa.sync.webdav.WebDavSyncProvider;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.Response;

public class SyncConfigActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_config);

        TextInputEditText urlEdit = findViewById(R.id.edit_webdav_url);
        TextInputEditText userEdit = findViewById(R.id.edit_webdav_user);
        TextInputEditText passEdit = findViewById(R.id.edit_webdav_pass);
        TextInputEditText pathEdit = findViewById(R.id.edit_webdav_path);
        Button saveBtn = findViewById(R.id.btn_save_sync_config);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        urlEdit.setText(prefs.getString(SyncManager.KEY_WEBDAV_URL, ""));
        userEdit.setText(prefs.getString(SyncManager.KEY_WEBDAV_USER, ""));
        passEdit.setText(prefs.getString(SyncManager.KEY_WEBDAV_PASS, ""));
        pathEdit.setText(prefs.getString(SyncManager.KEY_WEBDAV_PATH, SyncManager.DEFAULT_PATH));

        saveBtn.setOnClickListener(v -> {
            String url = Objects.requireNonNull(urlEdit.getText()).toString().trim();
            String user = Objects.requireNonNull(userEdit.getText()).toString().trim();
            String pass = Objects.requireNonNull(passEdit.getText()).toString().trim();
            String path = pathEdit.getText()!=null?pathEdit.getText().toString().trim():"";

            if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Compila tutti i campi obbligatori", Toast.LENGTH_SHORT).show();
                return;
            }

            saveBtn.setEnabled(false);
            testConnection(url, user, pass, path)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(success -> {
                        if (success) {
                            prefs.edit()
                                    .putString(SyncManager.KEY_WEBDAV_URL, url)
                                    .putString(SyncManager.KEY_WEBDAV_USER, user)
                                    .putString(SyncManager.KEY_WEBDAV_PASS, pass)
                                    .putString(SyncManager.KEY_WEBDAV_PATH, path)
                                    .apply();

                            // Initialize provider
                            String deviceId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);

                            String normalizedBase = path.endsWith("/") ? path : path + "/";
                            if (normalizedBase.startsWith("/")) normalizedBase = normalizedBase.substring(1);
                            String pantryPath = normalizedBase + SyncManager.DEFAULT_PANTRY_PATH+ SyncManager.DEFAULT_MAIN_PANTRY + "/";

                            WebDavClient client = new WebDavClient(url, user, pass);
                            WebDavRemoteStoreImpl remoteStore = new WebDavRemoteStoreImpl(client);
                            WebDavSyncProvider provider = new WebDavSyncProvider("webdav", remoteStore, client, deviceId, pantryPath);

                            SyncManager.getInstance().setProvider(provider);
                            
                            Toast.makeText(this, "Connessione riuscita e configurazione salvata", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            saveBtn.setEnabled(true);
                            Toast.makeText(this, "Connessione fallita. Controlla i parametri.", Toast.LENGTH_LONG).show();
                        }
                    }, throwable -> {
                        saveBtn.setEnabled(true);
                        Toast.makeText(this, "Errore: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
                    });
        });
    }

    private Single<Boolean> testConnection(String url, String user, String pass, String basePath) {
        return Single.fromCallable(() -> {
            WebDavClient client = new WebDavClient(url, user, pass);
            String deviceId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
            String pantryKey = SyncManager.DEFAULT_MAIN_PANTRY; // V1 default pantry key

            // Standardize base path (ensure it ends with /)
            String normalizedBase = basePath.endsWith("/") ? basePath : basePath + "/";
            if (normalizedBase.startsWith("/")) normalizedBase = normalizedBase.substring(1);

            // 1. Verify/Create Folder Structure
            String syncRoot = normalizedBase + "dispensa-sync/";
            if (!ensureFolderExists(client, syncRoot)) return false;

            String pantriesDir = syncRoot + "pantries/";
            if (!ensureFolderExists(client, pantriesDir)) return false;

            String pantryPath = pantriesDir + pantryKey + "/";
            if (!ensureFolderExists(client, pantryPath)) return false;

            String eventsDir = pantryPath + "events/";
            if (!ensureFolderExists(client, eventsDir)) return false;

            String devicesDir = pantryPath + "devices/";
            if (!ensureFolderExists(client, devicesDir)) return false;

            // 2. Verify/Create manifest.json
            String manifestPath = pantryPath + "manifest.json";
            try (Response response = client.propfind(manifestPath)) {
                if (response.code() == 404) {
                    Map<String, Object> manifest = new HashMap<>();
                    manifest.put("version", 1);
                    manifest.put("pantryKey", pantryKey);
                    manifest.put("createdAt", System.currentTimeMillis());
                    manifest.put("createdByDevice", deviceId);
                    manifest.put("provider", "webdav");
                    manifest.put("snapshotPresent", false);
                    manifest.put("snapshotVersion", 0);
                    manifest.put("lastEventSequence", 0);
                    manifest.put("eventsPath", "events/");
                    manifest.put("devicesPath", "devices/");

                    String json = new com.google.gson.Gson().toJson(manifest);
                    try (Response putResp = client.put(manifestPath, json.getBytes(), null)) {
                        if (!putResp.isSuccessful()) {
                            Log.w("SyncConfigActivity", "Failed to create manifest.json: " + putResp.code());
                            return false;
                        }
                    }
                } else if (!response.isSuccessful() && response.code() != 207) {
                    Log.w("SyncConfigActivity", "Error checking manifest.json: " + response.code());
                    return false;
                }
            }

            return true;
        });
    }

    private boolean ensureFolderExists(WebDavClient client, String folderPath) throws Exception {
        // MKCOL often fails if there is a trailing slash on certain servers
        String cleanPath = folderPath.endsWith("/") ? folderPath.substring(0, folderPath.length() - 1) : folderPath;
        
        try (Response response = client.propfind(cleanPath + "/")) {
            if (response.isSuccessful() || response.code() == 207) {
                return true;
            }
            if (response.code() != 404) {
                Log.w("SyncConfigActivity", "PROPFIND failed for " + cleanPath + ": " + response.code());
                return false;
            }
        }
        
        try (Response response = client.mkcol(cleanPath)) {
            boolean created = response.isSuccessful() || response.code() == 201;
            if (!created) {
                Log.w("SyncConfigActivity", "MKCOL failed for " + cleanPath + ": " + response.code());
            }
            return created;
        }
    }
}
