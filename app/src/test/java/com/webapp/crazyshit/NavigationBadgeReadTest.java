package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import android.os.Looper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Implementation;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35,
        shadows = {NavigationBadgeReadTest.Inbox.class, NavigationBadgeReadTest.Session.class})
public class NavigationBadgeReadTest {
    @Implements(value = UpdateInboxStore.class, isInAndroidSdk = false)
    public static class Inbox {
        static CountDownLatch entered, release;
        static AtomicInteger mainCalls;
        @Implementation protected static int unreadCount(Context context) {
            if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
                mainCalls.incrementAndGet();
                return 7;
            }
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return 7;
        }
    }
    @Implements(value = ZeroChillSessionStore.class, isInAndroidSdk = false)
    public static class Session {
        static volatile String owner;
        @Implementation protected static String currentUserId(Context context) { return owner; }
    }

    @Test public void blockedInboxReadDoesNotBlockTabsOrBackAndCannotPaintAnotherAccount() throws Exception {
        Inbox.entered = new CountDownLatch(1); Inbox.release = new CountDownLatch(1);
        Inbox.mainCalls = new AtomicInteger(); Session.owner = "owner-a";
        RuntimeEnvironment.getApplication().getSharedPreferences("app_prefs", 0).edit()
                .putBoolean("access_notice_2_8_3_accepted", true).commit();
        ActivityController<NativeMainActivity> host = Robolectric.buildActivity(NativeMainActivity.class)
                .create().start().resume().visible();
        try {
            NativeMainActivity activity = host.get();
            assertEquals("Startup/resume must not count notifications on main", 0, Inbox.mainCalls.get());
            assertTrue("Background badge reader should run", Inbox.entered.await(5, TimeUnit.SECONDS));
            ZeroChillBottomNavigationView nav = ReflectionHelpers.getField(activity, "bottomNavigation");
            nav.setSelectedItemId(6);
            activity.onBackPressed();
            assertFalse(activity.isFinishing());
            assertEquals(0, Inbox.mainCalls.get());
            Future<?> pending = ReflectionHelpers.getField(activity, "libraryBadgeRead");
            Session.owner = "owner-b";
            Inbox.release.countDown();
            pending.get(5, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertFalse("Account A badge must not paint account B", ReflectionHelpers.<Boolean>getField(nav, "libraryUnread"));
        } finally {
            Inbox.release.countDown();
            host.pause().stop().destroy();
        }
    }
}
