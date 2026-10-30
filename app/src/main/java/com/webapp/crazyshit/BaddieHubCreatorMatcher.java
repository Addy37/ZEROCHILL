package com.webapp.crazyshit;

import android.content.Context;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Worker-owned, conservative bridge from an already cleaned title to an OnlyFap identity. */
final class BaddieHubCreatorMatcher {
    interface Search { List<NativeContentItem> find(String title) throws IOException; }
    private final Context app;
    private final Search search;
    private final Map<String, NativeContentItem> successes = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> ambiguous = java.util.concurrent.ConcurrentHashMap.newKeySet();

    BaddieHubCreatorMatcher(Context context) {
        this(context, title -> {
            List<NativeContentItem> candidates = new ArrayList<>();
            FapelloRepository fapello = new FapelloRepository();
            IOException failure = null;
            try {
                for (FapelloRepository.Model model : fapello.searchConfirmedModels(context, title, 8)) {
                    // Search suggestions include partial matches. A canonical profile's explicit
                    // alias header can verify a different display name, never arbitrary page text.
                    if (CreatorNameMatcher.rank(model.name, title) == 0
                            || fapello.confirmsCreatorAlias(context, model, title))
                        candidates.add(CreatorCatalog.fromModel(model));
                }
            } catch (IOException unavailable) { failure = unavailable; }
            if (!candidates.isEmpty()) return candidates;
            try {
                for (OnlyHavenRepository.Creator creator : new OnlyHavenRepository()
                        .searchCategoryCreators(context, title, 8, ContentPreference.ALL)) {
                    if (CreatorNameMatcher.rank(creator.name, title) == 0)
                        candidates.add(FapzoneCreatorSearchRepository.onlyHavenItem(creator, creator.name));
                }
            } catch (IOException unavailable) { failure = unavailable; }
            if (candidates.isEmpty() && failure != null) throw failure;
            return candidates;
        });
    }

    BaddieHubCreatorMatcher(Context context, Search search) {
        app = context.getApplicationContext();
        this.search = search;
    }

    static boolean isCandidate(NativeContentItem media) {
        if (media == null || !media.isVideo() || !BaddieHubRepository.isBaddieHubUrl(media.url)) return false;
        String name = CreatorNameMatcher.normalized(media.title);
        return name.codePoints().anyMatch(Character::isLetter)
                && !name.equals("baddiehub") && !name.equals("video")
                && !name.equals("random video") && !name.equals("onlyfap");
    }

    NativeContentItem local(NativeContentItem media) {
        if (!isCandidate(media)) return null;
        NativeContentItem cached = successes.get(CreatorNameMatcher.normalized(media.title));
        if (cached != null) return cached;
        // Inspect the complete bounded local catalog, so a result limit cannot hide ambiguity.
        List<NativeContentItem> matches = new ArrayList<>();
        for (NativeContentItem creator : CreatorCatalog.all(app)) {
            if (exact(media.title, creator)) matches.add(creator);
        }
        if (!matches.isEmpty()) return localUnique(media.title, matches);
        if (BundledCreatorIndex.get(app).ambiguous(media.title)) {
            ambiguous.add(CreatorNameMatcher.normalized(media.title));
            return null;
        }
        NativeContentItem bundled = BundledCreatorIndex.get(app).exact(media.title);
        if (bundled == null) return null;
        for (NativeContentItem creator : CreatorCatalog.all(app)) {
            if (exact(bundled.title, creator)) matches.add(creator);
        }
        return matches.isEmpty() ? bundled : localUnique(media.title, matches);
    }

    private NativeContentItem localUnique(String title, List<NativeContentItem> matches) {
        NativeContentItem result = unique(matches);
        if (result == null) ambiguous.add(CreatorNameMatcher.normalized(title));
        return result;
    }

    NativeContentItem live(NativeContentItem media) throws IOException {
        NativeContentItem known = local(media);
        if (known != null || !isCandidate(media)
                || ambiguous.contains(CreatorNameMatcher.normalized(media.title))) return known;
        // The Search contract returns exact names or source-verified aliases, not suggestions.
        NativeContentItem result = unique(search.find(media.title.trim()));
        if (result != null) {
            successes.put(CreatorNameMatcher.normalized(media.title), result);
            CreatorCatalog.remember(app, Collections.singletonList(result));
        }
        return result;
    }

    static boolean exact(String title, NativeContentItem creator) {
        return creator != null && creator.isCreator()
                && (CreatorNameMatcher.normalized(title).equals(CreatorNameMatcher.normalized(creator.title))
                || CreatorNameMatcher.normalized(title).equals(CreatorNameMatcher.normalized(creator.searchQuery)));
    }

    static NativeContentItem unique(List<NativeContentItem> candidates) {
        NativeContentItem best = null;
        String key = null;
        for (NativeContentItem item : candidates) {
            if (item == null || !item.isCreator()) continue;
            String identity = CreatorIdentity.reviewed(item.title) ? CreatorIdentity.key(item.title)
                    : CreatorFavoriteStore.key(item);
            if (key != null && !key.equals(identity)) return null;
            if (best != null && !best.url.equals(item.url)
                    && (FapelloRepository.isModelUrl(best.url) && FapelloRepository.isModelUrl(item.url)
                    || OnlyHavenRepository.isOnlyHavenUrl(best.url)
                    && OnlyHavenRepository.isOnlyHavenUrl(item.url))) return null;
            key = identity;
            if (best == null || best.url.isEmpty() && !item.url.isEmpty()
                    || best.url.isEmpty() == item.url.isEmpty() && best.imageUrl.isEmpty()
                    && !item.imageUrl.isEmpty()) best = item;
        }
        return best;
    }
}
