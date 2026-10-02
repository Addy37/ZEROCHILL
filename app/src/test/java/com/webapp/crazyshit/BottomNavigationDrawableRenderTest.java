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
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.security.MessageDigest;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Compare real vector pixels through the Material hierarchy with an unclipped icon draw. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BottomNavigationDrawableRenderTest {
    @Test public void expandedInkGapMatchesScreenshotCalibratedMaterialReference() throws Exception {
        ActivityController<NativeMainActivity> controller = BottomNavigationInsetsTest.createActivity();
        NativeMainActivity activity = controller.get();
        ZeroChillBottomNavigationView nav = ReflectionHelpers.getField(activity, "bottomNavigation");
        FrostedNavigationLayout shell = ReflectionHelpers.getField(activity, "shell");
        float density = activity.getResources().getDisplayMetrics().density;
        int[] ids = {2, 4, 3, 6};
        com.google.android.material.bottomnavigation.BottomNavigationView reference =
                new com.google.android.material.bottomnavigation.BottomNavigationView(activity);
        reference.setMinimumHeight(0);
        for (int id : ids) {
            android.view.MenuItem item = nav.getMenu().findItem(id);
            reference.getMenu().add(0, id, 0, item.getTitle()).setIcon(item.getIcon());
        }
        StableBottomNavigationController.styleBar(reference);
        android.widget.FrameLayout root = ReflectionHelpers.getField(activity, "overlayRoot");
        root.addView(reference, new android.widget.FrameLayout.LayoutParams(680, 128));
        for (int selected : ids) {
            nav.setSelectedItemId(selected);
            reference.setSelectedItemId(selected);
            shadowOf(android.os.Looper.getMainLooper()).idle();
            shell.dispatchApplyWindowInsets(BottomNavigationInsetsTest.systemInsets(activity, 24));
            BottomNavigationInsetsTest.layoutShell(shell);
            for (int inset : new int[]{24, 16}) {
                reference.dispatchApplyWindowInsets(BottomNavigationInsetsTest.systemInsets(activity, inset));
                reference.measure(View.MeasureSpec.makeMeasureSpec(nav.getWidth(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(nav.getHeight(), View.MeasureSpec.EXACTLY));
                reference.layout(0, 0, nav.getWidth(), nav.getHeight());
                for (int id : ids) {
                    printVisualGeometry(reference, id, "material-inset=" + inset + " selected=" + selected);
                    if (inset == 16) {
                        for (int actualInset : new int[]{24, 48, 80, 24}) {
                            shell.dispatchApplyWindowInsets(BottomNavigationInsetsTest.systemInsets(activity, actualInset));
                            BottomNavigationInsetsTest.layoutShell(shell);
                            assertVisualGapMatchesReference(reference, nav, id, selected, actualInset);
                        }
                    } else {
                        View container = reference.findViewById(id).findViewById(
                                com.google.android.material.R.id.navigation_bar_item_icon_container);
                        assertEquals("24dp synthetic gutter compresses 30dp container", Math.round(26 * density), container.getHeight());
                    }
                }
            }
            for (int id : ids) printVisualGeometry(nav, id, "beta selected=" + selected);
        }
        controller.pause().stop().destroy();
    }

    private static void assertVisualGapMatchesReference(ViewGroup reference, ZeroChillBottomNavigationView nav,
            int id, int selected, int inset) {
        float density = nav.getResources().getDisplayMetrics().density;
        View item = nav.findViewById(id);
        View referenceItem = reference.findViewById(id);
        View container = item.findViewById(com.google.android.material.R.id.navigation_bar_item_icon_container);
        ImageView icon = item.findViewById(com.google.android.material.R.id.navigation_bar_item_icon_view);
        ImageView referenceIcon = referenceItem.findViewById(com.google.android.material.R.id.navigation_bar_item_icon_view);
        int labelId = id == selected ? com.google.android.material.R.id.navigation_bar_item_large_label_view
                : com.google.android.material.R.id.navigation_bar_item_small_label_view;
        TextView label = item.findViewById(labelId);
        TextView referenceLabel = referenceItem.findViewById(labelId);
        String state = "selected=" + selected + " tab=" + id + " inset=" + inset;
        assertEquals(state + " full container height", Math.round(30 * density), container.getHeight());
        assertEquals(state + " unchanged icon size", Math.round(24 * density), icon.getHeight());
        android.graphics.Rect iconInk = inkBounds(nav, icon);
        android.graphics.Rect labelInk = inkBounds(nav, label);
        int actualGap = labelInk.top - iconInk.bottom;
        int referenceGap = inkBounds(reference, referenceLabel).top - inkBounds(reference, referenceIcon).bottom;
        assertTrue(state + " reference has visible breathing room: " + referenceGap,
                referenceGap >= Math.round(3 * density));
        assertEquals(state + " drawable-to-glyph gap", referenceGap, actualGap);
        assertEquals(state + " icon ink position", inkBounds(reference, referenceIcon), iconInk);
        assertEquals(state + " label ink position", inkBounds(reference, referenceLabel), labelInk);
        assertEquals(state + " label baseline", mappedBounds(reference, referenceLabel).top + referenceLabel.getBaseline(),
                mappedBounds(nav, label).top + label.getBaseline(), 0f);
        assertEquals(state + " label scale", 1f, label.getScaleY(), 0f);
        assertEquals(state + " no label offset", 0f, label.getTranslationY(), 0f);
        Bitmap rendered = Bitmap.createBitmap(nav.getWidth(), nav.getHeight(), Bitmap.Config.ARGB_8888);
        nav.draw(new Canvas(rendered));
        Bitmap expected = Bitmap.createBitmap(nav.getWidth(), nav.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(expected);
        applyIconTransform(canvas, nav, label);
        label.draw(canvas);
        assertIconPixels(rendered, expected, state + " complete label glyphs");
        rendered.recycle(); expected.recycle();
    }

    private static void printVisualGeometry(ViewGroup nav, int id, String state) {
        View item = nav.findViewById(id);
        ImageView icon = item.findViewById(com.google.android.material.R.id.navigation_bar_item_icon_view);
        View container = item.findViewById(com.google.android.material.R.id.navigation_bar_item_icon_container);
        android.graphics.Rect iconInk = inkBounds(nav, icon);
        for (int labelId : new int[]{com.google.android.material.R.id.navigation_bar_item_small_label_view,
                com.google.android.material.R.id.navigation_bar_item_large_label_view}) {
            TextView label = item.findViewById(labelId);
            RectF bounds = mappedBounds(nav, label);
            android.graphics.Rect ink = inkBounds(nav, label);
            android.graphics.Paint.FontMetrics fm = label.getPaint().getFontMetrics();
            System.out.println("NAV_GAP " + state + " tab=" + id + " itemHeight=" + item.getHeight()
                    + " icon=" + mappedBounds(nav, icon) + " container=" + mappedBounds(nav, container)
                    + " iconInk=" + iconInk + " label=" + bounds + " visible=" + label.getVisibility()
                    + " labelInk=" + ink + " gap=" + (ink.top - iconInk.bottom)
                    + " baseline=" + (bounds.top + label.getBaseline()) + " scale=" + label.getScaleY()
                    + " pivot=" + label.getPivotY() + " translation=" + label.getTranslationY()
                    + " font=" + fm.top + "," + fm.ascent + "," + fm.descent + "," + fm.bottom
                    + " includeFontPadding=" + label.getIncludeFontPadding());
        }
    }

    private static android.graphics.Rect inkBounds(ViewGroup nav, View child) {
        Bitmap bitmap = Bitmap.createBitmap(nav.getWidth(), nav.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        applyIconTransform(canvas, nav, child);
        child.draw(canvas);
        android.graphics.Rect bounds = new android.graphics.Rect();
        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
            if (Color.alpha(pixels[y * bitmap.getWidth() + x]) >= 128) bounds.union(x, y, x + 1, y + 1);
        }
        bitmap.recycle();
        assertFalse("empty ink " + child, bounds.isEmpty());
        return bounds;
    }

    @Test public void expandedContentMatchesV431ReferenceThroughLiveInsets() {
        ActivityController<NativeMainActivity> controller = BottomNavigationInsetsTest.createActivity();
        NativeMainActivity activity = controller.get();
        ZeroChillBottomNavigationView nav = ReflectionHelpers.getField(activity, "bottomNavigation");
        FrostedNavigationLayout shell = ReflectionHelpers.getField(activity, "shell");
        int[] ids = {2, 4, 3, 6};
        float density = activity.getResources().getDisplayMetrics().density;
        for (int selected = 0; selected < ids.length; selected++) {
            nav.setSelectedItemId(ids[selected]);
            nav.setPagerPosition(selected);
            shadowOf(android.os.Looper.getMainLooper()).idle();
            for (int inset : new int[]{24, 48, 80, 24}) {
                shell.dispatchApplyWindowInsets(BottomNavigationInsetsTest.systemInsets(activity, inset));
                BottomNavigationInsetsTest.layoutShell(shell);
                Bitmap bitmap = Bitmap.createBitmap(
                        nav.getWidth(), nav.getHeight(), Bitmap.Config.ARGB_8888);
                nav.draw(new Canvas(bitmap));

                assertEquals("v4.3.1 expanded top padding inset=" + inset,
                        0, nav.getPaddingTop());
                assertEquals("v4.3.1 reference gesture padding inset=" + inset,
                        Math.round(16f * density), nav.getPaddingBottom());

                for (int id : ids) {
                    View item = nav.findViewById(id);
                    ImageView icon = item.findViewById(
                            com.google.android.material.R.id.navigation_bar_item_icon_view);
                    TextView label = item.findViewById(id == ids[selected]
                            ? com.google.android.material.R.id.navigation_bar_item_large_label_view
                            : com.google.android.material.R.id.navigation_bar_item_small_label_view);
                    RectF iconBounds = mappedBounds(nav, icon);
                    RectF labelBounds = mappedBounds(nav, label);
                    String state = "expanded selected=" + ids[selected]
                            + " tab=" + id + " inset=" + inset;

                    assertEquals(state + " label visible", View.VISIBLE, label.getVisibility());
                    assertEquals(state + " label alpha", 1f, label.getAlpha(), 0f);
                    assertEquals(state + " no custom label translation",
                            0f, label.getTranslationY(), 0.01f);
                    assertTrue(state + " label below icon",
                            labelBounds.top + label.getBaseline() > iconBounds.bottom);
                }

                View selectedItem = nav.findViewById(ids[selected]);
                RectF itemBounds = mappedBounds(nav, selectedItem);
                RectF expectedCapsule = new RectF(
                        itemBounds.left + 4f * density,
                        Math.max(3f * density, itemBounds.top + 4f * density),
                        itemBounds.right - 4f * density,
                        Math.min(nav.getHeight() - 3f * density,
                                itemBounds.bottom + 10f * density)
                );
                assertEquals("v4.3.1 selected capsule inset=" + inset,
                        expectedCapsule, nav.selectedCapsuleBoundsForTest());

                bitmap.recycle();
            }
        }
        controller.pause().stop().destroy();
    }

    @Test public void childBoundsAndLabelBaselinesMoveContinuouslyAndReverseWithoutDrift() {
        ActivityController<NativeMainActivity> controller = BottomNavigationInsetsTest.createActivity();
        NativeMainActivity activity = controller.get();
        ZeroChillBottomNavigationView nav = ReflectionHelpers.getField(activity, "bottomNavigation");
        FrostedNavigationLayout shell = ReflectionHelpers.getField(activity, "shell");
        int[] ids = {2, 4, 3, 6};
        float tolerance = 2f * activity.getResources().getDisplayMetrics().density;
        for (int selected = 0; selected < ids.length; selected++) {
            nav.setSelectedItemId(ids[selected]);
            nav.setPagerPosition(selected);
            shadowOf(android.os.Looper.getMainLooper()).idle();
            List<List<Float>> forward = new ArrayList<>();
            for (int step = 0; step <= 100; step++) {
                List<Float> geometry = sampleGeometry(nav, shell, activity, ids, step / 100f,
                        step % 2 == 0 ? 24 : 80);
                if (step > 0) {
                    List<Float> previous = forward.get(step - 1);
                    for (int i = 0; i < geometry.size(); i++) assertEquals(
                            "no position/height/baseline jump selected=" + ids[selected] + " step=" + step + " value=" + i,
                            previous.get(i), geometry.get(i), tolerance);
                }
                forward.add(geometry);
            }
            for (int step = 100; step >= 0; step--) {
                List<Float> reverse = sampleGeometry(nav, shell, activity, ids, step / 100f, 48);
                List<Float> expected = forward.get(step);
                // The existing transform setter skips differences <=0.01px. Allow that
                // subpixel threshold, while still rejecting any accumulated layout drift.
                for (int i = 0; i < expected.size(); i++) assertEquals(
                        "no accumulated transforms/reversal selected=" + ids[selected] + " step=" + step + " value=" + i,
                        expected.get(i), reverse.get(i), 0.02f);
            }
        }
        controller.pause().stop().destroy();
    }

    private static List<Float> sampleGeometry(ZeroChillBottomNavigationView nav, FrostedNavigationLayout shell,
            NativeMainActivity activity, int[] ids, float progress, int inset) {
        nav.setCollapseProgressForTest(progress);
        shell.dispatchApplyWindowInsets(BottomNavigationInsetsTest.systemInsets(activity, inset));
        BottomNavigationInsetsTest.layoutShell(shell);
        Bitmap bitmap = Bitmap.createBitmap(nav.getWidth(), nav.getHeight(), Bitmap.Config.ARGB_8888);
        nav.draw(new Canvas(bitmap));
        bitmap.recycle();
        List<Float> values = new ArrayList<>();
        for (int id : ids) {
            View item = nav.findViewById(id);
            View icon = item.findViewById(com.google.android.material.R.id.navigation_bar_item_icon_view);
            RectF bounds = mappedBounds(nav, icon);
            values.add(bounds.top); values.add(bounds.bottom);
            values.add((float) item.getHeight());
            View container = item.findViewById(com.google.android.material.R.id.navigation_bar_item_icon_container);
            RectF containerBounds = mappedBounds(nav, container);
            values.add(containerBounds.top); values.add(containerBounds.bottom);
            for (int labelId : new int[]{com.google.android.material.R.id.navigation_bar_item_small_label_view,
                    com.google.android.material.R.id.navigation_bar_item_large_label_view}) {
                TextView label = item.findViewById(labelId);
                RectF labelBounds = mappedBounds(nav, label);
                values.add(labelBounds.top); values.add(labelBounds.bottom);
                values.add(labelBounds.top + label.getBaseline());
                values.add(label.getTranslationY());
            }
        }
        RectF capsule = nav.selectedCapsuleBoundsForTest();
        values.add(capsule.top); values.add(capsule.bottom);
        values.add((float) nav.getPaddingTop()); values.add((float) nav.getPaddingBottom());
        return values;
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
                    if (selected == 3 && inset == 24) {
                        save(rendered, "nav-library-" + progress);
                        if (progress == 1f) assertApprovedCollapsedRender(rendered);
                    }
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

    private static void assertApprovedCollapsedRender(Bitmap bitmap) throws Exception {
        // Pixel fingerprint of the b53c7ae native Library-selected render approved on
        // device. Protect the entire collapsed glass, icons and capsule, not just bounds.
        assertEquals(480, bitmap.getWidth());
        assertEquals(100, bitmap.getHeight());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        // Hash the same native PNG representation as the approved artifact. getPixels
        // unpremultiplies translucent glass differently from PNG encoding; the original
        // and new saved PNGs are byte-identical despite that ARGB conversion difference.
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, png));
        digest.update(png.toByteArray());
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        assertEquals("approved collapsed pixels changed",
                "ba461010923cf0cc48229885bcbacc14824a64d759c915c537bd5119851b7970", hex.toString());
    }

    private static void save(Bitmap bitmap, String name) throws Exception {
        File dir = new File("build/reports/visual-tests");
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
