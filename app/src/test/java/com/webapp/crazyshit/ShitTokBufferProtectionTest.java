package com.webapp.crazyshit;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShitTokBufferProtectionTest {
    private ShitTokBufferProtection session() {
        ShitTokBufferProtection policy = new ShitTokBufferProtection();
        policy.select("clip");
        return policy;
    }
    private void ready(ShitTokBufferProtection p, long now, long position) {
        p.observe(now, 3, true, true, true, false, false, position, 10_000L);
    }
    private void stall(ShitTokBufferProtection p, long now) {
        p.observe(now, 2, false, true, true, false, false, 1_000L, 100L);
    }
    @Test public void startupDoesNotActivateProtectionOrChangeRecoveryTarget() {
        ShitTokBufferProtection p = session();
        stall(p, 0L); stall(p, 1000L);
        assertFalse(p.isProtected()); assertEquals(0, p.rebufferCount());
        assertEquals(0L, p.recoveryTargetMs());
        assertTrue(p.summary(1000).contains("startupBuffers=1"));
    }
    @Test public void firstRealRebufferProtectsAndCountsOnlyOneStallEpisode() {
        ShitTokBufferProtection p = session();
        ready(p, 0, 0); ready(p, 1000, 1000);
        stall(p, 1500); stall(p, 2000);
        assertTrue(p.isProtected()); assertEquals(1, p.rebufferCount());
        assertEquals(5000L, p.recoveryTargetMs());
        ready(p, 3000, 1000);
        assertTrue(p.summary(3000).contains("rebufferMs=1500"));
        assertTrue(p.summary(3000).contains("playedBeforeFirstRebufferMs=1000"));
    }
    @Test public void rapidRebuffersEscalateAndRemainCapped() {
        ShitTokBufferProtection p = session();
        ready(p, 0, 0); stall(p, 1000); ready(p, 2000, 1000);
        stall(p, 3000); assertEquals(8000, p.recoveryTargetMs());
        ready(p, 4000, 2000); stall(p, 5000); assertEquals(12000, p.recoveryTargetMs());
        ready(p, 6000, 3000); stall(p, 7000); assertEquals(12000, p.recoveryTargetMs());
    }
    @Test public void steadyRecoveryRestoresNormalWorkWithoutCountingPausedTime() {
        ShitTokBufferProtection p = session();
        ready(p, 0, 0); stall(p, 1000); ready(p, 2000, 1000);
        p.observe(3000, 3, false, false, true, true, false, 1000, 10000);
        ready(p, 60000, 1000); assertTrue(p.isProtected());
        for (int i = 1; i <= 15; i++) ready(p, 60000 + i * 1000L, 1000 + i * 1000L);
        assertFalse(p.isProtected()); assertEquals(0L, p.recoveryTargetMs());
    }
    @Test public void selectedItemChangeResetsEscalationAndDiagnostics() {
        ShitTokBufferProtection p = session(); ready(p, 0, 0); stall(p, 1000);
        p.select("next"); assertFalse(p.isProtected()); assertEquals(0, p.rebufferCount());
        stall(p, 2000); assertEquals(0, p.rebufferCount());
    }
    @Test public void userPausePagingBackgroundAndErrorAreNotNetworkStalls() {
        ShitTokBufferProtection p = session(); ready(p, 0, 0);
        p.observe(100, 2, false, false, true, true, false, 0, 0);
        p.observe(200, 2, false, true, false, false, false, 0, 0);
        p.observe(300, 1, false, true, true, false, true, 0, 0);
        assertEquals(0, p.rebufferCount()); assertFalse(p.isProtected());
    }
    @Test public void shortClipCompletionReleasesProtectionSoLastPageCanRefill() {
        ShitTokBufferProtection p = session(); ready(p, 0, 0); stall(p, 1000);
        ready(p, 3000, 1000);
        p.observe(4000, 4, false, true, true, false, false, 2000, 0);
        assertFalse(p.isProtected()); assertEquals(0, p.recoveryTargetMs());
        assertEquals(1, p.rebufferCount());
    }
    @Test public void seekingWaitsForSuccessfulPlaybackBeforeCountingRebuffers() {
        ShitTokBufferProtection p = session(); ready(p, 0, 0);
        p.interrupt(1000, true); stall(p, 1100);
        assertEquals(0, p.rebufferCount()); ready(p, 2000, 50000);
        stall(p, 3000); assertEquals(1, p.rebufferCount());
    }
    @Test public void adaptiveResumeKeepsDefaultMinimumAndBoundsAdditionalDelay() {
        assertFalse(ShitTokBufferProtection.mayResume(false, 9_000_000, 1f, 5_000_000, 12000, 1000));
        assertFalse(ShitTokBufferProtection.mayResume(true, 2_000_000, 1f, 5_000_000, 12000, 1000));
        assertTrue(ShitTokBufferProtection.mayResume(true, 5_000_000, 1f, 5_000_000, 12000, 1000));
        assertTrue(ShitTokBufferProtection.mayResume(true, 2_000_000, 1f, 5_000_000, 12000, 12000));
        assertFalse(ShitTokBufferProtection.mayResume(true, 5_000_000, 2f, 5_000_000, 12000, 1000));
    }
    @Test public void separateDistantStallsDoNotEscalate() {
        ShitTokBufferProtection p = session(); ready(p, 0, 0); stall(p, 1000);
        ready(p, 2000, 1000); stall(p, 40000);
        assertEquals(5000, p.recoveryTargetMs());
    }
    @Test public void stalledWallTimeRemainsBoundedAndReportsAnOpenStall() {
        ShitTokBufferProtection p = session(); ready(p, 0, 0); stall(p, 1000);
        assertTrue(p.summary(4100).contains("longestRebufferMs=3100"));
        p.interrupt(5000, false);
        assertTrue(p.summary(100000).contains("rebufferMs=4000"));
    }
}
