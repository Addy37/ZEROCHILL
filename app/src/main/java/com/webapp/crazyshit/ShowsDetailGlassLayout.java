package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

/** One page recording for the Shows detail header and pager.
 *  The episode section stays a clean opaque bar; only the chrome the user asked
 *  to frost samples the live page. */
final class ShowsDetailGlassLayout extends FrameLayout {
    static final int HEADER_HEIGHT_DP = 64;
    static final int EPISODE_BAR_HEIGHT_DP = 48;
    static final int PAGER_SIDE_MARGIN_DP = 12;
    static final int PAGER_BOTTOM_MARGIN_DP = 8;
    static final int PAGER_RADIUS_DP = 28;

    private final ViewPageBackdrop pageBackdrop = new ViewPageBackdrop(this);
    private GlassHeaderView headerView;
    private GlassHeaderView episodeView;
    private GlassHeaderView pagerView;
    private View contentView;
    private View episodeAnchor;
    private RecyclerView list;
    private boolean pageDirty = true;

    ShowsDetailGlassLayout(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Color.BLACK);
        ViewCompat.setOnApplyWindowInsetsListener(this, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets gestures = insets.getInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.mandatorySystemGestures());
            int bottom = safeBottomInset(bars, gestures);
            if (getPaddingLeft() != bars.left || getPaddingTop() != bars.top
                    || getPaddingRight() != bars.right || getPaddingBottom() != bottom) {
                setPadding(bars.left, bars.top, bars.right, bottom);
                pageDirty = true;
            }
            return insets;
        });
    }

    static int safeBottomInset(Insets bars, Insets gestures) {
        return Math.max(bars == null ? 0 : bars.bottom, gestures == null ? 0 : gestures.bottom);
    }

    void install(GlassHeaderView header, View content, GlassHeaderView episodes,
            GlassHeaderView pager, View episodeAnchor, RecyclerView list) {
        headerView = header;
        contentView = content;
        episodeView = episodes;
        pagerView = pager;
        this.episodeAnchor = episodeAnchor;
        this.list = list;

        header.setPageBackdrop(pageBackdrop);
        // The product request was top + bottom glass. Keeping this section opaque
        // prevents a third material layer from washing over episode artwork.
        episodes.setGlassEnabled(false);
        pager.setGlassCornerRadius(PAGER_RADIUS_DP);
        pager.setPageBackdrop(pageBackdrop);

        if (content instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) content;
            group.setClipChildren(false);
            group.setClipToPadding(false);
        }
        addView(content, new LayoutParams(-1, -1));
        addView(header, new LayoutParams(-1, -2));
        addView(episodes, new LayoutParams(-1, -2));
        addView(pager, new LayoutParams(-1, -2));
    }

    ViewPageBackdrop backdropForTest() { return pageBackdrop; }
    GlassHeaderView headerForTest() { return headerView; }
    GlassHeaderView episodePaneForTest() { return episodeView; }
    GlassHeaderView pagerForTest() { return pagerView; }
    View contentForTest() { return contentView; }

    boolean excludesGlassFromRecording() {
        return headerView != null && episodeView != null && pagerView != null && contentView != null
                && headerView.getParent() == this && episodeView.getParent() == this
                && pagerView.getParent() == this && contentView.getParent() == this
                && !contains(contentView, headerView) && !contains(contentView, episodeView)
                && !contains(contentView, pagerView);
    }

    int underlayClipTop() {
        return contentView == null ? getPaddingTop() : contentView.getTop();
    }

    /** The collapsing hero moves the episode anchor; keep the opaque section bar with it. */
    void followEpisodeAnchor() {
        if (episodeView == null || !isLaidOut()) return;
        int target = clampedEpisodeTop(episodeView.getHeight());
        if (episodeView.getTop() == target) return;
        episodeView.layout(episodeView.getLeft(), target,
                episodeView.getRight(), target + episodeView.getHeight());
        pageDirty = true;
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewCompat.requestApplyInsets(this);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = View.MeasureSpec.getSize(widthMeasureSpec);
        int height = View.MeasureSpec.getSize(heightMeasureSpec);
        int innerW = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int innerH = Math.max(0, height - getPaddingTop() - getPaddingBottom());
        int headerH = Math.min(innerH, BrowseUi.dp(getContext(), HEADER_HEIGHT_DP));
        int episodeH = Math.min(Math.max(0, innerH - headerH),
                BrowseUi.dp(getContext(), EPISODE_BAR_HEIGHT_DP));
        int pagerSideMargin = BrowseUi.dp(getContext(), PAGER_SIDE_MARGIN_DP);
        int pagerW = Math.max(0, innerW - (pagerSideMargin * 2));

        // Content now genuinely runs behind the top glass instead of starting below it.
        if (contentView != null) contentView.measure(
                View.MeasureSpec.makeMeasureSpec(innerW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(innerH, View.MeasureSpec.EXACTLY));
        if (headerView != null) headerView.measure(
                View.MeasureSpec.makeMeasureSpec(innerW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(headerH, View.MeasureSpec.EXACTLY));
        if (episodeView != null) episodeView.measure(
                View.MeasureSpec.makeMeasureSpec(innerW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(episodeH, View.MeasureSpec.EXACTLY));
        if (pagerView != null) pagerView.measure(
                View.MeasureSpec.makeMeasureSpec(pagerW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(Math.max(0, innerH - headerH), View.MeasureSpec.AT_MOST));
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int safeLeft = getPaddingLeft();
        int safeTop = getPaddingTop();
        int safeRight = right - left - getPaddingRight();
        int safeBottom = bottom - top - getPaddingBottom();
        int headerH = headerView == null ? 0 : headerView.getMeasuredHeight();

        if (contentView != null) contentView.layout(safeLeft, safeTop, safeRight, safeBottom);
        if (headerView != null) headerView.layout(safeLeft, safeTop, safeRight, safeTop + headerH);

        int pagerMargin = BrowseUi.dp(getContext(), PAGER_SIDE_MARGIN_DP);
        int pagerBottomMargin = BrowseUi.dp(getContext(), PAGER_BOTTOM_MARGIN_DP);
        int pagerH = pagerView == null ? 0 : pagerView.getMeasuredHeight();
        int pagerBottom = Math.max(safeTop + headerH, safeBottom - pagerBottomMargin);
        if (pagerH > pagerBottom - (safeTop + headerH)) {
            pagerH = Math.max(0, pagerBottom - safeTop - headerH);
        }
        if (pagerView != null) {
            int pagerLeft = Math.min(safeRight, safeLeft + pagerMargin);
            int pagerRight = Math.max(pagerLeft, safeRight - pagerMargin);
            pagerView.layout(pagerLeft, pagerBottom - pagerH, pagerRight, pagerBottom);
        }

        if (episodeView != null) {
            int episodeH = episodeView.getMeasuredHeight();
            int episodeTop = clampedEpisodeTop(episodeH);
            episodeView.layout(safeLeft, episodeTop, safeRight, episodeTop + episodeH);
        }
        syncListPadding();
        pageDirty = true;
    }

    private int clampedEpisodeTop(int episodeH) {
        int minTop = headerView == null ? getPaddingTop() : headerView.getBottom();
        int maxBottom = pagerView == null ? getHeight() - getPaddingBottom() : pagerView.getTop();
        int maxTop = Math.max(minTop, maxBottom - episodeH);
        int anchor = anchorTop();
        if (anchor < minTop) anchor = minTop;
        if (anchor > maxTop) anchor = maxTop;
        return anchor;
    }

    private int anchorTop() {
        if (episodeAnchor == null) return headerView == null ? getPaddingTop() : headerView.getBottom();
        int top = 0;
        View view = episodeAnchor;
        while (view != null && view.getParent() instanceof View && view.getParent() != this) {
            top += view.getTop();
            view = (View) view.getParent();
        }
        if (view != null && view.getParent() == this) top += view.getTop();
        return top;
    }

    private void syncListPadding() {
        if (list == null || episodeView == null || pagerView == null) return;
        int top = episodeView.getHeight();
        int bottom = pagerView.getHeight()
                + BrowseUi.dp(getContext(), PAGER_BOTTOM_MARGIN_DP * 2);
        if (list.getPaddingTop() == top && list.getPaddingBottom() == bottom
                && list.getPaddingLeft() == 0 && list.getPaddingRight() == 0) {
            if (list.getClipToPadding()) list.setClipToPadding(false);
            return;
        }
        list.setClipToPadding(false);
        list.setPadding(0, top, 0, bottom);
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        boolean chromeVisible = visible(headerView) || visible(episodeView) || visible(pagerView);
        if (Build.VERSION.SDK_INT >= 29 && canvas.isHardwareAccelerated() && chromeVisible
                && getWindowVisibility() == VISIBLE && getWidth() > 0 && getHeight() > 0) {
            if (pageDirty) {
                pageDirty = false;
                pageBackdrop.record(getWidth(), getHeight(), recording -> {
                    drawUnderlay(recording);
                    return kotlin.Unit.INSTANCE;
                });
            }
            if (pageBackdrop.drawPage(canvas)) {
                drawChrome(canvas);
                return;
            }
        }
        drawUnderlay(canvas);
        drawChrome(canvas);
    }

    private void drawUnderlay(Canvas canvas) {
        if (contentView == null || contentView.getVisibility() == GONE) return;
        int checkpoint = canvas.save();
        canvas.clipRect(contentView.getLeft(), underlayClipTop(),
                contentView.getRight(), contentView.getBottom());
        drawChild(canvas, contentView, getDrawingTime());
        canvas.restoreToCount(checkpoint);
    }

    private void drawChrome(Canvas canvas) {
        if (visible(episodeView)) drawChild(canvas, episodeView, getDrawingTime());
        if (visible(pagerView)) drawChild(canvas, pagerView, getDrawingTime());
        if (visible(headerView)) drawChild(canvas, headerView, getDrawingTime());
    }

    private static boolean visible(View view) {
        return view != null && view.getVisibility() == VISIBLE;
    }

    @Override public void onDescendantInvalidated(View child, View target) {
        if (child == contentView) pageDirty = true;
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

    private static boolean contains(View root, View target) {
        if (!(root instanceof ViewGroup) || target == null) return false;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child == target || contains(child, target)) return true;
        }
        return false;
    }
}
