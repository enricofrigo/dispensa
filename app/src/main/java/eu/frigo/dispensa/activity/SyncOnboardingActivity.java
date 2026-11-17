package eu.frigo.dispensa.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
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
import androidx.core.content.IntentCompat;
import androidx.preference.PreferenceManager;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.Repository;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.data.sync.RoomPantryDataBridge;
import eu.frigo.dispensa.sync.QrCodeGenerator;
import eu.frigo.dispensa.sync.SyncOnboardingFlavorDelegate;
import eu.frigo.dispensa.sync.core.crypto.CredentialCipher;
import eu.frigo.dispensa.sync.core.engine.FolderSyncEngine;
import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.engine.SyncCoordinatorImpl;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.model.PantryDevice;
import eu.frigo.dispensa.sync.core.model.PantryManifest;
import eu.frigo.dispensa.sync.core.pairing.OnboardingCoordinator;
import eu.frigo.dispensa.sync.core.pairing.PairingPayload;
import eu.frigo.dispensa.sync.core.pairing.PairingPayloadCodecImpl;
import eu.frigo.dispensa.sync.core.store.SharedFolderStore;
import eu.frigo.dispensa.sync.core.store.SyncCursorStoreImpl;
import eu.frigo.dispensa.sync.sharing.PantrySharingService;
import eu.frigo.dispensa.sync.webdav.WebDavConfig;
import eu.frigo.dispensa.sync.webdav.WebDavPairingHandler;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import eu.frigo.dispensa.sync.webdav.client.WebDavClientFactory;
import eu.frigo.dispensa.sync.webdav.store.WebDavFolderStore;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class SyncOnboardingActivity extends AppCompatActivity {
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_SHARE = "share";
    public static final String MODE_JOIN = "join";
    public static final String VERSION_MISMATCH = "VERSION_MISMATCH";
    public static final String DEVICE_ALREADY_REGISTERED = "DEVICE_ALREADY_REGISTERED";
    public static final String PAIRING_EXPIRED = "PAIRING_EXPIRED";

    private String scannedQrData;
    private SyncOnboardingFlavorDelegate flavorDelegate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_onboarding);

        flavorDelegate = new SyncOnboardingFlavorDelegate(this, rawData -> {
            scannedQrData = extractDataFromLink(rawData);
            promptForPinAndConfirmJoin();
        });

        android.net.Uri data = getIntent().getData();
        if (data != null && ("dispensa".equals(data.getScheme()) || "https".equals(data.getScheme()))) {
            // Started via Deep Link or App Link -> require explicit PIN entry and confirmation
            scannedQrData = data.getQueryParameter("data");
            if (scannedQrData != null && !scannedQrData.trim().isEmpty()) {
                setupJoinMode();
                promptForPinAndConfirmJoin();
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
        if (flavorDelegate != null) {
            flavorDelegate.onResume(scannedQrData != null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (flavorDelegate != null) {
            flavorDelegate.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (flavorDelegate != null) {
            flavorDelegate.onDestroy();
        }
    }

    @SuppressLint("CheckResult")
    private void setupShareMode() {
        TextView instruction = findViewById(R.id.tv_onboarding_instruction);
        instruction.setText(R.string.share_pantry);

        ImageView qrView = findViewById(R.id.iv_qr_code);
        Button shareBtn = findViewById(R.id.btn_share_link);

        Dispensa targetDispensa = IntentCompat.getSerializableExtra(getIntent(), ManageDevicesActivity.PANTRY_ID, Dispensa.class);

        Single<Dispensa> dispSingle;
        if (targetDispensa != null) {
            dispSingle = Single.just(targetDispensa);
        } else {
            dispSingle = Repository.getInstance(getApplication()).getCurrentDispensaSingle();
        }

        dispSingle.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(disp -> {
                    Schedulers.io().scheduleDirect(() -> {
                        AppDatabase db = AppDatabase.getDatabase(this);
                        JoinedPantryConfig config = db.joinedPantryConfigDao().getConfigByDispensaId(disp.id);
                        boolean isGDrive = flavorDelegate.isGDriveShare(config);

                        runOnUiThread(() -> {
                            if (isGDrive) {
                                flavorDelegate.setupGDriveShareUI(disp, config, qrView, shareBtn);
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

    @SuppressLint("CheckResult")
    private void setupStandardShareUI(Dispensa disp, JoinedPantryConfig config, ImageView qrView, Button shareBtn) {
        qrView.setVisibility(View.VISIBLE);
        if (shareBtn != null) shareBtn.setVisibility(View.VISIBLE);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String url = prefs.getString(SyncManager.KEY_WEBDAV_URL, "");
        String user = prefs.getString(SyncManager.KEY_WEBDAV_USER, "");
        String pass = prefs.getString(SyncManager.KEY_WEBDAV_PASS, "");
        String path = prefs.getString(SyncManager.KEY_WEBDAV_PATH, SyncManager.DEFAULT_PATH);
        String pantryKey = prefs.getString(SyncManager.SYNC_WEBDAV_PANTRY_KEY, "");
        boolean isShared = prefs.getBoolean(SyncManager.KEY_WEBDAV_MODE_SHARED, false);

        Single.fromCallable(() -> {
            String deviceId = InstallationIdProvider.getOrCreateInstallationId(this);
            String deviceName = prefs.getString(SyncManager.KEY_DEVICE_NAME, android.os.Build.MODEL);

            String remoteId = (disp.remoteId != null && !disp.remoteId.trim().isEmpty())
                    ? disp.remoteId.trim()
                    : UUID.randomUUID().toString();
            if (disp.remoteId == null || disp.remoteId.trim().isEmpty()) {
                disp.remoteId = remoteId;
                AppDatabase.getDatabase(this).dispensaDao().update(disp);
            }

            String ownerDeviceId = (disp.deviceOwnerId != null && !disp.deviceOwnerId.trim().isEmpty())
                    ? disp.deviceOwnerId.trim()
                    : deviceId;
            if (disp.deviceOwnerId == null || disp.deviceOwnerId.trim().isEmpty()) {
                disp.deviceOwnerId = ownerDeviceId;
                AppDatabase.getDatabase(this).dispensaDao().update(disp);
            }

            String pantryPath = PantrySharingService.getInstance().getPantryPath(this, disp, null);
            SharedFolderStore store = PantrySharingService.getInstance().createStore(this, disp, null);
            FolderSyncEngine ownerEngine = new FolderSyncEngine(
                    store,
                    new RoomPantryDataBridge(AppDatabase.getDatabase(this)),
                    new SyncCursorStoreImpl(this, disp.id),
                    deviceId,
                    deviceName,
                    pantryPath,
                    disp.id
            );
            ownerEngine.initializeRemoteStructure().blockingAwait();
            ownerEngine.forceCompaction();

            String plainPass = CredentialCipher.decryptIfNeeded(pass);
            WebDavConfig webDavConfig = new WebDavConfig(url, user, plainPass, path, pantryKey, disp.getName(), ownerDeviceId, remoteId, isShared);
            PairingPayload payload = WebDavPairingHandler.createPayload(deviceName, webDavConfig);
            payload.version = SyncManager.CURRENT_SYNC_VERSION;

            String pairingPin = OnboardingCoordinator.generatePairingCode();
            PairingPayloadCodecImpl codec = new PairingPayloadCodecImpl(pairingPin);
            String wireData = codec.encode(payload);
            String deepLink = "https://enricofrigo.github.io/dispensa/syncjoin?data=" + android.net.Uri.encode(wireData);
            Bitmap qrBitmap = QrCodeGenerator.generate(deepLink, 512);
            return new ShareInfo(deepLink, qrBitmap, null, pairingPin);
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(info -> {
            qrView.setImageBitmap(info.qrBitmap);
            TextView instruction = findViewById(R.id.tv_onboarding_instruction);
            if (instruction != null && info.pairingPin != null) {
                String formattedPin = info.pairingPin.substring(0, 3) + "-" + info.pairingPin.substring(3);
                instruction.setText(getString(R.string.share_pantry) + "\nPIN: " + formattedPin);
            }

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
            SyncCoordinatorImpl.getInstance(this).triggerManualSync();
        }, throwable -> {
            Log.e("SyncOnboarding", "Errore generazione QR", throwable);
            Toast.makeText(this, "Errore generazione QR: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    public static class ShareInfo {
        public final String deepLink;
        public final Bitmap qrBitmap;
        public final String recipientEmail;
        public final String pairingPin;

        public ShareInfo(String deepLink, Bitmap qrBitmap, String recipientEmail, String pairingPin) {
            this.deepLink = deepLink;
            this.qrBitmap = qrBitmap;
            this.recipientEmail = recipientEmail;
            this.pairingPin = pairingPin;
        }
    }

    private void setupJoinMode() {
        TextView instruction = findViewById(R.id.tv_onboarding_instruction);
        instruction.setText(R.string.join_pantry);
        flavorDelegate.initScanner(scannedQrData != null);
    }

    private void promptForPinAndConfirmJoin() {
        if (scannedQrData == null) return;
        flavorDelegate.hideScanner();

        View tilPin = findViewById(R.id.til_pairing_code);
        com.google.android.material.textfield.TextInputEditText etPin = findViewById(R.id.et_pairing_code);
        Button btnConfirm = findViewById(R.id.btn_confirm_onboarding);

        if (tilPin != null) tilPin.setVisibility(View.VISIBLE);
        if (btnConfirm != null) {
            btnConfirm.setVisibility(View.VISIBLE);
            btnConfirm.setEnabled(true);
            btnConfirm.setOnClickListener(v -> {
                String enteredPin = etPin != null && etPin.getText() != null
                        ? OnboardingCoordinator.normalizePairingCode(etPin.getText().toString())
                        : "";
                if (enteredPin.length() != 6) {
                    Toast.makeText(this, R.string.sync_pairing_error, Toast.LENGTH_SHORT).show();
                    return;
                }
                processScannedData(enteredPin);
            });
        }
    }

    @SuppressLint("CheckResult")
    private void processScannedData(String pairingPin) {
        if (scannedQrData == null) return;

        flavorDelegate.hideScanner();
        View btnConfirm = findViewById(R.id.btn_confirm_onboarding);
        if (btnConfirm != null) btnConfirm.setEnabled(false);

        new OnboardingCoordinator().joinPantry(pairingPin, scannedQrData)
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
                    return flavorDelegate.validateAndRegisterGDriveJoin(payload);
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
                newDispensa.deviceOwnerId = (ownerId != null && !ownerId.trim().isEmpty()) ? ownerId.trim() : "REMOTE_OWNER";
                newDispensa.deviceOwnerName = ownerName;
                newDispensa.remoteId = remoteId;

                long id = Repository.getInstance(getApplication()).insertDispensaSync(newDispensa, true);

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
                    config.remotePantryId = remoteId;
                }
                Repository.getInstance(getApplication()).insertJoinedPantryConfig(config);

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

                String myDeviceId = InstallationIdProvider.getOrCreateInstallationId(this);
                String myDeviceName = PreferenceManager.getDefaultSharedPreferences(this)
                        .getString(SyncManager.KEY_DEVICE_NAME, android.os.Build.MODEL);

                try {
                    if ("gdrive".equals(providerId)) {
                        flavorDelegate.performGDriveEagerSync(config, (int) id, myDeviceId, myDeviceName);
                    } else if ("webdav".equals(providerId)) {
                        String pantryPath = PantrySharingService.getInstance().getPantryPath(this, newDispensa, config);
                        SharedFolderStore store = PantrySharingService.getInstance().createStore(this, newDispensa, config);
                        FolderSyncEngine engine = new FolderSyncEngine(
                                store,
                                new RoomPantryDataBridge(AppDatabase.getDatabase(this)),
                                new SyncCursorStoreImpl(this, (int) id),
                                myDeviceId,
                                myDeviceName,
                                pantryPath,
                                (int) id
                        );
                        engine.sync().blockingAwait();
                    }
                } catch (Exception e) {
                    Log.w("SyncOnboarding", "Initial eager pull encountered an issue; background worker will retry", e);
                }

                return payload;
            }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()))
            .subscribe(payload -> {
                SyncCoordinatorImpl.getInstance(this).applyOnboarding(payload);
                Toast.makeText(this, R.string.sync_pairing_success, Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                finish();
            }, throwable -> {
                Log.e("SyncOnboarding", "Errore join", throwable);
                if (flavorDelegate.handleJoinError(throwable, () -> processScannedData(pairingPin))) {
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
                if (btnConfirm != null) btnConfirm.setEnabled(true);
                if (!PAIRING_EXPIRED.equals(throwable.getMessage())) {
                    promptForPinAndConfirmJoin();
                }
            });
    }

    private String getWebDavPantryPath(PairingPayload payload) {
        String path = payload.data.get("path");
        String remoteId = payload.data.get("remoteId");
        JoinedPantryConfig dummyConfig = new JoinedPantryConfig();
        dummyConfig.path = path;
        dummyConfig.remotePantryId = remoteId;
        Dispensa dummyDisp = new Dispensa();
        dummyDisp.remoteId = remoteId;
        return PantrySharingService.getInstance().getPantryPath(this, dummyDisp, dummyConfig);
    }

    private SharedFolderStore getWebDavStore(PairingPayload payload) {
        String url = payload.data.get("url");
        String user = payload.data.get("user");
        String pass = payload.data.get("pass");
        WebDavClient client = WebDavClientFactory.getInstance().getClient(url, user, pass);
        return new WebDavFolderStore(client);
    }

    private Single<Boolean> checkVersionCompatibility(PairingPayload payload) {
        return Single.fromCallable(() -> {
            String url = payload.data.get("url");
            String pass = payload.data.get("pass");
            if (url == null || pass == null) return false;

            String pantryPath = getWebDavPantryPath(payload);
            SharedFolderStore store = getWebDavStore(payload);
            String manifestPath = pantryPath + SyncManager.MANIFEST_JSON;

            Boolean exists = store.exists(manifestPath).blockingGet();
            if (exists == null || !exists) return false;

            byte[] data = store.read(manifestPath).blockingGet();
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

    private Single<Boolean> checkDeviceAlreadyRegistered(PairingPayload payload) {
        return Single.fromCallable(() -> {
            String url = payload.data.get("url");
            String pass = payload.data.get("pass");
            if (url == null || pass == null) return false;

            String pantryPath = getWebDavPantryPath(payload);
            SharedFolderStore store = getWebDavStore(payload);
            String deviceId = InstallationIdProvider.getOrCreateInstallationId(this);
            String devicePath = pantryPath + SyncManager.DEFAULT_DEVICES_FOLDER + deviceId + ".json";

            Boolean exists = store.exists(devicePath).blockingGet();
            return exists != null && exists;
        });
    }

    private Single<Boolean> registerDevice(PairingPayload payload) {
        return Single.fromCallable(() -> {
            String url = payload.data.get("url");
            String pass = payload.data.get("pass");
            if (url == null || pass == null) return false;

            String pantryPath = getWebDavPantryPath(payload);
            SharedFolderStore store = getWebDavStore(payload);
            String deviceId = InstallationIdProvider.getOrCreateInstallationId(this);
            String deviceName = PreferenceManager.getDefaultSharedPreferences(this)
                    .getString(SyncManager.KEY_DEVICE_NAME, android.os.Build.MODEL);

            PantryDevice device = new PantryDevice(deviceId, deviceName);
            String deviceJson = new com.google.gson.Gson().toJson(device);
            String devicePath = pantryPath + SyncManager.DEFAULT_DEVICES_FOLDER + deviceId + ".json";

            store.writeAtomic(devicePath, deviceJson.getBytes(StandardCharsets.UTF_8), null).blockingAwait();
            return true;
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
