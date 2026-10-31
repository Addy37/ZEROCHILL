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
    private final ShitTokAdmissionHealth health;
    private volatile boolean creatorScheduling;
    void setCreatorFocused(boolean value) { creatorScheduling = value; }
    private final LinkedHashMap<String, Entry> cache = new LinkedHashMap<String, Entry>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, ShitTokOrientationAdmission.Entry> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    ShitTokOrientationAdmission(Context context) {
        this(new ShitTokOrientationProbe(context), () -> System.nanoTime() / 1_000_000L,
                (phase, item, detail) -> AppPerformance.startupItem(phase, item.url, detail));
        ShitTokPoolDiagnostics.resetAdmissionOutcomes();
    }
    ShitTokOrientationAdmission(Probe probe, LongSupplier clock) {
        this(probe, clock, null);
    }
    ShitTokOrientationAdmission(Probe probe, LongSupplier clock, Observer observer) {
        this.probe = probe;
        this.clock = clock;
        this.health = new ShitTokAdmissionHealth(clock);
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
        return admit(candidates, resolver, allowedWork, random, maxItems, requireStreams, onVerified,
                ShitTokPoolDiagnostics.modeToken());
    }
    private Result admit(List<NativeContentItem> candidates, Resolver resolver, BooleanSupplier allowedWork,
                 Random random, int maxItems, boolean requireStreams, Consumer<Result> onVerified, long diagnosticToken) {
        final long workGeneration = admissionGeneration;
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
                CrazyShitRepository.StreamInfo reusable = known != null
                        && Float.compare(known.aspect, item.aspectRatioHint) == 0 ? known.stream : null;
                Entry hint = new Entry(item.aspectRatioHint, reusable, 0L, "metadata");
                synchronized (cache) { cache.put(item.url, hint); }
                if (!ShitTokOrientation.eligible(hint.aspect)) {
                    orientationRejected.incrementAndGet();
                    ShitTokPoolDiagnostics.admissionOutcome(item, "orientation", diagnosticToken);
                    continue;
                }
                if (!requireStreams || reusable != null) {
                    accept(item, hint, admitted, streams, onVerified, diagnosticToken);
                    continue;
                }
                known = hint;
            }
            if (known != null && (known.aspect > 0f || known.retryAt > clock.getAsLong())
                    && (!requireStreams || known.stream != null || !ShitTokOrientation.eligible(known.aspect))) {
                if (ShitTokOrientation.eligible(known.aspect)) accept(item, known, admitted, streams, onVerified, diagnosticToken);
                else if (known.stream == null || known.stream.mediaUrl == null || known.stream.mediaUrl.isEmpty()) {
                    unplayableRejected.incrementAndGet();
                    ShitTokPoolDiagnostics.admissionOutcome(item, "unplayable", diagnosticToken);
                } else {
                    orientationRejected.incrementAndGet();
                    ShitTokPoolDiagnostics.admissionOutcome(item, "orientation", diagnosticToken);
                }
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
            long candidateStarted = clock.getAsLong();
            CrazyShitRepository.StreamInfo stream = known != null && known.provenance.equals("pending") ? known.stream : null;
            float aspect = known != null && ShitTokOrientation.eligible(known.aspect) ? known.aspect : 0f;
            boolean terminalError = false;
            try {
                event("resolve_start", item, "source=" + ShitTokAspectPriority.sourceKey(item));
                if (stream == null) stream = resolver.resolve(item);
                event("resolve_end", item, "playable=" + (stream != null));
                if (!allowedWork.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new Deferred();
                if (stream != null && stream.mediaUrl != null && !stream.mediaUrl.isEmpty()) {
                    if (!(aspect > 0f)) {
                        Float prepared = ChaosStartupPreloader.preparedAspect(item.url, stream);
                        if (prepared != null) {
                            aspect = prepared;
                            event("early_probe_reused", item, "aspect=" + aspect);
                        } else {
                            event("probe_start", item, "");
                            aspect = probe.inspect(stream, allowedWork);
                            event("probe_end", item, "aspect=" + aspect);
                        }
                    }
                } else aspect = 0f;
            } catch (Exception failure) {
                if (failure instanceof Filtered) {
                    ShitTokPoolDiagnostics.admissionOutcome(item, "filtered", diagnosticToken);
                    continue;
                }
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
                terminalError = true;
                aspect = 0f;
            }
            // Keep an already completed probe when demand/permission closes. Only an actual
            // lifecycle/selection cancellation invalidates its result and source health.
            if (workGeneration != admissionGeneration || Thread.currentThread().isInterrupted()) {
                deferred = true; remaining.add(item); continue;
            }
            if (!(aspect > 0f) || !Float.isFinite(aspect)) aspect = 0f;
            String provenance = stream != null && stream.mediaUrl != null
                    && (stream.mediaUrl.contains(".m3u8") || stream.mediaUrl.contains(".mpd"))
                    ? "manifest" : "extractor";
            Entry result = new Entry(aspect, stream,
                    aspect > 0f ? 0L : clock.getAsLong() + UNKNOWN_COOLDOWN_MS, provenance);
            synchronized (cache) { cache.put(item.url, result); }
            if (!ShitTokOrientation.eligible(aspect)) {
                boolean missingStream = stream == null || stream.mediaUrl == null || stream.mediaUrl.isEmpty();
                if (missingStream) unplayableRejected.incrementAndGet();
                else orientationRejected.incrementAndGet();
                if (terminalError) {
                    ShitTokPoolDiagnostics.admissionOutcome(item, missingStream ? "resolve_error" : "probe_error", diagnosticToken);
                } else {
                    ShitTokPoolDiagnostics.admissionOutcome(item, missingStream ? "unplayable" : "orientation", diagnosticToken);
                }
            }
            if (creatorScheduling) health.result(ShitTokAspectPriority.sourceKey(item), ShitTokOrientation.eligible(aspect),
                    terminalError, clock.getAsLong() - candidateStarted);
            event(ShitTokOrientation.eligible(aspect) ? "verified" : "rejected", item, "aspect=" + aspect);
            if (ShitTokOrientation.eligible(aspect)) accept(item, result, admitted, streams, onVerified, diagnosticToken);
        }
        return new Result(admitted, streams, remaining, deferred);
    }

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
        return admitConcurrent(candidates, productionDone, resolver, allowedWork, () -> true,
                maxItems, onVerified, activeCount);
    }

    // Demand governs NEW work only. Playback/lifecycle permission still governs in-flight work;
    // a full runway must not discard a useful slow result or restart its resolver later.
    Result admitConcurrent(java.util.concurrent.BlockingQueue<NativeContentItem> candidates,
            BooleanSupplier productionDone, Resolver resolver, BooleanSupplier allowedWork,
            BooleanSupplier demand, java.util.function.IntSupplier maxItems,
            Consumer<Result> onVerified, java.util.function.IntConsumer activeCount) {
        ArrayList<NativeContentItem> admitted = new ArrayList<>(), remaining = new ArrayList<>();
        Map<String, CrazyShitRepository.StreamInfo> streams = new LinkedHashMap<>();
        long started = clock.getAsLong();
        int attempts = 0;
        try {
            while (allowedWork.getAsBoolean() && !Thread.currentThread().isInterrupted()
                    && admitted.size() < maxItems.getAsInt() && clock.getAsLong() - started < BATCH_MILLIS) {
                synchronized (pending) {
                    if (!allowedWork.getAsBoolean()) break;
                    if (!demand.getAsBoolean()) { demandPauses.incrementAndGet(); break; }
                    java.util.Iterator<Pending> iterator = pending.values().iterator();
                    while (iterator.hasNext() && admitted.size() < maxItems.getAsInt() && demand.getAsBoolean()) {
                        Pending task = iterator.next();
                        if (!task.future.isDone()) continue;
                        iterator.remove();
                        Result result;
                        try { result = task.future.get(); }
                        catch (java.util.concurrent.ExecutionException | java.util.concurrent.CancellationException failed) { continue; }
                        if (!allowedWork.getAsBoolean()) { remaining.add(task.item); break; }
                        if (result.items.isEmpty() && result.remaining.isEmpty()) rejectedCount.incrementAndGet();
                        admitted.addAll(result.items); streams.putAll(result.streams); remaining.addAll(result.remaining);
                        if (onVerified != null && !result.items.isEmpty()) onVerified.accept(result);
                    }
                    boolean waitingForHealth = false;
                    while (pending.size() < 3 && attempts < 24 && admitted.size() < maxItems.getAsInt()
                            && demand.getAsBoolean()) {
                        ArrayList<NativeContentItem> busy = new ArrayList<>();
                        for (Pending task : pending.values()) busy.add(task.item);
                        NativeContentItem item = nextCandidate(candidates, busy, productionDone.getAsBoolean(), remaining, attempts);
                        if (item == null) { waitingForHealth = true; break; }
                        if (pending.containsKey(item.url)) continue;
                        attempts++;
                        final long token = admissionGeneration;
                        final long diagnosticToken = ShitTokPoolDiagnostics.modeToken();
                        try {
                            java.util.concurrent.Future<Result> future = inventoryIo.submit(() -> {
                                // A queued lane may run after the UI publishes the last needed clip.
                                if (!demand.getAsBoolean()) {
                                    startDeferrals.incrementAndGet();
                                    return new Result(Collections.emptyList(), Collections.emptyMap(),
                                            Collections.singletonList(item), true);
                                }
                                admissionStarts.incrementAndGet();
                                verificationActive.incrementAndGet();
                                try {
                                    return admit(Collections.singletonList(item), resolver,
                                            () -> token == admissionGeneration && allowedWork.getAsBoolean(),
                                            new Random(0), 1, true, null, diagnosticToken);
                                } finally { verificationActive.decrementAndGet(); }
                            });
                            pending.put(item.url, new Pending(item, future));
                        } catch (java.util.concurrent.RejectedExecutionException full) { remaining.add(item); break; }
                    }
                    activeCount.accept(pending.size());
                    if (pending.isEmpty() && ((productionDone.getAsBoolean() && (candidates.isEmpty() || waitingForHealth)) || attempts >= 24)) break;
                }
                Thread.sleep(25L);
            }
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        // Normal runway completion does not cancel slow verification. Its bounded result is
        // collected by a later refill; only cancelPending() ends a lifecycle/category generation.
        return new Result(admitted, streams, remaining,
                !allowedWork.getAsBoolean() || !demand.getAsBoolean());
    }

    private final java.util.concurrent.atomic.AtomicInteger verificationActive = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicLong admissionStarts = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong demandPauses = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong startDeferrals = new java.util.concurrent.atomic.AtomicLong();
    String schedulingSummary() {
        return "admissionStarts=" + admissionStarts.get() + " runwayPauses=" + demandPauses.get()
                + " startDeferrals=" + startDeferrals.get() + " verificationActive=" + verificationActive.get()
                + " " + health.summary();
    }

    private static final class Pending {
        final NativeContentItem item;
        final java.util.concurrent.Future<Result> future;
        Pending(NativeContentItem item, java.util.concurrent.Future<Result> future) { this.item = item; this.future = future; }
    }
    private final Map<String, Pending> pending = new LinkedHashMap<>();
    private volatile long admissionGeneration;
    boolean hasPending() { synchronized (pending) { return !pending.isEmpty(); } }
    boolean canOffer(NativeContentItem item, java.util.Collection<NativeContentItem> candidates) {
        String source = ShitTokAspectPriority.sourceKey(item);
        if (!health.allowed(source)) return false;
        int count = 0, maximum = source.equals("bunkr") ? 2 : 12;
        for (NativeContentItem queued : candidates)
            if (source.equals(ShitTokAspectPriority.sourceKey(queued)) && ++count >= maximum) return false;
        return true;
    }

    List<NativeContentItem> cancelPending() {
        synchronized (pending) {
            ArrayList<NativeContentItem> abandoned = new ArrayList<>();
            for (Pending task : pending.values()) abandoned.add(task.item);
            admissionGeneration++;
            health.reset();
            for (Pending task : pending.values()) task.future.cancel(true);
            pending.clear(); inventoryIo.purge();
            return abandoned;
        }
    }

    private NativeContentItem nextCandidate(java.util.concurrent.BlockingQueue<NativeContentItem> candidates,
            java.util.Collection<NativeContentItem> active, boolean productionDone,
            List<NativeContentItem> remaining, int attempt) {
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (NativeContentItem item : active) counts.merge(ShitTokAspectPriority.sourceKey(item), 1, Integer::sum);
        synchronized (candidates) {
            Set<String> availableSources = new HashSet<>(counts.keySet());
            for (NativeContentItem item : candidates) availableSources.add(ShitTokAspectPriority.sourceKey(item));
            int perSource = productionDone && availableSources.size() == 1 ? 3 : 2;
            ArrayList<NativeContentItem> deferred = new ArrayList<>();
            ArrayList<NativeContentItem> eligible = new ArrayList<>();
            int available = candidates.size();
            for (int i = 0; i < available; i++) {
                NativeContentItem item = candidates.poll();
                if (item == null) break;
                ShitTokSmartRanker.register(item);
                String source = ShitTokAspectPriority.sourceKey(item);
                int limit = creatorScheduling && source.equals("bunkr") ? 1 : perSource;
                if ((!creatorScheduling || health.allowed(source)) && counts.getOrDefault(source, 0) < limit) {
                    eligible.add(item);
                } else {
                    deferred.add(item);
                }
            }

            NativeContentItem selected = null;
            if (!eligible.isEmpty()) {
                boolean fastPortrait = creatorScheduling && eligible.stream().anyMatch(i -> trustedHint(i)
                        && ShitTokOrientation.eligible(i.aspectRatioHint));
                if (fastPortrait) {
                    int index = -1;
                    for (int i = 0; i < eligible.size(); i++) {
                        NativeContentItem item = eligible.get(i);
                        if (trustedHint(item) && ShitTokOrientation.eligible(item.aspectRatioHint)
                                && (index < 0 || opportunity(item) > opportunity(eligible.get(index)))) index = i;
                    }
                    selected = eligible.remove(index);
                } else if (attempt % 4 == 3) {
                    selected = eligible.remove(0);
                } else {
                    int bestIndex = 0;
                    for (int i = 1; i < eligible.size(); i++) {
                        NativeContentItem current = eligible.get(i);
                        NativeContentItem best = eligible.get(bestIndex);
                        if (opportunity(current) > opportunity(best)) bestIndex = i;
                    }
                    selected = eligible.remove(bestIndex);
                }
            }
            if (selected != null) {
                admissionTurns.put(ShitTokAspectPriority.sourceKey(selected), ++admissionTurn);
                while (admissionTurns.size() > 32) admissionTurns.remove(admissionTurns.keySet().iterator().next());
            }
            for (NativeContentItem item : eligible) if (!candidates.offer(item)) remaining.add(item);
            for (NativeContentItem item : deferred) if (!candidates.offer(item)) remaining.add(item);
            return selected;
        }
    }

    private final Map<String, Long> admissionTurns = new LinkedHashMap<>();
    private long admissionTurn;
    private double opportunity(NativeContentItem item) {
        long age = admissionTurn - admissionTurns.getOrDefault(ShitTokAspectPriority.sourceKey(item), 0L);
        return Math.min(8, age) + Math.max(-2f, Math.min(2f, ShitTokSmartRanker.score(item)))
                + (trustedHint(item) ? .5 : 0) - (creatorScheduling ? health.cost(ShitTokAspectPriority.sourceKey(item)) : 0);
    }
    void close() { cancelPending(); inventoryIo.shutdownNow(); }

    String describe(String url) {
        synchronized (cache) {
            Entry entry = cache.get(url);
            if (entry == null) return "unknown";
            return entry.aspect > 0f ? "aspect=" + entry.aspect + " via=" + entry.provenance + " cache=session"
                    : "unknown cooldown=" + (entry.retryAt > clock.getAsLong());
        }
    }

    private static void accept(NativeContentItem item, Entry entry, List<NativeContentItem> items,
                               Map<String, CrazyShitRepository.StreamInfo> streams, Consumer<Result> callback, long diagnosticToken) {
        NativeContentItem copy = ShitTokOrientation.copy(item, entry.aspect);
        items.add(copy);
        ShitTokPoolDiagnostics.admissionOutcome(item, "accepted", diagnosticToken);
        if (entry.stream != null) streams.put(item.url, entry.stream);
        if (callback != null) callback.accept(new Result(Collections.singletonList(copy),
                entry.stream == null ? Collections.emptyMap() : Collections.singletonMap(item.url, entry.stream),
                Collections.emptyList(), false));
    }

    private static boolean trustedHint(NativeContentItem item) {
        if (!(item.aspectRatioHint > 0f) || !Float.isFinite(item.aspectRatioHint)) return false;
        String source = ShitTokAspectPriority.sourceKey(item);
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
            group.sort((a, b) -> Boolean.compare(trustedHint(b), trustedHint(a)));
            queues.add(new ArrayDeque<>(group));
        }
        Collections.shuffle(queues, rng);
        ArrayList<NativeContentItem> result = new ArrayList<>();
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
