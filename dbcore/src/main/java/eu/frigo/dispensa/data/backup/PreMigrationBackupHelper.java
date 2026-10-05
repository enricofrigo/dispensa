package eu.frigo.dispensa.data.backup;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PreMigrationBackupHelper {

    private static final String TAG = "PreMigrationBackup";

    public static void checkAndBackup(Context context, String dbName, int targetVersion) {
        File dbFile = context.getDatabasePath(dbName);
        if (!dbFile.exists()) {
            Log.d(TAG, "Database file does not exist, skipping pre-migration backup.");
            return;
        }

        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) {
            int currentVersion = db.getVersion();
            if (currentVersion > 0 && currentVersion < targetVersion) {
                Log.i(TAG, "Migration detected (v" + currentVersion + " -> v" + targetVersion + "). Performing pre-migration backup...");
                performRawBackup(context, db, currentVersion, targetVersion);
            } else {
                Log.d(TAG, "No migration needed (v" + currentVersion + "). skipping backup.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during pre-migration version check", e);
        }
    }

    private static void performRawBackup(Context context, SQLiteDatabase db, int fromVersion, int toVersion) {
        try {
            Map<String, List<Map<String, Object>>> backupData = new HashMap<>();
            
            // 1. Get all user tables
            List<String> tables = new ArrayList<>();
            try (Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_master_table'", null)) {
                while (cursor.moveToNext()) {
                    tables.add(cursor.getString(0));
                }
            }

            // 2. Export each table
            for (String table : tables) {
                List<Map<String, Object>> rows = new ArrayList<>();
                try (Cursor cursor = db.rawQuery("SELECT * FROM " + table, null)) {
                    String[] columnNames = cursor.getColumnNames();
                    while (cursor.moveToNext()) {
                        Map<String, Object> row = new HashMap<>();
                        for (String col : columnNames) {
                            int idx = cursor.getColumnIndex(col);
                            switch (cursor.getType(idx)) {
                                case Cursor.FIELD_TYPE_NULL: row.put(col, null); break;
                                case Cursor.FIELD_TYPE_INTEGER: row.put(col, cursor.getLong(idx)); break;
                                case Cursor.FIELD_TYPE_FLOAT: row.put(col, cursor.getDouble(idx)); break;
                                case Cursor.FIELD_TYPE_STRING: row.put(col, cursor.getString(idx)); break;
                                case Cursor.FIELD_TYPE_BLOB: row.put(col, "[BLOB]"); break;
                            }
                        }
                        rows.add(row);
                    }
                }
                backupData.put(table, rows);
            }

            // 3. Save to JSON
            File backupDir = new File(context.getFilesDir(), "backups/pre_migration");
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                Log.e(TAG, "Failed to create backup directory");
                return;
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date());
            String fileName = String.format("backup_v%d_to_v%d_%s.json", fromVersion, toVersion, timestamp);
            File backupFile = new File(backupDir, fileName);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileOutputStream fos = new FileOutputStream(backupFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos)) {
                gson.toJson(backupData, writer);
            }

            Log.i(TAG, "Pre-migration backup completed successfully: " + backupFile.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "Critical error during pre-migration backup. Migration might fail and data could be lost!", e);
        }
    }
}
