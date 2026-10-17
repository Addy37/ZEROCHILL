package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;

/** Records the live page once, excluding both glass surfaces that sample it. */
final class FrostedNavigationLayout extends LinearLayout {
    private final ViewPageBackdrop pageBackdrop = new ViewPageBackdrop(this);
    private boolean pageDirty = true;
    private View navigationView;
    private GlassHeaderView headerView;
    private View headerUnderlayView;

    void setFrostedHeaderView(GlassHeaderView header) {
        headerView = header;
        header.setPageBackdrop(pageBackdrop);
        pageDirty = true;
    }

    ViewPageBackdrop pageBackdropForOverlay() { return pageBackdrop; }

    void setHeaderUnderlayView(View content) { headerUnderlayView = content; }

    FrostedNavigationLayout(Context context) {
        super(context);
        setClipChildren(false); // Square's pressed selector and shadow exceed the shell.
    }

    FrostedNavigationLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(false);
    }

    void setFrostedNavigationView(View navigationView) {
        if (this.navigationView == navigationView) return;
        this.navigationView = navigationView;
        if (navigationView instanceof ZeroChillBottomNavigationView) {
            ((ZeroChillBottomNavigationView) navigationView).setPageBackdrop(pageBackdrop);
        }
        pageDirty = true;
        invalidate();
    }

    View frostedNavigationViewForTest() {
        return navigationView;
    }

    /** The activity pads this shell using real insets; its floating nav is inside that area. */
    @SuppressWarnings("deprecation")
    WindowInsets navigationContentInsets(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= 29) {
            return insets.inset(insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom());
        }
        return insets.replaceSystemWindowInsets(0, 0, 0, 0);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (Build.VERSION.SDK_INT >= 29 && canvas.isHardwareAccelerated() &&
                ((navigationView != null && navigationView.getVisibility() == VISIBLE &&
                navigationView.getAlpha() > 0f) ||
                (headerView != null && headerView.getVisibility() == VISIBLE)) && getWindowVisibility() == VISIBLE &&
                getWidth() > 0 && getHeight() > 0) {
            if (pageDirty) {
                pageDirty = false;
                pageBackdrop.record(getWidth(), getHeight(), recording -> {
                    drawContentChildren(recording, navigationView, getDrawingTime());
                    return kotlin.Unit.INSTANCE;
                });
            }
            if (pageBackdrop.drawPage(canvas)) {
                drawHeader(canvas);
                if (navigationView != null && navigationView.getVisibility() == VISIBLE)
                    drawChild(canvas, navigationView, getDrawingTime());
                return;
            }
        }
        drawContentChildren(canvas, navigationView, getDrawingTime());
        drawHeader(canvas);
        if (navigationView != null && navigationView.getVisibility() == VISIBLE) {
            drawChild(canvas, navigationView, getDrawingTime());
        }
    }

    @Override public void onDescendantInvalidated(View child, View target) {
        if (child != navigationView && child != headerView) pageDirty = true;
        super.onDescendantInvalidated(child, target);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        pageDirty = true;
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) pageDirty = true;
    }

    @Override protected void onDetachedFromWindow() {
        pageBackdrop.release();
        pageDirty = true;
        super.onDetachedFromWindow();
    }

    private void drawHeader(Canvas canvas) {
        if (headerView != null && headerView.getVisibility() == VISIBLE) {
            drawChild(canvas, headerView, getDrawingTime());
        }
    }

    private void drawContentChildren(Canvas canvas, View navigation, long drawingTime) {
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            if (child != navigation && child != headerView && child.getVisibility() != View.GONE) {
                // Only primary content may draw upward into the pinned header slot.
                // Keep the existing side/bottom clipping and system inset boundary.
                int checkpoint = canvas.save();
                int top = child == headerUnderlayView && headerView != null
                        && headerView.getVisibility() == VISIBLE ? headerView.getTop() : child.getTop();
                canvas.clipRect(child.getLeft(), top, child.getRight(), child.getBottom());
                drawChild(canvas, child, drawingTime);
                canvas.restoreToCount(checkpoint);
            }
        }
    }

}
