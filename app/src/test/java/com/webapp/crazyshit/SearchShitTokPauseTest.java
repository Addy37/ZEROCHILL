package com.webapp.crazyshit;

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
    @Test public void repeatedSearchKeepsAttachedLeaseSilentAndPreservesManualPauseAndPosition() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            ShitTokPlaybackOwner owner = ReflectionHelpers.getField(f.feed, "playbackOwner");
            String url = "https://example.invalid/clip-0";
            ReflectionHelpers.callInstanceMethod(f.selected, "attachLease");
            PlayerView surface = ReflectionHelpers.getField(f.selected, "playerView");
            assertSame(f.selectedPlayer.player, surface.getPlayer());
            int token = owner.token(url);
            owner.setUserPaused(url, true);
            for (int i = 0; i < 3; i++) {
                f.feed.onSearchOpened();
                assertSame(f.selectedPlayer.player, surface.getPlayer());
                assertSame(f.selectedPlayer.player, owner.player(url));
                assertEquals(token, owner.token(url));
                assertFalse(f.selectedPlayer.readyToPlay);
                assertEquals(0f, f.selectedPlayer.volume, 0f);
                assertFalse(f.offscreenPlayer.readyToPlay);
                assertEquals(0f, f.offscreenPlayer.volume, 0f);
                ReflectionHelpers.callInstanceMethod(f.selected, "playOwnedPlayer");
                assertFalse(f.selectedPlayer.readyToPlay);
                assertTrue(owner.isUserPaused(url));
            }
            f.feed.onHostResume();
            assertSame(f.selectedPlayer.player, surface.getPlayer());
            assertEquals(token, owner.token(url));
            assertTrue(owner.isUserPaused(url));
            assertFalse(f.selectedPlayer.readyToPlay);
            assertEquals(0, f.selectedPlayer.seeks);
        }
    }

    @Test public void closingSearchResumesOnlyCurrentUnpausedLeaseAndActualBackgroundReleasesIt() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            ShitTokPlaybackOwner owner = ReflectionHelpers.getField(f.feed, "playbackOwner");
            String url = "https://example.invalid/clip-0";
            ReflectionHelpers.callInstanceMethod(f.selected, "attachLease");
            PlayerView surface = ReflectionHelpers.getField(f.selected, "playerView");
            f.feed.onSearchOpened();
            f.feed.onHostResume();
            assertSame(f.selectedPlayer.player, surface.getPlayer());
            assertTrue(f.selectedPlayer.readyToPlay);
            assertFalse(f.offscreenPlayer.readyToPlay);
            assertEquals(0, f.selectedPlayer.seeks);
            f.feed.onSearchOpened();
            f.feed.onHostPause();
            assertNull(surface.getPlayer());
            assertNull(owner.player(url));
            assertFalse(f.selectedPlayer.readyToPlay);
        }
    }

    @Test public void mutedVideoReturnsPlayingWithoutRestoringAudio() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            ReflectionHelpers.setField(f.feed, "chaosMuted", true);
            ReflectionHelpers.callInstanceMethod(f.selected, "attachLease");
            f.feed.onSearchOpened();
            f.feed.onHostResume();
            assertTrue(f.selectedPlayer.readyToPlay);
            assertEquals(0f, f.selectedPlayer.volume, 0f);
            assertEquals(0, f.selectedPlayer.seeks);
        }
    }

    @Test public void nativeSearchEntryPreservesTheSurfaceAndRealActivityPauseReleasesIt() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
            context.getSharedPreferences("app_prefs", 0).edit()
                    .putBoolean("access_notice_2_8_3_accepted", true)
                    .putBoolean("immersive_motion_enabled", false).commit();
            var host = org.robolectric.Robolectric.buildActivity(NativeMainActivity.class).create().start().resume().visible();
            try {
                MainPagerAdapter adapter = ReflectionHelpers.getField(host.get(), "primaryPagerAdapter");
                ChaosFeedView previous = ReflectionHelpers.getField(adapter, "chaosView");
                previous.close();
                ReflectionHelpers.setField(adapter, "chaosView", f.feed);
                ReflectionHelpers.callInstanceMethod(f.selected, "attachLease");
                PlayerView surface = ReflectionHelpers.getField(f.selected, "playerView");
                host.get().openContextualSearch();
                assertSame(f.selectedPlayer.player, surface.getPlayer());
                assertFalse(f.selectedPlayer.readyToPlay);
                host.pause();
                assertNull(surface.getPlayer());
            } finally { host.stop().destroy(); }
        }
    }

    @Test public void leavingShitTokWhileSearchIsOpenRetiresTheHeldSurface() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            ReflectionHelpers.callInstanceMethod(f.selected, "attachLease");
            PlayerView surface = ReflectionHelpers.getField(f.selected, "playerView");
            f.feed.onSearchOpened();
            f.feed.setActive(false);
            assertNull(surface.getPlayer());
            assertFalse(f.selectedPlayer.readyToPlay);
        }
    }
}
