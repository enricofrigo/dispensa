package eu.frigo.dispensa.sync;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.activity.SyncOnboardingActivity;
import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.data.sync.RoomPantryDataBridge;
import eu.frigo.dispensa.sync.core.engine.FolderSyncEngine;
import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.engine.SyncCoordinatorImpl;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.model.PantryDevice;
import eu.frigo.dispensa.sync.core.model.PantryManifest;
import eu.frigo.dispensa.sync.core.pairing.OnboardingCoordinator;
import eu.frigo.dispensa.sync.core.pairing.PairingPayload;
import eu.frigo.dispensa.sync.core.pairing.PairingPayloadCodecImpl;
import eu.frigo.dispensa.sync.core.store.SyncCursorStoreImpl;
import eu.frigo.dispensa.sync.gdrive.auth.GDriveAuthManager;
import eu.frigo.dispensa.sync.gdrive.client.GDriveClient;
import eu.frigo.dispensa.sync.gdrive.store.GDriveFolderStore;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class SyncOnboardingFlavorDelegate {

    private final AppCompatActivity activity;
    private final Consumer<String> onRawQrScanned;

    private PreviewView previewViewScanner;
    private BarcodeScanner qrScanner;
    private ExecutorService cameraExecutor;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private boolean isCameraPermissionGranted = false;
    private Runnable pendingAuthAction;

    private final ActivityResultLauncher<String> requestPermissionLauncher;
    private final ActivityResultLauncher<Intent> consentLauncher;
    private final ActivityResultLauncher<Intent> contactPickerLauncher;

    public SyncOnboardingFlavorDelegate(AppCompatActivity activity, Consumer<String> onRawQrScanned) {
        this.activity = activity;
        this.onRawQrScanned = onRawQrScanned;

        this.requestPermissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        isCameraPermissionGranted = true;
                        startCamera();
                    } else {
                        isCameraPermissionGranted = false;
                        Toast.makeText(activity, "Permesso fotocamera negato. Impossibile scansionare il QR.", Toast.LENGTH_LONG).show();
                    }
                });

        this.consentLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        if (pendingAuthAction != null) {
                            Runnable action = pendingAuthAction;
                            pendingAuthAction = null;
                            action.run();
                        } else {
                            Toast.makeText(activity, "Autorizzazione concessa", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        pendingAuthAction = null;
                        Toast.makeText(activity, "Consenso Google Drive non concesso", Toast.LENGTH_LONG).show();
                    }
                });

        this.contactPickerLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        android.net.Uri contactUri = result.getData().getData();
                        if (contactUri != null) {
                            String email = extractEmailFromContactUri(contactUri);
                            if (email != null && !email.isEmpty()) {
                                com.google.android.material.textfield.TextInputEditText etEmail = activity.findViewById(R.id.et_gdrive_email);
                                com.google.android.material.textfield.TextInputLayout tilEmail = activity.findViewById(R.id.til_gdrive_email);
                                if (etEmail != null) etEmail.setText(email);
                                if (tilEmail != null) tilEmail.setError(null);
                            } else {
                                Toast.makeText(activity, R.string.sync_gdrive_no_email_in_contact, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }

    public void initScanner(boolean hasInitialQrData) {
        previewViewScanner = activity.findViewById(R.id.previewViewScanner);
        if (previewViewScanner != null) {
            previewViewScanner.setVisibility(View.VISIBLE);
        }
        cameraExecutor = Executors.newSingleThreadExecutor();
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        qrScanner = BarcodeScanning.getClient(options);

        if (!hasInitialQrData) {
            checkCameraPermissionAndStartScanner();
        }
    }

    public void onResume(boolean hasScannedData) {
        if (!hasScannedData && isCameraPermissionGranted) {
            startCamera();
        }
    }

    public void onPause() {
        stopCamera();
    }

    public void onDestroy() {
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (qrScanner != null) {
            qrScanner.close();
        }
    }

    public void hideScanner() {
        stopCamera();
        if (previewViewScanner != null) {
            previewViewScanner.setVisibility(View.GONE);
        }
    }

    public boolean isGDriveShare(JoinedPantryConfig config) {
        return config != null && "gdrive".equals(config.providerId);
    }

    private void checkCameraPermissionAndStartScanner() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            isCameraPermissionGranted = true;
            startCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        if (!isCameraPermissionGranted || previewViewScanner == null) return;

        previewViewScanner.setVisibility(View.VISIBLE);
        cameraProviderFuture = ProcessCameraProvider.getInstance(activity);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreviewAndAnalysis(cameraProvider);
            } catch (Exception e) {
                Log.e("SyncOnboarding", "Errore avvio fotocamera", e);
            }
        }, ContextCompat.getMainExecutor(activity));
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
                                        Log.d("SyncOnboarding", "QR scansionato con successo via ML Kit");
                                        activity.runOnUiThread(() -> {
                                            stopCamera();
                                            onRawQrScanned.accept(rawData);
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
            cameraProvider.bindToLifecycle(activity, cameraSelector, preview, imageAnalysis);
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

    private String extractEmailFromContactUri(android.net.Uri contactUri) {
        String email = null;
        try (android.database.Cursor cursor = activity.getContentResolver().query(
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

    @android.annotation.SuppressLint("CheckResult")
    public void setupGDriveShareUI(Dispensa disp, JoinedPantryConfig config, ImageView qrView, Button shareBtn) {
        View gdriveCard = activity.findViewById(R.id.card_gdrive_share_section);
        if (gdriveCard != null) gdriveCard.setVisibility(View.VISIBLE);

        com.google.android.material.textfield.TextInputLayout tilEmail = activity.findViewById(R.id.til_gdrive_email);
        com.google.android.material.textfield.TextInputEditText etEmail = activity.findViewById(R.id.et_gdrive_email);
        View btnPickContact = activity.findViewById(R.id.btn_pick_contact);
        View btnShareAction = activity.findViewById(R.id.btn_gdrive_share_action);
        View pbSharing = activity.findViewById(R.id.pb_gdrive_sharing);
        TextView tvSharedRecipient = activity.findViewById(R.id.tv_gdrive_shared_recipient);
        View btnShareAnother = activity.findViewById(R.id.btn_gdrive_share_another);
        View emailInputRow = activity.findViewById(R.id.ll_gdrive_email_input_row);

        View.OnClickListener pickContactAction = v -> {
            try {
                Intent pickIntent = new Intent(Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_URI);
                contactPickerLauncher.launch(pickIntent);
            } catch (Exception e) {
                Toast.makeText(activity, R.string.sync_gdrive_contacts_not_available, Toast.LENGTH_SHORT).show();
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
                    if (tilEmail != null) tilEmail.setError(activity.getString(R.string.sync_gdrive_invalid_email));
                    return;
                }
                if (tilEmail != null) tilEmail.setError(null);

                if (pbSharing != null) pbSharing.setVisibility(View.VISIBLE);
                btnShareAction.setEnabled(false);

                Single.fromCallable(() -> {
                    GDriveAuthManager authManager = new GDriveAuthManager(activity);
                    if (!authManager.isSignedIn()) {
                        throw new IllegalStateException(activity.getString(R.string.sync_gdrive_not_connected));
                    }
                    GDriveClient client = new GDriveClient(authManager);

                    AppDatabase db = AppDatabase.getDatabase(activity);
                    String remoteId = (disp.remoteId != null && !disp.remoteId.trim().isEmpty()) ? disp.remoteId.trim() : String.valueOf(disp.id);
                    if (disp.remoteId == null || disp.remoteId.trim().isEmpty()) {
                        disp.remoteId = remoteId;
                        db.dispensaDao().update(disp);
                    }
                    String pantryPath = GDriveFolderStore.APP_FOLDER_NAME + "/" + SyncManager.getSyncPath(remoteId);

                    JoinedPantryConfig currentConfig = db.joinedPantryConfigDao().getConfigByDispensaId(disp.id);
                    String folderId = (currentConfig != null && currentConfig.url != null && !currentConfig.url.isEmpty() && !"root".equals(currentConfig.url))
                            ? currentConfig.url
                            : null;

                    GDriveFolderStore rootStore = new GDriveFolderStore(client, "root");
                    if (folderId == null) {
                        folderId = rootStore.getOrCreateFolderId(pantryPath);
                        if (currentConfig != null) {
                            currentConfig.url = folderId;
                            db.joinedPantryConfigDao().update(currentConfig);
                        } else {
                            currentConfig = new JoinedPantryConfig(disp.id, "gdrive", null, remoteId);
                            currentConfig.url = folderId;
                            db.joinedPantryConfigDao().insert(currentConfig);
                        }
                    } else {
                        rootStore.ensureFolderMovedToAppParent(folderId);
                        if (currentConfig == null) {
                            currentConfig = new JoinedPantryConfig(disp.id, "gdrive", null, remoteId);
                            currentConfig.url = folderId;
                            db.joinedPantryConfigDao().insert(currentConfig);
                        }
                    }

                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
                    String syncedIds = prefs.getString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, "");
                    if (syncedIds.isEmpty()) {
                        syncedIds = String.valueOf(disp.id);
                    } else if (!Arrays.asList(syncedIds.split(",")).contains(String.valueOf(disp.id))) {
                        syncedIds += "," + disp.id;
                    }
                    prefs.edit()
                            .putString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, syncedIds)
                            .putString(SyncManager.SYNC_WEBDAV_PANTRY_NAME + "_" + disp.id, disp.getName())
                            .putBoolean(SyncManager.KEY_SYNC_ENABLED, true)
                            .apply();

                    client.shareFolderWithUser(folderId, email, "writer");

                    String deviceId = InstallationIdProvider.getOrCreateInstallationId(activity);
                    String deviceName = PreferenceManager.getDefaultSharedPreferences(activity)
                            .getString(SyncManager.KEY_DEVICE_NAME, android.os.Build.MODEL);

                    GDriveFolderStore pantryStore = new GDriveFolderStore(client, folderId);
                    FolderSyncEngine ownerEngine = new FolderSyncEngine(
                            pantryStore,
                            new RoomPantryDataBridge(db),
                            new SyncCursorStoreImpl(activity, disp.id),
                            deviceId,
                            deviceName,
                            "",
                            disp.id
                    );
                    ownerEngine.initializeRemoteStructure().blockingAwait();
                    ownerEngine.forceCompaction();

                    String ownerDeviceId = (disp.deviceOwnerId != null && !disp.deviceOwnerId.trim().isEmpty())
                            ? disp.deviceOwnerId.trim()
                            : deviceId;

                    Map<String, String> data = new HashMap<>();
                    data.put("providerId", "gdrive");
                    data.put("ownerDeviceId", ownerDeviceId);
                    data.put("pantryName", disp.getName());
                    data.put("remoteId", remoteId);
                    data.put("folderId", folderId);
                    data.put("recipientEmail", email);

                    PairingPayload payload = new PairingPayload("gdrive", deviceName, data);
                    payload.version = SyncManager.CURRENT_SYNC_VERSION;

                    String pairingPin = OnboardingCoordinator.generatePairingCode();
                    PairingPayloadCodecImpl codec = new PairingPayloadCodecImpl(pairingPin);
                    String wireData = codec.encode(payload);
                    String deepLink = "https://enricofrigo.github.io/dispensa/syncjoin?data=" + android.net.Uri.encode(wireData);
                    Bitmap qrBitmap = QrCodeGenerator.generate(deepLink, 512);

                    return new SyncOnboardingActivity.ShareInfo(deepLink, qrBitmap, email, pairingPin);
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(info -> {
                    if (pbSharing != null) pbSharing.setVisibility(View.GONE);
                    btnShareAction.setEnabled(true);

                    qrView.setImageBitmap(info.qrBitmap);
                    qrView.setVisibility(View.VISIBLE);
                    TextView instruction = activity.findViewById(R.id.tv_onboarding_instruction);
                    if (instruction != null && info.pairingPin != null) {
                        String formattedPin = info.pairingPin.substring(0, 3) + "-" + info.pairingPin.substring(3);
                        instruction.setText(activity.getString(R.string.share_pantry) + "\nPIN: " + formattedPin);
                    }

                    if (tvSharedRecipient != null) {
                        tvSharedRecipient.setText(activity.getString(R.string.sync_gdrive_shared_with, info.recipientEmail));
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
                            activity.startActivity(shareIntent);
                        });
                    }
                    SyncCoordinatorImpl.getInstance(activity).triggerManualSync();
                    Toast.makeText(activity, activity.getString(R.string.sync_gdrive_shared_with, info.recipientEmail), Toast.LENGTH_SHORT).show();
                }, throwable -> {
                    if (pbSharing != null) pbSharing.setVisibility(View.GONE);
                    btnShareAction.setEnabled(true);
                    Log.e("SyncOnboarding", "Errore condivisione GDrive", throwable);
                    Intent consentIntent = GDriveAuthManager.extractConsentIntent(throwable);
                    if (consentIntent != null) {
                        pendingAuthAction = btnShareAction::performClick;
                        consentLauncher.launch(consentIntent);
                        return;
                    }
                    Toast.makeText(activity, "Errore condivisione: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
                });
            });
        }
    }

    public Single<PairingPayload> validateAndRegisterGDriveJoin(PairingPayload payload) {
        GDriveAuthManager authManager = new GDriveAuthManager(activity);
        if (!authManager.isSignedIn()) {
            return Single.error(new IllegalStateException("Devi prima connettere Google Drive nelle impostazioni."));
        }
        try {
            authManager.getAccessToken();
        } catch (Exception e) {
            return Single.error(e);
        }
        return checkVersionCompatibilityGDrive(payload, authManager)
                .flatMap(compatible -> {
                    if (!compatible) {
                        return Single.error(new IllegalStateException(SyncOnboardingActivity.VERSION_MISMATCH));
                    }
                    return checkDeviceAlreadyRegisteredGDrive(payload, authManager);
                })
                .flatMap(exists -> {
                    if (exists) {
                        return Single.error(new IllegalStateException(SyncOnboardingActivity.DEVICE_ALREADY_REGISTERED));
                    }
                    return registerDeviceGDrive(payload, authManager).map(success -> payload);
                });
    }

    public void performGDriveEagerSync(JoinedPantryConfig config, int dispensaId, String myDeviceId, String myDeviceName) {
        GDriveAuthManager authManager = new GDriveAuthManager(activity);
        GDriveClient client = new GDriveClient(authManager);
        GDriveFolderStore store = new GDriveFolderStore(client, config.url);
        FolderSyncEngine engine = new FolderSyncEngine(
                store,
                new RoomPantryDataBridge(AppDatabase.getDatabase(activity)),
                new SyncCursorStoreImpl(activity, dispensaId),
                myDeviceId,
                myDeviceName,
                "",
                dispensaId
        );
        engine.sync().blockingAwait();
    }

    public boolean handleJoinError(Throwable throwable, Runnable retryAction) {
        Intent consentIntent = GDriveAuthManager.extractConsentIntent(throwable);
        if (consentIntent != null) {
            pendingAuthAction = retryAction;
            consentLauncher.launch(consentIntent);
            return true;
        }
        return false;
    }

    private Single<Boolean> checkVersionCompatibilityGDrive(PairingPayload payload, GDriveAuthManager authManager) {
        return Single.fromCallable(() -> {
            String folderId = payload.data.get("folderId");
            if (folderId == null || folderId.isEmpty()) return false;

            GDriveClient client = new GDriveClient(authManager);
            GDriveFolderStore store = new GDriveFolderStore(client, folderId);

            Boolean exists = store.exists(SyncManager.MANIFEST_JSON).blockingGet();
            if (exists == null || !exists) {
                return false;
            }

            byte[] data = store.read(SyncManager.MANIFEST_JSON).blockingGet();
            if (data == null || data.length == 0) return false;

            PantryManifest manifest = new com.google.gson.Gson().fromJson(new String(data, StandardCharsets.UTF_8), PantryManifest.class);
            if (manifest != null) {
                if (payload.data.get("ownerDeviceId") == null && manifest.createdByDevice != null && !manifest.createdByDevice.trim().isEmpty()) {
                    payload.data.put("ownerDeviceId", manifest.createdByDevice.trim());
                }
                return manifest.version == SyncManager.CURRENT_SYNC_VERSION;
            }
            return false;
        });
    }

    private Single<Boolean> checkDeviceAlreadyRegisteredGDrive(PairingPayload payload, GDriveAuthManager authManager) {
        return Single.fromCallable(() -> {
            String folderId = payload.data.get("folderId");
            if (folderId == null || folderId.isEmpty()) return false;

            GDriveClient client = new GDriveClient(authManager);
            GDriveFolderStore store = new GDriveFolderStore(client, folderId);

            String deviceId = InstallationIdProvider.getOrCreateInstallationId(activity);
            String devicePath = SyncManager.DEFAULT_DEVICES_FOLDER + deviceId + ".json";

            Boolean exists = store.exists(devicePath).blockingGet();
            return exists != null && exists;
        });
    }

    private Single<Boolean> registerDeviceGDrive(PairingPayload payload, GDriveAuthManager authManager) {
        return Single.fromCallable(() -> {
            String folderId = payload.data.get("folderId");
            if (folderId == null || folderId.isEmpty()) return false;

            GDriveClient client = new GDriveClient(authManager);
            GDriveFolderStore store = new GDriveFolderStore(client, folderId);

            String deviceId = InstallationIdProvider.getOrCreateInstallationId(activity);
            String deviceName = PreferenceManager.getDefaultSharedPreferences(activity)
                    .getString(SyncManager.KEY_DEVICE_NAME, android.os.Build.MODEL);

            PantryDevice device = new PantryDevice(deviceId, deviceName);
            String deviceJson = new com.google.gson.Gson().toJson(device);
            String devicePath = SyncManager.DEFAULT_DEVICES_FOLDER + deviceId + ".json";

            store.writeAtomic(devicePath, deviceJson.getBytes(StandardCharsets.UTF_8), null).blockingAwait();
            return true;
        });
    }
}
