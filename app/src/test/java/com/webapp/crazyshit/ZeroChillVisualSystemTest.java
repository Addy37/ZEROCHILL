package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ZeroChillVisualSystemTest {
    @Test public void textRolesAndPrimaryActionMeetNormalTextContrast() {
        for (int surface : new int[] {UiPalette.BACKGROUND, UiPalette.SURFACE, UiPalette.RAISED, UiPalette.FLOATING}) {
            for (int text : new int[] {UiPalette.TEXT_PRIMARY, UiPalette.TEXT_SECONDARY, UiPalette.TEXT_MUTED}) {
                assertTrue(ColorUtils.calculateContrast(text, surface) >= 4.5);
            }
        }
        assertTrue(ColorUtils.calculateContrast(UiPalette.ON_PRIMARY, UiPalette.PRIMARY) >= 4.5);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        TextView action = new TextView(activity);
        ZeroChillUi.styleAction(action, true);
        assertEquals(UiPalette.ON_PRIMARY, action.getCurrentTextColor());
        assertTrue(action.getMinHeight() >= ZeroChillUi.dimension(activity, R.dimen.zc_touch_target));
        action.setEnabled(false);
        assertNotEquals(UiPalette.ON_PRIMARY, action.getCurrentTextColor());
    }

    @Test public void canceledPressAndReducedMotionPreserveContentOpacityAndFinalState() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        TextView action = new TextView(activity);
        action.setClickable(true);
        activity.setContentView(action);
        action.setAlpha(0.7f);
        ZeroChillMotion.installPressFeedback(action);
        touch(action, MotionEvent.ACTION_DOWN);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
        assertTrue(action.getScaleX() < 1f);
        touch(action, MotionEvent.ACTION_UP);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(30));
        activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
                .putBoolean("immersive_motion_enabled", false).commit();
        touch(action, MotionEvent.ACTION_CANCEL);
        assertEquals(1f, action.getScaleX(), 0f);
        assertEquals(0.7f, action.getAlpha(), 0f);
        action.setTranslationX(20f);
        action.setAlpha(0f);
        ZeroChillMotion.enterFromEnd(action, 20f);
        assertEquals(0f, action.getTranslationX(), 0f);
        assertEquals(1f, action.getAlpha(), 0f);
    }

    @Test public void selectionDoesNotChangeGeometryAndRestartedSkeletonStaysVisible() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        TextView chip = new TextView(activity);
        ZeroChillUi.styleChip(chip, true);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
        assertEquals(1f, chip.getScaleX(), 0f);
        SkeletonLoaderView skeleton = new SkeletonLoaderView(activity);
        activity.setContentView(skeleton);
        skeleton.start();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        assertEquals(1f, skeleton.getAlpha(), 0f);
        skeleton.stop();
        skeleton.start();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
        assertEquals(View.VISIBLE, skeleton.getVisibility());
        skeleton.stop();
        assertEquals(View.GONE, skeleton.getVisibility());
    }

    @Test public void renderActualSharedComponents() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(24, 32, 24, 24);
        page.setBackgroundColor(UiPalette.BACKGROUND);
        activity.setContentView(page);
        add(page, "ZEROCHILL", ZeroChillTypography.Role.DISPLAY);
        add(page, "Your Library", ZeroChillTypography.Role.TITLE);
        add(page, "Saved for later", ZeroChillTypography.Role.SECTION);
        add(page, "The content comes first.", ZeroChillTypography.Role.CONTENT);
        add(page, "A quieter interface. Everything you saved, right here.", ZeroChillTypography.Role.BODY);
        add(page, "Downloads · Favorites · History", ZeroChillTypography.Role.SUPPORT);
        add(page, "Updated just now", ZeroChillTypography.Role.META);
        for (boolean primary : new boolean[] {true, false}) {
            TextView action = new TextView(activity);
            action.setText(primary ? "Continue" : "View all");
            ZeroChillUi.styleAction(action, primary);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = 16;
            page.addView(action, params);
        }
        page.measure(View.MeasureSpec.makeMeasureSpec(420, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, 420, 720);
        Bitmap image = Bitmap.createBitmap(420, 720, Bitmap.Config.ARGB_8888);
        page.draw(new Canvas(image));
        File folder = new File("build/reports/visual-tests");
        assertTrue(folder.exists() || folder.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(folder, "zerochill-5-system.png"))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out));
        }
    }

    private void add(LinearLayout page, String text, ZeroChillTypography.Role role) {
        TextView view = new TextView(page.getContext());
        view.setText(text);
        ZeroChillTypography.apply(view, role);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = 16;
        page.addView(view, params);
    }
    private void touch(View view, int action) {
        MotionEvent event = MotionEvent.obtain(0, 0, action, 4f, 4f, 0);
        view.dispatchTouchEvent(event);
        event.recycle();
    }
}
