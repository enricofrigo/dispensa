package eu.frigo.dispensa.sync.core.engine;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.frigo.dispensa.sync.core.model.SyncChange;

/**
 * Manages synchronization using Lamport clocks and Last-Write-Wins (LWW) conflict resolution.
 */
public class CrDtSyncManager {
    private static final String TAG = "CrDtSyncManager";

    public static final String PREFS_KEY_LAST_SYNC_VERSION = "last_sync_version";
    public static final String PREFS_KEY_DEVICE_ID = "sync_device_id";
    public static final String PREF_DEVICE_NAME = "sync_device_name";

    private final SupportSQLiteDatabase db;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    /**
     * Public constructor.
     * Takes RoomDatabase (e.g., AppDatabase) and Context.
     */
    public CrDtSyncManager(RoomDatabase database, Context context) {
        this.db = database.getOpenHelper().getWritableDatabase();
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * Package-private constructor for unit tests.
     */
    CrDtSyncManager(SupportSQLiteDatabase db, SharedPreferences prefs) {
        this.db = db;
        this.prefs = prefs;
    }

    /**
     * Exports local changes since lastSyncVersion.
     * @param lastSyncVersion The version/clock of the last successful sync.
     * @return UTF-8 encoded JSON SyncBlob.
     */
    public byte[] exportChanges(long lastSyncVersion) {
        List<SyncChange> changes = new ArrayList<>();
        String query = "SELECT tbl, pk_val, op, row_json, clock FROM sync_changes WHERE clock > ? ORDER BY clock ASC";
        
        try (Cursor cursor = db.query(query, new Object[]{lastSyncVersion})) {
            while (cursor.moveToNext()) {
                SyncChange change = new SyncChange();
                change.tbl = cursor.getString(0);
                change.pkVal = cursor.getString(1);
                change.op = cursor.getString(2);
                change.rowJson = cursor.getString(3);
                change.clock = cursor.getLong(4);
                change.deviceId = getLocalDeviceId();
                changes.add(change);
            }
        }

        SyncBlob blob = new SyncBlob();
        blob.senderDeviceId = getLocalDeviceId();
        blob.senderLastSyncVersion = getMaxSyncClock();
        blob.changes = changes;

        String json = gson.toJson(blob);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Imports changes from a remote blob using LWW conflict resolution.
     * @param blobBytes The received sync blob as bytes.
     */
    public void importChanges(byte[] blobBytes) {
        if (blobBytes == null || blobBytes.length == 0) return;
        
        String json = new String(blobBytes, StandardCharsets.UTF_8);
        SyncBlob blob = gson.fromJson(json, SyncBlob.class);
        if (blob == null || blob.changes == null) return;

        db.execSQL("UPDATE sync_import_lock SET locked = 1");
        db.beginTransaction();
        try {
            String localDeviceId = getLocalDeviceId();
            for (SyncChange incoming : blob.changes) {
                long localMaxClock = getLocalMaxClock(incoming.tbl, incoming.pkVal);
                
                // LWW Logic: incoming clock > local OR (same clock AND incoming deviceId > local deviceId)
                boolean incomingWins = incoming.clock > localMaxClock ||
                        (incoming.clock == localMaxClock && incoming.deviceId.compareTo(localDeviceId) > 0);

                if (incomingWins) {
                    if ("UPSERT".equals(incoming.op)) {
                        applyUpsert(incoming.tbl, incoming.rowJson);
                    } else if ("DELETE".equals(incoming.op)) {
                        applyDelete(incoming.tbl, incoming.pkVal);
                    }

                    // Record the change locally with the incoming clock
                    db.execSQL("INSERT OR REPLACE INTO sync_changes (tbl, pk_val, op, row_json, clock) VALUES (?, ?, ?, ?, ?)",
                            new Object[]{incoming.tbl, incoming.pkVal, incoming.op, incoming.rowJson, incoming.clock});
                }
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error importing changes", e);
        } finally {
            db.endTransaction();
            db.execSQL("UPDATE sync_import_lock SET locked = 0");
        }
    }

    /**
     * @return The highest clock value present in the local sync_changes table.
     */
    public long getMaxSyncClock() {
        try (Cursor cursor = db.query("SELECT COALESCE(MAX(clock), 0) FROM sync_changes", null)) {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        }
        return 0;
    }

    public long getLastSyncVersion() {
        return prefs.getLong(PREFS_KEY_LAST_SYNC_VERSION, 0L);
    }

    public void persistLastSyncVersion(long version) {
        prefs.edit().putLong(PREFS_KEY_LAST_SYNC_VERSION, version).apply();
    }

    public String getLocalDeviceId() {
        String deviceId = prefs.getString(PREFS_KEY_DEVICE_ID, null);
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString();
            prefs.edit().putString(PREFS_KEY_DEVICE_ID, deviceId).apply();
        }
        return deviceId;
    }

    public static String extractSenderDeviceId(byte[] blobBytes) {
        if (blobBytes == null) return null;
        try {
            String json = new String(blobBytes, StandardCharsets.UTF_8);
            JsonObject obj = new Gson().fromJson(json, JsonObject.class);
            return obj.has("senderDeviceId") ? obj.get("senderDeviceId").getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static long extractSenderLastSyncVersion(byte[] blobBytes) {
        if (blobBytes == null) return 0;
        try {
            String json = new String(blobBytes, StandardCharsets.UTF_8);
            JsonObject obj = new Gson().fromJson(json, JsonObject.class);
            return obj.has("senderLastSyncVersion") ? obj.get("senderLastSyncVersion").getAsLong() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private long getLocalMaxClock(String tbl, String pkVal) {
        try (Cursor cursor = db.query("SELECT clock FROM sync_changes WHERE tbl = ? AND pk_val = ?", new Object[]{tbl, pkVal})) {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        }
        return -1;
    }

    private void applyUpsert(String tbl, String rowJson) {
        if (rowJson == null) return;
        Map<String, Object> map = gson.fromJson(rowJson, new TypeToken<Map<String, Object>>() {}.getType());
        ContentValues values = new ContentValues();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object val = entry.getValue();
            if (val == null) {
                values.putNull(entry.getKey());
            } else if (val instanceof String) {
                values.put(entry.getKey(), (String) val);
            } else if (val instanceof Double) {
                Double d = (Double) val;
                if (d == Math.floor(d)) {
                    values.put(entry.getKey(), d.longValue());
                } else {
                    values.put(entry.getKey(), d);
                }
            } else if (val instanceof Boolean) {
                values.put(entry.getKey(), (Boolean) val ? 1 : 0);
            }
        }

        db.insert(tbl, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, values);
    }

    private void applyDelete(String tbl, String pkVal) {
        if ("product_category_links".equals(tbl)) {
            String[] parts = pkVal.split("\\|");
            if (parts.length == 2) {
                db.delete(tbl, "product_id_fk = ? AND category_id_fk = ?", parts);
            }
        } else {
            String pkCol = getPkColumn(tbl);
            db.delete(tbl, pkCol + " = ?", new String[]{pkVal});
        }
    }

    private String getPkColumn(String tbl) {
        switch (tbl) {
            case "categories_definitions":
                return "category_id";
            case "products":
            case "storage_locations":
            default:
                return "id";
        }
    }

    private static class SyncBlob {
        String senderDeviceId;
        long senderLastSyncVersion;
        List<SyncChange> changes;
    }
}
