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
        selector.setText((mode.symbol.isEmpty() ? "" : mode.symbol + "  ") + mode.label + "  ▾");
        selector.setTextColor(Color.WHITE);
        selector.setTypeface(null, weight(mode));
        android.graphics.drawable.Drawable icon = mode.icon() == 0 ? null
                : androidx.core.content.ContextCompat.getDrawable(context, mode.icon());
        if (icon != null) {
            icon = icon.mutate(); icon.setTint(ZeroChillUi.color(context, R.color.zc_cyan));
            icon.setBounds(0, 0, dp(context, 22), dp(context, 22));
        }
        selector.setCompoundDrawablesRelative(icon, null, null, null);
        selector.setCompoundDrawablePadding(dp(context, 7));
        selector.setBackground(glass(context, mode, true, false));
        selector.setContentDescription("Content preference: " + mode.label);
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

    /** Shared smoked glass, cyan rim and inset sheen, following navigation/search materials. */
    private static android.graphics.drawable.Drawable glass(Context context, ContentPreference mode,
            boolean selected, boolean panel) {
        float radius = dp(context, panel ? 28 : mode == ContentPreference.MEN
                || mode == ContentPreference.TRANS_MEN ? 20 : 28);
        int cyan = ZeroChillUi.color(context, R.color.zc_cyan);
        android.graphics.drawable.GradientDrawable body = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{panel ? 0xF0182029 : 0xDD18222C, panel ? 0xF2080C12 : 0xE0070C12});
        body.setCornerRadius(radius);
        body.setStroke(dp(context, 1), selected ? androidx.core.graphics.ColorUtils.setAlphaComponent(cyan, 150) : 0x30FFFFFF);
        android.graphics.drawable.GradientDrawable sheen = new android.graphics.drawable.GradientDrawable(
                mode == ContentPreference.NON_BINARY
                        ? android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
                        : android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{selected ? 0x240892D0 : 0x12FFFFFF, 0x01FFFFFF});
        sheen.setCornerRadius(radius);
        sheen.setStroke(dp(context, 1), mode == ContentPreference.TRANS_WOMEN
                || mode == ContentPreference.TRANS_MEN ? 0x36D8F3FF : 0x16FFFFFF);
        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{body, sheen});
        int inset = dp(context, mode == ContentPreference.GROUPS ? 4 : mode == ContentPreference.WOMEN ? 3 : 2);
        layers.setLayerInset(1, inset, dp(context, 2), inset, dp(context, 2));
        return layers;
    }

    static void present(FrameLayout surface, boolean shittok) {
        if (!surface.isShown() || !ContentPreference.acknowledge(surface.getContext(), shittok ? "shittok" : "onlyfap")) return;
        Context context = surface.getContext();
        ContentPreference mode = ContentPreference.get(context);
        LinearLayout overlay = new LinearLayout(context);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setPadding(dp(context, 24), dp(context, 20), dp(context, 24), dp(context, 20));
        overlay.setBackgroundColor(Color.argb(shittok ? 225 : 245, 0, 0, 0));
        // Playback and gestures continue underneath this purely visual presentation.
        overlay.setClickable(false);
        int cyan = ZeroChillUi.color(context, R.color.zc_cyan);
        if (mode.icon() != 0) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(mode.icon());
            icon.setColorFilter(cyan);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            overlay.addView(icon, new LinearLayout.LayoutParams(dp(context, 64), dp(context, 64)));
        } else {
            TextView symbol = label(context, mode.symbol, 58, cyan);
            symbol.setShadowLayer(dp(context, 12), 0, 0, cyan);
            symbol.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            overlay.addView(symbol);
        }
        String heading = mode.label.toUpperCase(java.util.Locale.US)
                + (shittok && mode != ContentPreference.ALL ? " PREFERENCE ACTIVE" : "");
        TextView title = label(context, heading, 23, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        overlay.addView(title);
        String detail = mode == ContentPreference.ALL
                ? "All content restored"
                : shittok ? "Matching OnlyFap content will be prioritized"
                : "OnlyFap filtered to your preference";
        overlay.addView(label(context, detail, 14, ZeroChillUi.color(context, R.color.zc_text_secondary)));
        View previous = surface.findViewWithTag("content_preference_presentation");
        if (previous != null) surface.removeView(previous);
        overlay.setTag("content_preference_presentation");
        surface.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        overlay.setAlpha(0);
        overlay.setScaleX(.94f); overlay.setScaleY(.94f);
        overlay.animate().alpha(1).scaleX(1).scaleY(1).setDuration(180).start();
        overlay.postDelayed(() -> overlay.animate().alpha(0).setDuration(260)
                .withEndAction(() -> surface.removeView(overlay)).start(), 1000);
        overlay.announceForAccessibility(heading + ". " + detail);
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
