package com.webapp.crazyshit;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;

import com.bumptech.glide.request.Request;
import com.bumptech.glide.request.target.DrawableImageViewTarget;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

/** Decorative pixels only. One bounded capture per tab per process, never on scroll. */
final class ColdTabSnapshot implements ViewTreeObserver.OnPreDrawListener,
        View.OnAttachStateChangeListener {
    private static final Set<String> STARTED = new HashSet<>();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final int MAX_EDGE = 512;
    private static final long FADE_MS = 450;
    private static final long MAX_WAIT_MS = 15_000;
    private final FrameLayout root;
    private final ScrollView live;
    private final ImageView hero;
    private final BooleanSupplier loaded;
    private final BooleanSupplier usable;
    private final File file;
    private ImageView cover;
    private Bitmap pixels;
    private boolean done;
    private boolean coverFading;
    private boolean released;
    private long started;
    private long readySince;
    private long nextCheck;

    static ColdTabSnapshot install(FrameLayout root, ScrollView live, ImageView hero,
            String tab, BooleanSupplier loaded) {
        return install(root, live, hero, tab, loaded, null);
    }

    /** Reveal usable live content early; keep the stronger gate for saving a new snapshot. */
    static ColdTabSnapshot install(FrameLayout root, ScrollView live, ImageView hero,
            String tab, BooleanSupplier loaded, BooleanSupplier usable) {
        return STARTED.add(tab) ? new ColdTabSnapshot(root, live, hero, tab, loaded, usable) : null;
    }

    private ColdTabSnapshot(FrameLayout root, ScrollView live, ImageView hero,
            String tab, BooleanSupplier loaded, BooleanSupplier usable) {
        this.root = root;
        this.live = live;
        this.hero = hero;
        this.loaded = loaded;
        this.usable = usable;
        file = new File(root.getContext().getNoBackupFilesDir(), "cold-tab-" + tab + ".jpg");
        root.addOnAttachStateChangeListener(this);
        IO.execute(() -> {
            Bitmap saved = read(file);
            root.post(() -> {
                if (done || released || saved == null) {
                    if (saved != null) saved.recycle();
                    return;
                }
                pixels = saved;
                cover = new ImageView(root.getContext());
                cover.setScaleType(ImageView.ScaleType.FIT_XY);
                cover.setImageBitmap(saved);
                cover.setClickable(false);
                cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                root.addView(cover, new FrameLayout.LayoutParams(-1, -1));
            });
        });
    }

    @Override public void onViewAttachedToWindow(View view) {
        root.getViewTreeObserver().addOnPreDrawListener(this);
    }

    @Override public void onViewDetachedFromWindow(View view) {
        if (root.getViewTreeObserver().isAlive()) {
            root.getViewTreeObserver().removeOnPreDrawListener(this);
        }
        removeCover();
        readySince = 0;
        // Pager recycling must not cancel a future capture after returning to this tab.
    }

    @Override public boolean onPreDraw() {
        if (done || !root.isShown() || root.getWidth() == 0 || root.getHeight() == 0) return true;
        if (!root.getGlobalVisibleRect(new Rect())) {
            readySince = 0;
            return true;
        }
        long now = SystemClock.uptimeMillis();
        if (now < nextCheck) return true;
        nextCheck = now + 100;
        if (started == 0) started = now;
        // A failure must expose the normal loader/error UI rather than cover it forever.
        if (now - started >= MAX_WAIT_MS) removeCover();
        if (live.getScrollY() != 0) {
            fadeCoverForInteraction();
            readySince = 0;
            return true;
        }
        Request heroRequest = new DrawableImageViewTarget(hero).getRequest();
        if (!released && usable != null && usable.getAsBoolean()
                && heroRequest != null && heroRequest.isComplete()) {
            released = true;
            fadeCoverForInteraction();
        }
        boolean ready = loaded.getAsBoolean() && heroRequest != null
                && heroRequest.isComplete() && visibleImagesSettled(live);
        if (!ready) readySince = 0;
        else if (readySince == 0) readySince = now;
        // Let existing image transitions finish and the first visible rails bind.
        if (readySince != 0 && now - readySince >= 500 && !coverFading) {
            finish(true);
        } else if (cover != null || readySince != 0) root.postInvalidateDelayed(100);
        return true;
    }

    boolean isCoverVisible() { return cover != null; }

    private boolean visibleImagesSettled(View view) {
        Rect bounds = new Rect();
        Rect viewport = new Rect();
        if (!root.getGlobalVisibleRect(viewport) || !view.isShown()
                || !view.getGlobalVisibleRect(bounds) || !Rect.intersects(bounds, viewport)) return true;
        if (view instanceof ImageView) {
            Request request = new DrawableImageViewTarget((ImageView) view).getRequest();
            if (request != null && request.isRunning()) return false;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (!visibleImagesSettled(group.getChildAt(i))) return false;
            }
        }
        return true;
    }

    private void capture() {
        if (!root.isAttachedToWindow() || !root.isShown() || live.getScrollY() != 0) return;
        try {
            android.content.Context context = root.getContext();
            while (context instanceof android.content.ContextWrapper
                    && !(context instanceof android.app.Activity)) {
                context = ((android.content.ContextWrapper) context).getBaseContext();
            }
            if (android.os.Build.VERSION.SDK_INT >= 26 && context instanceof android.app.Activity) {
                int width = live.getWidth(), height = live.getHeight();
                float scale = Math.min(1f, (float) MAX_EDGE / Math.max(width, height));
                Bitmap raw = Bitmap.createBitmap(Math.max(1, Math.round(width * scale)),
                        Math.max(1, Math.round(height * scale)), Bitmap.Config.ARGB_8888);
                int[] position = new int[2];
                live.getLocationInWindow(position);
                Rect area = new Rect();
                live.getLocalVisibleRect(area);
                area.offset(position[0], position[1]);
                try {
                    android.view.PixelCopy.request(((android.app.Activity) context).getWindow(),
                        area, raw, result -> {
                            if (result == android.view.PixelCopy.SUCCESS) saveSnapshot(raw);
                            else raw.recycle();
                        }, new android.os.Handler(android.os.Looper.getMainLooper()));
                } catch (RuntimeException | OutOfMemoryError failure) {
                    raw.recycle();
                    throw failure;
                }
            } else {
                float scale = Math.min(1f, (float) MAX_EDGE / Math.max(live.getWidth(), live.getHeight()));
                Bitmap raw = Bitmap.createBitmap(Math.max(1, Math.round(live.getWidth() * scale)),
                        Math.max(1, Math.round(live.getHeight() * scale)), Bitmap.Config.ARGB_8888);
                try {
                    Canvas canvas = new Canvas(raw);
                    canvas.scale(scale, scale);
                    live.draw(canvas);
                    saveSnapshot(raw);
                } catch (RuntimeException | OutOfMemoryError failure) {
                    raw.recycle();
                    throw failure;
                }
            }
        } catch (RuntimeException | OutOfMemoryError ignored) { /* Keep the normal UI. */ }
    }

    private void saveSnapshot(Bitmap bitmap) {
        IO.execute(() -> {
            try {
                GalleryPreviewBlur.soften(bitmap, 6);
                new Canvas(bitmap).drawColor(Color.argb(45, 0, 0, 0));
                write(file, bitmap);
            } catch (RuntimeException | OutOfMemoryError ignored) {
                // Decorative failure leaves live content and any previous file untouched.
            } finally { bitmap.recycle(); }
        });
    }

    private void finish(boolean fade) {
        if (done) return;
        done = true;
        if (root.getViewTreeObserver().isAlive()) {
            root.getViewTreeObserver().removeOnPreDrawListener(this);
        }
        root.removeOnAttachStateChangeListener(this);
        Runnable revealed = () -> {
            removeCover();
            // PixelCopy must run after the decorative cover has left the next frame.
            if (fade) live.postOnAnimation(() -> live.postOnAnimation(this::capture));
        };
        if (fade && cover != null) {
            cover.animate().alpha(0f).setDuration(FADE_MS).withEndAction(revealed).start();
        } else revealed.run();
    }

    private void fadeCoverForInteraction() {
        if (cover == null || coverFading) return;
        coverFading = true;
        cover.animate().alpha(0f).setDuration(180).withEndAction(this::removeCover).start();
    }

    private void removeCover() {
        if (cover != null) {
            cover.animate().cancel();
            cover.setImageDrawable(null);
            root.removeView(cover);
            cover = null;
        }
        coverFading = false;
        if (pixels != null) {
            pixels.recycle();
            pixels = null;
        }
    }

    static Bitmap read(File file) {
        try {
            AtomicFile atomic = new AtomicFile(file);
            try (java.io.FileInputStream input = atomic.openRead()) {
                if (input.getChannel().size() > 262_144) return null;
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(input, null, bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                        || bounds.outWidth > MAX_EDGE || bounds.outHeight > MAX_EDGE) return null;
            }
            try (java.io.FileInputStream input = atomic.openRead()) {
                return BitmapFactory.decodeStream(input);
            }
        } catch (Exception | OutOfMemoryError ignored) { return null; }
    }

    static void write(File file, Bitmap bitmap) {
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream output = null;
        try {
            output = atomic.startWrite();
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)) {
                throw new java.io.IOException("Snapshot compression failed");
            }
            atomic.finishWrite(output);
        } catch (Exception ignored) {
            if (output != null) atomic.failWrite(output);
        }
    }
}
