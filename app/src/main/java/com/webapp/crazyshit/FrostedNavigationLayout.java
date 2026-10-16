package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;

/** Records the live View page once, excluding the Compose navbar that samples it. */
final class FrostedNavigationLayout extends LinearLayout {
    private final ViewPageBackdrop pageBackdrop = new ViewPageBackdrop(this);
    private boolean pageDirty = true;
    private View navigationView;

    FrostedNavigationLayout(Context context) {
        super(context);
    }

    FrostedNavigationLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
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
                navigationView != null && navigationView.getVisibility() == VISIBLE &&
                navigationView.getAlpha() > 0f && getWindowVisibility() == VISIBLE &&
                getWidth() > 0 && getHeight() > 0) {
            if (pageDirty) {
                pageDirty = false;
                pageBackdrop.record(getWidth(), getHeight(), recording -> {
                    drawContentChildren(recording, navigationView, getDrawingTime());
                    return kotlin.Unit.INSTANCE;
                });
            }
            if (pageBackdrop.drawPage(canvas)) {
                drawChild(canvas, navigationView, getDrawingTime());
                return;
            }
        }
        super.dispatchDraw(canvas);
    }

    @Override public void onDescendantInvalidated(View child, View target) {
        if (child != navigationView) pageDirty = true;
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

    private void drawContentChildren(Canvas canvas, View navigation, long drawingTime) {
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            if (child != navigation && child.getVisibility() != View.GONE) {
                drawChild(canvas, child, drawingTime);
            }
        }
    }

}
