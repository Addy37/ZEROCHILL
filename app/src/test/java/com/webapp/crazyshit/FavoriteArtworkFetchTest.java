package com.webapp.crazyshit;

import static org.junit.Assert.*;
import android.app.Application;
import android.content.Context;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class FavoriteArtworkFetchTest {
    private final Context context = RuntimeEnvironment.getApplication();
    @Before public void clean() {
        for (String prefs : new String[]{"creator_favorites", CreatorCatalog.PREFS,
                "creator_avatar_overrides_v1", "manual_creator_merges_v1", "popular_creator_feed_v3",
                "popular_creator_feed_v2", "onlyfap_trending_v4", "fapzone_creator_feed_v3_1",
                "fapzone_creator_feed_v3_2", "fapzone_creator_feed_v3_3", "fapzone_creator_feed_v1_1",
                "fapzone_creator_feed_v1_2", "fapzone_creator_feed_v1_3"})
            context.getSharedPreferences(prefs, 0).edit().clear().commit();
        CreatorAvatarOverrideStore.clearCacheForTest();
        ManualCreatorMergeStore.clearCacheForTest();
    }
    private void favorites(String... names) {
        context.getSharedPreferences("creator_favorites", 0).edit()
                .putStringSet("creators", new HashSet<>(Arrays.asList(names))).commit();
    }
    private List<FapelloRepository.Model> found(String name) {
        return Collections.singletonList(new FapelloRepository.Model(name,
                "https://fapello.com/" + name.replace(' ', '-'), "https://cdn.example/" + name + ".jpg"));
    }
    @Test public void fetchesBeyondTenAndLaterCreatorsSurviveIndividualFailures() {
        String[] names = new String[24];
        for (int i = 0; i < names.length; i++) names[i] = String.format("creator %02d", i);
        favorites(names);
        AtomicInteger calls = new AtomicInteger();
        FavoriteCreatorArtworkHydrator fetch = new FavoriteCreatorArtworkHydrator((app, query, limit) -> {
            calls.incrementAndGet();
            if (query.equals("creator 04")) throw new IOException("offline");
            if (query.equals("creator 07")) return found("unrelated");
            return found(query);
        });
        List<FavoriteCreatorArtworkHydrator.Result> progress = new ArrayList<>();
        FavoriteCreatorArtworkHydrator.Result result = fetch.fetchMissing(context, () -> false, progress::add);
        assertEquals(24, calls.get()); assertEquals(24, result.total); assertEquals(24, result.completed);
        assertEquals(22, result.restored); assertEquals(1, result.errors); assertEquals(1, result.unresolved);
        assertEquals(25, progress.size()); assertFalse(result.cancelled);
        assertTrue(CreatorCatalog.favoriteGroups(context).stream().anyMatch(g -> g.item.title.equals("creator 23") && !g.item.imageUrl.isEmpty()));
        calls.set(0);
        assertEquals(2, fetch.fetchMissing(context, () -> false, null).total);
        assertEquals(2, calls.get());
    }
    @Test public void skipsValidArtworkAndOverridesAndDeduplicatesReviewedIdentities() {
        favorites("anna", "custom", "belle del", "belle delphine");
        CreatorCatalog.remember(context, Collections.singletonList(CreatorCatalog.fromModel(found("anna").get(0))));
        CreatorAvatarOverrideStore.save(context, new HashSet<>(Collections.singletonList("custom")),
                "https://custom.example/image.jpg", "https://custom.example", .5f, .5f, 1f);
        AtomicInteger calls = new AtomicInteger();
        FavoriteCreatorArtworkHydrator.Result result = new FavoriteCreatorArtworkHydrator((app, query, limit) -> {
            calls.incrementAndGet(); return found("Belle Delphine");
        }).fetchMissing(context, () -> false, null);
        assertEquals(1, calls.get()); assertEquals(1, result.restored);
        assertTrue(CreatorCatalog.favoriteGroups(context).stream().filter(g -> g.customAvatar)
                .allMatch(g -> g.item.imageUrl.equals("https://custom.example/image.jpg")));
        assertEquals(0, new FavoriteCreatorArtworkHydrator((app, query, limit) -> {
            fail("No repeat lookup expected"); return Collections.emptyList();
        }).fetchMissing(context, () -> false, null).total);
    }
    @Test public void cancellationKeepsSavedSuccessAndStopsFutureLookups() {
        favorites("alpha", "bravo", "charlie");
        AtomicBoolean stop = new AtomicBoolean();
        AtomicInteger calls = new AtomicInteger();
        FavoriteCreatorArtworkHydrator.Result result = new FavoriteCreatorArtworkHydrator((app, query, limit) -> {
            calls.incrementAndGet(); return found(query);
        }).fetchMissing(context, stop::get, progress -> { if (progress.restored == 1) stop.set(true); });
        assertTrue(result.cancelled); assertEquals(1, calls.get()); assertEquals(1, result.restored);
        assertEquals(1, CreatorCatalog.favoriteGroups(context).stream().filter(g -> !g.item.imageUrl.isEmpty()).count());
        assertEquals(2, new FavoriteCreatorArtworkHydrator((app, query, limit) -> found(query))
                .fetchMissing(context, () -> false, null).restored);
    }
    @Test public void placeholderAndLocalInvalidImageDoNotCountAsUsableNormalArtwork() {
        assertFalse(FavoriteCreatorArtworkHydrator.usableImage("https://test/load.svg"));
        assertFalse(FavoriteCreatorArtworkHydrator.usableImage("file:///missing"));
        assertTrue(FavoriteCreatorArtworkHydrator.usableImage("https://test/avatar.jpg"));
    }
}
