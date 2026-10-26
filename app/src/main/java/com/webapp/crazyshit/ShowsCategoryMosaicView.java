package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

import java.util.List;

/** Lazy 2x2 episode-art collage for a Shows category card. */
final class ShowsCategoryMosaicView extends FrameLayout {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private final ImageView[] tiles = new ImageView[4];
    private int generation;
    private String categoryUrl = "";

    ShowsCategoryMosaicView(Context context) {
        super(context);
        setVisibility(View.GONE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setBackgroundColor(Color.rgb(12, 14, 17));

        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(2);
        grid.setRowCount(2);
        addView(grid, new FrameLayout.LayoutParams(-1, -1));

        int gap = Math.max(1, Math.round(context.getResources().getDisplayMetrics().density));
        for (int index = 0; index < tiles.length; index++) {
            ImageView tile = new ImageView(context);
            tile.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.setBackground(new ColorDrawable(Color.rgb(18, 20, 23)));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(index / 2, 1f),
                    GridLayout.spec(index % 2, 1f)
            );
            params.width = 0;
            params.height = 0;
            params.setMargins(
                    index % 2 == 0 ? 0 : gap,
                    index / 2 == 0 ? 0 : gap,
                    0,
                    0
            );
            grid.addView(tile, params);
            tiles[index] = tile;
        }
    }

    void bind(NativeContentItem category) {
        int request = ++generation;
        categoryUrl = category == null || category.url == null ? "" : category.url.trim();
        setVisibility(View.GONE);
        if (category == null || categoryUrl.isEmpty()) return;

        ShowsCollectionWarmCache.request(getContext(), category, (url, items) -> {
            if (request != generation || !categoryUrl.equals(url)) return;
            List<NativeContentItem> selected = ShowsCategoryMosaicSelector.select(
                    items,
                    categoryUrl,
                    ShowsCategoryMosaicSelector.SESSION_SEED,
                    tiles.length
            );
            if (selected.size() < 2) return;
            for (int index = 0; index < tiles.length; index++) {
                load(tiles[index], selected.get(index % selected.size()));
            }
            setVisibility(View.VISIBLE);
        });
    }

    boolean isShowing() {
        return getVisibility() == View.VISIBLE;
    }

    void clear() {
        generation++;
        categoryUrl = "";
        setVisibility(View.GONE);
        for (ImageView tile : tiles) {
            if (tile != null) {
                Glide.with(tile).clear(tile);
                tile.setImageDrawable(new ColorDrawable(Color.rgb(18, 20, 23)));
            }
        }
    }

    private void load(ImageView view, NativeContentItem item) {
        String imageUrl = item == null || item.imageUrl == null ? "" : item.imageUrl.trim();
        if (imageUrl.isEmpty()) return;
        String pageUrl = item.url == null ? "" : item.url.trim();
        String agent = USER_AGENT;
        if (XMegaDriveRepository.isXMegaDriveUrl(imageUrl)
                || XMegaDriveRepository.isXMegaDriveUrl(pageUrl)) {
            String session = XMegaDriveBrowserFetch.sessionUserAgent();
            if (session != null && !session.trim().isEmpty()) agent = session.trim();
        }

        LazyHeaders.Builder headers = new LazyHeaders.Builder().addHeader("User-Agent", agent);
        if (!pageUrl.isEmpty()) headers.addHeader("Referer", pageUrl);
        headers.addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        try {
            String cookies = CookieManager.getInstance().getCookie(imageUrl);
            if ((cookies == null || cookies.trim().isEmpty()) && !pageUrl.isEmpty()) {
                cookies = CookieManager.getInstance().getCookie(pageUrl);
            }
            if (cookies != null && !cookies.trim().isEmpty()) headers.addHeader("Cookie", cookies);
        } catch (Exception ignored) {
        }

        Glide.with(view)
                .load(new GlideUrl(imageUrl, headers.build()))
                .disallowHardwareConfig()
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .centerCrop()
                .dontAnimate()
                .transition(ThumbnailFades.thumbnail())
                .placeholder(new ColorDrawable(Color.rgb(18, 20, 23)))
                .error(new ColorDrawable(Color.rgb(18, 20, 23)))
                .into(view);
    }

    @Override
    protected void onDetachedFromWindow() {
        clear();
        super.onDetachedFromWindow();
    }
}
