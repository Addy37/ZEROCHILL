package com.webapp.crazyshit;

import android.app.Application;
import android.view.View;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class PrimaryTabNavigationTest {
    @Test public void rapidAdjacentNonAdjacentAndRepeatedTapsSelectBeforeLibraryRefresh() {
        RuntimeEnvironment.getApplication().getSharedPreferences("app_prefs", 0).edit()
                .putBoolean("access_notice_2_8_3_accepted", true).commit();
        ActivityController<NativeMainActivity> controller = Robolectric.buildActivity(NativeMainActivity.class)
                .create().start().resume().visible();
        try {
            NativeMainActivity activity = controller.get();
            BottomNavigationView nav = ReflectionHelpers.getField(activity, "bottomNavigation");
            ViewPager2 pager = ReflectionHelpers.getField(activity, "primaryPager");
            MainPagerAdapter adapter = ReflectionHelpers.getField(activity, "primaryPagerAdapter");
            LibraryHubView library = ReflectionHelpers.getField(adapter, "libraryView");
            // No idle between taps: the event handler itself must select the target immediately.
            for (int id : new int[] {1, 2, 3, 6, 1, 6, 2, 6, 6}) {
                nav.setSelectedItemId(id);
                int expected = id == 1 ? 0 : id == 2 ? 1 : id == 3 ? 2 : 3;
                assertEquals(expected, pager.getCurrentItem());
                assertEquals(View.VISIBLE, pager.getVisibility());
                assertEquals(id, nav.getSelectedItemId());
                assertFalse("Tap dispatch must not read or rebuild Library",
                        ReflectionHelpers.<Boolean>getField(library, "loading"));
            }
        } finally { controller.pause().stop().destroy(); }
    }
}
