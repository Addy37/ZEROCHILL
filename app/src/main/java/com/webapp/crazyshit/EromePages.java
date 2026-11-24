package com.webapp.crazyshit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Offline Erome HTML parsing. No network, cookies, or player behavior. */
final class EromePages {
    static final int MAX_SEARCH_RESULTS = 60;
    static final int MAX_ALBUM_MEDIA = 400;
    private static final Pattern ALBUM_PATH = Pattern.compile("^/a/([A-Za-z0-9_-]{4,32})/?$");
    private static final Pattern DIGITS = Pattern.compile("(\\d+)");
    private static final Pattern VIEW_DECIMAL = Pattern.compile("(\\d+),(\\d)(?=\\s*[Kk])");
    private static final Pattern RESOLUTION = Pattern.compile("(?i)_(\\d{3,4})p(?:\\.[a-z0-9]+)?$");

    static final class AccessException extends IOException {
        AccessException(String message) { super(message); }

        static AccessException find(Throwable error) {
            Throwable current = error;
            while (current != null) {
                if (current instanceof AccessException) return (AccessException) current;
                current = current.getCause();
            }
            return null;
        }
    }

    static final class SearchPage {
        final List<NativeContentItem> items;
        final boolean hasNext;

        SearchPage(List<NativeContentItem> items, boolean hasNext) {
            this.items = items == null ? new ArrayList<>() : items;
            this.hasNext = hasNext;
        }
    }

    static final class AlbumPage {
        final List<NativeContentItem> items;
        final String nextUrl;

        AlbumPage(List<NativeContentItem> items, String nextUrl) {
            this.items = items == null ? new ArrayList<>() : items;
            this.nextUrl = nextUrl == null ? "" : nextUrl;
        }
    }

    private EromePages() {}

    static SearchPage parseSearch(String html) throws IOException {
        return parseSearch(Jsoup.parse(html == null ? "" : html, EromeRepository.ORIGIN + "/"));
    }

    static SearchPage parseSearch(Document doc) throws IOException {
        if (doc == null) throw new IOException("Erome search response was empty");
        if (isAccessRestricted(doc)) throw new AccessException("Erome requested verification");
        LinkedHashMap<String, NativeContentItem> albums = new LinkedHashMap<>();
        for (Element album : doc.select("div.album")) {
            if (albums.size() >= MAX_SEARCH_RESULTS) break;
            NativeContentItem item = albumCard(doc, album);
            if (item != null) albums.put(item.url, item);
        }
        if (albums.isEmpty()) {
            if (isEmptySearch(doc)) return new SearchPage(new ArrayList<>(), false);
            throw new IOException("Erome search response has an unexpected format");
        }
        return new SearchPage(new ArrayList<>(albums.values()), hasNext(doc));
    }

    static AlbumPage parseAlbum(String html, String albumUrl) throws IOException {
        return parseAlbum(Jsoup.parse(html == null ? "" : html, EromeRepository.ORIGIN + "/"), albumUrl);
    }

    static AlbumPage parseAlbum(Document doc, String albumUrl) throws IOException {
        if (doc == null) throw new IOException("Erome album response was empty");
        if (isAccessRestricted(doc)) throw new AccessException("Erome requested verification");
        String canonical = EromeRepository.canonicalAlbum(albumUrl);
        if (canonical.isEmpty()) throw new IOException("Not an Erome album URL");
        String title = albumTitle(doc);
        String uploader = text(doc.selectFirst("a#user_name"));
        LinkedHashMap<String, NativeContentItem> media = new LinkedHashMap<>();
        for (Element group : doc.select("div.media-group")) {
            if (media.size() >= MAX_ALBUM_MEDIA) break;
            NativeContentItem video = videoItem(group, canonical, title, uploader);
            if (video != null) media.put(video.url, video);
            for (Element image : group.select("img.img-front")) {
                if (media.size() >= MAX_ALBUM_MEDIA) break;
                NativeContentItem photo = imageItem(image, canonical, title, uploader);
                if (photo != null) media.put(photo.url, photo);
            }
        }
        if (media.isEmpty() && doc.selectFirst("h1.album-title-page, div.media-group") == null) {
            throw new IOException("Erome album response has an unexpected format");
        }
        return new AlbumPage(new ArrayList<>(media.values()), nextAlbumUrl(doc, canonical));
    }

