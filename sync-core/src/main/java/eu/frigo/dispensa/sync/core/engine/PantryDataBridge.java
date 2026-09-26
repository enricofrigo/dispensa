package eu.frigo.dispensa.sync.core.engine;

import java.util.List;

/**
 * Bridge between the generic FolderSyncEngine and the local database / persistence layer.
 */
public interface PantryDataBridge {

    List<PantryOutboxItem> getPendingOutboxEvents(int dispensaId) throws Exception;

    void markEventsAsSynced(List<String> syncIds) throws Exception;

    String createSnapshotJson(int dispensaId) throws Exception;

    void applySnapshotJson(String snapshotJson, int dispensaId) throws Exception;

    void applyEvent(String action, String payloadJson, long eventTimestamp, int dispensaId) throws Exception;

    String getPantryName(int dispensaId);

    boolean isPantryOwner(int dispensaId, String currentDeviceId);
}

