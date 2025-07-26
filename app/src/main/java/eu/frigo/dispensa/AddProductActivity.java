package eu.frigo.dispensa; // Assicurati che il package sia corretto

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.telecom.Call;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

// Per la scansione del codice a barre, dovrai aggiungere una libreria,
// ad esempio ML Kit Barcode Scanning o una libreria di terze parti come ZXing.
// Esempio con un placeholder per l'intent di scansione:
// import com.journeyapps.barcodescanner.ScanContract;
// import com.journeyapps.barcodescanner.ScanOptions;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.frigo.dispensa.data.AppDatabase;
import eu.frigo.dispensa.data.Product;
import eu.frigo.dispensa.network.OpenFoodFactsApiService;
import eu.frigo.dispensa.network.RetrofitClient;
import eu.frigo.dispensa.network.model.OpenFoodFactsProductResponse;
import eu.frigo.dispensa.viewmodel.AddProductViewModel;
import retrofit2.Callback;
import retrofit2.Response;

public class AddProductActivity extends AppCompatActivity {

    private TextInputEditText editTextBarcode;
    private ImageButton buttonScanBarcode;
    private TextInputEditText editTextQuantity;
    private TextInputEditText editTextExpiryDate;
    private Button buttonSaveProduct;
    private Toolbar toolbarAddProduct; // Variabile per la Toolbar
    private AddProductViewModel addProductViewModel;
    private int currentProductId = -1; // Per tenere traccia dell'ID del prodotto da modificare, -1 se è un nuovo prodotto
    private boolean isEditMode = false;
    private androidx.camera.view.PreviewView previewViewBarcode; // Aggiungeremo questo al layout
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
                    getSupportActionBar().setTitle(getString(R.string.add_product)); // Titolo della pagina
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
            buttonSaveProduct = findViewById(R.id.buttonSaveProduct);

            BarcodeScannerOptions options =
                    new BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(
                                    Barcode.FORMAT_EAN_13,
                                    Barcode.FORMAT_EAN_8,
                                    Barcode.FORMAT_UPC_A,
                                    Barcode.FORMAT_UPC_E,
                                    Barcode.FORMAT_QR_CODE, // Se vuoi anche i QR
                                    Barcode.FORMAT_CODE_128, // Formati comuni
                                    Barcode.FORMAT_CODE_39,
                                    Barcode.FORMAT_CODABAR
                            )
                            .build();
            barcodeScanner = BarcodeScanning.getClient(options);
            buttonScanBarcode.setOnClickListener(v -> {
                checkCameraPermissionAndStartScanner();
            });
            editTextBarcode.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) { // Quando l'utente esce dal campo
                    String barcode = editTextBarcode.getText().toString().trim();
                    if (!barcode.isEmpty() && (scannedBarcodeValue == null || !barcode.equals(scannedBarcodeValue))) {
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
                editTextProductName.setText(productName);
                editTextBarcode.setText(barcode);
                editTextQuantity.setText(String.valueOf(quantity));
                editTextExpiryDate.setText(expiryDate);
                if (expiryDate != null && !expiryDate.isEmpty()) {
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.ITALY);
                        calendar.setTime(Objects.requireNonNull(sdf.parse(expiryDate)));
                    } catch (ParseException e) {
                        Log.e("ParseDate", e.getMessage());
                    }
                }
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle("Modifica Prodotto"); // Cambia titolo in modalità modifica
                }
                buttonSaveProduct.setText("Aggiorna Prodotto"); // Cambia testo del pulsante
            } else {
                isEditMode = false;
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(getString(R.string.add_product_title));
                }
                buttonSaveProduct.setText(getString(R.string.save_product_button));
            }

            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowHomeEnabled(true);
            }

            editTextExpiryDate.setOnClickListener(v -> showDatePickerDialog());
            buttonScanBarcode.setOnClickListener(v -> checkCameraPermissionAndStartScanner());
            buttonSaveProduct.setOnClickListener(v -> saveOrUpdateProduct());
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
            // Associa i casi d'uso al ciclo di vita della fotocamera
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
        String fieldsToFetch = "product_name_it,product_name,image_front_url,image_url";

        retrofit2.Call<OpenFoodFactsProductResponse> call = apiService.getProductByBarcode(barcode, fieldsToFetch);
        call.enqueue(new Callback<OpenFoodFactsProductResponse>() {
            @Override
            public void onResponse(retrofit2.Call<OpenFoodFactsProductResponse> call, Response<OpenFoodFactsProductResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    OpenFoodFactsProductResponse apiResponse = response.body();
                    if (apiResponse.getStatus() == 1 && apiResponse.getProduct() != null) {
                        OpenFoodFactsProductResponse.ProductData productData = apiResponse.getProduct();
                        String productName = productData.getProductNameIt();
                        if (productName == null || productName.trim().isEmpty()) {
                            productName = productData.getProductName(); // Fallback al nome generico
                        }
                        currentProductNameFromApi = productName;
                        if (productName != null && !productName.trim().isEmpty()) {
                            editTextProductName.setText(productName);
                        } else {
                            editTextProductName.setText(""); // O un messaggio tipo "Nome non trovato"
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
                                    //.placeholder(R.drawable.ic_placeholder_image)
                                    //.error(R.drawable.ic_error_image)
                                    .into(imageViewProduct);
                            imageViewProduct.setVisibility(View.VISIBLE);
                        } else {
                            imageViewProduct.setVisibility(View.GONE); // O mostra un placeholder
                            Log.w("OpenFoodFacts", "URL immagine non trovato per: " + barcode);
                        }
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
            public void onFailure(retrofit2.Call<OpenFoodFactsProductResponse> call, Throwable t) {
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
        // Rilascia le risorse
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (barcodeScanner != null) {
            barcodeScanner.close(); // Importante per rilasciare risorse di ML Kit
        }
    }

    private void saveOrUpdateProduct() {
        String barcode = editTextBarcode.getText() != null ? editTextBarcode.getText().toString().trim() : "";
        String name = editTextProductName.getText() != null ? editTextProductName.getText().toString().trim() : "";
        String quantityStr = editTextQuantity.getText() != null ? editTextQuantity.getText().toString().trim() : "";
        String expiryDate = editTextExpiryDate.getText() != null ? editTextExpiryDate.getText().toString().trim() : "";

        // Validazione (come prima)
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
       if (currentProductNameFromApi != null && !currentProductNameFromApi.isEmpty()){
            name=currentProductNameFromApi;
        } else {
            name=barcode;
        }

        Product product = new Product(barcode, quantity, expiryDate,name,currentImageUrlFromApi);
        if(isEditMode) {
            product.setId(currentProductId);
            addProductViewModel.update(product);
        }else {
            addProductViewModel.insert(product);
        }
        clearProductApiFieldsAndData();
        Intent resultIntent = new Intent();
        resultIntent.putExtra("NEW_PRODUCT_BARCODE", barcode);
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
            String myFormat = "dd/MM/yyyy"; // Scegli il formato che preferisci
            SimpleDateFormat sdf = new SimpleDateFormat(myFormat, Locale.ITALY);
            editTextExpiryDate.setText(sdf.format(calendar.getTime()));
        }

        @Override
        public boolean onSupportNavigateUp() {
            onBackPressed(); // O finish();
            return true;
        }
    }

