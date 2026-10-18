package com.webapp.crazyshit;

import android.content.Context;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Session admission gate. Call from a background feed worker, never the UI/playback looper. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class ShitTokOrientationAdmission {
    static final int CACHE_LIMIT = 512;
    static final int MAX_PROBES_PER_BATCH = 8;
    static final long UNKNOWN_COOLDOWN_MS = 5 * 60_000L;
    static final long BATCH_MILLIS = 6000L;

    interface Resolver { CrazyShitRepository.StreamInfo resolve(NativeContentItem item) throws Exception; }
    interface Probe {
        float inspect(CrazyShitRepository.StreamInfo stream, BooleanSupplier allowedWork) throws Exception;
    }
    static final class Deferred extends Exception {}
    static final class Filtered extends Exception {}
    static final class Result {
        final List<NativeContentItem> items;
        final Map<String, CrazyShitRepository.StreamInfo> streams;
        final List<NativeContentItem> remaining;
        final boolean deferred;
        Result(List<NativeContentItem> items, Map<String, CrazyShitRepository.StreamInfo> streams,
               List<NativeContentItem> remaining, boolean deferred) {
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
            this.streams = Collections.unmodifiableMap(new LinkedHashMap<>(streams));
            this.remaining = Collections.unmodifiableList(new ArrayList<>(remaining));
            this.deferred = deferred;
        }
    }

    interface Observer { void event(String phase, NativeContentItem item, String detail); }
    private final Observer observer;
    private void event(String phase, NativeContentItem item, String detail) {
        if (observer != null) observer.event(phase, item, detail);
    }
    private final Probe probe;
    private final LongSupplier clock;
    private final LinkedHashMap<String, Entry> cache = new LinkedHashMap<String, Entry>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, ShitTokOrientationAdmission.Entry> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    ShitTokOrientationAdmission(Context context) {
        this(new ShitTokOrientationProbe(context), () -> System.nanoTime() / 1_000_000L,
                (phase, item, detail) -> AppPerformance.startupItem(phase, item.url, detail));
    }
    ShitTokOrientationAdmission(Probe probe, LongSupplier clock) {
        this(probe, clock, null);
    }
    ShitTokOrientationAdmission(Probe probe, LongSupplier clock, Observer observer) {
        this.probe = probe;
        this.clock = clock;
        this.observer = observer;
    }

    Result admit(List<NativeContentItem> candidates, Resolver resolver,
                 BooleanSupplier allowedWork, Random random) {
        return admit(candidates, resolver, allowedWork, random, null);
    }

    Result admit(List<NativeContentItem> candidates, Resolver resolver, BooleanSupplier allowedWork,
                 Random random, Consumer<Result> onVerified) {
        return admit(candidates, resolver, allowedWork, random, Integer.MAX_VALUE, false, onVerified);
    }

    Result admit(List<NativeContentItem> candidates, Resolver resolver, BooleanSupplier allowedWork,
                 Random random, int maxItems, boolean requireStreams, Consumer<Result> onVerified) {
        List<NativeContentItem> ordered = candidateOrder(candidates, random);
        ArrayList<NativeContentItem> admitted = new ArrayList<>();
        ArrayList<NativeContentItem> remaining = new ArrayList<>();
        LinkedHashMap<String, CrazyShitRepository.StreamInfo> streams = new LinkedHashMap<>();
        long started = clock.getAsLong();
        int probes = 0;
        boolean deferred = false;
        for (NativeContentItem item : ordered) {
            if (admitted.size() >= maxItems) { remaining.add(item); continue; }
            Entry known;
            synchronized (cache) { known = cache.get(item.url); }
            if (trustedHint(item)) {
                // A current trusted landscape hint must override an earlier portrait cache entry.
                CrazyShitRepository.StreamInfo reusable = known != null
                        && Float.compare(known.aspect, item.aspectRatioHint) == 0 ? known.stream : null;
                Entry hint = new Entry(item.aspectRatioHint, reusable, 0L, "metadata");
                synchronized (cache) { cache.put(item.url, hint); }
                if (!ShitTokOrientation.eligible(hint.aspect)) { orientationRejected.incrementAndGet(); continue; }
                if (!requireStreams || reusable != null) {
                    accept(item, hint, admitted, streams, onVerified);
                    continue;
                }
                // Trusted dimensions avoid the probe, but continuity still needs a playable URL.
                known = hint;
            }
            if (known != null && (known.aspect > 0f || known.retryAt > clock.getAsLong())
                    && (!requireStreams || known.stream != null || !ShitTokOrientation.eligible(known.aspect))) {
                if (ShitTokOrientation.eligible(known.aspect)) accept(item, known, admitted, streams, onVerified);
                else if (known.stream == null || known.stream.mediaUrl == null || known.stream.mediaUrl.isEmpty()) unplayableRejected.incrementAndGet();
                else orientationRejected.incrementAndGet();
                continue;
            }
            if (!allowedWork.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                deferred = true;
                remaining.add(item);
                continue;
            }
            if (probes >= MAX_PROBES_PER_BATCH || clock.getAsLong() - started >= BATCH_MILLIS) {
                remaining.add(item);
                continue;
            }
            probes++;
            CrazyShitRepository.StreamInfo stream = known != null && known.provenance.equals("pending") ? known.stream : null;
            float aspect = known != null && ShitTokOrientation.eligible(known.aspect) ? known.aspect : 0f;
            try {
                event("resolve_start", item, "source=" + ShitTokAspectPriority.sourceKey(item));
                if (stream == null) stream = resolver.resolve(item);
                event("resolve_end", item, "playable=" + (stream != null));
                if (!allowedWork.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new Deferred();
                if (stream != null && stream.mediaUrl != null && !stream.mediaUrl.isEmpty()) {
                    if (!(aspect > 0f)) {
                        event("probe_start", item, "");
                        aspect = probe.inspect(stream, allowedWork);
                        event("probe_end", item, "aspect=" + aspect);
                    }
                } else aspect = 0f;
            } catch (Exception failure) {
                if (failure instanceof Filtered) continue;
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                event("candidate_error", item, "type=" + failure.getClass().getSimpleName());
                if (!allowedWork.getAsBoolean() || Thread.currentThread().isInterrupted() || deferred(failure)) {
                    deferred = true;
                    if (stream != null) synchronized (cache) {
                        cache.put(item.url, new Entry(0f, stream, 0L, "pending"));
                    }
                    remaining.add(item);
                    continue;
                }
                // A failed item cannot prevent other sources from filling the feed.
                aspect = 0f;
            }
            if (!(aspect > 0f) || !Float.isFinite(aspect)) aspect = 0f;
            String provenance = stream != null && stream.mediaUrl != null
                    && (stream.mediaUrl.contains(".m3u8") || stream.mediaUrl.contains(".mpd"))
                    ? "manifest" : "extractor";
            Entry result = new Entry(aspect, stream,
                    aspect > 0f ? 0L : clock.getAsLong() + UNKNOWN_COOLDOWN_MS, provenance);
            synchronized (cache) { cache.put(item.url, result); }
            if (!ShitTokOrientation.eligible(aspect)) {
                if (stream == null || stream.mediaUrl == null || stream.mediaUrl.isEmpty()) unplayableRejected.incrementAndGet();
                else orientationRejected.incrementAndGet();
            }
            event(ShitTokOrientation.eligible(aspect) ? "verified" : "rejected", item, "aspect=" + aspect);
            if (ShitTokOrientation.eligible(aspect)) accept(item, result, admitted, streams, onVerified);
        }
        return new Result(admitted, streams, remaining, deferred);
    }

    // Three metadata/URL lanes only. These workers never allocate an ExoPlayer or media buffer.
    private final java.util.concurrent.ThreadPoolExecutor inventoryIo =
            new java.util.concurrent.ThreadPoolExecutor(3, 3, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
                    new java.util.concurrent.ArrayBlockingQueue<>(3));
    private final java.util.concurrent.atomic.AtomicInteger rejectedCount = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger orientationRejected = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger unplayableRejected = new java.util.concurrent.atomic.AtomicInteger();
    int rejectedCount() { return rejectedCount.get(); }
    int orientationRejectedCount() { return orientationRejected.get(); }
    int unplayableRejectedCount() { return unplayableRejected.get(); }

    Result admitConcurrent(java.util.concurrent.BlockingQueue<NativeContentItem> candidates,
            BooleanSupplier productionDone, Resolver resolver, BooleanSupplier allowedWork,
            int maxItems, Consumer<Result> onVerified, java.util.function.IntConsumer activeCount) {
        return admitConcurrent(candidates, productionDone, resolver, allowedWork, () -> maxItems, onVerified, activeCount);
    }

    Result admitConcurrent(java.util.concurrent.BlockingQueue<NativeContentItem> candidates,
            BooleanSupplier productionDone, Resolver resolver, BooleanSupplier allowedWork,
            java.util.function.IntSupplier maxItems, Consumer<Result> onVerified, java.util.function.IntConsumer activeCount) {
        java.util.concurrent.CompletionService<Result> completed =
                new java.util.concurrent.ExecutorCompletionService<>(inventoryIo);
        Map<java.util.concurrent.Future<Result>, NativeContentItem> active = new LinkedHashMap<>();
        ArrayList<NativeContentItem> admitted = new ArrayList<>(), remaining = new ArrayList<>();
        Map<String, CrazyShitRepository.StreamInfo> streams = new LinkedHashMap<>();
        long started = clock.getAsLong();
        int attempts = 0;
        try {
            while (allowedWork.getAsBoolean() && !Thread.currentThread().isInterrupted()
                    && admitted.size() < maxItems.getAsInt() && clock.getAsLong() - started < BATCH_MILLIS) {
                while (active.size() < 3 && attempts < 24) {
                    NativeContentItem item = candidates.poll();
                    if (item == null) break;
                    attempts++;
                    try {
                        active.put(completed.submit(() -> admit(Collections.singletonList(item), resolver,
                                allowedWork, new Random(0), 1, true, null)), item);
                    } catch (java.util.concurrent.RejectedExecutionException busy) {
                        remaining.add(item);
                        attempts = 24; // Old interrupted IO still owns the three slots. Retry in a later batch.
                        break;
                    }
                }
                activeCount.accept(active.size());
                if (active.isEmpty() && (productionDone.getAsBoolean() && candidates.isEmpty() || attempts >= 24)) break;
                java.util.concurrent.Future<Result> done = completed.poll(25, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (done == null) continue;
                NativeContentItem item = active.remove(done);
                Result result;
                try { result = done.get(); }
                catch (java.util.concurrent.ExecutionException failure) { continue; }
                if (!allowedWork.getAsBoolean()) { remaining.add(item); break; }
                if (result.items.isEmpty() && result.remaining.isEmpty()) rejectedCount.incrementAndGet();
                admitted.addAll(result.items);
                streams.putAll(result.streams);
                remaining.addAll(result.remaining);
                if (onVerified != null && !result.items.isEmpty()) onVerified.accept(result);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            for (Map.Entry<java.util.concurrent.Future<Result>, NativeContentItem> task : active.entrySet()) {
                task.getKey().cancel(true);
                remaining.add(task.getValue());
            }
            inventoryIo.purge();
            activeCount.accept(0);
        }
        return new Result(admitted, streams, remaining, !allowedWork.getAsBoolean());
    }

    void close() { inventoryIo.shutdownNow(); }

    String describe(String url) {
        synchronized (cache) {
            Entry entry = cache.get(url);
            if (entry == null) return "unknown";
            return entry.aspect > 0f ? "aspect=" + entry.aspect + " via=" + entry.provenance + " cache=session"
                    : "unknown cooldown=" + (entry.retryAt > clock.getAsLong());
        }
    }

    private static void accept(NativeContentItem item, Entry entry, List<NativeContentItem> items,
                               Map<String, CrazyShitRepository.StreamInfo> streams, Consumer<Result> callback) {
        NativeContentItem copy = ShitTokOrientation.copy(item, entry.aspect);
        items.add(copy);
        if (entry.stream != null) streams.put(item.url, entry.stream);
        if (callback != null) callback.accept(new Result(Collections.singletonList(copy),
                entry.stream == null ? Collections.emptyMap() : Collections.singletonMap(item.url, entry.stream),
                Collections.emptyList(), false));
    }

    private static boolean trustedHint(NativeContentItem item) {
        if (!(item.aspectRatioHint > 0f) || !Float.isFinite(item.aspectRatioHint)) return false;
        String source = ShitTokAspectPriority.sourceKey(item);
        // Shit Show uses HTMLVideoElement.videoWidth/videoHeight, BaddieHub uses video metadata.
        return source.equals("shit-show") || source.equals("baddiehub");
    }

    private static boolean deferred(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof Deferred || cause instanceof ShitTokOrientationProbe.DeferredIOException) return true;
        }
        return false;
    }

    private static List<NativeContentItem> candidateOrder(List<NativeContentItem> candidates, Random random) {
        LinkedHashMap<String, ArrayList<NativeContentItem>> groups = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        if (candidates != null) for (NativeContentItem item : candidates) {
            if (item == null || !item.isVideo() || item.url.isEmpty() || !seen.add(item.url)) continue;
            groups.computeIfAbsent(ShitTokAspectPriority.sourceKey(item), ignored -> new ArrayList<>()).add(item);
        }
        Random rng = random == null ? new Random() : random;
        ArrayList<ArrayDeque<NativeContentItem>> queues = new ArrayList<>();
        for (ArrayList<NativeContentItem> group : groups.values()) {
            Collections.shuffle(group, rng);
            // Stable sort keeps every trustworthy dimension hint ahead of network work.
            group.sort((a, b) -> Boolean.compare(trustedHint(b), trustedHint(a)));
            queues.add(new ArrayDeque<>(group));
        }
        Collections.shuffle(queues, rng);
        ArrayList<NativeContentItem> result = new ArrayList<>();
        // Publish all trusted hints first, without making them wait on another source's resolver.
        boolean haveHints;
        do {
            haveHints = false;
            for (ArrayDeque<NativeContentItem> queue : queues) {
                if (!queue.isEmpty() && trustedHint(queue.peekFirst())) {
                    result.add(queue.removeFirst());
                    haveHints = true;
                }
            }
        } while (haveHints);
        while (!queues.isEmpty()) {
            for (ArrayDeque<NativeContentItem> queue : queues)
                if (!queue.isEmpty()) result.add(queue.removeFirst());
            queues.removeIf(ArrayDeque::isEmpty);
        }
        return result;
    }

    private static final class Entry {
        final float aspect;
        final CrazyShitRepository.StreamInfo stream;
        final long retryAt;
        final String provenance;
        Entry(float aspect, CrazyShitRepository.StreamInfo stream, long retryAt, String provenance) {
            this.aspect = aspect; this.stream = stream; this.retryAt = retryAt; this.provenance = provenance;
        }
    }
}
