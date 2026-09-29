package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StartupWizardPolicyTest {
    @Test
    public void freshInstallShowsWizard() {
        assertTrue(StartupWizardPolicy.shouldShowFromState(
                false,
                false,
                1_000L,
                1_000L,
                false
        ));
    }

    @Test
    public void inPlaceUpgradeDoesNotShowWizard() {
        assertFalse(StartupWizardPolicy.shouldShowFromState(
                false,
                false,
                1_000L,
                10_000L,
                false
        ));
    }

    @Test
    public void completedWizardNeverShowsAgain() {
        assertFalse(StartupWizardPolicy.shouldShowFromState(
                true,
                false,
                1_000L,
                1_000L,
                false
        ));
    }

    @Test
    public void legacyAcceptedInstallDoesNotGetRetroactiveWizard() {
        assertFalse(StartupWizardPolicy.shouldShowFromState(
                false,
                false,
                1_000L,
                1_000L,
                true
        ));
    }

    @Test
    public void clearedStorageShowsWizardEvenWhenPackageLooksUpgraded() {
        assertTrue(StartupWizardPolicy.shouldShowFromState(
                false,
                true,
                1_000L,
                10_000L,
                false
        ));
    }

    @Test
    public void unknownInstallMetadataFailsSafeToWizard() {
        assertTrue(StartupWizardPolicy.shouldShowFromState(
                false,
                false,
                0L,
                0L,
                false
        ));
    }
}
