package com.webapp.crazyshit;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

/**
 * A connected-screen alternative to the liquid page mask.
 * The dock keeps owning selection and drag input. Only the retained screen pages
 * get a short directional fade and depth treatment, without bitmap copies or blur.
 */
final class PrimaryTabReveal implements ViewPager2.PageTransformer {
    private static final float EDGE_EPSILON = .001f;
    // Retained for the existing waterline implementation, which is unused by this trial.
    static final int BOTTOM = 1, FROM_LEFT = 2, FROM_RIGHT = 3;
    static final long TAP_DURATION_MS = 290L;
    private static final float ENTRY_OFFSET_DP = 18f;
    private static final float EXIT_OFFSET_DP = 5f;
    private final ViewPager2 pager;
    private final RecyclerView pages;
    private final float density;
    private ValueAnimator tap;
    private int tapFrom, tapTo;
    private float tapProgress;
    private int dragStart = RecyclerView.NO_POSITION;

    PrimaryTabReveal(ViewPager2 pager) {
        this.pager = pager;
        pages = (RecyclerView) pager.getChildAt(0);
        density = pager.getResources().getDisplayMetrics().density;
    }

    boolean startTap(int target, View dock) {
        int from = pager.getCurrentItem();
        if (target == from) return false;
        boolean reverse = tap != null && from == tapTo && target == tapFrom;
        float startProgress = reverse ? tapProgress : 0f;
        cancel();
        if (!ZeroChillMotion.animationsEnabled(pager.getContext()) || !pager.isShown()
                || pager.getWidth() <= 0 || pager.getHeight() <= 0 || dock.getWidth() <= 0
                || pages.findViewHolderForAdapterPosition(from) == null
                || pages.findViewHolderForAdapterPosition(target) == null) return false;

        if (!reverse) {
            tapFrom = from;
            tapTo = target;
        }
        tapProgress = startProgress;
        tap = ValueAnimator.ofFloat(startProgress, reverse ? 0f : 1f);
        tap.setDuration(reverse ? Math.max(80L, Math.round(startProgress * TAP_DURATION_MS))
                : TAP_DURATION_MS);
        tap.setInterpolator(ZeroChillMotion.EASE_OUT);
        tap.addUpdateListener(animation -> {
            tapProgress = (float) animation.getAnimatedValue();
            pager.requestTransform();
        });
        tap.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (tap != animation) return;
                tap = null;
                pager.requestTransform();
            }
        });
        return true;
    }

    void startArmedTap() {
        if (tap != null) tap.start();
    }

    void startDrag() {
        cancel();
        if (ZeroChillMotion.animationsEnabled(pager.getContext())) {
            dragStart = pager.getCurrentItem();
        }
    }

    // The pager continues to drive the dock's existing position and gestures.
    void onPageScrolled(float position) { }

    void finishDrag() {
        if (dragStart == RecyclerView.NO_POSITION) return;
        dragStart = RecyclerView.NO_POSITION;
        pager.requestTransform();
    }

    void cancel() {
        ValueAnimator previous = tap;
        boolean transforming = previous != null || dragStart != RecyclerView.NO_POSITION;
        tap = null;
        dragStart = RecyclerView.NO_POSITION;
        if (previous != null) previous.cancel();
        if (transforming) pager.requestTransform();
    }

    static int dragTarget(float position, int start) {
        int lower = (int) Math.floor(position);
        if (position - lower < EDGE_EPSILON || lower + 1 - position < EDGE_EPSILON) {
            return RecyclerView.NO_POSITION;
        }
        return position >= start ? lower + 1 : lower;
    }

    static float enteringAlpha(float progress) {
        return .10f + .90f * progress;
    }

    static float enteringScale(float progress) {
        return .990f + .010f * progress;
    }

    static float exitingScale(float progress) {
        return 1f - .012f * progress;
    }

    @Override public void transformPage(View view, float position) {
        if (!(view instanceof Page)) return;
        Page page = (Page) view;
        int id = pages.getChildAdapterPosition(view);
        if (tap != null && id != RecyclerView.NO_POSITION &&
                (id == tapFrom || id == tapTo)) {
            boolean incoming = id == tapTo;
            float p = Math.max(0f, Math.min(1f, tapProgress));
            float direction = (tapTo > tapFrom ? 1f : -1f) * (rtl() ? -1f : 1f);
            float alignedX = -position * pager.getWidth() * (rtl() ? -1f : 1f);
            page.setTranslationZ(incoming ? 1f : 0f);
            if (incoming) {
                page.setTranslationX(alignedX + direction * ENTRY_OFFSET_DP * density * (1f - p));
                page.setAlpha(enteringAlpha(p));
                page.setScaleX(enteringScale(p));
                page.setScaleY(enteringScale(p));
            } else {
                // Keep the prior screen visible while the next screen becomes ready.
                page.setTranslationX(alignedX - direction * EXIT_OFFSET_DP * density * p);
                page.setAlpha(1f);
                page.setScaleX(exitingScale(p));
                page.setScaleY(exitingScale(p));
            }
            return;
        }

        // A dock drag remains tied directly to the finger's native pager position.
        page.setTranslationX(0f);
        page.setTranslationZ(0f);
        float distance = ZeroChillMotion.animationsEnabled(pager.getContext())
                ? Math.min(1f, Math.abs(position)) : 0f;
        float intensity = dragStart != RecyclerView.NO_POSITION ? .06f : .10f;
        page.setAlpha(1f - intensity * distance);
        page.setScaleX(1f - .006f * distance);
        page.setScaleY(page.getScaleX());
    }

    private boolean rtl() { return pager.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL; }

    static final class Page extends FrameLayout {
        Page(Context context) { super(context); }
    }
}
