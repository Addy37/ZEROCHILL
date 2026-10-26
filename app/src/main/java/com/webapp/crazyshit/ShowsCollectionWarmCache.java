package com.webapp.crazyshit;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Small, bounded first-page cache for Shows collections that are currently visible on screen.
 * It keeps collection taps fast without changing source routing or blocking the Shows hub.
 */
final class ShowsCollectionWarmCache {
    private static final long TTL_MS = 3L * 60L * 1000L;
    private static final int MAX_ENTRIES = 16;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Listener {
        void onLoaded(String url, List<NativeContentItem> items);
    }

    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(
            2,
            2,
            30L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(4),
            new ThreadPoolExecutor.AbortPolicy()
    );

    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Map<String, CopyOnWriteArrayList<Listener>> WAITERS =
            new ConcurrentHashMap<>();
    private static final LinkedHashMap<String, CachedPage> CACHE =
            new LinkedHashMap<String, CachedPage>(MAX_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedPage> eldest) {
                    return size() > MAX_ENTRIES;
                }
            };

    static {
        IO.allowCoreThreadTimeOut(true);
    }

    private ShowsCollectionWarmCache() {
    }

    static void request(Context context, NativeContentItem item) {
        request(context, item, null);
    }

    static void request(Context context, NativeContentItem item, Listener listener) {
        if (context == null || item == null) return;
        String url = clean(item.url);
        if (!supported(url)) return;

        List<NativeContentItem> cached = get(url);
        if (cached != null) {
            if (listener != null) MAIN.post(() -> listener.onLoaded(url, cached));
            return;
        }
        if (listener != null) {
            WAITERS.computeIfAbsent(url, ignored -> new CopyOnWriteArrayList<>()).add(listener);
        }
        if (!IN_FLIGHT.add(url)) return;

        Context appContext = context.getApplicationContext();
        try {
            IO.execute(() -> {
                List<NativeContentItem> loaded = null;
                try {
                    loaded = fetch(appContext, url);
                    if (loaded != null && !loaded.isEmpty()) put(url, loaded);
                } catch (Exception ignored) {
                } finally {
                    IN_FLIGHT.remove(url);
                    dispatch(url, loaded);
                }
            });
        } catch (RejectedExecutionException ignored) {
            IN_FLIGHT.remove(url);
            dispatch(url, null);
        }
    }

    private static void dispatch(String url, List<NativeContentItem> items) {
        CopyOnWriteArrayList<Listener> listeners = WAITERS.remove(url);
        if (listeners == null || listeners.isEmpty()) return;
        List<NativeContentItem> copy = items == null
                ? new ArrayList<>()
                : new ArrayList<>(items);
        MAIN.post(() -> {
            for (Listener listener : listeners) {
                if (listener != null) listener.onLoaded(url, new ArrayList<>(copy));
            }
        });
    }

    static List<NativeContentItem> get(String url) {
        String key = clean(url);
        if (key.isEmpty()) return null;
        synchronized (CACHE) {
            CachedPage entry = CACHE.get(key);
            if (entry == null) return null;
            if (System.currentTimeMillis() - entry.createdAt > TTL_MS) {
                CACHE.remove(key);
                return null;
            }
            return new ArrayList<>(entry.items);
        }
    }

    private static void put(String url, List<NativeContentItem> items) {
        synchronized (CACHE) {
            CACHE.put(url, new CachedPage(new ArrayList<>(items), System.currentTimeMillis()));
        }
    }

    private static List<NativeContentItem> fetch(Context context, String url) throws Exception {
        if (WebVideoSourceRepository.isKaoticUrl(url)) {
            return new WebVideoSourceRepository().fetchFeed(
                    context,
                    WebVideoSourceRepository.Source.KAOTIC,
                    url,
                    1
            );
        }
        if (EfuktRepository.isEfuktUrl(url)) {
            return new EfuktRepository().fetchSeriesFeed(context, url, 1);
        }
        if (BaddieHubRepository.isBaddieHubUrl(url)) {
            return new BaddieHubRepository().fetchCategory(context, url, 1);
        }
        if (XMegaDriveRepository.isCategoryUrl(url)) {
            return new XMegaDriveRepository().fetchCategory(context, url, 1);
        }
        return new CrazyShitRepository().fetchFeed(context, url, 1);
    }

    private static boolean supported(String url) {
        return url.startsWith(CrazyShitRepository.BASE)
                || EfuktRepository.isEfuktUrl(url)
                || WebVideoSourceRepository.isKaoticUrl(url)
                || BaddieHubRepository.isBaddieHubUrl(url)
                || XMegaDriveRepository.isCategoryUrl(url);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class CachedPage {
        final List<NativeContentItem> items;
        final long createdAt;

        CachedPage(List<NativeContentItem> items, long createdAt) {
            this.items = items;
            this.createdAt = createdAt;
        }
    }
}
