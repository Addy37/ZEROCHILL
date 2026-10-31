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
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/** UI-owned cache. Catalog decoding/matching never runs in identity() or its result callback. */
final class ShitTokCreatorMetadataLoader {
    interface LiveResolver { NativeContentItem resolve(NativeContentItem media) throws java.io.IOException; }
    private final Executor worker, ui;
    private final ScheduledThreadPoolExecutor liveWorker;
    private final LiveResolver liveResolve;
    private ScheduledFuture<?> liveTask;
    private volatile NativeContentItem selected;
    private volatile int selection;
    private final Set<String> localFinished = new HashSet<>(), liveFinished = new HashSet<>();
    private final Map<String, Long> retryAt = new HashMap<>();
    private final Function<NativeContentItem, NativeContentItem> resolve;
    private final Consumer<String> ready;
    static final int MAX_IDENTITIES = 256;
    private final Map<String, NativeContentItem> identities = new java.util.LinkedHashMap<>(64, .75f, true);
    private long cacheHits, cacheMisses;
    private final Set<String> requested = new HashSet<>();
    private volatile int generation;
    private volatile boolean closed;

    ShitTokCreatorMetadataLoader(Context context, Executor ui, Consumer<String> ready) {
        this(context, ui, ready, new BaddieHubCreatorMatcher(context));
    }

