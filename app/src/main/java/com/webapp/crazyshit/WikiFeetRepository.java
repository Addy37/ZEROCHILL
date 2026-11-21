package com.webapp.crazyshit;

import android.content.Context;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** WikiFeet X creator profiles and galleries. Regular WikiFeet metadata is read for legacy records. */
final class WikiFeetRepository {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";
    private static final int MAX_BODY = 12 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;

    enum Site {
        WIKIFEET("wikifeet", "WikiFeet", "https://wikifeet.com/",
                "pics.wikifeet.com", "thumbs.wikifeet.com"),
        WIKIFEET_X("wikifeetx", "WikiFeet X", "https://wikifeetx.com/",
                "pics.wikifeet.com", "thumbs.wikifeet.com");

        final String id;
        final String label;
        final String baseUrl;
        final String pictureHost;
        final String thumbnailHost;

        Site(String id, String label, String baseUrl, String pictureHost, String thumbnailHost) {
            this.id = id;
            this.label = label;
            this.baseUrl = baseUrl;
            this.pictureHost = pictureHost;
            this.thumbnailHost = thumbnailHost;
        }
    }

    static final class Creator {
        final Site site;
        final String name;
        final String url;
        final String imageUrl;
        final int photoCount;
        final long profileId;
        final String profileHandle;

        Creator(Site site, String name, String url, String imageUrl, int photoCount) {
            this(site, name, url, imageUrl, photoCount, 0L);
        }

        Creator(Site site, String name, String url, String imageUrl, int photoCount, long profileId) {
            this.site = site;
            this.name = clean(name);
            this.url = normalizeProfileUrl(site, url);
            this.imageUrl = normalizeMediaUrl(site, imageUrl);
            this.photoCount = Math.max(0, photoCount);
            this.profileId = Math.max(0L, profileId);
            this.profileHandle = profileHandle(this.url);
        }

        NativeContentItem asItem() {
            String detail = site.label + (photoCount > 0 ? " · " + photoCount + " photos" : "");
            return new NativeContentItem(NativeContentItem.KIND_CREATOR, name, url,
                    imageUrl, "", url, "", detail, name).withCreatorIdentity(
                    java.util.Collections.singletonList(profileHandle),
                    java.util.Collections.singletonList(url));
        }
    }

    List<Creator> searchCreators(Context context, Site site, String query, int limit)
            throws IOException {
        return searchCreators(site, query, limit, false);
    }

    List<Creator> searchConfirmedCreators(Context context, String query, int limit) throws IOException {
        return searchCreators(Site.WIKIFEET_X, query, limit, true);
    }

    private List<Creator> searchCreators(Site site, String query, int limit, boolean confirmed)
            throws IOException {
        requireEnabled(site);
        String cleanQuery = clean(query);
        if (cleanQuery.length() < 2) return new ArrayList<>();
        int safeLimit = Math.max(1, Math.min(20, limit));
        String endpoint = searchUrl(site, cleanQuery);
        Connection.Response response = fetchPage(site, endpoint, true);
        List<Creator> candidates = parseSearch(response.readBody(), site, response.url().toString(), 100);
        List<Creator> parsed = confirmed ? confirmedMatches(candidates, cleanQuery)
                : matching(candidates, cleanQuery, safeLimit);
        if (!parsed.isEmpty() || (confirmed && hasExactCandidate(candidates, cleanQuery))) return parsed;

        // Search can return an empty result for an exact handle. A redirect is only evidence
        // if the resulting profile still matches the requested identity.
        String guessed = baseUrl(site) + profileSlug(cleanQuery);
        try {
            Connection.Response profile = fetchPage(site, guessed, false);
            Creator exact = parseProfile(profile.readBody(), site, profile.url().toString());
            if (matchesIdentity(exact, cleanQuery)) parsed.add(exact);
        } catch (IOException ignored) { }
        return parsed;
    }

    static List<Creator> confirmedMatches(List<Creator> candidates, String query) {
        Creator match = null;
        int best = Integer.MAX_VALUE;
        boolean ambiguous = false;
        for (Creator candidate : candidates) {
            int rank = identityRank(candidate, query);
            if (rank < best) {
                match = candidate;
                best = rank;
                ambiguous = false;
            } else if (match != null && rank == best && !match.url.equals(candidate.url)
                    && (match.profileId == 0L || match.profileId != candidate.profileId)) {
                ambiguous = true;
            }
        }
        return match == null || ambiguous ? new ArrayList<>()
                : new ArrayList<>(java.util.Collections.singletonList(match));
    }

