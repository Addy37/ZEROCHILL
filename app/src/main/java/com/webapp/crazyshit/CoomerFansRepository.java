package com.webapp.crazyshit;

import android.content.Context;
import android.webkit.CookieManager;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Low-priority, images-only creator fallback backed by CoomerFans. */
final class CoomerFansRepository {
    static final String BASE = "https://coomerfans.com/";

    static final class Creator {
        final String service;
        final String id;
        final String username;
        final String name;
        final String url;
        final String imageUrl;

        Creator(String service, String id, String username, String name, String url, String imageUrl) {
            this.service = cleanStatic(service);
            this.id = cleanStatic(id);
            this.username = cleanStatic(username);
            this.name = cleanStatic(name);
            this.url = cleanStatic(url);
            this.imageUrl = cleanStatic(imageUrl);
        }
    }

    static final class ProfilePage {
        final ArrayList<String> postUrls;
        final boolean hasNext;

        ProfilePage(ArrayList<String> postUrls, boolean hasNext) {
            this.postUrls = postUrls;
            this.hasNext = hasNext;
        }
    }

    List<Creator> searchCreators(Context context, String query, int limit) throws IOException {
        SourceConfig.CoomerFans config = config();
        if (!config.enabled) throw new IOException("CoomerFans is temporarily unavailable");
        String value = cleanStatic(query);
        if (value.length() < 2) return new ArrayList<>();
        String encoded = URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        String route = config.creatorSearchRoute.replace("{query}", encoded);
        Document document = fetchConfigured(context, config, config.baseUrl + route);
        return parseCreators(document, config, value, Math.max(1, Math.min(4, limit)));
    }

    ProfilePage fetchCreatorPosts(
            Context context,
            Creator creator,
            int page,
            int limit
    ) throws IOException {
        if (creator == null || creator.service.isEmpty() || creator.id.isEmpty() ||
                creator.username.isEmpty()) {
            return new ProfilePage(new ArrayList<>(), false);
        }
        SourceConfig.CoomerFans config = config();
        if (!config.enabled) throw new IOException("CoomerFans is temporarily unavailable");
        int safePage = Math.max(1, page);
        String route = config.creatorPageRoute
                .replace("{service}", urlToken(creator.service))
                .replace("{id}", urlToken(creator.id))
                .replace("{username}", urlToken(creator.username))
                .replace("{page}", String.valueOf(safePage));
        Document document = fetchConfigured(context, config, config.baseUrl + route);
        ArrayList<String> posts = parseProfilePostUrls(
                document, Math.max(1, limit));
        return new ProfilePage(posts, !posts.isEmpty());
    }

    ArrayList<NativeContentItem> fetchPostImages(
            Context context,
            Creator creator,
            String postUrl,
            int limit
    ) throws IOException {
        if (creator == null || !isPostUrl(postUrl)) return new ArrayList<>();
        SourceConfig.CoomerFans config = config();
        if (!config.enabled) throw new IOException("CoomerFans is temporarily unavailable");
        Document document = fetchConfigured(context, config, postUrl);
        return parseCreatorImages(document, config, creator, Math.max(1, limit));
    }

