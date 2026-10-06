package com.webapp.crazyshit;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.util.Base64;
import android.webkit.CookieManager;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BaddieHub source shared by ShitTok and Shows.
 *
 * BaddieHub does not expose trustworthy media dimensions in its listing/player markup. Candidates
 * are therefore validated off the feed thread with MediaMetadataRetriever and are only published
 * to ShitTok after their effective video height is greater than their width. Validation results and
 * direct media URLs are cached for the process lifetime so normal ShitTok playback does not repeat
 * the metadata work.
 *
 * Shows consumes normal category listings without an aspect-ratio gate. ShitTok alone uses the
 * asynchronous portrait validation below.
 */
final class BaddieHubRepository {
    static final String LABEL = "BaddieHub";
    private static final String KNOWN_GOOD_BASE = "https://baddiehub.com/";

    private static final int MAX_CANDIDATES = 18;
    private static final int MAX_SHOWS_PAGE_ITEMS = 128;
    private static final int MAX_VALIDATION_STARTS = 8;
    private static final int MAX_GLOBAL_VALIDATIONS = 8;
    private static final int MAX_RETURNED = 6;
    private static final long FIRST_RESULT_WAIT_MS = 850L;
    private static final long FAILURE_RETRY_MS = 60_000L;
    private static final int MAX_CATEGORY_DIRECTORY_PAGES = 4;

    private static final SourceConfig.BaddieHub COMPILED_CONFIG =
            SourceConfig.defaultBaddieHub();

    private static final ExecutorService VALIDATION_IO = Executors.newFixedThreadPool(4);
    private static final AtomicInteger VALIDATION_PENDING = new AtomicInteger();
    private static final Map<String, NativeContentItem> PORTRAIT = new ConcurrentHashMap<>();
    private static final Map<String, String> MEDIA = new ConcurrentHashMap<>();
    private static final Set<String> NON_PORTRAIT = ConcurrentHashMap.newKeySet();
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> RETRY_AFTER = new ConcurrentHashMap<>();

