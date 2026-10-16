package com.webapp.crazyshit;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/** Compatibility/navigation owner. Square's Compose stack owns all visible pixels. */
final class ZeroChillBottomNavigationView extends BottomNavigationView {
    interface OnNavigationDragListener {
        boolean onNavigationDragStart();
        void onNavigationDragBy(float deltaPageFraction);
        void onNavigationDragEnd(boolean canceled);
    }
    static final int[] PAGE_NAV_IDS = {2, 4, 3, 6};
    private float pagerPosition;
    private float collapseProgress;
    private boolean collapsedTarget;
    private ValueAnimator collapseAnimator;
    private OnNavigationDragListener navigationDragListener;
    private SquareNavigationBridge bridge;
    private ViewPageBackdrop pageBackdrop;
    private boolean libraryUnread;
    private boolean dragging;

    ZeroChillBottomNavigationView(Context context) {
        super(context);
        setMinimumHeight(0);
        setClipChildren(false);
        setClipToPadding(false);
        setPadding(0, 0, 0, 0);
    }

    @Override protected void onAttachedToWindow() {
        if (bridge == null) bridge = new SquareNavigationBridge(this);
        bridge.attach();
        super.onAttachedToWindow();
        updateBridge();
    }

    @Override protected void onMeasure(int width, int height) {
        // Retain Material's menu/check state API without rendering its legacy hierarchy.
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (!(child instanceof androidx.compose.ui.platform.ComposeView)) child.setVisibility(GONE);
        }
        setBackground(null);
        setElevation(0f);
        setPadding(0, 0, 0, 0);
        super.onMeasure(width, height);
    }

    @Override public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        // The outer shell alone owns real system clearance. No synthetic Material gutter.
        if (bridge != null) bridge.dispatchInsets();
        return insets;
    }

    void setPageBackdrop(ViewPageBackdrop backdrop) {
        pageBackdrop = backdrop;
        updateBridge();
    }
    void setLibraryUnread(boolean unread) { libraryUnread = unread; updateBridge(); }
    void setPagerPosition(float position) {
        pagerPosition = Math.max(0f, Math.min(3f, position));
        updateBridge();
    }
    void refreshItemColors() { updateBridge(); }
    void setOnNavigationDragListener(OnNavigationDragListener listener) { navigationDragListener = listener; }
    boolean startSquareDrag() {
        dragging = navigationDragListener != null && navigationDragListener.onNavigationDragStart();
        return dragging;
    }
    void dragSquareBy(float fraction) {
        if (dragging && navigationDragListener != null) navigationDragListener.onNavigationDragBy(fraction);
    }
    void endSquareDrag(boolean canceled) {
        if (dragging && navigationDragListener != null) navigationDragListener.onNavigationDragEnd(canceled);
        dragging = false;
        updateBridge();
    }
    void selectSquareTab(int index) { setSelectedItemId(PAGE_NAV_IDS[index]); }
    boolean isSquareDragging() { return dragging; }

    void setCollapsed(boolean collapsed, boolean animated) {
        float target = collapsed ? 1f : 0f;
        if (collapsedTarget == collapsed && (collapseAnimator != null || Math.abs(collapseProgress - target) < .001f)) return;
        collapsedTarget = collapsed;
        if (collapseAnimator != null) collapseAnimator.cancel();
        if (!animated || !ZeroChillMotion.animationsEnabled(getContext())) {
            collapseAnimator = null;
            collapseProgress = target;
            reapplyCurrentGeometry();
            return;
        }
        collapseAnimator = ValueAnimator.ofFloat(collapseProgress, target);
        collapseAnimator.setDuration(210L);
        collapseAnimator.setInterpolator(new DecelerateInterpolator());
        collapseAnimator.addUpdateListener(animation -> {
            collapseProgress = (float) animation.getAnimatedValue();
            reapplyCurrentGeometry();
        });
        collapseAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) { collapseAnimator = null; }
        });
        collapseAnimator.start();
    }
    void reapplyCurrentGeometry() {
        ViewGroup.LayoutParams raw = getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) raw;
            int height = dp(61f + (50f - 61f) * collapseProgress);
            int side = dp(16f + (60f - 16f) * collapseProgress);
            if (params.height != height || params.leftMargin != side || params.rightMargin != side ||
                    params.topMargin != -height - dp(8) || params.bottomMargin != dp(8)) {
                params.height = height;
                params.setMargins(side, -height - dp(8), side, dp(8));
                setLayoutParams(params);
            }
        }
        setPadding(0, 0, 0, 0);
        updateBridge();
    }
    private void updateBridge() {
        if (bridge != null) bridge.update(pageBackdrop, pagerPosition, collapseProgress, libraryUnread);
    }
    boolean isCollapsedForTest() { return collapsedTarget; }
    float collapseProgressForTest() { return collapseProgress; }
    float pagerPositionForTest() { return pagerPosition; }
    void setCollapseProgressForTest(float progress) {
        if (collapseAnimator != null) collapseAnimator.cancel();
        collapseAnimator = null;
        collapseProgress = Math.max(0f, Math.min(1f, progress));
        collapsedTarget = collapseProgress == 1f;
        reapplyCurrentGeometry();
    }
    RectF selectedCapsuleBoundsForTest() {
        float width = (getWidth() - dp(8)) / 4f;
        return new RectF(dp(4) + pagerPosition * width, dp(4),
                dp(4) + (pagerPosition + 1) * width, getHeight() - dp(4));
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onDetachedFromWindow() {
        if (collapseAnimator != null) collapseAnimator.cancel();
        collapseAnimator = null;
        endSquareDrag(true);
        if (bridge != null) bridge.detach();
        super.onDetachedFromWindow();
    }
}
