package com.webapp.crazyshit;

import static org.junit.Assert.*;
import org.junit.Test;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ShitTokOrientationProbeBudgetTest {
    @Test public void playbackGateCancelsWorkDuringAnActiveProbe() throws Exception {
        AtomicBoolean allowed = new AtomicBoolean(true);
        ShitTokOrientationProbe.Budget budget = new ShitTokOrientationProbe.Budget(allowed::get, () -> 0L);
        budget.check();
        allowed.set(false);
        try { budget.check(); fail("Expected cancellation"); }
        catch (ShitTokOrientationProbe.DeferredIOException expected) {}
    }

    @Test public void timeoutAppliesAcrossSeeksAndRequests() throws Exception {
        AtomicLong clock = new AtomicLong();
        ShitTokOrientationProbe.Budget budget = new ShitTokOrientationProbe.Budget(() -> true, clock::get);
        budget.check();
        clock.set(ShitTokOrientationProbe.MAX_MILLIS * 1_000_000L);
        try { budget.check(); fail("Expected deadline"); }
        catch (IOException expected) {}
    }

    @Test public void ignoredRangeSkipIsCountedEvenBeforeAReadReturns() {
        ShitTokOrientationProbe.Budget budget = new ShitTokOrientationProbe.Budget(() -> true, () -> 0L);
        try {
            budget.onBytesTransferred(null, null, true, (int) ShitTokOrientationProbe.MAX_BYTES);
            fail("Expected byte ceiling during ignored-Range skip");
        } catch (RuntimeException expected) {
            assertTrue(expected.getCause() instanceof IOException);
        }
    }

    @Test public void cachedAndNetworkBytesShareOneCeiling() throws Exception {
        ShitTokOrientationProbe.Budget budget = new ShitTokOrientationProbe.Budget(() -> true, () -> 0L);
        budget.onBytesTransferred(null, null, true, (int) ShitTokOrientationProbe.MAX_BYTES / 2);
        budget.readCompleted((int) ShitTokOrientationProbe.MAX_BYTES / 2 - 1, budget.networkBytes);
        budget.check();
        try { budget.readCompleted(1, budget.networkBytes); fail("Expected combined byte ceiling"); }
        catch (IOException expected) {}
    }

    @Test public void networkBytesDeliveredToExtractorAreChargedOnceAndSkipStillCounts() throws Exception {
        ShitTokOrientationProbe.Budget budget = new ShitTokOrientationProbe.Budget(() -> true, () -> 0L);
        int count = (int) ShitTokOrientationProbe.MAX_BYTES * 3 / 4;
        long before = budget.networkBytes;
        budget.onBytesTransferred(null, null, true, count);
        budget.readCompleted(count, before);
        assertEquals(count, budget.chargedBytes);
        assertEquals(count, budget.readBytes);
        // Network-only bytes from a server ignoring Range are not delivered to read().
        try {
            budget.onBytesTransferred(null, null, true, (int) ShitTokOrientationProbe.MAX_BYTES - count);
            fail("Expected union byte ceiling including ignored-Range skip");
        } catch (RuntimeException expected) {
            assertTrue(expected.getCause() instanceof ShitTokOrientationProbe.MetadataBudgetIOException);
        }
    }
}
