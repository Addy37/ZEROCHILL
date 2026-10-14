package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.ViewGroup;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Personal ZEROCHILL media hub.
 *
 * Existing stores remain the source of truth. This view only gives Continue Watching, creators,
 * Watch Later, Downloads and History a first-class visual presentation inside the primary pager.
 */
final class LibraryHubView extends ScrollView {
    interface Listener {
        void onOpenWatchLater(NativeContentItem item);
        void onOpenHistory(PlaybackHistoryStore.Item item);
        void onOpenCreator(NativeContentItem creator);
    }

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private final Activity activity;
    private final Listener listener;
    private final LinearLayout content;
    private final LibrarySocialHubView socialHub;
    private final Map<String, List<ImageView>> thumbnailTargets = new HashMap<>();
    private final Map<String, String> resolvedThumbnails = new HashMap<>();
    private final java.util.Set<String> requestedThumbnails = new java.util.HashSet<>();
    private final RenderedThumbnailResolver[] thumbnailResolvers;
    private final ShowsContinueFrameStore.Listener frameListener = this::onContinueFrameUpdated;
    private LinearLayout mediaContent;
    private final Map<String, SectionState> sectionStates = new HashMap<>();
    private final java.util.concurrent.ExecutorService refreshIo =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private final List<SharedPreferences> observedPreferences = new ArrayList<>();
    private final SharedPreferences.OnSharedPreferenceChangeListener invalidation = (prefs, key) -> {
        this.revision++;
        if (this.active) scheduleRefresh();
    };
    private PrimaryTabWorkQueue.Scheduler work = (key, task) -> post(task);
    private boolean active;
    private boolean loading;
    private int revision;
    private Snapshot displayed;
    private int resolverCursor;
    private volatile boolean closed;

    LibraryHubView(Activity activity, Listener listener) {
        super(activity);
        this.activity = activity;
        this.listener = listener;

        setFillViewport(true);
        setContentDescription("Library media hub");
        setVerticalScrollBarEnabled(false);
        setClipToPadding(false);
        setBackgroundColor(ZeroChillUi.background(activity));

        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(18), dp(12), dp(36));
        addView(content, new ScrollView.LayoutParams(-1, -2));
        socialHub = new LibrarySocialHubView(activity);
        content.addView(socialHub, new LinearLayout.LayoutParams(-1, -2));

