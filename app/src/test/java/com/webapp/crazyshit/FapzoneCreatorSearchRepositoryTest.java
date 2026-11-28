package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import org.json.JSONObject;

import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public final class FapzoneCreatorSearchRepositoryTest {
    @Before public void clearLearnedCreators() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(CreatorCatalog.PREFS, 0).edit().clear().commit();
        context.getSharedPreferences("creator_favorites", 0).edit().clear().commit();
        context.getSharedPreferences("remote_source_config", 0).edit().clear().commit();
        RemoteSourceConfigManager.resetForTests();
        ContentPreference.set(context, ContentPreference.ALL);
    }

    @Test public void explicitOnlyFapSearchIncludesPawchiveAndOtherSourcesWithAnyPreference() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        NativeContentItem pawchive = PawchiveRepository.creatorItem(
                new PawchiveRepository.Creator("patreon", "123", "Nolan", 1));
        NativeContentItem other = creator("Nolan", "Fapello");
        AtomicInteger pawchiveCalls = new AtomicInteger();
        FapzoneCreatorSearchRepository repository = new FapzoneCreatorSearchRepository(executor,
                Collections.singletonList((ignored, query, limit) ->
                        Collections.singletonList(other)),
                (ignored, query, limit, mode) -> Collections.emptyList(),
                (ignored, query, limit) -> {
                    pawchiveCalls.incrementAndGet();
                    return Collections.singletonList(pawchive);
                });
        try {
            assertEquals(1, repository.search(context, "Nolan", 8).size());
            assertEquals(0, pawchiveCalls.get());
            for (ContentPreference mode : Arrays.asList(ContentPreference.MEN, ContentPreference.PAWCHIVE,
                    ContentPreference.WOMEN, ContentPreference.TRANS, ContentPreference.GROUPS,
                    ContentPreference.ALL)) {
                ContentPreference.setOnlyFap(context, mode);
                List<NativeContentItem> found = repository.searchAllCreators(context, "Nolan", 20, null);
                assertEquals(2, found.size());
                assertTrue(found.stream().anyMatch(item -> pawchive.url.equals(item.url)));
                assertTrue(found.stream().anyMatch(item -> other.url.equals(item.url)));
                assertEquals(mode, ContentPreference.getOnlyFap(context));
            }
            assertEquals(1, pawchiveCalls.get());
        } finally {
            ContentPreference.setOnlyFap(context, ContentPreference.ALL);
            executor.shutdownNow();
        }
    }

    @Test public void completedSuggestionReusesLiveResultsForFullSearchButModeAndConfigRefetch() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicInteger broadCalls = new AtomicInteger();
        AtomicInteger categoryCalls = new AtomicInteger();
        FapzoneCreatorSearchRepository repository = new FapzoneCreatorSearchRepository(executor,
                Collections.singletonList((ignored, query, limit) -> {
                    assertEquals(20, limit);
                    broadCalls.incrementAndGet();
                    return Collections.singletonList(creator("Nolan", "Fapello"));
                }), (ignored, query, limit, mode) -> {
                    categoryCalls.incrementAndGet();
                    return Collections.emptyList();
                });
        try {
            assertEquals(1, repository.search(context, "Nolan", 8).size());
            assertEquals(1, repository.search(context, " nolan ", 20).size());
            assertEquals(1, broadCalls.get());
            ContentPreference.set(context, ContentPreference.MEN);
            assertTrue(repository.search(context, "Nolan", 8).isEmpty());
            assertEquals(1, categoryCalls.get());
            ContentPreference.set(context, ContentPreference.ALL);
            assertEquals(1, repository.search(context, "Nolan", 20).size());
            assertEquals(1, broadCalls.get());

            JSONObject updated;
            try (InputStream input = context.getAssets().open("source_config_defaults.json")) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) >= 0) bytes.write(buffer, 0, count);
                updated = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            }
            updated.put("configVersion", updated.getLong("configVersion") + 1);
            RemoteSourceConfigManager.applyRemoteForTests(context, updated.toString());
            assertEquals(1, repository.search(context, "Nolan", 20).size());
            assertEquals(2, broadCalls.get());
        } finally {
            ContentPreference.set(context, ContentPreference.ALL);
            context.getSharedPreferences("remote_source_config", 0).edit().clear().commit();
            RemoteSourceConfigManager.resetForTests();
            executor.shutdownNow();
        }
    }

    @Test public void incompleteResponseIsRetriedAndNeverCachedAsDefinitiveMiss() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger retries = new AtomicInteger();
        try {
            FapzoneCreatorSearchRepository repository = new FapzoneCreatorSearchRepository(executor,
                    Arrays.asList((context, query, limit) -> {
                        if (retries.incrementAndGet() == 1) throw new IOException("temporary");
                        return Collections.singletonList(creator("Nolan", "Fapello"));
                    }, (context, query, limit) -> Collections.emptyList()));
            try {
                repository.search(RuntimeEnvironment.getApplication(), "nolan", 8);
                fail("Partial empty search should fail");
            } catch (IOException expected) { assertTrue(expected.getMessage().contains("incomplete")); }
            assertEquals(1, repository.search(RuntimeEnvironment.getApplication(), "nolan", 20).size());
            assertEquals(2, retries.get());
        } finally { executor.shutdownNow(); }
    }

    @Test public void verifiedAliasSurvivesMergedSearchWithOriginalFavoriteKey() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        NativeContentItem item = new NativeContentItem(NativeContentItem.KIND_CREATOR, "CarrotSoki",
                "https://fapello.com/carrotsoki-2/", "", "", "", "", "Fapello", "CarrotSoki");
        FapzoneCreatorSearchRepository.ConfirmedSourceSearch verified =
                (ignored, query, limit) -> Collections.singletonList(item);
        try {
            List<NativeContentItem> found = new FapzoneCreatorSearchRepository(executor,
                    Collections.singletonList(verified)).search(context, "sofiavictoria_iris", 8);
            assertEquals(1, found.size());
            assertEquals("CarrotSoki", found.get(0).title);
            assertEquals(item.url, found.get(0).url);
            assertEquals(CreatorFavoriteStore.key(item), CreatorFavoriteStore.key(found.get(0)));
            assertEquals("CarrotSoki", found.get(0).searchQuery);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test public void publishesFastSourceBeforeSlowSourceCompletes() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstPublished = new CountDownLatch(1);

        List<FapzoneCreatorSearchRepository.SourceSearch> sources = Arrays.asList(
                (ignoredContext, query, limit) ->
                        Collections.singletonList(creator("Fast Creator", "Fapello")),
                (ignoredContext, query, limit) -> {
                    try {
                        assertTrue(firstPublished.await(2, TimeUnit.SECONDS));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException(interrupted);
                    }
                    return Collections.singletonList(creator("Slow Creator", "OnlyHaven"));
                }
        );

        FapzoneCreatorSearchRepository repository =
                new FapzoneCreatorSearchRepository(executor, sources);
        List<Integer> publishedSizes = new ArrayList<>();
        List<Boolean> completionStates = new ArrayList<>();

        try {
            List<NativeContentItem> finalItems = repository.search(
                    context,
                    "creator",
                    10,
                    (items, complete) -> {
                        publishedSizes.add(items.size());
                        completionStates.add(complete);
                        if (!complete && items.size() == 1) firstPublished.countDown();
                    }
            );

            assertEquals(Arrays.asList(1, 2), publishedSizes);
            assertEquals(Arrays.asList(false, true), completionStates);
            assertEquals(2, finalItems.size());
            assertEquals("Fast Creator", finalItems.get(0).title);
            assertEquals("Slow Creator", finalItems.get(1).title);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test public void rejectsUnrelatedAndOpaqueCreatorFallbackResults() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        List<FapzoneCreatorSearchRepository.SourceSearch> sources = Collections.singletonList(
                (ignoredContext, query, limit) -> Arrays.asList(
                        creator("Send feedback", "OnlyHaven"),
                        creator("39058172", "OnlyHaven"),
                        creator("Sophie Rain", "OnlyHaven")
                )
        );
        FapzoneCreatorSearchRepository repository =
                new FapzoneCreatorSearchRepository(executor, sources);

        try {
            List<NativeContentItem> nonsense = repository.search(
                    context, "fbdijdnd", 10);
            assertTrue(nonsense.isEmpty());

            List<NativeContentItem> matching = repository.search(
                    context, "sophie", 10);
            assertEquals(1, matching.size());
            assertEquals("Sophie Rain", matching.get(0).title);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test public void failedSourceCannotTurnHealthyEmptyRepliesIntoConfirmedNoMatches() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            FapzoneCreatorSearchRepository repository = new FapzoneCreatorSearchRepository(executor,
                    Arrays.asList((context, query, limit) -> { throw new IOException("Fapello unavailable"); },
                            (context, query, limit) -> Collections.emptyList()));
            try {
                repository.search(RuntimeEnvironment.getApplication(), "CarrotSoki", 10);
                fail("An incomplete search must not claim there were no matches");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("incomplete"));
            }
        } finally { executor.shutdownNow(); }
    }

    @Test public void completeHealthyEmptySearchStillReturnsNoMatches() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            FapzoneCreatorSearchRepository repository = new FapzoneCreatorSearchRepository(executor,
                    Collections.singletonList((context, query, limit) -> Collections.emptyList()));
            assertTrue(repository.search(RuntimeEnvironment.getApplication(), "CarrotSoki", 10).isEmpty());
        } finally { executor.shutdownNow(); }
    }

    @Test public void successfulMatchesSurviveAnotherSourcesFailure() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            FapzoneCreatorSearchRepository repository = new FapzoneCreatorSearchRepository(executor,
                    Arrays.asList((context, query, limit) -> { throw new IOException("unavailable"); },
                            (context, query, limit) -> Collections.singletonList(creator("CarrotSoki", "Fapello"))));
            assertEquals("CarrotSoki", repository.search(RuntimeEnvironment.getApplication(),
                    "Carrot Soki", 10).get(0).title);
        } finally { executor.shutdownNow(); }
    }

    @Test public void selectedModeSearchUsesOnlyProviderCategoryAndRetainsAlias() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ContentPreference.set(context, ContentPreference.MEN);
        String profile = "https://cum.st/creators/onlyfans/nolan-1";
        OnlyHavenRepository.Creator creator = new OnlyHavenRepository.Creator("onlyfans", "nolan-1",
                "Nolan", profile, "", 17, 0, "", "nolantwink", "nolantwink");
        CreatorCategoryIndex.remember(context, ContentPreference.MEN, Collections.singletonList(creator));
        FapzoneCreatorSearchRepository repository = new FapzoneCreatorSearchRepository(executor,
                Collections.singletonList((ignored, query, limit) -> {
                    fail("A non-OnlyHaven source must not run in a selected mode");
                    return Collections.emptyList();
                }), (ignored, query, limit, category) -> {
                    assertEquals(ContentPreference.MEN, category);
                    return Collections.singletonList(
                            FapzoneCreatorSearchRepository.onlyHavenItem(creator, query));
                });
        try {
            List<NativeContentItem> found = repository.search(context, "nolantwink", 8);
            assertEquals(1, found.size());
            assertEquals(profile, found.get(0).url);
            assertEquals("Nolan", found.get(0).title);
            assertEquals("nolantwink", found.get(0).searchQuery);
            assertTrue(found.get(0).creatorAliases.contains("nolantwink"));
            assertEquals(Collections.singletonList(profile), found.get(0).creatorProfileUrls);
        } finally {
            ContentPreference.set(context, ContentPreference.ALL);
            executor.shutdownNow();
        }
    }

    @Test public void sharedSearchIncludesLearnedProfilesEvenWhenEveryLiveSourceFails() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(CreatorCatalog.PREFS, 0).edit().clear().commit();
        NativeContentItem known = new NativeContentItem(NativeContentItem.KIND_CREATOR,
                "CarrotSoki", "https://fapello.com/carrotsoki-2/", "", "", "", "", "Fapello", "CarrotSoki");
        CreatorCatalog.remember(context, Collections.singletonList(known));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            FapzoneCreatorSearchRepository repository = new FapzoneCreatorSearchRepository(executor,
                    Collections.singletonList((ignored, query, limit) -> { throw new IOException("unavailable"); }));
            List<NativeContentItem> found = repository.search(context, "Carrot Soki", 10);
            assertEquals(1, found.size());
            assertEquals(known.url, found.get(0).url);
        } finally { executor.shutdownNow(); }
    }

    @Test public void completeSearchPreservesTwoProfilesWithIdenticalTitles() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        OnlyHavenRepository.Creator first = new OnlyHavenRepository.Creator("fansly", "42",
                "ryn! >_<", "https://cum.st/creators/fansly/42", "", 7, 0, "", "rynkerbelle", "rynkerbelle");
        OnlyHavenRepository.Creator second = new OnlyHavenRepository.Creator("onlyfans", "43",
                "ryn! >_<", "https://cum.st/creators/onlyfans/43", "", 3, 0, "", "ryl-other", "ryl-other");
        FapzoneCreatorSearchRepository repository = new FapzoneCreatorSearchRepository(executor,
                Collections.singletonList((ignored, query, limit) -> Arrays.asList(
                        FapzoneCreatorSearchRepository.onlyHavenItem(first, query),
                        FapzoneCreatorSearchRepository.onlyHavenItem(second, query))));
        try {
            List<NativeContentItem> results = repository.search(context, "ryn", 10);
            assertEquals(2, results.size());
            assertEquals("rynkerbelle", results.get(0).searchQuery);
            assertEquals("ryl-other", results.get(1).searchQuery);
            assertEquals(first.url, results.get(0).url);
            assertEquals(second.url, results.get(1).url);
        } finally { executor.shutdownNow(); }
    }

    @Test public void handlePunctuationKeepsSearchCacheEntriesIndependent() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicInteger calls = new AtomicInteger();
        FapzoneCreatorSearchRepository repository = new FapzoneCreatorSearchRepository(executor,
                Collections.singletonList((ignored, query, limit) -> {
                    calls.incrementAndGet();
                    return Collections.singletonList(creator(query, "Fapello"));
                }));
        try {
            assertEquals("example.handle", repository.search(null, "example.handle", 10).get(0).title);
            assertEquals("example_handle", repository.search(null, "example_handle", 10).get(0).title);
            assertEquals(2, calls.get());
        } finally { executor.shutdownNow(); }
    }

    private NativeContentItem creator(String name, String source) {
        return new NativeContentItem(
                NativeContentItem.KIND_CREATOR,
                name,
                "https://example.com/" + name.replace(" ", "-").toLowerCase(),
                "",
                "",
                "",
                "",
                source,
                name
        );
    }
}
