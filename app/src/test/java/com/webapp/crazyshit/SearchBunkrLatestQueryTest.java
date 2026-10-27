package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, shadows = {
        SearchBunkrLatestQueryTest.BlockingBunkr.class
})
public final class SearchBunkrLatestQueryTest {
    @Implements(value = BunkrRepository.class, isInAndroidSdk = false)
    public static class BlockingBunkr {
        static final AtomicInteger calls = new AtomicInteger();
        static volatile CountDownLatch bothStarted = new CountDownLatch(2);
        static volatile CountDownLatch release = new CountDownLatch(1);

        @Implementation protected List<NativeContentItem> searchAlbums(
                Context context, String query, int page) {
            calls.incrementAndGet();
            bothStarted.countDown();
            while (release.getCount() > 0) {
                try { release.await(50, TimeUnit.MILLISECONDS); }
                catch (InterruptedException ignored) { }
            }
            return Collections.singletonList(new NativeContentItem(
                    NativeContentItem.KIND_SERIES,
                    query,
                    "https://bunkr.cr/a/" + query.replace(' ', '-'),
                    "", "", "Bunkr", ""));
        }
    }

    @Before public void reset() {
        BlockingBunkr.calls.set(0);
        BlockingBunkr.bothStarted = new CountDownLatch(2);
        BlockingBunkr.release = new CountDownLatch(1);
    }

    @Test public void newerBunkrQueryStartsWhileOldSocketIgnoresCancellation() throws Exception {
        ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup();
        SearchGlassOwner owner = new SearchGlassOwner(host.get());
        SearchPanel panel = new SearchPanel(host.get(), new Intent(), true, () -> {});
        panel.create(null);
        host.get().setContentView(panel.view);
        try {
            chip(panel, "Bunkr").performClick();
            panel.field().setText("first query");
            ReflectionHelpers.callInstanceMethod(panel, "runSearch");
            await(() -> BlockingBunkr.calls.get() == 1);

            panel.field().setText("second query");
            ReflectionHelpers.callInstanceMethod(panel, "runSearch");

            assertTrue("new query should not queue behind the old socket",
                    BlockingBunkr.bothStarted.await(3, TimeUnit.SECONDS));
            assertEquals(2, BlockingBunkr.calls.get());
        } finally {
            BlockingBunkr.release.countDown();
            panel.close();
            owner.close();
            host.pause().stop().destroy();
        }
    }

    private static TextView chip(SearchPanel panel, String label) {
        for (TextView chip : ReflectionHelpers.<List<TextView>>getField(panel, "scopeViews")) {
            if (label.contentEquals(chip.getText())) return chip;
        }
        throw new AssertionError(label);
    }

    private static void await(java.util.function.BooleanSupplier done) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!done.getAsBoolean() && System.nanoTime() < end) {
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10);
        }
        assertTrue(done.getAsBoolean());
    }
}
