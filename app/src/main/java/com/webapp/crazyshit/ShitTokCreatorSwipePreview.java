package com.webapp.crazyshit;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight OnlyFap surface revealed under ShitTok during an interactive creator swipe.
 *
 * It paints from the already-prewarmed creator session so the user's finger reveals real gallery
 * media immediately. The actual creator activity takes over after the drag settles.
 */
final class ShitTokCreatorSwipePreview extends FrameLayout {
    private static final int GRID_COLUMNS = 3;
    private static final int GRID_ITEMS = 9;

    private final Activity activity;
    private final TextView creatorName;
    private final TextView creatorMeta;
    private final GridLayout grid;
    private String currentCreator = "";
    private String currentSessionId = "";
    private int refreshGeneration;

    ShitTokCreatorSwipePreview(Activity activity) {
        super(activity);
        this.activity = activity;
        setBackgroundColor(Color.BLACK);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.BLACK);
        addView(shell, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(6), dp(8), dp(6));
        top.setBackgroundColor(Color.BLACK);

        TextView back = text("‹", 34, Color.WHITE, false);
        back.setGravity(Gravity.CENTER);
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(52)));

        TextView heading = text("OnlyFap", 20, Color.WHITE, true);
        top.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView more = text("⋮", 28, Color.rgb(220, 220, 226), false);
        more.setGravity(Gravity.CENTER);
        top.addView(more, new LinearLayout.LayoutParams(dp(48), dp(52)));
        shell.addView(top, new LinearLayout.LayoutParams(-1, dp(64)));

        LinearLayout profile = new LinearLayout(activity);
        profile.setOrientation(LinearLayout.VERTICAL);
        profile.setGravity(Gravity.BOTTOM);
        profile.setPadding(dp(18), dp(18), dp(18), dp(18));
        profile.setBackground(profileBackground());

        creatorName = text("", 25, Color.WHITE, true);
        creatorName.setMaxLines(1);
        creatorName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        profile.addView(creatorName, new LinearLayout.LayoutParams(-1, -2));

        creatorMeta = text("Creator gallery", 12, Color.rgb(184, 184, 194), false);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = dp(4);
        profile.addView(creatorMeta, metaParams);
        shell.addView(profile, new LinearLayout.LayoutParams(-1, dp(138)));

        LinearLayout tabs = new LinearLayout(activity);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER);
        tabs.setBackgroundColor(Color.BLACK);
        String[] labels = {"ALL", "PICTURES", "VIDEOS"};
        for (int i = 0; i < labels.length; i++) {
            TextView tab = text(
                    labels[i],
                    11,
                    i == 0 ? UiPalette.PRIMARY : Color.rgb(174, 174, 182),
                    i == 0
            );
            tab.setGravity(Gravity.CENTER);
            tabs.addView(tab, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }
        shell.addView(tabs, new LinearLayout.LayoutParams(-1, dp(48)));

        grid = new GridLayout(activity);
        grid.setColumnCount(GRID_COLUMNS);
        grid.setRowCount(GRID_ITEMS / GRID_COLUMNS);
        grid.setBackgroundColor(Color.BLACK);
        grid.setPadding(dp(2), dp(2), dp(2), dp(2));
        shell.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));

        showSkeleton();
    }

    void showCreator(String creator, String sessionId) {
        currentCreator = clean(creator);
        currentSessionId = clean(sessionId);
        creatorName.setText(currentCreator);
        setVisibility(View.VISIBLE);
        refreshCreator(currentCreator, currentSessionId);
    }

    void refreshCreator(String creator, String sessionId) {
        String cleanCreator = clean(creator);
        if (!cleanCreator.isEmpty()) currentCreator = cleanCreator;
        String cleanSession = clean(sessionId);
        if (!cleanSession.isEmpty()) currentSessionId = cleanSession;
        creatorName.setText(currentCreator);

        BunkrGallerySessionStore.Snapshot snapshot =
                BunkrGallerySessionStore.snapshot(currentSessionId);
        if (snapshot != null && !snapshot.items.isEmpty()) {
            bindItems(snapshot.items);
            creatorMeta.setText(snapshot.items.size() + " items ready");
            return;
        }

        showSkeleton();
        creatorMeta.setText("Loading gallery…");
        int generation = ++refreshGeneration;
        scheduleRefresh(generation, 0);
    }

    private void scheduleRefresh(int generation, int attempt) {
        if (attempt >= 5) return;
        postDelayed(() -> {
            if (generation != refreshGeneration || getVisibility() != View.VISIBLE) return;
            String newest = ShitTokCreatorGalleryPreloader.sessionId(activity, currentCreator);
            if (!newest.isEmpty()) currentSessionId = newest;
            BunkrGallerySessionStore.Snapshot later =
                    BunkrGallerySessionStore.snapshot(currentSessionId);
            if (later != null && !later.items.isEmpty()) {
                bindItems(later.items);
                creatorMeta.setText(later.items.size() + " items ready");
                return;
            }
            scheduleRefresh(generation, attempt + 1);
        }, attempt == 0 ? 90L : 120L);
    }

    private void bindItems(List<NativeContentItem> items) {
        grid.removeAllViews();
        ArrayList<NativeContentItem> visible = new ArrayList<>();
        for (NativeContentItem item : items) {
            if (item == null || item.imageUrl == null || item.imageUrl.trim().isEmpty()) continue;
            visible.add(item);
            if (visible.size() >= GRID_ITEMS) break;
        }
        if (visible.isEmpty()) {
            showSkeleton();
            return;
        }

        for (int i = 0; i < GRID_ITEMS; i++) {
            if (i < visible.size()) addMediaCell(visible.get(i));
            else addSkeletonCell();
        }
    }

    private void addMediaCell(NativeContentItem item) {
        FrameLayout cell = cell();
        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(18, 18, 21));
        cell.addView(image, new FrameLayout.LayoutParams(-1, -1));

        Glide.with(activity)
                .load(withHeaders(item.imageUrl, imageReferer(item)))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .dontAnimate()
                .placeholder(new ColorDrawable(Color.rgb(18, 18, 21)))
                .error(new ColorDrawable(Color.rgb(18, 18, 21)))
                .into(image);

        if (item.isVideo()) {
            TextView play = text("▶", 13, Color.WHITE, true);
            play.setGravity(Gravity.CENTER);
            play.setBackground(playBadge());
            FrameLayout.LayoutParams playParams =
                    new FrameLayout.LayoutParams(dp(30), dp(30));
            playParams.gravity = Gravity.BOTTOM | Gravity.END;
            playParams.setMargins(0, 0, dp(7), dp(7));
            cell.addView(play, playParams);
        }
        grid.addView(cell, cellParams());
    }

    private void showSkeleton() {
        grid.removeAllViews();
        for (int i = 0; i < GRID_ITEMS; i++) addSkeletonCell();
    }

    private void addSkeletonCell() {
        View cell = new View(activity);
        cell.setBackgroundColor(iCellColor(grid.getChildCount()));
        grid.addView(cell, cellParams());
    }

    private FrameLayout cell() {
        FrameLayout cell = new FrameLayout(activity);
        cell.setBackgroundColor(Color.rgb(18, 18, 21));
        return cell;
    }

    private GridLayout.LayoutParams cellParams() {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = 0;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(1), dp(1), dp(1), dp(1));
        return params;
    }

    private int iCellColor(int index) {
        int base = 18 + (index % 3) * 3;
        return Color.rgb(base, base, base + 2);
    }

    private GradientDrawable profileBackground() {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(5, 5, 7), Color.rgb(11, 11, 14)}
        );
        background.setStroke(dp(1), Color.rgb(25, 25, 30));
        return background;
    }

    private GradientDrawable playBadge() {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(Color.argb(185, 0, 0, 0));
        background.setStroke(dp(1), Color.argb(120, 255, 255, 255));
        return background;
    }

    private GlideUrl withHeaders(String imageUrl, String referer) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("Referer", clean(referer).isEmpty()
                        ? BunkrRepository.DEFAULT_PAGE_ORIGIN + "/"
                        : referer)
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        try {
            headers.addHeader("User-Agent", WebSettings.getDefaultUserAgent(activity));
        } catch (Exception ignored) {
        }
        try {
            String cookies = CookieManager.getInstance().getCookie(imageUrl);
            if ((cookies == null || cookies.isEmpty()) && referer != null) {
                cookies = CookieManager.getInstance().getCookie(referer);
            }
            if (cookies != null && !cookies.isEmpty()) headers.addHeader("Cookie", cookies);
        } catch (Exception ignored) {
        }
        return new GlideUrl(imageUrl, headers.build());
    }

    private String imageReferer(NativeContentItem item) {
        if (item == null) return "";
        if (WikiFeetRepository.isWikiFeetUrl(item.url)
                && WikiFeetRepository.isWikiFeetUrl(item.uploader)) {
            return item.uploader;
        }
        if (!FapelloRepository.isPostUrl(item.url)
                && FapelloRepository.isModelUrl(item.uploader)) {
            return item.uploader;
        }
        if (OnlyHavenRepository.isOnlyHavenUrl(item.uploader)) {
            return item.uploader;
        }
        return item.url;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
