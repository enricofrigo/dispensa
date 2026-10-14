package eu.frigo.dispensa.sync.gdrive.auth;

import android.content.Intent;
import java.io.IOException;

public class GDriveRecoverableAuthException extends IOException {
    private final Intent consentIntent;

    public GDriveRecoverableAuthException(String message, Intent consentIntent, Throwable cause) {
        super(message, cause);
        this.consentIntent = consentIntent;
    }

    public Intent getConsentIntent() {
        return consentIntent;
    }
}
