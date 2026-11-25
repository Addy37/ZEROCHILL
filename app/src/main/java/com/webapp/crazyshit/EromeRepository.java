package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.CookieManager;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Native Erome album search and album-media metadata.
 * Requests stay on Erome. Cookies are read and written only for this host.
 */
public final class EromeRepository {
    public static final String ORIGIN = "https://www.erome.com";
    static final int SEARCH_PAGE_SIZE = 36;
    private static final int MAX_PAGE = 20;
    private static final int MAX_ALBUM_PAGES = 5;
    private static final int TIMEOUT_MS = 12_000;
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    public static final class SearchPage {
        public final List<NativeContentItem> items;
        public final boolean hasNext;

        public SearchPage(List<NativeContentItem> items, boolean hasNext) {
            this.items = items == null ? new ArrayList<>() : items;
            this.hasNext = hasNext;
        }

        static SearchPage empty() {
            return new SearchPage(new ArrayList<>(), false);
        }
    }

    public SearchPage search(Context context, String query, int page) throws IOException {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() < 2 || trimmed.length() > 100) return SearchPage.empty();
        int safePage = Math.max(1, Math.min(page, MAX_PAGE));
        preparePreference(context);
        Document document = get(context, searchUrl(trimmed, safePage));
        EromePages.SearchPage parsed = EromePages.parseSearch(document);
        return new SearchPage(parsed.items, parsed.hasNext);
    }

    public List<NativeContentItem> fetchAlbum(Context context, String albumUrl) throws IOException {
        String canonical = canonicalAlbum(albumUrl);
        if (canonical.isEmpty()) throw new IOException("Not an Erome album URL");
        preparePreference(context);
        LinkedHashMap<String, NativeContentItem> media = new LinkedHashMap<>();
        String next = canonical;
        for (int page = 0; page < MAX_ALBUM_PAGES && !next.isEmpty() && media.size() < EromePages.MAX_ALBUM_MEDIA; page++) {
            Document document;
            try {
                document = get(context, next);
            } catch (IOException error) {
                if (media.isEmpty()) throw error;
                break;
            }
            EromePages.AlbumPage parsed = EromePages.parseAlbum(document, canonical);
            for (NativeContentItem item : parsed.items) {
                if (item != null && item.url != null && !item.url.isEmpty()) media.putIfAbsent(item.url, item);
            }
            String following = sameAlbumPage(canonical, parsed.nextUrl);
            next = following.isEmpty() || following.equals(next) ? "" : following;
            if (parsed.items.isEmpty()) break;
        }
        return new ArrayList<>(media.values());
    }

    public CrazyShitRepository.StreamInfo resolvePlayable(Context context, String pageUrl) throws IOException {
        if (isDirectVideo(pageUrl)) {
            return new CrazyShitRepository.StreamInfo(pageUrl, ORIGIN + "/", "Video", ORIGIN + "/");
        }
        String album = canonicalAlbum(pageUrl);
        String token = fragmentToken(pageUrl);
        if (album.isEmpty() || token.isEmpty() || token.startsWith("img-")) {
            throw new IOException("Not an Erome video");
        }
        preparePreference(context);
        Document document = get(context, album);
        String media = EromePages.videoSource(document, album, token);
        if (!isDirectVideo(media)) throw new IOException("Erome video was not available");
        return new CrazyShitRepository.StreamInfo(media, album, "Video", album);
    }

    public static String searchUrl(String query, int page) {
        String trimmed = query == null ? "" : query.trim();
        String encoded;
        try {
            encoded = URLEncoder.encode(trimmed, "UTF-8");
        } catch (Exception ignored) {
            encoded = trimmed;
        }
        int safePage = Math.max(1, Math.min(page, MAX_PAGE));
        String url = ORIGIN + "/search?q=" + encoded;
        return safePage <= 1 ? url : url + "&page=" + safePage;
    }

    public static boolean isAlbumUrl(String url) {
        return isEromeHttpUrl(url) && EromePages.isAlbumPath(pathOf(url));
    }

    public static boolean isEromeHttpUrl(String url) {
        if (url == null) return false;
        Uri uri;
        try {
            uri = Uri.parse(url.trim());
        } catch (Exception ignored) {
            return false;
        }
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        if (uri.getUserInfo() != null && !uri.getUserInfo().isEmpty()) return false;
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.US);
        return "erome.com".equals(host) || host.endsWith(".erome.com");
    }

    public static boolean isDirectVideo(String url) {
        if (!isEromeHttpUrl(url)) return false;
        String path = pathOf(url).toLowerCase(Locale.US);
        return path.endsWith(".mp4");
    }

    static boolean isMediaUrl(String url) {
        if (!isEromeHttpUrl(url)) return false;
        String path = pathOf(url).toLowerCase(Locale.US);
        return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png")
                || path.endsWith(".webp") || path.endsWith(".gif") || path.endsWith(".mp4");
    }

    static String mediaUrl(String url) {
        return isMediaUrl(url) ? stripFragment(url) : "";
    }

    /** Smaller sibling used by album grids. Empty when the URL is not an Erome still. */
    public static String thumbnailFor(String imageUrl) {
        String clean = mediaUrl(imageUrl);
        if (!isStillImage(clean) || clean.contains("/thumbs/")) return isStillImage(clean) ? clean : "";
        int query = clean.indexOf('?');
        String path = query < 0 ? clean : clean.substring(0, query);
        String suffix = query < 0 ? "" : clean.substring(query);
        int slash = path.lastIndexOf('/');
        if (slash < 0) return "";
        return path.substring(0, slash) + "/thumbs" + path.substring(slash) + suffix;
    }

    /**
     * Erome video stills follow the provider file name: the mp4 host {@code vN}
     * and a resolution suffix map to {@code sN/.../token.jpg}. No frame is decoded.
     */
    public static String stillFromVideo(String videoUrl) {
        if (!isDirectVideo(videoUrl)) return "";
        Uri uri;
        try {
            uri = Uri.parse(videoUrl.trim());
        } catch (Exception ignored) {
            return "";
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.US);
        if (!host.matches("v\\d+\\.erome\\.com")) return "";
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (!path.matches("(?i).+_\\d{3,4}p\\.mp4")) return "";
        String stillPath = path.replaceFirst("(?i)_\\d{3,4}p\\.mp4$", ".jpg");
        return "https://s" + host.substring(1) + stillPath;
    }

    /** Grid request: the real poster first, then the square thumb if that poster fails. */
    public static String gridArtwork(NativeContentItem item) {
        for (String candidate : artworkCandidates(item)) return candidate;
        return "";
    }

    /**
     * Poster first, so the tile can use the same shape as the site. The square
     * {@code /thumbs/} file is only a fallback and never the only attempt.
     */
    public static java.util.List<String> artworkCandidates(NativeContentItem item) {
        java.util.LinkedHashSet<String> urls = new java.util.LinkedHashSet<>();
        String still = resolvedStill(item);
        if (!still.isEmpty() && !still.contains("/thumbs/")) urls.add(still);
        String thumb = thumbnailFor(still);
        if (!thumb.isEmpty()) urls.add(thumb);
        if (!still.isEmpty()) urls.add(still);
        return new java.util.ArrayList<>(urls);
    }

    /** Next still that has not already failed. Never removes the media item. */
    public static String nextArtwork(NativeContentItem item, java.util.Collection<String> rejected) {
        for (String candidate : artworkCandidates(item)) {
            if (rejected != null && rejected.contains(candidate)) continue;
            return candidate;
        }
        return "";
    }

    /** Cached album videos parsed before lazy posters were read. */
    public static boolean missingPoster(java.util.List<NativeContentItem> items) {
        if (items == null) return false;
        for (NativeContentItem item : items) {
            if (item != null && item.isVideo() && isAlbumUrl(item.url) && !isStillImage(item.imageUrl)) {
                return true;
            }
        }
        return false;
    }

    private static String resolvedStill(NativeContentItem item) {
        if (item == null || !isAlbumUrl(item.url)) return "";
        if (isStillImage(item.imageUrl)) return mediaUrl(item.imageUrl);
        if (item.isVideo()) {
            String derived = stillFromVideo(item.sourceMediaUrl);
            if (!derived.isEmpty()) return derived;
        }
        if (isStillImage(item.sourceMediaUrl)) return mediaUrl(item.sourceMediaUrl);
        return "";
    }

    /** Next still to try after a failed Erome image request. Never removes the media item. */
    public static String alternateArtwork(NativeContentItem item, String rejected) {
        java.util.ArrayList<String> skipped = new java.util.ArrayList<>();
        if (rejected != null && !rejected.isEmpty()) skipped.add(rejected);
        return nextArtwork(item, skipped);
    }

    /** Full photo for the existing gallery viewer. Videos stay on the playback resolver. */
    public static String originalImage(NativeContentItem item) {
        if (item == null || !item.isImage() || !isAlbumUrl(item.url)) return "";
        if (isStillImage(item.sourceMediaUrl) && !item.sourceMediaUrl.contains("/thumbs/")) {
            return mediaUrl(item.sourceMediaUrl);
        }
        if (isStillImage(item.imageUrl) && !item.imageUrl.contains("/thumbs/")) return mediaUrl(item.imageUrl);
        return "";
    }

    private static boolean isStillImage(String url) {
        if (!isMediaUrl(url)) return false;
        String path = pathOf(url).toLowerCase(Locale.US);
        return path.endsWith(".jpg") || path.endsWith(".jpeg")
                || path.endsWith(".png") || path.endsWith(".webp");
    }

    public static String canonicalAlbum(String url) {
        if (!isAlbumUrl(url)) return "";
        MatcherSafe id = albumId(url);
        return id.value.isEmpty() ? "" : ORIGIN + "/a/" + id.value;
    }

    public static String imageReferer(NativeContentItem item) {
        if (item == null) return "";
        if (!isAlbumUrl(item.url) && !isMediaUrl(item.url) && !isMediaUrl(item.imageUrl)) return "";
        String album = canonicalAlbum(item.url);
        return album.isEmpty() ? ORIGIN + "/" : album;
    }

    /** Opens the existing browser so the user can complete verification themselves. */
    public static void openForVerification(Activity activity, String url) {
        if (activity == null || !isEromeHttpUrl(url)) return;
        String target = isAlbumUrl(url) ? canonicalAlbum(url) : url;
        if (!isEromeHttpUrl(target)) return;
        try {
            Intent browser = new Intent(activity, WebFallbackActivity.class);
            browser.putExtra(WebFallbackActivity.EXTRA_URL, target);
            activity.startActivity(browser);
        } catch (Exception ignored) {
        }
    }

    /**
     * Asks Erome to set its own {@code erome_version} cookie.
     * Results are not filtered in the app. A matching cookie is reused.
     */
    public void preparePreference(Context context) throws IOException {
        if (context == null) return;
        EromePreference.Choice choice = EromePreference.get(context);
        String jar = cookies(ORIGIN + "/");
        if (choice.slug.equals(EromePreference.applied(context))
                && jar.toLowerCase(Locale.US).contains("erome_version=")) return;
        String url = EromePreference.versionUrl(choice);
        if (url.isEmpty()) throw new IOException("Erome preference is not supported");
        Connection.Response response = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .maxBodySize(64 * 1024)
                .followRedirects(false)
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .referrer(ORIGIN + "/")
                .header("Accept", "text/html,application/xhtml+xml")
                .execute();
        if (!acceptPreference(context, choice, response.statusCode(), response.headers("Set-Cookie"))) {
            throw new IOException("Erome did not apply the selected preference");
        }
    }

    static boolean acceptPreference(Context context, EromePreference.Choice choice, int status,
                                    java.util.List<String> setCookies) {
        if (choice == null || (status != 200 && status != 302 && status != 303)) return false;
        if (!EromePreference.hasVersionCookie(setCookies)) return false;
        try {
            CookieManager manager = CookieManager.getInstance();
            for (String header : setCookies) {
                if (header != null && !header.trim().isEmpty()) manager.setCookie(ORIGIN + "/", header);
            }
            manager.flush();
        } catch (Exception ignored) {
            return false;
        }
        EromePreference.markApplied(context, choice);
        return choice.slug.equals(EromePreference.applied(context));
    }

    private Document get(Context context, String url) throws IOException {
        if (!isEromeHttpUrl(url)) throw new IOException("Refusing a non-Erome URL");
        Connection connection = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .maxBodySize(MAX_BODY_BYTES)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .ignoreContentType(false)
                .referrer(ORIGIN + "/")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.9");
        String cookies = cookies(ORIGIN + "/");
        if (!cookies.isEmpty()) connection.header("Cookie", cookies);
        Connection.Response response = connection.execute();
        String finalUrl = response.url() == null ? "" : response.url().toString();
        if (!isEromeHttpUrl(finalUrl)) throw new IOException("Erome redirected away from the provider");
        int status = response.statusCode();
        if (status == 401 || status == 403 || status == 429 || status == 451) {
            throw new EromePages.AccessException("Erome returned HTTP " + status);
        }
        if (status != 200) throw new IOException("Erome returned HTTP " + status);
        rememberCookies(response);
        Document document = response.parse();
        if (EromePages.isAccessRestricted(document)) {
            throw new EromePages.AccessException("Erome requested verification");
        }
        return document;
    }

    private static void rememberCookies(Connection.Response response) {
        try {
            CookieManager manager = CookieManager.getInstance();
            for (String header : response.headers("Set-Cookie")) {
                if (header != null && !header.trim().isEmpty()) manager.setCookie(ORIGIN + "/", header);
            }
            manager.flush();
        } catch (Exception ignored) {
        }
    }

    private static String cookies(String url) {
        try {
            String value = CookieManager.getInstance().getCookie(url);
            return value == null ? "" : value.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    static String sameAlbumPage(String albumUrl, String candidate) {
        if (!isEromeHttpUrl(candidate)) return "";
        String current = albumId(albumUrl).value;
        String next = albumId(candidate).value;
        if (current.isEmpty() || !current.equals(next)) return "";
        String stripped = stripFragment(candidate.trim());
        return stripped.equals(canonicalAlbum(albumUrl)) ? "" : stripped;
    }

    private static String fragmentToken(String url) {
        if (url == null) return "";
        int hash = url.indexOf('#');
        if (hash < 0 || hash == url.length() - 1) return "";
        String token = url.substring(hash + 1).trim();
        return token.matches("[A-Za-z0-9_-]{2,80}") ? token : "";
    }

    private static String stripFragment(String url) {
        int hash = url.indexOf('#');
        return hash < 0 ? url : url.substring(0, hash);
    }

    private static String pathOf(String url) {
        try {
            String path = Uri.parse(url.trim()).getPath();
            return path == null ? "" : path;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static MatcherSafe albumId(String url) {
        String path = pathOf(stripFragment(url));
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^/a/([A-Za-z0-9_-]{4,32})/?$").matcher(path);
        return new MatcherSafe(matcher.matches() ? matcher.group(1) : "");
    }

    private static final class MatcherSafe {
        final String value;
        MatcherSafe(String value) { this.value = value == null ? "" : value; }
    }
}
