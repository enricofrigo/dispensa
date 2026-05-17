package eu.frigo.dispensa.sync.core;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import eu.frigo.dispensa.sync.core.engine.CrDtSyncManager;

/**
 * Service locator for sync transports.
 *
 * Breaks circular dependency: instead of SyncWorker importing all transport factories,
 * the app module registers transports at startup, and SyncWorker uses the registry.
 */
public class TransportRegistry {

    private static final String TAG = "TransportRegistry";
    private static volatile TransportRegistry INSTANCE;

    private final List<TransportFactory> factories = new ArrayList<>();

    private TransportRegistry() {
    }

    public static TransportRegistry getInstance() {
        if (INSTANCE == null) {
            synchronized (TransportRegistry.class) {
                if (INSTANCE == null) {
                    INSTANCE = new TransportRegistry();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Register a transport factory.
     * Called at app startup to register WebDAV, Local, Drive factories.
     */
    public void registerFactory(TransportFactory factory) {
        if (factory == null) {
            Log.w(TAG, "Attempted to register null factory");
            return;
        }
        factories.add(factory);
        Log.d(TAG, "Registered transport factory: " + factory.getClass().getSimpleName());
    }

    /**
     * Get all available transports for this sync session.
     */
    public List<SyncTransport> getActiveTransports(Context context, CrDtSyncManager syncManager) {
        List<SyncTransport> transports = new ArrayList<>();

        for (TransportFactory factory : factories) {
            try {
                SyncTransport transport = factory.create(context, syncManager);
                if (transport != null) {
                    transports.add(transport);
                    Log.d(TAG, "Created transport from " + factory.getClass().getSimpleName());
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to create transport from " + factory.getClass().getSimpleName(), e);
            }
        }

        return transports;
    }

    /**
     * Clear all registered factories (mainly for testing).
     */
    public void clear() {
        factories.clear();
        Log.d(TAG, "Registry cleared");
    }

    /**
     * Factory interface for creating transport instances.
     * Implemented by WebDavTransportFactory, LocalTransportFactory, DriveTransportFactory.
     */
    public interface TransportFactory {
        /**
         * Create a transport instance if configured and available.
         *
         * @return SyncTransport instance, or null if not configured/unavailable
         */
        SyncTransport create(Context context, CrDtSyncManager syncManager);
    }
}
