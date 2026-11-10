package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
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

    /**
     * Library gallery destinations can get an oversized top inset with some
     * immersive/edge-to-edge window transitions. Keep a bounded status-bar
     * allowance while retaining side, cutout and keyboard insets.
     *
     * Downloads uses the existing BrowseUi.screen() path, which already works
     * correctly on the physical device.
     */
    static LinearLayout compactRoot(Activity activity) {
        LinearLayout root = BrowseUi.screen(activity);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets system = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
                            | WindowInsetsCompat.Type.ime());
            int statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int cutoutTop = insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top;
            int top = Math.max(dp(activity, 24),
                    Math.min(Math.max(statusTop, cutoutTop), dp(activity, 42)));
            view.setPadding(system.left, top, system.right, system.bottom);
            return insets;
        });
        return root;
    }

    static void attachHeader(LinearLayout root, LinearLayout header, TextView title) {
        Context context = root.getContext();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
        header.setMinimumHeight(dp(context, 64));
        header.setBackground(surface(context, 21, 0xFF17242E, INSET, 56));
        header.setClipToOutline(true);
        title.setTextSize(22);
        title.setTextColor(AppearanceTheme.primaryText(context));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        for (int i = 0; i < header.getChildCount(); i++) {
            View child = header.getChildAt(i);
            if (child instanceof TextView && child != title) {
                styleAction((TextView) child, context);
            }
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 10));
        root.addView(header, params);
    }

    static void styleSearch(Context context, EditText input) {
        input.setTextColor(AppearanceTheme.primaryText(context));
        input.setHintTextColor(AppearanceTheme.secondaryText(context));
        input.setTextSize(15);
        input.setBackground(surface(context, 17, 0xFF17222B, OLED, 50));
        input.setMinHeight(dp(context, 50));
    }

    static void styleSummary(Context context, TextView count) {
        count.setTextColor(AppearanceTheme.secondaryText(context));
        count.setTextSize(12);
        count.setLetterSpacing(0.02f);
    }

    static void styleEmpty(Context context, TextView empty) {
        empty.setTextColor(AppearanceTheme.secondaryText(context));
        empty.setBackground(surface(context, 19, 0xFF121B23, OLED, 35));
    }

    static void styleAction(TextView action, Context context) {
        action.setTextColor(AppearanceTheme.accent(context));
        action.setBackground(surface(context, 15, RAISED, INSET, 72));
        action.setMinimumWidth(dp(context, 48));
        action.setMinimumHeight(dp(context, 48));
        action.setGravity(Gravity.CENTER);
    }

    static void styleMediaCard(Context context, MaterialCardView card) {
        card.setRadius(dp(context, 18));
        card.setCardElevation(0);
        card.setCardBackgroundColor(RAISED);
        card.setStrokeWidth(dp(context, 1));
        card.setStrokeColor(ColorUtils.setAlphaComponent(AppearanceTheme.accent(context), 65));
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
