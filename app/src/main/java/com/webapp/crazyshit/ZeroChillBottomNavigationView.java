package com.webapp.crazyshit;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Bottom navigation with a selected glass capsule that tracks ViewPager swipes.
 *
 * The parent samples the live backdrop through a refractive lens on API 33+.
 * This view owns optical finishing and motion, preserving Material hit targets and insets.
 */
final class ZeroChillBottomNavigationView extends BottomNavigationView {
    interface OnNavigationDragListener {
        boolean onNavigationDragStart();
        void onNavigationDragBy(float deltaPageFraction);
        void onNavigationDragEnd(boolean canceled);
    }

    private static final int[] PAGE_NAV_IDS = {2, 4, 3, 6};
    private static final long COLLAPSE_DURATION_MS = 210L;
    private static final int COLLAPSED_HEIGHT_DP = 50;
    private static final int COLLAPSED_SIDE_MARGIN_DP = 60;
    private static final float HORIZONTAL_DOMINANCE = 1.25f;

    private final Drawable selectedGlass;
    private final Rect firstRect = new Rect();
    private final Rect secondRect = new Rect();
    private final RectF indicatorRect = new RectF();
    private final RectF capsuleHitRect = new RectF();

    private float pagerPosition;
    private float lensPosition;
    private boolean hasLensPosition;
    private ValueAnimator selectionAnimator;
    private float pressScale = 1f;
    private ValueAnimator pressAnimator;
    private ValueAnimator collapseAnimator;
    private float collapseProgress;
    private boolean collapsedTarget;
    private final int swipeTouchSlop;
    private float dragDownX;
    private float dragDownY;
    private float dragLastX;
    private float navigationDragPageStep = 1f;
    private boolean navigationDragCandidate;
    private boolean navigationDragActive;
    private OnNavigationDragListener navigationDragListener;

