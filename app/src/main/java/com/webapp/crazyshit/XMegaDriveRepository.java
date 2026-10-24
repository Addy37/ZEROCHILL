package com.webapp.crazyshit;

import android.content.Context;
import android.webkit.CookieManager;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Isolated supplemental provider for the public XMegaDrive site.
 *
 * Compiled, not remote-config. Schema 1 has exactly seven configurable sources and does
 * not include XMegaDrive, so this provider stays in code. ShitTok scrapes public /videos/
 * listings. Shows discovers /categories/ and opens each category inside the existing
 * gallery. A direct request that only receives the edge challenge is retried once in a
 * hidden WebView so the Shows shelf can read the real category cards. Playback resolves the
 * direct MP4 from the video page without changing the shared Media3 path. Failure is contained
 * so other providers continue.
 */
final class XMegaDriveRepository {
    static final String LABEL = "XMegaDrive";
    private static final String BASE = "https://www.xmegadrive.com/";
    private static final String LISTING = BASE + "videos/";
    private static final String CATEGORIES = BASE + "categories/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final Object BROWSER_FALLBACK = new Object();
    private static final int MAX_CANDIDATES = 12;
    private static final int MAX_RETURNED = 8;
    private static final int MAX_CATEGORY_VIDEOS = 36;
    private static final int MAX_CATEGORY_DIRECTORY_PAGES = 2;

    List<NativeContentItem> fetchShitTokCandidates(Context context, int page) throws IOException {
        String url = page <= 1 ? LISTING : LISTING + "?page=" + Math.max(1, page);
        Document document = fetch(context, url);
        List<NativeContentItem> items = parseVideos(document, MAX_CANDIDATES);
        int take = Math.min(MAX_RETURNED, items.size());
        return new ArrayList<>(items.subList(0, take));
    }

    /**
     * One directory request, plus at most one discovered next page. Does not walk the live site
     * from a hard-coded category list.
     */
    List<NativeContentItem> fetchCategories(Context context) throws IOException {
        LinkedHashMap<String, NativeContentItem> found = new LinkedHashMap<>();
        String next = CATEGORIES;
        IOException failure = null;
        for (int page = 1; page <= MAX_CATEGORY_DIRECTORY_PAGES && !next.isEmpty(); page++) {
            Document document;
            try {
                document = fetch(context, next);
            } catch (IOException error) {
                failure = error;
                break;
            }
            int before = found.size();
            parseCategories(document, found);
            if (page > 1 && found.size() == before) break;
            next = nextDirectoryPage(document, page + 1);
        }
        if (found.isEmpty() && failure != null) throw failure;
        return new ArrayList<>(found.values());
    }

    List<NativeContentItem> fetchCategory(Context context, String categoryUrl, int page) throws IOException {
        String url = categoryPageUrl(categoryUrl, page);
        if (url.isEmpty()) return new ArrayList<>();
        return parseVideos(fetch(context, url), MAX_CATEGORY_VIDEOS);
    }

    CrazyShitRepository.StreamInfo resolvePlayable(Context context, String pageUrl) throws IOException {
        if (!isXMegaDriveUrl(pageUrl)) return null;
        Document page = fetch(context, pageUrl);
        String media = extractMedia(page);
        if (media == null || media.isEmpty()) return null;
        String title = extractTitle(page);
        return new CrazyShitRepository.StreamInfo(media, pageUrl, title.isEmpty() ? LABEL : title, pageUrl);
    }

    static boolean isXMegaDriveUrl(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
            String host = uri.getHost();
            return host != null && (host.equalsIgnoreCase("xmegadrive.com")
                    || host.equalsIgnoreCase("www.xmegadrive.com"));
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isCategoryUrl(String value) {
        return !normalizeCategoryUrl(value).isEmpty();
    }

    /** Canonical https://www.xmegadrive.com/categories/{slug}/ or empty when it is not a category. */
    static String normalizeCategoryUrl(String value) {
        if (!isXMegaDriveUrl(value)) return "";
        try {
            URI uri = URI.create(value.trim());
            String[] parts = pathParts(uri.getPath());
            if (parts.length != 2 || !"categories".equalsIgnoreCase(parts[0])) return "";
            String slug = parts[1].toLowerCase(Locale.US);
            if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || slug.matches("\\d+")) return "";
            return CATEGORIES + slug + "/";
        } catch (Exception ignored) {
            return "";
        }
    }

    static String categoryPageUrl(String categoryUrl, int page) {
        String canonical = normalizeCategoryUrl(categoryUrl);
        if (canonical.isEmpty()) return "";
        int safePage = Math.max(1, page);
        if (safePage == 1) return canonical;
        return canonical + safePage + "/";
    }

    /** Offline fixture entry for listing HTML. Does not fetch the network. */
    static List<NativeContentItem> parseListingHtml(String html) {
        return parseVideos(Jsoup.parse(html == null ? "" : html, BASE), MAX_CANDIDATES);
    }

