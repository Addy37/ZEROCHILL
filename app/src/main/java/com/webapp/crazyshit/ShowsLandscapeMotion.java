package com.webapp.crazyshit;

import android.animation.ValueAnimator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;

/** Resizes the retained Shows surface while the landscape rail and controls fade. */
@UnstableApi
final class ShowsLandscapeMotion {
    static final long RESIZE_MS = 250L;
    static final long CHROME_MS = 150L;
    private final LinearLayout shell;
    private final PlayerView surface;
    private final HorizontalScrollView rail;
    private final LinearLayout actions;
    private final ShowsFullscreenControls controls;
    private final FrameLayout.LayoutParams surfaceParams;
    private final LinearLayout.LayoutParams railParams;
    private final int railHeight;
    private ValueAnimator resize;
    private float fraction = 1f;
    private boolean active;
    private boolean targetVisible = true;
    private int lastWidth, lastHeight;

    ShowsLandscapeMotion(LinearLayout shell, PlayerView surface,
            HorizontalScrollView rail, LinearLayout actions, ShowsFullscreenControls controls, int railHeight) {
        this.shell = shell;
        this.surface = surface;
        this.rail = rail;
        this.actions = actions;
        this.controls = controls;
        this.railHeight = railHeight;
        surfaceParams = (FrameLayout.LayoutParams) surface.getLayoutParams();
        railParams = (LinearLayout.LayoutParams) rail.getLayoutParams();
        trimSecondaryActions(actions);
        shell.addOnLayoutChangeListener((v, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            if (active && (right - left != lastWidth || bottom - top != lastHeight)) apply();
        });
        rail.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) controls.holdInteraction(true);
            else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                controls.holdInteraction(false);
            return false; // Preserve rail scrolling and card taps.
        });
        rail.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            if (x != oldX && active) controls.show();
        });
        for (int i = 0; i < actions.getChildCount(); i++) {
            actions.getChildAt(i).setOnTouchListener((v, event) -> {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) controls.holdInteraction(true);
                else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                    controls.holdInteraction(false);
                return false;
            });
        }
    }

    static void trimSecondaryActions(LinearLayout actions) {
        if (actions == null) return;
        for (int i = actions.getChildCount() - 1; i >= 0; i--) {
            CharSequence description = actions.getChildAt(i).getContentDescription();
            if ("Open comments".contentEquals(description) || "Share video".contentEquals(description)) {
                actions.removeViewAt(i);
            }
        }
    }

    void enter() {
        if (active) { apply(); return; }
        cancel();
        active = true;
        targetVisible = true;
        fraction = 1f;
        rail.setVisibility(View.VISIBLE);
        actions.setVisibility(View.VISIBLE);
        rail.setAlpha(1f);
        actions.setAlpha(1f);
        rail.setEnabled(true);
        actions.setEnabled(true);
        railParams.height = railHeight;
        rail.setLayoutParams(railParams);
        apply();
        // Configuration can arrive before the new landscape dimensions are measured.
        shell.post(this::apply);
    }

    void visible(boolean shown) {
        if (!active) return;
        float target = shown ? 1f : 0f;
        if (targetVisible == shown && (fraction == target ||
                (resize != null && resize.isRunning()))) return;
        targetVisible = shown;
        rail.animate().cancel();
        actions.animate().cancel();
        if (shown) {
            rail.setVisibility(View.VISIBLE);
            actions.setVisibility(View.VISIBLE);
        }
        rail.setEnabled(shown);
        actions.setEnabled(shown);
        rail.setImportantForAccessibility(shown ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        actions.setImportantForAccessibility(shown ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        rail.animate().alpha(target).setDuration(CHROME_MS).withEndAction(() -> {
            if (!targetVisible && active) rail.setVisibility(View.INVISIBLE);
        }).start();
        actions.animate().alpha(target).setDuration(CHROME_MS).withEndAction(() -> {
            if (!targetVisible && active) actions.setVisibility(View.INVISIBLE);
        }).start();
        if (resize != null) resize.cancel();
        if (fraction == target) { apply(); return; }
        resize = ValueAnimator.ofFloat(fraction, target);
        resize.setDuration(RESIZE_MS);
        resize.addUpdateListener(animator -> {
            fraction = (float) animator.getAnimatedValue();
            apply();
        });
        resize.start();
    }

    void leave() {
        active = false;
        targetVisible = true;
        cancel();
        rail.animate().cancel();
        actions.animate().cancel();
        rail.setVisibility(View.GONE);
        actions.setVisibility(View.GONE);
        rail.setAlpha(1f);
        actions.setAlpha(1f);
        rail.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        actions.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        railParams.height = railHeight;
        rail.setLayoutParams(railParams);
        surfaceParams.width = -1;
        surfaceParams.height = -1;
        surfaceParams.gravity = Gravity.FILL;
        surfaceParams.topMargin = 0;
        surface.setLayoutParams(surfaceParams);
    }

    void cancel() {
        if (resize != null) { resize.cancel(); resize = null; }
        rail.animate().cancel();
        actions.animate().cancel();
        if (active) {
            rail.setAlpha(targetVisible ? 1f : 0f);
            actions.setAlpha(targetVisible ? 1f : 0f);
            if (!targetVisible) {
                rail.setVisibility(View.INVISIBLE);
                actions.setVisibility(View.INVISIBLE);
            }
        }
    }

    float fraction() { return fraction; }

    private void apply() {
        if (!active) return;
        int width = shell.getWidth();
        int height = shell.getHeight();
        if (width <= 0 || height <= 0) return;
        lastWidth = width;
        lastHeight = height;
        int targetRailHeight = Math.round(railHeight * fraction);
        if (railParams.height != targetRailHeight) {
            railParams.height = targetRailHeight;
            rail.setLayoutParams(railParams);
        }
        float scale = Math.min(width / 1536f, height / 698f);
        float compactWidth = Math.min(864f * scale, Math.min(width,
                Math.max(0f, height - railHeight) * 16f / 9f));
        float fullWidth = Math.min(width, height * 16f / 9f);
        int targetWidth = Math.round(fullWidth + (compactWidth - fullWidth) * fraction);
        int targetHeight = Math.round(targetWidth * 9f / 16f);
        int targetTop = Math.round((height - fullWidth * 9f / 16f) / 2f * (1f - fraction));
        if (surfaceParams.width != targetWidth || surfaceParams.height != targetHeight ||
                surfaceParams.gravity != (Gravity.TOP | Gravity.CENTER_HORIZONTAL) ||
                surfaceParams.topMargin != targetTop) {
            surfaceParams.width = targetWidth;
            surfaceParams.height = targetHeight;
            surfaceParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            surfaceParams.topMargin = targetTop;
            surface.setLayoutParams(surfaceParams);
        }
    }

    /** The compact reference is 864:486 inside 1536:698; hidden controls fit 16:9. */
    static Bounds bounds(float width, float height, float railHeight, float fraction) {
        float scale = Math.min(width / 1536f, height / 698f);
        float compactWidth = Math.min(864f * scale, Math.min(width,
                Math.max(0f, height - railHeight) * 16f / 9f));
        float compactHeight = compactWidth * 9f / 16f;
        float fullWidth = Math.min(width, height * 16f / 9f);
        float fullHeight = fullWidth * 9f / 16f;
        float f = Math.max(0f, Math.min(1f, fraction));
        float videoWidth = fullWidth + (compactWidth - fullWidth) * f;
        float videoHeight = fullHeight + (compactHeight - fullHeight) * f;
        float top = (height - fullHeight) / 2f * (1f - f);
        return new Bounds((width - videoWidth) / 2f, top, videoWidth, videoHeight);
    }

    static final class Bounds {
        final float left, top, width, height;
        Bounds(float left, float top, float width, float height) {
            this.left = left; this.top = top; this.width = width; this.height = height;
        }
    }
}
