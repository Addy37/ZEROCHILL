package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Source selector inside the shared glass header. */
final class ShitTokSourceControl extends FrameLayout {
    private final Activity activity;
    private final FrameLayout controls;
    private final View selection;
    private final TextView[] labels = new TextView[2];
    private ShitTokSourceMode displayed;
    private int displayedAccent = Integer.MIN_VALUE;

    ShitTokSourceControl(Activity activity) {
        super(activity);
        this.activity = activity;
        setTag("shittok_source_control");
        controls = new FrameLayout(activity);
        controls.setPadding(dp(4), dp(4), dp(4), dp(4));
        GradientDrawable film = new GradientDrawable();
        film.setColor(0x88202A32); film.setCornerRadius(dp(24));
        film.setStroke(dp(1), 0x40CCDDE8); setBackground(film);
        selection = new View(activity);
        selection.setBackgroundResource(R.drawable.zc_nav_selected_glass);
        controls.addView(selection, new LayoutParams(dp(56), -1));
        LinearLayout row = new LinearLayout(activity);
        for (ShitTokSourceMode mode : ShitTokSourceMode.values()) {
            TextView label = new TextView(activity);
            labels[mode.ordinal()] = label;
            label.setText(mode.label); label.setTextSize(12); label.setGravity(Gravity.CENTER);
            label.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            label.setContentDescription("ShitTok sources: " + mode.label);
            label.setTag("shittok_source_" + mode.value);
            label.setFocusable(true);
            label.setOnClickListener(v -> { ShitTokSourceMode.set(activity, mode); refresh(true); });
            ZeroChillMotion.installPressFeedback(label);
            row.addView(label, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        controls.addView(row, new LayoutParams(-1, -1));
        addView(controls, new LayoutParams(-1, -1));
        refresh(false);
    }
    void refresh(boolean animate) {
        ShitTokSourceMode mode = ShitTokSourceMode.get(activity);
        int accent = AppearanceTheme.accent(activity);
        if (displayed == mode && displayedAccent == accent) return;
        displayed = mode;
        displayedAccent = accent;
        for (int i = 0; i < labels.length; i++) {
            labels[i].setTextColor(i == mode.ordinal() ? accent : 0xFFE0E7ED);
            labels[i].setSelected(i == mode.ordinal());
        }
        selection.animate().cancel();
        float x = dp(56) * mode.ordinal();
        if (animate) selection.animate().translationX(x)
                .setDuration(ZeroChillMotion.duration(activity, ZeroChillMotion.STANDARD_MS))
                .setInterpolator(ZeroChillMotion.EASE_OUT).start();
        else selection.setTranslationX(x);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
