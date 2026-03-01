package eu.frigo.dispensa.activity;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.util.Log;

import java.util.Objects;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.ui.SettingsFragment;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        View rootLayout = findViewById(R.id.settings_root_layout);
        Toolbar toolbar = findViewById(R.id.toolbar_settings);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(getString(R.string.action_settings));

        TextView versionFooterTextView = findViewById(R.id.app_version_footer_text_view);
        setAppVersionFooter(versionFooterTextView);

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);

            if (versionFooterTextView != null) {
                versionFooterTextView.setPadding(
                        versionFooterTextView.getPaddingLeft(),
                        versionFooterTextView.getPaddingTop(),
                        versionFooterTextView.getPaddingRight(),
                        systemBars.bottom);
            }
            return windowInsets;
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }
    }

    private void setAppVersionFooter(TextView textView) {
        if (textView == null)
            return;

        String versionName = getAppVersionName(this);
        long versionCode = getAppVersionCode(this);

        if (!getString(R.string.not_defined).equals(versionName) && versionCode != -1L) {
            textView.setText(getString(R.string.version_format_detailed, versionName, String.valueOf(versionCode)));
        } else {
            textView.setText(getString(R.string.version_not_available));
        }
    }

    private String getAppVersionName(Context context) {
        try {
            PackageInfo packageInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(),
                        PackageManager.PackageInfoFlags.of(0));
            } else {
                packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            }
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("SettingsActivity", "NameNotFoundException while getting versionName", e);
            return getString(R.string.not_defined);
        }
    }

    private long getAppVersionCode(Context context) {
        try {
            PackageInfo packageInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(),
                        PackageManager.PackageInfoFlags.of(0));
            } else {
                packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            }
            return PackageInfoCompat.getLongVersionCode(packageInfo);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("SettingsActivity", "NameNotFoundException while getting versionCode", e);
            return -1L;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            navigateToListAndFinish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        navigateToListAndFinish();
        return true;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        navigateToListAndFinish();
    }

    private void navigateToListAndFinish() {
        Intent intent = new Intent(this, MainActivity.class); // oppure la tua activity della lista
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}