package com.webapp.crazyshit;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.SimpleItemAnimator;

/** Pinch once per gesture, and keep the first visible record when density changes. */
final class LibraryGridGesture extends RecyclerView.SimpleOnItemTouchListener {
    private final ScaleGestureDetector detector;
    private final Runnable smaller, larger;
    private float factor;
    private boolean intercepting;
    LibraryGridGesture(Context context, Runnable smaller, Runnable larger) {
        this.smaller = smaller; this.larger = larger;
        detector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector d) { factor = 1f; return true; }
            @Override public boolean onScale(ScaleGestureDetector d) { factor *= d.getScaleFactor(); return true; }
            @Override public void onScaleEnd(ScaleGestureDetector d) {
                if (factor < .80f) LibraryGridGesture.this.smaller.run();
                else if (factor > 1.25f) LibraryGridGesture.this.larger.run();
            }
        });
        detector.setQuickScaleEnabled(false);
    }
    @Override public boolean onInterceptTouchEvent(RecyclerView view, MotionEvent event) {
        detector.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            intercepting = true;
            return true;
        }
        return false;
    }
    @Override public void onTouchEvent(RecyclerView view, MotionEvent event) {
        if (!intercepting) return;
        if (event.getActionMasked() != MotionEvent.ACTION_POINTER_DOWN) detector.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
            intercepting = false;
    }
    static int clamp(int columns) { return Math.max(2, Math.min(5, columns)); }
    static int next(int columns) { return columns >= 5 ? 2 : columns + 1; }
    static void apply(RecyclerView recycler, int columns) {
        if (recycler == null || !(recycler.getLayoutManager() instanceof GridLayoutManager)) return;
        GridLayoutManager layout = (GridLayoutManager) recycler.getLayoutManager();
        int count = clamp(columns);
        if (layout.getSpanCount() == count) return;
        int first = layout.findFirstVisibleItemPosition();
        layout.setSpanCount(count);
        layout.getSpanSizeLookup().invalidateSpanIndexCache();
        if (recycler.getAdapter() != null) recycler.getAdapter().notifyDataSetChanged();
        if (first >= 0) layout.scrollToPositionWithOffset(first, 0);
    }
}