    private static boolean hasExactCandidate(List<Creator> candidates, String query) {
        for (Creator candidate : candidates) if (matchesIdentity(candidate, query)) return true;
        return false;
    }

    private static boolean matchesIdentity(Creator creator, String query) {
        return identityRank(creator, query) != Integer.MAX_VALUE;
    }

    private static int identityRank(Creator creator, String query) {
        if (creator == null || query.isEmpty() || !isXProfileUrl(creator.url)) return Integer.MAX_VALUE;
        if (query.equalsIgnoreCase(creator.profileHandle)) return 0;
        return ShitTokFilteredIdentity.strong(creator.name, query) ? 1 : Integer.MAX_VALUE;
    }

    private List<Creator> matching(List<Creator> creators, String query, int limit) {
        ArrayList<Creator> result = new ArrayList<>();
        for (Creator creator : creators) {
            if (searchRank(creator, query) != Integer.MAX_VALUE) result.add(creator);
        }
        result.sort(java.util.Comparator.comparingInt(creator -> searchRank(creator, query)));
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    private int searchRank(Creator creator, String query) {
        if (query.equalsIgnoreCase(creator.profileHandle)) return 0;
        return Math.min(CreatorNameMatcher.rank(creator.name, query),
                CreatorNameMatcher.rank(creator.profileHandle, query));
    }

    List<NativeContentItem> fetchCreatorMedia(
            Context context,
            Creator creator,
            int page,
            int pageSize
    ) throws IOException {
        requireEnabled(creator == null ? null : creator.site);
        if (creator == null || !isProfileUrl(creator.url, creator.site)) {
            throw new IOException("WikiFeet creator page was missing");
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(120, pageSize));
        Connection.Response profile = fetchPage(creator.site, creator.url, false);
        String body = profile.readBody();
        if (creator.profileId == 0L && !creator.profileHandle.equalsIgnoreCase(
                profileHandle(profile.url().toString()))) {
            Creator redirected = parseProfile(body, creator.site, profile.url().toString());
            if (!ShitTokFilteredIdentity.strong(creator.name, redirected.name)) {
                throw new IOException("WikiFeet X profile redirected to a different creator");
            }
        }
        return parseMedia(body, creator, safePage, safeSize);
    }

    static List<NativeContentItem> parseMedia(
            String body,
            Creator creator,
            int page,
            int pageSize
    ) throws IOException {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(120, pageSize));
        JSONObject data = profileData(body);
        if (creator.profileId > 0L && data.optLong("cid", 0L) != creator.profileId) {
            throw new IOException("WikiFeet X profile ID did not match");
        }
        String name = clean(data.optString("cname", creator.name));
        JSONArray gallery = data.optJSONArray("gallery");
        ArrayList<NativeContentItem> result = new ArrayList<>();
        if (gallery == null || name.isEmpty()) return result;
        int skip = (safePage - 1) * safeSize;
        int active = 0;
        for (int index = gallery.length() - 1; index >= 0 && result.size() < safeSize; index--) {
            JSONObject photo = gallery.optJSONObject(index);
            if (photo == null || photo.optInt("removed", 0) != 0) continue;
            long id = photo.optLong("pid", 0L);
            if (id <= 0L) continue;
            if (active++ < skip) continue;
            String original = originalUrl(creator.site, name, id);
            String thumbnail = thumbnailUrl(creator.site, id);
            String dimensions = dimensions(photo);
            result.add(new NativeContentItem(
                    NativeContentItem.KIND_IMAGE,
                    name + " #" + id,
                    original,
                    thumbnail,
                    creator.site.label,
                    creator.url,
                    "",
                    dimensions.isEmpty() ? creator.site.label : creator.site.label + " · " + dimensions
            ));
        }
        return result;
    }

