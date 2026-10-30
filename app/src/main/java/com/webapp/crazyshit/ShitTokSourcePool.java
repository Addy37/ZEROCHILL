package com.webapp.crazyshit;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** Session-only acquisition. Admission owns no provider futures; lifecycle owns cancellation. */
final class ShitTokSourcePool {
    enum Health { IDLE, FETCHING, SLOW, READY, EMPTY, FAILED, UNAVAILABLE, FILTERED, STARVED }
    private static final int PER_SOURCE = 12;
    private final Map<Integer, Slot> slots = new LinkedHashMap<>();
    private final ExecutorService executor;
    private final LongSupplier clock;
    private long generation, turn;
    private static final class Slot {
        final ArrayList<NativeContentItem> ready = new ArrayList<>();
        Future<?> work;
        Health health = Health.IDLE;
        long retryAt, started, delivered;
        int failures;
        boolean running, entered, eligible = true;
    }
    ShitTokSourcePool(ExecutorService executor, LongSupplier clock) {
        this.executor = executor; this.clock = clock;
    }
    synchronized void request(int source, boolean eligible, boolean available,
            Callable<List<NativeContentItem>> loader) {
        request(source, eligible, available, loader, () -> {});
    }
    synchronized void request(int source, boolean eligible, boolean available,
            Callable<List<NativeContentItem>> loader, Runnable availableCallback) {
        Slot slot = slots.computeIfAbsent(source, ignored -> new Slot());
        slot.eligible = eligible && available;
        if (!slot.eligible) {
            slot.health = eligible ? Health.UNAVAILABLE : Health.FILTERED;
            slot.ready.clear();
            return;
        }
        if (slot.running || !slot.ready.isEmpty() || clock.getAsLong() < slot.retryAt) return;
        slot.running = true; slot.entered = false; slot.started = clock.getAsLong(); slot.health = Health.FETCHING;
        long token = generation;
        FutureTask<Void> task = new FutureTask<Void>(() -> {
            synchronized (ShitTokSourcePool.this) {
                if (token != generation || Thread.currentThread().isInterrupted()) return null;
                slot.entered = true;
            }
            List<NativeContentItem> loaded = Collections.emptyList();
            boolean failed = false;
            try { loaded = loader.call(); }
            catch (Exception error) { failed = true; }
            synchronized (ShitTokSourcePool.this) {
                slot.running = false;
                slot.work = null;
                if (token != generation || !slot.eligible || Thread.currentThread().isInterrupted()) return null;
                Set<String> urls = new HashSet<>();
                if (loaded != null) for (NativeContentItem item : loaded) {
                    if (item != null && item.isVideo() && urls.add(item.url) && slot.ready.size() < PER_SOURCE)
                        slot.ready.add(item);
                }
                if (!slot.ready.isEmpty()) {
                    slot.health = Health.READY; slot.failures = 0; slot.retryAt = 0;
                } else {
                    slot.health = failed ? Health.FAILED : Health.EMPTY;
                    slot.failures = Math.min(5, slot.failures + 1);
                    slot.retryAt = clock.getAsLong() + Math.min(120_000L,
                            (failed ? 5_000L : 15_000L) * (1L << (slot.failures - 1)));
                }
                ShitTokSourcePool.this.notifyAll();
            }
            availableCallback.run();
            return null;
        }) {
            @Override protected void done() {
                synchronized (ShitTokSourcePool.this) {
                    if (!slot.entered) { slot.running = false; slot.work = null; }
                }
            }
        };
        slot.work = task;
        try { executor.execute(task); }
        catch (RejectedExecutionException full) {
            slot.running = false; slot.work = null; slot.health = Health.IDLE;
            slot.retryAt = clock.getAsLong() + 2_000L;
        }
    }
    /** Bounded score plus age gives ready sources opportunity without permanent percentages. */
    synchronized List<NativeContentItem> take(int maximum) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        while (result.size() < maximum) {
            Slot best = null; double score = -Double.MAX_VALUE;
            for (Slot slot : slots.values()) {
                if (slot.ready.isEmpty()) continue;
                double value = Math.min(8, turn - slot.delivered)
                        + Math.max(-2, Math.min(2, ShitTokSmartRanker.score(slot.ready.get(0))));
                if (best == null || value > score) { best = slot; score = value; }
            }
            if (best == null) break;
            int index = 0;
            if (turn % 4 != 3) for (int i = 1; i < best.ready.size(); i++)
                if (ShitTokSmartRanker.score(best.ready.get(i)) > ShitTokSmartRanker.score(best.ready.get(index))) index = i;
            result.add(best.ready.remove(index));
            best.delivered = ++turn;
            best.health = best.ready.isEmpty() ? Health.IDLE : Health.READY;
        }
        return result;
    }
    synchronized boolean hasReady(int source) {
        Slot slot = slots.get(source);
        return slot != null && !slot.ready.isEmpty();
    }
    synchronized void filterExcept(int allowed) {
        for (int source = 1; source <= 8; source++) if (source != allowed) {
            Slot slot = slots.computeIfAbsent(source, ignored -> new Slot());
            slot.eligible = false; slot.ready.clear(); slot.health = Health.FILTERED;
        }
    }
    synchronized Health health(int source) {
        Slot slot = slots.get(source);
        if (slot == null) return Health.IDLE;
        if (slot.running && clock.getAsLong() - slot.started > 5_500L) return Health.SLOW;
        if (!slot.ready.isEmpty() && turn - slot.delivered >= 8) return Health.STARVED;
        return slot.health;
    }
    synchronized String summary() {
        Map<Integer, String> result = new LinkedHashMap<>();
        for (Integer source : slots.keySet()) result.put(source, health(source) + ":" + slots.get(source).ready.size());
        return result.toString();
    }
    synchronized void stop() {
        generation++;
        for (Slot slot : slots.values()) {
            if (slot.work != null) slot.work.cancel(true);
            slot.ready.clear();
            // Keep running=true until the socket really exits; no duplicate fetch storm on resume.
            if (!slot.running) slot.health = Health.IDLE;
        }
        if (executor instanceof ThreadPoolExecutor) ((ThreadPoolExecutor) executor).purge();
    }
}
