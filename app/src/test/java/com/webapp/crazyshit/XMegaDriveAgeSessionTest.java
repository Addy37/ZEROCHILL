package com.webapp.crazyshit;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class XMegaDriveAgeSessionTest {
    @Test public void existingAcceptedInstallGetsOneRepairWhenMarkerMissing() {
        assertTrue(XMegaDriveAgeSession.shouldShowRepairFromState(true, false, false, false));
    }

    @Test public void freshOrHandledOrAlreadyReadyInstallSkipsRepair() {
        assertFalse(XMegaDriveAgeSession.shouldShowRepairFromState(false, false, false, false));
        assertFalse(XMegaDriveAgeSession.shouldShowRepairFromState(true, true, false, false));
        assertFalse(XMegaDriveAgeSession.shouldShowRepairFromState(true, false, true, false));
        assertFalse(XMegaDriveAgeSession.shouldShowRepairFromState(true, false, false, true));
    }
}
