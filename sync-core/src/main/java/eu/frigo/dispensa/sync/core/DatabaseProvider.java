package eu.frigo.dispensa.sync.core;

import android.content.Context;
import androidx.room.RoomDatabase;

/**
 * Interface to provide the Room database instance without direct coupling.
 */
public interface DatabaseProvider {
    RoomDatabase getDatabase(Context context);
}
