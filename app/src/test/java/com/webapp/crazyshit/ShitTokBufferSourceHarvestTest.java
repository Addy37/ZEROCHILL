package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokBufferSourceHarvestTest {
    @Test public void starvationStopsNewHiddenPageWarmupAndRecoveryReopensIt() {
        ShitShowTapSource source = new ShitShowTapSource();
        source.setPlaybackProtected(true);
        try (org.robolectric.android.controller.ActivityController<Activity> host =
                Robolectric.buildActivity(Activity.class).setup()) {
            source.prewarm(host.get());
            assertFalse(ReflectionHelpers.getField(source, "warming"));
            source.setPlaybackProtected(false);
            assertTrue(ReflectionHelpers.getField(source, "warming"));
            // Suppress queued startHarvest before fixture disposal; no page requests needed.
            source.setPlaybackProtected(true);
        }
    }
}
