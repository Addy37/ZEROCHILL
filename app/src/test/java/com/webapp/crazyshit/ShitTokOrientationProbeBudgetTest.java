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
        budget.readBytes = ShitTokOrientationProbe.MAX_BYTES / 2;
        budget.networkBytes = ShitTokOrientationProbe.MAX_BYTES / 2 - 1;
        budget.check();
        budget.networkBytes++;
        try { budget.check(); fail("Expected combined byte ceiling"); }
        catch (IOException expected) {}
    }
}
