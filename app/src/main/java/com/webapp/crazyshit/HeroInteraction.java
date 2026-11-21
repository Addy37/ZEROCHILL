package com.webapp.crazyshit;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.widget.FrameLayout;

/** A brief, directional accent wash on deliberate hero changes. */
final class HeroInteraction extends View {
    private final Paint paint = new Paint();
    private final View artwork;
    private ValueAnimator animation;
    private float strength;

    HeroInteraction(FrameLayout frame, View artwork, int height) {
        super(frame.getContext());
        this.artwork = artwork;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setVisibility(INVISIBLE);
        frame.addView(this, new FrameLayout.LayoutParams(-1, height));
    }

    void play(int direction) {
        if (!isAttachedToWindow() || !isShownInWindow() || getWidth() <= 0
                || !ZeroChillMotion.animationsEnabled(getContext())) return;
        stop();
        int accent = AppearanceTheme.accent(getContext());
        boolean fromRight = direction > 0;
        paint.setShader(new LinearGradient(fromRight ? getWidth() : 0, 0,
                fromRight ? getWidth() * .52f : getWidth() * .48f, getHeight() * .55f,
                accent, accent & 0x00ffffff, Shader.TileMode.CLAMP));
        setVisibility(VISIBLE);
        float travel = direction * BrowseUi.dp(getContext(), 9);
        animation = ValueAnimator.ofFloat(1f, 0f);
        animation.setDuration(280L);
        animation.setInterpolator(ZeroChillMotion.EASE_OUT);
        animation.addUpdateListener(frame -> {
            strength = (float) frame.getAnimatedValue();
            artwork.setTranslationX(travel * strength);
            invalidate();
        });
        animation.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator ended) {
                animation = null;
                artwork.setTranslationX(0f);
                setVisibility(INVISIBLE);
            }
        });
        animation.start();
    }

    private boolean isShownInWindow() {
        return getParent() instanceof View && ((View) getParent()).isShown()
                && getWindowVisibility() == VISIBLE;
    }

    void stop() {
        if (animation != null) animation.cancel();
    }

    @Override protected void onDraw(Canvas canvas) {
        paint.setAlpha(Math.round(strength * 72f));
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}
