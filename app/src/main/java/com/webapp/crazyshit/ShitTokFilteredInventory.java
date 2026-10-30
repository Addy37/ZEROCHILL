package com.webapp.crazyshit;

import android.content.Context;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.LongSupplier;

/** Five bounded, session-only inventories. Only category responses create creator work. */
final class ShitTokFilteredInventory {
    static final int HAVEN = 6, FAPELLO = 5, BUNKR = 9, SHOW = 4;
    static final int PAGE_SIZE = 12, MAX_CREATORS = 48, MEDIA_PAGE = 36;
    private final EnumMap<ContentPreference, Session> sessions = new EnumMap<>(ContentPreference.class);
    private final ExecutorService io;
    private final LongSupplier clock;
    private final Backend backend;
    private ContentPreference active = ContentPreference.ALL;

    interface Backend {
        OnlyHavenRepository.CategoryPage creators(ContentPreference mode, int offset) throws Exception;
        List<NativeContentItem> media(int source, Cursor cursor) throws Exception;
        List<NativeContentItem> show() throws Exception;
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
        final ShitTokSourcePool pool = new ShitTokSourcePool(io, clock);
        final Map<Integer, Lane> lanes = new LinkedHashMap<>();
        final LinkedHashMap<String, OnlyHavenRepository.Creator> creators = new LinkedHashMap<>();
        final LinkedHashMap<String, Integer> evidence = new LinkedHashMap<>();
        int offset;
        boolean hasMore = true, seeded;
        long accepted;
        Session(ContentPreference mode) {
            this.mode = mode;
            for (int source : new int[]{HAVEN, FAPELLO, BUNKR}) lanes.put(source, new Lane());
        }
    }

    ShitTokFilteredInventory(ExecutorService io, LongSupplier clock, Backend backend) {
        this.io = io; this.clock = clock; this.backend = backend;
    }

    synchronized void select(ContentPreference mode) {
        if (active == mode) return;
        pause(); active = mode;
        if (mode != ContentPreference.ALL) sessions.computeIfAbsent(mode, Session::new);
    }

    synchronized void pause() {
        for (Session session : sessions.values()) session.pool.pause();
    }

    synchronized void request(ContentPreference mode, boolean demand, Runnable available) {
        select(mode);
        if (mode == ContentPreference.ALL || !demand) return;
        Session session = sessions.get(mode);
        for (int source : new int[]{HAVEN, FAPELLO, BUNKR, SHOW}) {
            boolean eligible = source != SHOW || mode == ContentPreference.WOMEN;
            session.pool.setEligibility(source, eligible, backend.available(source));
            if (!eligible) continue;
            if (source != HAVEN && source != SHOW) {
                synchronized (session) {
                    Lane lane = session.lanes.get(source);
                    if (lane.count() == 0 && lane.overflow.isEmpty()) continue;
                }
            }
            if (source != SHOW) {
                synchronized (session) {
                    Lane lane = session.lanes.get(source);
                    if (lane.count() > 0 || !lane.overflow.isEmpty() || (source == HAVEN && session.hasMore))
                        session.pool.continueInventory(source);
                }
            }
            session.pool.request(source, true, backend.available(source),
                    () -> source == SHOW ? loadShow(session) : load(session, source, available), available);
        }
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
            expand = source == HAVEN && lane.fresh.isEmpty() && session.hasMore;
            offset = session.offset;
        }
        if (expand) {
            OnlyHavenRepository.CategoryPage page = backend.creators(session.mode, offset);
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            synchronized (session) {
                session.offset += PAGE_SIZE;
                session.hasMore = page.hasMore;
                addCreators(session, page.creators);
            }
            // Wake the coordinator so Fapello/Bunkr start without waiting for Haven media.
            available.run();
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
        if (mode == ContentPreference.ALL) return true;
        Session session = sessions.get(mode);
        if (session == null) return false;
        synchronized (session) {
            Integer source = session.evidence.get(item.url);
            return source != null && (source != SHOW || mode == ContentPreference.WOMEN)
                    && backend.available(source);
        }
    }

    synchronized boolean hasReady(ContentPreference mode) {
        Session session = sessions.get(mode);
        if (active != mode || session == null) return false;
        for (int source : new int[]{HAVEN, FAPELLO, BUNKR, SHOW}) if (session.pool.hasReady(source)) return true;
        return false;
    }

    synchronized List<NativeContentItem> take(ContentPreference mode) {
        Session session = sessions.get(mode);
        return mode == active && session != null ? session.pool.take(12) : Collections.emptyList();
    }

    synchronized ShitTokSourcePool.Health health() {
        Session session = sessions.get(active);
        return session == null ? ShitTokSourcePool.Health.IDLE : session.pool.health(HAVEN);
    }

    synchronized void verified(List<NativeContentItem> items) {
        Session session = sessions.get(active);
        if (session != null) for (NativeContentItem item : items) session.pool.verified(item.url);
    }
    synchronized void contributed(NativeContentItem item) {
        Session session = sessions.get(active);
        if (session != null) session.pool.contributed(item.url);
    }
    synchronized String summary() {
        Session session = sessions.get(active);
        if (session == null) return "";
        synchronized (session) {
            return " filtered=" + active.value + " creators=" + session.creators.size()
                    + " offset=" + session.offset + " more=" + session.hasMore
                    + " evidence=" + session.evidence.size() + " accepted=" + session.accepted
                    + " pools=" + session.pool.summary();
        }
    }

    static Backend backend(Context context, ShitShowTapSource show) {
        Context app = context.getApplicationContext();
        OnlyHavenRepository haven = new OnlyHavenRepository();
        FapelloRepository fapello = new FapelloRepository();
        BunkrRepository bunkr = new BunkrRepository();
        return new Backend() {
            public OnlyHavenRepository.CategoryPage creators(ContentPreference mode, int offset) throws Exception {
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
            public List<NativeContentItem> show() throws Exception { return show.takeBatchOrWarm(context, 12); }
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
                return true;
            }
        };
    }
}
