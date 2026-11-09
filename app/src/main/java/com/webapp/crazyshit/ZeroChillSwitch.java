package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.Switch;

/** Native accessible switch behavior with ZEROCHILL track and thumb. */
final class ZeroChillSwitch extends Switch {
    ZeroChillSwitch(Context context) {
        this(context, false);
    }

    ZeroChillSwitch(Context context, boolean compactSettings) {
        super(context);
        setShowText(false);
        setThumbDrawable(context.getDrawable(compactSettings ? R.drawable.zc_settings_switch_thumb : R.drawable.zc_switch_thumb));
        setTrackDrawable(context.getDrawable(compactSettings ? R.drawable.zc_settings_switch_track : R.drawable.zc_switch_track));
        setThumbTintList(null);
        setTrackTintList(null);
        // XML switches are cyan-only. Keep their exact OG look as the default,
        // but use equivalent-size state drawables for other global themes.
        if (AppearanceTheme.accentChoice(context) != AppearanceTheme.Accent.CYAN
                || AppearanceTheme.isLight(context)) {
            int width = compactSettings ? 44 : 48;
            int height = compactSettings ? 24 : 28;
            StateListDrawable track = new StateListDrawable();
            GradientDrawable checked = new GradientDrawable();
            checked.setColor(AppearanceTheme.accent(context));
            checked.setCornerRadius(dp(height / 2));
            checked.setSize(dp(width), dp(height));
            GradientDrawable unchecked = new GradientDrawable();
            unchecked.setColor(AppearanceTheme.raised(context));
            unchecked.setCornerRadius(dp(height / 2));
            unchecked.setStroke(dp(1), AppearanceTheme.outline(context));
            unchecked.setSize(dp(width), dp(height));
            track.addState(new int[]{android.R.attr.state_checked}, checked);
            track.addState(new int[]{}, unchecked);
            setTrackDrawable(track);
            StateListDrawable thumb = new StateListDrawable();
            GradientDrawable thumbOn = new GradientDrawable();
            thumbOn.setShape(GradientDrawable.OVAL);
            thumbOn.setColor(AppearanceTheme.onAccent(context));
            thumbOn.setSize(dp(compactSettings ? 18 : 20), dp(compactSettings ? 18 : 20));
            GradientDrawable thumbOff = new GradientDrawable();
            thumbOff.setShape(GradientDrawable.OVAL);
            thumbOff.setColor(AppearanceTheme.primaryText(context));
            thumbOff.setSize(dp(compactSettings ? 18 : 20), dp(compactSettings ? 18 : 20));
            thumb.addState(new int[]{android.R.attr.state_checked}, thumbOn);
            thumb.addState(new int[]{}, thumbOff);
            setThumbDrawable(thumb);
        }
        setSwitchMinWidth(dp(compactSettings ? 44 : 48));
        setMinimumHeight(dp(48));
    }
    private int dp(int amount) {
        return Math.round(amount * getResources().getDisplayMetrics().density);
    }
}
