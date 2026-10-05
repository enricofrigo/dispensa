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
import androidx.preference.PreferenceManager;

import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.sync.QrCodeGenerator;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.pairing.OnboardingCoordinator;
import eu.frigo.dispensa.sync.core.pairing.PairingPayload;
import eu.frigo.dispensa.sync.core.pairing.PairingPayloadCodecImpl;
import eu.frigo.dispensa.sync.webdav.WebDavConfig;
import eu.frigo.dispensa.sync.webdav.WebDavPairingHandler;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import eu.frigo.dispensa.sync.webdav.client.WebDavClientFactory;
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
    public static final java.lang.String DEVICE_ALREADY_REGISTERED = "DEVICE_ALREADY_REGISTERED";
    public static final java.lang.String VERSION_MISMATCH = "VERSION_MISMATCH";
    public static final java.lang.String PAIRING_EXPIRED = "PAIRING_EXPIRED";

    private String scannedQrData;
    private DecoratedBarcodeView barcodeView;

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
        if (barcodeView != null) {
            barcodeView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (barcodeView != null) {
            barcodeView.pause();
        }
    }

    private void setupShareMode() {
        TextView instruction = findViewById(R.id.tv_onboarding_instruction);
        instruction.setText(R.string.share_pantry);
        
        ImageView qrView = findViewById(R.id.iv_qr_code);
        Button shareBtn = findViewById(R.id.btn_share_link);
        
        qrView.setVisibility(View.VISIBLE);
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

        // Recupera la dispensa passata come extra
        Dispensa targetDispensa = (Dispensa) getIntent().getSerializableExtra(ManageDevicesActivity.PANTRY_ID);
        
        Single<eu.frigo.dispensa.data.dispensa.Dispensa> dispSingle;
        if (targetDispensa != null) {
            dispSingle = Single.just(targetDispensa);
        } else {
            dispSingle = eu.frigo.dispensa.data.Repository.getInstance(getApplication()).getCurrentDispensaSingle();
        }

        dispSingle.subscribeOn(Schedulers.io())
                .flatMap(disp -> Single.fromCallable(() -> {
                    String deviceId = eu.frigo.dispensa.sync.core.engine.InstallationIdProvider.getOrCreateInstallationId(this);
                    WebDavConfig config = new WebDavConfig(url, user, pass, path, pantryKey, disp.getName(), deviceId, disp.remoteId, isShared);
                    String deviceName = android.os.Build.MODEL;
                    PairingPayload payload = WebDavPairingHandler.createPayload(deviceName, config);
                    PairingPayloadCodecImpl codec = new PairingPayloadCodecImpl(null); // Use internal key
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

        ShareInfo(String deepLink, Bitmap qrBitmap) {
            this.deepLink = deepLink;
            this.qrBitmap = qrBitmap;
        }
    }

    @SuppressLint("CheckResult")
    private void setupJoinMode() {
        TextView instruction = findViewById(R.id.tv_onboarding_instruction);
        instruction.setText(R.string.join_pantry);

        barcodeView = findViewById(R.id.zxing_barcode_scanner);
        barcodeView.setVisibility(View.VISIBLE);
        barcodeView.setStatusText(getString(R.string.add_product_camera_preview_hint));

        barcodeView.decodeSingle(result -> {
            String rawData = result.getText();
            scannedQrData = extractDataFromLink(rawData);
            Log.d("SyncOnboarding", "QR scansionato con successo");
            runOnUiThread(this::processScannedData);
        });
    }

    private void processScannedData() {
        if (scannedQrData == null) return;
        
        barcodeView.setVisibility(View.GONE);
        findViewById(R.id.btn_confirm_onboarding).setVisibility(View.GONE); // Non serve più conferma manuale

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
                }
                return Single.just(payload);
            })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .flatMap(payload -> Single.fromCallable(() -> {
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
                JoinedPantryConfig config = new JoinedPantryConfig(
                        (int) id,
                        payload.data.get("url"),
                        payload.data.get("user"),
                        payload.data.get("pass"),
                        payload.data.get("path"),
                        Boolean.parseBoolean(payload.data.get("isShared")),
                        payload.data.get("pantryKey")
                );
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
                if (PAIRING_EXPIRED.equals(throwable.getMessage())) {
                    Toast.makeText(this, R.string.sync_pairing_expired, Toast.LENGTH_LONG).show();
                } else if (DEVICE_ALREADY_REGISTERED.equals(throwable.getMessage())) {
                    Toast.makeText(this, "Questo dispositivo è già registrato in questa dispensa.", Toast.LENGTH_LONG).show();
                } else if (VERSION_MISMATCH.equals(throwable.getMessage())) {
                    Toast.makeText(this, "Incompatibilità Versione: La dispensa remota non è compatibile con questa app.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, R.string.sync_pairing_error, Toast.LENGTH_LONG).show();
                }
                // Riavvia lo scanner se è fallito ma non scaduto
                if (!PAIRING_EXPIRED.equals(throwable.getMessage())) {
                    barcodeView.setVisibility(View.VISIBLE);
                    barcodeView.decodeSingle(result -> {
                        scannedQrData = extractDataFromLink(result.getText());
                        runOnUiThread(this::processScannedData);
                    });
                }
            });
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

            WebDavClient client = WebDavClientFactory.getInstance().getClient(url, user, pass);
            try (Response response = client.get(manifestPath)) {
                if (response.isSuccessful() && response.body() != null) {
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
