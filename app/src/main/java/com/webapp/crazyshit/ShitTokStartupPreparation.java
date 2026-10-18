package com.webapp.crazyshit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;

/** Bounded, process-start metadata race. Stops speculative work at the first verified portrait. */
final class ShitTokStartupPreparation {
    static final long BUDGET_MS = 6_000L;
    static final class Prepared {
        final CrazyShitRepository.StreamInfo stream;
        final Float aspect;
        final boolean attempted;
        Prepared(CrazyShitRepository.StreamInfo stream, Float aspect, boolean attempted) {
            this.stream = stream; this.aspect = aspect; this.attempted = attempted;
        }
    }

    private final Executor worker;
    private final ShitTokOrientationAdmission.Resolver resolver;
    private final ShitTokOrientationAdmission.Probe probe;
    private final LongSupplier clock;
    private final long deadline;
    private final Map<String, FutureTask<Prepared>> tasks = new LinkedHashMap<>();
    private final java.util.Set<String> handedOff = new java.util.HashSet<>();
    private volatile boolean portraitFound, closed;

    ShitTokStartupPreparation(ShitTokOrientationAdmission.Resolver resolver,
            ShitTokOrientationAdmission.Probe probe) {
        this(new ThreadPoolExecutor(3, 3, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(ChaosStartupPreloader.STARTER_ITEMS), task -> {
                    Thread thread = new Thread(task, "ShitTokStartupMetadata");
                    thread.setDaemon(true);
                    return thread;
                }), resolver, probe, () -> System.nanoTime() / 1_000_000L);
    }

    ShitTokStartupPreparation(Executor worker, ShitTokOrientationAdmission.Resolver resolver,
            ShitTokOrientationAdmission.Probe probe, LongSupplier clock) {
        this.worker = worker; this.resolver = resolver; this.probe = probe; this.clock = clock;
        deadline = clock.getAsLong() + BUDGET_MS;
    }

    void offer(NativeContentItem item) {
        FutureTask<Prepared> task;
        synchronized (tasks) {
            if (closed || tasks.containsKey(item.url) || tasks.size() >= ChaosStartupPreloader.STARTER_ITEMS) return;
            task = new FutureTask<>(() -> prepare(item));
            tasks.put(item.url, task);
        }
        try { worker.execute(task); }
        catch (java.util.concurrent.RejectedExecutionException stopped) {
            synchronized (tasks) { tasks.remove(item.url); }
            task.cancel(false);
        }
    }

    private boolean allowed() { return !closed && !portraitFound && clock.getAsLong() < deadline; }

    private Prepared prepare(NativeContentItem item) {
        if (!allowed()) return new Prepared(null, null, false);
        CrazyShitRepository.StreamInfo stream = null;
        Float aspect = null;
        AppPerformance.startupItem("early_resolve_start", item.url, "");
        try {
            stream = resolver.resolve(item);
            AppPerformance.startupItem("early_resolve_end", item.url, "playable=" + (stream != null));
            if (stream != null && stream.mediaUrl != null && !stream.mediaUrl.isEmpty() && allowed()) {
                AppPerformance.startupItem("early_probe_start", item.url, "");
                aspect = probe.inspect(stream, this::allowed);
                AppPerformance.startupItem("early_probe_end", item.url, "aspect=" + aspect);
                if (ShitTokOrientation.eligible(aspect)) portraitFound = true;
            }
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            // A stopped/contended probe remains unknown. The feed may inspect its cached URL
            // later under the playback owner's existing speculation permission.
            AppPerformance.startupItem("early_deferred", item.url, "type=" + failure.getClass().getSimpleName());
        }
        return new Prepared(stream, aspect, true);
    }

    Prepared await(String pageUrl) throws Exception {
        FutureTask<Prepared> task;
        synchronized (tasks) { task = tasks.get(pageUrl); }
        if (task == null) return null;
        try { return task.get(BUDGET_MS, TimeUnit.MILLISECONDS); }
        catch (TimeoutException busy) { throw new ShitTokOrientationAdmission.Deferred(); }
    }

    Prepared take(String pageUrl) throws Exception {
        synchronized (tasks) { if (handedOff.contains(pageUrl)) return null; }
        Prepared result = await(pageUrl);
        synchronized (tasks) { return result != null && handedOff.add(pageUrl) ? result : null; }
    }

    void streamHandedOff(String pageUrl) { synchronized (tasks) { handedOff.add(pageUrl); } }

    Float aspect(String pageUrl, CrazyShitRepository.StreamInfo stream) throws Exception {
        Prepared prepared = await(pageUrl);
        return prepared != null && prepared.stream != null && stream != null
                && prepared.stream.mediaUrl.equals(stream.mediaUrl) ? prepared.aspect : null;
    }

    boolean portraitReady(String pageUrl) {
        FutureTask<Prepared> task;
        synchronized (tasks) { task = tasks.get(pageUrl); }
        if (task == null || !task.isDone() || task.isCancelled()) return false;
        try {
            Float aspect = task.get().aspect;
            return aspect != null && ShitTokOrientation.eligible(aspect);
        } catch (Exception failed) { return false; }
    }

    void close() {
        closed = true;
        synchronized (tasks) { for (FutureTask<Prepared> task : tasks.values()) task.cancel(true); }
        if (worker instanceof ExecutorService) ((ExecutorService) worker).shutdownNow();
    }

    void finishOffers() {
        if (worker instanceof ExecutorService) ((ExecutorService) worker).shutdown();
    }
}
