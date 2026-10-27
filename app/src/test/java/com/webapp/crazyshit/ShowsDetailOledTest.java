package com.webapp.crazyshit;

import android.app.Application;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.AppBarLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w360dp-h800dp-port-mdpi")
public class ShowsDetailOledTest {
    @Test public void heroStartsAtTopAndToolbarOverlaysArtworkThenTurnsBlack() {
        try (Fixture f = new Fixture(null)) {
            View hero = field(f.browser, "showDetailsHeader");
            View toolbar = field(f.browser, "showsToolbar");
            assertEquals(0, topIn(f.root, hero));
            assertEquals(0, topIn(f.root, toolbar));
            assertEquals(286, hero.getHeight());
            assertEquals(64, toolbar.getHeight());
            assertEquals(Color.WHITE, findText(toolbar, "Shows").getCurrentTextColor());
            assertEquals(Color.WHITE, findText(toolbar, "‹").getCurrentTextColor());
            ColorDrawable black = field(f.browser, "showsToolbarBlack");
            assertEquals(0, black.getAlpha());
            int total = f.appBar.getTotalScrollRange();
            assertTrue(total > 0);
            f.collapse(total / 2);
            assertTrue(black.getAlpha() > 0 && black.getAlpha() < 255);
            f.collapse(total);
            assertEquals(-total, f.behavior().getTopAndBottomOffset());
            assertEquals(255, black.getAlpha());
            assertEquals(0f, hero.getAlpha(), 0f);
            assertEquals(64, toolbar.getHeight());
            assertEquals(0, topIn(f.root, toolbar));
            assertTrue(topIn(f.root, f.list) >= toolbar.getBottom());
        }
    }

    @Test public void episodesAndPagerAreInlineRowsWithoutArtworkOcclusion() {
        try (Fixture f = new Fixture(null)) {
            View section = field(f.browser, "showsEpisodeBar");
            View pager = field(f.browser, "showsEpisodePager");
            assertTrue(f.list.getAdapter() instanceof ConcatAdapter);
            assertEquals(12, f.list.getAdapter().getItemCount());
            assertEquals(10, f.episodes.getItemCount());
            assertEquals(Color.BLACK, ((ColorDrawable) section.getBackground()).getColor());
            assertEquals(UiPalette.PRIMARY, findText(section, "EPISODES").getCurrentTextColor());
            TextView range = field(f.browser, "episodeRangeAction");
            assertEquals(Color.WHITE, range.getCurrentTextColor());
            assertTrue(range.getHeight() >= 48);
            assertEquals(0, f.list.getPaddingTop());
            assertEquals(18, f.list.getPaddingBottom());
            assertTrue(pager.getParent() == null); // No permanent overlay in the root.
            f.collapse(f.appBar.getTotalScrollRange());
            ((LinearLayoutManager) f.list.getLayoutManager()).scrollToPositionWithOffset(10, 0);
            f.layout();
            assertSame(f.list, pager.getParent());
            assertEquals(11, f.list.getChildAdapterPosition(pager));
            RecyclerView.ViewHolder last = f.list.findViewHolderForAdapterPosition(10);
            assertNotNull(last);
            assertTrue(pager.getTop() >= last.itemView.getBottom());
            assertTrue(pager.getHeight() < 210);
        }
    }

