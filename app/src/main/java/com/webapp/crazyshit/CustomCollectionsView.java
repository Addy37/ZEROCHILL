package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.*;
import androidx.core.graphics.ColorUtils;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Wide artwork cards with separate labels, following the approved Library layout. */
final class CustomCollectionsView extends LinearLayout {
    private final Activity activity;
    private final LinearLayout tiles;
    private final TextView heading;
    private final List<View> panels = new ArrayList<>();
    private final List<TextView> primaryLabels = new ArrayList<>(), counts = new ArrayList<>(), accents = new ArrayList<>();
    private View createTile;
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
        LayoutParams headingParams = new LayoutParams(-1, -2);
        headingParams.bottomMargin = dp(12);
        addView(heading, headingParams);
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
        if (signature.equals(state.toString()) && tiles.getChildCount() > 0) return;
        signature = state.toString();
        clearImages(tiles);
        tiles.removeAllViews();
        panels.clear(); primaryLabels.clear(); counts.clear(); accents.clear(); createTile = null;
        LinearLayout row = null;
        for (int index = 0; index <= groups.size(); index++) {
            if (index % 2 == 0) {
                row = new LinearLayout(activity);
                row.setOrientation(HORIZONTAL);
                LayoutParams line = new LayoutParams(-1, -2);
                if (index > 0) line.topMargin = dp(14);
                tiles.addView(row, line);
            }
            final MediaCollectionStore.Preview p = index < groups.size() ? groups.get(index) : null;
            LinearLayout tile = new LinearLayout(activity);
            tile.setOrientation(VERTICAL);
            if (p == null) {
                createTile = tile;
                tile.setTag("library-new-collection");
                tile.setGravity(Gravity.CENTER);
                tile.setMinimumHeight(dp(150));
                tile.setPadding(dp(8), dp(8), dp(8), dp(8));
                TextView plus = new TextView(activity);
                plus.setText("+"); plus.setTextSize(30); plus.setGravity(Gravity.CENTER);
                tile.addView(plus, new LayoutParams(-1, -2)); accents.add(plus);
                TextView label = new TextView(activity);
                label.setText("New collection"); label.setTextSize(14); label.setGravity(Gravity.CENTER);
                tile.addView(label, new LayoutParams(-1, -2)); accents.add(label);
                tile.setContentDescription("Create new collection");
                tile.setOnClickListener(v -> MediaCollectionPicker.name(activity, "New collection", "",
                        value -> MediaCollectionStore.create(activity, value)));
            } else {
                tile.setTag("library-custom-collection-" + p.group.id);
                ArtworkPanel stack = new ArtworkPanel(activity);
                stack.setTag("collection-artwork");
                stack.setClipToOutline(true);
                panels.add(stack);
                tile.addView(stack, new LayoutParams(-1, -2));
                if (p.images.isEmpty() || index >= 12) {
                    TextView icon = new TextView(activity);
                    icon.setText("▣"); icon.setTextSize(32); icon.setGravity(Gravity.CENTER);
                    stack.addView(icon, new FrameLayout.LayoutParams(-1, -1)); accents.add(icon);
                } else {
                    int screenDp = activity.getResources().getDisplayMetrics().widthPixels * 160
                            / activity.getResources().getDisplayMetrics().densityDpi;
                    int photoWidth = Math.max(46, Math.min(80, (screenDp - 56) / 4));
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
            }
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
        for (View panel : panels) {
            GradientDrawable surface = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{ColorUtils.blendARGB(0xFF1B2630, accent, .04f), 0xFF151E27});
            surface.setCornerRadius(dp(14));
            panel.setBackground(surface);
        }
        if (createTile != null) {
            GradientDrawable outline = new GradientDrawable();
            outline.setColor(AppearanceTheme.surface(activity));
            outline.setCornerRadius(dp(14));
            outline.setStroke(dp(1), ColorUtils.blendARGB(0xff36434c, accent, .16f));
            createTile.setBackground(outline);
        }
        for (TextView name : primaryLabels) name.setTextColor(AppearanceTheme.primaryText(activity));
        for (TextView count : counts) count.setTextColor(AppearanceTheme.secondaryText(activity));
        for (TextView label : accents) label.setTextColor(accent);
    }

    private static final class ArtworkPanel extends FrameLayout {
        ArtworkPanel(Activity activity) { super(activity); }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int height = Math.round(width * .625f);
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
