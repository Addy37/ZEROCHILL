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
        boolean onlyHaven = "onlyhaven".equalsIgnoreCase(uploader)
                || description.contains("onlyhaven");
        if (!onlyHaven) return "";

        String creator = clean(item.title);
        return isGenericCreatorLabel(creator) ? "" : creator;
    }

    static boolean hasCreator(NativeContentItem item) {
        return !creatorName(item).isEmpty();
    }

    static NativeContentItem creatorIdentity(Context context, NativeContentItem media) {
        String creator = creatorName(media);
        if (creator.isEmpty()) return null;

        String profileUrl = "";
        if (media != null) {
            if (FapelloRepository.isModelUrl(media.url)
                    || OnlyHavenRepository.isOnlyHavenUrl(media.url)) {
                profileUrl = media.url;
            } else if (FapelloRepository.isModelUrl(media.uploader)
                    || OnlyHavenRepository.isOnlyHavenUrl(media.uploader)) {
                profileUrl = media.uploader;
            }
        }

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
                return candidate.merge(fallback);
            }
        }
        return fallback;
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
