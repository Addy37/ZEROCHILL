package com.webapp.crazyshit;

import android.content.Context;
import android.os.SystemClock;

import java.io.IOException;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/** Incrementally combines Bunkr, Fapello, OnlyHaven and WikiFeet X media for OnlyFap. */
final class BunkrCreatorGalleryRepository {
    interface ProgressListener {
        /** Called on the fetching thread with newly accepted media. */
        void onItems(List<NativeContentItem> items);
    }
    private static final int MAX_SESSIONS = 4;
    private static final int MAX_SEARCH_PAGES = 5;
    private static final int MAX_DUPLICATE_PAGES = 2;
    private static final int CURSOR_VERSION = 3;
    private static final int ALBUMS_PER_BATCH = 4;
    private static final int FAPELLO_MODELS_PER_BATCH = 2;
    private static final int FAPELLO_MODEL_LIMIT = 4;
    private static final int WIKIFEET_CREATORS_PER_BATCH = 2;
    private static final int WIKIFEET_CREATOR_LIMIT = 4;
    private static final int WIKIFEET_PAGE_SIZE = 24;
    private static final int ONLYHAVEN_CREATORS_PER_BATCH = 2;
    private static final int ONLYHAVEN_CREATOR_LIMIT = 4;
    private static final int ONLYHAVEN_PAGE_SIZE = 36;
    private static final int MAX_MERGED_DISCOVERY_QUERIES = 8;
    private static final int MERGED_SOURCE_LIMIT = 2;
    private static final int MAX_FAPELLO_PAGES = 250;
    private static final int MAX_MEDIA_ITEMS = 10_000;
    private static final long FAST_FIRST_PAINT_BUDGET_MS = 2_500L;
    private static final long ALBUM_BATCH_BUDGET_MS = 16_000L;
    private static final ExecutorService ALBUM_IO = Executors.newFixedThreadPool(8);
    private static final LinkedHashMap<String, State> STATES =
            new LinkedHashMap<>(8, 0.75f, true);

    static final class Batch {
        final ArrayList<NativeContentItem> items;
        final boolean endReached;
        final FapelloSourceException fapelloFailure;

        Batch(ArrayList<NativeContentItem> items, boolean endReached) {
            this(items, endReached, null);
        }

        Batch(
                ArrayList<NativeContentItem> items,
                boolean endReached,
                FapelloSourceException fapelloFailure
        ) {
            this.items = items;
            this.endReached = endReached;
            this.fapelloFailure = fapelloFailure;
        }
    }

    void reset(String sessionId, String query) {
        reset(sessionId, query, "", query);
    }

    void reset(String sessionId, String query, String sourceProfileUrl, String creatorName) {
        reset(sessionId, query, sourceProfileUrl, creatorName, null, null, null);
    }

    void reset(
            String sessionId,
            String query,
            String sourceProfileUrl,
            String creatorName,
            List<String> seedNames,
            List<String> seedUrls,
            List<String> seedImages
    ) {
        reset(sessionId, query, sourceProfileUrl, creatorName, seedNames, seedUrls, seedImages, null);
    }

    void reset(String sessionId, String query, String sourceProfileUrl, String creatorName,
               List<String> seedNames, List<String> seedUrls, List<String> seedImages, List<String> aliases) {
        if (sessionId == null || sessionId.trim().isEmpty()) return;
        synchronized (STATES) {
            State state = new State(query);
            seedKnownProfiles(
                    state,
                    sourceProfileUrl,
                    creatorName,
                    seedNames,
                    seedUrls,
                    seedImages,
                    aliases
            );
            STATES.put(sessionId, state);
            trimLocked();
        }
    }

    Batch fetchNext(Context context, String sessionId, String query) throws IOException {
        return fetchNext(context, sessionId, query, "", query);
    }

    Batch fetchNext(Context context, String sessionId, String query,
            String sourceProfileUrl, String creatorName) throws IOException {
        return fetchNext(context, sessionId, query, sourceProfileUrl, creatorName, null);
    }

    Batch fetchNext(Context context, String sessionId, String query,
            String sourceProfileUrl, String creatorName, ProgressListener listener) throws IOException {
        return fetchNext(
                context,
                sessionId,
                query,
                sourceProfileUrl,
                creatorName,
                null,
                null,
                null,
                listener
        );
    }

    Batch fetchNext(
            Context context,
            String sessionId,
            String query,
            String sourceProfileUrl,
            String creatorName,
            List<String> seedNames,
            List<String> seedUrls,
            List<String> seedImages,
            ProgressListener listener
    ) throws IOException {
        return fetchNext(context, sessionId, query, sourceProfileUrl, creatorName,
                seedNames, seedUrls, seedImages, null, listener);
    }

    Batch fetchNext(Context context, String sessionId, String query, String sourceProfileUrl,
                    String creatorName, List<String> seedNames, List<String> seedUrls,
                    List<String> seedImages, List<String> aliases, ProgressListener listener)
            throws IOException {
        PawchiveRepository.Creator paw = PawchiveRepository.profile(sourceProfileUrl, creatorName);
        if (paw != null) {
            State guard = state(context, sessionId, query);
            synchronized (guard) {
                BunkrGallerySessionStore.Snapshot snapshot = BunkrGallerySessionStore.restore(context, sessionId);
                if (snapshot != null && snapshot.endReached) return new Batch(new ArrayList<>(), true);
                int offset = snapshot == null ? 0 : snapshot.currentPage * PawchiveRepository.PAGE_SIZE;
                PawchiveRepository.GalleryPage page = new PawchiveRepository().galleryPage(paw, offset);
                if (listener != null) listener.onItems(page.items);
                try {
                    BunkrGallerySessionStore.recordCreatorBatch(context, sessionId, page.items, !page.hasMore,
                            new org.json.JSONObject().put("query", query).put("source", "pawchive")
                                    .put("profile", sourceProfileUrl).put("offset", offset + PawchiveRepository.PAGE_SIZE));
                } catch (org.json.JSONException error) { throw new IOException("Could not save gallery cursor", error); }
                return new Batch(page.items, !page.hasMore);
            }
        }
        State state = state(context, sessionId, query);
        synchronized (state) {
            seedKnownProfiles(state, sourceProfileUrl, creatorName, seedNames, seedUrls, seedImages, aliases);
            Batch batch = fetchNextLocked(context, state, listener);
            saveCursor(context, sessionId, state, batch);
            return batch;
        }
    }

