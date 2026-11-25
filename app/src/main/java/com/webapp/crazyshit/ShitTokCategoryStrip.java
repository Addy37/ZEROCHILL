package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.EnumMap;

/**
 * Video-overlay ShitTok navigation. Scrollable text preferences are separate from
 * the fixed search/more actions, and this view never consumes feed layout height.
 */
final class ShitTokCategoryStrip extends LinearLayout {
    enum Tab {
        CHAOS("chaos", "Chaos", ContentPreference.ALL),
        LADIES("ladies", "Straight", ContentPreference.WOMEN),
        GUYS("guys", "Gay", ContentPreference.MEN),
        TRANS("trans", "Trans", ContentPreference.TRANS_WOMEN),
        GROUPS("groups", "Groups", ContentPreference.GROUPS);

        final String value, label;
        final ContentPreference preference;
        Tab(String value, String label, ContentPreference preference) {
            this.value = value;
            this.label = label;
            this.preference = preference;
        }
        static Tab selected(Context context) {
            ContentPreference current = ShitTokContentPreference.get(context);
            if (current == ContentPreference.WOMEN) return LADIES;
            if (current == ContentPreference.MEN) return GUYS;
            if (current == ContentPreference.TRANS_MEN ||
                    current == ContentPreference.TRANS_WOMEN) return TRANS;
            if (current == ContentPreference.GROUPS) return GROUPS;
            return CHAOS;
        }
        static boolean select(Context context, Tab tab) {
            if (tab == null) return false;
            // The old selector is retired: clear its stored restriction without
            // changing the separate OnlyFap preference.
            ShitTokSourceMode.set(context, ShitTokSourceMode.ALL);
            return ShitTokContentPreference.set(context, tab.preference);
        }
    }

    private final HorizontalScrollView scroller;
    private final LinearLayout tabs;
    private final EnumMap<Tab, TextView> labels = new EnumMap<>(Tab.class);
    private final EnumMap<Tab, View> indicators = new EnumMap<>(Tab.class);
    private final ImageView search, more;
    private Runnable selectionChanged;
    private Tab displayed;
    private int displayedAccent = Integer.MIN_VALUE;

    ShitTokCategoryStrip(Context context) {
        super(context);
        setTag("shittok_category_strip");
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(9), 0, dp(7), 0);
        setClipChildren(false);
        setClipToPadding(false);
        setContentDescription("ShitTok content feeds");
        // Normalize older test-build selections to the new five-feed model.
        // OnlyFap's own saved gender preference remains untouched.
        ShitTokSourceMode.set(context, ShitTokSourceMode.ALL);
        if (ShitTokContentPreference.get(context) == ContentPreference.TRANS_MEN)
            ShitTokContentPreference.set(context, ContentPreference.TRANS_WOMEN);
        GradientDrawable fade = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99000000, 0x48000000, 0x00000000});
        setBackground(fade);

        scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setOverScrollMode(OVER_SCROLL_NEVER);
        // Center the tabs when they fit, but still allow scrolling on compact phones.
        scroller.setFillViewport(true);
        scroller.setClipToPadding(false);
        tabs = new LinearLayout(context);
        tabs.setGravity(Gravity.CENTER);
        tabs.setOrientation(HORIZONTAL);
        tabs.setPadding(dp(2), 0, dp(2), 0);
        scroller.addView(tabs, new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        addView(scroller, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

        for (Tab option : Tab.values()) {
            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setMinimumHeight(dp(46));
            cell.setMinimumWidth(dp(option.label.length() > 6 ? 68 : 48));
            cell.setPadding(dp(6), 0, dp(6), 0);
            cell.setTag("shittok_category_cell_" + option.value);
            cell.setClickable(true);
            cell.setFocusable(true);
            TextView title = new TextView(context);
            title.setTag("shittok_category_" + option.value);
            title.setText(option.label);
            title.setTextSize(14);
            title.setShadowLayer(dp(2), 0f, dp(1), 0xB9000000);
            title.setSingleLine(true);
            title.setGravity(Gravity.CENTER);
            title.setContentDescription("ShitTok feed: " + option.label);
            title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            cell.addView(title, new LinearLayout.LayoutParams(-2, dp(30)));
            View indicator = new View(context);
            LinearLayout.LayoutParams underline = new LinearLayout.LayoutParams(dp(25), dp(3));
            underline.topMargin = dp(1);
            cell.addView(indicator, underline);
            labels.put(option, title);
            indicators.put(option, indicator);
            ZeroChillMotion.installPressFeedback(cell);
            cell.setOnClickListener(v -> {
                if (Tab.select(getContext(), option)) {
                    refresh(true);
                    if (selectionChanged != null) selectionChanged.run();
                }
            });
            tabs.addView(cell, new LinearLayout.LayoutParams(-2, LayoutParams.MATCH_PARENT));
        }
        search = action(context, R.drawable.ic_nav_search, "Search ShitTok", "shittok_overlay_search");
        more = action(context, R.drawable.ic_more_overflow, "More options", "shittok_overlay_more");
        addView(search, new LinearLayout.LayoutParams(dp(40), dp(48)));
        addView(more, new LinearLayout.LayoutParams(dp(40), dp(48)));
        refresh(false);
    }

    private static ImageView action(Context context, int icon, String description, String tag) {
        ImageView view = new ImageView(context);
        view.setImageResource(icon);
        view.setPadding(dp(context, 9), dp(context, 9), dp(context, 9), dp(context, 9));
        view.setColorFilter(Color.WHITE);
        view.setContentDescription(description);
        view.setTag(tag);
        view.setClickable(true);
        view.setFocusable(true);
        ZeroChillMotion.installPressFeedback(view);
        return view;
    }

    void setOnSearchClickListener(View.OnClickListener listener) { search.setOnClickListener(listener); }
    void setOnMoreClickListener(View.OnClickListener listener) { more.setOnClickListener(listener); }
    void setOnSelectionChanged(Runnable listener) { selectionChanged = listener; }

    void refresh(boolean animate) {
        Tab current = Tab.selected(getContext());
        int accent = AppearanceTheme.accent(getContext());
        if (displayed == current && displayedAccent == accent) return;
        displayed = current;
        displayedAccent = accent;
        for (Tab option : Tab.values()) {
            boolean selected = option == current;
            TextView title = labels.get(option);
            title.setTextColor(selected ? Color.WHITE : 0xFFD5D8DE);
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
            cell.setContentDescription(title.getContentDescription()
                    + (selected ? ", selected" : ""));
        }
        TextView active = labels.get(current);
        if (active != null) post(() -> {
            if (displayed != current || !isAttachedToWindow()) return;
            View cell = (View) active.getParent();
            int x = Math.max(0, cell.getLeft() - (scroller.getWidth() - cell.getWidth()) / 2);
            if (animate && ZeroChillMotion.animationsEnabled(getContext())) scroller.smoothScrollTo(x, 0);
            else scroller.scrollTo(x, 0);
        });
    }

    private int dp(int value) { return dp(getContext(), value); }
    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }
}
