package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Outline;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.*;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Floating collection artwork with a single compact creation action in the heading. */
final class CustomCollectionsView extends LinearLayout {
    private final Activity activity;
    private final LinearLayout tiles;
    private final TextView heading;
    private final List<TextView> primaryLabels = new ArrayList<>(), counts = new ArrayList<>(), accents = new ArrayList<>();
    private final TextView createButton;
    private boolean bound;
    private String signature = "";

    CustomCollectionsView(Activity activity) {
        super(activity);
        this.activity = activity;
        setTag("library-custom-collections");
        setOrientation(VERTICAL);
        setPadding(0, 0, 0, dp(22));
        heading = new TextView(activity);
        heading.setText("My Collections");
        ZeroChillTypography.apply(heading, ZeroChillTypography.Role.SECTION);
        androidx.core.view.ViewCompat.setAccessibilityHeading(heading, true);
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(heading, new LayoutParams(0, -2, 1));
        createButton = new TextView(activity);
        createButton.setTag("library-new-collection");
        createButton.setText("+");
        createButton.setTextSize(27);
        createButton.setGravity(Gravity.CENTER);
        createButton.setContentDescription("Create new collection");
        createButton.setFocusable(true);
        createButton.setClickable(true);
        createButton.setOnClickListener(v -> MediaCollectionPicker.name(activity, "New collection", "",
                value -> MediaCollectionStore.create(activity, value)));
        ZeroChillMotion.installPressFeedback(createButton);
        header.addView(createButton, new LayoutParams(dp(48), dp(48)));
        LayoutParams headingParams = new LayoutParams(-1, -2);
        headingParams.bottomMargin = dp(4);
        addView(header, headingParams);
        tiles = new LinearLayout(activity);
        tiles.setOrientation(VERTICAL);
        addView(tiles, new LayoutParams(-1, -2));
        bind(Collections.emptyList());
    }

