package com.webapp.crazyshit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;

/** Launch metadata for one Favorite Creator card, including manually grouped source profiles. */
final class CreatorGallerySpec {
    private static final int MAX_SEEDS = 8;

    final NativeContentItem item;
    final String query;
    final String profileHint;
    final String cacheKey; // Persisted favorite and manual-group reference.
    final String sessionKey;
    final ArrayList<String> aliases;
    final ArrayList<String> memberSessionKeys;
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
            ArrayList<String> seedImages,
            ArrayList<String> aliases,
            ArrayList<String> memberSessionKeys
    ) {
        this.item = item;
        this.query = query;
        this.profileHint = profileHint;
        this.cacheKey = cacheKey;
        this.sessionKey = sessionKey(query, profileHint, cacheKey, seedNames, seedUrls, aliases);
        this.aliases = aliases;
        this.memberSessionKeys = memberSessionKeys;
        this.grouped = grouped;
        this.seedNames = seedNames;
        this.seedUrls = seedUrls;
        this.seedImages = seedImages;
    }

    static CreatorGallerySpec from(NativeContentItem item) {
        return from(new CreatorCatalog.FavoriteGroup(item, new java.util.LinkedHashMap<>(),
                Collections.emptySet(), false));
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
        LinkedHashSet<String> aliases = new LinkedHashSet<>(item.creatorAliases);
        ArrayList<String> memberSessionKeys = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        if (grouped) {
            Set<String> seenNames = new HashSet<>();
            for (NativeContentItem member : group.members.values()) {
                if (names.size() >= MAX_SEEDS) break;
                if (member == null || WikiFeetRepository.isRetiredItem(member)) continue;
                aliases.addAll(member.creatorAliases);
                memberSessionKeys.add(from(member).sessionKey);
                String name = clean(member.searchQuery);
                if (name.isEmpty()) name = clean(member.title);
                String normalizedName = CreatorNameMatcher.normalized(name);
                boolean newName = !normalizedName.isEmpty() && seenNames.add(normalizedName);

                String url = clean(member.url);
                if (knownGallerySource(url)) {
                    String canonical = url.replaceAll("/+$", "").toLowerCase(Locale.US);
                    if (!seenUrls.add(canonical)) url = "";
                } else {
                    url = "";
                }
                if (!newName && url.isEmpty()) continue;
                names.add(name);
                urls.add(url);
                images.add(clean(member.imageUrl));
            }
        }
        List<NativeContentItem> members = new ArrayList<>();
        members.add(item);
        if (grouped) members.addAll(group.members.values());
        for (NativeContentItem member : members) {
            if (member == null || WikiFeetRepository.isRetiredItem(member)) continue;
            for (String url : member.creatorProfileUrls) {
                if (names.size() >= MAX_SEEDS) break;
                if (!knownGallerySource(url)
                        || !seenUrls.add(url.replaceAll("/+$", "").toLowerCase(Locale.US))) continue;
                names.add("");
                urls.add(url);
                images.add("");
            }
        }
        String cacheKey = grouped ? groupedCacheKey(group, query) : query;
        ArrayList<String> verifiedAliases = new ArrayList<>(aliases);
        if (verifiedAliases.size() > MAX_SEEDS) verifiedAliases.subList(MAX_SEEDS, verifiedAliases.size()).clear();
        return new CreatorGallerySpec(
                item,
                query,
                NativeFeedBrowserActivity.creatorProfileHint(item),
                cacheKey,
                grouped,
                names,
                urls,
                images,
                verifiedAliases,
                memberSessionKeys
        );
    }

    private static boolean knownGallerySource(String url) {
        if (url.isEmpty()) return false;
        if (PawchiveRepository.isProfile(url) || FapelloRepository.isModelUrl(url)
                || OnlyHavenRepository.isOnlyHavenUrl(url)
                || BunkrRepository.isAlbumUrl(url)) {
            return true;
        }
        return WikiFeetRepository.isXProfileUrl(url);
    }

    static String sessionKey(String query, String profile, String favoriteKey,
                             List<String> names, List<String> urls, List<String> aliases) {
        ArrayList<String> evidence = new ArrayList<>();
        addEvidence(evidence, "name:", names);
        addEvidence(evidence, "profile:", urls);
        addEvidence(evidence, "alias:", aliases);
        Collections.sort(evidence);
        String prefix = PawchiveRepository.isProfile(profile) ? "creator-gallery:paw-bunkr-v1:" : "creator-gallery:v3:";
        return prefix + digest(clean(query).toLowerCase(Locale.ROOT) + "\n"
                + clean(profile) + "\n" + clean(favoriteKey).toLowerCase(Locale.ROOT)
                + "\n" + String.join("\n", evidence));
    }

    private static void addEvidence(List<String> output, String type, List<String> values) {
        if (values == null) return;
        for (String value : values) {
            String cleaned = clean(value);
            if (!cleaned.isEmpty()) output.add(type + cleaned);
        }
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
                out.append(Character.forDigit((bytes[i] >> 4) & 0xf, 16));
                out.append(Character.forDigit(bytes[i] & 0xf, 16));
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