    @Test public void firstMiddleFinalAndSourceRetryStatesRemainCompact() {
        try (Fixture f = new Fixture(null)) {
            TextView previous = field(f.browser, "episodePrevious");
            TextView next = field(f.browser, "episodeNext");
            TextView summary = field(f.browser, "episodePageSummary");
            TextView end = field(f.browser, "episodeEnd");
            TextView more = field(f.browser, "episodeLoadMore");
            assertEquals("1–10 of 40", summary.getText().toString());
            assertEquals(View.INVISIBLE, previous.getVisibility());
            assertFalse(previous.isEnabled());
            assertEquals(View.VISIBLE, next.getVisibility());
            next.performClick();
            assertEquals("11–20 of 40", summary.getText().toString());
            assertEquals(View.VISIBLE, previous.getVisibility());
            assertTrue(previous.isEnabled());
            assertEquals(View.VISIBLE, next.getVisibility());
            ReflectionHelpers.setField(f.browser, "episodeRangeStart", 30);
            f.apply(false, -1);
            assertEquals("31–40 of 40", summary.getText().toString());
            assertEquals(View.INVISIBLE, next.getVisibility());
            assertEquals(View.VISIBLE, end.getVisibility());
            assertEquals(View.GONE, more.getVisibility());
            ReflectionHelpers.setField(f.browser, "endReached", false);
            ReflectionHelpers.setField(f.browser, "loading", true);
            f.update();
            assertEquals(View.GONE, end.getVisibility());
            assertEquals("Loading more…", more.getText().toString());
            assertFalse(more.isEnabled());
            ReflectionHelpers.setField(f.browser, "loading", false);
            ReflectionHelpers.setField(f.browser, "episodePrefetchFailedPage", 2);
            f.update();
            assertEquals("Retry loading more", more.getText().toString());
            assertTrue(more.isEnabled());
            more.performClick();
            assertEquals(-1, (int) ReflectionHelpers.getField(f.browser, "episodePrefetchFailedPage"));
            assertTrue((boolean) ReflectionHelpers.getField(f.browser, "loading"));
            assertEquals(2, f.requests.count); // Initial load plus this explicit retry.
        }
    }

