package com.webapp.crazyshit;

import android.content.Context;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.LongSupplier;

/** Bounded session inventories keyed by source mode and gender. OnlyHaven owns category evidence. */
final class ShitTokFilteredInventory {
    static final int HAVEN = 6, FAPELLO = 5, BUNKR = 9, SHOW = 4, BADDIE = 7;
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
        default List<NativeContentItem> baddie(ContentPreference mode,
                List<OnlyHavenRepository.Creator> verified) throws Exception { return Collections.emptyList(); }
        boolean available(int source);
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
        int offset;
        boolean hasMore = true, seeded;
        long accepted, categoryRetryAt;
        int categoryFailures;
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
        select(sources, mode);
        if (!sources.creatorInventory(mode) || !demand) return;
        Session session = session(mode);
        for (int source : new int[]{HAVEN, FAPELLO, SHOW, BADDIE, BUNKR}) {
            boolean eligible = (source != SHOW || sources.showEligible(mode))
                    && (source != BADDIE || sources == ShitTokSourceMode.ONLY_FAP);
            session.pool.setEligibility(source, eligible, backend.available(source));
            if (!eligible) continue;
            if (source == BADDIE && mode != ContentPreference.ALL) {
                synchronized (session) { if (session.creators.isEmpty()) continue; }
            }
            if (source != HAVEN && source != SHOW && source != BADDIE) {
                synchronized (session) {
                    Lane lane = session.lanes.get(source);
                    if (lane.count() == 0 && lane.overflow.isEmpty()) continue;
                }
            }
            if (source != SHOW && source != BADDIE) {
                synchronized (session) {
                    Lane lane = session.lanes.get(source);
                    if (lane.count() > 0 || !lane.overflow.isEmpty() || (source == HAVEN && session.hasMore))
                        session.pool.continueInventory(source);
                }
            }
            session.pool.request(source, true, backend.available(source),
                    () -> source == SHOW ? loadShow(session) : source == BADDIE ? loadBaddie(session)
                            : load(session, source, available), available);
        }
    }

    private List<NativeContentItem> loadBaddie(Session session) throws Exception {
        List<OnlyHavenRepository.Creator> verified;
        synchronized (session) { verified = new ArrayList<>(session.creators.values()); }
        List<NativeContentItem> items = backend.baddie(session.mode, verified);
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        synchronized (session) {
            for (NativeContentItem item : items) if (item != null && item.isVideo())
                session.evidence.put(item.url, BADDIE);
            trimEvidence(session);
        }
        return items;
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
    private List<NativeContentItem> load(Session session, int source, Runnable available) throws Exception {
        Lane lane = session.lanes.get(source);
        synchronized (session) {
            if (!lane.overflow.isEmpty()) return drain(lane.overflow);
        }
        if (source == HAVEN && !session.seeded) {
            List<OnlyHavenRepository.Creator> known = backend.known(session.mode);
            synchronized (session) {
                addCreators(session, known);
                session.seeded = true;
            }
            if (!known.isEmpty()) available.run();
        }
        // Expand only on runway demand, one category page at a time. Slow cross-source
        // lanes cannot grow an unbounded catalog; evict their oldest unfinished cursor.
        boolean expand;
        int offset;
        synchronized (session) {
            expand = source == HAVEN && lane.fresh.isEmpty() && session.hasMore
                    && clock.getAsLong() >= session.categoryRetryAt;
            offset = session.offset;
        }
        if (expand) {
            try {
                OnlyHavenRepository.CategoryPage page = backend.creators(session.mode, offset);
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                synchronized (session) {
                    session.offset += PAGE_SIZE;
                    session.hasMore = page.hasMore;
                    session.categoryFailures = 0; session.categoryRetryAt = 0;
                    addCreators(session, page.creators);
                }
                // Wake cross-source discovery without waiting for Haven media.
                available.run();
            } catch (Exception failure) {
                if (failure instanceof InterruptedException || Thread.currentThread().isInterrupted()) throw failure;
                synchronized (session) {
                    session.categoryFailures = Math.min(5, session.categoryFailures + 1);
                    session.categoryRetryAt = clock.getAsLong() + Math.min(120_000L,
                            5_000L * (1L << (session.categoryFailures - 1)));
                    // Category discovery failing does not invalidate previously verified
                    // creators or their remaining media pages.
                    if (lane.count() == 0) throw failure;
                }
            }
        }
        List<List<NativeContentItem>> pages = new ArrayList<>();
        Exception lastFailure = null;
        // A Haven result contains several verified creators, rather than filling the entire
        // ready runway with the first profile's videos. Cross-source lanes remain single-flight.
        int maximum = source == HAVEN ? 3 : 1;
        Set<String> visited = new HashSet<>();
        for (int attempt = 0; attempt < maximum; attempt++) {
            Cursor cursor;
            synchronized (session) {
                cursor = !lane.fresh.isEmpty() ? lane.fresh.removeFirst() : lane.continuing.pollFirst();
            }
            if (cursor == null) break;
            if (!visited.add(cursor.creator.url)) {
                synchronized (session) { lane.continuing.addLast(cursor); }
                break;
            }
            try { pages.add(fetchCursor(session, lane, source, cursor)); }
            catch (Exception failure) {
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
            session.accepted += lane.overflow.size();
            if (pages.isEmpty() && lastFailure != null) throw lastFailure;
            return drain(lane.overflow);
        }
    }

    private void addCreators(Session session, List<OnlyHavenRepository.Creator> creators) {
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
                target.fresh.addLast(new Cursor(creator));
            }
        }
    }

    private List<NativeContentItem> fetchCursor(Session session, Lane lane, int source, Cursor cursor) throws Exception {
        List<NativeContentItem> loaded;
        try { loaded = backend.media(source, cursor); }
        catch (Exception failure) {
            synchronized (session) {
                if (Thread.currentThread().isInterrupted() || failure instanceof InterruptedException
                        || ++cursor.failures <= 2) lane.fresh.addLast(cursor);
            }
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
        if (item == null || !item.isVideo()) return false;
        if (!sourceMode.creatorInventory(mode)) return true;
        Session session = session(mode);
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
        for (int source : new int[]{HAVEN, FAPELLO, BUNKR, SHOW, BADDIE}) if (session.pool.hasReady(source)) return true;
        return false;
    }

    synchronized List<NativeContentItem> take(ContentPreference mode) {
        return take(mode, item -> true);
    }
    synchronized List<NativeContentItem> take(ContentPreference mode, java.util.function.Predicate<NativeContentItem> room) {
        Session session = session(mode);
        return mode == active && session != null ? session.pool.take(12, room) : Collections.emptyList();
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

    /** Requires the existing identity resolver AND an unambiguous verified creator, never title classification. */
    static OnlyHavenRepository.Creator linkedBaddie(NativeContentItem identity,
            List<OnlyHavenRepository.Creator> verified) {
        if (identity == null || !identity.isCreator()) return null;
        OnlyHavenRepository.Creator match = null;
        for (OnlyHavenRepository.Creator creator : verified) {
            if (!ShitTokFilteredIdentity.strong(creator.name, identity.title)) continue;
            if (match != null && !match.url.equals(creator.url)) return null;
            match = creator;
        }
        return match;
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
        BaddieHubCreatorMatcher matcher = new BaddieHubCreatorMatcher(app);
        java.util.concurrent.atomic.AtomicInteger baddiePage = new java.util.concurrent.atomic.AtomicInteger();
        ShitTokCreatorIdentityCache baddieIdentities = new ShitTokCreatorIdentityCache();
        return new Backend() {
            public OnlyHavenRepository.CategoryPage creators(ContentPreference mode, int offset) throws Exception {
                if (mode == ContentPreference.ALL)
                    return haven.fetchTrendingPage(app, PAGE_SIZE, offset);
                return haven.fetchCategoryPage(app, mode, PAGE_SIZE, offset);
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
            public List<NativeContentItem> baddie(ContentPreference mode,
                    List<OnlyHavenRepository.Creator> verified) throws Exception {
                ArrayList<NativeContentItem> result = new ArrayList<>();
                for (NativeContentItem item : baddie.fetchPortraitFeed(app, 1 + baddiePage.getAndIncrement() % 3)) {
                    String key = CreatorNameMatcher.normalized(item.title);
                    NativeContentItem identity = baddieIdentities.resolve(key, () -> matcher.local(item));
                    if (mode == ContentPreference.ALL) {
                        if (identity == null || (!OnlyHavenRepository.isOnlyHavenUrl(identity.url)
                                && !FapelloRepository.isModelUrl(identity.url))) result.add(item);
                        else result.add(new NativeContentItem(item.kind, item.title, item.url, item.imageUrl,
                                item.views, identity.url, item.comments, item.description, identity.title,
                                item.publishedAtMillis, item.aspectRatioHint));
                        continue;
                    }
                    OnlyHavenRepository.Creator creator = linkedBaddie(identity, verified);
                    if (creator == null) continue;
                    result.add(new NativeContentItem(item.kind, item.title, item.url, item.imageUrl,
                            item.views, creator.url, item.comments, item.description, creator.name,
                            item.publishedAtMillis, item.aspectRatioHint));
                }
                return result;
            }
            public List<OnlyHavenRepository.Creator> known(ContentPreference mode) {
                ArrayList<OnlyHavenRepository.Creator> result = new ArrayList<>();
                for (NativeContentItem item : CreatorCatalog.all(app)) {
                    if (!CreatorCategoryIndex.includes(app, mode, item)) continue;
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
                if (source == BADDIE) return !config.sourceKillSwitchesEnabled || config.baddieHub.enabled;
                return true;
            }
        };
    }
}
