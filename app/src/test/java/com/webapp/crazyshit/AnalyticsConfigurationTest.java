package com.webapp.crazyshit;

import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class AnalyticsConfigurationTest {
    @Test public void missingEndpointDoesNotInitializeOrQueueEvents() throws Exception {
        assumeTrue(BuildConfig.ANALYTICS_ENDPOINT.trim().isEmpty());
        Field initialized = AnalyticsTracker.class.getDeclaredField("INITIALIZED");
        initialized.setAccessible(true);
        Field recent = AnalyticsTracker.class.getDeclaredField("RECENT");
        recent.setAccessible(true);
        int before = ((Map<?, ?>) recent.get(null)).size();
        AnalyticsTracker.initialize(RuntimeEnvironment.getApplication());
        AnalyticsTracker.trackSource(RuntimeEnvironment.getApplication(), "fapello");
        assertFalse(((AtomicBoolean) initialized.get(null)).get());
        assertEquals(before, ((Map<?, ?>) recent.get(null)).size());
    }
}
