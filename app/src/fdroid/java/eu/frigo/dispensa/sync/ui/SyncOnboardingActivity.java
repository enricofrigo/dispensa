package eu.frigo.dispensa.sync.ui;

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

import com.google.android.material.textfield.TextInputLayout;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.util.Objects;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.pairing.OnboardingCoordinator;
import eu.frigo.dispensa.sync.core.pairing.PairingPayload;
import eu.frigo.dispensa.sync.core.pairing.PairingPayloadCodecImpl;
import eu.frigo.dispensa.sync.webdav.WebDavConfig;
import eu.frigo.dispensa.sync.webdav.WebDavPairingHandler;
import eu.frigo.dispensa.sync.webdav.client.WebDavClient;
import eu.frigo.dispensa.sync.webdav.client.WebDavClientFactory;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.Response;

public class SyncOnboardingActivity extends AppCompatActivity {
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_SHARE = "share";
    public static final String MODE_JOIN = "join";

    private String currentPairingCode;
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
        if (barcodeView != null) barcodeView.setVisibility(View.GONE);
        findViewById(R.id.til_pairing_code).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_confirm_onboarding).setVisibility(View.VISIBLE);
        Toast.makeText(this, "Link rilevato. Inserisci il codice di accoppiamento.", Toast.LENGTH_SHORT).show();
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

        try {
            // 1. Prepare WebDAV config
            WebDavConfig config = new WebDavConfig(url, user, pass, path, pantryKey, isShared);
            
            // 2. Create encrypted payload
            String deviceName = android.os.Build.MODEL;
            PairingPayload payload = WebDavPairingHandler.createPayload(deviceName, config);
            
            // 3. Encode with pairing code
            PairingPayloadCodecImpl codec = new PairingPayloadCodecImpl(currentPairingCode);
            String wireData = codec.encode(payload);
            
            // 4. Wrap in Deep Link for easier sharing/scanning
            String deepLink = "https://enricofrigo.github.io/dispensa/syncjoin?data=" + android.net.Uri.encode(wireData);
            
            // 5. Generate QR
            Bitmap qrBitmap = QrCodeGenerator.generate(deepLink, 512);
            qrView.setImageBitmap(qrBitmap);
            
            if (shareBtn != null) {
                shareBtn.setOnClickListener(v -> {
                    Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    sendIntent.putExtra(Intent.EXTRA_TEXT, "Unisciti alla mia dispensa condivisa!\n\nLink: " + deepLink + "\n\nCodice di accoppiamento: " + currentPairingCode);
                    sendIntent.setType("text/plain");

                    Intent shareIntent = Intent.createChooser(sendIntent, null);
                    startActivity(shareIntent);
                });
            }

            Log.d("SyncOnboarding", "QR generato con Deep Link: " + deepLink);

            // FORCE SYNC: L'host carica i suoi dati attuali per renderli disponibili al guest
            eu.frigo.dispensa.sync.core.engine.SyncCoordinatorImpl.getInstance(this).triggerManualSync();
            Log.d("SyncOnboarding", "Triggered manual sync for Host before sharing.");

        } catch (Exception e) {
            Log.e("SyncOnboarding", "Errore nella generazione del QR", e);
            Toast.makeText(this, "Errore generazione QR: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupJoinMode() {
        TextView instruction = findViewById(R.id.tv_onboarding_instruction);
        instruction.setText(R.string.join_pantry);

        barcodeView = findViewById(R.id.zxing_barcode_scanner);
        TextInputLayout til = findViewById(R.id.til_pairing_code);
        com.google.android.material.textfield.TextInputEditText etPairingCode = findViewById(R.id.et_pairing_code);
        Button confirm = findViewById(R.id.btn_confirm_onboarding);

        barcodeView.setVisibility(View.VISIBLE);
        barcodeView.setStatusText(getString(R.string.add_product_camera_preview_hint));

        barcodeView.decodeSingle(result -> {
            String rawData = result.getText();
            scannedQrData = extractDataFromLink(rawData);
            Log.d("SyncOnboarding", "QR scansionato con successo");
            runOnUiThread(() -> {
                barcodeView.setVisibility(View.GONE);
                til.setVisibility(View.VISIBLE);
                confirm.setVisibility(View.VISIBLE);
                Toast.makeText(this, "QR scansionato. Inserisci il codice di accoppiamento.", Toast.LENGTH_SHORT).show();
            });
        });
        
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
                    return checkDeviceAlreadyRegistered(payload)
                            .flatMap(exists -> {
                                if (exists) {
                                    return Single.error(new IllegalStateException("DEVICE_ALREADY_REGISTERED"));
                                }
                                // If not registered, register it now
                                return registerDevice(payload).map(success -> payload);
                            });
                }
                return Single.just(payload);
            })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(payload -> {
                eu.frigo.dispensa.sync.core.engine.SyncCoordinatorImpl.getInstance(this).applyOnboarding(payload);
                Toast.makeText(this, R.string.sync_pairing_success, Toast.LENGTH_LONG).show();
                finish();
            }, throwable -> {
                confirm.setEnabled(true);
                Log.e("SyncOnboarding", "Errore decriptazione pairing", throwable);
                if ("DEVICE_ALREADY_REGISTERED".equals(throwable.getMessage())) {
                    Toast.makeText(this, "Questo dispositivo è già registrato in questa dispensa.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, R.string.sync_pairing_error, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private Single<Boolean> checkDeviceAlreadyRegistered(PairingPayload payload) {
        return Single.fromCallable(() -> {
            String url = payload.data.get("url");
            String user = payload.data.get("user");
            String pass = payload.data.get("pass");
            String path = payload.data.get("path");
            String pantryKey = payload.data.get("pantryKey");
            boolean isShared = Boolean.parseBoolean(payload.data.get("isShared"));

            if (url == null || (!isShared && user == null) || pass == null || pantryKey == null) {
                return false;
            }

            String effectivePath = path != null ? path : SyncManager.DEFAULT_PATH;
            
            String deviceId = eu.frigo.dispensa.sync.core.engine.InstallationIdProvider.getOrCreateInstallationId(this);
            
            String normalizedBase = effectivePath.endsWith("/") ? effectivePath : effectivePath + "/";
            if (normalizedBase.startsWith("/")) normalizedBase = normalizedBase.substring(1);
            String pantryPath = normalizedBase + SyncManager.DEFAULT_PANTRY_PATH + pantryKey + "/";
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
            boolean isShared = Boolean.parseBoolean(payload.data.get("isShared"));

            if (url == null || (!isShared && user == null) || pass == null || pantryKey == null) {
                return false;
            }

            String effectivePath = path != null ? path : SyncManager.DEFAULT_PATH;
            String deviceId = eu.frigo.dispensa.sync.core.engine.InstallationIdProvider.getOrCreateInstallationId(this);
            
            String normalizedBase = effectivePath.endsWith("/") ? effectivePath : effectivePath + "/";
            if (normalizedBase.startsWith("/")) normalizedBase = normalizedBase.substring(1);
            String pantryPath = normalizedBase + SyncManager.DEFAULT_PANTRY_PATH + pantryKey + "/";
            String devicePath = pantryPath + SyncManager.DEFAULT_DEVICES_FOLDER + deviceId + ".json";

            eu.frigo.dispensa.sync.webdav.model.WebDavDevice device = new eu.frigo.dispensa.sync.webdav.model.WebDavDevice();
            device.deviceId = deviceId;
            device.deviceName = android.os.Build.MODEL;
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
