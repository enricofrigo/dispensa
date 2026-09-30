package eu.frigo.dispensa.sync.gdrive.client;

import java.io.IOException;

public interface GDriveTokenProvider {
    String getAccessToken() throws IOException;
    void invalidateToken(String token);
}
