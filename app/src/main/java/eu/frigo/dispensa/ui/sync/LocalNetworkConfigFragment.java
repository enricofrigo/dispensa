package eu.frigo.dispensa.ui.sync;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import eu.frigo.dispensa.R;

/**
 * Local Network (mDNS + TCP) sync configuration fragment.
 * Features:
 * - Device name configuration
 * - Peer discovery via mDNS
 * - Pairing/trust management
 */
public class LocalNetworkConfigFragment extends Fragment {
    
    private static final String TAG = "LocalNetworkConfigFragment";
    
    private EditText editDeviceName;
    private Button btnScan, btnEnable;
    private ProgressBar progressScan;
    private RecyclerView recyclerPeers;
    private TextView textNoPeers;
    private LinearLayout layoutNoPermission;
    private Button btnGrantPermission;
    
    private LocalPeersAdapter peersAdapter;
    private List<PeerDevice> discoveredPeers = new ArrayList<>();
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_local_network_config, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        editDeviceName = view.findViewById(R.id.edit_device_name);
        btnScan = view.findViewById(R.id.btn_scan_peers);
        btnEnable = view.findViewById(R.id.btn_enable_local_sync);
        progressScan = view.findViewById(R.id.progress_scan);
        recyclerPeers = view.findViewById(R.id.recycler_peers);
        textNoPeers = view.findViewById(R.id.text_no_peers);
        layoutNoPermission = view.findViewById(R.id.layout_no_permission);
        btnGrantPermission = view.findViewById(R.id.btn_grant_permission);
        
        setupRecyclerView();
        loadSavedDeviceName();
        setupListeners();
        
        // Check WiFi permissions
        checkWiFiPermissions();
    }
    
    private void setupRecyclerView() {
        peersAdapter = new LocalPeersAdapter(discoveredPeers, peer -> {
            // Handle peer trust/pairing
            trustPeer(peer);
        });
        recyclerPeers.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerPeers.setAdapter(peersAdapter);
    }
    
    private void loadSavedDeviceName() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String deviceName = prefs.getString("pref_local_device_name", "");
        editDeviceName.setText(deviceName);
    }
    
    private void setupListeners() {
        btnScan.setOnClickListener(v -> scanForPeers());
        btnEnable.setOnClickListener(v -> enableLocalSync());
        btnGrantPermission.setOnClickListener(v -> requestWiFiPermissions());
    }
    
    private void checkWiFiPermissions() {
        // TODO: Check for WiFi and location permissions (required for NSD on Android 10+)
        // If not granted, show layoutNoPermission
        if (hasWiFiPermissions()) {
            layoutNoPermission.setVisibility(View.GONE);
        } else {
            layoutNoPermission.setVisibility(View.VISIBLE);
        }
    }
    
    private boolean hasWiFiPermissions() {
        // TODO: Implement permission check
        return true;
    }
    
    private void requestWiFiPermissions() {
        // TODO: Request WiFi and location permissions
        Toast.makeText(requireContext(), "Permission requested", Toast.LENGTH_SHORT).show();
    }
    
    private void scanForPeers() {
        String deviceName = editDeviceName.getText().toString().trim();
        if (deviceName.isEmpty()) {
            showError("Please set a device name first");
            return;
        }
        
        // Save device name
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        prefs.edit().putString("pref_local_device_name", deviceName).apply();
        
        progressScan.setVisibility(View.VISIBLE);
        btnScan.setEnabled(false);
        discoveredPeers.clear();
        
        // Scan for peers on background thread
        new Thread(() -> {
            try {
                // TODO: Use LocalNetworkSyncTransport.getDiscoveredPeers()
                Thread.sleep(2000); // Simulate scan
                
                // Mock peer for demonstration
                discoveredPeers.add(new PeerDevice(
                        "device-1",
                        "Alice's Phone",
                        "192.168.1.100",
                        false
                ));
                
                requireActivity().runOnUiThread(() -> {
                    progressScan.setVisibility(View.GONE);
                    btnScan.setEnabled(true);
                    
                    if (discoveredPeers.isEmpty()) {
                        textNoPeers.setVisibility(View.VISIBLE);
                        recyclerPeers.setVisibility(View.GONE);
                    } else {
                        textNoPeers.setVisibility(View.GONE);
                        recyclerPeers.setVisibility(View.VISIBLE);
                        peersAdapter.notifyDataSetChanged();
                    }
                });
            } catch (InterruptedException e) {
                Log.e(TAG, "Scan interrupted", e);
            }
        }).start();
    }
    
    private void trustPeer(PeerDevice peer) {
        peer.trusted = !peer.trusted;
        
        // Save trusted status to SharedPreferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        prefs.edit().putBoolean("pref_trusted_peer_" + peer.deviceId, peer.trusted).apply();
        
        peersAdapter.notifyDataSetChanged();
        showSuccess(peer.trusted ? "Device trusted" : "Device untrusted");
    }
    
    private void enableLocalSync() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        boolean enabled = prefs.getBoolean("sync_local_network_enabled", false);
        prefs.edit().putBoolean("sync_local_network_enabled", !enabled).apply();
        
        String message = (!enabled) ? "Local Network sync enabled" : "Local Network sync disabled";
        showSuccess(message);
        
        // TODO: Start/stop LocalNetworkSyncTransport
    }
    
    private void showError(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
    
    private void showSuccess(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
    
    // ─────────────────────────────────────────────────────────────
    // Data class for discovered peers
    // ─────────────────────────────────────────────────────────────
    
    public static class PeerDevice {
        public String deviceId;
        public String friendlyName;
        public String ipAddress;
        public boolean trusted;
        
        public PeerDevice(String deviceId, String friendlyName, String ipAddress, boolean trusted) {
            this.deviceId = deviceId;
            this.friendlyName = friendlyName;
            this.ipAddress = ipAddress;
            this.trusted = trusted;
        }
    }
}
