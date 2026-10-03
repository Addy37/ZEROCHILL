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

final class AdminRepository {
    static final class Item {
        final String id, type, message, status, reply, appVersion, androidVersion, device, section, createdAt;
        final String lastMessage, lastSender, lastMessageAt;
        final int rating;
        final int unreadCount;

        Item(JSONObject value) {
            id = value.optString("id");
            type = value.optString("type", "general_feedback");
            message = value.optString("message");
            rating = value.optInt("rating", 0);
            status = value.optString("status", "submitted");
            reply = value.optString("developer_reply");
            appVersion = value.optString("app_version", "unknown");
            androidVersion = value.optString("android_version", "unknown");
            device = value.optString("device", "unknown");
            section = value.optString("section", "unknown");
            createdAt = value.optString("created_at");
            String fallback = reply.isEmpty() ? message : reply;
            lastMessage = value.optString("last_message", fallback);
            lastSender = value.optString(
                    "last_sender",
                    reply.isEmpty() ? "user" : "developer"
            );
            lastMessageAt = value.optString("last_message_at", createdAt);
            unreadCount = Math.max(0, value.optInt("unread_count", 0));
        }
    }

    static final class FeedbackMessage {
        final String id, feedbackId, sender, message, createdAt, readAt;

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
        final Item item;
        final List<FeedbackMessage> messages;

        FeedbackThread(Item item, List<FeedbackMessage> messages) {
            this.item = item;
            this.messages = messages;
        }
    }

    static final class ConfigVersion {
        final long version;
        final long basedOnVersion;
        final String updatedAt;
        final String action;
        final boolean active;

        ConfigVersion(JSONObject value) {
            version = value.optLong("config_version");
            basedOnVersion = value.optLong("based_on_version");
            updatedAt = value.optString("updated_at");
            action = value.optString("action", "publish");
            active = value.optBoolean("is_active");
        }
    }

    static final class AnalyticsRow {
        final String value;
        final long eventCount;
        final long uniqueUsers;
        final long usersToday;

        AnalyticsRow(JSONObject row) {
            value = row.optString("value", "Unknown");
            eventCount = row.optLong("event_count");
            uniqueUsers = row.optLong("unique_users");
            usersToday = row.optLong("users_today");
        }
    }

    static final class FavoriteCreatorRow {
        final String value;
        final long favoriteCount;

        FavoriteCreatorRow(JSONObject row) {
            value = row.optString("value", "Unknown");
            favoriteCount = Math.max(0L, row.optLong("favorite_count"));
        }
    }

    static final class SocialSummary {
        final long accountsTotal, accountsToday, accountsWeek;
        final long socialUsersTotal, socialUsersToday, socialUsersWeek;
        final long profilesWithAvatar, profilesWithBio;
        final long commentsTotal, commentsToday, commentsWeek, repliesTotal, repliesWeek;
        final long videoLikesTotal, videoLikesWeek, commentLikesTotal, commentLikesWeek;
        final long messagesTotal, messagesToday, messagesWeek, messageSendersWeek;
        final long conversationsWeek, messagesRead, messagesUnread;
        final double messageReadRate;
        final long creatorFavoritesTotal, creatorFavoriteAccounts;
        final double averageFavoritesPerAccount;
        final long blocksTotal, notificationPreferenceUsers;
        final long repliesDisabled, likesDisabled, directMessagesDisabled;

