package com.webapp.crazyshit;

import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokSeenStoreTest {
    @Test public void stablePostIdentityMatchesSafeRoutesAndKeepsUnrelatedPostsSeparate() {
        assertEquals(ShitTokContentIdentity.page("https://crazyshit.com/cnt/medias/123/old-title?utm_source=x"),
                ShitTokContentIdentity.page("http://www.crazyshit.com/cnt/medias/123/new-title#watch"));
        assertEquals(ShitTokContentIdentity.page("https://fapello.com/video/model/123/"),
                ShitTokContentIdentity.page("https://fapello.com/model/123/"));
        assertNotEquals(ShitTokContentIdentity.page("https://fapello.com/model/124/"),
                ShitTokContentIdentity.page("https://fapello.com/model/123/"));
        assertNotEquals(ShitTokContentIdentity.media("https://one.test/file.mp4"),
                ShitTokContentIdentity.media("https://two.test/file.mp4"));
        assertNotEquals(ShitTokContentIdentity.page("https://other.test/watch?id=1"),
                ShitTokContentIdentity.page("https://other.test/watch?id=2"));
    }

    @Test public void legacyUrlsMigrateAndStreamAliasesSurviveProcessReload() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("chaos_feed", 0).edit().putString("recent_urls",
                "[\"https://crazyshit.com/cnt/medias/5/old\"]").commit();
        ShitTokSeenStore store = new ShitTokSeenStore(app);
        assertTrue(store.seen("https://crazyshit.com/cnt/medias/5/new", null));
        store.watch("https://kaotic.com/video/post", "https://cdn.test/a.mp4?token=old&expires=1");
        store.flush(); drainDisk();
        ShitTokSeenStore restored = new ShitTokSeenStore(app);
        assertTrue(restored.seen("https://different.test/page", "https://cdn.test/a.mp4?token=new&expires=9"));
        assertTrue(restored.seen("https://cdn.test/a.mp4?token=new&expires=9", null));
        assertTrue(app.getSharedPreferences("chaos_feed", 0).getString("recent_urls", "").contains("/5/old"));
    }

    @Test public void heartedContentOutlivesOrdinaryHistoryAndMatchesMediaVariants() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        ShitTokSeenStore store = new ShitTokSeenStore(app);
        store.heart("https://kaotic.com/video/liked", "https://cdn.test/liked.mp4?token=a");
        store.watch("https://kaotic.com/video/old", null);
        for (int i = 0; i <= ShitTokSeenStore.SEEN_LIMIT; i++) store.watch("https://kaotic.com/video/" + i, null);
        assertEquals(ShitTokSeenStore.SEEN_LIMIT, store.seenSize());
        assertFalse(store.seen("https://kaotic.com/video/old", null));
        assertTrue(store.liked("https://kaotic.com/video/liked", null));
        assertTrue(store.liked("https://other.test/page", "https://cdn.test/liked.mp4?token=b"));
        store.flush(); drainDisk();
        ShitTokSeenStore restored = new ShitTokSeenStore(app);
        assertTrue(restored.liked("https://kaotic.com/video/liked", null));
        assertEquals(ShitTokSeenStore.SEEN_LIMIT, restored.seenSize());
    }
    @Test public void existingServerLikeKeyIsHardExcludedLocally() {
        ShitTokSeenStore store = new ShitTokSeenStore(RuntimeEnvironment.getApplication());
        String page = "https://kaotic.com/video/already-hearted";
        store.importLikes(java.util.Collections.singleton(ZeroChillSocialRepository.contentKey(page)));
        assertTrue(store.liked(page, null));
        assertFalse(store.liked("https://kaotic.com/video/other", null));
    }
    private static void drainDisk() throws Exception {
        ExecutorService disk = ReflectionHelpers.getStaticField(ShitTokSeenStore.class, "DISK");
        disk.submit(() -> {}).get(5, TimeUnit.SECONDS);
    }
}
