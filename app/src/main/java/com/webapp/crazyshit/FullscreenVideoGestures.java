package com.webapp.crazyshit;

import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Surface taps only. Parents keep drag interception; transport controls keep their own touches. */
final class FullscreenVideoGestures implements View.OnTouchListener {
    private final PlayerView surface;
    private final Supplier<Player> player;
    private final ShowsFullscreenControls controls;
    private final BooleanSupplier enabled;
    private final GestureDetector detector;
    private final TextView feedback;
    private boolean dragged;
    private final Runnable clearFeedback = () -> feedbackGone();

    FullscreenVideoGestures(PlayerView surface, Supplier<Player> player,
            ShowsFullscreenControls controls, BooleanSupplier enabled) {
        this.surface = surface;
        this.player = player;
        this.controls = controls;
        this.enabled = enabled;
        feedback = BrowseUi.text(surface.getContext(), "", 14, android.graphics.Color.WHITE);
        feedback.setGravity(Gravity.CENTER);
        feedback.setPadding(dp(14), dp(10), dp(14), dp(10));
        feedback.setBackground(BrowseUi.rounded(surface.getContext(), 0xC014181D, 18));
        feedback.setVisibility(View.GONE);
        feedback.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        surface.addView(feedback, new FrameLayout.LayoutParams(-2, -2));
        detector = new GestureDetector(surface.getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { dragged = false; return true; }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (enabled.getAsBoolean()) controls.onVideoTap();
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e) { return true; }
            @Override public boolean onDoubleTapEvent(MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    if (!dragged && enabled.getAsBoolean()) seek(e.getX() >= surface.getWidth() / 2f);
                }
                return true;
            }
            @Override public boolean onScroll(MotionEvent first, MotionEvent next, float dx, float dy) {
                dragged = true;
                return false;
            }
            @Override public void onLongPress(MotionEvent e) {
                if (enabled.getAsBoolean()) surface.performLongClick();
            }
        });
        ViewCompat.addAccessibilityAction(surface, "Show or hide video controls", (v, args) -> {
            if (!enabled.getAsBoolean()) return false;
            controls.onVideoTap(); return true;
        });
        ViewCompat.addAccessibilityAction(surface, "Rewind 10 seconds", (v, args) -> {
            if (!enabled.getAsBoolean()) return false;
            seek(false); return true;
        });
        ViewCompat.addAccessibilityAction(surface, "Forward 10 seconds", (v, args) -> {
            if (!enabled.getAsBoolean()) return false;
            seek(true); return true;
        });
    }

    @Override public boolean onTouch(View v, MotionEvent event) {
        if (!enabled.getAsBoolean()) return false;
        // Never call requestDisallowInterceptTouchEvent: a scrolling/paging parent can cancel us.
        if (event.getPointerCount() > 1 || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            cancel();
            return true;
        }
        // onDoubleTapEvent bypasses onScroll, so independently reject movement after the second down.
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getX(); downY = event.getY();
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE &&
                Math.hypot(event.getX() - downX, event.getY() - downY) >
                        android.view.ViewConfiguration.get(v.getContext()).getScaledTouchSlop()) dragged = true;
        detector.onTouchEvent(event);
        return true;
    }

    private float downX, downY;

    void seek(boolean forward) {
        Player current = player.get();
        if (current == null || !current.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) return;
        long target = Math.max(0L, current.getCurrentPosition() + (forward ? 10_000L : -10_000L));
        long duration = current.getDuration();
        current.seekTo(duration > 0L ? Math.min(duration, target) : target);
        controls.show();
        feedback.removeCallbacks(clearFeedback);
        feedback.animate().cancel();
        feedback.setText(forward ? "Forward 10s  ››" : "‹‹  Rewind 10s");
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) feedback.getLayoutParams();
        params.gravity = Gravity.CENTER_VERTICAL | (forward ? Gravity.RIGHT : Gravity.LEFT);
        params.leftMargin = params.rightMargin = Math.max(dp(12), surface.getWidth() / 12);
        feedback.setLayoutParams(params);
        feedback.setAlpha(1f);
        feedback.setVisibility(View.VISIBLE);
        feedback.postDelayed(clearFeedback, 650L);
        surface.announceForAccessibility(forward ? "Forward 10 seconds" : "Rewind 10 seconds");
    }

    void cancel() {
        dragged = true;
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent canceled = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
        detector.onTouchEvent(canceled);
        canceled.recycle();
        feedback.removeCallbacks(clearFeedback);
        feedback.animate().cancel();
        feedback.setVisibility(View.GONE);
    }

    private void feedbackGone() {
        if (ZeroChillMotion.animationsEnabled(surface.getContext())) {
            feedback.animate().alpha(0f).setDuration(ZeroChillMotion.QUICK_MS)
                    .withEndAction(() -> feedback.setVisibility(View.GONE)).start();
        } else feedback.setVisibility(View.GONE);
    }

    private int dp(int value) { return BrowseUi.dp(surface.getContext(), value); }
}
