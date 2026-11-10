package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Media orientation is presentation-only; existing view actions must survive grouping. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w360dp-h800dp")
public class LibraryMixedMediaGridTest {
    @Test public void mixedOrientationPreservesOrderAndOriginalClickTargets() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LibraryMixedMediaGrid gallery = new LibraryMixedMediaGrid(activity);
        AtomicInteger clicked = new AtomicInteger();
        View horizontal = card(activity, clicked);
        View portraitOne = card(activity, clicked);
        View portraitTwo = card(activity, clicked);
        Map<String, Boolean> portrait = new HashMap<>();
        portrait.put("p1", true);
        portrait.put("p2", true);
        gallery.setItems(Arrays.asList(horizontal, portraitOne, portraitTwo),
                Arrays.asList("l", "p1", "p2"), portrait);
        assertEquals(2, gallery.getChildCount());
        LinearLayout first = (LinearLayout) gallery.getChildAt(0);
        LinearLayout second = (LinearLayout) gallery.getChildAt(1);
        assertSame(horizontal, first.getChildAt(0));
        assertSame(portraitOne, second.getChildAt(0));
        assertSame(portraitTwo, second.getChildAt(1));
        assertTrue(((LibraryAspectMediaCard) ((LinearLayout) portraitOne).getChildAt(0)).isPortrait());
        assertTrue(horizontal.performClick());
        assertTrue(portraitOne.performClick());
        assertEquals(2, clicked.get());
        gallery.dispose();
    }

    @Test public void aSinglePortraitGetsHalfRowInsteadOfBeingStretched() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LibraryMixedMediaGrid gallery = new LibraryMixedMediaGrid(activity);
        View portraitItem = card(activity, new AtomicInteger());
        gallery.setItems(Arrays.asList(portraitItem), Arrays.asList("p"), Map.of("p", true));
        assertEquals(1, gallery.getChildCount());
        LinearLayout row = (LinearLayout) gallery.getChildAt(0);
        assertEquals(2, row.getChildCount());
        assertSame(portraitItem, row.getChildAt(0));
        assertEquals(0, row.getChildAt(0).getLayoutParams().width);
        gallery.dispose();
    }

    @Test public void portraitAndLandscapeCardsUseDifferentHeightRatios() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LibraryAspectMediaCard card = new LibraryAspectMediaCard(activity);
        int width = View.MeasureSpec.makeMeasureSpec(180, View.MeasureSpec.EXACTLY);
        card.setPortrait(false);
        card.measure(width, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int landscapeHeight = card.getMeasuredHeight();
        card.setPortrait(true);
        card.measure(width, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        assertTrue(card.getMeasuredHeight() > landscapeHeight);
    }

    @Test public void audioSafePreviewModeNeverUsesRenderedPageFallback() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        RenderedThumbnailResolver resolver = new RenderedThumbnailResolver(
                activity, (page, thumbnail) -> { }, false);
        try {
            assertEquals(Boolean.FALSE, ReflectionHelpers.getField(resolver, "renderedPageFallback"));
        } finally {
            resolver.close();
        }
    }

    private static View card(Activity activity, AtomicInteger clicked) {
        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(new LibraryAspectMediaCard(activity),
                new LinearLayout.LayoutParams(-1, -2));
        wrapper.setOnClickListener(v -> clicked.incrementAndGet());
        return wrapper;
    }
}