    private ShitTokCreatorMetadataLoader(Context context, Executor ui, Consumer<String> ready,
            BaddieHubCreatorMatcher matcher) {
        this(new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(48), task -> {
                    Thread thread = new Thread(task, "ShitTokCreatorMetadata");
                    thread.setPriority(Thread.MIN_PRIORITY);
                    thread.setDaemon(true);
                    return thread;
                }), newLiveWorker(), ui, combinedResolver(context, matcher), matcher::live, ready);
    }

    private static Function<NativeContentItem, NativeContentItem> combinedResolver(
            Context context, BaddieHubCreatorMatcher matcher) {
        Function<NativeContentItem, NativeContentItem> ordinary = resolver(context);
        ShitTokCreatorIdentityCache baddie = new ShitTokCreatorIdentityCache();
        return media -> BaddieHubCreatorMatcher.isCandidate(media)
                && ShitTokCreatorMetadata.knownProfileUrl(media).isEmpty()
                ? baddie.resolve(CreatorNameMatcher.normalized(media.title), () -> matcher.local(media))
                : ordinary.apply(media);
    }

    private static ScheduledThreadPoolExecutor newLiveWorker() {
        ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "ShitTokCreatorLive");
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.setDaemon(true);
            return thread;
        });
        pool.setRemoveOnCancelPolicy(true);
        pool.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return pool;
    }

    static Function<NativeContentItem, NativeContentItem> resolver(Context context) {
        Context app = context.getApplicationContext();
        java.util.LinkedHashSet<String> learned = new java.util.LinkedHashSet<>();
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
                while (learned.size() > 512) learned.remove(learned.iterator().next());
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
        this(worker, null, ui, resolve, null, ready);
    }

    ShitTokCreatorMetadataLoader(Executor worker, ScheduledThreadPoolExecutor liveWorker, Executor ui,
            Function<NativeContentItem, NativeContentItem> resolve, LiveResolver liveResolve,
            Consumer<String> ready) {
        this.worker = worker;
        this.liveWorker = liveWorker;
        this.liveResolve = liveResolve;
        this.ui = ui;
        this.resolve = resolve;
        this.ready = ready;
    }

    NativeContentItem identity(NativeContentItem media) {
        if (media == null) return null;
        String key = key(media);
        long diagnosticToken = ShitTokPoolDiagnostics.modeToken();
        if (!identities.containsKey(key)) {
            cacheMisses++;
            ShitTokPoolDiagnostics.modeEvent(diagnosticToken, "metadataMisses", 1);
            identities.put(key, ShitTokCreatorMetadata.creatorIdentity(null, media));
            trim();
        } else {
            cacheHits++;
            ShitTokPoolDiagnostics.modeEvent(diagnosticToken, "metadataHits", 1);
        }
        NativeContentItem fallback = identities.get(key);
        if (closed || fallback == null && !BaddieHubCreatorMatcher.isCandidate(media)
                || !requested.add(key)) return fallback;
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
                    if (closed || expected != generation || !identities.containsKey(key)) return;
                    identities.put(key, result);
                    localFinished.add(key);
                    // Retain the key until clear(): rebinding must not repeat the catalog lookup.
                    ready.accept(media.url);
                    if (selected == media) scheduleLive(media);
                });
            });
        } catch (RejectedExecutionException full) {
            requested.remove(key); // A later bind can retry; the fallback stays usable.
            ShitTokPoolDiagnostics.record("creatorMetadata", "status=queueFull");
        }
        return fallback;
    }

    private static String key(NativeContentItem media) {
        String profile = ShitTokCreatorMetadata.knownProfileUrl(media);
        String name = ShitTokCreatorMetadata.creatorName(media);
        if (!profile.isEmpty() && !name.isEmpty())
            return ShitTokAspectPriority.sourceKey(media) + "\n" + profile + "\n" + CreatorIdentity.key(name);
        if (!name.isEmpty()) return ShitTokAspectPriority.sourceKey(media) + "\n" + CreatorIdentity.key(name);
        // An unresolved BaddieHub title is not yet a creator key. Successful worker matches
        // are memoized separately; a miss must not suppress lookup for later clips.
        return media.url + "\n" + media.title;
    }
    private void trim() {
        while (identities.size() > MAX_IDENTITIES) {
            String key = identities.keySet().iterator().next();
            identities.remove(key); requested.remove(key); localFinished.remove(key);
            liveFinished.remove(key); retryAt.remove(key);
        }
    }
    String cacheSummary() { return "metadataEntries=" + identities.size() + " metadataHits=" + cacheHits + " metadataMisses=" + cacheMisses; }

    /** Called directly by the feed owner, never through diagnostic logging or a static loader. */
    void select(NativeContentItem media) {
        if (selected != media) {
            selection++;
            selected = media;
            if (liveTask != null) liveTask.cancel(false);
            liveTask = null;
        }
        if (media == null || closed) return;
        identity(media);
        scheduleLive(media);
    }

    private void scheduleLive(NativeContentItem media) {
        String key = key(media);
        if (liveWorker == null || liveResolve == null || closed || selected != media
                || !BaddieHubCreatorMatcher.isCandidate(media) || !localFinished.contains(key)
                || identities.get(key) != null || liveFinished.contains(key)
                || android.os.SystemClock.elapsedRealtime() < retryAt.getOrDefault(key, 0L)
                || liveTask != null) return;
        int expected = generation, expectedSelection = selection;
        liveTask = liveWorker.schedule(() -> runLive(media, expected, expectedSelection),
                750L, TimeUnit.MILLISECONDS);
    }

    private void runLive(NativeContentItem media, int expected, int expectedSelection) {
        if (closed || generation != expected || selection != expectedSelection || selected != media) return;
        NativeContentItem matched = null;
        boolean failed = false;
        try { matched = liveResolve.resolve(media); }
        catch (java.io.IOException | RuntimeException unavailable) { failed = true; }
        NativeContentItem result = matched;
        boolean retry = failed;
        ui.execute(() -> {
            if (closed || generation != expected) return;
            String key = key(media);
            if (retry) retryAt.put(key, android.os.SystemClock.elapsedRealtime() + 10_000L);
            else liveFinished.add(key);
            if (result != null) identities.put(key, result);
            if (selection == expectedSelection && selected == media) {
                liveTask = null;
                if (result != null) ready.accept(media.url);
            }
        });
    }

    void clear() {
        generation++;
        select(null);
        identities.clear();
        requested.clear();
        localFinished.clear();
        liveFinished.clear();
        retryAt.clear();
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
        if (liveWorker != null) liveWorker.shutdownNow();
    }
}
