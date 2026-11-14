package com.addy37.crazyshitadmin;

import org.junit.Test;

import static org.junit.Assert.*;

/** The admin APK must not silently reconnect to the retired Supabase project. */
public class NewZerochillAdminBackendTest {
    private static final String PROJECT = "https://aavjpykssueavbseqdlf.supabase.co";

    @Test public void adminRequestsUseOnlyNewProject() {
        assertTrue(BuildConfig.ADMIN_NEW_ZEROCHILL);
        assertEquals(PROJECT + "/functions/v1/feedback-admin", BuildConfig.ADMIN_FEEDBACK_ENDPOINT);
        assertEquals(PROJECT + "/functions/v1/app-config-admin", BuildConfig.ADMIN_SOURCE_CONFIG_ENDPOINT);
    }

    @Test public void existingAdminAppCanBeUpgraded() {
        assertEquals("com.addy37.crazyshitadmin", BuildConfig.APPLICATION_ID);
        assertTrue(BuildConfig.VERSION_CODE > 12);
    }
}
