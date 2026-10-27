package com.webapp.crazyshit;

import android.content.Intent;
import android.graphics.Color;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.RecyclerView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-port-mdpi")
public class ShowsDetailGlassTest {
    @Test public void panesShareOneBackdropAndLetEpisodesScrollUnderneath() {
        ActivityController<NativeFeedBrowserActivity> controller = details();
        NativeFeedBrowserActivity browser = controller.get();
        shadowOf(Looper.getMainLooper()).idle();
        ShowsDetailGlassLayout glass = root(browser);
        layout(glass);

        GlassHeaderView header = glass.headerForTest();
        GlassHeaderView episodes = glass.episodePaneForTest();
        GlassHeaderView pager = glass.pagerForTest();
        View content = glass.contentForTest();
        assertNotNull(header);
        assertNotNull(episodes);
        assertNotNull(pager);
        assertTrue(header.sharesBackdropWith(episodes));
        assertTrue(episodes.sharesBackdropWith(pager));
        assertTrue(header.usesHeaderGlass());
        assertTrue(episodes.usesHeaderGlass());
        assertTrue(pager.usesHeaderGlass());
        assertTrue(glass.excludesGlassFromRecording());
        assertEquals(BrowseUi.dp(browser, ShowsDetailGlassLayout.HEADER_HEIGHT_DP), header.getHeight());
        assertEquals(BrowseUi.dp(browser, ShowsDetailGlassLayout.EPISODE_BAR_HEIGHT_DP), episodes.getHeight());
        assertEquals(header.getBottom(), content.getTop());
        assertEquals(content.getBottom(), pager.getBottom());
        assertEquals(header.getTop(), glass.underlayClipTop());
        assertTrue(glass.underlayClipTop() < content.getTop());
        assertFalse(((ViewGroup) content).getClipChildren());

        ShowDetailsHeader hero = ReflectionHelpers.getField(browser, "showDetailsHeader");
        assertEquals(0, hero.getTop());
        assertEquals(BrowseUi.dp(browser, 286), hero.getHeight());
        assertEquals(content.getTop() + hero.getBottom(), episodes.getTop());

        RecyclerView list = ReflectionHelpers.getField(browser, "recycler");
        assertFalse(list.getClipToPadding());
        assertEquals(episodes.getHeight(), list.getPaddingTop());
        assertEquals(pager.getHeight(), list.getPaddingBottom());
        assertTrue(pager.getHeight() > episodes.getHeight());

        assertEquals(Color.WHITE, text(header, "Shows").getCurrentTextColor());
        assertEquals("Back", back(header).getContentDescription());
        assertEquals(UiPalette.PRIMARY, text(episodes, "EPISODES").getCurrentTextColor());
        assertEquals(UiPalette.PRIMARY, text(pager, "Next 10").getCurrentTextColor());
        assertEquals(Color.WHITE, text(pager, "Previous").getCurrentTextColor());
        assertEquals(browser.getColor(R.color.zc_text_secondary), summary(pager).getCurrentTextColor());
        assertTrue(((ViewGroup) pagerControls(pager)).getPaddingBottom() >= BrowseUi.dp(browser, 12));

        FrameLayout body = ReflectionHelpers.getField(browser, "showsDetailBody");
        body.offsetTopAndBottom(-80);
        glass.followEpisodeAnchor();
        assertEquals(content.getTop() + body.getTop(), episodes.getTop());
        assertTrue(episodes.getTop() >= header.getBottom());
        assertTrue(episodes.getBottom() <= pager.getTop());

        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(6, 24, 8, 16))
                .setInsets(WindowInsetsCompat.Type.mandatorySystemGestures(), Insets.of(0, 0, 0, 32))
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(0, 0, 0, 0))
                .build();
        ViewCompat.dispatchApplyWindowInsets(glass, insets);
        layout(glass);
        assertEquals(24, glass.getPaddingTop());
        assertEquals(6, glass.getPaddingLeft());
        assertEquals(8, glass.getPaddingRight());
        assertEquals(32, glass.getPaddingBottom());
        assertEquals(glass.getPaddingTop(), glass.headerForTest().getTop());
        assertEquals(glass.getHeight() - glass.getPaddingBottom(), glass.pagerForTest().getBottom());
        assertTrue(bottomIn(glass, text(glass.pagerForTest(), "Next 10"))
                <= glass.getHeight() - glass.getPaddingBottom() - BrowseUi.dp(browser, 8));
        assertEquals(glass.episodePaneForTest().getHeight(), list.getPaddingTop());
        assertEquals(glass.pagerForTest().getHeight(), list.getPaddingBottom());

        ViewPageBackdrop backdrop = glass.backdropForTest();
        ReflectionHelpers.setField(backdrop, "recorded", true);
        controller.pause().stop().destroy();
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse((boolean) ReflectionHelpers.getField(backdrop, "recorded"));
        androidx.lifecycle.LifecycleOwner owner = ReflectionHelpers.getField(browser, "showsGlassOwner");
        assertEquals(Lifecycle.State.DESTROYED, owner.getLifecycle().getCurrentState());
    }

    @Test public void ordinaryFeedsDoNotInstallShowGlass() {
        ActivityController<NativeFeedBrowserActivity> controller =
                Robolectric.buildActivity(NativeFeedBrowserActivity.class).create();
        View root = ((ViewGroup) controller.get().findViewById(android.R.id.content)).getChildAt(0);
        assertFalse(root instanceof ShowsDetailGlassLayout);
        controller.destroy();
    }

    private static ActivityController<NativeFeedBrowserActivity> details() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.putExtra(NativeFeedBrowserActivity.EXTRA_SHOW_DETAILS, true);
        intent.putExtra(NativeFeedBrowserActivity.EXTRA_TITLE, "FootJob");
        intent.putExtra(NativeFeedBrowserActivity.EXTRA_BASE_URL, "https://example.test/footjob");
        intent.putExtra(NativeFeedBrowserActivity.EXTRA_SOURCE, NativeFeedBrowserActivity.SOURCE_XMEGADRIVE);
        intent.putExtra(NativeFeedBrowserActivity.EXTRA_SHOW_KIND, NativeContentItem.KIND_CATEGORY);
        return Robolectric.buildActivity(NativeFeedBrowserActivity.class, intent).setup();
    }

    private static ShowsDetailGlassLayout root(NativeFeedBrowserActivity browser) {
        View root = ((ViewGroup) browser.findViewById(android.R.id.content)).getChildAt(0);
        assertTrue(root instanceof ShowsDetailGlassLayout);
        return (ShowsDetailGlassLayout) root;
    }

    private static void layout(View root) {
        int width = BrowseUi.dp(root.getContext(), 360);
        int height = BrowseUi.dp(root.getContext(), 800);
        int specW = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int specH = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
        root.measure(specW, specH);
        root.layout(0, 0, width, height);
        root.measure(specW, specH);
        root.layout(0, 0, width, height);
    }

    private static TextView text(View root, String label) {
        TextView found = textContaining(root, label);
        assertNotNull(label, found);
        return found;
    }

    private static TextView textContaining(View view, String label) {
        if (view instanceof TextView && ((TextView) view).getText() != null
                && ((TextView) view).getText().toString().contains(label)) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = textContaining(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TextView back(View header) {
        TextView found = text(header, "‹");
        assertEquals("Back", found.getContentDescription());
        return found;
    }

    private static TextView summary(GlassHeaderView pager) {
        TextView summary = firstSummary((ViewGroup) pagerControls(pager));
        assertNotNull(summary);
        return summary;
    }

    private static TextView firstSummary(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) {
                String value = ((TextView) child).getText().toString();
                if (!value.contains("Next") && !value.contains("Previous") && !value.contains("Load")
                        && !value.contains("Retry") && !value.contains("EPISODES")) return (TextView) child;
            } else if (child instanceof ViewGroup) {
                TextView found = firstSummary((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View pagerControls(GlassHeaderView pager) {
        for (int i = 0; i < pager.getChildCount(); i++) {
            View child = pager.getChildAt(i);
            if (child instanceof android.widget.LinearLayout) return child;
        }
        fail("pager controls missing");
        return null;
    }

    private static int bottomIn(View ancestor, View view) {
        int bottom = view.getBottom();
        View parent = (View) view.getParent();
        while (parent != null && parent != ancestor) {
            bottom += parent.getTop();
            parent = parent.getParent() instanceof View ? (View) parent.getParent() : null;
        }
        if (parent == ancestor) bottom += 0;
        return bottom;
    }
}
