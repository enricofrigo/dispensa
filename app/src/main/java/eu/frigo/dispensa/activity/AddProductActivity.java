package eu.frigo.dispensa.activity;

import static android.view.View.GONE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
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
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.Log;

import com.bumptech.glide.Glide;
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
import com.google.zxing.NotFoundException;
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
import eu.frigo.dispensa.network.tosano.TosanoApiService;
import eu.frigo.dispensa.network.tosano.TosanoRetrofitClient;
import eu.frigo.dispensa.network.tosano.model.TosanoApiResponse;
import eu.frigo.dispensa.util.KeyboardUtils;
import eu.frigo.dispensa.viewmodel.AddProductViewModel;
import eu.frigo.dispensa.util.DateConverter;
import retrofit2.Callback;
import retrofit2.Response;

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
    private DecoratedBarcodeView barcodeView;
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

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    isCameraPermissionGranted = true;
                    Toast.makeText(this, getString(R.string.camera_permission_accepted), Toast.LENGTH_SHORT).show();
                    startCamera();
                } else {
                    isCameraPermissionGranted = false;
                    Toast.makeText(this, getString(R.string.camera_permission_denied), Toast.LENGTH_LONG).show();
                }
            });

    private final BarcodeCallback barcodeCallback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (result != null && result.getText() != null && !result.getText().isEmpty() && isScanning) {
                String barcode = result.getText();

                // Verifica che sia un codice valido per prodotti
                if (isValidProductBarcode(barcode)) {
                    isScanning = false;
                    Log.d("BarcodeScanner", "Codice a barre trovato: " + barcode + " (Formato: " + result.getBarcodeFormat() + ")");

                    runOnUiThread(() -> {
                        editTextBarcode.setText(barcode);
                        stopCamera();
                        fetchProductDetailsFromApi(barcode);
                    });
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_product);

        // Inizializza ZXing reader per galleria
        initializeZXingReader();

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
        ImageButton buttonScanBarcodeCamera = findViewById(R.id.buttonScanBarcodeCamera);
        ImageButton buttonScanBarcodeGallery = findViewById(R.id.buttonScanBarcodeGallery);
        editTextQuantity = findViewById(R.id.editTextQuantity);
        buttonDecrementQuantityActivity = findViewById(R.id.buttonDecrementQuantityActivity);
        buttonIncrementQuantityActivity = findViewById(R.id.buttonIncrementQuantityActivity);
        editTextExpiryDate = findViewById(R.id.editTextExpiryDate);
        barcodeView = findViewById(R.id.previewViewBarcode);
        editTextProductName = findViewById(R.id.editTextProductName);
        imageViewProduct = findViewById(R.id.imageViewProduct);
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
        ExtendedFloatingActionButton fabButtonSaveProduct = findViewById(R.id.buttonSaveProduct);

        // Configura ZXing embedded per formati prodotti supermercato
        List<BarcodeFormat> formats = Arrays.asList(
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39
        );
        barcodeView.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(formats));
        barcodeView.setStatusText("Inquadra il codice a barre del prodotto");

        pickImageForBarcodeLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        Log.d("BarcodeScan", "Immagine selezionata dalla galleria per barcode: " + uri.toString());
                        stopCamera();
                        processImageForBarcode(uri);
                    } else {
                        Log.d("BarcodeScan", "Selezione immagine per barcode annullata.");
                    }
                }
        );

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
            buttonScanBarcodeGallery.setVisibility(GONE);
            buttonScanBarcodeCamera.setVisibility(GONE);
            editTextBarcode.setVisibility(GONE);
            barcodeView.setVisibility(GONE);
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

        buttonScanBarcodeCamera.setOnClickListener(v -> {
            isScanning = true;
            checkCameraPermissionAndStartScanner();
        });

        buttonScanBarcodeGallery.setOnClickListener(v -> pickImageForBarcodeLauncher.launch("image/*"));
        buttonMarkAsClosed.setOnClickListener(v -> {
            currentOpenedDate = 0L;
            updateOpenedDateUI(currentOpenedDate);
        });
        fabButtonSaveProduct.setOnClickListener(v -> saveOrUpdateProduct());
    }

    private void initializeZXingReader() {
        multiFormatReader = new MultiFormatReader();
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);

        List<BarcodeFormat> formats = Arrays.asList(
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39
        );

        hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        multiFormatReader.setHints(hints);
    }

    private boolean isValidProductBarcode(String barcode) {
        if (barcode == null || barcode.isEmpty()) {
            return false;
        }

        int length = barcode.length();
        return length == 8 || length == 12 || length == 13 || length == 14;
    }

    private void processImageForBarcode(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (inputStream != null) {
                inputStream.close();
            }

            if (bitmap != null) {
                String barcode = decodeBarcode(bitmap);
                if (barcode != null) {
                    Log.d("BarcodeScanner", "Codice a barre da immagine trovato: " + barcode);
                    runOnUiThread(() -> {
                        editTextBarcode.setText(barcode);
                        fetchProductDetailsFromApi(barcode);
                    });
                } else {
                    Log.d("BarcodeScanner", "Nessun codice a barre trovato nell'immagine.");
                    runOnUiThread(() ->
                            Toast.makeText(this, getString(R.string.no_barcode_in_image), Toast.LENGTH_SHORT).show()
                    );
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("BarcodeScanner", "Errore nel creare Bitmap da URI", e);
            Toast.makeText(this, getString(R.string.err_load_image), Toast.LENGTH_SHORT).show();
        }
    }

    private String decodeBarcode(Bitmap bitmap) {
        // Ridimensiona se troppo grande
        final int MAX_SIZE = 1080;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width > MAX_SIZE || height > MAX_SIZE) {
            float ratio = Math.min((float)MAX_SIZE / width, (float)MAX_SIZE / height);
            width = Math.round(width * ratio);
            height = Math.round(height * ratio);
            bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
        }

        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);

        MultiFormatReader reader = new MultiFormatReader();
        Hashtable<DecodeHintType, Object> hints = new Hashtable<>();
        // Dai istruzione di provare forte e tutti i formati principali
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39,
                BarcodeFormat.CODE_93,
                BarcodeFormat.ITF,
                BarcodeFormat.QR_CODE
        ));

        Bitmap[] bitmaps = new Bitmap[4];
        bitmaps[0] = bitmap;
        bitmaps[1] = rotateBitmap(bitmap, 90);
        bitmaps[2] = rotateBitmap(bitmap, 180);
        bitmaps[3] = rotateBitmap(bitmap, 270);

        for (Bitmap b : bitmaps) {
            // Primo tentativo normale
            String resultText = decodeZXingOnce(b, reader, hints, false);
            if (resultText != null) return resultText;
            // Secondo: invertito
            resultText = decodeZXingOnce(b, reader, hints, true);
            if (resultText != null) return resultText;
        }
        return null;
    }

    private String decodeZXingOnce(Bitmap bitmap, MultiFormatReader reader, Hashtable<DecodeHintType, Object> hints, boolean inverted) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);

        BinaryBitmap binaryBitmap = inverted
                ? new BinaryBitmap(new HybridBinarizer(new InvertedLuminanceSource(source)))
                : new BinaryBitmap(new HybridBinarizer(source));
        try {
            Result result = reader.decode(binaryBitmap, hints);
            return result.getText();
        } catch (Exception e) {
            return null;
        } finally {
            reader.reset();
        }
    }

    private Bitmap rotateBitmap(Bitmap b, int degrees) {
        Matrix m = new Matrix();
        m.postRotate(degrees);
        return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
    }
    private void updateOpenedDateUI(Long openedTimestamp) {
        if (buttonMarkAsOpened == null || textViewOpenedDate == null) return;

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
        if (timestamp == null || timestamp <= 0) return getString(R.string.not_defined);
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
                    Log.d("AddProductActivity", "Location selezionata: " + availableLocations.get(position).getName() + " (Key: " + selectedStorageInternalKey + ")");
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
                Log.d("AddProductActivity", "Spinner preselezionato (dinamicamente) su: " + availableLocations.get(i).getName());
                return;
            }
        }
        Log.w("AddProductActivity", "Valore di location (internalKey) '" + internalKey + "' non trovato nello spinner dinamico.");
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

        barcodeView.setVisibility(View.VISIBLE);
        isScanning = true;
        barcodeView.decodeContinuous(barcodeCallback);
        barcodeView.resume();
    }

    private void stopCamera() {
        isScanning = false;
        if (barcodeView != null) {
            barcodeView.pause();
            barcodeView.setVisibility(GONE);
        }
    }

    private void fetchProductDetailsFromApi(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.err_barcode), Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("OpenFoodFacts", "Fetching details for barcode: " + barcode);
        Toast.makeText(this, getString(R.string.notify_load_product), Toast.LENGTH_SHORT).show();

        OpenFoodFactsApiService OffApiService = OpenFoodFactsRetrofitClient.getApiService();
        String fieldsToFetch = "product_name_it,product_name,image_front_url,image_url,categories_tags";
        retrofit2.Call<OpenFoodFactsProductResponse> call = OffApiService.getProductByBarcode(barcode, fieldsToFetch);

        call.enqueue(new Callback<OpenFoodFactsProductResponse>() {
            @Override
            public void onResponse(@NonNull retrofit2.Call<OpenFoodFactsProductResponse> call, @NonNull Response<OpenFoodFactsProductResponse> response) {
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
                        Toast.makeText(AddProductActivity.this, getString(R.string.notify_loaded_product), Toast.LENGTH_SHORT).show();

                    } else {
                        Log.w("OpenFoodFacts", "Prodotto non trovato o dati mancanti nell'API per: " + barcode + ", Status: " + apiResponse.getStatus());
                        Toast.makeText(AddProductActivity.this, "Prodotto non trovato su Open Food Facts", Toast.LENGTH_LONG).show();
                        clearProductApiFieldsAndData();
                    }
                } else {
                    Log.e("OpenFoodFacts", "Errore nella risposta API: " + response.code() + " - " + response.message());
                    Toast.makeText(AddProductActivity.this, "Errore nel caricare i dati: " + response.code(), Toast.LENGTH_LONG).show();
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

        TosanoApiService tosanoApiService = TosanoRetrofitClient.getApiService(getApplicationContext());
        if (tosanoApiService != null){
            retrofit2.Call<TosanoApiResponse> callT = tosanoApiService.getProductByBarcode(barcode);
            callT.enqueue(new Callback<TosanoApiResponse>() {
                @Override
                public void onResponse(@NonNull retrofit2.Call<TosanoApiResponse> call, @NonNull Response<TosanoApiResponse> response) {
                    Log.d("Tosano", "Risposta API: " + response.body());
                }
                @Override
                public void onFailure(@NonNull retrofit2.Call<TosanoApiResponse> call, @NonNull Throwable t) {
                    Log.e("Tosano", "Errore chiamata API", t);
                }
            });
        }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (barcodeView != null) {
            barcodeView.pause();
        }
    }

    private void saveOrUpdateProduct() {
        String barcode = editTextBarcode.getText() != null ? editTextBarcode.getText().toString().trim() : "";
        String name = editTextProductName.getText() != null ? editTextProductName.getText().toString().trim() : "";
        String quantityStr = editTextQuantity.getText() != null ? editTextQuantity.getText().toString().trim() : "";
        String expiryDate = editTextExpiryDate.getText() != null ? editTextExpiryDate.getText().toString().trim() : "";
        String shelfLifeStr = editTextShelfLifeAfterOpening.getText() != null ? editTextShelfLifeAfterOpening.getText().toString().trim() : "";
        int shelfLifeDays = -1;
        if (!shelfLifeStr.isEmpty()) {
            try {
                shelfLifeDays = Integer.parseInt(shelfLifeStr);
                if (shelfLifeDays < 0) shelfLifeDays = -1;
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
        Product product = new Product(barcode, quantity, DateConverter.parseDisplayDateToTimestampMs(expiryDate), name, currentImageUrlFromApi, selectedStorageInternalKey, currentOpenedDate, shelfLifeDays);
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
        onBackPressed();
        return true;
    }

}