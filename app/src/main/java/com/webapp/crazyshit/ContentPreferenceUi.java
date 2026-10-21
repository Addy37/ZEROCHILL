package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared native picker and non-intercepting, one-time mode presentation. */
final class ContentPreferenceUi {
    static void choose(Context context, Runnable changed) {
        ContentPreference[] options = ContentPreference.values();
        String[] labels = new String[options.length];
        for (int i = 0; i < options.length; i++) labels[i] = options[i].label;
        new ZeroChillDialog.Builder(context).setTitle("Content preference")
                .setSingleChoiceItems(labels, ContentPreference.get(context).ordinal(), (dialog, which) -> {
                    boolean actualChange = ContentPreference.set(context, options[which]);
                    dialog.dismiss();
                    if (actualChange && changed != null) changed.run();
                }).setNegativeButton("Cancel", null).show();
    }

    static void bindSelector(TextView selector) {
        Context context = selector.getContext();
        ContentPreference mode = ContentPreference.get(context);
        selector.setText((mode.symbol.isEmpty() ? "" : mode.symbol + "  ") + mode.label + "  ▾");
        selector.setCompoundDrawablesWithIntrinsicBounds(mode.icon(), 0, 0, 0);
        selector.setCompoundDrawablePadding(dp(context, 6));
        selector.setContentDescription("Content preference: " + mode.label);
    }

    static void present(FrameLayout surface, boolean shittok) {
        if (!surface.isShown() || !ContentPreference.acknowledge(surface.getContext(), shittok ? "shittok" : "onlyfap")) return;
        Context context = surface.getContext();
        ContentPreference mode = ContentPreference.get(context);
        LinearLayout overlay = new LinearLayout(context);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setPadding(dp(context, 24), dp(context, 20), dp(context, 24), dp(context, 20));
        overlay.setBackgroundColor(Color.argb(shittok ? 225 : 245, 0, 0, 0));
        // Playback and gestures continue underneath this purely visual presentation.
        overlay.setClickable(false);
        int cyan = ZeroChillUi.color(context, R.color.zc_cyan);
        if (mode.icon() != 0) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(mode.icon());
            icon.setColorFilter(cyan);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            overlay.addView(icon, new LinearLayout.LayoutParams(dp(context, 64), dp(context, 64)));
        } else {
            TextView symbol = label(context, mode.symbol, 58, cyan);
            symbol.setShadowLayer(dp(context, 12), 0, 0, cyan);
            symbol.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            overlay.addView(symbol);
        }
        String heading = mode.label.toUpperCase(java.util.Locale.US)
                + (shittok && mode != ContentPreference.ALL ? " PREFERENCE ACTIVE" : "");
        TextView title = label(context, heading, 23, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        overlay.addView(title);
        String detail = mode == ContentPreference.ALL
                ? "All content restored"
                : shittok ? "Matching OnlyFap content will be prioritized"
                : "OnlyFap filtered to your preference";
        overlay.addView(label(context, detail, 14, ZeroChillUi.color(context, R.color.zc_text_secondary)));
        View previous = surface.findViewWithTag("content_preference_presentation");
        if (previous != null) surface.removeView(previous);
        overlay.setTag("content_preference_presentation");
        surface.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        overlay.setAlpha(0);
        overlay.setScaleX(.94f); overlay.setScaleY(.94f);
        overlay.animate().alpha(1).scaleX(1).scaleY(1).setDuration(180).start();
        overlay.postDelayed(() -> overlay.animate().alpha(0).setDuration(260)
                .withEndAction(() -> surface.removeView(overlay)).start(), 1000);
        overlay.announceForAccessibility(heading + ". " + detail);
    }
    private static TextView label(Context context, String text, int size, int color) {
        TextView view = new TextView(context);
        view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        return view;
    }
    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
