package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Keeps the current account's FCM token registered without exposing account credentials to Firebase. */
final class ZeroChillPushRegistration {
    private static final String PREFS = "zerochill_push_registration";
    private static final String KEY_INSTALLATION_ID = "installation_id";
    private static final String KEY_LAST_OWNER = "last_owner";
    private static final String KEY_LAST_APP = "last_app";
    private static final String KEY_LAST_TOKEN_HASH = "last_token_hash";
    private static final String KEY_TOKEN_RESET_PENDING = "token_reset_pending";
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();

    private ZeroChillPushRegistration() { }

    static void initialize(Context context) {
        Context app = context.getApplicationContext();
        if (sessionCredentials(ZeroChillSessionStore.read(app)) == null) return;
        syncCurrent(app, null);
    }

    static void onSessionAvailable(Context context) {
        syncCurrent(context.getApplicationContext(), null);
    }

    static void onTokenRefreshed(Context context, String token) {
        String cleanToken = clean(token);
        if (cleanToken.isEmpty()) return;
        syncCurrent(context.getApplicationContext(), cleanToken);
    }

    static void onSessionClearing(Context context, String sessionJson) {
        Context app = context.getApplicationContext();
        SessionCredentials credentials = sessionCredentials(sessionJson);
        if (credentials == null) return;

        SharedPreferences prefs = prefs(app);
        prefs.edit()
                .putBoolean(KEY_TOKEN_RESET_PENDING, true)
                .remove(KEY_LAST_OWNER)
                .remove(KEY_LAST_APP)
                .remove(KEY_LAST_TOKEN_HASH)
                .apply();

        String installationId = installationId(app);
        String applicationId = app.getPackageName();
        NETWORK.execute(() -> deleteRegistration(app, credentials, installationId, applicationId));
        resetFirebaseToken(app);
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

    private static void syncCurrent(Context app, String suppliedToken) {
        SessionCredentials expected = sessionCredentials(ZeroChillSessionStore.read(app));
        if (expected == null || !configured()) return;

        if (prefs(app).getBoolean(KEY_TOKEN_RESET_PENDING, false)) {
            resetFirebaseToken(app);
            return;
        }

        if (!clean(suppliedToken).isEmpty()) {
            queueUpsert(app, expected.userId, suppliedToken, false);
            return;
        }

        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) return;
            String token = clean(task.getResult());
            if (!token.isEmpty()) queueUpsert(app, expected.userId, token, false);
        });
    }

    private static void queueUpsert(Context app, String expectedOwner, String token, boolean retryAfterConflict) {
        String applicationId = app.getPackageName();
        String tokenHash = tokenHash(token);
        SharedPreferences prefs = prefs(app);
        if (!retryAfterConflict
                && expectedOwner.equals(clean(prefs.getString(KEY_LAST_OWNER, "")))
                && applicationId.equals(clean(prefs.getString(KEY_LAST_APP, "")))
                && tokenHash.equals(clean(prefs.getString(KEY_LAST_TOKEN_HASH, "")))) {
            return;
        }

        NETWORK.execute(() -> {
            SessionCredentials current = sessionCredentials(ZeroChillSessionStore.read(app));
            if (current == null || !expectedOwner.equals(current.userId)) return;
            int status = upsertRegistration(app, current, installationId(app), applicationId, token);
            if (status >= 200 && status < 300) {
                prefs.edit()
                        .putString(KEY_LAST_OWNER, current.userId)
                        .putString(KEY_LAST_APP, applicationId)
                        .putString(KEY_LAST_TOKEN_HASH, tokenHash)
                        .apply();
            } else if (status == 409 && !retryAfterConflict) {
                // A token left behind by an interrupted logout must never remain shared by accounts.
                prefs.edit().putBoolean(KEY_TOKEN_RESET_PENDING, true).apply();
                resetFirebaseToken(app);
            }
        });
    }

    private static int upsertRegistration(
            Context app,
            SessionCredentials credentials,
            String installationId,
            String applicationId,
            String token
    ) {
        HttpURLConnection connection = null;
        try {
            JSONObject body = new JSONObject()
                    .put("user_id", credentials.userId)
                    .put("installation_id", installationId)
                    .put("application_id", applicationId)
                    .put("fcm_token", token)
                    .put("updated_at", Instant.now().toString());
            String path = "/rest/v1/push_device_tokens?on_conflict=user_id%2Cinstallation_id%2Capplication_id";
            connection = open(app, "POST", path, credentials.accessToken);
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
            Context app,
            SessionCredentials credentials,
            String installationId,
            String applicationId
    ) {
        if (!configured()) return;
        HttpURLConnection connection = null;
        try {
            String path = "/rest/v1/push_device_tokens"
                    + "?user_id=eq." + encode(credentials.userId)
                    + "&installation_id=eq." + encode(installationId)
                    + "&application_id=eq." + encode(applicationId);
            connection = open(app, "DELETE", path, credentials.accessToken);
            connection.setRequestProperty("Prefer", "return=minimal");
            int status = connection.getResponseCode();
            closeResponse(connection, status);
        } catch (Exception ignored) {
            // The Firebase token is rotated separately, so a failed cleanup cannot leak future pushes.
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void resetFirebaseToken(Context app) {
        FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) return;
            prefs(app).edit().putBoolean(KEY_TOKEN_RESET_PENDING, false).apply();
            SessionCredentials current = sessionCredentials(ZeroChillSessionStore.read(app));
            if (current == null) return;
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(tokenTask -> {
                if (!tokenTask.isSuccessful()) return;
                String token = clean(tokenTask.getResult());
                if (!token.isEmpty()) queueUpsert(app, current.userId, token, true);
            });
        });
    }

    private static HttpURLConnection open(Context app, String method, String path, String accessToken) throws Exception {
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

    private static String tokenHash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte part : digest) result.append(String.format(Locale.US, "%02x", part & 0xff));
            return result.toString();
        } catch (Exception ignored) {
            return "";
        }
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
