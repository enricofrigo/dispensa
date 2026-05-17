package eu.frigo.dispensa.sync.core;

/**
 * Global registry for the database provider.
 */
public class DatabaseRegistry {
    private static DatabaseProvider provider;

    public static synchronized void setProvider(DatabaseProvider p) {
        provider = p;
    }

    public static synchronized DatabaseProvider getProvider() {
        return provider;
    }
}
