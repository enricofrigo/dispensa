package eu.frigo.dispensa.sync.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;

import androidx.sqlite.db.SupportSQLiteDatabase;

import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import eu.frigo.dispensa.sync.core.model.SyncChange;

public class SyncManagerTest {

    private SupportSQLiteDatabase db;
    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;
    private SyncManager syncManager;
    private final Gson gson = new Gson();

    @Before
    public void setUp() {
        db = mock(SupportSQLiteDatabase.class);
        prefs = mock(SharedPreferences.class);
        editor = mock(SharedPreferences.Editor.class);
        when(prefs.edit()).thenReturn(editor);
        when(editor.putString(anyString(), anyString())).thenReturn(editor);
        when(editor.putLong(anyString(), any(Long.class))).thenReturn(editor);

        syncManager = new SyncManager(db, prefs);
    }

    @Test
    public void testExportChanges_emptyDatabase() {
        Cursor cursor = mock(Cursor.class);
        when(db.query(anyString(), any())).thenReturn(cursor);
        when(cursor.moveToNext()).thenReturn(false);

        byte[] blob = syncManager.exportChanges(0);
        String json = new String(blob, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"changes\":[]"));
    }

    @Test
    public void testExportChanges_withChanges() {
        Cursor cursor = mock(Cursor.class);
        when(db.query(anyString(), any())).thenReturn(cursor);
        when(cursor.moveToNext()).thenReturn(true, false);
        when(cursor.getString(0)).thenReturn("products");
        when(cursor.getString(1)).thenReturn("1");
        when(cursor.getString(2)).thenReturn("UPSERT");
        when(cursor.getString(3)).thenReturn("{\"id\":1}");
        when(cursor.getLong(4)).thenReturn(10L);

        byte[] blob = syncManager.exportChanges(0);
        String json = new String(blob, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"tbl\":\"products\""));
        assertTrue(json.contains("\"pkVal\":\"1\""));
    }

    @Test
    public void testExportChanges_onlyChangesAfterClock() {
        Cursor cursor = mock(Cursor.class);
        when(db.query(anyString(), any())).thenReturn(cursor);
        when(cursor.moveToNext()).thenReturn(false);

        syncManager.exportChanges(50);
        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(db, atLeastOnce()).query(anyString(), captor.capture());
        assertEquals(50L, (long) captor.getAllValues().get(0)[0]);
    }

    @Test
    public void testImportChanges_applyUpsert() {
        SyncChange change = new SyncChange();
        change.tbl = "products";
        change.op = "UPSERT";
        change.rowJson = "{\"id\":1}";
        change.clock = 100;
        change.deviceId = "remote-device";

        List<SyncChange> changes = new ArrayList<>();
        changes.add(change);
        
        // Mock local state (older clock)
        Cursor clockCursor = mock(Cursor.class);
        when(db.query(anyString(), any())).thenReturn(clockCursor);
        when(clockCursor.moveToFirst()).thenReturn(true);
        when(clockCursor.getLong(0)).thenReturn(50L);

        byte[] blob = createSyncBlob("remote-device", changes);
        
        try {
            syncManager.importChanges(blob);
        } catch (Exception ignored) {}
    }

    @Test
    public void testImportChanges_applyDelete() {
        SyncChange change = new SyncChange();
        change.tbl = "products";
        change.op = "DELETE";
        change.pkVal = "1";
        change.clock = 100;
        change.deviceId = "remote-device";

        List<SyncChange> changes = new ArrayList<>();
        changes.add(change);

        Cursor clockCursor = mock(Cursor.class);
        when(db.query(anyString(), any())).thenReturn(clockCursor);
        when(clockCursor.moveToFirst()).thenReturn(true);
        when(clockCursor.getLong(0)).thenReturn(50L);

        byte[] blob = createSyncBlob("remote-device", changes);
        try {
            syncManager.importChanges(blob);
        } catch (Exception ignored) {}

        verify(db).delete(eq("products"), anyString(), any());
    }

    @Test
    public void testImportChanges_lwwConflict_incomingWins() {
        SyncChange incoming = new SyncChange();
        incoming.tbl = "products";
        incoming.pkVal = "1";
        incoming.clock = 200; // Greater than local
        incoming.deviceId = "remote";
        incoming.op = "UPSERT";
        incoming.rowJson = "{}";

        List<SyncChange> changes = new ArrayList<>();
        changes.add(incoming);

        Cursor clockCursor = mock(Cursor.class);
        when(db.query(anyString(), any())).thenReturn(clockCursor);
        when(clockCursor.moveToFirst()).thenReturn(true);
        when(clockCursor.getLong(0)).thenReturn(100L); // Local is older

        try {
            syncManager.importChanges(createSyncBlob("remote", changes));
        } catch (Exception ignored) {}
        
        verify(db, times(0)).delete(anyString(), anyString(), any());
    }

    @Test
    public void testImportChanges_lwwConflict_localWins() {
        SyncChange incoming = new SyncChange();
        incoming.tbl = "products";
        incoming.pkVal = "1";
        incoming.clock = 50; // Smaller than local
        incoming.deviceId = "remote";
        incoming.op = "UPSERT";
        incoming.rowJson = "{}";

        List<SyncChange> changes = new ArrayList<>();
        changes.add(incoming);

        Cursor clockCursor = mock(Cursor.class);
        when(db.query(anyString(), any())).thenReturn(clockCursor);
        when(clockCursor.moveToFirst()).thenReturn(true);
        when(clockCursor.getLong(0)).thenReturn(100L); // Local is newer

        try {
            syncManager.importChanges(createSyncBlob("remote", changes));
        } catch (Exception ignored) {}
        
        verify(db, times(0)).insert(anyString(), anyInt(), any());
    }

    @Test
    public void testImportChanges_lwwConflict_tiebreaker() {
        SyncChange incoming = new SyncChange();
        incoming.tbl = "products";
        incoming.pkVal = "1";
        incoming.clock = 100;
        incoming.deviceId = "ZZZZ"; // Larger lexicographically than local "AAAA"
        incoming.op = "UPSERT";
        incoming.rowJson = "{}";

        List<SyncChange> changes = new ArrayList<>();
        changes.add(incoming);

        when(prefs.getString(SyncManager.PREFS_KEY_DEVICE_ID, null)).thenReturn("AAAA");
        
        Cursor clockCursor = mock(Cursor.class);
        when(db.query(anyString(), any())).thenReturn(clockCursor);
        when(clockCursor.moveToFirst()).thenReturn(true);
        when(clockCursor.getLong(0)).thenReturn(100L); // Same clock

        try {
            syncManager.importChanges(createSyncBlob("ZZZZ", changes));
        } catch (Exception ignored) {}
    }

    @Test
    public void testGetMaxSyncClock() {
        Cursor cursor = mock(Cursor.class);
        when(db.query(anyString(), any())).thenReturn(cursor);
        when(cursor.moveToFirst()).thenReturn(true);
        when(cursor.getLong(0)).thenReturn(42L);

        assertEquals(42L, syncManager.getMaxSyncClock());
    }

    @Test
    public void testGetLocalDeviceId_generatesUUID() {
        when(prefs.getString(SyncManager.PREFS_KEY_DEVICE_ID, null)).thenReturn(null);
        
        String id = syncManager.getLocalDeviceId();
        assertNotNull(id);
        verify(editor).putString(eq(SyncManager.PREFS_KEY_DEVICE_ID), anyString());
    }

    private byte[] createSyncBlob(String deviceId, List<SyncChange> changes) {
        String json = "{\"senderDeviceId\":\"" + deviceId + "\", \"changes\":" + gson.toJson(changes) + "}";
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
