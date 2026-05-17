package eu.frigo.dispensa.ui.sync;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Stub Google Drive sync configuration fragment for fdroid flavor.
 */
public class GoogleDriveConfigFragment extends Fragment {
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        TextView textView = new TextView(getContext());
        textView.setText("Google Drive sync is not available in the F-Droid version.");
        textView.setPadding(32, 32, 32, 32);
        return textView;
    }
}
