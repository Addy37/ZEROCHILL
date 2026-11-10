package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Dedicated Library section destination.
 *
 * The Library hub chooses Continue Watching, History, or Watch Later before launching this screen.
 * ViewPager2 remains as the compatibility container, but user paging is disabled so each launch
 * behaves as a standalone destination.
 */
public class FavoritesActivity extends Activity {
    public static final String EXTRA_SELECTED_URL = "selected_url";
    public static final String EXTRA_SELECTED_TITLE = "selected_title";
    public static final String EXTRA_SELECTED_TAB = "selected_tab";
    public static final String EXTRA_MEDIA_FAVORITES = "media_favorites";
    private boolean mediaFavorites;
    public static final String EXTRA_START_TAB = "start_tab";
    public static final int START_CONTINUE = 0;
    public static final int START_HISTORY = 1;
    public static final int START_WATCH_LATER = 2;

    private static final int TAB_CONTINUE = 0;
    private static final int TAB_HISTORY = 1;
    private static final int TAB_WATCH_LATER = 2;
    private static final int PAGE_COUNT = 3;
    private static final String SITE = "https://crazyshit.com/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private final LinearLayout[] listContainers = new LinearLayout[PAGE_COUNT];
    private final View[] pageViews = new View[PAGE_COUNT];
    private TextView clearAction;
    private EditText input;
    private TextView count;
    private ViewPager2 pager;
    private int tab = TAB_CONTINUE;

    private final Map<String, List<ImageView>> thumbnailTargets = new HashMap<>();
    private final Map<String, String> resolvedThumbnails = new HashMap<>();
    private final Map<String, Boolean> portraitThumbnails = new HashMap<>();
    private LibraryMixedMediaGrid activeMixedGrid;
    private final Set<String> requestedThumbnails = new HashSet<>();
    private RenderedThumbnailResolver[] thumbnailResolvers;
    private int resolverCursor;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LibraryDestinationChrome.prepareWindow(this);
        tab = getIntent() == null
                ? TAB_CONTINUE
                : getIntent().getIntExtra(EXTRA_START_TAB, TAB_CONTINUE);
        mediaFavorites = getIntent() != null && getIntent().getBooleanExtra(EXTRA_MEDIA_FAVORITES, false);
        if (tab < TAB_CONTINUE || tab > TAB_WATCH_LATER) tab = TAB_CONTINUE;
        getWindow().setStatusBarColor(Color.rgb(13, 13, 15));
        getWindow().setNavigationBarColor(Color.BLACK);
        thumbnailResolvers = new RenderedThumbnailResolver[] {
                new RenderedThumbnailResolver(this, this::onThumbnailResolved, false),
                new RenderedThumbnailResolver(this, this::onThumbnailResolved, false)
        };
        buildUi();
        if (state != null && input != null) {
            input.setText(state.getString("query", ""));
            input.setSelection(input.length());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        for (RenderedThumbnailResolver resolver : thumbnailResolvers) resolver.setActive(true);
        renderAllPages();
    }

    @Override protected void onPause() {
        for (RenderedThumbnailResolver resolver : thumbnailResolvers) resolver.setActive(false);
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (activeMixedGrid != null) activeMixedGrid.dispose();
        for (RenderedThumbnailResolver resolver : thumbnailResolvers) resolver.close();
        for (List<ImageView> targets : thumbnailTargets.values())
            for (ImageView image : targets) Glide.with(image).clear(image);
        thumbnailTargets.clear();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("query", searchQuery());
        super.onSaveInstanceState(state);
    }

