package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class PrimaryTabWorkQueueTest {
    @Test public void refreshWaitsForSettledPageAndOnlyLatestSelectionRuns() {
        Fixture f = new Fixture();
        f.queue.select(1);
        f.queue.submit(1, "refresh", () -> f.calls.add("shows"));
        tick();
        assertTrue(f.calls.isEmpty());
        f.queue.setSettled(true);
        f.queue.select(4);
        f.queue.submit(4, "refresh", () -> f.calls.add("library"));
        tick();
        assertEquals(Arrays.asList("library"), f.calls);
        f.queue.select(1);
        tick();
        assertEquals(Arrays.asList("library", "shows"), f.calls);
        f.queue.close();
    }

    @Test public void dragPauseAndCloseCancelAlreadyPostedWork() {
        Fixture f = new Fixture();
        f.queue.select(4);
        f.queue.setSettled(true);
        f.queue.submit(4, "refresh", () -> f.calls.add("old"));
        f.queue.setSettled(false);
        tick();
        assertTrue(f.calls.isEmpty());
        f.queue.submit(4, "refresh", () -> f.calls.add("latest"));
        f.queue.setSettled(true);
        f.queue.setResumed(false);
        tick();
        assertTrue(f.calls.isEmpty());
        f.queue.setResumed(true);
        tick();
        assertEquals(Arrays.asList("latest"), f.calls);
        f.queue.submit(4, "close", () -> fail("closed work ran"));
        f.queue.close();
        tick();
    }

    static void tick() { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); }
    private static class Fixture {
        final List<String> calls = new ArrayList<>();
        final PrimaryTabWorkQueue queue;
        Fixture() {
            Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
            View view = new View(activity);
            activity.setContentView(view);
            queue = new PrimaryTabWorkQueue(view);
        }
    }
}
