package com.webapp.crazyshit;

import org.junit.Test;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class ShitTokPreferencePreparationTest {
    @Test public void selectedPreferencePromotesTheSameReservedProviderAndWakesForeground() {
        ShitTokPreferencePreparation preparation = new ShitTokPreferencePreparation();
        ArrayDeque<Runnable> work = new ArrayDeque<>();
        AtomicBoolean networkAllowed = new AtomicBoolean(true);
        AtomicInteger discovery = new AtomicInteger(), wakeups = new AtomicInteger();
        preparation.select("all:chaos");
        preparation.playbackHealthy(true);

        assertTrue(preparation.start("all:women", work::add, networkAllowed::get, job -> {
            discovery.incrementAndGet();
            preparation.buffering();
            assertFalse(job.allowed());
            preparation.select("onlyfap:women");
            assertFalse(job.allowed());
            return ShitTokPreferencePreparation.Outcome.DEFERRED;
        }, wakeups::incrementAndGet));
        assertFalse(preparation.start("all:men", work::add, () -> true,
                job -> { throw new AssertionError("parallel preparation"); }, () -> {}));
        work.remove().run();
        assertEquals(0, wakeups.get());

        preparation.playbackHealthy(true);
        assertTrue(preparation.start("all:women", work::add, networkAllowed::get, job -> {
            assertTrue(preparation.owns("all:women"));
            preparation.select("all:women");
            preparation.select("all:women");
            preparation.playbackHealthy(false);
            networkAllowed.set(false);
            assertTrue(job.allowed());
            return ShitTokPreferencePreparation.Outcome.READY;
        }, wakeups::incrementAndGet));
        work.remove().run();
        assertEquals(1, discovery.get());
        assertEquals(1, wakeups.get());
        assertFalse(preparation.owns("all:women"));
        assertTrue(preparation.summary().contains("providerWorkReused=1"));
        assertTrue(preparation.summary().contains("bufferingWhilePreparing=1"));
    }

    @Test public void returningToThePlayingPreferenceRevokesPromotedNetworkWork() {
        ShitTokPreferencePreparation preparation = new ShitTokPreferencePreparation();
        preparation.select("all:chaos");
        preparation.playbackHealthy(true);
        assertTrue(preparation.start("all:women", Runnable::run, () -> true, job -> {
            preparation.select("all:women");
            assertTrue(job.allowed());
            preparation.select("all:chaos");
            assertFalse(job.allowed());
            return ShitTokPreferencePreparation.Outcome.DEFERRED;
        }, () -> {}));
    }

    @Test public void suspendedDiscoveryRetainsItsCursorAndSelectionReusesTheFetchedPage() throws Exception {
        AtomicBoolean allowed = new AtomicBoolean(true);
        ShitTokFilteredInventoryTest.Fake backend = new ShitTokFilteredInventoryTest.Fake() {
            @Override public OnlyHavenRepository.CategoryPage creators(ContentPreference mode, int offset)
                    throws Exception {
                allowed.set(false);
                return super.creators(mode, offset);
            }
            @Override public boolean available(int source) { return source == ShitTokFilteredInventory.HAVEN; }
        };
        ShitTokFilteredInventory inventory = new ShitTokFilteredInventory(new ShitTokFilteredInventoryTest.Direct(),
                () -> 0L, backend);
        ShitTokSourceMode sources = ShitTokSourceMode.ALL;
        ContentPreference mode = ContentPreference.WOMEN;
        assertTrue(inventory.starter(sources, mode, allowed::get).isEmpty());
        assertTrue(backend.mediaCalls.isEmpty());
        assertEquals(1, backend.offsets.size());

        allowed.set(true);
        List<NativeContentItem> warm = inventory.starter(sources, mode, allowed::get);
        assertEquals(1, warm.size());
        inventory.request(sources, mode, true, () -> {}, true);
        assertTrue(inventory.take(mode, item -> true, true).isEmpty());
        assertEquals(warm, inventory.starter(sources, mode, allowed::get));
        inventory.request(sources, mode, true, () -> {}, false);
        assertEquals(warm, inventory.take(mode));
        assertEquals(1, backend.offsets.size());
        assertEquals(Integer.valueOf(1), backend.mediaCalls.get(ShitTokFilteredInventory.HAVEN));
        assertFalse(inventory.matches(warm.get(0), ShitTokSourceMode.ONLY_FAP, mode));
        assertFalse(inventory.matches(warm.get(0), sources, ContentPreference.MEN));
    }

    @Test public void transStarterPublishesBeforeTheSecondVerifiedCohortIsRequested() {
        List<ContentPreference> discovery = new ArrayList<>();
        ShitTokFilteredInventoryTest.Fake backend = new ShitTokFilteredInventoryTest.Fake() {
            @Override public OnlyHavenRepository.CategoryPage creators(ContentPreference mode, int offset)
                    throws Exception {
                discovery.add(mode);
                return super.creators(mode, offset);
            }
            @Override public boolean available(int source) { return source == ShitTokFilteredInventory.HAVEN; }
        };
        ShitTokFilteredInventory inventory = new ShitTokFilteredInventory(new ShitTokFilteredInventoryTest.Direct(),
                () -> 0L, backend);
        inventory.request(ContentPreference.TRANS_WOMEN, true, () -> {});
        assertEquals(Collections.singletonList(ContentPreference.TRANS_WOMEN), discovery);
        assertEquals(1, inventory.take(ContentPreference.TRANS_WOMEN).size());
        inventory.request(ContentPreference.TRANS_WOMEN, true, () -> {});
        assertEquals(ContentPreference.TRANS_MEN, discovery.get(1));
        assertTrue(inventory.take(ContentPreference.TRANS_WOMEN).stream()
                .anyMatch(item -> item.searchQuery.startsWith(ContentPreference.TRANS_MEN.value)));
    }

    @Test public void foregroundAdmissionUsesWarmStreamAndExpiresItAfterOneMinute() {
        AtomicLong clock = new AtomicLong();
        AtomicInteger resolves = new AtomicInteger(), probes = new AtomicInteger();
        ShitTokOrientationAdmission admission = new ShitTokOrientationAdmission((stream, allowed) -> {
            probes.incrementAndGet();
            return .5f;
        }, clock::get);
        NativeContentItem item = ShitTokFilteredInventoryTest.clip("starter", 6, 1);
        List<NativeContentItem> candidates = Collections.singletonList(item);
        ShitTokOrientationAdmission.Resolver resolver = candidate -> {
            resolves.incrementAndGet();
            return new CrazyShitRepository.StreamInfo(item.url, "", "");
        };
        try {
            assertEquals(1, admission.preparePreferenceStarter(candidates, resolver, () -> true, false).items.size());
            admission.admit(candidates, resolver, () -> true, new Random(0), 1, true, null);
            assertEquals(1, resolves.get());
            assertEquals(1, probes.get());
            clock.set(60_000L);
            admission.admit(candidates, resolver, () -> true, new Random(0), 1, true, null);
            assertEquals(2, resolves.get());
            assertEquals(2, probes.get());
        } finally {
            admission.close();
        }
    }
}
