package com.webapp.crazyshit;

import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.widget.ImageView;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 35, qualifiers = "w400dp-h800dp-xhdpi")
public class SearchShitTokPauseTest {
    private static final String URL = "https://example.invalid/clip-0";
    private ImageView frame(ChaosFeedView feed) { return ReflectionHelpers.getField(feed, "searchVideoFrame"); }
    private void attach(ShitTokAudioOwnershipTest.Fixture f) {
        ReflectionHelpers.callInstanceMethod(f.selected, "attachLease");
    }
    private ShitTokPlaybackOwner owner(ShitTokAudioOwnershipTest.Fixture f) {
        return ReflectionHelpers.getField(f.feed, "playbackOwner");
    }
    private ShitTokAudioOwnershipTest.FakePlayer resume(ShitTokAudioOwnershipTest.Fixture f) {
        var player = new ShitTokAudioOwnershipTest.FakePlayer();
        owner(f).setPlayerFactoryForTest(() -> player.player);
        f.feed.onHostResume();
        return player;
    }

    @Test public void repeatedSearchReusesOneBoundedFrameAndKeepsManualPauseWhileReleasingPlayers() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            attach(f); owner(f).setUserPaused(URL, true);
            f.feed.onSearchOpened();
            ImageView frozen = frame(f.feed);
            assertNotNull(frozen);
            assertTrue(((BitmapDrawable) frozen.getDrawable()).getBitmap().getWidth() <= 720);
            for (int i = 0; i < 3; i++) {
                f.feed.onSearchOpened();
                assertSame(frozen, frame(f.feed));
                assertNull(owner(f).player(URL));
                assertFalse(f.selectedPlayer.readyToPlay);
                assertEquals(0f, f.selectedPlayer.volume, 0f);
                assertFalse(f.offscreenPlayer.readyToPlay);
                assertTrue(owner(f).isUserPaused(URL));
            }
            var resumed = resume(f);
            assertTrue(owner(f).isUserPaused(URL));
            assertFalse(resumed.readyToPlay);
            assertEquals(12_000, resumed.sourceStartPosition);
            assertSame(frozen, frame(f.feed)); // Wait for a new rendered frame, not merely READY.
        }
    }

    @Test public void firstFreshFrameRetiresSnapshotAndActualBackgroundKeepsEverythingSilent() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            attach(f); f.feed.onSearchOpened();
            ImageView frozen = frame(f.feed);
            f.feed.onHostPause();
            assertSame(frozen, frame(f.feed));
            assertNull(owner(f).player(URL));
            var resumed = resume(f);
            assertTrue(resumed.readyToPlay);
            assertEquals(12_000, resumed.sourceStartPosition);
            Player.Listener listener = ReflectionHelpers.getField(f.selected, "uiPlayerListener");
            assertNotNull(listener);
            listener.onRenderedFirstFrame();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));
            assertNull(frame(f.feed));
            assertNull(frozen.getParent()); assertNull(frozen.getDrawable());
        }
    }

    @Test public void mutedVideoReturnsPlayingWithoutRestoringAudio() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            ReflectionHelpers.setField(f.feed, "chaosMuted", true);
            attach(f); f.feed.onSearchOpened();
            var resumed = resume(f);
            assertTrue(resumed.readyToPlay);
            assertEquals(0f, resumed.volume, 0f);
            assertEquals(12_000, resumed.sourceStartPosition);
        }
    }

    @Test public void nativeSearchEntryKeepsTheSnapshotAcrossRealActivityPause() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
            context.getSharedPreferences("app_prefs", 0).edit().putBoolean("access_notice_2_8_3_accepted", true)
                    .putBoolean("immersive_motion_enabled", false).commit();
            var host = org.robolectric.Robolectric.buildActivity(NativeMainActivity.class).create().start().resume().visible();
            try {
                MainPagerAdapter adapter = ReflectionHelpers.getField(host.get(), "primaryPagerAdapter");
                ChaosFeedView previous = ReflectionHelpers.getField(adapter, "chaosView"); previous.close();
                ReflectionHelpers.setField(adapter, "chaosView", f.feed); attach(f);
                host.get().openContextualSearch();
                ImageView frozen = frame(f.feed); assertNotNull(frozen);
                assertNull(owner(f).player(URL));
                host.pause();
                assertSame(frozen, frame(f.feed));
                assertNull(owner(f).player(URL));
            } finally { host.stop().destroy(); }
        }
    }

    @Test public void leavingOrClosingShitTokReleasesTheFrozenFrame() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            attach(f); f.feed.onSearchOpened(); ImageView frozen = frame(f.feed); assertNotNull(frozen);
            f.feed.setActive(false);
            assertNull(frame(f.feed)); assertNull(frozen.getDrawable());
            assertFalse(f.selectedPlayer.readyToPlay);
        }
    }
}
