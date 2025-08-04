package eu.frigo.dispensa.ui;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;

import eu.frigo.dispensa.R;
import java.util.Objects;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar_settings);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(getString(R.string.action_settings));


        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }
        TextView versionFooterTextView = findViewById(R.id.app_version_footer_text_view);
        setAppVersionFooter(versionFooterTextView);
    }
    private void setAppVersionFooter(TextView textView) {
        if (textView == null) return;

        String versionName = getAppVersionName(this);
        long versionCode = getAppVersionCode(this);

        if (!getString(R.string.not_defined).equals(versionName) && versionCode != -1L) {
            textView.setText(getString(R.string.version_format_detailed, versionName, String.valueOf(versionCode)));
        } else {
            textView.setText(getString(R.string.version_not_available));
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private String getAppVersionName(Context context) {
        try {
            PackageInfo packageInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.PackageInfoFlags.of(0));
            } else {
                PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                packageInfo = pi;
            }
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("SettingsActivity", "NameNotFoundException while getting versionName", e);
            return getString(R.string.not_defined);
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private long getAppVersionCode(Context context) {
        try {
            PackageInfo packageInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.PackageInfoFlags.of(0));
            } else {
                PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                packageInfo = pi;
            }
            return packageInfo.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("SettingsActivity", "NameNotFoundException while getting versionCode", e);
            return -1L;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}