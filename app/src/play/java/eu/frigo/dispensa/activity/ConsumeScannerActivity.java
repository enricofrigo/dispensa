package eu.frigo.dispensa.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
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

import android.os.Handler;
import android.os.Looper;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.util.DateConverter;

public class ConsumeScannerActivity extends AppCompatActivity {

    public static final String EXTRA_SCANNED_BARCODE = "SCANNED_BARCODE_DATA";
    public static final String EXTRA_SCANNED_DATE_MATCH = "SCANNED_DATE_MATCH";

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ExecutorService cameraExecutor;
    private com.google.mlkit.vision.barcode.BarcodeScanner barcodeScanner;
    private TextRecognizer textRecognizer;
    
    private boolean isCameraPermissionGranted;
    private boolean isScanned = false; // Prevent multiple scans
    
    private boolean isTextScanningMode = false;
    private String scannedBarcode = null;
    private Set<Long> targetExpiryDates = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;
    private int searchForExpirydateTimeout = 5000;

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    isCameraPermissionGranted = true;
                    startCamera();
                } else {
                    isCameraPermissionGranted = false;
                    Toast.makeText(this, getString(R.string.camera_permission_denied), Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consume_scanner);

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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            isCameraPermissionGranted = true;
            startCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        if (!isCameraPermissionGranted) return;

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreviewAndAnalysis(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, getString(R.string.err_camera), Toast.LENGTH_SHORT).show();
                Log.e("ConsumeScanner", "Errore nell'avvio della fotocamera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    private void bindPreviewAndAnalysis(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        androidx.camera.view.PreviewView previewView = findViewById(R.id.previewViewConsume);
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
                                        Log.d("ConsumeScanner", "Barcode letto: " + rawValue);
                                        isTextScanningMode = true;
                                        scannedBarcode = rawValue;
                                        
                                        timeoutRunnable = () -> {
                                            Log.d("ConsumeScanner", "Timeout "+searchForExpirydateTimeout+" secondi scaduto, restituisco solo barcode.");
                                            returnResult(scannedBarcode, null);
                                        };
                                        handler.postDelayed(timeoutRunnable, searchForExpirydateTimeout);
                                        Log.d("ConsumeScanner", "Timer di "+searchForExpirydateTimeout+" secondi avviato per la lettura data.");
                                        
                                        AppDatabase.databaseWriteExecutor.execute(() -> {
                                            List<Product> products = AppDatabase.getDatabase(getApplicationContext())
                                                    .productDao().getProductsByBarcode(rawValue);
                                            Set<Long> dates = new HashSet<>();
                                            for (Product p : products) {
                                                if (p.getExpiryDate() != null) {
                                                    dates.add(p.getExpiryDate());
                                                }
                                            }
                                            targetExpiryDates = dates;
                                            Log.d("ConsumeScanner", "Trovati " + products.size() + " prodotti. Date target uniche: " + targetExpiryDates.size());
                                            
                                            // Se non ci sono multiple date da risolvere o non ci sono prodotti
                                            if (products.size() <= 1 || targetExpiryDates.isEmpty()) {
                                                Log.d("ConsumeScanner", "Non ci sono abbastanza date da confrontare, uscita immediata iterazione testo.");
                                                handler.removeCallbacks(timeoutRunnable);
                                                runOnUiThread(() -> returnResult(scannedBarcode, null));
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
                                String resultText = text.getText();
                                String parsedDateStr = eu.frigo.dispensa.util.ExpiryDateParser.parseExpiryDate(resultText);
                                if (parsedDateStr != null) {
                                    Long parsedDate = DateConverter.parseDisplayDateToTimestampMs(parsedDateStr);
                                    if (parsedDate != null) {
                                        Log.d("ConsumeScanner", "Data estratta dal testo: " + parsedDateStr + " -> timestamp: " + parsedDate);
                                        if (targetExpiryDates.contains(parsedDate)) {
                                            Log.d("ConsumeScanner", "MATCH! Data trovata e presente tra i prodotti: " + parsedDate);
                                            handler.removeCallbacks(timeoutRunnable);
                                            returnResult(scannedBarcode, parsedDate);
                                            return;
                                        }
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
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        } catch (Exception exc) {
            Log.e("ConsumeScanner", "Use case binding fallito", exc);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
