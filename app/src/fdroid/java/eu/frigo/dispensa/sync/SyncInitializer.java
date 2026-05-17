package eu.frigo.dispensa.sync;

import android.content.Context;
import eu.frigo.dispensa.sync.core.TransportRegistry;
import eu.frigo.dispensa.sync.webdav.WebDavTransportFactory;
import eu.frigo.dispensa.sync.local.LocalTransportFactory;

public class SyncInitializer {
    public static void init(Context context) {
        TransportRegistry registry = TransportRegistry.getInstance();
        registry.registerFactory(new WebDavTransportFactory());
        registry.registerFactory(new LocalTransportFactory());
    }
}
