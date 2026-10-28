package com.webapp.crazyshit;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;

import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Touch-sequence-local ShitTok transform with an optional persistent focus mode.
 * Never recreates the player, changes its surface, or changes layout size.
 */
final class ShitTokDynamicPinchController {
    static final float MIN_SCALE = 0.45f;
    static final float MAX_SCALE = 3.5f;
    static final float FOCUS_ENTER_SCALE = 1.18f;
    private static final float SCALE_RESPONSE = 0.58f;
    private static final float PIVOT_RESPONSE = 0.54f;
    private static final long RETURN_DURATION_MS = 165L;
    private static final long FOCUS_RETURN_DURATION_MS = 180L;
    private static final long FOCUS_CHROME_DURATION_MS = 140L;

    interface Listener { void onPinchChanged(boolean active); }

    private final TouchFrame root;
    private final View media;
    private final BooleanSupplier eligible;
    private final Listener listener;
    private final View[] overlays;
    private final float[] overlayAlphas;
    private final Rect bounds = new Rect();
    private final Rect focusBounds = new Rect();
    private final DecelerateInterpolator settleInterpolator = new DecelerateInterpolator(1.65f);

    private FrameLayout focusChrome;
    private SeekBar focusSeek;
    private TextView focusPlay;
    private TextView focusSpeed;
    private boolean focusProgressDragging;
    private float focusOriginalSpeed = 1f;
    private final Runnable focusProgressTick = new Runnable() {
        @Override public void run() {
            if (!focusMode) return;
            updateFocusControls();
            root.postDelayed(this, 250L);
        }
    };

    private boolean videoTouchStarted;
    private boolean active;
    private boolean swallowRemainder;
    private boolean focusMode;
    private boolean focusMediaDown;
    private boolean listenerEngaged;
    private boolean settling;
    private boolean gestureStartedInFocus;
    private int firstId, secondId;
    private float initialSpan, originX, originY, pivotX, pivotY;
    private float displayedScale = 1f;
    private float targetScale = 1f;

