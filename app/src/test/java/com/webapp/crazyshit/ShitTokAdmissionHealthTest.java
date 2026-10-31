package com.webapp.crazyshit;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShitTokAdmissionHealthTest {
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
    static NativeContentItem bunkr(String title) { return new NativeContentItem("media", title, "https://bunkr.cr/f/" + title, "", "", "Bunkr", ""); }
    static CrazyShitRepository.StreamInfo stream(NativeContentItem item) { return new CrazyShitRepository.StreamInfo(item.url + ".mp4", item.url, item.title); }
}
