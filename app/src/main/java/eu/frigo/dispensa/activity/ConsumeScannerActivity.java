package eu.frigo.dispensa.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.scanner.ConsumeScannerDelegate;

/**
 * Unified ConsumeScannerActivity in src/main delegating camera/OCR scanning
 * to the flavor-specific ConsumeScannerDelegate (ML Kit in play, ZXing in fdroid).
 */
public class ConsumeScannerActivity extends AppCompatActivity {

    public static final String EXTRA_SCANNED_BARCODE = "SCANNED_BARCODE_DATA";
    public static final String EXTRA_SCANNED_DATE_MATCH = "SCANNED_DATE_MATCH";

    private ConsumeScannerDelegate scannerDelegate;
    private boolean isScanned = false;

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    if (scannerDelegate != null) {
                        scannerDelegate.startScanning();
                    }
                } else {
                    Toast.makeText(this, getString(R.string.camera_permission_denied), Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consume_scanner);

        scannerDelegate = new ConsumeScannerDelegate(this, this::returnResult);
        scannerDelegate.onCreate();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            scannerDelegate.startScanning();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (scannerDelegate != null) {
            scannerDelegate.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (scannerDelegate != null) {
            scannerDelegate.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scannerDelegate != null) {
            scannerDelegate.onDestroy();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (scannerDelegate != null && scannerDelegate.onKeyDown(keyCode, event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void returnResult(String barcode, Long expiryDate) {
        if (isScanned) return;
        isScanned = true;
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_SCANNED_BARCODE, barcode);
        if (expiryDate != null) {
            resultIntent.putExtra(EXTRA_SCANNED_DATE_MATCH, expiryDate);
        }
        setResult(RESULT_OK, resultIntent);
        finish();
    }
}
