package eu.frigo.dispensa.activity;

import static android.view.View.GONE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.util.Size;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;

import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.Log;
import androidx.preference.PreferenceManager;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.core.content.FileProvider;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.category.CategoryDefinition;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.category.ProductWithCategoryDefinitions;
import eu.frigo.dispensa.data.storage.StorageLocation;
import eu.frigo.dispensa.network.openfoodfacts.OpenFoodFactsApiService;
import eu.frigo.dispensa.network.openfoodfacts.OpenFoodFactsRetrofitClient;
import eu.frigo.dispensa.network.openfoodfacts.model.OpenFoodFactsProductResponse;
import eu.frigo.dispensa.ui.SettingsFragment;
import eu.frigo.dispensa.util.KeyboardUtils;
import eu.frigo.dispensa.viewmodel.AddProductViewModel;
import eu.frigo.dispensa.util.DateConverter;
import retrofit2.Callback;
import retrofit2.Response;

@SuppressLint("UnsafeOptInUsageError")
public class AddProductActivity extends AppCompatActivity {

    private Spinner spinnerStorageLocation;
    private TextInputEditText editTextBarcode;
    private TextInputEditText editTextQuantity;
    private ImageButton buttonDecrementQuantityActivity;
    private ImageButton buttonIncrementQuantityActivity;

    private static final String DEFAULT_QUANTITY = "1";
    private TextInputEditText editTextExpiryDate;
    private AddProductViewModel addProductViewModel;
    private int currentProductId = -1;
    private boolean isEditMode = false;
    private androidx.camera.view.PreviewView previewViewScanner;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ExecutorService cameraExecutor;
    private com.google.mlkit.vision.barcode.BarcodeScanner barcodeScanner;
    private TextRecognizer textRecognizer;
    private boolean isCameraPermissionGranted;
    private TextInputEditText editTextProductName;
    private ImageView imageViewProduct;
    private ImageButton buttonRescanExpiryDate;
    private final Calendar calendar = Calendar.getInstance();
    private String currentProductNameFromApi;
    private String currentImageUrlFromApi;
    private ChipGroup chipGroupCategories;
    private TextInputEditText editTextNewCategory;
    private final Set<String> currentProductTagsSet = new HashSet<>();
    private String preselectedLocationValue = null;
    private final List<StorageLocation> availableLocations = new ArrayList<>();
    private ArrayAdapter<String> locationSpinnerAdapter;
    private String selectedStorageInternalKey;
    private ProductWithCategoryDefinitions productBeingEdited;
    private TextInputEditText editTextShelfLifeAfterOpening;
    private Button buttonMarkAsOpened;
    private Button buttonMarkAsClosed;
    private TextView textViewOpenedDate;
    private Long currentOpenedDate = 0L;
    private int currentShelfLifeDays = -1;
    private String lastDiscardedExpiryDate = null;
    private boolean isRescanning = false;
    public final static String EXTRA_BARCODE = "SCANNED_BARCODE_DATA";
    public final static String EXTRA_PRODUCT_ID = "PRODUCT_ID";
    public final static String EXTRA_PRODUCT_NAME = "PRODUCT_NAME";
    public final static String EXTRA_PRODUCT_IMAGE = "PRODUCT_IMAGE";
    public final static String EXTRA_PRODUCT_QUANTITY = "PRODUCT_QUANTITY";
    public final static String EXTRA_PRODUCT_EXPIRY_DATE = "PRODUCT_EXPIRY_DATE";
    public final static String EXTRA_PRODUCT_BARCODE = "PRODUCT_BARCODE";
    public final static String NEW_PRODUCT_NAME = "NEW_PRODUCT_NAME";
    public final static String NEW_PRODUCT_EDIT = "NEW_PRODUCT_EDIT";
    public final static String PRESELECTED_LOCATION_INTERNAL_KEY = "PRESELECTED_LOCATION_INTERNAL_KEY";

    private ActivityResultLauncher<String> pickImageForScannerLauncher;

    /** URI del file temporaneo per la foto del prodotto scattata con la fotocamera */
    private Uri productPhotoCaptureUri;
    private String currentPhotoPath; // <-- Per salvare il path assoluto

    /** Picker galleria per l'immagine del prodotto */
    private ActivityResultLauncher<String> pickProductImageLauncher;

