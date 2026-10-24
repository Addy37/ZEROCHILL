package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.io.BufferedReader;
import java.io.StringReader;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, shadows = {
        SearchResultsPolishTest.SearchSource.class, SearchResultsPolishTest.Preload.class})
public class SearchResultsPolishTest {
    @Implements(value = FapzoneCreatorSearchRepository.class, isInAndroidSdk = false)
    public static class SearchSource {
        static List<String> queries = new CopyOnWriteArrayList<>();
        static CountDownLatch partial, finish;
        static volatile FapzoneCreatorSearchRepository.ResultListener listener;
        @Implementation protected List<NativeContentItem> search(Context context, String query, int limit,
                FapzoneCreatorSearchRepository.ResultListener callback) throws Exception {
            queries.add(query);
            listener = callback;
            callback.onUpdate(Collections.singletonList(creator("First " + query)), false);
            partial.countDown();
            finish.await(3, TimeUnit.SECONDS);
            return Arrays.asList(creator("First " + query), creator("Second " + query));
        }
    }
    @Implements(value = CreatorGalleryPreloader.class, isInAndroidSdk = false)
    public static class Preload {
        @Implementation protected static void warm(Context context, NativeContentItem item, int priority) { }
    }

    @Before public void reset() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        c.getSharedPreferences(RecentSearchStore.PREFS, 0).edit().clear().commit();
        c.getSharedPreferences(CreatorCatalog.PREFS, 0).edit().clear().commit();
        c.getSharedPreferences("creator_favorites", 0).edit().clear().commit();
        c.getSharedPreferences("app_prefs", 0).edit().putBoolean("immersive_motion_enabled", false).commit();
        ReflectionHelpers.setStaticField(BundledCreatorIndex.class, "cached",
                BundledCreatorIndex.parse(new BufferedReader(new StringReader(""))));
        SearchSource.queries.clear();
        SearchSource.partial = new CountDownLatch(1);
        SearchSource.finish = new CountDownLatch(1);
    }
    @After public void release() {
        SearchSource.finish.countDown();
        ReflectionHelpers.setStaticField(BundledCreatorIndex.class, "cached", null);
    }

    private static NativeContentItem creator(String name) {
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, name,
                "https://fapello.com/" + name.toLowerCase(Locale.ROOT).replace(' ', '-') + "/",
                "", "", "", "", "Fapello", name);
    }

    @Test public void recentQueriesAreBoundedDeduplicatedRemovableAndPersistent() {
        Context c = RuntimeEnvironment.getApplication();
        for (int i = 0; i < 12; i++) RecentSearchStore.remember(c, "Query " + i);
        RecentSearchStore.remember(c, "  QUERY 5  ");
        List<String> saved = RecentSearchStore.load(c);
        assertEquals(10, saved.size());
        assertEquals("QUERY 5", saved.get(0));
        assertFalse(saved.contains("Query 0"));
        assertEquals(saved, RecentSearchStore.load(c.getApplicationContext()));
        RecentSearchStore.remove(c, "query 5");
        assertEquals(9, RecentSearchStore.load(c).size());
        RecentSearchStore.clear(c);
        assertTrue(RecentSearchStore.load(c).isEmpty());
    }

    @Test public void historyShowsOnEmptyFieldAndSupportsRerunAndRemoval() {
        var host = Robolectric.buildActivity(Activity.class).setup();
        RecentSearchStore.remember(host.get(), "Sophie");
        SearchPanel panel = new SearchPanel(host.get(), new Intent().putExtra("scope", "bunkr"), true, () -> {});
        panel.create(null);
        try {
            View content = ReflectionHelpers.getField(panel, "content");
            assertEquals(View.VISIBLE, content.getVisibility());
            View rerun = find(panel.view, "Search again for Sophie");
            assertNotNull(rerun);
            rerun.performClick();
            assertEquals("Sophie", panel.field().getText().toString());
            assertFalse((boolean) ReflectionHelpers.getField(panel, "previewMode"));
            panel.field().setText("");
            find(panel.view, "Remove recent search Sophie").performClick();
            assertTrue(RecentSearchStore.load(host.get()).isEmpty());
            RecentSearchStore.remember(host.get(), "Another");
            panel.resume();
            find(panel.view, "Clear all recent searches").performClick();
            assertTrue(RecentSearchStore.load(host.get()).isEmpty());
        } finally { panel.close(); host.pause().stop().destroy(); }
    }

    @Test public void livePartialAndFinalResultsShareOneListAndStaleResponsesAreRejected() throws Exception {
        var host = Robolectric.buildActivity(Activity.class).setup();
        SearchPanel panel = new SearchPanel(host.get(), new Intent().putExtra("scope", "bunkr"), true, () -> {});
        panel.create(null);
        try {
            RecyclerView list = ReflectionHelpers.getField(panel, "recycler");
            panel.field().setText("needle");
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(280));
            assertTrue(SearchSource.partial.await(3, TimeUnit.SECONDS));
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, list.getAdapter().getItemCount());
            assertEquals(View.VISIBLE, ((View) ReflectionHelpers.getField(panel, "progress")).getVisibility());
            assertTrue(((ZeroChillProgressBar) ReflectionHelpers.getField(panel, "progress"))
                    .getIndeterminateDrawable() instanceof ZeroChillDotsDrawable);
            SearchSource.listener.onUpdate(Arrays.asList(creator("First needle"), creator("More needle")), false);
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(2, list.getAdapter().getItemCount());
            SearchSource.finish.countDown();
            await(() -> ((int) ReflectionHelpers.getField(panel, "pendingSources")) == 0);
            assertEquals(3, list.getAdapter().getItemCount());
            assertSame(list, ReflectionHelpers.getField(panel, "recycler"));
            assertEquals(View.GONE, ((View) ReflectionHelpers.getField(panel, "progress")).getVisibility());
            assertTrue(RecentSearchStore.load(host.get()).isEmpty());
            panel.field().setText("");
            SearchSource.listener.onUpdate(Collections.singletonList(creator("Stale")), false);
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(0, list.getAdapter().getItemCount());
        } finally { panel.close(); host.pause().stop().destroy(); }
    }

    @Test public void largerCreatorRowsClipPlaceholderAndRetainFavoriteActions() {
        var host = Robolectric.buildActivity(Activity.class).setup();
        try {
            OnlyFapCreatorSearchAdapter adapter = new OnlyFapCreatorSearchAdapter(item -> {});
            adapter.replace(Collections.singletonList(creator("Fixture")));
            var holder = adapter.onCreateViewHolder(new FrameLayout(host.get()), 0);
            adapter.onBindViewHolder(holder, 0);
            assertTrue(holder.avatar.getClipToOutline());
            assertEquals(BrowseUi.dp(host.get(), 72), holder.avatar.getLayoutParams().width);
            assertEquals(17f, holder.name.getTextSize() / host.get().getResources().getDisplayMetrics().scaledDensity, .1f);
            assertEquals(BrowseUi.dp(host.get(), 36),
                    ((android.graphics.drawable.GradientDrawable) holder.avatar.getBackground()).getCornerRadius(), 1f);
            holder.favorite.performClick();
            assertEquals("★", holder.favorite.getText().toString());
            holder.card.performLongClick();
            assertEquals("☆", holder.favorite.getText().toString());
            adapter.onViewRecycled(holder);
            assertEquals(1f, holder.card.getAlpha(), 0f);
        } finally { host.pause().stop().destroy(); }
    }

    @Test public void artworkFitsExistingRowsAndRecycledCreatorsReturnToThumbnailGeometry() {
        var host = Robolectric.buildActivity(Activity.class).setup();
        GlobalSearchAdapter adapter = new GlobalSearchAdapter(item -> {});
        try {
            NativeContentItem normal = new NativeContentItem(NativeContentItem.KIND_CATEGORY,
                    "A long result title that still wraps to at most two lines", "https://example.com/category", "", "", "", "");
            adapter.replace(Arrays.asList(GlobalSearchAdapter.Entry.item(normal, 0),
                    GlobalSearchAdapter.Entry.item(creator("A long creator name that wraps"), 0)));
            var holder = (GlobalSearchAdapter.ResultHolder) adapter.onCreateViewHolder(new FrameLayout(host.get()), 1);
            for (int position : new int[]{0, 1, 0}) {
                adapter.onBindViewHolder(holder, position);
                boolean creator = position == 1;
                assertEquals(BrowseUi.dp(host.get(), creator ? 72 : 100), holder.image.getLayoutParams().width);
                assertEquals(BrowseUi.dp(host.get(), creator ? 72 : 75), holder.image.getLayoutParams().height);
                assertTrue(holder.image.getClipToOutline());
                assertEquals(BrowseUi.dp(host.get(), creator ? 36 : 8),
                        ((android.graphics.drawable.GradientDrawable) holder.image.getBackground()).getCornerRadius(), 1f);
                assertEquals(17f, holder.title.getTextSize() / host.get().getResources().getDisplayMetrics().scaledDensity, .1f);
                assertEquals(2, holder.title.getMaxLines());
                assertEquals(android.text.TextUtils.TruncateAt.END, holder.title.getEllipsize());
                assertTrue(holder.title.getTypeface().isBold() || holder.title.getPaint().isFakeBoldText());
                assertEquals(BrowseUi.dp(host.get(), 48), holder.favorite.getLayoutParams().width);
                assertEquals(BrowseUi.dp(host.get(), 104), holder.card.getMinimumHeight());
                assertArtworkFits(host.get(), holder.card, holder.image);
            }
            OnlyFapCreatorSearchAdapter creators = new OnlyFapCreatorSearchAdapter(item -> {});
            creators.replace(Collections.singletonList(creator("A long creator name that wraps")));
            var creatorHolder = creators.onCreateViewHolder(new FrameLayout(host.get()), 0);
            creators.onBindViewHolder(creatorHolder, 0);
            assertEquals(BrowseUi.dp(host.get(), 72), creatorHolder.avatar.getLayoutParams().height);
            assertEquals(BrowseUi.dp(host.get(), 104), creatorHolder.card.getMinimumHeight());
            assertEquals(BrowseUi.dp(host.get(), 48), creatorHolder.favorite.getLayoutParams().width);
            assertEquals(2, creatorHolder.name.getMaxLines());
            assertEquals(android.text.TextUtils.TruncateAt.END, creatorHolder.name.getEllipsize());
            assertArtworkFits(host.get(), creatorHolder.card, creatorHolder.avatar);
        } finally { adapter.close(); host.pause().stop().destroy(); }
    }

    private static void assertArtworkFits(Activity activity, View card, View artwork) {
        for (int width : new int[]{320, 400}) {
            card.measure(View.MeasureSpec.makeMeasureSpec(BrowseUi.dp(activity, width), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            card.layout(0, 0, card.getMeasuredWidth(), card.getMeasuredHeight());
            View row = (View) artwork.getParent();
            assertEquals(artwork.getLayoutParams().height, artwork.getHeight());
            assertTrue(artwork.getTop() >= row.getPaddingTop());
            assertTrue(artwork.getBottom() <= row.getHeight() - row.getPaddingBottom());
        }
    }

    @Test public void globalRowsKeepTheirOrderWhenAnotherSourceArrives() {
        GlobalSearchAdapter adapter = new GlobalSearchAdapter(item -> {});
        NativeContentItem a = creator("Alpha"), b = creator("Beta"), video = new NativeContentItem(
                NativeContentItem.KIND_MEDIA, "Video", "https://example.com/video", "", "", "", "");
        adapter.replace(Arrays.asList(GlobalSearchAdapter.Entry.section("Creators • 1"),
                GlobalSearchAdapter.Entry.item(a, 0)));
        long firstId = adapter.getItemId(1);
        adapter.replace(Arrays.asList(GlobalSearchAdapter.Entry.section("Videos • 1"),
                GlobalSearchAdapter.Entry.item(video, 0), GlobalSearchAdapter.Entry.section("Creators • 2"),
                GlobalSearchAdapter.Entry.item(b, 0), GlobalSearchAdapter.Entry.item(a, 0)));
        assertEquals(firstId, adapter.getItemId(1));
        assertEquals(5, adapter.getItemCount());
        android.os.Bundle state = new android.os.Bundle();
        adapter.saveOrder(state);
        GlobalSearchAdapter restored = new GlobalSearchAdapter(item -> {});
        restored.restoreOrder(state);
        restored.replace(Arrays.asList(GlobalSearchAdapter.Entry.section("Videos • 1"),
                GlobalSearchAdapter.Entry.item(video, 0), GlobalSearchAdapter.Entry.section("Creators • 2"),
                GlobalSearchAdapter.Entry.item(b, 0), GlobalSearchAdapter.Entry.item(a, 0)));
        assertEquals(firstId, restored.getItemId(1));
    }

    @Test public void entranceFadesDoNotReplayOnMetadataUpdatesAndRespectReducedMotion() {
        var host = Robolectric.buildActivity(Activity.class).setup();
        try {
            View row = new View(host.get());
            SearchResultFades fades = new SearchResultFades();
            fades.show(row, "one");
            assertEquals(1f, row.getAlpha(), 0f);
            host.get().getSharedPreferences("app_prefs", 0).edit().putBoolean("immersive_motion_enabled", true).commit();
            android.provider.Settings.Global.putFloat(host.get().getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            fades.show(row, "two");
            assertEquals(0f, row.getAlpha(), 0f);
            fades.show(row, "two");
            assertEquals(1f, row.getAlpha(), 0f);
            SearchResultFades.recycle(row);
            assertEquals(1f, row.getAlpha(), 0f);
        } finally { host.pause().stop().destroy(); }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void renderLargerCreatorRowsForVisualReview() throws Exception {
        var host = Robolectric.buildActivity(Activity.class).setup();
        try {
            android.widget.LinearLayout rows = new android.widget.LinearLayout(host.get());
            rows.setOrientation(android.widget.LinearLayout.VERTICAL);
            rows.setBackgroundColor(android.graphics.Color.BLACK);
            OnlyFapCreatorSearchAdapter adapter = new OnlyFapCreatorSearchAdapter(item -> {});
            adapter.replace(Arrays.asList(creator("Sophie Example"),
                    creator("A Longer Creator Name That Wraps"), creator("Another Creator")));
            for (int i = 0; i < 3; i++) {
                var holder = adapter.onCreateViewHolder(rows, 0);
                adapter.onBindViewHolder(holder, i);
                rows.addView(holder.card, new android.widget.LinearLayout.LayoutParams(-1, -2));
            }
            host.get().setContentView(rows);
            int width = BrowseUi.dp(host.get(), 400), height = BrowseUi.dp(host.get(), 360);
            rows.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            rows.layout(0, 0, width, height);
            android.graphics.Bitmap image = android.graphics.Bitmap.createBitmap(width, height,
                    android.graphics.Bitmap.Config.ARGB_8888);
            rows.draw(new android.graphics.Canvas(image));
            java.io.File output = new java.io.File("build/reports/visual-tests/search-results-polish.png");
            output.getParentFile().mkdirs();
            try (java.io.FileOutputStream stream = new java.io.FileOutputStream(output)) {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
            }
        } finally { host.pause().stop().destroy(); }
    }

    private static View find(View root, String description) {
        if (root.getContentDescription() != null
                && description.contentEquals(root.getContentDescription())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View found = find(((ViewGroup) root).getChildAt(i), description);
            if (found != null) return found;
        }
        return null;
    }
    private static void await(java.util.function.BooleanSupplier done) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!done.getAsBoolean() && System.nanoTime() < end) {
            shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10);
        }
        assertTrue(done.getAsBoolean());
    }
}
