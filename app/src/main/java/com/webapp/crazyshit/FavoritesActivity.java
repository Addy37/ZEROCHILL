package com.webapp.crazyshit;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

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
 * Compose owns the keyed lazy grid. Existing stores, image requests and result extras are retained.
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
        presentation.setSectionActionLabel(tab == TAB_WATCH_LATER ? "Clear Watch Later" : "Clear watch history");
        presentation.setAction(this::confirmClear);
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
                    cards.add(watchLaterCard(item));
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
                    cards.add(historyCard(item, continuing));
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

    private LibraryCompose.Card historyCard(PlaybackHistoryStore.Item item, boolean continueOnly) {
        String title = OnlyFapVideoMetadata.displayTitle(item.content == null
                ? OnlyFapVideoMetadata.basic(item.title, item.pageUrl, item.posterUrl) : item.content);
        String detail = continueOnly
                ? (item.durationMs > 0L ? item.progressPercent() + "% watched" : formatTime(item.positionMs) + " watched")
                : item.lastWatched > 0L
                    ? (item.complete ? "Watched " : "Resume · ") + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(new Date(item.lastWatched)) : "Watched";
        boolean progress = !item.complete && item.durationMs > 0L;
        return new LibraryCompose.Card(item.pageUrl, title,
                (continueOnly ? "Continue watching " : "History item ") + item.title, detail,
                progress ? formatTime(Math.max(0L, item.durationMs - item.positionMs)) + " left" : "",
                progress ? item.progressPercent() / 100f : -1f,
                () -> makeThumbnail(item.pageUrl), this::releaseThumbnail, () -> select(item.pageUrl),
                continueOnly ? "Remove from Continue Watching" : "Delete from history", () -> {
                    haptic(getWindow().getDecorView());
                    PlaybackHistoryStore.remove(this, item.pageUrl);
                    renderAllPages();
                });
    }

    private LibraryCompose.Card watchLaterCard(FavoriteStore.Item item) {
        String title = OnlyFapVideoMetadata.displayTitle(item.content == null
                ? OnlyFapVideoMetadata.basic(item.title, item.url, "") : item.content);
        String detail = item.savedAt > 0L ? "Saved " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(item.savedAt)) : "Saved";
        return new LibraryCompose.Card(item.url, title, "Watch Later " + item.title, detail, "", -1f,
                () -> makeThumbnail(item.url), this::releaseThumbnail, () -> select(item.url, item.title),
                "Remove from Watch Later", () -> {
                    haptic(getWindow().getDecorView());
                    FavoriteStore.remove(this, item.url);
                    ZeroChillToast.makeText(this, "Removed from Watch Later.", ZeroChillToast.LENGTH_SHORT).show();
                    renderAllPages();
                });
    }

    private ImageView makeThumbnail(String pageUrl) {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(UiPalette.SURFACE);
        image.setTag(pageUrl);
        thumbnailTargets.computeIfAbsent(pageUrl, key -> new ArrayList<>()).add(image);
        String resolved = resolvedThumbnails.get(pageUrl);
        if (resolved != null && !resolved.isEmpty()) loadThumbnail(image, resolved, pageUrl);
        else requestThumbnail(pageUrl);
        return image;
    }

    private void releaseThumbnail(ImageView image) {
        String pageUrl = String.valueOf(image.getTag());
        List<ImageView> targets = thumbnailTargets.get(pageUrl);
        if (targets != null) {
            targets.remove(image);
            if (targets.isEmpty()) thumbnailTargets.remove(pageUrl);
        }
        Glide.with(image).clear(image);
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

    private void confirmClear() {
        haptic(getWindow().getDecorView());
        boolean watchLater = tab == TAB_WATCH_LATER;
        presentation.confirm(watchLater ? "Clear Watch Later?" : "Clear watch history?",
                watchLater ? "This removes every saved Watch Later item from this device."
                        : "This clears History and Continue Watching from this device.", () -> {
                    if (watchLater) FavoriteStore.clear(this);
                    else PlaybackHistoryStore.clear(this);
                    renderAllPages();
                });
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

}
