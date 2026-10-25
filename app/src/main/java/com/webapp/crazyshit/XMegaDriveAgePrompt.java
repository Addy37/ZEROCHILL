package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Native ZEROCHILL consent prompt for XMegaDrive's explicit age-session marker. */
final class XMegaDriveAgePrompt {
    static final String TAG_TITLE = "xmegadrive_age_prompt_title";
    static final String TAG_CONTINUE = "xmegadrive_age_prompt_continue";
    static final String TAG_CANCEL = "xmegadrive_age_prompt_cancel";
    static final String TAG_NOTE = "xmegadrive_age_prompt_note";

    interface ConfirmHandler {
        void onConfirm(XMegaDriveAgePrompt prompt);
    }

    private final Activity activity;
    private final ConfirmHandler onConfirm;
    private final Runnable onCancel;
    private final FrameLayout root;
    private final TextView continueButton;
    private final TextView note;

    XMegaDriveAgePrompt(Activity activity, ConfirmHandler onConfirm, Runnable onCancel) {
        this.activity = activity;
        this.onConfirm = onConfirm;
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

        TextView title = text(activity, "Set up XMegaDrive access", 24,
                activity.getColor(R.color.zc_text_primary), Typeface.BOLD);
        title.setTag(TAG_TITLE);
        LinearLayout.LayoutParams titleParams = wrap();
        titleParams.topMargin = dp(activity, 14);
        card.addView(title, titleParams);

        TextView body = text(
                activity,
                "Confirm that you're 18 or older. ZEROCHILL will prepare XMegaDrive's age-access marker on this device so the site's age page stays out of the way.",
                15,
                activity.getColor(R.color.zc_text_secondary),
                Typeface.NORMAL
        );
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(0f, 1.08f);
        LinearLayout.LayoutParams bodyParams = matchWidthWrap();
        bodyParams.topMargin = dp(activity, 12);
        card.addView(body, bodyParams);

        continueButton = text(activity, "CONFIRM 18+ & CONTINUE", 16,
                activity.getColor(R.color.app_on_primary), Typeface.BOLD);
        continueButton.setTag(TAG_CONTINUE);
        continueButton.setGravity(Gravity.CENTER);
        continueButton.setMinHeight(dp(activity, 52));
        continueButton.setClickable(true);
        continueButton.setFocusable(true);
        continueButton.setBackground(rounded(
                activity,
                activity.getColor(R.color.zc_cyan),
                activity.getColor(R.color.zc_cyan),
                0,
                18
        ));
        continueButton.setOnClickListener(view -> {
            showVerifying();
            if (onConfirm != null) onConfirm.onConfirm(this);
        });
        LinearLayout.LayoutParams continueParams = matchWidthWrap();
        continueParams.topMargin = dp(activity, 22);
        card.addView(continueButton, continueParams);

        note = text(
                activity,
                "This only prepares XMegaDrive access. It does not share your ZEROCHILL account or messages.",
                12,
                activity.getColor(R.color.zc_text_muted),
                Typeface.NORMAL
        );
        note.setTag(TAG_NOTE);
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
        activity.addContentView(root, new FrameLayout.LayoutParams(-1, -1));
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

    void showVerifying() {
        continueButton.setText("SETTING UP…");
        continueButton.setEnabled(false);
        continueButton.setAlpha(0.78f);
    }

    void showReady() {
        continueButton.setText("✓ ACCESS READY");
        continueButton.setEnabled(false);
        continueButton.setAlpha(1f);
        note.setText("XMegaDrive age access is ready on this device.");
    }

    void showFailure() {
        continueButton.setText("TRY AGAIN");
        continueButton.setEnabled(true);
        continueButton.setAlpha(1f);
        note.setText("Access is confirmed. ZEROCHILL couldn't store the marker yet. Try again or choose Not now.");
    }

    FrameLayout rootForTest() {
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
        return new LinearLayout.LayoutParams(-2, -2);
    }

    private static LinearLayout.LayoutParams matchWidthWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
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
