package com.webapp.crazyshit;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** ZeroChill-owned social data: comments, replies, and likes. */
final class ZeroChillSocialRepository {
    private static final ExecutorService NETWORK = Executors.newFixedThreadPool(2);

    interface Callback<T> {
        void complete(T value, Exception error);
    }

    static final class Comment {
        final String id;
        final String parentId;
        final String userId;
        final String body;
        final String createdAt;
        final String username;
        final String displayName;
        final String avatarPath;
        final int likeCount;
        final boolean likedByMe;

        Comment(JSONObject value, boolean likedByMe) {
            id = value.optString("id");
            parentId = value.optString("parent_id");
            userId = value.optString("user_id");
            body = value.optString("body");
            createdAt = value.optString("created_at");
            username = value.optString("username");
            displayName = value.optString("display_name");
            avatarPath = value.optString("avatar_path");
            likeCount = Math.max(0, value.optInt("like_count", 0));
            this.likedByMe = likedByMe;
        }
    }

    static final class VideoLikeState {
        final int count;
        final boolean liked;

        VideoLikeState(int count, boolean liked) {
            this.count = Math.max(0, count);
            this.liked = liked;
        }
    }

    static final class PublicProfile {
        final String userId;
        final String username;
        final String displayName;
        final String avatarPath;
        final String createdAt;
        final boolean currentUser;

        PublicProfile(JSONObject value, boolean currentUser) {
            userId = value.optString("user_id");
            username = value.optString("username");
            displayName = value.optString("display_name");
            avatarPath = value.optString("avatar_path");
            createdAt = value.optString("created_at");
            this.currentUser = currentUser;
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

    private ZeroChillSocialRepository() {}

    static String contentKey(String pageUrl) {
        String canonical = canonicalUrl(pageUrl);
        if (canonical.isEmpty()) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte value : hashed) out.append(String.format("%02x", value & 0xff));
            return out.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(canonical.hashCode());
        }
    }

