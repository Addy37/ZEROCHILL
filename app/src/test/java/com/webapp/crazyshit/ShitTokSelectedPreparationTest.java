package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Counts real selected-player dispatches with cached media and a decoder-free player. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w400dp-h800dp-xhdpi")
public class ShitTokSelectedPreparationTest {
    @Test public void cachedSelectionDispatchesOnceAndRespectsHostState() {
        ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup();
        ChaosFeedView feed = new ChaosFeedView(host.get(), item -> { }, false);
        Object holder = null;
        try {
            // Stop source work before installing a fixture. The selected stream is cached.
            feed.close();
            NativeContentItem item = new NativeContentItem(NativeContentItem.KIND_MEDIA,
                    "Fixture", "https://example.invalid/clip", "", "", "", "");
            ArrayList<NativeContentItem> items = ReflectionHelpers.getField(feed, "items");
            items.add(item);
            ViewPager2 pager = ReflectionHelpers.getField(feed, "pager");
            pager.getAdapter().notifyDataSetChanged();
            host.get().setContentView(feed);
            feed.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
            feed.layout(0, 0, 800, 1600);
            RecyclerView recycler = (RecyclerView) pager.getChildAt(0);
            holder = recycler.findViewHolderForAdapterPosition(0);
            assertNotNull(holder);
            CrazyShitRepository.StreamInfo stream = new CrazyShitRepository.StreamInfo(
                    "https://example.invalid/clip.mp4", item.url, item.title);
            Map<String, CrazyShitRepository.StreamInfo> cache =
                    ReflectionHelpers.getField(feed, "streamCache");
            cache.put(item.url, stream);
            ShitTokAudioOwnershipTest.FakePlayer fake = new ShitTokAudioOwnershipTest.FakePlayer();
            ShitTokPlaybackOwner owner = ReflectionHelpers.getField(feed, "playbackOwner");
            owner.setPlayerFactoryForTest(() -> fake.player);
            ReflectionHelpers.setField(feed, "closed", false);
            ReflectionHelpers.setField(feed, "active", true);
            ReflectionHelpers.setField(feed, "hostResumed", true);
            owner.permission(true, false);
            owner.select(item.url, null, null, false);
            owner.source(item.url, stream, owner.token(item.url));
            int initiallyPlayed = fake.plays;
            for (int i = 0; i < 100; i++) {
                ReflectionHelpers.callInstanceMethod(feed, "playSelected");
            }
            System.out.println("SELECTED_PLAYER_DISPATCHES selections=100 playCalls=" + (fake.plays - initiallyPlayed));
            assertEquals("Cached same-page dispatch must not restart the retained player", 0, fake.plays - initiallyPlayed);
            ReflectionHelpers.setField(feed, "active", false);
            ReflectionHelpers.callInstanceMethod(feed, "playSelected");
            assertEquals("Cached same-page dispatch must not restart the retained player", 0, fake.plays - initiallyPlayed);
            ReflectionHelpers.setField(feed, "active", true);
            ReflectionHelpers.setField(feed, "hostResumed", false);
            ReflectionHelpers.callInstanceMethod(feed, "playSelected");
            assertEquals("Cached same-page dispatch must not restart the retained player", 0, fake.plays - initiallyPlayed);
        } finally {
            if (holder != null) ReflectionHelpers.setField(holder, "player", null);
            feed.close();
            host.pause().stop().destroy();
        }
    }
}
