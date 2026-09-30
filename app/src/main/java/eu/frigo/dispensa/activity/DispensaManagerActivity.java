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
import java.util.UUID;

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
import eu.frigo.dispensa.sync.sharing.PantrySharingService;
import eu.frigo.dispensa.sync.sharing.SharingProvider;
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

        dispensaViewModel.getAllJoinedPantryConfigs().observe(this, configs -> {
            adapter.setJoinedConfigs(configs);
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
        Log.d("DispensaManager", "onShareClick called for dispensa: " + dispensa);

        List<JoinedPantryConfig> configs = dispensaViewModel.getAllJoinedPantryConfigs().getValue();
        JoinedPantryConfig existingConfig = null;
        if (configs != null) {
            for (JoinedPantryConfig c : configs) {
                if (c.dispensaId == dispensa.id) {
                    existingConfig = c;
                    break;
                }
            }
        }

        if (existingConfig != null) {
            showAlreadySharedOptionsDialog(dispensa, existingConfig);
        } else {
            showProviderSelectionDialog(dispensa);
        }
    }

    private void showAlreadySharedOptionsDialog(Dispensa dispensa, JoinedPantryConfig config) {
        SharingProvider provider = PantrySharingService.getInstance().getProvider(config.providerId);
        String providerName = provider != null ? provider.getDisplayName(this) : (config.providerId != null ? config.providerId : "Cloud");

        String[] options = new String[]{
                getString(R.string.show_pairing_qr),
                getString(R.string.action_change_provider),
                getString(R.string.action_manage_devices)
        };

        new AlertDialog.Builder(this)
                .setTitle(dispensa.getName() + " (" + providerName + ")")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        launchShareOnboarding(dispensa);
                    } else if (which == 1) {
                        showProviderSelectionDialog(dispensa);
                    } else if (which == 2) {
                        onDevicesClick(dispensa);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showProviderSelectionDialog(Dispensa dispensa) {
        List<SharingProvider> providers = PantrySharingService.getInstance().getAllProviders();
        if (providers.isEmpty()) {
            Toast.makeText(this, R.string.no_devices_found, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] items = new String[providers.size()];
        for (int i = 0; i < providers.size(); i++) {
            SharingProvider p = providers.get(i);
            String status = p.isConfigured(this) ? "" : getString(R.string.not_configured_badge);
            items[i] = p.getDisplayName(this) + status;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.share_pantry_dialog_title)
                .setItems(items, (dialog, which) -> {
                    SharingProvider selectedProvider = providers.get(which);
                    if (selectedProvider.isConfigured(this)) {
                        executeSharePantry(dispensa, selectedProvider.getProviderId());
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle(selectedProvider.getDisplayName(this))
                                .setMessage(getString(R.string.provider_not_configured_prompt, selectedProvider.getDisplayName(this)))
                                .setPositiveButton(R.string.action_configure_provider, (d2, w2) -> {
                                    Intent intent = selectedProvider.getConfigIntent(this);
                                    if (intent != null) {
                                        startActivity(intent);
                                    }
                                })
                                .setNegativeButton(R.string.cancel, null)
                                .show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @SuppressLint("CheckResult")
    private void executeSharePantry(Dispensa dispensa, String providerId) {
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.preparing_sync_server)
                .setView(new ProgressBar(this))
                .setCancelable(false)
                .show();

        PantrySharingService.getInstance().sharePantry(this, dispensa, providerId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    progressDialog.dismiss();
                    launchShareOnboarding(dispensa);
                }, throwable -> {
                    progressDialog.dismiss();
                    Log.e("DispensaManager", "Sharing setup failed", throwable);
                    Toast.makeText(this, getString(R.string.sync_setup_error, throwable.getMessage()), Toast.LENGTH_LONG).show();
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

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String deviceId = InstallationIdProvider.getOrCreateInstallationId(this);
        boolean isOwner = deviceId.equals(dispensa.deviceOwnerId);

        String syncedIdsStr = prefs.getString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, "");
        List<String> syncedIds = new ArrayList<>(Arrays.asList(syncedIdsStr.split(",")));
        boolean isSynced = syncedIds.contains(String.valueOf(dispensa.id));

        if (isOwner && isSynced) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.delete_dispensa_title)
                    .setMessage(R.string.sync_owner_delete_warning)
                    .setPositiveButton(R.string.delete, (dialog, which) -> deletePantryRemoteAndLocal(dispensa))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.delete_dispensa_title)
                    .setMessage(R.string.delete_dispensa_message)
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        if (isSynced) {
                            removePantryFromSync(dispensa.id, prefs, syncedIds);
                        }
                        dispensaViewModel.delete(dispensa);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }

    @SuppressLint("CheckResult")
    private void deletePantryRemoteAndLocal(Dispensa dispensa) {
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.delete)
                .setView(new ProgressBar(this))
                .setCancelable(false)
                .show();

        PantrySharingService.getInstance().deleteRemotePantry(this, dispensa)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    progressDialog.dismiss();
                    dispensaViewModel.delete(dispensa);
                    Toast.makeText(this, R.string.sync_pantry_deleted_success, Toast.LENGTH_SHORT).show();
                }, throwable -> {
                    progressDialog.dismiss();
                    Log.e("DispensaManager", "Remote delete failed", throwable);
                    Toast.makeText(this, getString(R.string.sync_remote_delete_error, throwable.getMessage()), Toast.LENGTH_LONG).show();
                    dispensaViewModel.delete(dispensa);
                });
    }

    private void removePantryFromSync(int pantryId, SharedPreferences prefs, List<String> syncedIds) {
        syncedIds.remove(String.valueOf(pantryId));
        prefs.edit()
                .putString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, TextUtils.join(",", syncedIds))
                .apply();
    }

    @Override
    public void onSetDefaultClick(Dispensa dispensa) {
        dispensaViewModel.setAsDefault(dispensa.id);
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_dispensa_manager, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_shared_dispense) {
            startActivity(new Intent(this, SharedDispenseActivity.class));
            return true;
        } else if (id == R.id.action_configure_providers) {
            showProviderConfigurationChooser();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showProviderConfigurationChooser() {
        List<SharingProvider> providers = PantrySharingService.getInstance().getAllProviders();
        if (providers.isEmpty()) {
            Toast.makeText(this, R.string.no_devices_found, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] items = new String[providers.size()];
        for (int i = 0; i < providers.size(); i++) {
            SharingProvider p = providers.get(i);
            String status = p.isConfigured(this) ? "" : " " + getString(R.string.not_configured_badge);
            items[i] = p.getDisplayName(this) + status;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.pref_providers_title)
                .setItems(items, (dialog, which) -> {
                    SharingProvider selected = providers.get(which);
                    Intent intent = selected.getConfigIntent(this);
                    if (intent != null) {
                        startActivity(intent);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
