package eu.frigo.dispensa.sync.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputLayout;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.sync.core.pairing.OnboardingCoordinator;
import eu.frigo.dispensa.sync.core.pairing.PairingPayload;

public class SyncOnboardingActivity extends AppCompatActivity {
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_SHARE = "share";
    public static final String MODE_JOIN = "join";

    private String currentPairingCode;
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

    private void setupShareMode() {
        TextView instruction = findViewById(R.id.tv_onboarding_instruction);
        instruction.setText(R.string.share_pantry);
        
        ImageView qrView = findViewById(R.id.iv_qr_code);
        TextView codeView = findViewById(R.id.tv_pairing_code);
        qrView.setVisibility(View.VISIBLE);
        codeView.setVisibility(View.VISIBLE);

        currentPairingCode = OnboardingCoordinator.generatePairingCode();
        codeView.setText(currentPairingCode);

        // In a real implementation, we would generate the QR here
        // Bitmap qr = QrCodeGenerator.generate(encryptedPayload);
        // qrView.setImageBitmap(qr);
    }

    private void setupJoinMode() {
        TextView instruction = findViewById(R.id.tv_onboarding_instruction);
        instruction.setText(R.string.join_pantry);

        barcodeView = findViewById(R.id.zxing_barcode_scanner);
        TextInputLayout til = findViewById(R.id.til_pairing_code);
        Button confirm = findViewById(R.id.btn_confirm_onboarding);

        barcodeView.setVisibility(View.VISIBLE);
        til.setVisibility(View.VISIBLE);
        confirm.setVisibility(View.VISIBLE);

        barcodeView.decodeSingle(result -> {
            // Handle QR scan
        });
        
        confirm.setOnClickListener(v -> {
            // Decrypt and apply
        });
    }
}
