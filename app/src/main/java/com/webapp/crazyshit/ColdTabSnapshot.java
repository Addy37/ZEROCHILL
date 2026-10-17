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
    private static final long FADE_MS = 450;
    private static final long MAX_WAIT_MS = 15_000;
    private final FrameLayout root;
    private final ScrollView live;
    private final ImageView hero;
    private final BooleanSupplier loaded;
    private final File file;
    private ImageView cover;
    private Bitmap pixels;
    private boolean done;
    private long started;
    private long readySince;
    private long nextCheck;

    static ColdTabSnapshot install(FrameLayout root, ScrollView live, ImageView hero,
            String tab, BooleanSupplier loaded) {
        return STARTED.add(tab) ? new ColdTabSnapshot(root, live, hero, tab, loaded) : null;
    }

    private ColdTabSnapshot(FrameLayout root, ScrollView live, ImageView hero,
            String tab, BooleanSupplier loaded) {
        this.root = root;
        this.live = live;
        this.hero = hero;
        this.loaded = loaded;
        file = new File(root.getContext().getNoBackupFilesDir(), "cold-tab-" + tab + ".jpg");
        root.addOnAttachStateChangeListener(this);
        IO.execute(() -> {
            Bitmap saved = read(file);
            root.post(() -> {
                if (done || saved == null) {
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

    @Override public void onViewDetachedFromWindow(View view) { finish(false); }

    @Override public boolean onPreDraw() {
        if (done || !root.isShown() || root.getWidth() == 0 || root.getHeight() == 0) return true;
        long now = SystemClock.uptimeMillis();
        if (now < nextCheck) return true;
        nextCheck = now + 100;
        if (started == 0) started = now;
        // A failure must expose the normal loader/error UI rather than cover it forever.
        if (now - started >= MAX_WAIT_MS) removeCover();
        if (now - started >= 60_000 || live.getScrollY() != 0) {
            finish(false);
            return true;
        }
        Request heroRequest = new DrawableImageViewTarget(hero).getRequest();
        boolean ready = loaded.getAsBoolean() && heroRequest != null
                && heroRequest.isComplete() && visibleImagesSettled(live);
        if (!ready) readySince = 0;
        else if (readySince == 0) readySince = now;
        // Let existing image transitions finish and the first visible rails bind.
        if (readySince != 0 && now - readySince >= 500) {
            finish(true);
        } else root.postInvalidateDelayed(100);
        return true;
    }

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
                float scale = Math.min(1f, 128f / Math.max(width, height));
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
                            try {
                                if (result == android.view.PixelCopy.SUCCESS) {
                                    saveBlurred(GalleryPreviewBlur.snapshot(live, canvas ->
                                            canvas.drawBitmap(raw, null, new Rect(0, 0, width, height), null)));
                                }
                            } catch (RuntimeException | OutOfMemoryError ignored) {
                                // Capture failure never changes live UI.
                            } finally { raw.recycle(); }
                        }, new android.os.Handler(android.os.Looper.getMainLooper()));
                } catch (RuntimeException | OutOfMemoryError failure) {
                    raw.recycle();
                    throw failure;
                }
            } else saveBlurred(GalleryPreviewBlur.snapshot(live, live::draw));
        } catch (RuntimeException | OutOfMemoryError ignored) { /* Keep the normal UI. */ }
    }

    private void saveBlurred(Bitmap bitmap) {
        if (bitmap == null) return;
        // Store only softened, darkened pixels, including on Android before API 31.
        new Canvas(bitmap).drawColor(Color.argb(55, 0, 0, 0));
        IO.execute(() -> {
            write(file, bitmap);
            bitmap.recycle();
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

    private void removeCover() {
        if (cover != null) {
            cover.setImageDrawable(null);
            root.removeView(cover);
            cover = null;
        }
        if (pixels != null) {
            pixels.recycle();
            pixels = null;
        }
    }

    static Bitmap read(File file) {
        try {
            AtomicFile atomic = new AtomicFile(file);
            try (java.io.FileInputStream input = atomic.openRead()) {
                if (input.getChannel().size() > 32_768) return null;
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(input, null, bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                        || bounds.outWidth > 128 || bounds.outHeight > 128) return null;
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
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 65, output)) {
                throw new java.io.IOException("Snapshot compression failed");
            }
            atomic.finishWrite(output);
        } catch (Exception ignored) {
            if (output != null) atomic.failWrite(output);
        }
    }
}
