package com.addy37.crazyshitadmin;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** Invoked explicitly before and after adb install -r by CI. Uses a synthetic token only. */
public final class TokenUpgradeTest {
    private static final String FIXTURE = "synthetic-admin-token-upgrade-check-only";
    @Test public void seed() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SecureTokenStore.save(context, FIXTURE);
        assertEquals(FIXTURE, SecureTokenStore.read(context));
    }
    @Test public void verify() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals(FIXTURE, SecureTokenStore.read(context));
        SecureTokenStore.clear(context);
    }
}
