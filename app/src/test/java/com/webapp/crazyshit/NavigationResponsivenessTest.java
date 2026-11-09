package com.webapp.crazyshit;

import android.app.Application;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.TextView;
import androidx.viewpager2.widget.ViewPager2;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class NavigationResponsivenessTest {
    private ActivityController<NativeMainActivity> launch() {
        RuntimeEnvironment.getApplication().getSharedPreferences("app_prefs", 0).edit()
                .putBoolean("access_notice_2_8_3_accepted", true)
                .putBoolean("immersive_motion_enabled", true).commit();
        return Robolectric.buildActivity(NativeMainActivity.class).create().start().resume().visible();
    }

    @Test public void tabTapAndBackSettleImmediatelyAndRetainPages() {
        ActivityController<NativeMainActivity> host = launch();
        try {
            NativeMainActivity activity = host.get();
            ViewPager2 pager = ReflectionHelpers.getField(activity, "primaryPager");
            MainPagerAdapter adapter = ReflectionHelpers.getField(activity, "primaryPagerAdapter");
            Object chaos = ReflectionHelpers.getField(adapter, "chaosView");
            Object library = ReflectionHelpers.getField(adapter, "libraryView");
            activity.findViewById(android.R.id.content).layout(0, 0, 1080, 2400);
            ZeroChillBottomNavigationView nav = ReflectionHelpers.getField(activity, "bottomNavigation");
            nav.setSelectedItemId(6); // Library: nonadjacent retained destination.
            System.out.println("NAV_PROBE tab scrollState=" + pager.getScrollState());
            assertEquals(ViewPager2.SCROLL_STATE_IDLE, pager.getScrollState());
            activity.onBackPressed();
            System.out.println("NAV_PROBE back scrollState=" + pager.getScrollState());
            assertEquals(MainPagerAdapter.pagerPositionForPage(MainPagerAdapter.PAGE_CHAOS), pager.getCurrentItem());
            assertEquals(ViewPager2.SCROLL_STATE_IDLE, pager.getScrollState());
            assertSame(adapter, pager.getAdapter());
            assertSame(chaos, ReflectionHelpers.getField(adapter, "chaosView"));
            assertSame(library, ReflectionHelpers.getField(adapter, "libraryView"));
            assertFalse(activity.isFinishing());
            activity.onBackPressed();
            assertTrue(activity.isFinishing());
        } finally { host.pause().stop().destroy(); }
    }

    @Test public void backClosesSearchBeforeNavigatingAndKeepsRetainedDestination() {
        ActivityController<NativeMainActivity> host = launch();
        try {
            NativeMainActivity activity = host.get();
            ZeroChillBottomNavigationView nav = ReflectionHelpers.getField(activity, "bottomNavigation");
            ViewPager2 pager = ReflectionHelpers.getField(activity, "primaryPager");
            Object adapter = pager.getAdapter();
            nav.setSelectedItemId(6);
            activity.openContextualSearch();
            assertNotNull(ReflectionHelpers.getField(activity, "searchPanel"));
            activity.onBackPressed();
            assertNull("Search close must not wait for its opening layout or exit animation",
                    ReflectionHelpers.getField(activity, "searchPanel"));
            assertEquals(MainPagerAdapter.pagerPositionForPage(MainPagerAdapter.PAGE_LIBRARY), pager.getCurrentItem());
            assertSame(adapter, pager.getAdapter());
            assertFalse(activity.isFinishing());
            activity.onBackPressed();
            assertEquals(MainPagerAdapter.pagerPositionForPage(MainPagerAdapter.PAGE_CHAOS), pager.getCurrentItem());
        } finally { host.pause().stop().destroy(); }
    }

    @Test public void tabChromeIsBoundOnceAndReselectionDoesNotRebind() {
        ActivityController<NativeMainActivity> host = launch();
        try {
            NativeMainActivity activity = host.get();
            TextView title = ReflectionHelpers.getField(activity, "headerTitle");
            int[] writes = {0};
            title.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) { writes[0]++; }
                public void afterTextChanged(Editable s) {}
            });
            ZeroChillBottomNavigationView nav = ReflectionHelpers.getField(activity, "bottomNavigation");
            nav.setSelectedItemId(6);
            System.out.println("NAV_PROBE title writes per tab=" + writes[0]);
            assertEquals(1, writes[0]);
            ReflectionHelpers.callInstanceMethod(activity, "showPrimaryPage",
                    ReflectionHelpers.ClassParameter.from(int.class, MainPagerAdapter.PAGE_LIBRARY));
            assertEquals(1, writes[0]);
        } finally { host.pause().stop().destroy(); }
    }
}