        SocialSummary(JSONObject value) {
            JSONObject accounts = object(value, "accounts");
            accountsTotal = accounts.optLong("total");
            accountsToday = accounts.optLong("new_today");
            accountsWeek = accounts.optLong("new_week");

            JSONObject users = object(value, "social_users");
            socialUsersTotal = users.optLong("total");
            socialUsersToday = users.optLong("today");
            socialUsersWeek = users.optLong("week");

            JSONObject profiles = object(value, "profile_adoption");
            profilesWithAvatar = profiles.optLong("with_avatar");
            profilesWithBio = profiles.optLong("with_bio");

            JSONObject comments = object(value, "comments");
            commentsTotal = comments.optLong("total");
            commentsToday = comments.optLong("today");
            commentsWeek = comments.optLong("week");
            repliesTotal = comments.optLong("replies_total");
            repliesWeek = comments.optLong("replies_week");

            JSONObject likes = object(value, "likes");
            videoLikesTotal = likes.optLong("video_total");
            videoLikesWeek = likes.optLong("video_week");
            commentLikesTotal = likes.optLong("comment_total");
            commentLikesWeek = likes.optLong("comment_week");

            JSONObject messages = object(value, "messages");
            messagesTotal = messages.optLong("total");
            messagesToday = messages.optLong("today");
            messagesWeek = messages.optLong("week");
            messageSendersWeek = messages.optLong("senders_week");
            conversationsWeek = messages.optLong("conversations_week");
            messagesRead = messages.optLong("read");
            messagesUnread = messages.optLong("unread");
            messageReadRate = messages.optDouble("read_rate_percent", 0d);

            JSONObject favorites = object(value, "creator_favorites");
            creatorFavoritesTotal = favorites.optLong("total");
            creatorFavoriteAccounts = favorites.optLong("accounts");
            averageFavoritesPerAccount = favorites.optDouble("average_per_account", 0d);

            JSONObject safety = object(value, "safety");
            blocksTotal = safety.optLong("blocks");

            JSONObject preferences = object(value, "notification_preferences");
            notificationPreferenceUsers = preferences.optLong("customized_users");
            repliesDisabled = preferences.optLong("replies_disabled");
            likesDisabled = preferences.optLong("likes_disabled");
            directMessagesDisabled = preferences.optLong("direct_messages_disabled");
        }
    }

    static final class ReleaseAdoption {
        final String version;
        final long usersToday, usersWeek, usersMonth;
        final double percentToday, percentWeek, percentMonth;

        ReleaseAdoption(JSONObject value) {
            version = value.optString("version");
            usersToday = value.optLong("users_today");
            usersWeek = value.optLong("users_week");
            usersMonth = value.optLong("users_month");
            percentToday = value.optDouble("percent_today", 0d);
            percentWeek = value.optDouble("percent_week", 0d);
            percentMonth = value.optDouble("percent_month", 0d);
        }
    }

    static final class AnalyticsDashboard {
        final long dailyUsers;
        final long weeklyUsers;
        final long monthlyUsers;
        final String generatedAt;
        final SocialSummary social;
        final ReleaseAdoption releaseAdoption;
        final List<AnalyticsRow> sections;
        final List<AnalyticsRow> sources;
        final List<AnalyticsRow> creators;
        final List<FavoriteCreatorRow> favoriteCreators;
        final List<AnalyticsRow> versions;
        final List<AnalyticsRow> deviceModels;
        final List<AnalyticsRow> deviceManufacturers;
        final List<AnalyticsRow> androidVersions;

        AnalyticsDashboard(JSONObject value) {
            JSONObject active = value.optJSONObject("active_users");
            dailyUsers = active == null ? 0L : active.optLong("daily");
            weeklyUsers = active == null ? 0L : active.optLong("weekly");
            monthlyUsers = active == null ? 0L : active.optLong("monthly");
            generatedAt = value.optString("generated_at");
            social = new SocialSummary(object(value, "social"));
            releaseAdoption = new ReleaseAdoption(object(value, "release_adoption"));
            sections = analyticsRows(value.optJSONArray("sections"));
            sources = analyticsRows(value.optJSONArray("sources"));
            creators = analyticsRows(value.optJSONArray("creators"));
            favoriteCreators = favoriteCreatorRows(value.optJSONArray("favorite_creators"));
            versions = analyticsRows(value.optJSONArray("versions"));
            deviceModels = analyticsRows(value.optJSONArray("device_models"));
            deviceManufacturers = analyticsRows(value.optJSONArray("device_manufacturers"));
            androidVersions = analyticsRows(value.optJSONArray("android_versions"));
        }
    }

    private AdminRepository() {}

    static List<Item> list(String token) throws Exception {
        JSONObject result = request(BuildConfig.ADMIN_FEEDBACK_ENDPOINT, token,
                new JSONObject().put("action", "list"));
        JSONArray rows = result.optJSONArray("items");
        List<Item> items = new ArrayList<>();
        if (rows != null) {
            for (int i = 0; i < rows.length(); i++) items.add(new Item(rows.getJSONObject(i)));
        }
        return items;
    }

