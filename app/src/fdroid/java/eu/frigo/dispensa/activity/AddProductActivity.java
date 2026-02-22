package eu.frigo.dispensa.activity;

import static android.view.View.GONE;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
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
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;

import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import com.bumptech.glide.Glide;
import com.googlecode.tesseract.android.TessBaseAPI;
import android.util.Log;
import android.util.Size;
import android.media.Image;
import java.io.FileOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.zxing.InvertedLuminanceSource;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import eu.frigo.dispensa.R;
import eu.frigo.dispensa.data.category.CategoryDefinition;
import eu.frigo.dispensa.data.product.Product;
import eu.frigo.dispensa.data.category.ProductWithCategoryDefinitions;
import eu.frigo.dispensa.data.storage.StorageLocation;
import eu.frigo.dispensa.network.openfoodfacts.OpenFoodFactsApiService;
import eu.frigo.dispensa.network.openfoodfacts.OpenFoodFactsRetrofitClient;
import eu.frigo.dispensa.network.openfoodfacts.model.OpenFoodFactsProductResponse;
import eu.frigo.dispensa.util.KeyboardUtils;
import eu.frigo.dispensa.viewmodel.AddProductViewModel;
import eu.frigo.dispensa.util.DateConverter;
import android.annotation.SuppressLint;
import java.io.OutputStream;
import retrofit2.Callback;
import retrofit2.Response;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import java.io.ByteArrayOutputStream;
import android.widget.FrameLayout;

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
    private PreviewView previewViewScanner;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ExecutorService cameraExecutor;
    private TessBaseAPI tessBaseAPI;
    private String dataPath;
    private ImageButton buttonRescanExpiryDate;
    private boolean isCameraPermissionGranted;
    private TextInputEditText editTextProductName;
    private ImageView imageViewProduct;
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

    // ZXing reader per immagini dalla galleria
    private MultiFormatReader multiFormatReader;
    private boolean isScanning = false;
    private boolean isAnalysisInProgress = false;
    private View layoutScannerContainer;

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

    private ActivityResultLauncher<String> pickImageForBarcodeLauncher;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_product);

        View rootLayout = findViewById(R.id.add_product_root_layout);
        View nestedScrollView = findViewById(R.id.add_product_nested_scroll);
        final ExtendedFloatingActionButton finalFab = findViewById(R.id.buttonSaveProduct);

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

            if (finalFab != null) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) finalFab.getLayoutParams();
                lp.bottomMargin = Math.max(systemBars.bottom, ime.bottom)
                        + (int) (16 * getResources().getDisplayMetrics().density);
                finalFab.setLayoutParams(lp);
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

        boolean openFoodFactsApiEnabled = OpenFoodFactsRetrofitClient
                .isOpenFoodFactsApiEnabled(getApplicationContext());

        if (!openFoodFactsApiEnabled) {
            showOpenFoodFactsBanner();
        }

        previewViewScanner = findViewById(R.id.previewViewScanner);
        editTextProductName = findViewById(R.id.editTextProductName);
        imageViewProduct = findViewById(R.id.imageViewProduct);
        cameraExecutor = Executors.newSingleThreadExecutor();
        buttonRescanExpiryDate = findViewById(R.id.buttonRescanExpiryDate);
        spinnerStorageLocation = findViewById(R.id.spinnerStorageLocation);
        editTextBarcode = findViewById(R.id.editTextBarcode);
        editTextQuantity = findViewById(R.id.editTextQuantity);
        buttonDecrementQuantityActivity = findViewById(R.id.buttonDecrementQuantityActivity);
        buttonIncrementQuantityActivity = findViewById(R.id.buttonIncrementQuantityActivity);
        layoutScannerContainer = findViewById(R.id.layoutScannerContainer);
        ImageButton buttonScanGallery = findViewById(R.id.buttonScanGallery);
        ImageButton buttonScanCamera = findViewById(R.id.buttonScanCamera);

        // Prepara Tesseract
        prepareTesseract();

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
        ExtendedFloatingActionButton fabButtonSaveProduct = finalFab;

        buttonAddCategory.setOnClickListener(v -> addNewCategoryTag());

        // Inizializza Tesseract API
        initTesseract();

        if (buttonRescanExpiryDate != null) {
            buttonRescanExpiryDate.setOnClickListener(v -> {
                editTextExpiryDate.setText("");
                buttonRescanExpiryDate.setVisibility(GONE);
                checkCameraPermissionAndStartScanner();
            });
        }

        pickImageForBarcodeLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        Log.d("AddProduct", "Immagine selezionata dalla galleria: " + uri.toString());
                        stopCamera();
                        processImageForBoth(uri);
                    } else {
                        Log.d("AddProduct", "Selezione immagine annullata.");
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
                editTextQuantity.setText("1");
            }
        });

        buttonDecrementQuantityActivity.setOnClickListener(v -> {
            try {
                int currentQuantity = Integer.parseInt(editTextQuantity.getText().toString());
                if (currentQuantity > 1) {
                    currentQuantity--;
                    editTextQuantity.setText(String.valueOf(currentQuantity));
                }
            } catch (NumberFormatException e) {
                editTextQuantity.setText("1");
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
            previewViewScanner.setVisibility(GONE);
        } else {
            isEditMode = false;
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getString(R.string.add_product_title));
            }
            fabButtonSaveProduct.setText(getString(R.string.save_product_button));
            editTextQuantity.setText(DEFAULT_QUANTITY);
            editTextQuantity.setSelection(Objects.requireNonNull(editTextQuantity.getText()).length());
            isScanning = true;
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

        buttonScanCamera.setOnClickListener(v -> {
            isScanning = true;
            checkCameraPermissionAndStartScanner();
        });

        buttonScanGallery.setOnClickListener(v -> pickImageForBarcodeLauncher.launch("image/*"));
        buttonMarkAsClosed.setOnClickListener(v -> {
            currentOpenedDate = 0L;
            updateOpenedDateUI(currentOpenedDate);
        });
        fabButtonSaveProduct.setOnClickListener(v -> saveOrUpdateProduct());
        if (!openFoodFactsApiEnabled) {
            buttonScanCamera.setVisibility(GONE);
            buttonScanGallery.setVisibility(GONE);
            previewViewScanner.setVisibility(GONE);
        }
    }

    private void prepareTesseract() {
        dataPath = getFilesDir() + "/tesseract/";
        File dir = new File(dataPath + "tessdata/");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        copyTessDataFiles("tessdata");
    }

    private void copyTessDataFiles(String path) {
        try {
            String[] fileList = getAssets().list(path);
            if (fileList != null) {
                for (String fileName : fileList) {
                    String pathToDataFile = dataPath + path + "/" + fileName;
                    if (!(new File(pathToDataFile)).exists()) {
                        InputStream in = getAssets().open(path + "/" + fileName);
                        OutputStream out = new FileOutputStream(pathToDataFile);
                        byte[] buff = new byte[1024];
                        int len;
                        while ((len = in.read(buff)) > 0) {
                            out.write(buff, 0, len);
                        }
                        in.close();
                        out.close();
                    }
                }
            }
        } catch (IOException e) {
            Log.e("Tesseract", "Errore nel copiare i file tessdata", e);
        }
    }

    private void initTesseract() {
        Log.d("Tesseract", "Inizializzazione Tesseract in corso...");
        tessBaseAPI = new TessBaseAPI();
        if (!tessBaseAPI.init(dataPath, "ita")) {
            Log.e("Tesseract", "Inizializzazione di Tesseract fallita");
            Toast.makeText(this, "Errore inizializzazione OCR", Toast.LENGTH_SHORT).show();
            return;
        }
        // Ottimizzazioni Tesseract
        tessBaseAPI.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT);
        tessBaseAPI.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789/-.");
        Log.d("Tesseract", "Tesseract inizializzato correttamente per 'ita'");
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

        // Riutilizziamo il multiFormatReader per ZXing
        MultiFormatReader reader = new MultiFormatReader();
        Hashtable<DecodeHintType, Object> hints = new Hashtable<>();
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39));

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            if (!isScanning || isAnalysisInProgress) {
                imageProxy.close();
                return;
            }

            isAnalysisInProgress = true;
            Image mediaImage = imageProxy.getImage();
            if (mediaImage != null) {
                // Conversione ImageProxy a Bitmap ottimizzata con Crop ROI
                Bitmap bitmap = imageProxyToCroppedBitmap(imageProxy);

                if (bitmap != null) {
                    boolean needsBarcode = Objects.requireNonNull(editTextBarcode.getText()).toString().trim()
                            .isEmpty();
                    boolean needsExpiry = Objects.requireNonNull(editTextExpiryDate.getText()).toString().trim()
                            .isEmpty();

                    if (needsBarcode) {
                        String barcode = decodeBarcode(bitmap);
                        if (barcode != null) {
                            Log.d("BarcodeScanner", "Codice a barre trovato (Cropped): " + barcode);
                            runOnUiThread(() -> {
                                editTextBarcode.setText(barcode);
                                fetchProductDetailsFromApi(barcode);
                            });
                        }
                    }

                    if (needsExpiry) {
                        // PASS 1: Fast Scan (Standard ROI)
                        String parsedDate = runOcrPass(bitmap, TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT);

                        // PASS 2: Enhanced Scan (Upscaled + High Contrast)
                        if (parsedDate == null) {
                            Bitmap enhancedBitmap = enhanceBitmapForOcr(bitmap, false);
                            parsedDate = runOcrPass(enhancedBitmap, TessBaseAPI.PageSegMode.PSM_SINGLE_LINE);
                        }

                        // PASS 3: Inverted Scan (For light text on dark backgrounds)
                        if (parsedDate == null) {
                            Bitmap invertedBitmap = enhanceBitmapForOcr(bitmap, true);
                            parsedDate = runOcrPass(invertedBitmap, TessBaseAPI.PageSegMode.PSM_SINGLE_LINE);
                        }

                        if (parsedDate != null) {
                            String finalDate = parsedDate;
                            Log.d("OCR", "Data trovata con multi-pass: " + finalDate);
                            runOnUiThread(() -> {
                                editTextExpiryDate.setText(finalDate);
                                if (buttonRescanExpiryDate != null)
                                    buttonRescanExpiryDate.setVisibility(View.VISIBLE);
                                Toast.makeText(AddProductActivity.this, "Data trovata: " + finalDate,
                                        Toast.LENGTH_SHORT).show();
                            });
                        }
                    }

                    if (!needsBarcode && !needsExpiry) {
                        isScanning = false;
                        runOnUiThread(() -> stopCamera(cameraProvider));
                    }
                }
            }
            isAnalysisInProgress = false;
            imageProxy.close();
        });

        layoutScannerContainer.setVisibility(View.VISIBLE);

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis);
        } catch (Exception exc) {
            Log.e("AddProductActivity", "Use case binding fallito", exc);
        }
    }

    private String runOcrPass(Bitmap bitmap, int psmMode) {
        if (bitmap == null)
            return null;
        tessBaseAPI.setPageSegMode(psmMode);
        tessBaseAPI.setImage(bitmap);
        String resultText = tessBaseAPI.getUTF8Text();
        return parseExpiryDate(resultText);
    }

    private Bitmap enhanceBitmapForOcr(Bitmap src, boolean invert) {
        // 1. Upscaling (2x) per aiutare Tesseract con piccoli font
        int width = src.getWidth() * 2;
        int height = src.getHeight() * 2;
        Bitmap scaled = Bitmap.createScaledBitmap(src, width, height, true);

        // 2. Aumento contrasto e binarizzazione semplice
        Bitmap enhanced = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(enhanced);
        android.graphics.Paint paint = new android.graphics.Paint();

        android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
        // Aumentiamo il contrasto (1.5x)
        float contrast = 1.5f;
        float brightness = -20f;
        cm.set(new float[] {
                contrast, 0, 0, 0, brightness,
                0, contrast, 0, 0, brightness,
                0, 0, contrast, 0, brightness,
                0, 0, 0, 1, 0
        });

        if (invert) {
            android.graphics.ColorMatrix invertMatrix = new android.graphics.ColorMatrix(new float[] {
                    -1, 0, 0, 0, 255,
                    0, -1, 0, 0, 255,
                    0, 0, -1, 0, 255,
                    0, 0, 0, 1, 0
            });
            invertMatrix.postConcat(cm);
            cm = invertMatrix;
        }

        paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
        canvas.drawBitmap(scaled, 0, 0, paint);

        return enhanced;
    }

    private Bitmap imageProxyToCroppedBitmap(androidx.camera.core.ImageProxy imageProxy) {
        int width = imageProxy.getWidth();
        int height = imageProxy.getHeight();

        // 1. Conversione veloce YUV a YuvImage (NV21)
        byte[] nv21 = yuv420ToNv21(imageProxy);

        // 2. Crop ROI (Area centrale del mirino)
        // Definiamo un'area del 30% in altezza e 80% in larghezza al centro
        int cropWidth = (int) (width * 0.8);
        int cropHeight = (int) (height * 0.3);
        int left = (width - cropWidth) / 2;
        int top = (height - cropHeight) / 2;

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(left, top, left + cropWidth, top + cropHeight), 90, out);
        byte[] imageBytes = out.toByteArray();
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        // 3. Rotazione se necessaria
        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        if (rotation != 0) {
            bitmap = rotateBitmap(bitmap, rotation);
        }
        return bitmap;
    }

    private byte[] yuv420ToNv21(androidx.camera.core.ImageProxy image) {
        androidx.camera.core.ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        return nv21;
    }

    private void showOpenFoodFactsBanner() {
        View banner = findViewById(R.id.banner_open_food_facts); // Devi avere questo View nel layout!
        Button btnEnableOFF = banner.findViewById(R.id.button_enable_off);
        banner.setVisibility(View.VISIBLE);
        btnEnableOFF.setOnClickListener(v -> {
            // Vai alle preferenze, es. se hai la SettingsActivity...
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra("openFoodFactsSection", true);
            startActivity(intent);
        });
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

    private void checkCameraPermissionAndStartScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            isCameraPermissionGranted = true;
            startCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
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

        spinnerStorageLocation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!availableLocations.isEmpty() && position >= 0 && position < availableLocations.size()) {
                    selectedStorageInternalKey = availableLocations.get(position).getInternalKey();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedStorageInternalKey = null;
            }
        });

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
    }

    private String parseExpiryDate(String text) {
        if (text == null || text.trim().isEmpty())
            return null;

        // Normalizzazione spinta: sostituiamo comuni errori OCR e uniformiamo
        // separatori
        String normalizedText = text.toUpperCase()
                .replaceAll("[OQ]", "0") // O o Q scambiati per 0
                .replaceAll("[IL|]", "1") // I, L o | scambiati per 1
                .replaceAll("[S]", "5") // S scambiato per 5
                .replaceAll("[B]", "8") // B scambiato per 8
                .replaceAll("[Z]", "2") // Z scambiato per 2
                .replace("\n", " ")
                .toLowerCase();

        String[] keywords = { "scadenza", "scad", "preferibilmente", "best before", "bb", "exp", "data", "lotto" };
        String sep = "[\\-/.\\s]*"; // Separatore opzionale o multiplo

        // Regex migliorate
        String regexDDMMYYYY = "\\b(0[1-9]|[12][0-9]|3[01])" + sep + "(0[1-9]|1[012])" + sep + "(20[2-3][0-9])\\b";
        String regexDDMMYY = "\\b(0[1-9]|[12][0-9]|3[01])" + sep + "(0[1-9]|1[012])" + sep + "([2-3][0-9])\\b";
        String regexMMYYYY = "\\b(0[1-9]|1[012])" + sep + "(20[2-3][0-9])\\b";
        String regexYYYYMMDD = "\\b(20[2-3][0-9])" + sep + "(0[1-9]|1[012])" + sep + "(0[1-9]|[12][0-9]|3[01])\\b";

        String[] patternsToTry = { regexDDMMYYYY, regexYYYYMMDD, regexMMYYYY, regexDDMMYY };

        // 1. Cerca date vicino a parole chiave
        for (String pattern : patternsToTry) {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(normalizedText);
            while (m.find()) {
                String dateFound = m.group();
                int idx = m.start();
                String substringBefore = normalizedText.substring(Math.max(0, idx - 30), idx);
                for (String kw : keywords) {
                    if (substringBefore.contains(kw)) {
                        return formatFoundDate(dateFound, pattern);
                    }
                }
            }
        }

        // 2. Cerca qualsiasi data valida se non trovata vicino a keyword
        for (String pattern : patternsToTry) {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(normalizedText);
            if (m.find()) {
                return formatFoundDate(m.group(), pattern);
            }
        }

        return null;
    }

    private String formatFoundDate(String rawDate, String pattern) {
        // Rimuove caratteri non numerici per normalizzare
        String digits = rawDate.replaceAll("\\D", "");

        if (digits.length() == 8) { // DDMMYYYY o YYYYMMDD
            if (rawDate.matches(".*20[2-3][0-9].*")) {
                if (digits.startsWith("20")) { // YYYYMMDD
                    return digits.substring(6, 8) + "/" + digits.substring(4, 6) + "/" + digits.substring(0, 4);
                } else { // DDMMYYYY
                    return digits.substring(0, 2) + "/" + digits.substring(2, 4) + "/" + digits.substring(4, 8);
                }
            }
        } else if (digits.length() == 6) { // DDMMYY o MMYYYY
            if (pattern.contains("20[2-3]")) { // MMYYYY
                return "01/" + digits.substring(0, 2) + "/" + digits.substring(2, 6);
            } else { // DDMMYY
                return digits.substring(0, 2) + "/" + digits.substring(2, 4) + "/20" + digits.substring(4, 6);
            }
        }

        // Fallback: prova a pulire i separatori
        return rawDate.replaceAll("[\\-/.\\s]+", "/");
    }

    private void stopCamera(ProcessCameraProvider cameraProvider) {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (layoutScannerContainer != null) {
            layoutScannerContainer.setVisibility(View.GONE);
        }
    }

    private void stopCamera() {
        isScanning = false;
        if (cameraProviderFuture != null) {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                stopCamera(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e("AddProductActivity", "Errore nello stop della fotocamera", e);
            }
        }
    }

    private void processImageForBoth(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (inputStream != null) {
                inputStream.close();
            }

            if (bitmap != null) {
                // Barcode search
                String barcode = decodeBarcode(bitmap);
                if (barcode != null) {
                    Log.d("BarcodeScanner", "Codice a barre da immagine trovato: " + barcode);
                    runOnUiThread(() -> {
                        editTextBarcode.setText(barcode);
                        fetchProductDetailsFromApi(barcode);
                    });
                }

                // OCR search
                tessBaseAPI.setImage(bitmap);
                String resultText = tessBaseAPI.getUTF8Text();
                String parsedDate = parseExpiryDate(resultText);
                if (parsedDate != null) {
                    runOnUiThread(() -> {
                        editTextExpiryDate.setText(parsedDate);
                        if (buttonRescanExpiryDate != null)
                            buttonRescanExpiryDate.setVisibility(View.VISIBLE);
                        Toast.makeText(this, "Data trovata (Gallery OCR): " + parsedDate, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("Scanner", "Errore nel processare l'immagine della galleria", e);
        }
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
                currentProductNameFromApi = productBeingEdited.product.getProductName();

                if (productBeingEdited.product.getExpiryDate() != 0) {
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

    private void fetchProductDetailsFromApi(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.err_barcode), Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("OpenFoodFacts", "Fetching details for barcode: " + barcode);
        Toast.makeText(this, getString(R.string.notify_load_product), Toast.LENGTH_SHORT).show();

        OpenFoodFactsApiService OffApiService = OpenFoodFactsRetrofitClient.getApiService(getApplicationContext());
        if (OffApiService == null) {
            Toast.makeText(this, getString(R.string.err_api), Toast.LENGTH_SHORT).show();
            return;
        }

        if (OffApiService != null) {
            String fieldsToFetch = "product_name_it,product_name,image_front_url,image_url,categories_tags";
            retrofit2.Call<OpenFoodFactsProductResponse> call = OffApiService.getProductByBarcode(barcode,
                    fieldsToFetch);

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
                                imageUrl = productData.getImageUrl();
                            }
                            currentImageUrlFromApi = imageUrl;
                            if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                                Glide.with(AddProductActivity.this)
                                        .load(imageUrl)
                                        .into(imageViewProduct);
                                imageViewProduct.setVisibility(View.VISIBLE);
                            } else {
                                imageViewProduct.setVisibility(GONE);
                                Log.w("OpenFoodFacts", "URL immagine non trovato per: " + barcode);
                            }
                            List<String> fetchedCategories = productData.getCategoriesTags();
                            if (fetchedCategories != null && !fetchedCategories.isEmpty()) {
                                currentProductTagsSet.clear();
                                currentProductTagsSet.addAll(fetchedCategories);
                                Log.d("OpenFoodFacts", "Categories fetched: " + fetchedCategories);
                            } else {
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
                public void onFailure(@NonNull retrofit2.Call<OpenFoodFactsProductResponse> call,
                        @NonNull Throwable t) {
                    Log.e("OpenFoodFacts", "Fallimento chiamata API", t);
                    Toast.makeText(AddProductActivity.this, getString(R.string.err_network), Toast.LENGTH_LONG).show();
                    clearProductApiFieldsAndData();
                }
            });
        }

    }

    private String decodeBarcode(Bitmap bitmap) {
        MultiFormatReader reader = new MultiFormatReader();
        Hashtable<DecodeHintType, Object> hints = new Hashtable<>();
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39));
        reader.setHints(hints);

        try {
            int[] intArray = new int[bitmap.getWidth() * bitmap.getHeight()];
            bitmap.getPixels(intArray, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
            RGBLuminanceSource source = new RGBLuminanceSource(bitmap.getWidth(), bitmap.getHeight(), intArray);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = reader.decode(binaryBitmap);
            return result.getText();
        } catch (Exception e) {
            // Se fallisce, proviamo con l'immagine invertita
            try {
                int[] intArray = new int[bitmap.getWidth() * bitmap.getHeight()];
                bitmap.getPixels(intArray, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
                RGBLuminanceSource source = new RGBLuminanceSource(bitmap.getWidth(), bitmap.getHeight(), intArray);
                BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(new InvertedLuminanceSource(source)));
                Result result = reader.decode(binaryBitmap);
                return result.getText();
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private void clearProductApiFieldsAndData() {
        editTextProductName.setText("");
        imageViewProduct.setImageDrawable(null);
        imageViewProduct.setVisibility(GONE);
        currentProductNameFromApi = null;
        currentImageUrlFromApi = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isEditMode && isCameraPermissionGranted && isScanning) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCamera();
    }

    // Metodo onDestroy rimosso perché duplicato alla fine del file

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
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (tessBaseAPI != null) {
            tessBaseAPI.recycle();
        }
    }

}