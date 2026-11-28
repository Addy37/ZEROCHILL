package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.concurrent.*;

/** Explicitly requested metadata indexing, independent of Activity lifetime. */
public final class GalleryIndexWorker extends Worker {
    private static final String PREFS = "gallery_index_queue_v1";
    private static final Object LOCK = new Object();
    private static final int MAX_RECORDS = 32;
    private static final long SLICE_MS = 7 * 60 * 1000L;
    private static final String WAITING = "Waiting";
    private static final String RUNNING = "Fetching";
    private static final String STOPPING = "Cancelling";
    private static final String CANCELLED = "Cancelled";
    private static final String COMPLETE = "Ready";
    private static final String PREPARING = "Preparing thumbnails";
    private static final String PARTIAL = "Partial";
    private static final String FAILED = "Failed";
    private static final java.util.concurrent.Semaphore SLOTS =
            new java.util.concurrent.Semaphore(GalleryIndexQueue.MAX_ACTIVE, true);

    private static final ExecutorService PREVIEW_IO = Executors.newFixedThreadPool(8);

    private static int thermal(Context context) {
        android.os.PowerManager power = context.getSystemService(android.os.PowerManager.class);
        return power != null && android.os.Build.VERSION.SDK_INT >= 29
                ? power.getCurrentThermalStatus() : 0;
    }

    private static boolean playback(Context context) {
        android.media.AudioManager audio = context.getSystemService(android.media.AudioManager.class);
        return audio != null && audio.isMusicActive();
    }

    private static int previewSlots(Context context) {
        android.app.ActivityManager.RunningAppProcessInfo state = new android.app.ActivityManager.RunningAppProcessInfo();
        android.app.ActivityManager.getMyMemoryState(state);
        android.os.PowerManager power = context.getSystemService(android.os.PowerManager.class);
        boolean interactive = state.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
                && (power == null || power.isInteractive());
        return GalleryIndexQueue.previewSlots(interactive, playback(context), thermal(context));
    }

