package com.webapp.crazyshit;

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;

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
import java.util.Collection;
import java.util.HashMap;
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
    private static final Object SUMMARY_LOCK = new Object();
    private static final long SUMMARY_TTL_MS = 120_000L;
    private static final int SUMMARY_MAX_ENTRIES = 120;
    private static long summaryRevision;
    private static final LinkedHashMap<String, SummaryCacheEntry> SUMMARY_CACHE =
            new LinkedHashMap<>(32, 0.75f, true);
    private static final Map<String, ArrayList<Callback<VideoSocialState>>> SUMMARY_PENDING = new HashMap<>();

    interface Callback<T> {
        void complete(T value, Exception error);
    }

    static final class Comment {
        final String id;
        final String parentId;
        final String userId;
        final String body;
        final String attachmentBucket;
        final String attachmentPath;
        final String createdAt;
        final String editedAt;
        final String deletedAt;
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
            attachmentBucket = value.optString("attachment_bucket", "");
            attachmentPath = value.optString("attachment_path", "");
            createdAt = value.optString("created_at");
            editedAt = value.isNull("edited_at") ? "" : value.optString("edited_at");
            deletedAt = value.isNull("deleted_at") ? "" : value.optString("deleted_at");
            username = value.optString("username");
            displayName = value.optString("display_name");
            avatarPath = value.optString("avatar_path");
            likeCount = Math.max(0, value.optInt("like_count", 0));
            this.likedByMe = likedByMe;
        }

        private Comment(Comment source, boolean liked) {
            id = source.id; parentId = source.parentId; userId = source.userId;
            attachmentBucket = source.attachmentBucket; attachmentPath = source.attachmentPath;
            body = source.body; createdAt = source.createdAt; editedAt = source.editedAt;
            deletedAt = source.deletedAt; username = source.username;
            displayName = source.displayName; avatarPath = source.avatarPath;
            likedByMe = liked;
            likeCount = Math.max(0, source.likeCount + (liked == source.likedByMe ? 0 : liked ? 1 : -1));
        }

        Comment withLikeState(boolean liked) { return new Comment(this, liked); }

        boolean deleted() {
            return !clean(deletedAt).isEmpty();
        }

        boolean edited() {
            return !deleted() && !clean(editedAt).isEmpty();
        }
    }

    static final class VideoLikeState {
        final int count;
        final boolean liked;
        final boolean countKnown;

        VideoLikeState(int count, boolean liked) { this(count, liked, true); }
        VideoLikeState(int count, boolean liked, boolean countKnown) {
            this.count = Math.max(0, count);
            this.liked = liked;
            this.countKnown = countKnown;
        }
    }

    static VideoLikeState confirmedVideoLikeWrite(boolean previouslyLiked, VideoLikeState refreshed) {
        // The mutation already committed. A failed count read must not undo its own-like state.
        return refreshed != null ? refreshed : new VideoLikeState(0, !previouslyLiked, false);
    }

    static final class VideoSocialState {
        final int likeCount;
        final int commentCount;
        final boolean liked;

        VideoSocialState(int likeCount, int commentCount, boolean liked) {
            this.likeCount = Math.max(0, likeCount);
            this.commentCount = Math.max(0, commentCount);
            this.liked = liked;
        }
    }

    private static final class SummaryCacheEntry {
        final VideoSocialState value;
        final long recordedAt;
        SummaryCacheEntry(VideoSocialState value, long recordedAt) {
            this.value = value;
            this.recordedAt = recordedAt;
        }
    }


    static final class PublicProfile {
        final String userId;
        final String username;
        final String displayName;
        final String avatarPath;
        final String bio;
        final String createdAt;
        final boolean currentUser;

        PublicProfile(JSONObject value, boolean currentUser) {
            userId = value.optString("user_id");
            username = value.optString("username");
            displayName = value.optString("display_name");
            avatarPath = value.optString("avatar_path");
            bio = value.optString("bio");
            createdAt = value.optString("created_at");
            this.currentUser = currentUser;
        }
    }

    static final class SharedCreator {
        final String key;
        final String name;
        final NativeContentItem metadata;

        SharedCreator(JSONObject value) { this(value, null); }

        SharedCreator(JSONObject value, NativeContentItem metadata) {
            this.metadata = metadata;
            key = clean(value.optString("creator_key"));
            name = clean(value.optString("creator_name"));
        }
    }

    static final class DirectMessage {
        final String id;
        final String senderId;
        final String recipientId;
        final String body;
        final String attachmentBucket;
        final String attachmentPath;
        final String createdAt;
        final String readAt;
        final boolean pending;

        DirectMessage(JSONObject value) {
            id = value.optString("id");
            senderId = value.optString("sender_id");
            recipientId = value.optString("recipient_id");
            body = value.optString("body");
            attachmentBucket = value.optString("attachment_bucket", "");
            attachmentPath = value.optString("attachment_path", "");
            createdAt = value.optString("created_at");
            readAt = value.isNull("read_at") ? "" : value.optString("read_at");
            pending = false;
        }

        DirectMessage(String clientId, String recipient, String message) {
            id = clientId;
            senderId = ""; // Local outgoing row; the server supplies auth.uid().
            recipientId = recipient;
            body = message;
            attachmentBucket = ""; attachmentPath = "";
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

    static final class ClearedMessageList extends ArrayList<DirectMessage> {
        final String cutoff;
        ClearedMessageList(String cutoff) { this.cutoff = cutoff; }
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
                        "/rest/v1/comment_feed?select=id,parent_id,user_id,body,created_at,edited_at,deleted_at,username,display_name,avatar_path,like_count,attachment_bucket,attachment_path"
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
        postComment(context,pageUrl,title,parentId,body,null,java.util.UUID.randomUUID().toString(),callback);
    }

    static void postComment(Context context, String pageUrl, String title, String parentId,
            String body, SocialMedia.Prepared media, String clientId, Callback<Boolean> callback) {
        final String owner = ZeroChillSessionStore.currentUserId(context);
        NETWORK.execute(() -> {
            try {
                String message = clean(body);
                if (message.isEmpty() && media != null) message = media.fallback();
                if (message.isEmpty() || message.length() > 2000) {
                    throw new IllegalArgumentException("Comment must contain 1 to 2000 characters.");
                }
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                String key = contentKey(pageUrl);
                JSONObject row = new JSONObject()
                        .put("id", clientId)
                        .put("content_key", key)
                        .put("canonical_url", canonicalUrl(pageUrl))
                        .put("video_title", clean(title))
                        .put("body", message);
                if (!clean(parentId).isEmpty()) row.put("parent_id", parentId);

                requireSameAccount(context, owner);
                attach(context, row, media, token, owner);
                Response response = request(
                        "POST",
                        "/rest/v1/comments",
                        token,
                        row.toString().getBytes(StandardCharsets.UTF_8),
                        "application/json",
                        "return=minimal"
                );
                if(response.status==409) {
                    Response existing=request("GET","/rest/v1/comments?select=id&id=eq."+encode(clientId)+"&user_id=eq."+encode(owner),token,null,null,null);
                    if(existing.ok() && new JSONArray(existing.body).length()==1) {
                        if (BuildConfig.ZEROCHILL_V7_TRAFFIC) invalidateVideoSummary();
                        callback.complete(true,null);return;
                    }
                }
                if (!response.ok()) throw error(response, "Unable to post the comment.");
                if (BuildConfig.ZEROCHILL_V7_TRAFFIC) invalidateVideoSummary();
                AnalyticsTracker.event(context,"comments");
                callback.complete(true, null);
            } catch (Exception error) {
                callback.complete(false, error);
            }
        });
    }

    static void editComment(
            Context context,
            String commentId,
            String body,
            Callback<Boolean> callback
    ) {
        NETWORK.execute(() -> {
            try {
                String id = clean(commentId);
                String message = clean(body);
                if (id.isEmpty()) throw new IllegalArgumentException("This comment is unavailable.");
                if (message.isEmpty() || message.length() > 2000) {
                    throw new IllegalArgumentException("Comment must contain 1 to 2000 characters.");
                }
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                String userId = ZeroChillAccountRepository.currentUserIdBlocking(context);
                JSONObject row = new JSONObject()
                        .put("body", message)
                        .put("edited_at", java.time.Instant.now().toString());
                Response response = request(
                        "PATCH",
                        "/rest/v1/comments?id=eq." + encode(id)
                                + "&user_id=eq." + encode(userId)
                                + "&deleted_at=is.null",
                        token,
                        row.toString().getBytes(StandardCharsets.UTF_8),
                        "application/json",
                        "return=minimal"
                );
                if (!response.ok()) throw error(response, "Unable to edit the comment.");
                callback.complete(true, null);
            } catch (Exception error) {
                callback.complete(false, error);
            }
        });
    }

    static void deleteComment(
            Context context,
            String commentId,
            Callback<Boolean> callback
    ) {
        NETWORK.execute(() -> {
            try {
                String id = clean(commentId);
                if (id.isEmpty()) throw new IllegalArgumentException("This comment is unavailable.");
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                String userId = ZeroChillAccountRepository.currentUserIdBlocking(context);
                JSONObject row = new JSONObject()
                        .put("body", "Comment deleted")
                        .put("deleted_at", java.time.Instant.now().toString());
                Response response = request(
                        "PATCH",
                        "/rest/v1/comments?id=eq." + encode(id)
                                + "&user_id=eq." + encode(userId)
                                + "&deleted_at=is.null",
                        token,
                        row.toString().getBytes(StandardCharsets.UTF_8),
                        "application/json",
                        "return=minimal"
                );
                if (!response.ok()) throw error(response, "Unable to delete the comment.");
                if (BuildConfig.ZEROCHILL_V7_TRAFFIC) invalidateVideoSummary();
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
                if(!currentlyLiked) AnalyticsTracker.event(context,"likes");
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
                        "/rest/v1/profiles?select=user_id,username,display_name,avatar_path,bio,created_at"
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

    static void loadSharedCreators(
            Context context,
            String targetUserId,
            Callback<ArrayList<SharedCreator>> callback
    ) {
        final String expectedUser = ZeroChillSessionStore.currentUserId(context);
        NETWORK.execute(() -> {
            try {
                requireSameAccount(context, expectedUser);
                String target = clean(targetUserId);
                String self = ZeroChillAccountRepository.currentUserIdBlocking(context);
                if (!expectedUser.equals(self)) throw new IllegalStateException("Account changed. Try again.");
                if (target.isEmpty() || target.equals(self)) {
                    callback.complete(new ArrayList<>(), null);
                    return;
                }
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                requireSameAccount(context, expectedUser);
                JSONObject args = new JSONObject().put("target_user_id", target);
                Response response = request("POST", "/rest/v1/rpc/shared_zerochill_creators",
                        token, args.toString().getBytes(StandardCharsets.UTF_8),
                        "application/json", null);
                if (!response.ok()) throw error(response, "Unable to load shared creators.");
                JSONArray rows = response.body.isEmpty() ? new JSONArray() : new JSONArray(response.body);
                ArrayList<SharedCreator> shared = new ArrayList<>();
                Map<String, NativeContentItem> artwork = new LinkedHashMap<>();
                for (NativeContentItem item : CreatorCatalog.rawFavorites(context))
                    artwork.put(CreatorFavoriteStore.key(item), item);
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    SharedCreator creator = new SharedCreator(row, artwork.get(clean(row.optString("creator_key"))));
                    if (!creator.key.isEmpty()) shared.add(creator);
                }
                requireSameAccount(context, expectedUser);
                callback.complete(shared, null);
            } catch (Exception error) {
                callback.complete(null, error);
            }
        });
    }

    static void loadBlockedUsers(Context context, Callback<ArrayList<PublicProfile>> callback) {
        final String expectedUser = ZeroChillSessionStore.currentUserId(context);
        NETWORK.execute(() -> {
            try {
                requireSameAccount(context, expectedUser);
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                String current = ZeroChillAccountRepository.currentUserIdBlocking(context);
                if (!expectedUser.equals(current)) throw new IllegalStateException("Account changed. Try again.");
                Response response = request("GET", "/rest/v1/user_blocks?select=blocked_id"
                                + "&blocker_id=eq." + encode(current) + "&order=created_at.desc&limit=500",
                        token, null, null, null);
                if (!response.ok()) throw error(response, "Unable to load blocked users.");
                JSONArray rows = response.body.isEmpty() ? new JSONArray() : new JSONArray(response.body);
                ArrayList<String> ids = new ArrayList<>();
                for (int i = 0; i < rows.length(); i++) {
                    String id = clean(rows.getJSONObject(i).optString("blocked_id"));
                    if (!id.isEmpty()) ids.add(id);
                }
                Map<String, PublicProfile> profiles = loadProfiles(token, ids);
                requireSameAccount(context, expectedUser);
                ArrayList<PublicProfile> blocked = new ArrayList<>();
                for (String id : ids) {
                    PublicProfile profile = profiles.get(id);
                    if (profile != null) blocked.add(profile);
                }
                callback.complete(blocked, null);
            } catch (Exception error) {
                callback.complete(null, error);
            }
        });
    }

    static void loadInbox(Context context, Callback<ArrayList<Conversation>> callback) {
        final String expectedUser = ZeroChillSessionStore.currentUserId(context);
        final long cleanupRevision = ZeroChillMessageBadgeStore.cleanupRevision(context, expectedUser);
        NETWORK.execute(() -> {
            try {
                requireSameAccount(context, expectedUser);
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                requireSameAccount(context, expectedUser);
                String userId = expectedUser;
                Response response = request(
                        "GET",
                        "/rest/v1/rpc/visible_zerochill_direct_messages?select=id,sender_id,recipient_id,body,created_at,read_at,attachment_bucket,attachment_path"
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
                requireSameAccount(context, expectedUser);
                requireCleanupRevision(context, expectedUser, cleanupRevision);
                callback.complete(conversations, null);
            } catch (Exception error) {
                callback.complete(null, error);
            }
        });
    }

    static void unreadMessageCount(Context context, Callback<Integer> callback) {
        final String expectedUser = ZeroChillSessionStore.currentUserId(context);
        final long cleanupRevision = ZeroChillMessageBadgeStore.cleanupRevision(context, expectedUser);
        NETWORK.execute(() -> {
            try {
                requireSameAccount(context, expectedUser);
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                requireSameAccount(context, expectedUser);
                String userId = expectedUser;
                Response response = request(
                        "GET",
                        "/rest/v1/rpc/visible_zerochill_direct_messages?select=id&recipient_id=eq." + userId
                                + "&read_at=is.null&limit=500",
                        token,
                        null,
                        null,
                        null
                );
                if (!response.ok()) throw error(response, "Unable to load unread messages.");
                JSONArray rows = response.body.isEmpty() ? new JSONArray() : new JSONArray(response.body);
                requireSameAccount(context, expectedUser);
                requireCleanupRevision(context, expectedUser, cleanupRevision);
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
        final String expectedUser = ZeroChillSessionStore.currentUserId(context);
        final long cleanupRevision = ZeroChillMessageBadgeStore.cleanupRevision(context, expectedUser);
        NETWORK.execute(() -> {
            try {
                String other = clean(partnerId);
                if (other.isEmpty()) throw new IllegalArgumentException("This conversation is unavailable.");
                requireSameAccount(context, expectedUser);
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                requireSameAccount(context, expectedUser);
                String userId = expectedUser;
                String cutoff = loadConversationCutoff(token, other);
                requireSameAccount(context, expectedUser);
                Response response = request(
                        "GET",
                        "/rest/v1/rpc/visible_zerochill_direct_messages?select=id,sender_id,recipient_id,body,created_at,read_at,attachment_bucket,attachment_path"
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
                ArrayList<DirectMessage> messages = new ClearedMessageList(cutoff);
                for (int i = 0; i < rows.length(); i++) {
                    messages.add(new DirectMessage(rows.getJSONObject(i)));
                }
                requireSameAccount(context, expectedUser);
                requireCleanupRevision(context, expectedUser, cleanupRevision);
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
        sendDirectMessage(context,recipientId,clientId,body,null,callback);
    }

    static void sendDirectMessage(Context context, String recipientId, String clientId, String body,
            SocialMedia.Prepared media, Callback<DirectMessage> callback) {
        final String expectedUser = ZeroChillSessionStore.currentUserId(context);
        NETWORK.execute(() -> {
            try {
                String recipient = clean(recipientId);
                String message = body == null ? "" : body;
                if (message.trim().isEmpty() && media != null) message = media.fallback();
                if (recipient.isEmpty()) throw new IllegalArgumentException("This user is unavailable.");
                if (message.trim().isEmpty() || message.length() > 2000) {
                    throw new IllegalArgumentException("Message must contain 1 to 2000 characters.");
                }
                requireSameAccount(context, expectedUser);
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                requireSameAccount(context, expectedUser);
                JSONObject row = directMessagePayload(clientId, recipient, message);
                attach(context,row,media,token,expectedUser);
                Response response = request(
                        "POST",
                        "/rest/v1/direct_messages",
                        token,
                        row.toString().getBytes(StandardCharsets.UTF_8),
                        "application/json",
                        "return=representation"
                );
                boolean reconciled=false;
                if(response.status==409) {
                    Response existing=request("GET","/rest/v1/direct_messages?select=*&id=eq."+encode(clientId)
                            +"&sender_id=eq."+encode(expectedUser)+"&recipient_id=eq."+encode(recipient),token,null,null,null);
                    if(existing.ok() && new JSONArray(existing.body).length()==1) {response=existing;reconciled=true;}
                }
                if (!response.ok()) {
                    if (response.status == 401 || response.status == 403) {
                        throw new IllegalStateException("You can't message this user.");
                    }
                    throw error(response, "Unable to send the message.");
                }
                if(!reconciled) AnalyticsTracker.event(context,"dms");
                JSONArray rows = response.body.isEmpty() ? new JSONArray() : new JSONArray(response.body);
                requireSameAccount(context, expectedUser);
                callback.complete(
                        rows.length() == 0 ? null : new DirectMessage(rows.getJSONObject(0)),
                        null
                );
            } catch (Exception error) {
                callback.complete(null, error);
            }
        });
    }

    private static void attach(Context context, JSONObject row, SocialMedia.Prepared media,
            String token, String owner) throws Exception {
        if (media == null) return;
        String id = row.optString("id");
        if (id.isEmpty()) { id = java.util.UUID.randomUUID().toString(); row.put("id",id); }
        String path = owner + "/" + id;
        requireSameAccount(context, owner);
        cleanupMediaDebt(context,token,owner);
        android.content.SharedPreferences debt=context.getSharedPreferences("social_media_debt_"+owner,Context.MODE_PRIVATE);
        debt.edit().putLong(media.bucket()+"/"+path,System.currentTimeMillis()).apply();
        Response upload = request("POST", "/functions/v1/social-media-upload?id="+id,
                token,media.bytes,media.mime,null);
        if (!upload.ok() && upload.status != 409) throw error(upload,"Upload failed. Your draft is saved; try again.");
        row.put("attachment_bucket",media.bucket()).put("attachment_path",path);
        requireSameAccount(context, owner);
    }

    private static void cleanupMediaDebt(Context context,String token,String owner) {
        android.content.SharedPreferences debt=context.getSharedPreferences("social_media_debt_"+owner,Context.MODE_PRIVATE);
        int attempted=0;
        for(Map.Entry<String,?> entry:debt.getAll().entrySet()) {
            if(attempted>=10 || !(entry.getValue() instanceof Long)
                    || System.currentTimeMillis()-(Long)entry.getValue()<24*60*60*1000L) continue;
            attempted++;
            try {
                requireSameAccount(context,owner);
                String[] parts=entry.getKey().split("/",2);
                Response removed=request("DELETE","/storage/v1/object/"+parts[0],token,
                        new JSONObject().put("prefixes",new JSONArray().put(parts[1])).toString().getBytes(StandardCharsets.UTF_8),"application/json",null);
                if(removed.ok()) debt.edit().remove(entry.getKey()).apply();
            } catch(Exception ignored) { }
        }
    }

    static void loadReactions(Context context, String partner, Callback<JSONArray> callback) {
        final String owner=ZeroChillSessionStore.currentUserId(context);
        NETWORK.execute(() -> {
            try {
                String token=ZeroChillAccountRepository.accessTokenBlocking(context);
                requireSameAccount(context,owner);
                Response response=request("POST","/rest/v1/rpc/zerochill_dm_reactions",token,
                        new JSONObject().put("partner",partner).toString().getBytes(StandardCharsets.UTF_8),"application/json",null);
                if(!response.ok()) throw error(response,"Unable to load reactions.");
                requireSameAccount(context,owner);
                callback.complete(new JSONArray(response.body),null);
            } catch(Exception e) { callback.complete(null,e); }
        });
    }

    static void react(Context context, String messageId, String emoji, Callback<Boolean> callback) {
        final String owner=ZeroChillSessionStore.currentUserId(context);
        NETWORK.execute(() -> {
            try {
                String token=ZeroChillAccountRepository.accessTokenBlocking(context);
                requireSameAccount(context,owner);
                Response response=request("POST","/rest/v1/rpc/toggle_zerochill_dm_reaction",token,
                        new JSONObject().put("message",messageId).put("reaction",emoji).toString().getBytes(StandardCharsets.UTF_8),"application/json",null);
                if(!response.ok()) throw error(response,"Unable to update reaction.");
                requireSameAccount(context,owner); callback.complete(true,null);
            } catch(Exception e) { callback.complete(false,e); }
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
        final String expectedUser = ZeroChillSessionStore.currentUserId(context);
        NETWORK.execute(() -> {
            try {
                String sender = clean(senderId);
                if (sender.isEmpty()) {
                    callback.complete(true, null);
                    return;
                }
                requireSameAccount(context, expectedUser);
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                requireSameAccount(context, expectedUser);
                String userId = expectedUser;
                String cutoff = loadConversationCutoff(token, sender);
                requireSameAccount(context, expectedUser);
                JSONObject body = new JSONObject().put("read_at", java.time.Instant.now().toString());
                Response response = request(
                        "PATCH",
                        "/rest/v1/direct_messages?recipient_id=eq." + userId
                                + "&sender_id=eq." + sender + "&read_at=is.null"
                                + (cutoff.isEmpty() ? "" : "&created_at=gt." + encode(cutoff)),
                        token,
                        body.toString().getBytes(StandardCharsets.UTF_8),
                        "application/json",
                        "return=minimal"
                );
                if (!response.ok()) throw error(response, "Unable to update message status.");
                requireSameAccount(context, expectedUser);
                callback.complete(true, null);
            } catch (Exception error) {
                callback.complete(false, error);
            }
        });
    }

    /** Removes only this account's view. Shared message rows and read receipts are untouched. */
    static void clearConversation(Context context, String partnerId, Callback<Boolean> callback) {
        final String expectedUser = ZeroChillSessionStore.currentUserId(context);
        NETWORK.execute(() -> {
            try {
                String other = java.util.UUID.fromString(clean(partnerId)).toString();
                requireSameAccount(context, expectedUser);
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                requireSameAccount(context, expectedUser);
                Response response = request("POST", "/rest/v1/rpc/clear_zerochill_conversation", token,
                        new JSONObject().put("partner", other).toString().getBytes(StandardCharsets.UTF_8),
                        "application/json", null);
                if (!response.ok()) throw error(response, "Unable to remove the conversation.");
                requireSameAccount(context, expectedUser);
                ZeroChillMessageBadgeStore.conversationCleared(context, expectedUser);
                callback.complete(true, null);
            } catch (Exception error) {
                callback.complete(false, error);
            }
        });
    }

    private static String loadConversationCutoff(String token, String partner) throws Exception {
        Response response = request("GET", "/rest/v1/conversation_clear_state?select=cleared_at"
                + "&partner_id=eq." + encode(partner), token, null, null, null);
        if (!response.ok()) throw error(response, "Unable to load conversation state.");
        JSONArray rows = response.body.isEmpty() ? new JSONArray() : new JSONArray(response.body);
        return rows.length() == 0 ? "" : rows.getJSONObject(0).optString("cleared_at");
    }

    private static void requireCleanupRevision(Context context, String userId, long revision) {
        if (revision != ZeroChillMessageBadgeStore.cleanupRevision(context, userId)) {
            throw new IllegalStateException("Messages changed. Refreshing your inbox.");
        }
    }

    static void blockState(Context context, String userId, Callback<Boolean> callback) {
        final String expectedUser = ZeroChillSessionStore.currentUserId(context);
        NETWORK.execute(() -> {
            try {
                requireSameAccount(context, expectedUser);
                String target = clean(userId);
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                String current = ZeroChillAccountRepository.currentUserIdBlocking(context);
                if (!expectedUser.equals(current)) throw new IllegalStateException("Account changed. Try again.");
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
                requireSameAccount(context, expectedUser);
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
        final String expectedUser = ZeroChillSessionStore.currentUserId(context);
        NETWORK.execute(() -> {
            try {
                requireSameAccount(context, expectedUser);
                String target = clean(userId);
                if (target.isEmpty()) throw new IllegalArgumentException("This user is unavailable.");
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                String current = ZeroChillAccountRepository.currentUserIdBlocking(context);
                if (!expectedUser.equals(current)) throw new IllegalStateException("Account changed. Try again.");
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
                requireSameAccount(context, expectedUser);
                callback.complete(blocked, null);
            } catch (Exception error) {
                callback.complete(!blocked, error);
            }
        });
    }

    private static void requireSameAccount(Context context, String expectedUser) {
        if (expectedUser.isEmpty()
                || !expectedUser.equals(ZeroChillSessionStore.currentUserId(context))) {
            throw new IllegalStateException("Account changed. Try again.");
        }
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

    static void loadAvatarProfiles(Context context, ArrayList<String> ids, Callback<Map<String, PublicProfile>> callback) {
        NETWORK.execute(() -> {
            try { callback.complete(loadProfiles("", ids), null); }
            catch (Exception error) { callback.complete(null, error); }
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

    /** One RLS-scoped summary for likes, the current user's state and visible comment count.
     * Concurrent like/count consumers of the selected video share a single request. */
    static void videoSocialState(Context context, String pageUrl, Callback<VideoSocialState> callback) {
        String content = contentKey(pageUrl);
        if (content.isEmpty()) {
            NETWORK.execute(() -> callback.complete(null,
                    new IllegalArgumentException("This video has no stable URL.")));
            return;
        }
        videoSocialState(content, () -> ZeroChillSessionStore.currentUserId(context), () -> {
            String owner = ZeroChillSessionStore.currentUserId(context);
            String token = "";
            if (!owner.isEmpty()) token = ZeroChillAccountRepository.accessTokenBlocking(context);
            if (!owner.equals(ZeroChillSessionStore.currentUserId(context)))
                throw new IllegalStateException("Account changed.");
            JSONObject body = new JSONObject().put("p_content_key", content);
            Response response = request("POST", "/rest/v1/rpc/zerochill_video_social_summary",
                    token, body.toString().getBytes(StandardCharsets.UTF_8), "application/json", null);
            if (!response.ok()) throw error(response, "Unable to load video activity.");
            return parseVideoSocialSummary(response.body);
        }, NETWORK, callback);
    }

    interface SummaryLoader { VideoSocialState load() throws Exception; }

    // Injectable scheduling/transport lets tests exercise actual cache races without live accounts.
    static void videoSocialState(String content, java.util.function.Supplier<String> session,
            SummaryLoader loader, java.util.concurrent.Executor executor, Callback<VideoSocialState> callback) {
        loadVideoSummary(content, session.get(), session, loader, executor, callback, 1);
    }

    private static void loadVideoSummary(String content, String owner,
            java.util.function.Supplier<String> session, SummaryLoader loader,
            java.util.concurrent.Executor executor, Callback<VideoSocialState> callback, int retries) {
        String cacheKey = owner + "|" + content;
        final long revision;
        final String pendingKey;
        final Callback<VideoSocialState> deliver = (value, failure) -> {
            if (!owner.equals(session.get())) {
                callback.complete(null, new IllegalStateException("Account changed while loading video activity."));
                return;
            }
            // A local write may supersede a queued cache hit or an in-flight read. Re-read once,
            // coalescing consumers as usual, without creating an unbounded retry loop.
            if (failure instanceof SupersededSummaryException && retries > 0) {
                loadVideoSummary(content, owner, session, loader, executor, callback, retries - 1);
                return;
            }
            callback.complete(failure == null ? value : null, failure);
        };
        synchronized (SUMMARY_LOCK) {
            revision = summaryRevision;
            SummaryCacheEntry cached = SUMMARY_CACHE.get(cacheKey);
            if (cached != null && SystemClock.elapsedRealtime() - cached.recordedAt < SUMMARY_TTL_MS) {
                executor.execute(() -> {
                    Exception failure;
                    synchronized (SUMMARY_LOCK) {
                        failure = revision == summaryRevision ? null : new SupersededSummaryException();
                    }
                    deliver.complete(cached.value, failure);
                });
                return;
            }
            pendingKey = cacheKey + "|" + revision;
            ArrayList<Callback<VideoSocialState>> waiting = SUMMARY_PENDING.get(pendingKey);
            if (waiting != null) {
                waiting.add(deliver);
                return;
            }
            waiting = new ArrayList<>();
            waiting.add(deliver);
            SUMMARY_PENDING.put(pendingKey, waiting);
        }

        executor.execute(() -> {
            VideoSocialState value = null;
            Exception failure = null;
            try {
                if (!owner.equals(session.get())) throw new IllegalStateException("Account changed.");
                value = loader.load();
                if (!owner.equals(session.get())) throw new IllegalStateException("Account changed.");
            } catch (Exception error) {
                failure = error;
            }
            ArrayList<Callback<VideoSocialState>> callbacks;
            synchronized (SUMMARY_LOCK) {
                callbacks = SUMMARY_PENDING.remove(pendingKey);
                if (failure == null && revision != summaryRevision) failure = new SupersededSummaryException();
                if (failure == null) {
                    SUMMARY_CACHE.put(cacheKey, new SummaryCacheEntry(value, SystemClock.elapsedRealtime()));
                    while (SUMMARY_CACHE.size() > SUMMARY_MAX_ENTRIES)
                        SUMMARY_CACHE.remove(SUMMARY_CACHE.keySet().iterator().next());
                }
            }
            if (callbacks != null) {
                for (Callback<VideoSocialState> waiting : callbacks) waiting.complete(value, failure);
            }
        });
    }

    private static final class SupersededSummaryException extends IllegalStateException {
        SupersededSummaryException() { super("Video activity changed during refresh."); }
    }

    static VideoSocialState parseVideoSocialSummary(String raw) throws Exception {
        JSONArray rows = new JSONArray(raw);
        if (rows.length() != 1) throw new IllegalStateException("Video summary unavailable.");
        JSONObject row = rows.getJSONObject(0);
        if (!row.has("like_count") || !row.has("comment_count") || !row.has("liked_by_me")) {
            throw new IllegalStateException("Incomplete video summary.");
        }
        return new VideoSocialState(
                (int) Math.min(Integer.MAX_VALUE, row.getLong("like_count")),
                (int) Math.min(Integer.MAX_VALUE, row.getLong("comment_count")),
                row.getBoolean("liked_by_me"));
    }

    static void loadCommentCount(Context context, String pageUrl, Callback<Integer> callback) {
        if (BuildConfig.ZEROCHILL_V7_TRAFFIC) {
            videoSocialState(context, pageUrl,
                    (summary, error) -> callback.complete(summary == null ? null : summary.commentCount, error));
            return;
        }
        loadComments(context, pageUrl, (comments, error) -> {
            if (comments == null || error != null) {
                callback.complete(null, error);
                return;
            }
            int count = 0;
            for (Comment comment : comments) if (comment != null && !comment.deleted()) count++;
            callback.complete(count, null);
        });
    }

    static void invalidateVideoSummary() {
        synchronized (SUMMARY_LOCK) {
            summaryRevision++;
            SUMMARY_CACHE.clear();
        }
    }

    static void videoLikeState(Context context, String pageUrl, Callback<VideoLikeState> callback) {
        if (BuildConfig.ZEROCHILL_V7_TRAFFIC) {
            final String requestedOwner = ZeroChillSessionStore.currentUserId(context);
            videoSocialState(context, pageUrl, (summary, failure) -> {
                if (failure != null || summary == null) {
                    callback.complete(null, failure);
                    return;
                }
                if (!requestedOwner.equals(ZeroChillSessionStore.currentUserId(context))) {
                    callback.complete(null, new IllegalStateException("Account changed."));
                    return;
                }
                if (summary.liked) ShitTokSeenStore.get(context).heart(pageUrl, null);
                if (!requestedOwner.isEmpty())
                    SocialContentContextStore.confirmLike(context, requestedOwner, pageUrl, summary.liked);
                callback.complete(new VideoLikeState(summary.likeCount, summary.liked), null);
            });
            return;
        }
        NETWORK.execute(() -> {
            final String requestedOwner = ZeroChillSessionStore.currentUserId(context);
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
                if (liked) ShitTokSeenStore.get(context).heart(pageUrl, null);
                if (requestedOwner.equals(mine)) SocialContentContextStore.confirmLike(context, mine, pageUrl, liked);
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
        toggleVideoLike(context, pageUrl, currentlyLiked, null, callback);
    }

    static void toggleVideoLike(Context context, NativeContentItem item, boolean currentlyLiked,
            Callback<VideoLikeState> callback) {
        toggleVideoLike(context, item.url, currentlyLiked, item, callback);
    }

    private static void toggleVideoLike(Context context, String pageUrl, boolean currentlyLiked,
            NativeContentItem metadata, Callback<VideoLikeState> callback) {
        final String owner = ZeroChillSessionStore.currentUserId(context);
        NETWORK.execute(() -> {
            try {
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                String userId = ZeroChillAccountRepository.currentUserIdBlocking(context);
                requireSameAccount(context, owner);
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
                if (BuildConfig.ZEROCHILL_V7_TRAFFIC) invalidateVideoSummary();
                if(!currentlyLiked) AnalyticsTracker.event(context,"likes");
                if (!currentlyLiked) ShitTokSeenStore.get(context).heart(pageUrl, null);
                if (metadata != null && userId.equals(ZeroChillSessionStore.currentUserId(context)))
                    SocialContentContextStore.remember(context, metadata);
                SocialContentContextStore.confirmLike(context, userId, pageUrl, !currentlyLiked);
                requireSameAccount(context, owner);
                videoLikeState(context, pageUrl, (state, failure) -> {
                    if (!owner.equals(ZeroChillSessionStore.currentUserId(context))) {
                        callback.complete(null, new IllegalStateException("Account changed."));
                        return;
                    }
                    if (BuildConfig.ZEROCHILL_V7_TRAFFIC) {
                        callback.complete(confirmedVideoLikeWrite(currentlyLiked, failure == null ? state : null), null);
                    } else callback.complete(state, failure);
                });
            } catch (Exception error) {
                callback.complete(null, error);
            }
        });
    }

    /** Explicit Library action only. Existing table and account ownership, no new backend. */
    static void loadLikedVideoPage(Context context, int offset, Callback<SocialContentContextStore.LikedPage> callback) {
        final String owner = ZeroChillSessionStore.currentUserId(context);
        NETWORK.execute(() -> {
            try {
                if (owner.isEmpty()) throw new IllegalStateException("Sign in to view liked videos.");
                String token = ZeroChillAccountRepository.accessTokenBlocking(context);
                if (!owner.equals(ZeroChillSessionStore.currentUserId(context)))
                    throw new IllegalStateException("Account changed while loading likes.");
                Response response = request("GET", "/rest/v1/video_likes?select=content_key,created_at&user_id=eq."
                        + encode(owner) + "&order=created_at.desc,content_key.asc&limit=100&offset=" + Math.max(0, offset),
                        token, null, null, null);
                if (!response.ok()) throw error(response, "Unable to load liked videos.");
                JSONArray rows = response.body.isEmpty() ? new JSONArray() : new JSONArray(response.body);
                callback.complete(SocialContentContextStore.cacheLikedPage(context, owner, rows, offset), null);
            } catch (Exception error) { callback.complete(null, error); }
        });
    }

    /** Small own-account batch against existing publicly readable likes. No auth refresh on startup. */
    static Set<String> shittokLikedKeysBlocking(Context context, Collection<String> keys) throws Exception {
        if (keys.isEmpty() || !ZeroChillAccountRepository.hasStoredSession(context)) return Collections.emptySet();
        String user = ZeroChillSessionStore.currentUserId(context);
        if (user.isEmpty()) throw new IllegalStateException("Like lookup has no current account.");
        Response response = request("GET",
                "/rest/v1/video_likes?select=content_key&user_id=eq." + encode(user)
                        + "&content_key=in.(" + String.join(",", keys) + ")&limit=100", "", null, null, null, 900);
        if (!response.ok()) throw error(response, "Like lookup is unavailable.");
        if (!user.equals(ZeroChillSessionStore.currentUserId(context)))
            throw new IllegalStateException("Account changed during like lookup.");
        JSONArray rows = new JSONArray(response.body);
        Set<String> liked = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) liked.add(rows.getJSONObject(i).getString("content_key"));
        return liked;
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
        return request(method, path, token, body, contentType, prefer, 0);
    }

    private static Response request(String method, String path, String token, byte[] body,
                                    String contentType, String prefer, int timeoutMs) throws Exception {
        if (!ZeroChillAccountRepository.isConfigured()) {
            throw new IllegalStateException("ZeroChill social features are not configured in this build.");
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(
                    BuildConfig.ACCOUNT_SUPABASE_URL + path
            ).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(timeoutMs > 0 ? timeoutMs : 12_000);
            connection.setReadTimeout(timeoutMs > 0 ? timeoutMs : 20_000);
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
            if(status>=400 && status!=409 && !path.contains("reaction")) AnalyticsTracker.backendFailure(status);
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            return new Response(status, read(stream));
        } catch(java.io.IOException error) {
            if(!path.contains("reaction")) AnalyticsTracker.backendFailure(0);
            throw error;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static final class BackendHttpException extends IllegalStateException {
        final int status;
        BackendHttpException(int status, String message) { super(message); this.status = status; }
    }

    static boolean isPermanentHttpError(Exception error) {
        if (!(error instanceof BackendHttpException)) return false;
        int status = ((BackendHttpException) error).status;
        return status == 401 || status == 403 || status == 404;
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
        return new BackendHttpException(response.status, message);
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
