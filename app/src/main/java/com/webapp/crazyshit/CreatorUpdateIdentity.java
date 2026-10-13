package com.webapp.crazyshit;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Source-qualified physical-post identity. Numeric IDs are only interpreted on Fapello hosts. */
final class CreatorUpdateIdentity {
    private static final Pattern POST = Pattern.compile("/(?:new/)?([0-9]+)/?$");
    private static final Pattern MEDIA = Pattern.compile(
            "(?i)(?:^|[_-])([0-9]{3,})(?:_[0-9]+px)?\\.(?:jpe?g|png|webp|avif|gif|bmp|heic|heif|mp4|webm|m4v|mov|m3u8|mpd)$");
    private CreatorUpdateIdentity() { }

    static String source(NativeContentItem item) {
        if (item == null) return "onlyfap";
        if (FapelloRepository.isFapelloUrl(item.url)) return "fapello";
        if (OnlyHavenRepository.isOnlyHavenUrl(item.url)
                || item.description.toLowerCase(Locale.US).contains("onlyhaven")) return "onlyhaven";
        if (item.description.toLowerCase(Locale.US).contains("fapello")) return "fapello";
        if (item.description.toLowerCase(Locale.US).contains("bunkr")) return "bunkr";
        return "onlyfap";
    }

    static String key(String source, NativeContentItem item) {
        String post = fapelloPost(item.url);
        if (post.isEmpty() && "fapello".equals(source)) post = fapelloPost(item.imageUrl);
        if (!post.isEmpty()) return "fapello:post:" + post;
        return source + ":url:" + canonical(item.url);
    }

    static boolean matches(NativeContentItem item, String url) {
        if (item == null || canonical(url).isEmpty()) return false;
        if (canonical(url).equals(canonical(item.url))) return true;
        String post = fapelloPost(url);
        return !post.isEmpty() && (post.equals(fapelloPost(item.url))
                || ("fapello".equals(source(item)) && post.equals(fapelloPost(item.imageUrl))));
    }

    private static String fapelloPost(String url) {
        if (!FapelloRepository.isFapelloUrl(url)) return "";
        try {
            String path = new URI(url.trim()).getPath();
            Matcher post = POST.matcher(path);
            if (post.find()) return post.group(1);
            Matcher media = MEDIA.matcher(path);
            return media.find() ? media.group(1) : "";
        } catch (Exception ignored) { return ""; }
    }

    static String canonical(String url) {
        try {
            URI uri = new URI(url == null ? "" : url.trim());
            if (uri.getHost() == null) return url == null ? "" : url.trim();
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
            return uri.getScheme().toLowerCase(Locale.US) + "://"
                    + uri.getHost().toLowerCase(Locale.US) + (uri.getPort() < 0 ? "" : ":" + uri.getPort()) + path
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        } catch (Exception ignored) { return url == null ? "" : url.trim(); }
    }
}
