package eu.frigo.dispensa.scanner;

import android.view.KeyEvent;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.util.function.BiConsumer;

import eu.frigo.dispensa.R;

public class ConsumeScannerDelegate {

    private final AppCompatActivity activity;
    private final BiConsumer<String, Long> resultCallback;
    private DecoratedBarcodeView barcodeView;
    private boolean isScanned = false;

    public ConsumeScannerDelegate(AppCompatActivity activity, BiConsumer<String, Long> resultCallback) {
        this.activity = activity;
        this.resultCallback = resultCallback;
    }

    public void onCreate() {
        barcodeView = activity.findViewById(R.id.barcode_scanner);
    }

    public void startScanning() {
        if (barcodeView == null) return;
        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (result.getText() != null && !isScanned) {
                    isScanned = true;
                    resultCallback.accept(result.getText(), null);
                }
            }
        });
    }

    public void onResume() {
        if (barcodeView != null) {
            barcodeView.resume();
        }
    }

    public void onPause() {
        if (barcodeView != null) {
            barcodeView.pause();
        }
    }

    public void onDestroy() {}

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return barcodeView != null && barcodeView.onKeyDown(keyCode, event);
    }
}
