package com.webapp.crazyshit;

import android.content.Context;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.LongSupplier;

/** Bounded session inventories keyed by source mode and gender. OnlyHaven owns category evidence. */
final class ShitTokFilteredInventory {
    static final int HAVEN = 6, FAPELLO = 5, BUNKR = 9, SHOW = 4, BADDIEHUB = 7, EROME = 11;
    static final int PAGE_SIZE = 12, MAX_CREATORS = 48, MEDIA_PAGE = 36;
    private final Map<String, Session> sessions = new LinkedHashMap<>();
    private ShitTokSourceMode sourceMode = ShitTokSourceMode.ALL;
    private final ExecutorService io;
    private final LongSupplier clock;
    private final Backend backend;
    private ContentPreference active = ContentPreference.ALL;

    interface Backend {
        OnlyHavenRepository.CategoryPage creators(ContentPreference mode, int offset) throws Exception;
        List<NativeContentItem> media(int source, Cursor cursor) throws Exception;
        List<NativeContentItem> show() throws Exception;
        boolean available(int source);
        default boolean supportsBaddieHub() { return false; }
        default List<NativeContentItem> baddieHub(ContentPreference mode, int page) throws Exception {
            return Collections.emptyList();
        }
        /** Off unless a backend explicitly opts in, so existing feed tests stay creator-only. */
        default boolean supportsErome() { return false; }
        default EromeRepository.ShitTokBatch erome(ContentPreference mode, int page) throws Exception {
            return EromeRepository.ShitTokBatch.empty(page);
        }
        default List<OnlyHavenRepository.Creator> known(ContentPreference mode) { return Collections.emptyList(); }
    }

    static final class Cursor {
        final OnlyHavenRepository.Creator creator;
        int page = 1, failures;
        FapelloRepository.Model model;
        NativeContentItem album;
        boolean searched, endReached;
        Set<String> lastPage = Collections.emptySet();
        Cursor(OnlyHavenRepository.Creator creator) { this.creator = creator; }
    }

    private static final class Lane {
        final ArrayDeque<Cursor> fresh = new ArrayDeque<>(), continuing = new ArrayDeque<>();
        final ArrayDeque<NativeContentItem> overflow = new ArrayDeque<>();
        int count() { return fresh.size() + continuing.size(); }
    }

    private final class Session {
        final ContentPreference mode;
        final ShitTokSourceMode sources;
        final ShitTokSourcePool pool = new ShitTokSourcePool(io, clock);
        final Map<Integer, Lane> lanes = new LinkedHashMap<>();
        final LinkedHashMap<String, OnlyHavenRepository.Creator> creators = new LinkedHashMap<>();
        final LinkedHashMap<String, Integer> evidence = new LinkedHashMap<>();
        int offset, transMenOffset;
        int baddiePage = 1;
        int eromePage = 1;
        boolean eromeHasMore = true;
        final LinkedHashSet<String> eromeSeen = new LinkedHashSet<>();
        boolean hasMore = true, transMenHasMore = true, nextTransMen, seeded, havenPublished;
        long accepted, categoryRetryAt, transMenRetryAt;
        int categoryFailures, transMenFailures;
        Session(ShitTokSourceMode sources, ContentPreference mode) {
            this.mode = mode; this.sources = sources;
            for (int source : new int[]{HAVEN, FAPELLO, BUNKR}) lanes.put(source, new Lane());
        }
    }

    ShitTokFilteredInventory(ExecutorService io, LongSupplier clock, Backend backend) {
        this.io = io; this.clock = clock; this.backend = backend;
    }

    synchronized void select(ContentPreference mode) { select(ShitTokSourceMode.ALL, mode); }
    synchronized void select(ShitTokSourceMode sources, ContentPreference mode) {
        if (active == mode && sourceMode == sources) return;
        pause(); active = mode; sourceMode = sources;
        if (sources.creatorInventory(mode)) sessions.computeIfAbsent(sources.key(mode),
                ignored -> new Session(sources, mode));
    }
    private Session session(ContentPreference mode) { return sessions.get(sourceMode.key(mode)); }

