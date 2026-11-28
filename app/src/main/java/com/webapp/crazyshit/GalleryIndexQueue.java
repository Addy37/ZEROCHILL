package com.webapp.crazyshit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Executor;

/** In-process scheduling policy for manually requested gallery indexing. */
final class GalleryIndexQueue {
    enum Status { WAITING, RUNNING, CANCELLING, CANCELLED, COMPLETED, FAILED }

    interface Work {
        void run(Control control) throws Exception;
    }

    interface Control {
        boolean isCancelled();
        void reportItems(int count);
    }

    static final class Snapshot {
        final String key;
        final Status status;
        final int items;

        Snapshot(String key, Status status, int items) {
            this.key = key;
            this.status = status;
            this.items = items;
        }
    }

    private final class Entry implements Control {
        final String key;
        final Work work;
        Status status = Status.WAITING;
        int items;

        Entry(String key, Work work, int items) {
            this.key = key;
            this.work = work;
            this.items = items;
        }

        @Override public boolean isCancelled() {
            synchronized (GalleryIndexQueue.this) {
                return status == Status.CANCELLING || status == Status.CANCELLED;
            }
        }

        @Override public void reportItems(int count) {
            synchronized (GalleryIndexQueue.this) {
                if (status == Status.RUNNING || status == Status.CANCELLING) {
                    items = Math.max(items, count);
                }
            }
        }
    }

    private static final int MAX_ACTIVE = 2;
    private final Executor executor;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    private int active;

    GalleryIndexQueue(Executor executor) {
        this.executor = java.util.Objects.requireNonNull(executor);
    }

    /** Key must be a canonical gallery identity, not a display name. */
    synchronized boolean enqueue(String key, Work work, int cachedItems) {
        if (key == null || key.trim().isEmpty() || work == null) return false;
        Entry previous = entries.get(key);
        if (previous != null && (previous.status == Status.WAITING
                || previous.status == Status.RUNNING || previous.status == Status.CANCELLING)) {
            return false;
        }
        // Retried work goes to the back of the queue. The caller supplies the real
        // persisted item count, which may have changed since an earlier attempt.
        entries.remove(key);
        entries.put(key, new Entry(key, work, Math.max(0, cachedItems)));
        pump();
        return true;
    }

    synchronized boolean cancel(String key) {
        Entry entry = entries.get(key);
        if (entry == null) return false;
        if (entry.status == Status.WAITING) {
            entry.status = Status.CANCELLED;
            return true;
        }
        if (entry.status != Status.RUNNING) return false;
        // Keep the slot occupied until the running request actually exits.
        entry.status = Status.CANCELLING;
        return true;
    }

    synchronized List<Snapshot> snapshots() {
        List<Snapshot> result = new ArrayList<>();
        for (Entry entry : entries.values()) {
            result.add(new Snapshot(entry.key, entry.status, entry.items));
        }
        return result;
    }

    synchronized boolean dismiss(String key) {
        Entry entry = entries.get(key);
        if (entry == null || entry.status == Status.WAITING || entry.status == Status.RUNNING
                || entry.status == Status.CANCELLING) return false;
        entries.remove(key);
        return true;
    }

    private void pump() {
        while (active < MAX_ACTIVE) {
            Entry next = null;
            for (Entry entry : entries.values()) {
                if (entry.status == Status.WAITING) {
                    next = entry;
                    break;
                }
            }
            if (next == null) return;
            final Entry entry = next;
            entry.status = Status.RUNNING;
            active++;
            try {
                executor.execute(() -> execute(entry));
            } catch (java.util.concurrent.RejectedExecutionException failure) {
                entry.status = Status.FAILED;
                active--;
            }
        }
    }

    private void execute(Entry entry) {
        boolean completed = false;
        try {
            if (!entry.isCancelled()) {
                entry.work.run(entry);
                completed = true;
            }
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            synchronized (this) {
                entry.status = entry.status == Status.CANCELLING
                        ? Status.CANCELLED : completed ? Status.COMPLETED : Status.FAILED;
                active--;
                pump();
            }
        }
    }
}
