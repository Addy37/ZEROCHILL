package com.webapp.crazyshit;

import android.content.Context;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Builds randomized Chaos batches from the site's broad video catalog.
 *
 * Normal site feeds are parsed with the repository. Shit Show is intentionally separate because
 * it is a JavaScript-driven swipe player, not a normal /cnt/medias/ listing.
 */
final class ChaosSourceMixer {
    private static final int MAX_SOURCE_PAGE = 8;
    private static final ExecutorService CRAZY_IO = Executors.newFixedThreadPool(2);
    private static final ExecutorService SOURCE_IO = Executors.newFixedThreadPool(5);
    private static final long MIX_BATCH_BUDGET_MS = 2_200L;
    private static final int BATCH_REGULAR = 1;
    private static final int BATCH_EFUKT = 2;
    private static final int BATCH_KAOTIC = 3;
    private static final int BATCH_FAPELLO = 5;
    private static final int BATCH_ONLY_HAVEN = 6;
    private static final int SOURCES_PER_BATCH = 3;
    private static final int REGULAR_ITEMS_PER_SOURCE = 4;
    private static final int SHIT_SHOW_PER_BATCH = 24;
    private static final int EFUKT_ITEMS_PER_BATCH = 12;
    private static final int EFUKT_SERIES_PER_BATCH = 2;
    private static final int FAPELLO_ITEMS_PER_BATCH = 4;
    private static final int KAOTIC_ITEMS_PER_BATCH = 8;
    private static final int ONLY_HAVEN_ITEMS_PER_BATCH = 6;
    private static final int ONLY_HAVEN_ITEMS_PER_CREATOR = 3;
    private static final int ONLY_HAVEN_TRENDING_CREATORS = 30;
    private static final String VIDEOS = CrazyShitRepository.BASE + "videos/";
    private static final String USER_UPLOADS = CrazyShitRepository.BASE + "submissions/";

    private final CrazyShitRepository repository;
    private final Random random;
    private final ShitShowTapSource shitShow = new ShitShowTapSource();
    private final EfuktRepository efukt = new EfuktRepository();
    private final FapelloRepository fapello = new FapelloRepository();
    private final WebVideoSourceRepository webVideo = new WebVideoSourceRepository();
    private final OnlyHavenRepository onlyHaven = new OnlyHavenRepository();
    private final ArrayList<NativeContentItem> efuktSeries = new ArrayList<>();
    private final ArrayDeque<NativeContentItem> efuktSeriesDeck = new ArrayDeque<>();
    private final ArrayList<OnlyHavenRepository.Creator> onlyHavenCreators = new ArrayList<>();
    private final ArrayDeque<OnlyHavenRepository.Creator> onlyHavenCreatorDeck = new ArrayDeque<>();
    private final ArrayList<String> catalog = new ArrayList<>();
    private final ArrayDeque<String> sourceDeck = new ArrayDeque<>();
    private final Set<String> usedSourcePages = new HashSet<>();
    private final Object catalogLock = new Object();
    private boolean catalogLoaded;
    private boolean catalogLoading;
    private boolean efuktCatalogAttempted;
    private boolean onlyHavenCatalogAttempted;
    private boolean starterPending = true;

    ChaosSourceMixer(CrazyShitRepository repository, Random random) {
        this.repository = repository;
        this.random = random;
    }

