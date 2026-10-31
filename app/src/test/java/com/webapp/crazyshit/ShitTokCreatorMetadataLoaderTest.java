package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class ShitTokCreatorMetadataLoaderTest {
    static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        public void execute(Runnable task) { tasks.add(task); }
        void next() { tasks.removeFirst().run(); }
    }

    static NativeContentItem clip() {
        return ShitTokOrientation.copy(new NativeContentItem(NativeContentItem.KIND_MEDIA, "Clip",
                "https://fapello.com/jane/123/", "https://example.com/clip.webp",
                "", "Jane", "", "OnlyFap"), .5f);
    }

    static NativeContentItem creator() {
        return new NativeContentItem(NativeContentItem.KIND_CREATOR, "Jane",
                "https://fapello.com/jane/", "https://example.com/avatar.webp",
                "", "", "", "", "Jane");
    }

    @Test public void fallbackIsImmediateAndRichIdentityNeedsBothWorkerAndUiDispatch() {
        Queue worker = new Queue(), ui = new Queue();
        List<String> ready = new ArrayList<>();
        AtomicInteger lookups = new AtomicInteger();
        ShitTokCreatorMetadataLoader loader = new ShitTokCreatorMetadataLoader(worker, ui,
                media -> { lookups.incrementAndGet(); return creator(); }, ready::add);
        NativeContentItem fallback = loader.identity(clip());
        assertEquals("Jane", fallback.title);
        assertEquals(clip().imageUrl, fallback.imageUrl);
        assertEquals(0, lookups.get());
        assertSame(fallback, loader.identity(clip()));
        assertEquals(1, worker.tasks.size());
        worker.next();
        assertEquals(1, lookups.get());
        assertSame(fallback, loader.identity(clip()));
        assertTrue(ready.isEmpty());
        ui.next();
        assertEquals(creator().imageUrl, loader.identity(clip()).imageUrl);
        assertEquals(1, ready.size());
        assertTrue(worker.tasks.isEmpty()); // Rebind does not decode the catalog again.
        loader.close();
    }

    @Test public void repeatedCreatorClipsShareOneEnrichmentAndRetainExactProfileNavigation() {
        Queue worker = new Queue(), ui = new Queue();
        AtomicInteger lookups = new AtomicInteger();
        ShitTokCreatorMetadataLoader loader = new ShitTokCreatorMetadataLoader(worker, ui,
                media -> { lookups.incrementAndGet(); return ShitTokCreatorMetadata.creatorIdentity(null, media); }, url -> {});
        try {
            for (int i = 0; i < 30; i++) loader.identity(new NativeContentItem("media", "lilijunex #" + i,
                    "https://fapello.com/lilijunex-1/" + i + "/", "", "", "https://fapello.com/lilijunex-1/", "", "Fapello"));
            assertEquals(1, worker.tasks.size());
            worker.next(); ui.next();
            NativeContentItem identity = loader.identity(new NativeContentItem("media", "lilijunex #100",
                    "https://fapello.com/lilijunex-1/100/", "", "", "https://fapello.com/lilijunex-1/", "", "Fapello"));
            assertEquals(1, lookups.get()); assertTrue(worker.tasks.isEmpty());
            assertEquals("lilijunex", identity.title);
            assertEquals("https://fapello.com/lilijunex-1/", identity.url);
            assertTrue(loader.cacheSummary().contains("metadataEntries=1"));
            assertTrue(loader.cacheSummary().contains("metadataMisses=1"));
        } finally { loader.close(); }
    }

    @Test public void unresolvedBaddieTitleDoesNotSuppressNewClipAfterCreatorDiscovery() {
        Queue worker = new Queue(), ui = new Queue();
        java.util.concurrent.atomic.AtomicBoolean discovered = new java.util.concurrent.atomic.AtomicBoolean();
        ShitTokCreatorMetadataLoader loader = new ShitTokCreatorMetadataLoader(worker, ui,
                media -> discovered.get() ? creator() : null, url -> {});
        try {
            NativeContentItem first = new NativeContentItem("media", "Jane", "https://baddiehub.com/first/", "", "", "BaddieHub", "");
            NativeContentItem second = new NativeContentItem("media", "Jane", "https://baddiehub.com/second/", "", "", "BaddieHub", "");
            assertNull(loader.identity(first)); worker.next(); ui.next();
            discovered.set(true);
            assertNull(loader.identity(second));
            assertEquals(1, worker.tasks.size()); worker.next(); ui.next();
            assertEquals(creator().url, loader.identity(second).url);
        } finally { loader.close(); }
    }

    @Test public void creatorCacheIsBoundedWithoutQueuingOrdinaryClipWork() {
        ShitTokCreatorMetadataLoader loader = new ShitTokCreatorMetadataLoader(Runnable::run, Runnable::run,
                media -> null, url -> {});
        try {
            for (int i = 0; i < 700; i++) loader.identity(new NativeContentItem("media", "clip" + i,
                    "https://fixture.invalid/" + i, "", "", "Uploader", ""));
            assertTrue(loader.cacheSummary().contains("metadataEntries=" + ShitTokCreatorMetadataLoader.MAX_IDENTITIES));
        } finally { loader.close(); }
    }

    @Test public void refreshDiscardsOldPublicationEvenWhenSameClipIsRequestedAgain() {
        Queue worker = new Queue(), ui = new Queue();
        List<String> ready = new ArrayList<>();
        ShitTokCreatorMetadataLoader loader = new ShitTokCreatorMetadataLoader(worker, ui,
                media -> creator(), ready::add);
        loader.identity(clip()); worker.next();
        loader.clear();
        NativeContentItem freshFallback = loader.identity(clip());
        ui.next();
        assertSame(freshFallback, loader.identity(clip()));
        assertTrue(ready.isEmpty());
        worker.next(); ui.next();
        assertEquals(1, ready.size());
        loader.close();
    }

    @Test public void closeDiscardsQueuedWorkAndCompletedButUndeliveredResults() {
        for (boolean resolved : new boolean[]{false, true}) {
            Queue worker = new Queue(), ui = new Queue();
            List<String> ready = new ArrayList<>();
            AtomicInteger lookups = new AtomicInteger();
            ShitTokCreatorMetadataLoader loader = new ShitTokCreatorMetadataLoader(worker, ui,
                    media -> { lookups.incrementAndGet(); return creator(); }, ready::add);
            loader.identity(clip());
            if (resolved) worker.next();
            loader.close();
            if (resolved) ui.next(); else worker.next();
            assertEquals(resolved ? 1 : 0, lookups.get());
            assertTrue(ready.isEmpty());
        }
    }

    @Test public void failuresAndFullQueueKeepUsableFallback() {
        Queue ui = new Queue();
        ShitTokCreatorMetadataLoader full = new ShitTokCreatorMetadataLoader(
                task -> { throw new RejectedExecutionException(); }, ui, media -> creator(), url -> fail());
        assertEquals("Jane", full.identity(clip()).title);
        assertTrue(ui.tasks.isEmpty());
        full.close();
        Queue worker = new Queue();
        ShitTokCreatorMetadataLoader failed = new ShitTokCreatorMetadataLoader(worker, ui,
                media -> { throw new IllegalStateException(); }, url -> {});
        NativeContentItem fallback = failed.identity(clip());
        worker.next(); ui.next();
        assertSame(fallback, failed.identity(clip()));
        assertTrue(worker.tasks.isEmpty());
        failed.close();
    }

    @Test public void ordinaryClipNeverQueuesCatalogWork() {
        Queue worker = new Queue();
        ShitTokCreatorMetadataLoader loader = new ShitTokCreatorMetadataLoader(worker, Runnable::run,
                media -> { fail(); return null; }, url -> fail());
        assertNull(loader.identity(new NativeContentItem(NativeContentItem.KIND_MEDIA, "Video",
                "https://crazyshit.com/cnt/medias/123", "", "", "Uploader", "", "")));
        assertTrue(worker.tasks.isEmpty());
        loader.close();
    }

    @Test public void realCatalogLookupRetainsCreatorAndFavoriteIdentity() {
        Context app = org.robolectric.RuntimeEnvironment.getApplication();
        CreatorCatalog.remember(app, java.util.Collections.singletonList(creator()));
        app.getSharedPreferences("creator_favorites", Context.MODE_PRIVATE).edit()
                .putStringSet("creators", java.util.Collections.singleton("jane")).commit();
        Queue worker = new Queue(), ui = new Queue();
        ShitTokCreatorMetadataLoader loader = new ShitTokCreatorMetadataLoader(worker, ui,
                media -> ShitTokCreatorMetadata.creatorIdentity(app, media), url -> {});
        assertTrue(CreatorFavoriteStore.contains(app, loader.identity(clip())));
        worker.next(); ui.next();
        NativeContentItem identity = loader.identity(clip());
        assertEquals(creator().url, identity.url);
        assertEquals(creator().imageUrl, identity.imageUrl);
        assertTrue(CreatorFavoriteStore.contains(app, identity));
        loader.close();
    }

    @Test public void feedAdmissionAndHolderBindDoNotWaitForCreatorLookup() {
        try (org.robolectric.android.controller.ActivityController<Activity> host =
                Robolectric.buildActivity(Activity.class).setup()) {
            ChaosFeedView feed = new ChaosFeedView(host.get(), item -> {}, false);
            ShitTokCreatorMetadataLoader original = ReflectionHelpers.getField(feed, "creatorMetadata");
            original.close();
            Queue worker = new Queue();
            ShitTokCreatorMetadataLoader loader = new ShitTokCreatorMetadataLoader(worker, Runnable::run,
                    media -> creator(), url -> ReflectionHelpers.callInstanceMethod(feed,
                            "onCreatorMetadataReady", from(String.class, url)));
            ReflectionHelpers.setField(feed, "creatorMetadata", loader);
            try {
                ReflectionHelpers.callInstanceMethod(feed, "appendUnique", from(List.class,
                        java.util.Collections.singletonList(clip())));
                List<NativeContentItem> items = ReflectionHelpers.getField(feed, "items");
                assertEquals(1, items.size());
                assertEquals(1, worker.tasks.size());
                // Bind only creator chrome on a real holder, without acquiring/preparing a player.
                Object adapter = ReflectionHelpers.getField(feed, "adapter");
                android.view.ViewGroup parent = new android.widget.FrameLayout(host.get());
                Object holder = ReflectionHelpers.callInstanceMethod(adapter, "onCreateViewHolder",
                        from(android.view.ViewGroup.class, parent), from(int.class, 0));
                ReflectionHelpers.setField(holder, "item", clip());
                ReflectionHelpers.callInstanceMethod(holder, "bindCreatorAvatar",
                        from(NativeContentItem.class, clip()), from(boolean.class, true));
                assertEquals(clip().imageUrl,
                        ((NativeContentItem) ReflectionHelpers.getField(holder, "creatorIdentity")).imageUrl);
                java.util.Set<Object> holders = ReflectionHelpers.getField(feed, "playerHolders");
                holders.add(holder);
                ReflectionHelpers.setField(feed, "userPaging", true);
                worker.next();
                assertEquals(clip().imageUrl,
                        ((NativeContentItem) ReflectionHelpers.getField(holder, "creatorIdentity")).imageUrl);
                ReflectionHelpers.setField(feed, "userPaging", false);
                ReflectionHelpers.callInstanceMethod(feed, "applyDeferredUi");
                assertEquals(creator().imageUrl,
                        ((NativeContentItem) ReflectionHelpers.getField(holder, "creatorIdentity")).imageUrl);
                assertNull(ReflectionHelpers.getField(holder, "player"));
                ReflectionHelpers.callInstanceMethod(feed, "timedAdmissionApply", from(String.class, "test"),
                        from(Runnable.class, () -> org.robolectric.shadows.ShadowSystemClock.advanceBy(
                                java.time.Duration.ofMillis(123))));
                assertEquals(1L, (long) ReflectionHelpers.getField(feed, "admissionApplyCount"));
                assertEquals(123L, (long) ReflectionHelpers.getField(feed, "admissionApplyMaxMs"));
            } finally { feed.close(); }
        }
    }
}
