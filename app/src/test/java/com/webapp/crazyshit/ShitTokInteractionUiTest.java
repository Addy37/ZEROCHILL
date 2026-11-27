package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import android.graphics.Rect;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w400dp-h800dp-xhdpi",
        shadows = { ShitTokInteractionUiTest.Account.class, ShitTokInteractionUiTest.Social.class })
public class ShitTokInteractionUiTest {
    static boolean signedIn;
    static int writes;
    static ZeroChillSocialRepository.Callback<ZeroChillSocialRepository.VideoLikeState> pending;
    @Implements(ZeroChillAccountRepository.class)
    public static class Account {
        @Implementation protected static boolean hasStoredSession(Context context) { return signedIn; }
    }
    @Implements(ZeroChillSocialRepository.class)
    public static class Social {
        @Implementation protected static void videoLikeState(Context context, String url,
                ZeroChillSocialRepository.Callback<ZeroChillSocialRepository.VideoLikeState> callback) {
            callback.complete(new ZeroChillSocialRepository.VideoLikeState(0, false), null);
        }
        @Implementation protected static void toggleVideoLike(Context context, NativeContentItem item, boolean liked,
                ZeroChillSocialRepository.Callback<ZeroChillSocialRepository.VideoLikeState> callback) {
            assertNotNull("Library metadata must come from the current native item", item);
            assertFalse(item.url.isEmpty());
            toggleVideoLike(context, item.url, liked, callback);
        }
        @Implementation protected static void toggleVideoLike(Context context, String url, boolean liked,
                ZeroChillSocialRepository.Callback<ZeroChillSocialRepository.VideoLikeState> callback) {
            assertFalse("double tap must never issue an unlike", liked);
            writes++; pending = callback;
        }
    }
    @Before public void reset() { signedIn = true; writes = 0; pending = null; }
    static void idle(long millis) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis)); }
    static ShitTokAudioOwnershipTest.Fixture fixture() {
        ShitTokAudioOwnershipTest.Fixture f = new ShitTokAudioOwnershipTest.Fixture();
        ReflectionHelpers.callInstanceMethod(f.feed, "playSelected");
        idle(50); // Finish the attached activity's first traversal before sending touches.
        f.selectedPlayer.plays = f.selectedPlayer.pauses = f.selectedPlayer.seeks = 0;
        return f;
    }
    static View root(Object holder) { return ((RecyclerView.ViewHolder) holder).itemView; }
    static void event(View root, long down, int action, float x, float y) {
        MotionEvent e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        root.dispatchTouchEvent(e); e.recycle();
    }
    static void tap(View root, float x, float y) {
        long down = SystemClock.uptimeMillis();
        event(root, down, MotionEvent.ACTION_DOWN, x, y); idle(40);
        event(root, down, MotionEvent.ACTION_UP, x, y);
    }
    static void doubleTap(View root, float x, float y) { tap(root, x, y); idle(60); tap(root, x, y); }

    @Test public void doubleTapAddsHeartImmediatelyAndRepeatedTapsNeverUnlike() {
        try (ShitTokAudioOwnershipTest.Fixture f = fixture()) {
            View root = root(f.selected);
            tap(root, 400, 500); idle(60);
            long down = SystemClock.uptimeMillis();
            event(root, down, MotionEvent.ACTION_DOWN, 400, 500);
            assertEquals(1, writes);
            assertTrue(ReflectionHelpers.getField(f.selected, "videoLiked"));
            assertEquals("Unlike video", ((TextView) ReflectionHelpers.getField(f.selected, "like")).getContentDescription());
            assertEquals(View.VISIBLE, root.findViewWithTag("shittok_double_tap_heart").getVisibility());
            idle(40); event(root, down, MotionEvent.ACTION_UP, 400, 500);
            idle(400); doubleTap(root, 400, 500);
            assertEquals(1, writes);
            pending.complete(new ZeroChillSocialRepository.VideoLikeState(1, true), null); idle(0);
            idle(400); doubleTap(root, 400, 500);
            assertEquals(1, writes);
            assertEquals(0, f.selectedPlayer.pauses);
            idle(600);
            assertEquals(View.GONE, root.findViewWithTag("shittok_double_tap_heart").getVisibility());
            assertTrue(((ViewPager2) ReflectionHelpers.getField(f.feed, "pager")).isUserInputEnabled());
        }
    }
    @Test public void failedHeartRestoresExistingStateAndLateResponseCannotChangeReboundItem() {
        try (ShitTokAudioOwnershipTest.Fixture f = fixture()) {
            doubleTap(root(f.selected), 400, 500);
            pending.complete(null, new IllegalStateException("offline")); idle(0);
            assertFalse(ReflectionHelpers.getField(f.selected, "videoLiked"));
            idle(400); doubleTap(root(f.selected), 400, 500);
            var old = pending;
            NativeContentItem next = new NativeContentItem(NativeContentItem.KIND_MEDIA, "Next",
                    "https://example.invalid/next", "", "", "", "");
            ReflectionHelpers.callInstanceMethod(f.selected, "bind", from(NativeContentItem.class, next), from(int.class, 1));
            old.complete(new ZeroChillSocialRepository.VideoLikeState(1, true), null); idle(0);
            assertFalse(ReflectionHelpers.getField(f.selected, "videoLiked"));
            assertEquals(View.GONE, root(f.selected).findViewWithTag("shittok_double_tap_heart").getVisibility());
        }
    }
    @Test public void signedOutDoubleTapKeepsPlaybackAndDoesNotFakeLike() {
        signedIn = false;
        try (ShitTokAudioOwnershipTest.Fixture f = fixture()) {
            doubleTap(root(f.selected), 400, 500); idle(400);
            assertEquals(0, writes); assertEquals(0, f.selectedPlayer.pauses);
            assertFalse(ReflectionHelpers.getField(f.selected, "videoLiked"));
            assertNull(root(f.selected).findViewWithTag("shittok_double_tap_heart"));
            assertNull(shadowOf(f.host.get()).getNextStartedActivity());
        }
    }
    @Test public void singleTapStillPerformsPlaybackActionAndDragDoesNotTap() {
        try (ShitTokAudioOwnershipTest.Fixture f = fixture()) {
            View root = root(f.selected);
            tap(root, 400, 500); assertEquals(0, f.selectedPlayer.pauses);
            idle(400);
            assertTrue(((ShitTokPlaybackOwner) ReflectionHelpers.getField(f.feed, "playbackOwner"))
                    .isUserPaused("https://example.invalid/clip-0"));
            int pauses = f.selectedPlayer.pauses;
            long down = SystemClock.uptimeMillis();
            event(root, down, MotionEvent.ACTION_DOWN, 400, 500); idle(40);
            event(root, down, MotionEvent.ACTION_MOVE, 400, 300); idle(40);
            event(root, down, MotionEvent.ACTION_UP, 400, 200); idle(400);
            assertEquals(0, writes); assertEquals(pauses, f.selectedPlayer.pauses);
        }
    }
    @Test public void doubleTapOnExplicitSaveControlNeverTriggersVideoHeart() {
        try (ShitTokAudioOwnershipTest.Fixture f = fixture()) {
            View root = root(f.selected), save = ReflectionHelpers.getField(f.selected, "save");
            Rect r = new Rect(0, 0, save.getWidth(), save.getHeight());
            ((android.view.ViewGroup) root).offsetDescendantRectToMyCoords(save, r);
            assertTrue(r.width() > 0);
            doubleTap(root, r.centerX(), r.centerY()); idle(400);
            assertEquals(0, writes);
            assertNull(root.findViewWithTag("shittok_double_tap_heart"));
            assertFalse(FavoriteStore.contains(f.host.get(), "https://example.invalid/clip-0"));
        }
    }
    @Test public void longVideoHasSlimProgressAndShortVideoStaysClean() {
        try (ShitTokAudioOwnershipTest.Fixture f = fixture()) {
            AtomicLong duration = new AtomicLong(60_000L);
            ExoPlayer player = (ExoPlayer) Proxy.newProxyInstance(ExoPlayer.class.getClassLoader(),
                    new Class<?>[]{ ExoPlayer.class }, (proxy, method, args) ->
                            method.getName().equals("getDuration") ? duration.get()
                                    : method.invoke(f.selectedPlayer.player, args));
            ReflectionHelpers.setField(f.selected, "player", player);
            ReflectionHelpers.callInstanceMethod(f.selected, "syncPausedChrome");
            View progress = root(f.selected).findViewWithTag("shittok_progress_row");
            SeekBar bar = ReflectionHelpers.getField(f.selected, "seekBar");
            assertEquals(View.VISIBLE, progress.getVisibility());
            assertEquals(0, bar.getThumb().getAlpha());
            assertNull(ReflectionHelpers.getField(f.selected, "scrubPreview"));
            duration.set(59_999); ReflectionHelpers.callInstanceMethod(f.selected, "updateProgress");
            idle(ZeroChillMotion.QUICK_MS + 20);
            assertEquals(View.INVISIBLE, progress.getVisibility());
            duration.set(60_000); ReflectionHelpers.callInstanceMethod(f.selected, "updateProgress");
            assertEquals(View.VISIBLE, progress.getVisibility());
            ReflectionHelpers.setField(f.selected, "userPaused", true);
            ReflectionHelpers.callInstanceMethod(f.selected, "syncPausedChrome");
            assertEquals(255, bar.getThumb().getAlpha());
        }
    }
    @Test public void portraitLineRetainsTouchTargetAndRestoresLandscapeThumbAcrossRotation() {
        try (ShitTokAudioOwnershipTest.Fixture f = fixture()) {
            SeekBar bar = ReflectionHelpers.getField(f.selected, "seekBar");
            View row = root(f.selected).findViewWithTag("shittok_progress_row");
            float density = bar.getResources().getDisplayMetrics().density;
            ReflectionHelpers.setField(f.selected, "userPaused", true);
            ReflectionHelpers.callInstanceMethod(f.selected, "syncOrientationChrome");
            assertEquals(Math.round(48 * density), row.getLayoutParams().height);
            assertEquals(Math.round(4 * density), bar.getMaxHeight());
            assertEquals(255, bar.getThumb().getAlpha());
            assertEquals(-Math.round(13 * density),
                    ((android.widget.FrameLayout.LayoutParams) row.getLayoutParams()).bottomMargin);

            org.robolectric.RuntimeEnvironment.setQualifiers("w740dp-h360dp-land-xhdpi");
            ReflectionHelpers.callInstanceMethod(f.selected, "syncOrientationChrome");
            assertEquals(255, bar.getThumb().getAlpha());
            assertEquals(Math.round(4 * density), bar.getMaxHeight());
            assertEquals(0, bar.getPaddingTop());
            assertEquals(View.VISIBLE, root(f.selected).findViewWithTag("shittok_elapsed").getVisibility());

            org.robolectric.RuntimeEnvironment.setQualifiers("w400dp-h800dp-port-xhdpi");
            ReflectionHelpers.callInstanceMethod(f.selected, "syncOrientationChrome");
            assertEquals(255, bar.getThumb().getAlpha());
            assertEquals(Math.round(4 * density), bar.getMaxHeight());
            assertEquals(View.GONE, root(f.selected).findViewWithTag("shittok_elapsed").getVisibility());
            ReflectionHelpers.setField(f.selected, "stream", null);
            ShitTokInteractionUiTest.tap(bar, bar.getWidth() * .7f, bar.getHeight() / 2f);
            assertEquals(1, f.selectedPlayer.seeks);
            assertEquals(255, bar.getThumb().getAlpha());
        }
    }

    @Test public void explicitButtonKeepsPriorityOverOverlappingProgressHitRow() {
        try (ShitTokAudioOwnershipTest.Fixture f = fixture()) {
            var root = (android.view.ViewGroup) root(f.selected);
            View more = root.findViewWithTag("shittok_share");
            Rect bounds = new Rect(0, 0, more.getWidth(), more.getHeight());
            root.offsetDescendantRectToMyCoords(more, bounds);
            View row = root.findViewWithTag("shittok_progress_row");
            Rect hitRow = new Rect(0, 0, row.getWidth(), row.getHeight());
            root.offsetDescendantRectToMyCoords(row, hitRow);
            assertTrue(hitRow.contains(bounds.centerX(), bounds.bottom - 2));
            java.util.concurrent.atomic.AtomicInteger clicks = new java.util.concurrent.atomic.AtomicInteger();
            more.setOnClickListener(v -> clicks.incrementAndGet());
            tap(root, bounds.centerX(), bounds.bottom - 2);
            idle(0); // View posts the button's normal performClick after ACTION_UP.
            assertEquals(1, clicks.get());
            assertFalse(ReflectionHelpers.getField(f.selected, "scrubbing"));
            assertNull(ReflectionHelpers.getField(f.selected, "scrubPreview"));
        }
    }
    @Test public void timeOnlyScrubPreviewsAndTransitionCancelsWithoutSeeking() {
        try (ShitTokAudioOwnershipTest.Fixture f = fixture()) {
            ReflectionHelpers.setField(f.selected, "stream", null); // Unavailable preview is optional.
            SeekBar bar = ReflectionHelpers.getField(f.selected, "seekBar");
            var listener = (SeekBar.OnSeekBarChangeListener) ReflectionHelpers.getField(bar, "mOnSeekBarChangeListener");
            var pager = (ViewPager2) ReflectionHelpers.getField(f.feed, "pager");
            listener.onStartTrackingTouch(bar);
            assertFalse(pager.isUserInputEnabled());
            bar.setProgress(500); listener.onProgressChanged(bar, 500, true);
            View card = root(f.selected).findViewWithTag("shittok_scrub_preview");
            assertEquals(View.VISIBLE, card.getVisibility());
            assertEquals(0, f.selectedPlayer.seeks);
            assertEquals("0:30 / 1:00", ((TextView) ((android.view.ViewGroup) card).getChildAt(1)).getText().toString());
            ReflectionHelpers.callInstanceMethod(f.selected, "syncOrientationChrome");
            assertTrue(ReflectionHelpers.getField(f.selected, "scrubbing"));
            assertFalse(pager.isUserInputEnabled());
            ReflectionHelpers.callInstanceMethod(f.feed, "resetTemporaryPinch");
            assertEquals(View.GONE, card.getVisibility()); assertTrue(pager.isUserInputEnabled());
            assertFalse(ReflectionHelpers.getField(f.selected, "scrubbing"));
            assertNull(ReflectionHelpers.getField(f.selected, "scrubPreview"));
        }
    }
    @Test public void fullscreenSurfaceTogglesAllChromeWithoutPausing() {
        try (ShitTokAudioOwnershipTest.Fixture f = fixture()) {
            enterLandscape(f, true);
            View overlay = root(f.selected).findViewWithTag("shittok_fullscreen_controls");
            View progress = root(f.selected).findViewWithTag("shittok_progress_row");
            View surface = root(f.selected).findViewWithTag("shittok_fullscreen_surface");
            assertSame(overlay, progress.getParent());
            assertEquals(View.VISIBLE, overlay.getVisibility());

            fullscreenTap(surface, .25f);
            idle(350);
            assertEquals(View.INVISIBLE, overlay.getVisibility());
            assertTrue(f.selectedPlayer.readyToPlay);
            assertEquals(0, f.selectedPlayer.pauses);

            fullscreenTap(surface, .25f);
            idle(350);
            assertEquals(View.VISIBLE, overlay.getVisibility());
            View playPause = root(f.selected).findViewWithTag("shittok_fullscreen_play_pause");
            assertEquals("Pause video", playPause.getContentDescription());
            assertTrue(playPause.performClick());
            assertFalse(f.selectedPlayer.readyToPlay);
            assertEquals("Play video", playPause.getContentDescription());
            idle(3_500);
            assertEquals(View.VISIBLE, overlay.getVisibility());
            assertTrue(playPause.performClick());
            assertTrue(f.selectedPlayer.readyToPlay);
            idle(3_500);
            assertEquals(View.INVISIBLE, overlay.getVisibility());
        }
    }

    @Test public void fullscreenDoubleTapsAccumulateAndSwipesCannotLeaveTheVideo() {
        try (ShitTokAudioOwnershipTest.Fixture f = fixture()) {
            enterLandscape(f, true);
            f.selectedPlayer.reflectSeeks = true;
            View surface = root(f.selected).findViewWithTag("shittok_fullscreen_surface");
            ViewPager2 pager = ReflectionHelpers.getField(f.feed, "pager");
            assertFalse(pager.isUserInputEnabled());
            int sources = f.selectedPlayer.sources;
            fullscreenTap(surface, .75f);
            idle(50);
            fullscreenTap(surface, .75f);
            assertEquals(22_000L, f.selectedPlayer.seekPosition);
            idle(50);
            fullscreenTap(surface, .75f);
            assertEquals(32_000L, f.selectedPlayer.seekPosition);
            idle(700);
            fullscreenTap(surface, .25f);
            idle(50);
            fullscreenTap(surface, .25f);
            assertEquals(22_000L, f.selectedPlayer.seekPosition);
            assertEquals(0, writes);

            View fullscreenControls = root(f.selected).findViewWithTag("shittok_fullscreen_controls");
            assertEquals(View.VISIBLE, fullscreenControls.getVisibility());
            fullscreenDrag(surface, 0, -200);
            fullscreenDrag(surface, -240, 0);
            ReflectionHelpers.callInstanceMethod(f.feed, "requestAutoAdvance", from(int.class, 0));
            idle(100);
            assertEquals(0, pager.getCurrentItem());
            assertFalse(pager.isUserInputEnabled());
            assertNull(shadowOf(f.host.get()).getNextStartedActivity());
            assertEquals(sources, f.selectedPlayer.sources);
        }
    }

    @Test public void fullscreenScrubUsesSelectedPlayerAndRestoresPortraitState() {
        try (ShitTokAudioOwnershipTest.Fixture f = fixture()) {
            f.selectedPlayer.reflectSeeks = true;
            f.selectedPlayer.speed = f.selectedPlayer.speed.withSpeed(1.25f);
            ReflectionHelpers.setField(f.selected, "horizontalVideo", true);
            assertTrue(root(f.selected).findViewWithTag("shittok_fullscreen").performClick());
            assertFalse(((ViewPager2) ReflectionHelpers.getField(f.feed, "pager")).isUserInputEnabled());
            enterLandscape(f, true);
            View overlay = root(f.selected).findViewWithTag("shittok_fullscreen_controls");
            SeekBar bar = ReflectionHelpers.getField(f.selected, "seekBar");
            TextView elapsed = root(f.selected).findViewWithTag("shittok_elapsed");
            TextView duration = root(f.selected).findViewWithTag("shittok_duration");
            assertEquals(200, bar.getProgress());
            assertEquals("0:12", elapsed.getText());
            assertEquals("1:00", duration.getText());
            SeekBar.OnSeekBarChangeListener seek = ReflectionHelpers.getField(bar, "mOnSeekBarChangeListener");
            int pauses = f.selectedPlayer.pauses;
            seek.onStartTrackingTouch(bar);
            bar.setProgress(600);
            seek.onProgressChanged(bar, 600, true);
            ReflectionHelpers.callInstanceMethod(f.selected, "updateProgress");
            assertEquals(600, bar.getProgress());
            assertEquals("0:36", elapsed.getText());
            assertEquals(pauses, f.selectedPlayer.pauses);
            idle(3_500);
            assertEquals(View.VISIBLE, overlay.getVisibility());
            seek.onStopTrackingTouch(bar);
            assertEquals(36_000L, f.selectedPlayer.seekPosition);
            assertEquals("0:36", elapsed.getText());

            f.selectedPlayer.seekAvailable = false;
            ReflectionHelpers.callInstanceMethod(f.selected, "updateProgress");
            assertFalse(bar.isEnabled());
            f.selectedPlayer.durationMs = androidx.media3.common.C.TIME_UNSET;
            ReflectionHelpers.callInstanceMethod(f.selected, "updateProgress");
            assertEquals("–:––", duration.getText());
            f.selectedPlayer.durationMs = 60_000L;

            assertTrue(f.feed.exitSensorFullscreenForBack());
            org.robolectric.RuntimeEnvironment.setQualifiers("w400dp-h800dp-port-xhdpi");
            f.feed.onConfigurationChanged();
            assertTrue(((ViewPager2) ReflectionHelpers.getField(f.feed, "pager")).isUserInputEnabled());
            assertSame(root(f.selected), ((View) ReflectionHelpers.getField(f.selected, "progressRow")).getParent());
            assertEquals(View.GONE, overlay.getVisibility());
            assertSame(f.selectedPlayer.player, ReflectionHelpers.getField(f.selected, "player"));
            assertEquals(36_000L, f.selectedPlayer.positionMs);
            assertEquals(1.25f, f.selectedPlayer.speed.speed, 0f);
            assertTrue(f.selectedPlayer.readyToPlay);
            assertEquals(pauses, f.selectedPlayer.pauses);
            View portraitSurface = ReflectionHelpers.getField(f.selected, "playerView");
            assertTrue(portraitSurface.performClick());
            assertFalse(f.selectedPlayer.readyToPlay);
        }
    }

    @Test public void fullscreenSpeedMenuKeepsControlsAndUsesTheSamePlayer() {
        try (ShitTokAudioOwnershipTest.Fixture f = fixture()) {
            enterLandscape(f, true);
            View overlay = root(f.selected).findViewWithTag("shittok_fullscreen_controls");
            TextView speed = root(f.selected).findViewWithTag("shittok_fullscreen_speed");
            assertTrue(speed.performClick());
            idle(3_500);
            assertEquals(View.VISIBLE, overlay.getVisibility());
            android.app.AlertDialog dialog = ReflectionHelpers.getField(f.feed, "actionDialog");
            assertTrue(dialog.isShowing());
            android.widget.ListView choices = dialog.getListView();
            assertTrue(choices.performItemClick(choices.getAdapter().getView(4, null, choices), 4,
                    choices.getAdapter().getItemId(4)));
            assertEquals(1.5f, f.selectedPlayer.speed.speed, 0f);
            assertEquals("1.5×", speed.getText());
            assertTrue(f.selectedPlayer.readyToPlay);
            idle(3_500);
            assertEquals(View.INVISIBLE, overlay.getVisibility());
        }
    }

    @Test public void backBeforeRotationDoesNotReopenFullscreen() {
        try (ShitTokAudioOwnershipTest.Fixture f = fixture()) {
            ReflectionHelpers.setField(f.selected, "horizontalVideo", true);
            View enter = root(f.selected).findViewWithTag("shittok_fullscreen");
            assertTrue(enter.performClick());
            assertTrue(f.feed.exitSensorFullscreenForBack());
            org.robolectric.RuntimeEnvironment.setQualifiers("w740dp-h360dp-land-xhdpi");
            f.feed.onConfigurationChanged();
            assertFalse(f.feed.fullscreenInputLocked());
            ViewPager2 pager = ReflectionHelpers.getField(f.feed, "pager");
            assertTrue(pager.isUserInputEnabled());
            assertEquals(View.GONE, root(f.selected).findViewWithTag("shittok_fullscreen_controls").getVisibility());
            assertSame(f.selectedPlayer.player, ReflectionHelpers.getField(f.selected, "player"));
        }
    }

    private static void enterLandscape(ShitTokAudioOwnershipTest.Fixture fixture, boolean reducedMotion) {
        fixture.host.get().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("immersive_motion_enabled", !reducedMotion).commit();
        fixture.selectedPlayer.seekAvailable = true;
        org.robolectric.RuntimeEnvironment.setQualifiers("w740dp-h360dp-land-xhdpi");
        fixture.feed.onConfigurationChanged();
        idle(100); // Apply the window's configuration before sending input to its resized surface.
        ReflectionHelpers.callInstanceMethod(fixture.selected, "syncOrientationChrome");
        View surface = root(fixture.selected).findViewWithTag("shittok_fullscreen_surface");
        assertTrue("Fullscreen touch surface must be laid out", surface.getWidth() > 0);
        assertTrue("Fixture must hold the selected player", ReflectionHelpers.callInstanceMethod(
                fixture.selected, "ownsPlayback"));
    }

    private static void fullscreenTap(View surface, float fraction) {
        fullscreenDrag(surface, 0, 0, fraction);
    }

    private static void fullscreenDrag(View surface, float dx, float dy) {
        fullscreenDrag(surface, dx, dy, .75f);
    }

    private static void fullscreenDrag(View surface, float dx, float dy, float fraction) {
        long down = android.os.SystemClock.uptimeMillis();
        float x = surface.getWidth() * fraction, y = surface.getHeight() / 2f;
        sendFullscreenTouch(surface, down, down, MotionEvent.ACTION_DOWN, x, y);
        if (dx != 0 || dy != 0) sendFullscreenTouch(surface, down, down + 15, MotionEvent.ACTION_MOVE, x + dx, y + dy);
        sendFullscreenTouch(surface, down, down + 25, MotionEvent.ACTION_UP, x + dx, y + dy);
    }

    private static void sendFullscreenTouch(View surface, long down, long time, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, time, action, x, y, 0);
        surface.dispatchTouchEvent(event);
        event.recycle();
    }

    @Test public void scrubCardClampsBothEdgesAndKeepsItsSizeAcrossCacheMisses() {
        try (ShitTokAudioOwnershipTest.Fixture f = fixture()) {
            ReflectionHelpers.setField(f.selected, "stream", null);
            SeekBar bar = ReflectionHelpers.getField(f.selected, "seekBar");
            var listener = (SeekBar.OnSeekBarChangeListener) ReflectionHelpers.getField(bar, "mOnSeekBarChangeListener");
            listener.onStartTrackingTouch(bar);
            ShitTokScrubPreviewView card = ReflectionHelpers.getField(f.selected, "scrubPreviewView");
            var holderRoot = (android.widget.FrameLayout) root(f.selected);
            bar.setProgress(0); card.position(holderRoot, bar);
            assertTrue(card.getTranslationX() >= 0);
            bar.setProgress(1000); card.position(holderRoot, bar);
            assertTrue(card.getTranslationX() + card.getMeasuredWidth() <= holderRoot.getWidth());
            card.frame(android.graphics.Bitmap.createBitmap(32, 18, android.graphics.Bitmap.Config.ARGB_8888));
            card.position(holderRoot, bar);
            int height = card.getMeasuredHeight();
            card.frame(null); card.position(holderRoot, bar);
            assertEquals(height, card.getMeasuredHeight());
            listener.onStopTrackingTouch(bar);
            assertEquals(View.GONE, card.getVisibility());
        }
    }

    @Test public void pageBackgroundRotationRecycleAndSourceTransitionsCancelBeforeExtraction() {
        for (String transition : new String[]{"page", "background", "rotation", "recycle", "source"}) {
            try (ShitTokAudioOwnershipTest.Fixture f = fixture()) {
                SeekBar bar = ReflectionHelpers.getField(f.selected, "seekBar");
                var listener = (SeekBar.OnSeekBarChangeListener) ReflectionHelpers.getField(bar, "mOnSeekBarChangeListener");
                listener.onStartTrackingTouch(bar);
                ShitTokScrubPreview preview = ReflectionHelpers.getField(f.selected, "scrubPreview");
                assertNotNull(preview);
                switch (transition) {
                    case "page": ReflectionHelpers.callInstanceMethod(f.feed, "handlePagerScrollState",
                            from(int.class, ViewPager2.SCROLL_STATE_DRAGGING)); break;
                    case "background": f.feed.onHostPause(); break;
                    case "rotation": f.feed.onConfigurationChanged(); break;
                    case "recycle": ReflectionHelpers.callInstanceMethod(f.selected, "detachLease"); break;
                    case "source": ReflectionHelpers.callInstanceMethod(f.selected, "prepareMeasured",
                            from(CrazyShitRepository.StreamInfo.class, new CrazyShitRepository.StreamInfo(
                                    "https://example.invalid/renewed.mp4", "https://example.invalid/clip-0", "fixture")),
                            from(boolean.class, false)); break;
                }
                assertFalse(ReflectionHelpers.getField(f.selected, "scrubbing"));
                assertFalse(ReflectionHelpers.getField(preview, "active"));
                assertNull(ReflectionHelpers.getField(preview, "extractor"));
                assertEquals(View.GONE, root(f.selected).findViewWithTag("shittok_scrub_preview").getVisibility());
                assertTrue(((ViewPager2) ReflectionHelpers.getField(f.feed, "pager")).isUserInputEnabled());
            }
        }
    }
}
