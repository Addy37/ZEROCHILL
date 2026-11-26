package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import java.util.ArrayList;
import java.util.List;

/** Compact saved-content shortcuts; custom collections own the artwork previews. */
final class LibraryCollectionView extends LinearLayout {
    private final List<LinearLayout> tiles = new ArrayList<>();
    private final List<ImageView> icons = new ArrayList<>();
    private final List<TextView> labels = new ArrayList<>();

    LibraryCollectionView(Context context, Runnable watchLater, Runnable downloads,
            Runnable history, Runnable favorites) {
        super(context);
        setOrientation(VERTICAL);
        setTag("library-collection");
        setPadding(0, 0, 0, dp(22));
        LinearLayout top = row();
        tile(top, "Watch Later", "watch-later", R.drawable.ic_nav_saved, watchLater);
        tile(top, "Favorites", "favorites", R.drawable.ic_action_heart_filled, favorites);
        LinearLayout bottom = row();
        ((LayoutParams) bottom.getLayoutParams()).topMargin = dp(8);
        tile(bottom, "History", "history", R.drawable.ic_library_history, history);
        tile(bottom, "Downloads", "downloads", R.drawable.ic_action_download, downloads);
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
        tile.setOrientation(HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(12), dp(10), dp(12), dp(10));
        tile.setMinimumHeight(dp(52));
        tile.setTag("library-collection-" + tag);
        tile.setContentDescription("Open " + title);
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setOnClickListener(v -> open.run());
        ZeroChillMotion.installPressFeedback(tile);
        ImageView icon = new ImageView(getContext());
        icon.setImageResource(drawable);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(icon, new LayoutParams(dp(20), dp(20)));
        TextView label = new TextView(getContext());
        label.setText(title);
        label.setTextSize(14);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams text = new LayoutParams(0, -2, 1);
        text.setMarginStart(dp(9));
        tile.addView(label, text);
        LayoutParams cell = new LayoutParams(0, -1, 1);
        if (row.getChildCount() > 0) cell.setMarginStart(dp(8));
        row.addView(tile, cell);
        tiles.add(tile); icons.add(icon); labels.add(label);
    }

    void applyTheme() {
        int accent = AppearanceTheme.accent(getContext());
        for (int i = 0; i < tiles.size(); i++) {
            GradientDrawable surface = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {ColorUtils.blendARGB(0xFF1B2630, accent, .04f), 0xFF151E27});
            surface.setCornerRadius(dp(15));
            tiles.get(i).setBackground(surface);
            icons.get(i).setColorFilter(accent);
            labels.get(i).setTextColor(AppearanceTheme.primaryText(getContext()));
        }
    }

    private int dp(int value) { return BrowseUi.dp(getContext(), value); }
}
