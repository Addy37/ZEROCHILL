package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.view.View;

import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.VideoSize;
import androidx.media3.common.Timeline;
import androidx.media3.common.Tracks;
import androidx.media3.common.MediaMetadata;
import android.os.Looper;
import java.util.Set;

import static org.junit.Assert.*;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

/** Exercise real holders and their player registry without network or decoder timing. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w400dp-h800dp-xhdpi")
public class ShitTokAudioOwnershipTest {
    static final class FakePlayer {
        int plays, pauses, positionReads, seeks, sources, releases, listeners;
        long sourceStartPosition;
        int state = Player.STATE_READY;
        androidx.media3.exoplayer.ExoPlaybackException error;
        float volume = 1f;
        androidx.media3.common.PlaybackParameters speed = new androidx.media3.common.PlaybackParameters(1f);
        boolean readyToPlay = true;
        boolean failPosition;
        final ExoPlayer player = (ExoPlayer) Proxy.newProxyInstance(ExoPlayer.class.getClassLoader(),
                new Class<?>[]{ExoPlayer.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "play": plays++; readyToPlay = true; return null;
                        case "getApplicationLooper": return Looper.getMainLooper();
                        case "getTrackSelectionParameters": return TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT;
                        case "getVideoSize": return VideoSize.UNKNOWN;
                        case "getCurrentTimeline": return Timeline.EMPTY;
                        case "getCurrentTracks": return Tracks.EMPTY;
                        case "getMediaMetadata": return MediaMetadata.EMPTY;
                        case "setPlayWhenReady": readyToPlay=(Boolean)args[0]; return null;
                        case "equals": return proxy == args[0];
                        case "hashCode": return System.identityHashCode(proxy);
                        case "pause": pauses++; readyToPlay = false; return null;
                        case "getPlaybackParameters": return speed;
                        case "setPlaybackSpeed": speed = speed.withSpeed((Float) args[0]); return null;
                        case "setPlaybackParameters": speed = (androidx.media3.common.PlaybackParameters) args[0]; return null;
                        case "setVolume": volume = (Float) args[0]; return null;
                        case "getVolume": return volume;
                        case "getPlayWhenReady": return readyToPlay;
                        case "isPlaying": return readyToPlay;
                        case "getPlaybackState": return state;
                        case "getPlayerError": return error;
                        case "addListener": listeners++; return null;
                        case "removeListener": listeners--; return null;
                        case "release": releases++; return null;
                        case "setMediaSource":
                            sources++;
                            if (args.length > 1 && args[1] instanceof Long) sourceStartPosition = (Long) args[1];
                            return null;
                        case "seekTo": seeks++; state=Player.STATE_READY; return null;
                        case "getCurrentPosition":
                            positionReads++;
                            if (failPosition) throw new IllegalStateException("stale player position");
                            return 12_000L;
                        case "getDuration": return 60_000L;
                        default:
                            if (method.getReturnType() == boolean.class) return false;
                            if (method.getReturnType() == int.class) return 0;
                            if (method.getReturnType() == long.class) return 0L;
                            if (method.getReturnType() == float.class) return 0f;
                            return null;
                    }
                });
    }

    static final class Fixture implements AutoCloseable {
        final ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup();
        final ChaosFeedView feed = new ChaosFeedView(host.get(), item -> { }, false);
        final Object selected, offscreen, duplicate;
        final FakePlayer selectedPlayer = new FakePlayer();
        final FakePlayer offscreenPlayer = new FakePlayer();
        final FakePlayer duplicatePlayer = new FakePlayer();

        Fixture() {
            feed.close();
            ArrayList<NativeContentItem> items = ReflectionHelpers.getField(feed, "items");
            for (int i = 0; i < 3; i++) items.add(new NativeContentItem(NativeContentItem.KIND_MEDIA,
                    "Fixture " + i, "https://example.invalid/clip-" + i, "", "", "", ""));
            ViewPager2 pager = ReflectionHelpers.getField(feed, "pager");
            RecyclerView.Adapter adapter = pager.getAdapter();
            adapter.notifyDataSetChanged();
            host.get().setContentView(feed);
            feed.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
            feed.layout(0, 0, 800, 1600);
            RecyclerView recycler = (RecyclerView) pager.getChildAt(0);
            selected = recycler.findViewHolderForAdapterPosition(0);
            assertNotNull(selected);
            RecyclerView.ViewHolder detached = adapter.createViewHolder(recycler, 0);
            adapter.bindViewHolder(detached, 1);
            offscreen = detached;
            RecyclerView.ViewHolder stale = adapter.createViewHolder(recycler, 0);
            adapter.bindViewHolder(stale, 0);
            duplicate = stale;
            assertNull(detached.itemView.getParent());
            assertNull(stale.itemView.getParent());
            install(offscreen, offscreenPlayer);
            install(duplicate, duplicatePlayer);
            ReflectionHelpers.setField(feed, "closed", false);
            ReflectionHelpers.setField(feed, "active", true);
            ReflectionHelpers.setField(feed, "hostResumed", true);
            ReflectionHelpers.setField(feed, "chaosMuted", false);
            ShitTokPlaybackOwner owner = ReflectionHelpers.getField(feed, "playbackOwner");
            final int[] count = {0};
            owner.setPlayerFactoryForTest(() -> count[0]++ == 0 ? selectedPlayer.player : offscreenPlayer.player);
            owner.permission(true, false);
            owner.select(items.get(0).url, items.get(1).url, null, true);
            CrazyShitRepository.StreamInfo selectedStream = new CrazyShitRepository.StreamInfo(
                    "https://example.invalid/clip.mp4", items.get(0).url, "Fixture");
            owner.source(items.get(0).url, selectedStream, owner.token(items.get(0).url));
            ReflectionHelpers.setField(selected, "stream", selectedStream);
            java.util.Map<String, CrazyShitRepository.StreamInfo> streams = ReflectionHelpers.getField(feed, "streamCache");
            streams.put(items.get(0).url, selectedStream);
            selectedPlayer.plays = selectedPlayer.pauses = selectedPlayer.positionReads = 0;
            selectedPlayer.readyToPlay = false;
            offscreenPlayer.volume = 1f;
            offscreenPlayer.readyToPlay = true;
            offscreenPlayer.plays = offscreenPlayer.pauses = offscreenPlayer.positionReads = 0;
        }

        void install(Object holder, FakePlayer fake) {
            ReflectionHelpers.setField(holder, "player", fake.player);
            Set<Object> holders = ReflectionHelpers.getField(feed, "playerHolders");
            holders.add(holder);
        }

        @Override public void close() {
            ReflectionHelpers.setField(selected, "player", null);
            ReflectionHelpers.setField(offscreen, "player", null);
            ReflectionHelpers.setField(duplicate, "player", null);
            Set<?> holders = ReflectionHelpers.getField(feed, "playerHolders");
            holders.clear();
            feed.close();
            host.pause().stop().destroy();
        }
    }

    @Test public void pageSelectionSilencesDetachedAndDuplicatePositionPlayers() {
        try (Fixture f = new Fixture()) {
            ReflectionHelpers.callInstanceMethod(f.feed, "pauseNonSelected", from(int.class, 0));
            assertEquals(0, f.selectedPlayer.pauses);
            assertTrue(f.offscreenPlayer.pauses >= 1);
            assertTrue(f.duplicatePlayer.pauses >= 1);
            assertEquals(0f, f.offscreenPlayer.volume, 0f);
            assertEquals(0f, f.duplicatePlayer.volume, 0f);
        }
    }

    @Test public void historyReadFailureCannotPreventSilencingOrPause() {
        try (Fixture f = new Fixture()) {
            f.offscreenPlayer.failPosition = true;
            ReflectionHelpers.callInstanceMethod(f.offscreen, "pauseAndRecord");
            assertEquals(0f, f.offscreenPlayer.volume, 0f);
            assertTrue(f.offscreenPlayer.pauses >= 1);
        }
    }

    @Test public void delayedOffscreenResumeAndCompletionCannotPlay() {
        try (Fixture f = new Fixture()) {
            ReflectionHelpers.callInstanceMethod(f.offscreen, "playOwnedPlayer");
            ReflectionHelpers.callInstanceMethod(f.duplicate, "handleCompletion",
                    from(ExoPlayer.class, f.duplicatePlayer.player));
            assertEquals(0, f.offscreenPlayer.plays);
            assertEquals(0, f.duplicatePlayer.plays);
            assertEquals(0f, f.offscreenPlayer.volume, 0f);
            assertEquals(0f, f.duplicatePlayer.volume, 0f);
        }
    }

    @Test public void selectedResumeGrantsOnlyOneAudiblePlayerAndHonorsMute() {
        try (Fixture f = new Fixture()) {
            ReflectionHelpers.callInstanceMethod(f.selected, "playOwnedPlayer");
            assertEquals(1, f.selectedPlayer.plays);
            assertEquals(1f, f.selectedPlayer.volume, 0f);
            assertEquals(0f, f.offscreenPlayer.volume, 0f);
            assertEquals(0f, f.duplicatePlayer.volume, 0f);
            ReflectionHelpers.setField(f.feed, "chaosMuted", true);
            ReflectionHelpers.callInstanceMethod(f.selected, "applyMuteState");
            assertEquals(0f, f.selectedPlayer.volume, 0f);
            ReflectionHelpers.callInstanceMethod(f.offscreen, "applyMuteState");
            assertEquals(0f, f.offscreenPlayer.volume, 0f);
        }
    }

    @Test public void backgroundOrInactiveFeedCannotResumeAnyHolder() {
        try (Fixture f = new Fixture()) {
            ReflectionHelpers.setField(f.feed, "hostResumed", false);
            ReflectionHelpers.callInstanceMethod(f.selected, "playOwnedPlayer");
            assertEquals(0, f.selectedPlayer.plays);
            assertEquals(0f, f.selectedPlayer.volume, 0f);
            ReflectionHelpers.setField(f.feed, "hostResumed", true);
            ReflectionHelpers.setField(f.feed, "active", false);
            ReflectionHelpers.callInstanceMethod(f.selected, "playOwnedPlayer");
            assertEquals(0, f.selectedPlayer.plays);
        }
    }

    @Test public void repeatedOwnershipChecksRecordAnOutgoingHolderOnlyOnce() {
        try (Fixture f = new Fixture()) {
            for (int i = 0; i < 5; i++) ReflectionHelpers.callInstanceMethod(f.offscreen, "pauseAndRecord");
            assertEquals(1, f.offscreenPlayer.positionReads);
            assertEquals(0f, f.offscreenPlayer.volume, 0f);
            assertFalse(f.offscreenPlayer.readyToPlay);
        }
    }

    @Test public void holderDetachDoesNotReleasePooledPlayers() {
        try (Fixture f = new Fixture()) {
            ReflectionHelpers.callInstanceMethod(f.offscreen, "detachLease");
            ShitTokPlaybackOwner owner = ReflectionHelpers.getField(f.feed, "playbackOwner");
            assertSame(f.offscreenPlayer.player, owner.player("https://example.invalid/clip-1"));
            assertNull(ReflectionHelpers.getField(f.offscreen, "player"));
        }
    }
}