    List<NativeContentItem> fetchPortraitFeed(Context context, int page) throws IOException {
        SourceConfig.BaddieHub config = config(context);
        if (!enabled(config)) return new ArrayList<>();
        String listingUrl = routeUrl(config, page <= 1
                ? config.listingFirstRoute : config.listingPageRoute, "", page);
        List<Candidate> candidates = parseListing(
                fetch(context, listingUrl, config.baseUrl, config),
                config.cardLinksSelector,
                MAX_CANDIDATES
        );
        if (candidates.isEmpty()) return new ArrayList<>();

        ArrayList<NativeContentItem> ready = readyPortraits(candidates);
        if (ready.size() >= MAX_RETURNED) return first(ready, MAX_RETURNED);

        int started = 0;
        long now = System.currentTimeMillis();
        for (Candidate candidate : candidates) {
            if (candidate == null || candidate.pageUrl.isEmpty()) continue;
            if (PORTRAIT.containsKey(candidate.pageUrl) || NON_PORTRAIT.contains(candidate.pageUrl)) {
                continue;
            }
            Long retry = RETRY_AFTER.get(candidate.pageUrl);
            if (retry != null && retry > now) continue;
            if (started >= MAX_VALIDATION_STARTS) break;
            if (!IN_FLIGHT.add(candidate.pageUrl)) continue;

            int pending = VALIDATION_PENDING.incrementAndGet();
            if (pending > MAX_GLOBAL_VALIDATIONS) {
                VALIDATION_PENDING.decrementAndGet();
                IN_FLIGHT.remove(candidate.pageUrl);
                break;
            }

            started++;
            VALIDATION_IO.execute(() -> validate(context, candidate));
        }

        // Keep the source inside the existing ShitTok refill budget. Fast CDN metadata can join
        // this batch; slower validation finishes in the background and is reused by the next refill.
        long deadline = System.nanoTime() + FIRST_RESULT_WAIT_MS * 1_000_000L;
        while (ready.isEmpty() && started > 0 && System.nanoTime() < deadline) {
            try {
                Thread.sleep(55L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
            ready = readyPortraits(candidates);
        }
        return first(ready, MAX_RETURNED);
    }

    List<NativeContentItem> fetchCategories(Context context) throws IOException {
        SourceConfig.BaddieHub config = config(context);
        if (!enabled(config)) return new ArrayList<>();
        LinkedHashMap<String, NativeContentItem> found = new LinkedHashMap<>();
        IOException failure = null;
        for (int page = 1; page <= MAX_CATEGORY_DIRECTORY_PAGES; page++) {
            String route = page == 1 ? config.categoriesFirstRoute : config.categoriesPageRoute;
            String url = routeUrl(config, route, "", page);
            try {
                Document document = fetch(context, url, config.baseUrl, config);
                int before = found.size();
                parseCategories(document, config, found);
                if (page > 1 && found.size() == before) break;
            } catch (IOException error) {
                failure = error;
                break;
            }
        }
        if (found.isEmpty() && failure != null) throw failure;
        return new ArrayList<>(found.values());
    }

    List<NativeContentItem> fetchCategory(
            Context context,
            String categoryUrl,
            int page
    ) throws IOException {
        SourceConfig.BaddieHub config = config(context);
        if (!enabled(config)) return new ArrayList<>();
        String slug = categorySlug(categoryUrl, config);
        if (slug.isEmpty()) return new ArrayList<>();
        int safePage = Math.max(1, page);
        String url = categoryPageUrl(config, categoryUrl, safePage);
        List<Candidate> candidates = parseListing(
                fetch(context, url, config.baseUrl, config),
                config.cardLinksSelector,
                MAX_SHOWS_PAGE_ITEMS
        );
        return toShowsItems(candidates);
    }

    static String categoryPageUrl(
            SourceConfig.BaddieHub config,
            String categoryUrl,
            int page
    ) {
        String slug = categorySlug(categoryUrl, config);
        if (slug.isEmpty()) return "";
        int safePage = Math.max(1, page);
        String route = safePage == 1 ? config.categoryFirstRoute : config.categoryPageRoute;
        return routeUrl(config, route, slug, safePage);
    }

    static List<NativeContentItem> parseShowsListing(
            String html,
            String location,
            String selector
    ) {
        return toShowsItems(parseListing(
                Jsoup.parse(html, location),
                selector,
                MAX_SHOWS_PAGE_ITEMS
        ));
    }

    private static List<NativeContentItem> toShowsItems(List<Candidate> candidates) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        for (Candidate candidate : candidates) {
            result.add(new NativeContentItem(
                    NativeContentItem.KIND_MEDIA,
                    candidate.title.isEmpty() ? LABEL : candidate.title,
                    candidate.pageUrl,
                    candidate.imageUrl,
                    "",
                    LABEL,
                    "",
                    "",
                    ""
            ));
        }
        return result;
    }

    CrazyShitRepository.StreamInfo resolvePlayable(Context context, String pageUrl) throws IOException {
        if (!isBaddieHubUrl(pageUrl)) return null;
        String media = MEDIA.get(pageUrl);
        String title = "";
        if (media == null || media.isEmpty()) {
            Resolved resolved = resolvePage(context, pageUrl);
            media = resolved.mediaUrl;
            title = resolved.title;
            if (!media.isEmpty()) MEDIA.put(pageUrl, media);
        } else {
            NativeContentItem cached = PORTRAIT.get(pageUrl);
            if (cached != null) title = cached.title;
        }
        if (media == null || media.isEmpty()) return null;
        return new CrazyShitRepository.StreamInfo(
                media,
                pageUrl,
                title.isEmpty() ? LABEL : title,
                pageUrl
        );
    }

    static boolean isBaddieHubUrl(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
            String host = uri.getHost();
            if (host == null) return false;
            for (String base : configuredBases(config(null))) {
                String configuredHost = URI.create(base).getHost();
                if (configuredHost == null) continue;
                if (host.equalsIgnoreCase(configuredHost)) return true;
                String bareConfigured = configuredHost.replaceFirst("(?i)^www\\.", "");
                String bareCandidate = host.replaceFirst("(?i)^www\\.", "");
                if (bareCandidate.equalsIgnoreCase(bareConfigured)) return true;
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isPortraitDimensions(int width, int height, int rotation) {
        if (width <= 0 || height <= 0) return false;
        int normalized = Math.abs(rotation) % 360;
        if (normalized == 90 || normalized == 270) {
            int swap = width;
            width = height;
            height = swap;
        }
        return height > width;
    }

    private void validate(Context context, Candidate candidate) {
        try {
            Resolved resolved = resolvePage(context, candidate.pageUrl);
            if (resolved.mediaUrl.isEmpty()) {
                retryLater(candidate.pageUrl);
                return;
            }
            Dimensions dimensions = mediaDimensions(
                    resolved.mediaUrl,
                    candidate.pageUrl,
                    config(context).userAgent
            );
            if (dimensions.width <= 0 || dimensions.height <= 0) {
                retryLater(candidate.pageUrl);
                return;
            }
            if (!isPortraitDimensions(dimensions.width, dimensions.height, dimensions.rotation)) {
                NON_PORTRAIT.add(candidate.pageUrl);
                MEDIA.remove(candidate.pageUrl);
                return;
            }

            int width = dimensions.width;
            int height = dimensions.height;
            int rotation = Math.abs(dimensions.rotation) % 360;
            if (rotation == 90 || rotation == 270) {
                int swap = width;
                width = height;
                height = swap;
            }
            float aspectRatio = width / Math.max(1f, height);
            String title = !resolved.title.isEmpty() ? resolved.title : candidate.title;
            String image = !resolved.posterUrl.isEmpty() ? resolved.posterUrl : candidate.imageUrl;
            NativeContentItem item = new NativeContentItem(
                    NativeContentItem.KIND_MEDIA,
                    title.isEmpty() ? LABEL : title,
                    candidate.pageUrl,
                    image,
                    "",
                    LABEL,
                    "",
                    "",
                    "",
                    0L,
                    aspectRatio
            );
            MEDIA.put(candidate.pageUrl, resolved.mediaUrl);
            PORTRAIT.put(candidate.pageUrl, item);
            RETRY_AFTER.remove(candidate.pageUrl);
        } catch (Exception ignored) {
            retryLater(candidate.pageUrl);
        } finally {
            IN_FLIGHT.remove(candidate.pageUrl);
            VALIDATION_PENDING.decrementAndGet();
        }
    }

    private Dimensions mediaDimensions(String mediaUrl, String referer, String userAgent) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            LinkedHashMap<String, String> headers = new LinkedHashMap<>();
            headers.put("User-Agent", userAgent);
            headers.put("Referer", referer);
            retriever.setDataSource(mediaUrl, headers);
            int width = integer(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int height = integer(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            int rotation = integer(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
            return new Dimensions(width, height, rotation);
        } catch (Exception ignored) {
            return new Dimensions(0, 0, 0);
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private static List<Candidate> parseListing(
            Document document,
            String cardSelector,
            int maxItems
    ) {
        LinkedHashMap<String, Candidate> found = new LinkedHashMap<>();
        if (document == null) return new ArrayList<>();
        for (Element anchor : document.select(cardSelector)) {
            String url = absolute(anchor, "href", document.location());
            if (!isVideoPage(url)) continue;
            Element image = anchor.selectFirst("img");
            if (image == null && anchor.parent() != null) image = anchor.parent().selectFirst("img");
            if (image == null) continue;

            String canonical = stripQueryAndFragment(url);
            if (found.containsKey(canonical)) continue;
            String title = clean(anchor.text());
            if (title.isEmpty()) title = clean(image.attr("alt"));
            if (title.isEmpty()) title = slugTitle(canonical);
            String imageUrl = imageUrl(image, document.location());
            found.put(canonical, new Candidate(canonical, title, imageUrl));
            if (found.size() >= maxItems) break;
        }
        return new ArrayList<>(found.values());
    }

    private void parseCategories(
            Document document,
            SourceConfig.BaddieHub config,
            Map<String, NativeContentItem> found
    ) {
        if (document == null) return;
        for (Element anchor : document.select(config.categoryLinksSelector)) {
            String url = stripQueryAndFragment(absolute(anchor, "href", document.location()));
            String slug = categorySlug(url, config);
            if (slug.isEmpty()) continue;
            Element image = anchor.selectFirst("img");
            if (image == null && anchor.parent() != null) image = anchor.parent().selectFirst("img");
            String title = clean(anchor.text());
            if (title.isEmpty() && image != null) title = clean(image.attr("alt"));
            if (title.isEmpty()) title = slugTitle(url);
            NativeContentItem candidate = new NativeContentItem(
                    NativeContentItem.KIND_CATEGORY,
                    title,
                    url,
                    imageUrl(image, document.location()),
                    "",
                    LABEL,
                    "",
                    "BaddieHub category",
                    ""
            );
            NativeContentItem existing = found.get(url);
            if (existing == null || (clean(existing.imageUrl).isEmpty()
                    && !clean(candidate.imageUrl).isEmpty())) {
                found.put(url, candidate);
            }
        }
    }

    private Resolved resolvePage(Context context, String pageUrl) throws IOException {
        SourceConfig.BaddieHub config = config(context);
        Document page = fetch(context, pageUrl, config.baseUrl, config);
        Element heading = page.selectFirst("h1");
        String title = clean(heading == null ? "" : heading.text());
        if (title.isEmpty()) title = clean(page.title());
        Element direct = page.selectFirst(config.playableVideoSelector);
        if (direct != null) {
            String media = absolute(direct, "src", page.location());
            if (isDirectVideo(media)) return new Resolved(title, media, "");
        }
        for (Element frame : page.select(config.playableFrameSelector)) {
            String frameUrl = absolute(frame, "src", page.location());
            if (!frameUrl.contains("player-x.php") || !frameUrl.contains("q=")) continue;
            Resolved decoded = decodePlayer(frameUrl, title);
            if (!decoded.mediaUrl.isEmpty()) return decoded;
        }
        return new Resolved(title, "", "");
    }

    private Resolved decodePlayer(String frameUrl, String title) {
        try {
            String encoded = queryValue(URI.create(frameUrl).getRawQuery(), "q");
            if (encoded.isEmpty()) return new Resolved(title, "", "");
            byte[] decoded = Base64.decode(encoded, Base64.DEFAULT);
            String payload = new String(decoded, StandardCharsets.UTF_8);
            String tag = queryValue(payload, "tag");
            if (tag.isEmpty()) tag = urlDecode(payload);
            Document fragment = Jsoup.parseBodyFragment(tag);
            Element source = fragment.selectFirst("source[src]");
            Element video = fragment.selectFirst("video");
            String media = source == null ? "" : source.attr("src").trim();
            String poster = video == null ? "" : video.attr("poster").trim();
            if (!isDirectVideo(media)) return new Resolved(title, "", poster);
            return new Resolved(title, media, poster);
        } catch (Exception ignored) {
            return new Resolved(title, "", "");
        }
    }

    private Document fetch(
            Context context,
            String url,
            String referer,
            SourceConfig.BaddieHub config
    ) throws IOException {
        IOException last = null;
        for (String candidateUrl : requestUrls(url, config)) {
            for (int attempt = 0; attempt <= config.retryCount; attempt++) {
                try {
                    Connection connection = Jsoup.connect(candidateUrl)
                            .userAgent(config.userAgent)
                            .referrer(config.refererOverride.isEmpty()
                                    ? referer : config.refererOverride)
                            .timeout(config.requestTimeoutMs)
                            .maxBodySize(8 * 1024 * 1024)
                            .followRedirects(true)
                            .ignoreHttpErrors(false)
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                    for (Map.Entry<String, String> header : config.requestHeaders.entrySet()) {
                        connection.header(header.getKey(), header.getValue());
                    }
                    try {
                        String cookies = CookieManager.getInstance().getCookie(candidateUrl);
                        if (cookies != null && !cookies.trim().isEmpty()) {
                            connection.header("Cookie", cookies);
                        }
                    } catch (Exception ignored) {
                    }
                    return connection.get();
                } catch (IOException error) {
                    last = error;
                }
            }
        }
        throw last == null ? new IOException("BaddieHub request failed") : last;
    }

    private ArrayList<NativeContentItem> readyPortraits(List<Candidate> candidates) {
        ArrayList<NativeContentItem> ready = new ArrayList<>();
        for (Candidate candidate : candidates) {
            NativeContentItem item = PORTRAIT.get(candidate.pageUrl);
            if (item != null) ready.add(item);
        }
        Collections.shuffle(ready);
        return ready;
    }

    private static ArrayList<NativeContentItem> first(List<NativeContentItem> source, int max) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        if (source == null) return result;
        for (NativeContentItem item : source) {
            if (item == null) continue;
            result.add(item);
            if (result.size() >= max) break;
        }
        return result;
    }

    private void retryLater(String pageUrl) {
        RETRY_AFTER.put(pageUrl, System.currentTimeMillis() + FAILURE_RETRY_MS);
    }

    private static boolean isVideoPage(String value) {
        if (!isBaddieHubUrl(value)) return false;
        try {
            URI uri = URI.create(value);
            String path = uri.getPath() == null ? "" : uri.getPath();
            path = path.replaceAll("^/+|/+$", "");
            if (path.isEmpty() || path.contains("/")) return false;
            String lower = path.toLowerCase(Locale.US);
            return !lower.equals("categories") && !lower.equals("category") &&
                    !lower.equals("tags") && !lower.equals("tag") &&
                    !lower.equals("login") && !lower.equals("register") &&
                    !lower.equals("privacy-policy") && !lower.equals("dmca") &&
                    !lower.equals("contact") && !lower.startsWith("wp-");
        } catch (Exception ignored) {
            return false;
        }
    }

    static String categorySlug(String value) {
        return categorySlug(value, config(null));
    }

    private static String categorySlug(String value, SourceConfig.BaddieHub config) {
        if (!isBaddieHubUrl(value)) return "";
        try {
            String path = URI.create(value).getPath();
            if (path == null) return "";
            String[] parts = path.replaceAll("^/+|/+$", "").split("/");
            String prefix = config.categoryFirstRoute.split("\\{slug\\}", 2)[0]
                    .replaceAll("^/+|/+$", "");
            String[] prefixParts = prefix.isEmpty() ? new String[0] : prefix.split("/");
            if (parts.length != prefixParts.length + 1) return "";
            for (int index = 0; index < prefixParts.length; index++) {
                if (!parts[index].equalsIgnoreCase(prefixParts[index])) return "";
            }
            String slug = parts[parts.length - 1];
            return slug.matches("[A-Za-z0-9_-]+") ? slug : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static SourceConfig.BaddieHub config(Context context) {
        if (context != null) RemoteSourceConfigManager.initialize(context);
        SourceConfig snapshot = RemoteSourceConfigManager.snapshotOrNull();
        return snapshot == null || snapshot.baddieHub == null
                ? COMPILED_CONFIG : snapshot.baddieHub;
    }

    private static List<String> configuredBases(SourceConfig.BaddieHub config) {
        ArrayList<String> result = new ArrayList<>();
        result.add(KNOWN_GOOD_BASE);
        result.add(config.baseUrl);
        SourceConfig snapshot = RemoteSourceConfigManager.snapshotOrNull();
        if (snapshot == null || snapshot.fallbacksEnabled) result.addAll(config.fallbackDomains);
        return result;
    }

    private static List<String> requestUrls(String value, SourceConfig.BaddieHub config) {
        ArrayList<String> result = new ArrayList<>();
        result.add(value);
        try {
            URI original = URI.create(value);
            SourceConfig snapshot = RemoteSourceConfigManager.snapshotOrNull();
            List<String> fallbacks = snapshot != null && !snapshot.fallbacksEnabled
                    ? Collections.emptyList() : config.fallbackDomains;
            for (String base : fallbacks) {
                URI fallback = URI.create(base);
                result.add(new URI(
                        fallback.getScheme(),
                        fallback.getAuthority(),
                        original.getPath(),
                        original.getQuery(),
                        original.getFragment()
                ).toString());
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static boolean enabled(SourceConfig.BaddieHub config) {
        SourceConfig snapshot = RemoteSourceConfigManager.snapshotOrNull();
        return snapshot == null || !snapshot.sourceKillSwitchesEnabled || config.enabled;
    }

    private static String routeUrl(
            SourceConfig.BaddieHub config,
            String route,
            String slug,
            int page
    ) {
        String resolved = route
                .replace("{slug}", slug == null ? "" : slug)
                .replace("{page}", String.valueOf(Math.max(1, page)));
        return URI.create(config.baseUrl).resolve(resolved).toString();
    }

    private static String queryValue(String rawQuery, String name) {
        if (rawQuery == null || rawQuery.isEmpty()) return "";
        for (String part : rawQuery.split("&")) {
            int equals = part.indexOf('=');
            String rawKey = equals < 0 ? part : part.substring(0, equals);
            String rawValue = equals < 0 ? "" : part.substring(equals + 1);
            if (!name.equals(urlDecode(rawKey))) continue;
            return urlDecode(rawValue);
        }
        return "";
    }

    private static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value == null ? "" : value, "UTF-8");
        } catch (Exception ignored) {
            return value == null ? "" : value;
        }
    }

    private static String stripQueryAndFragment(String value) {
        if (value == null) return "";
        int end = value.length();
        int query = value.indexOf('?');
        int fragment = value.indexOf('#');
        if (query >= 0) end = Math.min(end, query);
        if (fragment >= 0) end = Math.min(end, fragment);
        return value.substring(0, end);
    }

    private static boolean isDirectVideo(String value) {
        if (value == null || value.isEmpty()) return false;
        String lower = value.toLowerCase(Locale.US);
        int query = lower.indexOf('?');
        if (query >= 0) lower = lower.substring(0, query);
        return lower.endsWith(".mp4") || lower.endsWith(".m4v") || lower.endsWith(".webm");
    }

    private static String absolute(Element element, String attr, String base) {
        if (element == null) return "";
        String value = element.attr(attr).trim();
        if (value.isEmpty()) return "";
        try {
            return URI.create(base).resolve(value).toString();
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String imageUrl(Element image, String base) {
        if (image == null) return "";
        for (String attr : new String[]{"src", "data-src", "data-lazy-src"}) {
            String value = image.attr(attr).trim();
            if (value.isEmpty()) continue;
            try {
                return URI.create(base).resolve(value).toString();
            } catch (Exception ignored) {
                return value;
            }
        }
        return "";
    }

    private static String slugTitle(String pageUrl) {
        try {
            String path = URI.create(pageUrl).getPath();
            if (path == null) return LABEL;
            path = path.replaceAll("^/+|/+$", "");
            if (path.isEmpty()) return LABEL;
            String text = path.replace('-', ' ').replace('_', ' ').replaceAll("\\s+", " ").trim();
            if (text.isEmpty()) return LABEL;
            return Character.toUpperCase(text.charAt(0)) + text.substring(1);
        } catch (Exception ignored) {
            return LABEL;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static int integer(String value) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static final class Candidate {
        final String pageUrl;
        final String title;
        final String imageUrl;

        Candidate(String pageUrl, String title, String imageUrl) {
            this.pageUrl = pageUrl == null ? "" : pageUrl;
            this.title = title == null ? "" : title;
            this.imageUrl = imageUrl == null ? "" : imageUrl;
        }
    }

    private static final class Resolved {
        final String title;
        final String mediaUrl;
        final String posterUrl;

        Resolved(String title, String mediaUrl, String posterUrl) {
            this.title = title == null ? "" : title;
            this.mediaUrl = mediaUrl == null ? "" : mediaUrl;
            this.posterUrl = posterUrl == null ? "" : posterUrl;
        }
    }

    private static final class Dimensions {
        final int width;
        final int height;
        final int rotation;

        Dimensions(int width, int height, int rotation) {
            this.width = width;
            this.height = height;
            this.rotation = rotation;
        }
    }
}
