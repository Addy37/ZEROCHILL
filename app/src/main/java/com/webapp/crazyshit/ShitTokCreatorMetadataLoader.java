package com.webapp.crazyshit;

import android.content.Context;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/** UI-owned cache. Catalog decoding/matching never runs in identity() or its result callback. */
final class ShitTokCreatorMetadataLoader {
    private static final long LIVE_SELECTED_DELAY_MS = 2_000L;
    private static final long LIVE_READY_DELAY_MS = 150L;
    private static volatile WeakReference<ShitTokCreatorMetadataLoader> activeLiveLoader =
            new WeakReference<>(null);

    private final Executor worker, ui;
    private final ScheduledThreadPoolExecutor liveWorker;
    private final Function<NativeContentItem, NativeContentItem> resolve, liveResolve;
    private final Consumer<String> ready;
    private final Map<String, NativeContentItem> identities = new HashMap<>();
    private final Map<String, NativeContentItem> mediaByUrl = new HashMap<>();
    private final Set<String> requested = new HashSet<>();
    private final Map<String, ScheduledFuture<?>> liveScheduled = new ConcurrentHashMap<>();
    private final Set<String> liveAttempted = ConcurrentHashMap.newKeySet();
    private volatile int generation;
    private volatile boolean closed;

    ShitTokCreatorMetadataLoader(Context context, Executor ui, Consumer<String> ready) {
        this(newMetadataWorker(), newLiveWorker(), ui, resolver(context), liveResolver(context), ready);
        activeLiveLoader = new WeakReference<>(this);
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

    static void onShitTokSelected(String url) {
        ShitTokCreatorMetadataLoader loader = activeLiveLoader.get();
        if (loader != null) loader.scheduleSelected(url, LIVE_SELECTED_DELAY_MS, false);
    }

    static void onShitTokPlaybackReady(String url) {
        ShitTokCreatorMetadataLoader loader = activeLiveLoader.get();
        if (loader != null) loader.scheduleSelected(url, LIVE_READY_DELAY_MS, true);
    }

    NativeContentItem identity(NativeContentItem media) {
        if (media == null) return null;
        mediaByUrl.put(media.url, media);
        if (!identities.containsKey(media.url))
            identities.put(media.url, ShitTokCreatorMetadata.creatorIdentity(null, media));
        NativeContentItem fallback = identities.get(media.url);
        boolean deferredBaddieHub = fallback == null && BaddieHubCreatorMatcher.isCandidate(media);
        if (deferredBaddieHub && AppPerformance.isShitTokSelected(media.url)) {
            scheduleSelected(
                    media.url,
                    AppPerformance.isShitTokSelectedReady(media.url)
                            ? LIVE_READY_DELAY_MS : LIVE_SELECTED_DELAY_MS,
                    AppPerformance.isShitTokSelectedReady(media.url)
            );
        }
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

    private void scheduleSelected(String url, long delayMs, boolean replaceExisting) {
        if (closed || liveWorker == null || liveResolve == null || url == null || url.isEmpty()
                || liveAttempted.contains(url)) return;
        NativeContentItem media = mediaByUrl.get(url);
        if (media == null || !BaddieHubCreatorMatcher.isCandidate(media)
                || identities.get(url) != null) return;
        ScheduledFuture<?> existing = liveScheduled.get(url);
        if (existing != null && !replaceExisting) return;
        if (existing != null) existing.cancel(false);
        final int expected = generation;
        try {
            ScheduledFuture<?> scheduled = liveWorker.schedule(
                    () -> runLiveSelected(media, expected), delayMs, TimeUnit.MILLISECONDS);
            liveScheduled.put(url, scheduled);
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void runLiveSelected(NativeContentItem media, int expected) {
        liveScheduled.remove(media.url);
        if (closed || expected != generation || !AppPerformance.isShitTokSelected(media.url)
                || !liveAttempted.add(media.url)) return;
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
        mediaByUrl.clear();
        requested.clear();
        liveAttempted.clear();
        for (ScheduledFuture<?> future : liveScheduled.values()) future.cancel(false);
        liveScheduled.clear();
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
        ShitTokCreatorMetadataLoader active = activeLiveLoader.get();
        if (active == this) activeLiveLoader = new WeakReference<>(null);
        if (worker instanceof ExecutorService) ((ExecutorService) worker).shutdownNow();
        if (liveWorker != null) liveWorker.shutdownNow();
    }
}
