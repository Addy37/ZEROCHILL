package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 35, shadows = {
        CreatorGalleryRequestReuseTest.Bunkr.class,
        CreatorGalleryRequestReuseTest.Fapello.class,
        CreatorGalleryRequestReuseTest.OnlyHaven.class,
        CreatorGalleryRequestReuseTest.WikiFeet.class
})
public class CreatorGalleryRequestReuseTest {
    private static final String ALBUM = "https://bunkr.cr/a/fixture";
    private static final String BUNKR_IMAGE = "https://example.com/album.jpg";
    private static final String FAPELLO_IMAGE = "https://example.com/profile.jpg";
    private static final AtomicInteger RUNS = new AtomicInteger();
    private static final AtomicInteger ALBUM_CALLS = new AtomicInteger();
    private static final AtomicInteger PROFILE_CALLS = new AtomicInteger();
    private static CountDownLatch profileStarted;
    private static volatile CountDownLatch releaseWikiCatalog;

    @Test public void publishesFirstSourceAndRetainsSlowerVerifiedPage() throws Exception {
        fetchGallery(null);
    }

    @Test public void publishesWorkingPagesBeforeSlowCatalogFinishes() throws Exception {
        CountDownLatch catalog = new CountDownLatch(1);
        releaseWikiCatalog = catalog;
        try {
            fetchGallery(catalog);
        } finally {
            catalog.countDown();
            releaseWikiCatalog = null;
        }
    }

    private void fetchGallery(CountDownLatch catalog) throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        BunkrCreatorGalleryRepository repository = new BunkrCreatorGalleryRepository();
        List<String> published = new ArrayList<>();
        long[] firstResult = {0L};
        boolean[] secondPublishedWhileDiscovering = {false};
        ALBUM_CALLS.set(0);
        PROFILE_CALLS.set(0);
        profileStarted = new CountDownLatch(1);
        String session = BunkrGallerySessionStore.createCreator(
                "fixture", BunkrRepository.searchUrl("fixture"), "fixture:request-reuse");
        String profile = "https://fapello.com/fixture" + RUNS.incrementAndGet() + "/";
        long start = System.nanoTime();
        BunkrCreatorGalleryRepository.Batch batch = repository.fetchNext(
                context, session, "fixture", profile, "fixture",
                List.of("fixture"), List.of(ALBUM), List.of(""), List.of("fixture"), items -> {
                    if (published.isEmpty()) firstResult[0] = System.nanoTime();
                    for (NativeContentItem item : items) published.add(item.url);
                    if (catalog != null && published.size() == 2) {
                        secondPublishedWhileDiscovering[0] = catalog.getCount() != 0;
                        catalog.countDown();
                    }
                });
        long elapsed = System.nanoTime() - start;
        System.out.printf("Creator fixture first media %.1f ms, batch %.1f ms, album calls %d, profile calls %d%n",
                (firstResult[0] - start) / 1e6, elapsed / 1e6, ALBUM_CALLS.get(), PROFILE_CALLS.get());

        assertNotNull(batch);
        assertEquals(List.of(BUNKR_IMAGE, FAPELLO_IMAGE), published);
        assertEquals(2, batch.items.size());
        assertTrue(firstResult[0] > start);
        assertEquals(2, ALBUM_CALLS.get());
        assertEquals(1, PROFILE_CALLS.get());
        if (catalog != null) assertTrue(secondPublishedWhileDiscovering[0]);
    }

    @Implements(value = BunkrRepository.class, isInAndroidSdk = false)
    public static class Bunkr {
        @Implementation protected List<NativeContentItem> searchAlbums(Context context, String query, int page) {
            return Collections.emptyList();
        }

        @Implementation protected List<NativeContentItem> fetchAlbum(Context context, String url, int page)
                throws InterruptedException {
            ALBUM_CALLS.incrementAndGet();
            if (page != 1) return Collections.emptyList();
            assertTrue(profileStarted.await(5, TimeUnit.SECONDS));
            return List.of(image(BUNKR_IMAGE));
        }
    }

    @Implements(value = FapelloRepository.class, isInAndroidSdk = false)
    public static class Fapello {
        @Implementation protected List<FapelloRepository.Model> searchGalleryModels(
                Context context, String query, int limit) {
            return Collections.emptyList();
        }

        @Implementation protected FapelloRepository.MediaPage fetchModelMediaPage(
                Context context, FapelloRepository.Model model, int page) throws InterruptedException {
            PROFILE_CALLS.incrementAndGet();
            profileStarted.countDown();
            // Keep this verified source in flight when the first result is published.
            Thread.sleep(350);
            return new FapelloRepository.MediaPage(List.of(image(FAPELLO_IMAGE)), false, "");
        }
    }

    @Implements(value = OnlyHavenRepository.class, isInAndroidSdk = false)
    public static class OnlyHaven {
        @Implementation protected List<OnlyHavenRepository.Creator> searchGalleryCreators(
                Context context, String query, int limit) {
            return Collections.emptyList();
        }
    }

    @Implements(value = WikiFeetRepository.class, isInAndroidSdk = false)
    public static class WikiFeet {
        @Implementation protected List<WikiFeetRepository.Creator> searchConfirmedCreators(
                Context context, String query, int limit) throws InterruptedException {
            CountDownLatch blocked = releaseWikiCatalog;
            if (blocked != null && !blocked.await(2, TimeUnit.SECONDS)) blocked.countDown();
            return Collections.emptyList();
        }
    }

    private static NativeContentItem image(String url) {
        return new NativeContentItem(NativeContentItem.KIND_IMAGE, "fixture", url, url, "", "", "");
    }
}
