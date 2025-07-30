package eu.frigo.dispensa;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.util.Size;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Se vuoi una Toolbar anche qui
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.Log;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.frigo.dispensa.data.CategoryDefinition;
import eu.frigo.dispensa.data.Product;
import eu.frigo.dispensa.network.OpenFoodFactsApiService;
import eu.frigo.dispensa.network.RetrofitClient;
import eu.frigo.dispensa.network.model.OpenFoodFactsProductResponse;
import eu.frigo.dispensa.viewmodel.AddProductViewModel;
import eu.frigo.dispensa.util.DateConverter;
import retrofit2.Callback;
import retrofit2.Response;

public class AddProductActivity extends AppCompatActivity {

    private Spinner spinnerStorageLocation;
    private String selectedStorageLocation;
    public static final String EXTRA_PRODUCT_ID = "extra_product_id";
    private TextInputEditText editTextBarcode;
    private ImageButton buttonScanBarcode;
    private TextInputEditText editTextQuantity;
    private static final String DEFAULT_QUANTITY = "1";
    private TextInputEditText editTextExpiryDate;
    private Button buttonSaveProduct;
    private Toolbar toolbarAddProduct;
    private AddProductViewModel addProductViewModel;
    private int currentProductId = -1;
    private boolean isEditMode = false;
    private androidx.camera.view.PreviewView previewViewBarcode;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ExecutorService cameraExecutor;
    private com.google.mlkit.vision.barcode.BarcodeScanner barcodeScanner;
    private boolean isCameraPermissionGranted;
    private TextInputEditText editTextProductName;
    private ImageView imageViewProduct;
    private String scannedBarcodeValue;
    private final Calendar calendar = Calendar.getInstance();
    private String currentProductNameFromApi;
    private String currentImageUrlFromApi;
    private ChipGroup chipGroupCategories;
    private TextInputEditText editTextNewCategory;
    private Button buttonAddCategory;
    private Set<String> currentProductTagsSet = new HashSet<>();
    //private Collection<String> currentCategoriesFromApi;
    private static final String[] STORAGE_LOCATIONS_DISPLAY = {"Frigorifero", "Freezer", "Dispensa"};
    private static final String[] STORAGE_LOCATIONS_VALUES = {Product.LOCATION_FRIDGE, Product.LOCATION_FREEZER, Product.LOCATION_PANTRY};
    private String preselectedLocationValue = null; // Per memorizzare la location passata


    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    isCameraPermissionGranted = true;
                    Toast.makeText(this, "Permesso fotocamera concesso", Toast.LENGTH_SHORT).show();
                    startCamera(); // Avvia la fotocamera se il permesso è stato concesso ora
                } else {
                    isCameraPermissionGranted = false;
                    Toast.makeText(this, "Permesso fotocamera negato. Impossibile scansionare.", Toast.LENGTH_LONG).show();
                }
            });

        private final ActivityResultLauncher<Intent> barcodeScannerLauncher =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String scannedBarcode = result.getData().getStringExtra("SCANNED_BARCODE_DATA"); // Placeholder
                        if (scannedBarcode != null) {
                            editTextBarcode.setText(scannedBarcode);
                        } else {
                            Toast.makeText(this, "Scansione codice a barre fallita o annullata", Toast.LENGTH_SHORT).show();
                        }
                    }
                });


    @SuppressLint("UnsafeOptInUsageError")
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_add_product);
            addProductViewModel = new ViewModelProvider(this).get(AddProductViewModel.class);
            toolbarAddProduct = findViewById(R.id.toolbar_add_product);
            if (toolbarAddProduct != null) {
                setSupportActionBar(toolbarAddProduct);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                    getSupportActionBar().setTitle(getString(R.string.add_product));
                }
            }

            editTextBarcode = findViewById(R.id.editTextBarcode);
            buttonScanBarcode = findViewById(R.id.buttonScanBarcode);
            editTextQuantity = findViewById(R.id.editTextQuantity);
            editTextExpiryDate = findViewById(R.id.editTextExpiryDate);
            previewViewBarcode = findViewById(R.id.previewViewBarcode);
            editTextProductName = findViewById(R.id.editTextProductName);
            imageViewProduct = findViewById(R.id.imageViewProduct);
            cameraExecutor = Executors.newSingleThreadExecutor();
            spinnerStorageLocation = findViewById(R.id.spinnerStorageLocation);
            if (getIntent().hasExtra("PRESELECTED_LOCATION")) {
                preselectedLocationValue = getIntent().getStringExtra("PRESELECTED_LOCATION");
                Log.d("AddProductActivity", "Ricevuta location preselezionata: " + preselectedLocationValue);
            }
            setupStorageLocationSpinner();
            chipGroupCategories = findViewById(R.id.chipGroupCategories);
            editTextNewCategory = findViewById(R.id.editTextNewCategory);
            buttonAddCategory = findViewById(R.id.buttonAddCategory);
            buttonSaveProduct = findViewById(R.id.buttonSaveProduct);

            BarcodeScannerOptions options =
                    new BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(
                                    Barcode.FORMAT_EAN_13,
                                    Barcode.FORMAT_EAN_8,
                                    Barcode.FORMAT_UPC_A,
                                    Barcode.FORMAT_UPC_E,
                                    Barcode.FORMAT_QR_CODE,
                                    Barcode.FORMAT_CODE_128,
                                    Barcode.FORMAT_CODE_39,
                                    Barcode.FORMAT_CODABAR
                            )
                            .build();
            barcodeScanner = BarcodeScanning.getClient(options);
            buttonScanBarcode.setOnClickListener(v -> {checkCameraPermissionAndStartScanner();});
            editTextBarcode.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    String barcode = Objects.requireNonNull(editTextBarcode.getText()).toString().trim();
                    if (!barcode.isEmpty() && (!barcode.equals(scannedBarcodeValue))) {
                        fetchProductDetailsFromApi(barcode);
                    }
                }
            });

            Intent intent = getIntent();
            if (intent != null && intent.hasExtra("PRODUCT_ID")) {
                isEditMode = true;
                currentProductId = intent.getIntExtra("PRODUCT_ID", -1);
                String barcode = intent.getStringExtra("PRODUCT_BARCODE");
                int quantity = intent.getIntExtra("PRODUCT_QUANTITY", 0);
                String expiryDate = intent.getStringExtra("PRODUCT_EXPIRY_DATE");
                String productName = intent.getStringExtra("PRODUCT_NAME");
                String imageUrl = intent.getStringExtra("PRODUCT_IMAGE");

                currentImageUrlFromApi = imageUrl;
                currentProductNameFromApi = productName;
                editTextProductName.setText(productName);
                editTextBarcode.setText(barcode);
                editTextQuantity.setText(String.valueOf(quantity));
                editTextExpiryDate.setText(expiryDate);
                if (expiryDate != null && !expiryDate.isEmpty()) {
                    calendar.setTime(Objects.requireNonNull(DateConverter.parseDisplayDateToDate(expiryDate)));
                }
                if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                    Glide.with(AddProductActivity.this)
                            .load(imageUrl)
                            .into(imageViewProduct);
                    imageViewProduct.setVisibility(View.VISIBLE);
                }
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle("Modifica Prodotto");
                }
                buttonSaveProduct.setText("Aggiorna Prodotto");
            } else {
                isEditMode = false;
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(getString(R.string.add_product_title));
                }
                buttonSaveProduct.setText(getString(R.string.save_product_button));
                editTextQuantity.setText(DEFAULT_QUANTITY);
                editTextQuantity.setSelection(editTextQuantity.getText().length());

            }
            if (isEditMode && currentProductId != -1) {
                addProductViewModel.getProductById(currentProductId).observe(this, productWithDefs -> {
                    if (productWithDefs != null && productWithDefs.product != null) {
                        String currentLocation = productWithDefs.product.getStorageLocation();
                        if (currentLocation != null) {
                            for (int i = 0; i < STORAGE_LOCATIONS_VALUES.length; i++) {
                                if (STORAGE_LOCATIONS_VALUES[i].equals(currentLocation)) {
                                    spinnerStorageLocation.setSelection(i);
                                    break;
                                }
                            }
                        }
                        if (productWithDefs.categoryDefinitions != null) {
                            currentProductTagsSet.clear();
                            for (CategoryDefinition def : productWithDefs.categoryDefinitions) {
                                currentProductTagsSet.add(def.tagName);
                            }
                            updateChipGroup();
                        }
                    }
                });
            }

            buttonAddCategory.setOnClickListener(v -> addNewCategoryTag());
            editTextNewCategory.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    addNewCategoryTag();
                    return true;
                }
                return false;
            });

            Log.d("AddProductActivity", editTextBarcode.getText()+" "+editTextQuantity.getText()+" "+editTextExpiryDate.getText()+" "+imageViewProduct.toString());
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowHomeEnabled(true);
            }

            editTextExpiryDate.setOnClickListener(v -> showDatePickerDialog());
            buttonScanBarcode.setOnClickListener(v -> checkCameraPermissionAndStartScanner());
            buttonSaveProduct.setOnClickListener(v -> saveOrUpdateProduct());
        }
    private void setupStorageLocationSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, STORAGE_LOCATIONS_DISPLAY);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStorageLocation.setAdapter(adapter);
        if (preselectedLocationValue != null) {
            int selectionIndex = -1;
            for (int i = 0; i < STORAGE_LOCATIONS_VALUES.length; i++) {
                if (preselectedLocationValue.equals(STORAGE_LOCATIONS_VALUES[i])) {
                    selectionIndex = i;
                    break;
                }
            }
            if (selectionIndex != -1) {
                spinnerStorageLocation.setSelection(selectionIndex);
                Log.d("AddProductActivity", "Spinner impostato su: " + STORAGE_LOCATIONS_DISPLAY[selectionIndex]);
            } else {
                Log.w("AddProductActivity", "Valore di location preselezionato '" + preselectedLocationValue + "' non trovato nei valori dello spinner.");
            }
        }
        spinnerStorageLocation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedStorageLocation = STORAGE_LOCATIONS_VALUES[position];
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }
    private void updateChipGroup() {
        chipGroupCategories.removeAllViews(); // Pulisci i chip esistenti
        for (String tag : currentProductTagsSet) {
            Chip chip = new Chip(this);
            chip.setText(tag);
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> {
                currentProductTagsSet.remove(tag); // Rimuovi dal Set
                updateChipGroup(); // Ridisegna la ChipGroup
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
            if (currentProductTagsSet.add(newTag)) { // .add() di Set restituisce true se l'elemento è stato aggiunto (non era già presente)
                updateChipGroup(); // Aggiorna la UI
            }
            editTextNewCategory.setText(""); // Pulisci l'EditText
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

        previewViewBarcode.setVisibility(View.VISIBLE);
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreviewAndAnalysis(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Errore nell'avvio della fotocamera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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

        preview.setSurfaceProvider(previewViewBarcode.getSurfaceProvider());

        ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720)) // Scegli una risoluzione appropriata
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            Image mediaImage = imageProxy.getImage();
            if (mediaImage != null) {
                InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                barcodeScanner.process(image)
                        .addOnSuccessListener(barcodes -> {
                            if (!barcodes.isEmpty()) {
                                for (Barcode barcode : barcodes) {
                                    String rawValue = barcode.getRawValue();
                                    Log.d("BarcodeScanner", "Codice a barre trovato: " + rawValue);

                                    runOnUiThread(() -> {
                                        editTextBarcode.setText(rawValue);
                                        stopCamera(cameraProvider);
                                        Toast.makeText(getApplicationContext(), "Codice: " + rawValue, Toast.LENGTH_SHORT).show();
                                        fetchProductDetailsFromApi(rawValue);
                                    });
                                    return;
                                }
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e("BarcodeScanner", "Errore nella scansione del codice a barre", e);
                        })
                        .addOnCompleteListener(task -> {imageProxy.close();});
            }
        });

        cameraProvider.unbindAll();

        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Log.e("AddProductActivity", "Fallimento nel bind dei casi d'uso della fotocamera", e);
        }
    }
    private void fetchProductDetailsFromApi(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            Toast.makeText(this, "Codice a barre non valido", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("OpenFoodFacts", "Fetching details for barcode: " + barcode);
        Toast.makeText(this, "Caricamento dati prodotto...", Toast.LENGTH_SHORT).show();

        OpenFoodFactsApiService apiService = RetrofitClient.getApiService();
        String fieldsToFetch = "product_name_it,product_name,image_front_url,image_url,categories_tags";

        retrofit2.Call<OpenFoodFactsProductResponse> call = apiService.getProductByBarcode(barcode, fieldsToFetch);
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
                            editTextProductName.setText("Non trovato");
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
                            imageViewProduct.setVisibility(View.GONE); // O mostra un placeholder
                            Log.w("OpenFoodFacts", "URL immagine non trovato per: " + barcode);
                        }
                        List<String> fetchedCategories = productData.getCategoriesTags();
                        if (fetchedCategories != null && !fetchedCategories.isEmpty()) {
                            currentProductTagsSet.clear();
                            currentProductTagsSet.addAll(fetchedCategories);
                            Log.d("OpenFoodFacts", "Categories fetched: " + fetchedCategories);
                        } else {
                            // currentProductTagsSet.clear(); // Decidi se pulire se l'API non restituisce nulla
                            Log.d("OpenFoodFacts", "No categories found from API.");
                        }
                        updateChipGroup();
                        Toast.makeText(AddProductActivity.this, "Dati caricati!", Toast.LENGTH_SHORT).show();

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
                Toast.makeText(AddProductActivity.this, "Errore di rete: " + t.getMessage(), Toast.LENGTH_LONG).show();
                clearProductApiFieldsAndData();
            }
        });
    }
    private void clearProductApiFieldsAndData() {
        editTextProductName.setText("");
        imageViewProduct.setImageDrawable(null);
        imageViewProduct.setVisibility(View.GONE);
        currentProductNameFromApi=null;
        currentImageUrlFromApi=null;
    }
    private void stopCamera(ProcessCameraProvider cameraProvider) {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        previewViewBarcode.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {cameraExecutor.shutdown();}
        if (barcodeScanner != null) {barcodeScanner.close();}
    }

    private void saveOrUpdateProduct() {
        String barcode = editTextBarcode.getText() != null ? editTextBarcode.getText().toString().trim() : "";
        String name = editTextProductName.getText() != null ? editTextProductName.getText().toString().trim() : "";
        String quantityStr = editTextQuantity.getText() != null ? editTextQuantity.getText().toString().trim() : "";
        String expiryDate = editTextExpiryDate.getText() != null ? editTextExpiryDate.getText().toString().trim() : "";

        if (barcode.isEmpty()) {
            editTextBarcode.setError("Il codice a barre è obbligatorio");
            editTextBarcode.requestFocus();
            return;
        }
        if (quantityStr.isEmpty()) {
            editTextQuantity.setError("La quantità è obbligatoria");
            editTextQuantity.requestFocus();
            return;
        }
        int quantity;
        try {
            quantity = Integer.parseInt(quantityStr);
            if (quantity <= 0) {
                editTextQuantity.setError("La quantità deve essere maggiore di zero");
                editTextQuantity.requestFocus();
                return;
            }
        } catch (NumberFormatException e) {
            editTextQuantity.setError("Inserisci un numero valido per la quantità");
            editTextQuantity.requestFocus();
            return;
        }
        if (expiryDate.isEmpty()) {
            editTextExpiryDate.setError("La data di scadenza è obbligatoria");
            Toast.makeText(this, "La data di scadenza è obbligatoria", Toast.LENGTH_SHORT).show();
            return;
        }
        if(name.isEmpty()){
            if (currentProductNameFromApi != null && !currentProductNameFromApi.isEmpty()){
                name=currentProductNameFromApi;
            } else {
                name=barcode;
            }
        }
        Product product = new Product(barcode, quantity, DateConverter.parseDisplayDateToTimestampMs(expiryDate), name, currentImageUrlFromApi, selectedStorageLocation);
        Log.d("AddProductActivity", "Salvataggio prodotto: " + product.toString());
        List<String> tagsToSave = new ArrayList<>(currentProductTagsSet);
        if(isEditMode) {
            product.setId(currentProductId);
            addProductViewModel.update(product,tagsToSave);
        }else {
            addProductViewModel.insert(product,tagsToSave);
        }
        clearProductApiFieldsAndData();
        Intent resultIntent = new Intent();
        resultIntent.putExtra("NEW_PRODUCT_NAME", name);
        resultIntent.putExtra("NEW_PRODUCT_EDIT", isEditMode);
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

