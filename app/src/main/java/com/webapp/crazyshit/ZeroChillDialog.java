package com.webapp.crazyshit;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.TextView;

/** Shared app-owned dialog shell. Keeps AlertDialog's button, list, and listener contract. */
final class ZeroChillDialog {
    private ZeroChillDialog() { }

    static final class Builder extends AlertDialog.Builder {
        private final Context context;
        private boolean destructive;

        Builder(Context context) {
            super(context, R.style.Theme_ZeroChill_Dialog);
            this.context = context;
        }

        Builder destructive() { destructive = true; return this; }

        @Override public Builder setPositiveButton(CharSequence label, DialogInterface.OnClickListener action) {
            if (destructive && label != null) {
                SpannableString colored = new SpannableString(label);
                colored.setSpan(new ForegroundColorSpan(0xFFFF9C9C), 0, colored.length(), 0);
                super.setPositiveButton(colored, action);
            } else super.setPositiveButton(label, action);
            return this;
        }

        @Override public AlertDialog create() {
            AlertDialog dialog = super.create();
            ZeroChillToast.registerDialog(dialog);
            return dialog;
        }

        @Override public Builder setTitle(CharSequence title) {
            TextView heading = new TextView(context);
            heading.setText(title);
            heading.setTextColor(ZeroChillUi.color(context, R.color.zc_text_primary));
            heading.setTextSize(20);
            heading.setTypeface(null, Typeface.BOLD);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            int horizontal = ZeroChillUi.dimension(context, R.dimen.zc_space_xl);
            int vertical = ZeroChillUi.dimension(context, R.dimen.zc_space_lg);
            heading.setPadding(horizontal, vertical, horizontal, 0);
            super.setCustomTitle(heading);
            return this;
        }

        @Override public Builder setTitle(int titleId) {
            return setTitle(context.getText(titleId));
        }
    }
}
