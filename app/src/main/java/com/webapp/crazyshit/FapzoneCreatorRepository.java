package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds Fapzone creator shelves whose profiles open the unified multi-source gallery. */
final class FapzoneCreatorRepository {
    static final int MODE_TOP_50 = 0;
    static final int MODE_NEW = 1;
    static final int MODE_HOT = 2;
    static final int MODE_POPULAR = 3;

    private static final int LIVE_ITEMS = 30;
    static final int DISCOVER_ITEMS = 16;
    static final int DISCOVER_CANDIDATES = 48;
    static final int DISCOVER_START_PAGE = 2;
    static final int DISCOVER_END_PAGE = 3;
    static final int LIVE_PAGES = 4;
    static final int DISCOVER_REFILL_START_PAGE = LIVE_PAGES + 1;
    private static final int DISCOVER_REFILL_PAGES = 4;
    private static final int PROGRESS_STEP = 6;
    private static final long CACHE_AGE_MS = TimeUnit.HOURS.toMillis(6);
    private static final long FETCH_BUDGET_MS = 45_000L;
    private static final Pattern FILE_COUNT = Pattern.compile("(?i)([0-9,]+)\\s+files?");

    interface ProgressListener {
        void onProgress(List<NativeContentItem> items);
    }

    private final OnlyHavenRepository onlyHaven = new OnlyHavenRepository();
    private final FapelloRepository fapello = new FapelloRepository();
    private final BunkrRepository bunkr = new BunkrRepository();