    ArrayList<String> parseProfilePostUrls(Document document, int limit) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (document == null) return new ArrayList<>();
        for (Element link : document.select("a[href^=/p/]")) {
            String url = absolute(link, "href", document.location());
            if (!isPostUrl(url)) continue;
            result.putIfAbsent(url, url);
            if (result.size() >= limit) break;
        }
        return new ArrayList<>(result.values());
    }

    List<Creator> parseCreators(
            Document document,
            SourceConfig.CoomerFans config,
            String query,
            int limit
    ) {
        LinkedHashMap<String, Creator> exact = new LinkedHashMap<>();
        LinkedHashMap<String, Creator> fallback = new LinkedHashMap<>();
        if (document == null || config == null) return new ArrayList<>();
        for (Element link : document.select(config.creatorLinksSelector)) {
            String url = absolute(link, "href", document.location());
            Creator parsed = parseCreator(url, link, document.location());
            if (parsed == null) continue;
            String key = parsed.service.toLowerCase(Locale.US) + ":" +
                    parsed.id.toLowerCase(Locale.US);
            if (matchesQuery(parsed.name, parsed.username, query)) {
                exact.putIfAbsent(key, parsed);
            } else {
                // The server already filtered this page by query. Keep returned aliases as a
                // bounded fallback instead of rejecting profiles whose public handle changed.
                fallback.putIfAbsent(key, parsed);
            }
            if (exact.size() >= limit) break;
        }
        LinkedHashMap<String, Creator> result = exact.isEmpty() ? fallback : exact;
        ArrayList<Creator> values = new ArrayList<>();
        for (Creator creator : result.values()) {
            values.add(creator);
            if (values.size() >= limit) break;
        }
        return values;
    }

    ArrayList<NativeContentItem> parseCreatorImages(
            Document document,
            SourceConfig.CoomerFans config,
            Creator creator,
            int limit
    ) {
        LinkedHashMap<String, NativeContentItem> result = new LinkedHashMap<>();
        if (document == null || config == null || creator == null) {
            return new ArrayList<>();
        }
        for (Element media : document.select(config.profileImagesSelector)) {
            for (String attr : new String[]{"src", "data-src", "poster", "href", "srcset"}) {
                if (!media.hasAttr(attr)) continue;
                String raw = media.attr(attr);
                if ("srcset".equals(attr)) {
                    for (String part : raw.split(",")) {
                        String candidate = part.trim().split("\\s+")[0];
                        addImageCandidate(result, candidate, document.location(), config, creator);
                        if (result.size() >= limit) break;
                    }
                } else {
                    addImageCandidate(result, raw, document.location(), config, creator);
                }
                if (result.size() >= limit) break;
            }
            if (result.size() >= limit) break;
        }
        return new ArrayList<>(result.values());
    }

    private void addImageCandidate(
            LinkedHashMap<String, NativeContentItem> result,
            String raw,
            String base,
            SourceConfig.CoomerFans config,
            Creator creator
    ) {
        String url = absolute(base, raw);
        if (!isContentImageUrl(config, url)) return;
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains("/istorage/") || lower.contains("/avatar") ||
                lower.contains("/profile") || lower.contains("/logo") ||
                lower.contains("/icon")) {
            return;
        }
        String title = creator.name.isEmpty() ? creator.username : creator.name;
        result.putIfAbsent(url, new NativeContentItem(
                NativeContentItem.KIND_IMAGE,
                title,
                url,
                url,
                "CoomerFans",
                creator.url,
                "",
                creator.service + " · CoomerFans"
        ));
    }

    private Creator parseCreator(String url, Element link, String base) {
        try {
            URI uri = new URI(url);
            if (!isCoomerFansUrl(url)) return null;
            String[] raw = uri.getPath() == null ? new String[0] : uri.getPath().split("/");
            ArrayList<String> parts = new ArrayList<>();
            for (String part : raw) if (!cleanStatic(part).isEmpty()) parts.add(cleanStatic(part));
            if (parts.size() < 4 || !"u".equalsIgnoreCase(parts.get(0))) return null;
            String service = decode(parts.get(1));
            String id = decode(parts.get(2));
            String username = decode(parts.get(3));
            if (service.isEmpty() || id.isEmpty() || username.isEmpty()) return null;
            Element scope = cardScope(link);
            String name = firstUseful(
                    link == null ? "" : link.attr("title"),
                    link == null ? "" : link.attr("aria-label"),
                    heading(scope),
                    compactCardText(scope),
                    username.replace('_', ' ')
            );
            String image = "";
            if (scope != null) {
                Element img = scope.selectFirst("img[src]");
                if (img != null) {
                    String candidate = absolute(img, "src", base);
                    if (isImageCdnUrl(config(), candidate)) image = candidate;
                }
            }
            return new Creator(service, id, username, name, url, image);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean matchesQuery(String name, String username, String query) {
        if (CreatorNameMatcher.rank(name, query) != Integer.MAX_VALUE ||
                CreatorNameMatcher.rank(username, query) != Integer.MAX_VALUE) {
            return true;
        }
        String needle = CreatorNameMatcher.normalized(query).replace(" ", "");
        String named = CreatorNameMatcher.normalized(name).replace(" ", "");
        String handle = CreatorNameMatcher.normalized(username).replace(" ", "");
        return oneEditApart(named, needle) || oneEditApart(handle, needle);
    }

    private boolean oneEditApart(String left, String right) {
        if (left == null || right == null || left.length() < 5 || right.length() < 5 ||
                Math.abs(left.length() - right.length()) > 1) {
            return false;
        }
        int i = 0;
        int j = 0;
        int edits = 0;
        while (i < left.length() && j < right.length()) {
            if (left.charAt(i) == right.charAt(j)) {
                i++;
                j++;
                continue;
            }
            if (++edits > 1) return false;
            if (left.length() > right.length()) i++;
            else if (right.length() > left.length()) j++;
            else {
                i++;
                j++;
            }
        }
        if (i < left.length() || j < right.length()) edits++;
        return edits <= 1;
    }

    private boolean hasNextPage(Document document, int page) {
        if (document == null) return false;
        if (document.selectFirst("a[rel=next][href]") != null) return true;
        int wanted = page + 1;
        for (Element link : document.select("a[href*='page=']")) {
            String href = link.attr("href");
            if (href.matches("(?i).*?[?&]page=" + wanted + "(?:&.*)?$")) return true;
        }
        return false;
    }

    private Document fetchConfigured(
            Context context,
            SourceConfig.CoomerFans config,
            String requested
    ) throws IOException {
        IOException failure = null;
        for (String candidate : candidates(config, requested)) {
            for (int attempt = 0; attempt <= config.retryCount; attempt++) {
                try {
                    Connection connection = Jsoup.connect(candidate)
                            .userAgent(config.userAgent)
                            .referrer(config.refererOverride.isEmpty()
                                    ? config.baseUrl : config.refererOverride)
                            .timeout(config.requestTimeoutMs)
                            .maxBodySize(8 * 1024 * 1024)
                            .followRedirects(true)
                            .ignoreHttpErrors(false);
                    for (Map.Entry<String, String> header : config.requestHeaders.entrySet()) {
                        connection.header(header.getKey(), header.getValue());
                    }
                    try {
                        String cookies = CookieManager.getInstance().getCookie(candidate);
                        if (cookies != null && !cookies.trim().isEmpty()) {
                            connection.header("Cookie", cookies);
                        }
                    } catch (Exception ignored) {
                    }
                    return connection.get();
                } catch (IOException error) {
                    failure = error;
                }
            }
        }
        throw failure == null ? new IOException("CoomerFans request failed") : failure;
    }

    private List<String> candidates(SourceConfig.CoomerFans config, String requested) {
        ArrayList<String> values = new ArrayList<>();
        values.add(requested);
        SourceConfig current = RemoteSourceConfigManager.snapshotOrNull();
        if (current == null || !current.fallbacksEnabled) return values;
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

    private SourceConfig.CoomerFans config() {
        return RemoteSourceConfigManager.snapshot().coomerFans;
    }

    static boolean isCoomerFansUrl(String value) {
        String host = host(value);
        if (host.isEmpty()) return false;
        if (host.equals("coomerfans.com") || host.endsWith(".coomerfans.com")) return true;
        SourceConfig current = RemoteSourceConfigManager.snapshotOrNull();
        if (current == null || current.coomerFans == null) return false;
        if (sameHost(host, current.coomerFans.baseUrl)) return true;
        for (String fallback : current.coomerFans.fallbackDomains) {
            if (sameHost(host, fallback)) return true;
        }
        return false;
    }

    static boolean isPostUrl(String value) {
        if (!isCoomerFansUrl(value)) return false;
        try {
            String path = new URI(cleanStatic(value)).getPath();
            return path != null && path.matches("^/p/\\d+(?:/.*)?$");
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isDirectImageUrl(String value) {
        SourceConfig current = RemoteSourceConfigManager.snapshotOrNull();
        SourceConfig.CoomerFans config = current == null ? null : current.coomerFans;
        return config != null && isContentImageUrl(config, value);
    }

    private static boolean isContentImageUrl(SourceConfig.CoomerFans config, String value) {
        try {
            URI uri = new URI(cleanStatic(value));
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return false;
            String host = uri.getHost().toLowerCase(Locale.US);
            String suffix = cleanStatic(config.imageHostSuffix).toLowerCase(Locale.US);
            boolean trustedHost = host.equals(suffix) || host.endsWith("." + suffix);
            if (!trustedHost) return false;
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.US);
            if (host.matches("img\\d+\\." + java.util.regex.Pattern.quote(suffix))) return true;
            return path.matches(".*\\.(?:jpg|jpeg|png|webp|gif|avif)$");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isImageCdnUrl(SourceConfig.CoomerFans config, String value) {
        try {
            URI uri = new URI(cleanStatic(value));
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return false;
            String host = uri.getHost().toLowerCase(Locale.US);
            String suffix = cleanStatic(config.imageHostSuffix).toLowerCase(Locale.US);
            return host.matches("img\\d+\\." + java.util.regex.Pattern.quote(suffix));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean sameHost(String host, String url) {
        return host.equals(host(url));
    }

    private static String host(String value) {
        try {
            String host = new URI(cleanStatic(value)).getHost();
            return host == null ? "" : host.toLowerCase(Locale.US);
        } catch (Exception ignored) {
            return "";
        }
    }

    private Element cardScope(Element element) {
        Element current = element;
        Element best = element;
        for (int depth = 0; depth < 5 && current != null; depth++) {
            current = current.parent();
            if (current == null) break;
            if (current.hasClass("thumb") || current.hasClass("creator") ||
                    current.hasClass("model")) return current;
            if (current.text().length() < 500) best = current;
        }
        return best;
    }

    private String heading(Element scope) {
        if (scope == null) return "";
        Element heading = scope.selectFirst("h1,h2,h3,h4,h5,.title,.name,.username");
        return heading == null ? "" : cleanStatic(heading.text());
    }

    private String compactCardText(Element scope) {
        if (scope == null) return "";
        String value = cleanStatic(scope.text());
        return value.length() <= 120 ? value : "";
    }

    private String firstUseful(String... values) {
        for (String value : values) {
            String clean = cleanStatic(value);
            if (!clean.isEmpty() && clean.length() <= 180) return clean;
        }
        return "";
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

    private String absolute(Element element, String attr, String base) {
        if (element == null || !element.hasAttr(attr)) return "";
        String absolute = element.absUrl(attr);
        if (!absolute.isEmpty()) return cleanUrl(absolute);
        try {
            return new URI(base).resolve(cleanUrl(element.attr(attr))).toASCIIString();
        } catch (Exception ignored) {
            return cleanUrl(element.attr(attr));
        }
    }

    private String urlToken(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (Exception ignored) {
            return value;
        }
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }

    private String cleanUrl(String value) {
        return cleanStatic(value).replace("&amp;", "&").replace("\\/", "/");
    }

    private static String cleanStatic(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }
}
