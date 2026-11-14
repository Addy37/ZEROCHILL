package com.webapp.crazyshit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.Locale;

/** Centered ZEROCHILL update panel based on the What's New visual language. */
@SuppressLint("SetTextI18n")
final class UpdateCardController {
    interface Action {
        void run();
    }

    private final Activity activity;
    private final MaterialCardView card;
    private final FrameLayout backdrop;
    private final LinearLayout content;
    private final TextView eyebrow;
    private final TextView title;
    private final TextView body;
    private final TextView percent;
    private final ProgressBar progress;
    private final LinearLayout actions;
    private final MaterialButton primary;
    private final MaterialButton secondary;

    private boolean attached;
    private boolean downloadHidden;
    private boolean downloading;

    UpdateCardController(Activity activity) {
        this.activity = activity;

        card = new MaterialCardView(activity);
        ZeroChillUi.styleMaterialCard(card, R.dimen.zc_radius_medium);
        card.setRadius(dp(24));
        card.setCardBackgroundColor(Color.BLACK);
        card.setStrokeColor(AppearanceTheme.accent(activity));
        card.setStrokeWidth(dp(1));
        card.setCardElevation(dp(12));
        card.setClickable(true);
        card.setFocusable(true);
        card.setTag("zerochill_update_card");

        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(24), dp(24), dp(18));

        LinearLayout branding = new LinearLayout(activity);
        branding.setOrientation(LinearLayout.HORIZONTAL);
        branding.setGravity(Gravity.CENTER);
        TextView zero = text("ZERO", 27, Color.WHITE);
        zero.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView chill = text("CHILL", 27, AppearanceTheme.accent(activity));
        branding.addView(zero);
        branding.addView(chill);
        content.addView(branding);

