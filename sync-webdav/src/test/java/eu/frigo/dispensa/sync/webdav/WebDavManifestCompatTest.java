package eu.frigo.dispensa.sync.webdav;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

import eu.frigo.dispensa.sync.webdav.model.WebDavManifest;

public class WebDavManifestCompatTest {

    private final Gson gson = new Gson();

    @Test
    public void testParseLegacyManifestV1_0() {
        String legacyJson = "{" +
                "\"latest_snapshot_id\": \"snap_123.json\"," +
                "\"last_global_timestamp\": 1620000000000," +
                "\"active_event_files\": [\"events/ev1.json\", \"events/ev2.json\"]" +
                "}";

        WebDavManifest manifest = gson.fromJson(legacyJson, WebDavManifest.class);

        assertNotNull(manifest);
        assertEquals("snap_123.json", manifest.latestSnapshotId);
        assertEquals(1620000000000L, manifest.lastGlobalTimestamp);
        assertEquals(2, manifest.activeEventFiles.size());
        assertTrue(manifest.activeEventFiles.contains("events/ev1.json"));
    }

    @Test
    public void testParseHybridManifestV1_1() {
        String hybridJson = "{" +
                "\"latest_snapshot_id\": \"snap_123.json\"," +
                "\"last_global_timestamp\": 1620000000000," +
                "\"active_event_files\": [\"events/ev1.json\"]," +
                "\"latest_sync_blob_id\": \"sync/blob_abc.json\"," +
                "\"sync_format\": \"crdt\"," +
                "\"device_clocks\": {\"device1\": 10, \"device2\": 20}" +
                "}";

        WebDavManifest manifest = gson.fromJson(hybridJson, WebDavManifest.class);

        assertNotNull(manifest);
        // Verify v1.0 fields
        assertEquals("snap_123.json", manifest.latestSnapshotId);
        assertEquals(1, manifest.activeEventFiles.size());
        
        // Verify v1.1 fields
        assertEquals("sync/blob_abc.json", manifest.latestSyncBlobId);
        assertEquals("crdt", manifest.syncFormat);
        assertNotNull(manifest.deviceClocks);
        assertEquals(Long.valueOf(10L), manifest.deviceClocks.get("device1"));
        assertEquals(Long.valueOf(20L), manifest.deviceClocks.get("device2"));
    }

    @Test
    public void testMigrationScenario() {
        // v1.1 device reading a manifest written by a v1.0 device
        String fromOldDevice = "{" +
                "\"active_event_files\": [\"events/old_event.json\"]" +
                "}";
        
        WebDavManifest manifest = gson.fromJson(fromOldDevice, WebDavManifest.class);
        assertEquals(1, manifest.activeEventFiles.size());
        assertEquals("events/old_event.json", manifest.activeEventFiles.get(0));
        assertTrue("New field should be null if not present in old manifest", manifest.latestSyncBlobId == null);

        // v1.1 device reading a manifest written by another v1.1 device
        String fromNewDevice = "{" +
                "\"active_event_files\": [\"events/old_event.json\"]," +
                "\"latest_sync_blob_id\": \"sync/new_blob.json\"" +
                "}";
        manifest = gson.fromJson(fromNewDevice, WebDavManifest.class);
        assertEquals(1, manifest.activeEventFiles.size());
        assertEquals("sync/new_blob.json", manifest.latestSyncBlobId);
    }
}
