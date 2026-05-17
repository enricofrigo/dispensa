package eu.frigo.dispensa.ui.sync;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;

import eu.frigo.dispensa.R;

/**
 * Google Drive sync configuration fragment (play flavor only).
 */
public class GoogleDriveConfigFragment extends Fragment {
    
    private static final String TAG = "GoogleDriveConfigFragment";
    
    private GoogleSignInClient signInClient;
    private Button btnSignIn;
    private Button btnSignOut;
    private Button btnCreateHousehold;
    private Button btnJoinHousehold;
    private TextView textSignedInAs;
    private LinearLayout layoutSignedIn;
    private LinearLayout layoutNotSignedIn;
    private ProgressBar progressSignIn;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_google_drive_config, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        btnSignIn = view.findViewById(R.id.btn_drive_sign_in);
        btnSignOut = view.findViewById(R.id.btn_drive_sign_out);
        btnCreateHousehold = view.findViewById(R.id.btn_create_household);
        btnJoinHousehold = view.findViewById(R.id.btn_join_household);
        textSignedInAs = view.findViewById(R.id.text_signed_in_as);
        layoutSignedIn = view.findViewById(R.id.layout_signed_in);
        layoutNotSignedIn = view.findViewById(R.id.layout_not_signed_in);
        progressSignIn = view.findViewById(R.id.progress_sign_in);
        
        setupGoogleSignIn();
        setupListeners();
        updateUI();
    }
    
    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(new Scope("https://www.googleapis.com/auth/drive.appdata"),
                               new Scope("https://www.googleapis.com/auth/drive.file"))
                .build();
        
        signInClient = GoogleSignIn.getClient(requireActivity(), gso);
    }
    
    private void setupListeners() {
        btnSignIn.setOnClickListener(v -> signIn());
        btnSignOut.setOnClickListener(v -> signOut());
        btnCreateHousehold.setOnClickListener(v -> createHousehold());
        btnJoinHousehold.setOnClickListener(v -> joinHousehold());
    }
    
    private void signIn() {
        progressSignIn.setVisibility(View.VISIBLE);
        btnSignIn.setEnabled(false);
        
        signInClient.signOut().addOnCompleteListener(task -> {
            var signInIntent = signInClient.getSignInIntent();
            startActivityForResult(signInIntent, 1001);
        });
    }
    
    private void signOut() {
        signInClient.signOut().addOnCompleteListener(task -> {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            prefs.edit()
                    .putBoolean("sync_drive_enabled", false)
                    .remove("pref_drive_household_folder_id")
                    .apply();
            updateUI();
            showSuccess("Signed out");
        });
    }
    
    private void createHousehold() {
        showSuccess("Creating household...");
    }
    
    private void joinHousehold() {
        showSuccess("Join household...");
    }
    
    private void updateUI() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireActivity());
        
        if (account != null) {
            textSignedInAs.setText("Signed in as: " + account.getDisplayName());
            layoutSignedIn.setVisibility(View.VISIBLE);
            layoutNotSignedIn.setVisibility(View.GONE);
            progressSignIn.setVisibility(View.GONE);
        } else {
            layoutSignedIn.setVisibility(View.GONE);
            layoutNotSignedIn.setVisibility(View.VISIBLE);
            progressSignIn.setVisibility(View.GONE);
            btnSignIn.setEnabled(true);
        }
    }
    
    private void showSuccess(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
}
