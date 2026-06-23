package com.example.pestdetection;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.view.View;
import androidx.core.content.FileProvider;

public class MainActivity extends AppCompatActivity {

    ImageView imageView;
    TextView resultText, treatmentText, tvConnectionStatus, tvAppSubtitle;
    View statusBadgeContainer, statusIndicatorDot;
    Uri imageUri;
    EditText etServerUrl;

    // View references for enhanced UX
    ScrollView mainScrollView;
    View loadingOverlay;
    ProgressBar progressBar;
    Button btnSelect, btnCamera, btnUpload;
    View resultCard;
    View settingsCard;
    int titleClickCount = 0;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    imageUri = result.getData().getData();
                    imageView.setImageURI(imageUri);
                    if (btnUpload != null) {
                        btnUpload.setEnabled(true);
                    }
                }
            });

    ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success) {
                    imageView.setImageURI(imageUri);
                    if (btnUpload != null) {
                        btnUpload.setEnabled(true);
                    }
                }
            });

    ActivityResultLauncher<String> requestCameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    launchCameraIntent();
                } else {
                    Toast.makeText(this, "Camera permission required to take pictures", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainScrollView = findViewById(R.id.mainScrollView);
        imageView = findViewById(R.id.imageView);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        progressBar = findViewById(R.id.progressBar);
        btnSelect = findViewById(R.id.btnSelect);
        btnCamera = findViewById(R.id.btnCamera);
        btnUpload = findViewById(R.id.btnUpload);
        resultCard = findViewById(R.id.resultCard);
        settingsCard = findViewById(R.id.settingsCard);

        TextView tvAppName = findViewById(R.id.tvAppName);
        if (tvAppName != null) {
            tvAppName.setOnClickListener(v -> {
                titleClickCount++;
                if (titleClickCount >= 5) {
                    if (settingsCard != null) {
                        settingsCard.setVisibility(View.VISIBLE);
                        Toast.makeText(MainActivity.this, "Developer Mode: Server settings unlocked", Toast.LENGTH_SHORT).show();
                    }
                    titleClickCount = 0;
                }
            });
        }
        resultText = findViewById(R.id.resultText);
        treatmentText = findViewById(R.id.treatmentText);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        statusBadgeContainer = findViewById(R.id.statusBadgeContainer);
        statusIndicatorDot = findViewById(R.id.statusIndicatorDot);
        tvAppSubtitle = findViewById(R.id.tvAppSubtitle);

        etServerUrl = findViewById(R.id.etServerUrl);
        View settingsHeader = findViewById(R.id.settingsHeader);
        View settingsContent = findViewById(R.id.settingsContent);
        ImageView settingsToggleArrow = findViewById(R.id.settingsToggleArrow);
        Button btnTestConnection = findViewById(R.id.btnTestConnection);

        if (btnUpload != null) {
            btnUpload.setEnabled(false); // Initially disable upload button
        }

        etServerUrl.setText(ApiConfig.getPredictUrl(this));

        settingsHeader.setOnClickListener(v -> {
            if (settingsContent.getVisibility() == View.GONE) {
                settingsContent.setVisibility(View.VISIBLE);
                settingsToggleArrow.setImageResource(android.R.drawable.arrow_up_float);
            } else {
                settingsContent.setVisibility(View.GONE);
                settingsToggleArrow.setImageResource(android.R.drawable.arrow_down_float);
                saveServerUrlFromInput();
            }
        });

        btnTestConnection.setOnClickListener(v -> {
            saveServerUrlFromInput();
            checkBackendConnection();
        });

        btnSelect.setOnClickListener(v -> openGallery());
        btnCamera.setOnClickListener(v -> openCamera());
        btnUpload.setOnClickListener(v -> uploadImage());

        registerNetworkMonitor();
        checkBackendConnection();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkBackendConnection();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (imageView != null) {
            imageView.setImageResource(R.mipmap.ic_launcher);
        }
        if (btnUpload != null) {
            btnUpload.setEnabled(false);
        }
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
        }
        if (resultCard != null) {
            resultCard.setVisibility(View.GONE);
        }
        if (resultText != null) {
            resultText.setText("Waiting for scan...");
        }
        if (treatmentText != null) {
            treatmentText.setText("N/A");
        }
        imageUri = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
    }

    private void saveServerUrlFromInput() {
        String newUrl = etServerUrl.getText().toString().trim();
        if (!newUrl.isEmpty()) {
            if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                newUrl = "http://" + newUrl;
            }
            newUrl = newUrl.replaceAll("\\s+", "");
            ApiConfig.savePredictUrl(this, newUrl);
            etServerUrl.setText(ApiConfig.getPredictUrl(this));
        }
    }

    private void registerNetworkMonitor() {
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return;
        }

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                runOnUiThread(() -> checkBackendConnection());
            }

            @Override
            public void onLost(Network network) {
                runOnUiThread(() -> updateConnectionStatus(false, "No network"));
            }
        };

        connectivityManager.registerNetworkCallback(request, networkCallback);
    }

    private void checkBackendConnection() {
        if (!isNetworkAvailable()) {
            updateConnectionStatus(false, "No network");
            return;
        }

        updateConnectionStatus(null, "Checking server...");
        String healthUrl = ApiConfig.getHealthUrl(this);

        PestApiClient.getInstance().checkHealth(healthUrl, (connected, message) -> {
            updateConnectionStatus(connected, message);
        });
    }

    private boolean isNetworkAvailable() {
        if (connectivityManager == null) {
            return true;
        }
        Network network = connectivityManager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void updateConnectionStatus(Boolean connected, String message) {
        if (statusBadgeContainer == null || statusIndicatorDot == null || tvConnectionStatus == null) {
            return;
        }

        int colorBg, colorDotText;
        String statusText;

        if (connected == null) {
            colorBg = getColor(R.color.status_gray_bg);
            colorDotText = getColor(R.color.text_hint);
            statusText = "Checking...";
        } else if (connected) {
            colorBg = getColor(R.color.primary_green_light);
            colorDotText = getColor(R.color.primary_green);
            statusText = "Online";
        } else {
            colorBg = getColor(R.color.status_red_bg);
            colorDotText = getColor(R.color.status_red_text);
            statusText = "Offline";
        }

        statusBadgeContainer.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colorBg));
        statusIndicatorDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colorDotText));
        tvConnectionStatus.setTextColor(colorDotText);
        tvConnectionStatus.setText(statusText);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void openCamera() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            launchCameraIntent();
        } else {
            requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA);
        }
    }

    private void launchCameraIntent() {
        try {
            File photoFile = createImageFile();
            imageUri = FileProvider.getUriForFile(this,
                    "com.example.pestdetection.fileprovider", photoFile);
            takePictureLauncher.launch(imageUri);
        } catch (Exception e) {
            Toast.makeText(this, "Error creating file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws java.io.IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void uploadImage() {
        if (imageUri == null) {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            saveServerUrlFromInput();
            final String serverUrl = ApiConfig.getPredictUrl(this);

            resultText.setText("Analyzing...");
            treatmentText.setText("Please wait...");

            // Show loaders and disable action controls to avoid duplicate clicks
            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
            if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
            if (resultCard != null) resultCard.setVisibility(View.GONE);
            if (btnUpload != null) btnUpload.setEnabled(false);
            if (btnSelect != null) btnSelect.setEnabled(false);
            if (btnCamera != null) btnCamera.setEnabled(false);

            // Scaled and compressed image bytes on-device
            byte[] imageBytes = getScaledAndCompressedImage(imageUri);

            PestApiClient.getInstance().predict(serverUrl, imageBytes, new PestApiClient.PredictCallback() {
                @Override
                public void onSuccess(String pest, double confidence, String treatment) {
                    resultText.setText(pest + " (" + (int) (confidence * 100) + "%)");
                    treatmentText.setText(treatment);
                    updateConnectionStatus(true, "Connected");

                    // Hide loaders and re-enable buttons
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                    if (resultCard != null) resultCard.setVisibility(View.VISIBLE);
                    if (btnUpload != null) btnUpload.setEnabled(true);
                    if (btnSelect != null) btnSelect.setEnabled(true);
                    if (btnCamera != null) btnCamera.setEnabled(true);

                    // Auto-scroll layout to reveal results
                    if (mainScrollView != null) {
                        mainScrollView.post(() -> mainScrollView.fullScroll(View.FOCUS_DOWN));
                    }
                }

                @Override
                public void onError(String message) {
                    resultText.setText("FAILED: " + message);
                    treatmentText.setText("Check server URL in Connection Settings");
                    updateConnectionStatus(false, message);

                    // Hide loaders and re-enable buttons
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                    if (resultCard != null) resultCard.setVisibility(View.VISIBLE);
                    if (btnUpload != null) btnUpload.setEnabled(true);
                    if (btnSelect != null) btnSelect.setEnabled(true);
                    if (btnCamera != null) btnCamera.setEnabled(true);

                    android.util.Log.e("PestScanner", "Connection Error to " + serverUrl + ": " + message);
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            resultText.setText("Error: " + e.getMessage());

            // Reset loaders and re-enable buttons on error
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
            if (resultCard != null) resultCard.setVisibility(View.VISIBLE);
            if (btnUpload != null) btnUpload.setEnabled(true);
            if (btnSelect != null) btnSelect.setEnabled(true);
            if (btnCamera != null) btnCamera.setEnabled(true);
        }
    }

    private byte[] getScaledAndCompressedImage(Uri uri) throws Exception {
        InputStream input = getContentResolver().openInputStream(uri);
        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(input);
        if (input != null) {
            input.close();
        }

        if (bitmap == null) {
            throw new Exception("Failed to decode image");
        }

        int maxSize = 1024;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (width > maxSize || height > maxSize) {
            float ratio = (float) width / (float) height;
            if (ratio > 1) {
                width = maxSize;
                height = (int) (maxSize / ratio);
            } else {
                height = maxSize;
                width = (int) (maxSize * ratio);
            }
            android.graphics.Bitmap resizedBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, width, height, true);
            if (resizedBitmap != bitmap) {
                bitmap.recycle();
            }
            bitmap = resizedBitmap;
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outputStream);
        byte[] bytes = outputStream.toByteArray();
        bitmap.recycle();
        return bytes;
    }
}
