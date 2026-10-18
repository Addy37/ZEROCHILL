package com.webapp.crazyshit;

import android.content.Context;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** One process-scoped metadata pipeline shared by splash and the first feed harvest. No player. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class ChaosStartupPreloader {
    static final int STARTER_ITEMS = 6;
    private static final Object LOCK = new Object();
    private static final ArrayList<NativeContentItem> READY = new ArrayList<>();
    private static final HashMap<String, CrazyShitRepository.StreamInfo> RESOLVED = new HashMap<>();
    private static final Set<String> published = new HashSet<>();
    private static boolean started, finished, claimed;
    private static long deadline;
    private static String resolvingUrl;
    private static FutureTask<CrazyShitRepository.StreamInfo> resolution;
    private static ShitTokStartupPreparation preparation;

    private ChaosStartupPreloader() { }

    static void start(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        if (app == null) return; // Never retain a splash Activity in process-scoped work.
        synchronized (LOCK) {
            if (started) return;
            started = true;
            preparation = new ShitTokStartupPreparation(item -> resolveListing(app, item),
                    new ShitTokOrientationProbe(app));
            deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ChaosStarterSources.STARTUP_MILLIS);
        }
        AppPerformance.startup("preload");
        Thread worker = new Thread(() -> load(app), "ChaosStartupPreload");
        worker.setDaemon(true);
        worker.start();
    }

    /** Background-only. Transfer partial results as they arrive, including an in-flight splash race. */
    static List<NativeContentItem> collect(Context context, Consumer<List<NativeContentItem>> onCandidates)
            throws InterruptedException {
        start(context);
        AppPerformance.startup("handoff_collect");
        synchronized (LOCK) {
            if (claimed) return new ArrayList<>();
            claimed = true;
        }
        ArrayList<NativeContentItem> result = new ArrayList<>();
        try {
            for (;;) {
                List<NativeContentItem> next;
                boolean done;
                synchronized (LOCK) {
                    while (READY.isEmpty() && !finished && started) {
                        long left = deadline - System.nanoTime();
                        if (left <= 0) break;
                        TimeUnit.NANOSECONDS.timedWait(LOCK, left);
                    }
                    next = new ArrayList<>(READY);
                    READY.clear();
                    done = finished || !started || System.nanoTime() >= deadline;
                }
                ShitTokStartupPreparation early;
                synchronized (LOCK) { early = preparation; }
                if (early != null) {
                    Set<String> portraits = new HashSet<>();
                    for (NativeContentItem item : next) if (early.portraitReady(item.url)) portraits.add(item.url);
                    next.sort((a, b) -> Boolean.compare(portraits.contains(b.url), portraits.contains(a.url)));
                }
                for (NativeContentItem item : next) AppPerformance.startupItem("handoff_consumed", item.url, "");
                result.addAll(next);
                if (onCandidates != null && !next.isEmpty()) onCandidates.accept(next);
                if (done) return result;
            }
        } finally {
            synchronized (LOCK) { claimed = false; }
        }
    }

    static CrazyShitRepository.StreamInfo takeResolved(String pageUrl) {
        synchronized (LOCK) {
            CrazyShitRepository.StreamInfo stream = RESOLVED.remove(pageUrl);
            if (stream != null && preparation != null) preparation.streamHandedOff(pageUrl);
            return stream;
        }
    }

    /** Join the splash resolver instead of fetching the same page twice. Other candidates proceed. */
    static CrazyShitRepository.StreamInfo resolveForFeed(Context context, NativeContentItem item)
            throws Exception {
        ShitTokStartupPreparation early;
        synchronized (LOCK) { early = preparation; }
        ShitTokStartupPreparation.Prepared prepared = early == null ? null : early.take(item.url);
        if (prepared != null && prepared.attempted) return prepared.stream;
        return resolveListing(context, item);
    }

    static Float preparedAspect(String pageUrl, CrazyShitRepository.StreamInfo stream) throws Exception {
        ShitTokStartupPreparation early;
        synchronized (LOCK) { early = preparation; }
        return early == null ? null : early.aspect(pageUrl, stream);
    }

    private static CrazyShitRepository.StreamInfo resolveListing(Context context, NativeContentItem item)
            throws Exception {
        FutureTask<CrazyShitRepository.StreamInfo> pending;
        synchronized (LOCK) { pending = item.url.equals(resolvingUrl) ? resolution : null; }
        if (pending != null) {
            AppPerformance.startupItem("join_preload", item.url, "done=" + pending.isDone());
            return pending.get(ChaosStarterSources.STARTUP_MILLIS, TimeUnit.MILLISECONDS);
        }
        return PlayableSourceRouter.resolve(context, item);
    }

    private static void publish(Context app, List<NativeContentItem> items) {
        // One early candidate per source leaves all three admission lanes useful to the race.
        publish(app, items, 1);
    }

    private static void publish(Context app, List<NativeContentItem> items, int limit) {
        ShitTokSeenStore exclusions = ShitTokSeenStore.get(app);
        FutureTask<CrazyShitRepository.StreamInfo> firstTask = null;
        synchronized (LOCK) {
            int added = 0;
            for (NativeContentItem item : items) {
                // Reserve fallback room for each of the three racing sources.
                if (published.size() >= STARTER_ITEMS || added >= limit) break;
                // Choose the first fresh candidate, rather than wasting a provider's fair early
                // slot on a clip the feed will discard. This runs on the startup worker.
                if (exclusions.seen(item.url, null) || exclusions.liked(item.url, null)) continue;
                if (!published.add(item.url)) continue;
                if (resolution == null) {
                    resolvingUrl = item.url;
                    resolution = new FutureTask<>(() -> {
                        AppPerformance.startup("resolve_begin");
                        AppPerformance.startupItem("preload_resolve_start", item.url, "");
                        CrazyShitRepository.StreamInfo stream = PlayableSourceRouter.resolve(app, item);
                        AppPerformance.startupItem("preload_resolve_end", item.url, "playable=" + (stream != null));
                        if (stream != null) {
                            AppPerformance.startup("resolved");
                            synchronized (LOCK) { RESOLVED.put(item.url, stream); }
                        }
                        return stream;
                    });
                    firstTask = resolution;
                }
                AppPerformance.startupItem("handoff_published", item.url, "source=" + ShitTokAspectPriority.sourceKey(item));
                READY.add(item);
                if (preparation != null) preparation.offer(item);
                added++;
            }
            LOCK.notifyAll();
        }
        if (firstTask != null) {
            Thread resolver = new Thread(firstTask, "ChaosStartupResolve");
            resolver.setDaemon(true);
            resolver.start();
        }
    }

    private static void load(Context app) {
        try {
            Random random = new Random();
            List<NativeContentItem> rest = ChaosStarterSources.first(
                    ChaosStarterSources.live(app, new CrazyShitRepository(), random),
                    random, ChaosStarterSources.STARTUP_MILLIS, batch -> publish(app, batch));
            // If other providers failed, retain the winning source's remaining cheap fallbacks.
            publish(app, rest, STARTER_ITEMS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            ShitTokStartupPreparation early;
            synchronized (LOCK) { early = preparation; }
            if (early != null) early.finishOffers();
            synchronized (LOCK) { finished = true; LOCK.notifyAll(); }
        }
    }
}
