package com.example.pestdetection;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Html;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import org.json.JSONArray;
import org.json.JSONObject;

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
    Button btnMySubmissions, btnReportUnrecognized;
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
        btnMySubmissions = findViewById(R.id.btnMySubmissions);
        btnReportUnrecognized = findViewById(R.id.btnReportUnrecognized);
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
        if (btnMySubmissions != null) {
            btnMySubmissions.setOnClickListener(v -> showMySubmissions());
        }
        if (btnReportUnrecognized != null) {
            btnReportUnrecognized.setOnClickListener(v -> reportUnrecognizedPest());
        }

        registerNetworkMonitor();
        checkBackendConnection();

        if (savedInstanceState != null) {
            String uriStr = savedInstanceState.getString("image_uri");
            if (uriStr != null) {
                imageUri = Uri.parse(uriStr);
                if (imageView != null) {
                    imageView.setImageURI(imageUri);
                }
                if (btnUpload != null) {
                    btnUpload.setEnabled(true);
                }
            }
            String resText = savedInstanceState.getString("result_text");
            if (resText != null && resultText != null) {
                resultText.setText(resText);
            }
            String treatText = savedInstanceState.getString("treatment_text");
            if (treatText != null && treatmentText != null) {
                treatmentText.setText(treatText);
            }
            int cardVis = savedInstanceState.getInt("result_card_visibility", View.GONE);
            if (resultCard != null) {
                resultCard.setVisibility(cardVis);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkBackendConnection();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
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
            stopPolling(); // Cancel any existing polling tasks when starting a new upload
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
            String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

            PestApiClient.getInstance().predict(serverUrl, deviceId, imageBytes, new PestApiClient.PredictCallback() {
                @Override
                public void onSuccess(String pest, double confidence, String treatment, int reportId) {
                    // Hide loaders and re-enable buttons
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                    if (btnSelect != null) btnSelect.setEnabled(true);
                    if (btnCamera != null) btnCamera.setEnabled(true);

                    if ("Invalid Image".equalsIgnoreCase(pest)) {
                        // Clear image preview and states to block further processing
                        imageView.setImageDrawable(null);
                        imageUri = null;
                        if (btnUpload != null) btnUpload.setEnabled(false);
                        if (resultCard != null) resultCard.setVisibility(View.GONE);

                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Invalid Image")
                                .setMessage("Invalid Image: Please submit an image containing a crop pest or infected plant.")
                                .setPositiveButton("OK", null)
                                .show();
                        return;
                    }

                    if (btnUpload != null) btnUpload.setEnabled(true);
                    if (resultCard != null) resultCard.setVisibility(View.VISIBLE);

                    if ("Pest Not Recognized".equalsIgnoreCase(pest)) {
                        resultText.setText("Pest Not Recognized");
                        treatmentText.setText(treatment);
                        if (btnReportUnrecognized != null) btnReportUnrecognized.setVisibility(View.GONE);

                        // Start background polling for auto-reported unrecognized pest
                        if (reportId != -1) {
                            activeReportId = reportId;
                            startPolling();
                        }
                    } else if ("None".equalsIgnoreCase(pest)) {
                        resultText.setText("No Pests Detected");
                        treatmentText.setText(treatment);
                        if (btnReportUnrecognized != null) btnReportUnrecognized.setVisibility(View.VISIBLE);
                    } else {
                        resultText.setText(pest + " (" + (int) (confidence * 100) + "%)");
                        treatmentText.setText(treatment);
                        if (btnReportUnrecognized != null) btnReportUnrecognized.setVisibility(View.VISIBLE);
                    }
                    updateConnectionStatus(true, "Connected");

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

    @Override
    protected void onSaveInstanceState(android.os.Bundle outState) {
        super.onSaveInstanceState(outState);
        if (imageUri != null) {
            outState.putString("image_uri", imageUri.toString());
        }
        if (resultText != null && !resultText.getText().toString().equals("Waiting for scan...")) {
            outState.putString("result_text", resultText.getText().toString());
        }
        if (treatmentText != null && !treatmentText.getText().toString().equals("N/A")) {
            outState.putString("treatment_text", treatmentText.getText().toString());
        }
        if (resultCard != null) {
            outState.putInt("result_card_visibility", resultCard.getVisibility());
        }
    }

    private void reportUnrecognizedPest() {
        if (imageUri == null) {
            Toast.makeText(this, "Please capture or select an image first", Toast.LENGTH_SHORT).show();
            return;
        }

        String serverUrl = etServerUrl.getText().toString().trim();
        String reportUrl;
        if (serverUrl.endsWith("/predict")) {
            reportUrl = serverUrl.replace("/predict", "/predict/report-unrecognized");
        } else {
            reportUrl = serverUrl + "/predict/report-unrecognized";
        }

        byte[] imageBytes;
        try {
            imageBytes = getScaledAndCompressedImage(imageUri);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to process image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
        if (btnReportUnrecognized != null) btnReportUnrecognized.setEnabled(false);

        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        PestApiClient.getInstance().reportUnrecognized(reportUrl, deviceId, imageBytes, new PestApiClient.PredictCallback() {
            @Override
            public void onSuccess(String pest, double confidence, String message, int reportId) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                if (btnReportUnrecognized != null) {
                    btnReportUnrecognized.setEnabled(true);
                    btnReportUnrecognized.setVisibility(View.GONE);
                }

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Report Submitted!")
                        .setMessage("Thank you! Our agricultural experts will analyze this unrecognized pest and update the diagnostic console.")
                        .setPositiveButton("OK", null)
                        .show();
            }

            @Override
            public void onError(String error) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                if (btnReportUnrecognized != null) btnReportUnrecognized.setEnabled(true);

                Toast.makeText(MainActivity.this, "Submission failed: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showMySubmissions() {
        String serverUrl = etServerUrl.getText().toString().trim();
        String statusUrl;
        if (serverUrl.endsWith("/predict")) {
            statusUrl = serverUrl.replace("/predict", "/reports/status");
        } else {
            statusUrl = serverUrl + "/reports/status";
        }

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);

        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        PestApiClient.getInstance().getReportsStatus(statusUrl, deviceId, new PestApiClient.ReportsCallback() {
            @Override
            public void onSuccess(String jsonResult) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);

                try {
                    JSONObject json = new JSONObject(jsonResult);
                    JSONArray reports = json.optJSONArray("reports");
                    if (reports == null || reports.length() == 0) {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("My Submissions")
                                .setMessage("You have not submitted any unrecognized pests yet.")
                                .setPositiveButton("Close", null)
                                .show();
                        return;
                    }

                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < reports.length(); i++) {
                        JSONObject r = reports.getJSONObject(i);
                        String status = r.optString("status", "pending");
                        String date = r.optString("created_at", "");
                        
                        sb.append("<font color='#2E7D32'><b>Submission #").append(r.optInt("id")).append("</b></font><br/>");
                        sb.append("<b>Date:</b> ").append(date).append("<br/>");
                        
                        if ("resolved".equalsIgnoreCase(status)) {
                            sb.append("<b>Status:</b> <font color='#10b981'><b>RESOLVED</b></font><br/>");
                            sb.append("<b>Identified Pest:</b> ").append(r.optString("pest_name")).append("<br/>");
                            sb.append("<b>Treatment Advice:</b> ").append(r.optString("treatment")).append("<br/>");
                        } else {
                            sb.append("<b>Status:</b> <font color='#f59e0b'><b>PENDING REVIEW</b></font><br/>");
                            sb.append("Our experts are currently identifying this pest. Check back later.<br/>");
                        }
                        sb.append("<br/>----------------------------------------<br/><br/>");
                    }

                    TextView tv = new TextView(MainActivity.this);
                    tv.setPadding(40, 40, 40, 40);
                    tv.setTextSize(14);
                    tv.setTextColor(getResources().getColor(android.R.color.black));
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        tv.setText(Html.fromHtml(sb.toString(), Html.FROM_HTML_MODE_LEGACY));
                    } else {
                        tv.setText(Html.fromHtml(sb.toString()));
                    }

                    ScrollView sv = new ScrollView(MainActivity.this);
                    sv.addView(tv);

                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("My Submissions")
                            .setView(sv)
                            .setPositiveButton("Close", null)
                            .show();

                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Failed to parse history: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String error) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);

                Toast.makeText(MainActivity.this, "Failed to fetch history: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private android.os.Handler pollingHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pollingRunnable;
    private int activeReportId = -1;

    private void startPolling() {
        if (pollingRunnable != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
        }
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (activeReportId == -1) return;

                String serverUrl = etServerUrl.getText().toString().trim();
                String statusUrl;
                if (serverUrl.endsWith("/predict")) {
                    statusUrl = serverUrl.replace("/predict", "/reports/status");
                } else {
                    statusUrl = serverUrl + "/reports/status";
                }

                String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

                PestApiClient.getInstance().getReportsStatus(statusUrl, deviceId, new PestApiClient.ReportsCallback() {
                    @Override
                    public void onSuccess(String jsonResult) {
                        try {
                            JSONObject json = new JSONObject(jsonResult);
                            JSONArray reports = json.optJSONArray("reports");
                            if (reports != null) {
                                boolean foundAndResolved = false;
                                String pestName = "";
                                String treatment = "";

                                for (int i = 0; i < reports.length(); i++) {
                                    JSONObject r = reports.getJSONObject(i);
                                    if (r.optInt("id", -1) == activeReportId) {
                                        String status = r.optString("status", "pending");
                                        if ("resolved".equalsIgnoreCase(status)) {
                                            foundAndResolved = true;
                                            pestName = r.optString("pest_name", "Unknown Pest");
                                            treatment = r.optString("treatment", "No advice available.");
                                        }
                                        break;
                                    }
                                }

                                if (foundAndResolved) {
                                    final String finalPest = pestName;
                                    final String finalTreatment = treatment;
                                    stopPolling();

                                    // Update UI views
                                    resultText.setText(finalPest);
                                    treatmentText.setText(finalTreatment);

                                    // Show popup notification
                                    new AlertDialog.Builder(MainActivity.this)
                                            .setTitle("Pest Identified!")
                                            .setMessage("Our agricultural experts have identified the unrecognized pest.\n\n" +
                                                    "Identified Pest: " + finalPest + "\n\n" +
                                                    "Treatment Advice:\n" + finalTreatment)
                                            .setPositiveButton("OK", null)
                                            .show();
                                } else {
                                    // Not resolved yet, run again in 30 seconds
                                    if (activeReportId != -1) {
                                        pollingHandler.postDelayed(pollingRunnable, 30000);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            // Retry in 30 seconds on parsing error
                            if (activeReportId != -1) {
                                pollingHandler.postDelayed(pollingRunnable, 30000);
                            }
                        }
                    }

                    @Override
                    public void onError(String error) {
                        // Retry in 30 seconds on network error
                        if (activeReportId != -1) {
                            pollingHandler.postDelayed(pollingRunnable, 30000);
                        }
                    }
                });
            }
        };
        pollingHandler.postDelayed(pollingRunnable, 30000);
    }

    private void stopPolling() {
        activeReportId = -1;
        if (pollingRunnable != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
            pollingRunnable = null;
        }
    }
}