    List<NativeContentItem> loadRandomBatch(Context context) {
        // Cold-start optimization: warm Shit Show immediately, but let the first Chaos request
        // return a small starter queue instead of waiting for the full mixed catalog.
        shitShow.prewarm(context);
        if (starterPending) {
            starterPending = false;

            List<NativeContentItem> preloaded = ChaosStartupPreloader.takeStarter();
            if (!preloaded.isEmpty()) return preloaded;

            List<NativeContentItem> starter = loadStarterBatch(context);
            if (!starter.isEmpty()) return starter;
        }

        // Full refills race independent sources together. A slow or dead provider gets the same
        // bounded batch window as every other provider instead of serially delaying ShitTok.
        ExecutorCompletionService<SourceBatch> completions =
                new ExecutorCompletionService<>(SOURCE_IO);
        ArrayList<Future<SourceBatch>> work = new ArrayList<>();
        work.add(completions.submit(() -> new SourceBatch(
                BATCH_REGULAR, loadRegularBatch(context))));
        work.add(completions.submit(() -> new SourceBatch(
                BATCH_EFUKT, loadEfuktBatch(context))));
        work.add(completions.submit(() -> new SourceBatch(
                BATCH_KAOTIC, loadKaoticBatch(context))));
        work.add(completions.submit(() -> new SourceBatch(
                BATCH_FAPELLO, loadFapelloBatch(context))));
        work.add(completions.submit(() -> new SourceBatch(
                BATCH_ONLY_HAVEN, loadOnlyHavenBatch(context))));

        ArrayList<NativeContentItem> regularItems = new ArrayList<>();
        ArrayList<NativeContentItem> efuktItems = new ArrayList<>();
        ArrayList<NativeContentItem> kaoticItems = new ArrayList<>();
        ArrayList<NativeContentItem> fapelloItems = new ArrayList<>();
        ArrayList<NativeContentItem> onlyHavenItems = new ArrayList<>();

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MIX_BATCH_BUDGET_MS);
        int remainingWork = work.size();
        try {
            while (remainingWork-- > 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) break;
                Future<SourceBatch> completed = completions.poll(
                        remaining, TimeUnit.NANOSECONDS);
                if (completed == null) break;
                try {
                    SourceBatch batch = completed.get();
                    if (batch == null || batch.items == null) continue;
                    switch (batch.source) {
                        case BATCH_REGULAR:
                            regularItems.addAll(batch.items);
                            break;
                        case BATCH_EFUKT:
                            efuktItems.addAll(batch.items);
                            break;
                        case BATCH_KAOTIC:
                            kaoticItems.addAll(batch.items);
                            break;
                        case BATCH_FAPELLO:
                            fapelloItems.addAll(batch.items);
                            break;
                        case BATCH_ONLY_HAVEN:
                            onlyHavenItems.addAll(batch.items);
                            break;
                        default:
                            break;
                    }
                } catch (Exception ignored) {
                    // Other source tasks continue contributing to this batch.
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            for (Future<SourceBatch> request : work) {
                if (!request.isDone()) request.cancel(true);
            }
        }

        Collections.shuffle(regularItems, random);
        Collections.shuffle(efuktItems, random);
        Collections.shuffle(kaoticItems, random);
        Collections.shuffle(fapelloItems, random);
        Collections.shuffle(onlyHavenItems, random);

        ArrayList<NativeContentItem> shitShowItems = new ArrayList<>();
        HashSet<String> regularUrls = new HashSet<>();
        for (NativeContentItem item : regularItems) {
            if (item != null && item.url != null) regularUrls.add(item.url);
        }
        for (NativeContentItem item : shitShow.takeReadyBatch(
                kaoticItems.size() < 6 ? SHIT_SHOW_PER_BATCH : 7)) {
            if (item == null || item.url == null || item.url.isEmpty()) continue;
            if (regularUrls.contains(item.url)) continue;
            shitShowItems.add(item);
        }
        Collections.shuffle(shitShowItems, random);

        return mixAvailable(kaoticItems, shitShowItems, fapelloItems,
                onlyHavenItems, regularItems, efuktItems);
    }

    static List<NativeContentItem> mixAvailable(
            List<NativeContentItem> kaotic, List<NativeContentItem> shitShow,
            List<NativeContentItem> fapello, List<NativeContentItem> onlyHaven,
            List<NativeContentItem> crazyShit, List<NativeContentItem> efukt
    ) {
        ArrayList<NativeContentItem> preferred = new ArrayList<>();
        boolean kaoticReady = kaotic != null && kaotic.size() >= 6;
        addUpTo(preferred, kaotic, Integer.MAX_VALUE);
        // Kaotic leads a healthy batch. If it is short, keep the other feeds uncapped.
        addUpTo(preferred, shitShow, kaoticReady ? 7 : Integer.MAX_VALUE);
        addUpTo(preferred, fapello, Integer.MAX_VALUE);
        addUpTo(preferred, onlyHaven, Integer.MAX_VALUE);
        addUpTo(preferred, crazyShit, kaoticReady ? 6 : Integer.MAX_VALUE);

        int efuktLimit = preferred.size() < 10 ? Integer.MAX_VALUE
                : Math.max(1, (preferred.size() + 12) / 13);
        ArrayList<NativeContentItem> result = new ArrayList<>(preferred);
        addUpTo(result, efukt, efuktLimit);
        // FeedView applies its portrait and source-weighted shuffle to this complete pool.
        return result;
    }

