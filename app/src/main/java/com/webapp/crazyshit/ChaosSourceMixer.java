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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private static final ExecutorService SOURCE_IO = new java.util.concurrent.ThreadPoolExecutor(
            6, 6, 0L, TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(16));
    private static final long MIX_BATCH_BUDGET_MS = 5_500L;
    private static final int BATCH_SHIT_SHOW = 4;
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

    private final ShitTokSourcePool sourcePool = new ShitTokSourcePool(SOURCE_IO, android.os.SystemClock::elapsedRealtime);
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
        return mode != ContentPreference.ALL && ShitTokPreferenceMix.eligible(item, mode, preferenceMatches);
    }
    private volatile ContentPreference poolMode = ContentPreference.ALL;

    synchronized void requestPreference(Context context, Runnable available) {
        ContentPreference mode = ContentPreference.get(context);
        if (poolMode != mode) {
            sourcePool.stop(); shitShow.stop(); poolMode = mode;
        }
        if (mode == ContentPreference.ALL) return;
        sourcePool.filterExcept(BATCH_ONLY_HAVEN);
        SourceConfig config = RemoteSourceConfigManager.snapshotOrNull();
        boolean enabled = config == null || !config.sourceKillSwitchesEnabled || config.onlyHaven.enabled;
        Context app = context.getApplicationContext();
        sourcePool.request(BATCH_ONLY_HAVEN, true, enabled,
                () -> source(BATCH_ONLY_HAVEN, () -> loadOnlyHavenBatch(app, mode)), available);
    }

    boolean hasPreference(ContentPreference mode) {
        return mode != ContentPreference.ALL && poolMode == mode && sourcePool.hasReady(BATCH_ONLY_HAVEN);
    }
    List<NativeContentItem> takePreference(ContentPreference mode) {
        return poolMode == mode ? sourcePool.take(2) : Collections.emptyList();
    }
    void stopPreference() { sourcePool.stop(); shitShow.stop(); }

    private boolean starterPending = true;
    private volatile boolean explorationAllowed;
    private final java.util.concurrent.atomic.AtomicInteger activeFetches = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.ConcurrentHashMap<Integer, String> providerTimings = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<Integer> fetching = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<String, ArrayDeque<Integer>> pageDecks = new java.util.HashMap<>();
    int activeFetches() { return activeFetches.get(); }
    String providerSummary() { return sourcePool.summary(); }

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
        ContentPreference mode = ContentPreference.get(context);
        if (mode != ContentPreference.ALL) {
            // Only the authoritative category loader can produce a filtered candidate.
            return takePreference(mode);
        }
        if (starterPending) {
            starterPending = false;
            List<NativeContentItem> starter = loadStarterBatch(context, onCandidates);
            if (!starter.isEmpty()) return starter;
        }
        recoverSources(context);
        ArrayList<NativeContentItem> result = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MIX_BATCH_BUDGET_MS);
        // The consumer can stop admission at any time. Provider work retains its own bounded
        // session buffers and survives that decision, until explicit lifecycle/mode cancellation.
        while (!Thread.currentThread().isInterrupted() && System.nanoTime() < deadline) {
            List<NativeContentItem> ready = sourcePool.take(8);
            if (!ready.isEmpty()) {
                result.addAll(ready);
                if (onCandidates != null) onCandidates.accept(ready);
                if (result.size() >= 24) break;
            }
            try { Thread.sleep(40L); }
            catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); break; }
        }
        return result;
    }

    List<NativeContentItem> takeReadyCandidates() { return sourcePool.take(16); }

    void recoverSources(Context context) {
        if (ContentPreference.get(context) != ContentPreference.ALL) return;
        SourceConfig config = RemoteSourceConfigManager.snapshotOrNull();
        boolean switches = config != null && config.sourceKillSwitchesEnabled;
        sourcePool.request(BATCH_KAOTIC, true, config == null || config.kaotic.enabled, () -> source(BATCH_KAOTIC, () -> loadKaoticBatch(context)));
        sourcePool.request(BATCH_FAPELLO, true, !switches || config.fapello.enabled, () -> source(BATCH_FAPELLO, () -> loadFapelloBatch(context)));
        sourcePool.request(BATCH_REGULAR, true, true, () -> source(BATCH_REGULAR, () -> loadRegularBatch(context, null)));
        sourcePool.request(BATCH_BADDIEHUB, true, !switches || config.baddieHub.enabled, () -> source(BATCH_BADDIEHUB, () -> loadBaddieHubBatch(context)));
        sourcePool.request(BATCH_XMEGADRIVE, true, true, () -> source(BATCH_XMEGADRIVE, () -> loadXMegaDriveBatch(context)));
        sourcePool.request(BATCH_EFUKT, true, true, () -> source(BATCH_EFUKT,
                () -> explorationAllowed ? loadEfuktBatch(context) : efukt.fetchLatest(context)));
        if (explorationAllowed) {
            sourcePool.request(BATCH_ONLY_HAVEN, true, !switches || config.onlyHaven.enabled, () -> source(BATCH_ONLY_HAVEN, () -> loadOnlyHavenBatch(context)));
            sourcePool.request(BATCH_SHIT_SHOW, true, true, () -> source(BATCH_SHIT_SHOW,
                    () -> shitShow.takeBatchOrWarm(context, SHIT_SHOW_PER_BATCH)));
        }
    }

    private List<NativeContentItem> source(int id, java.util.concurrent.Callable<List<NativeContentItem>> loader) throws Exception {
        return provider(id, loader).call().items;
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
            throw new IllegalStateException("Source acquisition failed", ignored);
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
            throw new IllegalStateException("Source acquisition failed", ignored);
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
                for (NativeContentItem item : ShitTokPoolDiagnostics.fetched(BATCH_EFUKT, efukt.fetchSeriesFeed(context, series.url, 1))) {
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
                    ShitTokPoolDiagnostics.fetched(BATCH_FAPELLO, fapello.fetchPopularVideos(context, nextPage("fapello", MAX_SOURCE_PAGE)))
            );
            candidates.removeIf(item -> item == null || !item.isVideo());
            Collections.shuffle(candidates, random);
            int take = Math.min(FAPELLO_ITEMS_PER_BATCH, candidates.size());
            return new ArrayList<>(candidates.subList(0, take));
        } catch (Exception ignored) {
            throw new IllegalStateException("Source acquisition failed", ignored);
        }
    }

    private List<NativeContentItem> loadKaoticBatch(Context context) {
        try {
            ArrayList<NativeContentItem> candidates = new ArrayList<>(
                    ShitTokPoolDiagnostics.fetched(BATCH_KAOTIC, webVideo.fetchFeed(
                            context,
                            WebVideoSourceRepository.Source.KAOTIC,
                            nextPage("kaotic", MAX_SOURCE_PAGE)
                    ))
            );
            candidates.removeIf(item -> item == null || !item.isVideo());
            Collections.shuffle(candidates, random);
            int take = Math.min(KAOTIC_ITEMS_PER_BATCH, candidates.size());
            return new ArrayList<>(candidates.subList(0, take));
        } catch (Exception ignored) {
            throw new IllegalStateException("Source acquisition failed", ignored);
        }
    }

    private List<NativeContentItem> loadOnlyHavenBatch(Context context) {
        return loadOnlyHavenBatch(context, ContentPreference.ALL);
    }

    private List<NativeContentItem> loadOnlyHavenBatch(Context context, ContentPreference mode) {
        // Catalog serialization must not hold this mixer's nextPage() monitor across IO.
        synchronized (onlyHavenLock) { return harvestOnlyHavenBatch(context, mode); }
    }

    private List<NativeContentItem> harvestOnlyHavenBatch(Context context, ContentPreference mode) {
        if (onlyHavenDeckReset.getAndSet(false)) {
            onlyHavenCreatorDeck.clear();
            if (onlyHavenCreators.isEmpty()) onlyHavenCatalogAttempted = false;
        }
        ensureOnlyHavenCatalog(context, mode);
        if (onlyHavenCreators.isEmpty()) return new ArrayList<>();

        LinkedHashMap<String, NativeContentItem> combined = new LinkedHashMap<>();
        HashSet<String> usedCreators = new HashSet<>();
        int attempts = Math.min(4, onlyHavenCreators.size());
        // The independent preference buffer holds at most two raw matching clips.
        // Ordinary providers keep their normal fetch/admission path.
        int batchLimit = mode == ContentPreference.ALL ? ONLY_HAVEN_ITEMS_PER_BATCH : 2;
        while (combined.size() < batchLimit && attempts-- > 0) {
            if (Thread.currentThread().isInterrupted()) break;
            if (onlyHavenCreatorDeck.isEmpty()) refillOnlyHavenDeck();
            OnlyHavenRepository.Creator creator = onlyHavenCreatorDeck.pollFirst();
            if (creator == null || creator.url == null || creator.url.isEmpty()) continue;
            if (!usedCreators.add(creator.url)) continue;

            ArrayList<NativeContentItem> candidates = new ArrayList<>();
            try {
                for (NativeContentItem item :
                        ShitTokPoolDiagnostics.fetched(BATCH_ONLY_HAVEN, onlyHaven.fetchCreatorMedia(context, creator, 1, 18))) {
                    if (item == null || !item.isVideo()) continue;
                    candidates.add(onlyHavenClip(item, creator));
                }
            } catch (Exception ignored) {
            }

            Collections.shuffle(candidates, random);
            int perCreator = Math.min(ONLY_HAVEN_ITEMS_PER_CREATOR, candidates.size());
            for (int i = 0; i < perCreator; i++) {
                NativeContentItem item = candidates.get(i);
                if (item.url == null || item.url.isEmpty()) continue;
                combined.putIfAbsent(item.url, item);
                if (mode != ContentPreference.ALL) {
                    if (preferenceMatches.size() > 4096) preferenceMatches.clear();
                    preferenceMatches.add(mode.value + "|" + item.url);
                }
                if (combined.size() >= batchLimit) break;
            }
        }

        return new ArrayList<>(combined.values());
    }

    // Preserve the source relationship at harvest time; playback still uses item.url.
    static NativeContentItem onlyHavenClip(NativeContentItem item, OnlyHavenRepository.Creator creator) {
        return new NativeContentItem(
                item.kind, item.title, item.url, item.imageUrl, item.views,
                creator.url.isEmpty() ? item.uploader : creator.url,
                item.comments, item.description,
                creator.name.isEmpty() ? item.searchQuery : creator.name,
                item.publishedAtMillis, item.aspectRatioHint
        );
    }

    private void ensureOnlyHavenCatalog(Context context, ContentPreference mode) {
        if (onlyHavenCatalogPreference != mode) {
            onlyHavenCreators.clear(); onlyHavenCreatorDeck.clear();
            onlyHavenCatalogAttempted = false;
            onlyHavenCatalogPreference = mode;
            categoryRetryAfter = 0;
        }
        if (onlyHavenCatalogAttempted || (mode != ContentPreference.ALL
                && android.os.SystemClock.elapsedRealtime() < categoryRetryAfter)) return;
        onlyHavenCatalogAttempted = true;
        boolean loaded = false;
        try {
            for (OnlyHavenRepository.Creator creator :
                    onlyHaven.fetchCategoryCreators(context, mode, ONLY_HAVEN_TRENDING_CREATORS)) {
                if (Thread.currentThread().isInterrupted()) break;
                if (creator == null || creator.url == null || creator.url.isEmpty()) continue;
                if (creator.isKnownEmpty()) continue;
                onlyHavenCreators.add(creator);
            }
            loaded = !Thread.currentThread().isInterrupted();
        } catch (Exception ignored) {
        }
        if (onlyHavenCreators.isEmpty() && mode != ContentPreference.ALL) {
            onlyHavenCatalogAttempted = false;
            // Cancellation is navigation, not evidence of a failing provider.
            categoryRetryAfter = Thread.currentThread().isInterrupted() ? 0L
                    : android.os.SystemClock.elapsedRealtime() + 60_000L;
        } else if (!loaded && onlyHavenCreators.isEmpty()) onlyHavenCatalogAttempted = false;
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
            if (!explorationAllowed || catalogLoaded || catalogLoading) return;
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
            for (NativeContentItem item : ShitTokPoolDiagnostics.fetched(BATCH_REGULAR, repository.fetchFeed(context, request.url, request.page))) {
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

                int page = nextPage(url, MAX_SOURCE_PAGE);
                String key = url + "#" + page;
                if (usedSourcePages.add(key)) return new SourceRequest(url, page);
            }

            usedSourcePages.clear();
            if (sourceDeck.isEmpty()) refillDeck();
            String url = sourceDeck.pollFirst();
            if (url == null || url.isEmpty()) return null;
            int page = nextPage(url, MAX_SOURCE_PAGE);
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
