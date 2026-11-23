package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokCategoryStripTest {
    private Activity activity;

    @Before public void setup() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContentPreference.prefs(activity).edit().clear().commit();
    }

    @Test public void showsFiveOverlayTabsAndFixedSearchMoreActions() {
        ShitTokCategoryStrip strip = new ShitTokCategoryStrip(activity);
        HorizontalScrollView scroll = (HorizontalScrollView) strip.getChildAt(0);
        LinearLayout row = (LinearLayout) scroll.getChildAt(0);
        assertEquals(5, row.getChildCount());
        assertEquals("Chaos", ((TextView) strip.findViewWithTag("shittok_category_chaos"))
                .getText().toString());
        assertTrue(((TextView) strip.findViewWithTag("shittok_category_chaos")).isSelected());
        for (ShitTokCategoryStrip.Tab tab : ShitTokCategoryStrip.Tab.values())
            assertNotNull(strip.findViewWithTag("shittok_category_cell_" + tab.value));
        assertNotNull(strip.findViewWithTag("shittok_overlay_search"));
        assertNotNull(strip.findViewWithTag("shittok_overlay_more"));
        assertEquals(3, strip.getChildCount());
    }

    @Test public void categorySwitchPreservesOnlyFapChoiceAndRetiresOldSelector() {
        ContentPreference.set(activity, ContentPreference.WOMEN);
        ShitTokContentPreference.set(activity, ContentPreference.ALL);
        ShitTokSourceMode.set(activity, ShitTokSourceMode.ONLY_FAP);
        ShitTokCategoryStrip strip = new ShitTokCategoryStrip(activity);
        assertEquals(ShitTokSourceMode.ALL, ShitTokSourceMode.get(activity));

        int[] selections = {0};
        strip.setOnSelectionChanged(() -> selections[0]++);
        View cell = strip.findViewWithTag("shittok_category_cell_trans");
        cell.performClick();
        assertEquals(ContentPreference.TRANS_WOMEN, ShitTokContentPreference.get(activity));
        assertEquals(ContentPreference.WOMEN, ContentPreference.get(activity));
        assertEquals(ShitTokSourceMode.ALL, ShitTokSourceMode.get(activity));
        assertEquals(1, selections[0]);
        assertTrue(((TextView) strip.findViewWithTag("shittok_category_trans")).isSelected());
        cell.performClick();
        assertEquals(1, selections[0]);
    }

    @Test public void oldTransMenSelectionMapsToCombinedTransWithoutTouchingOnlyFap() {
        ContentPreference.set(activity, ContentPreference.MEN);
        ShitTokContentPreference.set(activity, ContentPreference.TRANS_MEN);
        ShitTokCategoryStrip strip = new ShitTokCategoryStrip(activity);
        assertTrue(((TextView) strip.findViewWithTag("shittok_category_trans")).isSelected());
        assertEquals(ContentPreference.TRANS_WOMEN, ShitTokContentPreference.get(activity));
        assertEquals(ContentPreference.MEN, ContentPreference.get(activity));
    }
}
