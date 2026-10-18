package com.webapp.crazyshit;

import android.app.Application;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.lang.reflect.Method;
import java.util.ArrayList;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w320dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ZeroChillSocialSettingsUiTest {
    @Test public void notificationControlsShowSupportedInAppCategoriesAtLargeFont() throws Exception {
        ZeroChillSocialSettingsActivity activity = create(ZeroChillSocialSettingsActivity.MODE_NOTIFICATIONS);
        Configuration config = new Configuration(activity.getResources().getConfiguration());
        config.fontScale = 1.5f;
        activity.getResources().updateConfiguration(config, activity.getResources().getDisplayMetrics());
        Method render = ZeroChillSocialSettingsActivity.class.getDeclaredMethod(
                "renderPreferences", ZeroChillNotificationPreferences.Values.class);
        render.setAccessible(true);
        render.invoke(activity, ZeroChillNotificationPreferences.Values.defaults());
        View root = activity.getWindow().getDecorView();
        ArrayList<Switch> switches = new ArrayList<>();
        collectSwitches(root, switches);
        assertEquals(4, switches.size());
        for (Switch control : switches) assertTrue(control.isChecked());
        Method setControlsEnabled = ZeroChillSocialSettingsActivity.class
                .getDeclaredMethod("setControlsEnabled", boolean.class);
        setControlsEnabled.setAccessible(true);
        setControlsEnabled.invoke(activity, false);
        for (Switch control : switches) assertFalse(control.isEnabled());
        setControlsEnabled.invoke(activity, true);
        for (Switch control : switches) assertTrue(control.isEnabled());
        assertNull(find(root, "Direct messages"));
        assertNotNull(find(root, "Messages remain in your inbox. Social push alerts are not available."));
        ZeroChillPublicProfileUiTest.capture(root, "profile-account-2-notifications.png", 320, 800);
    }

    @Test public void blockedUsersEmptyStateIsClear() throws Exception {
        ZeroChillSocialSettingsActivity activity = create(ZeroChillSocialSettingsActivity.MODE_BLOCKED);
        Method render = ZeroChillSocialSettingsActivity.class.getDeclaredMethod(
                "renderBlocked", ArrayList.class, int.class);
        render.setAccessible(true);
        render.invoke(activity, new ArrayList<ZeroChillSocialRepository.PublicProfile>(), 1);
        assertNotNull(find(activity.getWindow().getDecorView(), "You haven't blocked anyone."));
    }

    private static ZeroChillSocialSettingsActivity create(String mode) {
        Intent intent = new Intent().putExtra(ZeroChillSocialSettingsActivity.EXTRA_MODE, mode);
        return Robolectric.buildActivity(ZeroChillSocialSettingsActivity.class, intent).setup().get();
    }

    private static void collectSwitches(View root, ArrayList<Switch> result) {
        if (root instanceof Switch) result.add((Switch) root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) collectSwitches(group.getChildAt(i), result);
        }
    }

    private static View find(View root, String text) {
        if (root instanceof TextView && text.equals(((TextView) root).getText().toString())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
