package com.webapp.crazyshit;

import android.content.Context;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Strictly links BaddieHub title candidates to verified OnlyFap creators. */
final class BaddieHubCreatorMatcher {
    private static final int CANDIDATE_LIMIT = 24;
    private static final int LIVE_LIMIT = 6;

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
        NativeContentItem learned = learned(app, media.title);
        if (learned != null) return learned;

        // Resolve reviewed aliases to their canonical name before enriching profile/artwork.
        NativeContentItem bundled = BundledCreatorIndex.get(app).exact(media.title);
        if (bundled == null) return null;
        NativeContentItem enriched = learned(app, bundled.title);
        return enriched == null ? bundled : enriched;
    }

    /**
     * Background-only fallback. Local catalog and reviewed aliases always win. The source calls
     * accept only an exact normalized creator name and save a successful identity for later clips.
     */
    static NativeContentItem matchLive(Context context, NativeContentItem media) {
        if (context == null || !isCandidate(media)) return null;
        Context app = context.getApplicationContext();
        NativeContentItem local = match(app, media);
        if (local != null) return local;

        NativeContentItem fapello = exactFapello(app, media.title);
        if (fapello != null) return remember(app, fapello);

        NativeContentItem onlyHaven = exactOnlyHaven(app, media.title);
        return onlyHaven == null ? null : remember(app, onlyHaven);
    }

    private static NativeContentItem exactFapello(Context app, String title) {
        String wanted = CreatorNameMatcher.normalized(title);
        if (wanted.isEmpty()) return null;
        try {
            for (FapelloRepository.Model model :
                    new FapelloRepository().searchConfirmedModels(app, title, LIVE_LIMIT)) {
                if (model == null || !wanted.equals(CreatorNameMatcher.normalized(model.name))) continue;
                return CreatorCatalog.fromModel(model);
            }
        } catch (Exception unavailable) {
            ShitTokPoolDiagnostics.record("creatorLiveFapello", "status=unavailable");
        }
        return null;
    }

    private static NativeContentItem exactOnlyHaven(Context app, String title) {
        String wanted = CreatorNameMatcher.normalized(title);
        if (wanted.isEmpty()) return null;
        try {
            for (OnlyHavenRepository.Creator creator : new OnlyHavenRepository()
                    .searchCategoryCreators(app, title, LIVE_LIMIT, ContentPreference.ALL)) {
                if (creator == null || !wanted.equals(CreatorNameMatcher.normalized(creator.name))) continue;
                return FapzoneCreatorSearchRepository.onlyHavenItem(creator, creator.name);
            }
        } catch (Exception unavailable) {
            ShitTokPoolDiagnostics.record("creatorLiveOnlyHaven", "status=unavailable");
        }
        return null;
    }

    private static NativeContentItem remember(Context app, NativeContentItem creator) {
        CreatorCatalog.remember(app, Collections.singletonList(creator));
        return creator;
    }

    private static NativeContentItem learned(Context app, String title) {
        List<NativeContentItem> learned = CreatorCatalog.matching(
                app, title, false, CANDIDATE_LIMIT);
        NativeContentItem best = null;
        int bestRichness = -1;
        for (NativeContentItem candidate : learned) {
            if (!exact(title, candidate)) continue;
            int richness = richness(candidate);
            if (best == null || richness > bestRichness) {
                best = candidate;
                bestRichness = richness;
            }
        }
        return best;
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
