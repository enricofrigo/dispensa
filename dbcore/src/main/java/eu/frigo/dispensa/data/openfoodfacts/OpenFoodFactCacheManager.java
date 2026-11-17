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
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    public static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024; // 5 MB max
    public static final String USER_AGENT = "Dispensa/0.1.18 (Android; +https://github.com/enricofrigo/dispensa)";

    public static boolean isFileExisting(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        File f = new File(path);
        return f.exists();
    }

    public static File getCacheDirectory(Context context) {
        File dir = new File(context.getFilesDir(), CACHE_DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static String sanitizeBarcodeForFilename(String barcode) {
        if (barcode == null) return "";
        return barcode.trim().replaceAll("[^a-zA-Z0-9_-]", "");
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
        String safeBarcode = sanitizeBarcodeForFilename(barcode);
        if (safeBarcode.isEmpty()) {
            Log.w("OpenFoodFactCacheManager", "Invalid or unsafe barcode for filename: " + barcode);
            return null;
        }

        File imageFile = null;
        HttpURLConnection connection = null;
        boolean success = false;
        try {
            File dir = getCacheDirectory(context);
            imageFile = new File(dir, safeBarcode + ".jpg");
            String dirCanonical = dir.getCanonicalPath();
            String fileCanonical = imageFile.getCanonicalPath();
            if (!fileCanonical.startsWith(dirCanonical + File.separator)) {
                Log.e("OpenFoodFactCacheManager", "Path traversal blocked for barcode: " + barcode);
                return null;
            }

            URL url = new URL(imageUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setDoInput(true);
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w("OpenFoodFactCacheManager", "HTTP " + responseCode + " downloading image: " + imageUrl);
                return null;
            }

            int contentLength = connection.getContentLength();
            if (contentLength > MAX_IMAGE_BYTES) {
                Log.w("OpenFoodFactCacheManager", "Image exceeds max size (" + contentLength + " bytes): " + imageUrl);
                return null;
            }

            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(imageFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                int totalRead = 0;
                while ((bytesRead = input.read(buffer)) != -1) {
                    totalRead += bytesRead;
                    if (totalRead > MAX_IMAGE_BYTES) {
                        Log.w("OpenFoodFactCacheManager", "Stream exceeded max size limit of " + MAX_IMAGE_BYTES + " bytes");
                        return null;
                    }
                    output.write(buffer, 0, bytesRead);
                }
                output.flush();
            }

            success = true;
            return imageFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e("OpenFoodFactCacheManager", "Error downloading image for cache: " + e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (!success && imageFile != null && imageFile.exists()) {
                imageFile.delete();
            }
        }
    }
}
