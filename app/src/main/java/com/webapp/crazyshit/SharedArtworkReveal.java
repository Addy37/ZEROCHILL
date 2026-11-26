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
    private SharedArtworkReveal() {}

    static DrawableTransitionOptions artwork(String scope, String key, boolean round) {
        return DrawableTransitionOptions.with((source, first) -> transition(scope, key, round));
    }

    static Transition<Drawable> transition(String scope, String key, boolean round) {
        final String identity = scope + ":" + key;
        return (resource, adapter) -> {
            View target = adapter.getView();
            if (!(target instanceof ImageView) || resource instanceof Animatable
                    || key == null || key.isEmpty()
                    || !ZeroChillMotion.animationsEnabled(target.getContext())) return false;
            if (target.isAttachedToWindow() && session(target).seen(identity)) return false;
            adapter.setDrawable(new Reveal(resource, (ImageView) target, identity, round));
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
        return view != null && view.getDrawable() instanceof Reveal
                && !((Reveal) view.getDrawable()).finished;
    }

    static void finishNow(ImageView view) {
        if (view != null && view.getDrawable() instanceof Reveal)
            ((Reveal) view.getDrawable()).finish(view);
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

    private static final class Reveal extends Drawable implements Drawable.Callback {
        private final Drawable content;
        private final WeakReference<ImageView> owner;
        private final String identity;
        private final boolean round;
        private final Rect visible = new Rect();
        private final int[] location = new int[2];
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private final int matte = GalleryRevealImageView.MATTE;
        private final LinearGradient gradient = new LinearGradient(0f, 0f, 0f, 1f,
                new int[]{matte & 0x00ffffff, (matte & 0x00ffffff) | 0x28000000,
                        (matte & 0x00ffffff) | 0xd7000000, matte},
                new float[]{0f, .24f, .76f, 1f}, Shader.TileMode.CLAMP);
        private long start = -1L, lastDraw, lastFast;
        private int lastX, lastY;
        private boolean finished;
        private final Runnable tick = this::invalidateSelf;

        Reveal(Drawable content, ImageView owner, String identity, boolean round) {
            this.content = content; this.owner = new WeakReference<>(owner);
            this.identity = identity; this.round = round;
            content.setCallback(this);
        }

        @Override public void draw(@NonNull Canvas canvas) {
            ImageView view = owner.get();
            if (view == null || view.getDrawable() != this) return;
            if (finished) { content.draw(canvas); return; }
            if (!ZeroChillMotion.animationsEnabled(view.getContext()) || session(view).seen(identity)) {
                finish(view); content.draw(canvas); return;
            }
            Rect bounds = getBounds();
            if (!view.isAttachedToWindow() || !view.isShown() || view.getWindowVisibility() != View.VISIBLE
                    || !view.getGlobalVisibleRect(visible) || bounds.isEmpty()) {
                // No polling for detached/recycled/offscreen views. Their next draw resumes the reveal.
                if (start >= 0) start = -1;
                lastDraw = 0L;
                return;
            }
            long now = SystemClock.uptimeMillis();
            view.getLocationOnScreen(location);
            if (lastDraw > 0L && now > lastDraw && now - lastDraw <= 80L) {
                float density = Math.max(1f, view.getResources().getDisplayMetrics().density);
                double moved = Math.hypot(location[0] - lastX, location[1] - lastY) / density;
                if (moved > Math.max(8L, now - lastDraw) * .7f) lastFast = now;
            }
            lastDraw = now; lastX = location[0]; lastY = location[1];
            if (start < 0L || now < start) {
                if (settling(view) || (lastFast != 0L && now - lastFast < GalleryRevealImageView.SETTLE_MS)) {
                    start = -1L;
                } else if (start < 0L) {
                    start = session(view).reserve(now, columns(view));
                }
            }
            float progress = start < 0L ? 0f :
                    Math.min(1f, Math.max(0f, (now - start) / (float) GalleryRevealImageView.DURATION_MS));
            if (progress >= 1f) {
                finish(view); content.draw(canvas); return;
            }
            // Reuse the gallery's soft descending matte. Avoid a per-frame bitmap or saveLayer.
            if (progress > 0f) content.draw(canvas);
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
            // View invalidation follows the display refresh rate; no work for non-drawing cells.
            unscheduleSelf(tick);
            scheduleSelf(tick, now + 16L);
        }

        void finish(ImageView view) {
            if (finished) return;
            finished = true;
            unscheduleSelf(tick);
            if (view.getDrawable() != this) return;
            session(view).mark(identity);
            // Unwrap outside draw; if Glide has rebound the view the old image is never reinstalled.
            view.post(() -> {
                if (view.getDrawable() == this) view.setImageDrawable(content);
            });
        }

        @Override protected void onBoundsChange(Rect bounds) { content.setBounds(bounds); }
        @Override public int getIntrinsicWidth() { return content.getIntrinsicWidth(); }
        @Override public int getIntrinsicHeight() { return content.getIntrinsicHeight(); }
        @Override public boolean isStateful() { return content.isStateful(); }
        @Override protected boolean onStateChange(int[] state) { return content.setState(state); }
        @Override protected boolean onLevelChange(int level) { return content.setLevel(level); }
        @Override public boolean setVisible(boolean value, boolean restart) {
            content.setVisible(value, restart);
            return super.setVisible(value, restart);
        }
        @Override public void setAlpha(int alpha) { content.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(@Nullable ColorFilter filter) { content.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public void invalidateDrawable(@NonNull Drawable who) { invalidateSelf(); }
        @Override public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) { scheduleSelf(what, when); }
        @Override public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) { unscheduleSelf(what); }
    }
}