    /** Offline fixture entry for the public categories directory. */
    static List<NativeContentItem> parseCategoriesHtml(String html) {
        LinkedHashMap<String, NativeContentItem> found = new LinkedHashMap<>();
        parseCategories(Jsoup.parse(html == null ? "" : html, BASE), found);
        return new ArrayList<>(found.values());
    }

    /** Offline fixture entry for one category page. Does not fetch the network. */
    static List<NativeContentItem> parseCategoryVideosHtml(String html) {
        return parseVideos(Jsoup.parse(html == null ? "" : html, BASE), MAX_CATEGORY_VIDEOS);
    }

    /** Offline fixture entry for a video page. Returns the playable MP4 or empty. */
    static String mediaFromHtml(String html, String pageUrl) {
        String base = pageUrl == null || pageUrl.isEmpty() ? BASE : pageUrl;
        return extractMedia(Jsoup.parse(html == null ? "" : html, base));
    }

    /** True when the body is an edge challenge instead of a listing. */
    static boolean looksBlocked(String html) {
        return looksBlocked(Jsoup.parse(html == null ? "" : html, BASE));
    }

    static boolean looksBlocked(Document document) {
        if (document == null) return true;
        String title = document.title() == null ? "" : document.title().toLowerCase(Locale.US);
        if (title.contains("just a moment")
                || title.contains("attention required")
                || title.contains("cloudflare")) {
            return true;
        }
        String html = document.html().toLowerCase(Locale.US);
        return html.contains("cf-challenge")
                || html.contains("challenge-platform")
                || html.contains("checking your browser")
                || html.contains("/cdn-cgi/challenge");
    }

    private static Document fetch(Context context, String url) throws IOException {
        try {
            Document direct = fetchDirect(context, url);
            if (hasUsefulMarkup(direct)) return direct;
        } catch (IOException direct) {
            if (!isBlocked(direct)) throw direct;
        }
        synchronized (BROWSER_FALLBACK) {
            try {
                Document retry = fetchDirect(context, url);
                if (hasUsefulMarkup(retry)) return retry;
            } catch (IOException ignored) {
            }
            Document rendered = XMegaDriveBrowserFetch.fetch(context, url, USER_AGENT, BASE);
            try {
                Document after = fetchDirect(context, url);
                if (hasUsefulMarkup(after)) return after;
            } catch (IOException ignored) {
            }
            if (hasUsefulMarkup(rendered)) return rendered;
            throw new IOException("XMegaDrive stayed blocked after the browser check");
        }
    }

    private static Document fetchDirect(Context context, String url) throws IOException {
        Connection connection = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .referrer(BASE)
                .timeout(8000)
                .maxBodySize(4 * 1024 * 1024)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9");
        String cookies = cookiesFor(url);
        if (!cookies.isEmpty()) connection.header("Cookie", cookies);
        Connection.Response response = connection.execute();
        int status = response.statusCode();
        Document document = response.parse();
        document.setBaseUri(url);
        if (status == 404) throw new IOException("XMegaDrive returned HTTP 404");
        if (status >= 400 || looksBlocked(document)) {
            throw new IOException("XMegaDrive blocked the direct request (" + status + ")");
        }
        return document;
    }

    private static boolean hasUsefulMarkup(Document document) {
        if (document == null || looksBlocked(document)) return false;
        LinkedHashMap<String, NativeContentItem> categories = new LinkedHashMap<>();
        parseCategories(document, categories);
        if (!categories.isEmpty()) return true;
        if (!parseVideos(document, 1).isEmpty()) return true;
        return !extractMedia(document).isEmpty();
    }

    private static boolean isBlocked(IOException error) {
        String message = error == null || error.getMessage() == null
                ? ""
                : error.getMessage().toLowerCase(Locale.US);
        return message.contains("blocked");
    }

