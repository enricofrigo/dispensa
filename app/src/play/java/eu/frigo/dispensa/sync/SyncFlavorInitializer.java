package eu.frigo.dispensa.sync;

import android.app.Application;

import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.gdrive.GDriveSyncProviderLoader;
import eu.frigo.dispensa.sync.local.LocalSafSyncProviderLoader;
import eu.frigo.dispensa.sync.sharing.GDriveSharingProvider;
import eu.frigo.dispensa.sync.sharing.LocalSafSharingProvider;
import eu.frigo.dispensa.sync.sharing.PantrySharingService;
import eu.frigo.dispensa.sync.sharing.WebDavSharingProvider;
import eu.frigo.dispensa.sync.webdav.WebDavSyncProviderLoader;

public class SyncFlavorInitializer {
    public static void initialize(Application app) {
        SyncManager.getInstance().registerLoader(new WebDavSyncProviderLoader());
        SyncManager.getInstance().registerLoader(new LocalSafSyncProviderLoader());
        SyncManager.getInstance().registerLoader(new GDriveSyncProviderLoader());

        PantrySharingService sharingService = PantrySharingService.getInstance();
        sharingService.registerProvider(new WebDavSharingProvider());
        sharingService.registerProvider(new LocalSafSharingProvider());
        sharingService.registerProvider(new GDriveSharingProvider());
    }
}
