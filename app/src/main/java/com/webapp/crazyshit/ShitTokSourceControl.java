package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Stationary glass capsule. Samples only the pager, never itself or Focus controls. */
final class ShitTokSourceControl extends FrameLayout {
    private final Activity activity;
    private final View source;
    private final ViewPageBackdrop page;
    private final FrameLayout controls;
    private final View selection;
    private final TextView[] labels = new TextView[2];
    private boolean dirty = true, installed;
    private ShitTokSourceMode displayed;

    ShitTokSourceControl(Activity activity, View source) {
        super(activity);
        this.activity = activity; this.source = source;
        page = new ViewPageBackdrop(source);
        setTag("shittok_source_control");
        controls = new FrameLayout(activity);
        controls.setPadding(dp(4), dp(4), dp(4), dp(4));
        GradientDrawable film = new GradientDrawable();
        film.setColor(0x88202A32); film.setCornerRadius(dp(24));
        film.setStroke(dp(1), 0x40CCDDE8); setBackground(film);
        selection = new View(activity);
        selection.setBackgroundResource(R.drawable.zc_nav_selected_glass);
        controls.addView(selection, new LayoutParams(dp(82), -1));
        LinearLayout row = new LinearLayout(activity);
        for (ShitTokSourceMode mode : ShitTokSourceMode.values()) {
            TextView label = new TextView(activity);
            labels[mode.ordinal()] = label;
            label.setText(mode.label); label.setTextSize(13); label.setGravity(Gravity.CENTER);
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
        if (displayed == mode) return;
        displayed = mode;
        for (int i = 0; i < labels.length; i++) {
            labels[i].setTextColor(i == mode.ordinal() ? Color.rgb(0, 229, 255) : 0xFFE0E7ED);
            labels[i].setSelected(i == mode.ordinal());
        }
        selection.animate().cancel();
        float x = dp(82) * mode.ordinal();
        if (animate) selection.animate().translationX(x)
                .setDuration(ZeroChillMotion.duration(activity, ZeroChillMotion.STANDARD_MS))
                .setInterpolator(ZeroChillMotion.EASE_OUT).start();
        else selection.setTranslationX(x);
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        View ancestor = this;
        while (ancestor != null && !(ancestor instanceof FrostedNavigationLayout))
            ancestor = ancestor.getParent() instanceof View ? (View) ancestor.getParent() : null;
        if (!installed && ancestor != null) {
            installed = true; removeView(controls); setBackgroundColor(Color.TRANSPARENT);
            GlassHeaderView glass = new GlassHeaderView(activity, controls, 24f, false, false, true, 0f);
            glass.setPageBackdrop(page); addView(glass, new LayoutParams(-1, -1));
        }
    }
    void invalidateBackdrop() { dirty = true; if (getVisibility() == VISIBLE) invalidate(); }
    @Override protected void dispatchDraw(Canvas canvas) {
        if (dirty && canvas.isHardwareAccelerated() && source.getWidth() > 0 && source.getHeight() > 0) {
            dirty = false;
            page.record(source.getWidth(), source.getHeight(), recording -> { source.draw(recording); return kotlin.Unit.INSTANCE; });
        }
        super.dispatchDraw(canvas);
    }
    @Override protected void onDetachedFromWindow() {
        selection.animate().cancel(); page.release(); dirty = true;
        super.onDetachedFromWindow();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
