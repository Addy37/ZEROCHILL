package com.webapp.crazyshit;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Compare real vector pixels through the Material hierarchy with an unclipped icon draw. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BottomNavigationDrawableRenderTest {
    @Test public void expandedContentAndCapsuleAreVerticallyCentered() {
        ActivityController<NativeMainActivity> controller = BottomNavigationInsetsTest.createActivity();
        NativeMainActivity activity = controller.get();
        ZeroChillBottomNavigationView nav = ReflectionHelpers.getField(activity, "bottomNavigation");
        FrostedNavigationLayout shell = ReflectionHelpers.getField(activity, "shell");
        int[] ids = {2, 4, 3, 6};
        for (int selected = 0; selected < ids.length; selected++) {
            nav.setSelectedItemId(ids[selected]);
            nav.setPagerPosition(selected);
            shadowOf(android.os.Looper.getMainLooper()).idle();
            shell.dispatchApplyWindowInsets(BottomNavigationInsetsTest.systemInsets(activity, 24));
            BottomNavigationInsetsTest.layoutShell(shell);
            Bitmap bitmap = Bitmap.createBitmap(nav.getWidth(), nav.getHeight(), Bitmap.Config.ARGB_8888);
            nav.draw(new Canvas(bitmap));
            bitmap.recycle();
            for (int id : ids) {
                View item = nav.findViewById(id);
                ImageView icon = item.findViewById(com.google.android.material.R.id.navigation_bar_item_icon_view);
                TextView label = item.findViewById(id == ids[selected]
                        ? com.google.android.material.R.id.navigation_bar_item_large_label_view
                        : com.google.android.material.R.id.navigation_bar_item_small_label_view);
                RectF content = mappedBounds(nav, icon);
                RectF labelBounds = mappedBounds(nav, label);
                content.union(labelBounds);
                System.out.println("NAV_VERTICAL_MEASURE selected=" + ids[selected] + " tab=" + id
                        + " nav=" + nav.getHeight() + " paddingTop=" + nav.getPaddingTop()
                        + " paddingBottom=" + nav.getPaddingBottom() + " menu=" + ((View) item.getParent()).getHeight()
                        + " item=" + item.getHeight() + " icon=" + mappedBounds(nav, icon)
                        + " label=" + labelBounds + " baseline=" + (labelBounds.top + label.getBaseline())
                        + " capsule=" + nav.selectedCapsuleBoundsForTest());
                assertEquals("expanded icon/label content tab=" + id, nav.getHeight() / 2f,
                        content.centerY(), activity.getResources().getDisplayMetrics().density);
            }
            assertEquals("expanded selected capsule", nav.getHeight() / 2f,
                    nav.selectedCapsuleBoundsForTest().centerY(), 0.5f);
        }
        controller.pause().stop().destroy();
    }

    private static RectF mappedBounds(ViewGroup nav, View child) {
        RectF rect = new RectF(0, 0, child.getWidth(), child.getHeight());
        while (child != nav) {
            ViewGroup parent = (ViewGroup) child.getParent();
            child.getMatrix().mapRect(rect);
            rect.offset(child.getLeft() - parent.getScrollX(), child.getTop() - parent.getScrollY());
            child = parent;
        }
        return rect;
    }

    @Test public void everyTabRendersItsCompleteIconAtEverySizeAndSelection() throws Exception {
        ActivityController<NativeMainActivity> controller = BottomNavigationInsetsTest.createActivity();
        NativeMainActivity activity = controller.get();
        ZeroChillBottomNavigationView nav = ReflectionHelpers.getField(activity, "bottomNavigation");
        FrostedNavigationLayout shell = ReflectionHelpers.getField(activity, "shell");
        int[] ids = {2, 4, 3, 6};
        for (float progress : new float[]{0f, 0.25f, 0.5f, 0.75f, 1f, 0.5f, 0f}) {
            nav.setCollapseProgressForTest(progress);
            for (int selected = 0; selected < ids.length; selected++) {
                nav.setSelectedItemId(ids[selected]);
                nav.setPagerPosition(selected);
                shadowOf(android.os.Looper.getMainLooper()).idle();
                for (int inset : new int[]{24, 80, 24}) {
                    shell.dispatchApplyWindowInsets(BottomNavigationInsetsTest.systemInsets(activity, inset));
                    BottomNavigationInsetsTest.layoutShell(shell);
                    Bitmap rendered = Bitmap.createBitmap(nav.getWidth(), nav.getHeight(), Bitmap.Config.ARGB_8888);
                    nav.draw(new Canvas(rendered));
                    for (int id : ids) {
                        ImageView icon = nav.findViewById(id).findViewById(
                                com.google.android.material.R.id.navigation_bar_item_icon_view);
                        Bitmap expected = Bitmap.createBitmap(nav.getWidth(), nav.getHeight(), Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(expected);
                        applyIconTransform(canvas, nav, icon);
                        icon.draw(canvas);
                        assertIconPixels(rendered, expected, "tab=" + id + " selected=" + ids[selected]
                                + " collapse=" + progress + " inset=" + inset);
                        expected.recycle();
                    }
                    if (selected == 3 && inset == 24) save(rendered, "nav-library-" + progress);
                    rendered.recycle();
                }
            }
        }
        controller.pause().stop().destroy();
    }

    private static void applyIconTransform(Canvas canvas, ViewGroup nav, View icon) {
        List<View> path = new ArrayList<>();
        for (View child = icon; child != nav; child = (View) child.getParent()) path.add(child);
        for (int i = path.size() - 1; i >= 0; i--) {
            View child = path.get(i);
            View parent = (View) child.getParent();
            canvas.translate(child.getLeft() - parent.getScrollX(), child.getTop() - parent.getScrollY());
            canvas.concat(child.getMatrix());
        }
    }

    private static void assertIconPixels(Bitmap actual, Bitmap expected, String state) {
        int solidPixels = 0;
        int missing = 0;
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                int wanted = expected.getPixel(x, y);
                // Interior vector pixels have an exact tint; skip antialiased edges which
                // legitimately composite with the glass behind the icon.
                if (Color.alpha(wanted) < 250) continue;
                solidPixels++;
                int got = actual.getPixel(x, y);
                if (Math.abs(Color.red(wanted) - Color.red(got)) > 8
                        || Math.abs(Color.green(wanted) - Color.green(got)) > 8
                        || Math.abs(Color.blue(wanted) - Color.blue(got)) > 8) missing++;
            }
        }
        assertTrue(state + " empty icon oracle", solidPixels > 10);
        assertEquals(state + " missing complete icon pixels out of " + solidPixels, 0, missing);
    }

    private static void save(Bitmap bitmap, String name) throws Exception {
        File dir = new File("build/reports/visual-tests");
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
