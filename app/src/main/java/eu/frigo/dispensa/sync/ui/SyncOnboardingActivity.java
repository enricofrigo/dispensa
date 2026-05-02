package eu.frigo.dispensa.sync.ui;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.textfield.TextInputLayout;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.util.Objects;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.pairing.OnboardingCoordinator;
import eu.frigo.dispensa.sync.core.pairing.PairingPayload;
import eu.frigo.dispensa.sync.core.pairing.PairingPayloadCodecImpl;
import eu.frigo.dispensa.sync.webdav.WebDavConfig;
import eu.frigo.dispensa.sync.webdav.WebDavPairingHandler;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class SyncOnboardingActivity extends AppCompatActivity {
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_SHARE = "share";
    public static final String MODE_JOIN = "join";

    private String currentPairingCode;
    private String scannedQrData;
    private DecoratedBarcodeView barcodeView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_onboarding);

        String mode = getIntent().getStringExtra(EXTRA_MODE);
        if (MODE_SHARE.equals(mode)) {
            setupShareMode();
        } else {
            setupJoinMode();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (barcodeView != null) {
            barcodeView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (barcodeView != null) {
            barcodeView.pause();
        }
    }

    private void setupShareMode() {
        TextView instruction = findViewById(R.id.tv_onboarding_instruction);
        instruction.setText(R.string.share_pantry);
        
        ImageView qrView = findViewById(R.id.iv_qr_code);
        TextView codeView = findViewById(R.id.tv_pairing_code);
        qrView.setVisibility(View.VISIBLE);
        codeView.setVisibility(View.VISIBLE);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String url = prefs.getString(SyncManager.KEY_WEBDAV_URL, "");
        String user = prefs.getString(SyncManager.KEY_WEBDAV_USER, "");
        String pass = prefs.getString(SyncManager.KEY_WEBDAV_PASS, "");
        String path = prefs.getString(SyncManager.KEY_WEBDAV_PATH, SyncManager.DEFAULT_PATH);

        if (url.isEmpty() || user.isEmpty()) {
            Toast.makeText(this, "Configura prima il sync nelle impostazioni", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        currentPairingCode = OnboardingCoordinator.generatePairingCode();
        codeView.setText(currentPairingCode);

        try {
            // 1. Prepare WebDAV config
            WebDavConfig config = new WebDavConfig(url, user, pass, path);
            
            // 2. Create encrypted payload
            String deviceName = android.os.Build.MODEL;
            PairingPayload payload = WebDavPairingHandler.createPayload(deviceName, config);
            
            // 3. Encode with pairing code
            String cleanCode = currentPairingCode.replace("-", "").toUpperCase();
            PairingPayloadCodecImpl codec = new PairingPayloadCodecImpl(cleanCode);
            String wireData = codec.encode(payload);
            
            // 4. Generate QR
            Bitmap qrBitmap = QrCodeGenerator.generate(wireData, 512);
            qrView.setImageBitmap(qrBitmap);
            
            Log.d("SyncOnboarding", "QR generato con successo per il codice: " + currentPairingCode);
        } catch (Exception e) {
            Log.e("SyncOnboarding", "Errore nella generazione del QR", e);
            Toast.makeText(this, "Errore generazione QR: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupJoinMode() {
        TextView instruction = findViewById(R.id.tv_onboarding_instruction);
        instruction.setText(R.string.join_pantry);

        barcodeView = findViewById(R.id.zxing_barcode_scanner);
        TextInputLayout til = findViewById(R.id.til_pairing_code);
        com.google.android.material.textfield.TextInputEditText etPairingCode = findViewById(R.id.et_pairing_code);
        Button confirm = findViewById(R.id.btn_confirm_onboarding);

        barcodeView.setVisibility(View.VISIBLE);
        barcodeView.setStatusText(getString(R.string.add_product_camera_preview_hint));

        barcodeView.decodeSingle(result -> {
            scannedQrData = result.getText();
            Log.d("SyncOnboarding", "QR scansionato con successo");
            runOnUiThread(() -> {
                barcodeView.setVisibility(View.GONE);
                til.setVisibility(View.VISIBLE);
                confirm.setVisibility(View.VISIBLE);
                Toast.makeText(this, "QR scansionato. Inserisci il codice di accoppiamento.", Toast.LENGTH_SHORT).show();
            });
        });
        
        confirm.setOnClickListener(v -> {
            String pairingCode = Objects.requireNonNull(etPairingCode.getText()).toString().trim();
            if (pairingCode.isEmpty()) {
                etPairingCode.setError("Codice richiesto");
                return;
            }

            if (scannedQrData == null) {
                Toast.makeText(this, "Scansiona prima il QR Code", Toast.LENGTH_SHORT).show();
                return;
            }

            confirm.setEnabled(false);
            new OnboardingCoordinator().joinPantry(pairingCode, scannedQrData)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(payload -> {
                        eu.frigo.dispensa.sync.core.engine.SyncCoordinatorImpl.getInstance(this).applyOnboarding(payload);
                        Toast.makeText(this, R.string.sync_pairing_success, Toast.LENGTH_LONG).show();
                        finish();
                    }, throwable -> {
                        confirm.setEnabled(true);
                        Log.e("SyncOnboarding", "Errore decriptazione pairing", throwable);
                        Toast.makeText(this, R.string.sync_pairing_error, Toast.LENGTH_LONG).show();
                    });
        });
    }
}
