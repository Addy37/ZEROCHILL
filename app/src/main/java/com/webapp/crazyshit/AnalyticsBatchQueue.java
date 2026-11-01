package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.List;

/** Confined to the analytics worker. No alarms, jobs, UI waits or persistent identifiers. */
final class AnalyticsBatchQueue {
    static final int BATCH_SIZE = 30;
    static final long FLUSH_DELAY_MS = 60_000L;
    static final long RETRY_DELAY_MS = 60_000L;
    static final long FAILURE_COOLDOWN_MS = 5 * 60_000L;
    private static final int MAX_PENDING = 500;
    interface Cancellation { void cancel(); }
    interface Scheduler { Cancellation schedule(Runnable task, long delayMs); }
    interface Transport { int send(List<String> events) throws Exception; }
    private final Scheduler scheduler;
    private final Transport transport;
    private final List<String> pending = new ArrayList<>();
    private List<String> retry;
    private Cancellation scheduled;
    private boolean coolingDown;

    AnalyticsBatchQueue(Scheduler scheduler, Transport transport) {
        this.scheduler = scheduler;
        this.transport = transport;
    }

    void add(String event) {
        // Best-effort analytics: bound memory during an extended outage. Normal batches never
        // hit this limit. Retain recent events rather than growing an unbounded offline backlog.
        if (pending.size() == MAX_PENDING) pending.remove(0);
        pending.add(event);
        if (retry != null || coolingDown) return;
        if (pending.size() >= BATCH_SIZE) flush();
        else if (scheduled == null) schedule(FLUSH_DELAY_MS);
    }

    private void schedule(long delay) {
        if (scheduled != null) scheduled.cancel();
        scheduled = scheduler.schedule(() -> {
            scheduled = null;
            coolingDown = false;
            flush();
        }, delay);
    }

    private void flush() {
        if (scheduled != null) { scheduled.cancel(); scheduled = null; }
        boolean secondAttempt = retry != null;
        List<String> batch = retry;
        retry = null;
        if (batch == null) {
            if (pending.isEmpty()) return;
            int size = Math.min(BATCH_SIZE, pending.size());
            batch = new ArrayList<>(pending.subList(0, size));
            pending.subList(0, size).clear();
        }
        int status;
        try { status = transport.send(batch); }
        catch (Exception ignored) { status = 0; }
        boolean success = status >= 200 && status < 300;
        boolean transientFailure = status == 0 || status == 429 || status >= 500;
        if (!success && transientFailure) {
            if (!secondAttempt) {
                retry = batch;
                schedule(RETRY_DELAY_MS);
            } else {
                coolingDown = true;
                schedule(FAILURE_COOLDOWN_MS);
            }
        } else if (!pending.isEmpty()) {
            // Drain an outage backlog gradually; never burst one invocation per queued event.
            schedule(FLUSH_DELAY_MS);
        }
    }
}
