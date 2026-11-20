package com.webapp.crazyshit;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.core.view.ViewCompat;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Surface taps only. Parents keep drag interception and controls keep their own touches. */
final class FullscreenVideoGestures implements View.OnTouchListener {
    private static final int SEEK_SECONDS = 10;
    private static final long REPEAT_TIMEOUT_MS = 650L;

    private final PlayerView surface;
    private final Supplier<Player> player;
    private final BooleanSupplier enabled;
    private final Runnable onTap;
    private final Runnable onSeek;
    private final VideoSeekFeedback feedback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int touchSlop;
    private final int doubleTapSlop;
    private final Runnable confirmSingleTap = this::confirmTap;
    private final Runnable longPress = this::performLongPress;

    private boolean tracking;
    private boolean pendingTap;
    private boolean seekOnUp;
    private float downX;
    private float downY;
    private long downTime;
    private float tapX;
    private float tapY;
    private long tapUpTime;
    private int seekSeconds;
    private long seekTime;

    FullscreenVideoGestures(PlayerView surface, Supplier<Player> player,
            Runnable onTap, BooleanSupplier enabled) {
        this(surface, player, onTap, enabled, () -> {});
    }

    FullscreenVideoGestures(PlayerView surface, Supplier<Player> player,
            Runnable onTap, BooleanSupplier enabled, Runnable onSeek) {
        this.surface = surface;
        this.player = player;
        this.onTap = onTap;
        this.enabled = enabled;
        this.onSeek = onSeek;
        ViewConfiguration configuration = ViewConfiguration.get(surface.getContext());
        touchSlop = configuration.getScaledTouchSlop();
        doubleTapSlop = configuration.getScaledDoubleTapSlop();
        feedback = new VideoSeekFeedback(surface.getContext());
        surface.addView(feedback, new FrameLayout.LayoutParams(-1, -1));
        surface.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {}
            @Override public void onViewDetachedFromWindow(View view) { cancel(); }
        });
        ViewCompat.addAccessibilityAction(surface, surface.getContext().getString(R.string.video_toggle_controls),
                (view, args) -> {
                    if (!enabled.getAsBoolean()) return false;
                    cancel();
                    onTap.run();
                    return true;
                });
        ViewCompat.addAccessibilityAction(surface, surface.getContext().getString(R.string.video_rewind_action),
                (view, args) -> seekFromAccessibility(false));
        ViewCompat.addAccessibilityAction(surface, surface.getContext().getString(R.string.video_forward_action),
                (view, args) -> seekFromAccessibility(true));
    }

    @Override public boolean onTouch(View view, MotionEvent event) {
        if (!enabled.getAsBoolean()) {
            cancel();
            return false;
        }
        if (event.getPointerCount() > 1 || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            cancel();
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                beginTap(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (tracking && movedBeyondSlop(event)) cancel();
                break;
            case MotionEvent.ACTION_UP:
                if (tracking) {
                    boolean held = event.getEventTime() - downTime >= ViewConfiguration.getLongPressTimeout();
                    if (held || movedBeyondSlop(event) || isForward(event.getX()) != isForward(downX)) {
                        cancel();
                    } else finishTap(event);
                }
                break;
            default:
                break;
        }
        return true;
    }

    private void beginTap(MotionEvent event) {
        downX = event.getX();
        downY = event.getY();
        downTime = event.getEventTime();
        tracking = true;
        boolean forward = isForward(downX);
        // Once a pair has sought, every timely tap on that half adds one step.
        boolean repeating = seekSeconds != 0 && (seekSeconds > 0) == forward
                && event.getEventTime() - seekTime <= REPEAT_TIMEOUT_MS;
        if (!repeating) seekSeconds = 0;
        boolean doubleTap = pendingTap && isForward(tapX) == forward
                && event.getEventTime() - tapUpTime <= ViewConfiguration.getDoubleTapTimeout()
                && Math.hypot(downX - tapX, downY - tapY) <= doubleTapSlop;
        seekOnUp = repeating || doubleTap;
        handler.removeCallbacks(confirmSingleTap);
        if (doubleTap) pendingTap = false;
        else if (pendingTap) confirmTap();
        if (surface.isLongClickable()) {
            handler.postAtTime(longPress, downTime + ViewConfiguration.getLongPressTimeout());
        }
    }

    private void finishTap(MotionEvent event) {
        tracking = false;
        handler.removeCallbacks(longPress);
        if (seekOnUp) {
            seek(isForward(downX), event.getX(), event.getY(), event.getEventTime());
            return;
        }
        pendingTap = true;
        tapX = downX;
        tapY = downY;
        tapUpTime = event.getEventTime();
        handler.postAtTime(confirmSingleTap, tapUpTime + ViewConfiguration.getDoubleTapTimeout());
    }

    private boolean movedBeyondSlop(MotionEvent event) {
        return Math.hypot(event.getX() - downX, event.getY() - downY) > touchSlop;
    }

    private boolean isForward(float x) {
        return x >= surface.getWidth() / 2f;
    }

    private void confirmTap() {
        pendingTap = false;
        if (enabled.getAsBoolean()) onTap.run();
    }

    private void performLongPress() {
        cancel();
        if (enabled.getAsBoolean()) surface.performLongClick();
    }

    private boolean seekFromAccessibility(boolean forward) {
        if (!enabled.getAsBoolean()) return false;
        cancel();
        boolean handled = seek(forward, surface.getWidth() * (forward ? .75f : .25f),
                surface.getHeight() / 2f, SystemClock.uptimeMillis());
        if (handled) {
            surface.announceForAccessibility(surface.getContext().getString(
                    forward ? R.string.video_forward_action : R.string.video_rewind_action));
            seekSeconds = 0;
        }
        return handled;
    }

    private boolean seek(boolean forward, float x, float y, long eventTime) {
        Player current = player.get();
        if (current == null || !current.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            return false;
        }
        long duration = current.getDuration();
        if (duration <= 0L) return false;

        long position = current.getCurrentPosition();
        int step = forward ? SEEK_SECONDS : -SEEK_SECONDS;
        long target = Math.max(0L, Math.min(duration, position + step * 1000L));
        if (target != position) current.seekTo(target);
        seekSeconds += step;
        seekTime = eventTime;
        feedback.show(forward, Math.abs(seekSeconds), x, y);
        onSeek.run();
        return true;
    }

    void cancel() {
        tracking = false;
        pendingTap = false;
        seekSeconds = 0;
        handler.removeCallbacks(confirmSingleTap);
        handler.removeCallbacks(longPress);
        feedback.hide();
    }
}
