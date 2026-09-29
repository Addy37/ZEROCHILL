package com.webapp.crazyshit;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Local-only playback history and Continue Watching data. */
public final class PlaybackHistoryStore {
    private static final String PREFS = "playback_history";
    private static final String KEY_ITEMS = "items";
    private static final int MAX_ITEMS = 200;
    private static final long MIN_HISTORY_MS = 5_000L;
    private static final long MIN_CONTINUE_MS = 30_000L;
    private static final float COMPLETE_FRACTION = 0.95f;

    private static final Object LOCK = new Object();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final ArrayDeque<RecordMutation> pendingRecords = new ArrayDeque<>();

    private static ArrayList<Item> cachedItems;
    private static Future<?> loadFuture;
    private static boolean writeScheduled;
    private static long stateVersion;

    private PlaybackHistoryStore() {
    }

    /**
     * Starts loading history before the first playback event. Record calls never wait for this
     * load: mutations queue in memory and are merged by the background worker.
     */
    public static void initializeAsync(Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        synchronized (LOCK) {
            ensureLoadScheduledLocked(appContext);
        }
    }

    public static void record(
            Context context,
            String title,
            String pageUrl,
            long positionMs,
            long durationMs,
            boolean ended
    ) {
        record(context, title, pageUrl, "", positionMs, durationMs, ended, false);
    }

    public static void record(
            Context context,
            String title,
            String pageUrl,
            String posterUrl,
            long positionMs,
            long durationMs,
            boolean ended,
            boolean fromShows
    ) {
        if (context == null || pageUrl == null || pageUrl.trim().isEmpty()) return;
        if (!ended && positionMs < MIN_HISTORY_MS) return;

        Context appContext = context.getApplicationContext();
        RecordMutation mutation = new RecordMutation(
                title,
                pageUrl,
                posterUrl,
                positionMs,
                durationMs,
                ended,
                fromShows,
                System.currentTimeMillis()
        );

        synchronized (LOCK) {
            if (cachedItems == null) {
                pendingRecords.addLast(mutation);
                ensureLoadScheduledLocked(appContext);
                return;
            }

            applyRecord(cachedItems, mutation);
            stateVersion++;
            scheduleWriteLocked(appContext);
        }
    }

    public static List<Item> load(Context context) {
        if (context == null) return new ArrayList<>();
        awaitLoaded(context.getApplicationContext());
        synchronized (LOCK) {
            return cachedItems == null
                    ? new ArrayList<>()
                    : new ArrayList<>(cachedItems);
        }
    }

    public static List<Item> continueWatching(Context context) {
        ArrayList<Item> out = new ArrayList<>();
        for (Item item : load(context)) {
            if (!isContinueCandidate(item)) continue;
            out.add(item);
        }
        return out;
    }

    public static List<Item> continueWatchingShows(Context context) {
        ArrayList<Item> out = new ArrayList<>();
        for (Item item : load(context)) {
            if (!item.fromShows || isKnownNonShowsSource(item.pageUrl) ||
                    !isShowsContinueCandidate(item)) {
                continue;
            }
            out.add(item);
        }
        return out;
    }

    public static void remove(Context context, String pageUrl) {
        if (context == null || pageUrl == null) return;
        Context appContext = context.getApplicationContext();
        awaitLoaded(appContext);
        synchronized (LOCK) {
            if (cachedItems == null) return;
            boolean changed = cachedItems.removeIf(item -> pageUrl.equals(item.pageUrl));
            if (!changed) return;
            stateVersion++;
            scheduleWriteLocked(appContext);
        }
    }

    public static void clear(Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        awaitLoaded(appContext);
        synchronized (LOCK) {
            pendingRecords.clear();
            if (cachedItems == null) cachedItems = new ArrayList<>();
            else cachedItems.clear();
            stateVersion++;
            scheduleWriteLocked(appContext);
        }
    }

