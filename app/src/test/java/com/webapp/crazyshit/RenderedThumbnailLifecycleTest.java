package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class RenderedThumbnailLifecycleTest {
    @Test public void pausedResolverKeepsRequestsAndRejectsOldResultsAfterResume() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        List<String> results = new ArrayList<>();
        RenderedThumbnailResolver resolver = new RenderedThumbnailResolver(activity, (page, image) -> results.add(image));
        ExecutorService original = ReflectionHelpers.getField(resolver, "io"); original.shutdownNow();
        ManualExecutor io = new ManualExecutor(); ReflectionHelpers.setField(resolver, "io", io);
        try {
            resolver.setActive(false);
            resolver.request("https://fixture.invalid/library/video");
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue(io.tasks.isEmpty());
            resolver.setActive(true);
            assertEquals(1, io.tasks.size());
            int stale = ReflectionHelpers.getField(resolver, "generation");
            resolver.setActive(false);
            assertFalse((boolean) ReflectionHelpers.getField(resolver, "busy"));
            resolver.setActive(true);
            assertEquals(2, io.tasks.size());
            int current = ReflectionHelpers.getField(resolver, "generation");
            finish(resolver, stale, "https://fixture.invalid/stale.jpg");
            assertTrue(results.isEmpty());
            assertTrue((boolean) ReflectionHelpers.getField(resolver, "busy"));
            finish(resolver, current, "https://fixture.invalid/current.jpg");
            assertEquals(Collections.singletonList("https://fixture.invalid/current.jpg"), results);
            resolver.close();
            finish(resolver, current, "https://fixture.invalid/after-close.jpg");
            assertEquals(1, results.size());
        } finally { resolver.close(); }
    }

    private void finish(RenderedThumbnailResolver resolver, int token, String image) {
        ReflectionHelpers.callInstanceMethod(resolver, "finish",
                ReflectionHelpers.ClassParameter.from(int.class, token),
                ReflectionHelpers.ClassParameter.from(String.class, "https://fixture.invalid/library/video"),
                ReflectionHelpers.ClassParameter.from(String.class, image));
    }
    private static final class ManualExecutor extends AbstractExecutorService {
        final List<Runnable> tasks = new ArrayList<>();
        public void execute(Runnable r) { tasks.add(r); }
        public void shutdown() { }
        public List<Runnable> shutdownNow() { return Collections.emptyList(); }
        public boolean isShutdown() { return false; }
        public boolean isTerminated() { return false; }
        public boolean awaitTermination(long t, TimeUnit u) { return true; }
    }
}
