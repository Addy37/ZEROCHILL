package com.webapp.crazyshit;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class BottomNavigationInsetsTest {
    private static final int[] IDS = {2, 4, 3, 6};

    @Test public void gestureGeometryMatchesProductionMaterialMenuIncludingLibraryCapsule() {
        ActivityController<NativeMainActivity> controller = createActivity();
        NativeMainActivity activity = controller.get();
        ZeroChillBottomNavigationView nav = ReflectionHelpers.getField(activity, "bottomNavigation");
        FrostedNavigationLayout shell = ReflectionHelpers.getField(activity, "shell");
        nav.setSelectedItemId(6);
        nav.setPagerPosition(3f);
        // The production constructor used the theme's original style and Material listener.
        BottomNavigationView production = new BottomNavigationView(activity);
        production.setMinimumHeight(0);
        for (int id : IDS) {
            android.view.MenuItem item = nav.getMenu().findItem(id);
            production.getMenu().add(0, id, 0, item.getTitle()).setIcon(item.getIcon());
        }
        StableBottomNavigationController.styleBar(production);
        production.setSelectedItemId(6);
        android.widget.FrameLayout root = ReflectionHelpers.getField(activity, "overlayRoot");
        root.addView(production, new android.widget.FrameLayout.LayoutParams(dp(activity, 340), dp(activity, 64)));
        for (int id : IDS) {
            View item = production.findViewById(id);
            item.setMinimumHeight(0);
            item.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        WindowInsets gesture = systemInsets(activity, 24);
        shell.dispatchApplyWindowInsets(gesture);
        production.dispatchApplyWindowInsets(gesture);
        layoutShell(shell);
        measure(production, nav.getWidth(), nav.getHeight());
        // Material posts active-indicator sizing until the measured item width is available.
        shadowOf(android.os.Looper.getMainLooper()).idle();
        // Attachment can dispatch the test window's default zero insets. Reapply the
        // simulated production input after that asynchronous dispatch, before comparison.
        shell.dispatchApplyWindowInsets(gesture);
        production.dispatchApplyWindowInsets(gesture);
        layoutShell(shell);
        measure(production, nav.getWidth(), nav.getHeight());
        assertEquals(dp(activity, 24), production.getPaddingBottom());
        assertEquals(production.getPaddingBottom(), nav.getPaddingBottom());
        assertEquals(menuGeometry(production), menuGeometry(nav));
        draw(nav);
        Rect item = bounds(production, production.findViewById(6));
        RectF expectedCapsule = new RectF(item.left + dp(activity, 4),
                Math.max(dp(activity, 3), item.top + dp(activity, 4)),
                item.right - dp(activity, 4),
                Math.min(production.getHeight() - dp(activity, 3), item.bottom + dp(activity, 10)));
        assertEquals(expectedCapsule, nav.selectedCapsuleBoundsForTest());
        assertLabelsInsideBar(nav);
        controller.pause().stop().destroy();
    }

    @Test public void liveInsetSwitchMovesWholeBarAndPreservesAllProgressAndTabGeometry() {
        ActivityController<NativeMainActivity> controller = createActivity();
        NativeMainActivity activity = controller.get();
        FrostedNavigationLayout shell = ReflectionHelpers.getField(activity, "shell");
        ZeroChillBottomNavigationView nav = ReflectionHelpers.getField(activity, "bottomNavigation");
        for (float progress : new float[]{0f, 0.25f, 0.5f, 0.75f, 1f}) {
            nav.setCollapseProgressForTest(progress);
            for (int id : IDS) {
                nav.setSelectedItemId(id);
                nav.setPagerPosition(indexOf(id));
                shell.dispatchApplyWindowInsets(systemInsets(activity, 24));
                layoutShell(shell);
                draw(nav);
                List<String> expected = menuGeometry(nav);
                RectF capsule = nav.selectedCapsuleBoundsForTest();
                int gestureTop = nav.getTop();
                int height = nav.getHeight();
                int width = nav.getWidth();
                for (int bottom : new int[]{48, 80, 48, 24, 24}) {
                    shell.dispatchApplyWindowInsets(systemInsets(activity, bottom));
                    layoutShell(shell);
                    draw(nav);
                    assertEquals(dp(activity, bottom), shell.getPaddingBottom());
                    assertEquals(dp(activity, 24), shell.getPaddingTop());
                    assertEquals(dp(activity, 24), activity.cachedPortraitInsetTop());
                    assertEquals(gestureTop - dp(activity, bottom - 24), nav.getTop());
                    assertEquals(height, nav.getHeight());
                    assertEquals(width, nav.getWidth());
                    assertEquals(dp(activity, 24), nav.getPaddingBottom());
                    assertEquals(expected, menuGeometry(nav));
                    assertEquals(capsule, nav.selectedCapsuleBoundsForTest());
                    assertLabelsInsideBar(nav);
                }
            }
        }
        controller.pause().stop().destroy();
    }

    @Config(sdk = 26)
    @Test public void legacyButtonInsetsAreAlsoOwnedByOuterShell() {
        ActivityController<NativeMainActivity> controller = createActivity();
        NativeMainActivity activity = controller.get();
        FrostedNavigationLayout shell = ReflectionHelpers.getField(activity, "shell");
        ZeroChillBottomNavigationView nav = ReflectionHelpers.getField(activity, "bottomNavigation");
        for (int bottom : new int[]{48, 24, 48}) {
            WindowInsets legacy = ReflectionHelpers.callConstructor(WindowInsets.class,
                    ReflectionHelpers.ClassParameter.from(Rect.class,
                            new Rect(0, dp(activity, 24), 0, dp(activity, bottom))));
            shell.dispatchApplyWindowInsets(legacy);
            layoutShell(shell);
            assertEquals(dp(activity, bottom), shell.getPaddingBottom());
            assertEquals(dp(activity, 24), nav.getPaddingBottom());
        }
        controller.pause().stop().destroy();
    }

    private static ActivityController<NativeMainActivity> createActivity() {
        org.robolectric.RuntimeEnvironment.getApplication().getSharedPreferences("app_prefs", 0)
                .edit().putBoolean("access_notice_2_8_3_accepted", true).apply();
        ActivityController<NativeMainActivity> controller = Robolectric.buildActivity(NativeMainActivity.class)
                .create().start().resume().visible();
        // Application.class omits ZeroChillApplication's UI-foundation lifecycle callback.
        StableBottomNavigationController.attach(controller.get());
        shadowOf(android.os.Looper.getMainLooper()).idle();
        return controller;
    }

    private static WindowInsets systemInsets(NativeMainActivity activity, int bottomDp) {
        return new WindowInsets.Builder()
                .setInsets(WindowInsets.Type.statusBars(), android.graphics.Insets.of(0, dp(activity, 24), 0, 0))
                .setVisible(WindowInsets.Type.statusBars(), true)
                .setInsets(WindowInsets.Type.navigationBars(), android.graphics.Insets.of(0, 0, 0, dp(activity, bottomDp)))
                .setVisible(WindowInsets.Type.navigationBars(), true).build();
    }

    private static void layoutShell(FrostedNavigationLayout shell) {
        measure(shell, Math.round(360 * shell.getResources().getDisplayMetrics().density),
                Math.round(800 * shell.getResources().getDisplayMetrics().density));
    }

    private static void measure(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static void draw(View view) {
        view.draw(new Canvas(Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888)));
    }

    private static List<String> menuGeometry(BottomNavigationView nav) {
        List<String> result = new ArrayList<>();
        for (int id : IDS) collectGeometry(nav, nav.findViewById(id), result);
        return result;
    }

    private static void collectGeometry(ViewGroup nav, View view, List<String> result) {
        result.add(view.getClass().getSimpleName() + ":" + bounds(nav, view) + ":"
                + view.getTranslationY() + ":" + view.getAlpha() + ":" + view.getVisibility());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectGeometry(nav, group.getChildAt(i), result);
        }
    }

    private static Rect bounds(ViewGroup nav, View view) {
        Rect rect = new Rect(0, 0, view.getWidth(), view.getHeight());
        nav.offsetDescendantRectToMyCoords(view, rect);
        return rect;
    }

    private static void assertLabelsInsideBar(ViewGroup nav) {
        checkLabels(nav, nav);
    }

    private static void checkLabels(ViewGroup nav, View view) {
        if (view instanceof TextView && view.getVisibility() == View.VISIBLE && view.getAlpha() > 0f) {
            Rect rect = bounds(nav, view);
            assertTrue("label top " + rect, rect.top >= 0);
            assertTrue("label bottom " + rect, rect.bottom <= nav.getHeight());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) checkLabels(nav, group.getChildAt(i));
        }
    }

    private static int indexOf(int id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i] == id) return i;
        throw new AssertionError(id);
    }

    private static int dp(NativeMainActivity activity, int dp) {
        return Math.round(dp * activity.getResources().getDisplayMetrics().density);
    }
}
