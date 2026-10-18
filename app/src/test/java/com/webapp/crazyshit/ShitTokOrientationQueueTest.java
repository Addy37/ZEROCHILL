package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokOrientationQueueTest {
    private NativeContentItem item(String name, float aspect) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, name,
                "https://fixture.invalid/" + name, "", "", "Shit Show", "", "", "", 0L, aspect);
    }
    @Test public void visibleQueueOnlyContainsVerifiedEligibleUniqueNonHiddenClips() {
        try (ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup()) {
            ChaosFeedView feed = new ChaosFeedView(host.get(), item -> {}, false);
            Set<String> hidden = ReflectionHelpers.getField(feed, "hiddenUrls");
            hidden.add(item("hidden", .5f).url);
            ReflectionHelpers.callInstanceMethod(feed, "appendUnique", from(java.util.List.class,
                    Arrays.asList(item("portrait", .5f), item("square", 1f),
                            item("landscape", 1.001f), item("unknown", 0f),
                            item("portrait", .5f), item("hidden", .5f))));
            ArrayList<NativeContentItem> queue = ReflectionHelpers.getField(feed, "items");
            assertEquals(2, queue.size());
            assertEquals("portrait", queue.get(0).title);
            assertEquals("square", queue.get(1).title);
            feed.close();
        }
    }
    @Test public void staleWarmNotificationCannotRestartDeferredProbesAfterLeavingFeed() {
        try (ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture()) {
            ReflectionHelpers.setField(f.feed, "verifiedQueueEmpty", false);
            ReflectionHelpers.setField(f.feed, "orientationWorkAllowed", true);
            ShitTokPlaybackOwner owner = ReflectionHelpers.getField(f.feed, "playbackOwner");
            owner.permission(false, false);
            ReflectionHelpers.setField(f.feed, "active", false);
            boolean allowed = ReflectionHelpers.callInstanceMethod(f.feed, "mayVerifyOrientation");
            assertFalse(allowed);
            assertFalse(ChaosFeedView.shouldRefillVerifiedPool(2, 0, false, true, allowed));
            ReflectionHelpers.setField(f.feed, "active", true);
            owner.permission(true, false);
            allowed = ReflectionHelpers.callInstanceMethod(f.feed, "mayVerifyOrientation");
            assertTrue(allowed);
            assertTrue(ChaosFeedView.shouldRefillVerifiedPool(2, 0, false, true, allowed));
        }
    }
    @Test public void rejectedBatchesRefillButStopAfterFourDryLoads() {
        for (int dry = 0; dry < 4; dry++)
            assertTrue(ChaosFeedView.shouldRefillVerifiedPool(0, dry, false, false, true));
        assertFalse(ChaosFeedView.shouldRefillVerifiedPool(0, 4, false, false, true));
        assertFalse(ChaosFeedView.shouldRefillVerifiedPool(14, 0, false, false, true));
        assertTrue(ChaosFeedView.shouldRefillVerifiedPool(5, 0, false, false, true));
    }
    @Test public void starvedProbesWaitAndResumeInsteadOfSpinningOrLosingPendingCandidates() {
        assertFalse(ChaosFeedView.shouldRefillVerifiedPool(2, 0, true, true, false));
        assertTrue(ChaosFeedView.shouldRefillVerifiedPool(2, 0, true, true, true));
        assertFalse(ChaosFeedView.shouldRefillVerifiedPool(24, 0, false, false, true));
    }
}
