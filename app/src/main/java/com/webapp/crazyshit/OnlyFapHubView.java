package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.FutureTarget;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.card.MaterialCardView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cinematic creator-discovery landing page for OnlyFap.
 *
 * Creator cards always open the existing unified creator gallery. The hero prefers
 * high-resolution creator gallery artwork, with wide headers and avatars as fallbacks.
 */
final class OnlyFapHubView extends FrameLayout {
    interface Listener {
        void onOpenCreator(NativeContentItem creator);

        default void onOpenFavoriteCreator(CreatorCatalog.FavoriteGroup group) {
            if (group != null) onOpenCreator(group.item);
        }

        void onSearch();

        default void onSearch(View anchor) { onSearch(); }

        default void onMore() {
        }

        default void onMore(View anchor) {
            onMore();
        }

        void onViewAllFavorites();
    }

    private HeaderProfileAvatar profileAvatar;
    private TextView preferenceSelector;
    private Runnable preferenceChanged;
    private ContentPreference discoveryPreference = ContentPreference.ALL;
    private ContentPreference publishedPreference;

    void setPreferenceChanged(Runnable changed) { preferenceChanged = changed; }

    void preparePreference(ContentPreference mode) {
        if (discoveryPreference != mode) {
            cancelHeroMorphForSelection();
            preferenceHeroPending = preferenceHeroPending || heroArtworkReady;
            discoveryPreference = mode;
            pendingRestoreState = null;
            ContentPreferenceUi.clearPresentation(this);
            clear();
        }
        ContentPreferenceUi.bindSelector(preferenceSelector);
        pulsePreferenceSelector();
        if (trendingShelf != null) {
            ((TextView) trendingShelf.container.getChildAt(0)).setText(mode == ContentPreference.ALL
                    ? "Discover" : mode.label);
            ((TextView) trendingShelf.container.getChildAt(1)).setText(mode == ContentPreference.ALL
                    ? FapzoneCreatorRepository.hintFor(FapzoneCreatorRepository.MODE_TOP_50)
                    : "OnlyHaven · " + mode.label);
        }
    }

    private void presentPreference() {
        if (active && heroMorph == null && heroArtworkReady && !preferenceHeroPending
                && discoveryPreference == ContentPreference.get(getContext()))
            ContentPreferenceUi.present(this, false, preferenceSelector);
    }

    void setPreferenceCreators(List<NativeContentItem> items) {
        discoverCandidates = safe(items);
        trendingItems = safe(items);
        trendingShelf.adapter.replace(trendingItems);
        trendingShelf.container.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        shelvesFinished = true;
        publishedPreference = discoveryPreference;
        loadingLabel.setText("No creators available for " + discoveryPreference.label + ". Pull down to retry.");
        loadingLabel.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        requestStartupHero();
        refreshHeroCandidates();
        applyPendingRestoreState();
        refreshFavorites();
        if (items.isEmpty()) showPreferenceEmptyState();
        if (active) post(this::presentPreference);
    }

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";
    private static final long HERO_ROTATION_MS = 13_000L;
    private static final int HERO_MAX_ITEMS = 8;
    private static final int HERO_SEARCH_LIMIT = 180;
    private static final int HERO_DISCOVERY_FIRST_PAGE = 2;
    private static final int HERO_DISCOVERY_LAST_PAGE = 8;
    private static final int HERO_ORIGINAL_CHECKS_PER_PAGE = 4;

    private final Listener listener;
    private final ScrollView scroll;
    private final LinearLayout content;
    private final LinearLayout body;
    private final MaterialCardView heroCard;
    private ImageView heroImage;
    private ImageView preferenceImage;
    private boolean preferenceHeroPending;
    private boolean heroArtworkReady;
    private android.animation.ValueAnimator heroMorph;
    private android.animation.ValueAnimator selectorPulse;
    private float riftProgress;
    private int deferredHeroIndex = -1;
    private final android.animation.ArgbEvaluator selectorColors = new android.animation.ArgbEvaluator();
    private static final long LIQUID_RIFT_MS = 1040L;
    private android.widget.ProgressBar preferenceLoading;
    private TextView previousHeroTitle, previousHeroHint;
    private FrameLayout heroFrame, heroImages;
    private final TextView heroTitle;
    private final TextView heroHint;
    private final TextView heroAction;
    private final TextView heroDots;
    private final TextView loadingLabel;
    private final LinearLayout favoritesSection;
    private final LinearLayout favoritesRail;
    private final CreatorShelf trendingShelf;
    private final CreatorShelf newShelf;
    private final CreatorShelf hotShelf;
    private final CreatorShelf popularShelf;
    private final ColdTabSnapshot coldSnapshot;
    private long openStartedAt = -1;
    private boolean openedBefore;
    private String openTimingLabel;
    private boolean openDrawRecorded, openUsableRecorded, openFinishedRecorded;
    private final android.view.ViewTreeObserver.OnPreDrawListener openDrawListener = () -> {
        recordOpenProgress();
        return true;
    };

    private final Handler heroHandler = new Handler(Looper.getMainLooper());
    private final Handler heroRefreshHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService metadataIo = Executors.newSingleThreadExecutor();
    private final ExecutorService heroIo = Executors.newSingleThreadExecutor();
    private final ExecutorService heroDiscoveryIo = Executors.newSingleThreadExecutor();
    private final Set<String> requestedHeroCreators = new HashSet<>();
    private final Set<String> rejectedHeroUrls = new HashSet<>();
    private final List<HeroCandidate> heroItems = new ArrayList<>();
    private final List<NativeContentItem> heroDiscoveryItems = new ArrayList<>();

    private List<NativeContentItem> discoverCandidates = Collections.emptyList();
    private List<NativeContentItem> trendingItems = Collections.emptyList();
    private List<NativeContentItem> newItems = Collections.emptyList();
    private List<NativeContentItem> hotItems = Collections.emptyList();
    private List<NativeContentItem> popularItems = Collections.emptyList();
    private boolean shelvesFinished;
    private HeroCandidate heroItem;
    private HeroCandidate startupHero;
    private boolean startupRequested;
    private int heroIndex = -1;
    private int heroResolveInFlight;
    private int heroDiscoveryNextPage = HERO_DISCOVERY_FIRST_PAGE;
    private boolean heroDiscoveryLoading;
    private boolean heroDiscoveryExhausted;
    private volatile boolean active;
    private PrimaryTabWorkQueue.Scheduler work = (key, task) -> post(task);
    private boolean heroSelectionLoading;
    private boolean favoritesLoading;
    private boolean favoritesDirty = true;
    private List<String> favoriteInputs;
    private final List<android.content.SharedPreferences> favoritePreferences = new ArrayList<>();
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener favoriteInvalidation =
            (prefs, key) -> {
                this.favoritesDirty = true;
                if (this.active) work.schedule("favorite-refresh", this::refreshFavorites);
            };

    void setWorkScheduler(PrimaryTabWorkQueue.Scheduler scheduler) { work = scheduler; }
    private volatile boolean closed;
    private volatile int heroGeneration;
    private java.util.concurrent.Future<?> startupTask, selectionTask, resolutionTask, discoveryTask;
    private volatile boolean fapelloPostsBlocked;
    private Bundle pendingRestoreState;

