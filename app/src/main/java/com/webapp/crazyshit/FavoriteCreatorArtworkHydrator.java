package com.webapp.crazyshit;

import android.content.Context;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** User-requested sequential artwork repair. Each success is saved before progressing. */
final class FavoriteCreatorArtworkHydrator {
    private static final int SEARCH_LIMIT = 6;

    interface Searcher {
        List<FapelloRepository.Model> search(Context context, String query, int limit)
                throws IOException;
    }

    interface Listener {
        void onResolved();
    }

    private final Searcher searcher;

    FavoriteCreatorArtworkHydrator() {
        this((context, query, limit) ->
                new FapelloRepository().searchConfirmedModels(context, query, limit));
    }

    FavoriteCreatorArtworkHydrator(Searcher searcher) {
        this.searcher = searcher;
    }

    interface Progress { void update(Result result); }

    static final class Result {
        final int total, completed, restored, unresolved, errors;
        final boolean cancelled;
        Result(int total, int completed, int restored, int unresolved, int errors, boolean cancelled) {
            this.total = total; this.completed = completed; this.restored = restored;
            this.unresolved = unresolved; this.errors = errors; this.cancelled = cancelled;
        }
    }

    // Retained for existing callers/tests. The explicit operation always scans the full collection.
    void hydrate(Context context, int ignoredLimit, Listener listener) {
        final int[] last = {0};
        fetchMissing(context, () -> false, result -> {
            if (listener != null && result.restored > last[0]) listener.onResolved();
            last[0] = result.restored;
        });
    }

    Result fetchMissing(Context context, java.util.function.BooleanSupplier cancelled, Progress progress) {
        Context app = context.getApplicationContext();
        java.util.ArrayList<CreatorCatalog.FavoriteGroup> missing = new java.util.ArrayList<>();
        java.util.HashSet<String> identities = new java.util.HashSet<>();
        for (CreatorCatalog.FavoriteGroup group : CreatorCatalog.favoriteGroups(app)) {
            if (group == null || group.item == null || group.customAvatar || usableImage(group.item.imageUrl)) continue;
            String identity = CreatorIdentity.key(group.item.searchQuery.isEmpty() ? group.item.title : group.item.searchQuery);
            if (identities.add(identity)) missing.add(group);
        }
        int completed = 0, restored = 0, unresolved = 0, errors = 0;
        if (progress != null) progress.update(new Result(missing.size(), 0, 0, 0, 0, false));
        for (CreatorCatalog.FavoriteGroup group : missing) {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) break;
            String query = clean(group.item.searchQuery);
            if (query.isEmpty()) query = clean(group.item.title);
            try {
                FapelloRepository.Model model = query.length() < 2 ? null
                        : exactArtworkMatch(query, searcher.search(app, query, SEARCH_LIMIT));
                if (model == null) unresolved++;
                else {
                    java.util.ArrayList<NativeContentItem> metadata = new java.util.ArrayList<>();
                    // Save to the existing raw identity too, so reviewed aliases and old favorites
                    // receive artwork without changing their favorite keys or grouping data.
                    for (String key : group.members.keySet()) {
                        if (!CreatorIdentity.sameReviewedIdentity(key, model.name)
                                && !CreatorNameMatcher.normalized(key).equals(CreatorNameMatcher.normalized(model.name))) continue;
                        metadata.add(new NativeContentItem(NativeContentItem.KIND_CREATOR, key,
                                model.url, model.imageUrl, "", model.url, "", "Fapello", key));
                    }
                    metadata.add(CreatorCatalog.fromModel(model));
                    CreatorCatalog.remember(app, metadata);
                    restored++;
                }
            } catch (IOException | RuntimeException error) { errors++; }
            completed++;
            if (progress != null) progress.update(new Result(missing.size(), completed, restored, unresolved, errors, false));
        }
        return new Result(missing.size(), completed, restored, unresolved, errors,
                completed < missing.size());
    }

    static FapelloRepository.Model exactArtworkMatch(
            String query,
            List<FapelloRepository.Model> models
    ) {
        String target = CreatorNameMatcher.normalized(clean(query));
        if (target.isEmpty() || models == null) return null;
        for (FapelloRepository.Model model : models) {
            if (model == null || !usableImage(model.imageUrl)) continue;
            if (target.equals(CreatorNameMatcher.normalized(clean(model.name)))
                    || CreatorIdentity.sameReviewedIdentity(query, model.name)) {
                return model;
            }
        }
        return null;
    }

    static boolean usableImage(String value) {
        String clean = clean(value);
        if (clean.isEmpty()) return false;
        String lower = clean.toLowerCase(Locale.US);
        return (lower.startsWith("https://") || lower.startsWith("http://"))
                && !lower.contains("load.svg");
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').trim();
    }
}