    private void buildUi() {
        LinearLayout root = BrowseUi.screen(this);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));

        TextView back = BrowseUi.action(this, "‹", "Back", v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = BrowseUi.text(this, sectionTitle(), 20, Color.WHITE);
        title.setPadding(dp(12), 0, 0, 0);
        title.setContentDescription(sectionTitle() + " section");
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        clearAction = BrowseUi.action(this, "⋮", "Section options", this::showSectionMenu);
        clearAction.setTextSize(24);
        header.addView(clearAction, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LibraryDestinationChrome.attachHeader(root, header, title);

        input = new ZeroChillEditText(this);
        input.setHint("Search " + sectionTitle());
        input.setHintTextColor(BrowseUi.MUTED);
        input.setTextColor(Color.WHITE);
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setPadding(dp(14), 0, dp(14), 0);
        LibraryDestinationChrome.styleSearch(this, input);
        input.setContentDescription("Search " + sectionTitle());
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, dp(50));
        inputParams.setMargins(dp(12), 0, dp(12), dp(8));
        root.addView(input, inputParams);

        count = BrowseUi.text(this, "", 12, BrowseUi.MUTED);
        count.setPadding(dp(16), dp(4), dp(16), dp(8));
        LibraryDestinationChrome.styleSummary(this, count);
        root.addView(count);

        for (int i = 0; i < PAGE_COUNT; i++) pageViews[i] = buildPage(i);

        pager = new ViewPager2(this);
        pager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        pager.setUserInputEnabled(false);
        pager.setOffscreenPageLimit(PAGE_COUNT - 1);
        pager.setAdapter(new LibraryPagerAdapter());
        pager.setCurrentItem(tab, false);
        root.addView(pager, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        input.addTextChangedListener(BrowseUi.onText(value -> renderAllPages()));
        getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                        | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        );
    }

    private View buildPage(int index) {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.BLACK);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(6), dp(12), dp(24));
        listContainers[index] = list;
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        return page;
    }

    private String sectionTitle() {
        if (tab == TAB_HISTORY) return "History";
        if (tab == TAB_WATCH_LATER) return mediaFavorites ? "Favorites" : "Watch Later";
        return "Continue Watching";
    }


    private void renderAllPages() {
        if (activeMixedGrid != null) {
            activeMixedGrid.dispose();
            activeMixedGrid = null;
        }
        for (List<ImageView> targets : thumbnailTargets.values())
            for (ImageView image : targets) Glide.with(image).clear(image);
        thumbnailTargets.clear();
        for (LinearLayout list : listContainers) {
            if (list != null) list.removeAllViews();
        }
        if (tab == TAB_HISTORY) {
            renderHistory(listContainers[TAB_HISTORY], false);
        } else if (tab == TAB_WATCH_LATER) {
            renderWatchLater(listContainers[TAB_WATCH_LATER]);
        } else {
            renderHistory(listContainers[TAB_CONTINUE], true);
        }
    }

    private void renderHistory(LinearLayout target, boolean continueOnly) {
        if (target == null) return;
        List<PlaybackHistoryStore.Item> allItems = continueOnly
                ? PlaybackHistoryStore.continueWatching(this)
                : PlaybackHistoryStore.load(this);

        String query = searchQuery();
        List<PlaybackHistoryStore.Item> items = new ArrayList<>();
        for (PlaybackHistoryStore.Item item : allItems) {
            if (LibrarySearch.matches(
                    query,
                    item.title,
                    item.content == null ? "" : OnlyFapVideoMetadata.displayTitle(item.content),
                    item.pageUrl,
                    item.fromShows ? "shows" : ""
            )) {
                items.add(item);
            }
        }
        updateCount(items.size(), allItems.size(), continueOnly ? "In progress" : "Recent first");

        if (allItems.isEmpty()) {
            showEmpty(
                    target,
                    continueOnly ? "Nothing to continue" : "No watch history yet",
                    continueOnly
                            ? "Videos watched for at least 30 seconds appear here until they're nearly finished."
                            : "Videos you watch in the native player will appear here."
            );
            return;
        }
        if (items.isEmpty()) {
            showEmpty(target, "No matching videos", "Try a different title, source, or keyword.");
            return;
        }

        List<View> cards = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (PlaybackHistoryStore.Item item : items) {
            if (item.posterUrl != null && !item.posterUrl.isEmpty()
                    && !resolvedThumbnails.containsKey(item.pageUrl))
                resolvedThumbnails.put(item.pageUrl, item.posterUrl);
            cards.add(makeHistoryCard(item, continueOnly));
            keys.add(item.pageUrl);
        }
        addMixedGallery(target, cards, keys);
    }

    private View makeHistoryCard(PlaybackHistoryStore.Item item, boolean continueOnly) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setClickable(true);
        wrapper.setFocusable(true);
        wrapper.setContentDescription((continueOnly ? "Continue watching " : "History item ") + item.title);
        wrapper.setOnClickListener(v -> select(item.pageUrl));
        ZeroChillMotion.installPressFeedback(wrapper);

        LibraryAspectMediaCard card = new LibraryAspectMediaCard(this);
        card.setCardBackgroundColor(UiPalette.SURFACE);
        card.setRadius(dp(16));
        card.setCardElevation(0f);
        LibraryDestinationChrome.styleMediaCard(this, card);

        FrameLayout media = new FrameLayout(this);
        card.addView(media, new MaterialCardView.LayoutParams(-1, -1));
        media.addView(makeThumbnail(item.pageUrl), new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(bottomShade());
        media.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        TextView title = text(OnlyFapVideoMetadata.displayTitle(item.content == null
                ? OnlyFapVideoMetadata.basic(item.title, item.pageUrl, item.posterUrl) : item.content), 13, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams titleParams =
                new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        titleParams.setMargins(dp(10), 0, dp(34), dp(11));
        media.addView(title, titleParams);

        if (!item.complete && item.durationMs > 0L) {
            long remainingMs = Math.max(0L, item.durationMs - item.positionMs);
            TextView remaining = overlayPill(formatTime(remainingMs) + " left");
            FrameLayout.LayoutParams remainingParams =
                    new FrameLayout.LayoutParams(-2, dp(24), Gravity.TOP | Gravity.START);
            remainingParams.setMargins(dp(8), dp(8), 0, 0);
            media.addView(remaining, remainingParams);

            FrameLayout track = new FrameLayout(this);
            track.setBackground(rounded(Color.argb(110, 255, 255, 255), dp(2)));
            FrameLayout.LayoutParams trackParams =
                    new FrameLayout.LayoutParams(-1, dp(3), Gravity.BOTTOM);
            trackParams.setMargins(dp(8), 0, dp(8), dp(6));
            media.addView(track, trackParams);

            View fill = new View(this);
            fill.setBackground(rounded(AppearanceTheme.accent(this), dp(2)));
            int width = Math.max(dp(3), Math.round(dp(142) * (item.progressPercent() / 100f)));
            track.addView(fill, new FrameLayout.LayoutParams(width, -1));
        }

        TextView more = overflowButton("Options for " + item.title);
        more.setOnClickListener(v -> showHistoryMenu(v, item, continueOnly));

        wrapper.addView(card, new LinearLayout.LayoutParams(-1, -2));

        String meta;
        if (continueOnly) {
            meta = item.durationMs > 0L
                    ? item.progressPercent() + "% watched"
                    : formatTime(item.positionMs) + " watched";
        } else {
            meta = item.lastWatched > 0L
                    ? (item.complete ? "Watched " : "Resume · ") + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(item.lastWatched))
                    : "Watched";
        }
        TextView detail = text(meta, 12, UiPalette.TEXT_MUTED);
        detail.setMaxLines(1);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.setMargins(dp(3), dp(6), dp(3), dp(2));
        wrapper.addView(detail, detailParams);
        return LibraryOverlayActionCard.wrap(this, wrapper, more);
    }

    private void showHistoryMenu(View anchor, PlaybackHistoryStore.Item item, boolean continueOnly) {
        ZeroChillMenu menu = new ZeroChillMenu(this, anchor);
        menu.getMenu().add(continueOnly ? "Remove from Continue Watching" : "Delete from history");
        menu.setOnMenuItemClickListener(clicked -> {
            haptic(anchor);
            PlaybackHistoryStore.remove(this, item.pageUrl);
            renderAllPages();
            return true;
        });
        menu.show();
    }

    private void renderWatchLater(LinearLayout target) {
        if (target == null) return;
        List<FavoriteStore.Item> allItems = mediaFavorites ? FavoriteStore.mediaFavorites(this) : FavoriteStore.watchLater(this);
        String query = searchQuery();
        List<FavoriteStore.Item> items = new ArrayList<>();
        for (FavoriteStore.Item item : allItems) {
            if (LibrarySearch.matches(query, item.title, item.url,
                    item.content == null ? "" : OnlyFapVideoMetadata.displayTitle(item.content))) {
                items.add(item);
            }
        }
        updateCount(items.size(), allItems.size(), "Saved");

        if (allItems.isEmpty()) {
            showEmpty(target, "Nothing saved yet", mediaFavorites ? "Long-press a gallery item and choose Favorite." : "Long-press a video card and choose Save to Watch Later.");
            return;
        }
        if (items.isEmpty()) {
            showEmpty(target, "No matching videos", "Try a different title, source, or keyword.");
            return;
        }

        List<View> cards = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (FavoriteStore.Item item : items) {
            cards.add(makeWatchLaterCard(item));
            keys.add(item.url);
        }
        addMixedGallery(target, cards, keys);
    }

    private View makeWatchLaterCard(FavoriteStore.Item item) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setClickable(true);
        wrapper.setFocusable(true);
        wrapper.setContentDescription((mediaFavorites ? "Favorite " : "Watch Later ") + item.title);
        wrapper.setOnClickListener(v -> {
            if (mediaFavorites && item.content != null) SavedGalleryMedia.open(this, item);
            else select(item.url, item.title);
        });
        ZeroChillMotion.installPressFeedback(wrapper);

        LibraryAspectMediaCard card = new LibraryAspectMediaCard(this);
        card.setCardBackgroundColor(UiPalette.SURFACE);
        card.setRadius(dp(16));
        card.setCardElevation(0f);
        LibraryDestinationChrome.styleMediaCard(this, card);

        FrameLayout media = new FrameLayout(this);
        card.addView(media, new MaterialCardView.LayoutParams(-1, -1));
        if (item.content != null && !item.content.imageUrl.isEmpty())
            resolvedThumbnails.put(item.url, item.content.imageUrl);
        FrameLayout thumbnail = makeThumbnail(item.url);
        if (item.content != null && item.content.isImage() && thumbnail.getChildCount() > 1)
            thumbnail.getChildAt(1).setVisibility(View.GONE);
        media.addView(thumbnail, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(bottomShade());
        media.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        TextView title = text(OnlyFapVideoMetadata.displayTitle(item.content == null
                ? OnlyFapVideoMetadata.basic(item.title, item.url, "") : item.content), 13, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        if (mediaFavorites && item.content != null && item.content.isImage()) {
            title.setMinHeight(dp(48));
            title.setGravity(Gravity.BOTTOM);
            title.setFocusable(true);
            title.setContentDescription("Open gallery for " + title.getText());
            title.setOnClickListener(v -> SavedGalleryMedia.openCreator(this, item));
        }
        FrameLayout.LayoutParams titleParams =
                new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        titleParams.setMargins(dp(10), 0, dp(34), dp(10));
        media.addView(title, titleParams);

        TextView more = overflowButton("Options for " + item.title);
        more.setOnClickListener(v -> showWatchLaterMenu(v, item));

        wrapper.addView(card, new LinearLayout.LayoutParams(-1, -2));

        String date = item.savedAt > 0L
                ? "Saved " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(item.savedAt))
                : "Saved";
        TextView saved = text(date, 12, UiPalette.TEXT_MUTED);
        saved.setMaxLines(1);
        saved.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams savedParams = new LinearLayout.LayoutParams(-1, -2);
        savedParams.setMargins(dp(3), dp(6), dp(3), dp(2));
        wrapper.addView(saved, savedParams);
        return LibraryOverlayActionCard.wrap(this, wrapper, more);
    }

    private void showWatchLaterMenu(View anchor, FavoriteStore.Item item) {
        ZeroChillMenu menu = new ZeroChillMenu(this, anchor);
        if (mediaFavorites && item.content != null && item.content.isImage()) {
            Intent gallery = SavedGalleryMedia.creatorGallery(this, item);
            menu.getMenu().add("Open gallery").setEnabled(gallery != null)
                    .setOnMenuItemClickListener(clicked -> { startActivity(gallery); return true; });
        }
        menu.getMenu().add(mediaFavorites ? "Remove from favorites" : "Remove from Watch Later");
        menu.setOnMenuItemClickListener(clicked -> {
            haptic(anchor);
            if (mediaFavorites && item.content != null) FavoriteStore.setMediaFavorite(this, item.content, false);
            else FavoriteStore.remove(this, item.url);
            ZeroChillToast.makeText(this, mediaFavorites ? "Removed from Favorites." : "Removed from Watch Later.", ZeroChillToast.LENGTH_SHORT).show();
            renderAllPages();
            return true;
        });
        menu.show();
    }

    private void addMixedGallery(LinearLayout target, List<View> cards, List<String> keys) {
        activeMixedGrid = new LibraryMixedMediaGrid(this);
        activeMixedGrid.setItems(cards, keys, portraitThumbnails);
        target.addView(activeMixedGrid, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView overflowButton(String description) {
        TextView view = text("⋮", 22, Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(description);
        view.setClickable(true);
        view.setFocusable(true);
        view.setBackground(rounded(Color.argb(150, 0, 0, 0), dp(17)));
        return view;
    }

    private TextView overlayPill(String value) {
        TextView view = text(value, 9, Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), 0, dp(8), 0);
        view.setBackground(rounded(Color.argb(180, 0, 0, 0), dp(12)));
        return view;
    }

    private GradientDrawable bottomShade() {
        GradientDrawable shade = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        Color.TRANSPARENT,
                        Color.argb(32, 0, 0, 0),
                        Color.argb(225, 0, 0, 0)
                }
        );
        return shade;
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(color);
        background.setCornerRadius(radius);
        return background;
    }

    private FrameLayout makeThumbnail(String pageUrl) {
        FrameLayout media = new FrameLayout(this);
        media.setBackgroundColor(UiPalette.SURFACE);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(UiPalette.SURFACE);
        media.addView(image, new FrameLayout.LayoutParams(-1, -1));

        TextView play = text("▶", 20, Color.WHITE);
        play.setGravity(Gravity.CENTER);
        play.setBackground(new ColorDrawable(Color.argb(115, 0, 0, 0)));
        FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(dp(38), dp(38));
        playParams.gravity = Gravity.CENTER;
        media.addView(play, playParams);

        thumbnailTargets.computeIfAbsent(pageUrl, key -> new ArrayList<>()).add(image);
        String resolved = resolvedThumbnails.get(pageUrl);
        if (resolved != null && !resolved.isEmpty()) {
            loadThumbnail(image, resolved, pageUrl);
        } else {
            requestThumbnail(pageUrl);
        }
        return media;
    }

    private void requestThumbnail(String pageUrl) {
        if (pageUrl == null || pageUrl.isEmpty() || thumbnailResolvers == null) return;
        if (!requestedThumbnails.add(pageUrl)) return;
        RenderedThumbnailResolver resolver = thumbnailResolvers[resolverCursor++ % thumbnailResolvers.length];
        resolver.request(pageUrl);
    }

    private void onThumbnailResolved(String pageUrl, String thumbnailUrl) {
        if (isFinishing() || isDestroyed() || pageUrl == null || pageUrl.isEmpty()
                || thumbnailUrl == null || thumbnailUrl.isEmpty()) return;
        resolvedThumbnails.put(pageUrl, thumbnailUrl);
        List<ImageView> targets = thumbnailTargets.get(pageUrl);
        if (targets == null) return;
        for (ImageView target : new ArrayList<>(targets)) {
            loadThumbnail(target, thumbnailUrl, pageUrl);
        }
    }

    private void loadThumbnail(ImageView image, String imageUrl, String pageUrl) {
        if (isFinishing() || image == null || imageUrl == null || imageUrl.isEmpty()) return;
        Object source = imageUrl.startsWith("file://") ? imageUrl : withSiteHeaders(imageUrl, pageUrl);
        try {
            Glide.with(image)
                    .load(source)
                    .dontTransform()
                    .downsample(com.bumptech.glide.load.resource.bitmap.DownsampleStrategy.AT_MOST)
                    .override(dp(512), dp(512))
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException error,
                                Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                boolean first) { return false; }

                        @Override public boolean onResourceReady(android.graphics.drawable.Drawable drawable,
                                Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                com.bumptech.glide.load.DataSource source, boolean first) {
                            if (drawable.getIntrinsicWidth() > 0 && drawable.getIntrinsicHeight() > 0) {
                                boolean portrait = drawable.getIntrinsicHeight() > drawable.getIntrinsicWidth();
                                image.post(() -> {
                                    if (!isFinishing() && !isDestroyed() && image.isAttachedToWindow())
                                        updateThumbnailOrientation(pageUrl, portrait);
                                });
                            }
                            return false;
                        }
                    })
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .dontAnimate()
                    .transition(ThumbnailFades.thumbnail())
                    .placeholder(new ColorDrawable(UiPalette.SURFACE))
                    .error(new ColorDrawable(UiPalette.SURFACE))
                    .into(image);
        } catch (Exception ignored) {
        }
    }

    private void updateThumbnailOrientation(String pageUrl, boolean portrait) {
        Boolean known = portraitThumbnails.put(pageUrl, portrait);
        if (known != null && known == portrait) return;
        if (activeMixedGrid != null) activeMixedGrid.updateOrientation(pageUrl, portrait);
    }

    private GlideUrl withSiteHeaders(String imageUrl, String pageUrl) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", pageUrl == null || pageUrl.isEmpty() ? SITE : pageUrl)
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
        try {
            String cookies = CookieManager.getInstance().getCookie(imageUrl);
            if (cookies == null || cookies.trim().isEmpty()) {
                cookies = CookieManager.getInstance().getCookie(pageUrl == null ? SITE : pageUrl);
            }
            if (cookies == null || cookies.trim().isEmpty()) {
                cookies = CookieManager.getInstance().getCookie(SITE);
            }
            if (cookies != null && !cookies.trim().isEmpty()) headers.addHeader("Cookie", cookies);
        } catch (Exception ignored) {
        }
        return new GlideUrl(imageUrl, headers.build());
    }

    private MaterialButton compactAction(String label) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        return button;
    }

    private void showEmpty(LinearLayout target, String titleValue, String bodyValue) {
        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), dp(72), dp(24), dp(24));
        TextView title = text(titleValue, 20, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView body = text(bodyValue, 14, UiPalette.TEXT_SECONDARY);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, dp(8), 0, 0);
        empty.addView(title);
        empty.addView(body);
        target.addView(empty);
    }

    private void select(String url) {
        select(url, "");
    }

    private void select(String url, String title) {
        haptic(pager == null ? clearAction : pager);
        Intent data = new Intent();
        data.putExtra(EXTRA_SELECTED_URL, url);
        data.putExtra(EXTRA_SELECTED_TITLE, title);
        data.putExtra(EXTRA_SELECTED_TAB, tab);
        setResult(RESULT_OK, data);
        finish();
    }

    private String searchQuery() {
        return input == null ? "" : input.getText().toString();
    }

    private void updateCount(int visible, int total, String label) {
        if (count == null) return;
        String noun = total == 1 ? "video" : "videos";
        if (LibrarySearch.normalize(searchQuery()).isEmpty()) {
            count.setText(total + " " + noun + " · " + label);
        } else {
            count.setText(visible + " of " + total + " " + noun + " · Search results");
        }
    }

    private void showSectionMenu(View anchor) {
        ZeroChillMenu menu = new ZeroChillMenu(this, anchor);
        String label = tab == TAB_WATCH_LATER
                ? (mediaFavorites ? "Clear Favorites" : "Clear Watch Later")
                : "Clear watch history";
        menu.getMenu().add(label);
        menu.setOnMenuItemClickListener(item -> {
            haptic(anchor);
            confirmClear();
            return true;
        });
        menu.show();
    }

    private void confirmClear() {
        boolean watchLater = tab == TAB_WATCH_LATER;
        new ZeroChillDialog.Builder(this).destructive()
                .setTitle(watchLater ? (mediaFavorites ? "Clear Favorites?" : "Clear Watch Later?") : "Clear watch history?")
                .setMessage(watchLater
                        ? (mediaFavorites ? "This removes every media favorite from this device." : "This removes every saved Watch Later item from this device.")
                        : "This clears History and Continue Watching from this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    if (watchLater && mediaFavorites) FavoriteStore.clearMediaFavorites(this);
                    else if (watchLater) FavoriteStore.clear(this);
                    else PlaybackHistoryStore.clear(this);
                    renderAllPages();
                })
                .show();
    }

    private MaterialCardView card() {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.rgb(24, 24, 28));
        card.setRadius(dp(20));
        card.setStrokeWidth(1);
        card.setStrokeColor(Color.rgb(45, 45, 52));
        card.setCardElevation(0f);
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(5), 0, dp(5));
        return params;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private String formatTime(long milliseconds) {
        long total = Math.max(0L, milliseconds) / 1000L;
        long hours = total / 3600L;
        long minutes = (total % 3600L) / 60L;
        long seconds = total % 60L;
        if (hours > 0L) return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    private void haptic(View view) {
        if (view == null) return;
        if (!getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("haptics_enabled", true)) return;
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class LibraryPagerAdapter extends RecyclerView.Adapter<PageHolder> {
        @Override
        public int getItemCount() {
            return PAGE_COUNT;
        }

        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout container = new FrameLayout(parent.getContext());
            container.setLayoutParams(new RecyclerView.LayoutParams(-1, -1));
            return new PageHolder(container);
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            View page = pageViews[position];
            if (page.getParent() instanceof ViewGroup) {
                ((ViewGroup) page.getParent()).removeView(page);
            }
            holder.container.removeAllViews();
            holder.container.addView(page, new FrameLayout.LayoutParams(-1, -1));
        }
    }

    private static final class PageHolder extends RecyclerView.ViewHolder {
        final FrameLayout container;

        PageHolder(FrameLayout container) {
            super(container);
            this.container = container;
        }
    }
}
