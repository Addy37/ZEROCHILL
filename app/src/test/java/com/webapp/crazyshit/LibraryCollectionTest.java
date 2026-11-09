package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35, qualifiers = "w320dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LibraryCollectionTest {
    @Test public void allCollectionsOpenExistingDestinationsBeforeLibraryReads() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FavoriteStore.add(activity, "Saved item", "https://fixture.invalid/saved");
        String saved = activity.getSharedPreferences("watch_later", 0).getString("items", "");
        LibraryHubView hub = new LibraryHubView(activity, null, true);
        activity.setContentView(hub);
        try {
            assertNotNull(hub.findViewWithTag("library-collection"));
            assertTrue(((java.util.List<?>) ReflectionHelpers.getField(hub, "observedPreferences")).isEmpty());
            Intent watch = open(activity, hub, "watch-later");
            assertEquals(FavoritesActivity.class.getName(), watch.getComponent().getClassName());
            assertEquals(FavoritesActivity.START_WATCH_LATER, watch.getIntExtra(FavoritesActivity.EXTRA_START_TAB, -1));
            assertFalse(watch.getBooleanExtra(FavoritesActivity.EXTRA_MEDIA_FAVORITES, false));
            assertEquals(DownloadedActivity.class.getName(), open(activity, hub, "downloads").getComponent().getClassName());
            Intent history = open(activity, hub, "history");
            assertEquals(FavoritesActivity.class.getName(), history.getComponent().getClassName());
            assertEquals(FavoritesActivity.START_HISTORY, history.getIntExtra(FavoritesActivity.EXTRA_START_TAB, -1));
            Intent favorites = open(activity, hub, "favorites");
            assertEquals(FavoritesActivity.class.getName(), favorites.getComponent().getClassName());
            assertEquals(FavoritesActivity.START_WATCH_LATER, favorites.getIntExtra(FavoritesActivity.EXTRA_START_TAB, -1));
            assertTrue(favorites.getBooleanExtra(FavoritesActivity.EXTRA_MEDIA_FAVORITES, false));
            assertEquals(saved, activity.getSharedPreferences("watch_later", 0).getString("items", ""));
        } finally { hub.close(); }
    }

    @Test public void collectionStaysAfterPreviewsAndAccentChangeKeepsSnapshotAndTiles() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LibraryHubView hub = new LibraryHubView(activity, null);
        activity.setContentView(hub);
        Map<String, Runnable> work = new LinkedHashMap<>(); hub.setWorkScheduler(work::put);
        try {
            hub.setSocialActive(true);
            work.remove("library-refresh").run();
            ((ExecutorService) ReflectionHelpers.getField(hub, "refreshIo")).submit(() -> {}).get(5, TimeUnit.SECONDS);
            shadowOf(Looper.getMainLooper()).idle();
            work.remove("library-publish").run();
            shadowOf(Looper.getMainLooper()).idle();
            View grid = hub.findViewWithTag("library-collection");
            LinearLayout content = ReflectionHelpers.getField(hub, "content");
            assertSame(grid, content.getChildAt(content.getChildCount() - 1));
            Object snapshot = ReflectionHelpers.getField(hub, "displayed");
            work.clear();
            AppearanceTheme.setAccent(activity, AppearanceTheme.Accent.PURPLE);
            shadowOf(Looper.getMainLooper()).idle();
            assertSame(grid, hub.findViewWithTag("library-collection"));
            assertSame(snapshot, ReflectionHelpers.getField(hub, "displayed"));
            assertFalse(work.containsKey("library-refresh"));
            ViewGroup tile = (ViewGroup) hub.findViewWithTag("library-collection-history");
            assertNotNull(((ImageView) tile.getChildAt(0)).getColorFilter());
            android.graphics.drawable.GradientDrawable surface =
                    (android.graphics.drawable.GradientDrawable) tile.getBackground();
            assertEquals(androidx.core.graphics.ColorUtils.blendARGB(0xFF1B2630,
                    AppearanceTheme.accent(activity), .04f), surface.getColors()[0]);
            hub.setSocialActive(false);
            AppearanceTheme.setAccent(activity, AppearanceTheme.Accent.GREEN);
            shadowOf(Looper.getMainLooper()).idle();
            hub.setSocialActive(true);
            surface = (android.graphics.drawable.GradientDrawable) tile.getBackground();
            assertEquals(androidx.core.graphics.ColorUtils.blendARGB(0xFF1B2630,
                    AppearanceTheme.accent(activity), .04f), surface.getColors()[0]);
        } finally { hub.close(); }
    }

    @Test public void narrowLargeTextGridKeepsFourWholeAccessibleTargets() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        android.content.res.Configuration config = new android.content.res.Configuration(activity.getResources().getConfiguration());
        config.fontScale = 2f;
        activity.getResources().updateConfiguration(config, activity.getResources().getDisplayMetrics());
        int[] clicks = {0};
        LibraryCollectionView grid = new LibraryCollectionView(activity,
                () -> clicks[0]++, () -> clicks[0]++, () -> clicks[0]++, () -> clicks[0]++);
        int width = BrowseUi.dp(activity, 288);
        grid.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        grid.layout(0, 0, width, grid.getMeasuredHeight());
        assertEquals(3, grid.getChildCount());
        for (int row = 1; row <= 2; row++) {
            ViewGroup pair = (ViewGroup) grid.getChildAt(row);
            assertEquals(2, pair.getChildCount());
            assertEquals(pair.getChildAt(0).getWidth(), pair.getChildAt(1).getWidth());
            assertEquals(pair.getChildAt(0).getHeight(), pair.getChildAt(1).getHeight());
            for (int col = 0; col < 2; col++) {
                ViewGroup tile = (ViewGroup) pair.getChildAt(col);
                assertTrue(tile.isFocusable());
                assertTrue(tile.getHeight() >= BrowseUi.dp(activity, 48));
                assertNotNull(tile.getContentDescription());
                TextView caption = (TextView) tile.getChildAt(1);
                assertTrue(caption.getBottom() + tile.getPaddingBottom() <= tile.getHeight());
                assertTrue(caption.getLayout().getLineCount() > 0);
                assertEquals(0, caption.getLayout().getEllipsisCount(caption.getLayout().getLineCount() - 1));
                tile.performClick();
            }
        }
        assertEquals(4, clicks[0]);
    }

    private Intent open(Activity activity, LibraryHubView hub, String tag) {
        View tile = hub.findViewWithTag("library-collection-" + tag);
        assertNotNull(tag, tile);
        tile.performClick();
        return shadowOf(activity).getNextStartedActivity();
    }
}
