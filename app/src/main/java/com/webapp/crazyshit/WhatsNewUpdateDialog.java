package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

/** One-time in-app release announcement backed by the existing live liquid-glass page. */
final class WhatsNewUpdateDialog {
    private static final String PREFS = "app_prefs";
    private static final String KEY_LAST_ACKNOWLEDGED = "whats_new_acknowledged_version_code";

    private final Activity activity;
    private final FrameLayout parent;
    private final View protectedContent;
    private final FrameLayout scrim;
    private final GlassHeaderView glassCard;
    private final ScrollView notesScroller;
    private final FrameLayout.LayoutParams cardParams;
    private final int contentAccessibilityBefore;
    private final int versionCode;
    private final boolean preview;
    private final Runnable afterDismiss;
    private boolean showing = true;

    private WhatsNewUpdateDialog(Activity activity, FrameLayout parent, View protectedContent,
                                 ViewPageBackdrop backdrop, WhatsNewChangelog.Entry entry,
                                 int versionCode, boolean preview, Runnable afterDismiss) {
        this.activity = activity;
        this.parent = parent;
        this.protectedContent = protectedContent;
        this.versionCode = versionCode;
        this.preview = preview;
        this.afterDismiss = afterDismiss;
        contentAccessibilityBefore = protectedContent.getImportantForAccessibility();

        scrim = new FrameLayout(activity);
        scrim.setBackgroundColor(Color.argb(175, 0, 0, 0));
        scrim.setClickable(true);
        scrim.setFocusableInTouchMode(true);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            scrim.setAccessibilityPaneTitle("What's New in ZEROCHILL");
        }

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(21), dp(22), dp(19));
        content.setBackground(panelFilm());
        content.setClickable(true);
        content.setFocusable(true);

        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);

        ImageView mascot = new ImageView(activity);
        mascot.setImageResource(R.mipmap.ic_launcher);
        mascot.setContentDescription(null);
        mascot.setScaleType(ImageView.ScaleType.FIT_CENTER);
        heading.addView(mascot, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout headingText = new LinearLayout(activity);
        headingText.setOrientation(LinearLayout.VERTICAL);
        headingText.setPadding(dp(12), 0, 0, 0);

        TextView eyebrow = label("WHAT'S NEW", 12f, UiPalette.PRIMARY, true);
        eyebrow.setLetterSpacing(.12f);
        headingText.addView(eyebrow);

        TextView wordmark = label("ZEROCHILL", 21f, Color.WHITE, true);
        wordmark.setText(ZeroChillUi.sectionTitle(activity, "ZEROCHILL", 4, 9));
        headingText.addView(wordmark);
        heading.addView(headingText, new LinearLayout.LayoutParams(0, -2, 1f));
        content.addView(heading);

        TextView version = label("VERSION " + entry.versionName
                + (preview ? "  •  PREVIEW" : ""), 12f,
                ZeroChillUi.color(activity, R.color.zc_text_secondary), false);
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(-1, -2);
        versionParams.topMargin = dp(13);
        content.addView(version, versionParams);

        notesScroller = new ScrollView(activity);
        notesScroller.setFillViewport(false);
        notesScroller.setVerticalScrollBarEnabled(false);
        notesScroller.setClipToPadding(false);
        LinearLayout notes = new LinearLayout(activity);
        notes.setOrientation(LinearLayout.VERTICAL);
        notes.setPadding(0, dp(10), 0, dp(9));
        for (String highlight : entry.highlights) {
            notes.addView(highlightRow(highlight));
        }
        notesScroller.addView(notes, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams notesParams = new LinearLayout.LayoutParams(-1, dp(235));
        content.addView(notesScroller, notesParams);

        MaterialButton gotIt = new MaterialButton(activity);
        gotIt.setText("Got it");
        gotIt.setAllCaps(false);
        gotIt.setTextSize(15);
        gotIt.setTypeface(null, Typeface.BOLD);
        gotIt.setTextColor(Color.WHITE);
        gotIt.setMinHeight(0);
        gotIt.setInsetTop(0);
        gotIt.setInsetBottom(0);
        gotIt.setCornerRadius(dp(15));
        gotIt.setBackgroundTintList(ColorStateList.valueOf(UiPalette.PRIMARY));
        gotIt.setContentDescription("Got it. Close What's New.");
        ZeroChillMotion.installPressFeedback(gotIt);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(49));
        buttonParams.topMargin = dp(9);
        content.addView(gotIt, buttonParams);

        // GlassHeaderView is the SAME Compose/AGSL material and live page backdrop
        // used by the top bar, Search and navbar. No bitmap capture or copy of shaders.
        FrameLayout controls = new FrameLayout(activity);
        controls.addView(content, new FrameLayout.LayoutParams(-1, -2));
        glassCard = new GlassHeaderView(activity, controls, 25f, true, false, false, 0f);
        glassCard.setPageBackdrop(backdrop);
        glassCard.setClickable(true);
        cardParams = new FrameLayout.LayoutParams(dp(350), -2, Gravity.CENTER);
        scrim.addView(glassCard, cardParams);

        gotIt.setOnClickListener(v -> dismiss(true));
        scrim.setOnClickListener(v -> dismiss(true));
        protectedContent.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        parent.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        scrim.requestFocus();
        scrim.addOnLayoutChangeListener((v, left, top, right, bottom,
                                         oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                refreshLayout();
            }
        });
        refreshLayout();
        if (ZeroChillMotion.animationsEnabled(activity)) {
            glassCard.setAlpha(0f);
            glassCard.setScaleX(.96f);
            glassCard.setScaleY(.96f);
            glassCard.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setInterpolator(ZeroChillMotion.EASE_OUT)
                    .setDuration(ZeroChillMotion.STANDARD_MS).start();
        }
    }

    static WhatsNewUpdateDialog maybeShow(Activity activity, FrameLayout parent,
                                           View protectedContent, ViewPageBackdrop backdrop,
                                           Runnable onDismiss) {
        if (activity == null || parent == null || protectedContent == null || backdrop == null
                || activity.isFinishing() || activity.isDestroyed()) return null;
        int currentCode = BuildConfig.VERSION_CODE;
        WhatsNewChangelog.Entry entry = WhatsNewChangelog.forVersion(currentCode);
        if (entry == null) return null;

        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Integer acknowledged = prefs.contains(KEY_LAST_ACKNOWLEDGED)
                ? prefs.getInt(KEY_LAST_ACKNOWLEDGED, 0) : null;
        long firstInstallTime = 0L;
        long lastUpdateTime = 0L;
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(
                    activity.getPackageName(), 0);
            firstInstallTime = info.firstInstallTime;
            lastUpdateTime = info.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }

        boolean show = WhatsNewChangelog.shouldShow(currentCode, acknowledged,
                firstInstallTime, lastUpdateTime, true);
        if (!show) {
            // A genuine fresh install begins tracking at this version.
            if (acknowledged == null && firstInstallTime > 0 && lastUpdateTime > 0
                    && lastUpdateTime - firstInstallTime <= 1_000L) {
                prefs.edit().putInt(KEY_LAST_ACKNOWLEDGED, currentCode).apply();
            }
            return null;
        }
        return new WhatsNewUpdateDialog(activity, parent, protectedContent,
                backdrop, entry, currentCode, false, onDismiss);
    }

    /** Debug-only entry point used by a long press on the main header. */
    static WhatsNewUpdateDialog showPreview(Activity activity, FrameLayout parent,
                                             View protectedContent, ViewPageBackdrop backdrop) {
        if (!BuildConfig.DEBUG || activity == null || parent == null
                || protectedContent == null || backdrop == null) return null;
        WhatsNewChangelog.Entry entry = WhatsNewChangelog.forVersion(
                WhatsNewChangelog.FIRST_SUPPORTED_VERSION_CODE);
        return new WhatsNewUpdateDialog(activity, parent, protectedContent,
                backdrop, entry, BuildConfig.VERSION_CODE, true, null);
    }

    boolean isShowing() {
        return showing;
    }

    void refreshLayout() {
        if (!showing) return;
        int w = parent.getWidth() > 0 ? parent.getWidth()
                : activity.getResources().getDisplayMetrics().widthPixels;
        int h = parent.getHeight() > 0 ? parent.getHeight()
                : activity.getResources().getDisplayMetrics().heightPixels;
        int panelWidth = Math.min(Math.max(dp(190), w - dp(32)), dp(440));
        if (cardParams.width != panelWidth) {
            cardParams.width = panelWidth;
            glassCard.setLayoutParams(cardParams);
        }
        ViewGroup.LayoutParams notesParams = notesScroller.getLayoutParams();
        int scrollerHeight = Math.min(dp(235), Math.max(dp(68), h - dp(255)));
        if (notesParams.height != scrollerHeight) {
            notesParams.height = scrollerHeight;
            notesScroller.setLayoutParams(notesParams);
        }
    }

    /** On navigation or activity destruction do not acknowledge an unseen release. */
    void dismissWithoutAcknowledging() {
        close(false);
    }

    void dismiss(boolean acknowledge) {
        close(acknowledge);
    }

    private void close(boolean acknowledge) {
        if (!showing) return;
        showing = false;
        glassCard.animate().cancel();
        protectedContent.setImportantForAccessibility(contentAccessibilityBefore);
        parent.removeView(scrim);
        if (acknowledge && !preview) {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_LAST_ACKNOWLEDGED, versionCode).apply();
        }
        if (acknowledge && afterDismiss != null
                && !activity.isFinishing() && !activity.isDestroyed()) {
            afterDismiss.run();
        }
    }

    private View highlightRow(String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(10), 0, dp(10));

        TextView bullet = label("•", 22f, UiPalette.PRIMARY, true);
        bullet.setGravity(Gravity.TOP);
        row.addView(bullet, new LinearLayout.LayoutParams(dp(22), -2));
        TextView body = label(value, 14f,
                ZeroChillUi.color(activity, R.color.zc_text_primary), false);
        body.setLineSpacing(dp(2), 1.08f);
        row.addView(body, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private TextView label(String text, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private GradientDrawable panelFilm() {
        GradientDrawable result = new GradientDrawable();
        result.setColor(Color.argb(176, 9, 15, 23));
        result.setCornerRadius(dp(25));
        result.setStroke(dp(1), Color.argb(180, 8, 146, 208));
        return result;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
