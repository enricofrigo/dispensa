package eu.frigo.dispensa.sync.local;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.frigo.dispensa.sync.core.engine.CrDtSyncManager;
import eu.frigo.dispensa.sync.core.SyncTransport;

/**
 * Implementation of SyncTransport using local network (mDNS/NSD) for peer discovery
 * and raw TCP sockets for data transfer.
 */
public class LocalNetworkSyncTransport implements SyncTransport {
    private static final String TAG = "LocalSyncTransport";

    public static final String SERVICE_TYPE = "_dispensa._tcp.";
    public static final String SERVICE_NAME_PREFIX = "dispensa-";
    private static final int SERVER_SOCKET_TIMEOUT = 30_000;

    private final NsdManager nsdManager;
    private final WifiManager wifiManager;
    private final CrDtSyncManager syncManager;
    private final ExecutorService executor;
    private final List<NsdServiceInfo> discoveredPeers = new ArrayList<>();

    private WifiManager.MulticastLock multicastLock;
    private ServerSocket serverSocket;
    private int localPort;
    private boolean isRunning = false;
    private NsdManager.RegistrationListener registrationListener;
    private NsdManager.DiscoveryListener discoveryListener;

    public LocalNetworkSyncTransport(Context context, CrDtSyncManager syncManager) {
        this.nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        this.syncManager = syncManager;
        this.executor = Executors.newCachedThreadPool();
    }

    /**
     * Package-private constructor for unit tests.
     */
    LocalNetworkSyncTransport(NsdManager nsd, WifiManager wifi, CrDtSyncManager sm, ExecutorService exec) {
        this.nsdManager = nsd;
        this.wifiManager = wifi;
        this.syncManager = sm;
        this.executor = exec;
    }

    public synchronized void start() {
        if (isRunning) return;

        try {
            // Acquire multicast lock for NSD to work on some devices
            multicastLock = wifiManager.createMulticastLock("dispensa-sync-lock");
            multicastLock.setReferenceCounted(true);
            multicastLock.acquire();

            serverSocket = new ServerSocket(0);
            localPort = serverSocket.getLocalPort();
            isRunning = true;

            registerService();
            startDiscovery();

            executor.execute(this::acceptIncomingConnections);
            Log.d(TAG, "Started on port: " + localPort);
        } catch (IOException e) {
            Log.e(TAG, "Failed to start local sync server", e);
            stop();
        }
    }

    public synchronized void stop() {
        isRunning = false;
        if (nsdManager != null) {
            if (registrationListener != null) {
                nsdManager.unregisterService(registrationListener);
                registrationListener = null;
            }
            if (discoveryListener != null) {
                nsdManager.stopServiceDiscovery(discoveryListener);
                discoveryListener = null;
            }
        }

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
            serverSocket = null;
        }

        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
            multicastLock = null;
        }

        synchronized (discoveredPeers) {
            discoveredPeers.clear();
        }
        Log.d(TAG, "Stopped");
    }

    @Override
    public void push(byte[] data, SyncCallback callback) {
        NsdServiceInfo peer;
        synchronized (discoveredPeers) {
            if (discoveredPeers.isEmpty()) {
                callback.onSuccess(null);
                return;
            }
            peer = discoveredPeers.get(0); // Simple logic: pick first peer
        }

        executor.execute(() -> {
            try (Socket socket = new Socket(peer.getHost(), peer.getPort())) {
                socket.setSoTimeout(SERVER_SOCKET_TIMEOUT);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream());

                // Send data
                out.writeInt(data.length);
                out.write(data);
                out.flush();

                // Receive response
                int responseLen = in.readInt();
                byte[] responseData = new byte[responseLen];
                in.readFully(responseData);

                callback.onSuccess(responseData);
            } catch (IOException e) {
                callback.onError(e);
            }
        });
    }

    @Override
    public void pull(SyncCallback callback) {
        // Passive mode: we wait for others to push to us.
        callback.onSuccess(null);
    }

    public List<NsdServiceInfo> getDiscoveredPeers() {
        synchronized (discoveredPeers) {
            return new ArrayList<>(discoveredPeers);
        }
    }

    private void registerService() {
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(SERVICE_NAME_PREFIX + syncManager.getLocalDeviceId());
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(localPort);

        registrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
                Log.d(TAG, "Service registered: " + NsdServiceInfo.getServiceName());
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Registration failed: " + errorCode);
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo arg0) {
                Log.d(TAG, "Service unregistered");
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Unregistration failed: " + errorCode);
            }
        };

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
    }

    private void startDiscovery() {
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String regType) {
                Log.d(TAG, "Discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                Log.d(TAG, "Service found: " + service.getServiceName());
                if (!service.getServiceType().equals(SERVICE_TYPE)) {
                    Log.d(TAG, "Unknown Service Type: " + service.getServiceType());
                } else if (service.getServiceName().contains(syncManager.getLocalDeviceId())) {
                    Log.d(TAG, "Own service found: " + service.getServiceName());
                } else {
                    resolveService(service);
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                Log.d(TAG, "Service lost: " + service.getServiceName());
                synchronized (discoveredPeers) {
                    discoveredPeers.removeIf(p -> p.getServiceName().equals(service.getServiceName()));
                }
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.i(TAG, "Discovery stopped: " + serviceType);
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                nsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery stop failed: Error code:" + errorCode);
                nsdManager.stopServiceDiscovery(this);
            }
        };

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }

    private void resolveService(NsdServiceInfo info) {
        nsdManager.resolveService(info, new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Resolve failed: " + errorCode);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Resolve Succeeded: " + serviceInfo);
                if (serviceInfo.getPort() == localPort) {
                    return;
                }
                synchronized (discoveredPeers) {
                    discoveredPeers.add(serviceInfo);
                }
            }
        });
    }

    private void acceptIncomingConnections() {
        while (isRunning && serverSocket != null) {
            try {
                Socket socket = serverSocket.accept();
                executor.execute(() -> handleIncomingConnection(socket));
            } catch (IOException e) {
                if (isRunning) Log.e(TAG, "Error accepting connection", e);
            }
        }
    }

    private void handleIncomingConnection(Socket socket) {
        try {
            socket.setSoTimeout(SERVER_SOCKET_TIMEOUT);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // 1. Read data
            int length = in.readInt();
            byte[] incomingData = new byte[length];
            in.readFully(incomingData);

            // 2. Import changes
            syncManager.importChanges(incomingData);

            // 3. Export local changes for response
            long senderLastVer = CrDtSyncManager.extractSenderLastSyncVersion(incomingData);
            byte[] response = syncManager.exportChanges(senderLastVer);

            // 4. Send response
            out.writeInt(response.length);
            out.write(response);
            out.flush();

            Log.d(TAG, "Handled incoming connection from " + CrDtSyncManager.extractSenderDeviceId(incomingData));

        } catch (IOException e) {
            Log.e(TAG, "Error handling incoming connection", e);
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }
}
