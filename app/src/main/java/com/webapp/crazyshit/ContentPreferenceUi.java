package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared native picker and non-intercepting, one-time mode presentation. */
final class ContentPreferenceUi {
    static void choose(Context context, Runnable changed) {
        int cyan = ZeroChillUi.color(context, R.color.zc_cyan);
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(context, 12), dp(context, 16), dp(context, 12), dp(context, 6));
        TextView heading = label(context, "Content preference", 18, Color.WHITE);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setShadowLayer(dp(context, 3), 0, dp(context, 1), Color.BLACK);
        panel.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        android.widget.ScrollView scroll = new android.widget.ScrollView(context);
        scroll.setFillViewport(false);
        LinearLayout choices = new LinearLayout(context);
        choices.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(choices);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, -2);
        scrollParams.topMargin = dp(context, 12);
        panel.addView(scroll, scrollParams);
        PreferenceGlassPanel glassPanel = new PreferenceGlassPanel(context, panel);
        android.app.AlertDialog dialog = new ZeroChillDialog.Builder(context).setView(glassPanel).create();
        ContentPreference current = ContentPreference.get(context);
        for (ContentPreference option : ContentPreference.values()) {
            boolean selected = current == option;
            LinearLayout row = new LinearLayout(context) {
                @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(info);
                    info.setClassName("android.widget.RadioButton");
                    info.setCheckable(true); info.setChecked(isSelected());
                }
            };
            row.setTag("preference_" + option.value);
            row.setSelected(selected);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(context, 48));
            row.setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6));
            row.setBackground(selected ? ZeroChillUi.rounded(context, 0x300892D0,
                    0x600892D0, R.dimen.zc_radius_medium) : null);
            row.setClickable(true); row.setFocusable(true);
            row.setContentDescription(option.label);
            if (option.icon() != 0) {
                ImageView icon = new ImageView(context);
                icon.setImageResource(option.icon()); icon.setColorFilter(selected ? cyan : Color.WHITE);
                icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                row.addView(icon, new LinearLayout.LayoutParams(dp(context, 34), dp(context, 28)));
            } else {
                TextView icon = label(context, option.symbol, option == ContentPreference.ALL ? 12 : 25,
                        selected ? cyan : Color.WHITE);
                icon.setTypeface(null, weight(option));
                icon.setShadowLayer(dp(context, 3), 0, dp(context, 1), Color.BLACK);
                icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                row.addView(icon, new LinearLayout.LayoutParams(dp(context, 34), dp(context, 32)));
            }
            TextView title = label(context, option.label, 15, Color.WHITE);
            title.setGravity(Gravity.CENTER_VERTICAL);
            title.setShadowLayer(dp(context, 3), 0, dp(context, 1), Color.BLACK);
            title.setPadding(dp(context, 12), 0, dp(context, 8), 0);
            title.setTypeface(null, selected ? android.graphics.Typeface.BOLD : weight(option));
            row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            TextView check = label(context, selected ? "✓" : "○", 20, selected ? cyan : Color.WHITE);
            check.setShadowLayer(dp(context, 3), 0, dp(context, 1), Color.BLACK);
            check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(check, new LinearLayout.LayoutParams(dp(context, 26), -2));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(context, 2);
            choices.addView(row, params);
            ZeroChillMotion.installPressFeedback(row);
            row.setOnClickListener(v -> {
                boolean actualChange = ContentPreference.set(context, option);
                dialog.dismiss();
                if (actualChange && changed != null) changed.run();
            });
        }
        TextView cancel = label(context, "Cancel", 14, Color.WHITE);
        cancel.setTag("preference_cancel");
        cancel.setShadowLayer(dp(context, 3), 0, dp(context, 1), Color.BLACK);
        cancel.setMinimumHeight(dp(context, 48));
        cancel.setClickable(true); cancel.setFocusable(true);
        cancel.setOnClickListener(v -> dialog.dismiss());
        ZeroChillMotion.installPressFeedback(cancel);
        panel.addView(cancel, new LinearLayout.LayoutParams(-1, -2));
        dialog.setCanceledOnTouchOutside(true);
        dialog.setView(glassPanel, 0, 0, 0, 0);
        if (dialog.getWindow() != null) {
            // Native RenderNode optics require a hardware-accelerated dialog window.
            if (android.os.Build.VERSION.SDK_INT >= 31) dialog.getWindow().addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
            // Install the decor before assigning zero; otherwise the theme restores its animation.
            dialog.getWindow().getDecorView();
            dialog.getWindow().setWindowAnimations(ZeroChillMotion.animationsEnabled(context)
                    ? R.style.Animation_ZeroChill_Preference : 0);
        }
        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.getDecorView().setPadding(0, 0, 0, 0);
            android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            int width = pickerWidth(context);
            // Keep every option reachable with large fonts, small windows and rotation.
            panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int chrome = panel.getMeasuredHeight() - scroll.getMeasuredHeight();
            scroll.getLayoutParams().height = Math.min(scroll.getMeasuredHeight(),
                    Math.max(dp(context, 100), (int) (metrics.heightPixels * .82f) - chrome));
            window.setLayout(width, -2);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.16f);
        }
    }

    static int pickerWidth(Context context) {
        return Math.min(dp(context, 292), Math.round(context.getResources().getDisplayMetrics().widthPixels * .80f));
    }

    static void bindSelector(TextView selector) {
        Context context = selector.getContext();
        ContentPreference mode = ContentPreference.get(context);
        Object previous = selector.getTag(R.id.content_preference_mode);
        selector.setTag(R.id.content_preference_mode, mode);
        String compact = (mode.symbol.isEmpty() ? "" : mode.symbol + "  ") + "▾";
        android.text.SpannableString ink = new android.text.SpannableString(compact);
        if (!mode.symbol.isEmpty() && mode != ContentPreference.ALL) {
            ink.setSpan(new android.text.style.RelativeSizeSpan(1.65f), 0, mode.symbol.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        selector.setText(ink);
        selector.setTextColor(Color.WHITE);
        selector.setTypeface(null, weight(mode));
        android.graphics.drawable.Drawable icon = mode.icon() == 0 ? null
                : androidx.core.content.ContextCompat.getDrawable(context, mode.icon());
        if (icon != null) {
            icon = icon.mutate(); icon.setTint(Color.WHITE);
            icon.setBounds(0, 0, dp(context, 22), dp(context, 22));
        }
        selector.setCompoundDrawablesRelative(icon, null, null, null);
        selector.setCompoundDrawablePadding(dp(context, 7));
        selector.setBackground(heroButtonBackground(context));
        selector.setContentDescription("Content preference: " + mode.label);
        selector.setTooltipText("Content preference: " + mode.label);
        if (previous != null && previous != mode && selector.isAttachedToWindow()
                && ZeroChillMotion.animationsEnabled(context)) {
            selector.animate().cancel(); selector.setAlpha(.65f);
            selector.animate().alpha(1).setDuration(ZeroChillMotion.QUICK_MS).start();
        }
    }

    private static int weight(ContentPreference mode) {
        return mode == ContentPreference.ALL || mode == ContentPreference.MEN
                || mode == ContentPreference.TRANS_MEN ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL;
    }

    /** Shared with View Gallery so both lower hero controls use the same material. */
    static android.graphics.drawable.GradientDrawable heroButtonBackground(Context context) {
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(Color.argb(205, 15, 18, 22));
        background.setCornerRadius(dp(context, 20));
        background.setStroke(dp(context, 1), Color.argb(80, 255, 255, 255));
        return background;
    }

    static void clearPresentation(FrameLayout surface) {
        View previous = surface.findViewWithTag("content_preference_presentation");
        if (previous != null && previous.getParent() == surface) surface.removeView(previous);
    }

    static void present(FrameLayout surface, boolean shittok) { present(surface, shittok, null); }

    static void present(FrameLayout surface, boolean shittok, View anchor) {
        if (!surface.isShown() || !ContentPreference.acknowledge(surface.getContext(), shittok ? "shittok" : "onlyfap")) return;
        clearPresentation(surface);
        Context context = surface.getContext();
        ContentPreference mode = ContentPreference.get(context);
        PreferenceNotice notice = new PreferenceNotice(context);
        notice.setTag("content_preference_presentation");
        notice.setOrientation(LinearLayout.HORIZONTAL);
        notice.setGravity(Gravity.CENTER);
        notice.setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10));
        notice.setClickable(false); notice.setFocusable(false);
        int cyan = ZeroChillUi.color(context, R.color.zc_cyan);
        android.graphics.drawable.GradientDrawable glass = heroButtonBackground(context);
        glass.setColor(0xB015222C); glass.setStroke(dp(context, 1), 0x900892D0);
        android.graphics.drawable.GradientDrawable sheen = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{0x40B4EFFF, 0x0A0892D0, 0x080892D0});
        sheen.setCornerRadius(dp(context, 24));
        notice.setBackground(new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{glass, sheen}));
        if (mode.icon() != 0) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(mode.icon()); icon.setColorFilter(cyan);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            notice.addView(icon, new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)));
        } else {
            TextView icon = label(context, mode.symbol, 18, cyan);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            icon.setShadowLayer(dp(context, 6), 0, 0, 0x600892D0);
            notice.addView(icon);
        }
        TextView title = label(context, mode.label + (shittok && mode != ContentPreference.ALL ? " · prioritized" : ""), 14, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(2);
        if (shittok) title.setMaxWidth(Math.max(dp(context, 100),
                (int) (context.getResources().getDisplayMetrics().widthPixels * .8f) - dp(context, 80)));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-2, -2);
        titleParams.setMarginStart(dp(context, 8));
        notice.addView(title, titleParams);
        // Keep the finite confirmation clear of the creator title and gallery controls.
        FrameLayout.LayoutParams placement = new FrameLayout.LayoutParams(-2, -2,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        placement.topMargin = dp(context, 88);
        surface.addView(notice, placement);
        notice.start(glass, sheen);
        notice.announceForAccessibility(mode.label + (shittok && mode != ContentPreference.ALL
                ? ". Matching OnlyFap content will be prioritized" : ""));
    }

    /** One finite glass bloom, with no full-screen scrim or touch interception. */
    @android.annotation.SuppressLint("ViewConstructor") // Programmatic transient notice, never inflated.
    private static final class PreferenceNotice extends LinearLayout {
        private android.animation.ValueAnimator bloom;
        private final Runnable dismiss = () -> {
            if (getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) getParent()).removeView(this);
        };
        PreferenceNotice(Context context) { super(context); }
        void start(android.graphics.drawable.GradientDrawable glass, android.graphics.drawable.GradientDrawable sheen) {
            if (!ZeroChillMotion.animationsEnabled(getContext())) { postDelayed(dismiss, 500); return; }
            setAlpha(0f);
            bloom = android.animation.ValueAnimator.ofFloat(0f, 1f);
            bloom.setDuration(500L);
            bloom.addUpdateListener(animation -> {
                float p = (float) animation.getAnimatedValue();
                float form = Math.min(1f, p / .3f);
                setScaleX(.76f + .24f * form);
                setScaleY(.88f + .12f * form);
                setAlpha(Math.min(1f, p / .16f) * Math.min(1f, (1f - p) / .28f));
                glass.setCornerRadius(dp(getContext(), 24) + dp(getContext(), 20) * (1f - form));
                sheen.setAlpha((int) (180 + 75 * Math.sin(Math.PI * p)));
            });
            bloom.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) { dismiss.run(); }
            });
            bloom.start();
        }
        @Override protected void onDetachedFromWindow() {
            removeCallbacks(dismiss);
            if (bloom != null) { bloom.removeAllListeners(); bloom.removeAllUpdateListeners(); bloom.cancel(); bloom = null; }
            super.onDetachedFromWindow();
        }
    }
    private static TextView label(Context context, String text, int size, int color) {
        TextView view = new TextView(context);
        view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        return view;
    }
    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
