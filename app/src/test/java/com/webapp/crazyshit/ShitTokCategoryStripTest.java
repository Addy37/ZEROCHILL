package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.view.View;
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

    @Test public void hasAllSixOriginalCategoriesAndShowsForYouForAll() {
        ShitTokCategoryStrip strip = new ShitTokCategoryStrip(activity);
        LinearLayout row = (LinearLayout) strip.getChildAt(0);
        assertEquals(6, row.getChildCount());
        TextView all = strip.findViewWithTag("shittok_category_all");
        assertEquals("For You", all.getText().toString());
        assertTrue(all.isSelected());
        for (ContentPreference option : ContentPreference.values()) {
            assertNotNull(strip.findViewWithTag("shittok_category_cell_" + option.value));
        }
    }

    @Test public void changingShitTokCategoryDoesNotChangeOnlyFapOrSourceMode() {
        ContentPreference.set(activity, ContentPreference.WOMEN);
        ShitTokContentPreference.set(activity, ContentPreference.ALL);
        ShitTokSourceMode.set(activity, ShitTokSourceMode.ONLY_FAP);
        ShitTokCategoryStrip strip = new ShitTokCategoryStrip(activity);
        int[] selections = {0};
        strip.setOnSelectionChanged(() -> selections[0]++);
        View cell = strip.findViewWithTag("shittok_category_cell_trans_men");
        cell.performClick();
        assertEquals(ContentPreference.TRANS_MEN, ShitTokContentPreference.get(activity));
        assertEquals(ContentPreference.WOMEN, ContentPreference.get(activity));
        assertEquals(ShitTokSourceMode.ONLY_FAP, ShitTokSourceMode.get(activity));
        assertEquals(1, selections[0]);
        assertTrue(((TextView) strip.findViewWithTag("shittok_category_trans_men")).isSelected());
        cell.performClick();
        assertEquals(1, selections[0]);
    }
}
