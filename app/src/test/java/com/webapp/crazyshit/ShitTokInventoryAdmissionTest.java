package com.webapp.crazyshit;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShitTokInventoryAdmissionTest {
    @Test public void aSlowProviderCannotMonopolizeAllLanesWhileOtherSourcesAreArriving() {
        ShitTokOrientationAdmission gate = new ShitTokOrientationAdmission((s, allowed) -> .5f, () -> 0L);
        BlockingQueue<NativeContentItem> queue = new ArrayBlockingQueue<>(48);
        for (int i = 0; i < 4; i++) queue.add(new NativeContentItem("media", "slow" + i,
                "https://fixture.invalid/slow" + i, "", "", "Kaotic", "", ""));
        queue.add(new NativeContentItem("media", "fast", "https://fixture.invalid/fast",
                "", "", "creator", "", "OnlyFap"));
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean productionDone = new AtomicBoolean();
        try {
            ShitTokOrientationAdmission.Result result = gate.admitConcurrent(queue, productionDone::get, candidate -> {
                if (!candidate.title.equals("fast")) release.await(2, TimeUnit.SECONDS);
                return new CrazyShitRepository.StreamInfo(candidate.url + ".mp4", candidate.url, candidate.title);
            }, () -> true, 1, partial -> {
                assertEquals("fast", partial.items.get(0).title);
                productionDone.set(true); release.countDown();
            }, count -> assertTrue(count <= 3));
            assertEquals("fast", result.items.get(0).title);
        } finally { release.countDown(); gate.close(); }
    }

    @Test public void communityHotCandidateLeadsProgressiveAdmissionWithinSource() {
        ShitTokOrientationAdmission gate = new ShitTokOrientationAdmission((s, allowed) -> .5f, () -> 0L);
        BlockingQueue<NativeContentItem> queue = new ArrayBlockingQueue<>(48);
        NativeContentItem cold0 = item("cold0"), cold1 = item("cold1"), cold2 = item("cold2"), hot = item("hot");
        queue.add(cold0); queue.add(cold1); queue.add(cold2); queue.add(hot);
        Map<String, Float> scores = new HashMap<>();
        scores.put(ZeroChillSocialRepository.contentKey(hot.url), 5f);
        ShitTokCommunityPopularity.setScoresForTest(scores);
        CountDownLatch release = new CountDownLatch(1);
        try {
            ShitTokOrientationAdmission.Result result = gate.admitConcurrent(queue, () -> true, candidate -> {
                if (!candidate.title.equals("hot")) release.await(2, TimeUnit.SECONDS);
                return new CrazyShitRepository.StreamInfo(candidate.url + ".mp4", candidate.url, candidate.title);
            }, () -> true, 1, partial -> {
                assertEquals("hot", partial.items.get(0).title);
                release.countDown();
            }, count -> assertTrue(count <= 3));
            assertEquals("hot", result.items.get(0).title);
        } finally {
            release.countDown();
            ShitTokCommunityPopularity.resetForTest();
            gate.close();
        }
    }

    @Test public void cancelledResolutionSlotWaitDefersWithoutUnplayableCooldown() {
        ShitTokOrientationAdmission gate = new ShitTokOrientationAdmission((s, allowed) -> .5f, () -> 0L);
        BlockingQueue<NativeContentItem> queue = new ArrayBlockingQueue<>(48);
        queue.add(item("waiting"));
        try {
            ShitTokOrientationAdmission.Result result = gate.admitConcurrent(queue, () -> true,
                    candidate -> { throw new InterruptedException("slot wait cancelled"); },
                    () -> true, 1, null, count -> {});
            assertTrue(result.items.isEmpty());
            assertEquals(1, result.remaining.size());
            assertEquals(0, gate.unplayableRejectedCount());
        } finally { gate.close(); }
    }
    @Test public void oneMissingClipStillRacesAllThreeLanes() throws Exception {
        ShitTokOrientationAdmission gate = new ShitTokOrientationAdmission((s, allowed) -> .5f, () -> 0L);
        BlockingQueue<NativeContentItem> queue = new ArrayBlockingQueue<>(48);
        queue.add(item("slow")); queue.add(item("fast")); queue.add(item("other"));
        CountDownLatch release = new CountDownLatch(1);
        try {
            ShitTokOrientationAdmission.Result result = gate.admitConcurrent(queue, () -> true, candidate -> {
                if (!candidate.title.equals("fast")) release.await(2, TimeUnit.SECONDS);
                return new CrazyShitRepository.StreamInfo(candidate.url + ".mp4", candidate.url, candidate.title);
            }, () -> true, 1, partial -> {
                assertEquals("fast", partial.items.get(0).title);
                release.countDown();
            }, active -> assertTrue(active <= 3));
            assertEquals("fast", result.items.get(0).title);
        } finally { release.countDown(); gate.close(); }
    }

    @Test public void consumptionAndRapidPressureCanGrowBudgetDuringIncrementalAdmission() {
        ShitTokOrientationAdmission gate = new ShitTokOrientationAdmission((s, allowed) -> .5f, () -> 0L);
        BlockingQueue<NativeContentItem> queue = new ArrayBlockingQueue<>(48);
        for (int i = 0; i < 16; i++) queue.add(item("fresh" + i));
        AtomicInteger budget = new AtomicInteger(6), published = new AtomicInteger();
        try {
            ShitTokOrientationAdmission.Result result = gate.admitConcurrent(queue, () -> true,
                    candidate -> new CrazyShitRepository.StreamInfo(candidate.url + ".mp4", candidate.url, candidate.title),
                    () -> true, budget::get, partial -> {
                        // Two clips consumed and rapid mode entered while this batch is still running.
                        if (published.incrementAndGet() == 1) budget.set(12);
                    }, count -> assertTrue(count <= 3));
            assertEquals(12, result.items.size());
            assertEquals(12, published.get());
        } finally { gate.close(); }
    }

    @Test public void resolvedSeenAliasesDoNotSpendAdmissionBudgetOrProbe() {
        AtomicInteger probes = new AtomicInteger();
        ShitTokOrientationAdmission gate = new ShitTokOrientationAdmission((s, allowed) -> {
            probes.incrementAndGet(); return .5f;
        }, () -> 0L);
        BlockingQueue<NativeContentItem> queue = new ArrayBlockingQueue<>(48);
        queue.add(item("seen")); queue.add(item("fresh"));
        try {
            ShitTokOrientationAdmission.Result result = gate.admitConcurrent(queue, () -> true, candidate -> {
                if (candidate.title.equals("seen")) throw new ShitTokOrientationAdmission.Filtered();
                return new CrazyShitRepository.StreamInfo(candidate.url + ".mp4", candidate.url, candidate.title);
            }, () -> true, 1, null, count -> {});
            assertEquals("fresh", result.items.get(0).title);
            assertEquals(0, gate.unplayableRejectedCount());
        } finally { gate.close(); }
    }
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
    @Test public void slowVerificationSurvivesRefillAndReentersWithoutAnotherResolution() throws Exception {
        AtomicLong clock = new AtomicLong();
        ShitTokOrientationAdmission gate = new ShitTokOrientationAdmission((stream, allowed) -> .5f, clock::get);
        BlockingQueue<NativeContentItem> queue = new ArrayBlockingQueue<>(48);
        NativeContentItem slow = new NativeContentItem("media", "slow-browser", "https://slow.invalid/1", "", "", "", "");
        queue.add(slow); queue.add(item("fast"));
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), resolved = new CountDownLatch(1);
        AtomicInteger slowCalls = new AtomicInteger();
        try {
            ShitTokOrientationAdmission.Resolver resolver = candidate -> {
                if (candidate == slow) {
                    slowCalls.incrementAndGet(); entered.countDown(); release.await(); resolved.countDown();
                }
                return new CrazyShitRepository.StreamInfo(candidate.url + ".mp4", candidate.url, candidate.title);
            };
            ShitTokOrientationAdmission.Result first = gate.admitConcurrent(queue, () -> true, resolver,
                    () -> true, 1, partial -> clock.set(ShitTokOrientationAdmission.BATCH_MILLIS), count -> {});
            assertEquals("fast", first.items.get(0).title);
            assertTrue(entered.await(2, TimeUnit.SECONDS)); assertTrue(gate.hasPending());
            release.countDown(); assertTrue(resolved.await(2, TimeUnit.SECONDS));
            ShitTokOrientationAdmission.Result next = gate.admitConcurrent(queue, () -> true, resolver,
                    () -> true, 1, null, count -> {});
            assertEquals("slow-browser", next.items.get(0).title); assertEquals(1, slowCalls.get());
        } finally { release.countDown(); gate.close(); }
    }
    @Test public void lifecycleCancellationRejectsVerificationThatIgnoresInterrupts() throws Exception {
        ShitTokOrientationAdmission gate = new ShitTokOrientationAdmission((stream, allowed) -> .5f, () -> 0);
        BlockingQueue<NativeContentItem> queue = new ArrayBlockingQueue<>(48);
        queue.add(item("old"));
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService coordinator = Executors.newSingleThreadExecutor();
        AtomicBoolean allowed = new AtomicBoolean(true);
        AtomicInteger callbacks = new AtomicInteger();
        try {
            Future<?> batch = coordinator.submit(() -> gate.admitConcurrent(queue, () -> true, candidate -> {
                entered.countDown();
                while (release.getCount() > 0) try { release.await(); } catch (InterruptedException ignored) {}
                return new CrazyShitRepository.StreamInfo(candidate.url + ".mp4", candidate.url, candidate.title);
            }, allowed::get, 1, partial -> callbacks.incrementAndGet(), count -> {}));
            assertTrue(entered.await(2, TimeUnit.SECONDS)); allowed.set(false); gate.cancelPending();
            release.countDown(); batch.get(2, TimeUnit.SECONDS);
            assertEquals(0, callbacks.get()); assertFalse(gate.hasPending());
            queue.add(item("new"));
            ShitTokOrientationAdmission.Result next = gate.admitConcurrent(queue, () -> true,
                    candidate -> new CrazyShitRepository.StreamInfo(candidate.url + ".mp4", candidate.url, candidate.title),
                    () -> true, 1, null, count -> {});
            assertEquals("new", next.items.get(0).title);
        } finally { release.countDown(); coordinator.shutdownNow(); gate.close(); }
    }

}
