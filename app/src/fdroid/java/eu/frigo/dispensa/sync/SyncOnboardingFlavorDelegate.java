package eu.frigo.dispensa.sync;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.util.function.Consumer;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.sync.core.pairing.PairingPayload;
import io.reactivex.rxjava3.core.Single;

public class SyncOnboardingFlavorDelegate {

    private final AppCompatActivity activity;
    private final Consumer<String> onRawQrScanned;
    private DecoratedBarcodeView barcodeView;

    public SyncOnboardingFlavorDelegate(AppCompatActivity activity, Consumer<String> onRawQrScanned) {
        this.activity = activity;
        this.onRawQrScanned = onRawQrScanned;
    }

    public void initScanner(boolean hasInitialQrData) {
        barcodeView = activity.findViewById(R.id.zxing_barcode_scanner);
        if (!hasInitialQrData && barcodeView != null) {
            barcodeView.setVisibility(View.VISIBLE);
            barcodeView.setStatusText(activity.getString(R.string.add_product_camera_preview_hint));
            barcodeView.decodeSingle(result -> {
                String rawData = result.getText();
                Log.d("SyncOnboarding", "QR scansionato con successo");
                activity.runOnUiThread(() -> onRawQrScanned.accept(rawData));
            });
        }
    }

    public void onResume(boolean hasScannedData) {
        if (!hasScannedData && barcodeView != null) {
            barcodeView.resume();
        }
    }

    public void onPause() {
        if (barcodeView != null) {
            barcodeView.pause();
        }
    }

    public void onDestroy() {
        // No-op for ZXing DecoratedBarcodeView
    }

    public void hideScanner() {
        if (barcodeView != null) {
            barcodeView.pause();
            barcodeView.setVisibility(View.GONE);
        }
    }

    public boolean isGDriveShare(JoinedPantryConfig config) {
        return false;
    }

    public void setupGDriveShareUI(Dispensa disp, JoinedPantryConfig config, ImageView qrView, Button shareBtn) {
        // Not supported on F-Droid flavor
    }

    public Single<PairingPayload> validateAndRegisterGDriveJoin(PairingPayload payload) {
        return Single.just(payload);
    }

    public void performGDriveEagerSync(JoinedPantryConfig config, int dispensaId, String myDeviceId, String myDeviceName) {
        // Not supported on F-Droid flavor
    }

    public boolean handleJoinError(Throwable throwable, Runnable retryAction) {
        return false;
    }
}
