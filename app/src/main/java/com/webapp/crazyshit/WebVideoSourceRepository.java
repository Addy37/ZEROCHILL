package com.webapp.crazyshit;

import android.content.Context;
import android.webkit.CookieManager;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared native feed and playback parser for lightweight public video sources. */
final class WebVideoSourceRepository {
    private final Map<String, String> kaoticPaginationTemplates = new java.util.concurrent.ConcurrentHashMap<>();

    enum Source {
        KAOTIC("Kaotic");

        final String label;

        Source(String label) {
            this.label = label;
        }
    }

    private static final Pattern NUMBER = Pattern.compile(
            "(?i)(?:\\b\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?[KMB]?\\b|\\b\\d+(?:\\.\\d+)?[KMB]\\b)"
    );
    private static final Pattern KAOTIC_SECTION = Pattern.compile(
            "(?i)^(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\s+"
                    + "(?:january|february|march|april|may|june|july|august|september|october|november|december)\\s+"
                    + "\\d{1,2}(?:st|nd|rd|th)?(?:,?\\s+\\d{4})?$"
    );

    List<NativeContentItem> fetchFeed(Context context, Source source, int page) throws IOException {
        SourceConfig.WebVideo config = config(source);
        if (!config.enabled) throw new IOException(source.label + " is temporarily unavailable");
        String route = page <= 1 ? config.feedFirstRoute : config.feedPageRoute;
        route = route.replace("{page}", String.valueOf(Math.max(1, page)));
        String requested = config.baseUrl + route;

        Document document = null;
        IOException directFailure = null;
        try {
            document = fetchConfigured(context, config, requested);
        } catch (IOException error) {
            directFailure = error;
        }

        List<NativeContentItem> result = document == null
                ? new ArrayList<>()
                : parseFeed(document, source, page, config);

        if (page <= 1 && result.isEmpty()) {
            throw directFailure == null
                    ? new IOException(source.label + " returned no playable feed items")
                    : new IOException(source.label + " could not load its feed", directFailure);
        }
        return result;
    }

    List<NativeContentItem> fetchCategories(Context context, Source source) throws IOException {
        if (source != Source.KAOTIC) return new ArrayList<>();
        SourceConfig.WebVideo config = config(source);
        if (!config.enabled) throw new IOException(source.label + " is temporarily unavailable");

        Document home = fetchConfigured(context, config, config.baseUrl);
        String categoriesUrl = "";
        for (Element link : home.select("a[href]")) {
            if (!"categories".equalsIgnoreCase(clean(link.text()))) continue;
            String candidate = absolute(link, "href", home.location());
            if (sameHost(config.baseUrl, candidate)) {
                categoriesUrl = candidate;
                break;
            }
        }
        if (categoriesUrl.isEmpty()) {
            throw new IOException("Kaotic categories directory was not found");
        }

        Document categories = fetchConfigured(context, config, categoriesUrl);
        return parseKaoticCategories(categories);
    }

    List<NativeContentItem> fetchFeed(
            Context context,
            Source source,
            String collectionUrl,
            int page
    ) throws IOException {
        if (source != Source.KAOTIC) return fetchFeed(context, source, page);
        SourceConfig.WebVideo config = config(source);
        if (!config.enabled) throw new IOException(source.label + " is temporarily unavailable");

        String base = cleanUrl(collectionUrl);
        if (base.isEmpty()) throw new IOException("Kaotic category URL is empty");

        int requestedPage = Math.max(1, page);
        String requested = base;
        Document document = null;
        if (requestedPage > 1) {
            String template = kaoticPaginationTemplates.get(base);
            if (template == null || template.isEmpty()) {
                Document first = fetchConfigured(context, config, base);
                rememberKaoticPaginationTemplate(base, first);
                template = kaoticPaginationTemplates.get(base);
            }
            if (template == null || template.isEmpty()) return new ArrayList<>();
            requested = template.replace("{page}", String.valueOf(requestedPage));
        }

        document = fetchConfigured(context, config, requested);
        if (requestedPage == 1) rememberKaoticPaginationTemplate(base, document);
        return parseFeed(document, source, requestedPage, config);
    }

