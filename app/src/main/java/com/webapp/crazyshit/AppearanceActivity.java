package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Compact appearance studio, backed by the same settings store as the working OG app. */
public final class AppearanceActivity extends Activity {
    private ScrollView scroll;
    private TextView glassSample;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        render(0);
    }

    @Override protected void onResume() {
        super.onResume();
        ZeroChillUi.applySystemBars(this);
    }

    private void render(int retainedScrollY) {
        ZeroChillUi.applySystemBars(this);
        int background = AppearanceTheme.background(this);
        int ink = AppearanceTheme.primaryText(this);
        int secondary = AppearanceTheme.secondaryText(this);
        int accent = AppearanceTheme.accent(this);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(background);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            Insets system = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            scroll.setPadding(0, system.top, 0, system.bottom);
            return insets;
        });

        LinearLayout body = column();
        body.setPadding(dp(16), dp(8), dp(16), dp(30));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = row();
        TextView back = text("‹", 30, accent, true);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(48)));
        LinearLayout headings = column();
        headings.addView(text("Appearance", 24, ink, true));
        headings.addView(text("ZEROCHILL 7.0", 11, secondary, false));
        LinearLayout.LayoutParams headerText = new LinearLayout.LayoutParams(0, -2, 1);
        headerText.leftMargin = dp(8);
        header.addView(headings, headerText);
        body.addView(header);
        spacer(body, 20);

        LinearLayout colors = card(body);
        label(colors, "Colors", ink);
        supporting(colors, "Theme", secondary);
        AppearanceTheme.Mode[] modes = AppearanceTheme.Mode.values();
        for (int start = 0; start < modes.length; start += 2) {
            LinearLayout choices = row();
            for (int i = start; i < start + 2 && i < modes.length; i++) {
                final AppearanceTheme.Mode mode = modes[i];
                TextView chip = option(mode.label, mode == AppearanceTheme.mode(this));
                chip.setOnClickListener(view -> {
                    AppearanceTheme.setMode(this, mode);
                    render(scroll.getScrollY());
                });
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1);
                if (i > start) p.leftMargin = dp(8);
                choices.addView(chip, p);
            }
            colors.addView(choices);
            spacer(colors, 8);
        }

        supporting(colors, "Accent", secondary);
        LinearLayout swatches = row();
        for (AppearanceTheme.Accent choice : AppearanceTheme.Accent.values()) {
            FrameLayout outer = new FrameLayout(this);
            int stroke = choice == AppearanceTheme.accentChoice(this) ? accent : AppearanceTheme.outline(this);
            outer.setBackground(shape(AppearanceTheme.raised(this), stroke, 14));
            outer.setContentDescription(choice.label + (choice == AppearanceTheme.accentChoice(this) ? ", selected" : ""));
            outer.setClickable(true);
            outer.setFocusable(true);
            outer.setOnClickListener(view -> {
                AppearanceTheme.setAccent(this, choice);
                render(scroll.getScrollY());
            });
            View dot = new View(this);
            dot.setBackground(shape(choice.color, choice == AppearanceTheme.Accent.WHITE ? 0xFF818C98 : choice.color, 50));
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER);
            outer.addView(dot, dotParams);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(45), 1);
            if (choice != AppearanceTheme.Accent.CYAN) p.leftMargin = dp(6);
            swatches.addView(outer, p);
        }
        colors.addView(swatches);
        spacer(colors, 12);
        preview(colors);

        spacer(body, 12);
        LinearLayout glass = card(body);
        label(glass, "Glass", ink);
        supporting(glass, "Adjust the existing liquid-glass effects without replacing them.", secondary);
        slider(glass, "Glass intensity", AppearanceTheme.glassIntensity(this), true);
        slider(glass, "Menu tint", AppearanceTheme.menuTint(this), false);

        spacer(body, 12);
        LinearLayout motion = card(body);
        label(motion, "Motion", ink);
        LinearLayout motionRow = row();
        LinearLayout description = column();
        description.addView(text("Animations", 15, ink, true));
        description.addView(text("Respect Android's reduced-motion setting", 12, secondary, false));
        motionRow.addView(description, new LinearLayout.LayoutParams(0, -2, 1));
        Switch enabled = new Switch(this);
        enabled.setButtonTintList(null);
        enabled.setThumbTintList(ColorStateList.valueOf(accent));
        enabled.setChecked(AppearanceTheme.prefs(this).getBoolean("immersive_motion_enabled", true));
        enabled.setOnCheckedChangeListener((button, value) ->
                AppearanceTheme.prefs(this).edit().putBoolean("immersive_motion_enabled", value).apply());
        motionRow.addView(enabled, new LinearLayout.LayoutParams(dp(54), dp(48)));
        motion.addView(motionRow);

        spacer(body, 18);
        TextView reset = text("Reset appearance to defaults", 13, accent, true);
        reset.setGravity(Gravity.CENTER);
        reset.setMinHeight(dp(48));
        reset.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle("Reset appearance?")
                .setMessage("Restore OLED black, cyan and default glass settings? Your saved content stays untouched.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (dialog, which) -> {
                    AppearanceTheme.reset(this);
                    render(0);
                }).show());
        body.addView(reset);
        setContentView(scroll);
        scroll.post(() -> scroll.scrollTo(0, retainedScrollY));
    }

    private void preview(LinearLayout parent) {
        LinearLayout surface = column();
        surface.setPadding(dp(12), dp(12), dp(12), dp(12));
        surface.setBackground(shape(AppearanceTheme.raised(this), AppearanceTheme.outline(this), 16));
        TextView title = text("ZEROCHILL", 15, AppearanceTheme.primaryText(this), true);
        surface.addView(title);
        spacer(surface, 10);
        LinearLayout capsule = row();
        capsule.setPadding(dp(10), dp(9), dp(10), dp(9));
        capsule.setBackground(shape(AppearanceTheme.surface(this), AppearanceTheme.outline(this), 18));
        TextView active = text("Shows", 12, AppearanceTheme.accent(this), true);
        capsule.addView(active, new LinearLayout.LayoutParams(0, -2, 1));
        for (String tab : new String[] {"ShitTok", "OnlyFap", "Library"}) {
            TextView other = text(tab, 12, AppearanceTheme.secondaryText(this), false);
            capsule.addView(other, new LinearLayout.LayoutParams(0, -2, 1));
        }
        surface.addView(capsule);
        spacer(surface, 9);
        TextView action = text("Sample glass control", 13, AppearanceTheme.primaryText(this), false);
        action.setPadding(dp(13), dp(11), dp(13), dp(11));
        glassSample = action;
        updateGlassSample();
        surface.addView(action);
        parent.addView(surface);
    }

    /** Immediate optical/tint preview; actual page refraction comes from OG's glass engine. */
    private void updateGlassSample() {
        if (glassSample == null) return;
        float intensity = AppearanceTheme.glassIntensity(this);
        float tint = AppearanceTheme.menuTint(this);
        int background = AppearanceTheme.surface(this);
        int accent = AppearanceTheme.accent(this);
        int alpha = Math.round(50 + tint * 130);
        int film = Color.argb(alpha, Color.red(background), Color.green(background), Color.blue(background));
        GradientDrawable surface = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(Math.round(20 + intensity * 50), 255, 255, 255), film, film});
        surface.setCornerRadius(dp(14));
        surface.setStroke(dp(1), Color.argb(Math.round(45 + intensity * 190),
                Color.red(accent), Color.green(accent), Color.blue(accent)));
        glassSample.setBackground(surface);
    }

    private void slider(LinearLayout parent, String title, float value, boolean glassIntensity) {
        LinearLayout titleRow = row();
        titleRow.addView(text(title, 14, AppearanceTheme.primaryText(this), true),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView percent = text(Math.round(value * 100) + "%", 12, AppearanceTheme.secondaryText(this), false);
        titleRow.addView(percent);
        parent.addView(titleRow);
        SeekBar slider = new SeekBar(this);
        slider.setMax(100);
        slider.setProgress(Math.round(value * 100));
        slider.setProgressTintList(ColorStateList.valueOf(AppearanceTheme.accent(this)));
        slider.setThumbTintList(ColorStateList.valueOf(AppearanceTheme.accent(this)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                percent.setText(progress + "%");
                if (!fromUser) return;
                if (glassIntensity) AppearanceTheme.setGlassIntensity(AppearanceActivity.this, progress / 100f);
                else AppearanceTheme.setMenuTint(AppearanceActivity.this, progress / 100f);
                updateGlassSample();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        parent.addView(slider, new LinearLayout.LayoutParams(-1, dp(42)));
    }

    private TextView option(String label, boolean selected) {
        TextView result = text(label, 13, selected ? AppearanceTheme.accent(this) : AppearanceTheme.secondaryText(this), selected);
        result.setGravity(Gravity.CENTER);
        result.setBackground(shape(selected ? AppearanceTheme.raised(this) : AppearanceTheme.surface(this),
                selected ? AppearanceTheme.accent(this) : AppearanceTheme.outline(this), 13));
        return result;
    }

    private LinearLayout card(LinearLayout root) {
        LinearLayout content = column();
        content.setPadding(dp(14), dp(13), dp(14), dp(14));
        content.setBackground(shape(AppearanceTheme.surface(this), AppearanceTheme.outline(this), 19));
        root.addView(content, new LinearLayout.LayoutParams(-1, -2));
        return content;
    }

    private void label(LinearLayout root, String value, int color) {
        root.addView(text(value, 18, color, true));
        spacer(root, 9);
    }

    private void supporting(LinearLayout root, String value, int color) {
        root.addView(text(value, 12, color, false));
        spacer(root, 9);
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout row() {
        LinearLayout view = new LinearLayout(this);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setOrientation(LinearLayout.HORIZONTAL);
        return view;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void spacer(LinearLayout parent, int heightDp) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private GradientDrawable shape(int fill, int edge, int radiusDp) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(fill);
        result.setCornerRadius(dp(radiusDp));
        result.setStroke(dp(1), edge);
        return result;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
