package eu.frigo.dispensa.sync.gdrive.auth;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;

import java.io.IOException;

import eu.frigo.dispensa.sync.gdrive.client.GDriveTokenProvider;
import io.reactivex.rxjava3.core.Completable;

public class GDriveAuthManager implements GDriveTokenProvider {
    private static final String TAG = "GDriveAuthManager";
    public static final String DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file";
    public static final String PREF_KEY_GDRIVE_ACCOUNT_EMAIL = "sync_gdrive_account_email";
    public static final String PREF_KEY_GDRIVE_ROOT_FOLDER_ID = "sync_gdrive_root_folder_id";

    private final Context context;

    public GDriveAuthManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static GoogleSignInOptions getGoogleSignInOptions() {
        return new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(DRIVE_FILE_SCOPE))
                .build();
    }

    public static GoogleSignInClient getGoogleSignInClient(Context context) {
        return GoogleSignIn.getClient(context, getGoogleSignInOptions());
    }

    public boolean isSignedIn() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        return account != null && GoogleSignIn.hasPermissions(account, new Scope(DRIVE_FILE_SCOPE));
    }

    @Nullable
    public GoogleSignInAccount getAccount() {
        return GoogleSignIn.getLastSignedInAccount(context);
    }

    @Nullable
    public String getAccountEmail() {
        GoogleSignInAccount account = getAccount();
        if (account != null && account.getEmail() != null) {
            return account.getEmail();
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(PREF_KEY_GDRIVE_ACCOUNT_EMAIL, null);
    }

    public void saveAccountEmail(@Nullable String email) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(PREF_KEY_GDRIVE_ACCOUNT_EMAIL, email).apply();
    }

    @Override
    public String getAccessToken() throws IOException {
        GoogleSignInAccount account = getAccount();
        if (account == null || account.getAccount() == null) {
            throw new IOException("No Google account signed in");
        }
        try {
            return GoogleAuthUtil.getToken(context, account.getAccount(), "oauth2:" + DRIVE_FILE_SCOPE);
        } catch (Exception e) {
            Log.e(TAG, "Error acquiring Google OAuth token", e);
            throw new IOException("Failed to obtain OAuth token: " + e.getMessage(), e);
        }
    }

    @Override
    public void invalidateToken(String token) {
        if (token != null && !token.isEmpty()) {
            try {
                GoogleAuthUtil.clearToken(context, token);
            } catch (Exception e) {
                Log.w(TAG, "Error clearing invalidated Google OAuth token", e);
            }
        }
    }

    public Completable signOut() {
        return Completable.fromAction(() -> {
            saveAccountEmail(null);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit().remove(PREF_KEY_GDRIVE_ROOT_FOLDER_ID).apply();
            getGoogleSignInClient(context).signOut();
        });
    }
}
