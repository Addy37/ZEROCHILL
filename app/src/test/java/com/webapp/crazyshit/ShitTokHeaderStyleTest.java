package com.webapp.crazyshit;

import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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

    @Test public void shittokIsOpaqueBlackWhileOtherTabsKeepTheirGlass() {
        NativeMainActivity activity = Robolectric.buildActivity(NativeMainActivity.class).get();
        LinearLayout controls = ReflectionHelpers.callInstanceMethod(activity, "buildTopBar");
        GlassHeaderView header = new GlassHeaderView(activity, controls);
        ReflectionHelpers.setField(activity, "topBar", header);

        ReflectionHelpers.callInstanceMethod(activity, "setPrimaryHeaderMaterial",
                from(int.class, MainPagerAdapter.PAGE_CHAOS));
        assertEquals(Color.BLACK, ((ColorDrawable) header.getBackground()).getColor());
        assertEquals(View.GONE, header.getChildAt(0).getVisibility());

        ReflectionHelpers.callInstanceMethod(activity, "setPrimaryHeaderMaterial",
                from(int.class, MainPagerAdapter.PAGE_LIBRARY));
        assertEquals(View.VISIBLE, header.getChildAt(0).getVisibility());
        assertEquals(Color.TRANSPARENT, ((ColorDrawable) header.getBackground()).getColor());
    }

    @Test public void categoryStripRemainsVisibleUntilAnImmersiveStateActuallyHidesIt() throws Exception {
        NativeMainActivity activity = Robolectric.buildActivity(NativeMainActivity.class).get();
        ShitTokCategoryStrip categories = new ShitTokCategoryStrip(activity);
        ReflectionHelpers.setField(activity, "shitTokCategoryStrip", categories);
        ReflectionHelpers.setField(activity, "primaryPager", new ViewPager2(activity));
        @SuppressWarnings("unchecked")
        Class<Enum> screenClass = (Class<Enum>) Class.forName(
                "com.webapp.crazyshit.NativeMainActivity$Screen");
        ReflectionHelpers.setField(activity, "screen", Enum.valueOf(screenClass, "CHAOS"));

        ReflectionHelpers.callInstanceMethod(activity, "updateShitTokCategoryStrip");
        assertEquals(View.VISIBLE, categories.getVisibility());
        assertEquals(Color.BLACK, ((ColorDrawable) categories.getBackground()).getColor());

        // No normal swipe state is allowed to collapse this row.
        ReflectionHelpers.callInstanceMethod(activity, "updateShitTokCategoryStrip");
        assertEquals(View.VISIBLE, categories.getVisibility());

        ReflectionHelpers.setField(activity, "chaosClearDisplay", true);
        ReflectionHelpers.callInstanceMethod(activity, "updateShitTokCategoryStrip");
        assertEquals(View.GONE, categories.getVisibility());
        ReflectionHelpers.setField(activity, "chaosClearDisplay", false);
        ReflectionHelpers.callInstanceMethod(activity, "updateShitTokCategoryStrip");
        assertEquals(View.VISIBLE, categories.getVisibility());
    }
}
