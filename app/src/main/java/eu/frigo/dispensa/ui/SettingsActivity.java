package eu.frigo.dispensa.ui; // o il tuo package

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Usa androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat;
import androidx.media3.common.util.Log;

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
        getSupportActionBar().setTitle("Impostazioni");


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
        long versionCode = getAppVersionCode(this); // Opzionale

        if (!"N/D".equals(versionName) && versionCode != -1L) {
            textView.setText(getString(R.string.version_format_detailed, versionName, String.valueOf(versionCode)));
            // Esempio con string resource: Versione: %1$s (Build: %2$s)
        } else {
            textView.setText(getString(R.string.version_not_available));
            // Esempio con string resource: Versione: N/D
        }
    }
    private String getAppVersionName(Context context) {
        try {
            PackageInfo packageInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.PackageInfoFlags.of(0));
            } else {
                @SuppressWarnings("deprecation")
                PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                packageInfo = pi;
            }
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("SettingsActivity", "NameNotFoundException while getting versionName", e);
            return "N/D";
        }
    }

    private long getAppVersionCode(Context context) {
        try {
            PackageInfo packageInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.PackageInfoFlags.of(0));
            } else {
                @SuppressWarnings("deprecation")
                PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                packageInfo = pi;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return packageInfo.getLongVersionCode();
            } else {
                @SuppressWarnings("deprecation")
                long vc = packageInfo.versionCode;
                return vc;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("SettingsActivity", "NameNotFoundException while getting versionCode", e);
            return -1L;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Gestisce il click sul pulsante "indietro" della toolbar
        if (item.getItemId() == android.R.id.home) {
            onBackPressed(); // O NavUtils.navigateUpFromSameTask(this); se hai una gerarchia di parent definita
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