    Creator fetchProfile(Context context, Site site, String profileUrl) throws IOException {
        requireEnabled(site);
        String canonical = normalizeProfileUrl(site, profileUrl);
        if (!isProfileUrl(canonical, site)) throw new IOException("Invalid WikiFeet profile URL");
        Connection.Response profile = fetchPage(site, canonical, false);
        return parseProfile(profile.readBody(), site, profile.url().toString());
    }

    CrazyShitRepository.StreamInfo resolvePlayable(String value, String profileUrl) throws IOException {
        Site site = Site.WIKIFEET_X;
        requireEnabled(site);
        String url = clean(value);
        if (!isOriginalImageUrl(url)) throw new IOException("Not a WikiFeet X image URL");
        String origin = isXProfileUrl(profileUrl) ? profileUrl : baseUrl(site);
        return new CrazyShitRepository.StreamInfo(url, url, fileTitle(url), referer(site, origin));
    }

    static List<Creator> parseSearch(String body, Site site, String endpoint, int limit) {
        LinkedHashMap<String, Creator> result = new LinkedHashMap<>();
        String value = body == null ? "" : body.trim();
        if (value.startsWith("{") || value.startsWith("[")) {
            try { collectJson(new JSONTokener(value).nextValue(), site, result, limit); }
            catch (Exception ignored) { }
        }
        String bodyTree = assignedArray(value, "tbody");
        if (!bodyTree.isEmpty()) {
            try { collectJson(new JSONArray(bodyTree), site, result, limit); }
            catch (Exception ignored) { }
        }
        Document document = Jsoup.parse(value, endpoint);
        for (Element link : document.select(searchSelector(site))) {
            if (result.size() >= limit) break;
            String url = normalizeProfileUrl(site, link.absUrl("href"));
            if (!isProfileUrl(url, site)) continue;
            Element nameNode = link.selectFirst("div");
            String name = clean(nameNode == null ? link.ownText() : nameNode.text());
            if (name.isEmpty()) name = humanizeProfile(url);
            int count = firstNumber(link.select("small").text());
            String image = imageFrom(link, site);
            put(result, new Creator(site, name, url, image, count));
        }
        return limited(result, limit);
    }

    static Creator parseProfile(String body, Site site, String profileUrl) throws IOException {
        JSONObject data = profileData(body);
        String name = clean(data.optString("cname"));
        long creatorId = data.optLong("cid", 0L);
        if (name.isEmpty() || creatorId <= 0L) throw new IOException("WikiFeet profile was not found");
        JSONArray gallery = data.optJSONArray("gallery");
        int count = gallery == null ? 0 : gallery.length();
        String image = "";
        if (gallery != null) {
            for (int i = gallery.length() - 1; i >= 0; i--) {
                JSONObject photo = gallery.optJSONObject(i);
                long id = photo == null ? 0L : photo.optLong("pid", 0L);
                if (id > 0L && photo.optInt("removed", 0) == 0) {
                    image = thumbnailUrl(site, id);
                    break;
                }
            }
        }
        return new Creator(site, name, profileUrl, image, count, creatorId);
    }

    static JSONObject profileData(String body) throws IOException {
        String object = assignedObject(body, "tdata");
        if (object.isEmpty()) throw new IOException("WikiFeet profile data was missing");
        try { return new JSONObject(object); }
        catch (JSONException error) { throw new IOException("WikiFeet profile data was invalid", error); }
    }

    static String assignedObject(String body, String variable) {
        return assignedValue(body, variable, '{', '}');
    }

    static String assignedArray(String body, String variable) {
        return assignedValue(body, variable, '[', ']');
    }

    private static String assignedValue(String body, String variable, char open, char close) {
        if (body == null || variable == null) return "";
        int name = body.indexOf(variable);
        while (name >= 0) {
            int equals = body.indexOf('=', name + variable.length());
            if (equals < 0) return "";
            int start = body.indexOf(open, equals + 1);
            if (start < 0) return "";
            boolean quoted = false;
            boolean escaped = false;
            char quote = 0;
            int depth = 0;
            for (int i = start; i < body.length(); i++) {
                char character = body.charAt(i);
                if (quoted) {
                    if (escaped) escaped = false;
                    else if (character == '\\') escaped = true;
                    else if (character == quote) quoted = false;
                    continue;
                }
                if (character == '\"' || character == '\'') {
                    quoted = true;
                    quote = character;
                } else if (character == open) depth++;
                else if (character == close && --depth == 0) return body.substring(start, i + 1);
            }
            name = body.indexOf(variable, name + variable.length());
        }
        return "";
    }

