package com.webapp.crazyshit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Experimental album discovery adapter for Bunkr Index.
 *
 * The index's public search API currently returns file hits with album metadata.
 * Group these hits by album before handing them to the existing Bunkr gallery.
 * This class never requests media or changes Bunkr playback/signing.
 */
final class BunkrIndexSearch {
    private static final String SEARCH_ENDPOINT = "https://bunkrsearch.link/api/search";
    private static final String ALBUM_ORIGIN = "https://bunkr.cr";
    private static final Pattern ALBUM_SLUG = Pattern.compile("[A-Za-z0-9_-]{2,128}");
    private static final int MAX_HITS_PER_PAGE = 100;

    private BunkrIndexSearch() {}

    static final class Page {
        final List<NativeContentItem> albums;
        final int page;
        final int pages;

        Page(List<NativeContentItem> albums, int page, int pages) {
            this.albums = albums;
            this.page = page;
            this.pages = pages;
        }

        boolean hasNext() { return pages > page; }
    }

    static List<NativeContentItem> search(String query, int page) throws IOException {
        return searchPage(query, page).albums;
    }

    static Page searchPage(String query, int page) throws IOException {
        String request = searchUrl(query, page);
        Connection.Response response = Jsoup.connect(request)
                .userAgent(BunkrRepository.userAgent())
                .timeout(BunkrRepository.requestTimeoutMs())
                .maxBodySize(512 * 1024)
                .ignoreContentType(true)
                .followRedirects(true)
                .ignoreHttpErrors(false)
                .header("Accept", "application/json")
                .execute();
        if (response.statusCode() != 200) {
            throw new IOException("Bunkr Index search returned HTTP " + response.statusCode());
        }
        String body = response.readBody();
        int current = Math.max(1, page);
        int pages = -1;
        try {
            JSONObject root = new JSONObject(body);
            current = Math.max(1, root.optInt("page", current));
            if (root.has("pages")) pages = root.optInt("pages");
        } catch (JSONException ignored) { }
        return new Page(parseResults(body), current, pages);
    }

    static List<NativeContentItem> parseResponse(Connection.Response response) throws IOException {
        // Keep interrupted reads in searchAlbums' existing checked fallback path.
        return parseResults(response.readBody());
    }

    static String searchUrl(String query, int page) {
        String safeQuery = query == null ? "" : query.trim();
        String encoded;
        try {
            encoded = URLEncoder.encode(safeQuery, "UTF-8");
        } catch (UnsupportedEncodingException ignored) {
            encoded = safeQuery;
        }
        return SEARCH_ENDPOINT + "?q=" + encoded + "&page=" + Math.max(1, page);
    }

    static List<NativeContentItem> parseResults(String body) throws IOException {
        final JSONObject root;
        try {
            root = new JSONObject(body);
        } catch (JSONException error) {
            throw new IOException("Bunkr Index did not return JSON", error);
        }
        JSONArray hits = root.optJSONArray("results");
        if (hits == null || !root.has("page")) {
            throw new IOException("Bunkr Index search response has an unexpected format");
        }

        LinkedHashMap<String, NativeContentItem> albums = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(hits.length(), MAX_HITS_PER_PAGE); i++) {
            JSONObject hit = hits.optJSONObject(i);
            if (hit == null) continue;
            String slug = hit.optString("album_slug", "").trim();
            if (!ALBUM_SLUG.matcher(slug).matches()) continue;

            String albumUrl = ALBUM_ORIGIN + "/a/" + slug;
            if (!BunkrRepository.isAlbumUrl(albumUrl)) continue;

            String title = hit.optString("album_name", "").trim();
            if (title.isEmpty() || "null".equalsIgnoreCase(title)) title = "Bunkr album " + slug;
            String artwork = hit.optString("thumbnail", "").trim();
            if (!artwork.startsWith("https://") || !BunkrRepository.isUsableArtwork(artwork)) {
                artwork = "";
            }
            int files = Math.max(0, hit.optInt("file_count", 0));
            String description = files > 0 ? files + " files" : "Bunkr album";
            albums.putIfAbsent(albumUrl, new NativeContentItem(
                    NativeContentItem.KIND_SERIES,
                    title,
                    albumUrl,
                    artwork,
                    "",
                    "Bunkr",
                    "",
                    description
            ));
        }
        return new ArrayList<>(albums.values());
    }
}
