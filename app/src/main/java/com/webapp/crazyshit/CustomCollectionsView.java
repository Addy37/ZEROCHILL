package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.bumptech.glide.Glide;
import java.util.List;

/** Personal collections sit under the existing Library shortcuts. */
final class CustomCollectionsView extends LinearLayout {
    private final Activity activity;
    private final LinearLayout tiles;
    private String signature = "";

    CustomCollectionsView(Activity activity) {
        super(activity);
        this.activity = activity;
        setOrientation(VERTICAL);
        setPadding(0, 0, 0, dp(24));
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(activity);
        title.setText("My Collections");
        ZeroChillTypography.apply(title, ZeroChillTypography.Role.SECTION);
        header.addView(title, new LayoutParams(0, -2, 1));
        TextView create = BrowseUi.action(activity, "+", "New collection",
                v -> MediaCollectionPicker.name(activity, "New collection", "",
                        name -> MediaCollectionStore.create(activity, name)));
        create.setTextColor(AppearanceTheme.accent(activity));
        header.addView(create, new LayoutParams(dp(48), dp(48)));
        addView(header, new LayoutParams(-1, -2));
        tiles = new LinearLayout(activity);
        tiles.setOrientation(VERTICAL);
        addView(tiles, new LayoutParams(-1, -2));
    }

    void bind(List<MediaCollectionStore.Preview> groups) {
        StringBuilder state = new StringBuilder();
        for (MediaCollectionStore.Preview p : groups)
            state.append(p.group.id).append('|').append(p.group.name).append('|')
                    .append(p.count).append('|').append(p.images).append('\n');
        if (signature.equals(state.toString()) && tiles.getChildCount() > 0) return;
        signature = state.toString();
        clearImages(tiles);
        tiles.removeAllViews();
        LinearLayout row = null;
        for (int index = 0; index <= groups.size(); index++) {
            if (index % 2 == 0) {
                row = new LinearLayout(activity);
                row.setOrientation(HORIZONTAL);
                LayoutParams line = new LayoutParams(-1, -2);
                line.bottomMargin = dp(10);
                tiles.addView(row, line);
            }
            final MediaCollectionStore.Preview p = index < groups.size() ? groups.get(index) : null;
            LinearLayout tile = new LinearLayout(activity);
            tile.setOrientation(VERTICAL);
            tile.setPadding(dp(10), dp(10), dp(10), dp(10));
            ZeroChillUi.styleCard(tile);
            FrameLayout stack = new FrameLayout(activity);
            stack.setClipChildren(false);
            tile.addView(stack, new LayoutParams(-1, dp(88)));
            if (p == null || p.images.isEmpty() || index >= 12) {
                TextView icon = new TextView(activity);
                icon.setText(p == null ? "+" : "▣");
                icon.setTextSize(32);
                icon.setGravity(Gravity.CENTER);
                icon.setTextColor(AppearanceTheme.accent(activity));
                stack.addView(icon, new FrameLayout.LayoutParams(-1, -1));
            } else {
                for (int j = p.images.size() - 1; j >= 0; j--) {
                    ImageView image = new ImageView(activity);
                    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    image.setBackgroundColor(0xff1b2933);
                    image.setRotation(j == 0 ? 0 : j == 1 ? -8 : 8);
                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(68), dp(76), Gravity.CENTER);
                    params.leftMargin = dp(j * 9);
                    stack.addView(image, params);
                    String url = p.images.get(j);
                    Glide.with(image).load(LibraryHubView.remoteImage(url, url))
                            .centerCrop().override(dp(160), dp(160))
                            .transition(SharedArtworkReveal.artwork("custom-collection-" + p.group.id, url, false))
                            .into(image);
                }
            }
            TextView name = new TextView(activity);
            name.setText(p == null ? "New collection" : p.group.name);
            ZeroChillUi.styleTitle(name);
            name.setTextSize(14);
            name.setMaxLines(2);
            tile.addView(name);
            TextView count = new TextView(activity);
            count.setText(p == null ? "Create your own" : p.count + (p.count == 1 ? " item" : " items"));
            ZeroChillUi.styleSecondary(count);
            count.setTextSize(12);
            tile.addView(count);
            tile.setFocusable(true);
            tile.setClickable(true);
            ZeroChillMotion.installPressFeedback(tile);
            tile.setContentDescription(name.getText() + ", " + count.getText());
            tile.setOnClickListener(v -> {
                if (p == null) MediaCollectionPicker.name(activity, "New collection", "",
                        value -> MediaCollectionStore.create(activity, value));
                else activity.startActivityForResult(new Intent(activity, FavoritesActivity.class)
                        .putExtra(FavoritesActivity.EXTRA_START_TAB, FavoritesActivity.START_WATCH_LATER)
                        .putExtra(FavoritesActivity.EXTRA_MEDIA_FAVORITES, true)
                        .putExtra(FavoritesActivity.EXTRA_COLLECTION_ID, p.group.id),
                        NativeMainActivity.FAVORITES_REQUEST);
            });
            LayoutParams cell = new LayoutParams(0, -1, 1);
            if (index % 2 == 1) cell.leftMargin = dp(10);
            row.addView(tile, cell);
        }
        if (row != null && row.getChildCount() == 1) row.addView(new View(activity), new LayoutParams(0, 1, 1));
    }

    void release() { clearImages(tiles); }
    private void clearImages(View view) {
        if (view instanceof ImageView) Glide.with(view).clear((ImageView) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) clearImages(group.getChildAt(i));
        }
    }
    private int dp(int value) { return BrowseUi.dp(activity, value); }
}
