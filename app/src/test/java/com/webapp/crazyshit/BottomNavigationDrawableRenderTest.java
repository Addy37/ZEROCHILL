package com.webapp.crazyshit;

import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Identity contract; optical pixel/motion evidence comes from the GPU probe. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class BottomNavigationDrawableRenderTest {
    @Test public void squareRendererUsesZeroChillInkAndOriginalMenuIcons() {
        assertEquals(0xFF0892D0, SquareNavigationBridge.SELECTED_ARGB);
        assertEquals(0xFFB8C0CC, SquareNavigationBridge.INACTIVE_ARGB);
        assertArrayEquals(new String[]{"Shows","ShitTok","OnlyFap","Library"},
                SquareNavigationBridge.Companion.getLabels());
        assertArrayEquals(new int[]{R.drawable.ic_nav_series, R.drawable.ic_nav_chaos,
                R.drawable.ic_nav_onlyfap,R.drawable.ic_more_library},
                SquareNavigationBridge.Companion.getIcons());
    }
}
