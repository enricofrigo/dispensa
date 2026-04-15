package eu.frigo.dispensa.data.openfoodfacts;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import eu.frigo.dispensa.data.AppDatabase;

public class OpenFoodFactCacheManager {

    private static final String CACHE_DIR_NAME = "off_cache";

    public static File getCacheDirectory(Context context) {
        File dir = new File(context.getFilesDir(), CACHE_DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static void cleanExpiredAndOverflow(Context context, AppDatabase db, int limit, long ttlMs) {
        OpenFoodFactCacheDao dao = db.openFoodFactCacheDao();
        
        // Overflow
        int currentCount = dao.getCacheCount();
        if (currentCount > limit) {
            int toDeleteCount = currentCount - limit;
            List<String> oldImages = dao.getOldestImagePaths(toDeleteCount);
            if (oldImages != null) {
                for (String path : oldImages) {
                    deleteImageFile(path);
                }
            }
            dao.deleteOldest(toDeleteCount);
        }
        // Since we evaluate expired on GET, we could also cleanup expired here, but overflow is more critical for space.
    }

    public static void clearAllCache(Context context, AppDatabase db) {
        OpenFoodFactCacheDao dao = db.openFoodFactCacheDao();
        List<String> allImages = dao.getAllImagePaths();
        if (allImages != null) {
            for (String path : allImages) {
                deleteImageFile(path);
            }
        }
        dao.clearAllCache();
        
        File dir = getCacheDirectory(context);
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        }
    }

    private static void deleteImageFile(String path) {
        if (path != null && !path.isEmpty()) {
            File f = new File(path);
            if (f.exists()) {
                f.delete();
            }
        }
    }

    public static String downloadAndSaveImage(Context context, String imageUrl, String barcode) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return null;
        }
        try {
            File dir = getCacheDirectory(context);
            File imageFile = new File(dir, barcode + ".jpg");
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            
            FileOutputStream output = new FileOutputStream(imageFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            output.close();
            input.close();
            
            return imageFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e("OpenFoodFactCacheManager", "Error downloading image for cache: " + e.getMessage());
            return null;
        }
    }
}