    static String videoSource(Document doc, String albumUrl, String token) {
        if (doc == null || token == null || token.isEmpty()) return "";
        String canonical = EromeRepository.canonicalAlbum(albumUrl);
        String needle = "#" + token;
        for (Element group : doc.select("div.media-group")) {
            NativeContentItem video = videoItem(group, canonical, "", "");
            if (video != null && video.url.endsWith(needle) && EromeRepository.isDirectVideo(video.sourceMediaUrl)) {
                return video.sourceMediaUrl;
            }
        }
        return "";
    }

    static boolean isAccessRestricted(Document doc) {
        if (doc == null || !doc.select("div.album").isEmpty() || !doc.select("div.media-group").isEmpty()) {
            return false;
        }
        if (isEmptySearch(doc)) return false;
        String probe = ((doc.title() == null ? "" : doc.title()) + " "
                + (doc.body() == null ? "" : doc.body().text())).toLowerCase(Locale.US);
        return probe.contains("just a moment")
                || probe.contains("cf-challenge")
                || probe.contains("attention required")
                || probe.contains("confirm your age")
                || probe.contains("age verification")
                || probe.contains("i am 18")
                || probe.contains("please log in")
                || probe.contains("enable javascript and cookies");
    }

    static boolean isEmptySearch(Document doc) {
        if (doc == null || doc.selectFirst("input[name=q]") == null) return false;
        String text = doc.body() == null ? "" : doc.body().text();
        return text.contains("No results");
    }

    private static NativeContentItem albumCard(Document doc, Element album) {
        Element link = album.selectFirst("a.album-link[href]");
        if (link == null) return null;
        String url = EromeRepository.canonicalAlbum(absolute(doc, link));
        if (url.isEmpty()) return null;
        Element titleLink = album.selectFirst("a.album-title");
        String title = text(titleLink);
        if (title.isEmpty()) title = clean(link.selectFirst("img.album-thumbnail") == null
                ? "" : link.selectFirst("img.album-thumbnail").attr("alt"));
        if (title.isEmpty()) title = "Erome album";
        if (title.length() > 300) title = title.substring(0, 300).trim();
        Element thumb = album.selectFirst("img.album-thumbnail");
        String image = thumb == null ? "" : EromeRepository.mediaUrl(absolute(doc, thumb));
        int pictures = count(album.selectFirst("span.album-images"));
        int videos = count(album.selectFirst("span.album-videos"));
        return new NativeContentItem(
                NativeContentItem.KIND_SERIES,
                title,
                url,
                image,
                views(album.selectFirst("span.album-bottom-views")),
                text(album.selectFirst("span.album-user")),
                "",
                counts(pictures, videos)
        );
    }

