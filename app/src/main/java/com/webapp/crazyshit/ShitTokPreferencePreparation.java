package com.webapp.crazyshit;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** One unopened inventory may borrow an acquisition lane while selected playback has runway. */
final class ShitTokPreferencePreparation {
    enum Outcome { READY, MISS, DEFERRED }
    interface Prepare { Outcome run(Job job) throws Exception; }

    final class Job {
        final String key;
        private final String origin;
        private final BooleanSupplier networkAllowed;
        private volatile boolean foreground;
        private final java.util.concurrent.atomic.AtomicBoolean reservationReused =
                new java.util.concurrent.atomic.AtomicBoolean();
        private volatile boolean cancelled;

        Job(String key, String origin, BooleanSupplier networkAllowed) {
            this.key = key;
            this.origin = origin;
            this.networkAllowed = networkAllowed;
        }

        boolean allowed() {
            return !cancelled && (foreground || healthy && networkAllowed.getAsBoolean());
        }
    }

    private final LongSupplier clock;

    ShitTokPreferencePreparation(LongSupplier clock) {
        this.clock = clock;
    }

    private final Set<String> opened = new HashSet<>(), attempted = new HashSet<>();
    private volatile Job running;
    private volatile boolean healthy;
    private String selected;
    private volatile int ready, misses, promoted, bufferingWhilePreparing;

    synchronized void select(String key) {
        opened.add(key);
        selected = key;
        Job job = running;
        if (job == null || job.cancelled) return;
        if (job.key.equals(key)) {
            if (!job.foreground) {
                job.foreground = true;
                promoted++;
                ShitTokPoolDiagnostics.recordAt(clock.getAsLong(), "preferencePreparationReused", key);
            }
        } else if (job.foreground || !key.equals(job.origin)) {
            job.cancelled = true;
        }
    }

    void playbackHealthy(boolean value) { healthy = value; }

    synchronized void buffering() {
        healthy = false;
        if (running != null && !running.foreground && !running.cancelled) {
            bufferingWhilePreparing++;
            ShitTokPoolDiagnostics.recordAt(clock.getAsLong(), "preferencePreparationBuffering", running.key);
        }
    }

    synchronized void suspend() {
        healthy = false;
        if (running != null) running.cancelled = true;
    }

    boolean owns(String key) {
        Job job = running;
        return job != null && job.key.equals(key);
    }

    boolean reserveProvider(String key) {
        Job job = running;
        if (job == null || !job.key.equals(key)) return false;
        if (job.foreground && job.reservationReused.compareAndSet(false, true))
            ShitTokPoolDiagnostics.modeEvent(ShitTokPoolDiagnostics.modeToken(), "duplicateProviderPrevented", 1);
        return true;
    }

    synchronized boolean start(String key, Executor executor, BooleanSupplier networkAllowed,
                               Prepare prepare, Runnable available) {
        if (!healthy || selected == null || running != null || opened.contains(key)
                || attempted.contains(key) || !networkAllowed.getAsBoolean()) return false;
        Job job = new Job(key, selected, networkAllowed);
        running = job;
        try {
            executor.execute(() -> {
                Outcome outcome = Outcome.DEFERRED;
                long started = clock.getAsLong();
                try {
                    if (job.allowed()) outcome = prepare.run(job);
                } catch (Exception failure) {
                    if (job.allowed()) outcome = Outcome.MISS;
                } finally {
                    synchronized (ShitTokPreferencePreparation.this) {
                        if (outcome != Outcome.DEFERRED) {
                            attempted.add(key);
                            if (outcome == Outcome.READY) ready++; else misses++;
                        }
                        running = null;
                    }
                    long ended = clock.getAsLong();
                    ShitTokPoolDiagnostics.recordAt(ended, "preferencePreparation", key + " result=" + outcome
                            + " promoted=" + job.foreground + " ms=" + (ended - started));
                    if (job.foreground) available.run();
                }
            });
        } catch (RejectedExecutionException busy) {
            running = null;
            return false;
        }
        return true;
    }

    String summary() {
        return "preferenceReady=" + ready + " preferenceMisses=" + misses
                + " providerWorkReused=" + promoted
                + " bufferingWhilePreparing=" + bufferingWhilePreparing;
    }
}
