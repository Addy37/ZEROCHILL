package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ZeroChillIdUpgradeAnnouncementTest {
    @Test
    public void existingUserOn43SeesAnnouncement() {
        assertTrue(ZeroChillIdUpgradeAnnouncement.shouldShowFromState(
                4_003_000,
                false,
                true,
                false,
                1_000L,
                10_000L
        ));
    }

    @Test
    public void freshInstallDoesNotSeeUpgradeAnnouncement() {
        assertFalse(ZeroChillIdUpgradeAnnouncement.shouldShowFromState(
                4_003_000,
                false,
                true,
                false,
                10_000L,
                10_000L
        ));
    }

    @Test
    public void signedInUserDoesNotNeedAnnouncement() {
        assertFalse(ZeroChillIdUpgradeAnnouncement.shouldShowFromState(
                4_003_000,
                false,
                true,
                true,
                1_000L,
                10_000L
        ));
    }

    @Test
    public void dismissedAnnouncementNeverShowsAgain() {
        assertFalse(ZeroChillIdUpgradeAnnouncement.shouldShowFromState(
                4_003_000,
                true,
                true,
                false,
                1_000L,
                10_000L
        ));
    }

    @Test
    public void olderVersionDoesNotShow43Announcement() {
        assertFalse(ZeroChillIdUpgradeAnnouncement.shouldShowFromState(
                4_002_001,
                false,
                true,
                false,
                1_000L,
                10_000L
        ));
    }

    @Test
    public void unavailableAccountBackendDoesNotShowAnnouncement() {
        assertFalse(ZeroChillIdUpgradeAnnouncement.shouldShowFromState(
                4_003_000,
                false,
                false,
                false,
                1_000L,
                10_000L
        ));
    }
}
