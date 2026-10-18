package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class PrimaryTabLibraryTest {
    @Test public void deferredLibraryBuildsOnEntryAndReadsChangesMadeBeforeEntry() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LibraryHubView library = new LibraryHubView(activity, null, true);
        activity.setContentView(library);
        Map<String, Runnable> work = new LinkedHashMap<>(); library.setWorkScheduler(work::put);
        try {
            assertNull(ReflectionHelpers.getField(library, "socialHub"));
            assertTrue(((java.util.List<?>) ReflectionHelpers.getField(library, "observedPreferences")).isEmpty());
            library.setSocialActive(false);
            assertNull(ReflectionHelpers.getField(library, "socialHub"));
            FavoriteStore.add(activity, "Saved before entry", "https://fixture.invalid/saved-before-entry");
            library.setSocialActive(true); refresh(library, work);
            Object social = ReflectionHelpers.getField(library, "socialHub");
            assertNotNull(social);
            Object snapshot = ReflectionHelpers.getField(library, "displayed");
            assertEquals(1, ((java.util.List<?>) ReflectionHelpers.getField(snapshot, "watchLater")).size());
            library.setSocialActive(false); library.setSocialActive(true);
            assertSame(social, ReflectionHelpers.getField(library, "socialHub"));
        } finally { library.close(); }
    }

    @Test public void deferredLibraryCanCloseBeforeFirstEntry() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LibraryHubView library = new LibraryHubView(activity, null, true);
        library.close(); library.close(); library.setSocialActive(true);
        assertNull(ReflectionHelpers.getField(library, "socialHub"));
    }

    @Test public void unchangedReturnKeepsViewsAndFavoriteMutationRefreshesAfterEntry() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.getSharedPreferences("watch_later", 0).edit().clear().commit();
        LibraryHubView library = new LibraryHubView(activity, null);
        activity.setContentView(library);
        Map<String, Runnable> work = new LinkedHashMap<>();
        library.setWorkScheduler(work::put);
        try {
            library.setSocialActive(true);
            assertNull(ReflectionHelpers.getField(library, "displayed"));
            refresh(library, work);
            View initial = ReflectionHelpers.getField(library, "mediaContent");
            library.setSocialActive(false);
            library.setSocialActive(true);
            assertSame(initial, ReflectionHelpers.getField(library, "mediaContent"));
            refresh(library, work);
            assertSame("Unchanged data must keep image targets and rail views", initial,
                    ReflectionHelpers.getField(library, "mediaContent"));
            library.setSocialActive(false);
            FavoriteStore.add(activity, "Saved one", "https://example.com/saved-one");
            shadowOf(Looper.getMainLooper()).idle();
            assertSame(initial, ReflectionHelpers.getField(library, "mediaContent"));
            library.setSocialActive(true);
            refresh(library, work);
            LinearLayout updated = ReflectionHelpers.getField(library, "mediaContent");
            assertNotSame(initial, updated);
            assertEquals(1, FavoriteStore.load(activity).size());
            FavoriteStore.remove(activity, "https://example.com/saved-one");
            shadowOf(Looper.getMainLooper()).idle();
            refresh(library, work);
            assertTrue(FavoriteStore.load(activity).isEmpty());
        } finally { library.close(); }
    }

    @Test public void populatedSnapshotKeepsHistoryCreatorsAndDownloadsAndUpdatesProgress() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        PlaybackHistoryStore.resetForTests();
        PlaybackHistoryStore.record(activity, "Watch one", "https://example.com/watch",
                "https://cdn.example.com/poster.jpg", 60_000, 180_000, false, true);
        PlaybackHistoryStore.awaitPendingWritesForTests();
        NativeContentItem creator = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                "Creator One", "https://fapello.com/creator-one/", "https://cdn.example.com/creator.jpg",
                "", "", "", "", "Creator One");
        CreatorCatalog.remember(activity, java.util.Collections.singletonList(creator));
        CreatorFavoriteStore.toggle(activity, creator);
        VideoDownloadStore.Entry download = new VideoDownloadStore.Entry(-1, "Offline one",
                "https://example.com/download", "https://cdn.example.com/download.jpg", "", "video/mp4",
                1, android.app.DownloadManager.STATUS_SUCCESSFUL, 10, 10, "file:///test.mp4", 0);
        java.lang.reflect.Method save = VideoDownloadStore.class.getDeclaredMethod("saveMetadata",
                android.content.Context.class, VideoDownloadStore.Entry.class);
        save.setAccessible(true);
        save.invoke(null, activity, download);
        LibraryHubView library = new LibraryHubView(activity, null);
        activity.setContentView(library);
        Map<String, Runnable> work = new LinkedHashMap<>();
        library.setWorkScheduler(work::put);
        try {
            library.setSocialActive(true);
            refresh(library, work);
            Object snapshot = ReflectionHelpers.getField(library, "displayed");
            assertEquals(1, ((java.util.List<?>) ReflectionHelpers.getField(snapshot, "continueItems")).size());
            assertEquals(1, ((java.util.List<?>) ReflectionHelpers.getField(snapshot, "creators")).size());
            assertEquals(1, ((java.util.List<?>) ReflectionHelpers.getField(snapshot, "downloads")).size());
            library.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
            library.layout(0, 0, 500, 400);
            library.scrollTo(0, 150);
            int scroll = library.getScrollY();
            library.setSocialActive(false);
            library.setSocialActive(true);
            assertEquals(scroll, library.getScrollY());
            refresh(library, work);
            assertEquals(scroll, library.getScrollY());
            View originalMedia = ReflectionHelpers.getField(library, "mediaContent");
            View creatorsRail = originalMedia.findViewWithTag("Favorite Creators");
            View downloadsRail = originalMedia.findViewWithTag("Downloads");
            PlaybackHistoryStore.record(activity, "Watch one", "https://example.com/watch",
                    "https://cdn.example.com/poster.jpg", 120_000, 180_000, false, true);
            PlaybackHistoryStore.awaitPendingWritesForTests();
            shadowOf(Looper.getMainLooper()).idle();
            refresh(library, work);
            Object updated = ReflectionHelpers.getField(library, "displayed");
            java.util.List<PlaybackHistoryStore.Item> continued = ReflectionHelpers.getField(updated, "continueItems");
            assertEquals(120_000, continued.get(0).positionMs);
            View updatedMedia = ReflectionHelpers.getField(library, "mediaContent");
            assertSame("History updates must keep creator image targets", creatorsRail,
                    updatedMedia.findViewWithTag("Favorite Creators"));
            assertSame("History updates must keep download image targets", downloadsRail,
                    updatedMedia.findViewWithTag("Downloads"));
            CreatorFavoriteStore.toggle(activity, creator);
            shadowOf(Looper.getMainLooper()).idle();
            refresh(library, work);
            assertTrue(((java.util.List<?>) ReflectionHelpers.getField(
                    ReflectionHelpers.getField(library, "displayed"), "creators")).isEmpty());
        } finally { library.close(); }
    }

    @Test public void snapshotRetainsOnlyDisplayedSavedVideosWithoutTrimmingTheStore() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        for (int i = 0; i < 25; i++) FavoriteStore.add(activity, "Saved " + i, "https://example.com/saved/" + i);
        LibraryHubView library = new LibraryHubView(activity, null);
        activity.setContentView(library);
        Map<String, Runnable> work = new LinkedHashMap<>();
        library.setWorkScheduler(work::put);
        try {
            library.setSocialActive(true);
            refresh(library, work);
            Object snapshot = ReflectionHelpers.getField(library, "displayed");
            assertEquals(10, ((java.util.List<?>) ReflectionHelpers.getField(snapshot, "watchLater")).size());
            assertEquals(25, FavoriteStore.load(activity).size());
        } finally { library.close(); }
    }

    private void refresh(LibraryHubView library, Map<String, Runnable> work) throws Exception {
        Runnable refresh = work.remove("library-refresh");
        if (refresh != null) refresh.run();
        ExecutorService io = ReflectionHelpers.getField(library, "refreshIo");
        io.submit(() -> {}).get(5, TimeUnit.SECONDS);
        shadowOf(Looper.getMainLooper()).idle();
        Runnable publish = work.remove("library-publish");
        assertNotNull("Snapshot must be ready without touching displayed views", publish);
        publish.run();
        shadowOf(Looper.getMainLooper()).idle();
    }
}