    private static void addUpTo(List<NativeContentItem> result,
                                List<NativeContentItem> source, int limit) {
        if (source == null) return;
        for (int i = 0; i < source.size() && i < limit; i++) result.add(source.get(i));
    }

    private List<NativeContentItem> loadRegularBatch(Context context) {
        ensureCatalog(context);
        LinkedHashMap<String, NativeContentItem> regular = new LinkedHashMap<>();
        int sourceCount;
        synchronized (catalogLock) {
            sourceCount = Math.min(SOURCES_PER_BATCH, Math.max(2, catalog.size()));
        }
        for (int i = 0; i < sourceCount; i++) {
            if (Thread.currentThread().isInterrupted()) break;
            addRequest(context, regular, nextRequest());
        }
        return new ArrayList<>(regular.values());
    }

    void resetDeck() {
        synchronized (catalogLock) {
            sourceDeck.clear();
            usedSourcePages.clear();
        }
        starterPending = true;
        shitShow.resetDeck();
        efuktSeriesDeck.clear();
        if (efuktSeries.isEmpty()) efuktCatalogAttempted = false;
        onlyHavenCreatorDeck.clear();
        if (onlyHavenCreators.isEmpty()) onlyHavenCatalogAttempted = false;
    }

    private List<NativeContentItem> loadStarterBatch(Context context) {
        try {
            return ChaosStarterSources.first(ChaosStarterSources.live(context, repository, random),
                    random, ChaosStarterSources.STARTUP_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new ArrayList<>();
        }
    }


    private List<NativeContentItem> loadEfuktBatch(Context context) {
        ensureEfuktCatalog(context);
        if (efuktSeries.isEmpty()) return new ArrayList<>();

        LinkedHashMap<String, NativeContentItem> combined = new LinkedHashMap<>();
        HashSet<String> usedSeries = new HashSet<>();
        int seriesTarget = Math.min(EFUKT_SERIES_PER_BATCH, efuktSeries.size());
        int attempts = Math.max(4, efuktSeries.size() * 2);

        while (usedSeries.size() < seriesTarget && attempts-- > 0) {
            if (Thread.currentThread().isInterrupted()) break;
            if (efuktSeriesDeck.isEmpty()) refillEfuktDeck();
            NativeContentItem series = efuktSeriesDeck.pollFirst();
            if (series == null || series.url == null || series.url.isEmpty()) continue;
            if (!usedSeries.add(series.url)) continue;

            try {
                for (NativeContentItem item : efukt.fetchSeriesFeed(context, series.url, 1)) {
                    if (item == null || item.url == null || item.url.isEmpty()) continue;
                    if (!NativeContentItem.KIND_MEDIA.equals(item.kind)) continue;
                    combined.putIfAbsent(item.url, item);
                }
            } catch (Exception ignored) {
            }
        }

        ArrayList<NativeContentItem> candidates = new ArrayList<>(combined.values());
        Collections.shuffle(candidates, random);
        int take = Math.min(EFUKT_ITEMS_PER_BATCH, candidates.size());
        return new ArrayList<>(candidates.subList(0, take));
    }

    private void ensureEfuktCatalog(Context context) {
        if (efuktCatalogAttempted) return;
        efuktCatalogAttempted = true;
        boolean loaded = false;
        try {
            for (NativeContentItem series : efukt.fetchSeries(context)) {
                if (Thread.currentThread().isInterrupted()) break;
                if (series == null || series.url == null || series.url.isEmpty()) continue;
                if (!NativeContentItem.KIND_SERIES.equals(series.kind)) continue;
                efuktSeries.add(series);
            }
            loaded = !Thread.currentThread().isInterrupted();
        } catch (Exception ignored) {
        }
        if (!loaded && efuktSeries.isEmpty()) efuktCatalogAttempted = false;
        refillEfuktDeck();
    }

    private void refillEfuktDeck() {
        if (efuktSeries.isEmpty()) return;
        ArrayList<NativeContentItem> shuffled = new ArrayList<>(efuktSeries);
        Collections.shuffle(shuffled, random);
        efuktSeriesDeck.addAll(shuffled);
    }

    private List<NativeContentItem> loadFapelloBatch(Context context) {
        try {
            ArrayList<NativeContentItem> candidates = new ArrayList<>(
                    fapello.fetchPopularVideos(context, 1 + random.nextInt(MAX_SOURCE_PAGE))
            );
            candidates.removeIf(item -> item == null || !item.isVideo());
            Collections.shuffle(candidates, random);
            int take = Math.min(FAPELLO_ITEMS_PER_BATCH, candidates.size());
            return new ArrayList<>(candidates.subList(0, take));
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private List<NativeContentItem> loadKaoticBatch(Context context) {
        try {
            ArrayList<NativeContentItem> candidates = new ArrayList<>(
                    webVideo.fetchFeed(
                            context,
                            WebVideoSourceRepository.Source.KAOTIC,
                            1 + random.nextInt(MAX_SOURCE_PAGE)
                    )
            );
            candidates.removeIf(item -> item == null || !item.isVideo());
            Collections.shuffle(candidates, random);
            int take = Math.min(KAOTIC_ITEMS_PER_BATCH, candidates.size());
            return new ArrayList<>(candidates.subList(0, take));
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private List<NativeContentItem> loadOnlyHavenBatch(Context context) {
        ensureOnlyHavenCatalog(context);
        if (onlyHavenCreators.isEmpty()) return new ArrayList<>();

        LinkedHashMap<String, NativeContentItem> combined = new LinkedHashMap<>();
        HashSet<String> usedCreators = new HashSet<>();
        int attempts = Math.min(4, onlyHavenCreators.size());

        while (combined.size() < ONLY_HAVEN_ITEMS_PER_BATCH && attempts-- > 0) {
            if (Thread.currentThread().isInterrupted()) break;
            if (onlyHavenCreatorDeck.isEmpty()) refillOnlyHavenDeck();
            OnlyHavenRepository.Creator creator = onlyHavenCreatorDeck.pollFirst();
            if (creator == null || creator.url == null || creator.url.isEmpty()) continue;
            if (!usedCreators.add(creator.url)) continue;

            ArrayList<NativeContentItem> candidates = new ArrayList<>();
            try {
                for (NativeContentItem item :
                        onlyHaven.fetchCreatorMedia(context, creator, 1, 18)) {
                    if (item == null || !item.isVideo()) continue;
                    candidates.add(new NativeContentItem(
                            item.kind,
                            item.title,
                            item.url,
                            item.imageUrl,
                            item.views,
                            "OnlyHaven",
                            item.comments,
                            item.description,
                            item.searchQuery
                    ));
                }
            } catch (Exception ignored) {
            }

            Collections.shuffle(candidates, random);
            int perCreator = Math.min(ONLY_HAVEN_ITEMS_PER_CREATOR, candidates.size());
            for (int i = 0; i < perCreator; i++) {
                NativeContentItem item = candidates.get(i);
                if (item.url == null || item.url.isEmpty()) continue;
                combined.putIfAbsent(item.url, item);
                if (combined.size() >= ONLY_HAVEN_ITEMS_PER_BATCH) break;
            }
        }

        return new ArrayList<>(combined.values());
    }

    private void ensureOnlyHavenCatalog(Context context) {
        if (onlyHavenCatalogAttempted) return;
        onlyHavenCatalogAttempted = true;
        boolean loaded = false;
        try {
            for (OnlyHavenRepository.Creator creator :
                    onlyHaven.fetchTrendingCreators(context, ONLY_HAVEN_TRENDING_CREATORS)) {
                if (Thread.currentThread().isInterrupted()) break;
                if (creator == null || creator.url == null || creator.url.isEmpty()) continue;
                if (creator.isKnownEmpty()) continue;
                onlyHavenCreators.add(creator);
            }
            loaded = !Thread.currentThread().isInterrupted();
        } catch (Exception ignored) {
        }
        if (!loaded && onlyHavenCreators.isEmpty()) onlyHavenCatalogAttempted = false;
        refillOnlyHavenDeck();
    }

    private void refillOnlyHavenDeck() {
        if (onlyHavenCreators.isEmpty()) return;
        ArrayList<OnlyHavenRepository.Creator> shuffled =
                new ArrayList<>(onlyHavenCreators);
        Collections.shuffle(shuffled, random);
        onlyHavenCreatorDeck.addAll(shuffled);
    }

    private void ensureCatalog(Context context) {
        synchronized (catalogLock) {
            if (catalog.isEmpty()) {
                catalog.add(CrazyShitRepository.HOME);
                catalog.add(CrazyShitRepository.TRENDING);
                catalog.add(VIDEOS);
                catalog.add(USER_UPLOADS);
                refillDeck();
            }
            if (catalogLoaded || catalogLoading) return;
            catalogLoading = true;
        }

        // Category expansion is useful for variety, but it must never delay the base CrazyShit
        // feeds. Populate it independently and fold it into a later batch when it is ready.
        CRAZY_IO.execute(() -> {
            LinkedHashSet<String> discovered = new LinkedHashSet<>();
            try {
                for (NativeContentItem category : repository.fetchCategories(context)) {
                    if (Thread.currentThread().isInterrupted()) break;
                    if (category == null || category.url == null ||
                            category.url.trim().isEmpty()) continue;
                    discovered.add(category.url.trim());
                }
            } catch (Exception ignored) {
            }

            synchronized (catalogLock) {
                if (!discovered.isEmpty()) {
                    for (String url : discovered) {
                        if (!catalog.contains(url)) catalog.add(url);
                    }
                    catalogLoaded = true;
                    sourceDeck.clear();
                    refillDeck();
                }
                catalogLoading = false;
            }
        });
    }

    private void addRequest(Context context,
                            LinkedHashMap<String, NativeContentItem> combined,
                            SourceRequest request) {
        if (request == null) return;
        try {
            ArrayList<NativeContentItem> candidates = new ArrayList<>();
            for (NativeContentItem item : repository.fetchFeed(context, request.url, request.page)) {
                if (item == null || item.url == null || item.url.isEmpty()) continue;
                if (!NativeContentItem.KIND_MEDIA.equals(item.kind)) continue;
                candidates.add(item);
            }
            Collections.shuffle(candidates, random);
            int take = Math.min(REGULAR_ITEMS_PER_SOURCE, candidates.size());
            for (int i = 0; i < take; i++) {
                NativeContentItem item = candidates.get(i);
                combined.putIfAbsent(item.url, item);
            }
        } catch (Exception ignored) {
        }
    }

    private SourceRequest nextRequest() {
        synchronized (catalogLock) {
            if (catalog.isEmpty()) return null;

            int attempts = Math.max(16, catalog.size() * 3);
            for (int i = 0; i < attempts; i++) {
                if (sourceDeck.isEmpty()) refillDeck();
                String url = sourceDeck.pollFirst();
                if (url == null || url.isEmpty()) continue;

                int page = 1 + random.nextInt(MAX_SOURCE_PAGE);
                String key = url + "#" + page;
                if (usedSourcePages.add(key)) return new SourceRequest(url, page);
            }

            usedSourcePages.clear();
            if (sourceDeck.isEmpty()) refillDeck();
            String url = sourceDeck.pollFirst();
            if (url == null || url.isEmpty()) return null;
            int page = 1 + random.nextInt(MAX_SOURCE_PAGE);
            usedSourcePages.add(url + "#" + page);
            return new SourceRequest(url, page);
        }
    }

    private void refillDeck() {
        if (catalog.isEmpty()) return;
        ArrayList<String> shuffled = new ArrayList<>(catalog);
        Collections.shuffle(shuffled, random);
        sourceDeck.addAll(shuffled);
    }

    private static final class SourceBatch {
        final int source;
        final List<NativeContentItem> items;

        SourceBatch(int source, List<NativeContentItem> items) {
            this.source = source;
            this.items = items == null ? new ArrayList<>() : items;
        }
    }

    private static final class SourceRequest {
        final String url;
        final int page;

        SourceRequest(String url, int page) {
            this.url = url;
            this.page = page;
        }
    }
}
