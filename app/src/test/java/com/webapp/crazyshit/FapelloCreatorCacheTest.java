package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowSystemClock;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 35)
public class FapelloCreatorCacheTest {
    @Test public void cachesOnlyExactVerifiedDiscoveryAndRefreshesExpiredEvidence() throws Exception {
        FapelloRepository repo = new FapelloRepository();
        AtomicInteger calls = new AtomicInteger();
        FapelloRepository.SearchQueryFetcher fetch = (query, offset, size) -> {
            calls.incrementAndGet();
            return "{\"results\":[{\"name\":\"Fixture Display\","
                    + "\"url\":\"/fixture_handle/\",\"username\":\"fixture_handle\"}]}";
        };

        assertEquals(1, repo.searchGalleryModelsCached("fixture_handle", 4, fetch, null).size());
        assertEquals(1, repo.searchGalleryModelsCached("fixture_handle", 4, fetch, null).size());
        assertEquals(1, calls.get());
        assertTrue(repo.searchGalleryModelsCached("fixture_handle_other", 4, fetch, null).isEmpty());
        assertTrue(repo.searchGalleryModelsCached("fixture_handle_other", 4, fetch, null).isEmpty());
        assertEquals(3, calls.get());

        ShadowSystemClock.advanceBy(Duration.ofSeconds(61));
        assertEquals(1, repo.searchGalleryModelsCached("fixture_handle", 4, fetch, null).size());
        assertEquals(4, calls.get());
    }

    @Test @Config(shadows = MediaResponses.class)
    public void artworkWarmsTheExactProfilePageWithoutSkippingLaterPages() throws Exception {
        MediaResponses.calls = 0;
        MediaResponses.blocked = false;
        Context context = RuntimeEnvironment.getApplication();
        FapelloRepository repo = new FapelloRepository();
        FapelloRepository.Model model = model("artwork");

        assertEquals(1, repo.fetchCreatorPreview(context, model, true).size());
        FapelloRepository.MediaPage gallery = repo.fetchGalleryModelMediaPage(context, model, 1);
        assertTrue(gallery.hasNext);
        assertEquals("https://fapello.com/artwork/1/", gallery.items.get(0).url);
        gallery.items.clear();
        assertEquals(1, repo.fetchGalleryModelMediaPage(context, model, 1).items.size());
        assertEquals(1, MediaResponses.calls);
        assertFalse(repo.fetchGalleryModelMediaPage(context, model, 2).hasNext);
        assertEquals(2, MediaResponses.calls);
        ShadowSystemClock.advanceBy(Duration.ofSeconds(61));
        assertEquals(1, repo.fetchGalleryModelMediaPage(context, model, 1).items.size());
        assertEquals(3, MediaResponses.calls);
    }

    @Test @Config(shadows = MediaResponses.class)
    public void backgroundArtworkUsesConfirmedFallbackDuringCooldownAndRecovers() throws Exception {
        MediaResponses.calls = 0;
        MediaResponses.blocked = true;
        Context context = RuntimeEnvironment.getApplication();
        FapelloRepository repo = new FapelloRepository();

        for (int i = 0; i < 2; i++) {
            FapelloRepository.Model model = model("blocked_" + i);
            assertEquals(FapelloSourceException.Reason.BLOCKED, assertThrows(FapelloSourceException.class,
                    () -> repo.fetchCreatorPreview(context, model, true)).reason);
        }
        FapelloRepository.Model available = model("recoverable");
        assertTrue(repo.fetchCreatorPreview(context, available, true).isEmpty());
        assertEquals(2, MediaResponses.calls);

        MediaResponses.blocked = false;
        // Opening a gallery can fetch immediately, even during a background-artwork cooldown.
        assertEquals(1, repo.fetchGalleryModelMediaPage(context, model("opened"), 1).items.size());
        assertEquals(3, MediaResponses.calls);
        ShadowSystemClock.advanceBy(Duration.ofSeconds(31));
        assertEquals(1, repo.fetchCreatorPreview(context, available, true).size());
        assertEquals(4, MediaResponses.calls);
    }

    private static FapelloRepository.Model model(String slug) {
        return new FapelloRepository.Model(slug, "https://fapello.com/" + slug + "/", "");
    }

    @Implements(value = FapelloRepository.class, isInAndroidSdk = false)
    public static class MediaResponses {
        static int calls;
        static boolean blocked;

        @Implementation protected FapelloRepository.MediaPage fetchModelMediaPage(
                Context context, FapelloRepository.Model model, int page) throws FapelloSourceException {
            calls++;
            if (blocked) throw new FapelloSourceException(
                    FapelloSourceException.Reason.BLOCKED, 403, "Fixture challenge");
            NativeContentItem item = new NativeContentItem(NativeContentItem.KIND_IMAGE, model.name,
                    model.url + page + "/", "https://example.com/thumb.jpg", "", model.url, "");
            return new FapelloRepository.MediaPage(List.of(item), page == 1, "");
        }
    }
}
