package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.LongSupplier;

/** Two raw candidates, produced independently of the fast ordinary admission race. */
final class ShitTokPreferenceBuffer {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private final ExecutorService executor;
    private final LongSupplier clock;
    private ContentPreference mode = ContentPreference.ALL;
    private List<NativeContentItem> ready = Collections.emptyList();
    private Future<?> work;
    private int generation;
    private long retryAt;

    ShitTokPreferenceBuffer() { this(IO, () -> System.nanoTime() / 1_000_000L); }
    ShitTokPreferenceBuffer(ExecutorService executor, LongSupplier clock) {
        this.executor = executor;
        this.clock = clock;
    }

    synchronized void request(ContentPreference next, Callable<List<NativeContentItem>> loader, Runnable available) {
        if (mode != next) {
            stop(); mode = next; ready = Collections.emptyList(); retryAt = 0;
        }
        if (next == ContentPreference.ALL || work != null || !ready.isEmpty() || clock.getAsLong() < retryAt) return;
        int token = generation;
        work = executor.submit(() -> {
            List<NativeContentItem> loaded;
            try { loaded = loader.call(); }
            catch (Exception failed) { loaded = Collections.emptyList(); }
            boolean notify;
            synchronized (this) {
                if (token != generation || Thread.currentThread().isInterrupted()) return;
                ready = new ArrayList<>(loaded.subList(0, Math.min(2, loaded.size())));
                work = null;
                retryAt = clock.getAsLong() + (ready.isEmpty() ? 30_000L : 0L);
                notify = !ready.isEmpty();
            }
            if (notify) available.run();
        });
    }

    synchronized boolean hasReady(ContentPreference current) { return current == mode && !ready.isEmpty(); }

    synchronized List<NativeContentItem> take(ContentPreference current) {
        if (current != mode) return Collections.emptyList();
        List<NativeContentItem> result = ready;
        ready = Collections.emptyList();
        return result;
    }

    synchronized void stop() {
        generation++;
        if (work != null) work.cancel(true);
        work = null;
    }
}
