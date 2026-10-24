package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.concurrent.Future;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShowsCategoryLifecycleTest {
    @Test public void attachedOffscreenHubWaitsForSelectionAndDetachCancels() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ShowsHubView hub = new ShowsHubView(activity, item -> {}, item -> {}, item -> {});
        ShowsCategoryLoaderTest.Fixture f = new ShowsCategoryLoaderTest.Fixture();
        hub.setCategoryLoader(f.loader);
        activity.setContentView(hub);
        // All primary pages remain attached by the pager, including offscreen Shows.
        assertTrue(hub.isAttachedToWindow());
        assertEquals(0, f.io.work.size());
        hub.setActive(true);
        assertEquals(1, f.io.work.size());
        activity.setContentView(new View(activity));
        assertTrue(((Future<?>) f.io.work.get(0)).isCancelled());
        hub.closeCategoryLoader();
        activity.setContentView(hub);
        assertEquals(1, f.io.work.size());
    }

    @Test public void pagerDeparturePauseAndSearchCancelOwnedBrowserWork() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        MainPagerAdapter adapter = new MainPagerAdapter(activity, new MainPagerAdapter.Host() {
            @Override public void onOpenItem(NativeContentItem item) {}
            @Override public void onLongPressItem(NativeContentItem item, View anchor) {}
            @Override public void onOpenComments(NativeContentItem item) {}
        });
        try {
            Object[] pages = ReflectionHelpers.getField(adapter, "pages");
            ShowsHubView hub = ReflectionHelpers.getField(pages[MainPagerAdapter.PAGE_SERIES], "showsHub");
            ShowsCategoryLoaderTest.Fixture f = new ShowsCategoryLoaderTest.Fixture();
            hub.closeCategoryLoader();
            hub.setCategoryLoader(f.loader);
            activity.setContentView(hub);
            assertEquals(0, f.io.work.size());
            // Prevent unrelated live shelf IO in this lifecycle fixture.
            ReflectionHelpers.setField(pages[MainPagerAdapter.PAGE_SERIES], "loading", true);
            adapter.setPrimaryActive(MainPagerAdapter.PAGE_SERIES);
            adapter.onPrimaryMotionChanged(true);
            PrimaryTabWorkQueueTest.tick();
            assertEquals(1, f.io.work.size());
            adapter.setPrimaryActive(MainPagerAdapter.PAGE_ONLYFAP);
            assertTrue(((Future<?>) f.io.work.get(0)).isCancelled());
            adapter.setPrimaryActive(MainPagerAdapter.PAGE_SERIES);
            PrimaryTabWorkQueueTest.tick();
            assertEquals(2, f.io.work.size());
            adapter.onHostPause();
            assertTrue(((Future<?>) f.io.work.get(1)).isCancelled());
            adapter.onHostResume();
            PrimaryTabWorkQueueTest.tick();
            assertEquals(3, f.io.work.size());
            adapter.onSearchOpened();
            assertTrue(((Future<?>) f.io.work.get(2)).isCancelled());
        } finally { adapter.close(); }
    }
}
