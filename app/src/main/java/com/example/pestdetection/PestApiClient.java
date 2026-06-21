package com.example.pestdetection;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Shared HTTP client with connection pooling for a persistent backend link.
 */
public final class PestApiClient {

    public interface HealthCallback {
        void onResult(boolean connected, String message);
    }

    public interface PredictCallback {
        void onSuccess(String pest, double confidence, String treatment);
        void onError(String message);
    }

    private static final MediaType IMAGE_MEDIA_TYPE = MediaType.parse("image/*");
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static PestApiClient instance;

    private final OkHttpClient client;

    private PestApiClient() {
        client = new OkHttpClient.Builder()
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .retryOnConnectionFailure(true)
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized PestApiClient getInstance() {
        if (instance == null) {
            instance = new PestApiClient();
        }
        return instance;
    }

    public void checkHealth(String healthUrl, HealthCallback callback) {
        Request request = new Request.Builder()
                .url(healthUrl)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                post(() -> callback.onResult(false, e.getMessage() != null ? e.getMessage() : "Connection failed"));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                boolean connected = response.isSuccessful();
                String message = connected ? "Connected" : "Server error " + response.code();
                response.close();
                post(() -> callback.onResult(connected, message));
            }
        });
    }

    public void predict(String predictUrl, byte[] imageBytes, PredictCallback callback) {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "image.jpg", RequestBody.create(imageBytes, IMAGE_MEDIA_TYPE))
                .build();

        Request request = new Request.Builder()
                .url(predictUrl)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                StringBuilder errorMessage = new StringBuilder(
                        e.getMessage() != null ? e.getMessage() : "Connection failed");
                if (e.getCause() != null && e.getCause().getMessage() != null) {
                    errorMessage.append(" (").append(e.getCause().getMessage()).append(")");
                }
                final String message = errorMessage.toString();
                post(() -> callback.onError(message));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    StringBuilder sb = new StringBuilder("Server error ").append(response.code());
                    if (!body.isEmpty()) {
                        sb.append(": ").append(body);
                    }
                    final String error = sb.toString();
                    post(() -> callback.onError(error));
                    return;
                }

                try {
                    JSONObject json = new JSONObject(body);
                    String pest = json.optString("pest_detected", "Unknown");
                    double conf = json.optDouble("confidence", 0.0);
                    String treatment = json.optString("treatment", "No advice available.");
                    post(() -> callback.onSuccess(pest, conf, treatment));
                } catch (Exception e) {
                    post(() -> callback.onError("Invalid response: " + body));
                }
            }
        });
    }

    private static void post(Runnable runnable) {
        MAIN_HANDLER.post(runnable);
    }
}
