package com.webapp.crazyshit;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small REST client for first-party ZeroChill Supabase Auth and profiles. */
final class ZeroChillAccountRepository {
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private static final long REFRESH_EARLY_SECONDS = 60L;
    static final String AUTH_REDIRECT_URI = "com.addy37.zerochill://auth/confirmed";

    interface Callback<T> {
        void complete(T value, Exception error);
    }

    static final class AccountState {
        final boolean signedIn;
        final boolean pendingVerification;
        final String userId;
        final String email;
        final String username;
        final String displayName;
        final String avatarPath;
        final String createdAt;

        AccountState(
                boolean signedIn,
                boolean pendingVerification,
                String userId,
                String email,
                String username,
                String displayName,
                String avatarPath,
                String createdAt
        ) {
            this.signedIn = signedIn;
            this.pendingVerification = pendingVerification;
            this.userId = clean(userId);
            this.email = clean(email);
            this.username = clean(username);
            this.displayName = clean(displayName);
            this.avatarPath = clean(avatarPath);
            this.createdAt = clean(createdAt);
        }

        static AccountState signedOut() {
            return new AccountState(false, false, "", "", "", "", "", "");
        }

        static AccountState pending(String email) {
            return new AccountState(false, true, "", email, "", "", "", "");
        }
    }

    private static final class Session {
        final String accessToken;
        final String refreshToken;
        final long expiresAtSeconds;

        Session(String accessToken, String refreshToken, long expiresAtSeconds) {
            this.accessToken = clean(accessToken);
            this.refreshToken = clean(refreshToken);
            this.expiresAtSeconds = expiresAtSeconds;
        }

        boolean valid() {
            return !accessToken.isEmpty() && !refreshToken.isEmpty();
        }
    }

