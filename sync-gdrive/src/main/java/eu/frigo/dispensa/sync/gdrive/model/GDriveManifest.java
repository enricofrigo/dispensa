package eu.frigo.dispensa.sync.gdrive.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class GDriveManifest {
    @SerializedName("version")
    public int version = 1;

    @SerializedName("pantry_key")
    public String pantryKey;

    @SerializedName("created_at")
    public long createdAt;

    @SerializedName("created_by_device")
    public String createdByDevice;

    @SerializedName("latest_snapshot_id")
    public String latestSnapshotId;

    @SerializedName("active_event_files")
    public List<String> activeEventFiles = new ArrayList<>();

    @SerializedName("last_global_timestamp")
    public long lastGlobalTimestamp;

    // Transient field to store Google Drive File ID
    public transient String fileId;
}
