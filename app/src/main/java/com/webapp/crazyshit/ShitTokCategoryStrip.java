package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.EnumMap;

/** Scrollable, theme-aware ShitTok categories beneath the existing source header. */
final class ShitTokCategoryStrip extends HorizontalScrollView {
    private final LinearLayout tabs;
    private final EnumMap<ContentPreference, TextView> labels =
            new EnumMap<>(ContentPreference.class);
    private final EnumMap<ContentPreference, View> indicators =
            new EnumMap<>(ContentPreference.class);
    private Runnable selectionChanged;
    private ContentPreference displayed;
    private int displayedAccent = Integer.MIN_VALUE;

    ShitTokCategoryStrip(Context context) {
        super(context);
        setTag("shittok_category_strip");
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setFillViewport(false);
        setBackgroundColor(ZeroChillUi.background(context));
        setContentDescription("ShitTok content categories");
        tabs = new LinearLayout(context);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(8), 0, dp(8), 0);
        addView(tabs, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        for (ContentPreference option : ContentPreference.values()) {
            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setMinimumHeight(dp(38));
            cell.setPadding(dp(10), 0, dp(10), 0);
            cell.setTag("shittok_category_cell_" + option.value);
            cell.setClickable(true);
            cell.setFocusable(true);

            TextView title = new TextView(context);
            title.setTag("shittok_category_" + option.value);
            title.setText(option == ContentPreference.ALL ? "For You" : option.label);
            title.setTextSize(13);
            title.setSingleLine(true);
            title.setGravity(Gravity.CENTER);
            title.setContentDescription("ShitTok category: " + title.getText());
            title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            cell.addView(title, new LinearLayout.LayoutParams(-2, dp(27)));

            View indicator = new View(context);
            LinearLayout.LayoutParams underline =
                    new LinearLayout.LayoutParams(dp(28), dp(3));
            underline.topMargin = dp(2);
            cell.addView(indicator, underline);
            labels.put(option, title);
            indicators.put(option, indicator);
            ZeroChillMotion.installPressFeedback(cell);
            cell.setOnClickListener(v -> {
                if (ShitTokContentPreference.set(getContext(), option)) {
                    refresh(true);
                    if (selectionChanged != null) selectionChanged.run();
                }
            });
            tabs.addView(cell, new LinearLayout.LayoutParams(-2, -1));
        }
        refresh(false);
    }

    void setOnSelectionChanged(Runnable listener) { selectionChanged = listener; }

    void refresh(boolean animate) {
        ContentPreference current = ShitTokContentPreference.get(getContext());
        int accent = AppearanceTheme.accent(getContext());
        if (displayed == current && displayedAccent == accent) return;
        displayed = current;
        displayedAccent = accent;
        for (ContentPreference option : ContentPreference.values()) {
            boolean selected = option == current;
            TextView title = labels.get(option);
            title.setTextColor(selected ? Color.WHITE : 0xFF9FA8B4);
            title.setTypeface(null, selected ? android.graphics.Typeface.BOLD
                    : android.graphics.Typeface.NORMAL);
            title.setSelected(selected);
            View indicator = indicators.get(option);
            if (selected) {
                GradientDrawable line = new GradientDrawable();
                line.setColor(accent);
                line.setCornerRadius(dp(2));
                indicator.setBackground(line);
            }
            indicator.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            View cell = (View) title.getParent();
            cell.setSelected(selected);
            cell.setContentDescription(title.getContentDescription() + (selected ? ", selected" : ""));
        }
        TextView active = labels.get(current);
        if (active != null) {
            post(() -> {
                if (displayed != current || !isAttachedToWindow()) return;
                int x = Math.max(0, ((View) active.getParent()).getLeft()
                        - (getWidth() - ((View) active.getParent()).getWidth()) / 2);
                if (animate && ZeroChillMotion.animationsEnabled(getContext())) smoothScrollTo(x, 0);
                else scrollTo(x, 0);
            });
        }
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
