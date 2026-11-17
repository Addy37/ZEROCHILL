package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Update bytes must be complete before handing them to Android's installer. */
public class AppUpdaterInstallerTest {
    @Test public void rejectsMissingOrIncompleteApks() {
        assertFalse(AppUpdater.validDownloadedSize(0, 0));
        assertFalse(AppUpdater.validDownloadedSize(1, 20));
        assertFalse(AppUpdater.validDownloadedSize(21, 20));
    }

    @Test public void acceptsExactOrUnknownLengthWhenNonEmpty() {
        assertTrue(AppUpdater.validDownloadedSize(23222568, 23222568));
        assertTrue(AppUpdater.validDownloadedSize(1024, -1));
    }
}
