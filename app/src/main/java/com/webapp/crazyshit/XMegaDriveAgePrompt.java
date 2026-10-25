package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.atomic.AtomicBoolean;

/** Native ZEROCHILL cover for XMegaDrive's age gate while its WebView remains hidden. */
final class XMegaDriveAgePrompt {
    static final String TAG_TITLE = "xmegadrive_age_prompt_title";
    static final String TAG_CONTINUE = "xmegadrive_age_prompt_continue";
    static final String TAG_CANCEL = "xmegadrive_age_prompt_cancel";

    interface ContinueBoundsCallback {
        void onBounds(int left, int top, int width, int height);
    }

    private final Activity activity;
    private final Runnable onContinueGesture;
    private final Runnable onCancel;
    private final PassThroughRoot root;
    private final TextView continueButton;
    private final AtomicBoolean continuePressed = new AtomicBoolean();
    private final AtomicBoolean rawFallbackShown = new AtomicBoolean();
    private volatile long continuePressedAtMs;

    XMegaDriveAgePrompt(Activity activity, Runnable onContinueGesture, Runnable onCancel) {
        this.activity = activity;
        this.onContinueGesture = onContinueGesture;
        this.onCancel = onCancel;

        root = new PassThroughRoot(activity);
        root.setBackgroundColor(activity.getColor(R.color.zc_scrim));
        root.setClickable(true);
        root.setFocusable(true);
        root.setElevation(dp(activity, 96));

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        int cardPad = dp(activity, 24);
        card.setPadding(cardPad, cardPad, cardPad, cardPad);
        card.setBackground(rounded(
                activity,
                activity.getColor(R.color.zc_surface_raised),
                activity.getColor(R.color.zc_edge),
                1,
                28
        ));

        TextView eyebrow = text(activity, "ZEROCHILL • XMEGADRIVE", 12,
                activity.getColor(R.color.zc_cyan), Typeface.BOLD);
        eyebrow.setLetterSpacing(0.08f);
        card.addView(eyebrow, wrap());

        TextView title = text(activity, "One quick check", 24,
                activity.getColor(R.color.zc_text_primary), Typeface.BOLD);
        title.setTag(TAG_TITLE);
        LinearLayout.LayoutParams titleParams = wrap();
        titleParams.topMargin = dp(activity, 14);
        card.addView(title, titleParams);

        TextView body = text(
                activity,
                "XMegaDrive requires age confirmation before ZEROCHILL can load this source. Continue only if you're 18 or older.",
                15,
                activity.getColor(R.color.zc_text_secondary),
                Typeface.NORMAL
        );
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(0f, 1.08f);
        LinearLayout.LayoutParams bodyParams = matchWidthWrap();
        bodyParams.topMargin = dp(activity, 12);
        card.addView(body, bodyParams);

        continueButton = text(activity, "PREPARING…", 16,
                activity.getColor(R.color.app_on_primary), Typeface.BOLD);
        continueButton.setTag(TAG_CONTINUE);
        continueButton.setGravity(Gravity.CENTER);
        continueButton.setMinHeight(dp(activity, 52));
        continueButton.setEnabled(false);
        continueButton.setClickable(false);
        continueButton.setFocusable(false);
        continueButton.setAlpha(0.78f);
        continueButton.setBackground(rounded(
                activity,
                activity.getColor(R.color.zc_cyan),
                activity.getColor(R.color.zc_cyan),
                0,
                18
        ));
        LinearLayout.LayoutParams continueParams = matchWidthWrap();
        continueParams.topMargin = dp(activity, 22);
        card.addView(continueButton, continueParams);
        root.setPassThroughView(continueButton);
        root.setOnPassThroughDown(this::markContinuePressed);

        TextView note = text(
                activity,
                "The site stays hidden unless verification cannot finish.",
                12,
                activity.getColor(R.color.zc_text_muted),
                Typeface.NORMAL
        );
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = matchWidthWrap();
        noteParams.topMargin = dp(activity, 12);
        card.addView(note, noteParams);

        TextView cancel = text(activity, "NOT NOW", 14,
                activity.getColor(R.color.zc_text_secondary), Typeface.BOLD);
        cancel.setTag(TAG_CANCEL);
        cancel.setGravity(Gravity.CENTER);
        int cancelPad = dp(activity, 12);
        cancel.setPadding(cancelPad, cancelPad, cancelPad, cancelPad);
        cancel.setOnClickListener(view -> {
            if (onCancel != null) onCancel.run();
        });
        LinearLayout.LayoutParams cancelParams = wrap();
        cancelParams.topMargin = dp(activity, 4);
        card.addView(cancel, cancelParams);

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int margin = dp(activity, 24);
        cardParams.leftMargin = margin;
        cardParams.rightMargin = margin;
        cardParams.gravity = Gravity.CENTER;
        root.addView(card, cardParams);
    }

