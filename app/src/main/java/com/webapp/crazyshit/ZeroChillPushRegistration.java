package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.google.firebase.installations.FirebaseInstallations;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Keeps the current account's Firebase installation registered without exposing account credentials to Firebase. */
final class ZeroChillPushRegistration {
    private static final String PREFS = "zerochill_push_registration";
    private static final String KEY_INSTALLATION_ID = "installation_id";
    private static final String KEY_REGISTRATION_SUSPENDED = "registration_suspended";
    private static final String KEY_ROTATION_IN_PROGRESS = "rotation_in_progress";
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();

    private ZeroChillPushRegistration() { }

    static void initialize(Context context) {
        Context app = context.getApplicationContext();
        if (sessionCredentials(ZeroChillSessionStore.read(app)) == null || !configured()) return;
        prefs(app).edit().putBoolean(KEY_REGISTRATION_SUSPENDED, false).apply();
        registerWithFirebase(app);
    }

    static void onSessionAvailable(Context context) {
        Context app = context.getApplicationContext();
        if (!configured()) return;
        prefs(app).edit().putBoolean(KEY_REGISTRATION_SUSPENDED, false).apply();
        registerWithFirebase(app);
    }

    static void onRegistered(Context context, String firebaseInstallationId) {
        Context app = context.getApplicationContext();
        String fid = clean(firebaseInstallationId);
        if (!validFirebaseInstallationId(fid) || !configured()) return;
        if (prefs(app).getBoolean(KEY_REGISTRATION_SUSPENDED, false)) return;

        SessionCredentials expected = sessionCredentials(ZeroChillSessionStore.read(app));
        if (expected == null) return;
        queueUpsert(app, expected.userId, fid, true);
    }

    static void onUnregistered(Context context, String firebaseInstallationId) {
        Context app = context.getApplicationContext();
        if (!prefs(app).getBoolean(KEY_REGISTRATION_SUSPENDED, false)
                && sessionCredentials(ZeroChillSessionStore.read(app)) != null) {
            registerWithFirebase(app);
        }
    }

    static void onSessionClearing(Context context, String sessionJson) {
        Context app = context.getApplicationContext();
        SessionCredentials credentials = sessionCredentials(sessionJson);
        if (credentials == null) return;

        prefs(app).edit()
                .putBoolean(KEY_REGISTRATION_SUSPENDED, true)
                .putBoolean(KEY_ROTATION_IN_PROGRESS, true)
                .apply();

        String installationId = installationId(app);
        String applicationId = app.getPackageName();
        NETWORK.execute(() -> deleteRegistration(credentials, installationId, applicationId));
        invalidateFirebaseInstallation(app, false);
    }

    static String installationId(Context context) {
        SharedPreferences prefs = prefs(context);
        String existing = clean(prefs.getString(KEY_INSTALLATION_ID, ""));
        if (isUuid(existing)) return existing;
        String created = UUID.randomUUID().toString();
        prefs.edit().putString(KEY_INSTALLATION_ID, created).apply();
        return created;
    }