    ZeroChillBottomNavigationView(Context context) {
        // Match the approved v4.3.1 Material baseline. System clearance is handled by
        // the outer shell; this view receives a synthetic reference gesture inset below.
        super(context);
        selectedGlass = new LiquidGlassDrawable(context, LiquidGlassDrawable.Material.LENS);
        swipeTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setWillNotDraw(false);
        // The theme's 64dp minimum would otherwise keep Material's menu at full height
        // inside a 50dp parent, cutting its icon container during the transition.
        setMinimumHeight(0);
        addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            updateItemColors();
            setItemCollapseVisuals(clamp(collapseProgress, 0f, 1f));
        });
    }

    @Override
    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        if (getParent() instanceof FrostedNavigationLayout) {
            FrostedNavigationLayout shell = (FrostedNavigationLayout) getParent();
            WindowInsets local = shell.navigationContentInsets(insets);
            int referenceBottom = Math.round(lerp(
                    getResources().getDimensionPixelSize(R.dimen.zc_nav_reference_gesture_inset),
                    0,
                    clamp(collapseProgress, 0f, 1f)
            ));

            // v4.3.1's approved expanded geometry came from Material receiving the
            // gesture-navigation bottom inset itself. Recreate that exact local input
            // while the shell separately owns the device's real system-bar clearance.
            super.dispatchApplyWindowInsets(withReferenceBottomInset(local, referenceBottom));
            applyCollapseGutter(collapseProgress);

            // Before Android 11, ViewGroup forwards a child's returned insets to siblings.
            // Never let this navbar-local reference inset escape its subtree.
            return insets;
        }
        return super.dispatchApplyWindowInsets(insets);
    }

    @SuppressWarnings("deprecation")
    private static WindowInsets withReferenceBottomInset(WindowInsets base, int bottom) {
        if (Build.VERSION.SDK_INT >= 30) {
            return Api30Insets.withNavigationBottom(base, bottom);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            return Api29Insets.withSystemBottom(base, bottom);
        }
        return base.replaceSystemWindowInsets(0, 0, 0, bottom);
    }

    void setPagerPosition(float position) {
        float clamped = clamp(position, 0f, PAGE_NAV_IDS.length - 1f);
        boolean changed = Math.abs(pagerPosition - clamped) > .0001f;
        pagerPosition = clamped;
        if (!hasLensPosition || !ZeroChillMotion.animationsEnabled(getContext())) {
            if (selectionAnimator != null) selectionAnimator.cancel();
            lensPosition = clamped;
            hasLensPosition = true;
        } else if (changed) {
            if (selectionAnimator != null) selectionAnimator.cancel();
            if (Math.abs(clamped - Math.round(clamped)) > .001f || navigationDragActive) {
                lensPosition = clamped;
            } else {
                selectionAnimator = ValueAnimator.ofFloat(lensPosition, clamped);
                selectionAnimator.setDuration(240L);
                selectionAnimator.setInterpolator(new android.view.animation.PathInterpolator(.2f, 0f, 0f, 1f));
                selectionAnimator.addUpdateListener(animation -> {
                    lensPosition = (float) animation.getAnimatedValue();
                    invalidate();
                });
                selectionAnimator.start();
            }
        }
        updateItemColors();
        invalidate();
    }

    void refreshItemColors() {
        updateItemColors();
    }

    void setOnNavigationDragListener(OnNavigationDragListener listener) {
        navigationDragListener = listener;
    }

    void setCollapsed(boolean collapsed, boolean animated) {
        float target = collapsed ? 1f : 0f;
        if (collapsedTarget == collapsed) {
            if (collapseAnimator != null && collapseAnimator.isRunning()) return;
            if (Math.abs(collapseProgress - target) < 0.001f) return;
        }

        collapsedTarget = collapsed;
        if (collapseAnimator != null) {
            collapseAnimator.cancel();
            collapseAnimator = null;
        }

        if (!animated || !ZeroChillMotion.animationsEnabled(getContext())) {
            collapseProgress = target;
            applyCollapseProgress(collapseProgress);
            return;
        }

        collapseAnimator = ValueAnimator.ofFloat(collapseProgress, target);
        collapseAnimator.setDuration(COLLAPSE_DURATION_MS);
        collapseAnimator.setInterpolator(new DecelerateInterpolator());
        collapseAnimator.addUpdateListener(animation -> {
            collapseProgress = (float) animation.getAnimatedValue();
            applyCollapseProgress(collapseProgress);
        });
        collapseAnimator.start();
    }

    boolean isCollapsedForTest() {
        return collapsedTarget;
    }

    /** Restore this view's own geometry after a portrait chrome/lifecycle pass. */
    void reapplyCurrentGeometry() {
        applyCollapseProgress(collapseProgress);
    }

    float collapseProgressForTest() {
        return collapseProgress;
    }

    void setCollapseProgressForTest(float progress) {
        if (collapseAnimator != null) collapseAnimator.cancel();
        collapseAnimator = null;
        collapseProgress = clamp(progress, 0f, 1f);
        collapsedTarget = collapseProgress >= 1f;
        applyCollapseProgress(collapseProgress);
    }

    RectF selectedCapsuleBoundsForTest() {
        return new RectF(indicatorRect);
    }

    float pagerPositionForTest() {
        return pagerPosition;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Material updates its checked item's label/icon layout during quick tab changes.
        // Reapply the current visual progress before children draw, including mid-transition.
        setItemCollapseVisuals(collapseProgress);
        drawSelectedGlass(canvas);
        super.dispatchDraw(canvas);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event == null) return super.dispatchTouchEvent(null);

        int action = event.getActionMasked();
        updateMaterialPressure(action);

        if (action == MotionEvent.ACTION_DOWN) {
            dragDownX = event.getX();
            dragDownY = event.getY();
            dragLastX = dragDownX;
            navigationDragPageStep = pageStepDistance();
            navigationDragActive = false;
            navigationDragCandidate = isInsideSelectedCapsule(dragDownX, dragDownY);
            return super.dispatchTouchEvent(event);
        }

        float dx = event.getX() - dragDownX;
        float dy = event.getY() - dragDownY;
        boolean horizontalIntent = navigationDragCandidate
                && Math.abs(dx) > swipeTouchSlop
                && Math.abs(dx) > Math.abs(dy) * HORIZONTAL_DOMINANCE;

        if (action == MotionEvent.ACTION_MOVE && !navigationDragActive && horizontalIntent) {
            boolean started = navigationDragListener != null
                    && navigationDragListener.onNavigationDragStart();
            if (started) {
                navigationDragActive = true;
                cancelChildTouch(event);
                navigationDragListener.onNavigationDragBy(dx / navigationDragPageStep);
                dragLastX = event.getX();
                return true;
            }
            navigationDragCandidate = false;
        }

        if (action == MotionEvent.ACTION_MOVE && navigationDragActive) {
            float deltaX = event.getX() - dragLastX;
            dragLastX = event.getX();
            if (navigationDragListener != null && deltaX != 0f) {
                navigationDragListener.onNavigationDragBy(deltaX / navigationDragPageStep);
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP && navigationDragActive) {
            navigationDragActive = false;
            navigationDragCandidate = false;
            if (navigationDragListener != null) {
                navigationDragListener.onNavigationDragEnd(false);
            }
            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL && navigationDragActive) {
            navigationDragActive = false;
            navigationDragCandidate = false;
            if (navigationDragListener != null) {
                navigationDragListener.onNavigationDragEnd(true);
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            navigationDragCandidate = false;
        }
        return super.dispatchTouchEvent(event);
    }

    private float pageStepDistance() {
        View first = findViewById(PAGE_NAV_IDS[0]);
        View second = findViewById(PAGE_NAV_IDS[1]);
        if (first == null || second == null ||
                first.getWidth() <= 0 || second.getWidth() <= 0) {
            return Math.max(1f, getWidth() / 4f);
        }
        descendantRect(first, firstRect);
        descendantRect(second, secondRect);
        return Math.max(1f, Math.abs(secondRect.exactCenterX() - firstRect.exactCenterX()));
    }

    private boolean isInsideSelectedCapsule(float x, float y) {
        return selectedGlassBounds(capsuleHitRect) && capsuleHitRect.contains(x, y);
    }

    private void updateMaterialPressure(int action) {
        if (action == MotionEvent.ACTION_DOWN) {
            animatePress(.975f);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            animatePress(1f);
        }
    }

    private void cancelChildTouch(MotionEvent source) {
        MotionEvent cancel = MotionEvent.obtain(source);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (pressAnimator != null) pressAnimator.cancel();
        if (collapseAnimator != null) collapseAnimator.cancel();
        if (selectionAnimator != null) selectionAnimator.cancel();
        selectionAnimator = null;
        pressAnimator = null;
        lensPosition = pagerPosition;
        pressScale = 1f;
        collapseAnimator = null;
        navigationDragListener = null;
        super.onDetachedFromWindow();
    }

    private void drawSelectedGlass(Canvas canvas) {
        if (!selectedGlassBounds(indicatorRect)) return;
        float center = indicatorRect.centerX();
        float midY = indicatorRect.centerY();
        float halfWidth = indicatorRect.width() * pressScale / 2f;
        float halfHeight = indicatorRect.height() * pressScale / 2f;
        indicatorRect.set(center - halfWidth, midY - halfHeight,
                center + halfWidth, midY + halfHeight);

        if (selectedGlass != null) {
            selectedGlass.setBounds(
                    Math.round(indicatorRect.left),
                    Math.round(indicatorRect.top),
                    Math.round(indicatorRect.right),
                    Math.round(indicatorRect.bottom)
            );
            selectedGlass.draw(canvas);
        }

        // The lens samples content in the parent, before the icons are drawn.
    }

    /** Unpressed capsule geometry shared by drawing and navbar drag hit-testing. */
    private boolean selectedGlassBounds(RectF out) {
        if (getWidth() <= 0 || getHeight() <= 0) return false;

        float position = clamp(lensPosition, 0f, PAGE_NAV_IDS.length - 1f);
        int lower = Math.min(PAGE_NAV_IDS.length - 1, (int) Math.floor(position));
        int upper = Math.min(PAGE_NAV_IDS.length - 1, lower + 1);
        float fraction = position - lower;

        View first = findViewById(PAGE_NAV_IDS[lower]);
        View second = findViewById(PAGE_NAV_IDS[upper]);
        if (first == null || second == null || first.getWidth() <= 0 || second.getWidth() <= 0) {
            return false;
        }

        descendantRect(first, firstRect);
        descendantRect(second, secondRect);

        float firstCenter = firstRect.exactCenterX();
        float secondCenter = secondRect.exactCenterX();
        float center = lerp(firstCenter, secondCenter, fraction);
        float width = lerp(firstRect.width(), secondRect.width(), fraction) - dp(8);

        // Exact v4.3.1 capsule geometry. Because the reference Material inset is released
        // during collapse, this same formula also preserves the approved compact endpoint.
        float top = Math.max(dp(3), lerp(firstRect.top, secondRect.top, fraction) + dp(4));
        float bottom = Math.min(getHeight() - dp(3),
                lerp(firstRect.bottom, secondRect.bottom, fraction) + dp(10));
        if (bottom <= top) return false;

        out.set(center - width / 2f, top, center + width / 2f, bottom);
        return true;
    }

    void copyOpticalLensBounds(RectF out) {
        if (!selectedGlassBounds(out)) out.setEmpty();
        float dx = out.width() * (1f - pressScale) * .5f;
        float dy = out.height() * (1f - pressScale) * .5f;
        out.inset(dx, dy);
    }

    float opticalPressure() { return (1f - pressScale) * 12f; }

    private void animatePress(float target) {
        if (pressAnimator != null) pressAnimator.cancel();
        if (!ZeroChillMotion.animationsEnabled(getContext())) {
            pressScale = 1f;
            invalidate();
            return;
        }
        pressAnimator = ValueAnimator.ofFloat(pressScale, target);
        pressAnimator.setDuration(target < 1f ? ZeroChillMotion.PRESS_IN_MS : ZeroChillMotion.STANDARD_MS);
        pressAnimator.setInterpolator(new DecelerateInterpolator());
        pressAnimator.addUpdateListener(animation -> {
            pressScale = (float) animation.getAnimatedValue();
            invalidate();
        });
        pressAnimator.start();
    }

    private void applyCollapseProgress(float progress) {
        float p = clamp(progress, 0f, 1f);
        applyCollapseGutter(p);
        int expandedHeight = getResources().getDimensionPixelSize(R.dimen.zc_bottom_nav_height);
        int collapsedHeight = dp(COLLAPSED_HEIGHT_DP);
        int sideMargin = Math.round(lerp(dp(10), dp(COLLAPSED_SIDE_MARGIN_DP), p));

        ViewGroup.LayoutParams raw = getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) raw;
            int height = Math.round(lerp(expandedHeight, collapsedHeight, p));
            // The bar overlays content. Keep its bottom edge fixed while its height changes.
            int topMargin = -height - dp(6);
            int bottomMargin = dp(6);
            if (params.height != height || params.leftMargin != sideMargin ||
                    params.rightMargin != sideMargin || params.topMargin != topMargin ||
                    params.bottomMargin != bottomMargin) {
                params.height = height;
                params.setMargins(sideMargin, topMargin, sideMargin, bottomMargin);
                setLayoutParams(params);
            }
        }

        setItemCollapseVisuals(p);
        invalidate();
    }

    private void applyCollapseGutter(float progress) {
        float p = clamp(progress, 0f, 1f);
        int expandedBottom = getResources().getDimensionPixelSize(
                R.dimen.zc_nav_reference_gesture_inset);
        int bottom = Math.round(lerp(expandedBottom, 0, p));
        if (getPaddingTop() != 0 || getPaddingBottom() != bottom) {
            setPadding(getPaddingLeft(), 0, getPaddingRight(), bottom);
        }
    }

    private void setItemCollapseVisuals(float progress) {
        float labelAlpha = clamp(1f - progress * 1.45f, 0f, 1f);
        for (int id : PAGE_NAV_IDS) {
            View item = findViewById(id);
            if (item == null) continue;
            if (item.getMinimumHeight() != 0) item.setMinimumHeight(0);
            applyLabelAlpha(item, labelAlpha);
            View iconContainer = item.findViewById(
                    com.google.android.material.R.id.navigation_bar_item_icon_container);
            if (iconContainer != null && iconContainer.getHeight() > 0) {
                descendantRect(iconContainer, firstRect);
                float desiredCenter = lerp(firstRect.exactCenterY(), getHeight() / 2f, progress);
                // Move the whole container, not the 24dp ImageView inside its clipped frame.
                float shift = desiredCenter - firstRect.exactCenterY();
                if (Math.abs(iconContainer.getTranslationY() - shift) > 0.01f) {
                    iconContainer.setTranslationY(shift);
                }
            }
        }
    }

    private static void applyLabelAlpha(View view, float labelAlpha) {
        if (view instanceof TextView) {
            if (Math.abs(view.getAlpha() - labelAlpha) > 0.001f) view.setAlpha(labelAlpha);
            return;
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            applyLabelAlpha(group.getChildAt(i), labelAlpha);
        }
    }

    private void updateItemColors() {
        int active = ContextCompat.getColor(getContext(), R.color.zc_glass_selected_ink);
        int inactive = ContextCompat.getColor(getContext(), R.color.zc_glass_unselected_ink);
        for (int index = 0; index < PAGE_NAV_IDS.length; index++) {
            View item = findViewById(PAGE_NAV_IDS[index]);
            if (item != null) tintChildren(item, mix(inactive, active,
                    Math.max(0f, 1f - Math.abs(pagerPosition - index))));
        }
    }

    private static void tintChildren(View view, int color) {
        if (view instanceof ImageView) {
            ((ImageView) view).setImageTintList(ColorStateList.valueOf(color));
        } else if (view instanceof TextView) {
            ((TextView) view).setTextColor(color);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                tintChildren(group.getChildAt(i), color);
            }
        }
    }

    private static int mix(int from, int to, float fraction) {
        return Color.rgb(
                Math.round(lerp(Color.red(from), Color.red(to), fraction)),
                Math.round(lerp(Color.green(from), Color.green(to), fraction)),
                Math.round(lerp(Color.blue(from), Color.blue(to), fraction)));
    }

    private void descendantRect(View view, Rect out) {
        out.set(0, 0, view.getWidth(), view.getHeight());
        offsetDescendantRectToMyCoords(view, out);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static float lerp(float start, float end, float fraction) {
        return start + ((end - start) * fraction);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @RequiresApi(29)
    private static final class Api29Insets {
        private Api29Insets() {
        }

        static WindowInsets withSystemBottom(WindowInsets base, int bottom) {
            return new WindowInsets.Builder(base)
                    .setSystemWindowInsets(Insets.of(0, 0, 0, bottom))
                    .build();
        }
    }

    @RequiresApi(30)
    private static final class Api30Insets {
        private Api30Insets() {
        }

        static WindowInsets withNavigationBottom(WindowInsets base, int bottom) {
            return new WindowInsets.Builder(base)
                    .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, bottom))
                    .build();
        }
    }

}
