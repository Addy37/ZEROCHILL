package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;

import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.Player;
import androidx.recyclerview.widget.RecyclerView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.robolectric.Shadows.shadowOf;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokAutoScrollIntegrationTest {
    private ChaosFeedView feed(Activity activity, int size) {
        ChaosFeedView feed = new ChaosFeedView(activity, item -> { });
        // Stop the constructor's asynchronous source request from altering the fake deck.
        ReflectionHelpers.setField(feed, "closed", true);
        List<NativeContentItem> items = ReflectionHelpers.getField(feed, "items");
        for (int i = 0; i < size; i++) {
            items.add(new NativeContentItem(NativeContentItem.KIND_MEDIA,
                    "Video " + i, "https://example.invalid/video/" + i,
                    "", "", "", ""));
        }
        RecyclerView.Adapter<?> adapter = ReflectionHelpers.getField(feed, "adapter");
        adapter.notifyDataSetChanged();
        ReflectionHelpers.setField(feed, "active", true);
        activity.setContentView(feed);
        feed.measure(android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(800, android.view.View.MeasureSpec.EXACTLY));
        feed.layout(0, 0, 400, 800);
        return feed;
    }

    private void request(ChaosFeedView feed, int from) {
        ReflectionHelpers.callInstanceMethod(feed, "requestAutoAdvance",
                ReflectionHelpers.ClassParameter.from(int.class, from));
    }

    @Test public void duplicateAndStaleQueuedAdvanceAreCancelled() {
        ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup();
        host.get().setTheme(R.style.Theme_CrazyShit);
        ChaosFeedView feed = feed(host.get(), 2);
        try {
            request(feed, 0);
            int generation = ReflectionHelpers.getField(feed, "autoAdvanceGeneration");
            request(feed, 0);
            assertEquals(generation, (int) ReflectionHelpers.getField(feed, "autoAdvanceGeneration"));
            assertTrue(ReflectionHelpers.getField(feed, "autoAdvancePending"));
            androidx.viewpager2.widget.ViewPager2 pager = ReflectionHelpers.getField(feed, "pager");
            pager.setCurrentItem(1, false);
            assertFalse(ReflectionHelpers.getField(feed, "autoAdvancePending"));
            pager.setCurrentItem(0, false);
            assertEquals(0, (int) ReflectionHelpers.getField(feed, "selectedPosition"));
            request(feed, 0);
            ReflectionHelpers.callInstanceMethod(feed, "setAutoScrollEnabled",
                    ReflectionHelpers.ClassParameter.from(boolean.class, true));
            assertFalse(ReflectionHelpers.getField(feed, "autoAdvancePending"));
            assertTrue((int) ReflectionHelpers.getField(feed, "autoAdvanceGeneration") > generation);
            // After A -> B -> A and a toggle, old posted callbacks cannot move the new A.
            assertEquals(-1, (int) ReflectionHelpers.getField(feed, "autoAdvanceIssuedFrom"));
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(0, pager.getCurrentItem());
            feed.onHostPause();
            assertFalse(ReflectionHelpers.getField(feed, "autoAdvancePending"));
        } finally {
            feed.close();
            host.pause().stop().destroy();
        }
    }

    @Test public void endedSelectedPlayerLoopsOffAndQueuesNextOn() {
        ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup();
        host.get().setTheme(R.style.Theme_CrazyShit);
        ChaosFeedView feed = feed(host.get(), 2);
        try {
            RecyclerView.Adapter<?> adapter = ReflectionHelpers.getField(feed, "adapter");
            RecyclerView.ViewHolder holder = adapter.onCreateViewHolder(new RecyclerView(host.get()), 0);
            AtomicInteger seeks = new AtomicInteger();
            AtomicInteger plays = new AtomicInteger();
            ExoPlayer player = (ExoPlayer) Proxy.newProxyInstance(
                    ExoPlayer.class.getClassLoader(), new Class<?>[] { ExoPlayer.class },
                    (proxy, method, args) -> {
                        if ("seekTo".equals(method.getName())) seeks.incrementAndGet();
                        if ("play".equals(method.getName())) plays.incrementAndGet();
                        if (method.getReturnType() == boolean.class) return false;
                        if (method.getReturnType() == int.class) return 0;
                        if (method.getReturnType() == long.class) return 0L;
                        if (method.getReturnType() == float.class) return 0f;
                        return null;
                    });
            ReflectionHelpers.setField(holder, "player", player);
            ReflectionHelpers.setField(holder, "boundPosition", 0);
            ReflectionHelpers.callInstanceMethod(holder, "handleCompletion",
                    ReflectionHelpers.ClassParameter.from(ExoPlayer.class, player));
            assertEquals(1, seeks.get());
            assertEquals(1, plays.get());
            ReflectionHelpers.setField(holder, "userPaused", true);
            ReflectionHelpers.callInstanceMethod(holder, "handleCompletion",
                    ReflectionHelpers.ClassParameter.from(ExoPlayer.class, player));
            assertEquals(1, plays.get());
            ReflectionHelpers.setField(holder, "userPaused", false);
            ReflectionHelpers.callInstanceMethod(feed, "setAutoScrollEnabled",
                    ReflectionHelpers.ClassParameter.from(boolean.class, true));
            ReflectionHelpers.callInstanceMethod(holder, "handleCompletion",
                    ReflectionHelpers.ClassParameter.from(ExoPlayer.class, player));
            assertTrue(ReflectionHelpers.getField(feed, "autoAdvancePending"));
            assertEquals(1, plays.get());
            shadowOf(Looper.getMainLooper()).idle();
            androidx.viewpager2.widget.ViewPager2 pager = ReflectionHelpers.getField(feed, "pager");
            assertEquals(1, pager.getCurrentItem());
            assertFalse(ReflectionHelpers.getField(feed, "autoAdvancePending"));
            feed.onHostPause();
            assertFalse(ReflectionHelpers.getField(feed, "autoAdvancePending"));
        } finally {
            // Prevent this synthetic holder's fake player from entering release paths.
            feed.close();
            host.pause().stop().destroy();
        }
    }

    @Test public void turningOffPendingAutoScrollReplaysSelectedEndedClip() {
        ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup();
        host.get().setTheme(R.style.Theme_CrazyShit);
        ChaosFeedView feed = feed(host.get(), 2);
        try {
            androidx.viewpager2.widget.ViewPager2 pager = ReflectionHelpers.getField(feed, "pager");
            RecyclerView recycler = (RecyclerView) pager.getChildAt(0);
            RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(0);
            assertNotNull(holder);
            AtomicInteger seeks = new AtomicInteger();
            AtomicInteger plays = new AtomicInteger();
            ExoPlayer ended = (ExoPlayer) Proxy.newProxyInstance(
                    ExoPlayer.class.getClassLoader(), new Class<?>[] { ExoPlayer.class },
                    (proxy, method, args) -> {
                        if ("getPlaybackState".equals(method.getName())) return Player.STATE_ENDED;
                        if ("seekTo".equals(method.getName())) seeks.incrementAndGet();
                        if ("play".equals(method.getName())) plays.incrementAndGet();
                        if (method.getReturnType() == boolean.class) return false;
                        if (method.getReturnType() == int.class) return 0;
                        if (method.getReturnType() == long.class) return 0L;
                        if (method.getReturnType() == float.class) return 0f;
                        return null;
                    });
            ReflectionHelpers.setField(holder, "player", ended);
            ReflectionHelpers.callInstanceMethod(feed, "setAutoScrollEnabled",
                    ReflectionHelpers.ClassParameter.from(boolean.class, true));
            request(feed, 0);
            assertTrue(ReflectionHelpers.getField(feed, "autoAdvancePending"));
            ReflectionHelpers.callInstanceMethod(feed, "setAutoScrollEnabled",
                    ReflectionHelpers.ClassParameter.from(boolean.class, false));
            assertFalse(ReflectionHelpers.getField(feed, "autoAdvancePending"));
            assertEquals(1, seeks.get());
            assertEquals(1, plays.get());
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(0, pager.getCurrentItem());
        } finally {
            feed.close();
            host.pause().stop().destroy();
        }
    }
}
