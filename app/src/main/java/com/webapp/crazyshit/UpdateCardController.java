package com.webapp.crazyshit;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.Locale;

/** Small in-app updater surface that stays out of the way of primary navigation. */
final class UpdateCardController {
    interface Action {
        void run();
    }

    private final Activity activity;
    private final MaterialCardView card;
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

    UpdateCardController(Activity activity) {
        this.activity = activity;

        card = new MaterialCardView(activity);
        ZeroChillUi.styleMaterialCard(card, R.dimen.zc_radius_large);
        card.setStrokeColor(UiPalette.PRIMARY);
        card.setStrokeWidth(dp(1));
        card.setCardElevation(dp(10));
        card.setClickable(true);
        card.setFocusable(true);
        card.setTag("zerochill_update_card");

        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(11));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.mipmap.ic_launcher);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        iconParams.rightMargin = dp(10);
        header.addView(icon, iconParams);

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        eyebrow = text("ZEROCHILL UPDATE", 9, UiPalette.PRIMARY);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        eyebrow.setLetterSpacing(0.08f);
        title = text("", 16, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        body = text("", 11, Color.rgb(184, 190, 198));
        body.setMaxLines(2);
        titles.addView(eyebrow);
        titles.addView(title);
        titles.addView(body);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        percent = text("", 12, Color.WHITE);
        percent.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        percent.setGravity(Gravity.END);
        percent.setMinWidth(dp(42));
        header.addView(percent);
        content.addView(header);

        progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgress(0);
        progress.setIndeterminate(false);
        progress.setProgressTintList(ColorStateList.valueOf(UiPalette.PRIMARY));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(47, 53, 61)));
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
        progressParams.topMargin = dp(9);
        content.addView(progress, progressParams);

        actions = new LinearLayout(activity);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(8), 0, 0);

        secondary = button(false);
        primary = button(true);
        actions.addView(secondary);
        LinearLayout.LayoutParams primaryParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        primaryParams.leftMargin = dp(7);
        actions.addView(primary, primaryParams);
        content.addView(actions);

        card.addView(content);
    }

    void showAvailable(
            String version,
            String summary,
            Action download,
            Action later
    ) {
        ensureAttached();
        eyebrow.setText("ZEROCHILL UPDATE");
        progress.setVisibility(View.GONE);
        percent.setText("");
        percent.setTextColor(Color.WHITE);
        title.setText("Version " + cleanVersion(version) + " is ready");
        body.setText(summary == null || summary.trim().isEmpty()
                ? "A new ZEROCHILL update is available."
                : summary.trim());
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
        ensureAttached();
        eyebrow.setText("DOWNLOADING UPDATE");
        percent.setTextColor(Color.WHITE);
        int safe = Math.max(0, Math.min(100, progressPercent));
        title.setText("Downloading " + cleanVersion(version));
        percent.setText(safe + "%");
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(totalBytes <= 0L);
        if (totalBytes > 0L) progress.setProgress(safe * 10);
        body.setText(downloadDetail(downloadedBytes, totalBytes));
        primary.setVisibility(View.GONE);
        configureButton(secondary, "Cancel", cancel, false);
        showCard();
    }

    void showPreparing(String version, Action cancel) {
        ensureAttached();
        eyebrow.setText("VERIFYING UPDATE");
        percent.setTextColor(Color.WHITE);
        title.setText("Preparing " + cleanVersion(version));
        percent.setText("");
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        body.setText("Verifying the update before Android installs it.");
        primary.setVisibility(View.GONE);
        configureButton(secondary, "Cancel", cancel, false);
        showCard();
    }

    void showReady(String version, Action install, Action later) {
        ensureAttached();
        eyebrow.setText("UPDATE READY");
        title.setText(cleanVersion(version) + " downloaded");
        body.setText("Ready to install. Android will ask for confirmation.");
        percent.setText("✓");
        percent.setTextColor(UiPalette.PRIMARY);
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(false);
        progress.setProgress(1000);
        configureButton(primary, "Install now", install, true);
        configureButton(secondary, "Later", later, false);
        showCard();
    }

    void showPreviewReady(String version, Action done) {
        ensureAttached();
        eyebrow.setText("PREVIEW COMPLETE");
        title.setText(cleanVersion(version) + " is ready");
        body.setText("This was a safe UI preview. No APK was downloaded or installed.");
        percent.setText("✓");
        percent.setTextColor(UiPalette.PRIMARY);
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(false);
        progress.setProgress(1000);
        configureButton(primary, "Done", done, true);
        secondary.setVisibility(View.GONE);
        showCard();
    }

    void showError(String version, Action retry, Action dismiss) {
        ensureAttached();
        eyebrow.setText("UPDATE PAUSED");
        percent.setTextColor(Color.WHITE);
        title.setText("Couldn't download " + cleanVersion(version));
        body.setText("Your current ZEROCHILL install is untouched.");
        percent.setText("");
        progress.setVisibility(View.GONE);
        configureButton(primary, "Try again", retry, true);
        configureButton(secondary, "Dismiss", dismiss, false);
        showCard();
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
                .translationY(dp(18))
                .setDuration(ZeroChillMotion.QUICK_MS)
                .withEndAction(this::detach)
                .start();
    }

    void detachImmediately() {
        detach();
    }

    private void ensureAttached() {
        if (attached) return;
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;
        ViewGroup host = (ViewGroup) decor;

        int availableWidth = activity.getResources().getDisplayMetrics().widthPixels - dp(24);
        int width = Math.min(availableWidth, dp(440));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.max(dp(260), width),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        params.leftMargin = dp(12);
        params.rightMargin = dp(12);
        params.bottomMargin = activity instanceof NativeMainActivity ? dp(78) : dp(18);
        card.setLayoutParams(params);
        card.setAlpha(1f);
        card.setTranslationY(0f);
        host.addView(card);
        card.bringToFront();
        attached = true;
    }

    private void showCard() {
        card.setVisibility(View.VISIBLE);
        card.bringToFront();
        if (!ZeroChillMotion.animationsEnabled(activity)) return;
        if (card.getAlpha() >= 0.99f && Math.abs(card.getTranslationY()) < 0.5f) return;
        card.animate().cancel();
        card.setAlpha(0f);
        card.setTranslationY(dp(18));
        card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(ZeroChillMotion.STANDARD_MS)
                .start();
    }

    private void configureButton(MaterialButton button, String label, Action action, boolean emphasized) {
        button.setVisibility(View.VISIBLE);
        button.setText(label);
        button.setOnClickListener(v -> {
            if (action != null) action.run();
        });
        button.setTextColor(emphasized ? Color.BLACK : Color.WHITE);
        button.setBackgroundTintList(ColorStateList.valueOf(
                emphasized ? UiPalette.PRIMARY : Color.rgb(31, 36, 43)
        ));
        button.setStrokeWidth(emphasized ? 0 : dp(1));
        button.setStrokeColor(ColorStateList.valueOf(Color.rgb(65, 73, 84)));
        ZeroChillMotion.installPressFeedback(button);
    }

    private MaterialButton button(boolean emphasized) {
        MaterialButton button = new MaterialButton(activity);
        button.setAllCaps(false);
        button.setTextSize(11);
        button.setMinWidth(0);
        button.setMinHeight(dp(36));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setCornerRadius(dp(10));
        button.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        button.setLayoutParams(params);
        if (emphasized) primaryStyle(button);
        return button;
    }

    private void primaryStyle(MaterialButton button) {
        button.setTextColor(Color.BLACK);
        button.setBackgroundTintList(ColorStateList.valueOf(UiPalette.PRIMARY));
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        return view;
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
        ViewParentCompat.remove(card);
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
