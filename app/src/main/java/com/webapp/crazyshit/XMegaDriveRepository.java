package com.webapp.crazyshit;

import android.content.Context;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Isolated supplemental provider for the public XMegaDrive site.
 *
 * Compiled, not remote-config. Schema 1 has exactly seven configurable sources and does
 * not include XMegaDrive, so this provider stays in code. ShitTok scrapes public /videos/
 * listings. Shows discovers /categories/ and opens each category inside the existing
 * gallery. Pages are read in the WebView session that passed the site's browser check.
 * A direct client is not given that session's cookies or user agent. Playback resolves the
 * direct MP4 from the video page without changing the shared Media3 path. Failure is contained
 * so other providers continue.
 */
final class XMegaDriveRepository {
    static final String LABEL = "XMegaDrive";
    private static final String BASE = "https://www.xmegadrive.com/";
    private static final String LISTING = BASE + "videos/";
    private static final String CATEGORIES = BASE + "categories/";

    /** Device Chrome UA with the embedded-WebView marker removed. Client hints are left alone. */
    static String browserIdentity(String webViewUserAgent) {
        if (webViewUserAgent == null) return "";
        String agent = webViewUserAgent.trim();
        if (agent.isEmpty()) return "";
        agent = agent.replace("; wv)", ")").replace("; wv", "");
        agent = agent.replace("Version/4.0 ", "");
        return agent.replaceAll("\\s+", " ").trim();
    }

    /** Names only. Never returns cookie values. */
    static String cookiePresence(String cookieHeader) {
        return "cf_clearance=" + presence(cookieHeader, "cf_clearance")
                + " __cf_bm=" + presence(cookieHeader, "__cf_bm");
    }

    private static String presence(String header, String name) {
        if (header == null || name == null || name.isEmpty()) return "absent";
        for (String part : header.split(";")) {
            String trimmed = part.trim();
            int split = trimmed.indexOf('=');
            String key = split < 0 ? trimmed : trimmed.substring(0, split).trim();
            if (!name.equals(key)) continue;
            String value = split < 0 ? "" : trimmed.substring(split + 1).trim();
            return value.isEmpty() ? "absent" : "present";
        }
        return "absent";
    }

