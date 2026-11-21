package com.webapp.crazyshit;

import android.content.Context;
import android.os.SystemClock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Fast creator lookup across every source that feeds the unified Fapzone gallery. */
final class FapzoneCreatorSearchRepository {
    private static final ExecutorService SEARCH_IO = Executors.newFixedThreadPool(4);
    private static final long SEARCH_BUDGET_MS = 7_000L;
    private static final long CACHE_TTL_MS = 2L * 60L * 1_000L;
    private static final int CACHE_CAPACITY = 48;
    private static final Map<String, CachedSearch> SHARED_CACHE = new LinkedHashMap<String, CachedSearch>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, CachedSearch> eldest) {
            return size() > CACHE_CAPACITY;
        }
    };

    private static final class CachedSearch {
        final long storedAt;
        final List<NativeContentItem> networkItems;

        CachedSearch(List<NativeContentItem> networkItems) {
            storedAt = SystemClock.elapsedRealtime();
            this.networkItems = new ArrayList<>(networkItems);
        }
    }

    interface ResultListener {
        void onUpdate(List<NativeContentItem> items, boolean complete);
    }

    interface SourceSearch {
        List<NativeContentItem> search(Context context, String query, int limit) throws IOException;
    }

    /** Source validates its own suggestion matches, including verified profile aliases. */
    interface ConfirmedSourceSearch extends SourceSearch { }

    interface CategorySourceSearch {
        List<NativeContentItem> search(Context context, String query, int limit,
                                       ContentPreference preference) throws IOException;
    }

    private final ExecutorService searchIo;
    private final List<SourceSearch> sources;
    private final CategorySourceSearch categorySource;
    private final Map<String, CachedSearch> cache;

    FapzoneCreatorSearchRepository() {
        searchIo = SEARCH_IO;
        sources = new ArrayList<>();
        sources.add((ConfirmedSourceSearch) this::fromFapello);
        sources.add(this::fromWikiFeetX);
        sources.add(this::fromOnlyHaven);
        categorySource = this::fromOnlyHaven;
        cache = SHARED_CACHE;
    }

    FapzoneCreatorSearchRepository(ExecutorService searchIo, List<SourceSearch> sources) {
        this(searchIo, sources, null);
    }

    FapzoneCreatorSearchRepository(ExecutorService searchIo, List<SourceSearch> sources,
                                   CategorySourceSearch categorySource) {
        this.searchIo = searchIo;
        this.sources = new ArrayList<>(sources);
        this.categorySource = categorySource == null ? this::fromOnlyHaven : categorySource;
        cache = new LinkedHashMap<String, CachedSearch>(64, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, CachedSearch> eldest) {
                return size() > CACHE_CAPACITY;
            }
        };
    }

    List<NativeContentItem> search(Context context, String query, int limit) throws IOException {
        return search(context, query, limit, null);
    }

    List<NativeContentItem> search(
            Context context,
            String query,
            int limit,
            ResultListener listener
    ) throws IOException {
        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.length() < 2 || CreatorNameMatcher.normalized(cleanQuery).isEmpty()) {
            return new ArrayList<>();
        }
        int safeLimit = Math.max(1, Math.min(20, limit));
        ContentPreference preference = context == null ? ContentPreference.ALL : ContentPreference.get(context);
        ArrayList<NativeContentItem> learned = new ArrayList<>();
        if (context != null) {
            for (NativeContentItem item : CreatorCatalog.matching(context, cleanQuery, false, safeLimit)) {
                if (matchesQuery(item, cleanQuery) && hasUsableDisplayName(item)
                        && CreatorCategoryIndex.includes(context, preference, item)) learned.add(item);
            }
        }
        if (listener != null && !learned.isEmpty()) publish(listener, learned, false);
        String cacheKey = cacheKey(cleanQuery, preference);
        CachedSearch cached;
        synchronized (cache) { cached = cache.get(cacheKey); }
        if (cached != null && SystemClock.elapsedRealtime() - cached.storedAt < CACHE_TTL_MS) {
            ArrayList<NativeContentItem> output = new ArrayList<>(OnlyFapCreatorResults.merge(
                    learned, cached.networkItems, safeLimit));
            if (listener != null) publish(listener, output, true);
            return output;
        }
        ExecutorCompletionService<List<NativeContentItem>> completed =
                new ExecutorCompletionService<>(searchIo);
        ArrayList<Future<List<NativeContentItem>>> requests = new ArrayList<>();
        for (SourceSearch source : preference == ContentPreference.ALL ? sources
                : java.util.Collections.<SourceSearch>singletonList((c, q, n) ->
                        categorySource.search(c, q, n, preference))) {
            requests.add(completed.submit(() -> {
                // Fetch the full bounded page so suggestions can serve a later full-results search.
                List<NativeContentItem> items = source.search(context, cleanQuery, 20);
                if (items == null) throw new IOException("Creator source returned no response");
                ArrayList<NativeContentItem> confirmed = new ArrayList<>();
                for (NativeContentItem item : items) {
                    if (hasUsableDisplayName(item) && item.isCreator()
                            && !WikiFeetRepository.isRetiredItem(item)
                            && (source instanceof ConfirmedSourceSearch || matchesQuery(item, cleanQuery))) {
                        confirmed.add(item);
                    }
                }
                return confirmed;
            }));
        }

        ArrayList<NativeContentItem> network = new ArrayList<>();
        int replies = 0;
        int successes = 0;
        long deadline = SystemClock.elapsedRealtime() + SEARCH_BUDGET_MS;
        while (replies < requests.size()) {
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0L) break;
            try {
                Future<List<NativeContentItem>> reply = completed.poll(remaining, TimeUnit.MILLISECONDS);
                if (reply == null) break;
                replies++;
                List<NativeContentItem> items = reply.get();
                if (items == null) throw new IOException("Creator source returned no response");
                successes++;
                network.addAll(items);
                if (listener != null && replies < requests.size() && !network.isEmpty()) {
                    publish(listener, OnlyFapCreatorResults.merge(learned, network, safeLimit), false);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception failure) {
                android.util.Log.w("OnlyFapSearch", "Creator source request failed", failure);
            }
        }
        for (Future<?> request : requests) if (!request.isDone()) request.cancel(true);
        if (replies < requests.size()) {
            android.util.Log.w("OnlyFapSearch", (requests.size() - replies)
                    + " creator sources timed out or were cancelled");
        }
        if (successes == 0 && learned.isEmpty()) {
            throw new IOException("OnlyFap creator search could not be reached");
        }

        network.sort(java.util.Comparator.comparingInt(item -> relevance(item, cleanQuery)));
        List<NativeContentItem> networkItems = OnlyFapCreatorResults.merge(null, network, 20);
        ArrayList<NativeContentItem> output = new ArrayList<>(
                OnlyFapCreatorResults.merge(learned, networkItems, safeLimit));
        if (output.isEmpty() && successes < requests.size()) {
            throw new IOException("Creator search was incomplete; some sources were unavailable");
        }
        // Partial and timed-out replies must never turn into a definitive cached miss.
        if (successes == requests.size() && replies == requests.size()) {
            synchronized (cache) { cache.put(cacheKey, new CachedSearch(networkItems)); }
        }
        if (listener != null) publish(listener, output, true);
        return output;
    }

    private String cacheKey(String query, ContentPreference preference) {
        SourceConfig config = RemoteSourceConfigManager.snapshotOrNull();
        // Preserve handle punctuation: different accounts can share a fuzzy search key.
        return query.toLowerCase(java.util.Locale.ROOT) + "|" + preference.name() + "|"
                + (config == null ? "unset" : config.schemaVersion + ":" + config.configVersion);
    }

    private void publish(
            ResultListener listener,
            List<NativeContentItem> items,
            boolean complete
    ) {
        try {
            listener.onUpdate(new ArrayList<>(items), complete);
        } catch (RuntimeException ignored) {
        }
    }

    private List<NativeContentItem> fromFapello(Context context, String query, int limit)
            throws IOException {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        for (FapelloRepository.Model model :
                new FapelloRepository().searchConfirmedModels(context, query, limit)) {
            result.add(CreatorCatalog.fromModel(model));
        }
        return result;
    }

    private List<NativeContentItem> fromWikiFeetX(Context context, String query, int limit)
            throws IOException {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        for (WikiFeetRepository.Creator creator :
                new WikiFeetRepository().searchCreators(
                        context, WikiFeetRepository.Site.WIKIFEET_X, query, limit)) {
            result.add(creator.asItem());
        }
        return result;
    }

    private List<NativeContentItem> fromOnlyHaven(
            Context context,
            String query,
            int limit
    ) throws IOException {
        return fromOnlyHaven(context, query, limit, ContentPreference.ALL);
    }

    private List<NativeContentItem> fromOnlyHaven(Context context, String query, int limit,
                                                  ContentPreference preference) throws IOException {
        ArrayList<NativeContentItem> result = new ArrayList<>();
        for (OnlyHavenRepository.Creator creator :
                new OnlyHavenRepository().searchCategoryCreators(context, query, limit, preference)) {
            result.add(onlyHavenItem(creator, query));
        }
        return result;
    }

    static NativeContentItem onlyHavenItem(OnlyHavenRepository.Creator creator, String query) {
        StringBuilder description = new StringBuilder("OnlyHaven");
        String service = serviceLabel(creator.service);
        if (!service.isEmpty()) description.append(" · ").append(service);
        if (creator.postCount >= 0) {
            description.append(" · ")
                    .append(String.format(java.util.Locale.US, "%,d", creator.postCount))
                    .append(creator.postCount == 1 ? " post" : " posts");
        }
        return new NativeContentItem(
                NativeContentItem.KIND_CREATOR,
                creator.name,
                creator.url,
                creator.imageUrl,
                creator.postCount >= 0 ? String.valueOf(creator.postCount) : "",
                creator.url,
                "",
                description.toString(),
                CreatorNameMatcher.rank(creator.searchAlias, query) != Integer.MAX_VALUE
                        || CreatorNameMatcher.rank(creator.name, query) != Integer.MAX_VALUE
                        ? creator.searchAlias : creator.id
        ).withCreatorIdentity(java.util.Collections.singletonList(creator.username),
                java.util.Collections.singletonList(creator.url));
    }

    private int relevance(NativeContentItem item, String query) {
        for (String alias : item.creatorAliases) {
            if (CreatorGalleryMatch.exactHandle(query, alias)) return -1;
        }
        return OnlyFapCreatorResults.rank(item, query);
    }

    private boolean matchesQuery(NativeContentItem item, String query) {
        if (item == null || !item.isCreator()) return false;
        return OnlyFapCreatorResults.rank(item, query) != Integer.MAX_VALUE;
    }

    private boolean hasUsableDisplayName(NativeContentItem item) {
        if (item == null || item.title == null) return false;
        String title = item.title.trim();
        if (title.isEmpty()) return false;
        String lower = title.toLowerCase(java.util.Locale.US);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.contains("/creators/")) {
            return false;
        }
        String compact = CreatorNameMatcher.normalized(title).replace(" ", "");
        if (compact.isEmpty()) return false;
        for (int i = 0; i < compact.length(); i++) {
            if (!Character.isDigit(compact.charAt(i))) return true;
        }
        return false;
    }

    private static String serviceLabel(String service) {
        String value = service == null ? "" : service.trim().toLowerCase(java.util.Locale.US);
        if ("onlyfans".equals(value)) return "OnlyFans";
        if ("fansly".equals(value)) return "Fansly";
        if ("patreon".equals(value)) return "Patreon";
        return service == null ? "" : service.trim();
    }
}