    static String searchUrl(Site site, String query) {
        SourceConfig.WikiFeet config = config(site);
        if (config == null) return site.baseUrl + "search/" + encode(clean(query));
        return config.baseUrl + config.searchRoute.replace("{query}", encode(clean(query)));
    }

    static boolean isWikiFeetUrl(String value) {
        return pageSite(value) != null || matchesMediaHost(value, Site.WIKIFEET_X)
                || matchesMediaHost(value, Site.WIKIFEET);
    }

    static boolean isRetiredPageUrl(String value) {
        return pageSite(value) == Site.WIKIFEET;
    }

    static boolean isXPageUrl(String value) {
        return pageSite(value) == Site.WIKIFEET_X;
    }

    static boolean isXProfileUrl(String value) {
        return isProfileUrl(value, Site.WIKIFEET_X);
    }

    static boolean isXMediaUrl(String value) {
        return !isRetiredPageUrl(value) && matchesMediaHost(value, Site.WIKIFEET_X);
    }

    static boolean isXItem(NativeContentItem item) {
        return itemSite(item) == Site.WIKIFEET_X;
    }

    static String imageReferer(NativeContentItem item) {
        if (!isXItem(item)) return "";
        Site site = Site.WIKIFEET_X;
        String origin = isXProfileUrl(item.uploader) ? item.uploader
                : isXProfileUrl(item.url) ? item.url : baseUrl(site);
        return referer(site, origin);
    }

    static boolean isRetiredItem(NativeContentItem item) {
        return itemSite(item) == Site.WIKIFEET;
    }

    private static Site itemSite(NativeContentItem item) {
        if (item == null) return null;
        Site page = pageSite(item.url);
        if (page != null) return page;
        Site origin = pageSite(item.uploader);
        if (origin != null) return origin;
        if (matchesMediaHost(item.url, Site.WIKIFEET_X) && hasSourceLabel(item, Site.WIKIFEET_X)) {
            return Site.WIKIFEET_X;
        }
        if ((matchesMediaHost(item.url, Site.WIKIFEET) || (item.isCreator() && item.url.isEmpty()))
                && hasSourceLabel(item, Site.WIKIFEET)) return Site.WIKIFEET;
        return null;
    }

    private static boolean hasSourceLabel(NativeContentItem item, Site site) {
        return site.label.equalsIgnoreCase(item.views) || site.label.equalsIgnoreCase(item.description)
                || item.description.regionMatches(true, 0, site.label + " · ", 0, site.label.length() + 3);
    }

    static boolean isOriginalImageUrl(String value) {
        if (!isXMediaUrl(value)) return false;
        Site site = Site.WIKIFEET_X;
        String host = host(value);
        return (host.equals(pictureHost(site)) || host.equals(site.pictureHost))
                && "https".equalsIgnoreCase(scheme(value))
                && value.toLowerCase(Locale.US).matches(".*\\.(?:jpe?g|png|webp)(?:[?#].*)?$");
    }

    static boolean isProfileUrl(String value, Site site) {
        if (site == null || !"https".equalsIgnoreCase(scheme(value))) return false;
        if (site == Site.WIKIFEET_X ? !isXPageUrl(value) : !isRetiredPageUrl(value)) return false;
        String path = profileHandle(value);
        return !path.isEmpty() && !path.contains("/") && !path.equalsIgnoreCase("search")
                && !path.equalsIgnoreCase("photos") && !path.equalsIgnoreCase("videos");
    }

    static String originalUrl(Site site, String creatorName, long id) {
        String name = clean(creatorName).replaceAll("\\s+", "-")
                .replaceAll("[^a-zA-Z-]", "");
        return "https://" + pictureHost(site) + "/" + encodePath(name + "-Feet-" + id + ".jpg");
    }

    static String thumbnailUrl(Site site, long id) {
        return id <= 0L ? "" : "https://" + thumbnailHost(site) + "/" + id + ".jpg";
    }

