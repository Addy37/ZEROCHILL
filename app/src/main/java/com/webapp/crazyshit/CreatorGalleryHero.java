package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

/** Bounded, current-gallery photo rotation with two independent Glide owners. */
final class CreatorGalleryHero {
    static final long ROTATION_MS = 13_000L;
    static final int MAX_PHOTOS = 48;
    private final Context context;
    private final com.bumptech.glide.RequestManager requests;
    private final Slot[] slots;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String, Photo> photos = new LinkedHashMap<>();
    private final Random random = new Random();
    private final Runnable rotation = this::onRotationTimer;
    private Slot visible;
    private Slot pending;
    private Slot retiring;
    private android.animation.ValueAnimator fade;
    private long generation;
    private boolean active;
    private boolean closed;
    private boolean scheduled;
    private String displayedUrl = "";
    private Photo fallback;
    private boolean fallbackFailed;
    private Photo waitingForLayout;
    private final android.view.View.OnLayoutChangeListener layoutReady = (view, l, t, r, b, ol, ot, or, ob) -> {
        if (waitingForLayout != null && active && !closed && r > l && b > t) {
            Photo ready = waitingForLayout;
            waitingForLayout = null;
            request(ready);
        }
    };

    private static final class Slot {
        final ImageView view;
        CustomTarget<Drawable> owner;
        Slot(ImageView view) { this.view = view; }
    }
    private static final class Photo {
        final String url, referer;
        Photo(String url, String referer) { this.url = url; this.referer = referer; }
    }

    CreatorGalleryHero(ImageView first, ImageView second) {
        context = first.getContext();
        requests = Glide.with(first);
        slots = new Slot[]{new Slot(first), new Slot(second)};
        visible = slots[0];
        second.setAlpha(0f);
        first.addOnLayoutChangeListener(layoutReady);
    }

    void fallback(String url, String referer) {
        if (closed || !photos.isEmpty() || !http(url)) return;
        if (fallback == null || !url.equals(fallback.url)) fallbackFailed = false;
        fallback = new Photo(url, referer);
        if (active && !fallbackFailed && !url.equals(displayedUrl) && pending == null) request(fallback);
    }

    void items(List<NativeContentItem> items, boolean append) {
        if (closed) return;
        if (!append) { photos.clear(); cancelPending(); }
        if (items != null) for (NativeContentItem item : items) {
            if (item == null || !item.isImage()) continue; // Never decode video/poster frames.
            String url = photoUrl(item);
            if (!http(url) || photos.containsKey(url)) continue;
            if (photos.size() >= MAX_PHOTOS) break;
            String referer = photoReferer(item);
            photos.put(url, new Photo(url, referer));
        }
        if (active && pending == null && !photos.isEmpty() && !photos.containsKey(displayedUrl)) chooseNext();
        else if (active && photos.isEmpty() && displayedUrl.isEmpty()) tryFallback();
        else schedule();
    }

    void active(boolean value) {
        if (closed || active == value) return;
        active = value;
        if (!active) {
            handler.removeCallbacksAndMessages(null);
            scheduled = false;
            cancelPending();
            finishFade();
            return;
        }
        if (displayedUrl.isEmpty()) {
            if (!photos.isEmpty()) chooseNext();
            else tryFallback();
        } else schedule();
    }

    private void tryFallback() {
        if (active && !closed && pending == null && fallback != null && !fallbackFailed) request(fallback);
    }

    private void onRotationTimer() {
        scheduled = false;
        if (ZeroChillMotion.animationsEnabled(context)) chooseNext();
    }

    private void chooseNext() {
        if (!active || closed || pending != null) return;
        List<Photo> choices = new ArrayList<>();
        for (Photo photo : photos.values()) if (!photo.url.equals(displayedUrl)) choices.add(photo);
        if (choices.isEmpty()) { schedule(); return; }
        request(choices.get(random.nextInt(choices.size())));
    }

