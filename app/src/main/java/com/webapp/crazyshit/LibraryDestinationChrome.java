package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import androidx.core.view.WindowCompat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.google.android.material.card.MaterialCardView;

/**
 * Lightweight, shared visual chrome for the existing Library detail destinations.
 *
 * Only changes presentation. The activities keep ownership of loading, navigation,
 * input listeners, media, selection, permissions, downloads and persistence.
 * Static surfaces deliberately avoid live backdrop captures in scrolling grids.
 */
final class LibraryDestinationChrome {
    private static final int OLED = 0xFF080B10;
    private static final int RAISED = 0xFF141E27;
    private static final int INSET = 0xFF101720;

    private LibraryDestinationChrome() { }

    /**
     * Library detail screens handle system-bar and cutout insets in BrowseUi.screen.
     * Opt out of the framework's extra content fitting to avoid reserving the
     * same status-bar space twice on modern Android versions.
     */
    static void prepareWindow(Activity activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
    }

    static void attachHeader(LinearLayout root, LinearLayout header, TextView title) {
        Context context = root.getContext();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, 0);
        header.setMinimumHeight(dp(context, 52));
        header.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        header.setClipToOutline(false);
        title.setTextSize(24);
        title.setPadding(dp(context, 4), 0, dp(context, 4), 0);
        title.setTextColor(AppearanceTheme.primaryText(context));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        for (int i = 0; i < header.getChildCount(); i++) {
            View child = header.getChildAt(i);
            if (child instanceof TextView && child != title) {
                TextView action = (TextView) child;
                styleAction(action, context);
                if ("Back".contentEquals(action.getContentDescription())) {
                    action.setText("");
                    action.setPadding(dp(context, 12), 0, dp(context, 12), 0);
                    android.graphics.drawable.Drawable arrow = androidx.core.content.ContextCompat
                            .getDrawable(context, R.drawable.ic_player_back).mutate();
                    arrow.setTint(AppearanceTheme.primaryText(context));
                    arrow.setBounds(0, 0, dp(context, 24), dp(context, 24));
                    action.setCompoundDrawables(arrow, null, null, null);
                }
            }
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 4));
        root.addView(header, params);
    }

    static void styleSearch(Context context, EditText input) {
        input.setTextColor(AppearanceTheme.primaryText(context));
        input.setHintTextColor(AppearanceTheme.secondaryText(context));
        input.setTextSize(15);
        input.setBackground(surface(context, 14, 0xFF181D23, 0xFF101216, 0));
        input.setMinHeight(dp(context, 44));
        input.setSingleLine(true);
    }

    static ImageView iconAction(Context context, int drawable, String description, boolean accent,
            View.OnClickListener click) {
        ImageView icon = new ImageView(context);
        icon.setImageResource(drawable);
        icon.setColorFilter(accent ? AppearanceTheme.accent(context) : AppearanceTheme.primaryText(context));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
        icon.setContentDescription(description);
        icon.setFocusable(true);
        icon.setClickable(true);
        icon.setOnClickListener(click);
        ZeroChillMotion.installPressFeedback(icon);
        return icon;
    }

    static SearchController attachInlineSearch(Activity activity, LinearLayout header,
            TextView title, EditText input) {
        return new SearchController(activity, header, title, input);
    }

    static final class SearchController {
        private final Activity activity;
        private final LinearLayout header;
        private final TextView title;
        private final EditText input;
        private final java.util.List<View> actions = new java.util.ArrayList<>();
        private final ImageView search, close;
        private boolean open;

        SearchController(Activity activity, LinearLayout header, TextView title, EditText input) {
            this.activity = activity; this.header = header; this.title = title; this.input = input;
            int titleIndex = header.indexOfChild(title);
            for (int i = titleIndex + 1; i < header.getChildCount(); i++) actions.add(header.getChildAt(i));
            styleSearch(activity, input);
            input.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
            input.setContentDescription(input.getHint());
            input.setVisibility(View.GONE);
            header.addView(input, titleIndex + 1, new LinearLayout.LayoutParams(0, dp(activity, 44), 1));
            search = iconAction(activity, R.drawable.ic_nav_search, "Search", false, v -> show(true));
            search.setTag("library-search-open");
            header.addView(search, titleIndex + 2, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
            close = iconAction(activity, R.drawable.ic_more_close, "Close search", false, v -> close());
            close.setTag("library-search-close");
            close.setVisibility(View.GONE);
            header.addView(close, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
            if (titleIndex > 0) header.getChildAt(0).setOnClickListener(v -> {
                if (!close()) activity.finish();
            });
        }

        boolean isOpen() { return open; }

        void show(boolean keyboard) {
            if (!open) {
                open = true;
                title.setVisibility(View.GONE); search.setVisibility(View.GONE);
                for (View action : actions) action.setVisibility(View.GONE);
                input.setVisibility(View.VISIBLE); close.setVisibility(View.VISIBLE);
                if (ZeroChillMotion.animationsEnabled(activity)) {
                    input.setAlpha(0f); input.animate().alpha(1f).setDuration(140L).start();
                }
            }
            if (keyboard) {
                input.requestFocus();
                input.setSelection(input.length());
                input.post(() -> {
                    if (!open || !input.hasFocus()) return;
                    android.view.inputmethod.InputMethodManager manager =
                            (android.view.inputmethod.InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (manager != null) manager.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                });
            }
        }

        boolean close() {
            if (!open) return false;
            open = false;
            BrowseUi.hideKeyboard(activity, input);
            input.clearFocus(); input.animate().cancel(); input.setAlpha(1f);
            input.setText(""); input.setVisibility(View.GONE); close.setVisibility(View.GONE);
            title.setVisibility(View.VISIBLE); search.setVisibility(View.VISIBLE);
            for (View action : actions) action.setVisibility(View.VISIBLE);
            return true;
        }

        void restore(android.os.Bundle state) {
            if (state != null && (state.getBoolean("inline_search", false) || input.length() > 0)) show(false);
        }
        void save(android.os.Bundle state) { state.putBoolean("inline_search", open); }
    }

    static void attachSummary(LinearLayout root, TextView count, Runnable sort, Runnable density) {
        Context context = root.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 12), 0, dp(context, 8), 0);
        styleSummary(context, count);
        count.setPadding(dp(context, 4), 0, dp(context, 4), 0);
        row.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
        if (sort != null) {
            TextView action = BrowseUi.action(context, "⇅", "Change sort order", v -> sort.run());
            styleAction(action, context); action.setTextSize(22);
            row.addView(action, new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));
        }
        if (density != null) {
            ImageView grid = iconAction(context, R.drawable.ic_more_view_style, "Change grid columns", true, v -> density.run());
            row.addView(grid, new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));
        }
        row.setMinimumHeight(dp(context, 40));
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    static String compactDate(long timestamp, long now) {
        if (timestamp <= 0) return "";
        java.text.DateFormat format = LibraryBrowseGroups.TODAY.equals(LibraryBrowseGroups.dateBucket(timestamp, now))
                ? java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                : java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM);
        return format.format(new java.util.Date(timestamp));
    }

    static void styleSummary(Context context, TextView count) {
        count.setTextColor(AppearanceTheme.secondaryText(context));
        count.setTextSize(12);
        count.setLetterSpacing(0.02f);
    }

    static void styleEmpty(Context context, TextView empty) {
        empty.setTextColor(AppearanceTheme.secondaryText(context));
        empty.setBackgroundColor(android.graphics.Color.TRANSPARENT);
    }

    static void styleAction(TextView action, Context context) {
        action.setTextColor(AppearanceTheme.accent(context));
        action.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        action.setPadding(0, 0, 0, 0);
        action.setMinimumWidth(dp(context, 48));
        action.setMinimumHeight(dp(context, 48));
        action.setGravity(Gravity.CENTER);
    }

    static void styleMediaCard(Context context, MaterialCardView card) {
        card.setRadius(dp(context, 18));
        card.setCardElevation(0);
        card.setCardBackgroundColor(RAISED);
        card.setStrokeWidth(0);
    }

    static TextView mediaCaption(Context context, String value) {
        TextView title = BrowseUi.text(context, value, 14, AppearanceTheme.primaryText(context));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        title.setMinLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setIncludeFontPadding(false);
        return title;
    }

    static TextView sectionHeading(Context context, String value) {
        TextView title = BrowseUi.text(context, value, 15, AppearanceTheme.secondaryText(context));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(dp(context, 2), dp(context, 8), dp(context, 2), dp(context, 6));
        androidx.core.view.ViewCompat.setAccessibilityHeading(title, true);
        return title;
    }

    static void styleLikedRow(Context context, LinearLayout row) {
        row.setBackground(surface(context, 18, 0xFF151F28, 0xFF10161D, 47));
        row.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        row.setMinimumHeight(dp(context, 98));
        RecyclerMargin.apply(row, context);
    }

    static void styleLikedArtwork(Context context, ImageView image) {
        image.setBackground(surface(context, 13, RAISED, INSET, 34));
        image.setClipToOutline(true);
        image.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(context, 13));
            }
        });
    }

    private static GradientDrawable surface(Context context, int radius,
            int top, int bottom, int outlineAlpha) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{top, bottom});
        drawable.setCornerRadius(dp(context, radius));
        drawable.setStroke(dp(context, 1),
                ColorUtils.setAlphaComponent(AppearanceTheme.accent(context), outlineAlpha));
        return drawable;
    }

    private static int dp(Context context, int value) {
        return BrowseUi.dp(context, value);
    }

    /** Keep RecyclerView spacing on rows without changing list layout or adapter positions. */
    private static final class RecyclerMargin {
        private RecyclerMargin() { }
        static void apply(View row, Context context) {
            android.view.ViewGroup.LayoutParams original = row.getLayoutParams();
            if (original instanceof androidx.recyclerview.widget.RecyclerView.LayoutParams) {
                androidx.recyclerview.widget.RecyclerView.LayoutParams params =
                        (androidx.recyclerview.widget.RecyclerView.LayoutParams) original;
                params.setMargins(dp(context, 4), dp(context, 5), dp(context, 4), dp(context, 5));
                row.setLayoutParams(params);
            }
        }
    }
}
