package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.webkit.WebView;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokWomenShowAcquisitionTest {
    @Test public void emptyBroadCacheWomenStartsRealHarvesterWithWeakActivityHostAndReceivesStory() throws Exception {
        ExecutorService io = Executors.newFixedThreadPool(2);
        ShitShowTapSource show = new ShitShowTapSource();
        try (org.robolectric.android.controller.ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup()) {
            ShitShowPlayableResolver.rememberHost(host.get());
            ShitTokPoolDiagnostics.setEnabled(true);
            ShitTokPoolDiagnostics.beginMode(ShitTokSourceMode.ALL, ContentPreference.WOMEN);
            AtomicInteger broadCacheReads = new AtomicInteger(), ready = new AtomicInteger();
            // Deliberately supply only Application context to the real production backend.
            ShitTokFilteredInventory.Backend actual = ShitTokFilteredInventory.backend(host.get().getApplicationContext(), show,
                    () -> { broadCacheReads.incrementAndGet(); return Collections.emptyList(); });
            ShitTokFilteredInventory inv = new ShitTokFilteredInventory(io, () -> 0L,
                    new ShitTokFilteredInventory.Backend() {
                        public OnlyHavenRepository.CategoryPage creators(ContentPreference m, int o) { throw new AssertionError(); }
                        public List<NativeContentItem> media(int source, ShitTokFilteredInventory.Cursor c) { throw new AssertionError(); }
                        public List<NativeContentItem> show() throws Exception { return actual.show(); }
                        public boolean available(int source) { return source == 4; }
                    });
            try {
                inv.request(ContentPreference.WOMEN, true, ready::incrementAndGet);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                WebView web = null;
                while (web == null && System.nanoTime() < deadline) {
                    Shadows.shadowOf(Looper.getMainLooper()).idle();
                    web = ReflectionHelpers.getField(show, "webView");
                    Thread.yield();
                }
                assertNotNull("Women must actually start a harvest, not merely expose a loader", web);
                assertTrue(Shadows.shadowOf(web).getLastLoadedUrl().endsWith("/shitshow/"));
                assertEquals(1, broadCacheReads.get());
                // Simulate the site's JS bridge response after verifying the real WebView request.
                Class<?> bridgeClass = Arrays.stream(ShitShowTapSource.class.getDeclaredClasses())
                        .filter(c -> c.getSimpleName().equals("Bridge")).findFirst().get();
                java.lang.reflect.Constructor<?> constructor = bridgeClass.getDeclaredConstructor(ShitShowTapSource.class, long.class);
                constructor.setAccessible(true);
                Object bridge = constructor.newInstance(show, (long) ReflectionHelpers.getField(show, "generation"));
                ReflectionHelpers.callInstanceMethod(bridge, "onStory", from(String.class,
                        "{\"media\":\"https://fixture.invalid/direct.mp4\",\"title\":\"Fixture\",\"width\":720,\"height\":1280}"));
                while (!inv.hasReady(ContentPreference.WOMEN) && System.nanoTime() < deadline) Thread.yield();
                List<NativeContentItem> clips = inv.take(ContentPreference.WOMEN);
                assertEquals(1, clips.size()); assertTrue(inv.matches(clips.get(0), ContentPreference.WOMEN));
                assertFalse(inv.matches(clips.get(0), ContentPreference.MEN));
                assertTrue(ShitTokPoolDiagnostics.modeSummary().contains("showRaw=1"));
                assertTrue(ready.get() > 0);
            } finally { inv.pause(); }
        } finally { show.stop(); io.shutdownNow(); Shadows.shadowOf(Looper.getMainLooper()).idle(); }
    }
    @Test public void playbackProtectionDefersInsteadOfEscalatingEmptyCatalogBackoffAndRecoveryStartsHarvest() {
        AtomicLong clock = new AtomicLong();
        ShitShowTapSource show = new ShitShowTapSource();
        ShitTokSourcePool pool = new ShitTokSourcePool(new ShitTokFilteredInventoryTest.Direct(), clock::get);
        try (org.robolectric.android.controller.ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup()) {
            ShitShowPlayableResolver.rememberHost(host.get()); show.setPlaybackProtected(true);
            for (int i = 0; i < 8; i++) {
                pool.request(4, true, true, () -> show.takeBatchOrWarm(host.get().getApplicationContext(), 12));
                assertEquals(ShitTokSourcePool.Health.IDLE, pool.health(4));
                clock.addAndGet(2000L);
            }
            show.setPlaybackProtected(false);
            show.prewarm(host.get().getApplicationContext());
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNotNull(ReflectionHelpers.getField(show, "webView"));
        } finally { show.stop(); pool.stop(); Shadows.shadowOf(Looper.getMainLooper()).idle(); }
    }
}
