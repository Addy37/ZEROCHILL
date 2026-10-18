package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import java.util.function.BooleanSupplier;

/** Touch-sequence-local transform. Never changes the player, surface, or layout size. */
final class ShitTokDynamicPinchController {
    static final float MIN_SCALE = 0.45f;
    static final float MAX_SCALE = 3.5f;
    interface Listener { void onPinchChanged(boolean active); }

    private final TouchFrame root;
    private final View media;
    private final BooleanSupplier eligible;
    private final Listener listener;
    private final View[] overlays;
    private final float[] overlayAlphas;
    private final Rect bounds = new Rect();
    private boolean videoTouchStarted;
    private boolean active;
    private boolean swallowRemainder;
    private int firstId, secondId;
    private float initialSpan, originX, originY, pivotX, pivotY;

    ShitTokDynamicPinchController(TouchFrame root, View media, BooleanSupplier eligible,
            Listener listener, View... overlays) {
        this.root = root;
        this.media = media;
        this.eligible = eligible;
        this.listener = listener;
        this.overlays = overlays;
        overlayAlphas = new float[overlays.length];
        root.pinch = this;
        media.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (active && (l != ol || t != ot || r != or || b != ob)) reset();
        });
    }

    boolean isActive() { return active; }
    boolean ownsTouchSequence() { return active || swallowRemainder; }
    void videoTouchStarted() { videoTouchStarted = true; }

    static float clampedScale(float span, float initialSpan) {
        if (!Float.isFinite(span) || !Float.isFinite(initialSpan) || initialSpan <= 0f) return 1f;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, span / initialSpan));
    }

    private boolean handle(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            reset();
            videoTouchStarted = false;
            swallowRemainder = false;
            return false;
        }
        if (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL) {
            boolean consumed = ownsTouchSequence();
            reset();
            return consumed;
        }
        if (active && event.getPointerCount() != 2) {
            reset();
            return true;
        }
        if (!active && !swallowRemainder && videoTouchStarted
                && action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() == 2
                && eligible.getAsBoolean()) {
            float span = span(event, 0, 1);
            if (span < 1f) return false;
            firstId = event.getPointerId(0);
            secondId = event.getPointerId(1);
            initialSpan = span;
            // Capture untransformed coordinates once. All later events stay in root space.
            bounds.set(0, 0, media.getWidth(), media.getHeight());
            root.offsetDescendantRectToMyCoords(media, bounds);
            originX = bounds.left;
            originY = bounds.top;
            pivotX = media.getPivotX();
            pivotY = media.getPivotY();
            active = true;
            swallowRemainder = true;
            root.requestDisallowInterceptTouchEvent(true);
            listener.onPinchChanged(true);
            for (int i = 0; i < overlays.length; i++) {
                View overlay = overlays[i];
                if (overlay == null) continue;
                overlay.animate().withEndAction(null).cancel();
                overlayAlphas[i] = overlay.getAlpha();
                overlay.setAlpha(0f);
            }
            return true;
        }
        if (active && action == MotionEvent.ACTION_MOVE) {
            int first = event.findPointerIndex(firstId);
            int second = event.findPointerIndex(secondId);
            if (first < 0 || second < 0) {
                reset();
                return true;
            }
            float scale = clampedScale(span(event, first, second), initialSpan);
            media.setPivotX((event.getX(first) + event.getX(second)) * 0.5f - originX);
            media.setPivotY((event.getY(first) + event.getY(second)) * 0.5f - originY);
            media.setScaleX(scale);
            media.setScaleY(scale);
        }
        return ownsTouchSequence();
    }

    /** Also called on holder/owner/host transitions, before the next item can draw. */
    void reset() {
        videoTouchStarted = false;
        if (!active) return;
        active = false;
        media.setScaleX(1f);
        media.setScaleY(1f);
        media.setPivotX(pivotX);
        media.setPivotY(pivotY);
        for (int i = 0; i < overlays.length; i++) {
            if (overlays[i] != null) overlays[i].setAlpha(overlayAlphas[i]);
        }
        root.requestDisallowInterceptTouchEvent(false);
        listener.onPinchChanged(false);
    }

    private static float span(MotionEvent event, int a, int b) {
        return (float) Math.hypot(event.getX(a) - event.getX(b), event.getY(a) - event.getY(b));
    }

    /** Observe before child dispatch so scaled media never changes gesture coordinates. */
    static final class TouchFrame extends FrameLayout {
        private ShitTokDynamicPinchController pinch;
        TouchFrame(Context context) { super(context); }
        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            boolean wasOwned = pinch != null && pinch.ownsTouchSequence();
            if (pinch != null && pinch.handle(event)) {
                if (!wasOwned && pinch.isActive()) {
                    // Cancel the original video click/creator-swipe target exactly once.
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                    // ViewGroup clears its own disallow flag on CANCEL. Re-arm it so
                    // the later release propagates false all the way to the pager.
                    requestDisallowInterceptTouchEvent(true);
                }
                return true;
            }
            return super.dispatchTouchEvent(event);
        }
        @Override protected void onDetachedFromWindow() {
            if (pinch != null) pinch.reset();
            super.onDetachedFromWindow();
        }
    }
}
