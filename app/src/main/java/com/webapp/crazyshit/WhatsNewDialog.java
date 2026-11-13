package com.webapp.crazyshit;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Native fragment restores an unacknowledged dialog across rotation and process recreation. */
@SuppressWarnings("deprecation")
public final class WhatsNewDialog extends DialogFragment {
    static final String TAG = "zerochill_whats_new";
    static final String NOTES_REVISION = "7.0-personalization-v1";
    private boolean closing;
    private boolean customizing;

    static boolean show(Activity activity, boolean automatic) {
        if (activity.isFinishing() || activity.isDestroyed()
                || activity.getFragmentManager().isStateSaved()) return false;
        if (activity.getFragmentManager().findFragmentByTag(TAG) != null) return true;
        WhatsNewDialog dialog = new WhatsNewDialog();
        Bundle args = new Bundle();
        args.putString("version", BuildConfig.VERSION_NAME);
        args.putLong("code", BuildConfig.VERSION_CODE);
        args.putBoolean("automatic", automatic);
        dialog.setArguments(args);
        dialog.show(activity.getFragmentManager(), TAG);
        activity.getFragmentManager().executePendingTransactions();
        return true;
    }

    static boolean visible(Activity activity) {
        return activity.getFragmentManager().findFragmentByTag(TAG) != null;
    }

    static void deferAutomatic(Activity activity) {
        android.app.Fragment fragment = activity.getFragmentManager().findFragmentByTag(TAG);
        if (fragment instanceof WhatsNewDialog && fragment.getArguments().getBoolean("automatic")) {
            ((WhatsNewDialog) fragment).dismissAllowingStateLoss();
        }
    }

    @Override public Dialog onCreateDialog(Bundle state) {
        boolean sameVersion = getArguments().getLong("code") == BuildConfig.VERSION_CODE;
        closing = sameVersion && state != null && state.getBoolean("closing", false);
        customizing = sameVersion && state != null && state.getBoolean("customizing", false);
        // Android may restore a task saved before the package was updated.
        if (!sameVersion) {
            getArguments().putLong("code", BuildConfig.VERSION_CODE);
            getArguments().putString("version", BuildConfig.VERSION_NAME);
        }
        Activity activity = getActivity();
        Dialog dialog = new Dialog(activity) {
            @Override public void cancel() { close(false); }
        };
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(24), dp(24), dp(16));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.BLACK);
        background.setCornerRadius(dp(24));
        background.setStroke(dp(1), AppearanceTheme.accent(activity));
        content.setBackground(background);
        scroll.addView(content);

        LinearLayout branding = new LinearLayout(activity);
        branding.setGravity(Gravity.CENTER);
        branding.addView(text("ZERO", 27, Color.WHITE, true));
        branding.addView(text("CHILL", 27, AppearanceTheme.accent(activity), false));
        content.addView(branding);
        TextView title = text("What’s New", 23, Color.WHITE, true);
        title.setPadding(0, dp(20), 0, dp(4));
        content.addView(title);
        TextView version = text("Version " + getArguments().getString("version"), 12,
                AppearanceTheme.accent(activity), true);
        content.addView(version);
        for (String highlight : notes(activity, getArguments().getString("version"))) {
            TextView row = text("•  " + highlight, 14, AppearanceTheme.secondaryText(activity), false);
            row.setPadding(0, dp(16), 0, 0);
            row.setLineSpacing(dp(2), 1f);
            content.addView(row);
        }
        TextView go = button("LET’S GO", true);
        LinearLayout.LayoutParams primary = new LinearLayout.LayoutParams(-1, dp(50));
        primary.topMargin = dp(24);
        content.addView(go, primary);
        go.setOnClickListener(v -> close(false));
        TextView customize = button("Customize appearance", false);
        content.addView(customize, new LinearLayout.LayoutParams(-1, dp(48)));
        customize.setOnClickListener(v -> close(true));
        dialog.setContentView(scroll);
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    @Override public void onStart() {
        super.onStart();
        if (closing) {
            dismissAllowingStateLoss();
            return;
        }
        Window window = getDialog().getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.width = Math.min(dp(420), getResources().getDisplayMetrics().widthPixels - dp(28));
        attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
        attrs.dimAmount = .72f;
        window.setAttributes(attrs);
        View decor = window.getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(attrs.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((int) (getResources().getDisplayMetrics().heightPixels * .85f), View.MeasureSpec.AT_MOST));
        window.setLayout(attrs.width, Math.min(decor.getMeasuredHeight(), (int) (getResources().getDisplayMetrics().heightPixels * .85f)));
        if (ZeroChillMotion.animationsEnabled(getActivity())) {
            decor.setAlpha(0f);
            decor.setScaleX(.96f);
            decor.setScaleY(.96f);
            decor.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(190L).start();
        }
    }

    @Override public void onSaveInstanceState(Bundle out) {
        out.putBoolean("closing", closing);
        out.putBoolean("customizing", customizing);
        super.onSaveInstanceState(out);
    }

    private void close(boolean customize) {
        if (closing || getActivity() == null) return;
        closing = true;
        customizing = customize;
        Activity activity = getActivity();
        // Persist only an explicit dismissal, never lifecycle teardown or navigation deferral.
        WhatsNewPolicy.acknowledge(activity, getArguments().getLong("code"));
        Runnable finish = () -> {
            if (!isAdded()) return;
            dismissAllowingStateLoss();
        };
        Window window = getDialog().getWindow();
        if (window != null && ZeroChillMotion.animationsEnabled(activity)) {
            window.getDecorView().animate().alpha(0f).scaleX(.97f).scaleY(.97f)
                    .setDuration(140L).withEndAction(finish).start();
        } else finish.run();
    }

    @Override public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        Activity activity = getActivity();
        if (!closing || activity == null || activity.isChangingConfigurations()
                || activity.isFinishing() || activity.isDestroyed()) return;
        if (getArguments().getBoolean("automatic")
                && !WhatsNewPolicy.allowsAutomaticDisplay(activity.getIntent())) return;
        if (activity instanceof NativeMainActivity) {
            ((NativeMainActivity) activity).onWhatsNewAcknowledged(customizing);
        }
        if (customizing) activity.startActivity(new Intent(activity, AppearanceActivity.class));
    }

    static List<String> notes(Activity activity, String version) {
        // This source revision bundles verified changes in this binary. Its display version
        // comes from packaging, which also supports local/custom Beta version labels.
        try (InputStream input = activity.getResources().openRawResource(R.raw.whats_new_7_0);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            List<String> highlights = ReleaseNotesSummary.highlights(
                    new String(output.toByteArray(), StandardCharsets.UTF_8));
            if (!highlights.isEmpty()) return highlights;
        } catch (java.io.IOException ignored) { }
        return java.util.Collections.singletonList("Your installed version is ready. You can personalize ZEROCHILL in Appearance settings.");
    }

    private TextView button(String label, boolean primary) {
        TextView view = text(label, 14, primary ? AppearanceTheme.onAccent(getActivity())
                : AppearanceTheme.accent(getActivity()), true);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setFocusable(true);
        if (primary) {
            GradientDrawable fill = new GradientDrawable();
            fill.setColor(AppearanceTheme.accent(getActivity()));
            fill.setCornerRadius(dp(16));
            view.setBackground(fill);
        }
        ZeroChillMotion.installPressFeedback(view);
        return view;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(getActivity());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
