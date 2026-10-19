package com.webapp.crazyshit;

import android.content.Context;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/** UI-owned cache. Catalog decoding/matching never runs in identity() or its result callback. */
final class ShitTokCreatorMetadataLoader {
    private final Executor worker, ui;
    private final Function<NativeContentItem, NativeContentItem> resolve;
    private final Consumer<String> ready;
    private final Map<String, NativeContentItem> identities = new HashMap<>();
    private final Set<String> requested = new HashSet<>();
    private volatile int generation;
    private volatile boolean closed;

    ShitTokCreatorMetadataLoader(Context context, Executor ui, Consumer<String> ready) {
        this(new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(48), task -> {
                    Thread thread = new Thread(task, "ShitTokCreatorMetadata");
                    thread.setPriority(Thread.MIN_PRIORITY);
                    thread.setDaemon(true);
                    return thread;
                }), ui, resolver(context), ready);
    }

    static Function<NativeContentItem, NativeContentItem> resolver(Context context) {
        Context app = context.getApplicationContext();
        Set<String> learned = new HashSet<>();
        return media -> {
            NativeContentItem identity = ShitTokCreatorMetadata.creatorIdentity(app, media);
            String profile = ShitTokCreatorMetadata.knownProfileUrl(media);
            String name = identity == null ? "" : identity.title;
            boolean named = identity != null
                    && (FapelloRepository.isFapelloUrl(media.url) || (!media.searchQuery.trim().isEmpty()
                    && media.searchQuery.trim().equals(ShitTokCreatorMetadata.creatorName(media))))
                    && !name.regionMatches(true, 0, "http://", 0, 7)
                    && !name.regionMatches(true, 0, "https://", 0, 8)
                    && name.codePoints().anyMatch(Character::isLetter);
            if (named && !profile.isEmpty()
                    && learned.add(CreatorFavoriteStore.key(identity) + "\n" + profile)) {
                boolean saved = false;
                for (NativeContentItem candidate : CreatorCatalog.matching(
                        app, identity.title, false, 12)) {
                    if (profile.equals(candidate.url)
                            && CreatorFavoriteStore.key(identity).equals(CreatorFavoriteStore.key(candidate))) {
                        saved = true;
                        break;
                    }
                }
                // Worker-only, once per creator/profile. Never learn a guessed name-only identity
                // or rewrite catalog artwork each time another clip from the creator is bound.
                if (!saved) {
                    NativeContentItem catalog = new NativeContentItem(identity.kind, identity.title,
                            profile, identity.imageUrl, identity.views, identity.uploader, identity.comments,
                            FapelloRepository.isModelUrl(profile) ? "Fapello" : "OnlyHaven",
                            identity.searchQuery);
                    CreatorCatalog.remember(app, java.util.Collections.singletonList(catalog));
                }
            }
            return identity;
        };
    }

    ShitTokCreatorMetadataLoader(Executor worker, Executor ui,
            Function<NativeContentItem, NativeContentItem> resolve, Consumer<String> ready) {
        this.worker = worker;
        this.ui = ui;
        this.resolve = resolve;
        this.ready = ready;
    }

    NativeContentItem identity(NativeContentItem media) {
        if (media == null) return null;
        if (!identities.containsKey(media.url))
            identities.put(media.url, ShitTokCreatorMetadata.creatorIdentity(null, media));
        NativeContentItem fallback = identities.get(media.url);
        if (closed || fallback == null || !requested.add(media.url)) return fallback;
        final int expected = generation;
        try {
            worker.execute(() -> {
                if (closed || expected != generation) return;
                long started = android.os.SystemClock.elapsedRealtime();
                NativeContentItem enriched = fallback;
                try { enriched = resolve.apply(media); }
                catch (RuntimeException unavailable) {
                    ShitTokPoolDiagnostics.record("creatorMetadata", "status=fallback");
                }
                NativeContentItem result = enriched == null ? fallback : enriched;
                ShitTokPoolDiagnostics.record("creatorMetadata", "ms="
                        + (android.os.SystemClock.elapsedRealtime() - started));
                ui.execute(() -> {
                    if (closed || expected != generation) return;
                    identities.put(media.url, result);
                    // Retain the key until clear(): rebinding must not repeat the catalog lookup.
                    ready.accept(media.url);
                });
            });
        } catch (RejectedExecutionException full) {
            requested.remove(media.url); // A later bind can retry; the fallback stays usable.
            ShitTokPoolDiagnostics.record("creatorMetadata", "status=queueFull");
        }
        return fallback;
    }

    void clear() {
        generation++;
        identities.clear();
        requested.clear();
        if (worker instanceof ThreadPoolExecutor) ((ThreadPoolExecutor) worker).getQueue().clear();
    }

    int backlog() {
        if (!(worker instanceof ThreadPoolExecutor)) return -1;
        ThreadPoolExecutor pool = (ThreadPoolExecutor) worker;
        return pool.getActiveCount() + pool.getQueue().size();
    }

    void close() {
        closed = true;
        clear();
        if (worker instanceof ExecutorService) ((ExecutorService) worker).shutdownNow();
    }
}
