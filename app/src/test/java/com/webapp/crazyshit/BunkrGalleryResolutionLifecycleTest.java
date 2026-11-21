package com.webapp.crazyshit;

import android.app.AlertDialog;
import android.app.Application;
import android.app.Dialog;
import android.content.Intent;
import android.os.Looper;
import android.widget.TextView;

import androidx.viewpager2.widget.ViewPager2;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.util.ReflectionHelpers;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35,
        shadows = BunkrGalleryResolutionLifecycleTest.BlockedBunkrPage.class)
public class BunkrGalleryResolutionLifecycleTest {
    private static final String PAGE = "https://bunkr.cr/f/blocked-video";
    private static final String MAINTENANCE_PAGE = "https://bunkr.cr/f/maintenance-fixture";

    @Implements(value = Jsoup.class, isInAndroidSdk = false)
    public static class BlockedBunkrPage {
        static CountDownLatch entered;
        static CountDownLatch release;

        @Implementation protected static Connection connect(String url) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if ("get".equals(method.getName())) {
                            if (url.endsWith("/f/maintenance-fixture")) {
                                return Jsoup.parse(BunkrResolutionTest.MAINTENANCE_FILE_PAGE, url);
                            }
                            entered.countDown();
                            try {
                                if (!release.await(3, TimeUnit.SECONDS))
                                    throw new IOException("fixture timed out");
                            } catch (InterruptedException error) {
                                Thread.currentThread().interrupt();
                                throw new IOException(error);
                            }
                            throw new IOException("fixture resolution failed");
                        }
                        if (method.getReturnType().isInstance(proxy)) return proxy;
                        return null;
                    });
        }
    }

    @Test public void tappingMaintenanceFileShowsRetryInGallery() throws Exception {
        BunkrMaintenance.retry(MAINTENANCE_PAGE);
        NativeContentItem item = video(MAINTENANCE_PAGE);
        String session = BunkrGallerySessionStore.create("Gallery", "");
        BunkrGallerySessionStore.replace(session, Collections.singletonList(item), 1, true);
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), BunkrGalleryActivity.class)
                .putExtra(BunkrGalleryActivity.EXTRA_SESSION_ID, session);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (ActivityController<BunkrGalleryActivity> controller =
                     Robolectric.buildActivity(BunkrGalleryActivity.class, intent).create()) {
            BunkrGalleryActivity activity = controller.get();
            ReflectionHelpers.setField(activity, "mediaIo", worker);
            ReflectionHelpers.callInstanceMethod(activity, "onMediaTap",
                    ReflectionHelpers.ClassParameter.from(int.class, 0),
                    ReflectionHelpers.ClassParameter.from(NativeContentItem.class, item));
            worker.submit(() -> { }).get(2, TimeUnit.SECONDS);
            shadowOf(Looper.getMainLooper()).idle();

            assertTrue(BunkrMaintenance.isKnown(MAINTENANCE_PAGE));
            AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
            assertNotNull(dialog);
            TextView message = dialog.findViewById(android.R.id.message);
            assertEquals(activity.getString(R.string.bunkr_maintenance_message), message.getText());
            assertEquals(activity.getString(R.string.bunkr_maintenance_retry),
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText());
            dialog.dismiss();
        } finally {
            worker.shutdownNow();
            BunkrMaintenance.retry(MAINTENANCE_PAGE);
        }
    }

    @Test public void failedOnDemandResolutionAfterSwipeDoesNotShowDialogOrFailEitherPage()
            throws Exception {
        BlockedBunkrPage.entered = new CountDownLatch(1);
        BlockedBunkrPage.release = new CountDownLatch(1);
        NativeContentItem first = video(PAGE);
        NativeContentItem second = video("https://example.com/second-video");
        String session = BunkrGallerySessionStore.create("Gallery", "");
        BunkrGallerySessionStore.replace(session, Arrays.asList(first, second), 1, true);
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), BunkrGalleryActivity.class)
                .putExtra(BunkrGalleryActivity.EXTRA_SESSION_ID, session);
        ActivityController<BunkrGalleryActivity> controller =
                Robolectric.buildActivity(BunkrGalleryActivity.class, intent).create();
        BunkrGalleryActivity activity = controller.get();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        ReflectionHelpers.setField(activity, "mediaIo", worker);
        try {
            ViewPager2 pager = ReflectionHelpers.getField(activity, "pager");
            BunkrGalleryPagerAdapter adapter = ReflectionHelpers.getField(activity, "adapter");
            Dialog previousDialog = ShadowDialog.getLatestDialog();
            ReflectionHelpers.callInstanceMethod(activity, "playVideo",
                    ReflectionHelpers.ClassParameter.from(int.class, 0),
                    ReflectionHelpers.ClassParameter.from(NativeContentItem.class, first));
            assertTrue("resolver should start", BlockedBunkrPage.entered.await(2, TimeUnit.SECONDS));
            pager.setCurrentItem(1, false);
            assertEquals(1, pager.getCurrentItem());
            BlockedBunkrPage.release.countDown();
            worker.submit(() -> { }).get(2, TimeUnit.SECONDS);
            shadowOf(Looper.getMainLooper()).idle();

            assertFalse(adapter.isFailed(0));
            assertFalse(adapter.isFailed(1));
            assertSame(previousDialog, ShadowDialog.getLatestDialog());
        } finally {
            BlockedBunkrPage.release.countDown();
            controller.destroy();
            worker.shutdownNow();
        }
    }

    private static NativeContentItem video(String url) {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, "Video", url,
                "", "", "", "");
    }
}
