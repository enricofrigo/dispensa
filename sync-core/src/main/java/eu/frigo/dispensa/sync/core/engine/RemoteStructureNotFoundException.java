package eu.frigo.dispensa.sync.core.engine;

import java.io.IOException;

/**
 * Thrown when required remote pantry folders or files (root, devices/, events/, snapshots/, manifest.json)
 * are missing or were deleted on the remote storage.
 */
public class RemoteStructureNotFoundException extends IOException {
    private final String missingPath;

    public RemoteStructureNotFoundException(String missingPath, String message) {
        super(message);
        this.missingPath = missingPath;
    }

    public String getMissingPath() {
        return missingPath;
    }
}
