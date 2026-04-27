package eu.frigo.dispensa.data.openfoodfacts;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface OpenFoodFactCacheDao {

    @Query("SELECT * FROM openfoodfact_cache WHERE barcode = :barcode LIMIT 1")
    OpenFoodFactCacheEntity getCacheByBarcode(String barcode);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCache(OpenFoodFactCacheEntity entity);

    @Query("DELETE FROM openfoodfact_cache")
    void clearAllCache();

    @Query("SELECT COUNT(*) FROM openfoodfact_cache")
    int getCacheCount();

    @Query("DELETE FROM openfoodfact_cache WHERE barcode IN (SELECT barcode FROM openfoodfact_cache ORDER BY timestamp_ms ASC LIMIT :amountToDelete)")
    void deleteOldest(int amountToDelete);

    @Query("SELECT image_local_path FROM openfoodfact_cache WHERE image_local_path IS NOT NULL AND image_local_path != ''")
    List<String> getAllImagePaths();

    // Query helper to get image paths that are about to be deleted
    @Query("SELECT image_local_path FROM openfoodfact_cache ORDER BY timestamp_ms ASC LIMIT :amountToDelete")
    List<String> getOldestImagePaths(int amountToDelete);
}
