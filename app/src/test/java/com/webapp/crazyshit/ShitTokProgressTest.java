package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.recyclerview.widget.RecyclerView;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "land-w740dp-h360dp")
public class ShitTokProgressTest {
    @Test public void timesFollowPlaybackAndScrubTargetAndResetUnknownDuration() {
        ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup();
        host.get().setTheme(R.style.Theme_CrazyShit);
        ChaosFeedView feed = new ChaosFeedView(host.get(), item -> { });
        RecyclerView.ViewHolder holder = null;
        try {
            RecyclerView.Adapter<?> adapter = ReflectionHelpers.getField(feed, "adapter");
            holder = adapter.onCreateViewHolder(new RecyclerView(host.get()), 0);
            AtomicLong position = new AtomicLong(3000L);
            AtomicLong duration = new AtomicLong(28000L);
            ExoPlayer player = (ExoPlayer) Proxy.newProxyInstance(ExoPlayer.class.getClassLoader(),
                    new Class<?>[] { ExoPlayer.class }, (proxy, method, args) -> {
                        if ("getDuration".equals(method.getName())) return duration.get();
                        if ("getCurrentPosition".equals(method.getName())) return position.get();
                        if ("seekTo".equals(method.getName())) position.set((Long) args[0]);
                        if ("isPlaying".equals(method.getName())) return true;
                        if (method.getReturnType() == boolean.class) return false;
                        if (method.getReturnType() == int.class) return 0;
                        if (method.getReturnType() == long.class) return 0L;
                        if (method.getReturnType() == float.class) return 0f;
                        return null;
                    });
            ReflectionHelpers.setField(holder, "player", player);
            ReflectionHelpers.setField(holder, "boundPosition", 0);
            ReflectionHelpers.setField(feed, "active", true);
            ReflectionHelpers.setField(feed, "hostResumed", true);
            ReflectionHelpers.callInstanceMethod(holder, "syncOrientationChrome");
            TextView elapsed = holder.itemView.findViewWithTag("shittok_elapsed");
            TextView remaining = holder.itemView.findViewWithTag("shittok_remaining");
            assertEquals("0:03", elapsed.getText().toString());
            assertEquals("-0:25", remaining.getText().toString());
            position.set(4000L);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
            assertEquals("0:04", elapsed.getText().toString());
            assertEquals("-0:24", remaining.getText().toString());
            SeekBar bar = ReflectionHelpers.getField(holder, "seekBar");
            SeekBar.OnSeekBarChangeListener listener = ReflectionHelpers.getField(bar, "mOnSeekBarChangeListener");
            listener.onStartTrackingTouch(bar);
            androidx.viewpager2.widget.ViewPager2 pager = ReflectionHelpers.getField(feed, "pager");
            assertFalse(pager.isUserInputEnabled());
            listener.onProgressChanged(bar, 500, true);
            assertEquals(14000L, position.get());
            assertEquals("0:14", elapsed.getText().toString());
            assertEquals("-0:14", remaining.getText().toString());
            listener.onStopTrackingTouch(bar);
            assertTrue(pager.isUserInputEnabled());
            duration.set(-9223372036854775807L);
            ReflectionHelpers.callInstanceMethod(holder, "updateProgress");
            assertFalse(bar.isEnabled());
            assertEquals("0:00", elapsed.getText().toString());
            assertEquals("-0:00", remaining.getText().toString());
            feed.onHostPause();
            position.set(20000L);
            duration.set(28000L);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
            assertEquals("0:00", elapsed.getText().toString());
        } finally {
            if (holder != null) {
                ReflectionHelpers.setField(holder, "player", null);
                ReflectionHelpers.callInstanceMethod(holder, "releasePlayer");
            }
            feed.close();
            host.pause().stop().destroy();
        }
    }

    @Test public void timeFormattingDoesNotWrapLongClips() {
        assertEquals("0:00", ChaosFeedView.formatPlaybackTime(-1L));
        assertEquals("1:05", ChaosFeedView.formatPlaybackTime(65000L));
        assertEquals("60:03", ChaosFeedView.formatPlaybackTime(3603000L));
    }
}
