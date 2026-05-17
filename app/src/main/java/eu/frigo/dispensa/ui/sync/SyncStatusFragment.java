package eu.frigo.dispensa.ui.sync;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.sync.core.engine.CrDtSyncManager;
import eu.frigo.dispensa.sync.core.engine.SyncCoordinatorImpl;
import eu.frigo.dispensa.data.AppDatabase;

/**
 * Fragment displaying sync status and statistics.
 */
public class SyncStatusFragment extends Fragment {
    
    private static final String TAG = "SyncStatusFragment";
    
    private TextView textLastSyncTime;
    private TextView textSyncStatus;
    private TextView textDeviceId;
    private TextView textChangeCount;
    private Button btnSyncNow;
    private Button btnViewLog;
    private ProgressBar progressSync;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sync_status, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        textLastSyncTime = view.findViewById(R.id.text_last_sync_time);
        textSyncStatus = view.findViewById(R.id.text_sync_status);
        textDeviceId = view.findViewById(R.id.text_device_id);
        textChangeCount = view.findViewById(R.id.text_change_count);
        btnSyncNow = view.findViewById(R.id.btn_sync_now);
        btnViewLog = view.findViewById(R.id.btn_view_log);
        progressSync = view.findViewById(R.id.progress_sync);
        
        setupListeners();
        updateSyncStatus();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        updateSyncStatus();
    }
    
    private void setupListeners() {
        btnSyncNow.setOnClickListener(v -> triggerSync());
        btnViewLog.setOnClickListener(v -> viewDebugLog());
    }
    
    private void updateSyncStatus() {
        if (!isAdded()) return;
        
        AppDatabase db = AppDatabase.getDatabase(requireContext());
        CrDtSyncManager syncManager = new CrDtSyncManager(db, requireContext());
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        
        // Last sync time - using the key defined in CrDtSyncManager
        long lastSyncEpoch = prefs.getLong(CrDtSyncManager.PREFS_KEY_LAST_SYNC_VERSION, 0);
        String lastSyncText = getLastSyncText(lastSyncEpoch);
        textLastSyncTime.setText(lastSyncText);
        
        // Device ID
        String deviceId = syncManager.getLocalDeviceId();
        if (deviceId != null) {
            textDeviceId.setText("Device: " + deviceId.substring(0, Math.min(8, deviceId.length())) + "...");
        } else {
            textDeviceId.setText("Device: Not set");
        }
        
        // Change count
        long maxClock = syncManager.getMaxSyncClock();
        textChangeCount.setText("Local changes: " + maxClock);
        
        // Sync status (which transports are enabled)
        String statusText = getSyncStatusText(prefs);
        textSyncStatus.setText(statusText);
    }
    
    private String getLastSyncText(long epochMs) {
        if (epochMs == 0) {
            return "Never synced";
        }
        
        long now = System.currentTimeMillis();
        long diffMs = now - epochMs;
        long diffSeconds = diffMs / 1000;
        long diffMinutes = diffSeconds / 60;
        long diffHours = diffMinutes / 60;
        
        if (diffSeconds < 60) {
            return "Just now";
        } else if (diffMinutes < 60) {
            return diffMinutes + " minutes ago";
        } else if (diffHours < 24) {
            return diffHours + " hours ago";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            return "Last: " + sdf.format(new Date(epochMs));
        }
    }
    
    private String getSyncStatusText(SharedPreferences prefs) {
        StringBuilder status = new StringBuilder("Active: ");
        
        boolean any = false;
        if (prefs.getBoolean("sync_webdav_enabled", false)) {
            status.append("WebDAV ");
            any = true;
        }
        if (prefs.getBoolean("sync_local_network_enabled", false)) {
            status.append("Local ");
            any = true;
        }
        if (prefs.getBoolean("sync_drive_enabled", false)) {
            status.append("Drive");
            any = true;
        }
        
        if (!any) {
            return "None configured";
        }
        
        return status.toString();
    }
    
    private void triggerSync() {
        progressSync.setVisibility(View.VISIBLE);
        btnSyncNow.setEnabled(false);
        
        // Trigger sync
        SyncCoordinatorImpl.getInstance(requireContext()).triggerManualSync();
        
        // Update UI after 2 seconds
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (isAdded()) {
                progressSync.setVisibility(View.GONE);
                btnSyncNow.setEnabled(true);
                updateSyncStatus();
                Toast.makeText(requireContext(), "Sync triggered", Toast.LENGTH_SHORT).show();
            }
        }, 2000);
    }
    
    private void viewDebugLog() {
        // TODO: Launch debug log viewer activity
        Toast.makeText(requireContext(), "Debug log viewer - coming soon", Toast.LENGTH_SHORT).show();
    }
}
