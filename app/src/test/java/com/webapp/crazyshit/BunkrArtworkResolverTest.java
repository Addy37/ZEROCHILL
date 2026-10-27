package com.webapp.crazyshit;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.app.Application;
import android.content.Context;
import android.os.Looper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, shadows = BunkrArtworkResolverTest.Metadata.class)
public class BunkrArtworkResolverTest {
    @Implements(value = BunkrRepository.class, isInAndroidSdk = false)
    public static class Metadata {
        static AtomicInteger calls = new AtomicInteger();
        static CountDownLatch started = new CountDownLatch(1);
        static CountDownLatch release = new CountDownLatch(1);
        @Implementation protected String fetchFileArtwork(Context context, String page, String rejected) {
            calls.incrementAndGet(); started.countDown();
            try { release.await(3, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { }
            return "https://static.scdn.st/thumbs/repaired.png";
        }
    }

    @Test public void duplicateVisibleTilesShareOneMetadataRequestAndThenUseCache() throws Exception {
        AtomicInteger delivered = new AtomicInteger();
        Context context = RuntimeEnvironment.getApplication();
        try {
            assertTrue(BunkrArtworkResolver.request(context, "https://bunkr.cr/f/singleflight", "",
                    image -> { assertTrue(image.endsWith("repaired.png")); delivered.incrementAndGet(); }));
            assertTrue(Metadata.started.await(3, TimeUnit.SECONDS));
            assertTrue(BunkrArtworkResolver.request(context, "https://bunkr.cr/f/singleflight", "",
                    image -> delivered.incrementAndGet()));
            assertEquals(1, Metadata.calls.get());
            Metadata.release.countDown();
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (delivered.get() < 2 && System.nanoTime() < until) {
                Thread.sleep(10); shadowOf(Looper.getMainLooper()).idle();
            }
            assertEquals(2, delivered.get());
            assertTrue(BunkrArtworkResolver.request(context, "https://bunkr.cr/f/singleflight", "",
                    image -> delivered.incrementAndGet()));
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(3, delivered.get());
            assertEquals(1, Metadata.calls.get());
        } finally { Metadata.release.countDown(); }
    }
}
