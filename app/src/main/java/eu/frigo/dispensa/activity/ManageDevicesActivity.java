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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import eu.frigo.dispensa.sync.webdav.client.WebDavClientFactory;
import eu.frigo.dispensa.sync.webdav.model.WebDavDevice;
import eu.frigo.dispensa.sync.webdav.model.WebDavManifest;
import eu.frigo.dispensa.viewmodel.DispensaViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.Response;

public class ManageDevicesActivity extends AppCompatActivity {

    public static final String PANTRY_ID = "pantryId";
    private RecyclerView rvDevices;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private DeviceAdapter adapter;
    private DispensaViewModel dispensaViewModel;
    private final List<WebDavDevice> deviceList = new ArrayList<>();
    private final Gson gson = new Gson();
    private boolean isMaster = false;
    private String devicesPath;

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

        Dispensa d = (Dispensa) getIntent().getSerializableExtra(PANTRY_ID);
        if (d != null) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setSubtitle(d.getName());
            }
            loadDevices(d.id);
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
        }
    }

    @SuppressLint("CheckResult")
    private void loadDevices(int pantryId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String url = prefs.getString(SyncManager.KEY_WEBDAV_URL, "");
        String user = prefs.getString(SyncManager.KEY_WEBDAV_USER, "");
        String pass = prefs.getString(SyncManager.KEY_WEBDAV_PASS, "");
        String path = prefs.getString(SyncManager.KEY_WEBDAV_PATH, SyncManager.DEFAULT_PATH);
        
        String syncedIdsStr = prefs.getString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, "");
        boolean isSynced = false;
        if (!syncedIdsStr.isEmpty()) {
            for (String sId : syncedIdsStr.split(",")) {
                if (sId.equals(String.valueOf(pantryId))) {
                    isSynced = true;
                    break;
                }
            }
        }

        if (!isSynced) {
            tvEmpty.setText(R.string.sync_not_active_for_pantry);
            tvEmpty.setVisibility(View.VISIBLE);
            deviceList.clear();
            adapter.notifyDataSetChanged();
            return;
        }

        String pantryName = prefs.getString(SyncManager.SYNC_WEBDAV_PANTRY_NAME + "_" + pantryId, "Dispensa");
        boolean isShared = prefs.getBoolean(SyncManager.KEY_WEBDAV_MODE_SHARED, false);

        if (url.isEmpty() || (user.isEmpty() && !isShared) || pass.isEmpty()) {
            Toast.makeText(this, R.string.sync_not_configured, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String normalizedBase = path.endsWith("/") ? path : path + "/";
        if (normalizedBase.startsWith("/")) normalizedBase = normalizedBase.substring(1);
        String pantryBasePath = normalizedBase + SyncManager.getSyncPath(pantryName);
        devicesPath = pantryBasePath + SyncManager.DEFAULT_DEVICES_FOLDER;

        progressBar.setVisibility(View.VISIBLE);
        
        checkMasterStatus(pantryBasePath + SyncManager.MANIFEST_JSON)
                .flatMap(master -> {
                    this.isMaster = master;
                    return fetchDevices(devicesPath);
                })
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
                    Log.e("ManageDevices", "Error loading devices", throwable);
                    Toast.makeText(this, "Error: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private Single<Boolean> checkMasterStatus(String manifestPath) {
        return Single.fromCallable(() -> {
            WebDavClient client = WebDavClientFactory.getInstance().getClient(this);
            try (Response response = client.get(manifestPath)) {
                if (response.isSuccessful() && response.body() != null) {
                    WebDavManifest manifest = gson.fromJson(response.body().string(), WebDavManifest.class);
                    if (manifest != null) {
                        String currentId = InstallationIdProvider.getOrCreateInstallationId(this);
                        return currentId.equals(manifest.createdByDevice);
                    }
                }
            } catch (Exception e) {
                Log.e("ManageDevices", "Error checking manifest", e);
            }
            return false;
        });
    }

    private void showDeleteConfirmation(WebDavDevice device) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.delete_product_title)
                .setMessage(String.format(getString(R.string.sync_device_delete_confirm),device.deviceName))
                .setPositiveButton(R.string.delete, (dialog, which) -> deleteDevice(device))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @SuppressLint("CheckResult")
    private void deleteDevice(WebDavDevice device) {
        progressBar.setVisibility(View.VISIBLE);
        String deviceFilePath = devicesPath + device.deviceId + ".json";

        Completable.fromAction(() -> {
             WebDavClient client = WebDavClientFactory.getInstance().getClient(this);
            try (Response response = client.delete(deviceFilePath)) {
                if (!response.isSuccessful()) {
                    throw new IOException("Failed to delete device: " + response.code());
                }
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(() -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, R.string.sync_device_delete_success, Toast.LENGTH_SHORT).show();

            String currentDeviceId = InstallationIdProvider.getOrCreateInstallationId(this);
            if (currentDeviceId.equals(device.deviceId)) {
                // We removed ourselves: disable sync and close management
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                        .remove(SyncManager.SYNC_WEBDAV_PANTRY_KEY)
                        .putBoolean(SyncManager.KEY_SYNC_ENABLED, false)
                        .apply();
                finish();
            } else {
                Integer currentId = dispensaViewModel.getCurrentDispensaId().getValue();
                if (currentId != null) {
                    loadDevices(currentId);
                }
            }
        }, throwable -> {
            progressBar.setVisibility(View.GONE);
            Log.e("ManageDevices", "Error deleting device", throwable);
            Toast.makeText(this, String.format(getString(R.string.sync_delete_device_err),throwable.getMessage()), Toast.LENGTH_SHORT).show();
        });
    }

    private Single<List<WebDavDevice>> fetchDevices(String devicesPath) {
        return Single.fromCallable(() -> {
            WebDavClient client = WebDavClientFactory.getInstance().getClient(this);
            List<WebDavDevice> devices = new ArrayList<>();
            
            try (Response response = client.propfind(devicesPath)) {
                if (response.isSuccessful() && response.body() != null) {
                    String xml = response.body().string();
                    List<String> jsonFiles = extractJsonFiles(xml);
                    
                    for (String fileName : jsonFiles) {
                        // Extract just the filename if it's a full path
                        String shortName = fileName;
                        if (fileName.contains("/")) {
                            shortName = fileName.substring(fileName.lastIndexOf("/") + 1);
                        }
                        
                        try (Response devResp = client.get(devicesPath + shortName)) {
                            if (devResp.isSuccessful() && devResp.body() != null) {
                                WebDavDevice device = gson.fromJson(devResp.body().string(), WebDavDevice.class);
                                if (device != null) {
                                    devices.add(device);
                                }
                            }
                        }
                    }
                }
            }
            return devices;
        });
    }

    private List<String> extractJsonFiles(String xml) {
        List<String> files = new ArrayList<>();
        Pattern pattern = Pattern.compile("<[Dd]:href>([^<]+\\.json)</[Dd]:href>");
        Matcher matcher = pattern.matcher(xml);
        while (matcher.find()) {
            files.add(matcher.group(1));
        }
        if (files.isEmpty()) {
            // Try without D: prefix
            pattern = Pattern.compile("<href>([^<]+\\.json)</href>");
            matcher = pattern.matcher(xml);
            while (matcher.find()) {
                files.add(matcher.group(1));
            }
        }
        return files;
    }

    private static class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {
        private final List<WebDavDevice> devices;
        private boolean isMaster = false;
        private final OnDeviceDeleteListener deleteListener;

        interface OnDeviceDeleteListener {
            void onDelete(WebDavDevice device);
        }

        DeviceAdapter(List<WebDavDevice> devices, OnDeviceDeleteListener deleteListener) {
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
            WebDavDevice device = devices.get(position);
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
