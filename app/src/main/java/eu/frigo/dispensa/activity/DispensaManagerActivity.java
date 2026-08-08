package eu.frigo.dispensa.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.media3.common.util.Log;
import eu.frigo.dispensa.R;
import eu.frigo.dispensa.adapter.DispensaAdapter;
import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.backup.BackupData;
import eu.frigo.dispensa.data.backup.BackupManager;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import eu.frigo.dispensa.sync.webdav.client.WebDavClientFactory;
import eu.frigo.dispensa.util.WebDavSetupHelper;
import eu.frigo.dispensa.viewmodel.DispensaViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class DispensaManagerActivity extends AppCompatActivity implements DispensaAdapter.OnDispensaClickListener {

    private DispensaViewModel dispensaViewModel;
    private DispensaAdapter adapter;

    private final ActivityResultLauncher<Intent> joinLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    // Se il join ha avuto successo, chiudiamo questa attività per mostrare la nuova dispensa
                    finish();
                }
            }
    );

    private final ActivityResultLauncher<String[]> importFileLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    performImportFromFile(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dispensa_manager);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.manage_dispense_title);
        }

        RecyclerView recyclerView = findViewById(R.id.recyclerViewDispense);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DispensaAdapter(this);
        recyclerView.setAdapter(adapter);

        dispensaViewModel = new ViewModelProvider(this).get(DispensaViewModel.class);
        dispensaViewModel.getAllDispense().observe(this, dispense -> {
            adapter.submitList(dispense);
        });

        dispensaViewModel.getCurrentDispensaId().observe(this, id -> {
            adapter.setCurrentDispensaId(id != null ? id : -1);
        });

        dispensaViewModel.getPantryCreatedEvent().observe(this, created -> {
            if (created != null && created) {
                finish();
            }
        });

        FloatingActionButton fabMain = findViewById(R.id.fabMain);
        fabMain.setOnClickListener(this::showFabMenu);
    }

    private void showFabMenu(View view) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenuInflater().inflate(R.menu.menu_pantry_fab, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_create_pantry) {
                showAddEditDialog(null);
                return true;
            } else if (id == R.id.action_import_pantry) {
                importFileLauncher.launch(new String[]{"*/*"});
                return true;
            } else if (id == R.id.action_join_pantry) {
                Intent intent = new Intent(this, SyncOnboardingActivity.class);
                intent.putExtra(SyncOnboardingActivity.EXTRA_MODE, SyncOnboardingActivity.MODE_JOIN);
                joinLauncher.launch(intent);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void performImportFromFile(android.net.Uri uri) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                BackupManager backupManager = new BackupManager(this);
                // 1. Leggi il file per capire il nome della dispensa
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    BackupData data = backupManager.peekBackupData(is);
                    if (data != null && data.dispensa != null) {
                        String pantryName = data.dispensa.getName();
                        
                        // 2. Crea la nuova dispensa
                        Dispensa newDispensa = new Dispensa(pantryName, false);
                        // Usiamo un metodo sincrono nel repository per avere l'ID
                        long newId = eu.frigo.dispensa.data.Repository.getInstance(getApplication()).insertDispensaSync(newDispensa, true);
                        
                        // 3. Esegui l'import dei dati in questa nuova dispensa
                        try (InputStream is2 = getContentResolver().openInputStream(uri)) {
                            backupManager.importData(is2, (int) newId);
                        }
                        
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Dispensa '" + pantryName + "' importata con successo", Toast.LENGTH_LONG).show();
                            finish();
                        });
                    }
                }
            } catch (Exception e) {
                Log.e("DispensaManager", "Import failed", e);
                runOnUiThread(() -> Toast.makeText(this, "Errore durante l'importazione: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showAddEditDialog(Dispensa dispensa) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(dispensa == null ? R.string.add_dispensa : R.string.edit_dispensa);

        final EditText input = new EditText(this);
        input.setPadding(40, 40, 40, 40);
        if (dispensa != null) {
            input.setText(dispensa.getName());
        }
        builder.setView(input);

        builder.setPositiveButton(R.string.ok, (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!TextUtils.isEmpty(name)) {
                if (dispensa == null) {
                    Dispensa newDispensa = new Dispensa(name, false);
                    dispensaViewModel.insert(newDispensa, true);
                } else {
                    Dispensa updatedDispensa = new Dispensa(dispensa);
                    updatedDispensa.setName(name);
                    dispensaViewModel.update(updatedDispensa);
                }
            } else {
                Toast.makeText(this, R.string.name_required, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    @Override
    public void onDispensaClick(Dispensa dispensa) {
        dispensaViewModel.setCurrentDispensaId(dispensa.id);
        finish();
    }

    @Override
    public void onEditClick(Dispensa dispensa) {
        showAddEditDialog(dispensa);
    }

    @Override
    public void onShareClick(Dispensa dispensa) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        
        // Controlla se è già sincronizzata
        String syncedIdsStr = prefs.getString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, "");
        List<String> syncedIds = new ArrayList<>(Arrays.asList(syncedIdsStr.split(",")));
        if (syncedIds.contains(String.valueOf(dispensa.id))) {
            launchShareOnboarding(dispensa);
            return;
        }

        // Recupera provider configurati
        List<String> availableProviders = getConfiguredProviders(prefs);
        
        if (availableProviders.isEmpty()) {
            Toast.makeText(this, "Configura prima il sync nelle impostazioni", Toast.LENGTH_LONG).show();
            return;
        }

        showProviderSelectionDialog(dispensa, availableProviders, syncedIdsStr);
    }

    private List<String> getConfiguredProviders(SharedPreferences prefs) {
        List<String> providers = new ArrayList<>();
        
        // Verifica WebDAV
        String url = prefs.getString(SyncManager.KEY_WEBDAV_URL, "");
        String user = prefs.getString(SyncManager.KEY_WEBDAV_USER, "");
        String pass = prefs.getString(SyncManager.KEY_WEBDAV_PASS, "");
        boolean isShared = prefs.getBoolean(SyncManager.KEY_WEBDAV_MODE_SHARED, false);
        
        if (!url.isEmpty() && (!user.isEmpty() || isShared) && !pass.isEmpty()) {
            providers.add(getString(R.string.provider_webdav));
        }
        
        // In futuro qui si possono aggiungere altri provider (es. Google Drive, Dropbox)
        
        return providers;
    }

    private void showProviderSelectionDialog(Dispensa dispensa, List<String> providers, String currentSyncedIds) {
        final String[] items = providers.toArray(new String[0]);
        final int[] selectedIndex = {0}; // Pre-seleziona il primo

        new AlertDialog.Builder(this)
                .setTitle(R.string.share_pantry_dialog_title)
                .setMessage(String.format(getString(R.string.share_pantry_dialog_message), dispensa.getName()))
                .setSingleChoiceItems(items, 0, (dialog, which) -> selectedIndex[0] = which)
                .setPositiveButton(R.string.share_button, (dialog, which) -> {
                    String selectedProvider = items[selectedIndex[0]];
                    if (selectedProvider.equals(getString(R.string.provider_webdav))) {
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                        String url = prefs.getString(SyncManager.KEY_WEBDAV_URL, "");
                        String user = prefs.getString(SyncManager.KEY_WEBDAV_USER, "");
                        String pass = prefs.getString(SyncManager.KEY_WEBDAV_PASS, "");
                        prepareAndShare(dispensa, url, user, pass, currentSyncedIds);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @SuppressLint("CheckResult")
    private void prepareAndShare(Dispensa dispensa, String url, String user, String pass, String currentSyncedIds) {
        // Mostra un caricamento
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle("Preparazione server...")
                .setView(new ProgressBar(this))
                .setCancelable(false)
                .show();

        WebDavClient client = WebDavClientFactory.getInstance().getClient(url, user, pass);
        WebDavSetupHelper.preparePantryOnServer(this, client, dispensa.getName())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(success -> {
                    progressDialog.dismiss();
                    if (success) {
                        // Aggiorna preferenze locali
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                        String newSyncedIds = currentSyncedIds.isEmpty() ? String.valueOf(dispensa.id) : currentSyncedIds + "," + dispensa.id;
                        
                        // Imposta l'ID del dispositivo corrente come proprietario locale per coerenza
                        String currentDeviceId = InstallationIdProvider.getOrCreateInstallationId(this);
                        dispensa.deviceOwnerId = currentDeviceId;
                        dispensaViewModel.update(dispensa);

                        prefs.edit()
                                .putString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, newSyncedIds)
                                .putString(SyncManager.SYNC_WEBDAV_PANTRY_NAME + "_" + dispensa.id, dispensa.getName())
                                .apply();

                        // Save secure config for this specific pantry
                        String path = prefs.getString(SyncManager.KEY_WEBDAV_PATH, SyncManager.DEFAULT_PATH);
                        boolean isShared = prefs.getBoolean(SyncManager.KEY_WEBDAV_MODE_SHARED, false);
                        JoinedPantryConfig config = new JoinedPantryConfig(
                                dispensa.id,
                                url,
                                user,
                                pass,
                                path,
                                isShared,
                                prefs.getString(SyncManager.SYNC_WEBDAV_PANTRY_KEY, "")
                        );
                        dispensaViewModel.insertJoinedPantryConfig(config);
                        
                        launchShareOnboarding(dispensa);
                    } else {
                        Toast.makeText(this, "Errore durante la preparazione del server", Toast.LENGTH_LONG).show();
                    }
                }, throwable -> {
                    progressDialog.dismiss();
                    Log.e("DispensaManager", "Setup failed", throwable);
                    Toast.makeText(this, "Errore: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void launchShareOnboarding(Dispensa dispensa) {
        Intent intent = new Intent(this, SyncOnboardingActivity.class);
        intent.putExtra(SyncOnboardingActivity.EXTRA_MODE, SyncOnboardingActivity.MODE_SHARE);
        intent.putExtra(ManageDevicesActivity.PANTRY_ID, dispensa);
        startActivity(intent);
    }

    @Override
    public void onDevicesClick(Dispensa dispensa) {
        Intent intent = new Intent(this, ManageDevicesActivity.class);
        intent.putExtra(ManageDevicesActivity.PANTRY_ID, dispensa);
        startActivity(intent);
    }

    @Override
    public void onDeleteClick(Dispensa dispensa) {
        if (dispensa.isDefault()) {
            Toast.makeText(this, R.string.cannot_delete_default_dispensa, Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_dispensa_title)
                .setMessage(R.string.delete_dispensa_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    dispensaViewModel.delete(dispensa);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onSetDefaultClick(Dispensa dispensa) {
        dispensaViewModel.setAsDefault(dispensa.id);
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