    synchronized void pause() {
        for (Session session : sessions.values()) session.pool.pause();
    }

    synchronized void request(ContentPreference mode, boolean demand, Runnable available) {
        request(ShitTokSourceMode.ALL, mode, demand, available);
    }
    synchronized void request(ShitTokSourceMode sources, ContentPreference mode, boolean demand, Runnable available) {
        request(sources, mode, demand, available, false);
    }

    synchronized void request(ShitTokSourceMode sources, ContentPreference mode, boolean demand,
                              Runnable available, boolean preparingHaven) {
        select(sources, mode);
        if (!sources.creatorInventory(mode) || !demand) return;
        Session session = session(mode);
        for (int source : new int[]{HAVEN, FAPELLO, SHOW, BUNKR}) {
            boolean eligible = source != SHOW || sources.showEligible(mode);
            session.pool.setEligibility(source, eligible, backend.available(source));
            if (!eligible || source == HAVEN && preparingHaven) continue;
            if (source != SHOW) {
                synchronized (session) {
                    Lane lane = session.lanes.get(source);
                    boolean hasInventory = lane.count() > 0 || !lane.overflow.isEmpty();
                    if (source != HAVEN && !hasInventory) continue;
                    if (hasInventory || session.hasMore
                            || mode == ContentPreference.TRANS_WOMEN && session.transMenHasMore)
                        session.pool.continueInventory(source);
                }
            }
            session.pool.request(source, true, backend.available(source),
                    () -> source == SHOW ? loadShow(session)
                            : load(session, source, available, () -> true, false), available);
        }
        // BaddieHub is confined to the owner-approved categories. Never funnel
        // its unclassified videos into Men, Trans, or the Groups feed.
        boolean baddieEligible = backend.supportsBaddieHub()
                && (mode == ContentPreference.WOMEN || mode == ContentPreference.GROUPS);
        if (baddieEligible) {
            session.pool.setEligibility(BADDIEHUB, true, backend.available(BADDIEHUB));
            session.pool.request(BADDIEHUB, true, backend.available(BADDIEHUB),
                    () -> loadBaddieHub(session), available);
        }
        // Ladies, Guys, and Trans only. Chaos never reaches this loader, and Groups
        // has no Erome category. A slow or failed Erome call stays on its own lane.
        String eromeVersion = EromeRepository.shitTokVersion(mode);
        boolean eromeEligible = sources == ShitTokSourceMode.ALL && backend.supportsErome()
                && !eromeVersion.isEmpty();
        if (eromeEligible) {
            session.pool.setEligibility(EROME, true, backend.available(EROME));
            if (session.eromeHasMore) {
                session.pool.request(EROME, true, backend.available(EROME),
                        () -> loadErome(session), available);
            }
        }
    }

    List<NativeContentItem> starter(ShitTokSourceMode sources, ContentPreference mode,
                                  java.util.function.BooleanSupplier allowed) throws Exception {
        final Session session;
        synchronized (this) {
            session = sessions.computeIfAbsent(sources.key(mode), ignored -> new Session(sources, mode));
        }
        if (!backend.available(HAVEN)) return Collections.emptyList();
        List<NativeContentItem> cached = session.pool.peekCached(HAVEN);
        if (!cached.isEmpty() || !allowed.getAsBoolean()) return cached;
        List<NativeContentItem> items = load(session, HAVEN, () -> {}, allowed, true);
        session.pool.putCached(HAVEN, items);
        return items;
    }

    private List<NativeContentItem> loadBaddieHub(Session session) throws Exception {
        final int page;
        synchronized (session) { page = session.baddiePage; }
        List<NativeContentItem> candidates = backend.baddieHub(session.mode, page);
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        ArrayList<NativeContentItem> accepted = new ArrayList<>();
        synchronized (session) {
            // Do not advance failed pages; source health handles retries/backoff.
            session.baddiePage = page + 1;
            for (NativeContentItem item : candidates) {
                if (item == null || !item.isVideo() || item.url.isEmpty()) continue;
                session.evidence.put(item.url, BADDIEHUB);
                trimEvidence(session);
                if (accepted.size() < ShitTokSourcePool.MAX_READY_PER_SOURCE) accepted.add(item);
            }
        }
        return accepted;
    }

