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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import eu.frigo.dispensa.sync.webdav.model.WebDavDevice;
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
        adapter = new DeviceAdapter(deviceList);
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
        String devicesPath = normalizedBase + SyncManager.DEFAULT_PANTRY_PATH + pantryKey + "/" + SyncManager.DEFAULT_DEVICES_FOLDER;

        progressBar.setVisibility(View.VISIBLE);
        
        fetchDevices(url, user, pass, devicesPath)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(devices -> {
                    progressBar.setVisibility(View.GONE);
                    deviceList.clear();
                    deviceList.addAll(devices);
                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(deviceList.isEmpty() ? View.VISIBLE : View.GONE);
                }, throwable -> {
                    progressBar.setVisibility(View.GONE);
                    Log.e("ManageDevices", "Error loading devices", throwable);
                    Toast.makeText(this, "Error: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private Single<List<WebDavDevice>> fetchDevices(String url, String user, String pass, String devicesPath) {
        return Single.fromCallable(() -> {
            WebDavClient client = new WebDavClient(url, user, pass);
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

        DeviceAdapter(List<WebDavDevice> devices) {
            this.devices = devices;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            WebDavDevice device = devices.get(position);
            holder.text1.setText(device.deviceName != null ? device.deviceName : "Unknown Device");
            holder.text2.setText("ID: " + device.deviceId);
        }

        @Override
        public int getItemCount() {
            return devices.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            ViewHolder(View itemView) {
                super(itemView);
                text1 = itemView.findViewById(android.R.id.text1);
                text2 = itemView.findViewById(android.R.id.text2);
            }
        }
    }
}
