package com.webapp.crazyshit;

import android.app.Application;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Looper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.media3.ui.PlayerView;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.Arrays;
import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackParameters;
import static org.robolectric.Shadows.shadowOf;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w360dp-h800dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ShowsWatchPageTest {
    @Before public void clearQueuedNext() {
        org.robolectric.RuntimeEnvironment.getApplication().getSharedPreferences("player_positions", 0)
                .edit().remove("shows_play_next").commit();
    }
    public static class WatchProbe extends VideoDetailActivity {
        NativeContentItem dispatched;
        @Override void dispatchShowsSelection(NativeContentItem item) { dispatched = item; }
    }
    private NativeContentItem item(String name) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, "Episode " + name,
                "https://example.test/" + name, "https://example.test/" + name + ".jpg", "", "", "");
    }

    private VideoDetailActivity watch(NativeContentItem... episodes) {
        // Build the UI without preparing a decoder or fetching a source.
        VideoDetailActivity activity = Robolectric.buildActivity(VideoDetailActivity.class, new Intent()).get();
        activity.setTheme(R.style.Theme_CrazyShit);
        ReflectionHelpers.setField(activity, "showsOrigin", true);
        ReflectionHelpers.setField(activity, "showsSession", ShowsWatchSession.from(
                ShowsWatchSession.attach(new Intent(), Arrays.asList(episodes), true, "Season 1")));
        ReflectionHelpers.setField(activity, "pageUrl", episodes[0].url);
        ReflectionHelpers.setField(activity, "title", episodes[0].title);
        for (String field : new String[]{"views", "uploader", "comments", "userAgent", "cookies",
                "relatedFeedUrl", "source", "mediaReferer", "posterUrl"})
            ReflectionHelpers.setField(activity, field, "");
        ReflectionHelpers.callInstanceMethod(activity, "buildUi");
        ReflectionHelpers.callInstanceMethod(activity, "renderShowsRelated");
        return activity;
    }

    private WatchProbe probe(NativeContentItem... episodes) {
        org.robolectric.android.controller.ActivityController<WatchProbe> controller =
                Robolectric.buildActivity(WatchProbe.class, new Intent());
        WatchProbe activity = controller.get();
        activity.setTheme(R.style.Theme_CrazyShit);
        ReflectionHelpers.setField(activity, "showsOrigin", true);
        ReflectionHelpers.setField(activity, "showsSession", ShowsWatchSession.from(
                ShowsWatchSession.attach(new Intent(), Arrays.asList(episodes), true, "Season 1")));
        ReflectionHelpers.setField(activity, "pageUrl", episodes[0].url);
        ReflectionHelpers.setField(activity, "title", episodes[0].title);
        for (String field : new String[]{"views", "uploader", "comments", "userAgent", "cookies",
                "relatedFeedUrl", "source", "mediaReferer", "posterUrl"})
            ReflectionHelpers.setField(activity, field, "");
        ReflectionHelpers.callInstanceMethod(activity, "buildUi");
        controller.visible();
        return activity;
    }

    private void orient(VideoDetailActivity activity, int orientation) {
        ReflectionHelpers.callInstanceMethod(activity, "applyOrientation",
                ReflectionHelpers.ClassParameter.from(int.class, orientation));
    }

    private void render(VideoDetailActivity activity, int width, int height, String name) throws Exception {
        View root = ReflectionHelpers.getField(activity, "root");
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        File file = new File("build/reports/visual-tests/" + name);
        assertTrue(file.getParentFile().exists() || file.getParentFile().mkdirs());
        try (FileOutputStream out = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        }
        bitmap.recycle();
    }

    @Test public void portraitRelatedCardsHaveFullWidthArtworkAndSocialActions() throws Exception {
        VideoDetailActivity activity = watch(item("1"), item("2"));
        orient(activity, Configuration.ORIENTATION_PORTRAIT);
        LinearLayout related = ReflectionHelpers.getField(activity, "relatedContainer");
        LinearLayout card = (LinearLayout) related.getChildAt(0);
        assertTrue(card.getChildAt(0) instanceof ImageView);
        assertEquals(182, card.getChildAt(0).getLayoutParams().height);
        assertNotNull(ReflectionHelpers.getField(activity, "videoLikeButton"));
        assertNotNull(ReflectionHelpers.getField(activity, "commentButton"));
        assertNotNull(ReflectionHelpers.getField(activity, "watchLaterButton"));
        assertNotNull(activity.findViewById(R.id.player_menu));
        render(activity, 360, 800, "shows-watch-portrait.png");
    }

    @Test @Config(qualifiers = "w800dp-h360dp-land-mdpi")
    public void landscapeRailScalesFromReferenceAndVideoRemainsAboveIt() throws Exception {
        VideoDetailActivity activity = watch(item("1"), item("2"), item("3"));
        orient(activity, Configuration.ORIENTATION_LANDSCAPE);
        LinearLayout rail = ReflectionHelpers.getField(activity, "showsLandscapeRail");
        LinearLayout actions = ReflectionHelpers.getField(activity, "showsLandscapeActions");
        HorizontalScrollView scroll = ReflectionHelpers.getField(activity, "showsRailScroll");
        ViewGroup video = ReflectionHelpers.getField(activity, "playerContainer");
        float scale = Math.min(800f / 1536f, 360f / 698f);
        assertEquals(Math.round(304f * scale), rail.getChildAt(0).getLayoutParams().width);
        assertEquals(Math.round(171f * scale), rail.getChildAt(0).getLayoutParams().height);
        assertEquals(Math.round(199f * scale), scroll.getLayoutParams().height);
        assertEquals(View.VISIBLE, actions.getVisibility());
        assertEquals(4, actions.getChildCount());
        render(activity, 800, 360, "shows-watch-landscape.png");
        assertEquals(360 - scroll.getHeight(), video.getHeight());
    }

    @Test public void rotationKeepsSurfaceAndPresentsRelatedInBothOrientations() {
        VideoDetailActivity activity = watch(item("1"), item("2"), item("3"));
        PlayerView player = ReflectionHelpers.getField(activity, "playerView");
        View surface = player.getVideoSurfaceView();
        ScrollView details = ReflectionHelpers.getField(activity, "detailsScroll");
        HorizontalScrollView rail = ReflectionHelpers.getField(activity, "showsRailScroll");
        orient(activity, Configuration.ORIENTATION_PORTRAIT);
        assertEquals(View.VISIBLE, details.getVisibility());
        assertEquals(View.GONE, rail.getVisibility());
        orient(activity, Configuration.ORIENTATION_LANDSCAPE);
        assertEquals(View.GONE, details.getVisibility());
        assertEquals(View.VISIBLE, rail.getVisibility());
        assertTrue(player.getUseController());
        assertSame(surface, player.getVideoSurfaceView());
        orient(activity, Configuration.ORIENTATION_PORTRAIT);
        assertSame(surface, player.getVideoSurfaceView());
    }

    @Test public void backExitsDirectlyAfterSelectionsWithoutRelatedHistory() {
        VideoDetailActivity activity = watch(item("1"), item("2"), item("3"));
        AtomicInteger mediaSwaps = new AtomicInteger();
        AtomicInteger releases = new AtomicInteger();
        ExoPlayer engine = (ExoPlayer) Proxy.newProxyInstance(ExoPlayer.class.getClassLoader(),
                new Class<?>[]{ExoPlayer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("setMediaSource")) mediaSwaps.incrementAndGet();
                    if (method.getName().equals("release")) releases.incrementAndGet();
                    if (method.getName().equals("getPlaybackState")) return Player.STATE_READY;
                    if (method.getName().equals("getCurrentPosition")) return 12_000L;
                    if (method.getName().equals("getDuration")) return 100_000L;
                    if (method.getName().equals("getPlaybackParameters")) return new PlaybackParameters(1f);
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    if (method.getReturnType() == float.class) return 1f;
                    return null;
                });
        ReflectionHelpers.setField(activity, "player", engine);
        ReflectionHelpers.setField(activity, "mediaUrl", "content://example.test/media/1");
        Object surface = ((PlayerView) ReflectionHelpers.getField(activity, "playerView")).getVideoSurfaceView();
        activity.acceptShowsResolved(item("2"), new CrazyShitRepository.StreamInfo(
                "content://example.test/media/2", item("2").url, "Episode 2"));
        activity.acceptShowsResolved(item("3"), new CrazyShitRepository.StreamInfo(
                "content://example.test/media/3", item("3").url, "Episode 3"));
        assertEquals(2, mediaSwaps.get());
        assertEquals(0, releases.get());
        assertSame(engine, ReflectionHelpers.getField(activity, "player"));
        assertSame(surface, ((PlayerView) ReflectionHelpers.getField(activity, "playerView")).getVideoSurfaceView());
        assertNull(shadowOf(activity).getNextStartedActivity());
        assertFalse(activity.isFinishing());
        ReflectionHelpers.callInstanceMethod(activity, "handleBack");
        assertTrue(activity.isFinishing());
        assertTrue(((java.util.ArrayDeque<?>) ReflectionHelpers.getField(activity, "relatedHistory")).isEmpty());
    }

    @Test public void countdownCanBeCancelledAndLastEpisodeHasNoCountdown() {
        WatchProbe activity = probe(item("1"), item("2"));
        ReflectionHelpers.setField(activity, "recoveryResumed", true);
        ReflectionHelpers.callInstanceMethod(activity, "startShowsCountdown");
        assertNotNull(ReflectionHelpers.getField(activity, "showsCountdownItem"));
        ReflectionHelpers.callInstanceMethod(activity, "cancelShowsCountdown");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));
        assertNull(activity.dispatched);
        ReflectionHelpers.callInstanceMethod(activity, "startShowsCountdown");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));
        assertEquals(item("2").url, activity.dispatched.url);
        assertNull(ReflectionHelpers.getField(activity, "showsCountdownItem"));
        ReflectionHelpers.setField(activity, "pageUrl", item("2").url);
        ReflectionHelpers.callInstanceMethod(activity, "startShowsCountdown");
        assertNull(ReflectionHelpers.getField(activity, "showsCountdownItem"));
    }

    @Test public void queuedPlayNextOverridesSequenceAndCancelClearsTheQueue() {
        WatchProbe activity = probe(item("1"), item("2"));
        ReflectionHelpers.setField(activity, "recoveryResumed", true);
        VideoDetailActivity.setShowsPlayNext(activity, item("9"), "efukt", "series");
        ReflectionHelpers.callInstanceMethod(activity, "startShowsCountdown");
        NativeContentItem next = ReflectionHelpers.getField(activity, "showsCountdownItem");
        assertEquals(item("9").url, next.url);
        android.widget.FrameLayout overlay = ReflectionHelpers.getField(activity, "showsNextOverlay");
        LinearLayout content = (LinearLayout) overlay.getChildAt(0);
        LinearLayout buttons = (LinearLayout) content.getChildAt(content.getChildCount() - 1);
        buttons.getChildAt(buttons.getChildCount() - 1).performClick();
        assertNull(VideoDetailActivity.pendingShowsNext(activity, item("1").url));
        assertNull(ReflectionHelpers.getField(activity, "showsCountdownItem"));
    }

    @Test public void savedSelectionAndPlaybackPreferencesRestoreWithoutHistoryChain() {
        VideoDetailActivity activity = watch(item("1"), item("2"));
        ReflectionHelpers.setField(activity, "mediaUrl", "content://example.test/media/2");
        ReflectionHelpers.setField(activity, "pageUrl", item("2").url);
        ReflectionHelpers.setField(activity, "showsCompleted", true);
        ReflectionHelpers.setField(activity, "showsRestoreVolume", 0f);
        ReflectionHelpers.setField(activity, "showsRestoreSpeed", 1.5f);
        Bundle saved = new Bundle();
        activity.onSaveInstanceState(saved);
        VideoDetailActivity restored = watch(item("1"), item("2"));
        ReflectionHelpers.callInstanceMethod(restored, "restoreShowsState",
                ReflectionHelpers.ClassParameter.from(Bundle.class, saved));
        assertEquals(item("2").url, ReflectionHelpers.getField(restored, "pageUrl"));
        assertEquals("content://example.test/media/2", ReflectionHelpers.getField(restored, "mediaUrl"));
        assertTrue(ReflectionHelpers.getField(restored, "showsCompleted"));
        assertEquals(0f, (float) ReflectionHelpers.getField(restored, "showsRestoreVolume"), 0f);
        assertEquals(1.5f, (float) ReflectionHelpers.getField(restored, "showsRestoreSpeed"), 0f);
    }
}
