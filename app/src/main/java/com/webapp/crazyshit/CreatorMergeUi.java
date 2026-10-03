package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/** Shared visual treatment for manually merged favorite creators. */
final class CreatorMergeUi {
    private CreatorMergeUi() { }

    static boolean isMerged(CreatorCatalog.FavoriteGroup group) {
        return group != null && group.manual && group.members.size() > 1;
    }

    static TextView badge(Context context) {
        TextView badge = new TextView(context);
        badge.setTextColor(Color.BLACK);
        badge.setTextSize(10f);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setSingleLine(true);
        badge.setMinWidth(dp(context, 34));
        badge.setMinHeight(dp(context, 20));
        badge.setPadding(dp(context, 5), 0, dp(context, 6), 0);
        badge.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_creator_merged,
                0,
                0,
                0
        );
        badge.setCompoundDrawableTintList(ColorStateList.valueOf(Color.BLACK));
        badge.setCompoundDrawablePadding(dp(context, 3));
        badge.setBackground(BrowseUi.rounded(context, UiPalette.PRIMARY, 10));
        badge.setVisibility(View.GONE);
        badge.setFocusable(true);
        badge.setClickable(true);
        return badge;
    }

    static void bind(
            TextView badge,
            CreatorCatalog.FavoriteGroup group,
            Runnable onClick
    ) {
        if (!isMerged(group)) {
            badge.setText("");
            badge.setContentDescription(null);
            badge.setOnClickListener(null);
            badge.setVisibility(View.GONE);
            return;
        }
        int count = group.members.size();
        badge.setText(String.valueOf(count));
        badge.setContentDescription(
                "Merged creator, " + count + " profiles. Tap to view merged creators."
        );
        badge.setOnClickListener(v -> {
            if (onClick != null) onClick.run();
        });
        badge.setVisibility(View.VISIBLE);
    }

    static CreatorCatalog.FavoriteGroup findManualGroup(Context context, String cacheKey) {
        String expected = cacheKey == null ? "" : cacheKey.trim();
        if (expected.isEmpty() || !expected.startsWith("merged:")) return null;
        for (CreatorCatalog.FavoriteGroup group : CreatorCatalog.favoriteGroups(context)) {
            if (!isMerged(group)) continue;
            if (expected.equals(CreatorGallerySpec.from(group).cacheKey)) return group;
        }
        return null;
    }

    static void showMembers(Context context, CreatorCatalog.FavoriteGroup group) {
        if (!isMerged(group) || !(context instanceof Activity)) return;
        Activity activity = (Activity) context;
        if (activity.isFinishing() || activity.isDestroyed()) return;
        CreatorMergeSheet.showMembers(activity, group.members);
    }

    private static int dp(Context context, int value) {
        return BrowseUi.dp(context, value);
    }
}
