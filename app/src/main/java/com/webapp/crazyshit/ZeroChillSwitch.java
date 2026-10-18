package com.webapp.crazyshit;

import android.content.Context;
import android.widget.Switch;

/** Native accessible switch behavior with ZEROCHILL track and thumb. */
final class ZeroChillSwitch extends Switch {
    ZeroChillSwitch(Context context) {
        super(context);
        setShowText(false);
        setThumbDrawable(context.getDrawable(R.drawable.zc_switch_thumb));
        setTrackDrawable(context.getDrawable(R.drawable.zc_switch_track));
        setThumbTintList(null);
        setTrackTintList(null);
        setSwitchMinWidth(dp(48));
        setMinimumHeight(dp(48));
    }
    private int dp(int amount) {
        return Math.round(amount * getResources().getDisplayMetrics().density);
    }
}
