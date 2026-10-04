package com.webapp.crazyshit;

import android.content.Context;

import com.google.android.material.checkbox.MaterialCheckBox;

/** Accessible checkbox behavior with the app's cyan selection mark. */
final class ZeroChillCheckBox extends MaterialCheckBox {
    ZeroChillCheckBox(Context context) {
        super(context);
        setUseMaterialThemeColors(false);
        setButtonTintList(null);
        setButtonDrawable(R.drawable.zc_checkbox);
        setMinHeight(Math.round(48 * getResources().getDisplayMetrics().density));
    }
}
