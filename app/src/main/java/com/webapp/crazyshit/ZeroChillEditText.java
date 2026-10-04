package com.webapp.crazyshit;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.EditText;

/** Shared visible field appearance; keeps Android input, selection, and Autofill behavior. */
final class ZeroChillEditText extends EditText {
    ZeroChillEditText(Context context) {
        super(context);
        setBackground(context.getDrawable(R.drawable.zc_input_field));
        setTextColor(ZeroChillUi.color(context, R.color.zc_text_primary));
        setHintTextColor(ZeroChillUi.color(context, R.color.zc_text_muted));
        setHighlightColor(ZeroChillUi.color(context, R.color.zc_cyan_container));
        setBackgroundTintList(null);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            setTextCursorDrawable(context.getDrawable(R.drawable.zc_text_cursor));
        }
        int inset = Math.round(12 * getResources().getDisplayMetrics().density);
        setPadding(inset, inset, inset, inset);
    }
}
