package eu.frigo.dispensa.sync.drive;

import android.accounts.Account;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages the creation, sharing, and joining of shared household folders on Google Drive.
 */
public class HouseholdManager {
    private static final String TAG = "HouseholdManager";

    public static final String PREF_HOUSEHOLD_FOLDER_ID = "sync_drive_household_folder_id";
    public static final String PREF_HOUSEHOLD_FOLDER_NAME = "sync_drive_household_folder_name";

    /**
     * Creates a new shared folder for household sync.
     */
    public static String createHousehold(Context context, Account account, String householdName) throws IOException {
        Drive drive = buildDrive(context, account);
        
        File metadata = new File();
        metadata.setName("Dispensa " + sanitizeForFilename(householdName));
        metadata.setMimeType("application/vnd.google-apps.folder");

        File folder = drive.files().create(metadata).setFields("id").execute();
        String folderId = folder.getId();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit()
                .putString(PREF_HOUSEHOLD_FOLDER_ID, folderId)
                .putString(PREF_HOUSEHOLD_FOLDER_NAME, householdName)
                .apply();

        return folderId;
    }

    /**
     * Grants "writer" access to the household folder for another user.
     */
    public static void grantAccess(Drive drive, String folderId, String email) throws IOException {
        Permission permission = new Permission();
        permission.setType("user");
        permission.setRole("writer");
        permission.setEmailAddress(email);

        drive.permissions().create(folderId, permission).execute();
        Log.i(TAG, "Granted writer access to: " + email);
    }

    /**
     * Verifies access to a shared folder and saves it as the active household.
     */
    public static boolean verifyAndJoin(Context context, Account account, String folderId) {
        try {
            Drive drive = buildDrive(context, account);
            File folder = drive.files().get(folderId).setFields("id, name").execute();
            
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit()
                    .putString(PREF_HOUSEHOLD_FOLDER_ID, folderId)
                    .putString(PREF_HOUSEHOLD_FOLDER_NAME, folder.getName().replace("Dispensa ", ""))
                    .apply();
            return true;
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 403 || e.getStatusCode() == 404) {
                return false;
            }
            Log.e(TAG, "Error joining household", e);
            return false;
        } catch (IOException e) {
            Log.e(TAG, "Error joining household", e);
            return false;
        }
    }

    public static String getFolderId(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_HOUSEHOLD_FOLDER_ID, null);
    }

    public static void clearHouseholdFolderId(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .remove(PREF_HOUSEHOLD_FOLDER_ID)
                .remove(PREF_HOUSEHOLD_FOLDER_NAME)
                .apply();
    }

    public static String generateJoinDeepLink(String folderId) {
        return "dispensa://household?folderId=" + folderId;
    }

    /**
     * Builds a Drive service instance with necessary scopes for household operations.
     */
    public static Drive buildDrive(Context context, Account account) {
        Set<String> scopes = new HashSet<>(Arrays.asList(DriveScopes.DRIVE_APPDATA, DriveScopes.DRIVE_FILE));
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(context, scopes);
        credential.setSelectedAccount(account);

        return new Drive.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential)
                .setApplicationName("Dispensa")
                .build();
    }

    private static String sanitizeForFilename(String name) {
        if (name == null || name.isEmpty()) return "household";
        String sanitized = name.replaceAll("\\s+", "-").replaceAll("[^a-zA-Z0-9-]", "");
        if (sanitized.length() > 30) sanitized = sanitized.substring(0, 30);
        return sanitized.isEmpty() ? "household" : sanitized;
    }
}
