package com.webapp.crazyshit;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShitTokInventoryAdmissionTest {
    static NativeContentItem item(String name) { return new NativeContentItem(NativeContentItem.KIND_MEDIA,
            name, "https://fixture.invalid/" + name, "", "", "Shit Show", "", "", "", 0L, .5f); }
    @Test public void fastStarterPublishesBeforeSlowSecondaryAndFullRunway() throws Exception {
        ShitTokOrientationAdmission gate = new ShitTokOrientationAdmission((s, allowed) -> .5f, () -> 0L);
        BlockingQueue<NativeContentItem> candidates = new ArrayBlockingQueue<>(48);
        candidates.add(item("slow")); candidates.add(item("fast")); candidates.add(item("other"));
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger(), peak = new AtomicInteger();
        List<String> published = new ArrayList<>();
        try {
            ShitTokOrientationAdmission.Result result = gate.admitConcurrent(candidates, () -> true, candidate -> {
                int count = active.incrementAndGet(); peak.accumulateAndGet(count, Math::max);
                try {
                    if (candidate.title.equals("slow")) release.await(2, TimeUnit.SECONDS);
                    return new CrazyShitRepository.StreamInfo(candidate.url + ".mp4", candidate.url, candidate.title);
                } finally { active.decrementAndGet(); }
            }, () -> true, 3, partial -> {
                published.add(partial.items.get(0).title);
                if (published.size() == 1) { assertNotEquals("slow", published.get(0)); release.countDown(); }
            }, count -> assertTrue(count <= 3));
            assertEquals(3, result.items.size());
            assertTrue(peak.get() <= 3);
        } finally { release.countDown(); gate.close(); }
    }
    @Test public void failedCandidatesDoNotSpendInventoryBudgetAndStaleResultsCannotPublish() {
        ShitTokOrientationAdmission gate = new ShitTokOrientationAdmission((s, allowed) -> .5f, () -> 0L);
        BlockingQueue<NativeContentItem> queue = new ArrayBlockingQueue<>(48);
        queue.add(item("bad")); queue.add(item("good")); queue.add(item("replacement"));
        try {
            ShitTokOrientationAdmission.Result result = gate.admitConcurrent(queue, () -> true, candidate -> {
                if (candidate.title.equals("bad")) throw new java.io.IOException("unusable");
                return new CrazyShitRepository.StreamInfo(candidate.url + ".mp4", candidate.url, candidate.title);
            }, () -> true, 2, null, count -> {});
            assertEquals(2, result.items.size()); assertEquals(2, result.streams.size());
            queue.add(item("stale"));
            AtomicBoolean allowed = new AtomicBoolean(true);
            AtomicInteger callbacks = new AtomicInteger();
            result = gate.admitConcurrent(queue, () -> true, candidate -> {
                allowed.set(false);
                return new CrazyShitRepository.StreamInfo(candidate.url + ".mp4", candidate.url, candidate.title);
            }, allowed::get, 1, partial -> callbacks.incrementAndGet(), count -> {});
            assertEquals(0, callbacks.get()); assertTrue(result.items.isEmpty());
        } finally { gate.close(); }
    }
}
