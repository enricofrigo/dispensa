package eu.frigo.dispensa.sync.gdrive.auth;

import android.content.Intent;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class GDriveAuthManagerTest {

    @Test
    public void testExtractConsentIntent_fromRecoverableException() {
        Intent expectedIntent = new Intent("test_consent_action");
        GDriveRecoverableAuthException ex = new GDriveRecoverableAuthException("Consent required", expectedIntent, null);

        Intent extracted = GDriveAuthManager.extractConsentIntent(ex);
        Assert.assertNotNull(extracted);
        Assert.assertEquals("test_consent_action", extracted.getAction());
    }

    @Test
    public void testExtractConsentIntent_fromNestedException() {
        Intent expectedIntent = new Intent("nested_consent_action");
        GDriveRecoverableAuthException rootCause = new GDriveRecoverableAuthException("Nested", expectedIntent, null);
        Exception wrapper = new RuntimeException("Wrapper", rootCause);

        Intent extracted = GDriveAuthManager.extractConsentIntent(wrapper);
        Assert.assertNotNull(extracted);
        Assert.assertEquals("nested_consent_action", extracted.getAction());
    }

    @Test
    public void testExtractConsentIntent_returnsNullForOtherExceptions() {
        Exception normalEx = new IllegalArgumentException("Not an auth exception");
        Assert.assertNull(GDriveAuthManager.extractConsentIntent(normalEx));
        Assert.assertNull(GDriveAuthManager.extractConsentIntent(null));
    }

    @Test
    public void testGoogleSignInOptionsUsesDriveFileScopeInsteadOfFullDriveScope() {
        com.google.android.gms.auth.api.signin.GoogleSignInOptions options = GDriveAuthManager.getGoogleSignInOptions();
        Assert.assertNotNull(options);
        com.google.android.gms.common.api.Scope[] scopes = options.getScopeArray();
        boolean hasDriveFile = false;
        boolean hasFullDrive = false;
        for (com.google.android.gms.common.api.Scope s : scopes) {
            if (GDriveAuthManager.DRIVE_FILE_SCOPE.equals(s.getScopeUri())) {
                hasDriveFile = true;
            }
            if (GDriveAuthManager.DRIVE_SCOPE.equals(s.getScopeUri())) {
                hasFullDrive = true;
            }
        }
        Assert.assertTrue("Must request narrow drive.file scope", hasDriveFile);
        Assert.assertFalse("Must NOT request restricted full drive scope", hasFullDrive);
    }
}