    private static final Pattern PLAYER_URL = Pattern.compile(
            "video_(?:url|alt_url\\d*)\\s*[:=]\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern LICENSE_CODE = Pattern.compile(
            "license_code\\s*[:=]\\s*['\"]([^'\"]+)['\"]");
    private static final String[] POPUNDER_HOSTS = {
            "chaturbate.com",
            "stripchat.com",
            "bongacams.com",
            "livejasmin.com",
            "cam4.com",
            "camsoda.com",
            "myfreecams.com",
            "streamate.com",
            "flirt4free.com",
            "exoclick.com",
            "exosrv.com",
            "exdynsrv.com",
            "trafficjunky.com",
            "trafficjunky.net",
            "juicyads.com",
            "adsterra.com",
            "popads.net",
            "popcash.net",
            "propellerads.com",
            "hilltopads.net",
            "clickadu.com",
            "onclicka.com",
            "tsyndicate.com",
            "trafficstars.com",
            "twinrdengine.com",
            "twinrdsrv.com",
            "realsrv.com",
            "clickaine.com",
            "adxpansion.com",
            "evadav.com",
            "kadam.net",
            "plugrush.com",
            "ero-advertising.com",
            "adcash.com",
            "adnxs.com",
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com"
    };
    private static final Pattern RAW_FILE = Pattern.compile(
            "https?://[^\\s'\"<>]+/get_file/[^\\s'\"<>]+\\.mp4[^\\s'\"<>]*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HASH_FILE = Pattern.compile(
            "https?://[^\\s'\"<>]*[0-9a-f]{32}\\.mp4[^\\s'\"<>]*",
            Pattern.CASE_INSENSITIVE);
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
        return isOwnHost(hostOf(value)) && "https".equalsIgnoreCase(schemeOf(value));
    }

    /** Main-frame navigations may stay on XMegaDrive or the Cloudflare check. Popunders may not. */
    static boolean allowsMainFrame(String url) {
        if (url == null) return false;
        String value = url.trim();
        if (value.isEmpty() || value.startsWith("about:blank")) return true;
        String host = hostOf(value);
        return isOwnHost(host) || isChallengeHost(host);
    }

    /** True for cam and popunder hosts. Never true for XMegaDrive itself. */
    static boolean isPopunder(String url) {
        String host = hostOf(url);
        if (host.isEmpty() || isOwnHost(host) || isChallengeHost(host)) return false;
        for (String blocked : POPUNDER_HOSTS) {
            if (host.equals(blocked) || host.endsWith("." + blocked)) return true;
        }
        return false;
    }

    private static boolean isOwnHost(String host) {
        return "xmegadrive.com".equals(host) || host.endsWith(".xmegadrive.com");
    }

    private static boolean isChallengeHost(String host) {
        return "challenges.cloudflare.com".equals(host)
                || host.endsWith(".challenges.cloudflare.com");
    }

    private static String schemeOf(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        try {
            String scheme = URI.create(value.trim()).getScheme();
            return scheme == null ? "" : scheme;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String hostOf(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        try {
            String host = URI.create(value.trim()).getHost();
            return host == null ? "" : host.toLowerCase(Locale.US);
        } catch (Exception ignored) {
            return "";
        }
    }

    static boolean isPlayablePage(String url) {
        return !normalizeVideoUrl(url).isEmpty();
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
        synchronized (BROWSER_FALLBACK) {
            Document rendered = XMegaDriveBrowserFetch.fetch(context, url, BASE);
            if (hasUsefulMarkup(rendered, url)) return rendered;
            throw new IOException("XMegaDrive stayed blocked after the browser check");
        }
    }

    private static boolean hasUsefulMarkup(Document document, String url) {
        if (document == null || looksBlocked(document) || ageGate(document)) return false;
        if (isPlayablePage(url)) return !extractMedia(document).isEmpty();
        LinkedHashMap<String, NativeContentItem> categories = new LinkedHashMap<>();
        parseCategories(document, categories);
        if (!categories.isEmpty()) return true;
        if (!parseVideos(document, 1).isEmpty()) return true;
        return !extractMedia(document).isEmpty();
    }

    private static boolean ageGate(Document document) {
        String text = document.text() == null ? "" : document.text().toLowerCase(Locale.US);
        return text.contains("age confirmation");
    }

    private static void parseCategories(Document document, LinkedHashMap<String, NativeContentItem> found) {
        if (document == null || found == null) return;
        for (Element anchor : document.select("a[href*=/categories/]")) {
            String canonical = normalizeCategoryUrl(anchor.absUrl("href"));
            if (canonical.isEmpty()) continue;
            String imageUrl = cardImageUrl(anchor);
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
                    cardImageUrl(anchor),
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

    private static String httpImage(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase(Locale.US);
        if (!(lower.startsWith("https://") || lower.startsWith("http://"))) return "";
        if (lower.contains("blank.gif")
                || lower.contains("placeholder")
                || lower.contains("noimage")
                || lower.contains("no-image")
                || lower.contains("no_image")
                || lower.contains("no-thumb")
                || lower.contains("nothumb")
                || lower.contains("/cdn-cgi/challenge")
                || lower.contains("/cdn-cgi/images/")
                || lower.contains("/cdn-cgi/styles/")
                || lower.endsWith(".gif")
                || lower.contains(".gif?")) {
            return "";
        }
        boolean image = lower.contains(".jpg")
                || lower.contains(".jpeg")
                || lower.contains(".png")
                || lower.contains(".webp")
                || lower.contains(".avif")
                || lower.contains("/contents/")
                || lower.contains("videos_screenshots");
        return image ? trimmed : "";
    }

    /** Photo on the anchor, or on the single card wrapped around that anchor. */
    private static String cardImageUrl(Element anchor) {
        if (anchor == null) return "";
        String direct = imageUrl(anchor.selectFirst("img"));
        if (!direct.isEmpty()) return direct;
        Element parent = anchor.parent();
        for (int depth = 0; depth < 4 && parent != null; depth++) {
            String tag = parent.tagName();
            if ("body".equalsIgnoreCase(tag) || "html".equalsIgnoreCase(tag)) break;
            if (parent.select("a[href*=/categories/], a[href*=/videos/]").size() > 4) break;
            String found = imageUrl(parent.selectFirst("img"));
            if (!found.isEmpty()) return found;
            parent = parent.parent();
        }
        return "";
    }

    private static String imageUrl(Element image) {
        if (image == null) return "";
        String background = httpImage(image.absUrl("data-bg"));
        if (!background.isEmpty()) return background;
        String original = httpImage(image.absUrl("data-original"));
        if (!original.isEmpty()) return original;
        String dataSrc = httpImage(image.absUrl("data-src"));
        if (!dataSrc.isEmpty()) return dataSrc;
        String lazy = httpImage(image.absUrl("data-lazy-src"));
        if (!lazy.isEmpty()) return lazy;
        return httpImage(image.absUrl("src"));
    }

    private static String extractMedia(Document page) {
        if (page == null) return "";
        String source = pageSource(page);
        String license = firstMatch(LICENSE_CODE, source);
        String location = page.location();
        String preferred = firstPlayerFile(source, license, location, true);
        if (!preferred.isEmpty()) return preferred;
        String raw = firstPatternFile(RAW_FILE, source);
        if (!raw.isEmpty()) return raw;
        String hash = firstPatternFile(HASH_FILE, source);
        if (!hash.isEmpty()) return hash;
        String tagged = firstTagFile(page, true);
        if (!tagged.isEmpty()) return tagged;
        String plain = firstPlayerFile(source, license, location, false);
        if (!plain.isEmpty()) return plain;
        return firstTagFile(page, false);
    }

    private static String pageSource(Document page) {
        StringBuilder blob = new StringBuilder(page.html() == null ? "" : page.html());
        for (Element script : page.select("script")) {
            blob.append('\n').append(script.data());
        }
        return blob.toString();
    }

    /** Flashvars first. A preroll video tag must not beat get_file or a 32-hex MP4. */
    private static String firstPlayerFile(
            String source, String license, String pageUrl, boolean preferredOnly) {
        Matcher playerUrls = PLAYER_URL.matcher(source);
        while (playerUrls.find()) {
            String decoded = ownMedia(decodePlayerUrl(playerUrls.group(1), license, pageUrl));
            if (decoded.isEmpty() || decoded.contains("function/0/") || isAdMedia(decoded)) continue;
            if (preferredOnly && !isPreferredFile(decoded)) continue;
            return decoded;
        }
        return "";
    }

    private static String firstPatternFile(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            String file = ownMedia(matcher.group());
            if (file.isEmpty() || file.contains("function/0/") || isAdMedia(file)) continue;
            return file;
        }
        return "";
    }

    private static String firstTagFile(Document page, boolean preferredOnly) {
        for (Element el : page.select("video[src], source[src], a[href]")) {
            boolean videoTag = "video".equalsIgnoreCase(el.tagName())
                    || "source".equalsIgnoreCase(el.tagName());
            if (!videoTag && preferredOnly == false) continue;
            String raw = el.hasAttr("src") ? el.absUrl("src") : el.absUrl("href");
            String src = ownMedia(raw);
            if (src.isEmpty() || src.contains("function/0/") || isAdMedia(src)) continue;
            if (preferredOnly && !isPreferredFile(src)) continue;
            if (!preferredOnly && !videoTag) continue;
            return src;
        }
        return "";
    }

    private static boolean isPreferredFile(String url) {
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains("/get_file/")) return true;
        return HASH_FILE.matcher(url).find();
    }

    /** On-site sponsor clips. The real file is the get_file or 32-hex MP4, not these. */
    private static boolean isAdMedia(String url) {
        String lower = url.toLowerCase(Locale.US);
        return lower.contains("preroll")
                || lower.contains("/ads/")
                || lower.contains("/ad/")
                || lower.contains("vast")
                || lower.contains("advert")
                || lower.contains("chaturbate")
                || lower.contains("exoclick")
                || lower.contains("trafficjunky")
                || lower.contains("popunder");
    }

    private static String ownMedia(String url) {
        if (url == null || url.isEmpty() || !isOwnHost(hostOf(url))) return "";
        String lower = url.toLowerCase(Locale.US);
        if (!(lower.startsWith("https://") || lower.startsWith("http://"))) return "";
        if (lower.contains("/get_file/") || lower.contains(".mp4") || lower.contains(".m3u8")) {
            return url;
        }
        return "";
    }

    static String decodePlayerUrl(String videoUrl, String licenseCode, String pageUrl) {
        if (videoUrl == null) return "";
        String value = videoUrl.trim().replace("\\/", "/").replace("&", "&");
        if (value.startsWith("function/0/")) value = value.substring("function/0/".length());
        if (value.startsWith("//")) value = "https:" + value;
        if (value.startsWith("/")) {
            String base = pageUrl == null || pageUrl.isEmpty() ? BASE : pageUrl;
            try {
                URI page = URI.create(base);
                value = page.getScheme() + "://" + page.getHost() + value;
            } catch (Exception ignored) {
                return "";
            }
        }
        if (!value.contains("/get_file/")) return value.startsWith("http") ? value : "";
        if (licenseCode == null || licenseCode.isEmpty() || !videoUrl.contains("function/0/")) {
            return value;
        }
        try {
            URI uri = URI.create(value);
            String path = uri.getPath();
            if (path == null) return "";
            String[] parts = path.split("/", -1);
            if (parts.length < 4 || parts[3].length() < 32) return value;
            int[] token = licenseToken(licenseCode);
            if (token.length < 32) return "";
            int[] indices = new int[32];
            for (int index = 0; index < 32; index++) indices[index] = index;
            int accum = 0;
            for (int src = 31; src >= 0; src--) {
                accum += token[src];
                int dest = (src + accum) % 32;
                int swap = indices[src];
                indices[src] = indices[dest];
                indices[dest] = swap;
            }
            String hash = parts[3].substring(0, 32);
            StringBuilder rebuilt = new StringBuilder();
            for (int index : indices) rebuilt.append(hash.charAt(index));
            rebuilt.append(parts[3].substring(32));
            parts[3] = rebuilt.toString();
            StringBuilder newPath = new StringBuilder();
            for (int index = 0; index < parts.length; index++) {
                if (index > 0) newPath.append('/');
                newPath.append(parts[index]);
            }
            return new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    newPath.toString(),
                    uri.getQuery(),
                    uri.getFragment()
            ).toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static int[] licenseToken(String licenseCode) {
        String code = licenseCode.replace("$", "").trim();
        if (code.isEmpty() || !code.chars().allMatch(Character::isDigit)) return new int[0];
        String mod = code.replace('0', '1');
        int center = mod.length() / 2;
        java.math.BigInteger front = new java.math.BigInteger(mod.substring(0, center + 1));
        java.math.BigInteger back = new java.math.BigInteger(mod.substring(center));
        String diff = front.subtract(back).abs()
                .multiply(java.math.BigInteger.valueOf(4))
                .toString();
        int take = Math.min(center + 1, diff.length());
        String folded = diff.substring(0, take);
        int[] values = new int[code.length()];
        for (int index = 0; index < code.length(); index++) {
            values[index] = code.charAt(index) - '0';
        }
        int[] token = new int[folded.length() * 4];
        int cursor = 0;
        for (int index = 0; index < folded.length(); index++) {
            int current = folded.charAt(index) - '0';
            for (int offset = 0; offset < 4; offset++) {
                int valueIndex = index + offset;
                int base = valueIndex < values.length ? values[valueIndex] : 0;
                token[cursor++] = (base + current) % 10;
            }
        }
        return token;
    }

    private static String firstMatch(Pattern pattern, String source) {
        if (source == null) return "";
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? matcher.group(1) : "";
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
