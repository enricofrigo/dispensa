package eu.frigo.dispensa.sync;

import android.app.Activity;
import android.content.Context;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.gdrive.GDriveSyncProviderLoader;
import eu.frigo.dispensa.sync.ui.PlaySyncConfigActivity;
import eu.frigo.dispensa.sync.webdav.WebDavSyncProviderLoader;

public class SyncInitializer {
    public static void init(Context context) {
        SyncManager.getInstance().registerLoader(new WebDavSyncProviderLoader());
        SyncManager.getInstance().registerLoader(new GDriveSyncProviderLoader());
    }

    public static Class<? extends Activity> getSyncConfigActivity() {
        return PlaySyncConfigActivity.class;
    }
}
