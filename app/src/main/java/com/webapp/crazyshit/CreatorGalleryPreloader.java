package com.webapp.crazyshit;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Warms small creator-gallery sessions for creator cards that are visible or close to visible.
 *
 * Two workers protect source traffic. The queue is bounded by reservation count so fast scrolling
 * cannot turn creator-card binding into an unbounded network backlog.
 */
final class CreatorGalleryPreloader {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";
    private static final int MAX_RESERVED = 8;
    private static final int IMAGE_WARM_LIMIT = 8;
    private static final int IMAGE_CACHE_KEYS = 160;
    private static final int MERGED_MEMBER_ITEM_LIMIT = 24;
    private static final int MERGED_PREVIEW_LIMIT = 64;
    static final int PRIORITY_NORMAL = 0;
    static final int PRIORITY_HIGH = 10;

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(
            2,
            2,
            30L,
            TimeUnit.SECONDS,
            new PriorityBlockingQueue<>()
    );
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, String> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<String, WarmTask> PENDING = new ConcurrentHashMap<>();
    private static final Set<String> WARMING = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger RESERVED = new AtomicInteger();

    private static final LinkedHashMap<String, Boolean> WARMED_IMAGES =
            new LinkedHashMap<String, Boolean>(IMAGE_CACHE_KEYS, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > IMAGE_CACHE_KEYS;
                }
            };

    private CreatorGalleryPreloader() {
    }

    static void warm(Context context, NativeContentItem creator) {
        warm(context, creator, PRIORITY_NORMAL);
    }

    static void warm(Context context, NativeContentItem creator, int priority) {
        if (creator == null || !creator.isCreator()) return;
        String query = creator.searchQuery == null || creator.searchQuery.trim().isEmpty()
                ? creator.title
                : creator.searchQuery.trim();
        String sourceProfile = FapelloRepository.isModelUrl(creator.url) ||
                OnlyHavenRepository.isOnlyHavenUrl(creator.url)
                ? creator.url
                : "";
        warm(context, creator.title, query, sourceProfile, priority);
    }

    static void warm(Context context, String creatorName, String query, String fapelloProfileUrl) {
        warm(context, creatorName, query, fapelloProfileUrl, PRIORITY_NORMAL);
    }

    static void warm(
            Context context,
            String creatorName,
            String query,
            String fapelloProfileUrl,
            int priority
    ) {
        if (context == null) return;
        String cleanName = clean(creatorName);
        String cleanQuery = clean(query);
        if (cleanQuery.isEmpty()) cleanQuery = cleanName;
        if (cleanQuery.isEmpty()) return;

        String key = key(cleanQuery);
        String recent = BunkrGallerySessionStore.recentCreator(cleanQuery);
        if (recent != null) {
            BunkrGallerySessionStore.Snapshot snapshot = BunkrGallerySessionStore.snapshot(recent);
            if (snapshot != null) warmImages(context, snapshot.items);
            return;
        }
        String active = SESSIONS.get(key);
        if (active != null && BunkrGallerySessionStore.snapshot(active) == null) {
            SESSIONS.remove(key, active);
        }
        if (SESSIONS.containsKey(key) || WARMING.contains(key)) return;
        if (!reserve()) return;
        if (!WARMING.add(key)) {
            RESERVED.decrementAndGet();
            return;
        }

        Context app = context.getApplicationContext();
        Context safeContext = app == null ? context : app;
        String finalName = cleanName.isEmpty() ? cleanQuery : cleanName;
        String finalQuery = cleanQuery;
        String finalFapelloProfile = clean(fapelloProfileUrl);

        WarmTask task = new WarmTask(
                key,
                priority,
                SEQUENCE.getAndIncrement(),
                () -> {
            String sessionId = "";
            try {
                String nowRecent = BunkrGallerySessionStore.recentCreator(finalQuery);
                if (nowRecent != null) {
                    SESSIONS.put(key, nowRecent);
                    BunkrGallerySessionStore.Snapshot snapshot =
                            BunkrGallerySessionStore.snapshot(nowRecent);
                    if (snapshot != null) warmImages(safeContext, snapshot.items);
                    return;
                }

                String persistedId =
                        BunkrGallerySessionStore.recentCreatorId(safeContext, finalQuery);
                if (persistedId != null) {
                    BunkrGallerySessionStore.Snapshot persisted =
                            BunkrGallerySessionStore.restoreRecentCreator(
                                    safeContext, finalQuery);
                    if (persisted != null) {
                        SESSIONS.put(key, persistedId);
                        warmImages(safeContext, persisted.items);
                        return;
                    }
                }

                sessionId = BunkrGallerySessionStore.createCreator(
                        finalName,
                        BunkrRepository.searchUrl(finalQuery),
                        finalQuery
                );
                SESSIONS.put(key, sessionId);

                BunkrCreatorGalleryRepository repository = new BunkrCreatorGalleryRepository();
                repository.reset(sessionId, finalQuery, finalFapelloProfile, finalName);
                String activeSession = sessionId;
                repository.fetchNext(
                        safeContext,
                        activeSession,
                        finalQuery,
                        finalFapelloProfile,
                        finalName,
                        items -> {
                            if (items == null || items.isEmpty()) return;
                            BunkrGallerySessionStore.appendPreview(activeSession, items);
                            warmImages(safeContext, items);
                        }
                );

                BunkrGallerySessionStore.Snapshot snapshot =
                        BunkrGallerySessionStore.snapshot(activeSession);
                if (snapshot != null) warmImages(safeContext, snapshot.items);
            } catch (Exception ignored) {
            } finally {
                WARMING.remove(key);
                RESERVED.decrementAndGet();
                if (!sessionId.isEmpty()) {
                    SESSIONS.remove(key, sessionId);
                }
            }
        });
        PENDING.put(key, task);
        IO.execute(task);
    }


    static void warm(Context context, CreatorGallerySpec spec, int priority) {
        if (spec == null || spec.item == null) return;
        if (!spec.grouped) {
            warm(context, spec.item, priority);
            return;
        }
        if (context == null) return;

        String cleanName = clean(spec.item.title);
        String cleanQuery = clean(spec.query);
        String cleanCacheKey = clean(spec.cacheKey);
        if (cleanQuery.isEmpty() || cleanCacheKey.isEmpty()) return;

        String taskKey = key(cleanCacheKey);
        String recent = BunkrGallerySessionStore.recentCreator(cleanCacheKey);
        if (recent != null) {
            BunkrGallerySessionStore.Snapshot snapshot =
                    BunkrGallerySessionStore.snapshot(recent);
            if (snapshot != null) warmImages(context, snapshot.items);
            return;
        }
        String active = SESSIONS.get(taskKey);
        if (active != null && BunkrGallerySessionStore.snapshot(active) == null) {
            SESSIONS.remove(taskKey, active);
        }
        if (SESSIONS.containsKey(taskKey) || WARMING.contains(taskKey)) return;
        if (!reserve()) return;
        if (!WARMING.add(taskKey)) {
            RESERVED.decrementAndGet();
            return;
        }

        Context app = context.getApplicationContext();
        Context safeContext = app == null ? context : app;
        String finalName = cleanName.isEmpty() ? cleanQuery : cleanName;
        String finalQuery = cleanQuery;
        String finalCacheKey = cleanCacheKey;
        String finalProfileHint = clean(spec.profileHint);
        ArrayList<String> finalSeedNames = new ArrayList<>(spec.seedNames);
        ArrayList<String> finalSeedUrls = new ArrayList<>(spec.seedUrls);
        ArrayList<String> finalSeedImages = new ArrayList<>(spec.seedImages);

        WarmTask task = new WarmTask(
                taskKey,
                priority,
                SEQUENCE.getAndIncrement(),
                () -> {
            String sessionId = "";
            try {
                String nowRecent = BunkrGallerySessionStore.recentCreator(finalCacheKey);
                if (nowRecent != null) {
                    SESSIONS.put(taskKey, nowRecent);
                    BunkrGallerySessionStore.Snapshot snapshot =
                            BunkrGallerySessionStore.snapshot(nowRecent);
                    if (snapshot != null) warmImages(safeContext, snapshot.items);
                    return;
                }

                String persistedId =
                        BunkrGallerySessionStore.recentCreatorId(safeContext, finalCacheKey);
                if (persistedId != null) {
                    BunkrGallerySessionStore.Snapshot persisted =
                            BunkrGallerySessionStore.restoreRecentCreator(
                                    safeContext, finalCacheKey);
                    if (persisted != null) {
                        SESSIONS.put(taskKey, persistedId);
                        warmImages(safeContext, persisted.items);
                        return;
                    }
                }

                ArrayList<NativeContentItem> cachedPreview =
                        restoredMemberItems(
                                safeContext,
                                mergedMemberQueries(finalQuery, finalSeedNames)
                        );
                sessionId = BunkrGallerySessionStore.createCreator(
                        finalName,
                        BunkrRepository.searchUrl(finalQuery),
                        finalCacheKey
                );
                SESSIONS.put(taskKey, sessionId);
                if (!cachedPreview.isEmpty()) {
                    BunkrGallerySessionStore.appendPreview(sessionId, cachedPreview);
                    warmImages(safeContext, cachedPreview);
                }

                BunkrCreatorGalleryRepository repository =
                        new BunkrCreatorGalleryRepository();
                repository.reset(
                        sessionId,
                        finalQuery,
                        finalProfileHint,
                        finalName,
                        finalSeedNames,
                        finalSeedUrls,
                        finalSeedImages
                );
                String activeSession = sessionId;
                repository.fetchNext(
                        safeContext,
                        activeSession,
                        finalQuery,
                        finalProfileHint,
                        finalName,
                        finalSeedNames,
                        finalSeedUrls,
                        finalSeedImages,
                        items -> {
                            if (items == null || items.isEmpty()) return;
                            BunkrGallerySessionStore.appendPreview(activeSession, items);
                            warmImages(safeContext, items);
                        }
                );

                BunkrGallerySessionStore.Snapshot snapshot =
                        BunkrGallerySessionStore.snapshot(activeSession);
                if (snapshot != null) warmImages(safeContext, snapshot.items);
            } catch (Exception ignored) {
            } finally {
                WARMING.remove(taskKey);
                RESERVED.decrementAndGet();
                if (!sessionId.isEmpty()) {
                    SESSIONS.remove(taskKey, sessionId);
                }
            }
        });
        PENDING.put(taskKey, task);
        IO.execute(task);
    }

    static String composeInMemoryMergedSession(Context context, CreatorGallerySpec spec) {
        if (context == null || spec == null || !spec.grouped || spec.item == null) return "";
        String warm = sessionId(context, spec.cacheKey);
        if (!warm.isEmpty()) return warm;

        ArrayList<NativeContentItem> preview =
                recentMemberItems(mergedMemberQueries(spec.query, spec.seedNames));
        if (preview.isEmpty()) return "";

        String sessionId = BunkrGallerySessionStore.createCreator(
                spec.item.title,
                BunkrRepository.searchUrl(spec.query),
                spec.cacheKey
        );
        BunkrGallerySessionStore.appendPreview(sessionId, preview);
        warmImages(context, preview);
        return sessionId;
    }

    static void cancelQueued(CreatorGallerySpec spec) {
        if (spec == null) return;
        cancelQueuedKey(spec.grouped ? spec.cacheKey : spec.query);
    }

    private static ArrayList<String> mergedMemberQueries(
            String primaryQuery,
            List<String> memberQueries
    ) {
        ArrayList<String> result = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        String primary = clean(primaryQuery);
        if (!primary.isEmpty()) {
            result.add(primary);
            seen.add(key(primary));
        }
        if (memberQueries != null) {
            for (String query : memberQueries) {
                String cleanQuery = clean(query);
                if (cleanQuery.isEmpty() || !seen.add(key(cleanQuery))) continue;
                result.add(cleanQuery);
            }
        }
        return result;
    }

    private static ArrayList<NativeContentItem> restoredMemberItems(
            Context context,
            List<String> memberQueries
    ) {
        ArrayList<NativeContentItem> preview = new ArrayList<>();
        if (context == null || memberQueries == null || memberQueries.isEmpty()) return preview;
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (String query : memberQueries) {
            if (preview.size() >= MERGED_PREVIEW_LIMIT) break;
            BunkrGallerySessionStore.Snapshot snapshot =
                    BunkrGallerySessionStore.restoreRecentCreator(context, clean(query));
            if (snapshot == null || snapshot.items.isEmpty()) continue;
            int accepted = 0;
            for (NativeContentItem item : snapshot.items) {
                if (item == null || item.url.isEmpty() || !seen.add(item.url)) continue;
                preview.add(item);
                if (++accepted >= MERGED_MEMBER_ITEM_LIMIT
                        || preview.size() >= MERGED_PREVIEW_LIMIT) break;
            }
        }
        return preview;
    }

    private static ArrayList<NativeContentItem> recentMemberItems(List<String> memberQueries) {
        ArrayList<NativeContentItem> preview = new ArrayList<>();
        if (memberQueries == null || memberQueries.isEmpty()) return preview;
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (String query : memberQueries) {
            if (preview.size() >= MERGED_PREVIEW_LIMIT) break;
            String id = BunkrGallerySessionStore.recentCreator(clean(query));
            if (id == null) continue;
            BunkrGallerySessionStore.Snapshot snapshot =
                    BunkrGallerySessionStore.snapshot(id);
            if (snapshot == null || snapshot.items.isEmpty()) continue;
            int accepted = 0;
            for (NativeContentItem item : snapshot.items) {
                if (item == null || item.url.isEmpty() || !seen.add(item.url)) continue;
                preview.add(item);
                if (++accepted >= MERGED_MEMBER_ITEM_LIMIT
                        || preview.size() >= MERGED_PREVIEW_LIMIT) break;
            }
        }
        return preview;
    }

    private static void cancelQueuedKey(String query) {
        String cleanQuery = clean(query);
        if (cleanQuery.isEmpty()) return;
        String key = key(cleanQuery);
        WarmTask task = PENDING.get(key);
        if (task == null || task.priority >= PRIORITY_HIGH || !IO.remove(task)) return;
        if (PENDING.remove(key, task)) {
            WARMING.remove(key);
            RESERVED.decrementAndGet();
        }
    }

    static void cancelQueued(NativeContentItem creator) {
        if (creator == null || !creator.isCreator()) return;
        String query = creator.searchQuery == null || creator.searchQuery.trim().isEmpty()
                ? creator.title
                : creator.searchQuery.trim();
        cancelQueuedKey(query);
    }

    static String sessionId(NativeContentItem creator) {
        if (creator == null) return "";
        String query = creator.searchQuery == null || creator.searchQuery.trim().isEmpty()
                ? creator.title
                : creator.searchQuery.trim();
        return sessionId(query);
    }

    static String sessionId(Context context, NativeContentItem creator) {
        if (creator == null) return "";
        String query = creator.searchQuery == null || creator.searchQuery.trim().isEmpty()
                ? creator.title
                : creator.searchQuery.trim();
        return sessionId(context, query);
    }

    static String sessionId(String query) {
        String cleanQuery = clean(query);
        if (cleanQuery.isEmpty()) return "";
        String recent = BunkrGallerySessionStore.recentCreator(cleanQuery);
        if (recent != null) return recent;
        String key = key(cleanQuery);
        String session = SESSIONS.get(key);
        if (session == null) return "";
        BunkrGallerySessionStore.Snapshot snapshot =
                BunkrGallerySessionStore.snapshot(session);
        if (snapshot == null) {
            SESSIONS.remove(key, session);
            return "";
        }
        // Do not hand the UI an in-flight preload that still has no media. The repository
        // serializes one gallery session at a time, so reusing an empty warming session can
        // make the visible gallery wait behind background work. Once the first preview lands,
        // the same session becomes safe to reuse instantly.
        return snapshot.items.isEmpty() ? "" : session;
    }

    static String sessionId(Context context, String query) {
        String inMemory = sessionId(query);
        if (!inMemory.isEmpty()) return inMemory;
        String persisted = BunkrGallerySessionStore.recentCreatorId(context, clean(query));
        return persisted == null ? "" : persisted;
    }

    private static boolean reserve() {
        while (true) {
            int current = RESERVED.get();
            if (current >= MAX_RESERVED) return false;
            if (RESERVED.compareAndSet(current, current + 1)) return true;
        }
    }

    private static void warmImages(Context context, List<NativeContentItem> items) {
        if (context == null || items == null || items.isEmpty()) return;
        Context app = context.getApplicationContext();
        Context safeContext = app == null ? context : app;
        ArrayList<NativeContentItem> targets = new ArrayList<>();
        synchronized (WARMED_IMAGES) {
            for (NativeContentItem item : items) {
                if (item == null || item.imageUrl == null || item.imageUrl.trim().isEmpty()) continue;
                String image = item.imageUrl.trim();
                if (WARMED_IMAGES.containsKey(image)) continue;
                WARMED_IMAGES.put(image, Boolean.TRUE);
                targets.add(item);
                if (targets.size() >= IMAGE_WARM_LIMIT) break;
            }
        }
        if (targets.isEmpty()) return;

        MAIN.post(() -> {
            for (NativeContentItem item : targets) {
                try {
                    Glide.with(safeContext)
                            .load(withHeaders(item.imageUrl, imageReferer(item)))
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .dontTransform()
                            .preload(384, 384);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static GlideUrl withHeaders(String imageUrl, String pageUrl) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader(
                        "Referer",
                        pageUrl == null || pageUrl.trim().isEmpty()
                                ? BunkrRepository.DEFAULT_PAGE_ORIGIN + "/"
                                : pageUrl
                )
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        try {
            String cookies = CookieManager.getInstance().getCookie(imageUrl);
            if ((cookies == null || cookies.isEmpty()) && pageUrl != null) {
                cookies = CookieManager.getInstance().getCookie(pageUrl);
            }
            if (cookies != null && !cookies.isEmpty()) headers.addHeader("Cookie", cookies);
        } catch (Exception ignored) {
        }
        return new GlideUrl(imageUrl, headers.build());
    }

    private static String imageReferer(NativeContentItem item) {
        if (item != null && WikiFeetRepository.isWikiFeetUrl(item.url) &&
                WikiFeetRepository.isWikiFeetUrl(item.uploader)) return item.uploader;
        if (item != null && !FapelloRepository.isPostUrl(item.url) &&
                FapelloRepository.isModelUrl(item.uploader)) return item.uploader;
        if (item != null && OnlyHavenRepository.isOnlyHavenUrl(item.uploader)) {
            return item.uploader;
        }
        return item == null ? null : item.url;
    }

    private static final class WarmTask implements Runnable, Comparable<WarmTask> {
        private final String key;
        private final int priority;
        private final long sequence;
        private final Runnable work;

        WarmTask(String key, int priority, long sequence, Runnable work) {
            this.key = key;
            this.priority = priority;
            this.sequence = sequence;
            this.work = work;
        }

        @Override
        public void run() {
            PENDING.remove(key, this);
            work.run();
        }

        @Override
        public int compareTo(WarmTask other) {
            if (other == null) return -1;
            int byPriority = Integer.compare(other.priority, priority);
            return byPriority != 0 ? byPriority : Long.compare(sequence, other.sequence);
        }
    }

    private static String key(String creator) {
        String normalized = CreatorNameMatcher.normalized(creator);
        return normalized.isEmpty() ? clean(creator).toLowerCase(Locale.US) : normalized;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }
}
