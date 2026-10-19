package com.webapp.crazyshit;

import android.view.View;
import android.widget.TextView;

/** Preserve the creator suggestion favorite action in the shared results flow. */
final class SearchResultFavorites {
    private SearchResultFavorites() { }
    static TextView create(android.content.Context context) {
        TextView star = BrowseUi.action(context, "☆", "Favorite creator", v -> { });
        star.setTextSize(25f);
        star.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        return star;
    }
    static void bind(TextView star, View row, NativeContentItem creator) {
        star.setVisibility(creator.isCreator() ? View.VISIBLE : View.GONE);
        row.setOnLongClickListener(null);
        if (!creator.isCreator()) return;
        update(star, creator);
        View.OnClickListener toggle = v -> {
            CreatorFavoriteStore.toggle(star.getContext(), creator);
            update(star, creator);
        };
        star.setOnClickListener(toggle);
        row.setOnLongClickListener(v -> { toggle.onClick(v); return true; });
    }
    private static void update(TextView star, NativeContentItem creator) {
        boolean saved = CreatorFavoriteStore.contains(star.getContext(), creator);
        star.setText(saved ? "★" : "☆");
        star.setTextColor(saved ? UiPalette.PRIMARY : BrowseUi.MUTED);
        star.setContentDescription((saved ? "Unfavorite " : "Favorite ") + creator.title);
    }
}
