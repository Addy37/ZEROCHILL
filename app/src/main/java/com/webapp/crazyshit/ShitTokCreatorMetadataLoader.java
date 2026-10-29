package com.webapp.crazyshit;

import android.content.Context;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/** UI-owned cache. Catalog decoding/matching never runs in identity() or its result callback. */
final class ShitTokCreatorMetadataLoader {
    private static final long LIVE_INITIAL_DELAY_MS = 350L;
    private static final long LIVE_RECHECK_MS = 450L;

    private final Executor worker, ui;
    private final ScheduledThreadPoolExecutor liveWorker;
    private final Function<NativeContentItem, NativeContentItem> resolve, liveResolve;
    private final Consumer<String> ready;
    private final Map<String, NativeContentItem> identities = new HashMap<>();
    private final Map<String, Integer> observations = new HashMap<>();
    private final Set<String> requested = new HashSet<>();
    private final Set<String> liveWatching = ConcurrentHashMap.newKeySet();
    private final Set<String> liveAttempted = ConcurrentHashMap.newKeySet();
    private volatile int generation;
    private volatile boolean closed;

    ShitTokCreatorMetadataLoader(Context context, Executor ui, Consumer<String> ready) {
        this(newMetadataWorker(), newLiveWorker(), ui, resolver(context), liveResolver(context), ready);
    }

    private static ThreadPoolExecutor newMetadataWorker() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(48), task -> {
                    Thread thread = new Thread(task, "ShitTokCreatorMetadata");
                    thread.setPriority(Thread.MIN_PRIORITY);
                    thread.setDaemon(true);
                    return thread;
                });
    }

    private static ScheduledThreadPoolExecutor newLiveWorker() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "ShitTokCreatorLive");
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
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

    static Function<NativeContentItem, NativeContentItem> liveResolver(Context context) {
        Context app = context.getApplicationContext();
        return media -> BaddieHubCreatorMatcher.matchLive(app, media);
    }

    ShitTokCreatorMetadataLoader(Executor worker, Executor ui,
            Function<NativeContentItem, NativeContentItem> resolve, Consumer<String> ready) {
        this(worker, null, ui, resolve, null, ready);
    }

    ShitTokCreatorMetadataLoader(Executor worker, ScheduledThreadPoolExecutor liveWorker, Executor ui,
            Function<NativeContentItem, NativeContentItem> resolve,
            Function<NativeContentItem, NativeContentItem> liveResolve, Consumer<String> ready) {
        this.worker = worker;
        this.liveWorker = liveWorker;
        this.ui = ui;
        this.resolve = resolve;
        this.liveResolve = liveResolve;
        this.ready = ready;
    }

    NativeContentItem identity(NativeContentItem media) {
        if (media == null) return null;
        int seen = observations.getOrDefault(media.url, 0) + 1;
        observations.put(media.url, seen);
        if (!identities.containsKey(media.url))
            identities.put(media.url, ShitTokCreatorMetadata.creatorIdentity(null, media));
        NativeContentItem fallback = identities.get(media.url);
        boolean deferredBaddieHub = fallback == null && BaddieHubCreatorMatcher.isCandidate(media);
        // The first observation is feed admission. A later observation means the card has been
        // bound or refreshed near the viewport. Only then keep a lightweight live-match watch.
        if (deferredBaddieHub && seen > 1) watchLiveBaddieHub(media);
        if (closed || (!deferredBaddieHub && fallback == null) || !requested.add(media.url)) {
            return fallback;
        }
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

    private void watchLiveBaddieHub(NativeContentItem media) {
        if (closed || liveWorker == null || liveResolve == null
                || liveAttempted.contains(media.url) || !liveWatching.add(media.url)) return;
        scheduleLiveCheck(media, generation, LIVE_INITIAL_DELAY_MS);
    }

    private void scheduleLiveCheck(NativeContentItem media, int expected, long delayMs) {
        try {
            liveWorker.schedule(() -> runLiveCheck(media, expected), delayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException closedWorker) {
            liveWatching.remove(media.url);
        }
    }

    private void runLiveCheck(NativeContentItem media, int expected) {
        if (closed || expected != generation) {
            liveWatching.remove(media.url);
            return;
        }
        if (!ShitTokPlaybackOwner.isForegroundWindow(media.url)) {
            liveWatching.remove(media.url);
            return;
        }
        if (!ShitTokPlaybackOwner.allowsCreatorLookup(media.url)) {
            scheduleLiveCheck(media, expected, LIVE_RECHECK_MS);
            return;
        }

        liveWatching.remove(media.url);
        if (!liveAttempted.add(media.url)) return;
        long started = android.os.SystemClock.elapsedRealtime();
        NativeContentItem matched = null;
        try { matched = liveResolve.apply(media); }
        catch (RuntimeException unavailable) {
            ShitTokPoolDiagnostics.record("creatorMetadataLive", "status=fallback");
        }
        NativeContentItem result = matched;
        ShitTokPoolDiagnostics.record("creatorMetadataLive", "ms="
                + (android.os.SystemClock.elapsedRealtime() - started)
                + " matched=" + (result != null));
        if (result == null) return;
        ui.execute(() -> {
            if (closed || expected != generation) return;
            identities.put(media.url, result);
            ready.accept(media.url);
        });
    }

    void clear() {
        generation++;
        identities.clear();
        observations.clear();
        requested.clear();
        liveWatching.clear();
        liveAttempted.clear();
        if (worker instanceof ThreadPoolExecutor) ((ThreadPoolExecutor) worker).getQueue().clear();
        if (liveWorker != null) liveWorker.getQueue().clear();
    }

    int backlog() {
        int local = -1;
        if (worker instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor pool = (ThreadPoolExecutor) worker;
            local = pool.getActiveCount() + pool.getQueue().size();
        }
        if (liveWorker == null) return local;
        int live = liveWorker.getActiveCount() + liveWorker.getQueue().size();
        return local < 0 ? live : local + live;
    }

    void close() {
        closed = true;
        clear();
        if (worker instanceof ExecutorService) ((ExecutorService) worker).shutdownNow();
        if (liveWorker != null) liveWorker.shutdownNow();
    }
}
