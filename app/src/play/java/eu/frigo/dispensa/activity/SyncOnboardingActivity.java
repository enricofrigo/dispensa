package eu.frigo.dispensa.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.Objects;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.pairing.OnboardingCoordinator;
import eu.frigo.dispensa.sync.core.pairing.PairingPayload;
import eu.frigo.dispensa.sync.core.pairing.PairingPayloadCodecImpl;
import eu.frigo.dispensa.sync.QrCodeGenerator;
import eu.frigo.dispensa.sync.webdav.WebDavConfig;
import eu.frigo.dispensa.sync.webdav.WebDavPairingHandler;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import eu.frigo.dispensa.sync.webdav.client.WebDavClientFactory;
import eu.frigo.dispensa.sync.webdav.model.WebDavManifest;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.Response;

public class SyncOnboardingActivity extends AppCompatActivity {
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_SHARE = "share";
    public static final String MODE_JOIN = "join";
    public static final String VERSION_MISMATCH = "VERSION_MISMATCH";
    public static final String DEVICE_ALREADY_REGISTERED = "DEVICE_ALREADY_REGISTERED";

    private String currentPairingCode;
    private String scannedQrData;
    private PreviewView previewViewScanner;
    private BarcodeScanner qrScanner;
    private java.util.concurrent.ExecutorService cameraExecutor;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private boolean isCameraPermissionGranted = false;

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    isCameraPermissionGranted = true;
                    startCamera();
                } else {
                    isCameraPermissionGranted = false;
                    Toast.makeText(this, "Permesso fotocamera negato. Impossibile scansionare il QR.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_onboarding);

        android.net.Uri data = getIntent().getData();
        if (data != null && ("dispensa".equals(data.getScheme()) || "https".equals(data.getScheme()))) {
            // Started via Deep Link or App Link
            scannedQrData = data.getQueryParameter("data");
            if (scannedQrData != null) {
                setupJoinMode();
                showPairingCodeInput();
                return;
            }
        }

        String mode = getIntent().getStringExtra(EXTRA_MODE);
        if (MODE_SHARE.equals(mode)) {
            setupShareMode();
        } else {
            setupJoinMode();
        }
    }

    private void showPairingCodeInput() {
        if (previewViewScanner != null) previewViewScanner.setVisibility(View.GONE);
        findViewById(R.id.til_pairing_code).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_confirm_onboarding).setVisibility(View.VISIBLE);
        Toast.makeText(this, "Link rilevato. Inserisci il codice di accoppiamento.", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (scannedQrData == null && isCameraPermissionGranted) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (qrScanner != null) {
            qrScanner.close();
        }
    }

    private void checkCameraPermissionAndStartScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            isCameraPermissionGranted = true;
            startCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        if (!isCameraPermissionGranted) return;
        if (previewViewScanner == null) return;

        previewViewScanner.setVisibility(View.VISIBLE);
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreviewAndAnalysis(cameraProvider);
            } catch (Exception e) {
                Log.e("SyncOnboarding", "Errore avvio fotocamera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreviewAndAnalysis(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(previewViewScanner.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            @androidx.camera.core.ExperimentalGetImage
            android.media.Image mediaImage = imageProxy.getImage();
            if (mediaImage != null) {
                InputImage image = InputImage.fromMediaImage(mediaImage,
                        imageProxy.getImageInfo().getRotationDegrees());

                qrScanner.process(image)
                        .addOnSuccessListener(barcodes -> {
                            if (!barcodes.isEmpty()) {
                                for (Barcode barcode : barcodes) {
                                    if (barcode.getValueType() == Barcode.TYPE_URL || barcode.getValueType() == Barcode.TYPE_TEXT) {
                                        String rawData = barcode.getRawValue();
                                        scannedQrData = extractDataFromLink(rawData);
                                        Log.d("SyncOnboarding", "QR scansionato con successo via ML Kit");
                                        runOnUiThread(() -> {
                                            stopCamera();
                                            previewViewScanner.setVisibility(View.GONE);
                                            findViewById(R.id.til_pairing_code).setVisibility(View.VISIBLE);
                                            findViewById(R.id.btn_confirm_onboarding).setVisibility(View.VISIBLE);
                                            Toast.makeText(this, "QR scansionato. Inserisci il codice di accoppiamento.", Toast.LENGTH_SHORT).show();
                                        });
                                        return;
                                    }
                                }
                            }
                        })
                        .addOnCompleteListener(task -> imageProxy.close());
            } else {
                imageProxy.close();
            }
        });

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Log.e("SyncOnboarding", "Binding fallito", e);
        }
    }

    private void stopCamera() {
        if (cameraProviderFuture != null) {
            try {
                cameraProviderFuture.get().unbindAll();
            } catch (Exception e) {
                Log.e("SyncOnboarding", "Errore stop fotocamera", e);
            }
        }
    }

    @SuppressLint("CheckResult")
    private void setupShareMode() {
        TextView instruction = findViewById(R.id.tv_onboarding_instruction);
        instruction.setText(R.string.share_pantry);
        
        ImageView qrView = findViewById(R.id.iv_qr_code);
        TextView codeView = findViewById(R.id.tv_pairing_code);
        Button shareBtn = findViewById(R.id.btn_share_link);
        
        qrView.setVisibility(View.VISIBLE);
        codeView.setVisibility(View.VISIBLE);
        if (shareBtn != null) shareBtn.setVisibility(View.VISIBLE);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String url = prefs.getString(SyncManager.KEY_WEBDAV_URL, "");
        String user = prefs.getString(SyncManager.KEY_WEBDAV_USER, "");
        String pass = prefs.getString(SyncManager.KEY_WEBDAV_PASS, "");
        String path = prefs.getString(SyncManager.KEY_WEBDAV_PATH, SyncManager.DEFAULT_PATH);
        String pantryKey = prefs.getString(SyncManager.SYNC_WEBDAV_PANTRY_KEY, "");
        boolean isShared = prefs.getBoolean(SyncManager.KEY_WEBDAV_MODE_SHARED, false);

        if (url.isEmpty() || (user.isEmpty() && !isShared)) {
            Toast.makeText(this, "Configura prima il sync nelle impostazioni", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        currentPairingCode = OnboardingCoordinator.generatePairingCode();
        codeView.setText(currentPairingCode);

        eu.frigo.dispensa.data.Repository.getInstance(getApplication()).getCurrentDispensaNameSingle()
                .subscribeOn(Schedulers.io())
                .flatMap(pantryName -> Single.fromCallable(() -> {
                    String deviceId = eu.frigo.dispensa.sync.core.engine.InstallationIdProvider.getOrCreateInstallationId(this);
                    WebDavConfig config = new WebDavConfig(url, user, pass, path, pantryKey, pantryName, deviceId, isShared);
                    String deviceName = android.os.Build.MODEL;
                    PairingPayload payload = WebDavPairingHandler.createPayload(deviceName, config);
                    PairingPayloadCodecImpl codec = new PairingPayloadCodecImpl(currentPairingCode);
                    String wireData = codec.encode(payload);
                    String deepLink = "https://enricofrigo.github.io/dispensa/syncjoin?data=" + android.net.Uri.encode(wireData);
                    Bitmap qrBitmap = QrCodeGenerator.generate(deepLink, 512);
                    return new ShareInfo(deepLink, qrBitmap);
                }))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(info -> {
                    qrView.setImageBitmap(info.qrBitmap);

                    if (shareBtn != null) {
                        shareBtn.setOnClickListener(v -> {
                            Intent sendIntent = new Intent();
                            sendIntent.setAction(Intent.ACTION_SEND);
                            sendIntent.putExtra(Intent.EXTRA_TEXT, "Unisciti alla mia dispensa condivisa!\n\nLink: " + info.deepLink + "\n\nCodice di accoppiamento: " + currentPairingCode);
                            sendIntent.setType("text/plain");

                            Intent shareIntent = Intent.createChooser(sendIntent, null);
                            startActivity(shareIntent);
                        });
                    }
                    eu.frigo.dispensa.sync.core.engine.SyncCoordinatorImpl.getInstance(this).triggerManualSync();
                }, throwable -> {
                    Log.e("SyncOnboarding", "Errore generazione QR", throwable);
                    Toast.makeText(this, "Errore generazione QR: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private static class ShareInfo {
        final String deepLink;
        final Bitmap qrBitmap;

        ShareInfo(String deepLink, Bitmap qrBitmap) {
            this.deepLink = deepLink;
            this.qrBitmap = qrBitmap;
        }
    }

    @SuppressLint("CheckResult")
    private void setupJoinMode() {
        TextView instruction = findViewById(R.id.tv_onboarding_instruction);
        instruction.setText(R.string.join_pantry);

        previewViewScanner = findViewById(R.id.previewViewScanner);
        com.google.android.material.textfield.TextInputEditText etPairingCode = findViewById(R.id.et_pairing_code);
        Button confirm = findViewById(R.id.btn_confirm_onboarding);

        cameraExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        qrScanner = BarcodeScanning.getClient(options);

        if (scannedQrData == null) {
            checkCameraPermissionAndStartScanner();
        }
        
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
            Single<PairingPayload> pd = new OnboardingCoordinator().joinPantry(pairingCode, scannedQrData);
            pd.flatMap(payload -> {
                String providerId = payload.providerId != null ? payload.providerId : payload.data.get("providerId");
                if ("webdav".equals(providerId)) {
                    return checkVersionCompatibility(payload)
                            .flatMap(compatible -> {
                                if (!compatible) {
                                    return Single.error(new IllegalStateException(VERSION_MISMATCH));
                                }
                                return checkDeviceAlreadyRegistered(payload);
                            })
                            .flatMap(exists -> {
                                if (exists) {
                                    return Single.error(new IllegalStateException(DEVICE_ALREADY_REGISTERED));
                                }
                                // If not registered, register it now
                                return registerDevice(payload).map(success -> payload);
                            });
                }
                return Single.just(payload);
            })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .flatMap(payload -> Single.fromCallable(() -> {
                String ownerId = payload.data.get("ownerDeviceId");
                String pantryName = payload.data.get("pantryName");
                
                Dispensa newDispensa = new Dispensa(pantryName, false);
                newDispensa.deviceOwnerId = ownerId;
                
                long id = eu.frigo.dispensa.data.Repository.getInstance(getApplication()).insertDispensaSync(newDispensa, true);
                
                // Aggiorna le preferenze per includere la nuova dispensa nel sync
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                String syncedIds = prefs.getString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, "");
                if (syncedIds.isEmpty()) {
                    syncedIds = String.valueOf(id);
                } else {
                    syncedIds += "," + id;
                }
                prefs.edit()
                        .putString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, syncedIds)
                        .putString(SyncManager.SYNC_WEBDAV_PANTRY_NAME + "_" + id, pantryName)
                        .apply();
                
                return payload;
            }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()))
            .subscribe(payload -> {
                eu.frigo.dispensa.sync.core.engine.SyncCoordinatorImpl.getInstance(this).applyOnboarding(payload);
                Toast.makeText(this, R.string.sync_pairing_success, Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                finish();
            }, throwable -> {
                confirm.setEnabled(true);
                Log.e("SyncOnboarding", "Errore decriptazione pairing", throwable);
                if (DEVICE_ALREADY_REGISTERED.equals(throwable.getMessage())) {
                    Toast.makeText(this, "Questo dispositivo è già registrato in questa dispensa.", Toast.LENGTH_LONG).show();
                } else if (VERSION_MISMATCH.equals(throwable.getMessage())) {
                    Toast.makeText(this, "Incompatibilità Versione: La dispensa remota non è compatibile con questa app.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, R.string.sync_pairing_error, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private Single<Boolean> checkVersionCompatibility(PairingPayload payload) {
        return Single.fromCallable(() -> {
            String url = payload.data.get("url");
            String user = payload.data.get("user");
            String pass = payload.data.get("pass");
            String path = payload.data.get("path");
            String pantryName = payload.data.get("pantryName");

            if (url == null || pass == null) return false;

            String effectivePath = path != null ? path : SyncManager.DEFAULT_PATH;
            String normalizedBase = effectivePath.endsWith("/") ? effectivePath : effectivePath + "/";
            if (normalizedBase.startsWith("/")) normalizedBase = normalizedBase.substring(1);
            String pantryPath = normalizedBase + SyncManager.getSyncPath(pantryName);
            String manifestPath = pantryPath + SyncManager.MANIFEST_JSON;

            WebDavClient client = WebDavClientFactory.getInstance().getClient(url, user, pass);
            try (Response response = client.get(manifestPath)) {
                if (response.isSuccessful()) {
                    WebDavManifest manifest = new com.google.gson.Gson().fromJson(response.body().string(), WebDavManifest.class);
                    if (manifest != null) {
                        return manifest.version == SyncManager.CURRENT_SYNC_VERSION;
                    }
                }
            } catch (Exception e) {
                Log.e("SyncOnboardingActivity", "Error checking version", e);
            }
            return false;
        });
    }

    private Single<Boolean> checkDeviceAlreadyRegistered(PairingPayload payload) {
        return Single.fromCallable(() -> {
            String url = payload.data.get("url");
            String user = payload.data.get("user");
            String pass = payload.data.get("pass");
            String path = payload.data.get("path");
            String pantryKey = payload.data.get("pantryKey");
            String pantryName = payload.data.get("pantryName");
            boolean isShared = Boolean.parseBoolean(payload.data.get("isShared"));

            if (url == null || (!isShared && user == null) || pass == null || pantryKey == null) {
                return false;
            }

            String effectivePath = path != null ? path : SyncManager.DEFAULT_PATH;
            
            String deviceId = eu.frigo.dispensa.sync.core.engine.InstallationIdProvider.getOrCreateInstallationId(this);
            
            String normalizedBase = effectivePath.endsWith("/") ? effectivePath : effectivePath + "/";
            if (normalizedBase.startsWith("/")) normalizedBase = normalizedBase.substring(1);
            String pantryPath = normalizedBase + SyncManager.getSyncPath(pantryName);
            String devicePath = pantryPath + SyncManager.DEFAULT_DEVICES_FOLDER + deviceId + ".json";

            WebDavClient client = WebDavClientFactory.getInstance().getClient(url, user, pass);
            try (Response response = client.propfind(devicePath)) {
                return response.isSuccessful() || response.code() == 207;
            } catch (Exception e) {
                Log.e("SyncOnboardingActivity",url,e);
                return false;
            }
        });
    }

    private Single<Boolean> registerDevice(PairingPayload payload) {
        return Single.fromCallable(() -> {
            String url = payload.data.get("url");
            String user = payload.data.get("user");
            String pass = payload.data.get("pass");
            String path = payload.data.get("path");
            String pantryKey = payload.data.get("pantryKey");
            String pantryName = payload.data.get("pantryName");
            boolean isShared = Boolean.parseBoolean(payload.data.get("isShared"));

            if (url == null || (!isShared && user == null) || pass == null || pantryKey == null) {
                return false;
            }

            String effectivePath = path != null ? path : SyncManager.DEFAULT_PATH;
            String deviceId = eu.frigo.dispensa.sync.core.engine.InstallationIdProvider.getOrCreateInstallationId(this);
            
            String normalizedBase = effectivePath.endsWith("/") ? effectivePath : effectivePath + "/";
            if (normalizedBase.startsWith("/")) normalizedBase = normalizedBase.substring(1);
            String pantryPath = normalizedBase + SyncManager.getSyncPath(pantryName);
            String devicePath = pantryPath + SyncManager.DEFAULT_DEVICES_FOLDER + deviceId + ".json";

            eu.frigo.dispensa.sync.webdav.model.WebDavDevice device = new eu.frigo.dispensa.sync.webdav.model.WebDavDevice();
            device.deviceId = deviceId;
            device.deviceName = PreferenceManager.getDefaultSharedPreferences(this).getString(SyncManager.KEY_DEVICE_NAME, android.os.Build.MODEL);
            device.lastSeen = System.currentTimeMillis();

            String deviceJson = new com.google.gson.Gson().toJson(device);
            WebDavClient client = WebDavClientFactory.getInstance().getClient(url, user, pass);
            try (Response devResp = client.put(devicePath, deviceJson.getBytes(), null)) {
                return devResp.isSuccessful();
            } catch (Exception e) {
                Log.e("SyncOnboardingActivity", "Failed to register device", e);
                return false;
            }
        });
    }

    private String extractDataFromLink(String rawData) {
        if (rawData != null && (rawData.startsWith("dispensa://") || rawData.startsWith("https://enricofrigo.github.io/dispensa/syncjoin"))) {
            android.net.Uri uri = android.net.Uri.parse(rawData);
            String dataParam = uri.getQueryParameter("data");
            return dataParam != null ? dataParam : rawData;
        }
        return rawData;
    }
}
