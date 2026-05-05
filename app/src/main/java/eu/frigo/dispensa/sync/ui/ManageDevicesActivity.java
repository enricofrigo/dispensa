package eu.frigo.dispensa.sync.ui;

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
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import eu.frigo.dispensa.sync.webdav.client.WebDavClientFactory;
import eu.frigo.dispensa.sync.webdav.model.WebDavDevice;
import eu.frigo.dispensa.sync.webdav.model.WebDavManifest;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.Response;

public class ManageDevicesActivity extends AppCompatActivity {

    private RecyclerView rvDevices;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private DeviceAdapter adapter;
    private final List<WebDavDevice> deviceList = new ArrayList<>();
    private final Gson gson = new Gson();
    private String ownerDeviceId;
    private String currentDeviceId;
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
        currentDeviceId = eu.frigo.dispensa.sync.core.engine.InstallationIdProvider.getOrCreateInstallationId(this);
        adapter = new DeviceAdapter(deviceList, currentDeviceId, this::deleteDevice);
        rvDevices.setAdapter(adapter);

        loadDevices();
    }

    private void loadDevices() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String url = prefs.getString(SyncManager.KEY_WEBDAV_URL, "");
        String user = prefs.getString(SyncManager.KEY_WEBDAV_USER, "");
        String pass = prefs.getString(SyncManager.KEY_WEBDAV_PASS, "");
        String path = prefs.getString(SyncManager.KEY_WEBDAV_PATH, SyncManager.DEFAULT_PATH);
        String pantryKey = prefs.getString(SyncManager.SYNC_WEBDAV_PANTRY_KEY, "");
        boolean isShared = prefs.getBoolean(SyncManager.KEY_WEBDAV_MODE_SHARED, false);

        if (url.isEmpty() || (user.isEmpty() && !isShared) || pass.isEmpty() || pantryKey.isEmpty()) {
            Toast.makeText(this, "Sync not configured", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String normalizedBase = path.endsWith("/") ? path : path + "/";
        if (normalizedBase.startsWith("/")) normalizedBase = normalizedBase.substring(1);
        String pantryBasePath = normalizedBase + SyncManager.DEFAULT_PANTRY_PATH + pantryKey + "/";
        devicesPath = pantryBasePath + SyncManager.DEFAULT_DEVICES_FOLDER;
        String manifestPath = pantryBasePath + SyncManager.MANIFEST_JSON;

        progressBar.setVisibility(View.VISIBLE);
        
        fetchData(manifestPath, devicesPath)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(data -> {
                    progressBar.setVisibility(View.GONE);
                    ownerDeviceId = data.ownerId;
                    deviceList.clear();
                    deviceList.addAll(data.devices);
                    adapter.setOwnerId(ownerDeviceId);
                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(deviceList.isEmpty() ? View.VISIBLE : View.GONE);
                }, throwable -> {
                    progressBar.setVisibility(View.GONE);
                    Log.e("ManageDevices", "Error loading devices", throwable);
                    Toast.makeText(this, "Error: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private static class DeviceData {
        String ownerId;
        List<WebDavDevice> devices;
        DeviceData(String ownerId, List<WebDavDevice> devices) {
            this.ownerId = ownerId;
            this.devices = devices;
        }
    }

    private Single<DeviceData> fetchData(String manifestPath, String devicesPath) {
        return Single.fromCallable(() -> {
            WebDavClient client = WebDavClientFactory.getInstance().getClient(this);
            String ownerId = null;
            
            try (Response resp = client.get(manifestPath)) {
                if (resp.isSuccessful() && resp.body() != null) {
                    WebDavManifest manifest = gson.fromJson(resp.body().string(), WebDavManifest.class);
                    if (manifest != null) {
                        ownerId = manifest.createdByDevice;
                    }
                }
            }

            List<WebDavDevice> devices = new ArrayList<>();
            try (Response response = client.propfind(devicesPath)) {
                if (response.isSuccessful() && response.body() != null) {
                    String xml = response.body().string();
                    List<String> jsonFiles = extractJsonFiles(xml);
                    
                    for (String fileName : jsonFiles) {
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
            return new DeviceData(ownerId, devices);
        });
    }

    private void deleteDevice(WebDavDevice device) {
        if (device.deviceId.equals(currentDeviceId)) {
            Toast.makeText(this, "Cannot delete yourself from here", Toast.LENGTH_SHORT).show();
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Remove Device")
                .setMessage("Are you sure you want to remove " + device.deviceName + "?")
                .setPositiveButton(android.R.string.yes, (dialog, which) -> performDelete(device))
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    private void performDelete(WebDavDevice device) {
        progressBar.setVisibility(View.VISIBLE);
        Single.fromCallable(() -> {
            WebDavClient client = WebDavClientFactory.getInstance().getClient(this);
            try (Response response = client.delete(devicesPath + device.deviceId + ".json")) {
                return response.isSuccessful();
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(success -> {
            progressBar.setVisibility(View.GONE);
            if (success) {
                loadDevices();
            } else {
                Toast.makeText(this, "Failed to delete device", Toast.LENGTH_SHORT).show();
            }
        }, throwable -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Error: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
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
        private final String currentDeviceId;
        private final OnDeleteClickListener deleteClickListener;
        private String ownerId;

        interface OnDeleteClickListener {
            void onDelete(WebDavDevice device);
        }

        DeviceAdapter(List<WebDavDevice> devices, String currentDeviceId, OnDeleteClickListener deleteClickListener) {
            this.devices = devices;
            this.currentDeviceId = currentDeviceId;
            this.deleteClickListener = deleteClickListener;
        }

        void setOwnerId(String ownerId) {
            this.ownerId = ownerId;
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
            boolean isOwner = device.deviceId.equals(ownerId);
            String name = device.deviceName != null ? device.deviceName : "Unknown Device";
            if (isOwner) {
                name += " (owner)";
            }
            holder.tvName.setText(name);
            holder.tvId.setText("ID: " + device.deviceId);

            boolean iAmOwner = currentDeviceId != null && currentDeviceId.equals(ownerId);
            if (iAmOwner && !device.deviceId.equals(currentDeviceId)) {
                holder.btnDelete.setVisibility(View.VISIBLE);
                holder.btnDelete.setOnClickListener(v -> deleteClickListener.onDelete(device));
            } else {
                holder.btnDelete.setVisibility(View.GONE);
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
