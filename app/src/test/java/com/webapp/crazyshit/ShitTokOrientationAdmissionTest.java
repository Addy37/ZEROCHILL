package com.webapp.crazyshit;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ShitTokOrientationAdmissionTest {
    @Test public void continuitySkipsFailedAndLandscapeCandidatesAndStopsAtSmallPlayableBudget() {
        AtomicInteger resolves = new AtomicInteger();
        AtomicInteger probes = new AtomicInteger();
        ShitTokOrientationAdmission gate = new ShitTokOrientationAdmission((stream, allowed) ->
                probes.incrementAndGet() == 1 ? 1.5f : .5f, () -> 0L);
        List<NativeContentItem> candidates = new ArrayList<>();
        for (String name : Arrays.asList("bad", "wide", "a", "b", "c", "d"))
            candidates.add(item(name, "Kaotic", 0f));
        ShitTokOrientationAdmission.Result result = gate.admit(candidates, candidate -> {
            if (resolves.incrementAndGet() == 1) throw new java.io.IOException("failed");
            return stream(candidate);
        }, () -> true, new Random(2), 2, true, null);
        assertEquals(2, result.items.size()); assertEquals(2, result.streams.size());
        assertEquals(4, resolves.get());
        assertEquals(3, probes.get());
        assertFalse(result.remaining.isEmpty());
    }
    @Test public void continuityResolvesTrustedHintsBeforePublishingAndHealthyAdmissionStillReusesThem() {
        AtomicInteger resolves = new AtomicInteger();
        NativeContentItem hint = item("hint", "Shit Show", .5f);
        ShitTokOrientationAdmission gate = new ShitTokOrientationAdmission((stream, allowed) -> {
            throw new AssertionError("trusted metadata needs no probe");
        }, () -> 0L);
        assertTrue(gate.admit(Collections.singletonList(hint), candidate -> null,
                () -> false, new Random(1), 1, true, null).items.isEmpty());
        ShitTokOrientationAdmission.Result ready = gate.admit(Collections.singletonList(hint), candidate -> {
            resolves.incrementAndGet(); return stream(candidate);
        }, () -> true, new Random(1), 1, true, partial -> assertEquals(1, partial.streams.size()));
        assertEquals(1, ready.items.size());
        gate.admit(Collections.singletonList(hint), candidate -> {
            resolves.incrementAndGet(); return stream(candidate);
        }, () -> true, new Random(1));
        assertEquals(1, resolves.get());
    }
    @Test public void trustedHintsPublishBeforeNetworkAndPreserveFields() {
        AtomicInteger probes = new AtomicInteger();
        ShitTokOrientationAdmission gate = gate(probes, new AtomicLong(), .5f);
        NativeContentItem portrait = item("portrait", "Shit Show", .5f);
        NativeContentItem square = item("square", "BaddieHub", 1f);
        NativeContentItem wide = item("wide", "Shit Show", 1.01f);
        List<String> events = new ArrayList<>();
        ShitTokOrientationAdmission.Result result = gate.admit(
                Arrays.asList(item("unknown", "Kaotic", 0), portrait, wide, square),
                candidate -> { events.add("resolve"); return stream(candidate); }, () -> true, new Random(7),
                verified -> events.add(verified.items.get(0).title));
        assertEquals(3, result.items.size());
        assertEquals(1, probes.get());
        assertEquals("resolve", events.get(2));
        NativeContentItem copy = result.items.stream().filter(i -> i.url.equals(portrait.url)).findFirst().get();
        assertNotSame(portrait, copy);
        assertEquals(portrait.uploader, copy.uploader);
        assertEquals(portrait.searchQuery, copy.searchQuery);
        assertEquals(portrait.publishedAtMillis, copy.publishedAtMillis);
        assertEquals(ShitTokAspectPriority.sourceKey(portrait), ShitTokAspectPriority.sourceKey(copy));
    }

    @Test public void currentTrustedLandscapeHintOverridesCachedPortraitForTheSameUrl() {
        AtomicInteger probes = new AtomicInteger();
        ShitTokOrientationAdmission gate = gate(probes, new AtomicLong(), .5f);
        assertEquals(1, gate.admit(Collections.singletonList(item("same", "Shit Show", .5f)),
                ShitTokOrientationAdmissionTest::stream, () -> false, new Random(1)).items.size());
        assertTrue(gate.admit(Collections.singletonList(item("same", "Shit Show", 1.5f)),
                ShitTokOrientationAdmissionTest::stream, () -> false, new Random(1)).items.isEmpty());
        assertTrue(gate.admit(Collections.singletonList(item("same", "Shit Show", 0f)),
                ShitTokOrientationAdmissionTest::stream, () -> false, new Random(1)).items.isEmpty());
        assertEquals(0, probes.get());
    }

    @Test public void pausedWorkDefersUnknownButStillAdmitsKnownHints() {
        AtomicInteger probes = new AtomicInteger();
        ShitTokOrientationAdmission gate = gate(probes, new AtomicLong(), .5f);
        ShitTokOrientationAdmission.Result result = gate.admit(Arrays.asList(
                item("known", "Shit Show", 1), item("unknown", "Kaotic", 0)),
                ShitTokOrientationAdmissionTest::stream, () -> false, new Random(1));
        assertTrue(result.deferred);
        assertEquals(1, result.items.size());
        assertEquals(1, result.remaining.size());
        assertEquals(0, probes.get());
    }

    @Test public void exactItemResultsDoNotTeachOtherItemsAndReuseResolvedStream() {
        AtomicInteger probes = new AtomicInteger();
        AtomicInteger resolves = new AtomicInteger();
        ShitTokOrientationAdmission gate = gate(probes, new AtomicLong(), .6f);
        NativeContentItem one = item("one", "Kaotic", 0);
        ShitTokOrientationAdmission.Resolver resolver = candidate -> {
            resolves.incrementAndGet(); return stream(candidate);
        };
        gate.admit(Collections.singletonList(one), resolver, () -> true, new Random(1));
        ShitTokOrientationAdmission.Result cached = gate.admit(Collections.singletonList(one), resolver,
                () -> true, new Random(1));
        assertEquals(.6f, cached.items.get(0).aspectRatioHint, 0f);
        assertEquals(1, cached.streams.size());
        assertEquals(1, probes.get());
        gate.admit(Collections.singletonList(item("two", "Kaotic", 0)), resolver, () -> true, new Random(1));
        assertEquals(2, probes.get());
        assertEquals(2, resolves.get());
    }

    @Test public void unknownHasBoundedCooldownAndDoesNotBlockOtherSources() {
        AtomicLong clock = new AtomicLong();
        AtomicInteger probes = new AtomicInteger();
        ShitTokOrientationAdmission gate = new ShitTokOrientationAdmission((stream, allowed) -> {
            probes.incrementAndGet();
            if (stream.title.equals("bad")) throw new java.io.IOException("unavailable");
            return .5f;
        }, clock::get);
        NativeContentItem bad = item("bad", "Kaotic", 0);
        List<NativeContentItem> candidates = Arrays.asList(bad, item("good", "Other", 0));
        ShitTokOrientationAdmission.Result first = gate.admit(candidates,
                ShitTokOrientationAdmissionTest::stream, () -> true, new Random(1));
        assertEquals(1, first.items.size());
        assertEquals("good", first.items.get(0).title);
        gate.admit(candidates, ShitTokOrientationAdmissionTest::stream, () -> true, new Random(1));
        assertEquals(2, probes.get());
        clock.addAndGet(ShitTokOrientationAdmission.UNKNOWN_COOLDOWN_MS + 1);
        gate.admit(Collections.singletonList(bad), ShitTokOrientationAdmissionTest::stream, () -> true,
                new Random(1));
        assertEquals(3, probes.get());
    }

    @Test public void speculativeCancellationDoesNotCacheUnknownOrRepeatResolution() {
        AtomicBoolean allowed = new AtomicBoolean(true);
        AtomicInteger resolves = new AtomicInteger();
        ShitTokOrientationAdmission gate = new ShitTokOrientationAdmission((stream, predicate) -> {
            allowed.set(false); throw new ShitTokOrientationAdmission.Deferred();
        }, () -> 0L);
        NativeContentItem candidate = item("pending", "Kaotic", 0);
        ShitTokOrientationAdmission.Resolver resolver = item -> { resolves.incrementAndGet(); return stream(item); };
        ShitTokOrientationAdmission.Result result = gate.admit(Collections.singletonList(candidate), resolver,
                allowed::get, new Random(1));
        assertTrue(result.deferred);
        assertTrue(result.items.isEmpty());
        assertEquals(1, result.remaining.size());
        allowed.set(true);
        gate.admit(result.remaining, resolver, allowed::get, new Random(1));
        assertEquals(1, resolves.get());
    }

    @Test public void capRetainsUntestedCandidatesAndBalancesProbeSources() {
        AtomicInteger probes = new AtomicInteger();
        ShitTokOrientationAdmission gate = gate(probes, new AtomicLong(), .5f);
        ArrayList<NativeContentItem> candidates = new ArrayList<>();
        for (int i = 0; i < 20; i++) candidates.add(item("kaotic" + i, "Kaotic", 0));
        for (int i = 0; i < 8; i++) candidates.add(item("other" + i, "Other", 0));
        ShitTokOrientationAdmission.Result result = gate.admit(candidates,
                ShitTokOrientationAdmissionTest::stream, () -> true, new Random(1));
        assertEquals(ShitTokOrientationAdmission.MAX_PROBES_PER_BATCH, probes.get());
        assertEquals(20, result.remaining.size());
        assertEquals(4, result.items.stream().filter(i -> i.uploader.equals("Kaotic")).count());
        assertEquals(4, result.items.stream().filter(i -> i.uploader.equals("Other")).count());
    }

    @Test public void landscapeUnknownAndUntrustedHintsFailClosed() {
        AtomicInteger probes = new AtomicInteger();
        ShitTokOrientationAdmission gate = gate(probes, new AtomicLong(), 0);
        ShitTokOrientationAdmission.Result result = gate.admit(
                Arrays.asList(item("fakeHint", "Unknown", .5f), item("wide", "BaddieHub", 1.8f)),
                ShitTokOrientationAdmissionTest::stream, () -> true, new Random(1));
        assertTrue(result.items.isEmpty());
        assertEquals(1, probes.get());
    }

    @Test public void exactItemCacheEvictsOldSessionEntries() {
        ShitTokOrientationAdmission gate = gate(new AtomicInteger(), new AtomicLong(), .5f);
        for (int i = 0; i <= ShitTokOrientationAdmission.CACHE_LIMIT; i++) gate.admit(
                Collections.singletonList(item("known" + i, "Shit Show", 1)),
                ShitTokOrientationAdmissionTest::stream, () -> true, new Random(1));
        assertEquals("unknown", gate.describe(item("known0", "Shit Show", 1).url));
        assertTrue(gate.describe(item("known512", "Shit Show", 1).url).contains("metadata"));
        assertFalse(gate.describe(item("known512", "Shit Show", 1).url).contains("https"));
    }

    private static ShitTokOrientationAdmission gate(AtomicInteger probes, AtomicLong clock, float aspect) {
        return new ShitTokOrientationAdmission((stream, allowed) -> { probes.incrementAndGet(); return aspect; },
                clock::get);
    }
    private static CrazyShitRepository.StreamInfo stream(NativeContentItem item) {
        return new CrazyShitRepository.StreamInfo(item.url + ".mp4", item.url, item.title);
    }
    private static NativeContentItem item(String name, String source, float aspect) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, name, "https://example.test/" + name,
                "thumb", "4", source, "2", "description", "query", 123L, aspect);
    }
}
