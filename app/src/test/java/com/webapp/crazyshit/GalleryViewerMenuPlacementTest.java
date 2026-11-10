package com.webapp.crazyshit;

import android.app.Application;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.PopupWindow;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {35, 36}, qualifiers = "w400dp-h800dp-mdpi")
public class GalleryViewerMenuPlacementTest {
    @Test public void photoAndVideoMenusGrowAbovePortraitDockAndKeepActions() { checkMenus(400, 800); }

    @Test @Config(qualifiers = "w800dp-h400dp-land-mdpi")
    public void photoAndVideoMenusGrowAboveLandscapeDockAndRemainScrollable() { checkMenus(800, 400); }

    @Test public void nativeFallbackUsesSameUpwardPlacementAndDispatchesOriginalAction() {
        try (ActivityController<android.app.Activity> controller = Robolectric.buildActivity(android.app.Activity.class).setup()) {
            android.app.Activity activity = controller.get();
            android.widget.FrameLayout root = new android.widget.FrameLayout(activity);
            android.widget.TextView anchor = new android.widget.TextView(activity);
            root.addView(anchor, new android.widget.FrameLayout.LayoutParams(48, 48, Gravity.BOTTOM | Gravity.RIGHT));
            activity.setContentView(root);
            View decor = activity.getWindow().getDecorView();
            decor.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
            decor.layout(0, 0, 400, 800);
            ZeroChillMenu menu = new ZeroChillMenu(activity, anchor);
            int[] calls = {0};
            menu.getMenu().add("Share item").setOnMenuItemClickListener(item -> { calls[0]++; return true; });
            org.robolectric.util.ReflectionHelpers.callInstanceMethod(menu, "showInternal",
                    org.robolectric.util.ReflectionHelpers.ClassParameter.from(boolean.class, false),
                    org.robolectric.util.ReflectionHelpers.ClassParameter.from(boolean.class, true));
            PopupWindow popup = Shadows.shadowOf(RuntimeEnvironment.getApplication()).getLatestPopupWindow();
            WindowManager.LayoutParams placement = (WindowManager.LayoutParams) popup.getContentView().getRootView().getLayoutParams();
            assertEquals(Gravity.BOTTOM, placement.gravity & Gravity.VERTICAL_GRAVITY_MASK);
            assertTrue(popup.getHeight() >= 48);
            View share = visibleAction(popup.getContentView(), "Share item");
            assertNotNull(share); assertTrue(share.performClick());
            assertEquals(1, calls[0]); assertFalse(popup.isShowing());
        }
    }

    private void checkMenus(int width, int height) {
        for (String kind : new String[]{NativeContentItem.KIND_IMAGE, NativeContentItem.KIND_MEDIA}) {
            NativeContentItem item = new NativeContentItem(kind, "Menu fixture", "test-media", "", "", "", "", "");
            String session = BunkrGallerySessionStore.create("Fixture", "");
            BunkrGallerySessionStore.replace(session, Collections.singletonList(item), 1, true);
            Intent intent = new Intent(RuntimeEnvironment.getApplication(), BunkrGalleryActivity.class)
                    .putExtra(BunkrGalleryActivity.EXTRA_SESSION_ID, session)
                    .putExtra(BunkrGalleryActivity.EXTRA_INITIAL_URL, item.url);
            try (ActivityController<BunkrGalleryActivity> controller = Robolectric.buildActivity(
                    BunkrGalleryActivity.class, intent).setup()) {
                View decor = controller.get().getWindow().getDecorView();
                decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                decor.layout(0, 0, width, height);
                View more = visibleAction(decor, "More options");
                assertNotNull(more); assertTrue(more.performClick());
                PopupWindow popup = Shadows.shadowOf(RuntimeEnvironment.getApplication()).getLatestPopupWindow();
                GlassMenuTestSupport.assertGlass(popup);
                WindowManager.LayoutParams placement = (WindowManager.LayoutParams) popup.getContentView().getRootView().getLayoutParams();
                assertEquals(Gravity.BOTTOM, placement.gravity & Gravity.VERTICAL_GRAVITY_MASK);
                int[] anchor = new int[2], origin = new int[2];
                more.getLocationInWindow(anchor); decor.getLocationInWindow(origin);
                assertEquals(decor.getHeight() - (anchor[1] - origin[1]) + 4, placement.y);
                assertTrue("Menu must have room for full action rows", AnchoredMenuPopup.maxHeight(more, 420, true) > 96);
                assertTrue(GlassMenuTestSupport.hasAction(popup, "Share item"));
                assertTrue(GlassMenuTestSupport.hasAction(popup, "Open item page"));
                if (item.isVideo()) {
                    assertTrue(GlassMenuTestSupport.hasAction(popup, "Download video"));
                    assertTrue(GlassMenuTestSupport.hasAction(popup, "Advanced playback options"));
                }
                assertTrue(GlassMenuTestSupport.click(popup, "Share item"));
                assertFalse(popup.isShowing());
                assertFalse(controller.get().isFinishing());
                assertTrue(more.performClick());
                popup = Shadows.shadowOf(RuntimeEnvironment.getApplication()).getLatestPopupWindow();
                GlassMenuTestSupport.assertGlass(popup);
                popup.dismiss();
                assertFalse(popup.isShowing());
            }
        }
    }

    private View visibleAction(View view, String label) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (label.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = visibleAction(((ViewGroup) view).getChildAt(i), label);
            if (found != null) return found;
        }
        return null;
    }
}