    private List<NativeContentItem> loadErome(Session session) throws Exception {
        final int page;
        synchronized (session) { page = session.eromePage; }
        EromeRepository.ShitTokBatch batch = backend.erome(session.mode, page);
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        ArrayList<NativeContentItem> accepted = new ArrayList<>();
        synchronized (session) {
            if (session.eromePage == page) {
                session.eromePage = Math.max(page, batch.nextPage);
                session.eromeHasMore = batch.hasMore;
            }
            for (NativeContentItem item : batch.items) {
                if (item == null || !item.isVideo() || item.url.isEmpty() || !session.eromeSeen.add(item.url)) continue;
                while (session.eromeSeen.size() > 2048) {
                    session.eromeSeen.remove(session.eromeSeen.iterator().next());
                }
                session.evidence.put(item.url, EROME);
                trimEvidence(session);
                if (accepted.size() < ShitTokSourcePool.MAX_READY_PER_SOURCE) accepted.add(item);
            }
        }
        return accepted;
    }

    private List<NativeContentItem> loadShow(Session session) throws Exception {
        List<NativeContentItem> items = backend.show();
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        synchronized (session) {
            for (NativeContentItem item : items) if (item != null && item.isVideo())
                session.evidence.put(item.url, SHOW);
            trimEvidence(session);
        }
        return items;
    }
    private static void trimEvidence(Session session) {
        while (session.evidence.size() > 4096)
            session.evidence.remove(session.evidence.keySet().iterator().next());
    }
    private List<NativeContentItem> load(Session session, int source, Runnable available,
                                        java.util.function.BooleanSupplier allowed, boolean starter) throws Exception {
        Lane lane = session.lanes.get(source);
        synchronized (session) {
            if (!lane.overflow.isEmpty()) return drain(lane.overflow);
        }
        if (source == HAVEN && !session.seeded) {
            List<OnlyHavenRepository.Creator> known = backend.known(session.mode);
            synchronized (session) {
                addCreators(session, known, false);
                session.seeded = true;
            }
            if (!known.isEmpty()) available.run();
        }
        // Expand only on runway demand, one category page at a time. Slow cross-source
        // lanes cannot grow an unbounded catalog; evict their oldest unfinished cursor.
        final boolean expand;
        final int offset;
        final ContentPreference discoveryMode;
        synchronized (session) {
            boolean trans = session.mode == ContentPreference.TRANS_WOMEN;
            long now = clock.getAsLong();
            boolean womenAvailable = session.hasMore && now >= session.categoryRetryAt;
            boolean menAvailable = trans && session.transMenHasMore && now >= session.transMenRetryAt;
            boolean transMen = menAvailable && (session.nextTransMen || !womenAvailable);
            discoveryMode = transMen ? ContentPreference.TRANS_MEN : session.mode;
            offset = transMen ? session.transMenOffset : session.offset;
            // The first Trans cohort can deliver media before the other cohort's request.
            // Give the other cohort a turn on the next foreground load.
            expand = source == HAVEN && (transMen || womenAvailable)
                    && (lane.fresh.isEmpty() || trans && offset == 0);
        }
        if (expand && allowed.getAsBoolean()) {
            long started = clock.getAsLong();
            try {
                OnlyHavenRepository.CategoryPage page = backend.creators(discoveryMode, offset);
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                synchronized (session) {
                    if (session.mode == ContentPreference.TRANS_WOMEN) {
                        if (discoveryMode == ContentPreference.TRANS_MEN) {
                            session.transMenOffset += PAGE_SIZE / 2;
                            session.transMenHasMore = page.hasMore;
                            session.nextTransMen = false;
                            session.transMenFailures = 0;
                            session.transMenRetryAt = 0;
                        } else {
                            session.offset += PAGE_SIZE / 2;
                            session.hasMore = page.hasMore;
                            session.nextTransMen = true;
                        }
                    } else {
                        session.offset += PAGE_SIZE;
                        session.hasMore = page.hasMore;
                    }
                    if (discoveryMode != ContentPreference.TRANS_MEN
                            || session.mode != ContentPreference.TRANS_WOMEN) {
                        session.categoryFailures = 0;
                        session.categoryRetryAt = 0;
                    }
                    addCreators(session, page.creators,
                            session.mode == ContentPreference.TRANS_WOMEN && offset == 0 && lane.count() > 0);
                }
                available.run();
            } catch (Exception failure) {
                if (failure instanceof InterruptedException || Thread.currentThread().isInterrupted()) throw failure;
                synchronized (session) {
                    boolean mergedTrans = session.mode == ContentPreference.TRANS_WOMEN;
                    if (mergedTrans && discoveryMode == ContentPreference.TRANS_MEN) {
                        session.transMenFailures = Math.min(5, session.transMenFailures + 1);
                        session.transMenRetryAt = clock.getAsLong() + discoveryBackoff(session.transMenFailures);
                        session.nextTransMen = false;
                    } else {
                        session.categoryFailures = Math.min(5, session.categoryFailures + 1);
                        session.categoryRetryAt = clock.getAsLong() + discoveryBackoff(session.categoryFailures);
                        session.nextTransMen = mergedTrans;
                    }
                    if (lane.count() == 0) {
                        if (mergedTrans) throw new ShitTokSourcePool.Deferred();
                        throw failure;
                    }
                }
            } finally {
                long ended = clock.getAsLong();
                ShitTokPoolDiagnostics.recordAt(ended, "preferenceProviderDiscovery",
                        discoveryMode.value + " ms=" + (ended - started));
            }
        }
        List<List<NativeContentItem>> pages = new ArrayList<>();
        Exception lastFailure = null;
        // A Haven result contains several verified creators, rather than filling the entire
        // ready runway with the first profile's videos. Cross-source lanes remain single-flight.
        int maximum = source == HAVEN && !starter ? 3 : 1;
        Set<String> visited = new HashSet<>();
        for (int attempt = 0; attempt < maximum && allowed.getAsBoolean(); attempt++) {
            Cursor cursor;
            synchronized (session) {
                cursor = !lane.fresh.isEmpty() ? lane.fresh.removeFirst() : lane.continuing.pollFirst();
            }
            if (cursor == null) break;
            if (!visited.add(cursor.creator.url)) {
                synchronized (session) { lane.continuing.addLast(cursor); }
                break;
            }
            try {
                List<NativeContentItem> page = fetchCursor(session, lane, source, cursor);
                pages.add(page);
                if (source == HAVEN && !session.havenPublished
                        && page.stream().anyMatch(item -> item != null && item.isVideo() && !item.url.isEmpty()))
                    break;
            } catch (Exception failure) {
                if (failure instanceof InterruptedException || Thread.currentThread().isInterrupted()) throw failure;
                lastFailure = failure;
            }
        }
        synchronized (session) {
            // Interleave creator pages before the normal source/admission ranking.
            int longest = 0;
            for (List<NativeContentItem> page : pages) longest = Math.max(longest, page.size());
            for (int i = 0; i < longest; i++) for (List<NativeContentItem> page : pages) {
                if (i >= page.size()) continue;
                NativeContentItem item = page.get(i);
                if (item == null || !item.isVideo() || item.url.isEmpty()) continue;
                session.evidence.put(item.url, source);
                trimEvidence(session);
                if (lane.overflow.size() < MEDIA_PAGE) lane.overflow.addLast(item);
            }
            if (source == HAVEN && !lane.overflow.isEmpty()) session.havenPublished = true;
            session.accepted += lane.overflow.size();
            if (pages.isEmpty() && lastFailure != null) {
                // One creator's failed page must not park every other verified
                // creator behind provider-wide exponential acquisition backoff.
                if ((source == HAVEN || source == FAPELLO) && lane.count() > 0)
                    throw new ShitTokSourcePool.Deferred();
                throw lastFailure;
            }
            return drain(lane.overflow);
        }
    }