    static SessionCredentials sessionCredentials(String sessionJson) {
        try {
            JSONObject session = new JSONObject(clean(sessionJson));
            String accessToken = clean(session.optString("access_token", ""));
            if (accessToken.isEmpty()) return null;
            String[] parts = accessToken.split("\\.");
            if (parts.length < 2) return null;
            byte[] decoded = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            String userId = clean(new JSONObject(new String(decoded, StandardCharsets.UTF_8)).optString("sub", ""));
            if (!isUuid(userId)) return null;
            return new SessionCredentials(userId, accessToken);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void registerWithFirebase(Context app) {
        FirebaseMessaging.getInstance().register().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) return;
            // onRegistered() supplies the current FID and performs the authenticated upload.
        });
    }

    private static void queueUpsert(
            Context app,
            String expectedOwner,
            String firebaseInstallationId,
            boolean allowConflictRotation
    ) {
        NETWORK.execute(() -> {
            SessionCredentials current = sessionCredentials(ZeroChillSessionStore.read(app));
            if (current == null || !expectedOwner.equals(current.userId)) return;
            if (prefs(app).getBoolean(KEY_REGISTRATION_SUSPENDED, false)) return;

            int status = upsertRegistration(
                    current,
                    installationId(app),
                    app.getPackageName(),
                    firebaseInstallationId
            );
            if (status == 409 && allowConflictRotation) {
                // If a prior account's best-effort server cleanup failed, create a new FID so
                // the shared device can never inherit that account's direct-send registration.
                invalidateFirebaseInstallation(app, true);
            }
        });
    }

    private static int upsertRegistration(
            SessionCredentials credentials,
            String installationId,
            String applicationId,
            String firebaseInstallationId
    ) {
        HttpURLConnection connection = null;
        try {
            JSONObject body = new JSONObject()
                    .put("user_id", credentials.userId)
                    .put("installation_id", installationId)
                    .put("application_id", applicationId)
                    .put("firebase_installation_id", firebaseInstallationId)
                    .put("updated_at", Instant.now().toString());
            String path = "/rest/v1/push_device_tokens?on_conflict=user_id%2Cinstallation_id%2Capplication_id";
            connection = open("POST", path, credentials.accessToken);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal");
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
            int status = connection.getResponseCode();
            closeResponse(connection, status);
            return status;
        } catch (Exception ignored) {
            return -1;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void deleteRegistration(
            SessionCredentials credentials,
            String installationId,
            String applicationId
    ) {
        if (!configured()) return;
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpURLConnection connection = null;
            try {
                String path = "/rest/v1/push_device_tokens"
                        + "?user_id=eq." + encode(credentials.userId)
                        + "&installation_id=eq." + encode(installationId)
                        + "&application_id=eq." + encode(applicationId);
                connection = open("DELETE", path, credentials.accessToken);
                connection.setRequestProperty("Prefer", "return=minimal");
                int status = connection.getResponseCode();
                closeResponse(connection, status);
                if (status >= 200 && status < 300) return;
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
            try {
                Thread.sleep(250L * (attempt + 1));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void invalidateFirebaseInstallation(Context app, boolean registerAgain) {
        SharedPreferences prefs = prefs(app);
        if (registerAgain && prefs.getBoolean(KEY_ROTATION_IN_PROGRESS, false)) return;
        prefs.edit().putBoolean(KEY_ROTATION_IN_PROGRESS, true).apply();

        FirebaseMessaging.getInstance().unregister().addOnCompleteListener(unregisterTask ->
                FirebaseInstallations.getInstance().delete().addOnCompleteListener(deleteTask -> {
                    prefs.edit().putBoolean(KEY_ROTATION_IN_PROGRESS, false).apply();
                    if (!registerAgain) return;
                    if (prefs.getBoolean(KEY_REGISTRATION_SUSPENDED, false)) return;
                    if (sessionCredentials(ZeroChillSessionStore.read(app)) == null) return;
                    registerWithFirebase(app);
                })
        );
    }

    private static HttpURLConnection open(String method, String path, String accessToken) throws Exception {
        String base = clean(BuildConfig.ACCOUNT_SUPABASE_URL);
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(7_000);
        connection.setReadTimeout(10_000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Cache-Control", "no-store");
        connection.setRequestProperty("apikey", BuildConfig.ACCOUNT_SUPABASE_PUBLISHABLE_KEY);
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        return connection;
    }

    private static void closeResponse(HttpURLConnection connection, int status) {
        try {
            InputStream input = status >= 200 && status < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            if (input != null) input.close();
        } catch (Exception ignored) {
        }
    }

    private static boolean configured() {
        return !clean(BuildConfig.ACCOUNT_SUPABASE_URL).isEmpty()
                && !clean(BuildConfig.ACCOUNT_SUPABASE_PUBLISHABLE_KEY).isEmpty();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean validFirebaseInstallationId(String value) {
        String clean = clean(value);
        return clean.length() >= 20 && clean.length() <= 256
                && clean.matches("[A-Za-z0-9_\\-:.]+$" );
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean isUuid(String value) {
        try {
            return value != null && UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    static final class SessionCredentials {
        final String userId;
        final String accessToken;

        SessionCredentials(String userId, String accessToken) {
            this.userId = userId;
            this.accessToken = accessToken;
        }
    }
}
