package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.exoplayer.ExoPlayer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w400dp-h800dp-xhdpi",
        shadows = { ShitTokInteractionUiTest.Account.class, ShitTokInteractionUiTest.Social.class })
public class ShitTokSpeedMuteTest {
    private View video(ShitTokAudioOwnershipTest.Fixture f) {
        return ReflectionHelpers.getField(f.selected, "playerView");
    }
    private long down(ShitTokAudioOwnershipTest.Fixture f) {
        long down = SystemClock.uptimeMillis();
        ShitTokInteractionUiTest.event(video(f), down, MotionEvent.ACTION_DOWN, 300, 400);
        return down;
    }
    private void waitForHold() { ShitTokInteractionUiTest.idle(ViewConfiguration.getLongPressTimeout() + 150L); }
    private void boosted(ShitTokAudioOwnershipTest.Fixture f) {
        assertEquals(2f, f.selectedPlayer.speed.speed, 0f);
        assertEquals(View.VISIBLE, ShitTokInteractionUiTest.root(f.selected)
                .findViewWithTag("shittok_speed_badge").getVisibility());
    }
    @Test public void deliberateHoldBoostsOnceAndReleaseRestoresExactParametersWithoutTap() {
        for (int end : new int[] {MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL}) {
            try (var f = ShitTokInteractionUiTest.fixture()) {
                f.selectedPlayer.speed = new PlaybackParameters(1.25f, .9f);
                int plays = f.selectedPlayer.plays;
                long down = down(f);
                ShitTokInteractionUiTest.idle(100);
                assertEquals(1.25f, f.selectedPlayer.speed.speed, 0f);
                ShitTokInteractionUiTest.event(video(f), down, MotionEvent.ACTION_MOVE, 301, 401);
                waitForHold(); boosted(f);
                waitForHold(); boosted(f);
                assertEquals(plays, f.selectedPlayer.plays);
                ShitTokInteractionUiTest.event(video(f), down, end, 301, 401);
                assertEquals(new PlaybackParameters(1.25f, .9f), f.selectedPlayer.speed);
                ShitTokInteractionUiTest.idle(400);
                assertTrue(f.selectedPlayer.readyToPlay);
                assertEquals(View.GONE, ShitTokInteractionUiTest.root(f.selected)
                        .findViewWithTag("shittok_speed_badge").getVisibility());
            }
        }
    }
    @Test public void verticalAndCreatorSwipeCancelPendingAndActiveHold() {
        for (boolean active : new boolean[] {false, true}) {
            for (boolean horizontal : new boolean[] {false, true}) {
                try (var f = ShitTokInteractionUiTest.fixture()) {
                    if (horizontal) {
                        NativeContentItem original = ReflectionHelpers.getField(f.selected, "item");
                        NativeContentItem creator = new NativeContentItem(NativeContentItem.KIND_MEDIA,
                                "Fixture creator", original.url, "", "", "", "", "OnlyHaven", "OnlyHaven");
                        ReflectionHelpers.setField(f.selected, "item", creator);
                    }
                    long down = down(f);
                    if (active) { waitForHold(); boosted(f); }
                    ShitTokInteractionUiTest.event(video(f), down, MotionEvent.ACTION_MOVE,
                            horizontal ? 160 : 300, horizontal ? 400 : 250);
                    waitForHold();
                    assertEquals(1f, f.selectedPlayer.speed.speed, 0f);
                    if (horizontal) assertTrue(ReflectionHelpers.getField(f.selected, "creatorSwipeTracking"));
                    ShitTokInteractionUiTest.event(video(f), down, MotionEvent.ACTION_CANCEL, 160, 250);
                }
            }
        }
    }
    @Test public void pausedVideoNeverBoostsOrStarts() {
        try (var f = ShitTokInteractionUiTest.fixture()) {
            ShitTokPlaybackOwner pausedOwner = ReflectionHelpers.getField(f.feed, "playbackOwner");
            String pausedUrl = ((NativeContentItem) ReflectionHelpers.getField(f.selected, "item")).url;
            ReflectionHelpers.setField(f.selected, "userPaused", true);
            pausedOwner.setUserPaused(pausedUrl, true);
            int plays = f.selectedPlayer.plays;
            long down = down(f); waitForHold();
            assertEquals(1f, f.selectedPlayer.speed.speed, 0f);
            ShitTokInteractionUiTest.event(video(f), down, MotionEvent.ACTION_UP, 300, 400);
            ShitTokInteractionUiTest.idle(400);
            assertEquals(plays, f.selectedPlayer.plays);
            assertFalse(f.selectedPlayer.readyToPlay);
        }
    }
    @Test public void multiTouchAndRealPinchCancelHold() {
        for (boolean active : new boolean[] {false, true}) {
            try (var f = ShitTokInteractionUiTest.fixture()) {
                View root = ShitTokInteractionUiTest.root(f.selected);
                long down = SystemClock.uptimeMillis();
                ShitTokInteractionUiTest.event(root, down, MotionEvent.ACTION_DOWN, 300, 400);
                if (active) { waitForHold(); boosted(f); }
                MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[2];
                MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[2];
                for (int i = 0; i < 2; i++) {
                    properties[i] = new MotionEvent.PointerProperties(); properties[i].id = i;
                    properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
                    coords[i] = new MotionEvent.PointerCoords();
                    coords[i].x = 300 + i * 100; coords[i].y = 400;
                }
                MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                        2, properties, coords, 0, 0, 1, 1, 0, 0, 0, 0);
                root.dispatchTouchEvent(event); event.recycle();
                waitForHold();
                assertEquals(1f, f.selectedPlayer.speed.speed, 0f);
                ShitTokInteractionUiTest.event(root, down, MotionEvent.ACTION_CANCEL, 300, 400);
            }
        }
    }
    @Test public void scrubberNeverArmsHoldAndTakingScrubOwnershipEndsBoost() {
        try (var f = ShitTokInteractionUiTest.fixture()) {
            SeekBar bar = ReflectionHelpers.getField(f.selected, "seekBar");
            long down = SystemClock.uptimeMillis();
            ShitTokInteractionUiTest.event(bar, down, MotionEvent.ACTION_DOWN, 20, 24);
            waitForHold(); assertEquals(1f, f.selectedPlayer.speed.speed, 0f);
            ShitTokInteractionUiTest.event(bar, down, MotionEvent.ACTION_CANCEL, 20, 24);
            down(f); waitForHold(); boosted(f);
            SeekBar.OnSeekBarChangeListener listener = ReflectionHelpers.getField(bar, "mOnSeekBarChangeListener");
            listener.onStartTrackingTouch(bar);
            assertEquals(1f, f.selectedPlayer.speed.speed, 0f);
            listener.onStopTrackingTouch(bar);
        }
    }
    @Test public void ownershipAndLifecycleBoundariesRestoreOutgoingPlayer() {
        for (String boundary : new String[]{"page", "background", "detach", "release", "source", "pause", "fullscreen", "clear"}) {
            try (var f = ShitTokInteractionUiTest.fixture()) {
                down(f); waitForHold(); boosted(f);
                ShitTokPlaybackOwner owner = ReflectionHelpers.getField(f.feed, "playbackOwner");
                String url = ((NativeContentItem) ReflectionHelpers.getField(f.selected, "item")).url;
                switch (boundary) {
                    case "page": owner.select("https://example.invalid/clip-1", null, url, true); break;
                    case "background": f.feed.onHostPause(); break;
                    case "detach": ReflectionHelpers.callInstanceMethod(f.selected, "detachLease"); break;
                    case "release": owner.release(); break;
                    case "source": owner.invalidateSource(url); break;
                    case "pause": owner.setUserPaused(url, true); break;
                    case "fullscreen": ReflectionHelpers.callInstanceMethod(f.feed, "resetTemporaryPinch"); break;
                    case "clear": ReflectionHelpers.callInstanceMethod(f.feed, "setClearDisplay", from(boolean.class, true)); break;
                }
                assertEquals(boundary, 1f, f.selectedPlayer.speed.speed, 0f);
                waitForHold(); assertEquals(boundary, 1f, f.selectedPlayer.speed.speed, 0f);
                assertEquals(1f, f.offscreenPlayer.speed.speed, 0f);
            }
        }
    }
    @Test public void muteTogglePersistsAndNewActiveLeaseGetsChosenVolumeAndIcon() {
        try (var f = ShitTokInteractionUiTest.fixture()) {
            TextView mute = ReflectionHelpers.getField(f.selected, "mute");
            assertEquals("Mute video", mute.getContentDescription());
            mute.performClick();
            assertTrue(ReflectionHelpers.getField(f.feed, "chaosMuted"));
            String prefs = ReflectionHelpers.getStaticField(ChaosFeedView.class, "PREFS");
            assertTrue(f.host.get().getSharedPreferences(prefs, Context.MODE_PRIVATE).getBoolean("muted", false));
            assertEquals(0f, f.selectedPlayer.volume, 0f);
            assertEquals("Unmute video", mute.getContentDescription());
            // A fresh owner reads the persisted choice before attaching its first active lease.
            ShitTokPlaybackOwner owner = new ShitTokPlaybackOwner(f.host.get(), new ShitTokPlaybackOwner.Callbacks() {
                @Override public void onLeaseChanged(String url) {}
                @Override public void onLeaseRetired(String url) {}
            });
            try {
                ShitTokAudioOwnershipTest.FakePlayer fresh = new ShitTokAudioOwnershipTest.FakePlayer();
                owner.setPlayerFactoryForTest(() -> fresh.player);
                owner.permission(true, f.host.get().getSharedPreferences(prefs, Context.MODE_PRIVATE).getBoolean("muted", false));
                String url = "https://example.invalid/fresh";
                owner.select(url, null, null, false);
                androidx.media3.ui.PlayerView view = new androidx.media3.ui.PlayerView(f.host.get());
                ExoPlayer attached = owner.attach(url, view);
                assertNotNull(attached);
                assertEquals(0f, attached.getVolume(), 0f);
                owner.source(url, new CrazyShitRepository.StreamInfo("https://example.invalid/new.mp4", url, "New"), owner.token(url));
                assertEquals(0f, attached.getVolume(), 0f);
                owner.setMuted(false);
                assertEquals(1f, attached.getVolume(), 0f);
            } finally { owner.close(); }
            mute.performClick();
            assertFalse(f.host.get().getSharedPreferences(prefs, Context.MODE_PRIVATE).getBoolean("muted", true));
            assertEquals(1f, f.selectedPlayer.volume, 0f);
            assertEquals("Mute video", mute.getContentDescription());
        }
    }
}
