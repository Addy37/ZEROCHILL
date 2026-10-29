package com.webapp.crazyshit;

import android.content.Context;

import java.util.List;
import java.util.Locale;

/** Maps creator-based OnlyFap clips to the clean creator label used by ShitTok. */
final class ShitTokCreatorMetadata {
    private ShitTokCreatorMetadata() {
    }

    static String creatorName(NativeContentItem item) {
        if (item == null) return "";

        if (FapelloRepository.isFapelloUrl(item.url)) {
            String creator = clean(item.uploader);
            return isGenericCreatorLabel(creator) ? "" : creator;
        }

        String uploader = clean(item.uploader);
        String description = clean(item.description).toLowerCase(Locale.US);
        boolean onlyHaven = OnlyHavenRepository.isOnlyHavenUrl(uploader)
                || "onlyhaven".equalsIgnoreCase(uploader)
                || description.contains("onlyhaven");
        if (!onlyHaven) return "";

        String creator = clean(item.searchQuery);
        if (isGenericCreatorLabel(creator)) creator = clean(item.title);
        return isGenericCreatorLabel(creator) ? "" : creator;
    }

    static boolean hasCreator(NativeContentItem item) {
        return !creatorName(item).isEmpty();
    }

    static NativeContentItem creatorIdentity(Context context, NativeContentItem media) {
        if (BaddieHubCreatorMatcher.isCandidate(media)) {
            // A BaddieHub title is only a candidate. Do not expose a creator identity until the
            // background catalog lookup confirms an exact normalized OnlyFap creator match.
            return context == null ? null : BaddieHubCreatorMatcher.match(context, media);
        }

        String creator = creatorName(media);
        if (creator.isEmpty()) return null;

        String profileUrl = knownProfileUrl(media);

        NativeContentItem fallback = new NativeContentItem(
                NativeContentItem.KIND_CREATOR,
                creator,
                profileUrl,
                media == null ? "" : clean(media.imageUrl),
                "",
                media == null ? "" : clean(media.url),
                "",
                "ShitTok creator",
                creator
        );
        if (context == null) return fallback;

        String wanted = CreatorNameMatcher.normalized(creator);
        List<NativeContentItem> candidates =
                CreatorCatalog.matching(context, creator, false, 12);
        for (NativeContentItem candidate : candidates) {
            if (candidate == null) continue;
            String title = CreatorNameMatcher.normalized(candidate.title);
            String query = CreatorNameMatcher.normalized(candidate.searchQuery);
            if (wanted.equals(title) || wanted.equals(query)) {
                // Catalog artwork/name can enrich the clip, but must not replace its exact profile.
                NativeContentItem knownProfile = new NativeContentItem(
                        NativeContentItem.KIND_CREATOR, "", profileUrl, "", "", "", "", "", "");
                return knownProfile.merge(candidate).merge(fallback);
            }
        }
        return fallback;
    }

    static String knownProfileUrl(NativeContentItem media) {
        String profileUrl = "";
        if (media != null) {
            // Popular Fapello media keeps the display name in uploader and the exact
            // creator lookup URL in searchQuery; creator identities still search by name.
            if (FapelloRepository.isFapelloUrl(media.url)
                    && FapelloRepository.isModelUrl(media.searchQuery)) {
                profileUrl = media.searchQuery;
            } else if (isOnlyHavenProfile(media.uploader)) {
                profileUrl = media.uploader;
            } else if (FapelloRepository.isModelUrl(media.url)
                    || isOnlyHavenProfile(media.url)) {
                profileUrl = media.url;
            } else if (FapelloRepository.isModelUrl(media.uploader)) {
                profileUrl = media.uploader;
            }
        }

        return profileUrl;
    }

    private static boolean isOnlyHavenProfile(String url) {
        if (!OnlyHavenRepository.isOnlyHavenUrl(url)) return false;
        try {
            String path = new java.net.URI(url).getPath();
            // Media CDN hosts also end in cum.st; a playable URL is not a creator profile.
            return path != null && path.matches("/creators/[^/]+/[^/]+/?");
        } catch (java.net.URISyntaxException invalid) {
            return false;
        }
    }

    private static boolean isGenericCreatorLabel(String value) {
        if (value.isEmpty()) return true;
        String lower = value.toLowerCase(Locale.US);
        return "fapello".equals(lower)
                || "onlyfap".equals(lower)
                || "onlyhaven".equals(lower)
                || "onlyhaven media".equals(lower)
                || "random video".equals(lower);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
