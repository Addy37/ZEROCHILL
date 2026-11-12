package com.webapp.crazyshit;

import static org.junit.Assert.*;
import android.app.Application;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class SettingsCompactSwitchTest {
    @Test public void composeSettingsSwitchesKeepValuesAndCallbacks() {
        android.content.Context app = org.robolectric.RuntimeEnvironment.getApplication();
        ContentPreference.prefs(app).edit().clear().putBoolean("haptics_enabled", false).commit();
        org.robolectric.android.controller.ActivityController<SettingsActivity> controller =
                Robolectric.buildActivity(SettingsActivity.class).create();
        SettingsActivity activity = controller.get();
        // Settings uses the approved Compose presentation. Exercise the actual mounted
        // Activity models and preference callbacks used by both row and switch clicks.
        List<SettingsComposeScreen.Section> sections = ReflectionHelpers.getField(activity, "settingsSections");
        List<SettingsComposeScreen.Item> switches = new ArrayList<>();
        for (SettingsComposeScreen.Section section : sections)
            for (SettingsComposeScreen.Item item : section.getItems())
                if (item.getChecked() != null) switches.add(item);
        assertEquals(3, switches.size());
        SettingsComposeScreen.Item haptics = switches.stream()
                .filter(item -> item.getTitle().equals("Haptic feedback")).findFirst().get();
        assertEquals(Boolean.FALSE, haptics.getChecked());
        haptics.toggle(true);
        assertEquals(Boolean.TRUE, haptics.getChecked());
        assertTrue(ContentPreference.prefs(activity).getBoolean("haptics_enabled", false));
        haptics.toggle(false);
        assertEquals(Boolean.FALSE, haptics.getChecked());
        assertFalse(ContentPreference.prefs(activity).getBoolean("haptics_enabled", true));
        controller.destroy();
    }
}
