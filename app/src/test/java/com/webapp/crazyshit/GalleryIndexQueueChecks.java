package com.webapp.crazyshit;

import java.util.ArrayDeque;
import java.util.concurrent.RejectedExecutionException;

/** Standalone checks: no Android runtime or external dependencies required. */
public final class GalleryIndexQueueChecks {
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
    }

    private static GalleryIndexQueue.Snapshot get(GalleryIndexQueue queue, String key) {
        for (GalleryIndexQueue.Snapshot snapshot : queue.snapshots()) {
            if (snapshot.key.equals(key)) return snapshot;
        }
        throw new AssertionError("Missing task: " + key);
    }

    public static void main(String[] args) {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        GalleryIndexQueue queue = new GalleryIndexQueue(tasks::add);
        check(queue.enqueue("a", c -> { throw new AssertionError("Cancelled task executed"); }, 3));
        check(queue.enqueue("b", c -> c.reportItems(10), 2));
        check(queue.enqueue("c", c -> { c.reportItems(7); throw new java.io.IOException(); }, 0));
        check(queue.enqueue("d", c -> { throw new AssertionError("Cancelled waiting task executed"); }, 0));
        check(!queue.enqueue("a", c -> {}, 0));
        check(tasks.size() == 2 && get(queue, "c").status == GalleryIndexQueue.Status.WAITING);
        check(queue.cancel("d") && get(queue, "d").status == GalleryIndexQueue.Status.CANCELLED);
        check(queue.cancel("a") && get(queue, "a").status == GalleryIndexQueue.Status.CANCELLING);
        check(!queue.enqueue("a", c -> {}, 0));
        check(tasks.size() == 2 && get(queue, "c").status == GalleryIndexQueue.Status.WAITING);
        tasks.remove().run();
        check(get(queue, "a").status == GalleryIndexQueue.Status.CANCELLED && get(queue, "a").items == 3);
        check(tasks.size() == 2 && get(queue, "c").status == GalleryIndexQueue.Status.RUNNING);
        tasks.remove().run();
        check(get(queue, "b").status == GalleryIndexQueue.Status.COMPLETED && get(queue, "b").items == 10);
        tasks.remove().run();
        check(get(queue, "c").status == GalleryIndexQueue.Status.FAILED && get(queue, "c").items == 7);
        check(queue.enqueue("c", c -> { c.reportItems(5); c.reportItems(12); }, 7));
        tasks.remove().run();
        check(get(queue, "c").status == GalleryIndexQueue.Status.COMPLETED && get(queue, "c").items == 12);
        check(queue.dismiss("a") && !queue.cancel("a"));
        GalleryIndexQueue rejected = new GalleryIndexQueue(r -> { throw new RejectedExecutionException(); });
        check(rejected.enqueue("rejected", c -> {}, 0));
        check(get(rejected, "rejected").status == GalleryIndexQueue.Status.FAILED);
        System.out.println("GalleryIndexQueue checks passed");
    }
}
