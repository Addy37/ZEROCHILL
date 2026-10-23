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

/**
 * Isolated supplemental ShitTok provider for the public XMegaDrive site.
 *
 * Compiled (not remote-config). Listing scrapes /videos/ pages; playback resolves the
 * direct MP4 from the video page without changing the shared Media3 path.
 * Failure is contained so other providers continue.
 */
final class XMegaDriveRepository {
    static final String LABEL = "XMegaDrive";
    private static final String BASE = "https://www.xmegadrive.com/";
    private static final String LISTING = BASE + "videos/";
    private static final int MAX_CANDIDATES = 12;
    private static final int MAX_RETURNED = 8;

    List<NativeContentItem> fetchShitTokCandidates(Context context, int page) throws IOException {
        String url = page <= 1 ? LISTING : LISTING + "?page=" + Math.max(1, page);
        Document document = fetch(context, url);
        List<NativeContentItem> items = parseListing(document);
        int take = Math.min(MAX_RETURNED, items.size());
        return new ArrayList<>(items.subList(0, take));
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
            return host != null && (host.equalsIgnoreCase("xmegadrive.com") || host.equalsIgnoreCase("www.xmegadrive.com"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Document fetch(Context context, String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .referrer(BASE)
                .timeout(12000)
                .get();
    }

    private static List<NativeContentItem> parseListing(Document document) {
        LinkedHashMap<String, NativeContentItem> found = new LinkedHashMap<>();
        if (document == null) return new ArrayList<>();
        for (Element anchor : document.select("a[href*=/videos/]")) {
            String href = anchor.absUrl("href");
            if (href.isEmpty() || !href.contains("/videos/") || href.contains("/videos/?")) continue;
            String canonical = stripQuery(href);
            if (found.containsKey(canonical)) continue;
            String title = clean(anchor.text());
            if (title.isEmpty()) title = slugTitle(canonical);
            Element img = anchor.selectFirst("img");
            String image = img == null ? "" : img.absUrl("src");
            found.put(canonical, new NativeContentItem(
                    NativeContentItem.KIND_MEDIA,
                    title.isEmpty() ? LABEL : title,
                    canonical,
                    image,
                    "",
                    LABEL,
                    "",
                    "",
                    ""
            ));
            if (found.size() >= MAX_CANDIDATES) break;
        }
        return new ArrayList<>(found.values());
    }

    private static String extractMedia(Document page) {
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
        Element h1 = page.selectFirst("h1");
        return h1 == null ? "" : clean(h1.text());
    }

    private static String stripQuery(String url) {
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q);
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
            if (slug.isEmpty()) return "";
            return slug.replace('-', ' ').replace('_', ' ');
        } catch (Exception ignored) {
            return "";
        }
    }
}