    @Test public void rangeChangesKeepPartialHeroCollapseAndAlignFirstEpisodeWithMotionOff() {
        try (Fixture f = new Fixture(null)) {
            Settings.Global.putFloat(f.browser.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
            f.collapse(111);
            int offset = f.behavior().getTopAndBottomOffset();
            ((LinearLayoutManager) f.list.getLayoutManager()).scrollToPositionWithOffset(9, -30);
            f.layout();
            TextView next = field(f.browser, "episodeNext");
            next.performClick();
            f.idleAndLayout();
            assertEquals(10, (int) ReflectionHelpers.getField(f.browser, "episodeRangeStart"));
            f.assertSelectedAtTop(1, offset);
            TextView previous = field(f.browser, "episodePrevious");
            previous.performClick();
            f.idleAndLayout();
            f.assertSelectedAtTop(1, offset);
            // Title-search selection uses the same range path with the selected row.
            f.apply(true, 24);
            f.idleAndLayout();
            assertEquals(20, (int) ReflectionHelpers.getField(f.browser, "episodeRangeStart"));
            f.assertSelectedAtTop(5, offset);
        }
    }

    @Test public void animatedPagingKeepsCollapsedAppBarAndRestoresNestedScrolling() {
        try (Fixture f = new Fixture(null)) {
            Settings.Global.putFloat(f.browser.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            f.collapse(f.appBar.getTotalScrollRange());
            int offset = f.behavior().getTopAndBottomOffset();
            ((LinearLayoutManager) f.list.getLayoutManager()).scrollToPositionWithOffset(9, 0);
            f.layout();
            ((TextView) field(f.browser, "episodeNext")).performClick();
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
            f.layout();
            f.assertSelectedAtTop(1, offset);
            assertTrue(f.list.isNestedScrollingEnabled());
        }
    }

    @Test public void savedRangeScrollAndAppBarOffsetSurviveRecreation() {
        Bundle saved = new Bundle();
        int expected;
        try (Fixture f = new Fixture(null)) {
            ReflectionHelpers.setField(f.browser, "episodeRangeStart", 20);
            f.apply(false, -1);
            f.collapse(123);
            expected = f.behavior().getTopAndBottomOffset();
            ((LinearLayoutManager) f.list.getLayoutManager()).scrollToPositionWithOffset(4, -22);
            f.layout();
            f.controller.saveInstanceState(saved);
        }
        try (Fixture f = new Fixture(saved)) {
            assertEquals(20, (int) ReflectionHelpers.getField(f.browser, "episodeRangeStart"));
            ReflectionHelpers.callInstanceMethod(f.browser, "restoreScrollPositions");
            f.layout();
            assertEquals(expected, f.behavior().getTopAndBottomOffset());
            LinearLayoutManager lm = (LinearLayoutManager) f.list.getLayoutManager();
            assertEquals(4, lm.findFirstVisibleItemPosition());
            assertEquals(-22, lm.findViewByPosition(4).getTop());
        }
    }

    @Test public void delayedRestylingDoesNotChangeShowsButOrdinaryFeedsKeepTheirShell() {
        try (Fixture f = new Fixture(null)) {
            f.collapse(90);
            OledImmersiveUiController.attachBrowser(f.browser);
            FeedViewStyleController.attachBrowser(f.browser);
            ResponsiveFitmentController.applySoon(f.browser);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600));
            f.layout();
            View toolbar = field(f.browser, "showsToolbar");
            assertEquals(64, toolbar.getHeight());
            assertEquals(0, topIn(f.root, toolbar));
            assertTrue(f.list.getLayoutManager() instanceof LinearLayoutManager);
            assertFalse(f.list.getLayoutManager() instanceof androidx.recyclerview.widget.GridLayoutManager);
            assertEquals(0, f.list.getPaddingTop());
            assertEquals(18, f.list.getPaddingBottom());
            assertEquals(-90, f.behavior().getTopAndBottomOffset());
            WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(6, 24, 8, 32)).build();
            ViewCompat.dispatchApplyWindowInsets(f.root, insets);
            f.layout();
            assertEquals(24, topIn(f.root, field(f.browser, "showDetailsHeader")) + 90);
            assertEquals(24, topIn(f.root, toolbar));
            assertEquals(6, f.root.getPaddingLeft());
            assertEquals(32, f.root.getPaddingBottom());
        }
        ActivityController<NativeFeedBrowserActivity> ordinary =
                Robolectric.buildActivity(NativeFeedBrowserActivity.class);
        ReflectionHelpers.setField(ordinary.get(), "io", new RecordingExecutor());
        ordinary.setup();
        assertFalse(ordinary.get().ownsShowsDetailChrome());
        RecyclerView list = field(ordinary.get(), "recycler");
        assertTrue(list.getAdapter() instanceof NativeFeedAdapter);
        assertTrue(((ViewGroup) ordinary.get().findViewById(android.R.id.content)).getChildAt(0)
                instanceof android.widget.LinearLayout);
        ordinary.pause().stop().destroy();
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void renderActualExpandedCollapsedAndPagingGeometry() throws Exception {
        try (Fixture f = new Fixture(null)) {
            ShowDetailsHeader hero = field(f.browser, "showDetailsHeader");
            ImageView image = ReflectionHelpers.getField(hero, "artwork");
            image.setImageDrawable(new ColorDrawable(Color.rgb(32, 73, 95)));
            f.render("expanded");
            f.collapse(f.appBar.getTotalScrollRange());
            f.render("collapsed");
            ((LinearLayoutManager) f.list.getLayoutManager()).scrollToPositionWithOffset(10, 0);
            f.layout();
            f.render("pager");
        }
    }

