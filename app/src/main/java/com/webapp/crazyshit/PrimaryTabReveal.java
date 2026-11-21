package com.webapp.crazyshit;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

/** Draws the two retained pages in place while the pager continues to own selection. */
final class PrimaryTabReveal implements ViewPager2.PageTransformer {
    private static final float EDGE_EPSILON = .001f;
    static final int BOTTOM = 1, FROM_LEFT = 2, FROM_RIGHT = 3;
    private static final long TAP_DURATION_MS = 240L;
    private final ViewPager2 pager;
    private final RecyclerView pages;
    private final int[] dockLocation = new int[2];
    private final int[] pagerLocation = new int[2];
    private ValueAnimator tap;
    private int tapFrom, tapTo;
    private float tapX, tapY, tapProgress;
    private int dragStart = RecyclerView.NO_POSITION;
    private float dragPosition;

    PrimaryTabReveal(ViewPager2 pager) {
        this.pager = pager;
        pages = (RecyclerView) pager.getChildAt(0);
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
            dock.getLocationInWindow(dockLocation);
            pager.getLocationInWindow(pagerLocation);
            int slot = rtl() ? MainPagerAdapter.PAGE_COUNT - 1 - target : target;
            float inset = 4f * dock.getResources().getDisplayMetrics().density;
            tapX = dockLocation[0] - pagerLocation[0] + inset
                    + (slot + .5f) * (dock.getWidth() - 2f * inset) / MainPagerAdapter.PAGE_COUNT;
            tapY = dockLocation[1] - pagerLocation[1] + dock.getHeight() / 2f;
            tapFrom = from;
            tapTo = target;
        }
        tapProgress = startProgress;
        tap = ValueAnimator.ofFloat(startProgress, reverse ? 0f : 1f);
        tap.setDuration(reverse ? Math.max(80L, Math.round(startProgress * TAP_DURATION_MS)) : TAP_DURATION_MS);
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
            dragPosition = dragStart;
        }
    }

    void onPageScrolled(float position) { dragPosition = position; }

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

    @Override public void transformPage(View view, float position) {
        if (!(view instanceof Page)) return;
        Page page = (Page) view;
        int id = pages.getChildAdapterPosition(view);
        int from = RecyclerView.NO_POSITION;
        int to = RecyclerView.NO_POSITION;
        int shape = 0;
        float progress = 0f;
        if (tap != null) {
            from = tapFrom;
            to = tapTo;
            shape = BOTTOM;
            progress = tapProgress;
        } else if (dragStart != RecyclerView.NO_POSITION) {
            to = dragTarget(dragPosition, dragStart);
            if (to != RecyclerView.NO_POSITION) {
                boolean towardEnd = dragPosition >= dragStart;
                from = towardEnd ? to - 1 : to + 1;
                shape = towardEnd != rtl() ? FROM_RIGHT : FROM_LEFT;
                progress = Math.abs(dragPosition - from);
            }
        }

        boolean outgoing = id != RecyclerView.NO_POSITION && id == from;
        boolean incoming = id != RecyclerView.NO_POSITION && id == to;
        if (outgoing || incoming) {
            page.setTranslationX(-position * pager.getWidth() * (rtl() ? -1f : 1f));
            page.setTranslationZ(incoming ? 1f : 0f);
            page.setAlpha(1f);
            page.setScaleX(1f);
            page.setScaleY(1f);
            page.setReveal(incoming ? shape : 0, progress, tapX, tapY);
        } else {
            page.setTranslationX(0f);
            page.setTranslationZ(0f);
            page.setReveal(0, 0f, 0f, 0f);
            float distance = ZeroChillMotion.animationsEnabled(pager.getContext())
                    ? Math.min(1f, Math.abs(position)) : 0f;
            page.setAlpha(1f - distance * .10f);
            page.setScaleX(1f - distance * .012f);
            page.setScaleY(page.getScaleX());
        }
    }

    private boolean rtl() { return pager.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL; }

    static final class Page extends FrameLayout {
        private final Path clip = new Path();
        private int shape;
        private float progress, originX, originY;

        Page(Context context) { super(context); }

        void setReveal(int shape, float progress, float originX, float originY) {
            if (this.shape == shape && (shape == 0 || (this.progress == progress
                    && this.originX == originX && this.originY == originY))) return;
            this.shape = shape;
            this.progress = progress;
            this.originX = originX;
            this.originY = originY;
            invalidate();
        }

        @Override public void draw(Canvas canvas) {
            if (shape == 0 || progress >= 1f) {
                super.draw(canvas);
                return;
            }
            if (progress <= 0f) return;
            float w = getWidth();
            float h = getHeight();
            clip.rewind();
            if (shape == BOTTOM) {
                float stretch = 1.32f;
                float dx = Math.max(Math.abs(originX), Math.abs(w - originX)) / stretch;
                float dy = Math.max(Math.abs(originY), Math.abs(h - originY));
                float radius = (float) Math.hypot(dx, dy) * progress;
                clip.addOval(originX - radius * stretch, originY - radius,
                        originX + radius * stretch, originY + radius, Path.Direction.CW);
            } else {
                boolean right = shape == FROM_RIGHT;
                float edge = w * (right ? 1f - progress : progress);
                float bow = Math.min(Math.min(edge, w - edge), .13f * w) * (right ? 1 : -1);
                float back = right ? w : 0f;
                clip.moveTo(edge + bow, 0f);
                clip.quadTo(edge - 3f * bow, h / 2f, edge + bow, h);
                clip.lineTo(back, h);
                clip.lineTo(back, 0f);
                clip.close();
            }
            int save = canvas.save();
            canvas.clipPath(clip);
            super.draw(canvas);
            canvas.restoreToCount(save);
        }
    }
}