        thumbnailResolvers = new RenderedThumbnailResolver[] {
                new RenderedThumbnailResolver(activity, this::onThumbnailResolved),
                new RenderedThumbnailResolver(activity, this::onThumbnailResolved)
        };
        ShowsContinueFrameStore.addListener(frameListener);
        mediaContent = new LinearLayout(activity);
        mediaContent.setOrientation(LinearLayout.VERTICAL);
        content.addView(mediaContent, new LinearLayout.LayoutParams(-1, -2));
        addGlobalEmptyState();
        for (String name : new String[] {"playback_history", "watch_later", "creator_favorites",
                "creator_catalog_v1", "creator_avatar_overrides_v1", "manual_creator_merges_v1",
                "native_video_downloads", "zerochill_account_secure_state", "popular_creator_feed_v3",
                "popular_creator_feed_v2", "onlyfap_trending_v4", "fapzone_creator_feed_v3_1",
                "fapzone_creator_feed_v3_2", "fapzone_creator_feed_v3_3", "fapzone_creator_feed_v1_1",
                "fapzone_creator_feed_v1_2", "fapzone_creator_feed_v1_3"}) {
            SharedPreferences prefs = activity.getSharedPreferences(name, Activity.MODE_PRIVATE);
            observedPreferences.add(prefs);
            prefs.registerOnSharedPreferenceChangeListener(invalidation);
        }
    }

    void setWorkScheduler(PrimaryTabWorkQueue.Scheduler scheduler) { work = scheduler; }

    private void scheduleRefresh() { work.schedule("library-refresh", this::refresh); }

    void refresh() {
        if (closed || !active || loading) return;
        loading = true;
        final int requestedRevision = revision;
        final Snapshot previous = displayed;
        refreshIo.execute(() -> {
            Snapshot snapshot = new Snapshot(activity, observedPreferences, previous);
            if (closed) return;
            post(() -> work.schedule("library-publish", () -> {
                loading = false;
                if (closed) return;
                if (requestedRevision != revision) {
                    if (active) scheduleRefresh();
                    return;
                }
                if (displayed != null && displayed.sameData(snapshot)) return;
                android.os.Trace.beginSection("ZeroChill.Library.publish");
                try { render(snapshot); } finally { android.os.Trace.endSection(); }
                displayed = snapshot;
            }));
        });
    }

    private void render(Snapshot snapshot) {
        int scrollY = getScrollY();
        Map<String, Integer> railPositions = new HashMap<>();
        for (int i = 0; i < mediaContent.getChildCount(); i++) {
            View section = mediaContent.getChildAt(i);
            if (section instanceof ViewGroup && ((ViewGroup) section).getChildCount() > 1) {
                View rail = ((ViewGroup) section).getChildAt(1);
                railPositions.put(String.valueOf(section.getTag()), rail.getScrollX());
            }
        }
        LinearLayout previous = mediaContent;
        mediaContent = new LinearLayout(activity);
        mediaContent.setOrientation(LinearLayout.VERTICAL);
        // Unchanged rails keep their existing views, image requests and horizontal position.
        List<PlaybackHistoryStore.Item> history = snapshot.history;
        Map<String, PlaybackHistoryStore.Item> historyByUrl = new HashMap<>();
        for (PlaybackHistoryStore.Item item : history) {
            if (item != null && item.pageUrl != null && !item.pageUrl.isEmpty()) {
                historyByUrl.put(item.pageUrl, item);
            }
        }
        retainSection("continue", snapshot.inputs.get(0), () -> {
            if (!snapshot.continueItems.isEmpty()) addContinueSection(snapshot.continueItems);
        });
        List<Object> creatorState = new ArrayList<>(snapshot.inputs.subList(2, 6));
        creatorState.addAll(snapshot.inputs.subList(8, snapshot.inputs.size()));
        retainSection("creators", creatorState, () -> {
            if (!snapshot.creators.isEmpty()) addCreatorSection(snapshot.creators, snapshot.avatars);
        });
        retainSection("watch-later", java.util.Arrays.asList(snapshot.inputs.get(0), snapshot.inputs.get(1)), () -> {
            if (!snapshot.watchLater.isEmpty()) addWatchLaterSection(snapshot.watchLater, historyByUrl);
        });
        retainSection("downloads", java.util.Arrays.asList(snapshot.inputs.get(6), snapshot.downloadState), () -> {
            if (!snapshot.downloads.isEmpty()) addDownloadsSection(snapshot.downloads);
        });
        retainSection("history", snapshot.inputs.get(0), () -> {
            if (!history.isEmpty()) addHistorySection(history);
        });
        if (mediaContent.getChildCount() == 0) retainSection("empty", true, this::addGlobalEmptyState);
        else sectionStates.remove("empty");
        content.removeView(previous);
        content.addView(mediaContent, new LinearLayout.LayoutParams(-1, -2));
        clearImages(previous);
        for (List<ImageView> targets : thumbnailTargets.values()) {
            targets.removeIf(image -> !belongsToMediaContent(image));
        }
        thumbnailTargets.values().removeIf(List::isEmpty);
        resolvedThumbnails.keySet().retainAll(thumbnailTargets.keySet());
        requestedThumbnails.retainAll(thumbnailTargets.keySet());
        post(() -> {
            if (closed) return;
            scrollTo(0, Math.min(scrollY, Math.max(0, content.getHeight() - getHeight())));
            for (int i = 0; i < mediaContent.getChildCount(); i++) {
                View section = mediaContent.getChildAt(i);
                Integer x = railPositions.get(String.valueOf(section.getTag()));
                if (x != null && section instanceof ViewGroup &&
                        ((ViewGroup) section).getChildCount() > 1) {
                    ((ViewGroup) section).getChildAt(1).scrollTo(x, 0);
                }
            }
        });
    }

    private void retainSection(String key, Object state, Runnable build) {
        SectionState previous = sectionStates.get(key);
        if (previous != null && previous.state.equals(state)) {
            if (previous.view != null) {
                ((ViewGroup) previous.view.getParent()).removeView(previous.view);
                mediaContent.addView(previous.view);
            }
            return;
        }
        int before = mediaContent.getChildCount();
        build.run();
        View view = mediaContent.getChildCount() > before ? mediaContent.getChildAt(before) : null;
        sectionStates.put(key, new SectionState(state, view));
    }

    private boolean belongsToMediaContent(View view) {
        android.view.ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent == mediaContent) return true;
            parent = parent.getParent();
        }
        return false;
    }

    private static final class SectionState {
        final Object state;
        final View view;
        SectionState(Object state, View view) { this.state = state; this.view = view; }
    }

    private void clearImages(View view) {
        if (view instanceof ImageView) Glide.with(view).clear(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) clearImages(group.getChildAt(i));
        }
    }

    private static final class Snapshot {
        final List<String> inputs = new ArrayList<>();
        final List<PlaybackHistoryStore.Item> history;
        final List<PlaybackHistoryStore.Item> continueItems;
        final List<CreatorCatalog.FavoriteGroup> creators;
        final List<CreatorAvatarOverrideStore.Override> avatars;
        final List<FavoriteStore.Item> watchLater;
        final List<VideoDownloadStore.Entry> downloads;
        final String downloadState;

        Snapshot(Activity activity, List<SharedPreferences> preferences, Snapshot previous) {
            for (SharedPreferences prefs : preferences) inputs.add(PreferenceSnapshot.key(prefs));
            downloads = first(VideoDownloadStore.entries(activity), 10);
            StringBuilder state = new StringBuilder();
            for (VideoDownloadStore.Entry entry : downloads) {
                state.append(entry.id).append(':').append(entry.status).append(':')
                        .append(entry.downloadedBytes).append(':').append(entry.totalBytes)
                        .append(':').append(entry.localUri).append(':').append(entry.reason).append(';');
            }
            downloadState = state.toString();
            if (previous != null && inputs.equals(previous.inputs)) {
                history = previous.history;
                continueItems = previous.continueItems;
                creators = previous.creators;
                avatars = previous.avatars;
                watchLater = previous.watchLater;
            } else {
                history = PlaybackHistoryStore.load(activity);
                continueItems = first(PlaybackHistoryStore.continueWatching(activity), 10);
                creators = first(CreatorCatalog.favoriteGroups(activity), 14);
                List<CreatorAvatarOverrideStore.Override> allAvatars = CreatorAvatarOverrideStore.load(activity);
                avatars = new ArrayList<>();
                for (CreatorCatalog.FavoriteGroup creator : creators) {
                    CreatorAvatarOverrideStore.Override avatar =
                            CreatorAvatarOverrideStore.find(allAvatars, creator.relationshipKeys);
                    if (avatar != null) avatars.add(avatar);
                }
                watchLater = first(FavoriteStore.load(activity), 10);
            }
        }
        private static <T> List<T> first(List<T> items, int limit) {
            return new ArrayList<>(items.subList(0, Math.min(items.size(), limit)));
        }

        boolean sameData(Snapshot other) {
            return inputs.equals(other.inputs) && downloadState.equals(other.downloadState);
        }
    }

    void close() {
        if (closed) return;
        closed = true;
        active = false;
        refreshIo.shutdownNow();
        for (SharedPreferences prefs : observedPreferences) {
            prefs.unregisterOnSharedPreferenceChangeListener(invalidation);
        }
        clearImages(mediaContent);
        ShowsContinueFrameStore.removeListener(frameListener);
        socialHub.close();
        for (RenderedThumbnailResolver resolver : thumbnailResolvers) {
            if (resolver != null) resolver.close();
        }
        thumbnailTargets.clear();
        requestedThumbnails.clear();
        sectionStates.clear();
    }

    void setSocialActive(boolean active) {
        if (closed || this.active == active) return;
        this.active = active;
        socialHub.setActive(active);
        // DownloadManager can change without a preference write. Check once on settled entry.
        if (active) scheduleRefresh();
    }

    private void addContinueSection(List<PlaybackHistoryStore.Item> items) {
        LinearLayout rail = addSectionShell(
                "Continue Watching",
                "Pick up where you left off",
                "View all",
                () -> openSavedVideos(FavoritesActivity.START_CONTINUE)
        );

        int count = Math.min(10, items.size());
        for (int i = 0; i < count; i++) {
            PlaybackHistoryStore.Item item = items.get(i);
            rail.addView(continueCard(item), mediaRailParams(248, 140, i == count - 1));
        }
    }

    private View continueCard(PlaybackHistoryStore.Item item) {
        MaterialCardView card = mediaCard(22);
        FrameLayout frame = new FrameLayout(activity);
        card.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

        ImageView image = mediaImage(frame);
        loadMediaImage(image, item.pageUrl, item.posterUrl, true);

        View shade = new View(activity);
        shade.setBackground(bottomShade());
        frame.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        TextView remaining = pill(formatRemaining(item.positionMs, item.durationMs));
        remaining.setVisibility(item.durationMs > 0L ? View.VISIBLE : View.GONE);
        FrameLayout.LayoutParams remainingParams =
                new FrameLayout.LayoutParams(-2, dp(28), Gravity.TOP | Gravity.END);
        remainingParams.setMargins(0, dp(10), dp(10), 0);
        frame.addView(remaining, remainingParams);

        TextView title = mediaTitle(item.title, 16.5f);
        FrameLayout.LayoutParams titleParams =
                new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        titleParams.setMargins(dp(12), 0, dp(12), dp(17));
        frame.addView(title, titleParams);

        if (item.durationMs > 0L) {
            FrameLayout track = new FrameLayout(activity);
            GradientDrawable trackBg = new GradientDrawable();
            trackBg.setColor(Color.argb(118, 255, 255, 255));
            trackBg.setCornerRadius(dp(2));
            track.setBackground(trackBg);
            FrameLayout.LayoutParams trackParams =
                    new FrameLayout.LayoutParams(-1, dp(4), Gravity.BOTTOM);
            trackParams.setMargins(dp(10), 0, dp(10), dp(7));
            frame.addView(track, trackParams);

            View fill = new View(activity);
            GradientDrawable fillBg = new GradientDrawable();
            fillBg.setColor(UiPalette.PRIMARY);
            fillBg.setCornerRadius(dp(2));
            fill.setBackground(fillBg);
            int fillWidth = Math.max(dp(4),
                    Math.round(dp(228) * (item.progressPercent() / 100f)));
            track.addView(fill, new FrameLayout.LayoutParams(fillWidth, -1));
        }

        card.setContentDescription("Continue watching " + item.title);
        card.setOnClickListener(v -> openHistoryItem(item));
        return card;
    }

    private void addCreatorSection(List<CreatorCatalog.FavoriteGroup> creators,
            List<CreatorAvatarOverrideStore.Override> avatarOverrides) {
        LinearLayout rail = addSectionShell(
                "Favorite Creators",
                "Your saved creators",
                "View all",
                () -> activity.startActivity(new Intent(activity, CreatorsActivity.class))
        );
        int count = Math.min(14, creators.size());
        for (int i = 0; i < count; i++) {
            CreatorCatalog.FavoriteGroup group = creators.get(i);
            rail.addView(
                    creatorCard(group, avatarOverrides),
                    creatorRailParams(i == count - 1)
            );
        }
    }

    private View creatorCard(
            CreatorCatalog.FavoriteGroup group,
            List<CreatorAvatarOverrideStore.Override> avatarOverrides
    ) {
        NativeContentItem creator = group.item;
        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
        wrapper.setClickable(true);
        wrapper.setFocusable(true);
        wrapper.setContentDescription("Open " + creator.title);
        ZeroChillMotion.installPressFeedback(wrapper);

        MaterialCardView avatar = mediaCard(42);
        avatar.setRadius(dp(42));
        avatar.setClipToOutline(true);
        avatar.setCardBackgroundColor(Color.rgb(19, 23, 27));

        FrameLayout frame = new FrameLayout(activity);
        avatar.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

        TextView initials = text(initials(creator.title), 24f,
                Color.argb(105, 255, 255, 255));
        initials.setTypeface(null, android.graphics.Typeface.BOLD);
        initials.setGravity(Gravity.CENTER);
        frame.addView(initials, new FrameLayout.LayoutParams(-1, -1));

        CreatorAvatarImageView image = new CreatorAvatarImageView(activity);
        image.setBackgroundColor(Color.TRANSPARENT);
        image.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        image.setClipToOutline(true);
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
        loadCreatorImage(image, group, avatarOverrides);

        wrapper.addView(avatar, new LinearLayout.LayoutParams(dp(84), dp(84)));

        TextView name = text(creator.title, 12.5f,
                ZeroChillUi.color(activity, R.color.zc_text_primary));
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(dp(102), -2);
        nameParams.topMargin = dp(7);
        wrapper.addView(name, nameParams);

        View.OnClickListener openCreator = v -> {
            if (listener != null) listener.onOpenCreator(creator);
        };
        wrapper.setOnClickListener(openCreator);
        avatar.setOnClickListener(openCreator);
        avatar.setContentDescription("Open " + creator.title);
        return wrapper;
    }

    private void addWatchLaterSection(
            List<FavoriteStore.Item> items,
            Map<String, PlaybackHistoryStore.Item> historyByUrl
    ) {
        LinearLayout rail = addSectionShell(
                "Watch Later",
                "Saved for another time",
                "View all",
                () -> openSavedVideos(FavoritesActivity.START_WATCH_LATER)
        );

        int count = Math.min(10, items.size());
        for (int i = 0; i < count; i++) {
            FavoriteStore.Item item = items.get(i);
            PlaybackHistoryStore.Item historyItem = historyByUrl.get(item.url);
            String poster = historyItem == null ? "" : historyItem.posterUrl;
            rail.addView(
                    compactMediaCard(item.title, item.url, poster, "", false),
                    mediaRailParams(196, 122, i == count - 1)
            );
        }
    }

    private void addDownloadsSection(List<VideoDownloadStore.Entry> entries) {
        LinearLayout rail = addSectionShell(
                "Downloads",
                "Available offline and in progress",
                "View all",
                () -> activity.startActivity(new Intent(activity, DownloadedActivity.class))
        );

        int count = Math.min(10, entries.size());
        for (int i = 0; i < count; i++) {
            VideoDownloadStore.Entry entry = entries.get(i);
            rail.addView(downloadCard(entry), mediaRailParams(196, 122, i == count - 1));
        }
    }

    private View downloadCard(VideoDownloadStore.Entry entry) {
        MaterialCardView card = mediaCard(18);
        FrameLayout frame = new FrameLayout(activity);
        card.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

        ImageView image = mediaImage(frame);
        if (!entry.imageUrl.isEmpty()) {
            loadMediaImage(image, entry.pageUrl, entry.imageUrl, false);
        } else if (!entry.pageUrl.isEmpty()) {
            loadMediaImage(image, entry.pageUrl, "", false);
        } else {
            image.setImageResource(R.drawable.ic_action_download);
            image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            image.setPadding(dp(58), dp(32), dp(58), dp(32));
            image.setColorFilter(UiPalette.PRIMARY);
        }

        View shade = new View(activity);
        shade.setBackground(bottomShade());
        frame.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        TextView status = pill(VideoDownloadStore.statusText(entry));
        status.setTextSize(9.5f);
        FrameLayout.LayoutParams statusParams =
                new FrameLayout.LayoutParams(-2, dp(26), Gravity.TOP | Gravity.END);
        statusParams.setMargins(0, dp(8), dp(8), 0);
        frame.addView(status, statusParams);

        TextView title = mediaTitle(entry.title, 14.5f);
        FrameLayout.LayoutParams titleParams =
                new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        titleParams.setMargins(dp(10), 0, dp(10), dp(9));
        frame.addView(title, titleParams);

        card.setContentDescription(entry.title + ". " + VideoDownloadStore.statusText(entry));
        card.setOnClickListener(v -> VideoDownloadStore.open(activity, entry));
        return card;
    }

    private void addHistorySection(List<PlaybackHistoryStore.Item> items) {
        LinearLayout rail = addSectionShell(
                "History",
                "Recently watched",
                "View all",
                () -> openSavedVideos(FavoritesActivity.START_HISTORY)
        );

        int count = Math.min(12, items.size());
        for (int i = 0; i < count; i++) {
            PlaybackHistoryStore.Item item = items.get(i);
            rail.addView(
                    historyCard(item),
                    mediaRailParams(168, 105, i == count - 1)
            );
        }
    }

    private View historyCard(PlaybackHistoryStore.Item item) {
        MaterialCardView card = mediaCard(16);
        FrameLayout frame = new FrameLayout(activity);
        card.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

        ImageView image = mediaImage(frame);
        loadMediaImage(image, item.pageUrl, item.posterUrl, false);

        View shade = new View(activity);
        shade.setBackground(bottomShade());
        frame.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        TextView title = mediaTitle(item.title, 13.5f);
        FrameLayout.LayoutParams titleParams =
                new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        titleParams.setMargins(dp(10), 0, dp(10), dp(8));
        frame.addView(title, titleParams);

        card.setContentDescription(item.title);
        card.setOnClickListener(v -> openHistoryItem(item));
        return card;
    }

    private View compactMediaCard(
            String title,
            String pageUrl,
            String posterUrl,
            String badge,
            boolean small
    ) {
        MaterialCardView card = mediaCard(small ? 16 : 18);
        FrameLayout frame = new FrameLayout(activity);
        card.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

        ImageView image = mediaImage(frame);
        loadMediaImage(image, pageUrl, posterUrl, false);

        View shade = new View(activity);
        shade.setBackground(bottomShade());
        frame.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        if (badge != null && !badge.isEmpty()) {
            TextView badgeView = pill(badge);
            FrameLayout.LayoutParams badgeParams =
                    new FrameLayout.LayoutParams(-2, dp(26), Gravity.TOP | Gravity.END);
            badgeParams.setMargins(0, dp(8), dp(8), 0);
            frame.addView(badgeView, badgeParams);
        }

        TextView titleView = mediaTitle(title, small ? 13.5f : 14.5f);
        FrameLayout.LayoutParams titleParams =
                new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        titleParams.setMargins(dp(10), 0, dp(10), dp(8));
        frame.addView(titleView, titleParams);

        card.setContentDescription(title);
        card.setOnClickListener(v -> {
            if (listener != null && !clean(pageUrl).isEmpty()) {
                listener.onOpenWatchLater(videoItem(title, pageUrl, posterUrl));
            }
        });
        return card;
    }

    private LinearLayout addSectionShell(
            String title,
            String subtitle,
            String actionLabel,
            Runnable action
    ) {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setTag(title);

        LinearLayout headingRow = new LinearLayout(activity);
        headingRow.setOrientation(LinearLayout.HORIZONTAL);
        headingRow.setGravity(Gravity.CENTER_VERTICAL);
        headingRow.setPadding(dp(4), 0, dp(2), 0);

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);

        TextView heading = text(title, 21f,
                ZeroChillUi.color(activity, R.color.zc_text_primary));
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setContentDescription(title);
        copy.addView(heading);

        TextView hint = text(subtitle, 11.5f,
                ZeroChillUi.color(activity, R.color.zc_text_muted));
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(2);
        copy.addView(hint, hintParams);

        headingRow.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView viewAll = text(actionLabel, 11.5f, UiPalette.PRIMARY);
        viewAll.setTypeface(null, android.graphics.Typeface.BOLD);
        viewAll.setGravity(Gravity.CENTER);
        viewAll.setPadding(dp(12), dp(10), dp(8), dp(10));
        viewAll.setClickable(true);
        viewAll.setFocusable(true);
        viewAll.setContentDescription(actionLabel + " " + title);
        ZeroChillMotion.installPressFeedback(viewAll);
        viewAll.setOnClickListener(v -> action.run());
        headingRow.addView(viewAll, new LinearLayout.LayoutParams(-2, dp(42)));

        section.addView(headingRow, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        scroll.setPadding(dp(4), 0, dp(4), 0);

        LinearLayout rail = new LinearLayout(activity);
        rail.setOrientation(LinearLayout.HORIZONTAL);
        rail.setGravity(Gravity.TOP);
        rail.setClipChildren(false);
        rail.setClipToPadding(false);
        scroll.addView(rail, new HorizontalScrollView.LayoutParams(-2, -2));

        LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(-1, -2);
        railParams.topMargin = dp(10);
        section.addView(scroll, railParams);

        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
        sectionParams.setMargins(0, 0, 0, dp(25));
        mediaContent.addView(section, sectionParams);
        return rail;
    }

    private void addGlobalEmptyState() {
        LinearLayout empty = new LinearLayout(activity);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(26), dp(54), dp(26), dp(42));

        TextView icon = text("♡", 36f, UiPalette.PRIMARY);
        icon.setGravity(Gravity.CENTER);
        empty.addView(icon, new LinearLayout.LayoutParams(dp(60), dp(60)));

        TextView title = text("Your Library is ready", 20f,
                ZeroChillUi.color(activity, R.color.zc_text_primary));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(10);
        empty.addView(title, titleParams);

        TextView body = text(
                "Keep watching, favorite a creator, save something for later, or download a video.",
                13f,
                ZeroChillUi.color(activity, R.color.zc_text_secondary)
        );
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(0f, 1.08f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.setMargins(dp(18), dp(8), dp(18), 0);
        empty.addView(body, bodyParams);

        mediaContent.addView(empty, new LinearLayout.LayoutParams(-1, -2));
    }

    private MaterialCardView mediaCard(int radiusDp) {
        MaterialCardView card = new MaterialCardView(activity);
        ZeroChillUi.styleMediaCard(card, R.dimen.zc_radius_medium);
        card.setRadius(dp(radiusDp));
        card.setStrokeWidth(0);
        card.setCardElevation(0f);
        card.setClickable(true);
        card.setFocusable(true);
        card.setClipToOutline(true);
        ZeroChillMotion.installPressFeedback(card);
        return card;
    }

    private ImageView mediaImage(FrameLayout frame) {
        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(13, 16, 19));
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
        return image;
    }

    private void loadMediaImage(
            ImageView image,
            String pageUrl,
            String preferredImage,
            boolean preferContinueFrame
    ) {
        String cleanPage = clean(pageUrl);
        if (!cleanPage.isEmpty()) {
            thumbnailTargets.computeIfAbsent(cleanPage, key -> new ArrayList<>()).add(image);
        }

        if (preferContinueFrame && !cleanPage.isEmpty()) {
            File frame = ShowsContinueFrameStore.find(activity, cleanPage);
            if (frame != null) {
                loadResolvedImage(image, frame.toURI().toString(), cleanPage);
                return;
            }
        }

        String preferred = clean(preferredImage);
        if (!preferred.isEmpty()) {
            loadResolvedImage(image, preferred, cleanPage);
            return;
        }

        String resolved = resolvedThumbnails.get(cleanPage);
        if (resolved != null && !resolved.isEmpty()) {
            loadResolvedImage(image, resolved, cleanPage);
            return;
        }

        image.setImageDrawable(new ColorDrawable(Color.rgb(22, 25, 29)));
        requestThumbnail(cleanPage);
    }

    private void loadCreatorImage(
            CreatorAvatarImageView image,
            CreatorCatalog.FavoriteGroup group,
            List<CreatorAvatarOverrideStore.Override> avatarOverrides
    ) {
        NativeContentItem creator = group == null ? null : group.item;
        CreatorAvatarOverrideStore.Override avatarOverride =
                group != null && group.customAvatar
                        ? CreatorAvatarOverrideStore.find(
                                avatarOverrides,
                                group.relationshipKeys
                        )
                        : null;
        if (avatarOverride != null) {
            image.setAvatarCrop(
                    avatarOverride.focusX,
                    avatarOverride.focusY,
                    avatarOverride.zoom
            );
        } else {
            image.clearAvatarCrop();
        }

        String url = creator == null ? "" : clean(creator.imageUrl);
        if (url.isEmpty()) return;
        try {
            com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> request =
                    Glide.with(image)
                            .load(remoteImage(
                                    url,
                                    clean(creator.uploader).isEmpty()
                                            ? clean(creator.url)
                                            : clean(creator.uploader)
                            ))
                            .onlyRetrieveFromCache(true)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .dontAnimate()
                            .transition(ThumbnailFades.avatar());
            if (avatarOverride == null) request.circleCrop();
            request.into(image);
        } catch (Exception ignored) {
        }
    }

    private void requestThumbnail(String pageUrl) {
        if (pageUrl.isEmpty() || closed || !requestedThumbnails.add(pageUrl)) return;
        RenderedThumbnailResolver resolver =
                thumbnailResolvers[resolverCursor++ % thumbnailResolvers.length];
        resolver.request(pageUrl);
    }

    private void onThumbnailResolved(String pageUrl, String thumbnailUrl) {
        if (closed || clean(pageUrl).isEmpty() || clean(thumbnailUrl).isEmpty() ||
                !thumbnailTargets.containsKey(pageUrl)) return;
        resolvedThumbnails.put(pageUrl, thumbnailUrl);
        work.schedule("thumbnail:" + pageUrl, () -> applyResolvedThumbnail(pageUrl, thumbnailUrl));
    }

    private void applyResolvedThumbnail(String pageUrl, String thumbnailUrl) {
        if (closed) return;
        List<ImageView> targets = thumbnailTargets.get(pageUrl);
        if (targets == null) return;
        for (ImageView image : new ArrayList<>(targets)) {
            loadResolvedImage(image, thumbnailUrl, pageUrl);
        }
    }

    private void onContinueFrameUpdated(String pageUrl) {
        if (closed || pageUrl == null || pageUrl.isEmpty() ||
                !thumbnailTargets.containsKey(pageUrl)) return;
        work.schedule("thumbnail:" + pageUrl, () -> {
            if (closed) return;
            File frame = ShowsContinueFrameStore.find(activity, pageUrl);
            if (frame != null) applyResolvedThumbnail(pageUrl, frame.toURI().toString());
        });
    }

    private void loadResolvedImage(ImageView image, String source, String pageUrl) {
        if (closed || image == null || clean(source).isEmpty()) return;
        Object model = source.startsWith("file:")
                ? source
                : remoteImage(source, pageUrl);
        try {
            Glide.with(image)
                    .load(model)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .dontAnimate()
                    .transition(ThumbnailFades.thumbnail())
                    .placeholder(new ColorDrawable(Color.rgb(22, 25, 29)))
                    .error(new ColorDrawable(Color.rgb(22, 25, 29)))
                    .into(image);
        } catch (Exception ignored) {
        }
    }

    private Object remoteImage(String imageUrl, String pageUrl) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", USER_AGENT);
        if (pageUrl != null && !pageUrl.isEmpty()) headers.addHeader("Referer", pageUrl);
        return new GlideUrl(imageUrl, headers.build());
    }

    private void openHistoryItem(PlaybackHistoryStore.Item item) {
        if (item == null || clean(item.pageUrl).isEmpty() || listener == null) return;
        listener.onOpenHistory(item);
    }

    private NativeContentItem videoItem(String title, String pageUrl, String posterUrl) {
        return new NativeContentItem(
                NativeContentItem.KIND_MEDIA,
                clean(title).isEmpty() ? "Saved video" : title,
                pageUrl,
                posterUrl,
                "",
                "",
                ""
        );
    }

    private void openSavedVideos(int startTab) {
        Intent intent = new Intent(activity, FavoritesActivity.class)
                .putExtra(FavoritesActivity.EXTRA_START_TAB, startTab);
        activity.startActivityForResult(intent, NativeMainActivity.FAVORITES_REQUEST);
    }

    private TextView mediaTitle(String value, float size) {
        TextView title = text(
                clean(value).isEmpty() ? "Saved video" : value,
                size,
                Color.WHITE
        );
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setShadowLayer(5f, 0f, dp(1), Color.BLACK);
        return title;
    }

    private TextView pill(String value) {
        TextView badge = text(value, 10.5f, Color.WHITE);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(9), 0, dp(9), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(190, 5, 7, 9));
        background.setCornerRadius(dp(9));
        badge.setBackground(background);
        return badge;
    }

    private GradientDrawable bottomShade() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        Color.argb(0, 0, 0, 0),
                        Color.argb(32, 0, 0, 0),
                        Color.argb(226, 0, 0, 0)
                }
        );
    }

    private LinearLayout.LayoutParams mediaRailParams(
            int widthDp,
            int heightDp,
            boolean last
    ) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(dp(widthDp), dp(heightDp));
        params.setMarginEnd(last ? 0 : dp(10));
        return params;
    }

    private LinearLayout.LayoutParams creatorRailParams(boolean last) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(dp(104), dp(124));
        params.setMarginEnd(last ? 0 : dp(5));
        return params;
    }

    private TextView text(String value, float size, int color) {
        TextView text = new TextView(activity);
        text.setText(value == null ? "" : value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private String formatRemaining(long positionMs, long durationMs) {
        if (durationMs <= 0L) return "";
        long remainingMs = Math.max(0L, durationMs - Math.max(0L, positionMs));
        long totalMinutes = Math.max(1L, (remainingMs + 59_999L) / 60_000L);
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (hours > 0L && minutes > 0L) return hours + "h " + minutes + "m left";
        if (hours > 0L) return hours + "h left";
        return minutes + "m left";
    }

    private String initials(String value) {
        String clean = clean(value);
        if (clean.isEmpty()) return "?";
        String[] parts = clean.split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase(java.util.Locale.US);
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1))
                .toUpperCase(java.util.Locale.US);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
