package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import java.util.ArrayList;
import java.util.List;

/** Lightweight collection shortcuts. Opening a destination owns all data loading. */
final class LibraryCollectionView extends LinearLayout {
    private final TextView heading;
    private final List<LinearLayout> tiles = new ArrayList<>();
    private final List<ImageView> icons = new ArrayList<>();
    private final List<TextView> labels = new ArrayList<>();

    LibraryCollectionView(Context context, Runnable watchLater, Runnable downloads,
            Runnable history, Runnable favorites) {
        super(context);
        setOrientation(VERTICAL);
        setTag("library-collection");
        setPadding(0, dp(8), 0, dp(24));
        heading = new TextView(context);
        heading.setText("Your Collection");
        ZeroChillTypography.apply(heading, ZeroChillTypography.Role.SECTION);
        androidx.core.view.ViewCompat.setAccessibilityHeading(heading, true);
        LayoutParams titleParams = new LayoutParams(-1, -2);
        titleParams.bottomMargin = dp(12);
        addView(heading, titleParams);

        LinearLayout top = row();
        tile(top, "Watch Later", "watch-later", R.drawable.ic_nav_saved, watchLater);
        tile(top, "Downloads", "downloads", R.drawable.ic_action_download, downloads);
        LinearLayout bottom = row();
        ((LayoutParams) bottom.getLayoutParams()).topMargin = dp(10);
        tile(bottom, "History", "history", R.drawable.ic_library_history, history);
        tile(bottom, "Favorites", "favorites", R.drawable.ic_action_heart_filled, favorites);
        applyTheme();
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        addView(row, new LayoutParams(-1, -2));
        return row;
    }

    private void tile(LinearLayout row, String title, String tag, int drawable, Runnable open) {
        LinearLayout tile = new LinearLayout(getContext());
        tile.setOrientation(VERTICAL);
        tile.setPadding(dp(16), dp(16), dp(16), dp(16));
        tile.setMinimumHeight(dp(92));
        tile.setTag("library-collection-" + tag);
        tile.setContentDescription("Open " + title);
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setOnClickListener(v -> open.run());
        ZeroChillMotion.installPressFeedback(tile);

        ImageView icon = new ImageView(getContext());
        icon.setImageResource(drawable);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(icon, new LayoutParams(dp(25), dp(25)));
        TextView label = new TextView(getContext());
        label.setText(title);
        label.setTextSize(13);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams labelParams = new LayoutParams(-1, -2);
        labelParams.topMargin = dp(12);
        tile.addView(label, labelParams);

        LayoutParams params = new LayoutParams(0, -1, 1);
        if (row.getChildCount() > 0) params.setMarginStart(dp(10));
        row.addView(tile, params);
        tiles.add(tile);
        icons.add(icon);
        labels.add(label);
    }

    void applyTheme() {
        int accent = AppearanceTheme.accent(getContext());
        heading.setTextColor(AppearanceTheme.primaryText(getContext()));
        for (int i = 0; i < tiles.size(); i++) {
            GradientDrawable surface = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {ColorUtils.blendARGB(0xFF1B2630, accent, .04f), 0xFF151E27});
            surface.setCornerRadius(dp(19));
            surface.setStroke(dp(1), ColorUtils.blendARGB(0xFF303943, accent, .12f));
            tiles.get(i).setBackground(surface);
            icons.get(i).setColorFilter(accent);
            labels.get(i).setTextColor(AppearanceTheme.primaryText(getContext()));
        }
    }

    private int dp(int value) { return BrowseUi.dp(getContext(), value); }
}
