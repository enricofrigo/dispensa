package eu.frigo.dispensa.sync.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.api.services.drive.DriveScopes;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.engine.SyncCoordinatorImpl;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.gdrive.GDriveSyncProvider;
import eu.frigo.dispensa.sync.gdrive.GDriveSyncProviderLoader;
import eu.frigo.dispensa.sync.gdrive.client.GDriveClient;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class GDriveSyncConfigActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private Button btnConnect;
    private GoogleSignInClient googleSignInClient;

    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    handleSignInResult(result.getData());
                } else {
                    setUILocked(false);
                    Toast.makeText(this, "Sign in failed", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gdrive_sync_config); // I'll create this layout

        progressBar = findViewById(R.id.progress_gdrive_config);
        btnConnect = findViewById(R.id.btn_connect_gdrive);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        btnConnect.setOnClickListener(v -> {
            setUILocked(true);
            signInLauncher.launch(googleSignInClient.getSignInIntent());
        });
    }

    private void setUILocked(boolean locked) {
        btnConnect.setEnabled(!locked);
        progressBar.setVisibility(locked ? View.VISIBLE : View.GONE);
    }

    private void handleSignInResult(Intent data) {
        GoogleSignIn.getSignedInAccountFromIntent(data)
                .addOnSuccessListener(this::setupGDriveSync)
                .addOnFailureListener(e -> {
                    setUILocked(false);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void setupGDriveSync(GoogleSignInAccount account) {
        Single.fromCallable(() -> {
            String accountName = account.getEmail();
            GDriveClient client = new GDriveClient(this, accountName);
            
            // 1. Get or create root folder
            String rootFolderId = client.getOrCreateFolder("DispensaSync", null);
            
            // 2. Get or create pantry folder
            String pantryFolderId = client.getOrCreateFolder(SyncManager.DEFAULT_MAIN_PANTRY, rootFolderId);
            
            // 3. Ensure subfolders
            client.getOrCreateFolder(SyncManager.DEFAULT_EVENTS_FOLDER.replace("/", ""), pantryFolderId);
            client.getOrCreateFolder(SyncManager.DEFAULT_DEVICES_FOLDER.replace("/", ""), pantryFolderId);
            client.getOrCreateFolder(SyncManager.DEFAULT_SNAPSHOTS_FOLDER.replace("/", ""), pantryFolderId);

            return new SetupResult(accountName, pantryFolderId);
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(result -> {
            saveAndFinish(result.accountName, result.pantryFolderId);
        }, throwable -> {
            setUILocked(false);
            Toast.makeText(this, "Setup failed: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void saveAndFinish(String accountName, String pantryFolderId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit()
                .putString(GDriveSyncProviderLoader.KEY_GDRIVE_ACCOUNT, accountName)
                .putString(GDriveSyncProviderLoader.KEY_GDRIVE_PANTRY_FOLDER_ID, pantryFolderId)
                .putBoolean(SyncManager.KEY_SYNC_ENABLED, true)
                .apply();

        String deviceId = InstallationIdProvider.getOrCreateInstallationId(this);
        GDriveClient client = new GDriveClient(this, accountName);
        GDriveSyncProvider provider = new GDriveSyncProvider("gdrive", null, client, deviceId, pantryFolderId);

        SyncManager.getInstance().setProvider(provider);
        SyncCoordinatorImpl.getInstance(this).triggerManualSync();

        Toast.makeText(this, "Google Drive Sync configured!", Toast.LENGTH_SHORT).show();
        finish();
    }

    private static class SetupResult {
        final String accountName;
        final String pantryFolderId;
        SetupResult(String accountName, String pantryFolderId) {
            this.accountName = accountName;
            this.pantryFolderId = pantryFolderId;
        }
    }
}
