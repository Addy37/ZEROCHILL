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
    private static final int MAX_SOURCE_PAGE = 32;
    private static final int BADDIEHUB_MAX_PAGE = 3;
    private static final int XMEGADRIVE_MAX_PAGE = 4;
    private static final ExecutorService CRAZY_IO = Executors.newFixedThreadPool(2);
    private static final ExecutorService SOURCE_IO = Executors.newFixedThreadPool(6);
    private static final ExecutorService CONTINUITY_SOURCE_IO = Executors.newFixedThreadPool(3);
    private static final long MIX_BATCH_BUDGET_MS = 5_500L;
    private static final int BATCH_REGULAR = 1;
    private static final int BATCH_EFUKT = 2;
    private static final int BATCH_KAOTIC = 3;
    private static final int BATCH_FAPELLO = 5;
    private static final int BATCH_ONLY_HAVEN = 6;
    private static final int BATCH_BADDIEHUB = 7;
    private static final int BATCH_XMEGADRIVE = 8;
    private static final int SOURCES_PER_BATCH = 3;
    private static final int REGULAR_ITEMS_PER_SOURCE = 4;
    private static final int SHIT_SHOW_PER_BATCH = 24;
    private static final int EFUKT_ITEMS_PER_BATCH = 12;
    private static final int EFUKT_SERIES_PER_BATCH = 2;
    private static final int FAPELLO_ITEMS_PER_BATCH = 16;
    private static final int KAOTIC_ITEMS_PER_BATCH = 16;
    private static final int ONLY_HAVEN_ITEMS_PER_BATCH = 6;
    private static final int ONLY_HAVEN_ITEMS_PER_CREATOR = 3;
    private static final int ONLY_HAVEN_TRENDING_CREATORS = 30;
    private static final int BADDIEHUB_ITEMS_PER_BATCH = 7;
    private static final int XMEGADRIVE_ITEMS_PER_BATCH = 8;
    private static final String VIDEOS = CrazyShitRepository.BASE + "videos/";
    private static final String USER_UPLOADS = CrazyShitRepository.BASE + "submissions/";

    private final CrazyShitRepository repository;
    private final Random random;
    interface SourceLoader { List<NativeContentItem> load(int source) throws Exception; }
    private final SourceLoader sourceLoader;
    private final ShitShowTapSource shitShow = new ShitShowTapSource();
    private final EfuktRepository efukt = new EfuktRepository();
    private final FapelloRepository fapello = new FapelloRepository();
    private final WebVideoSourceRepository webVideo = new WebVideoSourceRepository();
    private final OnlyHavenRepository onlyHaven = new OnlyHavenRepository();
    private final BaddieHubRepository baddieHub = new BaddieHubRepository();
    private final XMegaDriveRepository xMegaDrive = new XMegaDriveRepository();
    private final ArrayList<NativeContentItem> efuktSeries = new ArrayList<>();
    private final ArrayDeque<NativeContentItem> efuktSeriesDeck = new ArrayDeque<>();
    private final ArrayList<OnlyHavenRepository.Creator> onlyHavenCreators = new ArrayList<>();
    private final ArrayDeque<OnlyHavenRepository.Creator> onlyHavenCreatorDeck = new ArrayDeque<>();
    private final ArrayList<String> catalog = new ArrayList<>();
    private final ArrayDeque<String> sourceDeck = new ArrayDeque<>();
    private final Set<String> usedSourcePages = new HashSet<>();
    private final Object catalogLock = new Object();
    private final Object onlyHavenLock = new Object();
    private boolean catalogLoaded;
    private boolean catalogLoading;
    private boolean efuktCatalogAttempted;
    private boolean onlyHavenCatalogAttempted;
    private final java.util.concurrent.atomic.AtomicBoolean onlyHavenDeckReset = new java.util.concurrent.atomic.AtomicBoolean();
    private ContentPreference onlyHavenCatalogPreference = ContentPreference.ALL;
    private long categoryRetryAfter;
    private final Set<String> preferenceMatches = java.util.concurrent.ConcurrentHashMap.newKeySet();

    boolean matchesPreference(NativeContentItem item, ContentPreference mode) {
        return mode != ContentPreference.ALL && preferenceMatches.contains(mode.value + "|" + item.url);
    }
    private final ShitTokPreferenceBuffer preferenceBuffer = new ShitTokPreferenceBuffer();

    void requestPreference(Context context, Runnable available) {
        ContentPreference mode = ContentPreference.get(context);
        if (!explorationAllowed && mode != ContentPreference.ALL) return;
        Context app = context.getApplicationContext();
        preferenceBuffer.request(mode, () -> provider(BATCH_ONLY_HAVEN,
                () -> loadOnlyHavenBatch(app, mode)).call().items, available);
    }

    boolean hasPreference(ContentPreference mode) { return preferenceBuffer.hasReady(mode); }
    List<NativeContentItem> takePreference(ContentPreference mode) { return preferenceBuffer.take(mode); }
    void stopPreference() { preferenceBuffer.stop(); }

    private boolean starterPending = true;
    private volatile boolean explorationAllowed;
    private final java.util.concurrent.atomic.AtomicInteger activeFetches = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.ConcurrentHashMap<Integer, String> providerTimings = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<Integer> fetching = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<String, ArrayDeque<Integer>> pageDecks = new java.util.HashMap<>();
    int activeFetches() { return activeFetches.get(); }
    String providerSummary() { return providerTimings.toString(); }

    // Page traversal is cheap post-starter work, independent of rendering/expensive catalog warmup.
    synchronized int nextPage(String source, int maximum) {
        ArrayDeque<Integer> pages = pageDecks.computeIfAbsent(source, ignored -> new ArrayDeque<>());
        if (pages.isEmpty()) {
            ArrayList<Integer> shuffled = new ArrayList<>();
            for (int page = 1; page <= maximum; page++) shuffled.add(page);
            Collections.shuffle(shuffled, random);
            pages.addAll(shuffled);
        }
        return pages.removeFirst();
    }

    private java.util.concurrent.Callable<SourceBatch> provider(int source,
            java.util.concurrent.Callable<List<NativeContentItem>> loader) {
        return () -> {
            // Interrupted socket IO may outlive a batch. Never queue another copy behind it.
            if (!fetching.add(source)) return new SourceBatch(source, Collections.emptyList());
            activeFetches.incrementAndGet();
            long at = android.os.SystemClock.elapsedRealtime();
            providerTimings.put(source, "active");
            try {
                List<NativeContentItem> items = sourceLoader == null ? loader.call() : sourceLoader.load(source);
                providerTimings.put(source, "ms=" + (android.os.SystemClock.elapsedRealtime() - at) + ",candidates=" + items.size());
                return new SourceBatch(source, items);
            } finally {
                providerTimings.computeIfPresent(source, (key, state) -> state.equals("active")
                        ? "ms=" + (android.os.SystemClock.elapsedRealtime() - at) + ",status=cancelledOrFailed" : state);
                activeFetches.decrementAndGet(); fetching.remove(source);
                ShitTokPoolDiagnostics.record("provider", "source=" + source + " " + providerTimings.get(source));
            }
        };
    }

    void onFirstFrame() { explorationAllowed = true; }

    ChaosSourceMixer(CrazyShitRepository repository, Random random) {
        this(repository, random, null);
    }

    ChaosSourceMixer(CrazyShitRepository repository, Random random, SourceLoader sourceLoader) {
        this.repository = repository;
        this.random = random;
        this.sourceLoader = sourceLoader;
    }

    void setPlaybackProtected(boolean value) {
        shitShow.setPlaybackProtected(value);
    }

    List<NativeContentItem> loadRandomBatch(Context context) {
        return loadRandomBatch(context, false);
    }

    List<NativeContentItem> loadRandomBatch(Context context, boolean continuityOnly) {
        return loadRandomBatch(context, continuityOnly, null);
    }

    List<NativeContentItem> loadRandomBatch(Context context, boolean continuityOnly,
            java.util.function.Consumer<List<NativeContentItem>> onCandidates) {
        if (starterPending) {
            starterPending = false;
            List<NativeContentItem> starter = loadStarterBatch(context, onCandidates);
            if (!starter.isEmpty()) return starter;
        }
        // Hidden WebView/catalog exploration begins only with normal post-starter refills.
        if (explorationAllowed) shitShow.prewarm(context);

        // Full refills race independent sources together. A slow or dead provider gets the same
        // bounded batch window as every other provider instead of serially delaying ShitTok.
        ExecutorCompletionService<SourceBatch> completions =
                new ExecutorCompletionService<>(continuityOnly ? CONTINUITY_SOURCE_IO : SOURCE_IO);
        ArrayList<Future<SourceBatch>> work = new ArrayList<>();
        ArrayList<java.util.concurrent.Callable<SourceBatch>> requests = new ArrayList<>();
        final ContentPreference preference = ContentPreference.get(context);
        // Fast independent listings enter the bounded three-worker pool before multi-page catalogs.
        requests.add(provider(BATCH_KAOTIC, () -> loadKaoticBatch(context)));
        requests.add(provider(BATCH_FAPELLO, () -> loadFapelloBatch(context)));
        requests.add(provider(BATCH_REGULAR, () -> loadRegularBatch(context, onCandidates)));
        requests.add(provider(BATCH_BADDIEHUB, () -> loadBaddieHubBatch(context)));
        requests.add(provider(BATCH_XMEGADRIVE, () -> loadXMegaDriveBatch(context)));
        // eFukt remains bounded before the first frame. No new sources are introduced.
        requests.add(provider(BATCH_EFUKT,
                () -> explorationAllowed ? loadEfuktBatch(context) : efukt.fetchLatest(context)));
        if (explorationAllowed && preference == ContentPreference.ALL)
            requests.add(provider(BATCH_ONLY_HAVEN, () -> loadOnlyHavenBatch(context)));
        for (java.util.concurrent.Callable<SourceBatch> request : requests) work.add(completions.submit(request));

        ArrayList<NativeContentItem> regularItems = new ArrayList<>();
        ArrayList<NativeContentItem> efuktItems = new ArrayList<>();
        ArrayList<NativeContentItem> kaoticItems = new ArrayList<>();
        ArrayList<NativeContentItem> fapelloItems = new ArrayList<>();
        ArrayList<NativeContentItem> onlyHavenItems = new ArrayList<>();
        ArrayList<NativeContentItem> baddieHubItems = new ArrayList<>();
        ArrayList<NativeContentItem> xMegaDriveItems = new ArrayList<>();

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
                    // Start admission as each provider completes. Slow catalogs cannot hold
                    // already available candidates behind the mixed-batch deadline.
                    if (onCandidates != null) {
                        int cap = batch.source == BATCH_EFUKT ? 1
                                : batch.source == BATCH_REGULAR ? 6 : batch.items.size();
                        onCandidates.accept(batch.items.subList(0, Math.min(cap, batch.items.size())));
                    }
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
                        case BATCH_BADDIEHUB:
                            baddieHubItems.addAll(batch.items);
                            break;
                        case BATCH_XMEGADRIVE:
                            xMegaDriveItems.addAll(batch.items);
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
        Collections.shuffle(baddieHubItems, random);
        Collections.shuffle(xMegaDriveItems, random);

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
        if (onCandidates != null) onCandidates.accept(shitShowItems);

        ArrayList<NativeContentItem> result = new ArrayList<>(mixAvailable(
                kaoticItems, shitShowItems, fapelloItems, onlyHavenItems, regularItems, efuktItems));
        addUpTo(result, baddieHubItems, BADDIEHUB_ITEMS_PER_BATCH);
        addUpTo(result, xMegaDriveItems, XMEGADRIVE_ITEMS_PER_BATCH);
        return result;
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

    private List<NativeContentItem> loadRegularBatch(Context context, java.util.function.Consumer<List<NativeContentItem>> onCandidates) {
        ensureCatalog(context);
        LinkedHashMap<String, NativeContentItem> regular = new LinkedHashMap<>();
        int sourceCount;
        synchronized (catalogLock) {
            sourceCount = Math.min(SOURCES_PER_BATCH, Math.max(2, catalog.size()));
        }
        for (int i = 0; i < sourceCount; i++) {
            if (Thread.currentThread().isInterrupted()) break;
            int before = regular.size();
            addRequest(context, regular, nextRequest());
            if (onCandidates != null && regular.size() > before) {
                List<NativeContentItem> arrived = new ArrayList<>(regular.values());
                onCandidates.accept(arrived.subList(before, arrived.size()));
            }
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
        onlyHavenDeckReset.set(true);
    }

    private List<NativeContentItem> withKnownStarter(List<NativeContentItem> starter) {
        // Reuse video dimensions already harvested during the starter race. No new wait or probe.
        ArrayList<NativeContentItem> combined = new ArrayList<>(shitShow.takeReadyBatch(6));
        combined.addAll(starter);
        return combined;
    }

    private List<NativeContentItem> loadStarterBatch(Context context,
            java.util.function.Consumer<List<NativeContentItem>> onCandidates) {
        try {
            return ChaosStartupPreloader.collect(context, onCandidates);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new ArrayList<>();
        }
    }

    private List<NativeContentItem> loadBaddieHubBatch(Context context) {
        try {
            ArrayList<NativeContentItem> candidates = new ArrayList<>(
                    ShitTokPoolDiagnostics.fetched(BATCH_BADDIEHUB, baddieHub.fetchShitTokCandidates(
                            context,
                            nextPage("baddiehub", BADDIEHUB_MAX_PAGE)
                    ))
            );
            candidates.removeIf(item -> item == null || !item.isVideo());
            Collections.shuffle(candidates, random);
            int take = Math.min(BADDIEHUB_ITEMS_PER_BATCH, candidates.size());
            return new ArrayList<>(candidates.subList(0, take));
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private List<NativeContentItem> loadXMegaDriveBatch(Context context) {
        try {
            ArrayList<NativeContentItem> candidates = new ArrayList<>(
                    ShitTokPoolDiagnostics.fetched(BATCH_XMEGADRIVE, xMegaDrive.fetchShitTokCandidates(
                            context,
                            nextPage("xmegadrive", XMEGADRIVE_MAX_PAGE)
                    ))
            );
            candidates.removeIf(item -> item == null || !item.isVideo());
            Collections.shuffle(candidates, random);
            int take = Math.min(XMEGADRIVE_ITEMS_PER_BATCH, candidates.size());
            return new ArrayList<>(candidates.subList(0, take));
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    // NOTE: Remaining methods (loadKaoticBatch, loadFapelloBatch, loadEfuktBatch, loadOnlyHavenBatch,
    // ensureCatalog, addRequest, nextRequest, refillDeck, SourceBatch, SourceRequest) must be restored
    // from the verified full local file. This partial push is a temporary marker.
}
