package com.webapp.crazyshit;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShitTokAdmissionHealthTest {
    @Test public void individualPrimaryProbeErrorsNeverParkTheWholeCreatorProvider() {
        AtomicLong clock = new AtomicLong();
        ShitTokAdmissionHealth health = new ShitTokAdmissionHealth(clock::get);
        for (String provider : Arrays.asList("onlyhaven", "fapello", "shit-show", "baddiehub")) {
            for (int i = 0; i < 30; i++) health.result(provider, false, true, 1800);
            assertTrue(provider, health.allowed(provider));
            assertTrue(health.cost(provider) > 0);
        }
    }

    @Test public void productiveSourceUsesAllFreeLanesWhileProducerAndBunkrBackoffRemainOpen() throws Exception {
        AtomicLong clock = new AtomicLong(System.nanoTime() / 1_000_000);
        ShitTokOrientationAdmission gate = new ShitTokOrientationAdmission((s, allowed) -> .5f, clock::get);
        gate.setCreatorFocused(true);
        for (int i = 0; i < 3; i++) gate.admit(Arrays.asList(bunkr("bad" + i)), candidate -> {
            throw new java.io.IOException();
        }, () -> true, new Random(0));
        BlockingQueue<NativeContentItem> queue = new ArrayBlockingQueue<>(48);
        queue.add(bunkr("waiting"));
        for (int i = 0; i < 3; i++) queue.add(ShitTokFilteredInventoryTest.clip("Jane", 6, i));
        CountDownLatch allStarted = new CountDownLatch(3), release = new CountDownLatch(1);
        ExecutorService io = Executors.newSingleThreadExecutor();
        try {
            Future<ShitTokOrientationAdmission.Result> result = io.submit(() -> gate.admitConcurrent(queue, () -> false,
                    candidate -> {
                        assertEquals("onlyhaven", ShitTokAspectPriority.sourceKey(candidate));
                        allStarted.countDown();
                        assertTrue(release.await(3, TimeUnit.SECONDS));
                        return stream(candidate);
                    }, () -> true, 3, partial -> {}, count -> assertTrue(count <= 3)));
            assertTrue("No idle third lane behind a running producer or Bunkr cooldown", allStarted.await(3, TimeUnit.SECONDS));
            release.countDown();
            assertEquals(3, result.get(3, TimeUnit.SECONDS).items.size());
        } finally { release.countDown(); gate.close(); io.shutdownNow(); }
    }

    @Test public void rawPoolCanDrainKnownNegativeItemsWithoutRepeatingTheirProbe() {
        AtomicLong clock = new AtomicLong();
        AtomicInteger probes = new AtomicInteger();
        ShitTokOrientationAdmission gate = new ShitTokOrientationAdmission((s, allowed) -> {
            probes.incrementAndGet(); return 1.777f;
        }, clock::get);
        NativeContentItem wide = ShitTokFilteredInventoryTest.clip("Jane", 6, 1);
        try {
            assertFalse(gate.rejectsKnown(wide));
            assertTrue(gate.admit(Arrays.asList(wide), ShitTokAdmissionHealthTest::stream,
                    () -> true, new Random(0)).items.isEmpty());
            assertTrue(gate.rejectsKnown(wide));
            assertEquals(1, probes.get());
        } finally { gate.close(); }
    }
    @Test public void repeatedBunkrErrorsBackOffTemporarilyAndSuccessfulRecoveryRestoresIt() {
        AtomicLong clock = new AtomicLong();
        ShitTokAdmissionHealth health = new ShitTokAdmissionHealth(clock::get);
        health.result("bunkr", false, true, 1800);
        assertTrue(health.allowed("bunkr"));
        health.result("bunkr", false, true, 1800); health.result("bunkr", false, true, 1800);
        assertFalse(health.allowed("bunkr"));
        for (String provider : Arrays.asList("onlyhaven", "fapello", "shit-show")) assertTrue(health.allowed(provider));
        clock.set(5000); assertTrue(health.allowed("bunkr"));
        for (int i = 0; i < 3; i++) health.result("bunkr", false, true, 1800);
        clock.set(14_999); assertFalse(health.allowed("bunkr"));
        clock.set(15_000); assertTrue(health.allowed("bunkr"));
        health.result("bunkr", true, false, 100);
        assertTrue(health.summary().contains("bunkrBackoffMs=0"));
    }
    @Test public void gateRecordsActualProbeFailuresAndLeavesHealthySourcesAdmissible() {
        AtomicLong clock = new AtomicLong();
        ShitTokOrientationAdmission gate = new ShitTokOrientationAdmission((stream, allowed) -> {
            if (stream.title.startsWith("bad")) throw new java.io.IOException("fixture probe");
            return .5f;
        }, clock::get);
        gate.setCreatorFocused(true);
        try {
            for (int i = 0; i < 3; i++) gate.admit(Arrays.asList(bunkr("bad" + i)), item -> stream(item), () -> true, new Random(0));
            assertFalse(gate.canOffer(bunkr("later"), Collections.emptyList()));
            for (int source : new int[]{6, 5}) assertTrue(gate.canOffer(ShitTokFilteredInventoryTest.clip("Jane", source, 1), Collections.emptyList()));
            assertTrue(gate.canOffer(ShitTokInventoryAdmissionTest.item("show"), Collections.emptyList()));
            clock.set(5000); assertTrue(gate.canOffer(bunkr("recovered"), Collections.emptyList()));
            assertFalse(gate.canOffer(bunkr("third"), Arrays.asList(bunkr("first"), bunkr("second"))));
            assertEquals(1, gate.admit(Arrays.asList(bunkr("recovered")), item -> stream(item), () -> true, new Random(0)).items.size());
        } finally { gate.close(); }
    }
    @Test public void slowBunkrNeverOwnsMoreThanOneLaneWhilePortraitSourcesContinue() {
        BlockingQueue<NativeContentItem> queue = new ArrayBlockingQueue<>(48);
        for (int i = 0; i < 12; i++) queue.add(bunkr("held" + i));
        queue.add(ShitTokInventoryAdmissionTest.item("show"));
        queue.add(ShitTokFilteredInventoryTest.clip("Jane", 5, 1));
        queue.add(ShitTokFilteredInventoryTest.clip("Alice", 6, 1));
        AtomicInteger active = new AtomicInteger(), maximum = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        ShitTokOrientationAdmission gate = new ShitTokOrientationAdmission((s, allowed) -> .5f, () -> System.nanoTime()/1_000_000);
        gate.setCreatorFocused(true);
        try {
            ShitTokOrientationAdmission.Result result = gate.admitConcurrent(queue, () -> true, item -> {
                if (ShitTokAspectPriority.sourceKey(item).equals("bunkr")) {
                    int n = active.incrementAndGet(); maximum.accumulateAndGet(n, Math::max);
                    try { release.await(3, TimeUnit.SECONDS); } finally { active.decrementAndGet(); }
                }
                return stream(item);
            }, () -> true, 3, partial -> assertFalse(ShitTokAspectPriority.sourceKey(partial.items.get(0)).equals("bunkr")), n -> assertTrue(n <= 3));
            assertEquals(3, result.items.size());
            assertTrue(result.items.stream().noneMatch(i -> ShitTokAspectPriority.sourceKey(i).equals("bunkr")));
            assertTrue(maximum.get() <= 1);
        } finally { release.countDown(); gate.close(); }
    }
    @Test public void completedProbeSurvivesClosedPermissionButSelectionCancellationInvalidatesIt() {
        for (boolean cancel : new boolean[]{false, true}) {
            AtomicBoolean allowed = new AtomicBoolean(true);
            ShitTokOrientationAdmission[] holder = new ShitTokOrientationAdmission[1];
            holder[0] = new ShitTokOrientationAdmission((stream, permission) -> {
                allowed.set(false);
                if (cancel) holder[0].cancelPending();
                return .5f;
            }, () -> 0L);
            try {
                ShitTokOrientationAdmission.Result result = holder[0].admit(Arrays.asList(bunkr("completed")),
                        item -> stream(item), allowed::get, new Random(0));
                assertEquals(cancel ? 0 : 1, result.items.size());
                assertEquals(cancel ? 1 : 0, result.remaining.size());
            } finally { holder[0].close(); }
        }
    }
    @Test public void portraitSchedulingStillRanksHotCandidatesWithinThePortraitSet() {
        ShitTokOrientationAdmission gate = new ShitTokOrientationAdmission((s, allowed) -> .5f, () -> 0L);
        gate.setCreatorFocused(true);
        BlockingQueue<NativeContentItem> queue = new ArrayBlockingQueue<>(48);
        NativeContentItem cold = ShitTokInventoryAdmissionTest.item("cold"), hot = ShitTokInventoryAdmissionTest.item("hot");
        queue.add(cold); queue.add(hot);
        ShitTokCommunityPopularity.setScoresForTest(Collections.singletonMap(ZeroChillSocialRepository.contentKey(hot.url), 5f));
        CountDownLatch release = new CountDownLatch(1);
        try {
            ShitTokOrientationAdmission.Result result = gate.admitConcurrent(queue, () -> true, candidate -> {
                if (candidate != hot) release.await(2, TimeUnit.SECONDS);
                return stream(candidate);
            }, () -> true, 1, partial -> { assertEquals(hot.url, partial.items.get(0).url); release.countDown(); }, count -> {});
            assertEquals(hot.url, result.items.get(0).url);
        } finally { release.countDown(); gate.close(); ShitTokCommunityPopularity.resetForTest(); }
    }

    static NativeContentItem bunkr(String title) { return new NativeContentItem("media", title, "https://bunkr.cr/f/" + title, "", "", "Bunkr", ""); }
    static CrazyShitRepository.StreamInfo stream(NativeContentItem item) { return new CrazyShitRepository.StreamInfo(item.url + ".mp4", item.url, item.title); }
}
