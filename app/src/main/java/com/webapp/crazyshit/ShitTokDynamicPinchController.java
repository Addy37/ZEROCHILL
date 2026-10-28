package com.webapp.crazyshit;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
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
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Touch-sequence-local ShitTok transform with an optional persistent focus mode.
 * Never recreates the player or changes its playback surface.
 */
final class ShitTokDynamicPinchController {
    static final float MIN_SCALE = 0.45f;
    static final float MAX_SCALE = 3.5f;
    static final float FOCUS_ENTER_SCALE = 1.18f;
    static final float FOCUS_EXIT_SCALE = 0.82f;

    private static final float SCALE_RESPONSE = 0.58f;
    private static final float PIVOT_RESPONSE = 0.54f;
    private static final long RETURN_DURATION_MS = 165L;
    private static final long FOCUS_RETURN_DURATION_MS = 180L;
    private static final long FOCUS_CHROME_DURATION_MS = 140L;

    // TikTok reference: full-width media above a compact black control zone.
    private static final int FOCUS_CHROME_HEIGHT_DP = 112;
    private static final int FOCUS_EDGE_DP = 12;
    private static final int FOCUS_BUTTON_DP = 48;
    private static final int FOCUS_PILL_WIDTH_DP = 112;
    private static final int FOCUS_PILL_HEIGHT_DP = 48;
    private static final int FOCUS_SEEK_TOP_DP = 8;
    private static final int FOCUS_CONTROL_BOTTOM_DP = 10;

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
    private boolean focusViewportApplied;
    private boolean focusViewportChanging;
    private boolean focusPeerPrepared;
    private boolean focusMigrationPending;

    private int firstId, secondId;
    private float initialSpan, originX, originY, pivotX, pivotY;
    private float displayedScale = 1f;
    private float targetScale = 1f;

    private int originalPaddingLeft;
    private int originalPaddingTop;
    private int originalPaddingRight;
    private int originalPaddingBottom;
    private int originalPlayerWidth;
    private int originalPlayerHeight;
    private int originalPlayerGravity;
    private int originalPlayerLeftMargin;
    private int originalPlayerTopMargin;
    private int originalPlayerRightMargin;
    private int originalPlayerBottomMargin;
    private boolean originalPlayerLayoutCaptured;

