package eu.frigo.dispensa.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.adapter.SharedDispensaAdapter;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.sharing.PantrySharingService;
import eu.frigo.dispensa.sync.sharing.SharingProvider;
import eu.frigo.dispensa.viewmodel.DispensaViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class SharedDispenseActivity extends AppCompatActivity implements SharedDispensaAdapter.OnSharedDispensaActionListener {

    private DispensaViewModel dispensaViewModel;
    private SharedDispensaAdapter adapter;
    private View layoutEmpty;
    private RecyclerView recyclerView;

    private List<Dispensa> allDispense = new ArrayList<>();
    private List<JoinedPantryConfig> joinedConfigs = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shared_dispense);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_shared_dispense);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_shared_dispense);
        }

        recyclerView = findViewById(R.id.recyclerViewSharedDispense);
        layoutEmpty = findViewById(R.id.layout_empty_shared);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SharedDispensaAdapter(this);
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btn_empty_manage_pantries).setOnClickListener(v -> finish());

        ExtendedFloatingActionButton fabConfigure = findViewById(R.id.fab_configure_providers);
        fabConfigure.setOnClickListener(v -> showProviderConfigurationChooser());

        dispensaViewModel = new ViewModelProvider(this).get(DispensaViewModel.class);

        dispensaViewModel.getAllDispense().observe(this, dispense -> {
            if (dispense != null) {
                allDispense = dispense;
                rebuildList();
            }
        });

        dispensaViewModel.getAllJoinedPantryConfigs().observe(this, configs -> {
            if (configs != null) {
                joinedConfigs = configs;
                rebuildList();
            }
        });
    }

    private void rebuildList() {
        Map<Integer, JoinedPantryConfig> configMap = new HashMap<>();
        for (JoinedPantryConfig config : joinedConfigs) {
            configMap.put(config.dispensaId, config);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String syncedIdsStr = prefs.getString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, "");
        List<String> syncedIds = new ArrayList<>(Arrays.asList(syncedIdsStr.split(",")));

        List<SharedDispensaAdapter.SharedPantryItem> items = new ArrayList<>();
        PantrySharingService sharingService = PantrySharingService.getInstance();

        for (Dispensa d : allDispense) {
            JoinedPantryConfig config = configMap.get(d.id);
            boolean isSynced = config != null || syncedIds.contains(String.valueOf(d.id));

            if (isSynced) {
                String providerId = config != null && config.providerId != null ? config.providerId : "webdav";
                SharingProvider provider = sharingService.getProvider(providerId);
                items.add(new SharedDispensaAdapter.SharedPantryItem(d, config, provider));
            }
        }

        adapter.setItems(items);

        if (items.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
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

    @Override
    public void onDevicesClick(Dispensa dispensa) {
        Intent intent = new Intent(this, ManageDevicesActivity.class);
        intent.putExtra(ManageDevicesActivity.PANTRY_ID, dispensa);
        startActivity(intent);
    }

    @Override
    public void onShareQrClick(Dispensa dispensa) {
        Intent intent = new Intent(this, SyncOnboardingActivity.class);
        intent.putExtra(SyncOnboardingActivity.EXTRA_MODE, SyncOnboardingActivity.MODE_SHARE);
        intent.putExtra(ManageDevicesActivity.PANTRY_ID, dispensa);
        startActivity(intent);
    }

    @Override
    public void onProviderClick(SharingProvider provider, JoinedPantryConfig config) {
        if (provider != null) {
            Intent intent = provider.getConfigIntent(this);
            if (intent != null) {
                startActivity(intent);
            }
        } else {
            showProviderConfigurationChooser();
        }
    }

    @SuppressLint("CheckResult")
    @Override
    public void onUnshareClick(Dispensa dispensa, JoinedPantryConfig config) {
        String providerName = (config != null && config.providerId != null) ? config.providerId : "Cloud";
        SharingProvider provider = config != null ? PantrySharingService.getInstance().getProvider(config.providerId) : null;
        if (provider != null) {
            providerName = provider.getDisplayName(this);
        }

        String currentDeviceId = InstallationIdProvider.getOrCreateInstallationId(this);
        boolean isOwner = dispensa.deviceOwnerId == null || dispensa.deviceOwnerId.equals(currentDeviceId);

        new AlertDialog.Builder(this)
                .setTitle(R.string.stop_sharing_confirm_title)
                .setMessage(getString(R.string.stop_sharing_confirm_message, dispensa.getName(), providerName))
                .setPositiveButton(R.string.action_stop_sharing, (dialog, which) -> {
                    if (isOwner) {
                        // Ask if owner wants to delete remote folder or just unlink locally
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.delete_dispensa_title)
                                .setMessage(R.string.sync_owner_delete_warning)
                                .setPositiveButton(R.string.delete, (d2, w2) -> executeDeleteRemoteSharing(dispensa))
                                .setNeutralButton(R.string.cancel, null)
                                .setNegativeButton("Solo locale", (d2, w2) -> executeUnshareLocal(dispensa))
                                .show();
                    } else {
                        executeUnshareLocal(dispensa);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @SuppressLint("CheckResult")
    private void executeUnshareLocal(Dispensa dispensa) {
        PantrySharingService.getInstance().unsharePantryLocally(this, dispensa)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    Toast.makeText(this, "Condivisione interrotta per " + dispensa.getName(), Toast.LENGTH_SHORT).show();
                    rebuildList();
                }, throwable -> {
                    Toast.makeText(this, "Errore: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @SuppressLint("CheckResult")
    private void executeDeleteRemoteSharing(Dispensa dispensa) {
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.preparing_sync_server)
                .setView(new ProgressBar(this))
                .setCancelable(false)
                .show();

        PantrySharingService.getInstance().deleteRemotePantry(this, dispensa)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, R.string.sync_pantry_deleted_success, Toast.LENGTH_SHORT).show();
                    rebuildList();
                }, throwable -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, getString(R.string.sync_remote_delete_error, throwable.getMessage()), Toast.LENGTH_LONG).show();
                    rebuildList();
                });
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
