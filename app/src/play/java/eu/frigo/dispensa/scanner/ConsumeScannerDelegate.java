package eu.frigo.dispensa.scanner;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.KeyEvent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.util.DateConverter;
import eu.frigo.dispensa.util.ExpiryDateParser;

public class ConsumeScannerDelegate {

    private final AppCompatActivity activity;
    private final BiConsumer<String, Long> resultCallback;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;
    private TextRecognizer textRecognizer;

    private boolean isScanned = false;
    private boolean isTextScanningMode = false;
    private String scannedBarcode = null;
    private Set<Long> targetExpiryDates = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;
    private static final int SEARCH_FOR_EXPIRY_DATE_TIMEOUT_MS = 5000;

    public ConsumeScannerDelegate(AppCompatActivity activity, BiConsumer<String, Long> resultCallback) {
        this.activity = activity;
        this.resultCallback = resultCallback;
    }

    public void onCreate() {
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E,
                        Barcode.FORMAT_QR_CODE,
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_CODABAR)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    public void startScanning() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(activity);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreviewAndAnalysis(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(activity, activity.getString(R.string.err_camera), Toast.LENGTH_SHORT).show();
                Log.e("ConsumeScanner", "Errore nell'avvio della fotocamera", e);
            }
        }, ContextCompat.getMainExecutor(activity));
    }

    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private void bindPreviewAndAnalysis(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        androidx.camera.view.PreviewView previewView = activity.findViewById(R.id.previewViewConsume);
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            if (isScanned) {
                imageProxy.close();
                return;
            }

            android.media.Image mediaImage = imageProxy.getImage();
            if (mediaImage != null) {
                InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

                if (!isTextScanningMode) {
                    barcodeScanner.process(image)
                            .addOnSuccessListener(barcodes -> {
                                if (!barcodes.isEmpty() && !isTextScanningMode && !isScanned) {
                                    for (Barcode barcode : barcodes) {
                                        String rawValue = barcode.getRawValue();
                                        isTextScanningMode = true;
                                        scannedBarcode = rawValue;

                                        timeoutRunnable = () -> deliverResult(scannedBarcode, null);
                                        handler.postDelayed(timeoutRunnable, SEARCH_FOR_EXPIRY_DATE_TIMEOUT_MS);

                                        AppDatabase.databaseWriteExecutor.execute(() -> {
                                            List<Product> products = AppDatabase.getDatabase(activity.getApplicationContext())
                                                    .productDao().getProductsByBarcode(rawValue);
                                            Set<Long> dates = new HashSet<>();
                                            for (Product p : products) {
                                                if (p.getExpiryDate() != null) {
                                                    dates.add(p.getExpiryDate());
                                                }
                                            }
                                            targetExpiryDates = dates;
                                            if (products.size() <= 1 || targetExpiryDates.isEmpty()) {
                                                handler.removeCallbacks(timeoutRunnable);
                                                activity.runOnUiThread(() -> deliverResult(scannedBarcode, null));
                                            }
                                        });
                                        break;
                                    }
                                }
                            })
                            .addOnFailureListener(e -> Log.e("ConsumeScanner", "Errore scanner barcode", e))
                            .addOnCompleteListener(task -> imageProxy.close());
                } else {
                    textRecognizer.process(image)
                            .addOnSuccessListener(text -> {
                                if (isScanned) return;
                                String parsedDateStr = ExpiryDateParser.parseExpiryDate(text.getText());
                                if (parsedDateStr != null) {
                                    Long parsedDate = DateConverter.parseDisplayDateToTimestampMs(parsedDateStr);
                                    if (parsedDate != null && targetExpiryDates.contains(parsedDate)) {
                                        handler.removeCallbacks(timeoutRunnable);
                                        deliverResult(scannedBarcode, parsedDate);
                                    }
                                }
                            })
                            .addOnFailureListener(e -> Log.e("ConsumeScanner", "Errore text recognition", e))
                            .addOnCompleteListener(task -> imageProxy.close());
                }
            } else {
                imageProxy.close();
            }
        });

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(activity, cameraSelector, preview, imageAnalysis);
        } catch (Exception exc) {
            Log.e("ConsumeScanner", "Use case binding fallito", exc);
        }
    }

    private void deliverResult(String barcode, Long expiryDate) {
        if (isScanned) return;
        isScanned = true;
        resultCallback.accept(barcode, expiryDate);
    }

    public void onResume() {}

    public void onPause() {}

    public void onDestroy() {
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (barcodeScanner != null) {
            barcodeScanner.close();
        }
        if (textRecognizer != null) {
            textRecognizer.close();
        }
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
        }
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }
}
