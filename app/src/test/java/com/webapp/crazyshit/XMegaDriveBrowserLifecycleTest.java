package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebView;
import android.widget.FrameLayout;

import org.jsoup.nodes.Document;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class XMegaDriveBrowserLifecycleTest {
    @Test public void cancelledQueuedStartCreatesNoViewsAndKeepsSessionCookies() throws Exception {
        Browser b = new Browser();
        CookieManager cookies = CookieManager.getInstance();
        cookies.setCookie(b.url, "cf_clearance=lifecycle-fixture");
        cookies.setCookie(b.url, "__cf_bm=browser-fixture");
        b.completed.set(true);
        b.start();
        assertNull(b.web.get());
        assertNull(b.overlay.get());
        String report = XMegaDriveRepository.cookiePresence(cookies.getCookie(b.url));
        assertEquals("cf_clearance=present __cf_bm=present", report);
    }

    @Test public void rendererExitReleasesOwnedWebViewAndSignalsFailure() throws Exception {
        Browser b = new Browser();
        b.start();
        WebView web = b.web.get();
        FrameLayout overlay = b.overlay.get();
        assertNotNull(web);
        assertNotNull(overlay.getParent());
        assertTrue(web.getWebViewClient().onRenderProcessGone(web, new RenderProcessGoneDetail() {
            @Override public boolean didCrash() { return true; }
            @Override public int rendererPriorityAtExit() { return 0; }
        }));
        assertTrue(b.completed.get());
        assertEquals(0, b.finished.getCount());
        assertNotNull(b.error.get());
        assertNull(b.web.get());
        assertNull(b.overlay.get());
        assertNull(overlay.getParent());
        assertTrue(shadowOf(web).wasDestroyCalled());
    }

    @Test public void interruptedFetchCannotCreateBrowserAfterItWaitedForOwnership() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            Thread.currentThread().interrupt();
            try {
                XMegaDriveBrowserFetch.fetch(activity, "https://example.invalid/", "");
                failure.set(new AssertionError("Cancelled fetch returned a page"));
            } catch (IOException expected) {
                if (!Thread.currentThread().isInterrupted()) failure.set(new AssertionError("Lost interrupt"));
            } catch (Throwable error) { failure.set(error); }
        });
        worker.start();
        worker.join(2000);
        assertFalse(worker.isAlive());
        assertNull(failure.get());
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static final class Browser {
        final String url = "https://www.xmegadrive.com/categories/";
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final AtomicReference<WebView> web = new AtomicReference<>();
        final AtomicReference<FrameLayout> overlay = new AtomicReference<>();
        final AtomicInteger generation = new AtomicInteger();
        final AtomicBoolean completed = new AtomicBoolean();
        final AtomicReference<Document> result = new AtomicReference<>();
        final AtomicReference<IOException> error = new AtomicReference<>();
        final CountDownLatch finished = new CountDownLatch(1);
        void start() throws Exception {
            Method start = XMegaDriveBrowserFetch.class.getDeclaredMethod("start",
                    Activity.class, String.class, String.class, AtomicReference.class,
                    AtomicReference.class, AtomicInteger.class, AtomicBoolean.class,
                    AtomicReference.class, AtomicReference.class, CountDownLatch.class);
            start.setAccessible(true);
            start.invoke(null, activity, url, "", web, overlay, generation, completed, result, error, finished);
        }
    }
}
