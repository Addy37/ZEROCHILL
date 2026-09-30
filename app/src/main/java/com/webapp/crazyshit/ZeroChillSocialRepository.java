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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** ZeroChill-owned social data: comments, likes, profiles, blocks, reports, and messaging. */
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

    static final class DirectMessage {
        final String id;
        final String senderId;
        final String recipientId;
        final String body;
        final String createdAt;
        final String readAt;
        final boolean pending;

        DirectMessage(JSONObject value) {
            id = value.optString("id");
            senderId = value.optString("sender_id");
            recipientId = value.optString("recipient_id");
            body = value.optString("body");
            createdAt = value.optString("created_at");
            readAt = value.isNull("read_at") ? "" : value.optString("read_at");
            pending = false;
        }

        DirectMessage(String clientId, String recipient, String message) {
            id = clientId;
            senderId = ""; // Local outgoing row; the server supplies auth.uid().
            recipientId = recipient;
            body = message;
            createdAt = java.time.Instant.now().toString();
            readAt = "";
            pending = true;
        }

        boolean unreadFor(String userId) {
            return clean(userId).equals(recipientId) && clean(readAt).isEmpty();
        }

        String partnerId(String userId) {
            return clean(userId).equals(senderId) ? recipientId : senderId;
        }
    }

    static final class Conversation {
        final PublicProfile profile;
        final DirectMessage lastMessage;
        final int unreadCount;

        Conversation(PublicProfile profile, DirectMessage lastMessage, int unreadCount) {
            this.profile = profile;
            this.lastMessage = lastMessage;
            this.unreadCount = Math.max(0, unreadCount);
        }
    }

    static final class SocialActivity {
        static final String TYPE_LIKE = "like";
        static final String TYPE_REPLY = "reply";

        final String eventId;
        final String type;
        final String actorUserId;
        final PublicProfile actor;
        final String commentId;
        final String pageUrl;
        final String videoTitle;
        final String originalBody;
        final String replyBody;
        final String createdAt;

        SocialActivity(
                String eventId,
                String type,
                String actorUserId,
                PublicProfile actor,
                String commentId,
                String pageUrl,
                String videoTitle,
                String originalBody,
                String replyBody,
                String createdAt
        ) {
            this.eventId = clean(eventId);
            this.type = clean(type);
            this.actorUserId = clean(actorUserId);
            this.actor = actor;
            this.commentId = clean(commentId);
            this.pageUrl = clean(pageUrl);
            this.videoTitle = clean(videoTitle);
            this.originalBody = clean(originalBody);
            this.replyBody = clean(replyBody);
            this.createdAt = clean(createdAt);
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

    static void loadCommentActivity(
            Context context,
            Callback<ArrayList<SocialActivity>> callback
    ) {
        NETWORK.execute(() -> {
            try {
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                String userId = ZeroChillAccountRepository.currentUserIdBlocking(context);
                Response own = request(
                        "GET",
                        "/rest/v1/comments?select=id,canonical_url,video_title,body,created_at"
                                + "&user_id=eq." + encode(userId)
                                + "&deleted_at=is.null&order=created_at.desc&limit=200",
                        token,
                        null,
                        null,
                        null
                );
                if (!own.ok()) throw error(own, "Unable to load your comments.");
                JSONArray ownRows = own.body.isEmpty() ? new JSONArray() : new JSONArray(own.body);
                if (ownRows.length() == 0) {
                    callback.complete(new ArrayList<>(), null);
                    return;
                }

                LinkedHashMap<String, JSONObject> commentsById = new LinkedHashMap<>();
                StringBuilder ids = new StringBuilder();
                for (int i = 0; i < ownRows.length(); i++) {
                    JSONObject row = ownRows.getJSONObject(i);
                    String id = clean(row.optString("id"));
                    if (id.isEmpty()) continue;
                    commentsById.put(id, row);
                    if (ids.length() > 0) ids.append(',');
                    ids.append(id);
                }
                if (ids.length() == 0) {
                    callback.complete(new ArrayList<>(), null);
                    return;
                }

                String cutoff = java.time.Instant.now()
                        .minus(30, java.time.temporal.ChronoUnit.DAYS)
                        .toString();
                Response likesResponse = request(
                        "GET",
                        "/rest/v1/comment_likes?select=comment_id,user_id,created_at"
                                + "&comment_id=in.(" + ids + ")"
                                + "&user_id=neq." + encode(userId)
                                + "&created_at=gte." + encode(cutoff)
                                + "&order=created_at.desc&limit=500",
                        token,
                        null,
                        null,
                        null
                );
                if (!likesResponse.ok()) throw error(likesResponse, "Unable to load comment likes.");
                JSONArray likes = likesResponse.body.isEmpty()
                        ? new JSONArray() : new JSONArray(likesResponse.body);

                Response repliesResponse = request(
                        "GET",
                        "/rest/v1/comments?select=id,parent_id,user_id,body,created_at,canonical_url,video_title"
                                + "&parent_id=in.(" + ids + ")"
                                + "&user_id=neq." + encode(userId)
                                + "&deleted_at=is.null"
                                + "&created_at=gte." + encode(cutoff)
                                + "&order=created_at.desc&limit=500",
                        token,
                        null,
                        null,
                        null
                );
                if (!repliesResponse.ok()) throw error(repliesResponse, "Unable to load comment replies.");
                JSONArray replies = repliesResponse.body.isEmpty()
                        ? new JSONArray() : new JSONArray(repliesResponse.body);

                LinkedHashSet<String> actorIds = new LinkedHashSet<>();
                for (int i = 0; i < likes.length(); i++) {
                    String actorId = clean(likes.getJSONObject(i).optString("user_id"));
                    if (!actorId.isEmpty()) actorIds.add(actorId);
                }
                for (int i = 0; i < replies.length(); i++) {
                    String actorId = clean(replies.getJSONObject(i).optString("user_id"));
                    if (!actorId.isEmpty()) actorIds.add(actorId);
                }
                Map<String, PublicProfile> profiles = loadProfiles(
                        token,
                        new ArrayList<>(actorIds)
                );

                ArrayList<SocialActivity> activity = new ArrayList<>();
                for (int i = 0; i < likes.length(); i++) {
                    JSONObject row = likes.getJSONObject(i);
                    String commentId = clean(row.optString("comment_id"));
                    String actorId = clean(row.optString("user_id"));
                    JSONObject original = commentsById.get(commentId);
                    PublicProfile actor = profiles.get(actorId);
                    if (original == null || actor == null) continue;
                    activity.add(new SocialActivity(
                            "like:" + commentId + ":" + actorId,
                            SocialActivity.TYPE_LIKE,
                            actorId,
                            actor,
                            commentId,
                            original.optString("canonical_url"),
                            original.optString("video_title"),
                            original.optString("body"),
                            "",
                            row.optString("created_at")
                    ));
                }

                for (int i = 0; i < replies.length(); i++) {
                    JSONObject row = replies.getJSONObject(i);
                    String parentId = clean(row.optString("parent_id"));
                    String actorId = clean(row.optString("user_id"));
                    JSONObject original = commentsById.get(parentId);
                    PublicProfile actor = profiles.get(actorId);
                    if (original == null || actor == null) continue;
                    activity.add(new SocialActivity(
                            "reply:" + clean(row.optString("id")),
                            SocialActivity.TYPE_REPLY,
                            actorId,
                            actor,
                            clean(row.optString("id")),
                            row.optString("canonical_url"),
                            row.optString("video_title"),
                            original.optString("body"),
                            row.optString("body"),
                            row.optString("created_at")
                    ));
                }

                Collections.sort(activity, (left, right) ->
                        Long.compare(socialTime(right.createdAt), socialTime(left.createdAt)));
                callback.complete(activity, null);
            } catch (Exception error) {
                callback.complete(null, error);
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

    static void loadInbox(Context context, Callback<ArrayList<Conversation>> callback) {
        NETWORK.execute(() -> {
            try {
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                String userId = ZeroChillAccountRepository.currentUserIdBlocking(context);
                Response response = request(
                        "GET",
                        "/rest/v1/direct_messages?select=id,sender_id,recipient_id,body,created_at,read_at"
                                + "&or=(sender_id.eq." + userId + ",recipient_id.eq." + userId + ")"
                                + "&order=created_at.desc&limit=500",
                        token,
                        null,
                        null,
                        null
                );
                if (!response.ok()) throw error(response, "Unable to load messages.");
                JSONArray rows = response.body.isEmpty() ? new JSONArray() : new JSONArray(response.body);

                LinkedHashMap<String, DirectMessage> lastByPartner = new LinkedHashMap<>();
                LinkedHashMap<String, Integer> unreadByPartner = new LinkedHashMap<>();
                ArrayList<String> partnerIds = new ArrayList<>();
                for (int i = 0; i < rows.length(); i++) {
                    DirectMessage message = new DirectMessage(rows.getJSONObject(i));
                    String partnerId = message.partnerId(userId);
                    if (partnerId.isEmpty()) continue;
                    if (!lastByPartner.containsKey(partnerId)) {
                        lastByPartner.put(partnerId, message);
                        partnerIds.add(partnerId);
                    }
                    if (message.unreadFor(userId)) {
                        unreadByPartner.put(
                                partnerId,
                                unreadByPartner.getOrDefault(partnerId, 0) + 1
                        );
                    }
                }

                Map<String, PublicProfile> profiles = loadProfiles(token, partnerIds);
                ArrayList<Conversation> conversations = new ArrayList<>();
                for (String partnerId : partnerIds) {
                    PublicProfile profile = profiles.get(partnerId);
                    if (profile == null) continue;
                    conversations.add(new Conversation(
                            profile,
                            lastByPartner.get(partnerId),
                            unreadByPartner.getOrDefault(partnerId, 0)
                    ));
                }
                callback.complete(conversations, null);
            } catch (Exception error) {
                callback.complete(null, error);
            }
        });
    }

    static void unreadMessageCount(Context context, Callback<Integer> callback) {
        NETWORK.execute(() -> {
            try {
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                String userId = ZeroChillAccountRepository.currentUserIdBlocking(context);
                Response response = request(
                        "GET",
                        "/rest/v1/direct_messages?select=id&recipient_id=eq." + userId
                                + "&read_at=is.null&limit=500",
                        token,
                        null,
                        null,
                        null
                );
                if (!response.ok()) throw error(response, "Unable to load unread messages.");
                JSONArray rows = response.body.isEmpty() ? new JSONArray() : new JSONArray(response.body);
                callback.complete(rows.length(), null);
            } catch (Exception error) {
                callback.complete(0, error);
            }
        });
    }

    static void loadDirectMessages(
            Context context,
            String partnerId,
            Callback<ArrayList<DirectMessage>> callback
    ) {
        NETWORK.execute(() -> {
            try {
                String other = clean(partnerId);
                if (other.isEmpty()) throw new IllegalArgumentException("This conversation is unavailable.");
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                String userId = ZeroChillAccountRepository.currentUserIdBlocking(context);
                Response response = request(
                        "GET",
                        "/rest/v1/direct_messages?select=id,sender_id,recipient_id,body,created_at,read_at"
                                + "&or=(and(sender_id.eq." + userId + ",recipient_id.eq." + other + "),"
                                + "and(sender_id.eq." + other + ",recipient_id.eq." + userId + "))"
                                + "&order=created_at.asc&limit=500",
                        token,
                        null,
                        null,
                        null
                );
                if (!response.ok()) throw error(response, "Unable to load the conversation.");
                JSONArray rows = response.body.isEmpty() ? new JSONArray() : new JSONArray(response.body);
                ArrayList<DirectMessage> messages = new ArrayList<>();
                for (int i = 0; i < rows.length(); i++) {
                    messages.add(new DirectMessage(rows.getJSONObject(i)));
                }
                callback.complete(messages, null);
            } catch (Exception error) {
                callback.complete(null, error);
            }
        });
    }

    static void sendDirectMessage(
            Context context,
            String recipientId,
            String clientId,
            String body,
            Callback<DirectMessage> callback
    ) {
        NETWORK.execute(() -> {
            try {
                String recipient = clean(recipientId);
                String message = body == null ? "" : body;
                if (recipient.isEmpty()) throw new IllegalArgumentException("This user is unavailable.");
                if (message.trim().isEmpty() || message.length() > 2000) {
                    throw new IllegalArgumentException("Message must contain 1 to 2000 characters.");
                }
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                JSONObject row = directMessagePayload(clientId, recipient, message);
                Response response = request(
                        "POST",
                        "/rest/v1/direct_messages",
                        token,
                        row.toString().getBytes(StandardCharsets.UTF_8),
                        "application/json",
                        "return=representation"
                );
                if (!response.ok()) {
                    if (response.status == 401 || response.status == 403) {
                        throw new IllegalStateException("You can't message this user.");
                    }
                    throw error(response, "Unable to send the message.");
                }
                JSONArray rows = response.body.isEmpty() ? new JSONArray() : new JSONArray(response.body);
                callback.complete(
                        rows.length() == 0 ? null : new DirectMessage(rows.getJSONObject(0)),
                        null
                );
            } catch (Exception error) {
                callback.complete(null, error);
            }
        });
    }

    // The existing UUID primary key links local echo, refresh, and POST response.
    // No content-based matching: identical consecutive messages remain distinct.
    static JSONObject directMessagePayload(String clientId, String recipient, String body)
            throws org.json.JSONException {
        return new JSONObject().put("id", java.util.UUID.fromString(clientId).toString())
                .put("recipient_id", recipient).put("body", body);
    }

    static void markDirectMessagesRead(
            Context context,
            String senderId,
            Callback<Boolean> callback
    ) {
        NETWORK.execute(() -> {
            try {
                String sender = clean(senderId);
                if (sender.isEmpty()) {
                    callback.complete(true, null);
                    return;
                }
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                String userId = ZeroChillAccountRepository.currentUserIdBlocking(context);
                JSONObject body = new JSONObject().put("read_at", java.time.Instant.now().toString());
                Response response = request(
                        "PATCH",
                        "/rest/v1/direct_messages?recipient_id=eq." + userId
                                + "&sender_id=eq." + sender + "&read_at=is.null",
                        token,
                        body.toString().getBytes(StandardCharsets.UTF_8),
                        "application/json",
                        "return=minimal"
                );
                if (!response.ok()) throw error(response, "Unable to update message status.");
                callback.complete(true, null);
            } catch (Exception error) {
                callback.complete(false, error);
            }
        });
    }

    static void blockState(Context context, String userId, Callback<Boolean> callback) {
        NETWORK.execute(() -> {
            try {
                String target = clean(userId);
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                String current = ZeroChillAccountRepository.currentUserIdBlocking(context);
                Response response = request(
                        "GET",
                        "/rest/v1/user_blocks?select=blocked_id&blocker_id=eq." + current
                                + "&blocked_id=eq." + target + "&limit=1",
                        token,
                        null,
                        null,
                        null
                );
                if (!response.ok()) throw error(response, "Unable to load block status.");
                JSONArray rows = response.body.isEmpty() ? new JSONArray() : new JSONArray(response.body);
                callback.complete(rows.length() > 0, null);
            } catch (Exception error) {
                callback.complete(false, error);
            }
        });
    }

    static void setBlocked(
            Context context,
            String userId,
            boolean blocked,
            Callback<Boolean> callback
    ) {
        NETWORK.execute(() -> {
            try {
                String target = clean(userId);
                if (target.isEmpty()) throw new IllegalArgumentException("This user is unavailable.");
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                String current = ZeroChillAccountRepository.currentUserIdBlocking(context);
                Response response;
                if (blocked) {
                    JSONObject row = new JSONObject().put("blocked_id", target);
                    response = request(
                            "POST",
                            "/rest/v1/user_blocks",
                            token,
                            row.toString().getBytes(StandardCharsets.UTF_8),
                            "application/json",
                            "return=minimal"
                    );
                } else {
                    response = request(
                            "DELETE",
                            "/rest/v1/user_blocks?blocker_id=eq." + current
                                    + "&blocked_id=eq." + target,
                            token,
                            null,
                            null,
                            "return=minimal"
                    );
                }
                if (!response.ok()) throw error(response, blocked
                        ? "Unable to block this user."
                        : "Unable to unblock this user.");
                callback.complete(blocked, null);
            } catch (Exception error) {
                callback.complete(!blocked, error);
            }
        });
    }

    static void reportUser(
            Context context,
            String userId,
            String reason,
            Callback<Boolean> callback
    ) {
        submitReport(context, userId, "", reason, callback);
    }

    static void reportDirectMessage(
            Context context,
            String userId,
            String messageId,
            String reason,
            Callback<Boolean> callback
    ) {
        submitReport(context, userId, messageId, reason, callback);
    }

    private static void submitReport(
            Context context,
            String userId,
            String messageId,
            String reason,
            Callback<Boolean> callback
    ) {
        NETWORK.execute(() -> {
            try {
                String target = clean(userId);
                String normalizedReason = clean(reason).toLowerCase(java.util.Locale.US);
                if (!"spam".equals(normalizedReason)
                        && !"harassment".equals(normalizedReason)
                        && !"other".equals(normalizedReason)) {
                    normalizedReason = "other";
                }
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                JSONObject row = new JSONObject()
                        .put("reported_user_id", target)
                        .put("reason", normalizedReason);
                if (!clean(messageId).isEmpty()) row.put("direct_message_id", messageId);
                Response response = request(
                        "POST",
                        "/rest/v1/social_reports",
                        token,
                        row.toString().getBytes(StandardCharsets.UTF_8),
                        "application/json",
                        "return=minimal"
                );
                if (!response.ok()) throw error(response, "Unable to submit the report.");
                callback.complete(true, null);
            } catch (Exception error) {
                callback.complete(false, error);
            }
        });
    }

    private static Map<String, PublicProfile> loadProfiles(
            String token,
            ArrayList<String> userIds
    ) throws Exception {
        LinkedHashMap<String, PublicProfile> profiles = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) return profiles;
        StringBuilder ids = new StringBuilder();
        for (String value : userIds) {
            String id = clean(value);
            if (id.isEmpty()) continue;
            if (ids.length() > 0) ids.append(',');
            ids.append(id);
        }
        if (ids.length() == 0) return profiles;
        Response response = request(
                "GET",
                "/rest/v1/profiles?select=user_id,username,display_name,avatar_path,created_at"
                        + "&user_id=in.(" + ids + ")",
                token,
                null,
                null,
                null
        );
        if (!response.ok()) throw error(response, "Unable to load profiles.");
        JSONArray rows = response.body.isEmpty() ? new JSONArray() : new JSONArray(response.body);
        for (int i = 0; i < rows.length(); i++) {
            PublicProfile profile = new PublicProfile(rows.getJSONObject(i), false);
            profiles.put(profile.userId, profile);
        }
        return profiles;
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

    private static long socialTime(String value) {
        try {
            return java.time.Instant.parse(clean(value)).toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
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