    private ViewPager2 focusPager;
    private RecyclerView focusRecycler;
    private ViewPager2.OnPageChangeCallback focusPageCallback;
    private RecyclerView.OnChildAttachStateChangeListener focusAttachListener;
    private float migrationSpeed = 1f;

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
            if (!focusViewportChanging && (active || focusMode || settling)
                    && (l != ol || t != ot || r != or || b != ob)) {
                reset();
            }
        });
    }

    boolean isActive() { return active; }
    boolean isFocusMode() { return focusMode; }
    boolean suppressesFeedChrome() { return listenerEngaged || focusMode || focusPeerPrepared; }
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

    static boolean shouldExitFocusMode(float scale) {
        return Float.isFinite(scale) && scale <= FOCUS_EXIT_SCALE;
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
                // Focus Mode owns media taps so the normal hold-for-2x detector never
                // starts. ViewPager2 can still intercept a vertical move for paging.
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
                if (action == MotionEvent.ACTION_UP) toggleFocusPlayback();
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
        if (focusMode && focusMediaDown && !active) return true;
        return ownsTouchSequence();
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
            engageListenerAndHideOverlays();
        }

        gestureStartedInFocus = focusMode;
        active = true;
        swallowRemainder = true;
        focusMediaDown = true;
        hideFocusChromeForPinch();
        setPagerInputEnabled(false);
        root.requestDisallowInterceptTouchEvent(true);
    }

    private void engageListenerAndHideOverlays() {
        pivotX = media.getPivotX();
        pivotY = media.getPivotY();
        listenerEngaged = true;
        listener.onPinchChanged(true);
        captureAndHideOverlays();
    }

    private void captureAndHideOverlays() {
        for (int i = 0; i < overlays.length; i++) {
            View overlay = overlays[i];
            if (overlay == null) continue;
            overlay.animate().withEndAction(null).cancel();
            overlayAlphas[i] = overlay.getAlpha();
        }
        hideOverlays();
    }

    /** Reassert suppression without replacing the pre-Focus alpha snapshot. */
    private void hideOverlays() {
        for (View overlay : overlays) {
            if (overlay == null) continue;
            overlay.animate().withEndAction(null).cancel();
            overlay.setAlpha(0f);
        }
    }

    private void restoreOverlays() {
        for (int i = 0; i < overlays.length; i++) {
            View overlay = overlays[i];
            if (overlay == null) continue;
            overlay.animate().withEndAction(null).cancel();
            overlay.setAlpha(overlayAlphas[i]);
        }
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
            if (!cancelled && shouldExitFocusMode(Math.min(displayedScale, targetScale))) {
                exitFocusModeAnimated();
                return;
            }
            focusMode = true;
            animateMediaToRest(FOCUS_RETURN_DURATION_MS, () -> {
                showFocusChrome();
                setPagerInputEnabled(true);
                prepareVisibleFocusPeers();
            });
            return;
        }

        animateMediaToRest(RETURN_DURATION_MS, this::finishInteraction);
    }

    private void enterFocusMode() {
        focusMode = true;
        Player player = player();
        focusOriginalSpeed = player == null ? 1f : player.getPlaybackParameters().speed;
        animateMediaToRest(FOCUS_RETURN_DURATION_MS, () -> {
            applyFocusViewport();
            startFocusPaging();
            prepareVisibleFocusPeers();
            setPagerInputEnabled(true);
            root.post(this::showFocusChrome);
        });
    }

    private void adoptFocusFromPaging(float speed) {
        if (focusMode) return;
        focusMigrationPending = false;
        focusMode = true;
        focusOriginalSpeed = speed;

        if (focusPeerPrepared) {
            focusPeerPrepared = false;
            pivotX = media.getPivotX();
            pivotY = media.getPivotY();
            listenerEngaged = true;
            listener.onPinchChanged(true);
        } else if (!listenerEngaged) {
            engageListenerAndHideOverlays();
            applyFocusViewport();
        }

        // Holder/listener synchronization may have touched chrome since pre-hide.
        // Keep the original snapshot so pinch-in/Back restores the pre-Focus UI.
        hideOverlays();
        startFocusPaging();
        prepareVisibleFocusPeers();
        setPagerInputEnabled(true);
        root.post(this::showFocusChrome);
    }

    private void exitFocusModeAnimated() {
        if (!focusMode) return;
        focusMode = false;
        focusMediaDown = false;
        focusMigrationPending = false;
        root.removeCallbacks(focusProgressTick);
        hideFocusChromeImmediate();
        stopFocusPaging(true);
        restoreOriginalPlaybackSpeed();
        restoreFocusViewport();
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
        stopFocusPaging(true);
        restoreOriginalPlaybackSpeed();
        restoreFocusViewport();
        restoreOverlays();
        root.requestDisallowInterceptTouchEvent(false);
        setPagerInputEnabled(true);
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

        if (shouldPreserveFocusForPageChange()) {
            detachForFocusPaging();
            return;
        }

        if (!active && !focusMode && !listenerEngaged && !settling
                && !focusViewportApplied && !focusPeerPrepared) return;

        active = false;
        focusMode = false;
        gestureStartedInFocus = false;
        settling = false;
        focusMigrationPending = false;
        media.animate().withEndAction(null).cancel();
        media.setScaleX(1f);
        media.setScaleY(1f);
        displayedScale = targetScale = 1f;
        if (listenerEngaged) {
            media.setPivotX(pivotX);
            media.setPivotY(pivotY);
        }
        hideFocusChromeImmediate();
        stopFocusPaging(true);
        restoreOriginalPlaybackSpeed();
        restoreFocusViewport();
        if (focusPeerPrepared) restorePeerPreparation();
        finishInteraction();
    }

    private boolean shouldPreserveFocusForPageChange() {
        if (!focusMode || active) return false;
        RecyclerView recycler = recyclerView();
        ViewPager2 pager = pagerView();
        if (recycler == null || pager == null) return false;
        int ownPosition = recycler.getChildAdapterPosition(root);
        return ownPosition != RecyclerView.NO_POSITION && pager.getCurrentItem() != ownPosition;
    }

    private void detachForFocusPaging() {
        Player player = player();
        migrationSpeed = player == null ? focusOriginalSpeed : player.getPlaybackParameters().speed;
        focusMigrationPending = true;
        focusMode = false;
        active = false;
        gestureStartedInFocus = false;
        settling = false;
        media.animate().withEndAction(null).cancel();
        media.setScaleX(1f);
        media.setScaleY(1f);
        displayedScale = targetScale = 1f;
        hideFocusChromeImmediate();
        restoreOriginalPlaybackSpeed();
        restoreFocusViewport();
        restoreOverlays();
        listenerEngaged = false;
        root.requestDisallowInterceptTouchEvent(false);
        setPagerInputEnabled(true);
        // Do not notify false here. The feed-level chrome stays hidden while the
        // newly selected holder adopts Focus Mode in the same page transition.
    }

    private void startFocusPaging() {
        ViewPager2 pager = pagerView();
        RecyclerView recycler = recyclerView();
        if (pager == null || recycler == null) return;

        if (focusPager != pager) {
            stopFocusObserversOnly();
            focusPager = pager;
            focusRecycler = recycler;
        }

        if (focusPageCallback == null) {
            focusPageCallback = new ViewPager2.OnPageChangeCallback() {
                @Override public void onPageSelected(int position) {
                    if (focusPageCallback != this) return;
                    ViewPager2 observedPager = focusPager;
                    if (observedPager == null) return;
                    observedPager.post(() -> migrateFocusTo(position));
                }
            };
            focusPager.registerOnPageChangeCallback(focusPageCallback);
        }

        if (focusAttachListener == null) {
            focusAttachListener = new RecyclerView.OnChildAttachStateChangeListener() {
                @Override public void onChildViewAttachedToWindow(View view) {
                    if (!focusMode && !focusMigrationPending) return;
                    preparePeerView(view);
                }

                @Override public void onChildViewDetachedFromWindow(View view) {
                }
            };
            focusRecycler.addOnChildAttachStateChangeListener(focusAttachListener);
        }
    }

    private void migrateFocusTo(int position) {
        if (!focusMigrationPending || focusRecycler == null) return;
        RecyclerView.ViewHolder holder = focusRecycler.findViewHolderForAdapterPosition(position);
        if (holder == null || !(holder.itemView instanceof TouchFrame)) {
            ViewPager2 pager = focusPager;
            if (pager != null) pager.postDelayed(() -> migrateFocusTo(position), 32L);
            return;
        }

        ShitTokDynamicPinchController next = ((TouchFrame) holder.itemView).pinch;
        if (next == null || next == this) return;

        float speed = migrationSpeed;
        focusMigrationPending = false;
        stopFocusObserversOnly();
        next.adoptFocusFromPaging(speed);
    }

    private void prepareVisibleFocusPeers() {
        RecyclerView recycler = focusRecycler != null ? focusRecycler : recyclerView();
        if (recycler == null) return;
        for (int i = 0; i < recycler.getChildCount(); i++) {
            preparePeerView(recycler.getChildAt(i));
        }
    }

    private void preparePeerView(View view) {
        if (!(view instanceof TouchFrame)) return;
        ShitTokDynamicPinchController peer = ((TouchFrame) view).pinch;
        if (peer == null || peer == this || peer.focusMode) return;
        peer.preparePeerForFocus();
    }

    private void preparePeerForFocus() {
        if (focusPeerPrepared || focusMode || listenerEngaged) return;
        focusPeerPrepared = true;
        captureAndHideOverlays();
        applyFocusViewport();
        hideFocusChromeImmediate();
    }

    private void restorePeerPreparation() {
        if (!focusPeerPrepared) return;
        focusPeerPrepared = false;
        hideFocusChromeImmediate();
        restoreFocusViewport();
        restoreOverlays();
    }

    private void stopFocusPaging(boolean restorePeers) {
        if (restorePeers && focusRecycler != null) {
            for (int i = 0; i < focusRecycler.getChildCount(); i++) {
                View child = focusRecycler.getChildAt(i);
                if (!(child instanceof TouchFrame)) continue;
                ShitTokDynamicPinchController peer = ((TouchFrame) child).pinch;
                if (peer != null && peer != this) peer.restorePeerPreparation();
            }
        }
        stopFocusObserversOnly();
    }

    private void stopFocusObserversOnly() {
        if (focusPager != null && focusPageCallback != null) {
            ViewPager2 observedPager = focusPager;
            ViewPager2.OnPageChangeCallback callback = focusPageCallback;
            // Reset can run inside a feed page/scroll callback. ViewPager2 forbids
            // mutating its callback list during dispatch. Invalidate synchronously
            // below, then remove only this captured subscription after dispatch.
            observedPager.post(() -> observedPager.unregisterOnPageChangeCallback(callback));
        }
        if (focusRecycler != null && focusAttachListener != null) {
            focusRecycler.removeOnChildAttachStateChangeListener(focusAttachListener);
        }
        focusPageCallback = null;
        focusAttachListener = null;
        focusPager = null;
        focusRecycler = null;
    }

    private void setPagerInputEnabled(boolean enabled) {
        ViewPager2 pager = focusPager != null ? focusPager : pagerView();
        if (pager != null) pager.setUserInputEnabled(enabled);
    }

    private ViewPager2 pagerView() {
        View current = root;
        while (current != null) {
            if (current instanceof ViewPager2) return (ViewPager2) current;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private RecyclerView recyclerView() {
        View current = root;
        while (current != null) {
            if (current instanceof RecyclerView) return (RecyclerView) current;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private void applyFocusViewport() {
        if (focusViewportApplied) return;
        PlayerView playerView = playerView();
        if (playerView == null || root.getWidth() <= 0 || root.getHeight() <= 0) return;

        originalPaddingLeft = root.getPaddingLeft();
        originalPaddingTop = root.getPaddingTop();
        originalPaddingRight = root.getPaddingRight();
        originalPaddingBottom = root.getPaddingBottom();

        if (playerView.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams current =
                    (FrameLayout.LayoutParams) playerView.getLayoutParams();
            originalPlayerWidth = current.width;
            originalPlayerHeight = current.height;
            originalPlayerGravity = current.gravity;
            originalPlayerLeftMargin = current.leftMargin;
            originalPlayerTopMargin = current.topMargin;
            originalPlayerRightMargin = current.rightMargin;
            originalPlayerBottomMargin = current.bottomMargin;
            originalPlayerLayoutCaptured = true;
        } else {
            originalPlayerLayoutCaptured = false;
        }

        focusViewportApplied = true;
        focusViewportChanging = true;
        root.setPadding(0, 0, 0, 0);

        int height = Math.max(dp(120), root.getHeight() - dp(FOCUS_CHROME_HEIGHT_DP));
        FrameLayout.LayoutParams focused =
                new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, height);
        focused.gravity = Gravity.TOP;
        playerView.setLayoutParams(focused);
        playerView.requestLayout();
        root.requestLayout();
        root.postDelayed(() -> focusViewportChanging = false, 80L);
    }

    private void restoreFocusViewport() {
        if (!focusViewportApplied) return;
        focusViewportChanging = true;
        PlayerView playerView = playerView();

        root.setPadding(
                originalPaddingLeft,
                originalPaddingTop,
                originalPaddingRight,
                originalPaddingBottom
        );

        if (playerView != null && originalPlayerLayoutCaptured) {
            FrameLayout.LayoutParams restored =
                    new FrameLayout.LayoutParams(originalPlayerWidth, originalPlayerHeight);
            restored.gravity = originalPlayerGravity;
            restored.setMargins(
                    originalPlayerLeftMargin,
                    originalPlayerTopMargin,
                    originalPlayerRightMargin,
                    originalPlayerBottomMargin
            );
            playerView.setLayoutParams(restored);
            playerView.requestLayout();
        }

        focusViewportApplied = false;
        root.requestLayout();
        root.postDelayed(() -> focusViewportChanging = false, 80L);
    }

    private void buildFocusChrome() {
        Context context = root.getContext();
        focusChrome = new FrameLayout(context);
        focusChrome.setVisibility(View.GONE);
        focusChrome.setAlpha(0f);
        focusChrome.setBackgroundColor(Color.TRANSPARENT);
        focusChrome.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

        FrameLayout.LayoutParams chromeParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(FOCUS_CHROME_HEIGHT_DP),
                Gravity.BOTTOM
        );
        root.addView(focusChrome, chromeParams);

        focusSeek = new SeekBar(context);
        focusSeek.setMax(1000);
        focusSeek.setPadding(0, 0, 0, 0);
        focusSeek.setContentDescription("Video progress");
        focusSeek.setSplitTrack(false);
        focusSeek.setThumb(new ColorDrawable(Color.TRANSPARENT));
        focusSeek.setProgressTintList(
                ColorStateList.valueOf(Color.argb(185, 235, 235, 238))
        );
        focusSeek.setProgressBackgroundTintList(
                ColorStateList.valueOf(Color.argb(70, 205, 205, 210))
        );

        FrameLayout.LayoutParams seekParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(24),
                Gravity.TOP
        );
        seekParams.setMargins(
                dp(FOCUS_EDGE_DP),
                dp(FOCUS_SEEK_TOP_DP),
                dp(FOCUS_EDGE_DP),
                0
        );
        focusChrome.addView(focusSeek, seekParams);

        TextView close = focusButton("×", "Exit focus mode", 22f);
        close.setBackground(roundRect(Color.rgb(35, 35, 38), FOCUS_BUTTON_DP / 2f));
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                dp(FOCUS_BUTTON_DP),
                dp(FOCUS_BUTTON_DP),
                Gravity.BOTTOM | Gravity.START
        );
        closeParams.setMargins(
                dp(FOCUS_EDGE_DP),
                0,
                0,
                dp(FOCUS_CONTROL_BOTTOM_DP)
        );
        focusChrome.addView(close, closeParams);

        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER);
        pill.setBackground(roundRect(
                Color.rgb(35, 35, 38),
                FOCUS_PILL_HEIGHT_DP / 2f
        ));
        FrameLayout.LayoutParams pillParams = new FrameLayout.LayoutParams(
                dp(FOCUS_PILL_WIDTH_DP),
                dp(FOCUS_PILL_HEIGHT_DP),
                Gravity.BOTTOM | Gravity.END
        );
        pillParams.setMargins(
                0,
                0,
                dp(FOCUS_EDGE_DP),
                dp(FOCUS_CONTROL_BOTTOM_DP)
        );
        focusChrome.addView(pill, pillParams);

        focusPlay = focusButton("❚❚", "Pause video", 16f);
        focusSpeed = focusButton("1×", "Playback speed 1x", 15f);
        View divider = new View(context);
        divider.setBackgroundColor(Color.argb(70, 255, 255, 255));

        pill.addView(focusPlay, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(dp(1), dp(20));
        pill.addView(divider, dividerParams);
        pill.addView(focusSpeed, new LinearLayout.LayoutParams(0, -1, 1f));

        close.setOnClickListener(v -> exitFocusModeAnimated());
        focusPlay.setOnClickListener(v -> {
            toggleFocusPlayback();
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
                root.requestDisallowInterceptTouchEvent(false);
                setPagerInputEnabled(true);
                updateFocusControls();
            }
        });
    }

    private TextView focusButton(String text, String description, float sizeSp) {
        TextView button = new TextView(root.getContext());
        button.setText(text);
        button.setTextColor(Color.rgb(235, 235, 238));
        button.setTextSize(sizeSp);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setContentDescription(description);
        button.setMinWidth(dp(48));
        button.setMinHeight(dp(48));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private GradientDrawable roundRect(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dpFloat(radiusDp));
        return drawable;
    }

    private void showFocusChrome() {
        if (!focusMode) return;
        updateFocusControls();
        focusChrome.animate().withEndAction(null).cancel();
        focusChrome.setVisibility(View.VISIBLE);
        focusChrome.setAlpha(0f);
        focusChrome.animate()
                .alpha(1f)
                .setStartDelay(35L)
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

    private void toggleFocusPlayback() {
        Player player = player();
        if (player == null) return;
        if (player.isPlaying()) player.pause();
        else player.play();
        updateFocusControls();
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
        PlayerView playerView = playerView();
        return playerView == null ? null : playerView.getPlayer();
    }

    private PlayerView playerView() {
        View current = media;
        while (current != null) {
            if (current instanceof PlayerView) return (PlayerView) current;
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

    private float dpFloat(float value) {
        return value * root.getResources().getDisplayMetrics().density;
    }

    private static String speedLabel(float speed) {
        if (Math.abs(speed - Math.round(speed)) < 0.01f) {
            return String.format(Locale.US, "%d×", Math.round(speed));
        }
        return String.format(Locale.US, "%.1f×", speed);
    }

    private static float span(MotionEvent event, int a, int b) {
        return (float) Math.hypot(event.getX(a) - event.getX(b),
                event.getY(a) - event.getY(b));
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
