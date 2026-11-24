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

    static final class PostPage {
        final List<Post> posts;
        final boolean hasMore;

        PostPage(List<Post> posts, boolean hasMore) {
            this.posts = Collections.unmodifiableList(new ArrayList<>(posts));
            this.hasMore = hasMore;
        }
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
