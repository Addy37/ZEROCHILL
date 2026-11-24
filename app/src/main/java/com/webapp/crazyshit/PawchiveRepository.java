package com.webapp.crazyshit;

import android.util.JsonReader;
import android.util.JsonToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pawchive public, read-only metadata API. Deliberately independent of OnlyHaven
 * and ShitTok. No requests are made until a caller explicitly invokes a method.
 */
final class PawchiveRepository {
    static final String ORIGIN = "https://pawchive.pw/";
    private static final String BASE = ORIGIN + "api/v1";
    private static final String IDENTITY_BASE = "https://file.pawchive.pw/data";
    private static final String THUMBNAIL_BASE = "https://img.pawchive.pw/thumbnail/data";
    private static final Pattern SHARD = Pattern.compile("https://n[1-9][0-9]{0,2}[.]pawchive[.]pw");
    private static final Pattern PROFILE = Pattern.compile(
            "^https://pawchive[.]pw/([a-z][a-z0-9_-]{0,31})/user/([A-Za-z0-9_-]{1,160})/?$");
    private static final Pattern DATA_PATH = Pattern.compile("/[A-Za-z0-9_./-]{1,1024}");
    private static final Pattern SERVICE = Pattern.compile("[a-z][a-z0-9_-]{0,31}");
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,160}");
    private static final Pattern IMAGE = Pattern.compile(".*[.](jpg|jpeg|png|gif|webp|avif)");
    private static final Pattern VIDEO = Pattern.compile(".*[.](mp4|webm|m4v|mov|mkv)");
    private static final Comparator<Creator> POPULARITY = Comparator
            .comparingLong((Creator creator) -> creator.favorites).reversed().thenComparing(Creator::key);
    private static final int LEGACY_LOOKUP_PAGES = 3;
    private static final int MAX_FIRST_PAGES = 8;
    private static final long FIRST_PAGE_TTL_MS = 120_000L;
    static final int PAGE_SIZE = 50;
    private static final int MAX_JSON_BYTES = 16 * 1024 * 1024;
    private static final int TIMEOUT_MS = 10000;

    interface Transport {
        String get(String route) throws IOException;
    }

    private final Transport transport;
    private final boolean useSharedCache;

    PawchiveRepository() {
        this.transport = PawchiveRepository::request;
        this.useSharedCache = true;
    }

    PawchiveRepository(Transport transport) {
        this.transport = transport;
        this.useSharedCache = false;
    }

    static final class Creator {
        final String service, id, name, searchName;
        final long favorites;

        Creator(String service, String id, String name, long favorites) {
            this.service = service;
            this.id = id;
            this.name = name;
            this.searchName = name.toLowerCase(Locale.ROOT);
            this.favorites = favorites;
        }

        String key() { return service + "/" + id; }
        String profileUrl() { return "https://pawchive.pw" + creatorRoute(service, id); }
    }

    static final class Post {
        final String service, creatorId, id, title;
        List<Media> listingMedia = Collections.emptyList();
        boolean awaitingDetails;

        Post(String service, String creatorId, String id, String title) {
            this.service = service;
            this.creatorId = creatorId;
            this.id = id;
            this.title = title;
        }

        String key() { return service + "/" + creatorId + "/" + id; }
        String postUrl() {
            return "https://pawchive.pw" + creatorRoute(service, creatorId) + "/post/" + id;
        }
    }

    static final class Media {
        final String name, url, originalUrl, thumbnailUrl;
        final boolean video;

        Media(String name, String path, String server, boolean video) {
            this.name = name;
            this.url = IDENTITY_BASE + path;
            this.originalUrl = SHARD.matcher(server).matches() ? server + "/data" + path : "";
            this.thumbnailUrl = video ? "" : THUMBNAIL_BASE + path;
            this.video = video;
        }
    }

    List<Media> postMedia(Post post) throws IOException {
        if (post == null || !validId(post.id)) throw new IllegalArgumentException("Invalid post");
        String route = creatorRoute(post.service, post.creatorId) + "/post/" + post.id;
        try {
            JSONObject body = new JSONObject(transport.get(route));
            // Some public clients return the post directly; accept the explicit envelope too.
            JSONObject row = body.optJSONObject("post");
            if (row == null) row = body;
            if (!post.service.equals(value(row, "service")) ||
                    !post.creatorId.equals(value(row, "user")) ||
                    !post.id.equals(value(row, "id"))) throw new IOException("Post identity mismatch");
            return parseMedia(row);
        } catch (JSONException | NullPointerException error) {
            throw new IOException("Invalid Pawchive post detail", error);
        }
    }

    static List<Media> parseMedia(JSONObject row) {
        Map<String, Media> result = new LinkedHashMap<>();
        addMedia(row.optJSONObject("file"), result);
        JSONArray attachments = row.optJSONArray("attachments");
        if (attachments != null) {
            for (int i = 0; i < attachments.length(); i++) addMedia(attachments.optJSONObject(i), result);
        }
        return Collections.unmodifiableList(new ArrayList<>(result.values()));
    }

    private static void addMedia(JSONObject file, Map<String, Media> result) {
        if (file == null || result.size() >= 100) return;
        String path = value(file, "path");
        if (path.startsWith("/data/")) path = path.substring("/data".length());
        if (!validMediaPath(path)) return;
        String server = value(file, "server").replaceFirst("/$", "");
        String name = value(file, "name");
        String lower = path.toLowerCase(Locale.ROOT);
        boolean video = VIDEO.matcher(lower).matches();
        if (!video && !IMAGE.matcher(lower).matches()) return;
        if (video && !SHARD.matcher(server).matches()) return;
        Media media = new Media(name.isEmpty() ? path.substring(path.lastIndexOf('/') + 1) : name,
                path, server, video);
        Media previous = result.get(media.url);
        if (previous == null || previous.originalUrl.isEmpty()) result.put(media.url, media);
    }

    private static boolean validMediaPath(String path) {
        return DATA_PATH.matcher(path).matches() && !path.contains("..") && !path.contains("//");
    }

    static final class PostPage {
        final List<Post> posts;
        final boolean hasMore;

        PostPage(List<Post> posts, boolean hasMore) {
            this.posts = Collections.unmodifiableList(new ArrayList<>(posts));
            this.hasMore = hasMore;
        }
    }

    private static List<Creator> cachedCreators;
    private static long cachedAt;
    private List<Creator> browseCatalog() throws IOException {
        if (!useSharedCache) {
            List<Creator> creators = new ArrayList<>(listCreators());
            creators.sort(POPULARITY);
            return creators;
        }
        synchronized (PawchiveRepository.class) {
            long now = android.os.SystemClock.elapsedRealtime();
            if (cachedCreators != null && now - cachedAt < 300_000L) return cachedCreators;
            List<Creator> creators = new ArrayList<>(listCreators());
            creators.sort(POPULARITY);
            cachedCreators = Collections.unmodifiableList(creators);
            cachedAt = now;
            return cachedCreators;
        }
    }

    OnlyFapCategoryLoader.Batch shelf(String query, int offset, int limit) throws IOException {
        List<Creator> matches = filterShelf(browseCatalog(), query, offset, limit);
        List<NativeContentItem> items = new ArrayList<>();
        for (Creator creator : matches) items.add(creatorItem(creator));
        return new OnlyFapCategoryLoader.Batch(items, matches.size() == limit);
    }

    static NativeContentItem creatorItem(Creator creator) {
        String profile = creator.profileUrl();
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, creator.name, profile,
                "https://pawchive.pw/icons/" + creator.service + "/" + creator.id,
                "", profile, "", "Pawchive", "pawchive:" + creator.key());
    }

    static Creator profile(String url, String name) {
        if (url == null) return null;
        java.util.regex.Matcher m = PROFILE.matcher(url);
        if (!m.matches()) return null;
        return new Creator(m.group(1), m.group(2), name == null ? "" : name, 0);
    }

    static boolean isProfile(String url) { return profile(url, "") != null; }

    /** Beta 2 attachment identities remain the keys used by favorites, downloads and sessions. */
    static boolean isDirectMedia(String url) {
        if (url == null || !url.startsWith(IDENTITY_BASE + "/")) return false;
        String path = url.substring(IDENTITY_BASE.length());
        String lower = path.toLowerCase(Locale.ROOT);
        return validMediaPath(path) && (IMAGE.matcher(lower).matches() || VIDEO.matcher(lower).matches());
    }

    static boolean isDeliveryFor(String identity, String url) {
        if (!isDirectMedia(identity) || url == null) return false;
        String tail = identity.substring(IDENTITY_BASE.length());
        String route = "/data" + tail;
        return url.endsWith(route) && SHARD.matcher(url.substring(0, url.length() - route.length())).matches();
    }

    static boolean isThumbnail(String url) {
        return url != null && url.startsWith(THUMBNAIL_BASE + "/");
    }

    static boolean isArtwork(String url) {
        return isThumbnail(url) || (url != null && url.startsWith(ORIGIN + "icons/"));
    }

    static String savedArtwork(String kind, String url, String artwork) {
        if (NativeContentItem.KIND_MEDIA.equals(kind) && isDirectMedia(url) && isThumbnail(artwork))
            return ""; // Older cards used the adjacent photo as their video poster.
        if (NativeContentItem.KIND_IMAGE.equals(kind) && isDirectMedia(url) && !isArtwork(artwork))
            return THUMBNAIL_BASE + url.substring(IDENTITY_BASE.length());
        return artwork;
    }

    static String original(NativeContentItem item) {
        return isDeliveryFor(item.url, item.sourceMediaUrl) ? item.sourceMediaUrl : "";
    }

    static String pageUrl(NativeContentItem item) {
        if (!isDirectMedia(item.url)) return item.url;
        Creator creator = profile(item.uploader, item.searchQuery);
        if (creator == null) return ORIGIN;
        String postBase = creator.profileUrl() + "/post/";
        if (item.sourcePageUrl.startsWith(postBase)
                && validId(item.sourcePageUrl.substring(postBase.length()))) return item.sourcePageUrl;
        return creator.profileUrl();
    }

    CrazyShitRepository.StreamInfo resolve(NativeContentItem item) throws IOException {
        if (!isDirectMedia(item.url)) return null;
        String url = original(item);
        if (!url.isEmpty())
            return new CrazyShitRepository.StreamInfo(url, pageUrl(item), item.title, ORIGIN);

        // Old beta records lack the shard. Look up the exact attachment within its creator.
        Creator creator = profile(item.uploader, item.searchQuery);
        if (creator == null) return null;
        for (int page = 0; page < LEGACY_LOOKUP_PAGES && !Thread.currentThread().isInterrupted(); page++) {
            PostPage posts = galleryPosts(creator, page * PAGE_SIZE);
            for (Post post : posts.posts) {
                for (Media media : post.listingMedia) {
                    if (item.url.equals(media.url) && !media.originalUrl.isEmpty())
                        return new CrazyShitRepository.StreamInfo(media.originalUrl, post.postUrl(), item.title, ORIGIN);
                }
            }
            if (!posts.hasMore) break;
        }
        return null;
    }

    static final class GalleryPage {
        final ArrayList<NativeContentItem> items;
        final boolean hasMore;
        GalleryPage(ArrayList<NativeContentItem> items, boolean hasMore) {
            this.items = items;
            this.hasMore = hasMore;
        }
    }

    private static final class RecentPage {
        final PostPage page;
        final long timestamp = android.os.SystemClock.elapsedRealtime();

        RecentPage(PostPage page) { this.page = page; }
    }

    private static final Map<String, RecentPage> FIRST_PAGES = new LinkedHashMap<>(16, .75f, true);

    private PostPage galleryPosts(Creator creator, int offset) throws IOException {
        if (!useSharedCache || offset != 0) return creatorPosts(creator.service, creator.id, null, offset);
        synchronized (FIRST_PAGES) {
            RecentPage cached = FIRST_PAGES.get(creator.key());
            if (cached != null && android.os.SystemClock.elapsedRealtime() - cached.timestamp < FIRST_PAGE_TTL_MS)
                return cached.page;
        }
        PostPage page = creatorPosts(creator.service, creator.id, null, 0);
        synchronized (FIRST_PAGES) {
            FIRST_PAGES.put(creator.key(), new RecentPage(page));
            if (FIRST_PAGES.size() > MAX_FIRST_PAGES) FIRST_PAGES.remove(FIRST_PAGES.keySet().iterator().next());
        }
        return page;
    }

    GalleryPage galleryPage(Creator creator, int offset) throws IOException {
        return galleryPage(creator, offset, true);
    }

    GalleryPage galleryPage(Creator creator, int offset, boolean loadMissingDetails) throws IOException {
        PostPage page = galleryPosts(creator, offset);
        Map<String, NativeContentItem> items = new LinkedHashMap<>();
        int attempted = 0;
        IOException detailFailure = null;
        for (Post post : page.posts) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Pawchive request canceled");
            List<Media> assets = post.listingMedia;
            if (loadMissingDetails && assets.isEmpty() && post.awaitingDetails && attempted++ < 2) {
                try {
                    assets = postMedia(post);
                } catch (IOException error) {
                    detailFailure = error;
                }
            }
            for (Media media : assets) {
                String title = post.title.isEmpty() ? media.name : post.title;
                NativeContentItem item = new NativeContentItem(
                        media.video ? NativeContentItem.KIND_MEDIA : NativeContentItem.KIND_IMAGE,
                        title, media.url, media.thumbnailUrl, "", creator.profileUrl(),
                        "", "OnlyFap · Pawchive", creator.name).withSourceMedia(media.originalUrl, post.postUrl());
                items.putIfAbsent(item.url, item);
            }
        }
        if (items.isEmpty() && detailFailure != null) throw detailFailure;
        return new GalleryPage(new ArrayList<>(items.values()), page.hasMore);
    }

    String creatorArtwork(Creator creator) throws IOException {
        for (NativeContentItem item : galleryPage(creator, 0, false).items) {
            if (item.isImage()) return item.imageUrl;
        }
        return "";
    }

    List<Creator> listCreators() throws IOException {
        return parseCreators(transport.get("/creators"));
    }

    PostPage recentPosts(String query, int offset) throws IOException {
        return parsePosts(transport.get(pageRoute("/posts", query, offset)), null, null);
    }

    PostPage creatorPosts(String service, String creatorId, String query, int offset)
            throws IOException {
        String route = creatorRoute(service, creatorId);
        return parsePosts(transport.get(pageRoute(route, query, offset)), service, creatorId);
    }

    static String creatorRoute(String service, String id) {
        if (!validService(service) || !validId(id)) {
            throw new IllegalArgumentException("Invalid Pawchive creator identity");
        }
        return "/" + service + "/user/" + id;
    }

    private static boolean validService(String value) {
        return value != null && SERVICE.matcher(value).matches();
    }

    private static boolean validId(String value) {
        return value != null && ID.matcher(value).matches();
    }

    static String pageRoute(String route, String query, int offset) {
        if (offset < 0 || offset % PAGE_SIZE != 0) {
            throw new IllegalArgumentException("Pawchive offsets must be multiples of 50");
        }
        String value = query == null ? "" : query.trim();
        if (!value.isEmpty() && (value.length() < 3 || value.length() > 200)) {
            throw new IllegalArgumentException("Pawchive search requires 3 to 200 characters");
        }
        try {
            return route + "?o=" + offset + (value.isEmpty() ? "" :
                    "&q=" + URLEncoder.encode(value, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }

    static List<Creator> parseCreators(String body) throws IOException {
        if (body == null) throw new IOException("Invalid Pawchive creator response");
        Map<String, Creator> unique = new LinkedHashMap<>();
        boolean hadRows;
        try (JsonReader reader = new JsonReader(new StringReader(body))) {
            reader.beginArray();
            hadRows = reader.hasNext();
            while (reader.hasNext()) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Pawchive request canceled");
                if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                    reader.skipValue();
                    continue;
                }
                String service = "", id = "", name = "";
                long favorites = 0;
                reader.beginObject();
                while (reader.hasNext()) {
                    String field = reader.nextName();
                    JsonToken token = reader.peek();
                    if (token != JsonToken.STRING && token != JsonToken.NUMBER) {
                        reader.skipValue();
                        continue;
                    }
                    switch (field) {
                        case "service": service = reader.nextString().trim(); break;
                        case "id": id = reader.nextString().trim(); break;
                        case "name": name = reader.nextString().trim(); break;
                        case "favorited":
                            try { favorites = Math.max(0L, Long.parseLong(reader.nextString())); }
                            catch (NumberFormatException ignored) { }
                            break;
                        default: reader.skipValue();
                    }
                }
                reader.endObject();
                if (!validService(service) || !validId(id) || name.isEmpty()) continue;
                Creator creator = new Creator(service, id, name, favorites);
                unique.putIfAbsent(creator.key(), creator);
            }
            reader.endArray();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing Pawchive catalog data");
        } catch (IllegalStateException error) {
            throw new IOException("Invalid Pawchive catalog", error);
        }
        if (hadRows && unique.isEmpty()) throw new IOException("Invalid Pawchive creator response");
        return Collections.unmodifiableList(new ArrayList<>(unique.values()));
    }

    static List<Creator> creatorShelf(List<Creator> source, String query, int offset, int limit) {
        List<Creator> ranked = new ArrayList<>(source);
        ranked.sort(POPULARITY);
        return filterShelf(ranked, query, offset, limit);
    }

    private static List<Creator> filterShelf(List<Creator> ranked, String query, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > PAGE_SIZE) {
            throw new IllegalArgumentException("Invalid Pawchive creator shelf");
        }
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Creator> matching = new ArrayList<>(limit);
        int skipped = 0;
        for (Creator creator : ranked) {
            if (!creator.searchName.contains(needle) && !creator.id.equals(needle)) continue;
            if (skipped < offset) {
                skipped++;
                continue;
            }
            matching.add(creator);
            if (matching.size() == limit) break;
        }
        return Collections.unmodifiableList(matching);
    }

    static PostPage parsePosts(String body, String expectedService, String expectedCreator)
            throws IOException {
        JSONArray rows = array(body);
        Map<String, Post> unique = new LinkedHashMap<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            String service = value(row, "service");
            String creator = value(row, "user");
            String id = value(row, "id");
            if (!validService(service) || !validId(creator) || !validId(id)) continue;
            if (expectedService != null && !expectedService.equals(service)) continue;
            if (expectedCreator != null && !expectedCreator.equals(creator)) continue;
            Post post = new Post(service, creator, id, value(row, "title"));
            post.listingMedia = parseMedia(row);
            post.awaitingDetails = row.optBoolean("has_full", false) && value(row, "preview_state").equals("pending");
            if (!unique.containsKey(post.key())) unique.put(post.key(), post);
        }
        if (rows.length() > 0 && unique.isEmpty()) throw new IOException("Invalid Pawchive post response");
        return new PostPage(new ArrayList<>(unique.values()), rows.length() >= PAGE_SIZE);
    }

    private static String value(JSONObject row, String key) {
        return row.isNull(key) ? "" : row.optString(key, "").trim();
    }

    private static JSONArray array(String body) throws IOException {
        try {
            return new JSONArray(body);
        } catch (JSONException | NullPointerException error) {
            throw new IOException("Invalid Pawchive JSON", error);
        }
    }

    private static String request(String route) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE + route).openConnection();
        try {
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("Pawchive HTTP " + status);
            try (InputStream in = connection.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Pawchive request canceled");
                    if (count > MAX_JSON_BYTES - out.size()) throw new IOException("Pawchive response too large");
                    out.write(buffer, 0, count);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }
}
