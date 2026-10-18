package com.webapp.crazyshit;

import android.content.Context;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Restores artwork for account-synced creator favorites after a fresh local install.
 *
 * Account sync intentionally stores stable creator keys, not transient source artwork URLs.
 * This resolver only touches visible favorites that are still missing artwork, then lets the
 * normal CreatorCatalog persist the resolved metadata for later launches.
 */
final class FavoriteCreatorArtworkHydrator {
    static final int MAX_VISIBLE_FAVORITES = 10;
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

    void hydrate(Context context, int visibleLimit, Listener listener) {
        if (context == null || searcher == null) return;
        Context appContext = context.getApplicationContext();
        List<CreatorCatalog.FavoriteGroup> favorites = CreatorCatalog.favoriteGroups(appContext);
        int limit = Math.min(
                MAX_VISIBLE_FAVORITES,
                Math.min(Math.max(0, visibleLimit), favorites.size())
        );

        for (int index = 0; index < limit; index++) {
            if (Thread.currentThread().isInterrupted()) return;
            CreatorCatalog.FavoriteGroup group = favorites.get(index);
            if (group == null || group.item == null || !clean(group.item.imageUrl).isEmpty()) {
                continue;
            }

            String query = clean(group.item.searchQuery);
            if (query.isEmpty()) query = clean(group.item.title);
            if (query.length() < 2) continue;

            try {
                FapelloRepository.Model model = exactArtworkMatch(
                        query,
                        searcher.search(appContext, query, SEARCH_LIMIT)
                );
                if (model == null) continue;
                CreatorCatalog.remember(
                        appContext,
                        Collections.singletonList(CreatorCatalog.fromModel(model))
                );
                if (listener != null) listener.onResolved();
            } catch (IOException ignored) {
                // Keep the text fallback. The normal OnlyFap load may still learn this creator.
            } catch (RuntimeException ignored) {
                // One malformed creator must not prevent the remaining favorites from restoring.
            }
        }
    }

    static FapelloRepository.Model exactArtworkMatch(
            String query,
            List<FapelloRepository.Model> models
    ) {
        String target = CreatorNameMatcher.normalized(clean(query));
        if (target.isEmpty() || models == null) return null;
        for (FapelloRepository.Model model : models) {
            if (model == null || !usableImage(model.imageUrl)) continue;
            if (target.equals(CreatorNameMatcher.normalized(clean(model.name)))) {
                return model;
            }
        }
        return null;
    }

    private static boolean usableImage(String value) {
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