    private static String cookiesFor(String url) {
        try {
            String cookies = CookieManager.getInstance().getCookie(url);
            return cookies == null ? "" : cookies.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void parseCategories(Document document, LinkedHashMap<String, NativeContentItem> found) {
        if (document == null || found == null) return;
        for (Element anchor : document.select("a[href*=/categories/]")) {
            String canonical = normalizeCategoryUrl(anchor.absUrl("href"));
            if (canonical.isEmpty()) continue;
            Element image = anchor.selectFirst("img");
            String imageUrl = imageUrl(image);
            String slug = canonical.substring(CATEGORIES.length(), canonical.length() - 1);
            NativeContentItem candidate = new NativeContentItem(
                    NativeContentItem.KIND_CATEGORY,
                    categoryTitle(anchor, slug),
                    canonical,
                    imageUrl,
                    "",
                    LABEL,
                    "",
                    "XMegaDrive category",
                    ""
            );
            NativeContentItem existing = found.get(canonical);
            if (existing == null || (existing.imageUrl.isEmpty() && !candidate.imageUrl.isEmpty())) {
                found.put(canonical, candidate);
            }
        }
    }

    private static String nextDirectoryPage(Document document, int page) {
        if (document == null || page < 2 || page > MAX_CATEGORY_DIRECTORY_PAGES) return "";
        String needle = "/categories/" + page + "/";
        for (Element anchor : document.select("a[href]")) {
            String href = anchor.absUrl("href");
            if (!isXMegaDriveUrl(href)) continue;
            try {
                String path = URI.create(href).getPath();
                if (path != null && (needle.equals(path) || needle.equals(path + "/"))) {
                    return CATEGORIES + page + "/";
                }
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private static List<NativeContentItem> parseVideos(Document document, int max) {
        LinkedHashMap<String, NativeContentItem> found = new LinkedHashMap<>();
        if (document == null || max <= 0) return new ArrayList<>();
        for (Element anchor : document.select("a[href*=/videos/]")) {
            String canonical = normalizeVideoUrl(anchor.absUrl("href"));
            if (canonical.isEmpty() || found.containsKey(canonical)) continue;
            String title = cardTitle(anchor);
            if (title.isEmpty()) title = slugTitle(canonical);
            found.put(canonical, new NativeContentItem(
                    NativeContentItem.KIND_MEDIA,
                    title.isEmpty() ? LABEL : title,
                    canonical,
                    imageUrl(anchor.selectFirst("img")),
                    "",
                    LABEL,
                    "",
                    "",
                    ""
            ));
            if (found.size() >= max) break;
        }
        return new ArrayList<>(found.values());
    }

    private static String normalizeVideoUrl(String value) {
        if (!isXMegaDriveUrl(value)) return "";
        try {
            URI uri = URI.create(value.trim());
            String[] parts = pathParts(uri.getPath());
            if (parts.length != 2 || !"videos".equalsIgnoreCase(parts[0])) return "";
            String slug = parts[1];
            if (slug.isEmpty() || slug.matches("\\d+")) return "";
            return BASE + "videos/" + slug + "/";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String[] pathParts(String path) {
        if (path == null || path.isEmpty()) return new String[0];
        String cleaned = path;
        if (cleaned.length() > 1 && cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        if (cleaned.isEmpty()) return new String[0];
        return cleaned.split("/");
    }

    private static String categoryTitle(Element anchor, String slug) {
        Element strong = anchor.selectFirst("strong.title");
        String title = clean(strong == null ? "" : strong.text());
        if (!title.isEmpty()) return title;
        title = clean(anchor.attr("title"));
        if (!title.isEmpty()) return title;
        String fromSlug = slug == null ? "" : slug.replace('-', ' ').replace('_', ' ').trim();
        return fromSlug.isEmpty() ? LABEL : fromSlug;
    }

    private static String cardTitle(Element anchor) {
        Element strong = anchor.selectFirst("strong.title");
        String title = clean(strong == null ? "" : strong.text());
        if (!title.isEmpty()) return title;
        title = clean(anchor.attr("title"));
        if (!title.isEmpty()) return title;
        return clean(anchor.text());
    }

    private static String imageUrl(Element image) {
        if (image == null) return "";
        String original = httpImage(image.absUrl("data-original"));
        if (!original.isEmpty()) return original;
        String dataSrc = httpImage(image.absUrl("data-src"));
        if (!dataSrc.isEmpty()) return dataSrc;
        return httpImage(image.absUrl("src"));
    }

    private static String httpImage(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase(Locale.US);
        if (lower.startsWith("https://") || lower.startsWith("http://")) return trimmed;
        return "";
    }

    private static String extractMedia(Document page) {
        if (page == null) return "";
        Element video = page.selectFirst("video[src*=.mp4], source[src*=.mp4]");
        if (video != null) {
            String src = video.absUrl("src");
            if (src.contains(".mp4")) return src;
        }
        for (Element el : page.select("[src*=.mp4]")) {
            String src = el.absUrl("src");
            if (src.contains("/get_file/") && src.contains(".mp4")) return src;
        }
        return "";
    }

    private static String extractTitle(Document page) {
        if (page == null) return "";
        Element h1 = page.selectFirst("h1");
        return h1 == null ? "" : clean(h1.text());
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String slugTitle(String url) {
        try {
            String path = URI.create(url).getPath();
            int last = path.lastIndexOf('/');
            String slug = last >= 0 ? path.substring(last + 1) : path;
            if (slug.isEmpty() && last > 0) {
                String trimmed = path.substring(0, last);
                int previous = trimmed.lastIndexOf('/');
                slug = previous >= 0 ? trimmed.substring(previous + 1) : trimmed;
            }
            if (slug.isEmpty()) return "";
            return slug.replace('-', ' ').replace('_', ' ');
        } catch (Exception ignored) {
            return "";
        }
    }
}
