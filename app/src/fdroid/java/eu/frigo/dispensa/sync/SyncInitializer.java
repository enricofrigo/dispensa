package eu.frigo.dispensa.sync;

import android.app.Activity;
import android.content.Context;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.ui.SyncConfigActivity;
import eu.frigo.dispensa.sync.webdav.WebDavSyncProviderLoader;

public class SyncInitializer {
    public static void init(Context context) {
        SyncManager.getInstance().registerLoader(new WebDavSyncProviderLoader());
    }

    public static Class<? extends Activity> getSyncConfigActivity() {
        return SyncConfigActivity.class;
    }
}
