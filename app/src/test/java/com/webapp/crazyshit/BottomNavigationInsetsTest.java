package com.webapp.crazyshit;

import android.app.Application;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import androidx.compose.ui.platform.ComposeView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Shell clearance and retained pager semantics, independent of GPU appearance. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class BottomNavigationInsetsTest {
    @Test public void realInsetsMoveWholeSquareBarWithoutAddingMaterialGutter() {
        ActivityController<NativeMainActivity> controller = createActivity();
        NativeMainActivity activity = controller.get();
        FrostedNavigationLayout shell = ReflectionHelpers.getField(activity, "shell");
        ZeroChillBottomNavigationView nav = ReflectionHelpers.getField(activity, "bottomNavigation");
        for (float fold : new float[]{0f, .5f, 1f, 0f}) {
            nav.setCollapseProgressForTest(fold);
            shell.dispatchApplyWindowInsets(systemInsets(activity, 24));
            layoutShell(shell);
            int top = nav.getTop();
            int height = nav.getHeight();
            assertEquals(Math.round((61 - 11 * fold) * activity.getResources().getDisplayMetrics().density), height);
            for (int inset : new int[]{48, 80, 24}) {
                WindowInsets actual = systemInsets(activity, inset);
                shell.dispatchApplyWindowInsets(actual);
                layoutShell(shell);
                assertSame(actual, nav.dispatchApplyWindowInsets(actual));
                assertEquals(top - dp(activity, inset - 24), nav.getTop());
                assertEquals(height, nav.getHeight());
                assertEquals(0, nav.getPaddingTop() + nav.getPaddingBottom());
                assertEquals(dp(activity, 8), ((ViewGroup.MarginLayoutParams)nav.getLayoutParams()).bottomMargin);
            }
        }
        controller.pause().stop().destroy();
    }
    @Test public void composeNavbarKeepsFourDestinationsAndPagerSynchronization() {
        ActivityController<NativeMainActivity> controller = createActivity();
        NativeMainActivity activity = controller.get();
        ZeroChillBottomNavigationView nav = ReflectionHelpers.getField(activity, "bottomNavigation");
        androidx.viewpager2.widget.ViewPager2 pager = ReflectionHelpers.getField(activity, "primaryPager");
        int[] ids = {2,4,3,6};
        for (int index = 0; index < 4; index++) {
            nav.selectSquareTab(index);
            shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(ids[index], nav.getSelectedItemId());
            assertEquals(index, pager.getCurrentItem());
            assertNotNull(nav.getMenu().findItem(ids[index]).getIcon());
        }
        nav.setPagerPosition(1.35f);
        assertEquals(1.35f, nav.pagerPositionForTest(), .001f);
        assertTrue(nav.getChildAt(nav.getChildCount()-1) instanceof ComposeView);
        controller.pause().stop().destroy();
    }
    @Test public void collapseTargetSurvivesRestylingAndBackgroundReturn() {
        ActivityController<NativeMainActivity> controller = createActivity();
        NativeMainActivity activity = controller.get();
        ZeroChillBottomNavigationView nav = ReflectionHelpers.getField(activity, "bottomNavigation");
        nav.setCollapsed(true, false);
        controller.pause().stop().start().resume();
        StableBottomNavigationController.applyOrientation(activity);
        assertTrue(nav.isCollapsedForTest());
        assertEquals(1f, nav.collapseProgressForTest(), 0f);
        nav.setCollapsed(false, false);
        assertFalse(nav.isCollapsedForTest());
        controller.pause().stop().destroy();
    }
    @Test public void dragAdapterForwardsCancellationAndRetainsListenerAcrossReattachment() {
        ActivityController<NativeMainActivity> controller = createActivity();
        ZeroChillBottomNavigationView nav = ReflectionHelpers.getField(controller.get(), "bottomNavigation");
        final int[] calls = new int[3];
        nav.setOnNavigationDragListener(new ZeroChillBottomNavigationView.OnNavigationDragListener() {
            public boolean onNavigationDragStart() { calls[0]++; return true; }
            public void onNavigationDragBy(float fraction) { assertEquals(.25f, fraction, 0f); calls[1]++; }
            public void onNavigationDragEnd(boolean canceled) { assertTrue(canceled); calls[2]++; }
        });
        assertTrue(nav.startSquareDrag()); nav.dragSquareBy(.25f); nav.endSquareDrag(true);
        assertArrayEquals(new int[]{1,1,1}, calls);
        controller.pause().stop().destroy();
    }
    @Test public void externalPagerProgressDoesNotRedispatchNavigation() {
        ActivityController<NativeMainActivity> controller = createActivity();
        ZeroChillBottomNavigationView nav = ReflectionHelpers.getField(controller.get(), "bottomNavigation");
        final int[] selections = {0};
        nav.setOnItemSelectedListener(item -> { selections[0]++; return true; });
        nav.setPagerPosition(2f);
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(800));
        assertEquals(0, selections[0]);
        nav.selectSquareTab(0);
        assertEquals(1, selections[0]);
        controller.pause().stop().destroy();
    }
    static ActivityController<NativeMainActivity> createActivity() {
        org.robolectric.RuntimeEnvironment.getApplication().getSharedPreferences("app_prefs", 0)
                .edit().putBoolean("access_notice_2_8_3_accepted", true).apply();
        ActivityController<NativeMainActivity> controller = Robolectric.buildActivity(NativeMainActivity.class)
                .create().start().resume().visible();
        StableBottomNavigationController.attach(controller.get());
        shadowOf(android.os.Looper.getMainLooper()).idle();
        return controller;
    }
    static WindowInsets systemInsets(NativeMainActivity activity, int bottomDp) {
        return new WindowInsets.Builder()
            .setInsets(WindowInsets.Type.statusBars(), android.graphics.Insets.of(0, dp(activity,24), 0, 0))
            .setVisible(WindowInsets.Type.statusBars(), true)
            .setInsets(WindowInsets.Type.navigationBars(), android.graphics.Insets.of(0, 0, 0, dp(activity,bottomDp)))
            .setVisible(WindowInsets.Type.navigationBars(), true).build();
    }
    static void layoutShell(FrostedNavigationLayout shell) {
        shell.measure(View.MeasureSpec.makeMeasureSpec(dp(shell.getContext(),360), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(shell.getContext(),800), View.MeasureSpec.EXACTLY));
        shell.layout(0, 0, shell.getMeasuredWidth(), shell.getMeasuredHeight());
    }
    private static int dp(android.content.Context c, int value) { return Math.round(value*c.getResources().getDisplayMetrics().density); }
}
