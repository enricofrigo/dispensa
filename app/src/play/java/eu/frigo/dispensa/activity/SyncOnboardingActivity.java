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
import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.pairing.OnboardingCoordinator;
import eu.frigo.dispensa.sync.core.pairing.PairingPayload;
import eu.frigo.dispensa.sync.core.pairing.PairingPayloadCodecImpl;
import eu.frigo.dispensa.sync.QrCodeGenerator;
import eu.frigo.dispensa.sync.webdav.WebDavConfig;
import eu.frigo.dispensa.sync.webdav.WebDavPairingHandler;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import eu.frigo.dispensa.sync.core.model.PantryDevice;
import eu.frigo.dispensa.sync.core.model.PantryManifest;
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
    public static final String PAIRING_EXPIRED = "PAIRING_EXPIRED";

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

    private Runnable pendingAuthAction;
    private final ActivityResultLauncher<Intent> consentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    if (pendingAuthAction != null) {
                        Runnable action = pendingAuthAction;
                        pendingAuthAction = null;
                        action.run();
                    } else {
                        Toast.makeText(this, "Autorizzazione concessa", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    pendingAuthAction = null;
                    Toast.makeText(this, "Consenso Google Drive non concesso", Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<Intent> contactPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    android.net.Uri contactUri = result.getData().getData();
                    if (contactUri != null) {
                        String email = extractEmailFromContactUri(contactUri);
                        if (email != null && !email.isEmpty()) {
                            com.google.android.material.textfield.TextInputEditText etEmail = findViewById(R.id.et_gdrive_email);
                            com.google.android.material.textfield.TextInputLayout tilEmail = findViewById(R.id.til_gdrive_email);
                            if (etEmail != null) etEmail.setText(email);
                            if (tilEmail != null) tilEmail.setError(null);
                        } else {
                            Toast.makeText(this, R.string.sync_gdrive_no_email_in_contact, Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });

    private String extractEmailFromContactUri(android.net.Uri contactUri) {
        String email = null;
        try (android.database.Cursor cursor = getContentResolver().query(
                contactUri,
                new String[]{android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS},
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int emailIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS);
                if (emailIndex >= 0) {
                    email = cursor.getString(emailIndex);
                }
            }
        } catch (Exception e) {
            Log.e("SyncOnboarding", "Errore estrazione email da contatto", e);
        }
        return email != null ? email.trim() : null;
    }

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
                // Immediately process if data is already present from deep link
                processScannedData();
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
                                            processScannedData();
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

    private void processScannedData() {
        if (scannedQrData == null) return;
        
        previewViewScanner.setVisibility(View.GONE);
        findViewById(R.id.btn_confirm_onboarding).setVisibility(View.GONE);

        new OnboardingCoordinator().joinPantry(null, scannedQrData)
            .flatMap(payload -> {
                // Check expiry (10 minutes = 600 seconds)
                long nowSec = System.currentTimeMillis() / 1000;
                if (nowSec - payload.issuedAt > 600) {
                    return Single.error(new IllegalStateException(PAIRING_EXPIRED));
                }

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
                                return registerDevice(payload).map(success -> payload);
                            });
                } else if ("gdrive".equals(providerId)) {
                    eu.frigo.dispensa.sync.gdrive.auth.GDriveAuthManager authManager = new eu.frigo.dispensa.sync.gdrive.auth.GDriveAuthManager(this);
                    if (!authManager.isSignedIn()) {
                        return Single.error(new IllegalStateException("Devi prima connettere Google Drive nelle impostazioni."));
                    }
                    try {
                        authManager.getAccessToken();
                    } catch (Exception e) {
                        return Single.error(e);
                    }
                    return Single.just(payload);
                }
                return Single.just(payload);
            })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .flatMap(payload -> Single.fromCallable(() -> {
                String providerId = payload.providerId != null ? payload.providerId : payload.data.get("providerId");
                String ownerId = payload.data.get("ownerDeviceId");
                String pantryName = payload.data.get("pantryName");
                String ownerName = payload.deviceName;
                String remoteId = payload.data.get("remoteId");
                
                Dispensa newDispensa = new Dispensa(pantryName, false);
                newDispensa.deviceOwnerId = ownerId;
                newDispensa.deviceOwnerName = ownerName;
                newDispensa.remoteId = remoteId;
                
                long id = eu.frigo.dispensa.data.Repository.getInstance(getApplication()).insertDispensaSync(newDispensa, true);
                
                // Save secure config to DB
                JoinedPantryConfig config;
                if ("gdrive".equals(providerId)) {
                    config = new JoinedPantryConfig((int) id, "gdrive", null, remoteId);
                    config.url = payload.data.get("folderId");
                } else {
                    config = new JoinedPantryConfig(
                            (int) id,
                            payload.data.get("url"),
                            payload.data.get("user"),
                            payload.data.get("pass"),
                            payload.data.get("path"),
                            Boolean.parseBoolean(payload.data.get("isShared")),
                            payload.data.get("pantryKey")
                    );
                }
                eu.frigo.dispensa.data.Repository.getInstance(getApplication()).insertJoinedPantryConfig(config);

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
                Log.e("SyncOnboarding", "Errore join", throwable);
                Intent consentIntent = eu.frigo.dispensa.sync.gdrive.auth.GDriveAuthManager.extractConsentIntent(throwable);
                if (consentIntent != null) {
                    pendingAuthAction = () -> processScannedData();
                    consentLauncher.launch(consentIntent);
                    return;
                }
                if (PAIRING_EXPIRED.equals(throwable.getMessage())) {
                    Toast.makeText(this, R.string.sync_pairing_expired, Toast.LENGTH_LONG).show();
                } else if (DEVICE_ALREADY_REGISTERED.equals(throwable.getMessage())) {
                    Toast.makeText(this, "Questo dispositivo è già registrato in questa dispensa.", Toast.LENGTH_LONG).show();
                } else if (VERSION_MISMATCH.equals(throwable.getMessage())) {
                    Toast.makeText(this, "Incompatibilità Versione: La dispensa remota non è compatibile con questa app.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, throwable.getMessage() != null ? throwable.getMessage() : getString(R.string.sync_pairing_error), Toast.LENGTH_LONG).show();
                }
                // Riavvia lo scanner se è fallito ma non scaduto
                if (!PAIRING_EXPIRED.equals(throwable.getMessage())) {
                    startCamera();
                }
            });
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
        Button shareBtn = findViewById(R.id.btn_share_link);

        // Recupera la dispensa passata come extra
        Dispensa targetDispensa = (Dispensa) getIntent().getSerializableExtra(ManageDevicesActivity.PANTRY_ID);

        Single<eu.frigo.dispensa.data.dispensa.Dispensa> dispSingle;
        if (targetDispensa != null) {
            dispSingle = Single.just(targetDispensa);
        } else {
            dispSingle = eu.frigo.dispensa.data.Repository.getInstance(getApplication()).getCurrentDispensaSingle();
        }

        dispSingle.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(disp -> {
                    Schedulers.io().scheduleDirect(() -> {
                        AppDatabase db = AppDatabase.getDatabase(this);
                        JoinedPantryConfig config = db.joinedPantryConfigDao().getConfigByDispensaId(disp.id);
                        boolean isGDrive = (config != null && "gdrive".equals(config.providerId));

                        runOnUiThread(() -> {
                            if (isGDrive) {
                                setupGDriveShareUI(disp, config, qrView, shareBtn);
                            } else {
                                setupStandardShareUI(disp, config, qrView, shareBtn);
                            }
                        });
                    });
                }, throwable -> {
                    Log.e("SyncOnboarding", "Errore recupero dispensa", throwable);
                    Toast.makeText(this, "Errore recupero dispensa: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void setupGDriveShareUI(Dispensa disp, JoinedPantryConfig config, ImageView qrView, Button shareBtn) {
        View gdriveCard = findViewById(R.id.card_gdrive_share_section);
        if (gdriveCard != null) gdriveCard.setVisibility(View.VISIBLE);

        com.google.android.material.textfield.TextInputLayout tilEmail = findViewById(R.id.til_gdrive_email);
        com.google.android.material.textfield.TextInputEditText etEmail = findViewById(R.id.et_gdrive_email);
        View btnPickContact = findViewById(R.id.btn_pick_contact);
        View btnShareAction = findViewById(R.id.btn_gdrive_share_action);
        View pbSharing = findViewById(R.id.pb_gdrive_sharing);
        TextView tvSharedRecipient = findViewById(R.id.tv_gdrive_shared_recipient);
        View btnShareAnother = findViewById(R.id.btn_gdrive_share_another);
        View emailInputRow = findViewById(R.id.ll_gdrive_email_input_row);

        View.OnClickListener pickContactAction = v -> {
            try {
                Intent pickIntent = new Intent(Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_URI);
                contactPickerLauncher.launch(pickIntent);
            } catch (Exception e) {
                Toast.makeText(this, R.string.sync_gdrive_contacts_not_available, Toast.LENGTH_SHORT).show();
            }
        };
        if (btnPickContact != null) btnPickContact.setOnClickListener(pickContactAction);
        if (tilEmail != null) tilEmail.setEndIconOnClickListener(pickContactAction);

        if (btnShareAnother != null) {
            btnShareAnother.setOnClickListener(v -> {
                if (emailInputRow != null) emailInputRow.setVisibility(View.VISIBLE);
                if (btnShareAction != null) btnShareAction.setVisibility(View.VISIBLE);
                if (etEmail != null) etEmail.setText("");
                if (tilEmail != null) tilEmail.setError(null);
            });
        }

        if (btnShareAction != null) {
            btnShareAction.setOnClickListener(v -> {
                String email = etEmail != null && etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
                if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    if (tilEmail != null) tilEmail.setError(getString(R.string.sync_gdrive_invalid_email));
                    return;
                }
                if (tilEmail != null) tilEmail.setError(null);

                if (pbSharing != null) pbSharing.setVisibility(View.VISIBLE);
                btnShareAction.setEnabled(false);

                Single.fromCallable(() -> {
                    eu.frigo.dispensa.sync.gdrive.auth.GDriveAuthManager authManager = new eu.frigo.dispensa.sync.gdrive.auth.GDriveAuthManager(this);
                    if (!authManager.isSignedIn()) {
                        throw new IllegalStateException(getString(R.string.sync_gdrive_not_connected));
                    }
                    eu.frigo.dispensa.sync.gdrive.client.GDriveClient client = new eu.frigo.dispensa.sync.gdrive.client.GDriveClient(authManager);

                    AppDatabase db = AppDatabase.getDatabase(this);
                    String remoteId = (disp.remoteId != null && !disp.remoteId.trim().isEmpty()) ? disp.remoteId.trim() : String.valueOf(disp.id);
                    if (disp.remoteId == null || disp.remoteId.trim().isEmpty()) {
                        disp.remoteId = remoteId;
                        db.dispensaDao().update(disp);
                    }
                    String pantryPath = SyncManager.getSyncPath(remoteId);

                    JoinedPantryConfig currentConfig = db.joinedPantryConfigDao().getConfigByDispensaId(disp.id);
                    String folderId = (currentConfig != null && currentConfig.url != null && !currentConfig.url.isEmpty() && !"root".equals(currentConfig.url))
                            ? currentConfig.url
                            : null;

                    if (folderId == null) {
                        eu.frigo.dispensa.sync.gdrive.store.GDriveFolderStore rootStore = new eu.frigo.dispensa.sync.gdrive.store.GDriveFolderStore(client, "root");
                        folderId = rootStore.getOrCreateFolderId(pantryPath);
                        if (currentConfig != null) {
                            currentConfig.url = folderId;
                            db.joinedPantryConfigDao().update(currentConfig);
                        } else {
                            currentConfig = new JoinedPantryConfig(disp.id, "gdrive", null, remoteId);
                            currentConfig.url = folderId;
                            db.joinedPantryConfigDao().insert(currentConfig);
                        }
                    } else if (currentConfig == null) {
                        currentConfig = new JoinedPantryConfig(disp.id, "gdrive", null, remoteId);
                        currentConfig.url = folderId;
                        db.joinedPantryConfigDao().insert(currentConfig);
                    }

                    // Assicurati che la dispensa sia inclusa nel sync locale
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                    String syncedIds = prefs.getString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, "");
                    if (syncedIds.isEmpty()) {
                        syncedIds = String.valueOf(disp.id);
                    } else if (!java.util.Arrays.asList(syncedIds.split(",")).contains(String.valueOf(disp.id))) {
                        syncedIds += "," + disp.id;
                    }
                    prefs.edit()
                            .putString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, syncedIds)
                            .putString(SyncManager.SYNC_WEBDAV_PANTRY_NAME + "_" + disp.id, disp.getName())
                            .putBoolean(SyncManager.KEY_SYNC_ENABLED, true)
                            .apply();

                    // Condividi la cartella tramite Drive Permissions API con l'email destinatario
                    client.shareFolderWithUser(folderId, email, "writer");

                    String deviceId = eu.frigo.dispensa.sync.core.engine.InstallationIdProvider.getOrCreateInstallationId(this);
                    String deviceName = android.os.Build.MODEL;

                    java.util.Map<String, String> data = new java.util.HashMap<>();
                    data.put("providerId", "gdrive");
                    data.put("ownerDeviceId", deviceId);
                    data.put("pantryName", disp.getName());
                    data.put("remoteId", remoteId);
                    data.put("folderId", folderId);
                    data.put("recipientEmail", email);

                    PairingPayload payload = new PairingPayload("gdrive", deviceName, data);
                    payload.version = 2;

                    PairingPayloadCodecImpl codec = new PairingPayloadCodecImpl(null);
                    String wireData = codec.encode(payload);
                    String deepLink = "https://enricofrigo.github.io/dispensa/syncjoin?data=" + android.net.Uri.encode(wireData);
                    Bitmap qrBitmap = QrCodeGenerator.generate(deepLink, 512);

                    return new ShareInfo(deepLink, qrBitmap, email);
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(info -> {
                    if (pbSharing != null) pbSharing.setVisibility(View.GONE);
                    btnShareAction.setEnabled(true);

                    qrView.setImageBitmap(info.qrBitmap);
                    qrView.setVisibility(View.VISIBLE);

                    if (tvSharedRecipient != null) {
                        tvSharedRecipient.setText(getString(R.string.sync_gdrive_shared_with, info.recipientEmail));
                        tvSharedRecipient.setVisibility(View.VISIBLE);
                    }
                    if (btnShareAnother != null) btnShareAnother.setVisibility(View.VISIBLE);
                    if (emailInputRow != null) emailInputRow.setVisibility(View.GONE);
                    btnShareAction.setVisibility(View.GONE);

                    if (shareBtn != null) {
                        shareBtn.setVisibility(View.VISIBLE);
                        shareBtn.setOnClickListener(v2 -> {
                            Intent sendIntent = new Intent();
                            sendIntent.setAction(Intent.ACTION_SEND);
                            sendIntent.putExtra(Intent.EXTRA_TEXT, "Unisciti alla mia dispensa condivisa!\n\nLink: " + info.deepLink);
                            sendIntent.setType("text/plain");

                            Intent shareIntent = Intent.createChooser(sendIntent, null);
                            startActivity(shareIntent);
                        });
                    }
                    eu.frigo.dispensa.sync.core.engine.SyncCoordinatorImpl.getInstance(this).triggerManualSync();
                    Toast.makeText(this, getString(R.string.sync_gdrive_shared_with, info.recipientEmail), Toast.LENGTH_SHORT).show();
                }, throwable -> {
                    if (pbSharing != null) pbSharing.setVisibility(View.GONE);
                    btnShareAction.setEnabled(true);
                    Log.e("SyncOnboarding", "Errore condivisione GDrive", throwable);
                    Intent consentIntent = eu.frigo.dispensa.sync.gdrive.auth.GDriveAuthManager.extractConsentIntent(throwable);
                    if (consentIntent != null) {
                        pendingAuthAction = () -> btnShareAction.performClick();
                        consentLauncher.launch(consentIntent);
                        return;
                    }
                    Toast.makeText(this, "Errore condivisione: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
                });
            });
        }
    }

    private void setupStandardShareUI(Dispensa disp, JoinedPantryConfig config, ImageView qrView, Button shareBtn) {
        qrView.setVisibility(View.VISIBLE);
        if (shareBtn != null) shareBtn.setVisibility(View.VISIBLE);

        Single.fromCallable(() -> {
            String deviceId = eu.frigo.dispensa.sync.core.engine.InstallationIdProvider.getOrCreateInstallationId(this);
            String deviceName = android.os.Build.MODEL;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String url = prefs.getString(SyncManager.KEY_WEBDAV_URL, "");
            String user = prefs.getString(SyncManager.KEY_WEBDAV_USER, "");
            String pass = prefs.getString(SyncManager.KEY_WEBDAV_PASS, "");
            String path = prefs.getString(SyncManager.KEY_WEBDAV_PATH, SyncManager.DEFAULT_PATH);
            String pantryKey = prefs.getString(SyncManager.SYNC_WEBDAV_PANTRY_KEY, "");
            boolean isShared = prefs.getBoolean(SyncManager.KEY_WEBDAV_MODE_SHARED, false);

            if (url.isEmpty() || (user.isEmpty() && !isShared)) {
                throw new IllegalStateException("Configura prima il sync nelle impostazioni");
            }
            WebDavConfig webDavConfig = new WebDavConfig(url, user, pass, path, pantryKey, disp.getName(), deviceId, disp.remoteId, isShared);
            PairingPayload payload = WebDavPairingHandler.createPayload(deviceName, webDavConfig);

            PairingPayloadCodecImpl codec = new PairingPayloadCodecImpl(null);
            String wireData = codec.encode(payload);
            String deepLink = "https://enricofrigo.github.io/dispensa/syncjoin?data=" + android.net.Uri.encode(wireData);
            Bitmap qrBitmap = QrCodeGenerator.generate(deepLink, 512);
            return new ShareInfo(deepLink, qrBitmap, null);
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(info -> {
            qrView.setImageBitmap(info.qrBitmap);

            if (shareBtn != null) {
                shareBtn.setOnClickListener(v -> {
                    Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    sendIntent.putExtra(Intent.EXTRA_TEXT, "Unisciti alla mia dispensa condivisa!\n\nLink: " + info.deepLink);
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
        final String recipientEmail;

        ShareInfo(String deepLink, Bitmap qrBitmap, String recipientEmail) {
            this.deepLink = deepLink;
            this.qrBitmap = qrBitmap;
            this.recipientEmail = recipientEmail;
        }

        ShareInfo(String deepLink, Bitmap qrBitmap) {
            this(deepLink, qrBitmap, null);
        }
    }

    @SuppressLint("CheckResult")
    private void setupJoinMode() {
        TextView instruction = findViewById(R.id.tv_onboarding_instruction);
        instruction.setText(R.string.join_pantry);

        previewViewScanner = findViewById(R.id.previewViewScanner);
        previewViewScanner.setVisibility(View.VISIBLE);

        cameraExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        qrScanner = BarcodeScanning.getClient(options);

        if (scannedQrData == null) {
            checkCameraPermissionAndStartScanner();
        }
    }

    private Single<Boolean> checkVersionCompatibility(PairingPayload payload) {
        return Single.fromCallable(() -> {
            String url = payload.data.get("url");
            String user = payload.data.get("user");
            String pass = payload.data.get("pass");
            String path = payload.data.get("path");
            String pantryName = payload.data.get("pantryName");
            String remoteId = payload.data.get("remoteId");

            if (url == null || pass == null) return false;

            String effectivePath = path != null ? path : SyncManager.DEFAULT_PATH;
            String normalizedBase = effectivePath.endsWith("/") ? effectivePath : effectivePath + "/";
            if (normalizedBase.startsWith("/")) normalizedBase = normalizedBase.substring(1);
            String pantryPath = normalizedBase + SyncManager.getSyncPath(remoteId);
            String manifestPath = pantryPath + SyncManager.MANIFEST_JSON;

            WebDavClient client = eu.frigo.dispensa.sync.webdav.client.WebDavClientFactory.getInstance().getClient(url, user, pass);
            try (Response response = client.get(manifestPath)) {
                if (response.isSuccessful()) {
                    PantryManifest manifest = new com.google.gson.Gson().fromJson(response.body().string(), PantryManifest.class);
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
            String remoteId = payload.data.get("remoteId");
            boolean isShared = Boolean.parseBoolean(payload.data.get("isShared"));

            if (url == null || (!isShared && user == null) || pass == null || pantryKey == null) {
                return false;
            }

            String effectivePath = path != null ? path : SyncManager.DEFAULT_PATH;
            
            String deviceId = eu.frigo.dispensa.sync.core.engine.InstallationIdProvider.getOrCreateInstallationId(this);
            
            String normalizedBase = effectivePath.endsWith("/") ? effectivePath : effectivePath + "/";
            if (normalizedBase.startsWith("/")) normalizedBase = normalizedBase.substring(1);
            String pantryPath = normalizedBase + SyncManager.getSyncPath(remoteId);
            String devicePath = pantryPath + SyncManager.DEFAULT_DEVICES_FOLDER + deviceId + ".json";

            WebDavClient client = eu.frigo.dispensa.sync.webdav.client.WebDavClientFactory.getInstance().getClient(url, user, pass);
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
            String remoteId = payload.data.get("remoteId");
            boolean isShared = Boolean.parseBoolean(payload.data.get("isShared"));

            if (url == null || (!isShared && user == null) || pass == null || pantryKey == null) {
                return false;
            }

            String effectivePath = path != null ? path : SyncManager.DEFAULT_PATH;
            String deviceId = eu.frigo.dispensa.sync.core.engine.InstallationIdProvider.getOrCreateInstallationId(this);
            
            String normalizedBase = effectivePath.endsWith("/") ? effectivePath : effectivePath + "/";
            if (normalizedBase.startsWith("/")) normalizedBase = normalizedBase.substring(1);
            String pantryPath = normalizedBase + SyncManager.getSyncPath(remoteId);
            String devicePath = pantryPath + SyncManager.DEFAULT_DEVICES_FOLDER + deviceId + ".json";

            PantryDevice device = new PantryDevice();
            device.deviceId = deviceId;
            device.deviceName = PreferenceManager.getDefaultSharedPreferences(this).getString(SyncManager.KEY_DEVICE_NAME, android.os.Build.MODEL);
            device.lastSeen = System.currentTimeMillis();

            String deviceJson = new com.google.gson.Gson().toJson(device);
            WebDavClient client = eu.frigo.dispensa.sync.webdav.client.WebDavClientFactory.getInstance().getClient(url, user, pass);
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