    OnlyFapHubView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setBackgroundColor(ZeroChillUi.background(context));

        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(36));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        heroCard = new MaterialCardView(context);
        ZeroChillUi.styleMediaCard(heroCard, R.dimen.zc_radius_large);
        heroCard.setRadius(0f);
        heroCard.setStrokeWidth(0);
        heroCard.setCardElevation(0f);
        heroCard.setClickable(true);
        heroCard.setFocusable(true);
        heroCard.setContentDescription("OnlyFap featured creator");
        ZeroChillMotion.installPressFeedback(heroCard);

        heroFrame = new FrameLayout(context);
        heroCard.addView(heroFrame, new MaterialCardView.LayoutParams(-1, -1));

        heroImages = new FrameLayout(context);
        heroFrame.addView(heroImages, new FrameLayout.LayoutParams(-1, -1));
        heroImage = new LiquidRiftImageView(context);
        heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroImage.setBackground(new ColorDrawable(Color.rgb(13, 16, 19)));
        heroImage.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        heroImages.addView(heroImage, new FrameLayout.LayoutParams(-1, -1));
        preferenceImage = new LiquidRiftImageView(context);
        preferenceImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preferenceImage.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        preferenceImage.setAlpha(0f);
        heroImages.addView(preferenceImage, new FrameLayout.LayoutParams(-1, -1));

        View heroShade = new View(context);
        heroShade.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.argb(6, 0, 0, 0),
                        Color.argb(24, 0, 0, 0),
                        Color.argb(92, 0, 0, 0),
                        Color.argb(218, 0, 0, 0),
                        Color.BLACK
                }
        ));
        heroShade.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        heroFrame.addView(heroShade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout heroBar = new LinearLayout(context);
        heroBar.setOrientation(LinearLayout.HORIZONTAL);
        heroBar.setGravity(Gravity.CENTER_VERTICAL);
        heroBar.setPadding(dp(20), dp(5), dp(12), 0);

        TextView heroBrand = text("OnlyFap", 28f, Color.WHITE);
        heroBrand.setText(ZeroChillUi.sectionTitle(context, "OnlyFap", 4, 7));
        ZeroChillTypography.apply(heroBrand, ZeroChillTypography.Role.DISPLAY);
        heroBrand.setSingleLine(true);
        heroBrand.setShadowLayer(dp(8), 0f, dp(2), Color.argb(150, 0, 0, 0));
        heroBar.addView(heroBrand, new LinearLayout.LayoutParams(0, dp(58), 1f));

        ImageView heroSearch = new ImageView(context);
        heroSearch.setImageResource(R.drawable.ic_nav_search);
        heroSearch.setPadding(dp(10), dp(10), dp(10), dp(10));
        heroSearch.setColorFilter(Color.WHITE);
        heroSearch.setClickable(true);
        heroSearch.setFocusable(true);
        heroSearch.setContentDescription("Search OnlyFap creators");
        heroSearch.setOnClickListener(v -> listener.onSearch(v));
        ZeroChillMotion.installPressFeedback(heroSearch);
        heroBar.addView(heroSearch, new LinearLayout.LayoutParams(dp(48), dp(48)));

        if (context instanceof android.app.Activity) {
            profileAvatar = new HeaderProfileAvatar((android.app.Activity) context);
            heroBar.addView(profileAvatar, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }

        ImageView heroMore = new ImageView(context);
        heroMore.setImageResource(R.drawable.ic_more_overflow);
        heroMore.setPadding(dp(10), dp(10), dp(10), dp(10));
        heroMore.setColorFilter(Color.WHITE);
        heroMore.setClickable(true);
        heroMore.setFocusable(true);
        heroMore.setContentDescription("OnlyFap More");
        heroMore.setOnClickListener(v -> listener.onMore(v));
        ZeroChillMotion.installPressFeedback(heroMore);
        LinearLayout.LayoutParams heroMoreParams =
                new LinearLayout.LayoutParams(dp(48), dp(48));
        heroMoreParams.setMarginStart(dp(2));
        heroBar.addView(heroMore, heroMoreParams);

        preferenceSelector = text("", 13f, UiPalette.PRIMARY);
        preferenceSelector.setGravity(Gravity.CENTER);
        preferenceSelector.setPadding(dp(8), dp(8), dp(8), dp(8));
        preferenceSelector.setMinimumHeight(dp(48));
        preferenceSelector.setClickable(true);
        preferenceSelector.setFocusable(true);
        ContentPreferenceUi.bindSelector(preferenceSelector);
        preferenceSelector.setOnClickListener(v -> ContentPreferenceUi.choose(context, () -> {
            ContentPreferenceUi.bindSelector(preferenceSelector);
            if (preferenceChanged != null) preferenceChanged.run();
        }));
        ZeroChillMotion.installPressFeedback(preferenceSelector);

        heroFrame.addView(heroBar, new FrameLayout.LayoutParams(
                -1,
                dp(64),
                Gravity.TOP
        ));

        heroDots = text("", 13f, Color.WHITE);
        heroDots.setGravity(Gravity.CENTER);
        heroDots.setVisibility(View.GONE);
        heroDots.setContentDescription("Featured creator position");

        LinearLayout heroCopy = new LinearLayout(context);
        heroCopy.setOrientation(LinearLayout.VERTICAL);
        heroCopy.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        heroCopy.setPadding(dp(24), dp(28), dp(24), dp(20));
        heroFrame.addView(heroCopy,
                new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        heroTitle = text("Discover creators", 29f, Color.WHITE);
        heroTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        heroTitle.setGravity(Gravity.CENTER);
        heroTitle.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        heroTitle.setShadowLayer(dp(8), 0f, dp(2), Color.argb(160, 0, 0, 0));
        heroTitle.setMaxLines(2);
        heroTitle.setMinLines(2);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams heroTitleParams = new LinearLayout.LayoutParams(-1, -2);
        heroTitleParams.topMargin = dp(4);
        FrameLayout titleLayers = new FrameLayout(context);
        titleLayers.addView(heroTitle, new FrameLayout.LayoutParams(-1, -2));
        previousHeroTitle = text("", 29f, Color.WHITE);
        previousHeroTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        previousHeroTitle.setGravity(Gravity.CENTER);
        previousHeroTitle.setMaxLines(2);
        previousHeroTitle.setMinLines(2);
        previousHeroTitle.setEllipsize(TextUtils.TruncateAt.END);
        previousHeroTitle.setShadowLayer(dp(8), 0f, dp(2), Color.argb(160, 0, 0, 0));
        previousHeroTitle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        previousHeroTitle.setVisibility(View.GONE);
        titleLayers.addView(previousHeroTitle, new FrameLayout.LayoutParams(-1, -2));
        heroCopy.addView(titleLayers, heroTitleParams);

        heroHint = text(
                "Your next favorite creator.",
                13f,
                ZeroChillUi.color(context, R.color.zc_text_secondary)
        );
        heroHint.setGravity(Gravity.CENTER);
        heroHint.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        heroHint.setMaxLines(2);
        heroHint.setMinLines(2);
        heroHint.setLineSpacing(0f, 1.05f);
        LinearLayout.LayoutParams heroHintParams = new LinearLayout.LayoutParams(-1, -2);
        heroHintParams.topMargin = dp(5);
        FrameLayout hintLayers = new FrameLayout(context);
        hintLayers.addView(heroHint, new FrameLayout.LayoutParams(-1, -2));
        previousHeroHint = text("", 13f, ZeroChillUi.color(context, R.color.zc_text_secondary));
        previousHeroHint.setGravity(Gravity.CENTER);
        previousHeroHint.setMaxLines(2);
        previousHeroHint.setMinLines(2);
        previousHeroHint.setLineSpacing(0f, 1.05f);
        previousHeroHint.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        previousHeroHint.setVisibility(View.GONE);
        hintLayers.addView(previousHeroHint, new FrameLayout.LayoutParams(-1, -2));
        heroCopy.addView(hintLayers, heroHintParams);

        LinearLayout heroActions = new LinearLayout(context);
        heroActions.setOrientation(LinearLayout.HORIZONTAL);
        heroActions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams heroActionsParams = new LinearLayout.LayoutParams(-2, -2);
        heroActionsParams.topMargin = dp(10);
        heroActionsParams.gravity = Gravity.CENTER_HORIZONTAL;
        heroCopy.addView(heroActions, heroActionsParams);

        heroAction = text("VIEW GALLERY", 13f, Color.WHITE);
        heroAction.setTypeface(null, android.graphics.Typeface.BOLD);
        heroAction.setGravity(Gravity.CENTER);
        heroAction.setVisibility(View.GONE);
        heroAction.setBackground(ContentPreferenceUi.heroButtonBackground(context));
        heroAction.setMinimumHeight(dp(48));
        heroAction.setContentDescription("Open featured creator gallery");
        heroActions.addView(heroAction, new LinearLayout.LayoutParams(dp(136), dp(48)));
        LinearLayout.LayoutParams selectorParams = new LinearLayout.LayoutParams(dp(72), dp(48));
        selectorParams.setMarginStart(dp(8));
        heroActions.addView(preferenceSelector, selectorParams);
        preferenceLoading = new android.widget.ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
        preferenceLoading.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(
                ZeroChillUi.color(context, R.color.zc_cyan)));
        preferenceLoading.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams busyParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        busyParams.setMarginStart(dp(8));
        heroActions.addView(preferenceLoading, busyParams);

        LinearLayout.LayoutParams heroDotsParams =
                new LinearLayout.LayoutParams(-2, dp(28));
        heroDotsParams.topMargin = dp(8);
        heroDotsParams.gravity = Gravity.CENTER_HORIZONTAL;
        heroCopy.addView(heroDots, heroDotsParams);

        HorizontalSwipeFrameLayout heroSwipe = new HorizontalSwipeFrameLayout(context);
        heroSwipe.setListener(new HorizontalSwipeFrameLayout.Listener() {
            @Override public void onSwipeLeft() {
                if (heroItems.size() > 1) showHero(heroIndex + 1, true);
            }

            @Override public void onSwipeRight() {
                if (heroItems.size() > 1) showHero(heroIndex - 1, true);
            }
        });
        heroSwipe.addView(heroCard, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, dp(640));
        content.addView(heroSwipe, heroParams);

        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(12), dp(16), 0);
        content.addView(body, new LinearLayout.LayoutParams(-1, -2));

        favoritesSection = new LinearLayout(context);
        favoritesSection.setOrientation(LinearLayout.VERTICAL);
        favoritesSection.setVisibility(View.GONE);

        LinearLayout favoriteHeading = new LinearLayout(context);
        favoriteHeading.setOrientation(LinearLayout.HORIZONTAL);
        favoriteHeading.setGravity(Gravity.CENTER_VERTICAL);

        TextView favoriteTitle = text("Favorite Creators", 19f, Color.WHITE);
        ZeroChillTypography.apply(favoriteTitle, ZeroChillTypography.Role.SECTION);
        favoriteHeading.addView(favoriteTitle, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView favoriteAll = text("View all", 13f, UiPalette.TEXT_SECONDARY);
        favoriteAll.setGravity(Gravity.CENTER);
        favoriteAll.setMinHeight(dp(48));
        favoriteAll.setMinWidth(dp(48));
        favoriteAll.setPadding(dp(10), dp(6), dp(4), dp(6));
        favoriteAll.setClickable(true);
        favoriteAll.setFocusable(true);
        favoriteAll.setContentDescription("View all favorite creators");
        favoriteAll.setOnClickListener(v -> listener.onViewAllFavorites());
        favoriteHeading.addView(favoriteAll);
        favoritesSection.addView(favoriteHeading);

        TextView favoriteHint = text(
                "Your saved creators",
                11.5f,
                ZeroChillUi.color(context, R.color.zc_text_muted)
        );
        LinearLayout.LayoutParams favoriteHintParams = new LinearLayout.LayoutParams(-1, -2);
        favoriteHintParams.topMargin = dp(1);
        favoritesSection.addView(favoriteHint, favoriteHintParams);

        HorizontalScrollView favoriteScroll = new HorizontalScrollView(context);
        favoriteScroll.setHorizontalScrollBarEnabled(false);
        favoriteScroll.setClipToPadding(false);
        favoriteScroll.setFillViewport(false);
        favoriteScroll.setContentDescription("Favorite creators shelf");

        favoritesRail = new LinearLayout(context);
        favoritesRail.setOrientation(LinearLayout.HORIZONTAL);
        favoritesRail.setGravity(Gravity.TOP);
        favoritesRail.setPadding(0, dp(10), dp(6), 0);
        favoriteScroll.addView(favoritesRail,
                new HorizontalScrollView.LayoutParams(-2, -1));
        favoritesSection.addView(favoriteScroll,
                new LinearLayout.LayoutParams(-1, dp(114)));

        LinearLayout.LayoutParams favoriteSectionParams =
                new LinearLayout.LayoutParams(-1, -2);
        favoriteSectionParams.setMargins(0, 0, 0, dp(12));
        body.addView(favoritesSection, favoriteSectionParams);

        loadingLabel = text(
                "Loading creators…",
                12f,
                ZeroChillUi.color(context, R.color.zc_text_muted)
        );
        loadingLabel.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(-1, -2);
        loadingParams.setMargins(0, 0, 0, dp(10));
        body.addView(loadingLabel, loadingParams);

        trendingShelf = addCreatorShelf(
                "Discover",
                "Random picks from Fapello",
                "Discover creators shelf"
        );
        newShelf = addCreatorShelf(
                "New Creators",
                "Recently added creators",
                "New creators shelf"
        );
        hotShelf = addCreatorShelf(
                "Hot",
                "Creators getting attention right now",
                "Hot creators shelf"
        );
        popularShelf = addCreatorShelf(
                "Popular",
                "Popular across OnlyFap sources",
                "Popular creators shelf"
        );

        heroCard.setOnClickListener(v -> openHero());
        heroAction.setOnClickListener(v -> openHero());

        for (String name : new String[] {"creator_favorites", "creator_catalog_v1",
                "creator_avatar_overrides_v1", "manual_creator_merges_v1"}) {
            android.content.SharedPreferences prefs =
                    context.getSharedPreferences(name, Context.MODE_PRIVATE);
            favoritePreferences.add(prefs);
            prefs.registerOnSharedPreferenceChangeListener(favoriteInvalidation);
        }
        refreshFavorites();
        coldSnapshot = ColdTabSnapshot.install(this, scroll, heroImage, "onlyfap",
                () -> shelvesFinished && (heroItem != null || startupHero != null),
                this::isUsableContentReady);
    }

    void onOpenRequested() {
        if (openStartedAt >= 0) return;
        openStartedAt = android.os.SystemClock.elapsedRealtime();
        openTimingLabel = openedBefore ? "OnlyFap return" : "OnlyFap first open";
        openedBefore = true;
        openDrawRecorded = openUsableRecorded = openFinishedRecorded = false;
        getViewTreeObserver().removeOnPreDrawListener(openDrawListener);
        getViewTreeObserver().addOnPreDrawListener(openDrawListener);
    }

    void onOpenCancelled() {
        openStartedAt = -1;
        if (getViewTreeObserver().isAlive())
            getViewTreeObserver().removeOnPreDrawListener(openDrawListener);
    }

    private boolean isUsableContentReady() {
        com.bumptech.glide.request.Request request =
                new com.bumptech.glide.request.target.DrawableImageViewTarget(heroImage).getRequest();
        return (heroItem != null || startupHero != null) && request != null && request.isComplete()
                && (favoritesRail.getChildCount() > 0 || itemCount() > 0);
    }

    private void recordOpenProgress() {
        if (openStartedAt < 0 || closed || !isShown()
                || !getGlobalVisibleRect(new android.graphics.Rect())) return;
        long elapsed = android.os.SystemClock.elapsedRealtime() - openStartedAt;
        if (!openDrawRecorded) {
            openDrawRecorded = true;
            AppPerformance.record(openTimingLabel + " -> screen drawn", elapsed);
        }
        if (!openUsableRecorded && isUsableContentReady()
                && (coldSnapshot == null || !coldSnapshot.isCoverVisible())) {
            openUsableRecorded = true;
            AppPerformance.record(openTimingLabel + " -> usable content", elapsed);
        }
        if (!openFinishedRecorded && shelvesFinished) {
            openFinishedRecorded = true;
            AppPerformance.record(openTimingLabel + " -> all shelves finished", elapsed);
        }
        if (openUsableRecorded && openFinishedRecorded) onOpenCancelled();
    }

    void beginRefresh() {
        // Keep committed Discover visible until all replacement shelves have completed.
        shelvesFinished = false;
    }

    void clear() {
        deferredHeroIndex = -1;
        finishHeroMorph();
        publishedPreference = null;
        heroGeneration++;
        for (java.util.concurrent.Future<?> task : new java.util.concurrent.Future<?>[]{
                startupTask, selectionTask, resolutionTask, discoveryTask}) {
            if (task != null) task.cancel(true);
        }
        heroCard.animate().cancel();
        heroCard.setAlpha(1f);
        startupHero = null;
        startupRequested = false;
        heroSelectionLoading = false;
        discoverCandidates = Collections.emptyList();
        trendingItems = Collections.emptyList();
        newItems = Collections.emptyList();
        hotItems = Collections.emptyList();
        popularItems = Collections.emptyList();
        shelvesFinished = false;
        trendingShelf.adapter.replace(Collections.emptyList());
        newShelf.adapter.replace(Collections.emptyList());
        hotShelf.adapter.replace(Collections.emptyList());
        popularShelf.adapter.replace(Collections.emptyList());
        trendingShelf.container.setVisibility(View.GONE);
        newShelf.container.setVisibility(View.GONE);
        hotShelf.container.setVisibility(View.GONE);
        popularShelf.container.setVisibility(View.GONE);
        heroHandler.removeCallbacksAndMessages(null);
        heroRefreshHandler.removeCallbacksAndMessages(null);
        Glide.with(preferenceImage).clear(preferenceImage);
        preferenceImage.setAlpha(0f);
        if (!preferenceHeroPending) {
            Glide.with(heroImage).clear(heroImage);
            heroImage.setImageDrawable(new ColorDrawable(Color.rgb(13, 16, 19)));
        }
        heroArtworkReady = false;
        heroAction.setEnabled(!preferenceHeroPending);
        heroCard.setEnabled(!preferenceHeroPending);
        preferenceLoading.setContentDescription("Loading " + discoveryPreference.label);
        preferenceLoading.setVisibility(preferenceHeroPending ? View.VISIBLE : View.INVISIBLE);
        heroItems.clear();
        heroDiscoveryItems.clear();
        heroResolveInFlight = 0;
        heroDiscoveryNextPage = HERO_DISCOVERY_FIRST_PAGE;
        heroDiscoveryLoading = false;
        heroDiscoveryExhausted = false;
        requestedHeroCreators.clear();
        rejectedHeroUrls.clear();
        fapelloPostsBlocked = false;
        heroItem = null;
        heroIndex = -1;
        heroDots.setVisibility(View.INVISIBLE);
        if (!preferenceHeroPending) {
            heroTitle.setText("Discover creators");
            heroHint.setText("Your next favorite creator.");
            heroAction.setVisibility(View.GONE);
        }
        loadingLabel.setText("Loading creators…");
        loadingLabel.setVisibility(View.VISIBLE);
        refreshFavorites();
        if (active) {
            requestStartupHero();
            scheduleHeroSetRefresh();
        }
    }

    void setTrending(List<NativeContentItem> items) {
        if ((items == null || items.isEmpty()) && !discoverCandidates.isEmpty()) return;
        discoverCandidates = safe(items);
        if (shelvesFinished) refreshDiscoverShelf();
        // Creator metadata is persisted by the loader before the final shelf publish.
        // Rebuild restored account favorites so newly learned artwork replaces placeholders.
        refreshFavorites();
    }

    void setNewCreators(List<NativeContentItem> items) {
        if ((items == null || items.isEmpty()) && !newItems.isEmpty()) return;
        newItems = safe(items);
        newShelf.adapter.replace(newItems);
        newShelf.container.setVisibility(newItems.isEmpty() ? View.GONE : View.VISIBLE);
        if (active && heroItem == null && startupHero == null) {
            for (NativeContentItem creator : newItems) {
                loadStartupThumbnail(creator);
                if (startupHero != null) break;
            }
        }
        refreshFavorites();
    }

    void setHot(List<NativeContentItem> items) {
        if ((items == null || items.isEmpty()) && !hotItems.isEmpty()) return;
        hotItems = safe(items);
        hotShelf.adapter.replace(hotItems);
        hotShelf.container.setVisibility(hotItems.isEmpty() ? View.GONE : View.VISIBLE);
        refreshFavorites();
    }

    void setPopular(List<NativeContentItem> items) {
        if ((items == null || items.isEmpty()) && !popularItems.isEmpty()) return;
        popularItems = safe(items);
        popularShelf.adapter.replace(popularItems);
        popularShelf.container.setVisibility(popularItems.isEmpty() ? View.GONE : View.VISIBLE);
        refreshFavorites();
    }

    private void refreshDiscoverShelf() {
        trendingItems = FapzoneCreatorRepository.selectDiscoverItems(
                discoverCandidates,
                newItems,
                hotItems,
                popularItems
        );
        trendingShelf.adapter.replace(trendingItems);
        trendingShelf.container.setVisibility(
                trendingItems.isEmpty() ? View.GONE : View.VISIBLE
        );
    }

    void refreshFavorites() {
        favoritesDirty = true;
        if (closed || !active || favoritesLoading) return;
        favoritesLoading = true;
        favoritesDirty = false;
        metadataIo.execute(() -> {
            List<String> inputs = new ArrayList<>();
            for (android.content.SharedPreferences prefs : favoritePreferences) {
                inputs.add(PreferenceSnapshot.key(prefs));
            }
            List<CreatorCatalog.FavoriteGroup> favorites = CreatorCatalog.favoriteGroups(getContext());
            List<CreatorAvatarOverrideStore.Override> avatars = CreatorAvatarOverrideStore.load(getContext());
            post(() -> work.schedule("favorite-publish", () -> {
                favoritesLoading = false;
                if (closed) return;
                if (!inputs.equals(favoriteInputs)) {
                    favoriteInputs = inputs;
                    favoritesRail.removeAllViews();
                    int count = Math.min(10, favorites.size());
                    for (int i = 0; i < count; i++) {
                        favoritesRail.addView(favoriteCreatorCard(favorites.get(i), avatars),
                                new LinearLayout.LayoutParams(dp(94), dp(108)));
                    }
                    favoritesSection.setVisibility(favorites.isEmpty() ? View.GONE : View.VISIBLE);
                    trendingShelf.adapter.refreshFavorites();
                    newShelf.adapter.refreshFavorites();
                    hotShelf.adapter.refreshFavorites();
                    popularShelf.adapter.refreshFavorites();
                }
                refreshHeroCandidates();
                if (favoritesDirty) refreshFavorites();
            }));
        });
    }

    void finishLoading() {
        shelvesFinished = true;
        if (discoveryPreference != ContentPreference.ALL) return;
        publishedPreference = discoveryPreference;
        if (active) post(this::presentPreference);
        // Select once against final shelves, never against progressively growing exclusions.
        refreshDiscoverShelf();
        refreshHeroCandidates();
        if (heroItems.size() < HERO_MAX_ITEMS) requestHeroCandidates();
        loadingLabel.setVisibility(itemCount() == 0 ? View.VISIBLE : View.GONE);
        if (itemCount() == 0) {
            loadingLabel.setText("OnlyFap creators could not load right now.");
        }
        applyPendingRestoreState();
    }

    int itemCount() {
        return trendingItems.size() + newItems.size() + hotItems.size() + popularItems.size();
    }

    void setProfileActive(boolean active) {
        if (profileAvatar == null) return;
        if (active) profileAvatar.resume();
        else profileAvatar.pause();
    }

    void setActive(boolean active) {
        setProfileActive(active);
        if (this.active == active) return;
        this.active = active;
        if (!active) {
            finishHeroMorph();
            ContentPreferenceUi.clearPresentation(this);
            heroHandler.removeCallbacksAndMessages(null);
            heroRefreshHandler.removeCallbacksAndMessages(null);
            return;
        }
        if (publishedPreference == discoveryPreference) post(this::presentPreference);
        if (preferenceHeroPending && heroItem != null) loadHeroArtwork(heroItem);
        requestStartupHero();
        requestHeroCandidates();
        refreshFavorites();
        scheduleHeroRotation();
        scheduleHeroSetRefresh();
    }

    void setNavigationScrollListener(View.OnScrollChangeListener listener) {
        scroll.setOnScrollChangeListener(listener);
    }

    void saveState(Bundle out) {
        if (out == null) return;
        out.putString("content_preference", discoveryPreference.value);
        out.putInt("scroll_y", scroll.getScrollY());
        out.putInt("hero_index", heroIndex);
        saveRailState(out, "trending", trendingShelf.rail);
        saveRailState(out, "new", newShelf.rail);
        saveRailState(out, "hot", hotShelf.rail);
        saveRailState(out, "popular", popularShelf.rail);
    }

    void restoreState(Bundle state) {
        pendingRestoreState = state != null && discoveryPreference.value.equals(
                state.getString("content_preference", "")) ? new Bundle(state) : null;
    }

    @Override protected void onDetachedFromWindow() {
        finishHeroMorph();
        ContentPreferenceUi.clearPresentation(this);
        super.onDetachedFromWindow();
    }

    void close() {
        onOpenCancelled();
        if (profileAvatar != null) profileAvatar.close();
        closed = true;
        finishHeroMorph();
        ContentPreferenceUi.clearPresentation(this);
        Glide.with(preferenceImage).clear(preferenceImage);
        active = false;
        heroHandler.removeCallbacksAndMessages(null);
        heroRefreshHandler.removeCallbacksAndMessages(null);
        for (android.content.SharedPreferences prefs : favoritePreferences) {
            prefs.unregisterOnSharedPreferenceChangeListener(favoriteInvalidation);
        }
        metadataIo.shutdownNow();
        heroIo.shutdownNow();
        heroDiscoveryIo.shutdownNow();
        Glide.with(heroImage).clear(heroImage);
        trendingShelf.adapter.close();
        newShelf.adapter.close();
        hotShelf.adapter.close();
        popularShelf.adapter.close();
    }

    private CreatorShelf addCreatorShelf(String title, String subtitle, String description) {
        LinearLayout section = new LinearLayout(getContext());
        section.setOrientation(LinearLayout.VERTICAL);
        section.setVisibility(View.GONE);

        TextView titleView = text(title, 19f, Color.WHITE);
        ZeroChillTypography.apply(titleView, ZeroChillTypography.Role.SECTION);
        section.addView(titleView);

        TextView subtitleView = text(
                subtitle,
                11.5f,
                ZeroChillUi.color(getContext(), R.color.zc_text_muted)
        );
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(1);
        section.addView(subtitleView, subtitleParams);

        RecyclerView rail = new RecyclerView(getContext());
        rail.setLayoutManager(new GridLayoutManager(
                getContext(),
                2,
                RecyclerView.HORIZONTAL,
                false
        ));
        rail.setHorizontalScrollBarEnabled(false);
        rail.setClipToPadding(false);
        rail.setPadding(0, dp(10), dp(8), dp(2));
        rail.setItemAnimator(null);
        rail.setContentDescription(description);

        CreatorPortraitAdapter adapter = new CreatorPortraitAdapter(listener);
        rail.setAdapter(adapter);
        // Preserve the source portrait ratio, with a small phone-friendly size reduction.
        int cardHeight = Math.round(dp(128) * 216f / 148f);
        int railHeight = 2 * (cardHeight + dp(10)) + rail.getPaddingTop() + rail.getPaddingBottom();
        section.addView(rail, new LinearLayout.LayoutParams(-1, railHeight));

        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
        sectionParams.setMargins(0, 0, 0, dp(14));
        body.addView(section, sectionParams);

        return new CreatorShelf(section, rail, adapter);
    }

    private View favoriteCreatorCard(
            CreatorCatalog.FavoriteGroup group,
            List<CreatorAvatarOverrideStore.Override> avatarOverrides
    ) {
        NativeContentItem creator = group.item;
        LinearLayout wrapper = new LinearLayout(getContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
        wrapper.setClickable(true);
        wrapper.setFocusable(true);
        wrapper.setContentDescription("Open " + creator.title + " gallery");
        wrapper.setOnClickListener(v -> listener.onOpenFavoriteCreator(group));
        ZeroChillMotion.installPressFeedback(wrapper);

        MaterialCardView avatar = new MaterialCardView(getContext());
        avatar.setRadius(dp(36));
        avatar.setCardElevation(0f);
        avatar.setStrokeWidth(0);
        avatar.setClipToOutline(true);
        avatar.setCardBackgroundColor(
                ZeroChillUi.color(getContext(), R.color.zc_surface_pressed));

        FrameLayout frame = new FrameLayout(getContext());
        avatar.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

        TextView initials = text(
                initials(creator.title),
                20f,
                Color.argb(110, 255, 255, 255)
        );
        initials.setTypeface(null, android.graphics.Typeface.BOLD);
        initials.setGravity(Gravity.CENTER);
        frame.addView(initials, new FrameLayout.LayoutParams(-1, -1));

        CreatorAvatarImageView image = new CreatorAvatarImageView(getContext());
        image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        image.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        image.setClipToOutline(true);
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
        loadCreatorImage(image, group, avatarOverrides);

        FrameLayout avatarWrap = new FrameLayout(getContext());
        FrameLayout.LayoutParams avatarParams =
                new FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER);
        avatarWrap.addView(avatar, avatarParams);

        TextView mergeBadge = CreatorMergeUi.badge(getContext());
        CreatorMergeUi.bind(
                mergeBadge,
                group,
                () -> CreatorMergeUi.showMembers(getContext(), group)
        );
        FrameLayout.LayoutParams mergeBadgeParams =
                new FrameLayout.LayoutParams(-2, dp(22), Gravity.BOTTOM | Gravity.END);
        mergeBadgeParams.setMargins(0, 0, 0, 0);
        avatarWrap.addView(mergeBadge, mergeBadgeParams);

        wrapper.addView(avatarWrap, new LinearLayout.LayoutParams(dp(76), dp(76)));

        TextView name = text(
                creator.title,
                11.5f,
                ZeroChillUi.color(getContext(), R.color.zc_text_primary)
        );
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(dp(90), -2);
        nameParams.topMargin = dp(1);
        wrapper.addView(name, nameParams);
        return wrapper;
    }

    private boolean startupAllowed(HeroCandidate candidate) {
        if (candidate == null) return false;
        if (discoveryPreference != ContentPreference.ALL)
            return OnlyFapHeroPolicy.inCategory(candidate.creator, discoverCandidates);
        return !OnlyFapHeroPolicy.excluded(
                CreatorFavoriteStore.names(getContext()), trendingItems, hotItems, popularItems)
                .contains(CreatorFavoriteStore.key(candidate.creator));
    }

    private void requestStartupHero() {
        if (closed || !active || heroItem != null || startupRequested) return;
        if (discoveryPreference != ContentPreference.ALL) {
            for (NativeContentItem creator : discoverCandidates) {
                loadStartupThumbnail(creator);
                if (startupHero != null) { startupRequested = true; break; }
            }
            return;
        }
        startupRequested = true;
        final int generation = heroGeneration;
        startupTask = metadataIo.submit(() -> {
            OnlyFapHeroMemory.Entry remembered = OnlyFapHeroMemory.read(getContext());
            List<NativeContentItem> known = remembered != null ? Collections.emptyList() : OnlyFapHeroPolicy.select(Collections.emptyList(),
                    CreatorCatalog.all(getContext()), CreatorFavoriteStore.names(getContext()),
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), 1);
            post(() -> {
                if (closed || generation != heroGeneration || heroItem != null) return;
                if (!active) { startupRequested = false; return; }
                HeroCandidate cached = remembered == null ? null : new HeroCandidate(
                        remembered.creator, remembered.imageUrl, remembered.referer);
                NativeContentItem fallback = known.isEmpty() ? null : known.get(0);
                if (startupAllowed(cached)) loadStartupHero(cached, true, fallback);
                else loadStartupThumbnail(fallback);
            });
        });
    }

    private void loadStartupThumbnail(NativeContentItem creator) {
        if (creator == null || !OnlyFapHeroMemory.isRemoteImage(creator.imageUrl)) return;
        HeroCandidate thumbnail = new HeroCandidate(creator, creator.imageUrl, creator.url);
        if (startupAllowed(thumbnail)) loadStartupHero(thumbnail, false, null);
    }

    private void loadStartupHero(HeroCandidate candidate, boolean cacheOnly,
            NativeContentItem fallback) {
        if (closed || heroItem != null || !startupAllowed(candidate)) return;
        startupHero = candidate;
        if (preferenceHeroPending) {
            loadPreferenceArtwork(candidate, true, cacheOnly, fallback);
            return;
        }
        Drawable previous = heroImage.getDrawable();
        Glide.with(heroImage).load(remoteImage(candidate.currentImageUrl(), candidate.currentReferer()))
                .centerCrop().diskCacheStrategy(DiskCacheStrategy.ALL)
                .onlyRetrieveFromCache(cacheOnly).transition(ThumbnailFades.hero())
                .placeholder(previous).error(previous)
                .listener(new RequestListener<Drawable>() {
                    @Override public boolean onLoadFailed(GlideException error, Object model,
                            Target<Drawable> target, boolean first) {
                        post(() -> {
                            if (closed || startupHero != candidate || heroItem != null) return;
                            startupHero = null;
                            if (cacheOnly) {
                                metadataIo.execute(() -> OnlyFapHeroMemory.forget(
                                        getContext(), candidate.currentImageUrl()));
                                loadStartupThumbnail(OnlyFapHeroMemory.isRemoteImage(candidate.creator.imageUrl)
                                        ? candidate.creator : fallback);
                            }
                        });
                        return false;
                    }
                    @Override public boolean onResourceReady(Drawable resource, Object model,
                            Target<Drawable> target, DataSource source, boolean first) {
                        if (heroItem != null || startupHero != candidate || !startupAllowed(candidate)) {
                            if (startupHero == candidate) startupHero = null;
                            return true;
                        }
                        heroArtworkReady = true;
                        showStartupCopy(candidate);
                        post(OnlyFapHubView.this::presentPreference);
                        return false;
                    }
                }).into(heroImage);
    }

    private void showStartupCopy(HeroCandidate candidate) {
        heroTitle.setText(candidate.creator.title);
        heroHint.setText(heroDescription(candidate.creator));
        heroAction.setVisibility(View.VISIBLE);
        heroCard.setContentDescription("Open " + candidate.creator.title + " gallery");
    }

    /** Reject stale work before coalescing, so it cannot replace a new generation's queue entry. */
    private void postHeroWork(int generation, String key, Runnable task) {
        post(() -> {
            if (closed || generation != heroGeneration) return;
            work.schedule(key, () -> {
                if (!closed && generation == heroGeneration) task.run();
            });
        });
    }

    private void requestHeroCandidates() {
        if (closed || !active || heroSelectionLoading || heroResolveInFlight > 0 ||
                heroItems.size() >= HERO_MAX_ITEMS) return;
        heroSelectionLoading = true;
        final int generation = heroGeneration;
        final ContentPreference mode = discoveryPreference;
        final List<NativeContentItem> category = new ArrayList<>(discoverCandidates);
        ArrayList<NativeContentItem> discoveryFirst = new ArrayList<>(newItems);
        discoveryFirst.addAll(heroDiscoveryItems);
        List<NativeContentItem> trending = new ArrayList<>(trendingItems);
        List<NativeContentItem> hot = new ArrayList<>(hotItems);
        List<NativeContentItem> popular = new ArrayList<>(popularItems);
        boolean finished = shelvesFinished;
        selectionTask = heroIo.submit(() -> {
            if (closed || generation != heroGeneration) return;
            List<NativeContentItem> candidates = mode != ContentPreference.ALL
                    ? OnlyFapHeroPolicy.selectCategory(category, HERO_SEARCH_LIMIT)
                    : OnlyFapHeroPolicy.select(
                    discoveryFirst, finished ? CreatorCatalog.all(getContext()) : Collections.emptyList(),
                    CreatorFavoriteStore.names(getContext()), trending, hot, popular,
                    OnlyFapHeroHistory.recent(getContext()), HERO_SEARCH_LIMIT);
            postHeroWork(generation, "hero-selection", () -> {
                if (closed || generation != heroGeneration) return;
                heroSelectionLoading = false;
                if (!active) return;
                List<NativeContentItem> replacements = OnlyFapHeroPolicy.nextUnrequested(
                        candidates, requestedHeroCreators, 1);
                for (NativeContentItem creator : replacements) {
                    if (!requestedHeroCreators.add(CreatorFavoriteStore.key(creator))) continue;
                    heroResolveInFlight++;
                    resolveHeroAsync(creator);
                }
                if (finished && replacements.isEmpty()) {
                    if (mode != ContentPreference.ALL && heroItems.isEmpty()) showPreferenceEmptyState();
                    else requestMoreHeroDiscovery();
                }
            });
        });
    }

    private void requestMoreHeroDiscovery() {
        if (discoveryPreference != ContentPreference.ALL) return;
        if (closed || !active || heroDiscoveryLoading || heroDiscoveryExhausted ||
                heroDiscoveryNextPage > HERO_DISCOVERY_LAST_PAGE) {
            if (heroDiscoveryExhausted && !heroDiscoveryLoading && heroItems.isEmpty() && !heroSelectionLoading && heroResolveInFlight == 0)
                showPreferenceEmptyState();
            if (heroDiscoveryNextPage > HERO_DISCOVERY_LAST_PAGE) {
                heroDiscoveryExhausted = true;
                if (!heroDiscoveryLoading && heroItems.isEmpty() && heroResolveInFlight == 0) showPreferenceEmptyState();
            }
            return;
        }

        final int generation = heroGeneration;
        final int page = heroDiscoveryNextPage++;
        heroDiscoveryLoading = true;
        discoveryTask = heroDiscoveryIo.submit(() -> {
            List<NativeContentItem> discovered = Collections.emptyList();
            boolean failed = false;
            try {
                FapelloRepository repository = new FapelloRepository();
                List<FapelloRepository.Model> models = repository.fetchModelListing(
                        getContext().getApplicationContext(),
                        FapelloRepository.LIST_NEW,
                        page
                );
                ArrayList<NativeContentItem> items = new ArrayList<>();
                if (models != null) {
                    for (FapelloRepository.Model model : models) {
                        if (model != null && FapelloRepository.isModelUrl(model.url)) {
                            items.add(CreatorCatalog.fromModel(model));
                        }
                    }
                }
                discovered = items;
            } catch (IOException ignored) {
                failed = true;
            }

            final List<NativeContentItem> result = discovered;
            final boolean pageFailed = failed;
            postHeroWork(generation, "hero-discovery", () -> {
                if (closed || generation != heroGeneration) return;
                heroDiscoveryLoading = false;
                if (pageFailed) {
                    heroDiscoveryExhausted = true;
                    return;
                }

                OnlyFapHeroPolicy.appendUniqueCreators(heroDiscoveryItems, result);
                if (result.isEmpty() || heroDiscoveryNextPage > HERO_DISCOVERY_LAST_PAGE) {
                    heroDiscoveryExhausted = result.isEmpty();
                }
                requestHeroCandidates();
            });
        });
    }

    private void refreshHeroCandidates() {
        if (closed) return;
        Set<String> excluded = discoveryPreference == ContentPreference.ALL
                ? OnlyFapHeroPolicy.excluded(CreatorFavoriteStore.names(getContext()),
                        trendingItems, hotItems, popularItems) : new HashSet<>();
        if (discoveryPreference != ContentPreference.ALL) {
            for (HeroCandidate item : heroItems)
                if (!OnlyFapHeroPolicy.inCategory(item.creator, discoverCandidates))
                    excluded.add(CreatorFavoriteStore.key(item.creator));
            if (startupHero != null && !startupAllowed(startupHero))
                excluded.add(CreatorFavoriteStore.key(startupHero.creator));
        }
        if (startupHero != null && excluded.contains(CreatorFavoriteStore.key(startupHero.creator))) {
            startupHero = null;
            if (heroItem == null) {
                Glide.with(heroImage).clear(heroImage);
                heroImage.setImageDrawable(new ColorDrawable(Color.rgb(13, 16, 19)));
                heroTitle.setText("Discover creators");
                heroAction.setVisibility(View.GONE);
            }
        }
        boolean selectedRemoved = heroItem != null &&
                excluded.contains(CreatorFavoriteStore.key(heroItem.creator));
        heroItems.removeIf(item -> excluded.contains(CreatorFavoriteStore.key(item.creator)));
        if (selectedRemoved) {
            heroItem = null;
            heroIndex = -1;
            if (!heroItems.isEmpty()) showHero(0, false);
            else if (!preferenceHeroPending) {
                heroHandler.removeCallbacksAndMessages(null);
                Glide.with(heroImage).clear(heroImage);
                heroImage.setImageDrawable(new ColorDrawable(Color.rgb(13, 16, 19)));
                heroTitle.setText("Discover creators");
                heroAction.setVisibility(View.GONE);
                heroDots.setVisibility(View.GONE);
            }
        } else if (heroItem != null) {
            heroIndex = heroItems.indexOf(heroItem);
            updateHeroDots();
        }
        requestHeroCandidates();
    }

    private void resolveHeroAsync(NativeContentItem creator) {
        final int generation = heroGeneration;
        final ContentPreference mode = discoveryPreference;
        resolutionTask = heroIo.submit(() -> {
            if (closed || generation != heroGeneration) return;

            ArrayList<OnlyFapHeroPolicy.Artwork> portraitArtwork = new ArrayList<>();

            // Prefer Fapello for portrait discovery. Learned/backfill creators may not carry a
            // Fapello URL, so search by creator name instead of skipping the source entirely.
            if (mode == ContentPreference.ALL) try {
                FapelloRepository fapello = new FapelloRepository();
                FapelloRepository.Model model = null;
                if (FapelloRepository.isModelUrl(creator.url)) {
                    model = new FapelloRepository.Model(
                            creator.title,
                            creator.url,
                            creator.imageUrl
                    );
                } else {
                    String query = clean(creator.searchQuery).isEmpty()
                            ? creator.title
                            : creator.searchQuery;
                    model = chooseFapelloMatch(
                            query,
                            fapello.searchConfirmedModels(
                                    getContext().getApplicationContext(),
                                    query,
                                    6
                            )
                    );
                }

                if (model != null) {
                    for (int mediaPage = 1;
                            active && !closed && generation == heroGeneration &&
                                    mediaPage <= 2 && portraitArtwork.size() < 2;
                            mediaPage++) {
                        List<NativeContentItem> media = fapello.fetchModelMedia(
                                getContext().getApplicationContext(),
                                model,
                                mediaPage
                        );
                        // Gallery previews are cropped to a different aspect ratio on the
                        // device. Qualify the original, never the thumbnail. Bound post-page
                        // requests to preserve the fast shelf and gallery loading path.
                        List<NativeContentItem> originals =
                                OnlyFapHeroPolicy.originalResolutionCandidates(
                                        media, HERO_ORIGINAL_CHECKS_PER_PAGE);
                        for (NativeContentItem item : originals) {
                            if (!active || closed || generation != heroGeneration ||
                                    portraitArtwork.size() >= 2) break;
                            String directOriginal =
                                    FapelloRepository.originalUrlFromPreview(item.imageUrl);
                            if (!directOriginal.isEmpty()) {
                                OnlyFapHeroPolicy.Artwork directChoice =
                                        new OnlyFapHeroPolicy.Artwork(
                                                directOriginal, model.url, false);
                                if (isGoodPortraitArtwork(directChoice)) {
                                    portraitArtwork.add(directChoice);
                                    continue;
                                }
                            }
                            if (fapelloPostsBlocked) continue;
                            try {
                                CrazyShitRepository.StreamInfo full = fapello.resolvePlayable(
                                        getContext().getApplicationContext(),
                                        item.url
                                );
                                OnlyFapHeroPolicy.Artwork choice =
                                        new OnlyFapHeroPolicy.Artwork(
                                                full.mediaUrl,
                                                full.requestReferer,
                                                false
                                        );
                                if (isGoodPortraitArtwork(choice)) {
                                    portraitArtwork.add(choice);
                                }
                            } catch (IOException error) {
                                if (error instanceof FapelloSourceException &&
                                        ((FapelloSourceException) error).reason ==
                                                FapelloSourceException.Reason.BLOCKED) {
                                    if (generation == heroGeneration) fapelloPostsBlocked = true;
                                }
                            }
                        }
                    }
                }
            } catch (IOException ignored) { }

            // If Fapello did not provide enough usable portrait media, try OnlyHaven.
            if (active && !closed && generation == heroGeneration && portraitArtwork.size() < 2) {
                try {
                    OnlyHavenRepository repository = new OnlyHavenRepository();
                    String query = clean(creator.searchQuery).isEmpty()
                            ? creator.title
                            : creator.searchQuery;
                    OnlyHavenRepository.Creator best;
                    if (mode != ContentPreference.ALL) {
                        // Resolve the exact category profile, never a name-based substitute.
                        java.util.regex.Matcher profile = RemoteSourceConfigManager.snapshot()
                                .onlyHaven.creatorUrlPattern.matcher(creator.url);
                        best = profile.find() && profile.groupCount() >= 2
                                ? new OnlyHavenRepository.Creator(profile.group(1), profile.group(2),
                                        creator.title, creator.url, creator.imageUrl) : null;
                    } else {
                        best = chooseOnlyHavenMatch(query, repository.searchCreators(
                                getContext().getApplicationContext(), query, 6));
                    }
                    if (best != null) {
                        List<NativeContentItem> media = repository.fetchCreatorMedia(
                                getContext().getApplicationContext(),
                                best,
                                1,
                                12
                        );
                        ArrayList<OnlyFapHeroPolicy.Artwork> candidates = new ArrayList<>();
                        OnlyFapHeroPolicy.addDirectImages(candidates, media, best.url);
                        for (OnlyFapHeroPolicy.Artwork choice : candidates) {
                            if (!active || closed || generation != heroGeneration ||
                                    portraitArtwork.size() >= 2) break;
                            if (isGoodPortraitArtwork(choice)) {
                                portraitArtwork.add(choice);
                            }
                        }
                    }
                } catch (IOException ignored) { }
            }

            List<OnlyFapHeroPolicy.Artwork> choices =
                    OnlyFapHeroPolicy.distinctArtwork(portraitArtwork);
            boolean interruptedByNavigation = !active;
            postHeroWork(generation, "hero-resolution", () -> {
                if (closed || generation != heroGeneration) return;
                if (interruptedByNavigation) requestedHeroCreators.remove(CreatorFavoriteStore.key(creator));
                finishHeroResolution(generation, creator, choices);
            });
        });
    }

    private void finishHeroResolution(
            int generation,
            NativeContentItem creator,
            List<OnlyFapHeroPolicy.Artwork> choices
    ) {
        if (closed || generation != heroGeneration) return;
        heroResolveInFlight = Math.max(0, heroResolveInFlight - 1);

        // A rejected portrait candidate does not consume a hero slot. Keep searching deeper
        // into the discovery pool until all eight slots are filled or candidates are exhausted.
        if (choices != null && !choices.isEmpty() && heroItems.size() < HERO_MAX_ITEMS) {
            addHeroCandidate(new HeroCandidate(creator, choices));
        }
        requestHeroCandidates();
    }

    private boolean isGoodPortraitArtwork(OnlyFapHeroPolicy.Artwork choice) {
        if (choice == null || clean(choice.url).isEmpty()) return false;
        FutureTarget<Bitmap> target = null;
        try {
            target = Glide.with(getContext().getApplicationContext())
                    .asBitmap()
                    .load(remoteImage(choice.url, choice.referer))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .fitCenter()
                    .submit(360, 540);
            Bitmap bitmap = target.get();
            return bitmap != null &&
                    OnlyFapHeroPolicy.isGoodPortraitDimensions(
                            bitmap.getWidth(),
                            bitmap.getHeight()
                    );
        } catch (Exception ignored) {
            return false;
        } finally {
            if (target != null) target.cancel(true);
        }
    }

    private FapelloRepository.Model chooseFapelloMatch(
            String query,
            List<FapelloRepository.Model> matches
    ) {
        if (matches == null || matches.isEmpty()) return null;
        FapelloRepository.Model best = null;
        int bestRank = Integer.MAX_VALUE;
        for (FapelloRepository.Model candidate : matches) {
            if (candidate == null) continue;
            int rank = CreatorNameMatcher.rank(candidate.name, query);
            if (rank < bestRank) {
                best = candidate;
                bestRank = rank;
            }
        }
        return bestRank == Integer.MAX_VALUE ? null : best;
    }

    private OnlyHavenRepository.Creator chooseOnlyHavenMatch(
            String query,
            List<OnlyHavenRepository.Creator> matches
    ) {
        if (matches == null || matches.isEmpty()) return null;
        OnlyHavenRepository.Creator best = null;
        int bestRank = Integer.MAX_VALUE;
        for (OnlyHavenRepository.Creator candidate : matches) {
            if (candidate == null) continue;
            int rank = CreatorNameMatcher.rank(candidate.name, query);
            if (rank < bestRank) {
                best = candidate;
                bestRank = rank;
            }
        }
        return bestRank == Integer.MAX_VALUE ? null : best;
    }

    private void addHeroCandidate(HeroCandidate candidate) {
        if (closed || candidate == null || heroItems.size() >= HERO_MAX_ITEMS) return;
        String key = CreatorFavoriteStore.key(candidate.creator);
        if (key.isEmpty() || findHeroCandidate(key) != null || !startupAllowed(candidate)) return;
        heroItems.add(candidate);
        if (discoveryPreference == ContentPreference.ALL)
            OnlyFapHeroHistory.remember(getContext(), candidate.creator);
        if (heroItem == null) {
            showHero(0, false);
        } else {
            updateHeroDots();
            scheduleHeroRotation();
        }
    }

    private HeroCandidate findHeroCandidate(String key) {
        if (key == null || key.isEmpty()) return null;
        for (HeroCandidate candidate : heroItems) {
            if (key.equals(CreatorFavoriteStore.key(candidate.creator))) {
                return candidate;
            }
        }
        return null;
    }

    private void showHero(int requestedIndex, boolean animate) {
        if (heroItems.isEmpty()) return;
        if (heroMorph != null) { deferredHeroIndex = requestedIndex; return; }
        int index = Math.floorMod(requestedIndex, heroItems.size());
        HeroCandidate next = heroItems.get(index);
        while (rejectedHeroUrls.contains(next.currentImageUrl()) && next.advance()) { }
        if (rejectedHeroUrls.contains(next.currentImageUrl())) {
            rejectHero(next);
            return;
        }

        finishHeroMorph();
        heroIndex = index;
        heroItem = next;
        if (preferenceHeroPending) {
            loadHeroArtwork(next);
            return;
        }
        heroTitle.setText(next.creator.title);
        heroHint.setText(heroDescription(next.creator));
        heroAction.setVisibility(View.VISIBLE);
        heroCard.setContentDescription("Open " + next.creator.title + " gallery");
        updateHeroDots();

        if (animate) {
            heroCard.animate().cancel();
            heroCard.animate()
                    .alpha(0.72f)
                    .setDuration(110L)
                    .withEndAction(() -> {
                        loadHeroArtwork(next);
                        heroCard.animate().alpha(1f).setDuration(180L).start();
                    })
                    .start();
        } else {
            loadHeroArtwork(next);
        }
        scheduleHeroRotation();
    }

    private void loadHeroArtwork(HeroCandidate candidate) {
        if (closed || candidate == null || heroItem != candidate || !startupAllowed(candidate)) return;
        if (preferenceHeroPending) {
            loadPreferenceArtwork(candidate);
            return;
        }
        final String imageUrl = candidate.currentImageUrl();
        final String referer = candidate.currentReferer();
        if (clean(imageUrl).isEmpty()) {
            handleHeroArtworkFailure(candidate, imageUrl);
            return;
        }
        try {
            Drawable previous = heroImage.getDrawable();
            com.bumptech.glide.RequestBuilder<Drawable> request = Glide.with(heroImage)
                    .load(remoteImage(imageUrl, referer))
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .transition(ThumbnailFades.hero())
                    .placeholder(previous != null ? previous : new ColorDrawable(Color.rgb(13, 16, 19)))
                    .error(previous != null ? previous : new ColorDrawable(Color.rgb(13, 16, 19)))
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(
                                GlideException e,
                                Object model,
                                Target<Drawable> target,
                                boolean first
                        ) {
                            post(() -> handleHeroArtworkFailure(
                                    candidate,
                                    imageUrl
                            ));
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(
                                Drawable resource,
                                Object model,
                                Target<Drawable> target,
                                DataSource dataSource,
                                boolean first
                        ) {
                            if (closed || heroItem != candidate || !startupAllowed(candidate)
                                    || !imageUrl.equals(candidate.currentImageUrl())) return true;
                            {
                                heroArtworkReady = true;
                                post(OnlyFapHubView.this::presentPreference);
                                startupHero = null;
                                if (discoveryPreference == ContentPreference.ALL && !candidate.usingAvatar()) metadataIo.execute(() ->
                                        OnlyFapHeroMemory.remember(getContext(), candidate.creator, imageUrl, referer));
                            }
                            return false;
                        }
                    });
            if (startupHero != null && previous != null) {
                request.transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.with(
                        (source, first) -> new com.bumptech.glide.request.transition.DrawableCrossFadeTransition(350, true)));
            }
            request.into(heroImage);
        } catch (Exception ignored) {
            handleHeroArtworkFailure(candidate, imageUrl);
        }
    }

    /** A separate Glide target retains ownership of the outgoing image until the fade ends. */
    private void loadPreferenceArtwork(HeroCandidate candidate) {
        loadPreferenceArtwork(candidate, false, false, null);
    }

    private boolean currentPreferenceArtwork(int generation, HeroCandidate candidate, String url,
            boolean startup) {
        return !closed && generation == heroGeneration && preferenceHeroPending
                && (startup ? heroItem == null && startupHero == candidate : heroItem == candidate)
                && url.equals(candidate.currentImageUrl()) && startupAllowed(candidate);
    }

    private void loadPreferenceArtwork(HeroCandidate candidate, boolean startup, boolean cacheOnly,
            NativeContentItem fallback) {
        final int generation = heroGeneration;
        final String url = candidate.currentImageUrl();
        final ImageView targetView = preferenceImage;
        if (clean(url).isEmpty()) { handleHeroArtworkFailure(candidate, url); return; }
        Glide.with(targetView).load(remoteImage(url, candidate.currentReferer()))
                .centerCrop().diskCacheStrategy(DiskCacheStrategy.ALL).dontAnimate()
                .onlyRetrieveFromCache(cacheOnly)
                .listener(new RequestListener<Drawable>() {
                    @Override public boolean onLoadFailed(GlideException error, Object model,
                            Target<Drawable> target, boolean first) {
                        post(() -> {
                            if (!currentPreferenceArtwork(generation, candidate, url, startup)) return;
                            if (!startup) { handleHeroArtworkFailure(candidate, url); return; }
                            startupHero = null;
                            if (cacheOnly) {
                                metadataIo.execute(() -> OnlyFapHeroMemory.forget(getContext(), url));
                                loadStartupThumbnail(OnlyFapHeroMemory.isRemoteImage(candidate.creator.imageUrl)
                                        ? candidate.creator : fallback);
                            }
                        });
                        return false;
                    }
                    @Override public boolean onResourceReady(Drawable resource, Object model,
                            Target<Drawable> target, DataSource source, boolean first) {
                        if (!currentPreferenceArtwork(generation, candidate, url, startup)) return true;
                        post(() -> {
                            if (currentPreferenceArtwork(generation, candidate, url, startup))
                                commitPreferenceArtwork(generation, candidate, resource);
                        });
                        return false;
                    }
                }).into(targetView);
    }

    private void commitPreferenceArtwork(int generation, HeroCandidate candidate, Drawable resource) {
        if (closed || generation != heroGeneration || !preferenceHeroPending
                || (heroItem != candidate && (heroItem != null || startupHero != candidate))
                || !startupAllowed(candidate)) return;
        boolean startup = heroItem == null;
        previousHeroTitle.setText(heroTitle.getText());
        previousHeroHint.setText(heroHint.getText());
        previousHeroTitle.setVisibility(View.VISIBLE);
        previousHeroHint.setVisibility(View.VISIBLE);
        previousHeroTitle.setAlpha(1f); previousHeroHint.setAlpha(1f);
        heroTitle.setText(candidate.creator.title);
        heroHint.setText(heroDescription(candidate.creator));
        heroTitle.setAlpha(0f); heroHint.setAlpha(0f);
        ImageView outgoing = heroImage;
        heroImage = preferenceImage;
        preferenceImage = outgoing;
        heroImage.setImageDrawable(resource);
        heroImage.setAlpha(0f);
        heroImage.bringToFront(); // Reorder only the stable image host, never detach a live Glide target.
        if (coldSnapshot != null) coldSnapshot.setHero(heroImage);
        preferenceHeroPending = false;
        heroArtworkReady = true;
        if (!startup) startupHero = null;
        heroAction.setEnabled(true); heroCard.setEnabled(true);
        heroAction.setVisibility(View.VISIBLE);
        heroCard.setContentDescription("Open " + candidate.creator.title + " gallery");
        preferenceLoading.setVisibility(View.INVISIBLE);
        updateHeroDots();
        if (!startup && discoveryPreference == ContentPreference.ALL && !candidate.usingAvatar()) {
            String image = candidate.currentImageUrl(), referer = candidate.currentReferer();
            metadataIo.execute(() -> OnlyFapHeroMemory.remember(getContext(), candidate.creator, image, referer));
        }
        ContentPreference.acknowledge(getContext(), "onlyfap");
        if (!active || !isAttachedToWindow() || !ZeroChillMotion.animationsEnabled(getContext())) {
            settleHeroMorph();
            return;
        }
        heroMorph = android.animation.ValueAnimator.ofFloat(0f, 1f);
        heroMorph.setDuration(LIQUID_RIFT_MS);
        heroMorph.setInterpolator(new android.view.animation.LinearInterpolator());
        heroMorph.addUpdateListener(animation -> applyLiquidRift((float) animation.getAnimatedValue()));
        heroMorph.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) { settleHeroMorph(); }
        });
        applyLiquidRift(0f);
        heroMorph.start();
    }

    private static float phase(float p, float start, float end) {
        float t = Math.max(0f, Math.min(1f, (p - start) / (end - start)));
        return t * t * (3f - 2f * t);
    }

    private void applyLiquidRift(float p) {
        riftProgress = p;
        float collapse = phase(p, .08f, .35f);
        float reveal = phase(p, .35f, .82f);
        preferenceImage.setAlpha(1f - collapse);
        preferenceImage.setScaleX(1f + .03f * collapse);
        preferenceImage.setScaleY(1f + .03f * collapse);
        ((LiquidRiftImageView) preferenceImage).rift(1f, collapse);
        heroImage.setAlpha(p < .35f ? 0f : 1f);
        heroImage.setScaleX(1.03f - .03f * reveal);
        heroImage.setScaleY(1.03f - .03f * reveal);
        ((LiquidRiftImageView) heroImage).rift(reveal, 1f - reveal);
        float title = phase(p, .72f, .92f), hint = phase(p, .79f, .98f);
        heroTitle.setAlpha(title); heroHint.setAlpha(hint);
        heroTitle.setTranslationY(dp(5) * (1f - title));
        heroHint.setTranslationY(dp(4) * (1f - hint));
        float oldCopy = 1f - phase(p, .02f, .25f);
        previousHeroTitle.setAlpha(oldCopy); previousHeroHint.setAlpha(oldCopy);
        // Controls stay in place and usable. Only the gallery ink settles last.
        heroAction.setAlpha(phase(p, .84f, 1f));
    }

    private void pulsePreferenceSelector() {
        if (selectorPulse != null) { selectorPulse.cancel(); selectorPulse = null; }
        preferenceSelector.setTextColor(UiPalette.PRIMARY);
        if (!active || !ZeroChillMotion.animationsEnabled(getContext())) return;
        selectorPulse = android.animation.ValueAnimator.ofFloat(0f, 1f);
        selectorPulse.setDuration(150L);
        selectorPulse.addUpdateListener(a -> {
            float pulse = (float) Math.sin(Math.PI * (float) a.getAnimatedValue());
            preferenceSelector.setTextColor((int) selectorColors.evaluate(
                    pulse * .7f, UiPalette.PRIMARY, 0xFF0892D0));
        });
        selectorPulse.start();
    }

    /** Cancel without ending the animator, which would flash a superseded incoming hero. */
    private void cancelHeroMorphForSelection() {
        if (heroMorph == null) return;
        heroMorph.removeAllListeners(); heroMorph.removeAllUpdateListeners(); heroMorph.cancel(); heroMorph = null;
        deferredHeroIndex = -1;
        if (riftProgress < .35f) {
            ImageView incoming = heroImage; heroImage = preferenceImage; preferenceImage = incoming;
            heroTitle.setText(previousHeroTitle.getText()); heroHint.setText(previousHeroHint.getText());
            heroImage.bringToFront();
            if (coldSnapshot != null) coldSnapshot.setHero(heroImage);
        }
        settleHeroMorph();
    }

    private void finishHeroMorph() {
        if (heroMorph != null) heroMorph.end();
        if (selectorPulse != null) { selectorPulse.cancel(); selectorPulse = null; }
        preferenceSelector.setTextColor(UiPalette.PRIMARY);
    }

    private void settleHeroMorph() {
        if (heroMorph != null) {
            heroMorph.removeAllUpdateListeners(); heroMorph.removeAllListeners(); heroMorph = null;
        }
        ((LiquidRiftImageView) heroImage).resetRift();
        ((LiquidRiftImageView) preferenceImage).resetRift();
        heroTitle.setTranslationY(0f); heroHint.setTranslationY(0f); heroAction.setAlpha(1f);
        heroImage.setAlpha(1f); heroTitle.setAlpha(1f); heroHint.setAlpha(1f);
        previousHeroTitle.setVisibility(View.GONE); previousHeroHint.setVisibility(View.GONE);
        preferenceImage.setAlpha(0f);
        Glide.with(preferenceImage).clear(preferenceImage);
        scheduleHeroRotation();
        if (deferredHeroIndex >= 0) {
            final int index = deferredHeroIndex, generation = heroGeneration;
            deferredHeroIndex = -1;
            post(() -> { if (!closed && active && generation == heroGeneration) showHero(index, false); });
        }
    }

    private void showPreferenceEmptyState() {
        if (!preferenceHeroPending) return;
        preferenceHeroPending = false;
        heroArtworkReady = false;
        Glide.with(heroImage).clear(heroImage);
        Glide.with(preferenceImage).clear(preferenceImage);
        heroImage.setImageDrawable(new ColorDrawable(Color.rgb(13, 16, 19)));
        preferenceImage.setAlpha(0f);
        heroTitle.setText("No featured creator");
        heroHint.setText("No usable portrait for " + discoveryPreference.label + ". Pull down to retry.");
        heroAction.setVisibility(View.GONE);
        heroCard.setEnabled(true);
        heroCard.setContentDescription("No featured creator for " + discoveryPreference.label);
        preferenceLoading.setVisibility(View.INVISIBLE);
        heroDots.setVisibility(View.GONE);
    }

    private void handleHeroArtworkFailure(
            HeroCandidate candidate,
            String failedUrl
    ) {
        if (closed || candidate == null || !heroItems.contains(candidate) ||
                !failedUrl.equals(candidate.currentImageUrl())) return;
        if (!clean(failedUrl).isEmpty()) rejectedHeroUrls.add(failedUrl);
        if (candidate.advance()) {
            if (candidate == heroItem) loadHeroArtwork(candidate);
            return;
        }
        rejectHero(candidate);
    }

    private void rejectHero(HeroCandidate candidate) {
        if (closed || candidate == null) return;
        int removed = heroItems.indexOf(candidate);
        if (removed >= 0) heroItems.remove(removed);
        if (heroItems.isEmpty()) {
            heroItem = null;
            if (preferenceHeroPending) {
                requestHeroCandidates();
                return;
            }
            heroIndex = -1;
            heroDots.setVisibility(View.GONE);
            heroAction.setVisibility(View.GONE);
            heroTitle.setText("Discover creators");
            heroHint.setText("Loading featured creator artwork…");
            if (startupAllowed(startupHero)) showStartupCopy(startupHero);
            requestHeroCandidates();
            return;
        }
        int next = Math.min(Math.max(0, removed), heroItems.size() - 1);
        showHero(next, false);
        requestHeroCandidates();
    }

    private String heroDescription(NativeContentItem creator) {
        String description = clean(creator.description);
        if (!description.isEmpty()) return description;
        return "Pictures + videos · Open creator gallery";
    }

    private void updateHeroDots() {
        if (preferenceHeroPending || heroItems.size() <= 1) {
            heroDots.setVisibility(View.INVISIBLE);
            return;
        }
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < heroItems.size(); i++) {
            if (i > 0) dots.append(' ');
            dots.append(i == heroIndex ? '●' : '○');
        }
        heroDots.setText(dots.toString());
        heroDots.setVisibility(View.VISIBLE);
    }

    private void refreshHeroSet() {
        if (!active || closed) return;
        // Retain a usable hero until a replacement is available. No blank refresh interval.
        if (heroResolveInFlight == 0 && !heroSelectionLoading && !heroDiscoveryLoading) {
            heroGeneration++;
            if (heroItem != null) {
                heroItems.clear();
                heroItems.add(heroItem);
                heroIndex = 0;
            } else heroItems.clear();
            heroDiscoveryItems.clear();
            heroDiscoveryNextPage = HERO_DISCOVERY_FIRST_PAGE;
            heroDiscoveryExhausted = false;
            requestedHeroCreators.clear();
            if (heroItem != null) requestedHeroCreators.add(CreatorFavoriteStore.key(heroItem.creator));
            rejectedHeroUrls.clear();
            requestHeroCandidates();
        }
        scheduleHeroSetRefresh();
    }

    private void scheduleHeroSetRefresh() {
        heroRefreshHandler.removeCallbacksAndMessages(null);
        if (!active || closed) return;
        heroRefreshHandler.postDelayed(
                this::refreshHeroSet,
                OnlyFapRefreshPolicy.REFRESH_AFTER_MS
        );
    }

    private void scheduleHeroRotation() {
        heroHandler.removeCallbacksAndMessages(null);
        if (!active || closed || preferenceHeroPending || heroMorph != null || heroItems.size() <= 1) return;
        heroHandler.postDelayed(
                () -> showHero(heroIndex + 1, true),
                HERO_ROTATION_MS
        );
    }

    private void openHero() {
        if (preferenceHeroPending) return;
        HeroCandidate selected = heroItem != null ? heroItem : startupHero;
        if (selected != null && selected.creator != null) listener.onOpenCreator(selected.creator);
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
        if (url.isEmpty()) {
            image.setImageResource(R.drawable.ic_more_account);
            return;
        }
        try {
            com.bumptech.glide.RequestBuilder<Drawable> request =
                    Glide.with(image)
                            .load(remoteImage(
                                    url,
                                    creator.uploader.isEmpty()
                                            ? creator.url
                                            : creator.uploader
                            ))
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .dontAnimate()
                            .transition(ThumbnailFades.avatar())
                            .placeholder(new ColorDrawable(
                                    ZeroChillUi.color(getContext(), R.color.zc_surface_pressed)))
                            .error(R.drawable.ic_more_account);
            if (avatarOverride == null) request.circleCrop();
            request.into(image);
        } catch (Exception ignored) {
            image.setImageResource(R.drawable.ic_more_account);
        }
    }

    private GlideUrl remoteImage(String imageUrl, String referer) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", USER_AGENT);
        if (!clean(referer).isEmpty()) headers.addHeader("Referer", referer);
        return new GlideUrl(imageUrl, headers.build());
    }

    private List<NativeContentItem> safe(List<NativeContentItem> items) {
        return items == null
                ? Collections.emptyList()
                : new ArrayList<>(items);
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private String initials(String value) {
        String clean = clean(value);
        if (clean.isEmpty()) return "?";
        String[] parts = clean.split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase(java.util.Locale.US);
        }
        return (parts[0].substring(0, 1) +
                parts[parts.length - 1].substring(0, 1))
                .toUpperCase(java.util.Locale.US);
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').trim();
    }

    private void saveRailState(Bundle out, String key, RecyclerView rail) {
        RecyclerView.LayoutManager manager = rail.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager)) return;
        LinearLayoutManager linear = (LinearLayoutManager) manager;
        int position = linear.findFirstVisibleItemPosition();
        if (position < 0) return;
        View child = linear.findViewByPosition(position);
        int offset = child == null ? 0 : child.getLeft() - rail.getPaddingLeft();
        out.putInt(key + "_position", position);
        out.putInt(key + "_offset", offset);
    }

    private void restoreRailState(Bundle state, String key, RecyclerView rail) {
        if (state == null || !state.containsKey(key + "_position")) return;
        RecyclerView.LayoutManager manager = rail.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager)) return;
        int position = state.getInt(key + "_position", 0);
        int offset = state.getInt(key + "_offset", 0);
        rail.post(() -> ((LinearLayoutManager) manager)
                .scrollToPositionWithOffset(position, offset));
    }

    private void applyPendingRestoreState() {
        Bundle state = pendingRestoreState;
        if (state == null) return;
        pendingRestoreState = null;

        int restoredHero = state.getInt("hero_index", -1);
        if (restoredHero >= 0 && restoredHero < heroItems.size()) {
            showHero(restoredHero, false);
        }

        restoreRailState(state, "trending", trendingShelf.rail);
        restoreRailState(state, "new", newShelf.rail);
        restoreRailState(state, "hot", hotShelf.rail);
        restoreRailState(state, "popular", popularShelf.rail);

        int y = Math.max(0, state.getInt("scroll_y", 0));
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class HeroCandidate {
        final NativeContentItem creator;
        final List<OnlyFapHeroPolicy.Artwork> artwork;
        int artworkIndex;

        HeroCandidate(NativeContentItem creator, List<OnlyFapHeroPolicy.Artwork> artwork) {
            this.creator = creator;
            this.artwork = artwork;
        }

        // Kept for the rotation regression fixture, which only needs one image.
        HeroCandidate(NativeContentItem creator, String imageUrl, String referer) {
            this(creator, Collections.singletonList(
                    new OnlyFapHeroPolicy.Artwork(imageUrl, referer, true)));
        }

        boolean advance() {
            if (artworkIndex + 1 >= artwork.size()) return false;
            artworkIndex++;
            return true;
        }

        boolean usingAvatar() {
            return artwork.get(artworkIndex).avatar;
        }

        String currentImageUrl() {
            return artwork.get(artworkIndex).url;
        }

        String currentReferer() {
            return artwork.get(artworkIndex).referer;
        }
    }

    private static final class CreatorShelf {
        final LinearLayout container;
        final RecyclerView rail;
        final CreatorPortraitAdapter adapter;

        CreatorShelf(
                LinearLayout container,
                RecyclerView rail,
                CreatorPortraitAdapter adapter
        ) {
            this.container = container;
            this.rail = rail;
            this.adapter = adapter;
        }
    }

    private static final class CreatorPortraitAdapter
            extends RecyclerView.Adapter<CreatorPortraitAdapter.Holder> {
        private final Listener listener;
        private final List<NativeContentItem> items = new ArrayList<>();
        private boolean closed;

        CreatorPortraitAdapter(Listener listener) {
            this.listener = listener;
            setHasStableIds(true);
        }

        void replace(List<NativeContentItem> next) {
            items.clear();
            if (next != null) items.addAll(next);
            notifyDataSetChanged();
        }

        void refreshFavorites() {
            if (!items.isEmpty()) notifyItemRangeChanged(0, items.size());
        }

        void close() {
            closed = true;
            items.clear();
        }

        @Override
        public long getItemId(int position) {
            NativeContentItem item = items.get(position);
            return CreatorFavoriteStore.key(item).hashCode();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            MaterialCardView card = new MaterialCardView(context);
            ZeroChillUi.styleMediaCard(card, R.dimen.zc_radius_medium);
            card.setRadius(BrowseUi.dp(context, 14));
            card.setCardElevation(0f);
            card.setStrokeWidth(0);
            card.setClickable(true);
            card.setFocusable(true);
            ZeroChillMotion.installPressFeedback(card);

            RecyclerView.LayoutParams cardParams = new RecyclerView.LayoutParams(
                    BrowseUi.dp(context, 128),
                    Math.round(BrowseUi.dp(context, 128) * 216f / 148f)
            );
            cardParams.setMargins(
                    0,
                    0,
                    BrowseUi.dp(context, 10),
                    BrowseUi.dp(context, 10)
            );
            card.setLayoutParams(cardParams);

            FrameLayout frame = new FrameLayout(context);
            card.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

            TextView initials = new TextView(context);
            initials.setGravity(Gravity.CENTER);
            initials.setTextColor(Color.argb(72, 255, 255, 255));
            initials.setTextSize(34f);
            initials.setTypeface(null, android.graphics.Typeface.BOLD);
            frame.addView(initials, new FrameLayout.LayoutParams(-1, -1));

            ImageView image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            frame.addView(image, new FrameLayout.LayoutParams(-1, -1));

            View shade = new View(context);
            shade.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{
                            Color.TRANSPARENT,
                            Color.argb(32, 0, 0, 0),
                            Color.argb(238, 0, 0, 0)
                    }
            ));
            FrameLayout.LayoutParams shadeParams =
                    new FrameLayout.LayoutParams(-1, BrowseUi.dp(context, 92), Gravity.BOTTOM);
            frame.addView(shade, shadeParams);

            TextView favorite = new TextView(context);
            favorite.setText("★");
            favorite.setTextColor(UiPalette.PRIMARY);
            favorite.setTextSize(17f);
            favorite.setGravity(Gravity.CENTER);
            favorite.setVisibility(View.GONE);
            favorite.setBackground(BrowseUi.rounded(
                    context,
                    Color.argb(185, 9, 12, 15),
                    16
            ));
            FrameLayout.LayoutParams favoriteParams =
                    new FrameLayout.LayoutParams(
                            BrowseUi.dp(context, 32),
                            BrowseUi.dp(context, 32),
                            Gravity.TOP | Gravity.END
                    );
            favoriteParams.setMargins(
                    0,
                    BrowseUi.dp(context, 9),
                    BrowseUi.dp(context, 9),
                    0
            );
            frame.addView(favorite, favoriteParams);

            LinearLayout copy = new LinearLayout(context);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setGravity(Gravity.BOTTOM);
            copy.setPadding(
                    BrowseUi.dp(context, 12),
                    BrowseUi.dp(context, 8),
                    BrowseUi.dp(context, 12),
                    BrowseUi.dp(context, 4)
            );
            FrameLayout.LayoutParams copyParams =
                    new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            frame.addView(copy, copyParams);

            TextView title = new TextView(context);
            title.setTextColor(Color.WHITE);
            title.setTextSize(14.5f);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(title);

            return new Holder(card, image, initials, title, favorite);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            NativeContentItem creator = items.get(position);
            Context context = holder.card.getContext();
            holder.title.setText(creator.title);
            holder.initials.setText(initialsStatic(creator.title));
            boolean favorite = CreatorFavoriteStore.contains(context, creator);
            holder.favorite.setVisibility(favorite ? View.VISIBLE : View.GONE);
            holder.card.setContentDescription("Open " + creator.title + " gallery");
            holder.card.setOnClickListener(v -> listener.onOpenCreator(creator));
            holder.card.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                boolean saved = CreatorFavoriteStore.toggle(context, creator);
                holder.favorite.setVisibility(saved ? View.VISIBLE : View.GONE);
                ZeroChillToast.makeText(
                        context,
                        saved
                                ? creator.title + " added to favorites."
                                : creator.title + " removed from favorites.",
                        ZeroChillToast.LENGTH_SHORT
                ).show();
                return true;
            });

            loadStaticImage(holder.image, creator);
        }

        @Override
        public void onViewRecycled(@NonNull Holder holder) {
            Glide.with(holder.image).clear(holder.image);
            holder.card.setOnClickListener(null);
            holder.card.setOnLongClickListener(null);
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private static void loadStaticImage(ImageView image, NativeContentItem creator) {
            String url = creator == null || creator.imageUrl == null
                    ? ""
                    : creator.imageUrl.trim();
            if (url.isEmpty()) {
                image.setImageResource(R.drawable.ic_more_account);
                return;
            }

            LazyHeaders.Builder headers = new LazyHeaders.Builder()
                    .addHeader("User-Agent", USER_AGENT);
            String referer = creator.uploader == null || creator.uploader.trim().isEmpty()
                    ? creator.url
                    : creator.uploader;
            if (referer != null && !referer.trim().isEmpty()) {
                headers.addHeader("Referer", referer);
            }

            try {
                Glide.with(image)
                        .load(new GlideUrl(url, headers.build()))
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .dontAnimate()
                        .transition(ThumbnailFades.thumbnail())
                        .placeholder(new ColorDrawable(
                                ZeroChillUi.color(
                                        image.getContext(),
                                        R.color.zc_surface_pressed)))
                        .error(R.drawable.ic_more_account)
                        .into(image);
            } catch (Exception ignored) {
                image.setImageResource(R.drawable.ic_more_account);
            }
        }

        private static String initialsStatic(String value) {
            String clean = value == null ? "" : value.trim();
            if (clean.isEmpty()) return "?";
            String[] parts = clean.split("\\s+");
            if (parts.length == 1) {
                return parts[0].substring(0, 1).toUpperCase(java.util.Locale.US);
            }
            return (parts[0].substring(0, 1) +
                    parts[parts.length - 1].substring(0, 1))
                    .toUpperCase(java.util.Locale.US);
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final ImageView image;
            final TextView initials;
            final TextView title;
            final TextView favorite;

            Holder(
                    MaterialCardView card,
                    ImageView image,
                    TextView initials,
                    TextView title,
                    TextView favorite
            ) {
                super(card);
                this.card = card;
                this.image = image;
                this.initials = initials;
                this.title = title;
                this.favorite = favorite;
            }
        }
    }
}
