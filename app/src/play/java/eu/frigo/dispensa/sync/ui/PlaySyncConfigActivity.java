package eu.frigo.dispensa.sync.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import eu.frigo.dispensa.R;

public class PlaySyncConfigActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play_sync_config);

        findViewById(R.id.btn_choose_webdav).setOnClickListener(v -> {
            startActivity(new Intent(this, SyncConfigActivity.class));
            finish();
        });

        findViewById(R.id.btn_choose_gdrive).setOnClickListener(v -> {
            startActivity(new Intent(this, GDriveSyncConfigActivity.class));
            finish();
        });
    }
}
