package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.os.Build;
import android.widget.EditText;

/** Shared visible field appearance; keeps Android input, selection, and Autofill behavior. */
final class ZeroChillEditText extends EditText {
    private CharSequence errorText;
    private Drawable regularBackground;
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
        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (errorText != null) setError(null);
            }
            @Override public void afterTextChanged(Editable text) { }
        });
    }

    @Override public void setError(CharSequence error) {
        // Avoid the platform error bubble while retaining the public error state.
        super.setError(null);
        errorText = error;
        if (error == null || error.length() == 0) {
            if (regularBackground != null) setBackground(regularBackground);
            regularBackground = null;
            if (Build.VERSION.SDK_INT >= 30) setStateDescription(null);
            return;
        }
        if (regularBackground == null) regularBackground = getBackground();
        setBackgroundResource(R.drawable.zc_input_error);
        if (Build.VERSION.SDK_INT >= 30) setStateDescription("Error: " + error);
        announceForAccessibility(error);
        ZeroChillToast.makeText(getContext(), error, ZeroChillToast.LENGTH_LONG).show();
    }

    @Override public CharSequence getError() { return errorText; }
}
