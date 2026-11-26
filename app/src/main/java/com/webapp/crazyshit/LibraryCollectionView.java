package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lightweight collection shortcuts. Opening a destination owns all data loading. */
final class LibraryCollectionView extends LinearLayout {
    private final TextView heading;
    private final List<FrameLayout> tiles = new ArrayList<>();
    private final List<ImageView> icons = new ArrayList<>();
    private final List<TextView> labels = new ArrayList<>();
    private final Map<String, FrameLayout> stacks = new LinkedHashMap<>();
    private final Map<String, TextView> labelByTag = new LinkedHashMap<>();

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
        FrameLayout tile = new FrameLayout(getContext());
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
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(25), dp(25), Gravity.START | Gravity.TOP);
        tile.addView(icon, iconParams);
        TextView label = new TextView(getContext());
        label.setText(title);
        label.setTextSize(13);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(-1, -2, Gravity.START | Gravity.TOP);
        labelParams.topMargin = dp(37);
        tile.addView(label, labelParams);

        FrameLayout stack = new FrameLayout(getContext());
        stack.setTag("library-collection-stack-" + tag);
        stack.setVisibility(View.GONE);
        stack.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(stack, new FrameLayout.LayoutParams(dp(76), dp(48), Gravity.END | Gravity.CENTER_VERTICAL));

        LayoutParams params = new LayoutParams(0, -1, 1);
        if (row.getChildCount() > 0) params.setMarginStart(dp(10));
        row.addView(tile, params);
        tiles.add(tile);
        icons.add(icon);
        labels.add(label);
        stacks.put(tag, stack);
        labelByTag.put(tag, label);
    }

    void setPreviews(String tag, List<String> urls) {
        FrameLayout stack = stacks.get(tag);
        TextView label = labelByTag.get(tag);
        if (stack == null) return;
        clearStack(stack);
        List<String> chosen = LibraryBrowseGroups.firstUrls(urls, 3);
        if (label != null && label.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams labelParams = (FrameLayout.LayoutParams) label.getLayoutParams();
            labelParams.rightMargin = chosen.isEmpty() ? 0 : dp(80);
            label.setLayoutParams(labelParams);
        }
        if (chosen.isEmpty()) {
            stack.setVisibility(View.GONE);
            return;
        }
        stack.setVisibility(View.VISIBLE);
        int size = dp(40);
        for (int i = chosen.size() - 1; i >= 0; i--) {
            ImageView image = new ImageView(getContext());
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(0xFF10161D);
            image.setClipToOutline(true);
            image.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(8));
                }
            });
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size, Gravity.START | Gravity.CENTER_VERTICAL);
            params.leftMargin = i * dp(16);
            stack.addView(image, params);
            String url = chosen.get(i);
            Glide.with(image)
                    .load(LibraryHubView.remoteImage(url, url))
                    .dontTransform()
                    .override(dp(128), dp(128))
                    .into(image);
        }
    }

    void release() {
        for (FrameLayout stack : stacks.values()) clearStack(stack);
    }

    private void clearStack(FrameLayout stack) {
        for (int i = 0; i < stack.getChildCount(); i++) {
            View child = stack.getChildAt(i);
            if (child instanceof ImageView) Glide.with(child).clear(child);
        }
        stack.removeAllViews();
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
