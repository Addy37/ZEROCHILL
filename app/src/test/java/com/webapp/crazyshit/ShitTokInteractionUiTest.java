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
            assertEquals(View.INVISIBLE, progress.getVisibility());
            duration.set(60_000); ReflectionHelpers.callInstanceMethod(f.selected, "updateProgress");
            assertEquals(View.VISIBLE, progress.getVisibility());
            ReflectionHelpers.setField(f.selected, "userPaused", true);
            ReflectionHelpers.callInstanceMethod(f.selected, "syncPausedChrome");
            assertEquals(255, bar.getThumb().getAlpha());
        }
    }
    @Test public void explicitButtonKeepsPriorityOverOverlappingProgressHitRow() {
        try (ShitTokAudioOwnershipTest.Fixture f = fixture()) {
            var root = (android.view.ViewGroup) root(f.selected);
            View more = root.findViewWithTag("shittok_more");
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
    @Test public void longLandscapeProgressSurvivesHiddenControlsWithoutOtherChrome() {
        org.robolectric.RuntimeEnvironment.setQualifiers("w740dp-h360dp-land");
        try (ShitTokAudioOwnershipTest.Fixture f = fixture()) {
            ReflectionHelpers.callInstanceMethod(f.selected, "syncOrientationChrome");
            ReflectionHelpers.callInstanceMethod(f.selected, "hideControlsNow"); idle(400);
            assertFalse(ReflectionHelpers.getField(f.selected, "controlsVisible"));
            assertEquals(View.VISIBLE, root(f.selected).findViewWithTag("shittok_progress_row").getVisibility());
            assertEquals(View.INVISIBLE, root(f.selected).findViewWithTag("shittok_landscape_header").getVisibility());
            assertEquals(View.INVISIBLE, root(f.selected).findViewWithTag("shittok_elapsed").getVisibility());
        }
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
