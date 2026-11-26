package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.bumptech.glide.signature.ObjectKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.function.Consumer;

/** One bounded, main-thread-owned preparation queue per open gallery, shared by its tabs. */
final class GalleryThumbnailPipeline {
    static final int SIZE = 384;
    private static final int MAX_PREFETCH_QUEUE = 24, MAX_RESULTS = 512;
    private int preparationColumns = 2;
    private boolean preparationConstrained;
    void setPreparationBudget(int columns, boolean constrained) {
        if (preparationColumns == columns && preparationConstrained == constrained) return;
        preparationColumns = columns;
        preparationConstrained = constrained;
        pump();
    }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final RequestManager glide;
    private final LinkedHashMap<String, Job> jobs = new LinkedHashMap<>();
    private final LinkedHashMap<String, Result> results = new LinkedHashMap<>(16, .75f, true);
    private final Cooldown cooldown = new Cooldown();
    // Presentation state is shared across tabs, but never written to disk.
    private final java.util.Set<String> revealed = new java.util.HashSet<>();
    boolean hasRevealed(String url) { return revealed.contains(url); }
    void markRevealed(String url) { revealed.add(url); }
    private int active;
    private boolean closed, paused;

    void setPaused(boolean value) {
        if (closed || paused == value) return;
        paused = value;
        if (!paused) pump();
    }
    private String retrySignature = java.util.UUID.randomUUID().toString();

    enum State { READY, OMIT, UNAVAILABLE }
    static final class Result {
        final State state;
        final GlideUrl model;
        final String signature;
        Result(State state, GlideUrl model, String signature) {
            this.state = state; this.model = model; this.signature = signature;
        }
    }
    static final class Cooldown {
        private int consecutive;
        private long until;
        void failure(int status, long now) {
            if (status != 502 && status != 503 && status != 504) { consecutive = 0; return; }
            if (++consecutive >= 3) { until = now + 30_000L; consecutive = 0; }
        }
        void success() { consecutive = 0; until = 0; }
        boolean skip(long now) { return now < until; }
    }
    private static final class Job {
        final NativeContentItem item;
        final GlideUrl preview, original;
        final LinkedHashMap<Object, Consumer<Result>> callbacks = new LinkedHashMap<>();
        boolean visible, running;
        CustomTarget<Drawable> target;
        Job(NativeContentItem item, GlideUrl preview, GlideUrl original) {
            this.item = item; this.preview = preview; this.original = original;
        }
    }

    GalleryThumbnailPipeline(Context context) { glide = Glide.with(context.getApplicationContext()); }

    static boolean supports(NativeContentItem item) {
        return GalleryBlackImage.eligible(item)
                || (item != null && item.isVideo() && OnlyHavenRepository.isOnlyHavenUrl(item.uploader));
    }

    static RequestBuilder<Drawable> request(RequestManager manager, GlideUrl model, String signature) {
        RequestBuilder<Drawable> request = manager.load(model).override(SIZE, SIZE)
                .dontTransform().disallowHardwareConfig().diskCacheStrategy(DiskCacheStrategy.ALL);
        if (!signature.isEmpty()) request = request.signature(new ObjectKey(signature));
        return request;
    }

    Result cached(String url) { return results.get(url); }

    void prepare(NativeContentItem item, GlideUrl preview, GlideUrl original, boolean visible,
            Object subscriber, Consumer<Result> callback) {
        prepare(item, preview, original, visible, false, subscriber, callback);
    }

    void prepare(NativeContentItem item, GlideUrl preview, GlideUrl original, boolean visible,
            boolean reservedBatchSlot, Object subscriber, Consumer<Result> callback) {
        if (closed) return;
        Result cached = results.get(item.url);
        if (cached != null) {
            if (callback != null) main.post(() -> {
                if (!closed) callback.accept(cached);
            });
            return;
        }
        Job job = jobs.get(item.url);
        if (job == null) {
            if (!visible && !reservedBatchSlot && jobs.size() >= MAX_PREFETCH_QUEUE) return;
            job = new Job(item, preview, original);
            jobs.put(item.url, job);
        }
        job.visible |= visible;
        if (subscriber != null && callback != null) job.callbacks.put(subscriber, callback);
        pump();
    }

    void unsubscribe(Object subscriber) {
        for (Job job : jobs.values()) job.callbacks.remove(subscriber);
    }

    void invalidateReady(String url) {
        Result result = results.get(url);
        if (result != null && result.state == State.READY) results.remove(url);
    }