    /** Fotocamera per scattare una nuova foto del prodotto */
    private ActivityResultLauncher<Uri> takeProductPhotoLauncher;

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    isCameraPermissionGranted = true;
                    Toast.makeText(this, getString(R.string.camera_permission_accepted), Toast.LENGTH_SHORT).show();
                    startCamera();
                } else {
                    isCameraPermissionGranted = false;
                    Toast.makeText(this, getString(R.string.camera_permission_denied), Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<Intent> barcodeScannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String scannedBarcode = result.getData().getStringExtra(EXTRA_BARCODE);
                    if (scannedBarcode != null) {
                        editTextBarcode.setText(scannedBarcode);
                    } else {
                        Toast.makeText(this, getString(R.string.scan_barcode_error), Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @SuppressLint("UnsafeOptInUsageError")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_product);

        View rootLayout = findViewById(R.id.add_product_root_layout);
        View nestedScrollView = findViewById(R.id.add_product_nested_scroll);
        final ExtendedFloatingActionButton fabButtonSaveProduct = findViewById(R.id.buttonSaveProduct);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        prefs.edit().putBoolean(SettingsFragment.KEY_PREF_ENABLE_OFF_API, true).apply();

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());

            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);

            if (nestedScrollView != null) {
                nestedScrollView.setPadding(
                        nestedScrollView.getPaddingLeft(),
                        nestedScrollView.getPaddingTop(),
                        nestedScrollView.getPaddingRight(),
                        Math.max(systemBars.bottom, ime.bottom)
                                + (int) (80 * getResources().getDisplayMetrics().density));
            }

            if (fabButtonSaveProduct != null) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) fabButtonSaveProduct
                        .getLayoutParams();
                lp.bottomMargin = Math.max(systemBars.bottom, ime.bottom)
                        + (int) (16 * getResources().getDisplayMetrics().density);
                fabButtonSaveProduct.setLayoutParams(lp);
            }

            return windowInsets;
        });

        addProductViewModel = new ViewModelProvider(this).get(AddProductViewModel.class);
        Toolbar toolbarAddProduct = findViewById(R.id.toolbar_add_product);
        if (toolbarAddProduct != null) {
            setSupportActionBar(toolbarAddProduct);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle(getString(R.string.add_product));
            }
        }

        editTextBarcode = findViewById(R.id.editTextBarcode);
        ImageButton buttonScanCamera = findViewById(R.id.buttonScanCamera);
        ImageButton buttonScanGallery = findViewById(R.id.buttonScanGallery);
        buttonRescanExpiryDate = findViewById(R.id.buttonRescanExpiryDate);
        editTextQuantity = findViewById(R.id.editTextQuantity);
        buttonDecrementQuantityActivity = findViewById(R.id.buttonDecrementQuantityActivity);
        buttonIncrementQuantityActivity = findViewById(R.id.buttonIncrementQuantityActivity);
        editTextExpiryDate = findViewById(R.id.editTextExpiryDate);
        previewViewScanner = findViewById(R.id.previewViewScanner);
        editTextProductName = findViewById(R.id.editTextProductName);
        imageViewProduct = findViewById(R.id.imageViewProduct);
        cameraExecutor = Executors.newSingleThreadExecutor();
        spinnerStorageLocation = findViewById(R.id.spinnerStorageLocation);
        if (getIntent().hasExtra(PRESELECTED_LOCATION_INTERNAL_KEY)) {
            preselectedLocationValue = getIntent().getStringExtra(PRESELECTED_LOCATION_INTERNAL_KEY);
            Log.d("AddProductActivity", "Ricevuta location preselezionata: " + preselectedLocationValue);
        }
        setupStorageLocationSpinner();

        editTextShelfLifeAfterOpening = findViewById(R.id.editTextShelfLifeAfterOpening);
        buttonMarkAsOpened = findViewById(R.id.buttonMarkAsOpened);
        buttonMarkAsClosed = findViewById(R.id.buttonMarkAsClosed);
        textViewOpenedDate = findViewById(R.id.textViewOpenedDate);

        chipGroupCategories = findViewById(R.id.chipGroupCategories);
        editTextNewCategory = findViewById(R.id.editTextNewCategory);
        Button buttonAddCategory = findViewById(R.id.buttonAddCategory);

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

        pickImageForScannerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        Log.d("AddProductActivity", "Immagine selezionata dalla galleria: " + uri.toString());
                        if (cameraProviderFuture != null) {
                            try {
                                cameraProviderFuture.get().unbindAll();
                            } catch (ExecutionException | InterruptedException e) {
                                Log.e("AddProductActivity", "Errore nel fermare la fotocamera", e);
                            }
                        }
                        if (previewViewScanner != null) {
                            previewViewScanner.setVisibility(GONE);
                        }
                        processImageForBoth(uri);
                    } else {
                        Log.d("AddProductActivity", "Selezione immagine annullata.");
                    }
                });

        // Picker galleria per immagine del prodotto
        pickProductImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        Uri copiedUri = copyImageToInternalStorage(uri);
                        if (copiedUri != null) {
                            onProductImageSelected(copiedUri);
                        } else {
                            Toast.makeText(this, getString(R.string.err_load_image), Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        // Fotocamera per scattare una nuova foto del prodotto
        takeProductPhotoLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && currentPhotoPath != null) {
                        onProductImageSelected(Uri.fromFile(new File(currentPhotoPath)));
                    }
                });

        editTextBarcode.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String barcode = Objects.requireNonNull(editTextBarcode.getText()).toString().trim();
                if (!barcode.isEmpty()) {
                    fetchProductDetailsFromApi(barcode);
                }
            }
        });
        buttonIncrementQuantityActivity.setOnClickListener(v -> {
            try {
                int currentQuantity = Integer.parseInt(editTextQuantity.getText().toString());
                currentQuantity++;
                editTextQuantity.setText(String.valueOf(currentQuantity));
            } catch (NumberFormatException e) {
                editTextQuantity.setText("1"); // In caso di errore o campo vuoto
            }
        });

        buttonDecrementQuantityActivity.setOnClickListener(v -> {
            try {
                int currentQuantity = Integer.parseInt(editTextQuantity.getText().toString());
                if (currentQuantity > 1) { // Impedisci che la quantità scenda sotto 1 (o 0 se preferisci)
                    currentQuantity--;
                    editTextQuantity.setText(String.valueOf(currentQuantity));
                }
            } catch (NumberFormatException e) {
                editTextQuantity.setText("1"); // In caso di errore o campo vuoto
            }
        });

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_PRODUCT_ID)) {
            isEditMode = true;
            currentProductId = intent.getIntExtra(EXTRA_PRODUCT_ID, -1);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getString(R.string.edit_product));
            }
            fabButtonSaveProduct.setText(getString(R.string.update_product));
            observeProductForEditMode();
            buttonScanGallery.setVisibility(GONE);
            buttonScanCamera.setVisibility(GONE);
            editTextBarcode.setVisibility(GONE);
        } else {
            isEditMode = false;
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getString(R.string.add_product_title));
            }
            fabButtonSaveProduct.setText(getString(R.string.save_product_button));
            editTextQuantity.setText(DEFAULT_QUANTITY);
            editTextQuantity.setSelection(Objects.requireNonNull(editTextQuantity.getText()).length());
            checkCameraPermissionAndStartScanner();

        }

        buttonAddCategory.setOnClickListener(v -> addNewCategoryTag());
        editTextNewCategory.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addNewCategoryTag();
                return true;
            }
            return false;
        });

        Log.d("AddProductActivity", editTextBarcode.getText() + " " + editTextQuantity.getText() + " "
                + editTextExpiryDate.getText() + " " + imageViewProduct.toString());
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        if (buttonMarkAsOpened != null) {
            buttonMarkAsOpened.setOnClickListener(v -> {
                currentOpenedDate = System.currentTimeMillis();
                updateOpenedDateUI(currentOpenedDate);
            });
        }
        editTextExpiryDate.setOnClickListener(v -> {
            KeyboardUtils.hideKeyboard(AddProductActivity.this);
            showDatePickerDialog();
        });
        buttonScanCamera.setOnClickListener(v -> checkCameraPermissionAndStartScanner());
        buttonScanGallery.setOnClickListener(v -> pickImageForScannerLauncher.launch("image/*"));

        // Pulsanti modifica immagine prodotto
        ImageButton buttonPickProductImageGallery = findViewById(R.id.buttonPickProductImageGallery);
        ImageButton buttonTakeProductPhoto = findViewById(R.id.buttonTakeProductPhoto);
        if (buttonPickProductImageGallery != null) {
            buttonPickProductImageGallery.setOnClickListener(v ->
                    pickProductImageLauncher.launch("image/*"));
        }
        if (buttonTakeProductPhoto != null) {
            buttonTakeProductPhoto.setOnClickListener(v -> {
                Uri uri = createProductPhotoUri();
                if (uri != null) {
                    productPhotoCaptureUri = uri;
                    takeProductPhotoLauncher.launch(uri);
                } else {
                    Toast.makeText(this, getString(R.string.err_create_photo_file), Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Pulsante per ri-scansionare la data di scadenza se quella letta era errata
        if (buttonRescanExpiryDate != null) {
            buttonRescanExpiryDate.setOnClickListener(v -> {
                if (editTextExpiryDate.getText() != null && !editTextExpiryDate.getText().toString().isEmpty()) {
                    lastDiscardedExpiryDate = editTextExpiryDate.getText().toString().trim();
                }
                editTextExpiryDate.setText("");
                buttonRescanExpiryDate.setVisibility(GONE);
                isRescanning = true;
                checkCameraPermissionAndStartScanner();
            });
        }
        String defaultShelfLife = prefs.getString(SettingsFragment.KEY_PREF_DEFAULT_SHELF_LIFE, "5");
        if (!defaultShelfLife.trim().isEmpty()) {
            editTextShelfLifeAfterOpening.setText(defaultShelfLife);
        }
        buttonMarkAsClosed.setOnClickListener(v -> {
            currentOpenedDate = 0L;
            updateOpenedDateUI(currentOpenedDate);
        });
        fabButtonSaveProduct.setOnClickListener(v -> saveOrUpdateProduct());
    }

    /**
     * Crea un file nella cartella predefinita product_images dell'app
     * e restituisce il relativo Uri tramite FileProvider.
     * Usato come destinazione per le foto scattate dalla fotocamera.
     */
    private Uri createProductPhotoUri() {
        try {
            File imagesDir = new File(getExternalFilesDir(null), "product_images");
            if (!imagesDir.exists() && !imagesDir.mkdirs()) {
                Log.e("AddProductActivity", "Impossibile creare la cartella product_images");
                return null;
            }
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File photoFile = new File(imagesDir, "product_" + timestamp + ".jpg");
            currentPhotoPath = photoFile.getAbsolutePath();
            return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
        } catch (Exception e) {
            Log.e("AddProductActivity", "Errore nella creazione del file foto", e);
            return null;
        }
    }

    /**
     * Copia l'immagine selezionata dalla galleria nello storage interno dell'app,
     * così da mantenere i permessi di lettura per le aperture future.
     */
    private Uri copyImageToInternalStorage(Uri uri) {
        try {
            File imagesDir = new File(getExternalFilesDir(null), "product_images");
            if (!imagesDir.exists() && !imagesDir.mkdirs()) {
                return null;
            }
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File destFile = new File(imagesDir, "product_" + timestamp + ".jpg");
            
            try (java.io.InputStream in = getContentResolver().openInputStream(uri);
                 java.io.OutputStream out = new java.io.FileOutputStream(destFile)) {
                if (in == null) return null;
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
            return Uri.fromFile(destFile);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Callback comune per quando l'utente ha selezionato/scattato un'immagine
     * da usare come immagine del prodotto.
     * Aggiorna currentImageUrlFromApi e mostra l'immagine nell'ImageView.
     */
    private void onProductImageSelected(Uri uri) {
        currentImageUrlFromApi = uri.toString();
        imageViewProduct.setVisibility(View.VISIBLE);
        Glide.with(this)
                .load(uri)
                .into(imageViewProduct);
        Toast.makeText(this, getString(R.string.image_changed), Toast.LENGTH_SHORT).show();
    }

    private void processImageForBoth(Uri imageUri) {
        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);

            barcodeScanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        if (!barcodes.isEmpty()) {
                            for (Barcode barcode : barcodes) {
                                String rawValue = barcode.getRawValue();
                                Log.d("BarcodeScanner", "Codice a barre da immagine trovato: " + rawValue);
                                runOnUiThread(() -> {
                                    editTextBarcode.setText(rawValue);
                                    fetchProductDetailsFromApi(rawValue);
                                });
                                break;
                            }
                        } else {
                            Log.d("BarcodeScanner", "Nessun codice a barre trovato nell'immagine.");
                        }
                    })
                    .addOnFailureListener(
                            e -> Log.e("BarcodeScanner", "Errore nella scansione del codice a barre da immagine", e));

            textRecognizer.process(image)
                    .addOnSuccessListener(text -> {
                        String resultText = text.getText();
                        String parsedDate = parseExpiryDate(resultText);
                        if (parsedDate != null) {
                            runOnUiThread(() -> {
                                editTextExpiryDate.setText(parsedDate);
                                Long ts = eu.frigo.dispensa.util.DateConverter.parseDisplayDateToTimestampMs(parsedDate);
                                if (ts != null) calendar.setTimeInMillis(ts);
                                if (buttonRescanExpiryDate != null)
                                    buttonRescanExpiryDate.setVisibility(View.VISIBLE);
                                Toast.makeText(this, "Data trovata: " + parsedDate, Toast.LENGTH_SHORT).show();
                            });
                        }
                    })
                    .addOnFailureListener(e -> Log.e("TextScanner", "Errore nella scansione del testo da imageUri", e));

        } catch (IOException e) {
            e.printStackTrace();
            Log.e("Scanner", "Errore nel creare InputImage da URI", e);
            Toast.makeText(this, getString(R.string.err_load_image), Toast.LENGTH_SHORT).show();
        }
    }

    private String parseExpiryDate(String text) {
        String normalizedText = text.toLowerCase().replace("\n", " ");
        String[] keywords = { "data di scadenza", "da consumarsi preferibilmente", "da consumarsi", "best before", "bb",
                "scadenza", "scad", "lotto" };

        // Cerca prima vicino alle parole chiave, poi ovunque
        String result = findDateNearKeywords(normalizedText, keywords, false);
        if (result != null) return result;
        return findAnyDate(normalizedText, false);
    }

    /**
     * Versione "full-only" del parser: durante il rescan preferisce date complete
     * (gg/MM/aaaa). Se non ne trova, accetta anche qualsiasi formato mese/anno.
     */
    private String parseExpiryDateFullOnly(String text) {
        String normalizedText = text.toLowerCase().replace("\n", " ");
        String[] keywords = { "data di scadenza", "da consumarsi preferibilmente", "da consumarsi", "best before", "bb",
                "scadenza", "scad", "lotto" };

        // Prima cerca data completa vicino a parole chiave
        String result = findDateNearKeywords(normalizedText, keywords, true);
        if (result != null) return result;
        // Poi qualsiasi data completa
        result = findAnyDate(normalizedText, true);
        if (result != null) return result;
        // Fallback: qualsiasi data (incluso mese/anno) vicino a parole chiave
        result = findDateNearKeywords(normalizedText, keywords, false);
        if (result != null) return result;
        // Ultimo fallback: qualsiasi data mese/anno
        return findAnyDate(normalizedText, false);
    }

    /**
     * Cerca una data nel testo solo nelle vicinanze di parole chiave.
     * @param fullOnly se true cerca solo date complete (gg/MM/aaaa)
     */
    private String findDateNearKeywords(String text, String[] keywords, boolean fullOnly) {
        for (Object[] entry : buildPatterns(fullOnly)) {
            Pattern p = (Pattern) entry[0];
            boolean reversed = (boolean) entry[1];
            Matcher m = p.matcher(text);
            while (m.find()) {
                int idx = m.start();
                String before = text.substring(Math.max(0, idx - 40), idx);
                for (String kw : keywords) {
                    if (before.contains(kw)) {
                        return normalizeMatch(m, reversed);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Cerca qualsiasi data nel testo (senza vincolo di parole chiave).
     * @param fullOnly se true cerca solo date complete (gg/MM/aaaa)
     */
    private String findAnyDate(String text, boolean fullOnly) {
        for (Object[] entry : buildPatterns(fullOnly)) {
            Pattern p = (Pattern) entry[0];
            boolean reversed = (boolean) entry[1];
            Matcher m = p.matcher(text);
            if (m.find()) {
                return normalizeMatch(m, reversed);
            }
        }
        return null;
    }

    /**
     * Costruisce la lista ordinata di pattern da provare.
     * Ogni entry è {Pattern, isReversed}.
     * Ordine: data completa → mese/anno (4 cifre) → anno/mese (4 cifre) → mese/anno (2 cifre) → anno/mese (2 cifre)
     */
    private List<Object[]> buildPatterns(boolean fullOnly) {
        String sep = "[\\-/.\\s]+";
        List<Object[]> list = new ArrayList<>();
        // Data completa: gg/MM/aaaa (anno 4 cifre)
        list.add(new Object[]{ Pattern.compile(
                "\\b(0[1-9]|[12][0-9]|3[01])" + sep + "(0[1-9]|1[012])" + sep + "((?:19|20)\\d\\d)\\b"), false });
        // Data completa: gg/MM/aa (anno 2 cifre, es. 21.12.25)
        list.add(new Object[]{ Pattern.compile(
                "\\b(0[1-9]|[12][0-9]|3[01])" + sep + "(0[1-9]|1[012])" + sep + "(\\d{2})\\b"), false });
        if (!fullOnly) {
            // MM/yyyy
            list.add(new Object[]{ Pattern.compile(
                    "\\b(0[1-9]|1[012])" + sep + "((?:19|20)\\d\\d)\\b"), false });
            // yyyy/MM  (reversed)
            list.add(new Object[]{ Pattern.compile(
                    "\\b((?:19|20)\\d\\d)" + sep + "(0[1-9]|1[012])\\b"), true });
            // MM/yy
            list.add(new Object[]{ Pattern.compile(
                    "\\b(0[1-9]|1[012])" + sep + "(\\d{2})\\b"), false });
            // yy/MM  (reversed)
            list.add(new Object[]{ Pattern.compile(
                    "\\b(\\d{2})" + sep + "(0[1-9]|1[012])\\b"), true });
        }
        return list;
    }

    /**
     * Estrae la stringa normalizzata dal matcher.
     * Se reversed=true, scambia gruppo 1 e gruppo 2 (es. yyyy/MM → MM/yyyy).
     */
    private String normalizeMatch(Matcher m, boolean reversed) {
        if (reversed && m.groupCount() >= 2) {
            String g1 = m.group(1); // es. "2026"
            String g2 = m.group(2); // es. "05"
            return g2 + "/" + g1;   // restituisce "05/2026"
        }
        return m.group().replaceAll("[\\-/.\\s]+", "/");
    }

    private void updateOpenedDateUI(Long openedTimestamp) {
        if (buttonMarkAsOpened == null || textViewOpenedDate == null)
            return;

        if (openedTimestamp != null && openedTimestamp > 0) {
            textViewOpenedDate.setText(String.format(getString(R.string.open_date), formatDate(openedTimestamp)));
            textViewOpenedDate.setVisibility(View.VISIBLE);
            buttonMarkAsOpened.setVisibility(GONE);
            buttonMarkAsClosed.setVisibility(View.VISIBLE);
        } else {
            textViewOpenedDate.setVisibility(GONE);
            buttonMarkAsOpened.setVisibility(View.VISIBLE);
            buttonMarkAsClosed.setVisibility(GONE);
        }
    }

    private String formatDate(Long timestamp) {
        if (timestamp == null || timestamp <= 0)
            return getString(R.string.not_defined);
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY);
        return sdf.format(new Date(timestamp));
    }

    private void setupStorageLocationSpinner() {
        List<String> locationDisplayNames = new ArrayList<>();
        locationSpinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, locationDisplayNames);
        locationSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStorageLocation.setAdapter(locationSpinnerAdapter);

        addProductViewModel.getAllSelectableLocations().observe(this, locations -> {
            if (locations != null && !locations.isEmpty()) {
                availableLocations.clear();
                availableLocations.addAll(locations);

                locationDisplayNames.clear();
                for (StorageLocation loc : locations) {
                    locationDisplayNames.add(loc.getLocalizedName(getApplicationContext()));
                }
                locationSpinnerAdapter.notifyDataSetChanged();

                String keyToSelect = null;
                if (preselectedLocationValue != null) {
                    keyToSelect = preselectedLocationValue;
                } else if (isEditMode && productBeingEdited != null && productBeingEdited.product != null) {
                    keyToSelect = productBeingEdited.product.getStorageLocation();
                }

                if (keyToSelect != null) {
                    selectSpinnerLocationByInternalKey(keyToSelect);
                } else if (!availableLocations.isEmpty()) {
                    spinnerStorageLocation.setSelection(0);
                    selectedStorageInternalKey = availableLocations.get(0).getInternalKey();
                    Log.d("AddProductActivity", "Spinner default su: " + availableLocations.get(0).getName());
                }

            } else {
                Log.w("AddProductActivity", "Nessuna location disponibile per lo spinner.");
                availableLocations.clear();
                locationDisplayNames.clear();
                locationSpinnerAdapter.notifyDataSetChanged();
            }
        });
        if (isEditMode && currentProductId != -1) {
            observeProductForEditMode();
        }
        spinnerStorageLocation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < availableLocations.size()) {
                    selectedStorageInternalKey = availableLocations.get(position).getInternalKey();
                    Log.d("AddProductActivity", "Location selezionata: " + availableLocations.get(position).getName()
                            + " (Key: " + selectedStorageInternalKey + ")");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedStorageInternalKey = null;
            }
        });
    }

    private void selectSpinnerLocationByInternalKey(String internalKey) {
        if (internalKey == null || availableLocations.isEmpty()) {
            return;
        }
        for (int i = 0; i < availableLocations.size(); i++) {
            if (internalKey.equals(availableLocations.get(i).getInternalKey())) {
                spinnerStorageLocation.setSelection(i);
                selectedStorageInternalKey = internalKey;
                Log.d("AddProductActivity",
                        "Spinner preselezionato (dinamicamente) su: " + availableLocations.get(i).getName());
                return;
            }
        }
        Log.w("AddProductActivity",
                "Valore di location (internalKey) '" + internalKey + "' non trovato nello spinner dinamico.");
    }

    private void observeProductForEditMode() {
        addProductViewModel.getProductById(currentProductId).observe(this, productWithDefs -> {
            if (productWithDefs != null && productWithDefs.product != null) {
                productBeingEdited = productWithDefs;
                editTextProductName.setText(productBeingEdited.product.getProductName());
                editTextBarcode.setText(productBeingEdited.product.getBarcode());
                editTextQuantity.setText(String.valueOf(productBeingEdited.product.getQuantity()));

                currentImageUrlFromApi = productBeingEdited.product.getImageUrl();
                currentProductNameFromApi = productBeingEdited.product.getProductName(); // O quello che usi per l'API

                if (productBeingEdited.product.getExpiryDate() != null
                        && productBeingEdited.product.getExpiryDate() != 0) {
                    calendar.setTimeInMillis(productBeingEdited.product.getExpiryDate());
                    updateLabel();
                }

                if (currentImageUrlFromApi != null && !currentImageUrlFromApi.trim().isEmpty()) {
                    Glide.with(AddProductActivity.this)
                            .load(currentImageUrlFromApi)
                            .into(imageViewProduct);
                    imageViewProduct.setVisibility(View.VISIBLE);
                }

                currentOpenedDate = productBeingEdited.product.getOpenedDate();
                currentShelfLifeDays = productBeingEdited.product.getShelfLifeAfterOpeningDays();

                if (currentShelfLifeDays > 0) {
                    editTextShelfLifeAfterOpening.setText(String.valueOf(currentShelfLifeDays));
                } else {
                    editTextShelfLifeAfterOpening.setText("");
                }
                updateOpenedDateUI(currentOpenedDate);

                if (productBeingEdited.categoryDefinitions != null) {
                    currentProductTagsSet.clear();
                    for (CategoryDefinition def : productBeingEdited.categoryDefinitions) {
                        currentProductTagsSet.add(def.tagName);
                    }
                    updateChipGroup();
                }

                if (!availableLocations.isEmpty() && productBeingEdited.product.getStorageLocation() != null) {
                    selectSpinnerLocationByInternalKey(productBeingEdited.product.getStorageLocation());
                }
            } else {
                productBeingEdited = null;
                Toast.makeText(this, getString(R.string.err_load_product), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void updateChipGroup() {
        chipGroupCategories.removeAllViews();
        for (String tag : currentProductTagsSet) {
            Chip chip = new Chip(this);
            chip.setText(tag);
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> {
                currentProductTagsSet.remove(tag);
                updateChipGroup();
            });
            chipGroupCategories.addView(chip);
        }
    }

    private void addNewCategoryTag() {
        String newTag = editTextNewCategory.getText().toString().trim();
        if (!newTag.isEmpty()) {
            if (!newTag.matches("^[a-z]{2}:.*")) {
                newTag = "it:" + newTag;
            }
            if (currentProductTagsSet.add(newTag)) {
                updateChipGroup();
            }
            editTextNewCategory.setText("");
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
        if (!isCameraPermissionGranted) {
            Log.e("AddProductActivity", "Tentativo di avviare la fotocamera senza permesso.");
            return;
        }

        previewViewScanner.setVisibility(View.VISIBLE);

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreviewAndAnalysis(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, getString(R.string.err_camera), Toast.LENGTH_SHORT).show();
                Log.e("AddProductActivity", "Errore nell'avvio della fotocamera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("UnsafeOptInUsageError")
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
            Image mediaImage = imageProxy.getImage();
            if (mediaImage != null) {
                InputImage image = InputImage.fromMediaImage(mediaImage,
                        imageProxy.getImageInfo().getRotationDegrees());

                boolean needsBarcode = Objects.requireNonNull(editTextBarcode.getText()).toString().trim().isEmpty();
                boolean needsExpiry = Objects.requireNonNull(editTextExpiryDate.getText()).toString().trim().isEmpty();

                com.google.android.gms.tasks.Task<?> barcodeTask = null;
                com.google.android.gms.tasks.Task<?> textTask = null;

                if (needsBarcode) {
                    barcodeTask = barcodeScanner.process(image)
                            .addOnSuccessListener(barcodes -> {
                                if (!barcodes.isEmpty()) {
                                    for (Barcode barcode : barcodes) {
                                        String rawValue = barcode.getRawValue();
                                        Log.d("BarcodeScanner", "Codice a barre trovato: " + rawValue);
                                        runOnUiThread(() -> {
                                            editTextBarcode.setText(rawValue);
                                            fetchProductDetailsFromApi(rawValue);
                                        });
                                        return;
                                    }
                                }
                            })
                            .addOnFailureListener(e -> Log.e("BarcodeScanner", "Errore", e));
                } else {
                    barcodeTask = com.google.android.gms.tasks.Tasks.forResult(null);
                }

                if (needsExpiry) {
                    textTask = textRecognizer.process(image)
                            .addOnSuccessListener(text -> {
                                String resultText = text.getText();
                                String parsedDate = isRescanning
                                        ? parseExpiryDateFullOnly(resultText)
                                        : parseExpiryDate(resultText);
                                if (parsedDate != null && !parsedDate.equals(lastDiscardedExpiryDate)) {
                                    lastDiscardedExpiryDate = null;
                                    isRescanning = false;
                                    runOnUiThread(() -> {
                                        editTextExpiryDate.setText(parsedDate);
                                        // Aggiorna il calendar con la data letta dall'OCR
                                        Long ts = eu.frigo.dispensa.util.DateConverter.parseDisplayDateToTimestampMs(parsedDate);
                                        if (ts != null) {
                                            calendar.setTimeInMillis(ts);
                                        }
                                        if (buttonRescanExpiryDate != null)
                                            buttonRescanExpiryDate.setVisibility(View.VISIBLE);
                                        Toast.makeText(AddProductActivity.this, "Data trovata: " + parsedDate,
                                                Toast.LENGTH_SHORT).show();
                                    });
                                }
                            })
                            .addOnFailureListener(e -> Log.e("TextScanner", "Errore", e));
                } else {
                    textTask = com.google.android.gms.tasks.Tasks.forResult(null);
                }

                com.google.android.gms.tasks.Tasks.whenAllComplete(barcodeTask, textTask)
                        .addOnCompleteListener(task -> {
                            imageProxy.close();

                            runOnUiThread(() -> {
                                boolean hasBarcodeNow = !Objects.requireNonNull(editTextBarcode.getText()).toString()
                                        .trim().isEmpty();
                                boolean hasExpiryNow = !Objects.requireNonNull(editTextExpiryDate.getText()).toString()
                                        .trim().isEmpty();
                                if (hasBarcodeNow && hasExpiryNow) {
                                    stopCamera(cameraProvider);
                                }
                            });
                        });
            } else {
                imageProxy.close();
            }
        });

        previewViewScanner.setVisibility(View.VISIBLE);

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis);
        } catch (Exception exc) {
            Log.e("AddProductActivity", "Use case binding fallito", exc);
        }
    }

    private void fetchProductDetailsFromApi(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.err_barcode), Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("OpenFoodFacts", "Fetching details for barcode: " + barcode);
        Toast.makeText(this, getString(R.string.notify_load_product), Toast.LENGTH_SHORT).show();

        OpenFoodFactsApiService OffApiService = OpenFoodFactsRetrofitClient.getApiService(getApplicationContext());
        String fieldsToFetch = "product_name_it,product_name,image_front_url,image_url,categories_tags";
        retrofit2.Call<OpenFoodFactsProductResponse> call = OffApiService.getProductByBarcode(barcode, fieldsToFetch);

        call.enqueue(new Callback<OpenFoodFactsProductResponse>() {
            @Override
            public void onResponse(@NonNull retrofit2.Call<OpenFoodFactsProductResponse> call,
                    @NonNull Response<OpenFoodFactsProductResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    OpenFoodFactsProductResponse apiResponse = response.body();
                    if (apiResponse.getStatus() == 1 && apiResponse.getProduct() != null) {
                        OpenFoodFactsProductResponse.ProductData productData = apiResponse.getProduct();
                        String productName = productData.getProductNameIt();
                        if (productName == null || productName.trim().isEmpty()) {
                            productName = productData.getProductName();
                        }
                        currentProductNameFromApi = productName;
                        if (productName != null && !productName.trim().isEmpty()) {
                            editTextProductName.setText(productName);
                        } else {
                            editTextProductName.setText(getString(R.string.not_find));
                            Log.w("OpenFoodFacts", "Nome prodotto non trovato per: " + barcode);
                        }

                        String imageUrl = productData.getImageFrontUrl();
                        if (imageUrl == null || imageUrl.trim().isEmpty()) {
                            imageUrl = productData.getImageUrl(); // Fallback ad altro URL immagine
                        }
                        currentImageUrlFromApi = imageUrl;
                        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                            Glide.with(AddProductActivity.this)
                                    .load(imageUrl)
                                    .into(imageViewProduct);
                            imageViewProduct.setVisibility(View.VISIBLE);
                        } else {
                            imageViewProduct.setVisibility(GONE); // O mostra un placeholder
                            Log.w("OpenFoodFacts", "URL immagine non trovato per: " + barcode);
                        }
                        List<String> fetchedCategories = productData.getCategoriesTags();
                        if (fetchedCategories != null && !fetchedCategories.isEmpty()) {
                            currentProductTagsSet.clear();
                            currentProductTagsSet.addAll(fetchedCategories);
                            Log.d("OpenFoodFacts", "Categories fetched: " + fetchedCategories);
                        } else {
                            // currentProductTagsSet.clear(); // Decidi se pulire se l'API non restituisce
                            // nulla
                            Log.d("OpenFoodFacts", "No categories found from API.");
                        }
                        updateChipGroup();
                        Toast.makeText(AddProductActivity.this, getString(R.string.notify_loaded_product),
                                Toast.LENGTH_SHORT).show();

                    } else {
                        Log.w("OpenFoodFacts", "Prodotto non trovato o dati mancanti nell'API per: " + barcode
                                + ", Status: " + apiResponse.getStatus());
                        Toast.makeText(AddProductActivity.this, "Prodotto non trovato su Open Food Facts",
                                Toast.LENGTH_LONG).show();
                        clearProductApiFieldsAndData();
                    }
                } else {
                    Log.e("OpenFoodFacts",
                            "Errore nella risposta API: " + response.code() + " - " + response.message());
                    Toast.makeText(AddProductActivity.this, "Errore nel caricare i dati: " + response.code(),
                            Toast.LENGTH_LONG).show();
                    clearProductApiFieldsAndData();
                }
            }

            @Override
            public void onFailure(@NonNull retrofit2.Call<OpenFoodFactsProductResponse> call, @NonNull Throwable t) {
                Log.e("OpenFoodFacts", "Fallimento chiamata API", t);
                Toast.makeText(AddProductActivity.this, getString(R.string.err_network), Toast.LENGTH_LONG).show();
                clearProductApiFieldsAndData();
            }
        });

    }

    private void clearProductApiFieldsAndData() {
        editTextProductName.setText("");
        imageViewProduct.setImageDrawable(null);
        imageViewProduct.setVisibility(GONE);
        currentProductNameFromApi = null;
        currentImageUrlFromApi = null;
    }

    private void stopCamera(ProcessCameraProvider cameraProvider) {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (previewViewScanner != null) {
            previewViewScanner.setVisibility(GONE);
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
    }

    private void saveOrUpdateProduct() {
        String barcode = editTextBarcode.getText() != null ? editTextBarcode.getText().toString().trim() : "";
        String name = editTextProductName.getText() != null ? editTextProductName.getText().toString().trim() : "";
        String quantityStr = editTextQuantity.getText() != null ? editTextQuantity.getText().toString().trim() : "";
        String expiryDate = editTextExpiryDate.getText() != null ? editTextExpiryDate.getText().toString().trim() : "";
        String shelfLifeStr = editTextShelfLifeAfterOpening.getText() != null
                ? editTextShelfLifeAfterOpening.getText().toString().trim()
                : "";
        int shelfLifeDays = -1;
        if (!shelfLifeStr.isEmpty()) {
            try {
                shelfLifeDays = Integer.parseInt(shelfLifeStr);
                if (shelfLifeDays < 0)
                    shelfLifeDays = -1;
            } catch (NumberFormatException e) {
                editTextShelfLifeAfterOpening.setError(getString(R.string.err_days));
                editTextShelfLifeAfterOpening.requestFocus();
                return;
            }
        }
        if (selectedStorageInternalKey == null || selectedStorageInternalKey.isEmpty()) {
            Toast.makeText(this, getString(R.string.err_location_select), Toast.LENGTH_SHORT).show();
            return;
        }
        if (barcode.isEmpty()) {
            editTextBarcode.setError(getString(R.string.barcode_mandatory));
            editTextBarcode.requestFocus();
            return;
        }
        if (quantityStr.isEmpty()) {
            editTextQuantity.setError(getString(R.string.quantity_mandatory));
            editTextQuantity.requestFocus();
            return;
        }
        int quantity;
        try {
            quantity = Integer.parseInt(quantityStr);
            if (quantity <= 0) {
                editTextQuantity.setError(getString(R.string.err_quantity_zero));
                editTextQuantity.requestFocus();
                return;
            }
        } catch (NumberFormatException e) {
            editTextQuantity.setError(getString(R.string.err_quantity_valid));
            editTextQuantity.requestFocus();
            return;
        }
        if (expiryDate.isEmpty()) {
            editTextExpiryDate.setError(getString(R.string.expiry_date_mandatory));
            Toast.makeText(this, "La data di scadenza è obbligatoria", Toast.LENGTH_SHORT).show();
            return;
        }
        if (name.isEmpty()) {
            if (currentProductNameFromApi != null && !currentProductNameFromApi.isEmpty()) {
                name = currentProductNameFromApi;
            } else {
                name = barcode;
            }
        }
        Product product = new Product(barcode, quantity, DateConverter.parseDisplayDateToTimestampMs(expiryDate), name,
                currentImageUrlFromApi, selectedStorageInternalKey, currentOpenedDate, shelfLifeDays);
        Log.d("AddProductActivity", "Salvataggio prodotto: " + product.toString());
        List<String> tagsToSave = new ArrayList<>(currentProductTagsSet);
        if (isEditMode) {
            product.setId(currentProductId);
            addProductViewModel.update(product, tagsToSave);
        } else {
            addProductViewModel.insert(product, tagsToSave);
        }
        clearProductApiFieldsAndData();
        Intent resultIntent = new Intent();
        resultIntent.putExtra(NEW_PRODUCT_NAME, name);
        resultIntent.putExtra(NEW_PRODUCT_EDIT, isEditMode);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void showDatePickerDialog() {
        DatePickerDialog.OnDateSetListener dateSetListener = (view, year, monthOfYear, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, monthOfYear);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateLabel();
        };

        new DatePickerDialog(AddProductActivity.this, dateSetListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    private void updateLabel() {
        editTextExpiryDate.setText(DateConverter.formatTimestampToDisplayDate(calendar.getTimeInMillis()));
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed(); // O finish();
        return true;
    }

}