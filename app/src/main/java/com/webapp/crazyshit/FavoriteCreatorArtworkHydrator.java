package com.webapp.crazyshit;

import android.content.Context;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Restores artwork for account-synced creator favorites after a fresh local install.
 *
 * Account sync intentionally stores stable creator keys, not transient source artwork URLs.
 * This resolver walks all favorites that are still missing artwork, then lets the
 * normal CreatorCatalog persist the resolved metadata for later launches.
 */
final class FavoriteCreatorArtworkHydrator {
    static final String RETRIES = "favorite_creator_artwork_lookups_v1";
    static final long MISS_RETRY_MS = java.util.concurrent.TimeUnit.HOURS.toMillis(24);
    static final long ERROR_RETRY_MS = java.util.concurrent.TimeUnit.MINUTES.toMillis(15);
    private static final Object LOOKUP_LOCK = new Object();
    private static final java.util.Set<String> IN_FLIGHT = new java.util.HashSet<>();
    private static final java.util.concurrent.ExecutorService RESTORE_IO =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private static android.app.Application pendingContext;
    private static boolean scheduled;

    /** Application context only: restoration can finish after the screen closes. */
    static synchronized void schedule(Context context) {
        Context app = context.getApplicationContext();
        if (!(app instanceof android.app.Application)) return;
        pendingContext = (android.app.Application) app;
        if (scheduled) return;
        scheduled = true;
        RESTORE_IO.execute(() -> {
            FavoriteCreatorArtworkHydrator resolver = new FavoriteCreatorArtworkHydrator();
            while (true) {
                Context next;
                synchronized (FavoriteCreatorArtworkHydrator.class) {
                    next = pendingContext;
                    pendingContext = null;
                    if (next == null) { scheduled = false; return; }
                }
                try { resolver.hydrate(next, null); }
                catch (RuntimeException ignored) { /* A later request can retry the scan. */ }
            }
        });
    }
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

    void hydrate(Context context, Listener listener) {
        if (context == null || searcher == null) return;
        Context appContext = context.getApplicationContext();
        android.content.SharedPreferences retries = appContext.getSharedPreferences(RETRIES, Context.MODE_PRIVATE);
        android.content.SharedPreferences favoritePrefs = appContext.getSharedPreferences("creator_favorites", Context.MODE_PRIVATE);
        String owner = favoritePrefs.getString("account_owner", "");
        List<CreatorCatalog.FavoriteGroup> favorites = CreatorCatalog.favoriteGroups(appContext);
        // Expired negative entries carry no value and need not grow with removed favorites.
        long now = System.currentTimeMillis();
        android.content.SharedPreferences.Editor expired = retries.edit();
        for (java.util.Map.Entry<String, ?> entry : retries.getAll().entrySet())
            if (entry.getValue() instanceof Long && (Long) entry.getValue() <= now) expired.remove(entry.getKey());
        expired.apply();
        for (CreatorCatalog.FavoriteGroup group : favorites) {
            if (Thread.currentThread().isInterrupted()) return;
            if (group == null || group.item == null || usableImage(group.item.imageUrl)) {
                continue;
            }

            String storedQuery = clean(group.item.searchQuery);
            if (storedQuery.isEmpty()) storedQuery = clean(group.item.title);
            String query = CreatorIdentity.reviewed(storedQuery) ? CreatorIdentity.display(storedQuery) : storedQuery;
            if (query.length() < 2) continue;

            if (!owner.equals(favoritePrefs.getString("account_owner", ""))) return;
            if (java.util.Collections.disjoint(group.relationshipKeys, CreatorFavoriteStore.names(appContext))) continue;
            String identity = CreatorIdentity.reviewed(query) ? CreatorIdentity.key(query)
                    : CreatorFavoriteStore.key(group.item);
            String lookupKey = appContext.getPackageName() + ":" + identity;
            String retryKey = "retry:" + identity;
            synchronized (LOOKUP_LOCK) {
                long retryAt = retries.getLong(retryKey, 0L);
                if (IN_FLIGHT.contains(lookupKey) || retryAt > System.currentTimeMillis()) continue;
                IN_FLIGHT.add(lookupKey);
            }
            long retryDelay = MISS_RETRY_MS;
            boolean interrupted = false;
            try {
                FapelloRepository.Model model = exactArtworkMatch(query,
                        searcher.search(appContext, query, SEARCH_LIMIT));
                interrupted = Thread.currentThread().isInterrupted();
                if (interrupted) return;
                if (!owner.equals(favoritePrefs.getString("account_owner", ""))) {
                    interrupted = true; return;
                }
                if (model == null || java.util.Collections.disjoint(
                        group.relationshipKeys, CreatorFavoriteStore.names(appContext))) continue;
                NativeContentItem resolved = CreatorCatalog.fromModel(model);
                // Keep the raw synced lookup key, even when its punctuation differs from the display name.
                NativeContentItem keyed = new NativeContentItem(resolved.kind, resolved.title,
                        resolved.url, resolved.imageUrl, resolved.views, resolved.uploader,
                        resolved.comments, resolved.description, storedQuery);
                CreatorCatalog.remember(appContext, java.util.Arrays.asList(resolved, keyed));
                if (listener != null) listener.onResolved();
            } catch (IOException | RuntimeException failure) {
                if (failure instanceof java.io.InterruptedIOException) Thread.currentThread().interrupt();
                interrupted = Thread.currentThread().isInterrupted();
                retryDelay = ERROR_RETRY_MS;
            } finally {
                synchronized (LOOKUP_LOCK) {
                    if (!interrupted) retries.edit().putLong(retryKey,
                            System.currentTimeMillis() + retryDelay).apply();
                    IN_FLIGHT.remove(lookupKey);
                }
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
            if (target.equals(CreatorNameMatcher.normalized(clean(model.name)))
                    || CreatorIdentity.sameReviewedIdentity(query, model.name)) {
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