    void show() {
        if (root.getParent() != null || activity.isFinishing() || activity.isDestroyed()) return;
        activity.addContentView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.bringToFront();
        root.requestFocus();
    }

    void dismiss() {
        try {
            ViewParent parent = root.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(root);
        } catch (Exception ignored) {
        }
    }

    void armPhysicalContinue() {
        if (continuePressed.get() || rawFallbackShown.get()) return;
        continueButton.setText("CONTINUE");
        continueButton.setEnabled(true);
        continueButton.setAlpha(1f);
        root.setPassThroughEnabled(true);
    }

    void postContinueBounds(ContinueBoundsCallback callback) {
        if (callback == null) return;
        continueButton.post(() -> {
            if (continueButton.getWidth() <= 0 || continueButton.getHeight() <= 0) return;
            int[] location = new int[2];
            continueButton.getLocationOnScreen(location);
            callback.onBounds(location[0], location[1], continueButton.getWidth(), continueButton.getHeight());
        });
    }

    void showRawFallback() {
        rawFallbackShown.set(true);
        root.setPassThroughEnabled(false);
        dismiss();
    }

    boolean isRawFallbackShown() {
        return rawFallbackShown.get();
    }

    boolean hasContinueBeenPressed() {
        return continuePressed.get();
    }

    long millisSinceContinue() {
        long pressedAt = continuePressedAtMs;
        if (pressedAt <= 0L) return 0L;
        return Math.max(0L, SystemClock.elapsedRealtime() - pressedAt);
    }

    View rootForTest() {
        return root;
    }

    private void markContinuePressed() {
        if (!continuePressed.compareAndSet(false, true)) return;
        continuePressedAtMs = SystemClock.elapsedRealtime();
        root.setPassThroughEnabled(false);
        continueButton.setEnabled(false);
        continueButton.setAlpha(0.78f);
        continueButton.setText("VERIFYING…");
        if (onContinueGesture != null) onContinueGesture.run();
    }

    private static TextView text(Context context, String value, float sp, int color, int style) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private static LinearLayout.LayoutParams matchWidthWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private static GradientDrawable rounded(
            Context context,
            int fill,
            int stroke,
            int strokeDp,
            int radiusDp
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(context, strokeDp), stroke);
        return drawable;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class PassThroughRoot extends FrameLayout {
        private View passThroughView;
        private Runnable onPassThroughDown;
        private boolean passThroughEnabled;

        PassThroughRoot(Context context) {
            super(context);
        }

        void setPassThroughView(View view) {
            passThroughView = view;
        }

        void setOnPassThroughDown(Runnable callback) {
            onPassThroughDown = callback;
        }

        void setPassThroughEnabled(boolean enabled) {
            passThroughEnabled = enabled;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (passThroughEnabled && event != null && isInsidePassThroughView(event)) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && onPassThroughDown != null) {
                    onPassThroughDown.run();
                }
                // Returning false on ACTION_DOWN lets the DecorView continue hit-testing
                // lower siblings. The underlying aligned WebView then owns the original
                // physical gesture for the rest of the pointer stream.
                return false;
            }
            return super.dispatchTouchEvent(event);
        }

        private boolean isInsidePassThroughView(MotionEvent event) {
            View target = passThroughView;
            if (target == null || target.getWidth() <= 0 || target.getHeight() <= 0) return false;
            int[] rootLocation = new int[2];
            int[] targetLocation = new int[2];
            getLocationOnScreen(rootLocation);
            target.getLocationOnScreen(targetLocation);
            float left = targetLocation[0] - rootLocation[0];
            float top = targetLocation[1] - rootLocation[1];
            float x = event.getX();
            float y = event.getY();
            return x >= left && x <= left + target.getWidth()
                    && y >= top && y <= top + target.getHeight();
        }
    }
}