    private static final class Fixture implements AutoCloseable {
        final RecordingExecutor requests = new RecordingExecutor();
        final ActivityController<NativeFeedBrowserActivity> controller;
        final NativeFeedBrowserActivity browser;
        final View root;
        final RecyclerView list;
        final NativeFeedAdapter episodes;
        final AppBarLayout appBar;
        Fixture(Bundle state) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.putExtra(NativeFeedBrowserActivity.EXTRA_SHOW_DETAILS, true);
            intent.putExtra(NativeFeedBrowserActivity.EXTRA_TITLE, "Sample collection");
            intent.putExtra(NativeFeedBrowserActivity.EXTRA_BASE_URL, "https://example.test/category");
            intent.putExtra(NativeFeedBrowserActivity.EXTRA_SOURCE, NativeFeedBrowserActivity.SOURCE_XMEGADRIVE);
            intent.putExtra(NativeFeedBrowserActivity.EXTRA_SHOW_KIND, NativeContentItem.KIND_CATEGORY);
            controller = Robolectric.buildActivity(NativeFeedBrowserActivity.class, intent);
            browser = controller.get();
            ReflectionHelpers.setField(browser, "io", requests);
            controller.create(state).start().resume().visible();
            root = ((ViewGroup) browser.findViewById(android.R.id.content)).getChildAt(0);
            list = field(browser, "recycler");
            episodes = field(browser, "adapter");
            appBar = field(browser, "showDetailsAppBar");
            ReflectionHelpers.setField(browser, "restoringBrowser", false);
            ReflectionHelpers.setField(browser, "loading", false);
            ReflectionHelpers.setField(browser, "currentPage", 1);
            ReflectionHelpers.setField(browser, "endReached", true);
            List<NativeContentItem> all = ReflectionHelpers.getField(browser, "showEpisodes");
            for (int i = 0; i < 40; i++) all.add(new NativeContentItem(NativeContentItem.KIND_MEDIA,
                    "Original source title for episode " + (i + 1), "https://example.test/" + i,
                    "", "", "", ""));
            apply(false, -1);
            ((View) field(browser, "progress")).setVisibility(View.GONE);
            idleAndLayout();
        }
        AppBarLayout.Behavior behavior() {
            return (AppBarLayout.Behavior) ((CoordinatorLayout.LayoutParams) appBar.getLayoutParams()).getBehavior();
        }
        void collapse(int amount) {
            behavior().onNestedPreScroll((CoordinatorLayout) appBar.getParent(), appBar, list,
                    0, amount, new int[2], ViewCompat.TYPE_NON_TOUCH);
            layout();
        }
        void apply(boolean scroll, int index) {
            ReflectionHelpers.callInstanceMethod(browser, "applyEpisodeRange",
                    ReflectionHelpers.ClassParameter.from(boolean.class, scroll),
                    ReflectionHelpers.ClassParameter.from(int.class, index));
        }
        void update() { ReflectionHelpers.callInstanceMethod(browser, "updateEpisodeRangeUi"); }
        void idleAndLayout() { shadowOf(Looper.getMainLooper()).idle(); layout(); }
        void layout() {
            for (int i = 0; i < 3; i++) {
                root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
                root.layout(0, 0, 360, 800);
            }
        }
        void assertSelectedAtTop(int position, int offset) {
            assertEquals(offset, behavior().getTopAndBottomOffset());
            LinearLayoutManager lm = (LinearLayoutManager) list.getLayoutManager();
            assertEquals(position, lm.findFirstVisibleItemPosition());
            assertEquals(0, lm.findViewByPosition(position).getTop());
        }
        void render(String state) throws Exception {
            layout();
            Bitmap bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(bitmap));
            File output = new File("build/reports/visual-tests/shows-detail-oled-" + state + ".png");
            output.getParentFile().mkdirs();
            try (FileOutputStream stream = new FileOutputStream(output)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            }
            bitmap.recycle();
        }
        @Override public void close() { controller.pause().stop().destroy(); }
    }
    @SuppressWarnings("unchecked") private static <T> T field(Object target, String name) {
        return ReflectionHelpers.getField(target, name);
    }
    private static TextView findText(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) return (TextView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static int topIn(View root, View view) {
        int top = view.getTop();
        while (view.getParent() instanceof View && view.getParent() != root) {
            view = (View) view.getParent();
            top += view.getTop();
        }
        return top;
    }
    private static final class RecordingExecutor extends AbstractExecutorService {
        int count;
        boolean closed;
        @Override public void execute(Runnable task) { count++; }
        @Override public void shutdown() { closed = true; }
        @Override public List<Runnable> shutdownNow() { closed = true; return Collections.emptyList(); }
        @Override public boolean isShutdown() { return closed; }
        @Override public boolean isTerminated() { return closed; }
        @Override public boolean awaitTermination(long t, TimeUnit unit) { return closed; }
    }
}
