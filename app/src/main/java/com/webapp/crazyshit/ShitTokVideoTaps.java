package com.webapp.crazyshit;

import android.content.Context;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** KidTok's confirmed-single/add-only-double pattern, scoped to the video touch target. */
final class ShitTokVideoTaps {
    interface DoubleTap { void favorite(float x, float y); }
    private final GestureDetector detector;
    private Object item;

    ShitTokVideoTaps(Context context, Supplier<Object> identity, BooleanSupplier eligible,
            Runnable singleTap, DoubleTap doubleTap) {
        detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            private boolean current() { return item != null && item == identity.get() && eligible.getAsBoolean(); }
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (current()) singleTap.run();
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e) { return current(); }
            @Override public boolean onDoubleTapEvent(MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN && current())
                    doubleTap.favorite(e.getX(), e.getY());
                return true;
            }
        });
        detector.setIsLongpressEnabled(false);
    }

    void touch(MotionEvent event, Object identity) {
        if (event.getPointerCount() > 1) { cancel(); return; }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (item != identity) cancel();
            item = identity;
        }
        detector.onTouchEvent(event);
    }

    void cancel() {
        if (item == null) return;
        item = null;
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
        detector.onTouchEvent(cancel);
        cancel.recycle();
    }
}
