package com.webapp.crazyshit;

import android.view.MotionEvent;
import androidx.media3.ui.PlayerView;
import androidx.viewpager2.widget.ViewPager2;
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
    private ShitTokPlaybackOwner owner(ShitTokAudioOwnershipTest.Fixture f) {
        return ReflectionHelpers.getField(f.feed, "playbackOwner");
    }
    private PlayerView attach(ShitTokAudioOwnershipTest.Fixture f) {
        ReflectionHelpers.callInstanceMethod(f.selected, "attachLease");
        ReflectionHelpers.callInstanceMethod(f.selected, "playOwnedPlayer");
        return ReflectionHelpers.getField(f.selected, "playerView");
    }
    private ViewPager2 pager(ShitTokAudioOwnershipTest.Fixture f) {
        return ReflectionHelpers.getField(f.feed, "pager");
    }

    @Test public void searchRetainsPlayingSelectedSurfaceWithoutPauseSeekOrSourceChange() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            PlayerView surface = attach(f);
            int pauses = f.selectedPlayer.pauses, seeks = f.selectedPlayer.seeks;
            int sources = f.selectedPlayer.sources, releases = f.selectedPlayer.releases;
            int token = owner(f).token(URL);
            f.feed.onSearchOpened();
            assertSame(f.selectedPlayer.player, surface.getPlayer());
            assertSame(f.selectedPlayer.player, owner(f).player(URL));
            assertEquals(token, owner(f).token(URL));
            assertTrue(f.selectedPlayer.readyToPlay);
            assertEquals(pauses, f.selectedPlayer.pauses);
            assertEquals(seeks, f.selectedPlayer.seeks);
            assertEquals(sources, f.selectedPlayer.sources);
            assertEquals(releases, f.selectedPlayer.releases);
            assertEquals(1f, f.selectedPlayer.volume, 0f);
            assertNull(owner(f).player("https://example.invalid/clip-1"));
            assertFalse(f.offscreenPlayer.readyToPlay);
            assertFalse(f.duplicatePlayer.readyToPlay);
            assertFalse(owner(f).allowsSpeculativeWork());
            assertFalse(pager(f).isUserInputEnabled());
        }
    }

    @Test public void searchClosePreservesAudioAndWindowWithoutChangingSelectedEngine() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            PlayerView surface = attach(f);
            int plays = f.selectedPlayer.plays;
            int pauses = f.selectedPlayer.pauses, seeks = f.selectedPlayer.seeks, sources = f.selectedPlayer.sources;
            f.feed.onSearchOpened(); f.feed.onSearchClosed();
            assertSame(f.selectedPlayer.player, surface.getPlayer());
            assertEquals(1f, f.selectedPlayer.volume, 0f);
            assertTrue(f.selectedPlayer.readyToPlay);
            assertEquals(plays, f.selectedPlayer.plays);
            assertEquals(pauses, f.selectedPlayer.pauses);
            assertEquals(seeks, f.selectedPlayer.seeks);
            assertEquals(sources, f.selectedPlayer.sources);
            assertTrue(pager(f).isUserInputEnabled());
            assertNotNull(owner(f).player("https://example.invalid/clip-1"));
        }
    }

    @Test public void unmutedManualPauseNeverRestartsOnSearchClose() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            attach(f);
            owner(f).setUserPaused(URL, true);
            int plays = f.selectedPlayer.plays, seeks = f.selectedPlayer.seeks;
            f.feed.onSearchOpened();
            assertFalse(f.selectedPlayer.readyToPlay);
            f.feed.onSearchClosed();
            assertFalse(f.selectedPlayer.readyToPlay);
            assertTrue(owner(f).isUserPaused(URL));
            assertEquals(plays, f.selectedPlayer.plays);
            assertEquals(seeks, f.selectedPlayer.seeks);
            assertFalse(ReflectionHelpers.getField(f.feed, "chaosMuted"));
        }
    }

    @Test public void closeThenTabDepartureRevokesSelectedOwnership() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            PlayerView surface = attach(f);
            f.feed.onSearchOpened(); f.feed.onSearchClosed();
            assertTrue(f.selectedPlayer.readyToPlay);
            f.feed.setActive(false);
            assertNull(surface.getPlayer());
            assertNull(owner(f).player(URL));
            assertFalse(f.selectedPlayer.readyToPlay);
            assertEquals(0f, f.selectedPlayer.volume, 0f);
        }
    }

    @Test public void coverageKeepsOnlySelectedAudibleThroughOwnershipCallbacks() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            attach(f); f.feed.onSearchOpened();
            owner(f).syncAudio();
            ReflectionHelpers.callInstanceMethod(f.selected, "applyMuteState");
            ReflectionHelpers.callInstanceMethod(f.offscreen, "playOwnedPlayer");
            ReflectionHelpers.callInstanceMethod(f.duplicate, "playOwnedPlayer");
            assertTrue(f.selectedPlayer.readyToPlay);
            assertEquals(1f, f.selectedPlayer.volume, 0f);
            assertEquals(0f, f.offscreenPlayer.volume, 0f);
            assertEquals(0f, f.duplicatePlayer.volume, 0f);
            assertFalse(f.offscreenPlayer.readyToPlay);
            assertFalse(f.duplicatePlayer.readyToPlay);
        }
    }

    @Test public void priorMuteAndManualPauseSurviveSearch() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            attach(f);
            ReflectionHelpers.setField(f.feed, "chaosMuted", true);
            ReflectionHelpers.callInstanceMethod(f.selected, "applyMuteState");
            owner(f).setUserPaused(URL, true);
            f.feed.onSearchOpened();
            assertFalse(f.selectedPlayer.readyToPlay);
            f.feed.onSearchClosed();
            assertFalse(f.selectedPlayer.readyToPlay);
            assertEquals(0f, f.selectedPlayer.volume, 0f);
            assertTrue(owner(f).isUserPaused(URL));
        }
    }

    @Test public void priorMuteAloneRemainsMutedAndPlaying() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            attach(f); ReflectionHelpers.setField(f.feed, "chaosMuted", true);
            ReflectionHelpers.callInstanceMethod(f.selected, "applyMuteState");
            f.feed.onSearchOpened();
            assertTrue(f.selectedPlayer.readyToPlay);
            assertEquals(0f, f.selectedPlayer.volume, 0f);
            f.feed.onSearchClosed();
            assertTrue(f.selectedPlayer.readyToPlay);
            assertEquals(0f, f.selectedPlayer.volume, 0f);
        }
    }

    @Test public void realBackgroundReleasesAndCloseCannotReactivateUntilResume() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            PlayerView surface = attach(f);
            f.feed.onSearchOpened(); f.feed.onHostPause(); f.feed.onSearchClosed();
            assertNull(surface.getPlayer()); assertNull(owner(f).player(URL));
            assertFalse(f.selectedPlayer.readyToPlay);
            assertTrue(f.selectedPlayer.releases > 0);
            assertFalse(pager(f).isUserInputEnabled());
            f.feed.onHostResume();
            assertNotNull(owner(f).player(URL));
        }
    }

    @Test public void leavingShitTokRetiresSelectedEvenWhileSearchIsOpen() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            PlayerView surface = attach(f);
            f.feed.onSearchOpened(); f.feed.setActive(false); f.feed.onSearchClosed();
            assertNull(surface.getPlayer()); assertNull(owner(f).player(URL));
            assertFalse(f.selectedPlayer.readyToPlay);
            assertEquals(0f, f.selectedPlayer.volume, 0f);
        }
    }

    @Test public void repeatedCyclesKeepOneLeaseAndNoSelectedCallbacksAccumulate() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            PlayerView surface = attach(f);
            int token = owner(f).token(URL), listeners = f.selectedPlayer.listeners;
            int pauses = f.selectedPlayer.pauses, sources = f.selectedPlayer.sources;
            for (int i = 0; i < 8; i++) {
                f.feed.onSearchOpened(); f.feed.onSearchOpened();
                assertSame(f.selectedPlayer.player, surface.getPlayer());
                assertEquals(1f, f.selectedPlayer.volume, 0f);
                f.feed.onSearchClosed(); f.feed.onSearchClosed();
                assertSame(f.selectedPlayer.player, owner(f).player(URL));
                assertEquals(token, owner(f).token(URL));
                assertEquals(listeners, f.selectedPlayer.listeners);
                assertEquals(1f, f.selectedPlayer.volume, 0f);
            }
            assertEquals(pauses, f.selectedPlayer.pauses);
            assertEquals(sources, f.selectedPlayer.sources);
            assertEquals(0, f.selectedPlayer.seeks);
        }
    }

    @Test public void searchConsumesTouchesAndCompletionCannotSeekOrAdvance() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            attach(f); f.feed.onSearchOpened();
            MotionEvent tap = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100, 100, 0);
            try { assertTrue(f.feed.dispatchTouchEvent(tap)); } finally { tap.recycle(); }
            f.selectedPlayer.state = androidx.media3.common.Player.STATE_ENDED;
            ReflectionHelpers.callInstanceMethod(f.selected, "handleCompletion",
                    from(androidx.media3.exoplayer.ExoPlayer.class, f.selectedPlayer.player));
            assertEquals(0, f.selectedPlayer.seeks);
            assertEquals(0, pager(f).getCurrentItem());
            assertFalse(ReflectionHelpers.getField(f.feed, "autoAdvancePending"));
            f.feed.onSearchClosed();
            assertEquals(0, f.selectedPlayer.seeks);
        }
    }

    @Test public void searchTypingTabsAndTouchScrollDoNotReachFeed() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            PlayerView surface = attach(f);
            android.widget.FrameLayout root = new android.widget.FrameLayout(f.host.get());
            ((android.view.ViewGroup) f.feed.getParent()).removeView(f.feed);
            root.addView(f.feed, new android.widget.FrameLayout.LayoutParams(-1, -1));
            f.host.get().setContentView(root);
            SearchGlassOwner glassOwner = new SearchGlassOwner(f.host.get());
            SearchPanel panel = new SearchPanel(f.host.get(), new android.content.Intent(), true, () -> {});
            panel.create(null);
            root.addView(panel.view, new android.widget.FrameLayout.LayoutParams(-1, -1));
            f.feed.onSearchOpened();
            try {
                root.measure(android.view.View.MeasureSpec.makeMeasureSpec(800, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(1600, android.view.View.MeasureSpec.EXACTLY));
                root.layout(0, 0, 800, 1600);
                int plays = f.selectedPlayer.plays, pauses = f.selectedPlayer.pauses;
                panel.field().setText("a"); // No provider requests for a one-character query.
                java.util.List<android.widget.TextView> tabs = ReflectionHelpers.getField(panel, "scopeViews");
                for (android.widget.TextView tab : tabs) tab.performClick();
                for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP}) {
                    MotionEvent event = MotionEvent.obtain(0, 16, action, 400,
                            action == MotionEvent.ACTION_DOWN ? 1200 : 600, 0);
                    try { assertTrue(root.dispatchTouchEvent(event)); } finally { event.recycle(); }
                }
                assertEquals(0, pager(f).getCurrentItem());
                assertFalse(pager(f).isUserInputEnabled());
                assertSame(f.selectedPlayer.player, surface.getPlayer());
                assertEquals(plays, f.selectedPlayer.plays);
                assertEquals(pauses, f.selectedPlayer.pauses);
                assertEquals(1f, f.selectedPlayer.volume, 0f);
                assertTrue(f.selectedPlayer.readyToPlay);
                assertFalse(owner(f).isUserPaused(URL));
            } finally {
                root.removeView(panel.view);
                panel.close();
                glassOwner.close();
                f.feed.onSearchClosed();
            }
        }
    }

    @Test public void nativeSearchResultNavigationUsesRealActivityPause() {
        try (var f = new ShitTokAudioOwnershipTest.Fixture()) {
            android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
            context.getSharedPreferences("app_prefs", 0).edit().putBoolean("access_notice_2_8_3_accepted", true)
                    .putBoolean("immersive_motion_enabled", false).commit();
            var host = org.robolectric.Robolectric.buildActivity(NativeMainActivity.class).create().start().resume().visible();
            try {
                MainPagerAdapter adapter = ReflectionHelpers.getField(host.get(), "primaryPagerAdapter");
                ChaosFeedView previous = ReflectionHelpers.getField(adapter, "chaosView"); previous.close();
                ReflectionHelpers.setField(adapter, "chaosView", f.feed);
                PlayerView surface = attach(f);
                host.get().openContextualSearch();
                assertSame(f.selectedPlayer.player, surface.getPlayer());
                assertEquals(1f, f.selectedPlayer.volume, 0f);
                assertTrue(ReflectionHelpers.getField(adapter, "hostResumed"));
                SearchPanel panel = ReflectionHelpers.getField(host.get(), "searchPanel");
                ReflectionHelpers.callInstanceMethod(panel, "openResult", from(NativeContentItem.class,
                        new NativeContentItem(NativeContentItem.KIND_CATEGORY, "Fixture category",
                                "https://example.invalid/category", "", "", "", "")));
                assertNotNull(org.robolectric.Shadows.shadowOf(host.get()).getNextStartedActivity());
                // Robolectric does not automatically pause the source Activity after startActivity.
                host.pause();
                assertNull(surface.getPlayer()); assertNull(owner(f).player(URL));
                ReflectionHelpers.callInstanceMethod(host.get(), "closeSearch");
                assertNull(owner(f).player(URL));
            } finally { host.stop().destroy(); }
        }
    }
}
