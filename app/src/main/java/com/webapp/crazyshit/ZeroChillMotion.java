package com.webapp.crazyshit;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

/** Bounded motion that preserves content state and respects Android's animator setting. */
final class ZeroChillMotion {
    static final long PRESS_IN_MS = 80L;
    static final long QUICK_MS = 140L;
    static final long STANDARD_MS = 190L;
    static final long MEDIA_MS = 250L;
    static final PathInterpolator EASE_OUT = new PathInterpolator(0.2f, 0f, 0f, 1f);

    private ZeroChillMotion() {}

    static boolean animationsEnabled(Context context) {
        return ValueAnimator.areAnimatorsEnabled()
                && context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getBoolean("immersive_motion_enabled", true);
    }

    static long duration(Context context, long milliseconds) {
        return animationsEnabled(context) ? milliseconds : 0L;
    }

    static void installPressFeedback(View view) {
        if (view == null || view.getTag(R.id.zerochill_motion_installed) != null) return;
        PressFeedback feedback = new PressFeedback(view);
        view.setTag(R.id.zerochill_motion_installed, feedback);
        view.setOnTouchListener(feedback);
        view.addOnAttachStateChangeListener(feedback);
    }

    // A separate animator owns only scale, never entrance alpha or pending-message opacity.
    private static final class PressFeedback implements View.OnTouchListener, View.OnAttachStateChangeListener {
        final View view;
        ValueAnimator animator;
        float baseX = 1f;
        float baseY = 1f;
        boolean pressed;

        PressFeedback(View view) { this.view = view; }

        @Override public boolean onTouch(View target, MotionEvent event) {
            if (!target.isEnabled() || !animationsEnabled(target.getContext())) {
                reset();
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    reset();
                    baseX = target.getScaleX();
                    baseY = target.getScaleY();
                    pressed = true;
                    animateTo(baseX * 0.985f, baseY * 0.985f, PRESS_IN_MS, false);
                    break;
                case MotionEvent.ACTION_UP:
                    if (pressed) animateTo(baseX, baseY, QUICK_MS, true);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    reset();
                    break;
                default:
                    break;
            }
            return false;
        }

        void animateTo(float x, float y, long duration, boolean release) {
            ValueAnimator previous = animator;
            animator = null;
            if (previous != null) previous.cancel();
            float fromX = view.getScaleX();
            float fromY = view.getScaleY();
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(duration);
            animator.setInterpolator(EASE_OUT);
            animator.addUpdateListener(animation -> {
                float fraction = (float) animation.getAnimatedValue();
                view.setScaleX(fromX + (x - fromX) * fraction);
                view.setScaleY(fromY + (y - fromY) * fraction);
            });
            if (release) animator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (animator == animation) { pressed = false; animator = null; }
                }
            });
            animator.start();
        }

        void reset() {
            ValueAnimator previous = animator;
            animator = null;
            if (previous != null) previous.cancel();
            if (pressed) {
                view.setScaleX(baseX);
                view.setScaleY(baseY);
            }
            pressed = false;
        }
        @Override public void onViewAttachedToWindow(View view) {}
        @Override public void onViewDetachedFromWindow(View view) { reset(); }
    }

    static void enterFromEnd(View view, float distance) {
        if (view == null) return;
        view.animate().cancel();
        if (!animationsEnabled(view.getContext())) {
            view.setAlpha(1f);
            view.setTranslationX(0f);
            return;
        }
        view.setAlpha(0f);
        view.setTranslationX(distance);
        view.animate().alpha(1f).translationX(0f).setDuration(STANDARD_MS)
                .setInterpolator(EASE_OUT).start();
    }
}
