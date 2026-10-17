package com.webapp.crazyshit;

import android.app.Application;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import androidx.viewpager2.widget.ViewPager2;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Geometry checks; GPU refraction and frame pacing still require a device. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class GlassHeaderLayoutTest {
    @Test public void scrollContentCrossesHeaderWithoutChangingOriginOrRange() {
        var controller = BottomNavigationInsetsTest.createActivity();
        NativeMainActivity activity = controller.get();
        MainPagerAdapter adapter = ReflectionHelpers.getField(activity, "primaryPagerAdapter");
        ViewPager2 pager = ReflectionHelpers.getField(activity, "primaryPager");
        int header = ZeroChillUi.dimension(activity, R.dimen.zc_top_bar_height);
        for (int position : new int[]{0, 3}) {
            MainPagerAdapter.Holder holder = adapter.onCreateViewHolder(pager, 0);
            adapter.onBindViewHolder(holder, position);
            FrameLayout frame = (FrameLayout) holder.itemView;
            View page = frame.getChildAt(0);
            ScrollView scroll;
            if (position == 0) {
                ViewGroup root = (ViewGroup) page;
                ShowsHubView hub = null;
                for (int i = 0; i < root.getChildCount(); i++) {
                    if (root.getChildAt(i) instanceof ShowsHubView) hub = (ShowsHubView) root.getChildAt(i);
                }
                assertNotNull(hub);
                scroll = (ScrollView) hub.getChildAt(0);
                layout(frame);
                assertEquals(-header, scroll.getTop());
            } else {
                scroll = (ScrollView) page;
                layout(frame);
                assertEquals(-header, page.getTop());
            }
            assertEquals(header, scroll.getPaddingTop());
            assertEquals(frame.getHeight() + header, scroll.getHeight());
            assertFalse(scroll.getClipToPadding());
            int originalOrigin = scroll.getChildAt(0).getTop() - header;
            scroll.scrollTo(0, header + 10);
            assertTrue(scroll.getScrollY() > header);
            assertEquals(originalOrigin - scroll.getScrollY(),
                    scroll.getTop() + scroll.getChildAt(0).getTop() - scroll.getScrollY());
            // Padding cancels the extra viewport height in the scroll-range calculation.
            assertEquals(scroll.getChildAt(0).getHeight() - frame.getHeight(),
                    scroll.getChildAt(0).getHeight() + scroll.getPaddingTop() - scroll.getHeight());
        }
        controller.pause().stop().destroy();
    }

    @Test public void onlyFapKeepsItsOriginalBoundsAndClipping() {
        var controller = BottomNavigationInsetsTest.createActivity();
        MainPagerAdapter adapter = ReflectionHelpers.getField(controller.get(), "primaryPagerAdapter");
        ViewPager2 pager = ReflectionHelpers.getField(controller.get(), "primaryPager");
        MainPagerAdapter.Holder holder = adapter.onCreateViewHolder(pager, 0);
        adapter.onBindViewHolder(holder, 2);
        ViewGroup frame = (ViewGroup) holder.itemView;
        assertTrue(frame.getClipChildren());
        assertEquals(0, ((ViewGroup.MarginLayoutParams) frame.getChildAt(0).getLayoutParams()).topMargin);
        assertEquals(0, frame.getChildAt(0).getPaddingTop());
        controller.pause().stop().destroy();
    }

    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1080, 600);
    }
}
