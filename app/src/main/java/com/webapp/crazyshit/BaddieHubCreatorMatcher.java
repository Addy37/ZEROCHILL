package com.webapp.crazyshit;

import android.content.Context;

import java.util.List;
import java.util.Locale;

/** Strictly links BaddieHub title candidates to creators already known by OnlyFap. */
final class BaddieHubCreatorMatcher {
    private static final int CANDIDATE_LIMIT = 24;

    private BaddieHubCreatorMatcher() { }

    static boolean isCandidate(NativeContentItem media) {
        if (media == null || !media.isVideo() || !BaddieHubRepository.isBaddieHubUrl(media.url)) {
            return false;
        }
        String title = clean(media.title);
        String normalized = CreatorNameMatcher.normalized(title);
        if (normalized.isEmpty() || normalized.codePoints().noneMatch(Character::isLetter)) {
            return false;
        }
        switch (normalized.toLowerCase(Locale.ROOT)) {
            case "baddiehub":
            case "video":
            case "random video":
            case "onlyfap":
                return false;
            default:
                return true;
        }
    }

    static NativeContentItem match(Context context, NativeContentItem media) {
        if (context == null || !isCandidate(media)) return null;
        Context app = context.getApplicationContext();

        // Prefer learned catalog metadata so a verified creator can open with known artwork/profile.
        List<NativeContentItem> learned = CreatorCatalog.matching(
                app, media.title, false, CANDIDATE_LIMIT);
        NativeContentItem best = null;
        int bestRichness = -1;
        for (NativeContentItem candidate : learned) {
            if (!exact(media.title, candidate)) continue;
            int richness = richness(candidate);
            if (best == null || richness > bestRichness) {
                best = candidate;
                bestRichness = richness;
            }
        }
        if (best != null) return best;

        // The immutable bundled index covers creators the user has not browsed yet. Its exact map
        // accepts only canonical names or reviewed aliases after the same normalization rules.
        return BundledCreatorIndex.get(app).exact(media.title);
    }

    static boolean exact(String baddieTitle, NativeContentItem creator) {
        if (creator == null || !creator.isCreator()) return false;
        String wanted = CreatorNameMatcher.normalized(baddieTitle);
        if (wanted.isEmpty()) return false;
        String title = CreatorNameMatcher.normalized(creator.title);
        String query = CreatorNameMatcher.normalized(creator.searchQuery);
        return wanted.equals(title) || wanted.equals(query);
    }

    private static int richness(NativeContentItem creator) {
        int score = 0;
        if (!clean(creator.url).isEmpty()) score += 4;
        if (!clean(creator.imageUrl).isEmpty()) score += 2;
        if (!clean(creator.searchQuery).isEmpty()) score += 1;
        return score;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