    private Connection.Response fetchPage(Site site, String url, boolean ajax) throws IOException {
        requireEnabled(site);
        IOException last = null;
        for (String candidate : requestCandidates(site, url)) {
            if (!isXPageUrl(candidate)) continue;
            for (int attempt = 0; attempt <= retryCount(site); attempt++) {
                try {
                    return requestPage(site, candidate, ajax);
                } catch (IOException error) {
                    if (Thread.currentThread().isInterrupted()) throw error;
                    last = error;
                }
            }
        }
        throw last == null ? new IOException("Invalid WikiFeet X URL") : last;
    }

    private Connection.Response requestPage(Site site, String candidate, boolean ajax) throws IOException {
        SourceConfig.WikiFeet config = config(site);
        String url = candidate;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("WikiFeet X request canceled");
            if (!isXPageUrl(url) || !"https".equalsIgnoreCase(scheme(url))) {
                throw new IOException("WikiFeet X redirected outside its source");
            }
            Connection connection = Jsoup.connect(url)
                    .userAgent(userAgent(site))
                    .referrer(referer(site, baseUrl(site)))
                    .timeout(ajax ? ajaxTimeoutMs(site) : requestTimeoutMs(site))
                    .maxBodySize(MAX_BODY)
                    .followRedirects(false)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true);
            if (config == null) {
                connection.header("Accept-Encoding", "identity")
                        .header("Accept", ajax
                                ? "application/json,text/html,*/*;q=0.8"
                                : "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                if (ajax) connection.header("X-Requested-With", "XMLHttpRequest");
            } else {
                applyHeaders(connection, config.requestHeaders);
                if (ajax) applyHeaders(connection, config.ajaxHeaders);
            }
            try {
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null && !cookies.trim().isEmpty()) connection.header("Cookie", cookies);
            } catch (Exception ignored) { }
            Connection.Response response = connection.execute();
            int code = response.statusCode();
            if (code >= 200 && code < 300) return response;
            String location = response.header("Location");
            if (code < 300 || code >= 400 || location == null || location.trim().isEmpty()) {
                throw new IOException("WikiFeet X returned HTTP " + code);
            }
            try {
                url = URI.create(url).resolve(location).toString();
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Invalid WikiFeet X redirect", invalid);
            }
        }
        throw new IOException("Too many WikiFeet X redirects");
    }

    private static void collectJson(
            Object value,
            Site site,
            LinkedHashMap<String, Creator> output,
            int limit
    ) {
        if (value == null || value == JSONObject.NULL || output.size() >= limit) return;
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length() && output.size() < limit; i++) {
                collectJson(array.opt(i), site, output, limit);
            }
            return;
        }
        if (!(value instanceof JSONObject)) return;
        JSONObject object = (JSONObject) value;
        String name = firstString(object, "name", "cname", "title", "label");
        String url = firstString(object, "url", "href", "path", "slug", "fetchname");
        if (!url.isEmpty() && !url.startsWith("http") && !url.startsWith("/")) url = "/" + url;
        url = normalizeProfileUrl(site, url);
        if (!name.isEmpty() && isProfileUrl(url, site)) {
            String image = firstString(object, "image", "thumbnail", "thumb", "photo");
            long pid = firstLong(object, "pid", "picture_id", "photo_id");
            if (pid <= 0L) {
                String pictures = firstString(object, "pics");
                if (!pictures.isEmpty()) {
                    try { pid = Long.parseLong(pictures.split(",", 2)[0].trim()); }
                    catch (Exception ignored) { }
                }
            }
            if (image.isEmpty() && pid > 0L) image = thumbnailUrl(site, pid);
            int count = (int) firstLong(object, "photos", "count", "photo_count", "pictures");
            put(output, new Creator(site, name, url, image, count, firstLong(object, "cid")));
        }
        java.util.Iterator<String> keys = object.keys();
        while (keys.hasNext() && output.size() < limit) collectJson(object.opt(keys.next()), site, output, limit);
    }

    private static void put(LinkedHashMap<String, Creator> output, Creator creator) {
        if (creator == null || creator.name.isEmpty() || !isProfileUrl(creator.url, creator.site)) return;
        output.putIfAbsent(creator.url, creator);
    }

    private static List<Creator> limited(LinkedHashMap<String, Creator> values, int limit) {
        ArrayList<Creator> output = new ArrayList<>(values.values());
        return output.size() > limit ? new ArrayList<>(output.subList(0, limit)) : output;
    }

    private static String firstString(JSONObject object, String... keys) {
        for (String key : keys) {
            Object value = object.opt(key);
            if (value instanceof String && !clean((String) value).isEmpty()) return clean((String) value);
            if (value instanceof JSONObject) {
                String nested = firstString((JSONObject) value, "url", "src", "href");
                if (!nested.isEmpty()) return nested;
            }
        }
        return "";
    }

    private static long firstLong(JSONObject object, String... keys) {
        for (String key : keys) {
            long value = object.optLong(key, 0L);
            if (value > 0L) return value;
        }
        return 0L;
    }

    private static String imageFrom(Element link, Site site) {
        Element image = link.selectFirst("img[src],img[data-src]");
        if (image == null) return "";
        String value = image.hasAttr("data-src") ? image.absUrl("data-src") : image.absUrl("src");
        return normalizeMediaUrl(site, value);
    }

    private static String dimensions(JSONObject photo) {
        int width = photo.optInt("pw", 0);
        int height = photo.optInt("ph", 0);
        return width > 0 && height > 0 ? width + " × " + height : "";
    }

    private static int firstNumber(String value) {
        try {
            String digits = clean(value).replaceAll("[^0-9].*$", "").replaceAll("[^0-9]", "");
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (Exception ignored) { return 0; }
    }

    private static String normalizeProfileUrl(Site site, String value) {
        String cleanValue = clean(value);
        if (site == null || cleanValue.isEmpty()) return "";
        String configuredBase = baseUrl(site);
        if (cleanValue.startsWith("/")) cleanValue = configuredBase.substring(0, configuredBase.length() - 1) + cleanValue;
        else if (!cleanValue.startsWith("http://") && !cleanValue.startsWith("https://")) cleanValue = configuredBase + cleanValue;
        try {
            URI uri = new URI(cleanValue);
            if (!isProfileUrl(cleanValue, site)) return "";
            URI base = new URI(configuredBase);
            String canonical = new URI("https", base.getAuthority(), uri.getPath(), null, null).toASCIIString();
            return isProfileUrl(canonical, site) ? canonical : "";
        } catch (Exception ignored) { return ""; }
    }

    private static String normalizeMediaUrl(Site site, String value) {
        String cleanValue = clean(value);
        if (cleanValue.isEmpty()) return "";
        try {
            URI uri = new URI(cleanValue);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return "";
            if (site != null && !matchesMediaHost(cleanValue, site)) return "";
            return uri.toASCIIString();
        } catch (Exception ignored) { return ""; }
    }

    private static Site pageSite(String value) {
        String host = profileHost(value);
        if (host.isEmpty()) return null;
        if (host.equals(profileHost(Site.WIKIFEET.baseUrl))) return Site.WIKIFEET;
        if (matchesPageHost(host, Site.WIKIFEET_X)) return Site.WIKIFEET_X;
        return matchesPageHost(host, Site.WIKIFEET) ? Site.WIKIFEET : null;
    }

    private static boolean matchesPageHost(String host, Site site) {
        if (host.equals(profileHost(site.baseUrl)) || host.equals(profileHost(baseUrl(site)))) return true;
        SourceConfig.WikiFeet config = config(site);
        if (config != null) for (String fallback : config.fallbackDomains) {
            if (host.equals(profileHost(fallback))) return true;
        }
        return false;
    }

    private static boolean matchesMediaHost(String value, Site site) {
        String host = host(value);
        return !host.isEmpty() && (host.equals(pictureHost(site)) || host.equals(thumbnailHost(site))
                || host.equals(site.pictureHost) || host.equals(site.thumbnailHost));
    }

    private static String scheme(String value) {
        try { return new URI(clean(value)).getScheme(); }
        catch (Exception ignored) { return null; }
    }

    private static String profileHost(String value) {
        String host = host(value);
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private static String host(String value) {
        try {
            String host = new URI(clean(value)).getHost();
            return host == null ? "" : host.toLowerCase(Locale.US);
        } catch (Exception ignored) { return ""; }
    }

    private static String humanizeProfile(String value) {
        return clean(profileHandle(value).replace('_', ' ').replace('-', ' ').replaceAll("\\([0-9]+\\)$", ""));
    }

    private static String profileHandle(String value) {
        try {
            String path = new URI(value).getPath();
            return path == null ? "" : path.replaceAll("^/+|/+$", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String profileSlug(String value) {
        return encodePath(clean(value).replaceAll("\\s+", "_"));
    }

    private static String fileTitle(String value) {
        try {
            String path = new URI(value).getPath();
            int slash = path == null ? -1 : path.lastIndexOf('/');
            return slash >= 0 ? path.substring(slash + 1).replace('-', ' ') : "WikiFeet photo";
        } catch (Exception ignored) { return "WikiFeet photo"; }
    }

    private static SourceConfig.WikiFeet config(Site site) {
        SourceConfig current = RemoteSourceConfigManager.snapshotOrNull();
        if (current == null || site == null) return null;
        return site == Site.WIKIFEET_X ? current.wikiFeetX : current.wikiFeet;
    }

    private static void requireEnabled(Site site) throws IOException {
        if (site != Site.WIKIFEET_X) throw new IOException("WikiFeet source is unavailable");
        SourceConfig current = RemoteSourceConfigManager.snapshotOrNull();
        SourceConfig.WikiFeet config = config(site);
        if (current != null && current.sourceKillSwitchesEnabled && config != null && !config.enabled) {
            throw new IOException(site.label + " is temporarily unavailable");
        }
    }

    private static String baseUrl(Site site) {
        SourceConfig.WikiFeet config = config(site);
        return config == null ? site.baseUrl : config.baseUrl;
    }

    private static String pictureHost(Site site) {
        SourceConfig.WikiFeet config = config(site);
        return config == null ? site.pictureHost : config.pictureHost;
    }

    private static String thumbnailHost(Site site) {
        SourceConfig.WikiFeet config = config(site);
        return config == null ? site.thumbnailHost : config.thumbnailHost;
    }

    private static String userAgent(Site site) {
        SourceConfig.WikiFeet config = config(site);
        return config == null ? USER_AGENT : config.userAgent;
    }

    private static String referer(Site site, String fallback) {
        SourceConfig.WikiFeet config = config(site);
        return config != null && !config.refererOverride.isEmpty()
                ? config.refererOverride : fallback;
    }

    private static int requestTimeoutMs(Site site) {
        SourceConfig.WikiFeet config = config(site);
        return config == null ? 15_000 : config.requestTimeoutMs;
    }

    private static int ajaxTimeoutMs(Site site) {
        SourceConfig.WikiFeet config = config(site);
        return config == null ? 6_000 : config.ajaxTimeoutMs;
    }

    private static int retryCount(Site site) {
        SourceConfig.WikiFeet config = config(site);
        return config == null ? 1 : config.retryCount;
    }

    private static String searchSelector(Site site) {
        SourceConfig.WikiFeet config = config(site);
        return config == null ? "#searchresults a[href], a[href]" : config.searchSelector;
    }

    private static void applyHeaders(Connection connection, Map<String, String> headers) {
        for (Map.Entry<String, String> header : headers.entrySet()) {
            connection.header(header.getKey(), header.getValue());
        }
    }

    private static List<String> requestCandidates(Site site, String url) {
        ArrayList<String> candidates = new ArrayList<>();
        candidates.add(url);
        SourceConfig current = RemoteSourceConfigManager.snapshotOrNull();
        if (current != null && !current.fallbacksEnabled) return candidates;
        SourceConfig.WikiFeet config = config(site);
        if (config == null || config.fallbackDomains.isEmpty()) return candidates;
        try {
            URI source = new URI(url);
            String path = source.getRawPath() == null ? "/" : source.getRawPath();
            if (source.getRawQuery() != null) path += "?" + source.getRawQuery();
            for (String fallback : config.fallbackDomains) {
                String root = fallback.replaceAll("/+$", "");
                candidates.add(root + (path.startsWith("/") ? path : "/" + path));
            }
        } catch (Exception ignored) { }
        return candidates;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String encodePath(String value) {
        return encode(value).replace("%2F", "/");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
