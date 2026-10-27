package com.addy37.crazyshitadmin;

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

/** Read-only ZEROCHILL account and social administration data. */
final class CommunityRepository {
    static final class Overview {
        final long accounts, newAccounts7d, comments, videoLikes, commentLikes, messages, reports, blocks;

        Overview(JSONObject value) {
            accounts = value.optLong("accounts");
            newAccounts7d = value.optLong("new_accounts_7d");
            comments = value.optLong("comments");
            videoLikes = value.optLong("video_likes");
            commentLikes = value.optLong("comment_likes");
            messages = value.optLong("messages");
            reports = value.optLong("reports");
            blocks = value.optLong("blocks");
        }

        long totalLikes() { return videoLikes + commentLikes; }
    }

    static final class Account {
        final String id, email, username, displayName, bio, avatarPath, status;
        final String createdAt, lastSignInAt, emailConfirmedAt, bannedUntil, deletedAt;

        Account(JSONObject value) {
            id = value.optString("id");
            email = value.optString("email");
            username = value.optString("username");
            displayName = value.optString("display_name");
            bio = value.optString("bio");
            avatarPath = value.optString("avatar_path");
            status = value.optString("status", "active");
            createdAt = value.optString("created_at");
            lastSignInAt = nullable(value, "last_sign_in_at");
            emailConfirmedAt = nullable(value, "email_confirmed_at");
            bannedUntil = nullable(value, "banned_until");
            deletedAt = nullable(value, "deleted_at");
        }

        String name() {
            if (!displayName.trim().isEmpty()) return displayName.trim();
            if (!username.trim().isEmpty()) return "@" + username.trim();
            if (!email.trim().isEmpty()) return email.trim();
            return id;
        }

        String handle() {
            return username.trim().isEmpty() ? "" : "@" + username.trim();
        }
    }

    static final class SocialActivity {
        final String type, id, userId, username, displayName, text, secondary, createdAt, state;

        SocialActivity(JSONObject value) {
            type = value.optString("type");
            id = value.optString("id");
            userId = value.optString("user_id");
            username = value.optString("username");
            displayName = value.optString("display_name");
            text = value.optString("text");
            secondary = value.optString("secondary");
            createdAt = value.optString("created_at");
            state = value.optString("state");
        }

        String actor() {
            if (!displayName.trim().isEmpty()) return displayName.trim();
            if (!username.trim().isEmpty()) return "@" + username.trim();
            return userId;
        }
    }

    static final class Report {
        final String id, reporterId, reporterName, reportedUserId, reportedName;
        final String reason, details, directMessageId, createdAt;

        Report(JSONObject value) {
            id = value.optString("id");
            reporterId = value.optString("reporter_id");
            reporterName = value.optString("reporter_name");
            reportedUserId = value.optString("reported_user_id");
            reportedName = value.optString("reported_name");
            reason = value.optString("reason");
            details = value.optString("details");
            directMessageId = nullable(value, "direct_message_id");
            createdAt = value.optString("created_at");
        }
    }

    static final class AccountDetail {
        final Account account;
        final long comments, videoLikes, commentLikes, messagesSent, messagesReceived;
        final long reportsFiled, reportsReceived, blocksMade, blocksReceived;
        final List<SocialActivity> recentComments, recentLikes, recentMessages;
        final List<Report> recentReports;

        AccountDetail(JSONObject value) {
            account = new Account(value.optJSONObject("account") == null
                    ? new JSONObject() : value.optJSONObject("account"));
            JSONObject metrics = value.optJSONObject("metrics");
            if (metrics == null) metrics = new JSONObject();
            comments = metrics.optLong("comments");
            videoLikes = metrics.optLong("video_likes");
            commentLikes = metrics.optLong("comment_likes");
            messagesSent = metrics.optLong("messages_sent");
            messagesReceived = metrics.optLong("messages_received");
            reportsFiled = metrics.optLong("reports_filed");
            reportsReceived = metrics.optLong("reports_received");
            blocksMade = metrics.optLong("blocks_made");
            blocksReceived = metrics.optLong("blocks_received");
            recentComments = activities(value.optJSONArray("recent_comments"));
            recentLikes = activities(value.optJSONArray("recent_likes"));
            recentMessages = activities(value.optJSONArray("recent_messages"));
            recentReports = reportRows(value.optJSONArray("recent_reports"));
        }
    }

    private CommunityRepository() {}

    static Overview overview(String token) throws Exception {
        JSONObject result = request(token, new JSONObject().put("action", "community_overview"));
        JSONObject value = result.optJSONObject("overview");
        return new Overview(value == null ? new JSONObject() : value);
    }

    static List<Account> accounts(String token, String query, String filter) throws Exception {
        JSONObject result = request(token, new JSONObject()
                .put("action", "accounts")
                .put("query", query == null ? "" : query.trim())
                .put("filter", filter == null ? "all" : filter)
                .put("limit", 100));
        JSONArray rows = result.optJSONArray("items");
        List<Account> items = new ArrayList<>();
        if (rows != null) {
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row != null) items.add(new Account(row));
            }
        }
        return items;
    }

    static AccountDetail accountDetail(String token, String userId) throws Exception {
        JSONObject result = request(token, new JSONObject()
                .put("action", "account_detail")
                .put("user_id", userId));
        JSONObject detail = result.optJSONObject("detail");
        if (detail == null) throw new IllegalStateException("Account details could not load.");
        return new AccountDetail(detail);
    }

    static List<SocialActivity> activity(String token, String query, String filter) throws Exception {
        JSONObject result = request(token, new JSONObject()
                .put("action", "social_activity")
                .put("query", query == null ? "" : query.trim())
                .put("filter", filter == null ? "all" : filter)
                .put("limit", 100));
        return activities(result.optJSONArray("items"));
    }

    static List<Report> reports(String token, String query, String filter) throws Exception {
        JSONObject result = request(token, new JSONObject()
                .put("action", "reports")
                .put("query", query == null ? "" : query.trim())
                .put("filter", filter == null ? "all" : filter)
                .put("limit", 100));
        return reportRows(result.optJSONArray("items"));
    }

    private static List<SocialActivity> activities(JSONArray rows) {
        List<SocialActivity> items = new ArrayList<>();
        if (rows == null) return items;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row != null) items.add(new SocialActivity(row));
        }
        return items;
    }

    private static List<Report> reportRows(JSONArray rows) {
        List<Report> items = new ArrayList<>();
        if (rows == null) return items;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row != null) items.add(new Report(row));
        }
        return items;
    }

    private static JSONObject request(String token, JSONObject body) throws Exception {
        if (token == null || token.trim().isEmpty()) throw new SecurityException("Admin token required.");
        String endpoint = BuildConfig.ADMIN_FEEDBACK_ENDPOINT;
        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new IllegalStateException("The admin endpoint is not configured.");
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(20_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("x-admin-token", token.trim());
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String raw = read(stream);
            if (status < 200 || status >= 300) {
                String detail = raw;
                try { detail = new JSONObject(raw).optString("error", raw); } catch (Exception ignored) {}
                if (status == 401) throw new SecurityException("The admin token is not valid.");
                throw new IllegalStateException(detail.isEmpty() ? "Request failed." : detail);
            }
            return raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String read(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder value = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) value.append(line);
        }
        return value.toString();
    }

    private static String nullable(JSONObject value, String key) {
        return value == null || value.isNull(key) ? "" : value.optString(key);
    }
}
