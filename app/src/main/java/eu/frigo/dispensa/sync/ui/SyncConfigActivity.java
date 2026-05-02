package eu.frigo.dispensa.sync.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import com.google.android.material.textfield.TextInputEditText;

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
    public static final String KEY_WEBDAV_URL = "sync_webdav_url";
    public static final String KEY_WEBDAV_USER = "sync_webdav_user";
    public static final String KEY_WEBDAV_PASS = "sync_webdav_pass";
    public static final String KEY_WEBDAV_PATH = "sync_webdav_path";

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
        urlEdit.setText(prefs.getString(KEY_WEBDAV_URL, ""));
        userEdit.setText(prefs.getString(KEY_WEBDAV_USER, ""));
        passEdit.setText(prefs.getString(KEY_WEBDAV_PASS, ""));
        pathEdit.setText(prefs.getString(KEY_WEBDAV_PATH, "/dispensa/"));

        saveBtn.setOnClickListener(v -> {
            String url = Objects.requireNonNull(urlEdit.getText()).toString().trim();
            String user = Objects.requireNonNull(userEdit.getText()).toString().trim();
            String pass = Objects.requireNonNull(passEdit.getText()).toString().trim();
            String path = Objects.requireNonNull(pathEdit.getText()).toString().trim();

            if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Compila tutti i campi obbligatori", Toast.LENGTH_SHORT).show();
                return;
            }

            saveBtn.setEnabled(false);
            testConnection(url, user, pass)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(success -> {
                        if (success) {
                            prefs.edit()
                                    .putString(KEY_WEBDAV_URL, url)
                                    .putString(KEY_WEBDAV_USER, user)
                                    .putString(KEY_WEBDAV_PASS, pass)
                                    .putString(KEY_WEBDAV_PATH, path)
                                    .apply();

                            // Initialize provider
                            WebDavClient client = new WebDavClient(url, user, pass);
                            WebDavSyncProvider provider = new WebDavSyncProvider("webdav", new WebDavRemoteStoreImpl(client));
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

    private Single<Boolean> testConnection(String url, String user, String pass) {
        return Single.fromCallable(() -> {
            WebDavClient client = new WebDavClient(url, user, pass);
            try (Response response = client.propfind("")) {
                boolean resp = response.isSuccessful() || response.code() == 207;
                if(!resp) Log.w("SyncConfigActivity", "test connection not successfull: "+response.code());
                // Un codice 207 (Multi-Status) o 200 indica solitamente che il server WebDAV ha risposto correttamente
                return resp;
            } catch (Exception e) {
                Log.e("SyncConfigActivity","test connection",e);
                return false;
            }
        });
    }
}
