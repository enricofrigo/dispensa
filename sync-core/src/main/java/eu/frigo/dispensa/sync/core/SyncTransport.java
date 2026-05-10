package eu.frigo.dispensa.sync.core;

import androidx.annotation.Nullable;

/**
 * Interface for pluggable synchronization transports (e.g., WebDAV, Bluetooth, QR).
 */
public interface SyncTransport {

    /**
     * Pushes local changes to the remote store and optionally receives a response blob.
     *
     * @param data     The local sync blob to send.
     * @param callback Result handler.
     */
    void push(byte[] data, SyncCallback callback);

    /**
     * Pulls remote changes from the remote store.
     *
     * @param callback Result handler.
     */
    void pull(SyncCallback callback);

    /**
     * Callback for asynchronous sync transport operations.
     */
    interface SyncCallback {
        /**
         * Called when the operation completes successfully.
         *
         * @param blobBytes The received remote sync blob, or null if no new data.
         */
        void onSuccess(@Nullable byte[] blobBytes);

        /**
         * Called when the operation fails.
         *
         * @param e The exception describing the failure.
         */
        void onError(Exception e);
    }
}
