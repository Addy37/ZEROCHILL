package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** Main-thread owner of the optional Shows browser request. No work starts at construction. */
final class ShowsCategoryLoader {
    private final ExecutorService io;
    private final Executor main;
    private final Callable<List<NativeContentItem>> fetch;
    private final Consumer<List<NativeContentItem>> publish;
    private Future<?> task;
    private List<NativeContentItem> cached;
    private int generation;
    private boolean active;
    private boolean closed;

    ShowsCategoryLoader(ExecutorService io, Executor main,
            Callable<List<NativeContentItem>> fetch, Consumer<List<NativeContentItem>> publish) {
        this.io = io;
        this.main = main;
        this.fetch = fetch;
        this.publish = publish;
    }

    void setActive(boolean value) {
        if (closed || active == value) return;
        active = value;
        if (!active) cancel();
        else load();
    }

    /** Explicit Shows refresh, not a tab switch. */
    void invalidate() {
        cancel();
        cached = null;
        if (active && !closed) load();
    }

    private void load() {
        if (cached != null || task != null) return;
        final int request = ++generation;
        task = io.submit(() -> {
            List<NativeContentItem> result;
            try {
                if (Thread.currentThread().isInterrupted()) return;
                result = fetch.call();
            } catch (Exception ignored) {
                result = null;
            }
            final List<NativeContentItem> items = result;
            main.execute(() -> {
                if (closed || !active || request != generation) return;
                task = null;
                // A failed/empty request can retry on the next activation.
                if (items == null || items.isEmpty()) return;
                cached = new ArrayList<>(items);
                publish.accept(cached);
            });
        });
    }

    private void cancel() {
        generation++;
        if (task != null) task.cancel(true);
        task = null;
    }

    void close() {
        closed = true;
        active = false;
        cancel();
        cached = null;
    }
}
