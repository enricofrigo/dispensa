package eu.frigo.dispensa.activity;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.model.PantryDevice;
import eu.frigo.dispensa.sync.core.store.SharedFolderStore;
import eu.frigo.dispensa.sync.sharing.PantrySharingService;
import eu.frigo.dispensa.sync.sharing.SharingProvider;
import eu.frigo.dispensa.viewmodel.DispensaViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class ManageDevicesActivity extends AppCompatActivity {

    public static final String PANTRY_ID = "pantryId";
    private RecyclerView rvDevices;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private DeviceAdapter adapter;
    private DispensaViewModel dispensaViewModel;
    private final List<PantryDevice> deviceList = new ArrayList<>();
    private final Gson gson = new Gson();
    private boolean isMaster = false;
    private String devicesPath;
    private String pantryBasePath;
    private SharedFolderStore activeStore;
    private Dispensa currentDispensa;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_devices);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_manage_devices);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvDevices = findViewById(R.id.rv_devices);
        progressBar = findViewById(R.id.progress_devices);
        tvEmpty = findViewById(R.id.tv_empty_devices);

        rvDevices.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeviceAdapter(deviceList, device -> showDeleteConfirmation(device));
        rvDevices.setAdapter(adapter);

        currentDispensa = (Dispensa) getIntent().getSerializableExtra(PANTRY_ID);
        if (currentDispensa != null) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setSubtitle(currentDispensa.getName());
            }
            loadDevices(currentDispensa.id);
        } else {
            // Fallback to current pantry if nothing passed (e.g. opened via settings)
            dispensaViewModel = new ViewModelProvider(this).get(DispensaViewModel.class);
            dispensaViewModel.getCurrentDispensaName().observe(this, name -> {
                if (name != null && getSupportActionBar() != null) {
                    getSupportActionBar().setSubtitle(name);
                }
            });

            dispensaViewModel.getCurrentDispensaId().observe(this, id -> {
                if (id != null) {
                    loadDevices(id);
                }
            });
            // We'll need the full object for ownership check if not passed
            dispensaViewModel.getAllDispense().observe(this, dispense -> {
                if (dispense != null) {
                    Integer currentId = dispensaViewModel.getCurrentDispensaId().getValue();
                    if (currentId != null) {
                        for (Dispensa d : dispense) {
                            if (d.id == currentId) {
                                currentDispensa = d;
                                break;
                            }
                        }
                    }
                }
            });
        }
    }

    @SuppressLint("CheckResult")
    private void loadDevices(int pantryId) {
        progressBar.setVisibility(View.VISIBLE);

        Single.fromCallable(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);
            if (currentDispensa == null) {
                currentDispensa = db.dispensaDao().getDispensaByIdSync(pantryId);
            }
            JoinedPantryConfig config = db.joinedPantryConfigDao().getConfigByDispensaId(pantryId);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String syncedIdsStr = prefs.getString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, "");
            boolean isSynced = config != null;
            if (!isSynced && !syncedIdsStr.isEmpty()) {
                for (String sId : syncedIdsStr.split(",")) {
                    if (sId.equals(String.valueOf(pantryId))) {
                        isSynced = true;
                        break;
                    }
                }
            }

            if (!isSynced) {
                throw new IllegalStateException("NOT_SYNCED");
            }

            String providerId = (config != null && config.providerId != null) ? config.providerId : "webdav";
            SharingProvider provider = PantrySharingService.getInstance().getProvider(providerId);
            if (provider == null || !provider.isConfigured(this)) {
                throw new IllegalStateException("NOT_CONFIGURED");
            }

            String currentId = InstallationIdProvider.getOrCreateInstallationId(this);
            this.isMaster = (currentDispensa == null || currentDispensa.deviceOwnerId == null || currentId.equals(currentDispensa.deviceOwnerId));

            this.activeStore = provider.createStore(this, currentDispensa, config);
            this.pantryBasePath = provider.getPantryPath(this, currentDispensa, config);

            String normalizedBase = (pantryBasePath == null) ? "" : pantryBasePath;
            if (!normalizedBase.isEmpty() && !normalizedBase.endsWith("/")) {
                normalizedBase += "/";
            }
            this.devicesPath = normalizedBase + SyncManager.DEFAULT_DEVICES_FOLDER;

            return activeStore;
        })
        .flatMap(store -> fetchDevices(store, devicesPath))
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(devices -> {
            progressBar.setVisibility(View.GONE);
            deviceList.clear();
            deviceList.addAll(devices);
            adapter.setMaster(isMaster);
            adapter.notifyDataSetChanged();
            tvEmpty.setVisibility(deviceList.isEmpty() ? View.VISIBLE : View.GONE);
        }, throwable -> {
            progressBar.setVisibility(View.GONE);
            if ("NOT_SYNCED".equals(throwable.getMessage())) {
                tvEmpty.setText(R.string.sync_not_active_for_pantry);
                tvEmpty.setVisibility(View.VISIBLE);
                deviceList.clear();
                adapter.notifyDataSetChanged();
            } else if ("NOT_CONFIGURED".equals(throwable.getMessage())) {
                Toast.makeText(this, R.string.sync_not_configured, Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Log.e("ManageDevices", "Error loading devices", throwable);
                Toast.makeText(this, "Error: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDeleteConfirmation(PantryDevice device) {
        boolean isDeletingOwner = currentDispensa != null && device.deviceId.equals(currentDispensa.deviceOwnerId);

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.delete_product_title);

        if (isDeletingOwner) {
            builder.setMessage(R.string.sync_owner_delete_warning);
        } else {
            builder.setMessage(String.format(getString(R.string.sync_device_delete_confirm), device.deviceName));
        }

        builder.setPositiveButton(R.string.delete, (dialog, which) -> deleteDevice(device))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @SuppressLint("CheckResult")
    private void deleteDevice(PantryDevice device) {
        progressBar.setVisibility(View.VISIBLE);
        boolean isDeletingOwner = currentDispensa != null && device.deviceId.equals(currentDispensa.deviceOwnerId);
        String deleteFilePath = devicesPath + device.deviceId + ".json";

        Completable.fromAction(() -> {
            if (activeStore != null) {
                if (isDeletingOwner) {
                    activeStore.deleteFolder(pantryBasePath).blockingAwait();
                } else {
                    activeStore.deleteFile(deleteFilePath).blockingAwait();
                }
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(() -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, isDeletingOwner ? R.string.sync_pantry_deleted_success : R.string.sync_device_delete_success, Toast.LENGTH_SHORT).show();

            String currentDeviceId = InstallationIdProvider.getOrCreateInstallationId(this);
            if (currentDeviceId.equals(device.deviceId) || isDeletingOwner) {
                // We removed ourselves or destroyed the whole share: disable sync locally for this pantry
                PantrySharingService.getInstance().unsharePantryLocally(this, currentDispensa)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::finish, err -> finish());
            } else {
                Integer currentId = currentDispensa != null ? currentDispensa.id : (dispensaViewModel != null ? dispensaViewModel.getCurrentDispensaId().getValue() : null);
                if (currentId != null) {
                    loadDevices(currentId);
                }
            }
        }, throwable -> {
            progressBar.setVisibility(View.GONE);
            Log.e("ManageDevices", "Error deleting device", throwable);
            Toast.makeText(this, String.format(getString(R.string.sync_delete_device_err), throwable.getMessage()), Toast.LENGTH_SHORT).show();
        });
    }

    private Single<List<PantryDevice>> fetchDevices(SharedFolderStore store, String folderPath) {
        return store.listFiles(folderPath)
                .map(fileNames -> {
                    List<PantryDevice> devices = new ArrayList<>();
                    for (String fileName : fileNames) {
                        if (!fileName.endsWith(".json")) continue;
                        String cleanFile = fileName;
                        if (cleanFile.contains("/")) {
                            cleanFile = cleanFile.substring(cleanFile.lastIndexOf("/") + 1);
                        }
                        String fullFilePath = folderPath + cleanFile;
                        try {
                            byte[] data = store.read(fullFilePath).blockingGet();
                            if (data != null && data.length > 0) {
                                PantryDevice device = gson.fromJson(new String(data, java.nio.charset.StandardCharsets.UTF_8), PantryDevice.class);
                                if (device != null) {
                                    devices.add(device);
                                }
                            }
                        } catch (Exception e) {
                            Log.w("ManageDevices", "Failed reading device file: " + fullFilePath, e);
                        }
                    }
                    return devices;
                });
    }

    private static class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {
        private final List<PantryDevice> devices;
        private boolean isMaster = false;
        private final OnDeviceDeleteListener deleteListener;

        interface OnDeviceDeleteListener {
            void onDelete(PantryDevice device);
        }

        DeviceAdapter(List<PantryDevice> devices, OnDeviceDeleteListener deleteListener) {
            this.devices = devices;
            this.deleteListener = deleteListener;
        }

        void setMaster(boolean master) {
            this.isMaster = master;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_device, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PantryDevice device = devices.get(position);
            holder.tvName.setText(device.deviceName != null ? device.deviceName : "Unknown Device");
            holder.tvId.setText("ID: " + device.deviceId);

            String currentDeviceId = InstallationIdProvider.getOrCreateInstallationId(holder.itemView.getContext());
            boolean isSelf = currentDeviceId.equals(device.deviceId);

            if (isMaster || isSelf) {
                holder.btnDelete.setVisibility(View.VISIBLE);
                holder.btnDelete.setOnClickListener(v -> deleteListener.onDelete(device));
            } else {
                holder.btnDelete.setVisibility(View.GONE);
            }
            
            if (isSelf) {
                holder.tvName.append(" (Questo dispositivo)");
            }
        }

        @Override
        public int getItemCount() {
            return devices.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvId;
            View btnDelete;
            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_device_name);
                tvId = itemView.findViewById(R.id.tv_device_id);
                btnDelete = itemView.findViewById(R.id.btn_delete_device);
            }
        }
    }
}