    List<NativeContentItem> fetch(
            Context context,
            int mode,
            ProgressListener listener
    ) throws IOException {
        if (mode == MODE_TOP_50) {
            return fetchDiscover(context, listener);
        }
        String listing = listingFor(mode);
        Context appContext = context.getApplicationContext();
        List<NativeContentItem> fresh = readCache(appContext, mode, false);
        if (fresh.size() >= LIVE_ITEMS) return fresh;
        List<NativeContentItem> stale = fresh.isEmpty()
                ? readCache(appContext, mode, true)
                : fresh;
        if (listener != null && !stale.isEmpty()) {
            listener.onProgress(new ArrayList<>(stale));
        }

        LinkedHashMap<String, FapelloRepository.Model> models = new LinkedHashMap<>();
        IOException listingError = null;
        for (int page = 1; page <= LIVE_PAGES && models.size() < LIVE_ITEMS; page++) {
            try {
                List<FapelloRepository.Model> pageModels =
                        fapello.fetchModelListing(context, listing, page);
                if (pageModels == null || pageModels.isEmpty()) break;
                for (FapelloRepository.Model model : pageModels) {
                    if (model == null || !FapelloRepository.isModelUrl(model.url)) continue;
                    models.putIfAbsent(model.url, model);
                    if (models.size() >= LIVE_ITEMS) break;
                }
            } catch (IOException error) {
                listingError = error;
                if (models.isEmpty()) continue;
                break;
            }
        }
        if (models.isEmpty()) {
            if (!stale.isEmpty()) return stale;
            throw listingError == null
                    ? new IOException("No Fapello creators were available")
                    : listingError;
        }

        ArrayList<FapelloRepository.Model> ordered = new ArrayList<>(models.values());
        ExecutorService workers = Executors.newFixedThreadPool(10);
        ExecutorCompletionService<ResolvedCreator> completed =
                new ExecutorCompletionService<>(workers);
        for (int index = 0; index < ordered.size(); index++) {
            int rank = index;
            FapelloRepository.Model model = ordered.get(index);
            completed.submit(() -> resolve(context, rank, model));
        }

        ArrayList<ResolvedCreator> resolved = new ArrayList<>();
        int lastPublished = stale.size();
        long deadline = SystemClock.elapsedRealtime() + FETCH_BUDGET_MS;
        try {
            for (int i = 0; i < ordered.size(); i++) {
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0L) break;
                Future<ResolvedCreator> future = completed.poll(remaining, TimeUnit.MILLISECONDS);
                if (future == null) break;
                try {
                    ResolvedCreator creator = future.get();
                    if (creator == null) continue;
                    resolved.add(creator);
                    if (listener != null) {
                        ArrayList<NativeContentItem> progress = buildItems(resolved);
                        int milestone = Math.min(
                                LIVE_ITEMS,
                                ((lastPublished / PROGRESS_STEP) + 1) * PROGRESS_STEP
                        );
                        if (progress.size() >= milestone) {
                            lastPublished = progress.size();
                            listener.onProgress(progress);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            workers.shutdownNow();
        }

        ArrayList<NativeContentItem> result = mergeWarm(buildItems(resolved), stale);
        if (listener != null && !result.isEmpty()) {
            listener.onProgress(new ArrayList<>(result));
        }
        if (!result.isEmpty()) {
            writeCache(appContext, mode, result);
            return result;
        }
        if (!stale.isEmpty()) return stale;
        throw new IOException("No OnlyFap creators were available");
    }

    private List<NativeContentItem> fetchDiscover(
            Context context,
            ProgressListener listener
    ) throws IOException {
        Context appContext = context.getApplicationContext();

        // Discover intentionally refreshes on each load so the set changes naturally.
        // Keep the last successful set only as an offline/network-failure fallback.
        List<NativeContentItem> stale = readCache(appContext, MODE_TOP_50, true);

        LinkedHashMap<String, FapelloRepository.Model> models = new LinkedHashMap<>();
        IOException lastError = null;
        String[] listings = {
                FapelloRepository.LIST_NEW,
                FapelloRepository.LIST_HOT,
                FapelloRepository.LIST_POPULAR
        };

        for (String listing : listings) {
            for (int page = DISCOVER_START_PAGE; page <= DISCOVER_END_PAGE; page++) {
                try {
                    List<FapelloRepository.Model> pageModels =
                            fapello.fetchModelListing(context, listing, page);
                    if (pageModels == null || pageModels.isEmpty()) break;
                    for (FapelloRepository.Model model : pageModels) {
                        if (model == null || !FapelloRepository.isModelUrl(model.url)) continue;
                        String imageUrl = clean(model.imageUrl);
                        if (imageUrl.isEmpty() ||
                                imageUrl.toLowerCase(Locale.US).contains("load.svg")) {
                            continue;
                        }
                        models.putIfAbsent(model.url, model);
                    }
                } catch (IOException error) {
                    lastError = error;
                    break;
                }
            }
        }

        if (models.isEmpty()) {
            if (!stale.isEmpty()) return stale;
            throw lastError == null
                    ? new IOException("No Fapello creators were available for Discover")
                    : lastError;
        }

        ArrayList<FapelloRepository.Model> pool = new ArrayList<>(models.values());
        java.util.Collections.shuffle(pool);

        ArrayList<NativeContentItem> result = new ArrayList<>();
        for (FapelloRepository.Model model : pool) {
            result.add(new NativeContentItem(
                    NativeContentItem.KIND_CREATOR,
                    model.name,
                    model.url,
                    model.imageUrl,
                    String.valueOf(result.size() + 1),
                    model.url,
                    "",
                    "Random Fapello discovery pick",
                    model.name
            ));
            if (result.size() >= DISCOVER_CANDIDATES) break;
        }

        if (result.isEmpty()) {
            if (!stale.isEmpty()) return stale;
            throw new IOException("No Fapello creators had usable Discover artwork");
        }

        // Cache only after final shelf exclusions and refill, so an overlapping warm pool
        // cannot overwrite the previous useful offline selection.
        if (listener != null) listener.onProgress(new ArrayList<>(result));
        return result;
    }

    interface DiscoverPageLoader {
        List<FapelloRepository.Model> load(String listing, int page) throws IOException;
    }

    List<NativeContentItem> completeDiscover(
            Context context, List<NativeContentItem> candidates,
            List<NativeContentItem> newItems, List<NativeContentItem> hotItems,
            List<NativeContentItem> popularItems
    ) {
        return completeDiscover(context, candidates, newItems, hotItems, popularItems,
                (listing, page) -> fapello.fetchModelListing(context, listing, page));
    }

    List<NativeContentItem> completeDiscover(
            Context context, List<NativeContentItem> candidates,
            List<NativeContentItem> newItems, List<NativeContentItem> hotItems,
            List<NativeContentItem> popularItems, DiscoverPageLoader loader
    ) {
        Context appContext = context.getApplicationContext();
        ArrayList<NativeContentItem> result = refillDiscoverCandidates(
                candidates, readCache(appContext, MODE_TOP_50, true),
                newItems, hotItems, popularItems, loader
        );
        if (!Thread.currentThread().isInterrupted() && !result.isEmpty()) {
            writeCache(appContext, MODE_TOP_50, result);
        }
        return result;
    }

    static ArrayList<NativeContentItem> refillDiscoverCandidates(
            List<NativeContentItem> candidates, List<NativeContentItem> cached,
            List<NativeContentItem> newItems, List<NativeContentItem> hotItems,
            List<NativeContentItem> popularItems, DiscoverPageLoader loader
    ) {
        ArrayList<NativeContentItem> pool = new ArrayList<>();
        if (candidates != null) pool.addAll(candidates);
        String[] listings = {FapelloRepository.LIST_NEW, FapelloRepository.LIST_HOT,
                FapelloRepository.LIST_POPULAR};
        boolean[] exhausted = new boolean[listings.length];
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(FETCH_BUDGET_MS);
        for (int page = DISCOVER_REFILL_START_PAGE;
                page < DISCOVER_REFILL_START_PAGE + DISCOVER_REFILL_PAGES; page++) {
            for (int index = 0; index < listings.length; index++) {
                if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline ||
                        selectDiscoverItems(pool, newItems, hotItems, popularItems).size()
                                >= DISCOVER_ITEMS) break;
                if (exhausted[index]) continue;
                try {
                    List<FapelloRepository.Model> models = loader.load(listings[index], page);
                    if (models == null || models.isEmpty()) {
                        exhausted[index] = true;
                        continue;
                    }
                    ArrayList<NativeContentItem> pageItems = new ArrayList<>();
                    for (FapelloRepository.Model model : models) {
                        if (model == null || !FapelloRepository.isModelUrl(model.url) ||
                                clean(model.imageUrl).isEmpty() ||
                                model.imageUrl.toLowerCase(Locale.US).contains("load.svg")) continue;
                        pageItems.add(new NativeContentItem(NativeContentItem.KIND_CREATOR,
                                model.name, model.url, model.imageUrl, "", model.url, "",
                                "Random Fapello discovery pick", model.name));
                    }
                    java.util.Collections.shuffle(pageItems);
                    pool.addAll(pageItems);
                } catch (IOException error) {
                    exhausted[index] = true;
                }
            }
        }
        // Fresh candidates take priority. A partial/failed refresh retains useful cached
        // creators without relaxing duplicate protection or clearing a good cache.
        if (cached != null) pool.addAll(cached);
        return selectDiscoverCandidates(pool, newItems, hotItems, popularItems,
                DISCOVER_CANDIDATES);
    }

    static ArrayList<NativeContentItem> selectDiscoverItems(
            List<NativeContentItem> candidates,
            List<NativeContentItem> newItems,
            List<NativeContentItem> hotItems,
            List<NativeContentItem> popularItems
    ) {
        return selectDiscoverCandidates(candidates, newItems, hotItems, popularItems,
                DISCOVER_ITEMS);
    }

    private static ArrayList<NativeContentItem> selectDiscoverCandidates(
            List<NativeContentItem> candidates, List<NativeContentItem> newItems,
            List<NativeContentItem> hotItems, List<NativeContentItem> popularItems, int limit
    ) {
        HashSet<String> excluded = new HashSet<>();
        addCreatorKeys(excluded, newItems);
        addCreatorKeys(excluded, hotItems);
        addCreatorKeys(excluded, popularItems);

        ArrayList<NativeContentItem> result = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        if (candidates == null) return result;

        for (NativeContentItem item : candidates) {
            if (item == null || matchesCreatorKeys(excluded, item) ||
                    matchesCreatorKeys(seen, item)) continue;
            String identity = primaryCreatorKey(item);
            if (identity.isEmpty()) continue;
            addCreatorKeys(seen, java.util.Collections.singletonList(item));
            result.add(item);
            if (result.size() >= limit) break;
        }
        return result;
    }

    private static void addCreatorKeys(Set<String> keys, List<NativeContentItem> items) {
        if (keys == null || items == null) return;
        for (NativeContentItem item : items) {
            if (item == null) continue;
            String urlKey = creatorUrlKey(item);
            String nameKey = creatorNameKey(item);
            if (!urlKey.isEmpty()) keys.add(urlKey);
            if (!nameKey.isEmpty()) keys.add(nameKey);
        }
    }

    private static boolean matchesCreatorKeys(Set<String> keys, NativeContentItem item) {
        if (keys == null || keys.isEmpty() || item == null) return false;
        String urlKey = creatorUrlKey(item);
        if (!urlKey.isEmpty() && keys.contains(urlKey)) return true;
        String nameKey = creatorNameKey(item);
        return !nameKey.isEmpty() && keys.contains(nameKey);
    }

    private static String primaryCreatorKey(NativeContentItem item) {
        String urlKey = creatorUrlKey(item);
        return urlKey.isEmpty() ? creatorNameKey(item) : urlKey;
    }

    private static String creatorUrlKey(NativeContentItem item) {
        String url = clean(item == null ? "" : item.url).toLowerCase(Locale.US);
        if (url.isEmpty()) return "";
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return "url:" + url;
    }

    private static String creatorNameKey(NativeContentItem item) {
        if (item == null) return "";
        String value = clean(item.searchQuery);
        if (value.isEmpty()) value = clean(item.title);
        String compact = compact(value);
        return compact.isEmpty() ? "" : "name:" + compact;
    }

    static String titleFor(int mode) {
        if (mode == MODE_NEW) return "New creators";
        if (mode == MODE_HOT) return "Hot creators";
        if (mode == MODE_POPULAR) return "Popular creators";
        return "Discover";
    }

    static String hintFor(int mode) {
        if (mode == MODE_NEW) return "Recently added on Fapello • one combined gallery";
        if (mode == MODE_HOT) return "Hot on Fapello • matched across OnlyFap";
        if (mode == MODE_POPULAR) return "Popular on Fapello • matched across OnlyFap";
        return "Random picks from Fapello · fresh each visit";
    }

    static String badgeFor(int mode) {
        if (mode == MODE_NEW) return "NEW";
        if (mode == MODE_HOT) return "HOT";
        if (mode == MODE_POPULAR) return "POP";
        return "MIX";
    }

    private ResolvedCreator resolveTrending(
            Context context,
            int rank,
            OnlyHavenRepository.Creator creator
    ) {
        FapelloRepository.Model model = null;
        try {
            model = chooseFapelloModel(
                    creator.name,
                    fapello.searchConfirmedModels(context, creator.name, 6)
            );
        } catch (Exception ignored) {
        }

        NativeContentItem album = null;
        try {
            album = chooseAlbum(creator.name, bunkr.searchAlbums(context, creator.name, 1));
        } catch (Exception ignored) {
        }

        String fapelloPreview = model == null ? "" : clean(model.imageUrl);
        if (model != null && fapelloPreview.isEmpty()) {
            try {
                fapelloPreview = chooseFapelloPreview(fapello.fetchModelMedia(context, model, 1));
            } catch (Exception ignored) {
            }
        }

        String bunkrPreview = album == null ? "" : clean(album.imageUrl);
        String imageUrl = chooseThumbnail(fapelloPreview, bunkrPreview);
        String cardUrl;
        String imageReferer;
        if (!fapelloPreview.isEmpty() && imageUrl.equals(fapelloPreview) && model != null) {
            cardUrl = model.url;
            imageReferer = model.url;
        } else if (album != null) {
            cardUrl = album.url;
            imageReferer = album.url;
        } else if (model != null) {
            cardUrl = model.url;
            imageReferer = model.url;
        } else {
            cardUrl = creator.url;
            imageReferer = creator.url;
        }

        // Some OnlyHaven display names do not map cleanly to Fapello/Bunkr names.
        // If the normal creator-card path has no artwork, use the exact creator media
        // feed that already powers the unified gallery and cache that preview on the card.
        if (imageUrl.isEmpty()) {
            try {
                List<NativeContentItem> galleryMedia =
                        onlyHaven.fetchCreatorMedia(context, creator, 1, 8);
                String galleryPreview = chooseGalleryPreview(galleryMedia);
                if (!galleryPreview.isEmpty()) {
                    imageUrl = galleryPreview;
                    cardUrl = creator.url;
                    imageReferer = creator.url;
                }
            } catch (Exception ignored) {
            }
        }

        // OnlyHaven decides the live rank. Artwork first uses the same Fapello/Bunkr
        // resolver as New, Hot and Popular, then falls back to the gallery's own media.
        NativeContentItem item = new NativeContentItem(
                NativeContentItem.KIND_CREATOR,
                creator.name,
                cardUrl,
                imageUrl,
                String.valueOf(rank + 1),
                imageReferer,
                "",
                trendingDescription(creator),
                creator.name
        );
        return new ResolvedCreator(rank, item);
    }

    private FapelloRepository.Model chooseFapelloModel(
            String creatorName,
            List<FapelloRepository.Model> models
    ) {
        if (models == null || models.isEmpty()) return null;
        FapelloRepository.Model best = null;
        int bestRank = Integer.MAX_VALUE;
        for (FapelloRepository.Model model : models) {
            if (model == null || !FapelloRepository.isModelUrl(model.url)) continue;
            int match = CreatorNameMatcher.rank(model.name, creatorName);
            if (match < bestRank) {
                best = model;
                bestRank = match;
            }
        }
        return bestRank == Integer.MAX_VALUE ? null : best;
    }

    private String trendingDescription(OnlyHavenRepository.Creator creator) {
        StringBuilder description = new StringBuilder("OnlyHaven");
        String service = serviceLabel(creator.service);
        if (!service.isEmpty()) description.append(" · ").append(service);
        if (creator.postCount >= 0) {
            description.append(" · ")
                    .append(String.format(Locale.US, "%,d", creator.postCount))
                    .append(creator.postCount == 1 ? " post" : " posts");
        }
        return description.toString();
    }

    private ResolvedCreator resolve(
            Context context,
            int rank,
            FapelloRepository.Model model
    ) {
        NativeContentItem album = null;
        try {
            album = chooseAlbum(model.name, bunkr.searchAlbums(context, model.name, 1));
        } catch (Exception ignored) {
        }

        String fapelloPreview = clean(model.imageUrl);
        if (fapelloPreview.isEmpty()) {
            try {
                fapelloPreview = chooseFapelloPreview(fapello.fetchModelMedia(context, model, 1));
            } catch (Exception ignored) {
            }
        }

        // Keep the exact Fapello profile on the card. The creator gallery uses it directly
        // instead of having to rediscover or guess the profile slug from the display name.
        String cardUrl = model.url;
        String bunkrPreview = album == null ? "" : clean(album.imageUrl);
        String imageUrl = chooseThumbnail(fapelloPreview, bunkrPreview);
        String imageReferer = imageUrl.equals(fapelloPreview)
                ? model.url
                : album == null ? model.url : album.url;
        NativeContentItem item = new NativeContentItem(
                NativeContentItem.KIND_CREATOR,
                model.name,
                cardUrl,
                imageUrl,
                String.valueOf(rank + 1),
                imageReferer,
                "",
                "Bunkr + Fapello + OnlyHaven + WikiFeet + WikiFeet X",
                model.name
        );
        return new ResolvedCreator(rank, item);
    }

    private NativeContentItem chooseAlbum(String creatorName, List<NativeContentItem> albums) {
        if (albums == null || albums.isEmpty()) return null;
        String creator = compact(creatorName);
        NativeContentItem best = null;
        long bestScore = Long.MIN_VALUE;
        for (NativeContentItem album : albums) {
            if (album == null || !BunkrRepository.isAlbumUrl(album.url)) continue;
            String title = compact(album.title);
            int nameScore = title.equals(creator) ? 3 : title.contains(creator) ? 2 : -1;
            if (creator.isEmpty() || nameScore < 0) continue;
            long score = (nameScore * 1_000_000L) + fileCount(album.description);
            if (best == null || score > bestScore) {
                best = album;
                bestScore = score;
            }
        }
        return best;
    }

    private String chooseFapelloPreview(List<NativeContentItem> media) {
        if (media == null) return "";
        for (NativeContentItem item : media) {
            if (item != null && item.isVideo() && !clean(item.imageUrl).isEmpty()) {
                return clean(item.imageUrl);
            }
        }
        for (NativeContentItem item : media) {
            if (item != null && !clean(item.imageUrl).isEmpty()) return clean(item.imageUrl);
        }
        return "";
    }

    static String chooseGalleryPreview(List<NativeContentItem> media) {
        if (media == null) return "";
        for (NativeContentItem item : media) {
            if (item == null) continue;
            String preview = clean(item.imageUrl);
            if (!preview.isEmpty()) return preview;
            if (item.isImage()) {
                String direct = clean(item.url);
                if (!direct.isEmpty()) return direct;
            }
        }
        return "";
    }

    private String chooseThumbnail(String fapelloUrl, String bunkrUrl) {
        int fapelloScore = thumbnailScore(fapelloUrl, true);
        int bunkrScore = thumbnailScore(bunkrUrl, false);
        return fapelloScore >= bunkrScore ? clean(fapelloUrl) : clean(bunkrUrl);
    }

    private int thumbnailScore(String value, boolean fapelloSource) {
        String url = clean(value);
        if (url.isEmpty()) return Integer.MIN_VALUE;
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains("load.svg") || lower.contains("/data/avatars/default/") ||
                lower.contains("placeholder") || lower.contains("/banners/")) {
            return -1000;
        }
        int score = 20;
        if (lower.contains("/content/") || lower.contains("poster")) score += 35;
        if (lower.contains("_300px") || lower.contains("thumb")) score += 15;
        if (fapelloSource) score += 8;
        return score;
    }

    private long fileCount(String description) {
        Matcher matcher = FILE_COUNT.matcher(description == null ? "" : description);
        if (!matcher.find()) return 0L;
        try {
            return Long.parseLong(matcher.group(1).replace(",", ""));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private ArrayList<NativeContentItem> buildItems(List<ResolvedCreator> creators) {
        return buildItems(creators, LIVE_ITEMS);
    }

    private ArrayList<NativeContentItem> buildItems(
            List<ResolvedCreator> creators,
            int limit
    ) {
        creators.sort(Comparator.comparingInt(value -> value.rank));
        ArrayList<NativeContentItem> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (ResolvedCreator creator : creators) {
            if (creator == null || creator.item == null ||
                    !names.add(compact(creator.item.title))) continue;
            result.add(creator.item);
            if (result.size() >= limit) break;
        }
        return result;
    }

    private ArrayList<NativeContentItem> mergeWarm(
            List<NativeContentItem> resolved,
            List<NativeContentItem> warm
    ) {
        return mergeWarm(resolved, warm, LIVE_ITEMS);
    }

    private ArrayList<NativeContentItem> mergeWarm(
            List<NativeContentItem> resolved,
            List<NativeContentItem> warm,
            int limit
    ) {
        LinkedHashMap<String, NativeContentItem> merged = new LinkedHashMap<>();
        if (resolved != null) {
            for (NativeContentItem item : resolved) {
                if (item != null) merged.putIfAbsent(compact(item.title), item);
            }
        }
        if (warm != null) {
            for (NativeContentItem item : warm) {
                if (item != null) merged.putIfAbsent(compact(item.title), item);
            }
        }
        ArrayList<NativeContentItem> result = new ArrayList<>(merged.values());
        result.sort(Comparator.comparingInt(item -> parseRank(item.views)));
        if (result.size() > limit) {
            return new ArrayList<>(result.subList(0, limit));
        }
        return result;
    }

    private List<NativeContentItem> readCache(Context context, int mode, boolean allowStale) {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        int limit = mode == MODE_TOP_50 ? DISCOVER_CANDIDATES : LIVE_ITEMS;
        try {
            SharedPreferences prefs = context.getSharedPreferences(cacheName(mode), Context.MODE_PRIVATE);
            long updated = prefs.getLong("updated", 0L);
            if (!allowStale && (updated <= 0L ||
                    System.currentTimeMillis() - updated > CACHE_AGE_MS)) return result;
            JSONArray values = new JSONArray(prefs.getString("items", "[]"));
            for (int i = 0; i < values.length() && result.size() < limit; i++) {
                JSONObject value = values.optJSONObject(i);
                if (value == null) continue;
                String name = clean(value.optString("name", ""));
                String url = clean(value.optString("url", ""));
                String query = clean(value.optString("query", name));
                if (name.isEmpty() || query.isEmpty() ||
                        (!BunkrRepository.isAlbumUrl(url) &&
                                !FapelloRepository.isModelUrl(url) &&
                                !OnlyHavenRepository.isOnlyHavenUrl(url))) {
                    continue;
                }
                result.add(new NativeContentItem(
                        NativeContentItem.KIND_CREATOR,
                        name,
                        url,
                        value.optString("image", ""),
                        value.optString("rank", ""),
                        value.optString("referer", url),
                        "",
                        value.optString(
                                "description",
                                mode == MODE_TOP_50
                                        ? "Random Fapello discovery pick"
                                        : "Bunkr + Fapello + OnlyHaven + WikiFeet + WikiFeet X"
                        ),
                        query
                ));
            }
        } catch (Exception ignored) {
            result.clear();
        }
        return result;
    }

    private void writeCache(Context context, int mode, List<NativeContentItem> items) {
        try {
            JSONArray values = new JSONArray();
            for (NativeContentItem item : items) {
                JSONObject value = new JSONObject();
                value.put("name", item.title);
                value.put("url", item.url);
                value.put("query", item.searchQuery);
                value.put("image", item.imageUrl);
                value.put("referer", item.uploader);
                value.put("rank", item.views);
                value.put("description", item.description);
                values.put(value);
            }
            context.getSharedPreferences(cacheName(mode), Context.MODE_PRIVATE)
                    .edit()
                    .putLong("updated", System.currentTimeMillis())
                    .putString("items", values.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private String listingFor(int mode) throws IOException {
        if (mode == MODE_NEW) return FapelloRepository.LIST_NEW;
        if (mode == MODE_HOT) return FapelloRepository.LIST_HOT;
        if (mode == MODE_POPULAR) return FapelloRepository.LIST_POPULAR;
        throw new IOException("Unknown OnlyFap creator mode");
    }

    private String cacheName(int mode) {
        if (mode == MODE_TOP_50) return "onlyfap_discover_v2";
        // v3 discards cards cached before static Fapello routes were excluded from listings.
        return "fapzone_creator_feed_v3_" + mode;
    }

    private String serviceLabel(String service) {
        String value = clean(service).toLowerCase(Locale.US);
        if ("onlyfans".equals(value)) return "OnlyFans";
        if ("fansly".equals(value)) return "Fansly";
        if ("patreon".equals(value)) return "Patreon";
        return clean(service);
    }

    private int parseRank(String value) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (Exception ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private static String compact(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", "");
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static final class ResolvedCreator {
        final int rank;
        final NativeContentItem item;

        ResolvedCreator(int rank, NativeContentItem item) {
            this.rank = rank;
            this.item = item;
        }
    }
}
