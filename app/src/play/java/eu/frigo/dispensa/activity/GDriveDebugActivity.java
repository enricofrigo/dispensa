package eu.frigo.dispensa.activity;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.dispensa.Dispensa;
import eu.frigo.dispensa.data.sync.JoinedPantryConfig;
import eu.frigo.dispensa.data.sync.RoomPantryDataBridge;
import eu.frigo.dispensa.sync.core.engine.FolderSyncEngine;
import eu.frigo.dispensa.sync.core.engine.InstallationIdProvider;
import eu.frigo.dispensa.sync.core.engine.SyncCoordinatorImpl;
import eu.frigo.dispensa.sync.core.engine.SyncManager;
import eu.frigo.dispensa.sync.core.model.PantryManifest;
import eu.frigo.dispensa.sync.core.policy.SyncPolicy;
import eu.frigo.dispensa.sync.core.store.SyncCursorStore;
import eu.frigo.dispensa.sync.core.store.SyncCursorStoreImpl;
import eu.frigo.dispensa.sync.gdrive.auth.GDriveAuthManager;
import eu.frigo.dispensa.sync.gdrive.client.GDriveClient;
import eu.frigo.dispensa.sync.gdrive.client.GDriveClient.DriveFile;
import eu.frigo.dispensa.sync.gdrive.store.GDriveFolderStore;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Attività di Debug SOLO PER SVILUPPO (non destinata alla produzione).
 * Permette di ispezionare, navigare ed eseguire diagnosi sulle cartelle e sui file
 * creati dall'app su Google Drive.
 */
public class GDriveDebugActivity extends AppCompatActivity {
    private static final String TAG = "GDriveDebug";

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private GDriveAuthManager authManager;
    private GDriveClient client;
    private AppDatabase db;

    // UI elements
    private TextView tvAccountInfo;
    private MaterialButton btnTestConn;
    private MaterialButton btnEnableSync;
    private MaterialButton btnTriggerSync;

    private MaterialButton btnCheckPantries;
    private LinearLayout layoutPantriesContainer;

    private TextView tvCurrentPath;
    private MaterialButton btnNavUp;
    private MaterialButton btnNavRoot;
    private MaterialButton btnRefreshFolder;
    private MaterialButton btnShowAllFiles;
    private ProgressBar progressExplorer;
    private TextView tvEmptyFolder;
    private LinearLayout layoutFilesContainer;

    private TextView tvLog;

    // Navigation state
    private static class FolderStep {
        final String id;
        final String name;
        FolderStep(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
    private final Stack<FolderStep> navStack = new Stack<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gdrive_debug);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_debug);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        authManager = new GDriveAuthManager(this);
        client = new GDriveClient(authManager);
        db = AppDatabase.getDatabase(this);

        initViews();
        setupListeners();

        // Start at root
        navStack.clear();
        navStack.push(new FolderStep("root", "Root (Radice Drive)"));

