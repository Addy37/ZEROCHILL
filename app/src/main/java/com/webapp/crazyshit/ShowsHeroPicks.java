package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * V2-style Shows hero picks from already-loaded provider collections.
 * No extra source requests, image probes or backend calls.
 */
final class ShowsHeroPicks {
    private static final String[] PLACEHOLDER_MARKERS = {
            "placeholder", "blank-tile", "blank.gif", "spacer", "sprite",
            "logo", "avatar", "/cdn-cgi/challenge", "no_image"
    };

    private ShowsHeroPicks() {
    }

    static List<NativeContentItem> mixed(
            List<List<NativeContentItem>> providers,
            Set<String> rejectedArtwork,
            int seed,
            int limit
    ) {
        if (providers == null || limit <= 0) return Collections.emptyList();

        List<List<NativeContentItem>> pools = new ArrayList<>(providers.size());
        for (int source = 0; source < providers.size(); source++) {
            ArrayList<NativeContentItem> eligible = new ArrayList<>();
            List<NativeContentItem> items = providers.get(source);
            if (items != null) {
                for (NativeContentItem item : items) {
                    if (item == null || item.title.trim().isEmpty()
                            || !isWebUrl(item.url) || !hasRemoteArtwork(item)) continue;
                    if (rejectedArtwork != null
                            && rejectedArtwork.contains(imageKey(item))) continue;
                    eligible.add(item);
                }
            }
            Collections.shuffle(eligible, new Random(seed ^ (source + 1) * 0x9E3779B9));
            pools.add(eligible);
        }

        int[] offsets = new int[pools.size()];
        Set<String> seenPages = new HashSet<>();
        Set<String> seenImages = new HashSet<>();
        List<NativeContentItem> picks = new ArrayList<>(limit);
        boolean added;
        do {
            added = false;
            for (int source = 0; source < pools.size() && picks.size() < limit; source++) {
                List<NativeContentItem> pool = pools.get(source);
                while (offsets[source] < pool.size()) {
                    NativeContentItem item = pool.get(offsets[source]++);
                    if (!seenPages.add(pageKey(item))) continue;
                    if (!seenImages.add(imageKey(item))) continue;
                    picks.add(item);
                    added = true;
                    break;
                }
            }
        } while (added && picks.size() < limit);
        return picks;
    }

    static boolean hasRemoteArtwork(NativeContentItem item) {
        if (item == null || !isWebUrl(item.imageUrl)) return false;
        String lower = item.imageUrl.toLowerCase(Locale.US);
        for (String marker : PLACEHOLDER_MARKERS) {
            if (lower.contains(marker)) return false;
        }
        return true;
    }

    /** Match V2's decoded-image size and aspect checks, before showing remote artwork. */
    static boolean usableArtworkSize(int width, int height) {
        if (width <= 0 || height <= 0) return false;
        float ratio = (float) width / height;
        return (width >= 320 && height >= 180 && ratio >= 1.20f && ratio <= 2.55f)
                || (width >= 240 && height >= 340
                && ratio >= 0.52f && ratio <= 1.04f);
    }

    static String pageKey(NativeContentItem item) {
        return item == null ? "" : normalized(item.url);
    }

    static String imageKey(NativeContentItem item) {
        return item == null ? "" : normalized(item.imageUrl);
    }

    private static String normalized(String url) {
        if (url == null) return "";
        String value = url.trim();
        int hash = value.indexOf('#');
        if (hash >= 0) value = value.substring(0, hash);
        while (value.endsWith("/") && value.length() > 8) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static boolean isWebUrl(String url) {
        if (url == null) return false;
        String value = url.trim().toLowerCase(Locale.US);
        return value.startsWith("https://") || value.startsWith("http://");
    }
}
