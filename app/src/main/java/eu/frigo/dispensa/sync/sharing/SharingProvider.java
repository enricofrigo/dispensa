package eu.frigo.dispensa.sync.sharing;

import android.content.Context;

import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.sync.core.store.SharedFolderStore;

/**
 * Interface defining a provider for sharing a pantry.
 */
public interface SharingProvider {

    /**
     * Unique identifier matching providerId (e.g. "webdav", "local_saf").
     */
    String getProviderId();

    /**
     * Human-readable name for UI presentation (e.g. "WebDAV", "Cartella Condivisa Locale (SAF)").
     */
    String getDisplayName(Context context);

    /**
     * Checks whether the user has configured the necessary parameters/permissions for this provider.
     */
    boolean isConfigured(Context context);

    /**
     * Creates the SharedFolderStore instance for the specified pantry.
     */
    SharedFolderStore createStore(Context context, Dispensa dispensa);

    /**
     * Computes the relative pantry path on the store for this dispensa.
     */
    String getPantryPath(Context context, Dispensa dispensa);

    /**
     * Creates the JoinedPantryConfig record for persisting in local DB.
     */
    JoinedPantryConfig createJoinedConfig(Context context, Dispensa dispensa);
}
