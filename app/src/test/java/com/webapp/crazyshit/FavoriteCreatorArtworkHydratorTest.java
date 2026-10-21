package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class FavoriteCreatorArtworkHydratorTest {
    private final Context context = RuntimeEnvironment.getApplication();
    @Before public void reset() {
        for (String name : Arrays.asList("creator_favorites", CreatorCatalog.PREFS,
                FavoriteCreatorArtworkHydrator.RETRIES, "creator_avatar_overrides_v1", "manual_creator_merges_v1",
                "popular_creator_feed_v3", "popular_creator_feed_v2", "onlyfap_trending_v4",
                "fapzone_creator_feed_v3_1", "fapzone_creator_feed_v3_2", "fapzone_creator_feed_v3_3",
                "fapzone_creator_feed_v1_1", "fapzone_creator_feed_v1_2", "fapzone_creator_feed_v1_3"))
            context.getSharedPreferences(name, 0).edit().clear().commit();
        CreatorAvatarOverrideStore.clearCacheForTest();
        ManualCreatorMergeStore.clearCacheForTest();
    }
    private void favorites(String... keys) {
        CreatorFavoriteStore.activateAccount(context, "fixture-account");
        CreatorFavoriteStore.replaceAccountNames(context, "fixture-account", new HashSet<>(Arrays.asList(keys)));
    }
    private List<FapelloRepository.Model> found(String query) {
        return Collections.singletonList(new FapelloRepository.Model(query,
                "https://fapello.com/" + query + "/", "https://fixture.invalid/" + query + ".jpg"));
    }
    @Test public void restoresAllSyncedFavoritesBeyondTenAndSurvivesNewResolverWithoutMoreRequests() {
        String[] keys = new String[28];
        for (int i = 0; i < keys.length; i++) keys[i] = String.format(Locale.US, "creator%03d", i);
        favorites(keys);
        AtomicInteger calls = new AtomicInteger(), published = new AtomicInteger();
        FavoriteCreatorArtworkHydrator.Searcher search = (ctx, query, limit) -> {
            calls.incrementAndGet(); return found(query);
        };
        new FavoriteCreatorArtworkHydrator(search).hydrate(context, published::incrementAndGet);
        assertEquals(28, calls.get()); assertEquals(28, published.get());
        for (CreatorCatalog.FavoriteGroup group : CreatorCatalog.favoriteGroups(context))
            assertTrue(group.item.imageUrl.startsWith("https://fixture.invalid/"));
        new FavoriteCreatorArtworkHydrator(search).hydrate(context, null);
        assertEquals(28, calls.get());
    }
    @Test public void reviewedAliasesShareOneLookupAndRawPunctuationKeepsItsSyncedKey() {
        favorites("belle del", "belle delphi", "anna.m");
        AtomicInteger calls = new AtomicInteger();
        FavoriteCreatorArtworkHydrator.Searcher search = (ctx, query, limit) -> {
            calls.incrementAndGet(); return found(query.equals("anna.m") ? "Anna M" : query);
        };
        new FavoriteCreatorArtworkHydrator(search).hydrate(context, null);
        assertEquals(2, calls.get());
        assertEquals(2, CreatorCatalog.favoriteGroups(context).size());
        for (CreatorCatalog.FavoriteGroup group : CreatorCatalog.favoriteGroups(context))
            assertFalse(group.item.imageUrl.isEmpty());
        new FavoriteCreatorArtworkHydrator(search).hydrate(context, null);
        assertEquals(2, calls.get());
        assertEquals(new HashSet<>(Arrays.asList("belle del", "belle delphi", "anna.m")), CreatorFavoriteStore.names(context));
    }

    @Test public void oneFailureOrMissDoesNotStopTheQueueAndHasPersistentRetryBackoff() {
        favorites("alpha", "beta", "gamma");
        Map<String, Integer> calls = new HashMap<>();
        FavoriteCreatorArtworkHydrator.Searcher search = (ctx, query, limit) -> {
            calls.merge(query, 1, Integer::sum);
            if (query.equals("alpha")) throw new IOException("offline");
            if (query.equals("beta")) return Collections.emptyList();
            return found(query);
        };
        new FavoriteCreatorArtworkHydrator(search).hydrate(context, null);
        new FavoriteCreatorArtworkHydrator(search).hydrate(context, null);
        assertEquals(Integer.valueOf(1), calls.get("alpha"));
        assertEquals(Integer.valueOf(1), calls.get("beta"));
        assertEquals(Integer.valueOf(1), calls.get("gamma"));
        assertFalse(CreatorCatalog.favoriteGroups(context).get(2).item.imageUrl.isEmpty());
        context.getSharedPreferences(FavoriteCreatorArtworkHydrator.RETRIES, 0).edit().clear().commit();
        new FavoriteCreatorArtworkHydrator(search).hydrate(context, null);
        assertEquals(Integer.valueOf(2), calls.get("alpha"));
        assertEquals(Integer.valueOf(2), calls.get("beta"));
        assertEquals(Integer.valueOf(1), calls.get("gamma"));
    }
    @Test public void concurrentResolversNeverLookUpTheSameFavoriteTwice() throws Exception {
        favorites("alpha");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        FavoriteCreatorArtworkHydrator.Searcher search = (ctx, query, limit) -> {
            calls.incrementAndGet(); entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("timeout"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            return found(query);
        };
        ExecutorService io = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = io.submit(() -> new FavoriteCreatorArtworkHydrator(search).hydrate(context, null));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            new FavoriteCreatorArtworkHydrator(search).hydrate(context, null);
            assertEquals(1, calls.get()); release.countDown(); first.get(5, TimeUnit.SECONDS);
        } finally { release.countDown(); io.shutdownNow(); }
        assertEquals(1, calls.get());
    }
    @Test public void skipsCustomAvatarsAndExistingArtworkAndRejectsNearMatches() {
        favorites("alpha", "beta", "gamma");
        CreatorCatalog.remember(context, Collections.singletonList(CreatorCatalog.fromModel(found("alpha").get(0))));
        assertTrue(CreatorAvatarOverrideStore.save(context, Collections.singleton("beta"),
                "https://fixture.invalid/custom.jpg", "https://fixture.invalid/", .5f, .5f, 1f));
        List<String> queries = new ArrayList<>();
        new FavoriteCreatorArtworkHydrator((ctx, query, limit) -> {
            queries.add(query); return found("gamma another");
        }).hydrate(context, null);
        assertEquals(Collections.singletonList("gamma"), queries);
        assertEquals("https://fixture.invalid/custom.jpg", CreatorCatalog.favoriteGroups(context).get(1).item.imageUrl);
        assertTrue(CreatorCatalog.favoriteGroups(context).get(2).item.imageUrl.isEmpty());
    }
    @Test public void accountSwitchDuringLookupDoesNotPublishOrConsumeTheNewAccountsFavorites() {
        favorites("alpha", "beta");
        AtomicInteger callbacks = new AtomicInteger();
        new FavoriteCreatorArtworkHydrator((ctx, query, limit) -> {
            CreatorFavoriteStore.activateAccount(context, "other");
            CreatorFavoriteStore.replaceAccountNames(context, "other", Collections.singleton("gamma"));
            return found(query);
        }).hydrate(context, callbacks::incrementAndGet);
        assertEquals(0, callbacks.get());
        assertEquals(Collections.singleton("gamma"), CreatorFavoriteStore.names(context));
        assertTrue(CreatorCatalog.favoriteGroups(context).get(0).item.imageUrl.isEmpty());
    }
    @Test public void interruptedLookupDoesNotPersistResultOrSuppressTheNextPass() {
        favorites("alpha");
        try {
            new FavoriteCreatorArtworkHydrator((ctx, query, limit) -> {
                Thread.currentThread().interrupt(); return found(query);
            }).hydrate(context, null);
        } finally { Thread.interrupted(); }
        assertTrue(CreatorCatalog.favoriteGroups(context).get(0).item.imageUrl.isEmpty());
        new FavoriteCreatorArtworkHydrator((ctx, query, limit) -> found(query)).hydrate(context, null);
        assertFalse(CreatorCatalog.favoriteGroups(context).get(0).item.imageUrl.isEmpty());
    }
}
