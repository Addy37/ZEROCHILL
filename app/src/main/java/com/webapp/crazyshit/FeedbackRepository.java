package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class FeedbackRepository {
    private static final String PREFS = "feedback_identity";
    private static final String INSTALLATION_ID = "installation_id";
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();

    interface RequestExecutor { JSONObject execute(JSONObject payload) throws Exception; }
    static volatile RequestExecutor requestOverride; // Test seam; production always uses the configured endpoint.

    interface Callback<T> {
        void complete(T value, Exception error);
    }

    static final class FeedbackItem {
        final String id;
        final String type;
        final String message;
        final int rating;
        final String status;
        final String developerReply;
        final String createdAt;
        final String lastMessage;
        final String lastSender;
        final String lastMessageAt;
        final int unreadCount;

        FeedbackItem(JSONObject value) {
            id = value.optString("id");
            type = value.optString("type", "feedback");
            message = value.optString("message");
            rating = value.optInt("rating");
            status = value.optString("status", "submitted");
            developerReply = value.optString("developer_reply");
            createdAt = value.optString("created_at");
            String fallback = developerReply.isEmpty() ? message : developerReply;
            lastMessage = value.optString("last_message", fallback);
            lastSender = value.optString(
                    "last_sender",
                    developerReply.isEmpty() ? "user" : "developer"
            );
            lastMessageAt = value.optString("last_message_at", createdAt);
            unreadCount = Math.max(0, value.optInt("unread_count", 0));
        }
    }

    static final class FeedbackMessage {
        final String id;
        final String feedbackId;
        final String sender;
        final String message;
        final String createdAt;
        final String readAt;

        FeedbackMessage(JSONObject value) {
            id = value.optString("id");
            feedbackId = value.optString("feedback_id");
            sender = value.optString("sender", "user");
            message = value.optString("message");
            createdAt = value.optString("created_at");
            readAt = value.optString("read_at");
        }

        boolean fromDeveloper() {
            return "developer".equals(sender);
        }

        boolean isRead() {
            return readAt != null && !readAt.isEmpty() && !"null".equals(readAt);
        }
    }

    static final class FeedbackThread {
        final FeedbackItem item;
        final List<FeedbackMessage> messages;

        FeedbackThread(FeedbackItem item, List<FeedbackMessage> messages) {
            this.item = item;
            this.messages = messages;
        }
    }

    private FeedbackRepository() {
    }

    static boolean isConfigured() {
        return !BuildConfig.FEEDBACK_ENDPOINT.trim().isEmpty()
                && !BuildConfig.FEEDBACK_ANON_KEY.trim().isEmpty();
    }

    static void submit(
            Context context,
            String type,
            String message,
            int rating,
            String section,
            Callback<String> callback
    ) {
        NETWORK.execute(() -> {
            try {
                JSONObject body = basePayload(context, "submit")
                        .put("type", type)
                        .put("message", message)
                        .put("rating", rating == 0 ? JSONObject.NULL : rating)
                        .put("section", section == null ? "More" : section);
                JSONObject result = request(body);
                callback.complete(result.optString("id"), null);
            } catch (Exception error) {
                callback.complete(null, error);
            }
        });
    }

    static void list(Context context, Callback<List<FeedbackItem>> callback) {
        NETWORK.execute(() -> {
            try {
                JSONObject result = request(basePayload(context, "list"));
                JSONArray rows = result.optJSONArray("items");
                List<FeedbackItem> items = new ArrayList<>();
                if (rows != null) {
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject row = rows.optJSONObject(i);
                        if (row != null) items.add(new FeedbackItem(row));
                    }
                }
                callback.complete(items, null);
            } catch (Exception error) {
                callback.complete(null, error);
            }
        });
    }

    static void thread(
            Context context,
            String feedbackId,
            Callback<FeedbackThread> callback
    ) {
        NETWORK.execute(() -> {
            try {
                JSONObject result = request(basePayload(context, "thread")
                        .put("feedback_id", feedbackId));
                JSONObject itemObject = result.optJSONObject("item");
                if (itemObject == null) {
                    throw new IllegalStateException("Feedback thread could not load.");
                }
                JSONArray rows = result.optJSONArray("messages");
                List<FeedbackMessage> messages = new ArrayList<>();
                if (rows != null) {
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject row = rows.optJSONObject(i);
                        if (row != null) messages.add(new FeedbackMessage(row));
                    }
                }
                callback.complete(
                        new FeedbackThread(new FeedbackItem(itemObject), messages),
                        null
                );
            } catch (Exception error) {
                callback.complete(null, error);
            }
        });
    }

    static void reply(
            Context context,
            String feedbackId,
            String message,
            Callback<FeedbackMessage> callback
    ) {
        NETWORK.execute(() -> {
            try {
                JSONObject result = request(basePayload(context, "reply")
                        .put("feedback_id", feedbackId)
                        .put("message", message));
                JSONObject sent = result.optJSONObject("message");
                if (sent == null) throw new IllegalStateException("Reply could not be sent.");
                callback.complete(new FeedbackMessage(sent), null);
            } catch (Exception error) {
                callback.complete(null, error);
            }
        });
    }

    // Called only by CrashReporter's background worker. Normal user Feedback keeps its own queue.
    static CrashDelivery.Transport crashTransport(Context context) {
        return new CrashDelivery.Transport() {
            public String find(String marker) throws Exception {
                JSONArray rows = request(basePayload(context, "list")).optJSONArray("items");
                if (rows != null)
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject row = rows.optJSONObject(i);
                        if (row != null
                                && "bug_report".equals(row.optString("type"))
                                && row.optString("message").contains(marker))
                            return row.optString("id");
                    }
                return "";
            }

            public String create(CrashReport report) throws Exception {
                return request(
                                basePayload(context, "submit")
                                        .put("type", "bug_report")
                                        .put("section", "Crash")
                                        .put("rating", JSONObject.NULL)
                                        .put("message", report.firstMessage())
                                        .put("app_version", report.version)
                                        .put("android_version", report.androidVersion)
                                        .put("device", report.device))
                        .optString("id");
            }

            public java.util.Set<String> messages(String id) throws Exception {
                JSONArray rows =
                        request(basePayload(context, "thread").put("feedback_id", id))
                                .optJSONArray("messages");
                java.util.Set<String> messages = new java.util.HashSet<>();
                if (rows != null)
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject row = rows.optJSONObject(i);
                        if (row != null) messages.add(row.optString("message"));
                    }
                return messages;
            }

            public void reply(String id, String text) throws Exception {
                request(
                        basePayload(context, "reply")
                                .put("feedback_id", id)
                                .put("message", CrashSanitizer.clean(text)));
            }
        };
    }

    private static JSONObject basePayload(Context context, String action) throws Exception {
        return new JSONObject()
                .put("action", action)
                .put("installation_id", installationId(context))
                .put("app_version", BuildConfig.VERSION_NAME)
                .put("android_version", Build.VERSION.RELEASE)
                .put("device", Build.MANUFACTURER + " " + Build.MODEL);
    }

    static synchronized String installationId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String current = prefs.getString(INSTALLATION_ID, "");
        if (current != null && !current.isEmpty()) return current;
        String created = UUID.randomUUID().toString();
        prefs.edit().putString(INSTALLATION_ID, created).apply();
        return created;
    }

    private static JSONObject request(JSONObject payload) throws Exception {
        RequestExecutor override=requestOverride;
        if(override!=null)return override.execute(payload);
        if (!isConfigured()) throw new IllegalStateException("Feedback service is not configured.");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(BuildConfig.FEEDBACK_ENDPOINT).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(20_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("apikey", BuildConfig.FEEDBACK_ANON_KEY);
            connection.setRequestProperty(
                    "Authorization", "Bearer " + BuildConfig.FEEDBACK_ANON_KEY);
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String raw = read(stream);
            if (status < 200 || status >= 300) {
                String detail = raw;
                try {
                    detail = new JSONObject(raw).optString("error", raw);
                } catch (Exception ignored) {
                }
                throw new IllegalStateException(
                        detail.isEmpty() ? "Feedback request failed." : detail
                );
            }
            return raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
        } finally {
            if (connection != null) connection.disconnect();
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
}
