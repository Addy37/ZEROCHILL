package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Outline;
import android.graphics.Rect;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;

/** One native-content recording for the pinned pill. Glass never records itself. */
final class SearchGlassLayout extends FrameLayout {
    private final ViewPageBackdrop page = new ViewPageBackdrop(this);
    private ViewPageBackdrop underlay;
    private final ViewPageBackdrop empty = new ViewPageBackdrop(this);
    private RectF pillBounds;
    private GlassHeaderView pill, favorites, results;
    private View rows;
    private boolean dirty = true;
    private int underlayRevision = -1;
    private int resultCount;
    private int resultRowHeightDp = 76;
    private android.animation.ValueAnimator heightAnimator;
    private int animatedHeight;
    private View recentContent;
    private int lastDesiredHeight = -1;
    private int lastAvailableHeight = -1;
    private int lastPanelWidth = -1;
    private final Path rowClip = new Path();
    private final Outline resultOutline = new Outline();
    private final Rect resultRect = new Rect();
    private final Rect rowsClipBounds = new Rect();
    private final RectF clipBounds = new RectF();
    private int imeBottom;
    private int systemBottom;
    private int imeAnimations;
    private WindowInsetsCompat appliedInsets;
    private WindowInsetsAnimationCompat.Callback insetAnimation;

