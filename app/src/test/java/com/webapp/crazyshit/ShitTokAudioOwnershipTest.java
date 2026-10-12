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
import java.util.Deque;
import java.util.Set;

import static org.junit.Assert.*;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

/** Exercise real holders and their player registry without network or decoder timing. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w400dp-h800dp-xhdpi")
public class ShitTokAudioOwnershipTest {
    static final class FakePlayer {
        int plays, pauses, positionReads;
        float volume = 1f;
        boolean readyToPlay = true;
        boolean failPosition;
        final ExoPlayer player = (ExoPlayer) Proxy.newProxyInstance(ExoPlayer.class.getClassLoader(),
                new Class<?>[]{ExoPlayer.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "play": plays++; readyToPlay = true; return null;
                        case "pause": pauses++; readyToPlay = false; return null;
                        case "setVolume": volume = (Float) args[0]; return null;
                        case "getVolume": return volume;
                        case "getPlayWhenReady": return readyToPlay;
                        case "isPlaying": return readyToPlay;
                        case "getPlaybackState": return Player.STATE_READY;
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
        final ChaosFeedView feed = new ChaosFeedView(host.get(), item -> { });
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
            install(selected, selectedPlayer);
            install(offscreen, offscreenPlayer);
            install(duplicate, duplicatePlayer);
            ReflectionHelpers.setField(feed, "closed", false);
            ReflectionHelpers.setField(feed, "active", true);
            ReflectionHelpers.setField(feed, "hostResumed", true);
            ReflectionHelpers.setField(feed, "chaosMuted", false);
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
            assertEquals(1, f.offscreenPlayer.pauses);
            assertEquals(1, f.duplicatePlayer.pauses);
            assertEquals(0f, f.offscreenPlayer.volume, 0f);
            assertEquals(0f, f.duplicatePlayer.volume, 0f);
        }
    }

    @Test public void historyReadFailureCannotPreventSilencingOrPause() {
        try (Fixture f = new Fixture()) {
            f.offscreenPlayer.failPosition = true;
            ReflectionHelpers.callInstanceMethod(f.offscreen, "pauseAndRecord");
            assertEquals(0f, f.offscreenPlayer.volume, 0f);
            assertEquals(1, f.offscreenPlayer.pauses);
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

    @Test public void deferredReleaseQueueOnlyReceivesSilentPausedPlayers() {
        try (Fixture f = new Fixture()) {
            ReflectionHelpers.callInstanceMethod(f.offscreen, "detachPlayerForDeferredRelease");
            Deque<ExoPlayer> releases = ReflectionHelpers.getField(f.feed, "deferredPlayerReleases");
            assertTrue(releases.contains(f.offscreenPlayer.player));
            assertEquals(0f, f.offscreenPlayer.volume, 0f);
            assertFalse(f.offscreenPlayer.readyToPlay);
            assertNull(ReflectionHelpers.getField(f.offscreen, "player"));
        }
    }
}
