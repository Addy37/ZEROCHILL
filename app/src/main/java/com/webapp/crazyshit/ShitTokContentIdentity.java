package com.webapp.crazyshit;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative aliases, based on the parsers' existing post IDs. Never match filenames globally. */
final class ShitTokContentIdentity {
    private static final Pattern CRAZY_POST = Pattern.compile("^/cnt/medias/(\\d+)(?:/.*)?$");
    private static final Pattern FAPELLO_POST = Pattern.compile("^/(?:video/(?:[^/]+/)?|[^/]+/)(\\d+)/?$");

    static String page(String raw) {
        try {
            URI uri = new URI(raw);
            String host = host(uri), path = uri.getRawPath();
            if (host.isEmpty() || path == null) return "";
            Matcher id = CRAZY_POST.matcher(path);
            if (host.equals("crazyshit.com") && id.matches()) return "crazyshit:post:" + id.group(1);
            id = FAPELLO_POST.matcher(path);
            if (FapelloRepository.isFapelloUrl(raw) && id.matches()) return "fapello:post:" + id.group(1);
            // Known page parsers ignore query/fragment. Unknown routes retain meaningful queries.
            boolean page = WebVideoSourceRepository.isKaoticUrl(raw)
                    || EfuktRepository.isEfuktUrl(raw) || BaddieHubRepository.isBaddieHubUrl(raw);
            return "url:" + host + path.replaceAll("/+$", "") + query(uri, page, false);
        } catch (Exception invalid) { return ""; }
    }

    static String media(String raw) {
        try {
            URI uri = new URI(raw);
            if (host(uri).isEmpty() || uri.getRawPath() == null) return "";
            // Opaque download endpoints may use a token as the content ID. Preserve it there.
            boolean filePath = uri.getRawPath().matches("(?i).+\\.(?:mp4|m4v|mov|webm|m3u8|mpd)$");
            return "media:" + host(uri) + uri.getRawPath() + query(uri, false, filePath);
        } catch (Exception invalid) { return ""; }
    }

    private static String host(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.US);
        if (host.startsWith("www.")) host = host.substring(4);
        return host + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
    }

    private static String query(URI uri, boolean ignore, boolean media) {
        if (ignore || uri.getRawQuery() == null) return "";
        java.util.ArrayList<String> kept = new java.util.ArrayList<>();
        for (String part : uri.getRawQuery().split("&")) {
            String key = part.split("=", 2)[0].toLowerCase(Locale.US);
            if (key.startsWith("utm_") || key.equals("fbclid")) continue;
            if (media && (key.equals("token") || key.equals("expires") || key.equals("signature")
                    || key.equals("policy") || key.equals("key-pair-id") || key.startsWith("x-amz-"))) continue;
            kept.add(part);
        }
        return kept.isEmpty() ? "" : "?" + String.join("&", kept);
    }
    private ShitTokContentIdentity() {}
}
