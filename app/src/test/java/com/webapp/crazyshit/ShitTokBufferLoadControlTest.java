package com.webapp.crazyshit;

import android.app.Application;
import android.os.SystemClock;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Timeline;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.SinglePeriodTimeline;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokBufferLoadControlTest {
    private LoadControl.Parameters sample(String id, long bufferedUs, boolean rebuffering) {
        Timeline timeline = new SinglePeriodTimeline(60_000_000L, true, false, false,
                null, new MediaItem.Builder().setMediaId(id).setUri("https://fixture.invalid/video.mp4").build());
        return new LoadControl.Parameters(PlayerId.UNSET, timeline,
                new MediaSource.MediaPeriodId(timeline.getUidOfPeriod(0)), 0L, bufferedUs,
                1f, true, rebuffering, C.TIME_UNSET, C.TIME_UNSET);
    }
    @Test public void selectedLoadingKeepsPriorityWhileNeighborLoadingStopsAndReturns() {
        try (ShitTokPlayerPoolTest.Harness h = new ShitTokPlayerPoolTest.Harness()) {
            LoadControl control = ReflectionHelpers.getField(h.owner, "control");
            control.onPrepared(PlayerId.UNSET);
            ReflectionHelpers.setField(control, "currentId", "current");
            ReflectionHelpers.setField(control, "currentNeedsBuffer", false);
            ReflectionHelpers.setField(control, "starving", true);
            assertTrue(control.shouldContinueLoading(sample("current", 0, true)));
            assertFalse(control.shouldContinueLoading(sample("neighbor", 0, false)));
            ReflectionHelpers.setField(control, "starving", false);
            assertTrue(control.shouldContinueLoading(sample("neighbor", 0, false)));
            control.onReleased(PlayerId.UNSET);
        }
    }
    @Test public void continuityLoadingHasShortCapAndCannotCompeteWithBufferingCurrent() {
        try (ShitTokPlayerPoolTest.Harness h = new ShitTokPlayerPoolTest.Harness()) {
            LoadControl control = ReflectionHelpers.getField(h.owner, "control");
            control.onPrepared(PlayerId.UNSET);
            ReflectionHelpers.setField(control, "currentId", "current");
            ReflectionHelpers.setField(control, "continuityId", "next");
            ReflectionHelpers.setField(control, "starving", true);
            ReflectionHelpers.setField(control, "currentNeedsBuffer", true);
            assertTrue(control.shouldContinueLoading(sample("current", 0, true)));
            assertFalse(control.shouldContinueLoading(sample("next", 0, false)));
            ReflectionHelpers.setField(control, "currentNeedsBuffer", false);
            assertTrue(control.shouldContinueLoading(sample("next", 0, false)));
            assertFalse(control.shouldContinueLoading(sample("next", 750_000L, false)));
            assertFalse(control.shouldContinueLoading(sample("previous", 0, false)));
            ReflectionHelpers.setField(control, "networkPaused", true);
            assertFalse(control.shouldContinueLoading(sample("next", 0, false)));
            control.onReleased(PlayerId.UNSET);
        }
    }
    @Test public void realMedia3ResumeHookKeepsStartupFastAndBoundsRecoveryWait() {
        try (ShitTokPlayerPoolTest.Harness h = new ShitTokPlayerPoolTest.Harness()) {
            LoadControl control = ReflectionHelpers.getField(h.owner, "control");
            control.onPrepared(PlayerId.UNSET);
            ReflectionHelpers.setField(control, "currentId", "current");
            ReflectionHelpers.setField(control, "recoveryTargetUs", 5_000_000L);
            ReflectionHelpers.setField(control, "recoveryDeadlineMs", SystemClock.elapsedRealtime() + 12_000L);
            assertTrue(control.shouldStartPlayback(sample("current", 1_500_000L, false)));
            assertFalse(control.shouldStartPlayback(sample("current", 3_000_000L, true)));
            assertTrue(control.shouldStartPlayback(sample("current", 5_000_000L, true)));
            assertTrue(control.shouldStartPlayback(sample("neighbor", 3_000_000L, true)));
            ShadowSystemClock.advanceBy(Duration.ofSeconds(13));
            assertTrue(control.shouldStartPlayback(sample("current", 3_000_000L, true)));
            assertFalse(control.shouldStartPlayback(sample("current", 10_000L, true)));
            control.onReleased(PlayerId.UNSET);
        }
    }
}
