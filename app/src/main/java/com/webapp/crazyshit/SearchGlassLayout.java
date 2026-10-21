package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.Canvas;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;

/** One native-content recording for the pinned pill. Glass never records itself. */
final class SearchGlassLayout extends FrameLayout {
    private final ViewPageBackdrop page = new ViewPageBackdrop(this);
    private final ViewPageBackdrop empty = new ViewPageBackdrop(this);
    private RectF pillBounds;
    private GlassHeaderView pill, favorites, results;
    private View rows;
    private boolean dirty = true;
    private int resultCount;
    private int resultRowHeightDp = 76;
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
        setBackgroundColor(android.graphics.Color.BLACK);
        setClipChildren(false);
        setClipToPadding(false);
    }

    void install(GlassHeaderView pill, GlassHeaderView favorites,
                 GlassHeaderView results, View rows) {
        this.pill = pill; this.favorites = favorites; this.results = results; this.rows = rows;
        addView(results); addView(rows); addView(pill); addView(favorites);
        // Results use the available rectangular viewport. Header glass stays separate.
        rows.setClipToOutline(false);
        pill.setPageBackdrop(page);
        favorites.setPageBackdrop(page);
        results.setPageBackdrop(empty);
    }

    void setUnderlay(ViewPageBackdrop value) {
        // Active Search never samples the busy screen below its opaque backdrop.
        dirty = true;
        invalidate();
    }

    void setResultCount(int count) { setResultCount(count, 76); }

    void setResultCount(int count, int rowHeightDp) {
        if (resultCount == count && resultRowHeightDp == rowHeightDp) return;
        resultCount = count;
        resultRowHeightDp = rowHeightDp;
        dirty = true;
        requestLayout();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        int height = resultHeight(MeasureSpec.getSize(heightSpec));
        if (pillBounds == null) place(pill, width - dp(88), dp(56), dp(14), dp(8));
        else place(pill, Math.round(pillBounds.width()), Math.round(pillBounds.height()),
                Math.round(pillBounds.left) - getPaddingLeft(), Math.round(pillBounds.top) - getPaddingTop());
        place(favorites, dp(48), dp(48), width - dp(62), dp(12));
        place(results, width, height, 0, dp(76));
        place(rows, width, height, 0, dp(76));
        super.onMeasure(widthSpec, heightSpec);
    }

    private int resultHeight(int windowHeight) {
        // Close the 4dp gap difference continuously as IME crosses the nav inset.
        // Switching gaps on visibility alone would briefly expand the pane.
        int bottomGap = dp(12) - Math.min(dp(4), Math.max(0, imeBottom - systemBottom));
        return Math.max(0, windowHeight - getPaddingTop() - getPaddingBottom()
                - dp(76) - bottomGap);
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
        // Child sizes can change while this full-window layout keeps the same bounds.
        rows.invalidateOutline();
        results.invalidateOutline();
        dirty = true;
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        if (dirty && canvas.isHardwareAccelerated() && getWidth() > 0 && getHeight() > 0) {
            dirty = false;
            page.record(getWidth(), getHeight(), recording -> {
                recording.drawColor(android.graphics.Color.BLACK);
                drawRows(recording);
                return kotlin.Unit.INSTANCE;
            });
        }
        // No giant glass results panel. The header samples only Search itself.
        drawRows(canvas);
        drawChild(canvas, pill, getDrawingTime());
        drawChild(canvas, favorites, getDrawingTime());
    }

    private void drawRows(Canvas canvas) {
        if (rows.getVisibility() != VISIBLE) return;
        int checkpoint = canvas.save();
        canvas.clipRect(rows.getLeft(), rows.getTop(), rows.getRight(), rows.getBottom());
        drawChild(canvas, rows, getDrawingTime());
        canvas.restoreToCount(checkpoint);
    }

    @Override public void onDescendantInvalidated(View child, View target) {
        if (child == rows) dirty = true;
        super.onDescendantInvalidated(child, target);
    }

    @Override protected void onDetachedFromWindow() {
        page.release(); empty.release(); dirty = true;
        super.onDetachedFromWindow();
    }

    private int dp(int value) { return BrowseUi.dp(getContext(), value); }
}