    static FeedbackThread thread(String token, String id) throws Exception {
        JSONObject result = request(BuildConfig.ADMIN_FEEDBACK_ENDPOINT, token,
                new JSONObject().put("action", "thread").put("id", id));
        JSONObject itemObject = result.optJSONObject("item");
        if (itemObject == null) throw new IllegalStateException("Feedback thread could not load.");
        JSONArray rows = result.optJSONArray("messages");
        List<FeedbackMessage> messages = new ArrayList<>();
        if (rows != null) {
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row != null) messages.add(new FeedbackMessage(row));
            }
        }
        return new FeedbackThread(new Item(itemObject), messages);
    }

    static void reply(String token, String id, String status, String message) throws Exception {
        request(BuildConfig.ADMIN_FEEDBACK_ENDPOINT, token, new JSONObject()
                .put("action", "reply")
                .put("id", id)
                .put("status", status)
                .put("message", message));
    }

    static void updateStatus(String token, String id, String status) throws Exception {
        request(BuildConfig.ADMIN_FEEDBACK_ENDPOINT, token, new JSONObject()
                .put("action", "status")
                .put("id", id)
                .put("status", status));
    }

    static void deleteFeedback(String token, String id) throws Exception {
        request(BuildConfig.ADMIN_FEEDBACK_ENDPOINT, token, new JSONObject()
                .put("action", "delete")
                .put("id", id));
    }

    static AnalyticsDashboard analytics(String token) throws Exception {
        JSONObject result = request(BuildConfig.ADMIN_FEEDBACK_ENDPOINT, token,
                new JSONObject().put("action", "analytics"));
        JSONObject analytics = result.optJSONObject("analytics");
        return new AnalyticsDashboard(analytics == null ? new JSONObject() : analytics);
    }

    static void update(String token, String id, String status, String reply) throws Exception {
        request(BuildConfig.ADMIN_FEEDBACK_ENDPOINT, token, new JSONObject()
                .put("action", "update")
                .put("id", id)
                .put("status", status)
                .put("developer_reply", reply));
    }

    static JSONObject currentConfig(String token) throws Exception {
        JSONObject result = request(BuildConfig.ADMIN_SOURCE_CONFIG_ENDPOINT, token,
                new JSONObject().put("action", "current"));
        return result.optJSONObject("item");
    }

    static List<ConfigVersion> configHistory(String token) throws Exception {
        JSONObject result = request(BuildConfig.ADMIN_SOURCE_CONFIG_ENDPOINT, token,
                new JSONObject().put("action", "history"));
        JSONArray rows = result.optJSONArray("items");
        List<ConfigVersion> items = new ArrayList<>();
        if (rows != null) for (int index = 0; index < rows.length(); index++) {
            items.add(new ConfigVersion(rows.getJSONObject(index)));
        }
        return items;
    }

    static long validateConfig(String token, JSONObject config) throws Exception {
        JSONObject result = request(BuildConfig.ADMIN_SOURCE_CONFIG_ENDPOINT, token,
                new JSONObject().put("action", "validate").put("config", config));
        if (!result.optBoolean("valid")) throw new IllegalStateException("Configuration was rejected.");
        return result.optLong("configVersion");
    }

    static long publishConfig(String token, JSONObject config) throws Exception {
        JSONObject result = request(BuildConfig.ADMIN_SOURCE_CONFIG_ENDPOINT, token,
                new JSONObject().put("action", "publish").put("config", config));
        return result.optLong("configVersion");
    }

    static long rollbackConfig(String token, long version) throws Exception {
        JSONObject result = request(BuildConfig.ADMIN_SOURCE_CONFIG_ENDPOINT, token,
                new JSONObject().put("action", "rollback").put("configVersion", version));
        return result.optLong("configVersion");
    }

    private static JSONObject object(JSONObject parent, String key) {
        if (parent == null) return new JSONObject();
        JSONObject value = parent.optJSONObject(key);
        return value == null ? new JSONObject() : value;
    }

    private static List<FavoriteCreatorRow> favoriteCreatorRows(JSONArray values) {
        List<FavoriteCreatorRow> rows = new ArrayList<>();
        if (values == null) return rows;
        for (int index = 0; index < values.length(); index++) {
            JSONObject row = values.optJSONObject(index);
            if (row != null) rows.add(new FavoriteCreatorRow(row));
        }
        return rows;
    }

    private static List<AnalyticsRow> analyticsRows(JSONArray values) {
        List<AnalyticsRow> rows = new ArrayList<>();
        if (values == null) return rows;
        for (int index = 0; index < values.length(); index++) {
            JSONObject row = values.optJSONObject(index);
            if (row != null) rows.add(new AnalyticsRow(row));
        }
        return rows;
    }

    private static JSONObject request(String endpoint, String token, JSONObject body) throws Exception {
        if (token == null || token.trim().isEmpty()) throw new SecurityException("Admin token required.");
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
}
