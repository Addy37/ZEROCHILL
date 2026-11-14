package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ZeroChillAccountMigrationNoticeTest {
    @Test public void existingSignedOutUserSeesMigrationPromptAfterUpgrade() {
        assertTrue(ZeroChillAccountMigrationNotice.shouldShowFromState(
                true, false, true, false, 1_000L, 20_000L));
    }

    @Test public void alreadyMigratedUserDoesNotGetPrompt() {
        assertFalse(ZeroChillAccountMigrationNotice.shouldShowFromState(
                true, false, true, true, 1_000L, 20_000L));
    }

    @Test public void newInstallDoesNotGetMigrationPrompt() {
        assertFalse(ZeroChillAccountMigrationNotice.shouldShowFromState(
                true, false, true, false, 20_000L, 20_000L));
    }

    @Test public void dismissedPromptNeverRepeats() {
        assertFalse(ZeroChillAccountMigrationNotice.shouldShowFromState(
                true, true, true, false, 1_000L, 20_000L));
    }

    @Test public void oldBackendDoesNotGetPrompt() {
        assertFalse(ZeroChillAccountMigrationNotice.shouldShowFromState(
                false, false, true, false, 1_000L, 20_000L));
    }

    @Test public void missingBackendDoesNotGetPrompt() {
        assertFalse(ZeroChillAccountMigrationNotice.shouldShowFromState(
                true, false, false, false, 1_000L, 20_000L));
    }

    @Test public void uncertainInstallMetadataDoesNotGetPrompt() {
        assertFalse(ZeroChillAccountMigrationNotice.shouldShowFromState(
                true, false, true, false, 0L, 20_000L));
        assertFalse(ZeroChillAccountMigrationNotice.shouldShowFromState(
                true, false, true, false, 20_000L, 20_500L));
    }
}
