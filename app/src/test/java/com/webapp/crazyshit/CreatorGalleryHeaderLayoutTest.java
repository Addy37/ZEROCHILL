package com.webapp.crazyshit;

import android.app.Application;
import android.content.Intent;
import android.graphics.Insets;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {35, 36}, qualifiers = "w400dp-h800dp-mdpi")
public class CreatorGalleryHeaderLayoutTest {
    private static final class NoProviderWork extends java.util.concurrent.AbstractExecutorService {
        private boolean closed;
        @Override public void execute(Runnable task) { }
        @Override public void shutdown() { closed = true; }
        @Override public java.util.List<Runnable> shutdownNow() { closed = true; return java.util.Collections.emptyList(); }
        @Override public boolean isShutdown() { return closed; }
        @Override public boolean isTerminated() { return closed; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return closed; }
    }
    private void layout(View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 800);
    }
    private View findDescription(View root, String description) {
        if (description.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View found = findDescription(((ViewGroup) root).getChildAt(i), description);
            if (found != null) return found;
        }
        return null;
    }
    private boolean containsText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            if (containsText(((ViewGroup) view).getChildAt(i), text)) return true;
        }
        return false;
    }

    @Test public void actualGalleryHeroStartsBelowSafeStatusBarAndKeepsOverlayBackAfterCollapse() {
        Intent intent = new Intent().putExtra(NativeFeedBrowserActivity.EXTRA_TITLE, "Geometry fixture")
                .putExtra(NativeFeedBrowserActivity.EXTRA_BUNKR_CREATOR_QUERY, "x")
                .putExtra(CreatorGalleryTransition.EXTRA_RIPPLE_REVEAL, true);
        // One-character fixture query prevents the existing optional header discovery.
        ActivityController<OnlyFapGalleryActivity> controller = Robolectric.buildActivity(OnlyFapGalleryActivity.class, intent);
        OnlyFapGalleryActivity browser = controller.get();
        ReflectionHelpers.setField(browser, "io", new NoProviderWork());
        controller.setup();
        View root = ((ViewGroup) browser.findViewById(android.R.id.content)).getChildAt(0);
        layout(root);
        assertTrue(browser.ownsCreatorGalleryChrome());
        OledImmersiveUiController.attachBrowser(browser);
        ResponsiveFitmentController.applySoon(browser);
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS);
        WindowInsets insets = new WindowInsets.Builder().setInsets(
                WindowInsets.Type.systemBars(), Insets.of(0, 48, 0, 24)).build();
        root.dispatchApplyWindowInsets(insets);
        layout(root);
        CreatorProfileHeader header = ReflectionHelpers.getField(browser, "creatorProfile");
        int[] rootLocation = new int[2], headerLocation = new int[2];
        root.getLocationOnScreen(rootLocation); header.getLocationOnScreen(headerLocation);
        assertEquals(48, root.getPaddingTop());
        assertEquals(48, headerLocation[1] - rootLocation[1]);
        assertEquals(336, header.getHeight());
        View back = findDescription(root, "Back");
        assertNotNull(back); assertEquals(48, back.getWidth()); assertEquals(48, back.getHeight());
        assertNull(findDescription(root, "Feed options"));
        assertFalse(containsText(root, "OnlyFap"));
        assertFalse(containsText(root, "⋮"));
        root.dispatchApplyWindowInsets(insets);
        layout(root);
        assertEquals(48, root.getPaddingTop()); // Repeated delayed fitment never adds twice.
        com.google.android.material.appbar.AppBarLayout appBar = ReflectionHelpers.getField(browser, "creatorAppBar");
        appBar.setExpanded(false, false);
        layout(root);
        ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS);
        layout(root);
        View compact = root.findViewWithTag("creator_favorite_compact");
        assertEquals(View.VISIBLE, compact.getVisibility());
        assertEquals(48, back.getHeight());
        assertEquals(336, header.getHeight());
        controller.pause().stop().destroy();
        ResponsiveFitmentController.release(browser);
        OledImmersiveUiController.detach(browser);
    }

    @Test public void ordinaryBrowserRetainsItsExistingToolbarAndOptions() {
        ActivityController<NativeFeedBrowserActivity> controller = Robolectric.buildActivity(NativeFeedBrowserActivity.class,
                new Intent().putExtra(NativeFeedBrowserActivity.EXTRA_TITLE, "Browse fixture"));
        NativeFeedBrowserActivity browser = controller.get();
        ReflectionHelpers.setField(browser, "io", new NoProviderWork());
        controller.setup();
        View root = ((ViewGroup) browser.findViewById(android.R.id.content)).getChildAt(0);
        layout(root);
        assertFalse(browser.ownsCreatorGalleryChrome());
        assertTrue(containsText(root, "Browse fixture"));
        assertNotNull(findDescription(root, "Feed options"));
        assertNotNull(findDescription(root, "Back"));
        controller.pause().stop().destroy();
    }
}
