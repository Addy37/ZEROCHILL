package com.webapp.crazyshit;

import static org.junit.Assert.*;
import android.app.Application;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class SettingsCompactSwitchTest {
    @Test public void compactSettingsSwitchesKeepValuesAndRowClickBehavior() {
        android.content.Context app = org.robolectric.RuntimeEnvironment.getApplication();
        ContentPreference.prefs(app).edit().clear().putBoolean("haptics_enabled", false).commit();
        org.robolectric.android.controller.ActivityController<SettingsActivity> controller =
                Robolectric.buildActivity(SettingsActivity.class).create();
        SettingsActivity activity = controller.get();
        List<ZeroChillSwitch> switches = new ArrayList<>();
        collect(activity.getWindow().getDecorView(), switches);
        assertEquals(3, switches.size());
        float density = activity.getResources().getDisplayMetrics().density;
        for (ZeroChillSwitch toggle : switches) {
            assertEquals(Math.round(44 * density), toggle.getSwitchMinWidth());
            assertTrue(toggle.getMinimumHeight() >= Math.round(48 * density));
            assertEquals(Math.round(24 * density), toggle.getTrackDrawable().getIntrinsicHeight());
        }
        ZeroChillSwitch haptics = switches.get(2);
        assertFalse(haptics.isChecked());
        ((View) haptics.getParent()).performClick();
        assertTrue(haptics.isChecked());
        assertTrue(ContentPreference.prefs(activity).getBoolean("haptics_enabled", false));
        haptics.performClick();
        assertFalse(ContentPreference.prefs(activity).getBoolean("haptics_enabled", true));
        controller.destroy();
    }
    private void collect(View view, List<ZeroChillSwitch> result) {
        if (view instanceof ZeroChillSwitch) result.add((ZeroChillSwitch) view);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            collect(((ViewGroup) view).getChildAt(i), result);
    }
}