    private static long discoveryBackoff(int failures) {
        return Math.min(120_000L, 5_000L * (1L << (failures - 1)));
    }

    private void addCreators(Session session, List<OnlyHavenRepository.Creator> creators, boolean prioritizeCohort) {
        Set<Lane> prioritized = new HashSet<>();
        for (OnlyHavenRepository.Creator creator : creators) {
            if (creator == null || creator.url.isEmpty() || session.creators.containsKey(creator.url)) continue;
            session.creators.put(creator.url, creator);
            while (session.creators.size() > MAX_CREATORS)
                session.creators.remove(session.creators.keySet().iterator().next());
            for (Lane target : session.lanes.values()) {
                while (target.count() >= MAX_CREATORS) {
                    if (!target.continuing.isEmpty()) target.continuing.removeFirst();
                    else target.fresh.removeFirst();
                }
                Cursor cursor = new Cursor(creator);
                if (prioritizeCohort && prioritized.add(target)) target.fresh.addFirst(cursor);
                else target.fresh.addLast(cursor);
            }
        }
    }

    private List<NativeContentItem> fetchCursor(Session session, Lane lane, int source, Cursor cursor) throws Exception {
        long diagnosticToken = ShitTokPoolDiagnostics.modeToken();
        List<NativeContentItem> loaded;
        try { loaded = backend.media(source, cursor); }
        catch (Exception failure) {
            synchronized (session) {
                if (Thread.currentThread().isInterrupted() || failure instanceof InterruptedException
                        || ++cursor.failures <= 2) lane.fresh.addLast(cursor);
            }
            if (!(failure instanceof InterruptedException) && !Thread.currentThread().isInterrupted())
                ShitTokPoolDiagnostics.modeEvent(diagnosticToken, "creatorPageFailure." + source, 1);
            throw failure;
        }
        if (Thread.currentThread().isInterrupted()) {
            synchronized (session) { lane.fresh.addFirst(cursor); }
            throw new InterruptedException();
        }
        synchronized (session) {
            cursor.failures = 0;
            cursor.page++;
            Set<String> pageUrls = new HashSet<>();
            for (NativeContentItem item : loaded) if (item != null) pageUrls.add(item.url);
            boolean repeated = !pageUrls.isEmpty() && pageUrls.equals(cursor.lastPage);
            cursor.lastPage = pageUrls;
            if (!loaded.isEmpty() && !repeated && !cursor.endReached) lane.continuing.addLast(cursor);
            return repeated ? Collections.emptyList() : loaded;
        }
    }