    public GalleryIndexWorker(Context context, WorkerParameters parameters) {
        super(context, parameters);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static JSONObject read(Context context, String key) {
        try { return new JSONObject(prefs(context).getString(key, "")); }
        catch (Exception ignored) { return null; }
    }

    private static boolean active(JSONObject value) {
        if (value == null) return false;
        String status = value.optString("status");
        return GalleryIndexQueue.active(status);
    }

    static boolean owns(Context context, String key, String session) {
        JSONObject value = read(context, key);
        return active(value) && session != null && session.equals(value.optString("session"));
    }

    static String activeSession(Context context, String key) {
        JSONObject value = read(context, key);
        return value != null && (active(value) || COMPLETE.equals(value.optString("status")) || PARTIAL.equals(value.optString("status"))) ? value.optString("session") : "";
    }

    private static ArrayList<JSONObject> records(Context context) {
        ArrayList<JSONObject> records = new ArrayList<>();
        for (String key : prefs(context).getAll().keySet()) {
            JSONObject value = read(context, key);
            if (value != null) records.add(value);
        }
        Collections.sort(records, Comparator.comparingLong(v -> v.optLong("created")));
        return records;
    }

    static boolean enqueue(Context context, String key, String session, String title,
            String query, String profile, String favoriteKey, List<String> names,
            List<String> urls, List<String> images, List<String> aliases) {
        if (key == null || key.isEmpty() || session == null || session.isEmpty()
                || query == null || query.isEmpty()) return false;
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            if (active(read(app, key))) return false;
            try {
                ArrayList<JSONObject> records = records(app);
                int[] lanes = new int[2];
                for (JSONObject value : records) {
                    if (active(value)) lanes[Math.max(0, Math.min(1, value.optInt("lane")))]++;
                }
                for (JSONObject value : records) {
                    if (records.size() < MAX_RECORDS) break;
                    if (!active(value)) {
                        prefs(app).edit().remove(value.optString("key")).commit();
                        records = records(app);
                        break;
                    }
                }
                if (records.size() >= MAX_RECORDS && read(app, key) == null) return false;
                int lane = GalleryIndexQueue.chooseLane(lanes[0], lanes[1]);
                String token = UUID.randomUUID().toString();
                BunkrGallerySessionStore.Snapshot current = BunkrGallerySessionStore.snapshot(session);
                JSONObject value = new JSONObject().put("key", key).put("session", session)
                        .put("title", title).put("query", query).put("profile", profile)
                        .put("favorite", favoriteKey).put("names", array(names))
                        .put("urls", array(urls)).put("images", array(images))
                        .put("aliases", array(aliases)).put("token", token)
                        .put("created", System.currentTimeMillis()).put("lane", lane)
                        .put("status", WAITING).put("items", current == null ? 0 : current.items.size());
                if (!prefs(app).edit().putString(key, value.toString()).commit()) return false;
                notifyProgress(app, value);
                OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(GalleryIndexWorker.class)
                        .setConstraints(new Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .setInputData(new Data.Builder().putString("key", key)
                                .putString("token", token).build())
                        .addTag("gallery-index").build();
                try {
                    androidx.work.Operation operation = WorkManager.getInstance(app)
                            .enqueueUniqueWork("gallery-index-lane-" + lane,
                                    ExistingWorkPolicy.APPEND_OR_REPLACE, work);
                    operation.getResult().addListener(() -> {
                        try { operation.getResult().get(); }
                        catch (Exception error) {
                            update(app, key, token, FAILED, value.optInt("items"), "Could not schedule");
                        }
                    }, Runnable::run);
                } catch (RuntimeException error) {
                    update(app, key, token, FAILED, value.optInt("items"), "Could not schedule");
                    return false;
                }
                return true;
            } catch (Exception ignored) { return false; }
        }
    }

    private static JSONArray array(List<String> values) {
        return values == null ? new JSONArray() : new JSONArray(values);
    }

    private static ArrayList<String> list(JSONObject value, String name) {
        ArrayList<String> result = new ArrayList<>();
        JSONArray array = value.optJSONArray(name);
        if (array != null) for (int i = 0; i < array.length(); i++) result.add(array.optString(i));
        return result;
    }

    private static boolean update(Context context, String key, String token, String status,
            int items, String detail) {
        synchronized (LOCK) {
            JSONObject value = read(context, key);
            if (value == null || !token.equals(value.optString("token"))) return false;
            String old = value.optString("status");
            status = GalleryIndexQueue.transition(old, status);
            try {
                value.put("status", status).put("items", Math.max(0, items)).put("detail", detail);
                if (!prefs(context).edit().putString(key, value.toString()).commit()) return false;
                notifyProgress(context, value);
                return !CANCELLED.equals(status);
            } catch (Exception ignored) { return false; }
        }
    }

    private static void notifyProgress(Context context, JSONObject value) {
        int active = 0, waiting = 0;
        for (JSONObject record : records(context)) if (active(record)) {
            active++;
            if (WAITING.equals(record.optString("status"))) waiting++;
        }
        GalleryIndexNotifications.update(context, value.optInt("lane"), value.optString("title"),
                value.optString("status"), value.optInt("items"), value.optInt("ready"),
                value.optInt("total"), active, waiting);
    }

    private static boolean thumbnails(Context context, String key, String token, int count, int ready, int total, boolean finished, int offset) {
        synchronized (LOCK) {
            JSONObject value = read(context, key);
            if (value == null || !token.equals(value.optString("token"))) return false;
            try {
                String status = GalleryIndexQueue.transition(value.optString("status"), finished ? PREPARING : RUNNING);
                value.put("status", status).put("items", count).put("ready", ready).put("total", total)
                        .put("detail", ready + " / " + total + " thumbnails").put("thumbnailOffset", offset);
                if (!prefs(context).edit().putString(key, value.toString()).commit()) return false;
                notifyProgress(context, value);
                return !CANCELLED.equals(status);
            } catch (Exception ignored) { return false; }
        }
    }

    private static void cancel(Context context, JSONObject task) {
        synchronized (LOCK) {
            String key = task.optString("key");
            JSONObject value = read(context, key);
            if (!active(value)) return;
            try {
                value.put("status", WAITING.equals(value.optString("status")) ? CANCELLED : STOPPING);
                prefs(context).edit().putString(key, value.toString()).commit();
                notifyProgress(context, value);
            } catch (Exception ignored) { }
        }
    }

    private boolean shouldStop(Context context, String key, String token) {
        JSONObject value = read(context, key);
        return isStopped() || value == null || !token.equals(value.optString("token"))
                || STOPPING.equals(value.optString("status")) || CANCELLED.equals(value.optString("status"));
    }

    @Override public Result doWork() {
        Context app = getApplicationContext();
        String key = getInputData().getString("key");
        String token = getInputData().getString("token");
        if (key == null || token == null) return Result.success();
        JSONObject task = read(app, key);
        if (!active(task) || !token.equals(task.optString("token"))) return Result.success();
        if (!SLOTS.tryAcquire()) return Result.retry();
        String session = task.optString("session");
        int count = task.optInt("items");
        BunkrGallerySessionStore.pinIndex(session);
        TurboBatch turbo = null;
        try {
            if (shouldStop(app, key, token)) {
                update(app, key, token, isStopped() ? WAITING : CANCELLED, count, "");
                return isStopped() ? Result.retry() : Result.success();
            }
            BunkrGallerySessionStore.Snapshot snapshot = BunkrGallerySessionStore.restore(app, session);
            if (snapshot == null || !key.equals(snapshot.creatorQuery)) {
                update(app, key, token, FAILED, 0, "Cached gallery expired; reopen to retry");
                return Result.success();
            }
            count = snapshot.items.size();
            if (!update(app, key, token, RUNNING, count, "")) return Result.success();
            try {
                setForegroundAsync(GalleryIndexNotifications.foreground(app, task.optInt("lane"),
                        task.optString("title"), RUNNING, count, 0, 0)).get(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) { throw interrupted; }
            catch (Exception ignored) { /* Android may deny a foreground start; WorkManager can retry. */ }
            BunkrCreatorGalleryRepository repository = new BunkrCreatorGalleryRepository();
            long deadline = SystemClock.elapsedRealtime() + SLICE_MS;
            int stagnant = 0;
            boolean sourceIncomplete = cursorIncomplete(snapshot);
            turbo = new TurboBatch(app, key, token, snapshot, task.optInt("thumbnailOffset"));
            if (task.optBoolean("paginationFinished")) {
                return prepareThumbnails(app, key, token, snapshot, deadline, turbo,
                        task.optBoolean("paginationIncomplete") || sourceIncomplete);
            }
            while (!shouldStop(app, key, token) && SystemClock.elapsedRealtime() < deadline) {
                if (!BunkrGallerySessionStore.persistForIndex(app, session)) {
                    update(app, key, token, PARTIAL, count, "Local cache limit reached");
                    return Result.success();
                }
                if (!turbo.pump(snapshot, false)) return Result.success();
                if (count >= GalleryIndexQueue.MAX_ITEMS || snapshot.endReached) {
                    return prepareThumbnails(app, key, token, snapshot, deadline, turbo,
                            GalleryIndexQueue.partial(count, sourceIncomplete || cursorIncomplete(snapshot)));
                }
                if (thermal(app) >= 4) {
                    Thread.sleep(500);
                    continue;
                }
                String priorCursor = snapshot.cursor;
                BunkrCreatorGalleryRepository.Batch batch = repository.fetchNext(app, session,
                        task.optString("query"), task.optString("profile"), task.optString("title"),
                        list(task, "names"), list(task, "urls"), list(task, "images"),
                        list(task, "aliases"), null);
                snapshot = BunkrGallerySessionStore.snapshot(session);
                if (snapshot == null || !BunkrGallerySessionStore.persistForIndex(app, session)) {
                    update(app, key, token, PARTIAL, count, "Local cache limit reached");
                    return Result.success();
                }
                int nextCount = snapshot.items.size();
                stagnant = GalleryIndexQueue.stagnantBatches(stagnant, count, nextCount, priorCursor, snapshot.cursor);
                sourceIncomplete |= batch.indexIncomplete;
                count = nextCount;
                if (!turbo.pump(snapshot, false)) return Result.success();
                // A failed supplementary provider must not halt remaining creator pages.
                // The repository records exhausted source failures separately from its end cursor.
                if (GalleryIndexQueue.stopIndexing(count, stagnant)) {
                    return prepareThumbnails(app, key, token, snapshot, deadline, turbo, true);
                }
                if (!snapshot.endReached) {
                    long pause = GalleryIndexQueue.pagePause(playback(app), thermal(app));
                    if (pause > 0) Thread.sleep(pause);
                }
            }
            if (!update(app, key, token, WAITING, count, "Will resume when permitted"))
                return Result.success();
            return Result.retry();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return update(app, key, token, WAITING, count, "Interrupted; will resume")
                    ? Result.retry() : Result.success();
        } catch (Exception error) {
            update(app, key, token, FAILED, count, "Source unavailable; retry");
            return Result.success();
        } finally {
            if (turbo != null) turbo.close();
            BunkrGallerySessionStore.unpinIndex(session);
            SLOTS.release();
        }
    }

    /** One rolling window per creator; only completed contiguous offsets are checkpointed.
     * All work shares the eight-thread pool, including workers winding down after cancellation. */
    private static final class TurboBatch {
        final Context app;
        final String key, token;
        final ArrayDeque<Future<Boolean>> pending = new ArrayDeque<>();
        int offset, next, ready, total, measured;
        boolean cacheFull;

        TurboBatch(Context app, String key, String token, BunkrGallerySessionStore.Snapshot snapshot, int saved) {
            this.app = app; this.key = key; this.token = token;
            offset = next = Math.max(0, Math.min(saved, snapshot.items.size()));
            for (int i = 0; i < offset; i++)
                if (PreparedGalleryThumbnails.cached(app, snapshot.items.get(i)) != null) ready++;
        }

        boolean pump(BunkrGallerySessionStore.Snapshot snapshot, boolean finished) throws InterruptedException {
            for (; measured < snapshot.items.size(); measured++)
                if (PreparedGalleryThumbnails.eligible(snapshot.items.get(measured))) total++;
            while (!pending.isEmpty() && pending.peek().isDone()) {
                Future<Boolean> future = pending.remove();
                try { if (future.get()) ready++; }
                catch (ExecutionException error) {
                    if (error.getCause() instanceof PreparedGalleryThumbnails.CacheFull) cacheFull = true;
                } catch (CancellationException ignored) {}
                offset++;
            }
            int slots = cacheFull ? 0 : previewSlots(app);
            while (pending.size() < slots && next < snapshot.items.size()) {
                NativeContentItem item = snapshot.items.get(next++);
                if (!PreparedGalleryThumbnails.eligible(item))
                    pending.add(CompletableFuture.completedFuture(false));
                else if (PreparedGalleryThumbnails.cached(app, item) != null)
                    pending.add(CompletableFuture.completedFuture(true));
                else pending.add(PREVIEW_IO.submit(() -> PreparedGalleryThumbnails.prepare(app, item)));
            }
            return thumbnails(app, key, token, snapshot.items.size(), ready, total, finished, offset);
        }

        void close() {
            for (Future<Boolean> future : pending) future.cancel(true);
            pending.clear();
        }
    }

    private Result prepareThumbnails(Context app, String key, String token,
            BunkrGallerySessionStore.Snapshot snapshot, long deadline, TurboBatch turbo, boolean incomplete)
            throws InterruptedException {
        int count = snapshot.items.size();
        synchronized (LOCK) {
            JSONObject current = read(app, key);
            if (current == null || !token.equals(current.optString("token"))) return Result.success();
            try {
                current.put("paginationFinished", true).put("paginationIncomplete", incomplete);
                if (!prefs(app).edit().putString(key, current.toString()).commit()) return Result.success();
            } catch (Exception error) { return Result.success(); }
        }
        while (!turbo.cacheFull && turbo.offset < count) {
            if (shouldStop(app, key, token) || SystemClock.elapsedRealtime() >= deadline) {
                if (!update(app, key, token, WAITING, count, "Thumbnail preparation will resume")) return Result.success();
                return Result.retry();
            }
            if (!turbo.pump(snapshot, true)) return Result.success();
            if (turbo.offset < count && !turbo.cacheFull) Thread.sleep(200);
        }
        int total = PreparedGalleryThumbnails.count(app, snapshot.items);
        int ready = PreparedGalleryThumbnails.ready(app, snapshot.items);
        if (!thumbnails(app, key, token, count, ready, total, true, turbo.offset)) return Result.success();
        boolean complete = !incomplete && ready == total && total > 0 && !turbo.cacheFull;
        update(app, key, token, complete ? COMPLETE : PARTIAL, count,
                ready + " / " + total + " thumbnails"
                        + (turbo.cacheFull ? " · Storage limit reached" : incomplete ? " · Source incomplete" : ""));
        return Result.success();
    }

    private static boolean cursorIncomplete(BunkrGallerySessionStore.Snapshot snapshot) {
        try { return new JSONObject(snapshot.cursor).optBoolean("indexIncomplete"); }
        catch (Exception ignored) { return false; }
    }

    static boolean ready(Context context, String key, String session) {
        JSONObject value = read(context, key);
        return value != null && session != null && session.equals(value.optString("session"))
                && COMPLETE.equals(value.optString("status"));
    }

    static boolean hasRecord(Context context, String key, String session) {
        JSONObject value = read(context, key);
        return value != null && session != null && session.equals(value.optString("session"));
    }

    static String label(Context context, String key, String session) {
        JSONObject value = read(context, key);
        if (value == null || session == null || !session.equals(value.optString("session")))
            return "Load entire gallery";
        String status = value.optString("status");
        String label = RUNNING.equals(status) ? "Indexing gallery" : COMPLETE.equals(status)
                ? "Gallery ready" : PARTIAL.equals(status) ? "Gallery partially prepared" : status;
        if (PREPARING.equals(status) || COMPLETE.equals(status) || PARTIAL.equals(status))
            return label + " · " + value.optInt("ready") + "/" + value.optInt("total") + " previews  ›";
        if (RUNNING.equals(status)) return label + " · " + value.optInt("items") + " items · " + value.optInt("ready") + " previews  ›";
        return label + " · " + value.optInt("items") + " items  ›";
    }

    static void showQueue(Activity activity) {
        ArrayList<JSONObject> tasks = records(activity);
        ArrayList<String> lines = new ArrayList<>();
        for (JSONObject task : tasks) lines.add(task.optString("title") + "\n"
                + task.optString("status") + " · " + task.optInt("items") + " items"
                + (task.optString("detail").isEmpty() ? "" : "\n" + task.optString("detail")));
        AlertDialog.Builder builder = new AlertDialog.Builder(activity).setTitle("Gallery Queue")
                .setNegativeButton("Close", null);
        if (lines.isEmpty()) builder.setMessage("No gallery requests yet.");
        else builder.setItems(lines.toArray(new String[0]), (dialog, position) -> {
            JSONObject task = tasks.get(position);
            ArrayList<String> actions = new ArrayList<>();
            actions.add("Open gallery");
            if (active(task)) actions.add("Cancel");
            else if (FAILED.equals(task.optString("status")) || CANCELLED.equals(task.optString("status")) || PARTIAL.equals(task.optString("status"))) actions.add("Retry");
            new AlertDialog.Builder(activity).setTitle(task.optString("title"))
                    .setItems(actions.toArray(new String[0]), (d, action) -> {
                        if (action == 0) {
                            Intent intent = NativeFeedBrowserActivity.createCreatorGallery(activity,
                                    task.optString("title"), task.optString("query"),
                                    task.optString("profile"), task.optString("session"),
                                    task.optString("favorite"), list(task, "names"), list(task, "urls"),
                                    list(task, "images"));
                            intent.putStringArrayListExtra(NativeFeedBrowserActivity.EXTRA_CREATOR_ALIASES,
                                    list(task, "aliases"));
                            activity.startActivity(intent);
                        } else if (active(task)) cancel(activity, task);
                        else enqueue(activity, task.optString("key"), task.optString("session"),
                                task.optString("title"), task.optString("query"), task.optString("profile"),
                                task.optString("favorite"), list(task, "names"), list(task, "urls"),
                                list(task, "images"), list(task, "aliases"));
                    }).setNegativeButton("Close", null).show();
        });
        builder.setNeutralButton("Refresh", (d, w) -> showQueue(activity)).show();
    }
}
