package com.webapp.crazyshit;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Metadata-only repair, requested by visible tiles, never by a full album scan. */
final class BunkrArtworkResolver {
    private static final int MAX_PENDING = 24;
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(2, 2, 0L,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(24));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, List<Consumer<String>>> PENDING = new LinkedHashMap<>();
    private static final LinkedHashMap<String, Cached> CACHE = new LinkedHashMap<>(256, .75f, true);

    private BunkrArtworkResolver() { }

    static boolean request(Context context, String pageUrl, String rejected, Consumer<String> callback) {
        if (!BunkrRepository.isBunkrUrl(pageUrl)) return true;
        final String key = pageUrl + "\n" + rejected;
        synchronized (PENDING) {
            Cached cached = CACHE.get(key);
            if (cached != null && cached.until > SystemClock.elapsedRealtime()) {
                MAIN.post(() -> callback.accept(cached.url));
                return true;
            }
            List<Consumer<String>> waiting = PENDING.get(key);
            if (waiting != null) {
                waiting.add(callback);
                return true;
            }
            if (PENDING.size() >= MAX_PENDING) return false;
            waiting = new ArrayList<>();
            waiting.add(callback);
            PENDING.put(key, waiting);
        }
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            String image = "";
            try { image = new BunkrRepository().fetchFileArtwork(app, pageUrl, rejected); }
            catch (Exception ignored) { }
            final String result = image;
            List<Consumer<String>> callbacks;
            synchronized (PENDING) {
                callbacks = PENDING.remove(key);
                CACHE.put(key, new Cached(result));
                while (CACHE.size() > 256) CACHE.remove(CACHE.keySet().iterator().next());
            }
            MAIN.post(() -> {
                if (callbacks != null) for (Consumer<String> ready : callbacks) ready.accept(result);
            });
        });
        return true;
    }

    private static final class Cached {
        final String url;
        final long until;
        Cached(String url) {
            this.url = url;
            until = SystemClock.elapsedRealtime() + (url.isEmpty() ? 300_000L : 3_600_000L);
        }
    }
}