    ShitTokDynamicPinchController(TouchFrame root, View media, BooleanSupplier eligible,
            Listener listener, View... overlays) {
        this.root = root;
        this.media = media;
        this.eligible = eligible;
        this.listener = listener;
        this.overlays = overlays;
        overlayAlphas = new float[overlays.length];
        root.pinch = this;
        buildFocusChrome();
        media.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if ((active || focusMode || settling)
                    && (l != ol || t != ot || r != or || b != ob)) reset();
        });
    }

    boolean isActive() { return active; }
    boolean isFocusMode() { return focusMode; }
    boolean ownsTouchSequence() { return active || swallowRemainder; }
    void videoTouchStarted() { videoTouchStarted = true; }

    static float clampedScale(float span, float initialSpan) {
        if (!Float.isFinite(span) || !Float.isFinite(initialSpan) || initialSpan <= 0f) return 1f;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, span / initialSpan));
    }

    /** Adds resistance around 1x so small finger changes do not produce a harsh visual jump. */
    static float softenedScale(float span, float initialSpan) {
        float raw = clampedScale(span, initialSpan);
        float softened = (float) Math.pow(raw, 0.82d);
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, softened));
    }

    static boolean shouldEnterFocusMode(float scale) {
        return Float.isFinite(scale) && scale >= FOCUS_ENTER_SCALE;
    }

    private boolean handle(MotionEvent event) {
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            if (settling) finishSettlingImmediately();
            videoTouchStarted = false;
            swallowRemainder = false;
            focusMediaDown = false;

            if (focusMode) {
                if (touchesFocusChrome(event)) return false;
                focusMediaDown = true;
                return true;
            }
            return false;
        }

        if (focusMode && !active && touchesFocusChrome(event)) return false;

        if (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL) {
            if (active) {
                boolean cancelled = action == MotionEvent.ACTION_CANCEL;
                finishPinch(cancelled);
                return true;
            }
            if (focusMode && focusMediaDown) {
                focusMediaDown = false;
                return true;
            }
            return swallowRemainder;
        }

        if (active && event.getPointerCount() != 2) {
            finishPinch(true);
            return true;
        }

        boolean canStartFromFeed = videoTouchStarted && eligible.getAsBoolean();
        boolean canStartFromFocus = focusMode && focusMediaDown;
        if (!active && !swallowRemainder
                && (canStartFromFeed || canStartFromFocus)
                && action == MotionEvent.ACTION_POINTER_DOWN
                && event.getPointerCount() == 2) {
            beginPinch(event);
            return active;
        }

        if (active && action == MotionEvent.ACTION_MOVE) {
            int first = event.findPointerIndex(firstId);
            int second = event.findPointerIndex(secondId);
            if (first < 0 || second < 0) {
                finishPinch(true);
                return true;
            }

            targetScale = softenedScale(span(event, first, second), initialSpan);
            displayedScale += (targetScale - displayedScale) * SCALE_RESPONSE;
            float targetPivotX = (event.getX(first) + event.getX(second)) * 0.5f - originX;
            float targetPivotY = (event.getY(first) + event.getY(second)) * 0.5f - originY;
            float nextPivotX = media.getPivotX()
                    + (targetPivotX - media.getPivotX()) * PIVOT_RESPONSE;
            float nextPivotY = media.getPivotY()
                    + (targetPivotY - media.getPivotY()) * PIVOT_RESPONSE;

            media.setPivotX(nextPivotX);
            media.setPivotY(nextPivotY);
            media.setScaleX(displayedScale);
            media.setScaleY(displayedScale);
        }
        return ownsTouchSequence() || (focusMode && focusMediaDown);
    }

    private void beginPinch(MotionEvent event) {
        float span = span(event, 0, 1);
        if (span < 1f) return;

        media.animate().withEndAction(null).cancel();
        settling = false;
        firstId = event.getPointerId(0);
        secondId = event.getPointerId(1);
        initialSpan = span;
        targetScale = displayedScale = Math.max(MIN_SCALE,
                Math.min(MAX_SCALE, media.getScaleX()));

        bounds.set(0, 0, media.getWidth(), media.getHeight());
        root.offsetDescendantRectToMyCoords(media, bounds);
        originX = bounds.left;
        originY = bounds.top;

        if (!listenerEngaged) {
            pivotX = media.getPivotX();
            pivotY = media.getPivotY();
            listenerEngaged = true;
            listener.onPinchChanged(true);
            for (int i = 0; i < overlays.length; i++) {
                View overlay = overlays[i];
                if (overlay == null) continue;
                overlay.animate().withEndAction(null).cancel();
                overlayAlphas[i] = overlay.getAlpha();
                overlay.setAlpha(0f);
            }
        }

        gestureStartedInFocus = focusMode;
        active = true;
        swallowRemainder = true;
        focusMediaDown = true;
        hideFocusChromeForPinch();
        root.requestDisallowInterceptTouchEvent(true);
    }

    private void finishPinch(boolean cancelled) {
        boolean wasFocus = gestureStartedInFocus || focusMode;
        active = false;
        focusMediaDown = false;
        gestureStartedInFocus = false;

        if (!cancelled && !wasFocus
                && shouldEnterFocusMode(Math.max(displayedScale, targetScale))) {
            enterFocusMode();
            return;
        }

        if (wasFocus) {
            focusMode = true;
            animateMediaToRest(FOCUS_RETURN_DURATION_MS, this::showFocusChrome);
            return;
        }

        animateMediaToRest(RETURN_DURATION_MS, this::finishInteraction);
    }

    private void enterFocusMode() {
        focusMode = true;
        Player player = player();
        focusOriginalSpeed = player == null ? 1f : player.getPlaybackParameters().speed;
        animateMediaToRest(FOCUS_RETURN_DURATION_MS, this::showFocusChrome);
    }

    private void exitFocusModeAnimated() {
        if (!focusMode) return;
        focusMode = false;
        focusMediaDown = false;
        root.removeCallbacks(focusProgressTick);
        hideFocusChromeImmediate();
        restoreOriginalPlaybackSpeed();
        animateMediaToRest(RETURN_DURATION_MS, this::finishInteraction);
    }

    private void animateMediaToRest(long duration, Runnable end) {
        settling = true;
        media.animate().withEndAction(null).cancel();
        media.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(duration)
                .setInterpolator(settleInterpolator)
                .withEndAction(() -> {
                    settling = false;
                    displayedScale = targetScale = 1f;
                    media.setScaleX(1f);
                    media.setScaleY(1f);
                    media.setPivotX(pivotX);
                    media.setPivotY(pivotY);
                    if (end != null) end.run();
                })
                .start();
    }

    private void finishSettlingImmediately() {
        media.animate().withEndAction(null).cancel();
        settling = false;
        media.setScaleX(1f);
        media.setScaleY(1f);
        displayedScale = targetScale = 1f;
        if (listenerEngaged) {
            media.setPivotX(pivotX);
            media.setPivotY(pivotY);
        }
        if (!focusMode && listenerEngaged) finishInteraction();
    }

    private void finishInteraction() {
        hideFocusChromeImmediate();
        restoreOriginalPlaybackSpeed();
        for (int i = 0; i < overlays.length; i++) {
            View overlay = overlays[i];
            if (overlay == null) continue;
            overlay.animate().withEndAction(null).cancel();
            overlay.setAlpha(overlayAlphas[i]);
        }
        root.requestDisallowInterceptTouchEvent(false);
        if (listenerEngaged) {
            listenerEngaged = false;
            listener.onPinchChanged(false);
        }
    }

    /** Also called on holder/owner/host transitions, before the next item can draw. */
    void reset() {
        videoTouchStarted = false;
        focusMediaDown = false;
        swallowRemainder = false;
        if (!active && !focusMode && !listenerEngaged && !settling) return;

        active = false;
        focusMode = false;
        gestureStartedInFocus = false;
        settling = false;
        media.animate().withEndAction(null).cancel();
        media.setScaleX(1f);
        media.setScaleY(1f);
        displayedScale = targetScale = 1f;
        if (listenerEngaged) {
            media.setPivotX(pivotX);
            media.setPivotY(pivotY);
        }
        hideFocusChromeImmediate();
        restoreOriginalPlaybackSpeed();
        finishInteraction();
    }

    private void buildFocusChrome() {
        Context context = root.getContext();
        focusChrome = new FrameLayout(context);
        focusChrome.setVisibility(View.GONE);
        focusChrome.setAlpha(0f);
        focusChrome.setBackgroundColor(Color.argb(178, 0, 0, 0));
        focusChrome.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

        FrameLayout.LayoutParams chromeParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(92),
                Gravity.BOTTOM
        );
        root.addView(focusChrome, chromeParams);

        focusSeek = new SeekBar(context);
        focusSeek.setMax(1000);
        focusSeek.setPadding(0, 0, 0, 0);
        focusSeek.setContentDescription("Video progress");
        focusSeek.setProgressTintList(ColorStateList.valueOf(UiPalette.PRIMARY));
        focusSeek.setProgressBackgroundTintList(
                ColorStateList.valueOf(Color.argb(150, 215, 220, 226))
        );
        focusSeek.setThumbTintList(ColorStateList.valueOf(UiPalette.PRIMARY));
        FrameLayout.LayoutParams seekParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(34),
                Gravity.TOP
        );
        seekParams.setMargins(dp(14), 0, dp(14), 0);
        focusChrome.addView(focusSeek, seekParams);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams rowParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(58),
                Gravity.BOTTOM
        );
        rowParams.setMargins(dp(8), 0, dp(8), dp(2));
        focusChrome.addView(row, rowParams);

        TextView close = focusButton("✕", "Exit focus mode", Gravity.START | Gravity.CENTER_VERTICAL);
        focusPlay = focusButton("❚❚", "Pause video", Gravity.CENTER);
        focusSpeed = focusButton("1×", "Playback speed 1x", Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(close, new LinearLayout.LayoutParams(0, -1, 1f));
        row.addView(focusPlay, new LinearLayout.LayoutParams(0, -1, 1f));
        row.addView(focusSpeed, new LinearLayout.LayoutParams(0, -1, 1f));

        close.setOnClickListener(v -> exitFocusModeAnimated());
        focusPlay.setOnClickListener(v -> {
            Player player = player();
            if (player == null) return;
            if (player.isPlaying()) player.pause();
            else player.play();
            updateFocusControls();
        });
        focusSpeed.setOnClickListener(v -> {
            Player player = player();
            if (player == null) return;
            float speed = player.getPlaybackParameters().speed;
            float next = speed < 1.25f ? 1.5f : speed < 1.75f ? 2f : 1f;
            player.setPlaybackParameters(new PlaybackParameters(next));
            updateFocusControls();
        });

        focusSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                Player player = player();
                if (player == null) return;
                long duration = player.getDuration();
                if (duration <= 0L) return;
                player.seekTo(Math.max(0L, Math.min(duration,
                        Math.round(duration * (progress / (float) bar.getMax())))));
            }

            @Override public void onStartTrackingTouch(SeekBar bar) {
                focusProgressDragging = true;
                root.requestDisallowInterceptTouchEvent(true);
            }

            @Override public void onStopTrackingTouch(SeekBar bar) {
                focusProgressDragging = false;
                root.requestDisallowInterceptTouchEvent(true);
                updateFocusControls();
            }
        });
    }

    private TextView focusButton(String text, String description, int gravity) {
        TextView button = new TextView(root.getContext());
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(20f);
        button.setGravity(gravity);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setContentDescription(description);
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private void showFocusChrome() {
        if (!focusMode) return;
        updateFocusControls();
        focusChrome.animate().withEndAction(null).cancel();
        focusChrome.setVisibility(View.VISIBLE);
        focusChrome.setAlpha(0f);
        focusChrome.animate()
                .alpha(1f)
                .setStartDelay(45L)
                .setDuration(FOCUS_CHROME_DURATION_MS)
                .setInterpolator(settleInterpolator)
                .start();
        root.removeCallbacks(focusProgressTick);
        root.post(focusProgressTick);
    }

    private void hideFocusChromeForPinch() {
        if (focusChrome == null || focusChrome.getVisibility() != View.VISIBLE) return;
        focusChrome.animate().withEndAction(null).cancel();
        focusChrome.setAlpha(0f);
        focusChrome.setVisibility(View.GONE);
        root.removeCallbacks(focusProgressTick);
    }

    private void hideFocusChromeImmediate() {
        if (focusChrome == null) return;
        focusChrome.animate().withEndAction(null).cancel();
        focusChrome.setAlpha(0f);
        focusChrome.setVisibility(View.GONE);
        root.removeCallbacks(focusProgressTick);
        focusProgressDragging = false;
    }

    private void updateFocusControls() {
        Player player = player();
        if (player == null) {
            focusPlay.setText("▶");
            focusSpeed.setText("1×");
            if (!focusProgressDragging) focusSeek.setProgress(0);
            return;
        }

        boolean playing = player.isPlaying();
        focusPlay.setText(playing ? "❚❚" : "▶");
        focusPlay.setContentDescription(playing ? "Pause video" : "Play video");
        float speed = player.getPlaybackParameters().speed;
        focusSpeed.setText(speedLabel(speed));
        focusSpeed.setContentDescription("Playback speed " + speedLabel(speed));

        if (!focusProgressDragging) {
            long duration = player.getDuration();
            long position = Math.max(0L, player.getCurrentPosition());
            int progress = duration > 0L
                    ? Math.max(0, Math.min(focusSeek.getMax(),
                    Math.round((position / (float) duration) * focusSeek.getMax())))
                    : 0;
            focusSeek.setProgress(progress);
        }
    }

    private void restoreOriginalPlaybackSpeed() {
        Player player = player();
        if (player == null) return;
        float current = player.getPlaybackParameters().speed;
        if (Math.abs(current - focusOriginalSpeed) > 0.01f) {
            player.setPlaybackParameters(new PlaybackParameters(focusOriginalSpeed));
        }
    }

    private Player player() {
        View current = media;
        while (current != null) {
            if (current instanceof PlayerView) return ((PlayerView) current).getPlayer();
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private boolean touchesFocusChrome(MotionEvent event) {
        if (!focusMode || focusChrome == null || focusChrome.getVisibility() != View.VISIBLE) {
            return false;
        }
        focusChrome.getHitRect(focusBounds);
        return focusBounds.contains(Math.round(event.getX()), Math.round(event.getY()));
    }

    private int dp(int value) {
        return Math.max(1, Math.round(
                value * root.getResources().getDisplayMetrics().density
        ));
    }

    private static String speedLabel(float speed) {
        if (Math.abs(speed - Math.round(speed)) < 0.01f) {
            return String.format(Locale.US, "%d×", Math.round(speed));
        }
        return String.format(Locale.US, "%.1f×", speed);
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