    void installInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(this, (view, insets) -> {
            appliedInsets = insets;
            applyInsets(insets, imeAnimations == 0);
            return insets;
        });
        insetAnimation = new WindowInsetsAnimationCompat.Callback(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            @Override public void onPrepare(WindowInsetsAnimationCompat animation) {
                if ((animation.getTypeMask() & WindowInsetsCompat.Type.ime()) != 0) imeAnimations++;
            }

            @Override public WindowInsetsCompat onProgress(WindowInsetsCompat insets,
                    java.util.List<WindowInsetsAnimationCompat> running) {
                applyInsets(insets, true);
                return insets;
            }

            @Override public void onEnd(WindowInsetsAnimationCompat animation) {
                if ((animation.getTypeMask() & WindowInsetsCompat.Type.ime()) == 0) return;
                imeAnimations = Math.max(0, imeAnimations - 1);
                if (imeAnimations == 0 && appliedInsets != null) applyInsets(appliedInsets, true);
            }
        };
        ViewCompat.setWindowInsetsAnimationCallback(this, insetAnimation);
    }

    private void applyInsets(WindowInsetsCompat insets, boolean updateIme) {
        androidx.core.graphics.Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.displayCutout());
        // onApply receives the END inset before the keyboard animation starts.
        // Defer that bottom extent and follow the actual inset on each progress frame.
        systemBottom = safe.bottom;
        if (updateIme) imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
        setPadding(safe.left, safe.top, safe.right, Math.max(safe.bottom, imeBottom));
        dirty = true;
        requestLayout();
    }

    SearchGlassLayout(Activity activity) {
        super(activity);
        setClipChildren(false);
        setClipToPadding(false);
    }

    void install(GlassHeaderView pill, GlassHeaderView favorites,
                 GlassHeaderView results, View rows) {
        this.pill = pill; this.favorites = favorites; this.results = results; this.rows = rows;
        addView(results); addView(rows); addView(pill); addView(favorites);
        // Share the glass outline itself, including its radius and current size.
        // This clips the complete native subtree on the hardware rendering path.
        rows.setOutlineProvider(results.getOutlineProvider());
        rows.setClipToOutline(true);
        pill.setPageBackdrop(page);
        favorites.setPageBackdrop(page);
        results.setPageBackdrop(empty);
    }

    void setUnderlay(ViewPageBackdrop value) {
        underlay = value;
        if (value != null) results.setPageBackdrop(value);
        dirty = true;
        invalidate();
    }

    void setResultCount(int count) { setResultCount(count, 76); }

    void setResultCount(int count, int rowHeightDp) {
        if (recentContent == null && resultCount == count && resultRowHeightDp == rowHeightDp) return;
        recentContent = null;
        resultCount = count;
        resultRowHeightDp = rowHeightDp;
        dirty = true;
        requestLayout();
    }

    void setRecentContent(View content) {
        recentContent = content;
        // The same panel is rebuilt after removal; measure its current children.
        dirty = true;
        requestLayout();
    }

    private void cancelHeightAnimation() {
        if (heightAnimator == null) return;
        heightAnimator.removeAllListeners();
        heightAnimator.removeAllUpdateListeners();
        heightAnimator.cancel();
        heightAnimator = null;
    }

    private void updateHeight(int desired, int available, int width) {
        boolean geometryChanged = available != lastAvailableHeight || width != lastPanelWidth;
        if (geometryChanged || !ZeroChillMotion.animationsEnabled(getContext())) cancelHeightAnimation();
        if (desired != lastDesiredHeight) {
            cancelHeightAnimation();
            int target = Math.min(available, desired);
            // First layout and a hidden pane commit their final geometry immediately.
            if (!geometryChanged && rows.isLaidOut() && rows.getVisibility() == VISIBLE
                    && rows.getAlpha() > 0f && rows.getHeight() != target
                    && ZeroChillMotion.animationsEnabled(getContext())) {
                animatedHeight = rows.getHeight();
                heightAnimator = android.animation.ValueAnimator.ofInt(animatedHeight, target);
                heightAnimator.setDuration(180L);
                heightAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
                heightAnimator.addUpdateListener(a -> { animatedHeight = (int) a.getAnimatedValue(); dirty = true; requestLayout(); });
                heightAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator a) { heightAnimator = null; requestLayout(); }
                });
                heightAnimator.start();
            }
        }
        lastDesiredHeight = desired;
        lastAvailableHeight = available;
        lastPanelWidth = width;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        int panelWidth = Math.max(0, width - dp(28));
        int desired = dp(96 + resultCount * resultRowHeightDp);
        if (recentContent != null) {
            recentContent.measure(MeasureSpec.makeMeasureSpec(panelWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            desired = recentContent.getMeasuredHeight() + rows.getPaddingTop() + rows.getPaddingBottom();
        }
        int available = resultHeight(MeasureSpec.getSize(heightSpec), Integer.MAX_VALUE);
        updateHeight(desired, available, panelWidth);
        int height = Math.min(available, heightAnimator != null ? animatedHeight : desired);
        if (pillBounds == null) place(pill, width - dp(88), dp(56), dp(14), dp(8));
        else place(pill, Math.round(pillBounds.width()), Math.round(pillBounds.height()),
                Math.round(pillBounds.left) - getPaddingLeft(), Math.round(pillBounds.top) - getPaddingTop());
        place(favorites, dp(48), dp(48), width - dp(62), dp(12));
        place(results, width - dp(28), height, dp(14), dp(76));
        place(rows, width - dp(28), height, dp(14), dp(76));
        super.onMeasure(widthSpec, heightSpec);
    }

    private int resultHeight(int windowHeight, int desired) {
        // Close the 4dp gap difference continuously as IME crosses the nav inset.
        // Switching gaps on visibility alone would briefly expand the pane.
        int bottomGap = dp(12) - Math.min(dp(4), Math.max(0, imeBottom - systemBottom));
        return Math.max(0, Math.min(windowHeight - getPaddingTop() - getPaddingBottom()
                - dp(76) - bottomGap, desired));
    }

    void setPillBounds(RectF bounds) {
        pillBounds = bounds;
        if (bounds != null) {
            // Apply the first animation frame before drawing, including a morph
            // started from OnPreDraw. No full-width capsule may flash first.
            pill.measure(MeasureSpec.makeMeasureSpec(Math.max(0, Math.round(bounds.width())), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.max(0, Math.round(bounds.height())), MeasureSpec.EXACTLY));
            pill.layout(Math.round(bounds.left), Math.round(bounds.top), Math.round(bounds.right), Math.round(bounds.bottom));
            pill.invalidateOutline();
        }
        requestLayout();
    }

    private void place(View view, int width, int height, int left, int top) {
        LayoutParams old = view.getLayoutParams() instanceof LayoutParams ? (LayoutParams) view.getLayoutParams() : null;
        if (old != null && old.width == Math.max(0, width) && old.height == height
                && old.leftMargin == left && old.topMargin == top) return;
        LayoutParams params = new LayoutParams(Math.max(0, width), height);
        params.leftMargin = left; params.topMargin = top;
        view.setLayoutParams(params);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        // This parent lets the floating pill extend beyond its bounds. Give the
        // rows their own RenderNode clip as well as the rounded outline: a
        // ScrollView's fading-edge layers otherwise escape the top on the GPU.
        rowsClipBounds.set(0, 0, rows.getWidth(), rows.getHeight());
        rows.setClipBounds(rowsClipBounds);
        // Child sizes can change while this full-window layout keeps the same bounds.
        rows.invalidateOutline();
        results.invalidateOutline();
        dirty = true;
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        if (underlay != null && underlayRevision != underlay.getRecordings()) {
            underlayRevision = underlay.getRecordings(); dirty = true;
        }
        if (dirty && canvas.isHardwareAccelerated() && getWidth() > 0 && getHeight() > 0) {
            dirty = false;
            page.record(getWidth(), getHeight(), recording -> {
                if (underlay != null) underlay.drawPageInto(recording, this);
                drawRows(recording);
                return kotlin.Unit.INSTANCE;
            });
        }
        if (results.getVisibility() == VISIBLE) drawChild(canvas, results, getDrawingTime());
        drawRows(canvas);
        drawChild(canvas, pill, getDrawingTime());
        drawChild(canvas, favorites, getDrawingTime());
    }

    private void drawRows(Canvas canvas) {
        if (rows.getVisibility() != VISIBLE) return;
        int checkpoint = canvas.save();
        rowClip.reset();
        results.getOutlineProvider().getOutline(results, resultOutline);
        if (!resultOutline.getRect(resultRect)) { canvas.restoreToCount(checkpoint); return; }
        clipBounds.set(resultRect);
        clipBounds.offset(results.getX(), results.getY());
        rowClip.addRoundRect(clipBounds, resultOutline.getRadius(), resultOutline.getRadius(), Path.Direction.CW);
        canvas.clipPath(rowClip);
        drawChild(canvas, rows, getDrawingTime());
        canvas.restoreToCount(checkpoint);
    }

    @Override public void onDescendantInvalidated(View child, View target) {
        if (child == rows) dirty = true;
        super.onDescendantInvalidated(child, target);
    }

    @Override protected void onDetachedFromWindow() {
        cancelHeightAnimation();
        lastDesiredHeight = lastAvailableHeight = lastPanelWidth = -1;
        page.release(); empty.release(); dirty = true;
        super.onDetachedFromWindow();
    }

    private int dp(int value) { return BrowseUi.dp(getContext(), value); }
}