    private void pump() {
        if (closed || paused) return;
        while (active < GalleryWorkload.preparationConcurrency(preparationColumns,
                preparationConstrained || cooldown.skip(SystemClock.elapsedRealtime()))) {
            Job next = null;
            for (Job job : jobs.values()) {
                if (job.running) continue;
                if (next == null || job.visible) next = job;
                if (job.visible) break;
            }
            if (next == null) return;
            next.running = true;
            active++;
            if (next.preview != null && !cooldown.skip(SystemClock.elapsedRealtime())) {
                start(next, next.preview, false, false);
            } else if (next.original != null) {
                start(next, next.original, true, false);
            } else {
                Job terminal = next;
                main.post(() -> complete(terminal, new Result(State.UNAVAILABLE, null, "")));
            }
        }
    }

    private boolean current(Job job, CustomTarget<Drawable> target) {
        return !closed && jobs.get(job.item.url) == job && job.target == target;
    }

    private void start(Job job, GlideUrl model, boolean original, boolean retry) {
        String signature = retry ? retrySignature : "";
        String stage = original ? (retry ? "grid_prepare_original_retry" : "grid_prepare_original")
                : "grid_prepare_thumbnail";
        CustomTarget<Drawable> target = new CustomTarget<Drawable>(SIZE, SIZE) {
            @Override public void onResourceReady(@NonNull Drawable resource,
                    @Nullable Transition<? super Drawable> transition) {
                if (!current(job, this)) return;
                if (!original) cooldown.success();
                GalleryDiagnostics.imageReady(stage, job.item, model.toStringUrl(),
                        resource.getIntrinsicWidth(), resource.getIntrinsicHeight());
                Consumer<Boolean> checked = black -> main.post(() -> {
                    if (!current(job, this)) return;
                    if (!black) complete(job, new Result(State.READY, model, signature));
                    else if (!original && job.original != null) advance(job, job.original, true, true);
                    else if (!retry && job.original != null) advance(job, job.original, true, true);
                    else {
                        GalleryDiagnostics.blackImage(job.item);
                        complete(job, new Result(State.OMIT, null, ""));
                    }
                });
                if (GalleryBlackImage.eligible(job.item)) GalleryBlackImage.check(resource, checked);
                else checked.accept(false);
            }
            @Override public void onLoadCleared(@Nullable Drawable placeholder) { }
        };
        job.target = target;
        request(glide, model, signature).priority(job.visible ? com.bumptech.glide.Priority.HIGH
                : com.bumptech.glide.Priority.NORMAL).listener(new RequestListener<Drawable>() {
            @Override public boolean onLoadFailed(@Nullable GlideException error, Object ignored,
                    Target<Drawable> ignoredTarget, boolean first) {
                if (!current(job, target)) return true;
                GalleryDiagnostics.imageFailure(stage, job.item, model.toStringUrl(),
                        job.item.uploader, model, error);
                if (!original) cooldown.failure(httpStatus(error), SystemClock.elapsedRealtime());
                main.post(() -> {
                    if (!current(job, target)) return;
                    if (!original && job.original != null) advance(job, job.original, true, false);
                    else if (original && !retry) advance(job, model, true, true);
                    else {
                        if (original) GalleryDiagnostics.unavailableImage(job.item);
                        complete(job, new Result(original ? State.OMIT : State.UNAVAILABLE, null, ""));
                    }
                });
                return true;
            }
            @Override public boolean onResourceReady(Drawable resource, Object model,
                    Target<Drawable> ignoredTarget, DataSource source, boolean first) { return false; }
        }).into(target);
    }

    private static int httpStatus(GlideException error) {
        if (error != null) for (Throwable cause : error.getRootCauses())
            if (cause instanceof com.bumptech.glide.load.HttpException)
                return ((com.bumptech.glide.load.HttpException) cause).getStatusCode();
        return 0;
    }

    private void advance(Job job, GlideUrl model, boolean original, boolean retry) {
        clearTarget(job);
        start(job, model, original, retry);
    }

    private void clearTarget(Job job) {
        CustomTarget<Drawable> previous = job.target;
        job.target = null;
        if (previous != null) glide.clear(previous);
    }

    private void complete(Job job, Result result) {
        if (jobs.get(job.item.url) != job) return;
        jobs.remove(job.item.url);
        active--;
        results.put(job.item.url, result);
        while (results.size() > MAX_RESULTS) results.remove(results.keySet().iterator().next());
        // Subscribers start a matching Glide display request while the prepared resource is held.
        for (Consumer<Result> callback : new ArrayList<>(job.callbacks.values())) callback.accept(result);
        clearTarget(job);
        pump();
    }

    void reset() {
        ArrayList<Job> previous = new ArrayList<>(jobs.values());
        jobs.clear(); results.clear(); active = 0; cooldown.success();
        retrySignature = java.util.UUID.randomUUID().toString();
        for (Job job : previous) clearTarget(job);
    }

    void close() { closed = true; reset(); revealed.clear(); }
}