    private static List<NativeContentItem> drain(ArrayDeque<NativeContentItem> items) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        while (!items.isEmpty() && result.size() < ShitTokSourcePool.MAX_READY_PER_SOURCE)
            result.add(items.removeFirst());
        return result;
    }

    synchronized boolean matches(NativeContentItem item, ContentPreference mode) {
        return matches(item, sourceMode, mode);
    }
    synchronized boolean matches(NativeContentItem item, ShitTokSourceMode sources, ContentPreference mode) {
        if (item == null || !item.isVideo()) return false;
        if (!sources.creatorInventory(mode)) return true;
        Session session = sessions.get(sources.key(mode));
        if (session == null) return false;
        synchronized (session) {
            Integer source = session.evidence.get(item.url);
            return source != null && (source != SHOW || session.sources.showEligible(mode))
                    && backend.available(source);
        }
    }

    synchronized boolean hasReady(ContentPreference mode) {
        Session session = session(mode);
        if (active != mode || session == null) return false;
        for (int source : new int[]{HAVEN, FAPELLO, BUNKR, SHOW, BADDIEHUB, EROME})
            if (session.pool.hasReady(source)) return true;
        return false;
    }

    synchronized List<NativeContentItem> take(ContentPreference mode) {
        return take(mode, item -> true);
    }
    synchronized List<NativeContentItem> take(ContentPreference mode, java.util.function.Predicate<NativeContentItem> room) {
        return take(mode, room, false);
    }
    synchronized List<NativeContentItem> take(ContentPreference mode, java.util.function.Predicate<NativeContentItem> room,
                                               boolean preparingHaven) {
        Session session = session(mode);
        return mode == active && session != null
                ? session.pool.take(12, room, preparingHaven ? HAVEN : -1) : Collections.emptyList();
    }
    synchronized void putBack(ContentPreference mode, NativeContentItem item) {
        Session session = session(mode);
        if (session == null) return;
        synchronized (session) {
            Integer source = session.evidence.get(item.url);
            if (source != null) session.pool.putCached(source, Collections.singletonList(item));
        }
    }

    synchronized List<NativeContentItem> returnShowToAll(int maximum) {
        Session women = sessions.get(ShitTokSourceMode.ALL.key(ContentPreference.WOMEN));
        return women == null ? Collections.emptyList() : women.pool.takeCached(SHOW, maximum);
    }

    synchronized ShitTokSourcePool.Health health() {
        Session session = session(active);
        return session == null ? ShitTokSourcePool.Health.IDLE : session.pool.health(HAVEN);
    }

    synchronized void verified(List<NativeContentItem> items) {
        Session session = session(active);
        if (session != null) for (NativeContentItem item : items) session.pool.verified(item.url);
    }
    synchronized void contributed(NativeContentItem item) {
        Session session = session(active);
        if (session != null) session.pool.contributed(item.url);
    }
    synchronized String summary() {
        Session session = session(active);
        if (session == null) return "";
        synchronized (session) {
            return " filtered=" + sourceMode.key(active) + " creators=" + session.creators.size()
                    + " offset=" + session.offset + " more=" + session.hasMore
                    + " categoryFailures=" + session.categoryFailures
                    + " categoryBackoff=" + (clock.getAsLong() < session.categoryRetryAt)
                    + " evidence=" + session.evidence.size() + " accepted=" + session.accepted
                    + " pools=" + session.pool.summary();
        }
    }

    static Backend backend(Context context, ShitShowTapSource show) {
        return backend(context, show, Collections::emptyList);
    }
    static Backend backend(Context context, ShitShowTapSource show,
                           java.util.function.Supplier<List<NativeContentItem>> cachedShow) {
        Context app = context.getApplicationContext();
        OnlyHavenRepository haven = new OnlyHavenRepository();
        FapelloRepository fapello = new FapelloRepository();
        BunkrRepository bunkr = new BunkrRepository();
        BaddieHubRepository baddie = new BaddieHubRepository();
        EromeRepository erome = new EromeRepository();
        return new Backend() {
            private List<NativeContentItem> groupCategories;

            @Override public boolean supportsBaddieHub() { return true; }

            @Override public boolean supportsErome() { return true; }

            @Override public EromeRepository.ShitTokBatch erome(ContentPreference mode, int page)
                    throws Exception {
                String version = EromeRepository.shitTokVersion(mode);
                if (version.isEmpty()) return EromeRepository.ShitTokBatch.empty(page);
                return erome.videosForShitTok(version, page);
            }

            @Override public List<NativeContentItem> baddieHub(ContentPreference mode, int page)
                    throws Exception {
                if (mode == ContentPreference.WOMEN) return baddie.fetchPortraitFeed(app, page);
                if (mode != ContentPreference.GROUPS) return Collections.emptyList();
                // Groups must originate from an explicitly named BaddieHub group
                // category, rather than being guessed from a random clip title.
                if (groupCategories == null) {
                    ArrayList<NativeContentItem> verified = new ArrayList<>();
                    for (NativeContentItem category : baddie.fetchCategories(app)) {
                        String label = category.title.toLowerCase(java.util.Locale.US)
                                .replace('-', ' ').replace('_', ' ');
                        if (label.contains("group") || label.contains("threesome")
                                || label.contains("orgy") || label.contains("gangbang")) {
                            verified.add(category);
                        }
                    }
                    groupCategories = verified;
                }
                ArrayList<NativeContentItem> result = new ArrayList<>();
                for (NativeContentItem category : groupCategories) {
                    for (NativeContentItem item : baddie.fetchCategory(app, category.url, page)) {
                        if (item != null && item.isVideo()) result.add(item);
                        if (result.size() >= ShitTokSourcePool.MAX_READY_PER_SOURCE) return result;
                    }
                }
                return result;
            }
            public OnlyHavenRepository.CategoryPage creators(ContentPreference mode, int offset) throws Exception {
                if (mode == ContentPreference.ALL)
                    return haven.fetchTrendingPage(app, PAGE_SIZE, offset);
                int limit = mode == ContentPreference.TRANS_WOMEN || mode == ContentPreference.TRANS_MEN
                        ? PAGE_SIZE / 2 : PAGE_SIZE;
                return haven.fetchCategoryPage(app, mode, limit, offset);
            }
            public List<NativeContentItem> media(int source, Cursor cursor) throws Exception {
                if (source == HAVEN) {
                    ArrayList<NativeContentItem> result = new ArrayList<>();
                    for (NativeContentItem item : haven.fetchCreatorMedia(app, cursor.creator, cursor.page, PAGE_SIZE))
                        result.add(ChaosSourceMixer.onlyHavenClip(item, cursor.creator));
                    return result;
                }
                if (source == FAPELLO) {
                    if (!cursor.searched) {
                        cursor.model = ShitTokFilteredIdentity.uniqueFapello(cursor.creator,
                                fapello.searchModels(app, cursor.creator.name, 12));
                        cursor.searched = true;
                    }
                    if (cursor.model == null) return Collections.emptyList();
                    FapelloRepository.MediaPage page = fapello.fetchModelMediaPage(app, cursor.model, cursor.page);
                    cursor.endReached = !page.hasNext;
                    return page.items;
                }
                if (!cursor.searched) {
                    cursor.album = ShitTokFilteredIdentity.uniqueBunkr(cursor.creator,
                            bunkr.searchAlbums(app, cursor.creator.name, 1));
                    cursor.searched = true;
                }
                if (cursor.album == null) return Collections.emptyList();
                ArrayList<NativeContentItem> result = new ArrayList<>();
                for (NativeContentItem item : bunkr.fetchAlbum(app, cursor.album.url, cursor.page)) {
                    // Preserve the Bunkr URL/resolver and attach the verified creator profile
                    // for existing block, gallery navigation and canonical diversity behavior.
                    result.add(new NativeContentItem(item.kind, item.title, item.url, item.imageUrl,
                            item.views, cursor.creator.url, item.comments, item.description,
                            cursor.creator.name, item.publishedAtMillis, item.aspectRatioHint));
                }
                return result;
            }
            public List<NativeContentItem> show() throws Exception {
                List<NativeContentItem> cached = cachedShow.get();
                if (!cached.isEmpty()) ShitTokPoolDiagnostics.modeEvent(ShitTokPoolDiagnostics.modeToken(), "showCacheRaw", cached.size());
                return cached.isEmpty() ? show.takeBatchOrWarm(app, 12) : cached;
            }
            public List<OnlyHavenRepository.Creator> known(ContentPreference mode) {
                ArrayList<OnlyHavenRepository.Creator> result = new ArrayList<>();
                for (NativeContentItem item : CreatorCatalog.all(app)) {
                    if (!CreatorCategoryIndex.includes(app, mode, item)
                            && !(mode == ContentPreference.TRANS_WOMEN
                                && CreatorCategoryIndex.includes(app, ContentPreference.TRANS_MEN, item)))
                        continue;
                    try {
                        String path = new java.net.URI(item.url).getPath();
                        if (path == null || !path.matches("/creators/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+/?")) continue;
                        String[] parts = path.split("/");
                        result.add(new OnlyHavenRepository.Creator(parts[2], parts[3], item.title, item.url, item.imageUrl));
                        if (result.size() == PAGE_SIZE) break;
                    } catch (java.net.URISyntaxException invalid) { /* Not a canonical profile. */ }
                }
                return result;
            }
            public boolean available(int source) {
                SourceConfig config = RemoteSourceConfigManager.snapshotOrNull();
                if (config == null) return true;
                if (source == HAVEN) return config.onlyHaven.enabled;
                if (source == FAPELLO) return !config.sourceKillSwitchesEnabled || config.fapello.enabled;
                if (source == BUNKR) return !config.sourceKillSwitchesEnabled || config.bunkr.enabled;
                if (source == BADDIEHUB) return !config.sourceKillSwitchesEnabled || config.baddieHub.enabled;
                return true;
            }
        };
    }
}
