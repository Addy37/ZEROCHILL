package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
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

    private final Activity activity;
    private final Runnable onContinue;
    private final Runnable onCancel;
    private final FrameLayout root;
    private final TextView continueButton;
    private final AtomicBoolean continuePressed = new AtomicBoolean();
    private volatile long continuePressedAtMs;

    XMegaDriveAgePrompt(Activity activity, Runnable onContinue, Runnable onCancel) {
        this.activity = activity;
        this.onContinue = onContinue;
        this.onCancel = onCancel;

        root = new FrameLayout(activity);
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

        continueButton = text(activity, "CONTINUE", 16,
                activity.getColor(R.color.app_on_primary), Typeface.BOLD);
        continueButton.setTag(TAG_CONTINUE);
        continueButton.setGravity(Gravity.CENTER);
        continueButton.setMinHeight(dp(activity, 52));
        continueButton.setBackground(rounded(
                activity,
                activity.getColor(R.color.zc_cyan),
                activity.getColor(R.color.zc_cyan),
                0,
                18
        ));
        continueButton.setOnClickListener(view -> {
            if (!continuePressed.compareAndSet(false, true)) return;
            continuePressedAtMs = SystemClock.elapsedRealtime();
            continueButton.setEnabled(false);
            continueButton.setAlpha(0.78f);
            continueButton.setText("VERIFYING…");
            if (onContinue != null) onContinue.run();
        });
        LinearLayout.LayoutParams continueParams = matchWidthWrap();
        continueParams.topMargin = dp(activity, 22);
        card.addView(continueButton, continueParams);

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
}