    private Batch fetchNextLocked(Context context, State state, ProgressListener listener) throws IOException {
        if (state.finished()) return new Batch(new ArrayList<>(), true, state.lastFapelloFailure);

        Context appContext = context.getApplicationContext();

        // All catalog discovery runs concurrently. If we already know a creator profile from
        // the tapped card or a restored cursor, use that known profile as a fast lane while the
        // broader catalogs keep loading in parallel.
        boolean bunkrReady = !state.pending.isEmpty();
        boolean fapelloReady = !state.fapelloPending.isEmpty();
        boolean wikiFeetReady = !state.wikiFeetPending.isEmpty();
        boolean onlyHavenReady = !state.onlyHavenPending.isEmpty();
        Future<IOException> wikiCatalog = ALBUM_IO.submit(() -> loadWikiFeetCatalog(appContext, state));
        Future<IOException> havenCatalog = ALBUM_IO.submit(() -> loadOnlyHavenCatalog(appContext, state));
        Future<IOException> fapelloCatalog = ALBUM_IO.submit(() -> {
            try {
                loadFapelloModelsIfNeeded(appContext, state);
                state.fapelloSearchFailures = 0;
                return null;
            } catch (IOException error) {
                state.lastFapelloFailure = fapelloFailure(error);
                state.fapelloSearchFailures++;
                if (!retryableFapello(state.lastFapelloFailure) ||
                        state.fapelloSearchFailures >= 2) state.fapelloCatalogLoaded = true;
                return error;
            }
        });
        Future<IOException> bunkrCatalog = ALBUM_IO.submit(() -> {
            try {
                loadAlbumsIfNeeded(appContext, state);
                state.bunkrSearchFailures = 0;
                return null;
            } catch (IOException error) {
                state.bunkrSearchFailures++;
                if (state.bunkrSearchFailures >= 2) {
                    state.searchFinished = true;
                    state.nextBunkrAlias = state.discoveryQueries.size();
                }
                return error;
            }
        });

        BatchWork batch = new BatchWork();
        if (listener != null) {
            runFastFirstPaint(context, state, listener, batch,
                    bunkrCatalog, fapelloCatalog, wikiCatalog, havenCatalog,
                    bunkrReady, fapelloReady, wikiFeetReady, onlyHavenReady);
        }

        Set<Future<IOException>> pendingCatalogs = new LinkedHashSet<>(
                Arrays.asList(bunkrCatalog, fapelloCatalog, wikiCatalog, havenCatalog));
        Map<Future<IOException>, IOException> catalogErrors = new LinkedHashMap<>();
        long deadline = 0L;
        try {
            while (!pendingCatalogs.isEmpty() || !batch.requests.isEmpty()) {
                for (Iterator<Future<IOException>> catalogs = pendingCatalogs.iterator();
                        catalogs.hasNext(); ) {
                    Future<IOException> catalog = catalogs.next();
                    if (!catalog.isDone()) continue;
                    catalogErrors.put(catalog, catalogResult(catalog));
                    catalogs.remove();
                    if (catalog == bunkrCatalog) {
                        for (int i = batch.inFlight(MediaSource.BUNKR);
                                i < ALBUMS_PER_BATCH && !state.pending.isEmpty(); i++) {
                            requestAlbum(appContext, state, batch);
                        }
                    } else if (catalog == fapelloCatalog) {
                        for (int i = batch.inFlight(MediaSource.FAPELLO);
                                i < FAPELLO_MODELS_PER_BATCH && !state.fapelloPending.isEmpty(); i++) {
                            requestFapello(context, state, batch);
                        }
                    } else if (catalog == havenCatalog) {
                        for (int i = batch.inFlight(MediaSource.ONLYHAVEN);
                                i < ONLYHAVEN_CREATORS_PER_BATCH && !state.onlyHavenPending.isEmpty(); i++) {
                            requestOnlyHaven(appContext, state, batch);
                        }
                    } else {
                        for (int i = batch.inFlight(MediaSource.WIKIFEET_X);
                                i < WIKIFEET_CREATORS_PER_BATCH && !state.wikiFeetPending.isEmpty(); i++) {
                            requestWikiFeet(appContext, state, batch);
                        }
                    }
                }

                if (pendingCatalogs.isEmpty() && batch.requests.isEmpty()) break;
                // Accept ready pages while other sources are still discovering profiles.
                // The existing media budget starts once all catalogs have returned.
                long wait = 60L;
                if (pendingCatalogs.isEmpty()) {
                    if (deadline == 0L) deadline = SystemClock.elapsedRealtime() + ALBUM_BATCH_BUDGET_MS;
                    wait = deadline - SystemClock.elapsedRealtime();
                    if (wait <= 0L) break;
                }
                Future<MediaPage> future = batch.completed.poll(wait, TimeUnit.MILLISECONDS);
                if (future != null) {
                    applyCompletedPage(state, batch, future, listener);
                } else if (pendingCatalogs.isEmpty()) {
                    break;
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            for (Future<IOException> catalog : pendingCatalogs) catalog.cancel(true);
            for (Future<MediaPage> future : new ArrayList<>(batch.requests.keySet())) {
                if (future.isDone()) {
                    applyCompletedPage(state, batch, future, listener);
                } else {
                    future.cancel(true);
                    batch.requests.remove(future).onFailure.accept(
                            new TimeoutException("Creator media request timed out"));
                }
            }
        }

        ArrayList<NativeContentItem> result = listener == null
                ? interleave(batch.bunkrItems, batch.fapelloItems, batch.onlyHavenItems, batch.wikiFeetItems)
                : batch.progressiveItems;

        if (state.loadedMediaUrls.size() >= MAX_MEDIA_ITEMS) {
            state.pending.clear();
            state.fapelloPending.clear();
            state.wikiFeetPending.clear();
            state.onlyHavenPending.clear();
            state.searchFinished = true;
            state.nextBunkrAlias = state.discoveryQueries.size();
            state.fapelloCatalogLoaded = true;
            state.wikiFeetXCatalogLoaded = true;
            state.onlyHavenCatalogLoaded = true;
        }
        if (result.isEmpty() && state.loadedMediaUrls.isEmpty() && catalogErrors.size() == 4
                && !catalogErrors.containsValue(null)) {
            throw new IOException("OnlyFap sources could not be reached", catalogErrors.get(fapelloCatalog));
        }
        return new Batch(result, state.finished(), state.lastFapelloFailure);
    }

    private void runFastFirstPaint(
            Context context, State state, ProgressListener listener, BatchWork batch,
            Future<IOException> bunkrCatalog, Future<IOException> fapelloCatalog,
            Future<IOException> wikiCatalog, Future<IOException> havenCatalog,
            boolean bunkrReady, boolean fapelloReady, boolean wikiFeetReady, boolean onlyHavenReady
    ) {
        boolean bunkrStarted = false;
        boolean fapelloStarted = false;
        boolean wikiFeetStarted = false;
        boolean onlyHavenStarted = false;

        long deadline = SystemClock.elapsedRealtime() + FAST_FIRST_PAINT_BUDGET_MS;
        while (batch.progressiveItems.isEmpty()) {
            if (!bunkrStarted && (bunkrReady || bunkrCatalog.isDone()) && !state.pending.isEmpty()) {
                requestAlbum(context.getApplicationContext(), state, batch);
                bunkrStarted = true;
            }
            if (!fapelloStarted && (fapelloReady || fapelloCatalog.isDone()) && !state.fapelloPending.isEmpty()) {
                requestFapello(context, state, batch);
                fapelloStarted = true;
            }
            if (!onlyHavenStarted && (onlyHavenReady || havenCatalog.isDone()) && !state.onlyHavenPending.isEmpty()) {
                requestOnlyHaven(context.getApplicationContext(), state, batch);
                onlyHavenStarted = true;
            }
            if (!wikiFeetStarted && (wikiFeetReady || wikiCatalog.isDone()) && !state.wikiFeetPending.isEmpty()) {
                requestWikiFeet(context.getApplicationContext(), state, batch);
                wikiFeetStarted = true;
            }

            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0L || batch.requests.isEmpty()
                    && bunkrCatalog.isDone() && fapelloCatalog.isDone()
                    && wikiCatalog.isDone() && havenCatalog.isDone()) break;
            try {
                Future<MediaPage> future = batch.completed.poll(Math.min(remaining, 60L),
                        TimeUnit.MILLISECONDS);
                if (future != null) applyCompletedPage(state, batch, future, listener);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void requestAlbum(Context context, State state, BatchWork batch) {
        AlbumCursor cursor = state.pending.removeFirst();
        batch.submit(MediaSource.BUNKR, () -> MediaPage.album(new AlbumPage(cursor,
                new BunkrRepository().fetchAlbum(context, cursor.albumUrl, cursor.nextPage), null)),
                error -> retry(state, cursor));
    }

    private void requestFapello(Context context, State state, BatchWork batch) {
        FapelloCursor cursor = state.fapelloPending.removeFirst();
        batch.submit(MediaSource.FAPELLO, () -> MediaPage.fapello(fetchFapelloPage(context, cursor)),
                error -> {
                    state.lastFapelloFailure = fapelloFailure(error);
                    retryFapello(state, cursor);
                });
    }

    private void requestOnlyHaven(Context context, State state, BatchWork batch) {
        OnlyHavenCursor cursor = state.onlyHavenPending.removeFirst();
        batch.submit(MediaSource.ONLYHAVEN, () -> MediaPage.onlyHaven(new OnlyHavenPage(cursor,
                new OnlyHavenRepository().fetchCreatorMedia(
                        context, cursor.creator, cursor.nextPage, ONLYHAVEN_PAGE_SIZE), null)),
                error -> retryOnlyHaven(state, cursor));
    }

    private void requestWikiFeet(Context context, State state, BatchWork batch) {
        WikiFeetCursor cursor = state.wikiFeetPending.removeFirst();
        batch.submit(MediaSource.WIKIFEET_X, () -> MediaPage.wikiFeet(new WikiFeetPage(cursor,
                new WikiFeetRepository().fetchCreatorMedia(
                        context, cursor.creator, cursor.nextPage, WIKIFEET_PAGE_SIZE), null)),
                error -> retryWikiFeet(state, cursor));
    }

    private void applyCompletedPage(State state, BatchWork batch, Future<MediaPage> future,
            ProgressListener listener) {
        PageRequest request = batch.requests.remove(future);
        MediaPage page;
        try {
            page = future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            request.onFailure.accept(interrupted);
            return;
        } catch (Exception error) {
            request.onFailure.accept(error);
            return;
        }

        ArrayList<NativeContentItem> items;
        int before;
        if (page.albumPage != null) {
            items = batch.bunkrItems;
            before = items.size();
            applyPage(state, items, page.albumPage);
        } else if (page.fapelloPage != null) {
            items = batch.fapelloItems;
            before = items.size();
            applyFapelloPage(state, items, page.fapelloPage);
        } else if (page.onlyHavenPage != null) {
            items = batch.onlyHavenItems;
            before = items.size();
            applyOnlyHavenPage(state, items, page.onlyHavenPage);
        } else {
            items = batch.wikiFeetItems;
            before = items.size();
            applyWikiFeetPage(state, items, page.wikiFeetPage);
        }
        if (listener != null && items.size() > before) {
            ArrayList<NativeContentItem> added = new ArrayList<>(items.subList(before, items.size()));
            batch.progressiveItems.addAll(added);
            listener.onItems(added);
        }
    }

    private IOException catalogResult(Future<IOException> request) {
        try {
            return request.get();
        } catch (Exception error) {
            request.cancel(true);
            return new IOException("Creator catalog request failed", error);
        }
    }

    private void loadAlbumsIfNeeded(Context context, State state) throws IOException {
        if (state.discoveryQueries.isEmpty()) {
            state.searchFinished = true;
            return;
        }
        BunkrRepository repository = new BunkrRepository();
        List<String> queries = new ArrayList<>(state.discoveryQueries);
        for (int attempt = 0; state.pending.isEmpty() && attempt < 2; attempt++) {
            if (!state.searchFinished) {
                int page = state.nextSearchPage;
                List<NativeContentItem> albums = repository.searchAlbums(context, queries.get(0), page);
                state.nextSearchPage = page + 1;
                state.searchFinished = albums == null || albums.isEmpty() || page >= MAX_SEARCH_PAGES;
                addBunkrAlbums(state, albums);
            } else if (state.nextBunkrAlias < queries.size()) {
                List<NativeContentItem> albums = repository.searchAlbums(
                        context, queries.get(state.nextBunkrAlias), 1);
                state.nextBunkrAlias++;
                addBunkrAlbums(state, albums);
            } else {
                break;
            }
        }
    }

    private void addBunkrAlbums(State state, List<NativeContentItem> albums) {
        if (albums == null) return;
        for (NativeContentItem album : albums) {
            if (!confirmedAlbum(album, state.discoveryQueries)) continue;
            if (state.albumUrls.add(album.url)) state.pending.addLast(new AlbumCursor(album.url));
        }
    }

    static boolean confirmedAlbum(NativeContentItem album, Iterable<String> identities) {
        if (album == null || !BunkrRepository.isAlbumUrl(album.url)) return false;
        for (String identity : identities) {
            if (CreatorGalleryMatch.albumMatches(album.title, identity)) return true;
        }
        return false;
    }

    private void loadFapelloModelsIfNeeded(Context context, State state) throws IOException {
        if (state.fapelloCatalogLoaded) return;
        FapelloRepository repository = new FapelloRepository();
        int index = 0;
        for (String query : state.discoveryQueries) {
            int limit = index++ == 0 ? FAPELLO_MODEL_LIMIT : MERGED_SOURCE_LIMIT;
            List<FapelloRepository.Model> models = repository.searchGalleryModels(context, query, limit);
            if (models == null) continue;
            int accepted = 0;
            for (FapelloRepository.Model model : models) {
                if (model == null || !FapelloRepository.isModelUrl(model.url)
                        || state.verifiedHandles.contains(CreatorGalleryMatch.handle(query))
                            && CreatorGalleryMatch.fapelloRank(model, query) != CreatorGalleryMatch.HANDLE
                        || !state.fapelloModelUrls.add(model.url)) continue;
                state.fapelloPending.addLast(new FapelloCursor(model));
                if (++accepted >= limit) break;
            }
        }
        state.fapelloCatalogLoaded = true;
    }

    private IOException loadOnlyHavenCatalog(Context context, State state) {
        if (state.onlyHavenCatalogLoaded) return null;
        try {
            OnlyHavenRepository repository = new OnlyHavenRepository();
            int index = 0;
            for (String query : state.discoveryQueries) {
                int limit = index++ == 0 ? ONLYHAVEN_CREATOR_LIMIT : MERGED_SOURCE_LIMIT;
                List<OnlyHavenRepository.Creator> creators =
                        repository.searchGalleryCreators(context, query, limit);
                if (creators == null) continue;
                int accepted = 0;
                for (OnlyHavenRepository.Creator creator : creators) {
                    if (creator == null || !OnlyHavenRepository.isOnlyHavenUrl(creator.url)
                            || state.verifiedHandles.contains(CreatorGalleryMatch.handle(query))
                                && CreatorGalleryMatch.onlyHavenRank(creator, query) != CreatorGalleryMatch.HANDLE
                            || !state.onlyHavenProfileUrls.add(creator.url)) continue;
                    state.onlyHavenPending.addLast(new OnlyHavenCursor(creator));
                    if (++accepted >= limit) break;
                }
            }
            state.onlyHavenCatalogLoaded = true;
            state.onlyHavenSearchFailures = 0;
            return null;
        } catch (IOException error) {
            if (++state.onlyHavenSearchFailures >= 2) state.onlyHavenCatalogLoaded = true;
            return error;
        }
    }

    private IOException loadWikiFeetCatalog(Context context, State state) {
        if (state.wikiFeetXCatalogLoaded) return null;
        try {
            WikiFeetRepository repository = new WikiFeetRepository();
            int index = 0;
            for (String query : state.discoveryQueries) {
                int limit = index++ == 0 ? WIKIFEET_CREATOR_LIMIT : MERGED_SOURCE_LIMIT;
                for (WikiFeetRepository.Creator creator : repository.searchConfirmedCreators(context, query, limit)) {
                    if (creator == null || !WikiFeetRepository.isXProfileUrl(creator.url)
                            || state.verifiedHandles.contains(CreatorGalleryMatch.handle(query))
                                && !CreatorGalleryMatch.exactHandle(query, creator.profileHandle)
                            || !state.wikiFeetProfileUrls.add(creator.url)) continue;
                    state.wikiFeetPending.addLast(new WikiFeetCursor(creator));
                }
            }
            state.wikiFeetXCatalogLoaded = true;
            state.wikiFeetXSearchFailures = 0;
            return null;
        } catch (IOException error) {
            if (++state.wikiFeetXSearchFailures >= 2) state.wikiFeetXCatalogLoaded = true;
            return error;
        }
    }

    private void applyPage(
            State state,
            ArrayList<NativeContentItem> destination,
            AlbumPage page
    ) {
        if (page == null || page.cursor == null) return;
        if (page.error != null) {
            retry(state, page.cursor);
            return;
        }
        if (page.items == null || page.items.isEmpty()) {
            if (page.cursor.nextPage == 1) retry(state, page.cursor);
            return;
        }

        int added = 0;
        for (NativeContentItem item : page.items) {
            if (item == null || item.url == null || item.url.isEmpty() ||
                    !state.loadedMediaUrls.add(item.url)) continue;
            destination.add(item);
            added++;
            if (state.loadedMediaUrls.size() >= MAX_MEDIA_ITEMS) break;
        }
        page.cursor.duplicatePages = duplicatePages(page.cursor.duplicatePages, added);
        if (page.cursor.duplicatePages <= MAX_DUPLICATE_PAGES && state.loadedMediaUrls.size() < MAX_MEDIA_ITEMS) {
            page.cursor.failures = 0;
            page.cursor.nextPage++;
            state.pending.addLast(page.cursor);
        }
    }

    private static int duplicatePages(int previous, int added) {
        return added == 0 ? previous + 1 : 0;
    }

    private void retry(State state, AlbumCursor cursor) {
        if (cursor == null) return;
        cursor.failures++;
        if (cursor.failures <= 1) state.pending.addLast(cursor);
    }

    private FapelloPage fetchFapelloPage(Context context, FapelloCursor cursor) {
        FapelloRepository repository = new FapelloRepository();
        try {
            return new FapelloPage(cursor,
                    repository.fetchGalleryModelMediaPage(context, cursor.model, cursor.nextPage), null);
        } catch (Exception error) {
            if (!cursor.selectedProfile || cursor.nextPage != 1
                    || fapelloFailure(error).reason != FapelloSourceException.Reason.NOT_FOUND
                    || Thread.currentThread().isInterrupted()) {
                return new FapelloPage(cursor, null, error);
            }
            try {
                FapelloRepository.Model replacement = repository.findReplacementProfile(context, cursor.model);
                if (replacement == null) return new FapelloPage(cursor, null, error);
                FapelloCursor recovered = new FapelloCursor(replacement);
                return new FapelloPage(recovered,
                        repository.fetchGalleryModelMediaPage(context, replacement, recovered.nextPage), null);
            } catch (Exception recoveryFailure) {
                return new FapelloPage(cursor, null, error);
            }
        }
    }

    private void applyFapelloPage(
            State state,
            ArrayList<NativeContentItem> destination,
            FapelloPage page
    ) {
        if (page == null || page.cursor == null) return;
        if (page.error != null) {
            state.lastFapelloFailure = fapelloFailure(page.error);
            retryFapello(state, page.cursor);
            return;
        }
        FapelloRepository.MediaPage media = page.media;
        if (media == null) {
            state.lastFapelloFailure = new FapelloSourceException(
                    FapelloSourceException.Reason.MALFORMED,
                    "Fapello returned no media page"
            );
            return;
        }
        state.lastFapelloFailure = null;
        state.fapelloModelUrls.add(page.cursor.model.url);

        int added = 0;
        for (NativeContentItem item : media.items) {
            if (item == null || item.url == null || item.url.isEmpty() ||
                    !state.loadedMediaUrls.add(item.url)) continue;
            destination.add(item);
            added++;
            if (state.loadedMediaUrls.size() >= MAX_MEDIA_ITEMS) break;
        }
        page.cursor.duplicatePages = duplicatePages(page.cursor.duplicatePages, added);
        if (page.cursor.duplicatePages <= MAX_DUPLICATE_PAGES
                && media.hasNext && state.loadedMediaUrls.size() < MAX_MEDIA_ITEMS &&
                page.cursor.nextPage < MAX_FAPELLO_PAGES) {
            page.cursor.failures = 0;
            page.cursor.nextPage++;
            state.fapelloPending.addLast(page.cursor);
        }
    }

    private void retryFapello(State state, FapelloCursor cursor) {
        if (cursor == null) return;
        if (!retryableFapello(state.lastFapelloFailure)) return;
        cursor.failures++;
        if (cursor.failures <= 1) state.fapelloPending.addLast(cursor);
    }

    private boolean retryableFapello(FapelloSourceException failure) {
        return failure == null || failure.reason == FapelloSourceException.Reason.NETWORK ||
                failure.reason == FapelloSourceException.Reason.HTTP;
    }

    private FapelloSourceException fapelloFailure(Exception error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof FapelloSourceException) {
                return (FapelloSourceException) current;
            }
            current = current.getCause();
        }
        return new FapelloSourceException(
                FapelloSourceException.Reason.NETWORK,
                "Fapello request failed",
                error
        );
    }

    private void applyOnlyHavenPage(
            State state,
            ArrayList<NativeContentItem> destination,
            OnlyHavenPage page
    ) {
        if (page == null || page.cursor == null) return;
        if (page.error != null) {
            retryOnlyHaven(state, page.cursor);
            return;
        }
        if (page.items == null || page.items.isEmpty()) return;
        int added = 0;
        for (NativeContentItem item : page.items) {
            if (item == null || item.url == null || item.url.isEmpty() ||
                    !state.loadedMediaUrls.add(item.url)) continue;
            destination.add(item);
            added++;
            if (state.loadedMediaUrls.size() >= MAX_MEDIA_ITEMS) break;
        }
        page.cursor.duplicatePages = duplicatePages(page.cursor.duplicatePages, added);
        if (page.cursor.duplicatePages <= MAX_DUPLICATE_PAGES && page.items.size() >= ONLYHAVEN_PAGE_SIZE &&
                state.loadedMediaUrls.size() < MAX_MEDIA_ITEMS) {
            page.cursor.failures = 0;
            page.cursor.nextPage++;
            state.onlyHavenPending.addLast(page.cursor);
        }
    }

    private void retryOnlyHaven(State state, OnlyHavenCursor cursor) {
        if (cursor == null) return;
        cursor.failures++;
        if (cursor.failures <= 1) state.onlyHavenPending.addLast(cursor);
    }

    private void applyWikiFeetPage(
            State state,
            ArrayList<NativeContentItem> destination,
            WikiFeetPage page
    ) {
        if (page == null || page.cursor == null) return;
        if (page.error != null) {
            retryWikiFeet(state, page.cursor);
            return;
        }
        if (page.items == null || page.items.isEmpty()) return;
        for (NativeContentItem item : page.items) {
            if (item == null || item.url == null || item.url.isEmpty() ||
                    !state.loadedMediaUrls.add(item.url)) continue;
            destination.add(item);
            if (state.loadedMediaUrls.size() >= MAX_MEDIA_ITEMS) break;
        }
        if (page.items.size() >= WIKIFEET_PAGE_SIZE &&
                state.loadedMediaUrls.size() < MAX_MEDIA_ITEMS) {
            page.cursor.failures = 0;
            page.cursor.nextPage++;
            state.wikiFeetPending.addLast(page.cursor);
        }
    }

    private void retryWikiFeet(State state, WikiFeetCursor cursor) {
        if (cursor == null) return;
        cursor.failures++;
        if (cursor.failures <= 1) state.wikiFeetPending.addLast(cursor);
    }

    private ArrayList<NativeContentItem> interleave(
            List<NativeContentItem> bunkr,
            List<NativeContentItem> fapello,
            List<NativeContentItem> onlyHaven,
            List<NativeContentItem> wikiFeet
    ) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        int count = Math.max(Math.max(size(bunkr), size(fapello)),
                Math.max(size(onlyHaven), size(wikiFeet)));
        for (int i = 0; i < count; i++) {
            addAt(result, bunkr, i);
            addAt(result, fapello, i);
            addAt(result, onlyHaven, i);
            addAt(result, wikiFeet, i);
        }
        return result;
    }

    private int size(List<NativeContentItem> items) {
        return items == null ? 0 : items.size();
    }

    private void addAt(
            List<NativeContentItem> output,
            List<NativeContentItem> source,
            int index
    ) {
        if (source != null && index < source.size()) output.add(source.get(index));
    }

    private State state(Context context, String sessionId, String query) throws IOException {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IOException("Creator gallery session was missing");
        }
        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.isEmpty()) throw new IOException("Creator name was missing");
        synchronized (STATES) {
            State current = STATES.get(sessionId);
            if (current == null || !cleanQuery.equalsIgnoreCase(current.query)) {
                current = restoreCursor(context, sessionId, cleanQuery);
                STATES.put(sessionId, current);
                trimLocked();
            }
            return current;
        }
    }

    private void seedKnownProfiles(
            State state, String sourceProfileUrl, String creatorName,
            List<String> seedNames, List<String> seedUrls, List<String> seedImages,
            List<String> aliases
    ) {
        if (state.initialized) return;
        state.initialized = true;
        if (aliases != null) for (String alias : aliases) {
            addDiscoveryQuery(state, alias);
            String handle = CreatorGalleryMatch.handle(alias);
            if (!handle.isEmpty()) state.verifiedHandles.add(handle);
        }
        boolean hasVerifiedAlias = !state.discoveryQueries.isEmpty();
        if (!hasVerifiedAlias || aliasCorroboratesName(state, state.query)) {
            addDiscoveryQuery(state, state.query);
        }
        if (hasVerifiedAlias && aliasCorroboratesName(state, creatorName)) {
            addDiscoveryQuery(state, creatorName);
        }
        if (seedNames != null) {
            for (String name : seedNames) {
                boolean selectedName = name != null && (name.equalsIgnoreCase(state.query)
                        || name.equalsIgnoreCase(creatorName));
                if (!hasVerifiedAlias || !selectedName || aliasCorroboratesName(state, name)) {
                    addDiscoveryQuery(state, name);
                }
            }
        }

        seedKnownProfile(state, sourceProfileUrl, creatorName, "");
        if (seedUrls == null || seedUrls.isEmpty()) return;
        int limit = Math.min(MAX_MERGED_DISCOVERY_QUERIES, seedUrls.size());
        for (int i = 0; i < limit; i++) {
            String url = seedUrls.get(i);
            String name = seedNames != null && i < seedNames.size()
                    ? seedNames.get(i)
                    : creatorName;
            String image = seedImages != null && i < seedImages.size()
                    ? seedImages.get(i)
                    : "";
            seedKnownProfile(state, url, name, image);
        }
    }

    private boolean aliasCorroboratesName(State state, String name) {
        for (String alias : state.discoveryQueries) {
            if (ShitTokFilteredIdentity.strong(alias, name)) return true;
        }
        return false;
    }

    private void addDiscoveryQuery(State state, String value) {
        if (state == null || value == null) return;
        String query = value.trim();
        if (query.length() < 2 || state.discoveryQueries.size() >= MAX_MERGED_DISCOVERY_QUERIES) {
            return;
        }
        for (String existing : state.discoveryQueries) {
            if (existing.equalsIgnoreCase(query) || CreatorGalleryMatch.exactHandle(existing, query)) return;
        }
        state.discoveryQueries.add(query);
    }

    private void seedKnownProfile(
            State state,
            String profileUrl,
            String creatorName,
            String imageUrl
    ) {
        if (state == null || profileUrl == null || profileUrl.trim().isEmpty()) return;
        String url = profileUrl.trim();
        if (FapelloRepository.isModelUrl(url)) {
            seedFapelloProfile(state, url, creatorName);
            return;
        }
        if (OnlyHavenRepository.isOnlyHavenUrl(url)) {
            seedOnlyHavenProfile(state, url, creatorName);
            return;
        }
        if (BunkrRepository.isAlbumUrl(url)) {
            if (state.albumUrls.add(url)) state.pending.addFirst(new AlbumCursor(url));
            return;
        }
        if (!WikiFeetRepository.isXProfileUrl(url) || !state.wikiFeetProfileUrls.add(url)) return;
        String name = creatorName == null || creatorName.trim().isEmpty()
                ? state.query
                : creatorName.trim();
        state.wikiFeetPending.addFirst(new WikiFeetCursor(
                new WikiFeetRepository.Creator(WikiFeetRepository.Site.WIKIFEET_X, name, url, imageUrl, 0)
        ));
        if (state.discoveryQueries.size() <= 1) state.wikiFeetXCatalogLoaded = true;
    }

    private void seedFapelloProfile(State state, String profileUrl, String creatorName) {
        if (state == null || !FapelloRepository.isModelUrl(profileUrl) ||
                !state.fapelloModelUrls.add(profileUrl)) return;
        String name = creatorName == null || creatorName.trim().isEmpty()
                ? state.query
                : creatorName.trim();
        FapelloRepository.Model model = new FapelloRepository.Model(name, profileUrl, "");
        state.fapelloPending.addFirst(new FapelloCursor(model, true));
        String galleryHandle = FapelloRepository.galleryHandle(model);
        if (state.discoveryQueries.size() <= 1 || state.discoveryQueries.stream().allMatch(query ->
                CreatorGalleryMatch.exactHandle(query, model.username)
                        || CreatorGalleryMatch.exactHandle(query, galleryHandle))) {
            state.fapelloCatalogLoaded = true;
        }
    }

    private void seedOnlyHavenProfile(State state, String profileUrl, String creatorName) {
        if (state == null || !OnlyHavenRepository.isOnlyHavenUrl(profileUrl) ||
                !state.onlyHavenProfileUrls.add(profileUrl)) return;
        try {
            java.net.URI parsed = new java.net.URI(profileUrl);
            String path = parsed.getPath();
            String[] raw = path == null ? new String[0] : path.split("/");
            ArrayList<String> parts = new ArrayList<>();
            for (String part : raw) if (part != null && !part.trim().isEmpty()) parts.add(part.trim());
            int marker = parts.indexOf("creators");
            if (marker < 0 || marker + 2 >= parts.size()) {
                state.onlyHavenProfileUrls.remove(profileUrl);
                return;
            }
            String service = parts.get(marker + 1);
            String id = parts.get(marker + 2);
            if (service.isEmpty() || id.isEmpty()) {
                state.onlyHavenProfileUrls.remove(profileUrl);
                return;
            }
            String name = creatorName == null || creatorName.trim().isEmpty()
                    ? state.query
                    : creatorName.trim();
            state.onlyHavenPending.addFirst(new OnlyHavenCursor(
                    new OnlyHavenRepository.Creator(
                            service, id, name, profileUrl, "")));
            if (state.discoveryQueries.size() <= 1) state.onlyHavenCatalogLoaded = true;
        } catch (Exception ignored) {
            state.onlyHavenProfileUrls.remove(profileUrl);
        }
    }

    private void saveCursor(Context context, String id, State state, Batch batch) {
        try {
            org.json.JSONObject json = new org.json.JSONObject().put("query", state.query).put("version", CURSOR_VERSION)
                    .put("next", state.nextSearchPage).put("searchFinished", state.searchFinished)
                    .put("nextBunkrAlias", state.nextBunkrAlias)
                    .put("discoveryQueries", new org.json.JSONArray(state.discoveryQueries))
                    .put("verifiedHandles", new org.json.JSONArray(state.verifiedHandles))
                    .put("fapelloLoaded", state.fapelloCatalogLoaded)
                    .put("wikiFeetXLoaded", state.wikiFeetXCatalogLoaded)
                    .put("onlyHavenLoaded", state.onlyHavenCatalogLoaded)
                    .put("albums", new org.json.JSONArray(state.albumUrls))
                    .put("models", new org.json.JSONArray(state.fapelloModelUrls))
                    .put("wikiFeetProfiles", new org.json.JSONArray(state.wikiFeetProfileUrls))
                    .put("onlyHavenProfiles", new org.json.JSONArray(state.onlyHavenProfileUrls))
                    .put("media", new org.json.JSONArray(state.loadedMediaUrls));
            org.json.JSONArray pending = new org.json.JSONArray();
            for (AlbumCursor cursor : state.pending) pending.put(new org.json.JSONObject()
                    .put("url", cursor.albumUrl).put("next", cursor.nextPage)
                    .put("duplicates", cursor.duplicatePages));
            json.put("pending", pending);
            org.json.JSONArray models = new org.json.JSONArray();
            for (FapelloCursor cursor : state.fapelloPending) models.put(new org.json.JSONObject()
                    .put("name", cursor.model.name).put("url", cursor.model.url)
                    .put("image", cursor.model.imageUrl).put("next", cursor.nextPage)
                    .put("selectedProfile", cursor.selectedProfile)
                    .put("duplicates", cursor.duplicatePages));
            json.put("pendingModels", models);
            org.json.JSONArray wikiFeet = new org.json.JSONArray();
            for (WikiFeetCursor cursor : state.wikiFeetPending) {
                WikiFeetRepository.Creator creator = cursor.creator;
                wikiFeet.put(new org.json.JSONObject()
                        .put("site", creator.site.id).put("name", creator.name)
                        .put("url", creator.url).put("image", creator.imageUrl)
                        .put("photos", creator.photoCount).put("profileId", creator.profileId)
                        .put("next", cursor.nextPage));
            }
            json.put("pendingWikiFeet", wikiFeet);
            org.json.JSONArray onlyHaven = new org.json.JSONArray();
            for (OnlyHavenCursor cursor : state.onlyHavenPending) {
                OnlyHavenRepository.Creator creator = cursor.creator;
                onlyHaven.put(new org.json.JSONObject()
                        .put("service", creator.service).put("id", creator.id)
                        .put("name", creator.name).put("url", creator.url)
                        .put("image", creator.imageUrl).put("next", cursor.nextPage)
                        .put("duplicates", cursor.duplicatePages));
            }
            json.put("pendingOnlyHaven", onlyHaven);
            BunkrGallerySessionStore.recordCreatorBatch(context, id, batch.items, batch.endReached, json);
        } catch (Exception ignored) { }
    }

    private State restoreCursor(Context context, String id, String query) {
        State state = new State(query);
        BunkrGallerySessionStore.Snapshot snapshot = BunkrGallerySessionStore.restore(context, id);
        org.json.JSONObject json = null;
        try { if (snapshot != null && !snapshot.cursor.isEmpty()) json = new org.json.JSONObject(snapshot.cursor); }
        catch (org.json.JSONException ignored) { }
        if (json == null || json.optInt("version") != CURSOR_VERSION
                || !query.equalsIgnoreCase(json.optString("query"))) return state;
        state.initialized = true;
        state.nextSearchPage = Math.max(1, json.optInt("next", 1));
        state.searchFinished = json.optBoolean("searchFinished");
        state.nextBunkrAlias = Math.max(1, json.optInt("nextBunkrAlias", 1));
        org.json.JSONArray discovery = json.optJSONArray("discoveryQueries");
        if (discovery != null) {
            for (int i = 0; i < Math.min(MAX_MERGED_DISCOVERY_QUERIES, discovery.length()); i++) {
                addDiscoveryQuery(state, discovery.optString(i));
            }
        }
        restoreSet(json.optJSONArray("verifiedHandles"), state.verifiedHandles);
        state.fapelloCatalogLoaded = json.optBoolean("fapelloLoaded");
        state.wikiFeetXCatalogLoaded = json.optBoolean("wikiFeetXLoaded");
        state.onlyHavenCatalogLoaded = json.optBoolean("onlyHavenLoaded");
        restoreSet(json.optJSONArray("albums"), state.albumUrls);
        restoreSet(json.optJSONArray("models"), state.fapelloModelUrls);
        restoreSet(json.optJSONArray("wikiFeetProfiles"), state.wikiFeetProfileUrls);
        restoreSet(json.optJSONArray("onlyHavenProfiles"), state.onlyHavenProfileUrls);
        restoreSet(json.optJSONArray("media"), state.loadedMediaUrls);
        org.json.JSONArray pending = json.optJSONArray("pending");
        if (pending != null) for (int i = 0; i < pending.length(); i++) {
            org.json.JSONObject item = pending.optJSONObject(i);
            if (item == null || !BunkrRepository.isAlbumUrl(item.optString("url"))) continue;
            AlbumCursor cursor = new AlbumCursor(item.optString("url"));
            cursor.nextPage = Math.max(1, item.optInt("next", 1));
            cursor.duplicatePages = item.optInt("duplicates");
            state.pending.add(cursor);
        }
        org.json.JSONArray models = json.optJSONArray("pendingModels");
        if (models != null) for (int i = 0; i < models.length(); i++) {
            org.json.JSONObject item = models.optJSONObject(i);
            if (item == null || !FapelloRepository.isModelUrl(item.optString("url"))) continue;
            FapelloCursor cursor = new FapelloCursor(new FapelloRepository.Model(
                    item.optString("name"), item.optString("url"), item.optString("image")),
                    item.optBoolean("selectedProfile"));
            cursor.nextPage = Math.max(1, item.optInt("next", 1));
            cursor.duplicatePages = item.optInt("duplicates");
            state.fapelloPending.add(cursor);
        }
        org.json.JSONArray wikiFeet = json.optJSONArray("pendingWikiFeet");
        if (wikiFeet != null) for (int i = 0; i < wikiFeet.length(); i++) {
            org.json.JSONObject item = wikiFeet.optJSONObject(i);
            if (item == null) continue;
            if (!"wikifeetx".equals(item.optString("site"))
                    || !WikiFeetRepository.isXProfileUrl(item.optString("url"))) continue;
            WikiFeetCursor cursor = new WikiFeetCursor(new WikiFeetRepository.Creator(
                    WikiFeetRepository.Site.WIKIFEET_X, item.optString("name"), item.optString("url"),
                    item.optString("image"), item.optInt("photos"), item.optLong("profileId")));
            cursor.nextPage = Math.max(1, item.optInt("next", 1));
            state.wikiFeetPending.add(cursor);
        }
        org.json.JSONArray onlyHaven = json.optJSONArray("pendingOnlyHaven");
        if (onlyHaven != null) for (int i = 0; i < onlyHaven.length(); i++) {
            org.json.JSONObject item = onlyHaven.optJSONObject(i);
            if (item == null || !OnlyHavenRepository.isOnlyHavenUrl(item.optString("url"))) continue;
            OnlyHavenCursor cursor = new OnlyHavenCursor(new OnlyHavenRepository.Creator(
                    item.optString("service"), item.optString("id"), item.optString("name"),
                    item.optString("url"), item.optString("image")));
            cursor.nextPage = Math.max(1, item.optInt("next", 1));
            cursor.duplicatePages = item.optInt("duplicates");
            state.onlyHavenPending.add(cursor);
        }
        return state;
    }

    private void restoreSet(org.json.JSONArray values, Set<String> output) {
        if (values != null) for (int i = 0; i < Math.min(10000, values.length()); i++) output.add(values.optString(i));
    }

    private void trimLocked() {
        while (STATES.size() > MAX_SESSIONS) {
            String oldest = STATES.keySet().iterator().next();
            STATES.remove(oldest);
        }
    }

    private enum MediaSource { BUNKR, FAPELLO, ONLYHAVEN, WIKIFEET_X }

    private static final class BatchWork {
        final ExecutorCompletionService<MediaPage> completed = new ExecutorCompletionService<>(ALBUM_IO);
        final Map<Future<MediaPage>, PageRequest> requests = new LinkedHashMap<>();
        final ArrayList<NativeContentItem> progressiveItems = new ArrayList<>();
        final ArrayList<NativeContentItem> bunkrItems = new ArrayList<>();
        final ArrayList<NativeContentItem> fapelloItems = new ArrayList<>();
        final ArrayList<NativeContentItem> onlyHavenItems = new ArrayList<>();
        final ArrayList<NativeContentItem> wikiFeetItems = new ArrayList<>();

        void submit(MediaSource source, Callable<MediaPage> fetch, Consumer<Exception> onFailure) {
            requests.put(completed.submit(fetch), new PageRequest(source, onFailure));
        }

        int inFlight(MediaSource source) {
            int count = 0;
            for (PageRequest request : requests.values()) {
                if (request.source == source) count++;
            }
            return count;
        }
    }

    private static final class PageRequest {
        final MediaSource source;
        final Consumer<Exception> onFailure;

        PageRequest(MediaSource source, Consumer<Exception> onFailure) {
            this.source = source;
            this.onFailure = onFailure;
        }
    }

    private static final class MediaPage {
        final AlbumPage albumPage;
        final FapelloPage fapelloPage;
        final OnlyHavenPage onlyHavenPage;
        final WikiFeetPage wikiFeetPage;

        private MediaPage(AlbumPage albumPage, FapelloPage fapelloPage,
                OnlyHavenPage onlyHavenPage, WikiFeetPage wikiFeetPage) {
            this.albumPage = albumPage;
            this.fapelloPage = fapelloPage;
            this.onlyHavenPage = onlyHavenPage;
            this.wikiFeetPage = wikiFeetPage;
        }

        static MediaPage album(AlbumPage page) {
            return new MediaPage(page, null, null, null);
        }

        static MediaPage fapello(FapelloPage page) {
            return new MediaPage(null, page, null, null);
        }

        static MediaPage onlyHaven(OnlyHavenPage page) {
            return new MediaPage(null, null, page, null);
        }

        static MediaPage wikiFeet(WikiFeetPage page) {
            return new MediaPage(null, null, null, page);
        }
    }

    private static final class State {
        final String query;
        final LinkedHashSet<String> discoveryQueries = new LinkedHashSet<>();
        final Set<String> verifiedHandles = new HashSet<>();
        final Deque<AlbumCursor> pending = new ConcurrentLinkedDeque<>();
        final Deque<FapelloCursor> fapelloPending = new ConcurrentLinkedDeque<>();
        final Deque<WikiFeetCursor> wikiFeetPending = new ConcurrentLinkedDeque<>();
        final Deque<OnlyHavenCursor> onlyHavenPending = new ConcurrentLinkedDeque<>();
        final Set<String> albumUrls = new HashSet<>();
        final Set<String> fapelloModelUrls = java.util.concurrent.ConcurrentHashMap.newKeySet();
        final Set<String> wikiFeetProfileUrls = new HashSet<>();
        final Set<String> onlyHavenProfileUrls = new HashSet<>();
        final Set<String> loadedMediaUrls = new HashSet<>();
        int nextSearchPage = 1;
        int nextBunkrAlias = 1;
        int bunkrSearchFailures;
        int fapelloSearchFailures;
        int wikiFeetXSearchFailures;
        int onlyHavenSearchFailures;
        boolean searchFinished;
        boolean initialized;
        boolean fapelloCatalogLoaded;
        boolean wikiFeetXCatalogLoaded;
        boolean onlyHavenCatalogLoaded;
        FapelloSourceException lastFapelloFailure;

        State(String query) {
            this.query = query == null ? "" : query.trim();
        }

        boolean finished() {
            return nextBunkrAlias >= discoveryQueries.size() && searchFinished && pending.isEmpty() &&
                    fapelloCatalogLoaded && fapelloPending.isEmpty() &&
                    wikiFeetXCatalogLoaded &&
                    wikiFeetPending.isEmpty() &&
                    onlyHavenCatalogLoaded && onlyHavenPending.isEmpty();
        }
    }

    private static final class AlbumCursor {
        final String albumUrl;
        int nextPage = 1;
        int failures;
        int duplicatePages;

        AlbumCursor(String albumUrl) {
            this.albumUrl = albumUrl;
        }
    }

    private static final class AlbumPage {
        final AlbumCursor cursor;
        final List<NativeContentItem> items;
        final Exception error;

        AlbumPage(AlbumCursor cursor, List<NativeContentItem> items, Exception error) {
            this.cursor = cursor;
            this.items = items;
            this.error = error;
        }
    }

    private static final class FapelloCursor {
        final FapelloRepository.Model model;
        final boolean selectedProfile;
        int nextPage = 1;
        int failures;
        int duplicatePages;

        FapelloCursor(FapelloRepository.Model model) {
            this(model, false);
        }

        FapelloCursor(FapelloRepository.Model model, boolean selectedProfile) {
            this.model = model;
            this.selectedProfile = selectedProfile;
        }
    }

    private static final class FapelloPage {
        final FapelloCursor cursor;
        final FapelloRepository.MediaPage media;
        final Exception error;

        FapelloPage(
                FapelloCursor cursor,
                FapelloRepository.MediaPage media,
                Exception error
        ) {
            this.cursor = cursor;
            this.media = media;
            this.error = error;
        }
    }

    private static final class OnlyHavenCursor {
        final OnlyHavenRepository.Creator creator;
        int nextPage = 1;
        int failures;
        int duplicatePages;

        OnlyHavenCursor(OnlyHavenRepository.Creator creator) {
            this.creator = creator;
        }
    }

    private static final class OnlyHavenPage {
        final OnlyHavenCursor cursor;
        final List<NativeContentItem> items;
        final Exception error;

        OnlyHavenPage(OnlyHavenCursor cursor, List<NativeContentItem> items, Exception error) {
            this.cursor = cursor;
            this.items = items;
            this.error = error;
        }
    }

    private static final class WikiFeetCursor {
        final WikiFeetRepository.Creator creator;
        int nextPage = 1;
        int failures;

        WikiFeetCursor(WikiFeetRepository.Creator creator) {
            this.creator = creator;
        }
    }

    private static final class WikiFeetPage {
        final WikiFeetCursor cursor;
        final List<NativeContentItem> items;
        final Exception error;

        WikiFeetPage(WikiFeetCursor cursor, List<NativeContentItem> items, Exception error) {
            this.cursor = cursor;
            this.items = items;
            this.error = error;
        }
    }
}