        updateAccountCard();
        refreshCurrentFolder();
        checkPantriesRemoteStructure();
    }

    private void initViews() {
        tvAccountInfo = findViewById(R.id.tv_debug_account_info);
        btnTestConn = findViewById(R.id.btn_debug_test_conn);
        btnEnableSync = findViewById(R.id.btn_debug_enable_sync);
        btnTriggerSync = findViewById(R.id.btn_debug_trigger_sync);

        btnCheckPantries = findViewById(R.id.btn_debug_check_pantries);
        layoutPantriesContainer = findViewById(R.id.layout_debug_pantries_container);

        tvCurrentPath = findViewById(R.id.tv_debug_current_path);
        btnNavUp = findViewById(R.id.btn_debug_nav_up);
        btnNavRoot = findViewById(R.id.btn_debug_nav_root);
        btnRefreshFolder = findViewById(R.id.btn_debug_refresh_folder);
        btnShowAllFiles = findViewById(R.id.btn_debug_show_all_files);
        progressExplorer = findViewById(R.id.progress_debug_explorer);
        tvEmptyFolder = findViewById(R.id.tv_debug_empty_folder);
        layoutFilesContainer = findViewById(R.id.layout_debug_files_container);

        tvLog = findViewById(R.id.tv_debug_log);
        findViewById(R.id.btn_debug_clear_log).setOnClickListener(v -> tvLog.setText(""));
    }

    private void setupListeners() {
        btnTestConn.setOnClickListener(v -> testConnection());

        btnEnableSync.setOnClickListener(v -> {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putBoolean(SyncManager.KEY_SYNC_ENABLED, true).apply();
            log("✔ Impostato pref_sync_enabled = TRUE");
            updateAccountCard();
            Toast.makeText(this, "Sync abilitato nelle preferenze", Toast.LENGTH_SHORT).show();
        });

        btnTriggerSync.setOnClickListener(v -> {
            log("🚀 Avvio triggerManualSync() tramite SyncCoordinator...");
            SyncCoordinatorImpl.getInstance(this).triggerManualSync();
            Toast.makeText(this, "Sync WorkManager accodato", Toast.LENGTH_SHORT).show();
        });

        btnCheckPantries.setOnClickListener(v -> checkPantriesRemoteStructure());

        btnNavUp.setOnClickListener(v -> {
            if (navStack.size() > 1) {
                navStack.pop();
                refreshCurrentFolder();
            } else {
                Toast.makeText(this, "Ti trovi già nella radice", Toast.LENGTH_SHORT).show();
            }
        });

        btnNavRoot.setOnClickListener(v -> {
            navStack.clear();
            navStack.push(new FolderStep("root", "Root (Radice Drive)"));
            refreshCurrentFolder();
        });

        btnRefreshFolder.setOnClickListener(v -> refreshCurrentFolder());

        btnShowAllFiles.setOnClickListener(v -> loadAllAppFiles());
    }

    private void log(String message) {
        String time = timeFormat.format(new Date());
        String line = "[" + time + "] " + message + "\n";
        Log.d(TAG, message);
        runOnUiThread(() -> {
            if (tvLog != null) {
                tvLog.append(line);
            }
        });
    }

    private void updateAccountCard() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean signedIn = authManager.isSignedIn();
        String email = authManager.getAccountEmail();
        boolean syncEnabled = prefs.getBoolean(SyncManager.KEY_SYNC_ENABLED, false);
        String syncedIds = prefs.getString(SyncManager.SYNC_WEBDAV_SYNCED_IDS, "(nessuno)");
        String rootFolderPref = prefs.getString(GDriveAuthManager.PREF_KEY_GDRIVE_ROOT_FOLDER_ID, "root");
        String deviceId = InstallationIdProvider.getOrCreateInstallationId(this);

        StringBuilder sb = new StringBuilder();
        sb.append("• Google Account: ").append(signedIn ? (email != null ? email : "Connesso") : "NON CONNESSO").append("\n");
        sb.append("• pref_sync_enabled: ").append(syncEnabled ? "TRUE (ATTIVO)" : "FALSE (DISATTIVATO)").append("\n");
        sb.append("• sync_webdav_synced_ids: ").append(syncedIds.isEmpty() ? "(vuoto)" : syncedIds).append("\n");
        sb.append("• sync_gdrive_root_folder_id: ").append(rootFolderPref).append("\n");
        sb.append("• Device ID locale: ").append(deviceId);

        tvAccountInfo.setText(sb.toString());
        tvAccountInfo.setTextColor(signedIn && syncEnabled ? Color.parseColor("#1B5E20") : Color.parseColor("#B71C1C"));
    }

    private void testConnection() {
        log("Testing connessione Google Drive API...");
        disposables.add(Single.fromCallable(() -> {
            String token = authManager.getAccessToken();
            boolean ok = client.testConnection();
            return new Object[]{ok, token};
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(res -> {
                    boolean ok = (boolean) res[0];
                    String token = (String) res[1];
                    String tokenPreview = token != null && token.length() > 10 ? token.substring(0, 8) + "..." : "(null)";
                    log("Esito connessione: " + (ok ? "SUCCESSO (Token: " + tokenPreview + ")" : "FALLITO"));
                    Toast.makeText(this, ok ? "Connessione Google Drive OK!" : "Errore connessione!", Toast.LENGTH_SHORT).show();
                    updateAccountCard();
                }, err -> {
                    log("ERRORE durante test token: " + err.getMessage());
                    Toast.makeText(this, "Errore: " + err.getMessage(), Toast.LENGTH_LONG).show();
                }));
    }

    // =========================================================================
    // SEZIONE 2: VERIFICA DISPENSE
    // =========================================================================

    private static class PantryStatus {
        Dispensa dispensa;
        JoinedPantryConfig config;
        String syncPath;
        boolean folderExists;
        String folderId;
        PantryManifest manifest;
        List<DriveFile> deviceFiles = new ArrayList<>();
        List<DriveFile> eventFiles = new ArrayList<>();
        List<DriveFile> snapshotFiles = new ArrayList<>();
        String errorMsg;
    }

    @SuppressLint("CheckResult")
    private void checkPantriesRemoteStructure() {
        layoutPantriesContainer.removeAllViews();
        log("Controllo struttura remota dispense nel DB...");

        disposables.add(Single.fromCallable(() -> {
            List<Dispensa> allDispense = db.dispensaDao().getAllDispense().getValue();
            List<PantryStatus> statuses = new ArrayList<>();

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String rootFolderId = prefs.getString(GDriveAuthManager.PREF_KEY_GDRIVE_ROOT_FOLDER_ID, "root");

            for (Dispensa d : allDispense) {
                PantryStatus ps = new PantryStatus();
                ps.dispensa = d;
                ps.config = db.joinedPantryConfigDao().getConfigByDispensaId(d.id);

                String remoteId = (d.remoteId != null && !d.remoteId.trim().isEmpty())
                        ? d.remoteId.trim()
                        : (ps.config != null && ps.config.remotePantryId != null ? ps.config.remotePantryId : String.valueOf(d.id));
                ps.syncPath = SyncManager.getSyncPath(remoteId);

                try {
                    String folderConfigId = (ps.config != null && ps.config.url != null && !ps.config.url.isEmpty())
                            ? ps.config.url : rootFolderId;
                    GDriveFolderStore store = new GDriveFolderStore(client, folderConfigId);

                    Boolean exists = store.exists(ps.syncPath).blockingGet();
                    ps.folderExists = exists != null && exists;

                    if (ps.folderExists) {
                        // Cerca ID della cartella per navigazione
                        DriveFile folderFile = client.findFile(folderConfigId, ps.syncPath.replace("/", ""));
                        if (folderFile != null) {
                            ps.folderId = folderFile.id;
                        }

                        // Leggi manifest
                        RoomPantryDataBridge bridge = new RoomPantryDataBridge(db);
                        SyncCursorStore cursor = new SyncCursorStoreImpl(this);
                        FolderSyncEngine engine = new FolderSyncEngine(
                                store, bridge, cursor,
                                InstallationIdProvider.getOrCreateInstallationId(this),
                                Build.MODEL, ps.syncPath, d.id
                        );
                        ps.manifest = engine.fetchManifest();

                        // Controlla sottocartelle
                        if (folderFile != null) {
                            DriveFile devDir = client.findFile(folderFile.id, "devices");
                            if (devDir != null) ps.deviceFiles = client.listFiles(devDir.id);

                            DriveFile evDir = client.findFile(folderFile.id, "events");
                            if (evDir != null) ps.eventFiles = client.listFiles(evDir.id);

                            DriveFile snapDir = client.findFile(folderFile.id, "snapshots");
                            if (snapDir != null) ps.snapshotFiles = client.listFiles(snapDir.id);
                        }
                    }
                } catch (Exception e) {
                    ps.errorMsg = e.getMessage();
                }
                statuses.add(ps);
            }
            return statuses;
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::renderPantriesStatus, err -> {
                    log("ERRORE verifica dispense: " + err.getMessage());
                    Toast.makeText(this, "Errore verifica: " + err.getMessage(), Toast.LENGTH_SHORT).show();
                }));
    }

    private void renderPantriesStatus(List<PantryStatus> statuses) {
        layoutPantriesContainer.removeAllViews();
        if (statuses.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("Nessuna dispensa presente nel database.");
            layoutPantriesContainer.addView(tv);
            return;
        }

        for (PantryStatus ps : statuses) {
            MaterialCardView card = new MaterialCardView(this);
            card.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            card.setCardElevation(1f);
            card.setRadius(8f);
            card.setUseCompatPadding(true);

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(24, 20, 24, 20);

            // Header dispensa
            TextView tvTitle = new TextView(this);
            tvTitle.setText(String.format(Locale.getDefault(), "#%d: %s", ps.dispensa.id, ps.dispensa.getName()));
            tvTitle.setTextSize(15f);
            tvTitle.setTypeface(null, Typeface.BOLD);
            content.addView(tvTitle);

            // Subtitle
            TextView tvSub = new TextView(this);
            tvSub.setTextSize(11f);

            StringBuilder sb = new StringBuilder();
            sb.append("remoteId: ").append(ps.dispensa.remoteId != null ? ps.dispensa.remoteId : "(null)").append("\n");
            sb.append("providerId: ").append(ps.config != null ? ps.config.providerId : "nessuna config").append("\n");
            sb.append("cartella attesa: ").append(ps.syncPath).append("\n");

            if (ps.errorMsg != null) {
                sb.append("⚠ ERRORE: ").append(ps.errorMsg);
                tvSub.setTextColor(Color.RED);
            } else if (ps.folderExists) {
                sb.append("✔ CARTELLA SU DRIVE: TROVATA");
                if (ps.folderId != null) sb.append(" (ID: ").append(ps.folderId).append(")");
                sb.append("\n");

                if (ps.manifest != null) {
                    sb.append("✔ manifest.json: v").append(ps.manifest.version)
                            .append(" | snap: ").append(ps.manifest.latestSnapshotId != null ? ps.manifest.latestSnapshotId : "(nessuno)")
                            .append(" | eventi: ").append(ps.manifest.activeEventFiles != null ? ps.manifest.activeEventFiles.size() : 0)
                            .append("\n");
                } else {
                    sb.append("❌ manifest.json: ASSENTE!\n");
                }

                sb.append("• devices/ : ").append(ps.deviceFiles.size()).append(" file\n");
                sb.append("• events/  : ").append(ps.eventFiles.size()).append(" file\n");
                sb.append("• snapshots: ").append(ps.snapshotFiles.size()).append(" file");
                tvSub.setTextColor(Color.parseColor("#1B5E20"));
            } else {
                sb.append("❌ CARTELLA SU DRIVE: NON TROVATA!");
                tvSub.setTextColor(Color.parseColor("#B71C1C"));
            }

            tvSub.setText(sb.toString());
            tvSub.setPadding(0, 8, 0, 12);
            content.addView(tvSub);

            // Action buttons row
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);

            // 1. Sync button
            MaterialButton btnSync = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btnSync.setText("⚡ Sync");
            btnSync.setTextSize(11f);
            btnSync.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            btnSync.setOnClickListener(v -> executeDirectSync(ps.dispensa));
            actions.addView(btnSync);

            // 2. Init remote structure button
            MaterialButton btnInit = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btnInit.setText("🛠 Inizializza");
            btnInit.setTextSize(11f);
            btnInit.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            btnInit.setOnClickListener(v -> executeInitRemote(ps.dispensa));
            actions.addView(btnInit);

            // 3. Force compaction / snapshot
            MaterialButton btnCompact = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btnCompact.setText("📦 Snapshot");
            btnCompact.setTextSize(11f);
            btnCompact.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            btnCompact.setOnClickListener(v -> executeForceCompaction(ps.dispensa));
            actions.addView(btnCompact);

            // 4. Open in explorer
            if (ps.folderId != null) {
                MaterialButton btnOpen = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                btnOpen.setText("📂 Apri");
                btnOpen.setTextSize(11f);
                btnOpen.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                btnOpen.setOnClickListener(v -> {
                    navStack.push(new FolderStep(ps.folderId, ps.syncPath));
                    refreshCurrentFolder();
                    Toast.makeText(this, "Aperta cartella " + ps.syncPath, Toast.LENGTH_SHORT).show();
                });
                actions.addView(btnOpen);
            }

            content.addView(actions);
            card.addView(content);
            layoutPantriesContainer.addView(card);
        }
    }

    private void executeDirectSync(Dispensa dispensa) {
        log("Esecuzione performSync() diretta per dispensa #" + dispensa.id + " (" + dispensa.getName() + ")...");
        disposables.add(Single.fromCallable(() -> {
            FolderSyncEngine engine = createEngineForDispensa(dispensa);
            engine.performSync(new SyncPolicy() {
                @Override public boolean canSyncNow() { return true; }
                @Override public long getRetryIntervalMillis() { return 0; }
            }).blockingAwait();
            return true;
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ok -> {
                    log("✔ performSync() completato con successo per #" + dispensa.id);
                    Toast.makeText(this, "Sync completato con successo!", Toast.LENGTH_SHORT).show();
                    checkPantriesRemoteStructure();
                    refreshCurrentFolder();
                }, err -> {
                    log("❌ ERRORE durante performSync() per #" + dispensa.id + ": " + err.getMessage());
                    Toast.makeText(this, "Errore sync: " + err.getMessage(), Toast.LENGTH_LONG).show();
                }));
    }

    private void executeInitRemote(Dispensa dispensa) {
        log("Inizializzazione struttura remota per #" + dispensa.id + " (" + dispensa.getName() + ")...");
        disposables.add(Single.fromCallable(() -> {
            FolderSyncEngine engine = createEngineForDispensa(dispensa);
            engine.initializeRemoteStructure().blockingAwait();
            return true;
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ok -> {
                    log("✔ initializeRemoteStructure() completato per #" + dispensa.id);
                    Toast.makeText(this, "Struttura remota inizializzata!", Toast.LENGTH_SHORT).show();
                    checkPantriesRemoteStructure();
                    refreshCurrentFolder();
                }, err -> {
                    log("❌ ERRORE inizializzazione remota: " + err.getMessage());
                    Toast.makeText(this, "Errore: " + err.getMessage(), Toast.LENGTH_LONG).show();
                }));
    }

    private void executeForceCompaction(Dispensa dispensa) {
        log("Creazione forzata snapshot (compaction) per #" + dispensa.id + " (" + dispensa.getName() + ")...");
        disposables.add(Single.fromCallable(() -> {
            FolderSyncEngine engine = createEngineForDispensa(dispensa);
            engine.forceCompaction();
            return true;
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ok -> {
                    log("✔ Snapshot creato e caricato su Drive per #" + dispensa.id);
                    Toast.makeText(this, "Snapshot creato e caricato!", Toast.LENGTH_SHORT).show();
                    checkPantriesRemoteStructure();
                    refreshCurrentFolder();
                }, err -> {
                    log("❌ ERRORE creazione snapshot: " + err.getMessage());
                    Toast.makeText(this, "Errore: " + err.getMessage(), Toast.LENGTH_LONG).show();
                }));
    }

    private FolderSyncEngine createEngineForDispensa(Dispensa dispensa) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        JoinedPantryConfig config = db.joinedPantryConfigDao().getConfigByDispensaId(dispensa.id);
        String remoteId = (dispensa.remoteId != null && !dispensa.remoteId.trim().isEmpty())
                ? dispensa.remoteId.trim()
                : (config != null && config.remotePantryId != null ? config.remotePantryId : String.valueOf(dispensa.id));
        String pantryPath = SyncManager.getSyncPath(remoteId);

        String rootFolderId = (config != null && config.url != null && !config.url.isEmpty())
                ? config.url : prefs.getString(GDriveAuthManager.PREF_KEY_GDRIVE_ROOT_FOLDER_ID, "root");

        GDriveFolderStore store = new GDriveFolderStore(client, rootFolderId);
        RoomPantryDataBridge bridge = new RoomPantryDataBridge(db);
        SyncCursorStore cursor = new SyncCursorStoreImpl(this);

        return new FolderSyncEngine(
                store, bridge, cursor,
                InstallationIdProvider.getOrCreateInstallationId(this),
                Build.MODEL, pantryPath, dispensa.id
        );
    }

    // =========================================================================
    // SEZIONE 3: ESPLORATORE FILE & CARTELLE GDRIVE
    // =========================================================================

    private void refreshCurrentFolder() {
        if (navStack.isEmpty()) {
            navStack.push(new FolderStep("root", "Root (Radice Drive)"));
        }
        FolderStep current = navStack.peek();
        updatePathDisplay();
        loadFolder(current.id);
    }

    private void updatePathDisplay() {
        StringBuilder sb = new StringBuilder("📁 Path: ");
        for (int i = 0; i < navStack.size(); i++) {
            if (i > 0) sb.append(" / ");
            sb.append(navStack.get(i).name);
        }
        tvCurrentPath.setText(sb.toString());
        btnNavUp.setEnabled(navStack.size() > 1);
    }

    private void loadFolder(String folderId) {
        progressExplorer.setVisibility(View.VISIBLE);
        layoutFilesContainer.removeAllViews();
        tvEmptyFolder.setVisibility(View.GONE);

        log("Caricamento contenuti cartella (ID: " + folderId + ")...");

        disposables.add(Single.fromCallable(() -> client.listFiles(folderId))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(files -> {
                    progressExplorer.setVisibility(View.GONE);
                    log("Trovati " + files.size() + " elementi in " + folderId);
                    renderFilesList(files, false);
                }, err -> {
                    progressExplorer.setVisibility(View.GONE);
                    log("ERRORE caricamento cartella: " + err.getMessage());
                    tvEmptyFolder.setText("Errore caricamento: " + err.getMessage());
                    tvEmptyFolder.setVisibility(View.VISIBLE);
                }));
    }

    private void loadAllAppFiles() {
        progressExplorer.setVisibility(View.VISIBLE);
        layoutFilesContainer.removeAllViews();
        tvEmptyFolder.setVisibility(View.GONE);
        tvCurrentPath.setText("📁 Vista Globale: TUTTI i file/cartelle dell'app su Google Drive");

        log("Interrogazione globale di tutti i file creati dall'app su Drive (query: trashed = false)...");

        disposables.add(Single.fromCallable(() -> client.searchFiles("trashed = false"))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(files -> {
                    progressExplorer.setVisibility(View.GONE);
                    log("Trovati " + files.size() + " elementi totali su Google Drive per questa app");
                    renderFilesList(files, true);
                }, err -> {
                    progressExplorer.setVisibility(View.GONE);
                    log("ERRORE interrogazione globale: " + err.getMessage());
                    tvEmptyFolder.setText("Errore: " + err.getMessage());
                    tvEmptyFolder.setVisibility(View.VISIBLE);
                }));
    }

    private void renderFilesList(List<DriveFile> files, boolean isGlobalSearch) {
        layoutFilesContainer.removeAllViews();
        if (files.isEmpty()) {
            tvEmptyFolder.setText("Nessun file o cartella trovato.");
            tvEmptyFolder.setVisibility(View.VISIBLE);
            return;
        }

        // Separa cartelle e file (cartelle per prime)
        List<DriveFile> folders = new ArrayList<>();
        List<DriveFile> normalFiles = new ArrayList<>();
        for (DriveFile df : files) {
            if (df.isFolder()) {
                folders.add(df);
            } else {
                normalFiles.add(df);
            }
        }

        List<DriveFile> sorted = new ArrayList<>(folders);
        sorted.addAll(normalFiles);

        for (DriveFile df : sorted) {
            MaterialCardView card = new MaterialCardView(this);
            card.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            card.setCardElevation(1f);
            card.setRadius(6f);
            card.setUseCompatPadding(true);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(16, 12, 16, 12);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

            // Icon
            ImageView icon = new ImageView(this);
            icon.setLayoutParams(new LinearLayout.LayoutParams(60, 60));
            icon.setImageResource(df.isFolder() ? R.drawable.ic_debug_folder_24 : R.drawable.ic_debug_file_24);
            row.addView(icon);

            // Info column
            LinearLayout infoCol = new LinearLayout(this);
            infoCol.setOrientation(LinearLayout.VERTICAL);
            infoCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            infoCol.setPadding(16, 0, 8, 0);

            TextView tvName = new TextView(this);
            tvName.setText(df.name);
            tvName.setTextSize(14f);
            tvName.setTypeface(null, Typeface.BOLD);
            infoCol.addView(tvName);

            TextView tvMeta = new TextView(this);
            tvMeta.setTextSize(10f);
            tvMeta.setTextColor(Color.DKGRAY);

            StringBuilder metaSb = new StringBuilder();
            metaSb.append("ID: ").append(df.id);
            if (df.size != null) {
                metaSb.append(" | ").append(formatFileSize(df.size));
            }
            if (df.modifiedTime != null) {
                metaSb.append("\nModificato: ").append(df.modifiedTime);
            }
            if (isGlobalSearch && df.parents != null && !df.parents.isEmpty()) {
                metaSb.append("\nGenitori: ").append(df.parents);
            }
            tvMeta.setText(metaSb.toString());
            infoCol.addView(tvMeta);

            row.addView(infoCol);

            // Action button
            if (df.isFolder()) {
                MaterialButton btnOpen = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                btnOpen.setText("Apri");
                btnOpen.setTextSize(11f);
                btnOpen.setOnClickListener(v -> {
                    navStack.push(new FolderStep(df.id, df.name));
                    refreshCurrentFolder();
                });
                row.addView(btnOpen);
            } else {
                MaterialButton btnView = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                btnView.setText("Leggi");
                btnView.setTextSize(11f);
                btnView.setOnClickListener(v -> viewFileContent(df));
                row.addView(btnView);
            }

            // Delete button
            MaterialButton btnDelete = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btnDelete.setText("🗑");
            btnDelete.setTextSize(11f);
            btnDelete.setPadding(8, 0, 8, 0);
            btnDelete.setOnClickListener(v -> confirmDelete(df));
            row.addView(btnDelete);

            card.addView(row);
            layoutFilesContainer.addView(card);
        }
    }

    private void viewFileContent(DriveFile file) {
        log("Scaricamento contenuto file: " + file.name + " (" + file.id + ")...");
        progressExplorer.setVisibility(View.VISIBLE);

        disposables.add(Single.fromCallable(() -> client.readFileString(file.id))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(rawContent -> {
                    progressExplorer.setVisibility(View.GONE);
                    log("✔ File scaricato con successo (" + rawContent.length() + " caratteri)");
                    showContentDialog(file, rawContent);
                }, err -> {
                    progressExplorer.setVisibility(View.GONE);
                    log("❌ ERRORE lettura file " + file.name + ": " + err.getMessage());
                    Toast.makeText(this, "Errore lettura: " + err.getMessage(), Toast.LENGTH_SHORT).show();
                }));
    }

    private void showContentDialog(DriveFile file, String rawContent) {
        String formatted = rawContent;
        try {
            JsonElement je = JsonParser.parseString(rawContent);
            formatted = prettyGson.toJson(je);
        } catch (Exception ignored) {}

        final String contentToDisplay = formatted;

        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(32, 24, 32, 16);

        TextView tvMeta = new TextView(this);
        tvMeta.setTextSize(11f);
        tvMeta.setText(String.format("ID: %s\nSize: %s\nMD5: %s\nVersion: %s",
                file.id,
                file.size != null ? formatFileSize(file.size) : "-",
                file.md5Checksum != null ? file.md5Checksum : "-",
                file.version != null ? file.version : "-"));
        tvMeta.setPadding(0, 0, 0, 16);
        dialogLayout.addView(tvMeta);

        androidx.core.widget.NestedScrollView scroller = new androidx.core.widget.NestedScrollView(this);
        scroller.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                800
        ));

        TextView tvBody = new TextView(this);
        tvBody.setTextSize(12f);
        tvBody.setTextIsSelectable(true);
        tvBody.setText(contentToDisplay);
        tvBody.setBackgroundColor(Color.parseColor("#F5F5F5"));
        tvBody.setPadding(16, 16, 16, 16);

        scroller.addView(tvBody);
        dialogLayout.addView(scroller);

        new AlertDialog.Builder(this)
                .setTitle("📄 " + file.name)
                .setView(dialogLayout)
                .setPositiveButton("Copia", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText(file.name, contentToDisplay));
                        Toast.makeText(this, "Copiato negli appunti!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Chiudi", null)
                .show();
    }

    private void confirmDelete(DriveFile file) {
        new AlertDialog.Builder(this)
                .setTitle("Elimina da Google Drive?")
                .setMessage("Vuoi davvero eliminare '" + file.name + "' (" + (file.isFolder() ? "Cartella" : "File") + ")?\nID: " + file.id)
                .setPositiveButton("Elimina", (dialog, which) -> {
                    log("Eliminazione di " + file.name + " (" + file.id + ")...");
                    disposables.add(Single.fromCallable(() -> {
                        client.delete(file.id);
                        return true;
                    }).subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(ok -> {
                                log("✔ Elemento eliminato: " + file.name);
                                Toast.makeText(this, "Eliminato!", Toast.LENGTH_SHORT).show();
                                refreshCurrentFolder();
                                checkPantriesRemoteStructure();
                            }, err -> {
                                log("❌ ERRORE eliminazione: " + err.getMessage());
                                Toast.makeText(this, "Errore eliminazione: " + err.getMessage(), Toast.LENGTH_SHORT).show();
                            }));
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disposables.clear();
    }
}
