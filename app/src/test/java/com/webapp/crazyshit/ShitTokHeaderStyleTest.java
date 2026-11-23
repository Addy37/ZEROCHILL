package com.webapp.crazyshit;

import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import androidx.viewpager2.widget.ViewPager2;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.*;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokHeaderStyleTest {
    @Test public void traditionalHeaderIsHiddenOnlyForShitTok() throws Exception {
        NativeMainActivity activity = Robolectric.buildActivity(NativeMainActivity.class).setup().get();
        GlassHeaderView header = ReflectionHelpers.getField(activity, "topBar");

        @SuppressWarnings("unchecked")
        Class<Enum> screenClass = (Class<Enum>) Class.forName(
                "com.webapp.crazyshit.NativeMainActivity$Screen");
        ReflectionHelpers.setField(activity, "screen", Enum.valueOf(screenClass, "CHAOS"));
        ReflectionHelpers.callInstanceMethod(activity, "setPrimaryHeaderMaterial",
                from(int.class, MainPagerAdapter.PAGE_CHAOS));
        ReflectionHelpers.callInstanceMethod(activity, "setChaosFullscreenChrome",
                from(boolean.class, false));
        assertEquals(View.GONE, header.getVisibility());
        assertEquals(View.GONE, header.getChildAt(0).getVisibility());
        assertEquals(0, header.getLayoutParams().height);

        // A late appearance pass after landscape fullscreen must not restore
        // the old ShitTok wordmark/search row above the floating category tabs.
        header.setVisibility(View.VISIBLE);
        header.getLayoutParams().height = ZeroChillUi.dimension(activity, R.dimen.zc_top_bar_height);
        ReflectionHelpers.callStaticMethod(OledImmersiveUiController.class,
                "applyMain", from(NativeMainActivity.class, activity));
        assertEquals(View.GONE, header.getVisibility());
        assertEquals(0, header.getLayoutParams().height);

        ReflectionHelpers.setField(activity, "screen", Enum.valueOf(screenClass, "LIBRARY"));
        ReflectionHelpers.callInstanceMethod(activity, "setPrimaryHeaderMaterial",
                from(int.class, MainPagerAdapter.PAGE_LIBRARY));
        ReflectionHelpers.callInstanceMethod(activity, "setChaosFullscreenChrome",
                from(boolean.class, false));
        assertEquals(View.VISIBLE, header.getVisibility());
        assertEquals(View.VISIBLE, header.getChildAt(0).getVisibility());
        assertEquals(ZeroChillUi.dimension(activity, R.dimen.zc_top_bar_height),
                header.getLayoutParams().height);
        assertEquals(Color.TRANSPARENT, ((ColorDrawable) header.getBackground()).getColor());
    }

    @Test public void navigationStaysVisibleNormallyButHidesDuringFocusMode() throws Exception {
        NativeMainActivity activity = Robolectric.buildActivity(NativeMainActivity.class).get();
        ShitTokCategoryStrip tabs = new ShitTokCategoryStrip(activity);
        ReflectionHelpers.setField(activity, "shitTokCategoryStrip", tabs);
        ReflectionHelpers.setField(activity, "primaryPager", new ViewPager2(activity));
        @SuppressWarnings("unchecked")
        Class<Enum> screenClass = (Class<Enum>) Class.forName(
                "com.webapp.crazyshit.NativeMainActivity$Screen");
        ReflectionHelpers.setField(activity, "screen", Enum.valueOf(screenClass, "CHAOS"));

        ReflectionHelpers.callInstanceMethod(activity, "updateShitTokCategoryStrip");
        assertEquals(View.VISIBLE, tabs.getVisibility());
        assertTrue(tabs.getBackground() instanceof GradientDrawable);
        assertEquals(View.VISIBLE, tabs.findViewWithTag("shittok_overlay_search").getVisibility());
        ReflectionHelpers.callInstanceMethod(activity, "updateShitTokCategoryStrip");
        assertEquals(View.VISIBLE, tabs.getVisibility());

        ReflectionHelpers.setField(activity, "chaosClearDisplay", true);
        ReflectionHelpers.callInstanceMethod(activity, "updateShitTokCategoryStrip");
        assertEquals(View.GONE, tabs.getVisibility());
        ReflectionHelpers.setField(activity, "chaosClearDisplay", false);
        ReflectionHelpers.callInstanceMethod(activity, "updateShitTokCategoryStrip");
        assertEquals(View.VISIBLE, tabs.getVisibility());
    }
}
