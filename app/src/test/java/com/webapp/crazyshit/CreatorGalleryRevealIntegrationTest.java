package com.webapp.crazyshit;

import android.app.Application;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Exercise real global styling against the new wrapper without starting providers. */
@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {35, 36})
public class CreatorGalleryRevealIntegrationTest {
    @Test public void deferredOledPassesLeaveOuterWindowTransparentAndNormalBrowserBlack() {
        NativeFeedBrowserActivity browser = Robolectric.buildActivity(NativeFeedBrowserActivity.class,
                new Intent().putExtra(CreatorGalleryTransition.EXTRA_RIPPLE_REVEAL, true)).get();
        browser.setTheme(R.style.Theme_ZeroChill_ShowsWatch);
        View page = new View(browser);
        browser.setContentView(new CreatorGalleryReveal(browser, page, false));
        View outer = browser.findViewById(android.R.id.content);
        outer.setBackgroundColor(Color.TRANSPARENT);
        // The production coordinator invokes this at 0, 140 and 520ms.
        for (int i = 0; i < 3; i++) ReflectionHelpers.callStaticMethod(OledThemeController.class,
                "apply", ReflectionHelpers.ClassParameter.from(android.app.Activity.class, browser));
        assertEquals(Color.TRANSPARENT, ((ColorDrawable) outer.getBackground()).getColor());
        browser.getIntent().removeExtra(CreatorGalleryTransition.EXTRA_RIPPLE_REVEAL);
        ReflectionHelpers.callStaticMethod(OledThemeController.class, "apply",
                ReflectionHelpers.ClassParameter.from(android.app.Activity.class, browser));
        assertEquals(Color.BLACK, ((ColorDrawable) outer.getBackground()).getColor());
        ((java.util.concurrent.ExecutorService) ReflectionHelpers.getField(browser, "io")).shutdownNow();
    }

    @Test public void immersiveControllerStillStylesOnlyTheToolbarInsideRevealWrapper() {
        NativeFeedBrowserActivity browser = Robolectric.buildActivity(NativeFeedBrowserActivity.class,
                new Intent().putExtra(CreatorGalleryTransition.EXTRA_RIPPLE_REVEAL, true)).get();
        browser.setTheme(R.style.Theme_ZeroChill_ShowsWatch);
        LinearLayout page = new LinearLayout(browser);
        page.setOrientation(LinearLayout.VERTICAL);
        TextView toolbar = new TextView(browser);
        toolbar.setText("Gallery toolbar");
        page.addView(toolbar);
        RecyclerView grid = new RecyclerView(browser);
        page.addView(grid);
        ReflectionHelpers.setField(browser, "recycler", grid);
        CreatorGalleryReveal reveal = new CreatorGalleryReveal(browser, page, false);
        browser.setContentView(reveal);
        ReflectionHelpers.callStaticMethod(OledImmersiveUiController.class, "applyBrowser",
                ReflectionHelpers.ClassParameter.from(NativeFeedBrowserActivity.class, browser));
        java.util.Map<?, ?> states = ReflectionHelpers.getStaticField(OledImmersiveUiController.class, "STATES");
        Object state = states.get(browser);
        assertSame(toolbar, ReflectionHelpers.getField(state, "topBar"));
        assertSame(page, ReflectionHelpers.getField(state, "ambientTarget"));
        assertSame(reveal, page.getParent());
        OledImmersiveUiController.detach(browser);
        ((java.util.concurrent.ExecutorService) ReflectionHelpers.getField(browser, "io")).shutdownNow();
    }
    @Test @Config(sdk = 26)
    public void androidEightKeepsOpaqueWindowForPortraitLockedGallery() {
        NativeFeedBrowserActivity browser = Robolectric.buildActivity(NativeFeedBrowserActivity.class,
                new Intent().putExtra(CreatorGalleryTransition.EXTRA_RIPPLE_REVEAL, true)).get();
        assertFalse(CreatorGalleryTransition.ownsRevealWindow(browser));
        assertFalse(CreatorGalleryTransition.shouldAnimate(browser, false));
        ((java.util.concurrent.ExecutorService) ReflectionHelpers.getField(browser, "io")).shutdownNow();
    }

}