    private static NativeContentItem videoItem(Element group, String albumUrl, String title, String uploader) {
        Element video = group.selectFirst("video");
        if (video == null) return null;
        String source = bestVideo(group);
        if (!EromeRepository.isDirectVideo(source)) return null;
        String token = token(source);
        if (token.isEmpty()) return null;
        String poster = EromeRepository.mediaUrl(video.hasAttr("poster") ? video.absUrl("poster") : "");
        if (poster.isEmpty()) poster = EromeRepository.mediaUrl(video.attr("poster"));
        float aspect = video.className().contains("vjs-portrait") ? 9f / 16f : 0f;
        String duration = text(group.selectFirst("span.duration"));
        NativeContentItem item = new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                title.isEmpty() ? "Video" : title,
                albumUrl + "#" + token,
                poster,
                "",
                uploader,
                "",
                duration,
                "",
                0L,
                aspect
        );
        return item.withSourceMedia(source, albumUrl);
    }

    private static NativeContentItem imageItem(Element image, String albumUrl, String title, String uploader) {
        String source = EromeRepository.mediaUrl(image.absUrl("src"));
        if (source.isEmpty()) source = EromeRepository.mediaUrl(image.attr("src"));
        if (source.isEmpty() || source.contains("/thumbs/")) return null;
        String token = token(source);
        if (token.isEmpty()) return null;
        int width = positive(image.attr("width"));
        int height = positive(image.attr("height"));
        float aspect = width > 0 && height > 0 ? (float) width / (float) height : 0f;
        if (aspect < 0.2f || aspect > 5f) aspect = 0f;
        NativeContentItem item = new NativeContentItem(
                NativeContentItem.KIND_IMAGE,
                title.isEmpty() ? "Picture" : title,
                albumUrl + "#img-" + token,
                source,
                "",
                uploader,
                "",
                "",
                "",
                0L,
                aspect
        );
        return item.withSourceMedia(source, albumUrl);
    }

    private static String bestVideo(Element group) {
        String best = "";
        int bestRes = -1;
        for (Element source : group.select("source[src]")) {
            String url = source.absUrl("src");
            if (!EromeRepository.isDirectVideo(url)) url = EromeRepository.mediaUrl(source.attr("src"));
            if (!EromeRepository.isDirectVideo(url)) continue;
            int res = positive(source.attr("res"));
            Matcher named = RESOLUTION.matcher(path(url));
            if (named.find()) res = Math.max(res, positive(named.group(1)));
            if (res >= bestRes) {
                bestRes = res;
                best = url;
            }
        }
        return best.isEmpty() ? "" : best;
    }

    private static boolean hasNext(Document doc) {
        Element next = doc.selectFirst("link[rel=next][href]");
        if (next == null) return false;
        String href = absolute(doc, next);
        return EromeRepository.isEromeHttpUrl(href) && href.contains("page=");
    }

    private static String nextAlbumUrl(Document doc, String albumUrl) {
        Element next = doc.selectFirst("link[rel=next][href], a[rel=next][href]");
        if (next == null) return "";
        return EromeRepository.sameAlbumPage(albumUrl, absolute(doc, next));
    }

    private static String albumTitle(Document doc) {
        String title = text(doc.selectFirst("h1.album-title-page"));
        if (!title.isEmpty()) return title.length() > 300 ? title.substring(0, 300).trim() : title;
        title = doc.title() == null ? "" : clean(doc.title());
        int cut = title.indexOf(" - ");
        if (cut > 0) title = title.substring(0, cut).trim();
        return title.isEmpty() ? "Erome album" : title;
    }

    private static String counts(int pictures, int videos) {
        if (pictures <= 0 && videos <= 0) return "Erome album";
        ArrayList<String> parts = new ArrayList<>();
        if (pictures > 0) parts.add(pictures == 1 ? "1 picture" : pictures + " pictures");
        if (videos > 0) parts.add(videos == 1 ? "1 video" : videos + " videos");
        return String.join(" · ", parts);
    }

    private static int count(Element element) {
        if (element == null) return 0;
        Matcher matcher = DIGITS.matcher(element.text());
        int value = 0;
        while (matcher.find()) value = positive(matcher.group(1));
        return value;
    }

    private static String views(Element element) {
        String value = text(element).replace(" ", "");
        if (value.isEmpty()) return "";
        return VIEW_DECIMAL.matcher(value).replaceAll("$1.$2");
    }

    private static String token(String url) {
        String file = path(url);
        int slash = file.lastIndexOf('/');
        if (slash >= 0) file = file.substring(slash + 1);
        int dot = file.lastIndexOf('.');
        if (dot > 0) file = file.substring(0, dot);
        file = file.replaceFirst("(?i)_(\\d{3,4})p$", "");
        return file.matches("[A-Za-z0-9_-]{2,80}") ? file : "";
    }

    private static String absolute(Document doc, Element element) {
        if (element == null) return "";
        String abs = element.absUrl("href");
        if (abs.isEmpty()) abs = element.absUrl("src");
        if (!abs.isEmpty()) return abs;
        String raw = element.hasAttr("href") ? element.attr("href") : element.attr("src");
        if (raw.startsWith("https://") || raw.startsWith("http://")) return raw;
        if (raw.startsWith("/")) return EromeRepository.ORIGIN + raw;
        return raw;
    }

    private static String path(String url) {
        if (url == null) return "";
        int scheme = url.indexOf("://");
        int start = scheme < 0 ? 0 : url.indexOf('/', scheme + 3);
        if (start < 0) return "";
        int end = url.length();
        int query = url.indexOf('?', start);
        int fragment = url.indexOf('#', start);
        if (query >= 0) end = Math.min(end, query);
        if (fragment >= 0) end = Math.min(end, fragment);
        return url.substring(start, end);
    }

    private static String text(Element element) {
        return element == null ? "" : clean(element.text());
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static int positive(String value) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            return Math.max(parsed, 0);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    static boolean isAlbumPath(String path) {
        return path != null && ALBUM_PATH.matcher(path).matches();
    }
}
