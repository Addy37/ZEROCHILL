package com.webapp.crazyshit;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Animatable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.transition.TransitionFactory;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Bounded image reveals; layouts and loaded images keep their intrinsic dimensions. */
final class ThumbnailFades {
    private static final int MAX_ACTIVE = 4;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static int active;
    private static final WeakHashMap<View, Long> LAST_SCROLL = new WeakHashMap<>();
    private static final WeakHashMap<View, ViewTreeObserver.OnScrollChangedListener> WATCHERS =
            new WeakHashMap<>();
    private static final DrawableTransitionOptions THUMBNAIL = options(200);
    private static final DrawableTransitionOptions AVATAR = options(140);
    private static final DrawableTransitionOptions HERO = options(240);

    private ThumbnailFades() {}

    static DrawableTransitionOptions thumbnail() { return THUMBNAIL; }
    static DrawableTransitionOptions avatar() { return AVATAR; }
    static DrawableTransitionOptions hero() { return HERO; }

    static TransitionFactory<Drawable> factoryForTest(int durationMs) {
        return factory(durationMs);
    }

    private static DrawableTransitionOptions options(int durationMs) {
        return DrawableTransitionOptions.with(factory(durationMs));
    }

    private static TransitionFactory<Drawable> factory(int durationMs) {
        return (source, firstResource) -> (resource, adapter) -> {
            View view = adapter.getView();
            if (resource instanceof Animatable || !shouldStart(source, view))
                return false;
            FadeDrawable fade = new FadeDrawable(resource, view, durationMs);
            active++;
            // A recycled or offscreen view may never draw again; expire its slot regardless.
            MAIN.postDelayed(fade::finish, durationMs);
            adapter.setDrawable(fade);
            return true;
        };
    }

    static boolean shouldStart(DataSource source, View view) {
        return source != DataSource.MEMORY_CACHE && active < MAX_ACTIVE && eligible(view);
    }

    static boolean eligible(View view) {
        if (view == null || !view.isAttachedToWindow() || !view.isShown()
                || view.getWindowVisibility() != View.VISIBLE
                || !ZeroChillMotion.animationsEnabled(view.getContext())) return false;
        View root = view.getRootView();
        watchScroll(root);
        Long lastScroll = LAST_SCROLL.get(root);
        if (lastScroll != null && SystemClock.uptimeMillis() - lastScroll < 100L) return false;
        for (ViewParent parent = view.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof RecyclerView
                    && ((RecyclerView) parent).getScrollState() != RecyclerView.SCROLL_STATE_IDLE)
                return false;
        }
        return view.getGlobalVisibleRect(new Rect());
    }

    private static void watchScroll(View root) {
        if (WATCHERS.containsKey(root)) return;
        WeakReference<View> weakRoot = new WeakReference<>(root);
        ViewTreeObserver.OnScrollChangedListener watcher = () -> {
            View current = weakRoot.get();
            if (current != null) LAST_SCROLL.put(current, SystemClock.uptimeMillis());
        };
        root.getViewTreeObserver().addOnScrollChangedListener(watcher);
        WATCHERS.put(root, watcher);
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}
            @Override public void onViewDetachedFromWindow(View v) {
                ViewTreeObserver observer = v.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnScrollChangedListener(watcher);
                WATCHERS.remove(v);
                LAST_SCROLL.remove(v);
                v.removeOnAttachStateChangeListener(this);
            }
        });
    }

    /** Only the new image is held, so a recycled previous Glide resource cannot be drawn. */
    static final class FadeDrawable extends Drawable implements Drawable.Callback {
        private final Drawable content;
        private final WeakReference<View> view;
        private final int durationMs;
        private final long startMs;
        private boolean finished;
        private int alpha = 255;
        private final Runnable tick = this::invalidateSelf;

        FadeDrawable(Drawable content, View view, int durationMs) {
            this.content = content;
            this.view = new WeakReference<>(view);
            this.durationMs = durationMs;
            this.startMs = SystemClock.uptimeMillis();
            content.setCallback(this);
        }

        @Override public void draw(@NonNull Canvas canvas) {
            View target = view.get();
            if (!(target instanceof ImageView) || ((ImageView) target).getDrawable() != this) {
                finish();
                return;
            }
            if (!finished && !eligible(target)) finish();
            if (((ImageView) target).getDrawable() != this) {
                if (((ImageView) target).getDrawable() == content) content.draw(canvas);
                return;
            }
            long elapsed = SystemClock.uptimeMillis() - startMs;
            if (!finished && elapsed >= durationMs) finish();
            if (((ImageView) target).getDrawable() != this) {
                if (((ImageView) target).getDrawable() == content) content.draw(canvas);
                return;
            }
            int opacity = finished ? alpha : (int) (alpha * Math.max(0L, elapsed) / durationMs);
            Rect bounds = getBounds();
            if (opacity == 255) {
                content.draw(canvas);
                return;
            }
            int layer = canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom,
                    opacity);
            content.draw(canvas);
            canvas.restoreToCount(layer);
            if (!finished) scheduleSelf(tick, SystemClock.uptimeMillis() + 16L);
        }

        void finish() {
            if (finished) return;
            finished = true;
            unscheduleSelf(tick);
            active--;
            View target = view.get();
            if (target instanceof ImageView && ((ImageView) target).getDrawable() == this) {
                ((ImageView) target).setImageDrawable(content);
            } else {
                if (content.getCallback() == this) content.setCallback(null);
            }
        }

        boolean isFinished() { return finished; }

        @Override protected void onBoundsChange(Rect bounds) { content.setBounds(bounds); }
        @Override public int getIntrinsicWidth() { return content.getIntrinsicWidth(); }
        @Override public int getIntrinsicHeight() { return content.getIntrinsicHeight(); }
        @Override public boolean isStateful() { return content.isStateful(); }
        @Override protected boolean onStateChange(int[] state) { return content.setState(state); }
        @Override protected boolean onLevelChange(int level) { return content.setLevel(level); }
        @Override public boolean setVisible(boolean visible, boolean restart) {
            content.setVisible(visible, restart);
            return super.setVisible(visible, restart);
        }
        @Override public void setAlpha(int alpha) { this.alpha = alpha; invalidateSelf(); }
        @Override public void setColorFilter(@Nullable ColorFilter filter) {
            content.setColorFilter(filter);
            invalidateSelf();
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public void invalidateDrawable(@NonNull Drawable who) { invalidateSelf(); }
        @Override public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what,
                long when) { scheduleSelf(what, when); }
        @Override public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
            unscheduleSelf(what);
        }
    }
}