    void bind(List<MediaCollectionStore.Preview> groups) {
        StringBuilder state = new StringBuilder();
        for (MediaCollectionStore.Preview p : groups)
            state.append(p.group.id).append('|').append(p.group.name).append('|')
                    .append(p.count).append('|').append(p.images).append('\n');
        if (bound && signature.equals(state.toString())) return;
        bound = true;
        signature = state.toString();
        clearImages(tiles);
        tiles.removeAllViews();
        primaryLabels.clear(); counts.clear(); accents.clear();
        if (groups.isEmpty()) {
            TextView hint = new TextView(activity);
            hint.setText("Tap + to create a collection");
            hint.setTextSize(13);
            counts.add(hint);
            tiles.addView(hint, new LayoutParams(-1, -2));
        }
        LinearLayout row = null;
        for (int index = 0; index < groups.size(); index++) {
            if (index % 2 == 0) {
                row = new LinearLayout(activity);
                row.setOrientation(HORIZONTAL);
                LayoutParams line = new LayoutParams(-1, -2);
                if (index > 0) line.topMargin = dp(14);
                tiles.addView(row, line);
            }
            final MediaCollectionStore.Preview p = groups.get(index);
            LinearLayout tile = new LinearLayout(activity);
            tile.setOrientation(VERTICAL);
            tile.setTag("library-custom-collection-" + p.group.id);
            ArtworkPanel stack = new ArtworkPanel(activity);
            stack.setTag("collection-artwork");
            stack.setClipChildren(false);
            stack.setClipToPadding(false);
            tile.addView(stack, new LayoutParams(-1, -2));
            if (p.images.isEmpty() || index >= 12) {
                TextView icon = new TextView(activity);
                icon.setText("▣"); icon.setTextSize(32); icon.setGravity(Gravity.CENTER);
                stack.addView(icon, new FrameLayout.LayoutParams(-1, -1)); accents.add(icon);
            } else {
                int screenDp = activity.getResources().getDisplayMetrics().widthPixels * 160
                        / activity.getResources().getDisplayMetrics().densityDpi;
                int photoWidth = Math.round(Math.max(46, Math.min(80, (screenDp - 56) / 4)) * 1.10f);
                int photoHeight = Math.round(photoWidth * 1.16f);
                for (int j = p.images.size() - 1; j >= 0; j--) {
                    ImageView image = new ImageView(activity);
                    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    image.setBackgroundColor(0xff1b2933);
                    image.setOutlineProvider(new ViewOutlineProvider() {
                        @Override public void getOutline(View view, Outline outline) {
                            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(8));
                        }
                    });
                    image.setClipToOutline(true);
                    image.setRotation(j == 0 ? 5f : j == 1 ? -8f : -15f);
                    image.setTranslationX(dp(j == 0 ? 9 : j == 1 ? -3 : -13));
                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(photoWidth), dp(photoHeight), Gravity.CENTER);
                    stack.addView(image, params);
                    String url = p.images.get(j);
                    Glide.with(image).load(LibraryHubView.remoteImage(url, url))
                            .centerCrop().override(dp(192), dp(192))
                            .transition(SharedArtworkReveal.artwork("custom-collection-" + p.group.id, url, false))
                            .into(image);
                }
            }
            TextView name = new TextView(activity);
            name.setText(p.group.name);
            ZeroChillUi.styleTitle(name);
            name.setTextSize(15); name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LayoutParams nameParams = new LayoutParams(-1, -2);
            nameParams.topMargin = dp(7);
            tile.addView(name, nameParams); primaryLabels.add(name);
            TextView count = new TextView(activity);
            count.setText(p.count + (p.count == 1 ? " item" : " items"));
            ZeroChillUi.styleSecondary(count); count.setTextSize(13);
            tile.addView(count); counts.add(count);
            tile.setContentDescription(p.group.name + ", " + count.getText());
            tile.setOnClickListener(v -> activity.startActivityForResult(new Intent(activity, FavoritesActivity.class)
                    .putExtra(FavoritesActivity.EXTRA_START_TAB, FavoritesActivity.START_WATCH_LATER)
                    .putExtra(FavoritesActivity.EXTRA_MEDIA_FAVORITES, true)
                    .putExtra(FavoritesActivity.EXTRA_COLLECTION_ID, p.group.id),
                    NativeMainActivity.FAVORITES_REQUEST));
            tile.setFocusable(true); tile.setClickable(true);
            ZeroChillMotion.installPressFeedback(tile);
            LayoutParams cell = new LayoutParams(0, -1, 1);
            if (index % 2 == 1) cell.leftMargin = dp(10);
            row.addView(tile, cell);
        }
        if (row != null && row.getChildCount() == 1) row.addView(new View(activity), new LayoutParams(0, 1, 1));
        applyTheme();
    }

    void applyTheme() {
        int accent = AppearanceTheme.accent(activity);
        heading.setTextColor(AppearanceTheme.primaryText(activity));
        createButton.setTextColor(accent);
        for (TextView name : primaryLabels) name.setTextColor(AppearanceTheme.primaryText(activity));
        for (TextView count : counts) count.setTextColor(AppearanceTheme.secondaryText(activity));
        for (TextView label : accents) label.setTextColor(accent);
    }

    private static final class ArtworkPanel extends FrameLayout {
        ArtworkPanel(Activity activity) { super(activity); }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int height = Math.round(width * .70f);
            // Keep rotated photos inside the cell, including on narrow/split-screen layouts.
            int maxPhoto = Math.max(1, Math.min(Math.round(width * .55f),
                    (int) (height / 1.40f)));
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (!(child instanceof ImageView)) continue;
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) child.getLayoutParams();
                int cap = Math.round(88 * getResources().getDisplayMetrics().density);
                params.width = Math.min(cap, maxPhoto);
                params.height = Math.round(params.width * 1.16f);
            }
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
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
