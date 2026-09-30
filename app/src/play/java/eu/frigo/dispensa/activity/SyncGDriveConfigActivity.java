package eu.frigo.dispensa.activity;

import android.content.Intent;
import android.content.SharedPreferences;
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
import androidx.preference.PreferenceManager;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.sync.core.engine.SyncCoordinatorImpl;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.gdrive.auth.GDriveAuthManager;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class SyncGDriveConfigActivity extends AppCompatActivity {
    private static final String TAG = "SyncGDriveConfig";

    private TextView statusTextView;
    private Button signInButton;
    private Button signOutButton;
    private Button saveButton;
    private ProgressBar progressBar;

    private GDriveAuthManager authManager;

    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null) {
                            authManager.saveAccountEmail(account.getEmail());
                            updateUiState();
                            Toast.makeText(this, getString(R.string.notify_saved), Toast.LENGTH_SHORT).show();
                        }
                    } catch (ApiException e) {
                        Log.e(TAG, "Google sign in failed", e);
                        Toast.makeText(this, "Errore Google Sign-In: " + e.getStatusCode(), Toast.LENGTH_LONG).show();
                    }
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_gdrive_config);

        authManager = new GDriveAuthManager(this);

        statusTextView = findViewById(R.id.tv_gdrive_status);
        signInButton = findViewById(R.id.btn_gdrive_sign_in);
        signOutButton = findViewById(R.id.btn_gdrive_sign_out);
        saveButton = findViewById(R.id.btn_save_gdrive_config);
        progressBar = findViewById(R.id.progress_gdrive_sync);

        signInButton.setOnClickListener(v -> {
            Intent signInIntent = GDriveAuthManager.getGoogleSignInClient(this).getSignInIntent();
            signInLauncher.launch(signInIntent);
        });

        signOutButton.setOnClickListener(v -> {
            authManager.signOut()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> {
                        updateUiState();
                        Toast.makeText(this, "Disconnesso da Google Drive", Toast.LENGTH_SHORT).show();
                    });
        });

        saveButton.setOnClickListener(v -> saveConfiguration());

        updateUiState();
    }

    private void updateUiState() {
        boolean signedIn = authManager.isSignedIn();
        if (signedIn) {
            String email = authManager.getAccountEmail();
            statusTextView.setText(getString(R.string.sync_gdrive_connected_as, email != null ? email : ""));
            signInButton.setVisibility(View.GONE);
            signOutButton.setVisibility(View.VISIBLE);
            saveButton.setEnabled(true);
        } else {
            statusTextView.setText(R.string.sync_gdrive_not_connected);
            signInButton.setVisibility(View.VISIBLE);
            signOutButton.setVisibility(View.GONE);
            saveButton.setEnabled(false);
        }
    }

    private void saveConfiguration() {
        if (!authManager.isSignedIn()) {
            Toast.makeText(this, "Effettua prima l'accesso con Google", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean(SyncManager.KEY_SYNC_ENABLED, true).apply();

        SyncCoordinatorImpl.getInstance(this).triggerManualSync();
        Toast.makeText(this, R.string.notify_saved, Toast.LENGTH_SHORT).show();
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
