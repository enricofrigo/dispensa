package eu.frigo.dispensa.sync.webdav.migration;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.sync.SyncOutbox;

/**
 * Utility for one-time migration from legacy SyncOutbox to CRDT sync_changes.
 */
public class WebDavSyncMigration {
    private static final String TAG = "WebDavSyncMigration";
    private static final String PREF_MIGRATION_DONE = "migration_outbox_to_sync_changes_done";

    public static void migrateOutboxToSyncChanges(AppDatabase db, Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean(PREF_MIGRATION_DONE, false)) {
            return;
        }

        List<SyncOutbox> pending = db.syncOutboxDao().getPendingChangesSync();
        if (pending == null || pending.isEmpty()) {
            markMigrationComplete(prefs);
            return;
        }

        Log.i(TAG, "Starting migration of " + pending.size() + " legacy events.");
        
        Gson gson = new Gson();
        List<String> migratedSyncIds = new ArrayList<>();
        long clock = 1;

        db.beginTransaction();
        try {
            db.getOpenHelper().getWritableDatabase().execSQL("UPDATE sync_import_lock SET locked = 1");

            for (SyncOutbox entry : pending) {
                try {
                    String table = mapDataTypeToTable(entry.dataType);
                    if (table == null) {
                        Log.w(TAG, "Skipping unknown dataType: " + entry.dataType);
                        continue;
                    }

                    JsonObject payload = gson.fromJson(entry.payload, JsonObject.class);
                    String pk = extractPk(table, payload);
                    
                    if (pk != null) {
                        db.getOpenHelper().getWritableDatabase().execSQL(
                                "INSERT OR REPLACE INTO sync_changes (tbl, pk_val, op, row_json, clock) VALUES (?, ?, ?, ?, ?)",
                                new Object[]{table, pk, "UPSERT", entry.payload, clock++}
                        );
                        migratedSyncIds.add(entry.syncId);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse legacy payload for syncId: " + entry.syncId, e);
                }
            }

            db.getOpenHelper().getWritableDatabase().execSQL("UPDATE sync_import_lock SET locked = 0");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        if (!migratedSyncIds.isEmpty()) {
            db.syncOutboxDao().markAsSynced(migratedSyncIds);
        }
        
        markMigrationComplete(prefs);
        Log.i(TAG, "Migrated " + migratedSyncIds.size() + " events to sync_changes.");
    }

    private static void markMigrationComplete(SharedPreferences prefs) {
        prefs.edit().putBoolean(PREF_MIGRATION_DONE, true).apply();
    }

    private static String mapDataTypeToTable(String dataType) {
        if (dataType == null) return null;
        if (dataType.contains("PRODUCT")) return "products";
        if (dataType.contains("LOCATION")) return "storage_locations";
        if (dataType.contains("CATEGORY")) return "categories_definitions";
        if (dataType.contains("LINK")) return "product_category_links";
        if (dataType.contains("SHOPPING")) return "shopping_items";
        return null;
    }

    private static String extractPk(String table, JsonObject payload) {
        if (payload == null) return null;
        
        if ("product_category_links".equals(table)) {
            if (payload.has("product_id_fk") && payload.has("category_id_fk")) {
                return payload.get("product_id_fk").getAsString() + "|" + payload.get("category_id_fk").getAsString();
            }
        } else if ("categories_definitions".equals(table)) {
            if (payload.has("category_id")) return payload.get("category_id").getAsString();
        } else {
            if (payload.has("id")) return payload.get("id").getAsString();
        }
        return null;
    }
}
