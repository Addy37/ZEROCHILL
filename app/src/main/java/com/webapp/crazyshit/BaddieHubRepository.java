package com.webapp.crazyshit;

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
 * Experimental ShitTok-only BaddieHub source.
 *
 * BaddieHub does not expose trustworthy media dimensions in its listing/player markup. Candidates
 * are therefore validated off the feed thread with MediaMetadataRetriever and are only published
 * to ShitTok after their effective video height is greater than their width. Validation results and
 * direct media URLs are cached for the process lifetime so normal ShitTok playback does not repeat
 * the metadata work.
 *
 * Source values remain compiled for this device-test pass. If the source is approved for release,
 * move the domain/routes/selectors/timeouts into the existing remote source configuration system.
 */
final class BaddieHubRepository {
    static final String LABEL = "BaddieHub";

    private static final String BASE = "https://baddiehub.com/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36";
    private static final int REQUEST_TIMEOUT_MS = 12_000;
    private static final int MAX_CANDIDATES = 18;
    private static final int MAX_VALIDATION_STARTS = 8;
    private static final int MAX_GLOBAL_VALIDATIONS = 8;
    private static final int MAX_RETURNED = 6;
    private static final long FIRST_RESULT_WAIT_MS = 850L;
    private static final long FAILURE_RETRY_MS = 60_000L;

    private static final ExecutorService VALIDATION_IO = Executors.newFixedThreadPool(4);
    private static final AtomicInteger VALIDATION_PENDING = new AtomicInteger();
    private static final Map<String, NativeContentItem> PORTRAIT = new ConcurrentHashMap<>();
    private static final Map<String, String> MEDIA = new ConcurrentHashMap<>();
    private static final Set<String> NON_PORTRAIT = ConcurrentHashMap.newKeySet();
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> RETRY_AFTER = new ConcurrentHashMap<>();

    List<NativeContentItem> fetchPortraitFeed(int page) throws IOException {
        List<Candidate> candidates = parseListing(fetch(listingUrl(page), BASE));
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
            VALIDATION_IO.execute(() -> validate(candidate));
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

    CrazyShitRepository.StreamInfo resolvePlayable(String pageUrl) throws IOException {
        if (!isBaddieHubUrl(pageUrl)) return null;
        String media = MEDIA.get(pageUrl);
        String title = "";
        if (media == null || media.isEmpty()) {
            Resolved resolved = resolvePage(pageUrl);
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
            String host = uri.getHost();
            return host != null && (host.equalsIgnoreCase("baddiehub.com") ||
                    host.equalsIgnoreCase("www.baddiehub.com"));
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

    private void validate(Candidate candidate) {
        try {
            Resolved resolved = resolvePage(candidate.pageUrl);
            if (resolved.mediaUrl.isEmpty()) {
                retryLater(candidate.pageUrl);
                return;
            }
            Dimensions dimensions = mediaDimensions(resolved.mediaUrl, candidate.pageUrl);
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

    private Dimensions mediaDimensions(String mediaUrl, String referer) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            LinkedHashMap<String, String> headers = new LinkedHashMap<>();
            headers.put("User-Agent", USER_AGENT);
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

    private List<Candidate> parseListing(Document document) {
        LinkedHashMap<String, Candidate> found = new LinkedHashMap<>();
        if (document == null) return new ArrayList<>();
        for (Element anchor : document.select("a[href]")) {
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
            if (found.size() >= MAX_CANDIDATES) break;
        }
        return new ArrayList<>(found.values());
    }

    private Resolved resolvePage(String pageUrl) throws IOException {
        Document page = fetch(pageUrl, BASE);
        Element heading = page.selectFirst("h1");
        String title = clean(heading == null ? "" : heading.text());
        if (title.isEmpty()) title = clean(page.title());
        for (Element frame : page.select("iframe[src]")) {
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

    private Document fetch(String url, String referer) throws IOException {
        Connection connection = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .referrer(referer)
                .timeout(REQUEST_TIMEOUT_MS)
                .maxBodySize(8 * 1024 * 1024)
                .followRedirects(true)
                .ignoreHttpErrors(false)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9");
        try {
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.trim().isEmpty()) connection.header("Cookie", cookies);
        } catch (Exception ignored) {
        }
        return connection.get();
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

    private String listingUrl(int page) {
        int safe = Math.max(1, page);
        if (safe == 1) return BASE + "?filter=latest";
        return BASE + "page/" + safe + "/?filter=latest";
    }

    private boolean isVideoPage(String value) {
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