    private static final class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body == null ? "" : body;
        }

        boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    private ZeroChillAccountRepository() {}

    static boolean isConfigured() {
        return !clean(BuildConfig.ACCOUNT_SUPABASE_URL).isEmpty()
                && !clean(BuildConfig.ACCOUNT_SUPABASE_PUBLISHABLE_KEY).isEmpty();
    }

    static boolean hasStoredSession(Context context) {
        return !ZeroChillSessionStore.read(context).isEmpty();
    }

    static void current(Context context, Callback<AccountState> callback) {
        NETWORK.execute(() -> {
            try {
                Session session = freshSession(context);
                if (session == null || !session.valid()) {
                    callback.complete(AccountState.signedOut(), null);
                    return;
                }
                AccountState state = loadAccount(session);
                syncCreatorFavoritesBlocking(context, session);
                callback.complete(state, null);
            } catch (Exception error) {
                ZeroChillSessionStore.clear(context);
                callback.complete(AccountState.signedOut(), error);
            }
        });
    }

    static void completeAuthRedirect(
            Context context,
            android.net.Uri uri,
            Callback<AccountState> callback
    ) {
        NETWORK.execute(() -> {
            try {
                if (uri == null
                        || !"com.addy37.zerochill".equalsIgnoreCase(clean(uri.getScheme()))
                        || !"auth".equalsIgnoreCase(clean(uri.getHost()))
                        || !"/confirmed".equals(clean(uri.getPath()))) {
                    throw new IllegalArgumentException("Invalid account confirmation link.");
                }

                java.util.Map<String, String> values = redirectValues(uri);
                String redirectError = clean(values.get("error_description"));
                if (redirectError.isEmpty()) redirectError = clean(values.get("error"));
                if (!redirectError.isEmpty()) {
                    throw new IllegalStateException(redirectError.replace('+', ' '));
                }

                String accessToken = clean(values.get("access_token"));
                String refreshToken = clean(values.get("refresh_token"));
                if (accessToken.isEmpty() || refreshToken.isEmpty()) {
                    callback.complete(AccountState.signedOut(), null);
                    return;
                }

                long expiresIn = 3600L;
                try {
                    expiresIn = Math.max(60L, Long.parseLong(clean(values.get("expires_in"))));
                } catch (Exception ignored) {
                }
                JSONObject token = new JSONObject()
                        .put("access_token", accessToken)
                        .put("refresh_token", refreshToken)
                        .put("expires_in", expiresIn);
                Session session = saveSession(context, token);
                AccountState state = loadAccount(session);
                syncCreatorFavoritesBlocking(context, session);
                callback.complete(state, null);
            } catch (Exception error) {
                ZeroChillSessionStore.clear(context);
                callback.complete(null, error);
            }
        });
    }

    static void signUp(
            Context context,
            String email,
            String username,
            String password,
            boolean adultConfirmed,
            boolean termsAccepted,
            Callback<AccountState> callback
    ) {
        NETWORK.execute(() -> {
            try {
                requireConfigured();
                String emailError = ZeroChillAccountValidation.email(email);
                String usernameError = ZeroChillAccountValidation.username(username);
                String passwordError = ZeroChillAccountValidation.password(password);
                if (!emailError.isEmpty()) throw new IllegalArgumentException(emailError);
                if (!usernameError.isEmpty()) throw new IllegalArgumentException(usernameError);
                if (!passwordError.isEmpty()) throw new IllegalArgumentException(passwordError);
                if (!adultConfirmed) throw new IllegalArgumentException("Confirm that you are 18 or older.");
                if (!termsAccepted) throw new IllegalArgumentException("Accept the Terms and Community Rules.");

                String normalized = ZeroChillAccountValidation.normalizedUsername(username);
                if (usernameExists(normalized)) {
                    throw new IllegalArgumentException("That username is already taken.");
                }

                JSONObject metadata = new JSONObject()
                        .put("username", username.trim())
                        .put("adult_confirmed", true)
                        .put("terms_accepted", true);
                JSONObject body = new JSONObject()
                        .put("email", email.trim())
                        .put("password", password)
                        .put("data", metadata);

                Response response = request(
                        "POST",
                        "/auth/v1/signup?redirect_to=" + encode(AUTH_REDIRECT_URI),
                        "",
                        body.toString().getBytes(StandardCharsets.UTF_8),
                        "application/json",
                        null
                );
                if (!response.ok()) throw responseError(response, "Unable to create the account.");
                JSONObject result = jsonObject(response.body);
                if (result.has("access_token")) {
                    Session session = saveSession(context, result);
                    AccountState state = loadAccount(session);
                    syncCreatorFavoritesBlocking(context, session);
                    callback.complete(state, null);
                } else {
                    callback.complete(AccountState.pending(email.trim()), null);
                }
            } catch (Exception error) {
                callback.complete(null, error);
            }
        });
    }

    static void signIn(
            Context context,
            String email,
            String password,
            Callback<AccountState> callback
    ) {
        NETWORK.execute(() -> {
            try {
                requireConfigured();
                String emailError = ZeroChillAccountValidation.email(email);
                String passwordError = ZeroChillAccountValidation.password(password);
                if (!emailError.isEmpty()) throw new IllegalArgumentException(emailError);
                if (!passwordError.isEmpty()) throw new IllegalArgumentException(passwordError);

                JSONObject body = new JSONObject()
                        .put("email", email.trim())
                        .put("password", password);
                Response response = request(
                        "POST",
                        "/auth/v1/token?grant_type=password",
                        "",
                        body.toString().getBytes(StandardCharsets.UTF_8),
                        "application/json",
                        null
                );
                if (!response.ok()) throw responseError(response, "Unable to sign in.");
                Session session = saveSession(context, jsonObject(response.body));
                AccountState state = loadAccount(session);
                syncCreatorFavoritesBlocking(context, session);
                callback.complete(state, null);
            } catch (Exception error) {
                callback.complete(null, error);
            }
        });
    }

    static void signOut(Context context, Callback<Boolean> callback) {
        NETWORK.execute(() -> {
            Exception failure = null;
            try {
                Session session = readSession(context);
                if (session != null && !session.accessToken.isEmpty()) {
                    request("POST", "/auth/v1/logout", session.accessToken, new byte[0],
                            "application/json", null);
                }
            } catch (Exception error) {
                failure = error;
            } finally {
                ZeroChillSessionStore.clear(context);
            }
            callback.complete(true, failure);
        });
    }

    static void updateDisplayName(
            Context context,
            String displayName,
            Callback<AccountState> callback
    ) {
        NETWORK.execute(() -> {
            try {
                Session session = requireSession(context);
                String userId = jwtSubject(session.accessToken);
                JSONObject body = new JSONObject()
                        .put("display_name", clean(displayName).substring(
                                0, Math.min(40, clean(displayName).length())
                        ))
                        .put("updated_at", java.time.Instant.now().toString());
                Response response = request(
                        "PATCH",
                        "/rest/v1/profiles?user_id=eq." + encode(userId),
                        session.accessToken,
                        body.toString().getBytes(StandardCharsets.UTF_8),
                        "application/json",
                        "return=minimal"
                );
                if (!response.ok()) throw responseError(response, "Unable to update the profile.");
                callback.complete(loadAccount(session), null);
            } catch (Exception error) {
                callback.complete(null, error);
            }
        });
    }

    static void uploadAvatar(Context context, byte[] jpeg, Callback<String> callback) {
        NETWORK.execute(() -> {
            try {
                if (jpeg == null || jpeg.length == 0) {
                    throw new IllegalArgumentException("Choose an image first.");
                }
                if (jpeg.length > 512 * 1024) {
                    throw new IllegalArgumentException("Avatar image is too large.");
                }
                Session session = requireSession(context);
                String userId = jwtSubject(session.accessToken);
                String path = userId + "/avatar.jpg";
                Response upload = request(
                        "POST",
                        "/storage/v1/object/avatars/" + path,
                        session.accessToken,
                        jpeg,
                        "image/jpeg",
                        null,
                        "x-upsert",
                        "true"
                );
                if (!upload.ok()) throw responseError(upload, "Unable to upload the avatar.");

                JSONObject update = new JSONObject()
                        .put("avatar_path", path)
                        .put("updated_at", java.time.Instant.now().toString());
                Response profile = request(
                        "PATCH",
                        "/rest/v1/profiles?user_id=eq." + encode(userId),
                        session.accessToken,
                        update.toString().getBytes(StandardCharsets.UTF_8),
                        "application/json",
                        "return=minimal"
                );
                if (!profile.ok()) throw responseError(profile, "Avatar uploaded, but the profile did not update.");
                callback.complete(avatarUrl(path), null);
            } catch (Exception error) {
                callback.complete(null, error);
            }
        });
    }

    static void setCreatorFavorite(
            Context context,
            String creatorKey,
            String creatorName,
            boolean favorite
    ) {
        if (!hasStoredSession(context) || clean(creatorKey).isEmpty()) return;
        NETWORK.execute(() -> {
            try {
                Session session = requireSession(context);
                String userId = jwtSubject(session.accessToken);
                if (favorite) {
                    JSONObject row = new JSONObject()
                            .put("creator_key", creatorKey)
                            .put("creator_name", clean(creatorName).isEmpty() ? creatorKey : creatorName);
                    request(
                            "POST",
                            "/rest/v1/creator_favorites?on_conflict=user_id,creator_key",
                            session.accessToken,
                            row.toString().getBytes(StandardCharsets.UTF_8),
                            "application/json",
                            "resolution=merge-duplicates,return=minimal"
                    );
                } else {
                    request(
                            "DELETE",
                            "/rest/v1/creator_favorites?user_id=eq." + encode(userId)
                                    + "&creator_key=eq." + encode(creatorKey),
                            session.accessToken,
                            new byte[0],
                            "application/json",
                            "return=minimal"
                    );
                }
            } catch (Exception ignored) {
            }
        });
    }

    static String avatarUrl(String path) {
        String value = clean(path);
        if (value.isEmpty()) return "";
        return clean(BuildConfig.ACCOUNT_SUPABASE_URL)
                + "/storage/v1/object/public/avatars/" + value;
    }

    static String accessTokenBlocking(Context context) throws Exception {
        return requireSession(context).accessToken;
    }

    static String currentUserIdBlocking(Context context) throws Exception {
        return jwtSubject(requireSession(context).accessToken);
    }

    private static AccountState loadAccount(Session session) throws Exception {
        Response userResponse = request(
                "GET",
                "/auth/v1/user",
                session.accessToken,
                null,
                null,
                null
        );
        if (!userResponse.ok()) throw responseError(userResponse, "Unable to load the account.");
        JSONObject user = jsonObject(userResponse.body);
        String userId = clean(user.optString("id"));
        String email = clean(user.optString("email"));

        Response profileResponse = request(
                "GET",
                "/rest/v1/profiles?select=user_id,username,display_name,avatar_path,created_at"
                        + "&user_id=eq." + encode(userId) + "&limit=1",
                session.accessToken,
                null,
                null,
                null
        );
        if (!profileResponse.ok()) {
            throw responseError(profileResponse, "Unable to load the profile.");
        }
        JSONArray rows = jsonArray(profileResponse.body);
        JSONObject profile = rows.length() == 0 ? new JSONObject() : rows.getJSONObject(0);
        return new AccountState(
                true,
                false,
                userId,
                email,
                profile.optString("username"),
                profile.optString("display_name"),
                profile.optString("avatar_path"),
                profile.optString("created_at")
        );
    }

    private static boolean usernameExists(String usernameKey) throws Exception {
        Response response = request(
                "GET",
                "/rest/v1/profiles?select=user_id&username_key=eq." + encode(usernameKey) + "&limit=1",
                "",
                null,
                null,
                null
        );
        if (!response.ok()) throw responseError(response, "Unable to check the username.");
        return jsonArray(response.body).length() > 0;
    }

    private static void syncCreatorFavoritesBlocking(Context context, Session session) {
        try {
            String userId = jwtSubject(session.accessToken);
            Set<String> merged = new HashSet<>(CreatorFavoriteStore.names(context));
            Response response = request(
                    "GET",
                    "/rest/v1/creator_favorites?select=creator_key,creator_name&user_id=eq."
                            + encode(userId),
                    session.accessToken,
                    null,
                    null,
                    null
            );
            if (response.ok()) {
                JSONArray rows = jsonArray(response.body);
                for (int i = 0; i < rows.length(); i++) {
                    String key = clean(rows.getJSONObject(i).optString("creator_key"));
                    if (!key.isEmpty()) merged.add(key);
                }
            }

            if (!merged.isEmpty()) {
                JSONArray rows = new JSONArray();
                for (String key : merged) {
                    rows.put(new JSONObject()
                            .put("creator_key", key)
                            .put("creator_name", key));
                }
                request(
                        "POST",
                        "/rest/v1/creator_favorites?on_conflict=user_id,creator_key",
                        session.accessToken,
                        rows.toString().getBytes(StandardCharsets.UTF_8),
                        "application/json",
                        "resolution=merge-duplicates,return=minimal"
                );
                CreatorFavoriteStore.mergeNames(context, merged);
            }
        } catch (Exception ignored) {
        }
    }

    private static Session requireSession(Context context) throws Exception {
        Session session = freshSession(context);
        if (session == null || !session.valid()) {
            throw new IllegalStateException("Sign in to use this feature.");
        }
        return session;
    }

    private static Session freshSession(Context context) throws Exception {
        Session current = readSession(context);
        if (current == null || !current.valid()) return null;
        long now = System.currentTimeMillis() / 1000L;
        if (current.expiresAtSeconds > now + REFRESH_EARLY_SECONDS) return current;

        JSONObject body = new JSONObject().put("refresh_token", current.refreshToken);
        Response response = request(
                "POST",
                "/auth/v1/token?grant_type=refresh_token",
                "",
                body.toString().getBytes(StandardCharsets.UTF_8),
                "application/json",
                null
        );
        if (!response.ok()) {
            ZeroChillSessionStore.clear(context);
            return null;
        }
        return saveSession(context, jsonObject(response.body));
    }

    private static Session readSession(Context context) {
        String raw = ZeroChillSessionStore.read(context);
        if (raw.isEmpty()) return null;
        try {
            JSONObject value = new JSONObject(raw);
            return new Session(
                    value.optString("access_token"),
                    value.optString("refresh_token"),
                    value.optLong("expires_at", 0L)
            );
        } catch (Exception error) {
            ZeroChillSessionStore.clear(context);
            return null;
        }
    }

    private static Session saveSession(Context context, JSONObject value) throws Exception {
        long now = System.currentTimeMillis() / 1000L;
        long expiresAt = value.optLong("expires_at", 0L);
        if (expiresAt <= now) expiresAt = now + Math.max(60L, value.optLong("expires_in", 3600L));
        Session session = new Session(
                value.optString("access_token"),
                value.optString("refresh_token"),
                expiresAt
        );
        if (!session.valid()) throw new IllegalStateException("The sign-in session was incomplete.");
        JSONObject stored = new JSONObject()
                .put("access_token", session.accessToken)
                .put("refresh_token", session.refreshToken)
                .put("expires_at", session.expiresAtSeconds);
        ZeroChillSessionStore.save(context, stored.toString());
        return session;
    }

    private static Response request(
            String method,
            String path,
            String accessToken,
            byte[] body,
            String contentType,
            String prefer,
            String... extraHeaders
    ) throws Exception {
        requireConfigured();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(
                    clean(BuildConfig.ACCOUNT_SUPABASE_URL) + path
            ).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(20_000);
            connection.setRequestProperty("apikey", BuildConfig.ACCOUNT_SUPABASE_PUBLISHABLE_KEY);
            if (!clean(accessToken).isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            }
            if (contentType != null) connection.setRequestProperty("Content-Type", contentType);
            if (prefer != null) connection.setRequestProperty("Prefer", prefer);
            for (int i = 0; i + 1 < extraHeaders.length; i += 2) {
                connection.setRequestProperty(extraHeaders[i], extraHeaders[i + 1]);
            }
            if (body != null && body.length > 0) {
                connection.setDoOutput(true);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body);
                }
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            return new Response(status, read(stream));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static Response request(
            String method,
            String path,
            String accessToken,
            byte[] body,
            String contentType,
            String prefer
    ) throws Exception {
        return request(method, path, accessToken, body, contentType, prefer, new String[0]);
    }

    private static Exception responseError(Response response, String fallback) {
        String message = fallback;
        try {
            JSONObject object = jsonObject(response.body);
            String candidate = clean(object.optString("msg"));
            if (candidate.isEmpty()) candidate = clean(object.optString("message"));
            if (candidate.isEmpty()) candidate = clean(object.optString("error_description"));
            if (candidate.isEmpty()) candidate = clean(object.optString("error"));
            if (!candidate.isEmpty()) message = candidate;
        } catch (Exception ignored) {
        }
        if (message.toLowerCase().contains("invalid login credentials")) {
            message = "Email or password is incorrect.";
        } else if (message.toLowerCase().contains("email not confirmed")) {
            message = "Verify your email before signing in.";
        } else if (message.toLowerCase().contains("user already registered")) {
            message = "An account already exists for that email.";
        }
        return new IllegalStateException(message);
    }

    private static java.util.Map<String, String> redirectValues(android.net.Uri uri) {
        java.util.Map<String, String> values = new java.util.HashMap<>();
        String query = uri.getEncodedQuery();
        String fragment = uri.getEncodedFragment();
        addEncodedPairs(values, query);
        addEncodedPairs(values, fragment);
        return values;
    }

    private static void addEncodedPairs(java.util.Map<String, String> values, String encoded) {
        if (encoded == null || encoded.isEmpty()) return;
        for (String pair : encoded.split("&")) {
            if (pair.isEmpty()) continue;
            int separator = pair.indexOf('=');
            String key = separator >= 0 ? pair.substring(0, separator) : pair;
            String value = separator >= 0 ? pair.substring(separator + 1) : "";
            try {
                key = java.net.URLDecoder.decode(key, StandardCharsets.UTF_8.name());
                value = java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.name());
            } catch (Exception ignored) {
            }
            if (!key.isEmpty()) values.put(key, value);
        }
    }

    private static JSONObject jsonObject(String raw) throws Exception {
        return clean(raw).isEmpty() ? new JSONObject() : new JSONObject(raw);
    }

    private static JSONArray jsonArray(String raw) throws Exception {
        return clean(raw).isEmpty() ? new JSONArray() : new JSONArray(raw);
    }

    private static String jwtSubject(String token) throws Exception {
        String[] parts = clean(token).split("\\.");
        if (parts.length < 2) throw new IllegalStateException("The account session is invalid.");
        byte[] decoded = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        String subject = new JSONObject(new String(decoded, StandardCharsets.UTF_8)).optString("sub");
        if (clean(subject).isEmpty()) throw new IllegalStateException("The account session is invalid.");
        return subject;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return value == null ? "" : value;
        }
    }

    private static String read(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
        }
        return text.toString();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static void requireConfigured() {
        if (!isConfigured()) throw new IllegalStateException("ZeroChill accounts are not configured in this build.");
    }
}