        title = text("", 23, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headingParams.topMargin = dp(20);
        content.addView(title, headingParams);

        eyebrow = text("", 12, AppearanceTheme.accent(activity));
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        versionParams.topMargin = dp(5);
        content.addView(eyebrow, versionParams);

        body = text("", 14, AppearanceTheme.secondaryText(activity));
        body.setMaxLines(7);
        body.setEllipsize(android.text.TextUtils.TruncateAt.END);
        body.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(18);
        content.addView(body, bodyParams);

        percent = text("", 12, AppearanceTheme.accent(activity));
        percent.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        percent.setGravity(Gravity.END);
        LinearLayout.LayoutParams percentParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        percentParams.topMargin = dp(12);
        content.addView(percent, percentParams);

        progress = new ZeroChillProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgress(0);
        progress.setIndeterminate(false);
        progress.setProgressTintList(ColorStateList.valueOf(AppearanceTheme.accent(activity)));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(47, 53, 61)));
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        progressParams.topMargin = dp(6);
        content.addView(progress, progressParams);

        actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, dp(20), 0, 0);

        primary = button(true);
        secondary = button(false);
        actions.addView(primary, new LinearLayout.LayoutParams(-1, dp(50)));
        actions.addView(secondary, new LinearLayout.LayoutParams(-1, dp(48)));
        content.addView(actions);

        backdrop = new FrameLayout(activity);
        backdrop.setBackgroundColor(Color.argb(188, 0, 0, 0));
        backdrop.setClickable(true);
        backdrop.setFocusableInTouchMode(true);
        backdrop.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode != android.view.KeyEvent.KEYCODE_BACK) return false;
            if (event.getAction() == android.view.KeyEvent.ACTION_UP) hideWhileDownloading();
            return true;
        });

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content);
        card.addView(scroll);
    }

    void showAvailable(
            String version,
            String summary,
            Action download,
            Action later
    ) {
        downloadHidden = false;
        downloading = false;
        ensureAttached();
        eyebrow.setText("VERSION " + cleanVersion(version));
        progress.setVisibility(View.GONE);
        percent.setText("");
        percent.setTextColor(AppearanceTheme.accent(activity));
        title.setText("Update Available");
        body.setText(displaySummary(summary));
        configureButton(primary, "Download update", download, true);
        configureButton(secondary, "Later", later, false);
        showCard();
    }

    void showDownloading(
            String version,
            int progressPercent,
            long downloadedBytes,
            long totalBytes,
            Action cancel
    ) {
        downloading = true;
        if (downloadHidden) return;
        ensureAttached();
        eyebrow.setText("VERSION " + cleanVersion(version) + " · DOWNLOADING");
        percent.setTextColor(AppearanceTheme.accent(activity));
        int safe = Math.max(0, Math.min(100, progressPercent));
        title.setText("Downloading " + cleanVersion(version));
        percent.setText(safe + "%");
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(totalBytes <= 0L);
        if (totalBytes > 0L) progress.setProgress(safe * 10);
        body.setText(downloadDetail(downloadedBytes, totalBytes));
        configureButton(primary, "Keep browsing", this::hideWhileDownloading, true);
        configureButton(secondary, "Cancel", cancel, false);
        showCard();
    }

    void showPreparing(String version, Action cancel) {
        downloading = true;
        if (downloadHidden) return;
        ensureAttached();
        eyebrow.setText("VERSION " + cleanVersion(version) + " · VERIFYING");
        percent.setTextColor(AppearanceTheme.accent(activity));
        title.setText("Preparing " + cleanVersion(version));
        percent.setText("");
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        body.setText("Verifying the update before Android installs it.");
        configureButton(primary, "Keep browsing", this::hideWhileDownloading, true);
        configureButton(secondary, "Cancel", cancel, false);
        showCard();
    }

    void showReady(String version, Action openDownloads, Action later) {
        downloading = false;
        // The system download notification remains available if the user hid the panel.
        if (downloadHidden) {
            downloadHidden = false;
            return;
        }
        ensureAttached();
        eyebrow.setText("VERSION " + cleanVersion(version) + " · DOWNLOADED");
        title.setText(cleanVersion(version) + " is ready to install");
        body.setText("Open Downloads, then tap the ZEROCHILL APK to finish the update.");
        percent.setText("✓");
        percent.setTextColor(AppearanceTheme.accent(activity));
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(false);
        progress.setProgress(1000);
        configureButton(primary, "Open Downloads", openDownloads, true);
        configureButton(secondary, "Later", later, false);
        showCard();
    }

    void showPreviewReady(String version, Action done) {
        downloading = false;
        if (downloadHidden) {
            downloadHidden = false;
            return;
        }
        ensureAttached();
        eyebrow.setText("VERSION " + cleanVersion(version) + " · PREVIEW");
        title.setText(cleanVersion(version) + " is ready");
        body.setText("This was a safe UI preview. No APK was downloaded or installed.");
        percent.setText("✓");
        percent.setTextColor(AppearanceTheme.accent(activity));
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(false);
        progress.setProgress(1000);
        configureButton(primary, "Done", done, true);
        secondary.setVisibility(View.GONE);
        showCard();
    }

    void showError(String version, Action retry, Action dismiss) {
        downloading = false;
        downloadHidden = false;
        ensureAttached();
        eyebrow.setText("VERSION " + cleanVersion(version) + " · PAUSED");
        percent.setTextColor(AppearanceTheme.accent(activity));
        title.setText("Couldn't download " + cleanVersion(version));
        body.setText("Your current ZEROCHILL install is untouched.");
        percent.setText("");
        progress.setVisibility(View.GONE);
        configureButton(primary, "Try again", retry, true);
        configureButton(secondary, "Dismiss", dismiss, false);
        showCard();
    }

    private void hideWhileDownloading() {
        if (downloading) downloadHidden = true;
        dismiss();
    }

    void dismiss() {
        if (!attached) return;
        if (!ZeroChillMotion.animationsEnabled(activity)) {
            detach();
            return;
        }
        card.animate().cancel();
        card.animate()
                .alpha(0f)
                .scaleX(.97f).scaleY(.97f)
                .setDuration(ZeroChillMotion.QUICK_MS)
                .withEndAction(this::detach)
                .start();
    }

    void detachImmediately() {
        detach();
    }

    private void ensureAttached() {
        if (attached || activity.isFinishing() || activity.isDestroyed()) return;
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;
        ViewGroup host = (ViewGroup) decor;

        int availableWidth = activity.getResources().getDisplayMetrics().widthPixels - dp(28);
        int width = Math.min(availableWidth, dp(420));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        card.setLayoutParams(params);
        if (ZeroChillMotion.animationsEnabled(activity)) {
            card.setAlpha(0f);
            card.setScaleX(.96f);
            card.setScaleY(.96f);
        } else {
            card.setAlpha(1f);
            card.setScaleX(1f);
            card.setScaleY(1f);
        }
        backdrop.addView(card);
        host.addView(backdrop, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        backdrop.bringToFront();
        backdrop.requestFocus();
        attached = true;
    }

    private void showCard() {
        card.setVisibility(View.VISIBLE);
        backdrop.bringToFront();
        if (!ZeroChillMotion.animationsEnabled(activity)) return;
        if (card.getAlpha() >= .99f && Math.abs(card.getScaleX() - 1f) < .01f) return;
        card.animate().cancel();
        card.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(190L).start();
    }

    private void configureButton(MaterialButton button, String label, Action action, boolean emphasized) {
        button.setVisibility(View.VISIBLE);
        button.setText(label);
        button.setOnClickListener(v -> {
            if (action != null) action.run();
        });
        button.setTextColor(emphasized ? AppearanceTheme.onAccent(activity) : AppearanceTheme.accent(activity));
        button.setBackgroundTintList(ColorStateList.valueOf(
                emphasized ? AppearanceTheme.accent(activity) : Color.TRANSPARENT
        ));
        button.setStrokeWidth(0);
        button.setStrokeColor(ColorStateList.valueOf(Color.rgb(65, 73, 84)));
        ZeroChillMotion.installPressFeedback(button);
    }

    private MaterialButton button(boolean emphasized) {
        MaterialButton button = new MaterialButton(activity);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setMinWidth(0);
        button.setMinHeight(dp(48));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setCornerRadius(dp(16));
        button.setPadding(dp(10), 0, dp(10), 0);
        if (emphasized) primaryStyle(button);
        return button;
    }

    private void primaryStyle(MaterialButton button) {
        button.setTextColor(AppearanceTheme.onAccent(activity));
        button.setBackgroundTintList(ColorStateList.valueOf(AppearanceTheme.accent(activity)));
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        return view;
    }

    static String displaySummary(String raw) {
        String summary = raw == null ? "" : raw.trim();
        if (summary.isEmpty()) return "A new ZEROCHILL update is available.";
        return summary.contains(" • ")
                ? "•  " + summary.replace(" • ", "\n\n•  ")
                : summary;
    }

    static String downloadDetail(long downloadedBytes, long totalBytes) {
        if (downloadedBytes < 0L) downloadedBytes = 0L;
        if (totalBytes <= 0L) {
            return formatBytes(downloadedBytes) + " downloaded";
        }
        return formatBytes(downloadedBytes) + " of " + formatBytes(totalBytes);
    }

    static String formatBytes(long bytes) {
        double safe = Math.max(0L, bytes);
        if (safe < 1024d * 1024d) {
            return String.format(Locale.US, "%.0f KB", safe / 1024d);
        }
        return String.format(Locale.US, "%.1f MB", safe / (1024d * 1024d));
    }

    private String cleanVersion(String version) {
        String clean = version == null ? "" : version.trim();
        return clean.isEmpty() ? "update" : clean;
    }

    private void detach() {
        if (!attached) return;
        card.animate().cancel();
        ViewParentCompat.remove(backdrop);
        if (card.getParent() == backdrop) backdrop.removeView(card);
        attached = false;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    /** Isolates removeView to keep the controller tolerant of any decor ViewGroup implementation. */
    private static final class ViewParentCompat {
        private ViewParentCompat() {}

        static void remove(View view) {
            if (view == null || !(view.getParent() instanceof ViewGroup)) return;
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }
}