    /**
     * Kaotic has no remote search route. The path is compiled. The origin, headers, timeouts,
     * retries and card parser all come from the current Kaotic configuration. At most one search
     * request, then the first configured feed page if that request misses.
     */
    static String kaoticSearchUrl(String baseUrl, String query) {
        String value = query == null ? "" : query.trim();
        if (value.length() < 2 || baseUrl == null || baseUrl.trim().isEmpty()) return "";
        try {
            String encoded = URLEncoder.encode(value, "UTF-8").replace("+", "%20");
            return URI.create(baseUrl).resolve("search/" + encoded).toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    List<NativeContentItem> search(Context context, String query) throws IOException {
        SourceConfig.WebVideo config = config(Source.KAOTIC);
        if (!config.enabled) throw new IOException(Source.KAOTIC.label + " is temporarily unavailable");
        String value = query == null ? "" : query.trim();
        if (value.length() < 2) return new ArrayList<>();
        if (Thread.currentThread().isInterrupted()) throw new IOException("Kaotic search was cancelled");
        String url = kaoticSearchUrl(config.baseUrl, value);
        ArrayList<NativeContentItem> found = new ArrayList<>();
        IOException failure = null;
        if (!url.isEmpty()) {
            try {
                addKaoticMatches(found, parseFeed(
                        fetchConfigured(context, config, url), Source.KAOTIC, 1, config), value);
            } catch (IOException error) {
                failure = error;
            }
        }
        if (!found.isEmpty()) return found;
        if (Thread.currentThread().isInterrupted()) {
            throw failure == null ? new IOException("Kaotic search was cancelled") : failure;
        }
        try {
            addKaoticMatches(found, fetchFeed(context, Source.KAOTIC, 1), value);
        } catch (IOException error) {
            if (failure != null) throw failure;
            throw error;
        }
        return found;
    }

    private static void addKaoticMatches(
            List<NativeContentItem> found,
            List<NativeContentItem> items,
            String query
    ) {
        if (items == null) return;
        for (NativeContentItem item : items) {
            if (item == null || item.isSection()) continue;
            if (!LibrarySearch.matches(query, item.title, item.url, item.description, item.uploader)) continue;
            found.add(item);
            if (found.size() >= 40) return;
        }
    }

    List<NativeContentItem> parseKaoticCategories(Document document) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        if (document == null) return result;

        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (Element heading : document.select("h2")) {
            Element link = heading.selectFirst("a[href]");
            if (link == null) continue;
            String url = absolute(link, "href", document.location());
            String title = clean(link.text());
            if (url.isEmpty() || title.isEmpty() || !sameHost(document.location(), url)) continue;
            if (!seen.add(url)) continue;

            result.add(new NativeContentItem(
                    NativeContentItem.KIND_CATEGORY,
                    title,
                    url,
                    image(link, cardScope(link), document.location()),
                    "",
                    Source.KAOTIC.label,
                    ""
            ));
            if (result.size() >= 40) break;
        }
        return result;
    }

    private void rememberKaoticPaginationTemplate(String baseUrl, Document document) {
        if (baseUrl == null || baseUrl.isEmpty() || document == null) return;
        int bestPage = Integer.MAX_VALUE;
        String bestUrl = "";
        for (Element link : document.select("a[href]")) {
            String label = clean(link.text());
            if (!label.matches("\\d+")) continue;
            int page;
            try {
                page = Integer.parseInt(label);
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (page <= 1 || page >= bestPage) continue;
            String candidate = absolute(link, "href", document.location());
            if (!sameHost(baseUrl, candidate)) continue;
            String template = paginationTemplate(candidate, page);
            if (template.isEmpty()) continue;
            bestPage = page;
            bestUrl = template;
        }
        if (!bestUrl.isEmpty()) kaoticPaginationTemplates.put(baseUrl, bestUrl);
    }

    private String paginationTemplate(String pageUrl, int pageNumber) {
        try {
            URI uri = new URI(pageUrl);
            String query = uri.getRawQuery();
            if (query == null || query.isEmpty()) return "";
            String[] parts = query.split("&");
            boolean replaced = false;
            for (int index = 0; index < parts.length; index++) {
                int equals = parts[index].indexOf('=');
                if (equals <= 0) continue;
                String value = parts[index].substring(equals + 1);
                if (!String.valueOf(pageNumber).equals(value)) continue;
                parts[index] = parts[index].substring(0, equals + 1) + "__ZEROCHILL_PAGE__";
                replaced = true;
                break;
            }
            if (!replaced) return "";
            String rebuiltQuery = String.join("&", parts);
            String rebuilt = new URI(
                    uri.getScheme(),
                    uri.getAuthority(),
                    uri.getPath(),
                    rebuiltQuery,
                    uri.getFragment()
            ).toASCIIString();
            return rebuilt.replace("__ZEROCHILL_PAGE__", "{page}");
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean sameHost(String firstUrl, String secondUrl) {
        try {
            URI first = new URI(firstUrl);
            URI second = new URI(secondUrl);
            return first.getHost() != null && second.getHost() != null
                    && first.getHost().equalsIgnoreCase(second.getHost());
        } catch (Exception ignored) {
            return false;
        }
    }

    List<NativeContentItem> parseFeed(Document document, Source source, int page) {
        return parseFeed(document, source, page, config(source));
    }

    private List<NativeContentItem> parseFeed(
            Document document,
            Source source,
            int page,
            SourceConfig.WebVideo config
    ) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        LinkedHashMap<String, Integer> positions = new LinkedHashMap<>();
        String pendingHeader = "";
        long currentPublishedAt = 0L;
        int sectionOrdinal = 0;
        int mediaCount = 0;

        for (Element node : document.getAllElements()) {
            String tag = node.tagName();
            if (!"a".equalsIgnoreCase(tag)) {
                if (source == Source.KAOTIC) {
                    String section = kaoticSection(node);
                    if (!section.isEmpty()) {
                        pendingHeader = section;
                        currentPublishedAt = SourcePublishedDate.parse(
                                section,
                                System.currentTimeMillis()
                        );
                    }
                }
                continue;
            }

            if (!node.is(config.cardLinksSelector)) continue;
            String pageUrl = absolute(node, "href", document.location());
            if (pageUrl.isEmpty() || !config.pageUrlPattern.matcher(pageUrl).find()) continue;

            Element scope = cardScope(node);
            String title = title(node, scope, pageUrl);
            if (title.length() < 2) continue;
            String image = image(node, scope, document.location());
            String views = views(scope);
            long publishedAt = source == Source.KAOTIC
                    ? currentPublishedAt
                    : SourcePublishedDate.parse(
                            scope == null ? "" : scope.text(),
                            System.currentTimeMillis()
                    );
            NativeContentItem candidate = new NativeContentItem(
                    NativeContentItem.KIND_MEDIA,
                    title,
                    pageUrl,
                    image,
                    views,
                    source.label,
                    "",
                    "",
                    "",
                    publishedAt
            );

            String feedKey = pageUrl;
            Integer oldPosition = positions.get(feedKey);
            if (oldPosition != null) {
                NativeContentItem old = result.get(oldPosition);
                result.set(oldPosition, old.merge(candidate));
                continue;
            }

            if (source == Source.KAOTIC && !pendingHeader.isEmpty()) {
                result.add(new NativeContentItem(
                        NativeContentItem.KIND_SECTION,
                        pendingHeader,
                        "section:kaotic:" + Math.max(1, page) + ":" + (++sectionOrdinal)
                                + ":" + slug(pendingHeader),
                        "",
                        "",
                        "",
                        ""
                ));
                pendingHeader = "";
            }

            positions.put(feedKey, result.size());
            result.add(candidate);
            mediaCount++;
            if (mediaCount >= 60) break;
        }
        return result;
    }

    private String kaoticSection(Element element) {
        if (element == null) return "";
        String own = clean(element.ownText());
        if (KAOTIC_SECTION.matcher(own).matches()) return own;
        String tag = element.tagName().toLowerCase(Locale.US);
        if (tag.matches("h[1-6]")) {
            String text = clean(element.text());
            if (KAOTIC_SECTION.matcher(text).matches()) return text;
        }
        return "";
    }

    private String slug(String value) {
        String text = clean(value).toLowerCase(Locale.US).replace('’', '\'');
        text = text.replaceAll("[^a-z0-9]+", "-");
        return text.replaceAll("(^-+|-+$)", "");
    }

    CrazyShitRepository.StreamInfo resolvePlayable(
            Context context,
            Source source,
            String pageUrl
    ) throws IOException {
        return resolvePlayable(context, source, pageUrl, "");
    }

    CrazyShitRepository.StreamInfo resolvePlayable(
            Context context,
            Source source,
            String pageUrl,
            String thumbnailUrl
    ) throws IOException {
        if (isDirectMedia(pageUrl)) {
            return new CrazyShitRepository.StreamInfo(pageUrl, pageUrl, source.label);
        }
        SourceConfig.WebVideo config = config(source);
        if (!config.enabled) throw new IOException(source.label + " is temporarily unavailable");
        Document page = fetchConfigured(context, config, pageUrl);
        String title = clean(page.title());
        String media = playableFromDocument(page, config);
        if (media.isEmpty()) {
            int checked = 0;
            for (Element frame : page.select("iframe[src]")) {
                if (++checked > 4) break;
                String frameUrl = absolute(frame, "src", page.location());
                if (!frameUrl.startsWith("https://")) continue;
                try {
                    Document framePage = fetchDocument(context, config, frameUrl, pageUrl);
                    media = playableFromDocument(framePage, config);
                    if (!media.isEmpty()) break;
                } catch (IOException ignored) {
                }
            }
        }
        return media.isEmpty()
                ? null
                : new CrazyShitRepository.StreamInfo(media, pageUrl, title.isEmpty() ? source.label : title);
    }

    private String playableFromDocument(Document document, SourceConfig.WebVideo config) {
        for (Element element : document.select(config.playableVideoSelector)) {
            String attr = element.hasAttr("src") ? "src" : "content";
            String candidate = absolute(element, attr, document.location());
            if (isDirectMedia(candidate)) return candidate;
        }
        for (Element script : document.select("script")) {
            String body = script.data();
            if (body == null || body.isEmpty()) body = script.html();
            if (body == null || body.isEmpty()) continue;
            body = body.replace("\\/", "/").replace("&amp;", "&");
            Matcher matcher = config.scriptMediaUrlPattern.matcher(body);
            while (matcher.find()) {
                String candidate = cleanUrl(matcher.group());
                if (isDirectMedia(candidate)) return candidate;
            }
        }
        return "";
    }

    private Document fetchConfigured(
            Context context,
            SourceConfig.WebVideo config,
            String requested
    ) throws IOException {
        IOException failure = null;
        for (String candidate : candidates(config, requested)) {
            for (int attempt = 0; attempt <= config.retryCount; attempt++) {
                try {
                    return fetchDocument(context, config, candidate, config.baseUrl);
                } catch (IOException error) {
                    failure = error;
                }
            }
        }
        throw failure == null ? new IOException("Source request failed") : failure;
    }

    private Document fetchDocument(
            Context context,
            SourceConfig.WebVideo config,
            String url,
            String referer
    ) throws IOException {
        Connection connection = Jsoup.connect(url)
                .userAgent(config.userAgent)
                .referrer(config.refererOverride.isEmpty() ? referer : config.refererOverride)
                .timeout(config.requestTimeoutMs)
                .maxBodySize(8 * 1024 * 1024)
                .followRedirects(true)
                .ignoreHttpErrors(false);
        for (Map.Entry<String, String> header : config.requestHeaders.entrySet()) {
            connection.header(header.getKey(), header.getValue());
        }
        try {
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.trim().isEmpty()) connection.header("Cookie", cookies);
        } catch (Exception ignored) {
        }
        return connection.get();
    }

    private List<String> candidates(SourceConfig.WebVideo config, String requested) {
        ArrayList<String> values = new ArrayList<>();
        values.add(requested);
        if (!RemoteSourceConfigManager.snapshot().fallbacksEnabled) return values;
        try {
            URI requestedUri = new URI(requested);
            URI baseUri = new URI(config.baseUrl);
            if (!baseUri.getHost().equalsIgnoreCase(requestedUri.getHost())) return values;
            String suffix = requestedUri.getRawPath();
            if (requestedUri.getRawQuery() != null) suffix += "?" + requestedUri.getRawQuery();
            for (String fallback : config.fallbackDomains) {
                values.add(fallback + (suffix.startsWith("/") ? suffix.substring(1) : suffix));
            }
        } catch (Exception ignored) {
        }
        return values;
    }

    private SourceConfig.WebVideo config(Source source) {
        SourceConfig current = RemoteSourceConfigManager.snapshot();
        return current.kaotic;
    }

    private Element cardScope(Element link) {
        Element best = link;
        Element current = link;
        for (int depth = 0; depth < 7 && current != null; depth++) {
            current = current.parent();
            if (current == null) break;
            String text = clean(current.text());
            boolean visual = !current.select("img,video[poster],source,[data-src],[data-lazy-src]").isEmpty();
            boolean heading = !current.select("h1,h2,h3,h4,h5,.title,.post-title,.video-title").isEmpty();
            if ((visual || heading) && text.length() > 2 && text.length() < 1500) {
                best = current;
                if (visual && heading) break;
            }
        }
        return best;
    }

    private String title(Element link, Element scope, String pageUrl) {
        String[] direct = {link.attr("title"), link.attr("aria-label"), link.ownText(), link.text()};
        for (String value : direct) {
            String candidate = clean(value);
            if (usefulTitle(candidate)) return candidate;
        }
        if (scope != null) {
            for (Element heading : scope.select("h1,h2,h3,h4,h5,.title,.post-title,.video-title")) {
                String candidate = clean(heading.text());
                if (usefulTitle(candidate)) return candidate;
            }
            Element image = scope.selectFirst("img[alt]");
            if (image != null && usefulTitle(clean(image.attr("alt")))) return clean(image.attr("alt"));
        }
        try {
            String path = new URI(pageUrl).getPath();
            if (path != null) {
                String[] pieces = path.split("/");
                for (int index = pieces.length - 1; index >= 0; index--) {
                    String part = pieces[index].replace('-', ' ').replace('_', ' ').trim();
                    if (part.length() >= 3 && !part.matches("\\d+")) return part;
                }
            }
        } catch (Exception ignored) {
        }
        return "Video";
    }

    private String image(Element link, Element scope, String base) {
        Element[] roots = scope == null ? new Element[]{link} : new Element[]{link, scope};
        for (Element root : roots) {
            for (Element visual : root.select("img,video[poster],source,[data-src],[data-lazy-src],[data-original]")) {
                String[] attrs = {"poster", "data-src", "data-lazy-src", "data-original", "src"};
                for (String attr : attrs) {
                    String candidate = absolute(visual, attr, base);
                    if (looksImage(candidate)) return candidate;
                }
                String srcset = visual.attr("srcset");
                if (!srcset.isEmpty()) {
                    String first = srcset.split(",")[0].trim().split("\\s+")[0];
                    String candidate = absolute(base, first);
                    if (looksImage(candidate)) return candidate;
                }
            }
        }
        return "";
    }

    private String views(Element scope) {
        if (scope == null) return "";
        String text = clean(scope.text());
        Matcher matcher = Pattern.compile("(?i)([0-9][0-9,.]*[KMB]?)\\s+views?").matcher(text);
        if (matcher.find()) return matcher.group(1);
        matcher = NUMBER.matcher(text);
        return matcher.find() && text.toLowerCase(Locale.US).contains("view") ? matcher.group() : "";
    }

    private boolean usefulTitle(String value) {
        if (value == null || value.length() < 2 || value.length() > 240) return false;
        String lower = value.toLowerCase(Locale.US);
        return !(lower.equals("video") || lower.equals("watch") || lower.equals("play") ||
                lower.equals("read more") || lower.equals("view"));
    }

    private String absolute(Element element, String attr, String base) {
        if (element == null || !element.hasAttr(attr)) return "";
        String value = element.absUrl(attr);
        if (!value.isEmpty()) return cleanUrl(value);
        return absolute(base, element.attr(attr));
    }

    private String absolute(String base, String value) {
        String clean = cleanUrl(value);
        if (clean.isEmpty()) return "";
        try {
            return new URI(base).resolve(clean).toASCIIString();
        } catch (Exception ignored) {
            return clean;
        }
    }

    private boolean looksImage(String url) {
        String value = cleanUrl(url).toLowerCase(Locale.US);
        return value.matches(".*\\.(?:jpg|jpeg|png|webp|gif|avif)(?:\\?.*)?$");
    }

    private boolean isDirectMedia(String url) {
        String value = cleanUrl(url).toLowerCase(Locale.US);
        return value.matches(".*\\.(?:mp4|m3u8|mpd|webm|m4v)(?:\\?.*)?$");
    }

    static boolean isKaoticUrl(String url) {
        return hostMatches(url, "kaotic.com");
    }

    private static boolean hostMatches(String url, String host) {
        try {
            String actual = new URI(url == null ? "" : url).getHost();
            return actual != null && (actual.equalsIgnoreCase(host) ||
                    actual.toLowerCase(Locale.US).endsWith("." + host));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String cleanUrl(String value) {
        if (value == null) return "";
        return value.trim().replace("&amp;", "&").replace("\\/", "/");
    }
}