    private static void ensureLoadScheduledLocked(Context appContext) {
        if (cachedItems != null || loadFuture != null) return;
        loadFuture = IO.submit(() -> {
            ArrayList<Item> loaded = readFromPreferences(appContext);
            boolean changed = false;
            synchronized (LOCK) {
                while (!pendingRecords.isEmpty()) {
                    applyRecord(loaded, pendingRecords.removeFirst());
                    changed = true;
                }
                cachedItems = loaded;
                if (changed) stateVersion++;
                loadFuture = null;
                LOCK.notifyAll();
                if (changed) scheduleWriteLocked(appContext);
            }
        });
    }

    private static void awaitLoaded(Context appContext) {
        Future<?> future;
        synchronized (LOCK) {
            if (cachedItems != null) return;
            ensureLoadScheduledLocked(appContext);
            future = loadFuture;
        }
        if (future == null) return;
        try {
            future.get();
        } catch (Exception ignored) {
            synchronized (LOCK) {
                if (cachedItems == null) {
                    cachedItems = readFromPreferences(appContext);
                    loadFuture = null;
                    LOCK.notifyAll();
                }
            }
        }
    }

    private static ArrayList<Item> readFromPreferences(Context context) {
        ArrayList<Item> items = new ArrayList<>();
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ITEMS, "[]");
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                String pageUrl = object.optString("pageUrl", "").trim();
                if (pageUrl.isEmpty()) continue;
                items.add(new Item(
                        object.optString("title", "Video"),
                        pageUrl,
                        object.optString("posterUrl", ""),
                        Math.max(0L, object.optLong("positionMs", 0L)),
                        Math.max(0L, object.optLong("durationMs", 0L)),
                        Math.max(0L, object.optLong("lastWatched", 0L)),
                        object.optBoolean("complete", false),
                        object.optBoolean("fromShows", false)
                ));
            }
        } catch (Exception ignored) {
        }
        Collections.sort(items, (a, b) -> Long.compare(b.lastWatched, a.lastWatched));
        trimToLimit(items);
        return items;
    }

    private static void applyRecord(ArrayList<Item> items, RecordMutation mutation) {
        String cleanPageUrl = mutation.pageUrl.trim();
        Item previous = null;
        for (Item item : items) {
            if (cleanPageUrl.equals(item.pageUrl)) {
                previous = item;
                break;
            }
        }
        items.removeIf(item -> cleanPageUrl.equals(item.pageUrl));

        boolean complete = mutation.ended ||
                isComplete(mutation.positionMs, mutation.durationMs);
        long safePosition = Math.max(0L, mutation.positionMs);
        long safeDuration = Math.max(0L, mutation.durationMs);
        String safeTitle = mutation.title == null || mutation.title.trim().isEmpty()
                ? "Video"
                : mutation.title.trim();
        String safePoster = mutation.posterUrl == null ? "" : mutation.posterUrl.trim();
        if (safePoster.isEmpty() && previous != null) safePoster = previous.posterUrl;
        boolean showsRelated = mutation.fromShows ||
                (previous != null
                        && previous.fromShows
                        && !isKnownNonShowsSource(cleanPageUrl));

        items.add(0, new Item(
                safeTitle,
                cleanPageUrl,
                safePoster,
                safePosition,
                safeDuration,
                mutation.watchedAt,
                complete,
                showsRelated
        ));
        trimToLimit(items);
    }

    private static void trimToLimit(ArrayList<Item> items) {
        if (items.size() <= MAX_ITEMS) return;
        items.subList(MAX_ITEMS, items.size()).clear();
    }

    private static void scheduleWriteLocked(Context appContext) {
        if (writeScheduled) return;
        writeScheduled = true;
        IO.execute(() -> drainWrites(appContext));
    }

    private static void drainWrites(Context appContext) {
        while (true) {
            ArrayList<Item> snapshot;
            long snapshotVersion;
            synchronized (LOCK) {
                snapshot = cachedItems == null
                        ? new ArrayList<>()
                        : new ArrayList<>(cachedItems);
                snapshotVersion = stateVersion;
            }

            writeSnapshot(appContext, snapshot);

            synchronized (LOCK) {
                if (snapshotVersion == stateVersion) {
                    writeScheduled = false;
                    LOCK.notifyAll();
                    return;
                }
            }
        }
    }

    private static void writeSnapshot(Context context, List<Item> items) {
        JSONArray array = new JSONArray();
        try {
            for (Item item : items) {
                JSONObject object = new JSONObject();
                object.put("title", item.title);
                object.put("pageUrl", item.pageUrl);
                object.put("posterUrl", item.posterUrl);
                object.put("positionMs", item.positionMs);
                object.put("durationMs", item.durationMs);
                object.put("lastWatched", item.lastWatched);
                object.put("complete", item.complete);
                object.put("fromShows", item.fromShows);
                array.put(object);
            }
        } catch (Exception ignored) {
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_ITEMS, array.toString()).commit();
    }

    private static boolean isKnownNonShowsSource(String pageUrl) {
        return FapelloRepository.isFapelloUrl(pageUrl) ||
                OnlyHavenRepository.isOnlyHavenUrl(pageUrl) ||
                BunkrRepository.isBunkrUrl(pageUrl);
    }

    private static boolean isContinueCandidate(Item item) {
        if (item == null || item.complete) return false;
        if (item.positionMs < MIN_CONTINUE_MS) return false;
        return item.durationMs <= 0L || !isComplete(item.positionMs, item.durationMs);
    }

    private static boolean isShowsContinueCandidate(Item item) {
        if (item == null || item.complete) return false;
        if (item.positionMs < MIN_HISTORY_MS) return false;
        return item.durationMs <= 0L || !isComplete(item.positionMs, item.durationMs);
    }

    private static boolean isComplete(long positionMs, long durationMs) {
        return durationMs > 0L && positionMs >= (long) (durationMs * COMPLETE_FRACTION);
    }

    static void awaitPendingWritesForTests() {
        try {
            IO.submit(() -> { }).get();
        } catch (Exception ignored) {
        }
    }

    static void resetForTests() {
        awaitPendingWritesForTests();
        synchronized (LOCK) {
            cachedItems = null;
            pendingRecords.clear();
            loadFuture = null;
            writeScheduled = false;
            stateVersion = 0L;
        }
    }

    private static final class RecordMutation {
        final String title;
        final String pageUrl;
        final String posterUrl;
        final long positionMs;
        final long durationMs;
        final boolean ended;
        final boolean fromShows;
        final long watchedAt;

        RecordMutation(
                String title,
                String pageUrl,
                String posterUrl,
                long positionMs,
                long durationMs,
                boolean ended,
                boolean fromShows,
                long watchedAt
        ) {
            this.title = title;
            this.pageUrl = pageUrl;
            this.posterUrl = posterUrl;
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.ended = ended;
            this.fromShows = fromShows;
            this.watchedAt = watchedAt;
        }
    }

    public static final class Item {
        public final String title;
        public final String pageUrl;
        public final String posterUrl;
        public final long positionMs;
        public final long durationMs;
        public final long lastWatched;
        public final boolean complete;
        public final boolean fromShows;

        Item(
                String title,
                String pageUrl,
                String posterUrl,
                long positionMs,
                long durationMs,
                long lastWatched,
                boolean complete,
                boolean fromShows
        ) {
            this.title = title == null || title.trim().isEmpty() ? "Video" : title;
            this.pageUrl = pageUrl == null ? "" : pageUrl;
            this.posterUrl = posterUrl == null ? "" : posterUrl;
            this.positionMs = Math.max(0L, positionMs);
            this.durationMs = Math.max(0L, durationMs);
            this.lastWatched = Math.max(0L, lastWatched);
            this.complete = complete;
            this.fromShows = fromShows;
        }

        public int progressPercent() {
            if (durationMs <= 0L) return 0;
            return (int) Math.max(0L, Math.min(100L, positionMs * 100L / durationMs));
        }
    }
}
