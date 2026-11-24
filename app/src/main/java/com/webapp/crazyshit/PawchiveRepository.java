package com.webapp.crazyshit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
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
    private static final String BASE = "https://pawchive.pw/api/v1";
    static final int PAGE_SIZE = 50;
    private static final int MAX_JSON_BYTES = 16 * 1024 * 1024;
    private static final int TIMEOUT_MS = 10000;

    interface Transport {
        String get(String route) throws IOException;
    }

    private final Transport transport;

    PawchiveRepository() {
        this(PawchiveRepository::request);
    }

    PawchiveRepository(Transport transport) {
        this.transport = transport;
    }

    static final class Creator {
        final String service, id, name;
        final long favorites;

        Creator(String service, String id, String name, long favorites) {
            this.service = service;
            this.id = id;
            this.name = name;
            this.favorites = favorites;
        }

        String key() { return service + "/" + id; }
        String profileUrl() { return "https://pawchive.pw" + creatorRoute(service, id); }
    }

    static final class Post {
        final String service, creatorId, id, title;
        List<Media> listingMedia = Collections.emptyList();

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
        final String name, url;
        final boolean video;
        Media(String name, String url, boolean video) {
            this.name = name; this.url = url; this.video = video;
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
        // Accept only provider-relative data paths; never follow arbitrary links or executable attachments.
        if (!path.matches("/[A-Za-z0-9_./-]{1,1024}") || path.contains("..") || path.contains("//")) return;
        String name = value(file, "name");
        String lower = path.toLowerCase(Locale.ROOT);
        boolean video = lower.matches(".*[.](mp4|webm|m4v|mov|mkv)");
        boolean image = lower.matches(".*[.](jpg|jpeg|png|gif|webp|avif)");
        if (!video && !image) return;
        String url = "https://file.pawchive.pw/data" + path;
        if (!result.containsKey(url)) result.put(url, new Media(name.isEmpty() ? path.substring(path.lastIndexOf('/') + 1) : name, url, video));
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
        synchronized (PawchiveRepository.class) {
            long now = android.os.SystemClock.elapsedRealtime();
            if (cachedCreators != null && now - cachedAt < 300_000L) return cachedCreators;
            List<Creator> creators = listCreators();
            cachedCreators = creators;
            cachedAt = now;
            return creators;
        }
    }

    OnlyFapCategoryLoader.Batch shelf(String query, int offset, int limit) throws IOException {
        List<Creator> matches = creatorShelf(browseCatalog(), query, offset, limit);
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
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "^https://pawchive[.]pw/([a-z][a-z0-9_-]{0,31})/user/([A-Za-z0-9_-]{1,160})/?$").matcher(url);
        if (!m.matches()) return null;
        return new Creator(m.group(1), m.group(2), name == null ? "" : name, 0);
    }

    static boolean isProfile(String url) { return profile(url, "") != null; }

    static boolean isDirectMedia(String url) {
        if (url == null || !url.startsWith("https://file.pawchive.pw/data/")) return false;
        String path = url.substring("https://file.pawchive.pw/data".length());
        return path.matches("/[A-Za-z0-9_./-]{1,1024}") && !path.contains("..") && !path.contains("//")
                && path.toLowerCase(Locale.ROOT).matches(".*[.](jpg|jpeg|png|gif|webp|avif|mp4|webm|m4v|mov|mkv)");
    }

    static final class GalleryPage {
        final ArrayList<NativeContentItem> items;
        final boolean hasMore;
        GalleryPage(ArrayList<NativeContentItem> items, boolean hasMore) {
            this.items = items; this.hasMore = hasMore;
        }
    }

    GalleryPage galleryPage(Creator creator, int offset) throws IOException {
        PostPage page = creatorPosts(creator.service, creator.id, null, offset);
        Map<String, NativeContentItem> items = new LinkedHashMap<>();
        for (Post post : page.posts) {
            for (Media media : post.listingMedia) {
                if (!isDirectMedia(media.url)) continue;
                String title = post.title.isEmpty() ? media.name : post.title;
                NativeContentItem item = new NativeContentItem(
                        media.video ? NativeContentItem.KIND_MEDIA : NativeContentItem.KIND_IMAGE,
                        title, media.url, media.video ? "" : media.url, "", creator.profileUrl(),
                        "", "OnlyFap · Pawchive", creator.name);
                items.putIfAbsent(item.url, item);
            }
        }
        return new GalleryPage(new ArrayList<>(items.values()), page.hasMore);
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
        return value != null && value.matches("[a-z][a-z0-9_-]{0,31}");
    }

    private static boolean validId(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,160}");
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
        JSONArray rows = array(body);
        Map<String, Creator> unique = new LinkedHashMap<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            String service = value(row, "service");
            String id = value(row, "id");
            String name = value(row, "name");
            if (!validService(service) || !validId(id) || name.isEmpty()) continue;
            Creator creator = new Creator(service, id, name, Math.max(0L, row.optLong("favorited", 0)));
            if (!unique.containsKey(creator.key())) unique.put(creator.key(), creator);
        }
        if (rows.length() > 0 && unique.isEmpty()) throw new IOException("Invalid Pawchive creator response");
        return Collections.unmodifiableList(new ArrayList<>(unique.values()));
    }

    static List<Creator> creatorShelf(List<Creator> source, String query, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > PAGE_SIZE) {
            throw new IllegalArgumentException("Invalid Pawchive creator shelf");
        }
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Creator> matching = new ArrayList<>();
        for (Creator creator : source) {
            if (needle.isEmpty() || creator.name.toLowerCase(Locale.ROOT).contains(needle)
                    || creator.id.toLowerCase(Locale.ROOT).equals(needle)) matching.add(creator);
        }
        matching.sort((a, b) -> {
            int popularity = Long.compare(b.favorites, a.favorites);
            return popularity != 0 ? popularity : a.key().compareTo(b.key());
        });
        int start = Math.min(offset, matching.size());
        int end = start + Math.min(limit, matching.size() - start);
        return Collections.unmodifiableList(new ArrayList<>(matching.subList(start, end)));
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
