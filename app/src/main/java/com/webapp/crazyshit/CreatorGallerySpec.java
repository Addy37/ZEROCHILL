package com.webapp.crazyshit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Launch metadata for one Favorite Creator card, including manually grouped source profiles. */
final class CreatorGallerySpec {
    private static final int MAX_SEEDS = 8;

    final NativeContentItem item;
    final String query;
    final String profileHint;
    final String cacheKey;
    final boolean grouped;
    final ArrayList<String> seedNames;
    final ArrayList<String> seedUrls;
    final ArrayList<String> seedImages;

    private CreatorGallerySpec(
            NativeContentItem item,
            String query,
            String profileHint,
            String cacheKey,
            boolean grouped,
            ArrayList<String> seedNames,
            ArrayList<String> seedUrls,
            ArrayList<String> seedImages
    ) {
        this.item = item;
        this.query = query;
        this.profileHint = profileHint;
        this.cacheKey = cacheKey;
        this.grouped = grouped;
        this.seedNames = seedNames;
        this.seedUrls = seedUrls;
        this.seedImages = seedImages;
    }

    static CreatorGallerySpec from(CreatorCatalog.FavoriteGroup group) {
        NativeContentItem item = group == null || group.item == null
                ? new NativeContentItem(
                        NativeContentItem.KIND_CREATOR,
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        ""
                )
                : group.item;
        String query = clean(item.searchQuery);
        if (query.isEmpty()) query = clean(item.title);
        boolean grouped = group != null && group.members.size() > 1;
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> urls = new ArrayList<>();
        ArrayList<String> images = new ArrayList<>();
        if (grouped) {
            Set<String> seenNames = new HashSet<>();
            Set<String> seenUrls = new HashSet<>();
            for (NativeContentItem member : group.members.values()) {
                if (member == null || names.size() >= MAX_SEEDS) break;
                String name = clean(member.searchQuery);
                if (name.isEmpty()) name = clean(member.title);
                String normalizedName = CreatorNameMatcher.normalized(name);
                if (normalizedName.isEmpty() || !seenNames.add(normalizedName)) continue;

                String url = clean(member.url);
                if (knownGallerySource(url)) {
                    String canonical = url.replaceAll("/+$", "").toLowerCase(Locale.US);
                    if (!seenUrls.add(canonical)) url = "";
                } else {
                    url = "";
                }
                names.add(name);
                urls.add(url);
                images.add(clean(member.imageUrl));
            }
        }
        String cacheKey = grouped ? groupedCacheKey(group, query) : query;
        return new CreatorGallerySpec(
                item,
                query,
                NativeFeedBrowserActivity.creatorProfileHint(item),
                cacheKey,
                grouped,
                names,
                urls,
                images
        );
    }

    private static boolean knownGallerySource(String url) {
        if (url.isEmpty()) return false;
        if (FapelloRepository.isModelUrl(url)
                || OnlyHavenRepository.isOnlyHavenUrl(url)
                || BunkrRepository.isAlbumUrl(url)) {
            return true;
        }
        return WikiFeetRepository.isProfileUrl(url, WikiFeetRepository.Site.WIKIFEET)
                || WikiFeetRepository.isProfileUrl(url, WikiFeetRepository.Site.WIKIFEET_X);
    }

    private static String groupedCacheKey(
            CreatorCatalog.FavoriteGroup group,
            String query
    ) {
        ArrayList<String> keys = new ArrayList<>(group.relationshipKeys);
        Collections.sort(keys);
        StringBuilder identity = new StringBuilder(query);
        for (String key : keys) identity.append('\n').append(key == null ? "" : key);
        String digest = digest(identity.toString());
        return "merged:v2:" + digest;
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < Math.min(12, bytes.length); i++) {
                out.append(String.format(Locale.US, "%02x", bytes[i] & 0xff));
            }
            return out.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
