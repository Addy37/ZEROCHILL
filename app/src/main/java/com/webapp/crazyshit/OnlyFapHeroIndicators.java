package com.webapp.crazyshit;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Compact Nuvio-style hero indicator. The selected dot animates as an accent pill. */
final class OnlyFapHeroIndicators extends View {
    interface Listener { void onSelect(int position); }

    static final int MAX_VISIBLE = 7;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Listener listener;
    private ValueAnimator animator;
    private int count;
    private int selected;
    private int first;
    private int accent = Color.rgb(8, 146, 208);
    private float highlightedSlot;
    private float emphasis = 1f;
    private float downX;
    private boolean hasPosition;

    OnlyFapHeroIndicators(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void setListener(Listener listener) { this.listener = listener; }

    static int startIndex(int selected, int count) {
        if (count <= MAX_VISIBLE) return 0;
        return Math.min(count - MAX_VISIBLE, Math.max(0, selected - MAX_VISIBLE / 2));
    }

    void show(int selected, int count, int accent, boolean animate) {
        int previousSelected = this.selected;
        int previousCount = this.count;
        int newCount = Math.max(0, count);
        int next = newCount == 0 ? 0 : Math.min(Math.max(selected, 0), newCount - 1);
        int nextFirst = startIndex(next, newCount);
        float nextSlot = next - nextFirst;
        float fromSlot = hasPosition ? highlightedSlot : nextSlot;
        if (animator != null) { animator.cancel(); animator = null; }
        this.count = newCount;
        this.selected = next;
        this.first = nextFirst;
        this.accent = accent;
        this.hasPosition = true;
        setContentDescription("Featured creator " + (next + 1) + " of " + newCount
                + ". Tap an indicator to switch.");
        requestLayout();
        if (animate && ZeroChillMotion.animationsEnabled(getContext()) && newCount > 1 && (previousSelected != next || previousCount != newCount)) {
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(280L);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                emphasis = (float) a.getAnimatedValue();
                highlightedSlot = fromSlot + (nextSlot - fromSlot) * emphasis;
                invalidate();
            });
            animator.start();
        } else {
            emphasis = 1f;
            highlightedSlot = nextSlot;
            invalidate();
        }
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int visible = Math.min(count, MAX_VISIBLE);
        int desiredW = dp(16) + Math.max(1, visible) * dp(18);
        int desiredH = dp(32);
        setMeasuredDimension(resolveSize(desiredW, widthMeasureSpec),
                resolveSize(desiredH, heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int visible = Math.min(count, MAX_VISIBLE);
        if (visible == 0) return;
        float y = getHeight() / 2f;
        float step = dp(18);
        float left = (getWidth() - visible * step) / 2f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(145, 255, 255, 255));
        for (int i = 0; i < visible; i++) {
            canvas.drawCircle(left + step * (i + .5f), y, dp(3), paint);
        }
        paint.setColor(accent);
        float cx = left + step * (highlightedSlot + .5f);
        float halfWidth = dp(7) + dp(2) * (float) Math.sin(emphasis * Math.PI);
        canvas.drawRoundRect(cx - halfWidth, y - dp(3.25f), cx + halfWidth,
                y + dp(3.25f), dp(3.25f), dp(3.25f), paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (count <= 1) return super.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                return true;
            case MotionEvent.ACTION_UP:
                if (Math.abs(event.getX() - downX) > dp(12)) return false;
                performClick();
                int visible = Math.min(count, MAX_VISIBLE);
                float left = (getWidth() - visible * dp(18)) / 2f;
                int slot = (int) Math.floor((event.getX() - left) / dp(18));
                if (slot >= 0 && slot < visible && listener != null) {
                    int chosen = first + slot;
                    if (chosen != selected) listener.onSelect(chosen);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) { animator.cancel(); animator = null; }
        super.onDetachedFromWindow();
    }

    private int dp(float v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
