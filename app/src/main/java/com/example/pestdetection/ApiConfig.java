package com.example.pestdetection;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Single source of truth for the backend URL.
 * Priority: saved preference → ApiConstants.DEFAULT_API_BASE_URL (baked into the app).
 */
public final class ApiConfig {

    private static final String PREFS_NAME = "PestDetectorPrefs";
    private static final String KEY_SERVER_URL = "server_url";

    private ApiConfig() {
    }

    public static String getPredictUrl(Context context) {
        String saved = getPrefs(context).getString(KEY_SERVER_URL, null);
        if (saved != null && !saved.trim().isEmpty()) {
            return normalizePredictUrl(saved.trim());
        }
        return normalizePredictUrl(ApiConstants.DEFAULT_API_BASE_URL);
    }

    public static String getHealthUrl(Context context) {
        return toHealthUrl(getPredictUrl(context));
    }

    public static void savePredictUrl(Context context, String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        getPrefs(context).edit().putString(KEY_SERVER_URL, normalizePredictUrl(url.trim())).apply();
    }

    public static String getDefaultPredictUrl() {
        return normalizePredictUrl(ApiConstants.DEFAULT_API_BASE_URL);
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String normalizePredictUrl(String url) {
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/predict")) {
            return trimmed;
        }
        return trimmed + "/predict";
    }

    private static String toHealthUrl(String predictUrl) {
        if (predictUrl.endsWith("/predict")) {
            return predictUrl.substring(0, predictUrl.length() - "/predict".length()) + "/health";
        }
        return predictUrl + "/health";
    }
}
