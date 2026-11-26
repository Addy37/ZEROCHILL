package com.webapp.crazyshit;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.transition.Transition;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.WeakHashMap;

/** First-visible presentation only. The existing Glide request still owns the decoded artwork. */
final class SharedArtworkReveal {
    private static final WeakHashMap<View, Session> SESSIONS = new WeakHashMap<>();
    private static final int MAX_SEEN = 4096;
    static final long MAX_HIDDEN_MS = 750L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<ImageView, Reveal> ACTIVE = new WeakHashMap<>();
    private SharedArtworkReveal() {}

    static DrawableTransitionOptions artwork(String scope, String key, boolean round) {
        return DrawableTransitionOptions.with((source, first) -> transition(scope, key, round));
    }

    static Transition<Drawable> transition(String scope, String key, boolean round) {
        final String identity = scope + ":" + key;
        return (resource, adapter) -> {
            View target = adapter.getView();
            if (!(target instanceof ImageView)) return false;
            ImageView image = (ImageView) target;
            finishNow(image);
            // Never substitute a wrapper for the actual Glide drawable.
            adapter.setDrawable(resource);
            if (!(resource instanceof Animatable) && key != null && !key.isEmpty()
                    && ZeroChillMotion.animationsEnabled(image.getContext())) {
                start(image, resource, identity, round);
            }
            return true;
        };
    }

    private static Session session(View target) {
        View root = target.getRootView();
        Session state = SESSIONS.get(root);
        if (state == null) {
            state = new Session();
            SESSIONS.put(root, state);
        }
        return state;
    }

    static final class Session {
        private final LinkedHashMap<String, Boolean> displayed = new LinkedHashMap<>(16, .75f, true);
        private long nextStart;
        boolean seen(String key) { return displayed.get(key) != null; }
        void mark(String key) {
            displayed.put(key, Boolean.TRUE);
            while (displayed.size() > MAX_SEEN) displayed.remove(displayed.keySet().iterator().next());
        }
        long reserve(long now, int columns) {
            // Overlap dense reveals, but never queue an entire screen behind an animation.
            long start = Math.max(now, Math.min(nextStart, now + 150L));
            nextStart = start + GalleryWorkload.staggerMs(columns, false);
            return start;
        }
    }

    static boolean pending(ImageView view) {
        Reveal reveal = ACTIVE.get(view);
        return reveal != null && reveal.installed && reveal.current(view);
    }

    static void finishNow(ImageView view) {
        Reveal reveal = ACTIVE.get(view);
        if (reveal != null) reveal.finish();
    }

    static void start(ImageView view, Drawable resource, String identity, boolean round) {
        finishNow(view);
        if (view.isAttachedToWindow() && session(view).seen(identity)) return;
        Reveal reveal = new Reveal(view, resource, identity, round);
        ACTIVE.put(view, reveal);
        reveal.begin();
    }

