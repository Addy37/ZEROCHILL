package com.webapp.crazyshit;

import java.util.Locale;

/**
 * The provider that is actually playing, not a creator who also appears elsewhere.
 * Icons are bundled. Nothing here requests a logo from the network.
 */
final class ShitTokSourceMark {
    static final class Provider {
        final String name;
        final int iconRes;
        final String monogram;
        final int color;

        Provider(String name, int iconRes, String monogram, int color) {
            this.name = name;
            this.iconRes = iconRes;
            this.monogram = monogram == null ? "" : monogram;
            this.color = color;
        }
    }

    private ShitTokSourceMark() {}

    static Provider of(NativeContentItem item) {
        if (item == null) return null;
        if (any(item, EromeRepository::isEromeHttpUrl)) {
            return new Provider("Erome", 0, "E", 0);
        }
        if (any(item, BunkrRepository::isBunkrUrl)) {
            return new Provider("Bunkr", R.drawable.ic_source_bunkr, "", 0);
        }
        if (any(item, BaddieHubRepository::isBaddieHubUrl)) {
            return new Provider("BaddieHub", 0, "BH", 0);
        }
        if (any(item, PawchiveRepository::isDirectMedia) || PawchiveRepository.isProfile(item.url)
                || PawchiveRepository.isProfile(item.sourcePageUrl)) {
            return new Provider("Pawchive", R.drawable.ic_content_pawchive, "", 0);
        }
        if (WikiFeetRepository.isXItem(item)) {
            return new Provider("WikiFeet X", R.drawable.ic_source_wikifeet, "", 0);
        }
        if (any(item, OnlyHavenRepository::isOnlyHavenUrl)
                || labeled(item.uploader, "onlyhaven")) {
            return new Provider("OnlyHaven", R.drawable.ic_source_onlyhaven, "", 0);
        }
        if (any(item, FapelloRepository::isFapelloUrl)
                || labeled(item.description, "fapello")
                || labeled(item.description, "onlyfap")) {
            return new Provider("Fapello", R.drawable.ic_source_fapello, "", 0);
        }
        if (any(item, WebVideoSourceRepository::isKaoticUrl) || labeled(item.uploader, "kaotic")) {
            return new Provider("Kaotic", 0, "K", 0);
        }
        if (any(item, EfuktRepository::isEfuktUrl) || labeled(item.uploader, "efukt")) {
            return new Provider("eFukt", 0, "eF", 0);
        }
        if (shitShow(item)) return new Provider("Shit Show", 0, "SS", 0);
        if (crazyShit(item)) return new Provider("CrazyShit", 0, "CS", 0);
        if (any(item, XMegaDriveRepository::isXMegaDriveUrl)) {
            return new Provider("XMegaDrive", 0, "X", 0);
        }
        return null;
    }

    private static boolean any(NativeContentItem item, java.util.function.Predicate<String> test) {
        return test.test(item.url) || test.test(item.sourcePageUrl) || test.test(item.sourceMediaUrl);
    }

    private static boolean labeled(String value, String expected) {
        return value != null && expected.equals(value.trim().toLowerCase(Locale.US));
    }

    private static boolean shitShow(NativeContentItem item) {
        if (labeled(item.uploader, "shit show")) return true;
        String url = item.url == null ? "" : item.url.toLowerCase(Locale.US);
        String page = item.sourcePageUrl == null ? "" : item.sourcePageUrl.toLowerCase(Locale.US);
        return url.contains("/shitshow/") || page.contains("/shitshow/");
    }

    private static boolean crazyShit(NativeContentItem item) {
        if (labeled(item.uploader, "crazyshit")) return true;
        String url = item.url == null ? "" : item.url.toLowerCase(Locale.US);
        String page = item.sourcePageUrl == null ? "" : item.sourcePageUrl.toLowerCase(Locale.US);
        return url.contains("crazyshit.com") || page.contains("crazyshit.com");
    }
}