    private void request(Photo photo) {
        if (!active || closed || pending != null || photo.url.equals(displayedUrl)) return;
        finishFade();
        Slot next = visible == slots[0] ? slots[1] : slots[0];
        clear(next);
        pending = next;
        long request = ++generation;
        int width = Math.max(1, visible.view.getWidth());
        int height = Math.max(1, visible.view.getHeight());
        // Wait for normal layout, never decode a 1px stand-in and upscale it later.
        if (width <= 1 || height <= 1) {
            pending = null;
            waitingForLayout = photo;
            return;
        }
        CustomTarget<Drawable> target = new CustomTarget<Drawable>(width, height) {
            @Override public void onResourceReady(Drawable resource, Transition<? super Drawable> transition) {
                // Post outside Glide's callback: retiring another target inside it is illegal.
                handler.post(() -> {
                    if (closed || !active || request != generation || pending != next || next.owner != this) return;
                    Slot outgoing = visible;
                    next.view.setImageDrawable(resource);
                    next.view.bringToFront();
                    visible = next;
                    pending = null;
                    displayedUrl = photo.url;
                    if (outgoing.owner != null && ZeroChillMotion.animationsEnabled(context)) {
                        retiring = outgoing;
                        next.view.setAlpha(0f);
                        fade = android.animation.ValueAnimator.ofFloat(0f, 1f);
                        fade.setDuration(200L);
                        fade.setInterpolator(ZeroChillMotion.EASE_OUT);
                        fade.addUpdateListener(a -> next.view.setAlpha((float) a.getAnimatedValue()));
                        fade.addListener(new android.animation.AnimatorListenerAdapter() {
                            @Override public void onAnimationEnd(android.animation.Animator animation) {
                                if (fade == animation) finishFade();
                            }
                        });
                        fade.start();
                    } else {
                        next.view.setAlpha(1f);
                        clear(outgoing);
                    }
                    if (photos.isEmpty() && fallback != null && !fallbackFailed && !fallback.url.equals(displayedUrl)) request(fallback);
                    else schedule();
                });
            }
            @Override public void onLoadFailed(Drawable errorDrawable) {
                handler.post(() -> {
                    if (request != generation || pending != next || next.owner != this) return;
                    pending = null;
                    clear(next);
                    photos.remove(photo.url);
                    if (fallback != null && fallback.url.equals(photo.url)) fallbackFailed = true;
                    if (!photos.isEmpty() && !photos.containsKey(displayedUrl)) chooseNext();
                    else if (photos.isEmpty() && displayedUrl.isEmpty()) tryFallback();
                    else schedule(); // Retain current artwork; retry another photo on the next tick.
                });
            }
            @Override public void onLoadCleared(Drawable placeholder) {
                if (next.owner == this) next.view.setImageDrawable(null);
            }
        };
        next.owner = target;
        LazyHeaders.Builder headers = new LazyHeaders.Builder();
        if (photo.referer != null && !photo.referer.isEmpty()) headers.addHeader("Referer", photo.referer);
        requests.load(new GlideUrl(photo.url, headers.build()))
                .diskCacheStrategy(DiskCacheStrategy.ALL).centerCrop().dontAnimate().into(target);
    }

    private void schedule() {
        if (closed || !active || pending != null || scheduled || photos.size() <= 1
                || !ZeroChillMotion.animationsEnabled(context)) return;
        scheduled = true;
        handler.postDelayed(rotation, ROTATION_MS);
    }

    private void finishFade() {
        android.animation.ValueAnimator previous = fade;
        fade = null;
        if (previous != null) {
            previous.removeAllListeners();
            previous.removeAllUpdateListeners();
            previous.cancel();
        }
        visible.view.setAlpha(1f);
        Slot outgoing = retiring;
        retiring = null;
        if (outgoing != null && outgoing != visible) clear(outgoing);
    }

    private void cancelPending() {
        generation++;
        waitingForLayout = null;
        if (pending != null) { clear(pending); pending = null; }
    }

    private void clear(Slot slot) {
        slot.view.setImageDrawable(null);
        CustomTarget<Drawable> owner = slot.owner;
        slot.owner = null;
        if (owner != null) requests.clear(owner);
    }

    void close() {
        if (closed) return;
        active(false);
        finishFade();
        closed = true;
        handler.removeCallbacksAndMessages(null);
        photos.clear();
        slots[0].view.removeOnLayoutChangeListener(layoutReady);
        for (Slot slot : slots) clear(slot);
    }

    private static String photoReferer(NativeContentItem item) {
        if (WikiFeetRepository.isWikiFeetUrl(item.url) && WikiFeetRepository.isWikiFeetUrl(item.uploader)) return item.uploader;
        if (!FapelloRepository.isPostUrl(item.url) && FapelloRepository.isModelUrl(item.uploader)) return item.uploader;
        if (OnlyHavenRepository.isOnlyHavenUrl(item.uploader)) return item.uploader;
        return item.url;
    }

    private static String photoUrl(NativeContentItem item) {
        String original = FapelloRepository.photoUrlFromItem(item);
        if (http(original)) return original;
        if (OnlyHavenRepository.isOnlyHavenUrl(item.uploader) && http(item.url)) return item.url;
        return http(item.imageUrl) ? item.imageUrl : item.url;
    }

    private static boolean http(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://"));
    }
}
