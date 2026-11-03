package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WhatsNewChangelogTest {
    @Test public void currentPublicVersionHasNoUnreleasedAnnouncement() {
        assertNull(WhatsNewChangelog.forVersion(6_000_002));
    }

    @Test public void firstSupportedReleaseHasAccurateBundledHighlights() {
        WhatsNewChangelog.Entry entry = WhatsNewChangelog.forVersion(6_000_003);
        assertNotNull(entry);
        assertEquals("6.0.3", entry.versionName);
        assertEquals(2, entry.highlights.size());
    }

    @Test public void unknownLaterVersionDoesNotReuseStaleNotes() {
        assertNull(WhatsNewChangelog.forVersion(6_000_004));
    }

    @Test public void existingUserUpgradingFromSixSeesWhatsNew() {
        assertTrue(WhatsNewChangelog.shouldShow(6_000_003, null, 1_000L, 9_000L, true));
    }

    @Test public void firstInstallDoesNotSeeAnnouncement() {
        assertFalse(WhatsNewChangelog.shouldShow(6_000_003, null, 9_000L, 9_000L, true));
    }

    @Test public void justInstalledClockSlopDoesNotLookLikeUpdate() {
        assertFalse(WhatsNewChangelog.shouldShow(6_000_003, null, 9_000L, 9_700L, true));
    }

    @Test public void missingPackageTimesDoesNotClaimUpgrade() {
        assertFalse(WhatsNewChangelog.shouldShow(6_000_003, null, 0L, 0L, true));
    }

    @Test public void acknowledgedVersionNeverRepeats() {
        assertFalse(WhatsNewChangelog.shouldShow(6_000_003, 6_000_003, 1_000L, 9_000L, true));
    }

    @Test public void higherVersionEligibleOnceNotesAdded() {
        assertTrue(WhatsNewChangelog.shouldShow(6_000_004, 6_000_003, 1_000L, 9_000L, true));
    }

    @Test public void downgradeDoesNotRepeat() {
        assertFalse(WhatsNewChangelog.shouldShow(6_000_003, 6_000_004, 1_000L, 9_000L, true));
    }

    @Test public void noNotesDoesNotShow() {
        assertFalse(WhatsNewChangelog.shouldShow(6_000_004, 6_000_003, 1_000L, 9_000L, false));
    }
}