    private static int columns(View view) {
        for (ViewParent parent = view.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof RecyclerView) {
                RecyclerView.LayoutManager manager = ((RecyclerView) parent).getLayoutManager();
                if (manager instanceof androidx.recyclerview.widget.GridLayoutManager)
                    return ((androidx.recyclerview.widget.GridLayoutManager) manager).getSpanCount();
                if (manager instanceof androidx.recyclerview.widget.StaggeredGridLayoutManager)
                    return ((androidx.recyclerview.widget.StaggeredGridLayoutManager) manager).getSpanCount();
                if (manager instanceof androidx.recyclerview.widget.LinearLayoutManager
                        && ((androidx.recyclerview.widget.LinearLayoutManager) manager).getOrientation()
                        == RecyclerView.HORIZONTAL) return 4;
            }
        }
        return 2;
    }

    private static boolean settling(View view) {
        for (ViewParent parent = view.getParent(); parent != null; parent = parent.getParent())
            if (parent instanceof RecyclerView && ((RecyclerView) parent).getScrollState()
                    == RecyclerView.SCROLL_STATE_SETTLING) return true;
        return false;
    }

    /** Only a decorative ViewOverlay is animated. The underlying image is always installed. */
    private static final class Reveal extends Drawable implements View.OnAttachStateChangeListener {
        private final WeakReference<ImageView> owner;
        private final WeakReference<Drawable> resource;
        private final String identity;
        private final boolean round;
        private final Rect visible = new Rect();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private final int matte = GalleryRevealImageView.MATTE;
        private final LinearGradient gradient = new LinearGradient(0f, 0f, 0f, 1f,
                new int[]{matte & 0x00ffffff, (matte & 0x00ffffff) | 0x28000000,
                        (matte & 0x00ffffff) | 0xd7000000, matte},
                new float[]{0f, .24f, .76f, 1f}, Shader.TileMode.CLAMP);
        private final long deadline = SystemClock.uptimeMillis() + MAX_HIDDEN_MS;
        private long start = -1L;
        private boolean finished, installed;
        private float progress;
        private final Runnable tick = this::advance;
        private final Runnable timeout = this::finish;

        Reveal(ImageView view, Drawable resource, String identity, boolean round) {
            owner = new WeakReference<>(view);
            this.resource = new WeakReference<>(resource);
            this.identity = identity;
            this.round = round;
        }

        boolean current(ImageView view) {
            return !finished && view != null && ACTIVE.get(view) == this
                    && resource.get() != null && view.getDrawable() == resource.get();
        }

        void begin() {
            ImageView view = owner.get();
            if (view == null) { finish(); return; }
            view.addOnAttachStateChangeListener(this);
            // Independent main-thread timeout, including if no drawing/animation callbacks occur.
            MAIN.postDelayed(timeout, MAX_HIDDEN_MS);
            MAIN.post(tick);
        }

        private void advance() {
            ImageView view = owner.get();
            if (!current(view)) { finish(); return; }
            long now = SystemClock.uptimeMillis();
            if (now >= deadline || !ZeroChillMotion.animationsEnabled(view.getContext())) {
                finish(); return;
            }
            boolean shown = view.isAttachedToWindow() && view.isShown()
                    && view.getWindowVisibility() == View.VISIBLE && view.getWidth() > 0
                    && view.getHeight() > 0 && view.getGlobalVisibleRect(visible);
            if (!shown) {
                if (installed) { finish(); return; }
                // Prefetched targets get a brief opportunity to become visible, but are never masked.
                MAIN.postDelayed(tick, 50L);
                return;
            }
            if (session(view).seen(identity)) { finish(); return; }
            if (!installed) {
                setBounds(0, 0, view.getWidth(), view.getHeight());
                view.getOverlay().add(this);
                installed = true;
                view.invalidate();
            }
            if (start < 0L && !settling(view))
                start = session(view).reserve(now, columns(view));
            if (start >= 0L && now < start && settling(view)) start = -1L;
            progress = start < 0L ? 0f
                    : Math.min(1f, Math.max(0f, (now - start) / (float) GalleryRevealImageView.DURATION_MS));
            if (progress >= 1f) { finish(); return; }
            setBounds(0, 0, view.getWidth(), view.getHeight());
            invalidateSelf();
            MAIN.postDelayed(tick, 16L);
        }

        @Override public void draw(@NonNull Canvas canvas) {
            ImageView view = owner.get();
            // Rebind, clearing, or a stale render must never conceal a different image.
            if (!current(view) || SystemClock.uptimeMillis() >= deadline) return;
            Rect bounds = getBounds();
            paint.setShader(null);
            paint.setColor(matte);
            if (progress > 0f) {
                float eased = 1f - (1f - progress) * (1f - progress);
                float band = Math.max(1f, bounds.height() * .65f);
                matrix.setScale(1f, band);
                matrix.postTranslate(0f, bounds.top + GalleryRevealImageView.front(eased, bounds.height(), band));
                gradient.setLocalMatrix(matrix);
                paint.setShader(gradient);
            }
            if (round) canvas.drawOval(bounds.left, bounds.top, bounds.right, bounds.bottom, paint);
            else canvas.drawRect(bounds, paint);
            paint.setShader(null);
        }

        void finish() {
            if (finished) return;
            ImageView view = owner.get();
            boolean displayed = current(view) && installed && view.isAttachedToWindow()
                    && view.isShown() && view.getGlobalVisibleRect(visible);
            finished = true;
            MAIN.removeCallbacks(tick);
            MAIN.removeCallbacks(timeout);
            if (view != null) {
                if (installed) view.getOverlay().remove(this);
                view.removeOnAttachStateChangeListener(this);
                if (ACTIVE.get(view) == this) ACTIVE.remove(view);
                if (displayed) session(view).mark(identity);
                view.invalidate();
            }
            installed = false;
        }

        @Override public void onViewAttachedToWindow(View v) { }
        @Override public void onViewDetachedFromWindow(View v) { finish(); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(@Nullable ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
