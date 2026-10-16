package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Looper;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w360dp-h800dp-port-mdpi")
public class CreatorPlayerQolTest {
    private Player player(AtomicBoolean playing, AtomicLong position) {
        ArrayList<Player.Listener> listeners = new ArrayList<>();
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isPlaying": case "getPlayWhenReady": return playing.get();
                        case "getPlaybackState": return Player.STATE_READY;
                        case "getDuration": return 60_000L;
                        case "getCurrentPosition": return position.get();
                        case "seekTo": position.set((long) args[args.length - 1]); return null;
                        case "addListener": listeners.add((Player.Listener) args[0]); return null;
                        case "removeListener": listeners.remove(args[0]); return null;
                        case "getApplicationLooper": return Looper.getMainLooper();
                        case "getCurrentTimeline": return androidx.media3.common.Timeline.EMPTY;
                        case "getCurrentTracks": return androidx.media3.common.Tracks.EMPTY;
                        case "getMediaMetadata": return androidx.media3.common.MediaMetadata.EMPTY;
                        case "getPlaybackParameters": return androidx.media3.common.PlaybackParameters.DEFAULT;
                        case "play": case "pause":
                            playing.set(method.getName().equals("play"));
                            for (Player.Listener listener : new ArrayList<>(listeners))
                                listener.onEvents((Player) proxy, new Player.Events(new androidx.media3.common.FlagSet.Builder()
                                        .add(Player.EVENT_PLAY_WHEN_READY_CHANGED).build()));
                            return null;
                        case "isCommandAvailable": return true;
                        case "getAvailableCommands": return new Player.Commands.Builder().addAllCommands().build();
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    if (method.getReturnType() == float.class) return 0f;
                    return null;
                });
    }

    @Test public void surfaceTapsToggleChromeWithoutPausingAndSeekIsBounded() {
        Activity host = Robolectric.buildActivity(Activity.class).setup().get();
        host.setTheme(R.style.Theme_CrazyShit);
        PlayerView surface = (PlayerView) host.getLayoutInflater().inflate(R.layout.view_shows_fullscreen_player, null);
        host.setContentView(surface);
        layout(surface, 360, 800);
        ShowsFullscreenControls controls = new ShowsFullscreenControls(surface);
        AtomicBoolean playing = new AtomicBoolean(true);
        AtomicLong position = new AtomicLong(30_000L);
        Player player = player(playing, position);
        controls.bind(player);
        FullscreenVideoGestures gestures = new FullscreenVideoGestures(surface, () -> player, controls, () -> true);
        tap(gestures, surface, 80, 0);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350));
        assertFalse(controls.isVisible());
        assertTrue(playing.get());
        tap(gestures, surface, 80, 600);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350));
        assertTrue(controls.isVisible());
        assertTrue(playing.get());
        tap(gestures, surface, 300, 1200);
        tap(gestures, surface, 300, 1300);
        assertEquals(40_000L, position.get());
        gestures.seek(false);
        assertEquals(30_000L, position.get());
        position.set(3000L); gestures.seek(false); assertEquals(0L, position.get());
        position.set(59_000L); gestures.seek(true); assertEquals(60_000L, position.get());
        controls.release(); gestures.cancel(); host.finish();
    }

    @Test public void transportButtonPausesAndPlaysAndPausedChromeDoesNotExpire() {
        Activity host = Robolectric.buildActivity(Activity.class).setup().get();
        host.setTheme(R.style.Theme_CrazyShit);
        PlayerView surface = (PlayerView) host.getLayoutInflater().inflate(R.layout.view_shows_fullscreen_player, null);
        host.setContentView(surface);
        ShowsFullscreenControls controls = new ShowsFullscreenControls(surface);
        AtomicBoolean playing = new AtomicBoolean(true);
        Player player = player(playing, new AtomicLong(30_000L));
        androidx.media3.ui.PlayerControlView controller = surface.findViewById(androidx.media3.ui.R.id.exo_controller);
        controller.setPlayer(player);
        controls.bind(player);
        View transport = surface.findViewById(androidx.media3.ui.R.id.exo_play_pause);
        assertTrue(transport.isEnabled());
        transport.performClick();
        assertFalse(playing.get());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));
        assertTrue(controls.isVisible());
        transport.performClick();
        assertTrue(playing.get());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        assertFalse(controls.isVisible());
        controls.release(); host.finish();
    }

    @Test public void parentCanInterceptOnlyFapDragAndCanceledDoubleTapDoesNotSeek() {
        Activity host = Robolectric.buildActivity(Activity.class).setup().get();
        host.setTheme(R.style.Theme_CrazyShit);
        FrameLayout scrollingParent = new FrameLayout(host);
        PlayerView surface = (PlayerView) host.getLayoutInflater().inflate(R.layout.view_shows_fullscreen_player, null);
        scrollingParent.addView(surface);
        host.setContentView(scrollingParent);
        layout(scrollingParent, 360, 800);
        ShowsFullscreenControls controls = new ShowsFullscreenControls(surface);
        AtomicLong position = new AtomicLong(30_000L);
        Player player = player(new AtomicBoolean(true), position);
        controls.bind(player);
        FullscreenVideoGestures gestures = new FullscreenVideoGestures(surface, () -> player, controls, () -> true);
        tap(gestures, surface, 300, 0);
        event(gestures, surface, MotionEvent.ACTION_DOWN, 300, 300, 100);
        event(gestures, surface, MotionEvent.ACTION_MOVE, 300, 430, 130);
        event(gestures, surface, MotionEvent.ACTION_CANCEL, 300, 450, 150);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        assertEquals(30_000L, position.get());
        assertEquals(0, ReflectionHelpers.<Integer>getField(scrollingParent, "mGroupFlags") & 0x80000);
        controls.release(); gestures.cancel(); host.finish();
    }

    @Test public void onlyFapPlayerAllowsRealVerticalAndHorizontalRecyclerDrags() {
        for (int orientation : new int[] {RecyclerView.VERTICAL, RecyclerView.HORIZONTAL}) {
            Activity host = Robolectric.buildActivity(Activity.class).setup().get();
            host.setTheme(R.style.Theme_CrazyShit);
            RecyclerView list = new RecyclerView(host);
            list.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(host, orientation, false));
            BunkrGalleryPagerAdapter adapter = new BunkrGalleryPagerAdapter(host, new BunkrGalleryPagerAdapter.Listener() {
                @Override public void onMediaTap(int position, NativeContentItem item) { }
                @Override public void onMediaLongPress(int position, NativeContentItem item) { }
                @Override public void onResolvedImageFailed(int position, NativeContentItem item) { }
            });
            ArrayList<NativeContentItem> media = new ArrayList<>();
            for (int i = 0; i < 5; i++) media.add(new NativeContentItem(NativeContentItem.KIND_MEDIA,
                    "Video " + i, "https://example.test/video/" + i, "", "", "", ""));
            adapter.replace(media, Collections.emptyMap());
            list.setAdapter(adapter);
            host.setContentView(list);
            layout(list, 360, 800);
            androidx.media3.exoplayer.ExoPlayer actual = new androidx.media3.exoplayer.ExoPlayer.Builder(host).build();
            actual.seekTo(23_456L);
            adapter.activateVideo(0, actual);
            adapter.onVideoFirstFrame(0, actual);
            layout(list, 360, 800);
            BunkrGalleryPagerAdapter.Holder holder = (BunkrGalleryPagerAdapter.Holder) list.findViewHolderForAdapterPosition(0);
            assertNotNull(holder);
            holder.image.setVisibility(View.GONE);
            holder.controls.hide();
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
            assertSame(actual, holder.playerView.getPlayer());
            long now = android.os.SystemClock.uptimeMillis();
            for (int i = 0; i < 4; i++) {
                int action = i == 0 ? MotionEvent.ACTION_DOWN : i == 3 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
                float x = orientation == RecyclerView.HORIZONTAL ? 290 - i * 65 : 180;
                float y = orientation == RecyclerView.VERTICAL ? 550 - i * 100 : 400;
                MotionEvent event = MotionEvent.obtain(now, now + i * 50, action, x, y, 0);
                list.dispatchTouchEvent(event); event.recycle();
            }
            int offset = orientation == RecyclerView.VERTICAL ? list.computeVerticalScrollOffset() : list.computeHorizontalScrollOffset();
            assertTrue("Parent scrolls in orientation " + orientation, offset > 0);
            assertEquals(23_456L, actual.getCurrentPosition());
            list.setAdapter(null);
            actual.release(); host.finish();
        }
    }

    @Test public void onlyFapRotationKeepsPositionPauseStateAndManualFullscreenUnderRotationLock() {
        BunkrGalleryActivity activity = Robolectric.buildActivity(BunkrGalleryActivity.class).get();
        BunkrGalleryPagerAdapter adapter = new BunkrGalleryPagerAdapter(activity, new BunkrGalleryPagerAdapter.Listener() {
            @Override public void onMediaTap(int position, NativeContentItem item) { }
            @Override public void onMediaLongPress(int position, NativeContentItem item) { }
            @Override public void onResolvedImageFailed(int position, NativeContentItem item) { }
        });
        androidx.media3.exoplayer.ExoPlayer actual = new androidx.media3.exoplayer.ExoPlayer.Builder(activity).build();
        actual.seekTo(23_456L); actual.pause();
        ReflectionHelpers.setField(activity, "player", actual);
        ReflectionHelpers.setField(activity, "adapter", adapter);
        ReflectionHelpers.setField(activity, "videoSizeKnown", true);
        Settings.System.putInt(activity.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1);
        for (SensorMediaOrientationListener.Position position : SensorMediaOrientationListener.Position.values()) {
            ReflectionHelpers.callInstanceMethod(activity, "onVideoOrientation", ReflectionHelpers.ClassParameter.from(
                    SensorMediaOrientationListener.Position.class, position));
            assertSame(actual, ReflectionHelpers.getField(activity, "player"));
            assertEquals(23_456L, actual.getCurrentPosition());
            assertFalse(actual.getPlayWhenReady());
        }
        Settings.System.putInt(activity.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
        ReflectionHelpers.setField(activity, "sensorVideoFullscreen", false);
        ReflectionHelpers.callInstanceMethod(activity, "onVideoOrientation", ReflectionHelpers.ClassParameter.from(
                SensorMediaOrientationListener.Position.class, SensorMediaOrientationListener.Position.LANDSCAPE));
        assertFalse(ReflectionHelpers.<Boolean>getField(activity, "sensorVideoFullscreen"));
        ReflectionHelpers.callInstanceMethod(activity, "toggleVideoFullscreen");
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, activity.getRequestedOrientation());
        assertEquals(23_456L, actual.getCurrentPosition());
        ReflectionHelpers.callInstanceMethod(activity, "toggleVideoFullscreen");
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.getRequestedOrientation());
        ReflectionHelpers.setField(activity, "portraitVideo", true);
        Settings.System.putInt(activity.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1);
        ReflectionHelpers.callInstanceMethod(activity, "onVideoOrientation", ReflectionHelpers.ClassParameter.from(
                SensorMediaOrientationListener.Position.class, SensorMediaOrientationListener.Position.LANDSCAPE));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.getRequestedOrientation());
        actual.release(); PhoneOrientationPolicy.onActivityDestroyed(activity);
    }

    @Test public void repeatedLandscapeDimensionsDoNotUndoOnlyFapManualFullscreen() {
        BunkrGalleryActivity activity = Robolectric.buildActivity(BunkrGalleryActivity.class).get();
        ReflectionHelpers.setField(activity, "videoSizeKnown", true);
        ReflectionHelpers.setField(activity, "manualVideoFullscreen", true);
        java.util.concurrent.atomic.AtomicInteger rechecks = new java.util.concurrent.atomic.AtomicInteger();
        SensorMediaOrientationListener sensor = new SensorMediaOrientationListener(activity, p -> rechecks.incrementAndGet());
        ReflectionHelpers.setField(sensor, "enabled", true);
        ReflectionHelpers.setField(sensor, "stablePosition", SensorMediaOrientationListener.Position.PORTRAIT);
        ReflectionHelpers.setField(activity, "videoOrientationListener", sensor);
        ReflectionHelpers.callInstanceMethod(activity, "onVideoDimensions", ReflectionHelpers.ClassParameter.from(
                androidx.media3.common.VideoSize.class, new androidx.media3.common.VideoSize(1280, 720)));
        assertEquals(0, rechecks.get());
        assertTrue(ReflectionHelpers.<Boolean>getField(activity, "manualVideoFullscreen"));
        ReflectionHelpers.setField(activity, "videoSizeKnown", false);
        ReflectionHelpers.callInstanceMethod(activity, "onVideoDimensions", ReflectionHelpers.ClassParameter.from(
                androidx.media3.common.VideoSize.class, new androidx.media3.common.VideoSize(1920, 1080)));
        assertEquals(1, rechecks.get());
        sensor.disable();
    }

    @Test public void compactFavoriteUsesHeaderStateAndPersistsAcrossHeaderCreation() {
        Activity host = Robolectric.buildActivity(Activity.class).setup().get();
        host.setTheme(R.style.Theme_CrazyShit);
        CreatorProfileHeader header = new CreatorProfileHeader(host, "QoL Creator", "", "");
        TextView compact = new TextView(host);
        header.bindCompactFavorite(compact);
        host.setContentView(header);
        layout(header, 360, 184);
        assertFalse(header.isFavoriteOffscreen(0));
        assertTrue(header.isFavoriteOffscreen(-184));
        assertEquals("☆", compact.getText().toString());
        compact.performClick();
        TextView full = header.findViewWithTag("creator_favorite");
        assertEquals("★  Favorited", full.getText().toString());
        assertEquals("★", compact.getText().toString());
        CreatorProfileHeader restored = new CreatorProfileHeader(host, "QoL Creator", "", "");
        TextView restoredFull = restored.findViewWithTag("creator_favorite");
        assertEquals("★  Favorited", restoredFull.getText().toString());
        full.performClick();
        assertEquals("☆", compact.getText().toString());
        host.finish();
    }

    @Test public void creatorHeaderTargetsNavigateAndTwentySixTilesUseNineLazyGridRows() throws Exception {
        org.robolectric.android.controller.ActivityController<UpdateInboxActivity> controller =
                Robolectric.buildActivity(UpdateInboxActivity.class).setup();
        UpdateInboxActivity activity = controller.get();
        NotificationInboxTestIdle.await(activity);
        UpdateInboxStore.Entry group = new UpdateInboxStore.Entry();
        group.id = "qol-group"; group.title = group.creatorName = "QoL Creator";
        group.category = UpdateInboxStore.CATEGORY_ONLYFAP;
        for (int i = 0; i < 26; i++) group.children.add(new UpdateInboxStore.CreatorItem("fapello", "QoL Creator",
                new NativeContentItem(NativeContentItem.KIND_MEDIA, "Video " + i,
                        "https://example.test/video/" + i, "", "", "", ""), i, false));
        RecyclerView recycler = ReflectionHelpers.getField(activity, "recycler");
        Object adapter = recycler.getAdapter();
        ReflectionHelpers.callInstanceMethod(adapter, "replace", ReflectionHelpers.ClassParameter.from(
                java.util.List.class, Collections.singletonList(group)));
        NotificationInboxTestIdle.await(activity);
        RecyclerView.Adapter rows = recycler.getAdapter();
        RecyclerView.ViewHolder header = rows.createViewHolder(recycler, 0);
        rows.bindViewHolder(header, 0);
        LinearLayout row = (LinearLayout) ((ViewGroup) header.itemView).getChildAt(1);
        LinearLayout labels = (LinearLayout) row.getChildAt(1);
        assertFalse(row.isClickable());
        row.getChildAt(0).performClick();
        Intent avatarIntent = shadowOf(activity).getNextStartedActivity();
        assertEquals(NativeFeedBrowserActivity.class.getName(), avatarIntent.getComponent().getClassName());
        labels.getChildAt(0).performClick();
        assertEquals(NativeFeedBrowserActivity.class.getName(), shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
        assertEquals(1, rows.getItemCount());
        ((ViewGroup) labels.getChildAt(2)).getChildAt(0).performClick();
        NotificationInboxTestIdle.await(activity);
        assertEquals(27, rows.getItemCount());
        GridLayoutManager grid = (GridLayoutManager) recycler.getLayoutManager();
        assertEquals(3, grid.getSpanCount());
        assertEquals(9, grid.getSpanSizeLookup().getSpanGroupIndex(26, 3));
        RecyclerView.ViewHolder tile = rows.createViewHolder(recycler, rows.getItemViewType(1));
        rows.bindViewHolder(tile, 1);
        layout(tile.itemView, 104, 130);
        assertEquals(130, tile.itemView.getMeasuredHeight());
        assertEquals(View.VISIBLE, ((ViewGroup) tile.itemView).getChildAt(1).getVisibility());
        assertEquals(View.VISIBLE, ((ViewGroup) tile.itemView).getChildAt(2).getVisibility());
        assertEquals(2, UpdateInboxActivity.creatorGridColumns(280));
        assertTrue(UpdateInboxActivity.creatorGridColumns(800) > 3);
        controller.pause().stop().destroy();
    }

    @Test public void showsRotationHonorsLockKnownDimensionsAndKeepsSamePlayer() {
        VideoDetailActivity activity = Robolectric.buildActivity(VideoDetailActivity.class, new Intent()).get();
        activity.setTheme(R.style.Theme_CrazyShit);
        for (String field : new String[] {"mediaUrl", "pageUrl", "title", "views", "uploader", "comments",
                "userAgent", "cookies", "relatedFeedUrl", "source", "mediaReferer", "posterUrl"})
            ReflectionHelpers.setField(activity, field, "");
        ReflectionHelpers.callInstanceMethod(activity, "buildUi");
        androidx.media3.exoplayer.ExoPlayer actual = new androidx.media3.exoplayer.ExoPlayer.Builder(activity).build();
        actual.seekTo(23_456L);
        actual.setPlayWhenReady(true);
        ReflectionHelpers.setField(activity, "player", actual);
        ReflectionHelpers.setField(activity, "manualLandscapeFullscreen", true);
        Settings.System.putInt(activity.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1);
        physical(activity, SensorMediaOrientationListener.Position.LANDSCAPE);
        assertFalse(ReflectionHelpers.<Boolean>getField(activity, "sensorFullscreen"));
        ReflectionHelpers.setField(activity, "videoSizeKnown", true);
        Settings.System.putInt(activity.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
        physical(activity, SensorMediaOrientationListener.Position.LANDSCAPE);
        assertFalse(ReflectionHelpers.<Boolean>getField(activity, "sensorFullscreen"));
        Settings.System.putInt(activity.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1);
        physical(activity, SensorMediaOrientationListener.Position.LANDSCAPE);
        assertTrue(ReflectionHelpers.<Boolean>getField(activity, "sensorFullscreen"));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, activity.getRequestedOrientation());
        assertSame(actual, ReflectionHelpers.getField(activity, "player"));
        assertEquals(23_456L, actual.getCurrentPosition());
        assertTrue(actual.getPlayWhenReady());
        physical(activity, SensorMediaOrientationListener.Position.PORTRAIT);
        assertFalse(ReflectionHelpers.<Boolean>getField(activity, "rotatableFullscreen"));
        assertSame(actual, ReflectionHelpers.getField(activity, "player"));
        assertEquals(23_456L, actual.getCurrentPosition());
        assertTrue(actual.getPlayWhenReady());
        ReflectionHelpers.setField(activity, "portraitVideo", true);
        physical(activity, SensorMediaOrientationListener.Position.LANDSCAPE);
        assertFalse(ReflectionHelpers.<Boolean>getField(activity, "sensorFullscreen"));
        ReflectionHelpers.setField(activity, "portraitVideo", false);
        Settings.System.putInt(activity.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
        ReflectionHelpers.callInstanceMethod(activity, "setRotatableFullscreen",
                ReflectionHelpers.ClassParameter.from(boolean.class, true));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, activity.getRequestedOrientation());
        assertEquals(23_456L, actual.getCurrentPosition());
        actual.release();
        ((ShowsFullscreenControls) ReflectionHelpers.getField(activity, "fullscreenControls")).release();
    }

    private void physical(VideoDetailActivity activity, SensorMediaOrientationListener.Position position) {
        ReflectionHelpers.callInstanceMethod(activity, "onPhysicalOrientation", ReflectionHelpers.ClassParameter.from(
                SensorMediaOrientationListener.Position.class, position));
    }
    private void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, view.getMeasuredHeight());
    }
    private void tap(FullscreenVideoGestures gestures, PlayerView view, float x, long time) {
        event(gestures, view, MotionEvent.ACTION_DOWN, x, 300, time);
        event(gestures, view, MotionEvent.ACTION_UP, x, 300, time + 30);
    }
    private long eventBase = -1L;
    private void event(FullscreenVideoGestures gestures, PlayerView view, int action, float x, float y, long time) {
        if (eventBase < 0L) eventBase = android.os.SystemClock.uptimeMillis();
        time += eventBase;
        long delay = time - android.os.SystemClock.uptimeMillis();
        if (delay > 0L) shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(delay));
        MotionEvent event = MotionEvent.obtain(time, time, action, x, y, 0);
        gestures.onTouch(view, event); event.recycle();
    }
}
