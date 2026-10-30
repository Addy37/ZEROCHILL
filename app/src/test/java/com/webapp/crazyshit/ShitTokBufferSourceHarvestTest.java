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
            source.prewarm(host.get());
            assertTrue(ReflectionHelpers.getField(source, "warming"));
            // Suppress queued startHarvest before fixture disposal; no page requests needed.
            source.setPlaybackProtected(true);
        }
    }
    @Test public void pausedHarvestRetainsReadyMetadataAndAbandonedUnplayedStoryCanReturn() {
        ShitShowTapSource source = new ShitShowTapSource();
        String story = "{\"media\":\"https://cdn.fixture.invalid/unplayed.mp4\",\"title\":\"Fixture\"}";
        ReflectionHelpers.callInstanceMethod(source, "acceptStory", ReflectionHelpers.ClassParameter.from(String.class, story));
        source.stop();
        java.util.List<NativeContentItem> retained = source.takeReadyBatch(1);
        assertEquals(1, retained.size());
        source.releaseUnplayed(retained.get(0).url);
        ReflectionHelpers.callInstanceMethod(source, "acceptStory", ReflectionHelpers.ClassParameter.from(String.class, story));
        assertEquals(1, source.takeReadyBatch(1).size());
    }
    @Test public void stoppedHarvestRejectsItsOldJavascriptBridge() throws Exception {
        ShitShowTapSource source = new ShitShowTapSource();
        Class<?> bridgeClass = java.util.Arrays.stream(ShitShowTapSource.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("Bridge")).findFirst().get();
        java.lang.reflect.Constructor<?> constructor = bridgeClass.getDeclaredConstructor(ShitShowTapSource.class, long.class);
        constructor.setAccessible(true);
        Object bridge = constructor.newInstance(source, 0L);
        source.stop();
        java.lang.reflect.Method callback = bridgeClass.getDeclaredMethod("onStory", String.class);
        callback.setAccessible(true);
        callback.invoke(bridge, "{\"media\":\"https://cdn.fixture.invalid/stale.mp4\"}");
        assertTrue(source.takeReadyBatch(10).isEmpty());
    }

}