    static void loadComments(Context context, String pageUrl, Callback<ArrayList<Comment>> callback) {
        NETWORK.execute(() -> {
            try {
                String key = contentKey(pageUrl);
                if (key.isEmpty()) throw new IllegalArgumentException("This video does not have a stable page URL.");
                Response response = request(
                        "GET",
                        "/rest/v1/comment_feed?select=id,parent_id,user_id,body,created_at,username,display_name,avatar_path,like_count"
                                + "&content_key=eq." + encode(key) + "&order=created_at.asc",
                        "",
                        null,
                        null,
                        null
                );
                if (!response.ok()) throw error(response, "Unable to load comments.");
                JSONArray rows = response.body.isEmpty() ? new JSONArray() : new JSONArray(response.body);

                Set<String> liked = new HashSet<>();
                if (ZeroChillAccountRepository.hasStoredSession(context) && rows.length() > 0) {
                    try {
                        String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                        String userId = ZeroChillAccountRepository.currentUserIdBlocking(context);
                        StringBuilder ids = new StringBuilder();
                        for (int i = 0; i < rows.length(); i++) {
                            if (i > 0) ids.append(',');
                            ids.append(rows.getJSONObject(i).optString("id"));
                        }
                        Response own = request(
                                "GET",
                                "/rest/v1/comment_likes?select=comment_id&user_id=eq." + encode(userId)
                                        + "&comment_id=in.(" + ids + ")",
                                token,
                                null,
                                null,
                                null
                        );
                        if (own.ok()) {
                            JSONArray ownRows = new JSONArray(own.body);
                            for (int i = 0; i < ownRows.length(); i++) {
                                liked.add(ownRows.getJSONObject(i).optString("comment_id"));
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }

                ArrayList<Comment> comments = new ArrayList<>();
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    comments.add(new Comment(row, liked.contains(row.optString("id"))));
                }
                callback.complete(comments, null);
            } catch (Exception error) {
                callback.complete(null, error);
            }
        });
    }

    static void postComment(
            Context context,
            String pageUrl,
            String title,
            String parentId,
            String body,
            Callback<Boolean> callback
    ) {
        NETWORK.execute(() -> {
            try {
                String message = clean(body);
                if (message.isEmpty() || message.length() > 2000) {
                    throw new IllegalArgumentException("Comment must contain 1 to 2000 characters.");
                }
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                String key = contentKey(pageUrl);
                JSONObject row = new JSONObject()
                        .put("content_key", key)
                        .put("canonical_url", canonicalUrl(pageUrl))
                        .put("video_title", clean(title))
                        .put("body", message);
                if (!clean(parentId).isEmpty()) row.put("parent_id", parentId);

                Response response = request(
                        "POST",
                        "/rest/v1/comments",
                        token,
                        row.toString().getBytes(StandardCharsets.UTF_8),
                        "application/json",
                        "return=minimal"
                );
                if (!response.ok()) throw error(response, "Unable to post the comment.");
                callback.complete(true, null);
            } catch (Exception error) {
                callback.complete(false, error);
            }
        });
    }

    static void toggleCommentLike(
            Context context,
            String commentId,
            boolean currentlyLiked,
            Callback<Boolean> callback
    ) {
        NETWORK.execute(() -> {
            try {
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                String userId = ZeroChillAccountRepository.currentUserIdBlocking(context);
                Response response;
                if (currentlyLiked) {
                    response = request(
                            "DELETE",
                            "/rest/v1/comment_likes?comment_id=eq." + encode(commentId)
                                    + "&user_id=eq." + encode(userId),
                            token,
                            null,
                            null,
                            "return=minimal"
                    );
                } else {
                    JSONObject row = new JSONObject().put("comment_id", commentId);
                    response = request(
                            "POST",
                            "/rest/v1/comment_likes",
                            token,
                            row.toString().getBytes(StandardCharsets.UTF_8),
                            "application/json",
                            "return=minimal"
                    );
                }
                if (!response.ok()) throw error(response, "Unable to update the like.");
                callback.complete(!currentlyLiked, null);
            } catch (Exception error) {
                callback.complete(currentlyLiked, error);
            }
        });
    }

    static void loadProfile(Context context, String userId, Callback<PublicProfile> callback) {
        NETWORK.execute(() -> {
            try {
                String id = clean(userId);
                if (id.isEmpty()) throw new IllegalArgumentException("This profile is unavailable.");
                Response response = request(
                        "GET",
                        "/rest/v1/profiles?select=user_id,username,display_name,avatar_path,created_at"
                                + "&user_id=eq." + encode(id) + "&limit=1",
                        "",
                        null,
                        null,
                        null
                );
                if (!response.ok()) throw error(response, "Unable to load the profile.");
                JSONArray rows = response.body.isEmpty() ? new JSONArray() : new JSONArray(response.body);
                if (rows.length() == 0) throw new IllegalStateException("This profile is unavailable.");

                boolean current = false;
                if (ZeroChillAccountRepository.hasStoredSession(context)) {
                    try {
                        current = id.equals(ZeroChillAccountRepository.currentUserIdBlocking(context));
                    } catch (Exception ignored) {
                    }
                }
                callback.complete(new PublicProfile(rows.getJSONObject(0), current), null);
            } catch (Exception error) {
                callback.complete(null, error);
            }
        });
    }

    static void videoLikeState(Context context, String pageUrl, Callback<VideoLikeState> callback) {
        NETWORK.execute(() -> {
            try {
                String key = contentKey(pageUrl);
                Response response = request(
                        "GET",
                        "/rest/v1/video_likes?select=user_id&content_key=eq." + encode(key),
                        "",
                        null,
                        null,
                        null
                );
                if (!response.ok()) throw error(response, "Unable to load likes.");
                JSONArray rows = response.body.isEmpty() ? new JSONArray() : new JSONArray(response.body);
                String mine = "";
                if (ZeroChillAccountRepository.hasStoredSession(context)) {
                    try {
                        mine = ZeroChillAccountRepository.currentUserIdBlocking(context);
                    } catch (Exception ignored) {
                    }
                }
                boolean liked = false;
                for (int i = 0; i < rows.length(); i++) {
                    if (mine.equals(rows.getJSONObject(i).optString("user_id"))) liked = true;
                }
                callback.complete(new VideoLikeState(rows.length(), liked), null);
            } catch (Exception error) {
                callback.complete(null, error);
            }
        });
    }

    static void toggleVideoLike(
            Context context,
            String pageUrl,
            boolean currentlyLiked,
            Callback<VideoLikeState> callback
    ) {
        NETWORK.execute(() -> {
            try {
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                String userId = ZeroChillAccountRepository.currentUserIdBlocking(context);
                String key = contentKey(pageUrl);
                Response response;
                if (currentlyLiked) {
                    response = request(
                            "DELETE",
                            "/rest/v1/video_likes?content_key=eq." + encode(key)
                                    + "&user_id=eq." + encode(userId),
                            token,
                            null,
                            null,
                            "return=minimal"
                    );
                } else {
                    JSONObject row = new JSONObject().put("content_key", key);
                    response = request(
                            "POST",
                            "/rest/v1/video_likes",
                            token,
                            row.toString().getBytes(StandardCharsets.UTF_8),
                            "application/json",
                            "return=minimal"
                    );
                }
                if (!response.ok()) throw error(response, "Unable to update the video like.");
                videoLikeState(context, pageUrl, callback);
            } catch (Exception error) {
                callback.complete(null, error);
            }
        });
    }

    private static String canonicalUrl(String raw) {
        String value = clean(raw);
        if (value.isEmpty()) return "";
        try {
            Uri uri = Uri.parse(value);
            Uri.Builder builder = uri.buildUpon().fragment(null);
            String built = builder.build().toString();
            while (built.endsWith("/") && built.length() > 8) {
                built = built.substring(0, built.length() - 1);
            }
            return built;
        } catch (Exception ignored) {
            return value;
        }
    }

    private static Response request(
            String method,
            String path,
            String token,
            byte[] body,
            String contentType,
            String prefer
    ) throws Exception {
        if (!ZeroChillAccountRepository.isConfigured()) {
            throw new IllegalStateException("ZeroChill social features are not configured in this build.");
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(
                    BuildConfig.ACCOUNT_SUPABASE_URL + path
            ).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(20_000);
            connection.setRequestProperty("apikey", BuildConfig.ACCOUNT_SUPABASE_PUBLISHABLE_KEY);
            if (!clean(token).isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
            if (contentType != null) connection.setRequestProperty("Content-Type", contentType);
            if (prefer != null) connection.setRequestProperty("Prefer", prefer);
            if (body != null && body.length > 0) {
                connection.setDoOutput(true);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body);
                }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            return new Response(status, read(stream));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static Exception error(Response response, String fallback) {
        String message = fallback;
        try {
            JSONObject object = new JSONObject(response.body);
            String candidate = clean(object.optString("message"));
            if (candidate.isEmpty()) candidate = clean(object.optString("error"));
            if (!candidate.isEmpty()) message = candidate;
        } catch (Exception ignored) {
        }
        return new IllegalStateException(message);
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
}
