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
 * Compose owns the keyed lazy grid. Existing stores, card actions and result extras are retained.
 */
public class FavoritesActivity extends androidx.activity.ComponentActivity {
    public static final String EXTRA_SELECTED_URL = "selected_url";
    public static final String EXTRA_SELECTED_TITLE = "selected_title";
    public static final String EXTRA_SELECTED_TAB = "selected_tab";
    public static final String EXTRA_START_TAB = "start_tab";
    public static final int START_CONTINUE = 0;
    public static final int START_HISTORY = 1;
    public static final int START_WATCH_LATER = 2;

    private static final int TAB_CONTINUE = 0;
    private static final int TAB_HISTORY = 1;
    private static final int TAB_WATCH_LATER = 2;
    private static final String SITE = "https://crazyshit.com/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private LibraryCompose presentation;
    private int tab = TAB_CONTINUE;

    private final Map<String, List<ImageView>> thumbnailTargets = new HashMap<>();
    private final Map<String, String> resolvedThumbnails = new HashMap<>();
    private final Set<String> requestedThumbnails = new HashSet<>();
    private RenderedThumbnailResolver[] thumbnailResolvers;
    private int resolverCursor;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        tab = getIntent() == null
                ? TAB_CONTINUE
                : getIntent().getIntExtra(EXTRA_START_TAB, TAB_CONTINUE);
        if (tab < TAB_CONTINUE || tab > TAB_WATCH_LATER) tab = TAB_CONTINUE;
        getWindow().setStatusBarColor(Color.rgb(13, 13, 15));
        getWindow().setNavigationBarColor(Color.BLACK);
        thumbnailResolvers = new RenderedThumbnailResolver[] {
                new RenderedThumbnailResolver(this, this::onThumbnailResolved),
                new RenderedThumbnailResolver(this, this::onThumbnailResolved)
        };
        buildUi();
        if (state != null) presentation.setQuery(state.getString("query", ""));
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderAllPages();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("query", searchQuery());
        super.onSaveInstanceState(state);
    }

    private void buildUi() {
        ZeroChillUi.applySystemBars(this);
        presentation = new LibraryCompose(this, sectionTitle(), value -> renderAllPages());
        presentation.setActionLabel("Section options");
        presentation.setAction(() -> showSectionMenu(getWindow().getDecorView()));
        presentation.install(null);
    }

    private String sectionTitle() {
        if (tab == TAB_HISTORY) return "History";
        if (tab == TAB_WATCH_LATER) return "Watch Later";
        return "Continue Watching";
    }


    private void renderAllPages() {
        if (presentation == null) return;
        List<LibraryCompose.Card> cards = new ArrayList<>();
        String query = searchQuery();
        int total;
        String empty;
        if (tab == TAB_WATCH_LATER) {
            List<FavoriteStore.Item> all = FavoriteStore.load(this);
            total = all.size();
            for (FavoriteStore.Item item : all) {
                if (LibrarySearch.matches(query, item.title, item.url,
                        item.content == null ? "" : OnlyFapVideoMetadata.displayTitle(item.content))) {
                    cards.add(new LibraryCompose.Card(item.url, item.url + item.savedAt + item.title,
                            () -> makeWatchLaterCard(item)));
                }
            }
            empty = "Nothing saved yet\n\nLong-press a video card and choose Save to Watch Later.";
        } else {
            boolean continuing = tab == TAB_CONTINUE;
            List<PlaybackHistoryStore.Item> all = continuing
                    ? PlaybackHistoryStore.continueWatching(this) : PlaybackHistoryStore.load(this);
            total = all.size();
            for (PlaybackHistoryStore.Item item : all) {
                if (LibrarySearch.matches(query, item.title,
                        item.content == null ? "" : OnlyFapVideoMetadata.displayTitle(item.content),
                        item.pageUrl, item.fromShows ? "shows" : "")) {
                    cards.add(new LibraryCompose.Card(item.pageUrl,
                            item.pageUrl + item.title + item.lastWatched + item.positionMs + item.durationMs + item.complete,
                            () -> makeHistoryCard(item, continuing)));
                }
            }
            empty = continuing ? "Nothing to continue\n\nVideos watched for at least 30 seconds appear here until they're nearly finished."
                    : "No watch history yet\n\nVideos you watch in the native player will appear here.";
        }
        updateCount(cards.size(), total, tab == TAB_WATCH_LATER ? "Saved" : tab == TAB_HISTORY ? "Recent first" : "In progress");
        presentation.setEmptyMessage(total == 0 ? empty : cards.isEmpty()
                ? "No matching videos\n\nTry a different title, source, or keyword." : "");
        presentation.showCards(cards);
    }

    private View makeHistoryCard(PlaybackHistoryStore.Item item, boolean continueOnly) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setClickable(true);
        wrapper.setFocusable(true);
        wrapper.setContentDescription((continueOnly ? "Continue watching " : "History item ") + item.title);
        wrapper.setOnClickListener(v -> select(item.pageUrl));
        ZeroChillMotion.installPressFeedback(wrapper);

        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(UiPalette.SURFACE);
        card.setRadius(dp(16));
        card.setCardElevation(0f);
        card.setStrokeWidth(0);

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
            fill.setBackground(rounded(UiPalette.PRIMARY, dp(2)));
            int width = Math.max(dp(3), Math.round(dp(142) * (item.progressPercent() / 100f)));
            track.addView(fill, new FrameLayout.LayoutParams(width, -1));
        }

        TextView more = overflowButton("Options for " + item.title);
        more.setOnClickListener(v -> showHistoryMenu(v, item, continueOnly));
        FrameLayout.LayoutParams moreParams =
                new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.TOP | Gravity.END);
        moreParams.setMargins(0, dp(5), dp(5), 0);
        media.addView(more, moreParams);

        wrapper.addView(card, new LinearLayout.LayoutParams(-1, dp(102)));

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
        return wrapper;
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

    private View makeWatchLaterCard(FavoriteStore.Item item) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setClickable(true);
        wrapper.setFocusable(true);
        wrapper.setContentDescription("Watch Later " + item.title);
        wrapper.setOnClickListener(v -> select(item.url, item.title));
        ZeroChillMotion.installPressFeedback(wrapper);

        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(UiPalette.SURFACE);
        card.setRadius(dp(16));
        card.setCardElevation(0f);
        card.setStrokeWidth(0);

        FrameLayout media = new FrameLayout(this);
        card.addView(media, new MaterialCardView.LayoutParams(-1, -1));
        media.addView(makeThumbnail(item.url), new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(bottomShade());
        media.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        TextView title = text(OnlyFapVideoMetadata.displayTitle(item.content == null
                ? OnlyFapVideoMetadata.basic(item.title, item.url, "") : item.content), 13, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams titleParams =
                new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        titleParams.setMargins(dp(10), 0, dp(34), dp(10));
        media.addView(title, titleParams);

        TextView more = overflowButton("Options for " + item.title);
        more.setOnClickListener(v -> showWatchLaterMenu(v, item));
        FrameLayout.LayoutParams moreParams =
                new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.TOP | Gravity.END);
        moreParams.setMargins(0, dp(5), dp(5), 0);
        media.addView(more, moreParams);

        wrapper.addView(card, new LinearLayout.LayoutParams(-1, dp(102)));

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
        return wrapper;
    }

    private void showWatchLaterMenu(View anchor, FavoriteStore.Item item) {
        ZeroChillMenu menu = new ZeroChillMenu(this, anchor);
        menu.getMenu().add("Remove from Watch Later");
        menu.setOnMenuItemClickListener(clicked -> {
            haptic(anchor);
            FavoriteStore.remove(this, item.url);
            ZeroChillToast.makeText(this, "Removed from Watch Later.", ZeroChillToast.LENGTH_SHORT).show();
            renderAllPages();
            return true;
        });
        menu.show();
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
        image.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View view) { }
            public void onViewDetachedFromWindow(View view) {
                List<ImageView> targets = thumbnailTargets.get(pageUrl);
                if (targets != null) { targets.remove(image); if (targets.isEmpty()) thumbnailTargets.remove(pageUrl); }
            }
        });
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
        if (isFinishing() || isDestroyed()) return;
        if (pageUrl == null || pageUrl.isEmpty() || thumbnailUrl == null || thumbnailUrl.isEmpty()) return;
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
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .dontAnimate()
                    .transition(ThumbnailFades.thumbnail())
                    .placeholder(new ColorDrawable(UiPalette.SURFACE))
                    .error(new ColorDrawable(UiPalette.SURFACE))
                    .into(image);
        } catch (Exception ignored) {
        }
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

    private void select(String url) {
        select(url, "");
    }

    private void select(String url, String title) {
        haptic(getWindow().getDecorView());
        Intent data = new Intent();
        data.putExtra(EXTRA_SELECTED_URL, url);
        data.putExtra(EXTRA_SELECTED_TITLE, title);
        data.putExtra(EXTRA_SELECTED_TAB, tab);
        setResult(RESULT_OK, data);
        finish();
    }

    private String searchQuery() {
        return presentation == null ? "" : presentation.getQuery();
    }

    private void updateCount(int visible, int total, String label) {
        if (presentation == null) return;
        String noun = total == 1 ? "video" : "videos";
        if (LibrarySearch.normalize(searchQuery()).isEmpty()) {
            presentation.setSummary(total + " " + noun + " · " + label);
        } else {
            presentation.setSummary(visible + " of " + total + " " + noun + " · Search results");
        }
    }

    private void showSectionMenu(View anchor) {
        ZeroChillMenu menu = new ZeroChillMenu(this, anchor);
        String label = tab == TAB_WATCH_LATER
                ? "Clear Watch Later"
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
                .setTitle(watchLater ? "Clear Watch Later?" : "Clear watch history?")
                .setMessage(watchLater
                        ? "This removes every saved Watch Later item from this device."
                        : "This clears History and Continue Watching from this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    if (watchLater) FavoriteStore.clear(this);
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

    @Override protected void onDestroy() {
        if (thumbnailResolvers != null) for (RenderedThumbnailResolver resolver : thumbnailResolvers) resolver.close();
        thumbnailTargets.clear();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

}
