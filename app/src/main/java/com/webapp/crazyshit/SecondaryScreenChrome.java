package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

/** Presentation only. Keep each screen's inset owner, listeners and selection state. */
final class SecondaryScreenChrome {
    private SecondaryScreenChrome() {}

    static void header(LinearLayout row, TextView title, boolean conversation) {
        Context context = row.getContext();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 8), 0, dp(context, 8), 0);
        row.setMinimumHeight(dp(context, 56));
        row.setBackgroundColor(Color.TRANSPARENT);
        title.setTextSize(conversation ? 17 : 24);
        title.setTextColor(AppearanceTheme.primaryText(context));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(conversation ? 1 : 2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(0, 0, 0, 0);
        androidx.core.view.ViewCompat.setAccessibilityHeading(title, true);
    }

    static void back(TextView view, boolean selecting) {
        icon(view, selecting ? R.drawable.ic_more_close : R.drawable.ic_player_back,
                selecting ? "Clear message selection" : "Back", false);
    }

    static void icon(TextView view, int res, String description, boolean accent) {
        Context context = view.getContext();
        view.setText("");
        view.setBackgroundColor(Color.TRANSPARENT);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        view.setMinimumWidth(dp(context, 48));
        view.setMinimumHeight(dp(context, 48));
        Drawable glyph = ContextCompat.getDrawable(context, res);
        if (glyph != null) {
            glyph = glyph.mutate();
            glyph.setTint(accent ? AppearanceTheme.accent(context) : AppearanceTheme.primaryText(context));
            glyph.setBounds(0, 0, dp(context, 24), dp(context, 24));
        }
        view.setCompoundDrawables(glyph, null, null, null);
        view.setContentDescription(description);
        view.setFocusable(true);
    }

    static void quietAction(TextView view) {
        Context context = view.getContext();
        view.setBackgroundColor(Color.TRANSPARENT);
        view.setTextColor(AppearanceTheme.accent(context));
        view.setGravity(Gravity.CENTER);
        view.setMinimumHeight(dp(context, 48));
    }

    private static int dp(Context context, int value) { return BrowseUi.dp(context, value); }
}
