package eu.frigo.dispensa.sync.webdav.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class WebDavManifest {
    @SerializedName("version")
    public int version = 1;

    @SerializedName("pantryKey")
    public String pantryKey;

    @SerializedName("createdAt")
    public long createdAt;

    @SerializedName("createdByDevice")
    public String createdByDevice;

    @SerializedName("provider")
    public String provider = "webdav";

    @SerializedName("latest_snapshot_id")
    public String latestSnapshotId;

    @SerializedName("last_global_timestamp")
    public long lastGlobalTimestamp;

    @SerializedName("active_event_files")
    public List<String> activeEventFiles = new ArrayList<>();

    @SerializedName("etag")
    public transient String etag;
}
