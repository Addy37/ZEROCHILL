package com.webapp.crazyshit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.view.ViewAnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.signature.ObjectKey;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Nuvio-inspired proof-of-concept landing page for the Shows tab. */
final class ShowsHubView extends FrameLayout {
    interface Listener {
        void onOpen(NativeContentItem item);

        default void onOpen(NativeContentItem item, View sourceView) {
            onOpen(item);
        }
    }

    interface ContinueListener {
        void onResume(PlaybackHistoryStore.Item item);

        default void onResume(PlaybackHistoryStore.Item item, View sourceView) {
            onResume(item);
        }
    }

    interface PrewarmListener {
        void onPrewarm(NativeContentItem item);
    }

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";
    private static final long HERO_ROTATION_MS = 6_000L;
    private static final int HERO_MAX_ITEMS = 8;

    private final int shelfTitleHeight;
    private final Listener listener;
    private final Listener weeklyListener;
    private final ContinueListener continueListener;
    private final PrewarmListener prewarmListener;
    private final ShowsContinueFrameStore.Listener continueFrameListener =
            this::onContinueFrameUpdated;
    private final Handler heroHandler = new Handler(Looper.getMainLooper());
    private final int heroShuffleSeed = new java.util.Random().nextInt();
    private final Set<String> rejectedHeroImages = new HashSet<>();
    private final ScrollView scroll;
    private final LinearLayout content;
    private final LinearLayout body;
    private final MaterialCardView heroCard;
    private final ImageView heroBackdrop;
    private final ImageView heroImage;
    private final ImageView heroSearch;
    private final ImageView heroMore;
    private final ImageView heroPrevious;
    private final TextView heroSource;
    private final TextView heroTitle;
    private final TextView heroHint;
    private final TextView heroAction;
    private final LinearLayout heroDots;
    private final TextView loadingLabel;
    private final ContinueShelf continueShelf;
    private final Shelf thisWeekShelf;
    private final Shelf crazyShelf;
    private final Shelf efuktShelf;
    private final Shelf categoryShelf;
    private final Shelf kaoticCategoryShelf;
    private final Shelf baddieHubCategoryShelf;
    private final Shelf xMegaDriveCategoryShelf;

    private List<NativeContentItem> thisWeekItems = Collections.emptyList();
    private List<NativeContentItem> crazyItems = Collections.emptyList();
    private List<NativeContentItem> efuktItems = Collections.emptyList();
    private List<NativeContentItem> categoryItems = Collections.emptyList();
    private List<NativeContentItem> kaoticCategoryItems = Collections.emptyList();
    private List<NativeContentItem> baddieHubCategoryItems = Collections.emptyList();
    private List<NativeContentItem> xMegaDriveCategoryItems = Collections.emptyList();
    private List<NativeContentItem> heroItems = Collections.emptyList();
    private NativeContentItem heroItem;
    private int heroIndex = -1;
    private int heroArtworkRequest;
    private Animator heroRevealAnimator;
    private boolean active;
    private ShowsCategoryLoader categoryLoader;
    private Bundle pendingRestoreState;

    ShowsHubView(
            Context context,
            Listener listener,
            ContinueListener continueListener,
            PrewarmListener prewarmListener
    ) {
        this(context, listener, continueListener, prewarmListener, listener);
    }

    ShowsHubView(
            Context context,
            Listener listener,
            ContinueListener continueListener,
            PrewarmListener prewarmListener,
            Listener weeklyListener
    ) {
        super(context);
        TextView titleMeasure = text("", 12f, Color.WHITE);
        titleMeasure.setTypeface(null, android.graphics.Typeface.BOLD);
        shelfTitleHeight = Math.max(dp(38), titleMeasure.getLineHeight() * 2 + dp(6));
        this.listener = listener;
        this.weeklyListener = weeklyListener;
        this.continueListener = continueListener;
        this.prewarmListener = prewarmListener;
        setBackgroundColor(ZeroChillUi.background(context));

        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        // Shows owns its header inside the hero, so no shared-header underlap is needed.
        setClipChildren(false);
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
        heroCard.setContentDescription("Shows featured title");
        ZeroChillMotion.installPressFeedback(heroCard);

        FrameLayout heroFrame = new FrameLayout(context);
        heroCard.addView(heroFrame, new MaterialCardView.LayoutParams(-1, -1));

        heroBackdrop = new ImageView(context);
        heroBackdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroBackdrop.setBackground(new ColorDrawable(Color.rgb(13, 16, 19)));
        heroBackdrop.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        heroBackdrop.setScaleX(1.08f);
        heroBackdrop.setScaleY(1.08f);
        heroBackdrop.setAlpha(0.46f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            heroBackdrop.setRenderEffect(RenderEffect.createBlurEffect(
                    dp(18),
                    dp(18),
                    Shader.TileMode.CLAMP
            ));
        }
        heroFrame.addView(heroBackdrop, new FrameLayout.LayoutParams(-1, -1));

        View backdropDim = new View(context);
        backdropDim.setBackgroundColor(Color.argb(92, 0, 0, 0));
        backdropDim.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        heroFrame.addView(backdropDim, new FrameLayout.LayoutParams(-1, -1));

        MaterialCardView heroArtworkCard = new MaterialCardView(context);
        heroArtworkCard.setRadius(0f);
        heroArtworkCard.setCardElevation(0f);
        heroArtworkCard.setStrokeWidth(0);
        heroArtworkCard.setCardBackgroundColor(Color.rgb(13, 16, 19));
        heroArtworkCard.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        int heroArtworkHeight = Math.round(
                getResources().getDisplayMetrics().widthPixels * 9f / 16f
        );
        FrameLayout.LayoutParams heroArtworkParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                heroArtworkHeight,
                Gravity.TOP
        );
        heroArtworkParams.topMargin = 0;
        heroFrame.addView(heroArtworkCard, heroArtworkParams);

        heroPrevious = new ImageView(context);
        heroPrevious.setScaleType(ImageView.ScaleType.FIT_CENTER);
        heroPrevious.setVisibility(View.GONE);
        heroPrevious.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        heroArtworkCard.addView(heroPrevious, new MaterialCardView.LayoutParams(-1, -1));

        heroImage = new ImageView(context);
        heroImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        heroImage.setBackground(new ColorDrawable(Color.rgb(13, 16, 19)));
        heroImage.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        heroArtworkCard.addView(heroImage, new MaterialCardView.LayoutParams(-1, -1));

        int heroBackground = ZeroChillUi.background(context);
        View heroShade = new View(context);
        // Fade over the artwork itself, reaching the control-bar color at its edge.
        android.graphics.drawable.ShapeDrawable heroScrim =
                new android.graphics.drawable.ShapeDrawable(
                        new android.graphics.drawable.shapes.RectShape());
        heroScrim.setShaderFactory(new android.graphics.drawable.ShapeDrawable.ShaderFactory() {
            @Override public Shader resize(int width, int height) {
                return new android.graphics.LinearGradient(0, 0, 0, Math.max(1, height),
                        new int[] {
                                Color.argb(100, 0, 0, 0), Color.TRANSPARENT, Color.TRANSPARENT,
                                Color.argb(161, Color.red(heroBackground),
                                        Color.green(heroBackground), Color.blue(heroBackground)),
                                heroBackground
                        }, new float[] {0f, 0.30f, 0.53f, 0.77f, 1f}, Shader.TileMode.CLAMP);
            }
        });
        heroShade.setBackground(heroScrim);
        heroShade.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        heroFrame.addView(heroShade, new FrameLayout.LayoutParams(-1, heroArtworkHeight));

        LinearLayout heroBar = new LinearLayout(context);
        heroBar.setOrientation(LinearLayout.HORIZONTAL);
        heroBar.setGravity(Gravity.CENTER_VERTICAL);
        heroBar.setPadding(dp(20), dp(15), dp(20), dp(15));

        TextView heroBrand = text("Shows", 28f, Color.WHITE);
        ZeroChillTypography.apply(heroBrand, ZeroChillTypography.Role.DISPLAY);
        heroBrand.setTextSize(28f);
        heroBrand.setTypeface(null, android.graphics.Typeface.BOLD);
        heroBrand.setGravity(Gravity.CENTER_VERTICAL);
        heroBrand.setSingleLine(true);
        heroBrand.setShadowLayer(dp(8), 0f, dp(2), Color.argb(150, 0, 0, 0));
        heroBar.addView(heroBrand, new LinearLayout.LayoutParams(0, -1, 1f));
        heroSearch = heroHeaderAction(R.drawable.ic_nav_search, "Search Shows");
        heroBar.addView(heroSearch, new LinearLayout.LayoutParams(dp(48), dp(48)));
        heroMore = heroHeaderAction(R.drawable.ic_more_overflow, "More Shows options");
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        moreParams.setMarginStart(dp(4));
        heroBar.addView(heroMore, moreParams);

        heroFrame.addView(heroBar, new FrameLayout.LayoutParams(-1, dp(78), Gravity.TOP));

        heroDots = new LinearLayout(context);
        heroDots.setVisibility(View.GONE);
        heroDots.setContentDescription("Featured show position");

        LinearLayout heroCopy = new LinearLayout(context);
        heroCopy.setOrientation(LinearLayout.VERTICAL);
        heroCopy.setGravity(Gravity.BOTTOM | Gravity.START);
        heroCopy.setPadding(dp(20), 0, dp(20), dp(5));
        FrameLayout.LayoutParams heroCopyParams =
                new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        heroCopyParams.bottomMargin = dp(60);
        heroFrame.addView(heroCopy, heroCopyParams);

        heroSource = text("FEATURED", 10f, themeAccent());
        heroSource.setTypeface(null, android.graphics.Typeface.BOLD);
        heroSource.setLetterSpacing(0.2f);
        heroCopy.addView(heroSource);

        heroTitle = text("Featured Shows", 24f, Color.WHITE);
        heroTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        heroTitle.setGravity(Gravity.START);
        heroTitle.setTextAlignment(TEXT_ALIGNMENT_VIEW_START);
        heroTitle.setShadowLayer(dp(8), 0f, dp(2), Color.argb(160, 0, 0, 0));
        heroTitle.setMaxLines(2);
        heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams heroTitleParams = new LinearLayout.LayoutParams(-1, -2);
        heroTitleParams.topMargin = dp(4);
        heroCopy.addView(heroTitle, heroTitleParams);

        heroHint = text(
                "CrazyShit, EFukt and Kaotic together in one media hub.",
                13f,
                ZeroChillUi.color(context, R.color.zc_text_secondary)
        );
        heroHint.setGravity(Gravity.START);
        heroHint.setTextAlignment(TEXT_ALIGNMENT_VIEW_START);
        heroHint.setVisibility(View.GONE);
        heroHint.setMaxLines(2);
        heroHint.setLineSpacing(0f, 1.05f);
        LinearLayout.LayoutParams heroHintParams = new LinearLayout.LayoutParams(-1, -2);
        heroHintParams.topMargin = dp(5);
        heroCopy.addView(heroHint, heroHintParams);

        heroAction = text("VIEW SHOW", 12f, Color.WHITE);
        heroAction.setTypeface(null, android.graphics.Typeface.BOLD);
        heroAction.setGravity(Gravity.CENTER);
        heroAction.setSingleLine(true);
        heroAction.setMinWidth(dp(128));
        heroAction.setVisibility(View.GONE);
        GradientDrawable actionBackground = new GradientDrawable();
        actionBackground.setColor(androidx.core.graphics.ColorUtils.compositeColors(
                androidx.core.graphics.ColorUtils.setAlphaComponent(themeAccent(), 33),
                Color.argb(219, 28, 40, 51)));
        actionBackground.setCornerRadius(dp(17));
        actionBackground.setStroke(dp(1), Color.argb(31, 255, 255, 255));
        heroAction.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(75, 255, 255, 255)),
                actionBackground, null));
        android.graphics.drawable.Drawable play =
                androidx.appcompat.content.res.AppCompatResources.getDrawable(
                        context, R.drawable.ic_player_play).mutate();
        play.setTint(Color.WHITE);
        play.setBounds(0, 0, dp(19), dp(19));
        heroAction.setCompoundDrawablesRelative(play, null, null, null);
        heroAction.setCompoundDrawablePadding(dp(7));
        heroAction.setPadding(dp(18), 0, dp(18), 0);
        heroAction.setIncludeFontPadding(false);
        heroAction.setContentDescription("Open featured show");
        LinearLayout controls = new LinearLayout(context);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(16), dp(8), dp(16), dp(8));
        controls.setBackgroundColor(heroBackground);
        heroFrame.addView(controls, new FrameLayout.LayoutParams(-1, dp(60), Gravity.BOTTOM));
        controls.addView(heroAction, new LinearLayout.LayoutParams(-2, dp(44)));
        LinearLayout.LayoutParams heroDotsParams =
                new LinearLayout.LayoutParams(0, dp(30), 1f);
        heroDots.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        controls.addView(heroDots, heroDotsParams);

        HorizontalSwipeFrameLayout heroSwipe = new HorizontalSwipeFrameLayout(context);
        heroSwipe.setListener(new HorizontalSwipeFrameLayout.Listener() {
            @Override public void onSwipeLeft() {
                if (heroItems.size() > 1) {
                    showHero(heroIndex + 1, true);
                    scheduleHeroRotation();
                }
            }

            @Override public void onSwipeRight() {
                if (heroItems.size() > 1) {
                    showHero(heroIndex - 1, true);
                    scheduleHeroRotation();
                }
            }
        });
        heroSwipe.addView(heroCard, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
                -1, heroArtworkHeight + dp(60));
        content.addView(heroSwipe, heroParams);

        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(12), dp(16), 0);
        content.addView(body, new LinearLayout.LayoutParams(-1, -2));

        loadingLabel = text(
                "Loading Shows…",
                12f,
                ZeroChillUi.color(context, R.color.zc_text_muted)
        );
        loadingLabel.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(-1, -2);
        loadingParams.setMargins(0, 0, 0, dp(10));
        body.addView(loadingLabel, loadingParams);

        continueShelf = addContinueShelf();
        thisWeekShelf = addShelf(
                "This Week",
                "Videos from the source homepages",
                false,
                weeklyListener,
                true,
                false
        );
        thisWeekShelf.rail.setId(R.id.shows_weekly_rail);
        crazyShelf = addShelf("CrazyShit Shows", "Series and recurring collections", false);
        efuktShelf = addShelf("EFukt Series", "Browse EFukt by series", false);
        categoryShelf = addShelf("CrazyShit Categories", "Jump into a type of content", false);
        kaoticCategoryShelf = addShelf("Kaotic Categories", "Browse Kaotic by category", false);
        baddieHubCategoryShelf = addShelf(
                "BaddieHub Categories",
                "Browse BaddieHub by category",
                true
        );
        xMegaDriveCategoryShelf = addShelf(
                "XMegaDrive",
                "Browse XMegaDrive by category",
                true
        );

        heroCard.setOnClickListener(v -> openHero());
        heroAction.setOnClickListener(v -> openHero());
        refreshContinueWatching();
        ColdTabSnapshot.install(this, scroll, heroImage, "shows",
                () -> heroItem != null && itemCount() > 0);
    }

    void clear() {
        thisWeekItems = Collections.emptyList();
        crazyItems = Collections.emptyList();
        efuktItems = Collections.emptyList();
        categoryItems = Collections.emptyList();
        kaoticCategoryItems = Collections.emptyList();
        baddieHubCategoryItems = Collections.emptyList();
        xMegaDriveCategoryItems = Collections.emptyList();
        heroHandler.removeCallbacksAndMessages(null);
        heroCard.animate().cancel();
        heroImage.animate().cancel();
        heroTitle.animate().cancel();
        heroArtworkRequest++;
        cancelHeroReveal();
        heroPrevious.setVisibility(View.GONE);
        heroPrevious.setImageDrawable(null);
        heroCard.setAlpha(1f);
        heroImage.setAlpha(1f);
        heroImage.setVisibility(View.VISIBLE);
        heroTitle.setAlpha(1f);
        heroItems = Collections.emptyList();
        heroItem = null;
        heroIndex = -1;
        heroDots.setVisibility(View.GONE);
        thisWeekShelf.adapter.replace(Collections.emptyList());
        crazyShelf.adapter.replace(Collections.emptyList());
        efuktShelf.adapter.replace(Collections.emptyList());
        categoryShelf.adapter.replace(Collections.emptyList());
        kaoticCategoryShelf.adapter.replace(Collections.emptyList());
        baddieHubCategoryShelf.adapter.replace(Collections.emptyList());
        xMegaDriveCategoryShelf.adapter.replace(Collections.emptyList());
        thisWeekShelf.container.setVisibility(View.GONE);
        crazyShelf.container.setVisibility(View.GONE);
        efuktShelf.container.setVisibility(View.GONE);
        categoryShelf.container.setVisibility(View.GONE);
        kaoticCategoryShelf.container.setVisibility(View.GONE);
        baddieHubCategoryShelf.container.setVisibility(View.GONE);
        xMegaDriveCategoryShelf.container.setVisibility(View.GONE);
        loadingLabel.setText("Loading Shows…");
        loadingLabel.setVisibility(View.VISIBLE);
        heroSource.setText("FEATURED");
        heroTitle.setText("Featured Shows");
        heroHint.setText("CrazyShit, EFukt and Kaotic together in one media hub.");
        heroAction.setVisibility(View.GONE);
        Glide.with(heroBackdrop).clear(heroBackdrop);
        Glide.with(heroImage).clear(heroImage);
        heroImage.setTag(R.id.shows_watch_source_page, null);
        heroBackdrop.setImageDrawable(new ColorDrawable(Color.rgb(13, 16, 19)));
        heroImage.setImageDrawable(new ColorDrawable(Color.rgb(13, 16, 19)));
        if (categoryLoader != null) categoryLoader.invalidate();
    }

    /** Reuse loaded shelf metadata when opening the watch page. */
    List<NativeContentItem> watchItems() {
        return new ArrayList<>(thisWeekItems);
    }

    void setThisWeek(List<NativeContentItem> items) {
        thisWeekItems = safe(items);
        thisWeekShelf.adapter.replace(thisWeekItems);
        thisWeekShelf.container.setVisibility(
                thisWeekItems.isEmpty() ? View.GONE : View.VISIBLE
        );
        if (!thisWeekItems.isEmpty()) loadingLabel.setVisibility(View.GONE);
    }

    void setCrazyShit(List<NativeContentItem> items) {
        crazyItems = safe(items);
        crazyShelf.adapter.replace(crazyItems);
        crazyShelf.container.setVisibility(crazyItems.isEmpty() ? View.GONE : View.VISIBLE);
        rebuildHeroCandidates();
    }

    void setEfukt(List<NativeContentItem> items) {
        efuktItems = safe(items);
        efuktShelf.adapter.replace(efuktItems);
        efuktShelf.container.setVisibility(efuktItems.isEmpty() ? View.GONE : View.VISIBLE);
        rebuildHeroCandidates();
    }

    void setCategories(List<NativeContentItem> items) {
        categoryItems = safe(items);
        categoryShelf.adapter.replace(categoryItems);
        categoryShelf.container.setVisibility(categoryItems.isEmpty() ? View.GONE : View.VISIBLE);
        rebuildHeroCandidates();
    }

    void setKaoticCategories(List<NativeContentItem> items) {
        kaoticCategoryItems = safe(items);
        kaoticCategoryShelf.adapter.replace(kaoticCategoryItems);
        kaoticCategoryShelf.container.setVisibility(
                kaoticCategoryItems.isEmpty() ? View.GONE : View.VISIBLE
        );
        rebuildHeroCandidates();
    }

    void setBaddieHubCategories(List<NativeContentItem> items) {
        baddieHubCategoryItems = safe(items);
        baddieHubCategoryShelf.adapter.replace(baddieHubCategoryItems);
        baddieHubCategoryShelf.container.setVisibility(
                baddieHubCategoryItems.isEmpty() ? View.GONE : View.VISIBLE
        );
        rebuildHeroCandidates();
    }

    void setXMegaDriveCategories(List<NativeContentItem> items) {
        xMegaDriveCategoryItems = new ArrayList<>();
        if (items != null) {
            for (NativeContentItem item : items) {
                if (item != null && item.imageUrl != null && !item.imageUrl.trim().isEmpty()) {
                    xMegaDriveCategoryItems.add(item);
                }
            }
        }
        xMegaDriveCategoryShelf.adapter.replace(xMegaDriveCategoryItems);
        xMegaDriveCategoryShelf.container.setVisibility(
                xMegaDriveCategoryItems.isEmpty() ? View.GONE : View.VISIBLE
        );
        if (!xMegaDriveCategoryItems.isEmpty()) loadingLabel.setVisibility(View.GONE);
        rebuildHeroCandidates();
    }

    void finishLoading() {
        loadingLabel.setVisibility(itemCount() == 0 ? View.VISIBLE : View.GONE);
        if (itemCount() == 0) {
            loadingLabel.setText("Shows could not load right now.");
        }
        applyPendingRestoreState();
    }

    void saveState(Bundle out) {
        if (out == null) return;
        out.putInt("scroll_y", scroll.getScrollY());
        out.putInt("hero_index", heroIndex);
        saveRailState(out, "continue", continueShelf.rail);
        saveRailState(out, "this_week", thisWeekShelf.rail);
        saveRailState(out, "crazy", crazyShelf.rail);
        saveRailState(out, "efukt", efuktShelf.rail);
        saveRailState(out, "categories", categoryShelf.rail);
        saveRailState(out, "kaotic_categories", kaoticCategoryShelf.rail);
        saveRailState(out, "baddiehub_categories", baddieHubCategoryShelf.rail);
        saveRailState(out, "xmegadrive_categories", xMegaDriveCategoryShelf.rail);
    }

    void restoreState(Bundle state) {
        pendingRestoreState = state == null ? null : new Bundle(state);
    }

    int itemCount() {
        return thisWeekItems.size() + crazyItems.size() + efuktItems.size()
                + categoryItems.size() + kaoticCategoryItems.size()
                + baddieHubCategoryItems.size()
                + xMegaDriveCategoryItems.size();
    }

    void setNavigationScrollListener(View.OnScrollChangeListener listener) {
        scroll.setOnScrollChangeListener(listener);
    }

    void refreshContinueWatching() {
        // A newly visible Continue shelf pushes every lower card down. Hold the
        // existing visible shelf at the same screen position on the first draw.
        final boolean preserveAnchor = scroll.isLaidOut() && thisWeekShelf.container.isLaidOut() &&
                thisWeekShelf.container.getVisibility() == View.VISIBLE;
        int[] before = new int[2];
        int[] viewport = new int[2];
        if (preserveAnchor) {
            thisWeekShelf.container.getLocationOnScreen(before);
            scroll.getLocationOnScreen(viewport);
        }
        final boolean anchorVisible = preserveAnchor && before[1] < viewport[1] + scroll.getHeight();
        List<PlaybackHistoryStore.Item> items =
                PlaybackHistoryStore.continueWatchingShows(getContext());
        continueShelf.adapter.replace(items);
        continueShelf.container.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        if (anchorVisible) {
            final int top = before[1];
            scroll.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    scroll.getViewTreeObserver().removeOnPreDrawListener(this);
                    if (!scroll.isAttachedToWindow()) return true;
                    int[] after = new int[2];
                    thisWeekShelf.container.getLocationOnScreen(after);
                    int delta = after[1] - top;
                    if (delta == 0) return true;
                    scroll.scrollBy(0, delta);
                    return false;
                }
            });
        }
    }

    void setCategoryLoader(ShowsCategoryLoader loader) {
        categoryLoader = loader;
    }

    void closeCategoryLoader() {
        if (categoryLoader != null) categoryLoader.close();
    }

    void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        if (categoryLoader != null) android.util.Log.i("XMegaDrive",
                "showsCategories active=" + active + " attached=" + isAttachedToWindow());
        if (categoryLoader != null) categoryLoader.setActive(active && isAttachedToWindow());
        if (!active) {
            heroHandler.removeCallbacksAndMessages(null);
            heroCard.animate().cancel();
            heroCard.setAlpha(1f);
            return;
        }
        prewarmNextHero();
        scheduleHeroRotation();
    }

    private List<List<NativeContentItem>> heroProviderPools() {
        ArrayList<NativeContentItem> crazy = new ArrayList<>(crazyItems);
        crazy.addAll(categoryItems);
        // V2's round-robin provider order. All collections are already loaded by Shows.
        return Arrays.asList(crazy, kaoticCategoryItems, efuktItems,
                xMegaDriveCategoryItems, baddieHubCategoryItems);
    }

    private void rebuildHeroCandidates() {
        List<NativeContentItem> next = ShowsHeroPicks.mixed(
                heroProviderPools(), rejectedHeroImages, heroShuffleSeed, HERO_MAX_ITEMS);

        if (next.isEmpty()) {
            // When every CDN rejects its images, keep a real locally bundled
            // collection cover instead of repeatedly rotating through blank slides.
            ArrayList<NativeContentItem> bundled = new ArrayList<>();
            for (List<NativeContentItem> provider : heroProviderPools()) {
                for (NativeContentItem item : provider) {
                    if (item == null || item.title.trim().isEmpty()
                            || !EmbeddedBrowseArtwork.has(getContext(), item.url)) continue;
                    bundled.add(item);
                    break;
                }
                if (bundled.size() >= HERO_MAX_ITEMS) break;
            }
            next = bundled;
        }

        String currentUrl = heroItem == null ? "" : heroItem.url;
        heroItems = next;
        heroIndex = indexOfUrl(heroItems, currentUrl);
        if (heroItems.isEmpty()) {
            heroArtworkRequest++;
            cancelHeroReveal();
            heroItem = null;
            heroIndex = -1;
            heroDots.setVisibility(View.GONE);
            heroSource.setText("FEATURED");
            heroTitle.setText("Featured Shows");
            heroAction.setVisibility(View.GONE);
            heroImage.setTag(R.id.shows_watch_source_page, null);
            Glide.with(heroImage).clear(heroImage);
            Glide.with(heroBackdrop).clear(heroBackdrop);
            heroImage.setImageDrawable(artworkPlaceholder());
            heroBackdrop.setImageDrawable(artworkPlaceholder());
        } else if (heroIndex < 0 || heroItem == null
                || !heroItems.get(heroIndex).imageUrl.equals(heroItem.imageUrl)) {
            showHero(Math.max(0, heroIndex), false);
        } else {
            updateHeroDots();
            prewarmNextHero();
        }
        if (itemCount() > 0) loadingLabel.setVisibility(View.GONE);
        scheduleHeroRotation();
    }

    private int indexOfUrl(List<NativeContentItem> items, String url) {
        if (url == null || url.isEmpty() || items == null) return -1;
        for (int index = 0; index < items.size(); index++) {
            NativeContentItem item = items.get(index);
            if (item != null && url.equals(item.url)) return index;
        }
        return -1;
    }

    private void showHero(int requestedIndex, boolean animate) {
        if (heroItems.isEmpty()) return;
        int index = Math.floorMod(requestedIndex, heroItems.size());
        NativeContentItem next = heroItems.get(index);
        boolean reveal = animate && ZeroChillMotion.animationsEnabled(getContext())
                && heroItem != null;
        heroTitle.animate().cancel();
        heroIndex = index;
        heroItem = next;
        heroSource.setText("FEATURED");
        heroSource.setTextColor(themeAccent());
        heroTitle.setText(next.title);
        heroAction.setVisibility(View.VISIBLE);
        heroImage.setTag(R.id.shows_watch_source_page, next.url);
        loadArtwork(heroBackdrop, next, true);
        loadHeroArtwork(next, reveal);
        updateHeroDots();
        loadingLabel.setVisibility(View.GONE);
        prewarmNextHero();
        if (reveal) {
            heroTitle.setAlpha(0.3f);
            heroTitle.animate().alpha(1f).setDuration(420L).start();
        } else {
            heroTitle.setAlpha(1f);
        }
    }

    private void loadHeroArtwork(NativeContentItem item, boolean reveal) {
        final int request = ++heroArtworkRequest;
        cancelHeroReveal();
        boolean hasPrevious = reveal && snapshotHeroArtwork();
        heroImage.setVisibility(hasPrevious ? View.INVISIBLE : View.VISIBLE);

        byte[] embedded = EmbeddedBrowseArtwork.get(getContext(), item.url);
        if (embedded == null || embedded.length < 512) embedded = null;
        boolean remote = ShowsHeroPicks.hasRemoteArtwork(item)
                && !rejectedHeroImages.contains(ShowsHeroPicks.imageKey(item));
        Object model = embedded;
        if (remote) {
            String imageUrl = item.imageUrl.trim();
            LazyHeaders.Builder headers = new LazyHeaders.Builder()
                    .addHeader("User-Agent", artworkAgent(imageUrl, item.url));
            if (item.url != null && item.url.startsWith("http")) {
                headers.addHeader("Referer", item.url);
            }
            addXMegaDriveArtworkHeaders(headers, imageUrl, item.url);
            model = new GlideUrl(imageUrl, headers.build());
        }

        if (model == null) {
            Glide.with(heroImage).clear(heroImage);
            heroImage.setImageDrawable(artworkPlaceholder());
            finishHeroReveal(request, false);
            return;
        }

        Glide.with(heroImage).load(model)
                .diskCacheStrategy(remote ? DiskCacheStrategy.ALL : DiskCacheStrategy.NONE)
                .disallowHardwareConfig()
                // Inspect decoded image pixels, not a thumbnail already shrunk
                // by fitting a tall poster into the wide hero slot.
                .override(1080, 1080)
                .dontTransform()
                .placeholder(artworkPlaceholder())
                .error(artworkPlaceholder())
                .listener(heroArtworkListener(request, item, remote, hasPrevious))
                .into(heroImage);
    }

    private RequestListener<android.graphics.drawable.Drawable> heroArtworkListener(
            int request, NativeContentItem item, boolean remote, boolean hasPrevious
    ) {
        return new RequestListener<android.graphics.drawable.Drawable>() {
            @Override public boolean onLoadFailed(
                    GlideException error, Object source,
                    Target<android.graphics.drawable.Drawable> target,
                    boolean firstResource
            ) {
                heroImage.post(() -> {
                    if (remote) rejectHeroArtwork(request, item);
                    else finishHeroReveal(request, false);
                });
                return false;
            }

            @Override public boolean onResourceReady(
                    android.graphics.drawable.Drawable resource, Object source,
                    Target<android.graphics.drawable.Drawable> target,
                    DataSource dataSource, boolean firstResource
            ) {
                if (remote && !ShowsHeroPicks.usableArtworkSize(
                        resource.getIntrinsicWidth(), resource.getIntrinsicHeight())) {
                    heroImage.post(() -> rejectHeroArtwork(request, item));
                    // Do not briefly display a rejected low-resolution poster.
                    return true;
                }
                heroImage.post(() -> finishHeroReveal(request, hasPrevious));
                return false;
            }
        };
    }

    private void rejectHeroArtwork(int request, NativeContentItem item) {
        if (request != heroArtworkRequest || heroItem == null
                || !heroItem.url.equals(item.url)) return;
        String image = ShowsHeroPicks.imageKey(item);
        if (!image.isEmpty() && rejectedHeroImages.add(image)) {
            rebuildHeroCandidates();
        } else {
            finishHeroReveal(request, false);
        }
    }

    private boolean snapshotHeroArtwork() {
        ImageView source = heroPrevious.getVisibility() == View.VISIBLE
                ? heroPrevious : heroImage;
        if (source.getDrawable() == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            heroPrevious.setVisibility(View.GONE);
            return false;
        }
        int width = source.getWidth();
        int height = source.getHeight();
        float scale = Math.min(1f, 1024f / Math.max(width, height));
        try {
            Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(width * scale)),
                    Math.max(1, Math.round(height * scale)), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(scale, scale);
            source.draw(canvas);
            heroPrevious.setImageBitmap(bitmap);
            heroPrevious.setVisibility(View.VISIBLE);
            return true;
        } catch (RuntimeException | OutOfMemoryError error) {
            heroPrevious.setImageDrawable(null);
            heroPrevious.setVisibility(View.GONE);
            return false;
        }
    }

    private void finishHeroReveal(int request, boolean reveal) {
        if (request != heroArtworkRequest) return;
        heroImage.setVisibility(View.VISIBLE);
        if (!reveal || heroImage.getWidth() <= 0 || heroImage.getHeight() <= 0) {
            heroPrevious.setVisibility(View.GONE);
            heroPrevious.setImageDrawable(null);
            return;
        }
        int centerX = heroImage.getWidth() / 2;
        int centerY = heroImage.getHeight() / 2;
        float radius = (float) Math.hypot(centerX, centerY);
        Animator ripple = ViewAnimationUtils.createCircularReveal(
                heroImage, centerX, centerY, 0f, radius);
        heroRevealAnimator = ripple;
        ripple.setDuration(420L);
        ripple.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (heroRevealAnimator == animation) heroRevealAnimator = null;
                if (request == heroArtworkRequest) {
                    heroPrevious.setVisibility(View.GONE);
                    heroPrevious.setImageDrawable(null);
                }
            }
        });
        ripple.start();
    }

    private void cancelHeroReveal() {
        if (heroRevealAnimator != null) {
            Animator running = heroRevealAnimator;
            heroRevealAnimator = null;
            running.cancel();
        }
    }

    private void updateHeroDots() {
        if (heroItems.size() <= 1 || heroIndex < 0) {
            heroDots.setVisibility(View.GONE);
            return;
        }
        heroDots.removeAllViews();
        for (int index = 0; index < heroItems.size(); index++) {
            final int selectedIndex = index;
            FrameLayout target = new FrameLayout(getContext());
            LinearLayout.LayoutParams targetParams =
                    new LinearLayout.LayoutParams(dp(20), dp(30));
            heroDots.addView(target, targetParams);
            target.setContentDescription("Show featured item " + (index + 1));
            target.setOnClickListener(v -> {
                showHero(selectedIndex, true);
                scheduleHeroRotation();
            });
            View mark = new View(getContext());
            boolean selected = index == heroIndex;
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(selected ? themeAccent() : Color.argb(180, 255, 255, 255));
            pill.setCornerRadius(dp(3));
            mark.setBackground(pill);
            target.addView(mark, new FrameLayout.LayoutParams(
                    dp(selected ? 16 : 6), dp(6), Gravity.CENTER));
        }
        heroDots.setContentDescription(
                "Featured show " + (heroIndex + 1) + " of " + heroItems.size()
        );
        heroDots.setVisibility(View.VISIBLE);
    }

    private void scheduleHeroRotation() {
        heroHandler.removeCallbacksAndMessages(null);
        if (!active || heroItems.size() <= 1 || !isAttachedToWindow()
                || !ZeroChillMotion.animationsEnabled(getContext())) return;
        heroHandler.postDelayed(this::rotateHero, HERO_ROTATION_MS);
    }

    private void rotateHero() {
        if (!active || !isAttachedToWindow() || getWindowVisibility() != View.VISIBLE
                || heroItems.size() <= 1) {
            scheduleHeroRotation();
            return;
        }
        int next = (Math.max(0, heroIndex) + 1) % heroItems.size();
        showHero(next, true);
        scheduleHeroRotation();
    }

    private void prewarmNextHero() {
        if (!active || heroItems.size() <= 1 || heroIndex < 0) return;
        preloadArtwork(heroItems.get((heroIndex + 1) % heroItems.size()));
    }

    private void preloadArtwork(NativeContentItem item) {
        if (item == null) return;
        String imageUrl = item.imageUrl == null ? "" : item.imageUrl.trim();
        if (imageUrl.isEmpty()) return;

        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", artworkAgent(imageUrl, item.url));
        if (item.url != null && item.url.startsWith("http")) {
            headers.addHeader("Referer", item.url);
        }
        addXMegaDriveArtworkHeaders(headers, imageUrl, item.url);
        Glide.with(this)
                .load(new GlideUrl(imageUrl, headers.build()))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .disallowHardwareConfig()
                .preload(dp(360), dp(220));
    }

    private void saveRailState(Bundle out, String key, RecyclerView rail) {
        if (out == null || rail == null) return;
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
        if (state == null || rail == null || !state.containsKey(key + "_position")) return;
        RecyclerView.LayoutManager manager = rail.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager)) return;
        int position = state.getInt(key + "_position", 0);
        int offset = state.getInt(key + "_offset", 0);
        LinearLayoutManager linear = (LinearLayoutManager) manager;
        rail.post(() -> linear.scrollToPositionWithOffset(position, offset));
    }

    private void applyPendingRestoreState() {
        Bundle state = pendingRestoreState;
        if (state == null) return;
        pendingRestoreState = null;

        int restoredHero = state.getInt("hero_index", -1);
        if (restoredHero >= 0 && restoredHero < heroItems.size()) {
            showHero(restoredHero, false);
        }

        restoreRailState(state, "continue", continueShelf.rail);
        restoreRailState(state, "this_week", thisWeekShelf.rail);
        restoreRailState(state, "crazy", crazyShelf.rail);
        restoreRailState(state, "efukt", efuktShelf.rail);
        restoreRailState(state, "categories", categoryShelf.rail);
        restoreRailState(state, "kaotic_categories", kaoticCategoryShelf.rail);
        restoreRailState(state, "baddiehub_categories", baddieHubCategoryShelf.rail);
        restoreRailState(state, "xmegadrive_categories", xMegaDriveCategoryShelf.rail);

        int y = Math.max(0, state.getInt("scroll_y", 0));
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (categoryLoader != null) categoryLoader.setActive(active);
        ShowsContinueFrameStore.addListener(continueFrameListener);
        refreshContinueWatching();
        scheduleHeroRotation();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (categoryLoader != null) categoryLoader.setActive(false);
        ShowsContinueFrameStore.removeListener(continueFrameListener);
        heroHandler.removeCallbacksAndMessages(null);
        heroCard.animate().cancel();
        heroArtworkRequest++;
        cancelHeroReveal();
        heroImage.setVisibility(View.VISIBLE);
        heroPrevious.setVisibility(View.GONE);
        heroPrevious.setImageDrawable(null);
        super.onDetachedFromWindow();
    }

    private void onContinueFrameUpdated(String pageUrl) {
        if (!isAttachedToWindow()) return;
        continueShelf.rail.post(() -> {
            if (isAttachedToWindow()) continueShelf.adapter.refreshArtwork(pageUrl);
        });
    }

    private void openHero() {
        if (heroItem != null && listener != null) listener.onOpen(heroItem);
    }

    void setHeaderActions(View.OnClickListener onSearch, View.OnClickListener onMore) {
        heroSearch.setOnClickListener(onSearch);
        heroMore.setOnClickListener(onMore);
    }

    private ImageView heroHeaderAction(int icon, String description) {
        ImageView action = new ImageView(getContext());
        action.setImageResource(icon);
        action.setColorFilter(Color.WHITE);
        action.setContentDescription(description);
        action.setClickable(true);
        action.setFocusable(true);
        GradientDrawable film = new GradientDrawable();
        film.setShape(GradientDrawable.OVAL);
        film.setColor(AppearanceTheme.glassPreset(getContext()) == AppearanceTheme.GlassPreset.OFF
                ? Color.rgb(18, 22, 27) : Color.argb(90, 0, 0, 0));
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        int accent = themeAccent();
        action.setBackground(new android.graphics.drawable.InsetDrawable(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(65, Color.red(accent), Color.green(accent),
                        Color.blue(accent))), film, mask), dp(3)));
        // Android background setters can overwrite ImageView padding.
        action.setPadding(dp(15), dp(15), dp(15), dp(15));
        ZeroChillMotion.installPressFeedback(action);
        return action;
    }

    private ContinueShelf addContinueShelf() {
        LinearLayout block = new LinearLayout(getContext());
        block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(-1, -2);
        blockParams.setMargins(0, 0, 0, dp(20));
        body.addView(block, blockParams);

        LinearLayout heading = new LinearLayout(getContext());
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(0, 0, 0, dp(12));

        TextView titleView = text("Continue Watching", 19f, AppearanceTheme.primaryText(getContext()));
        ZeroChillTypography.apply(titleView, ZeroChillTypography.Role.SECTION);
        titleView.setTextSize(19f);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setBackground(null);
        titleView.setPadding(0, 0, 0, 0);
        heading.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        TextView subtitle = text("Pick up where you stopped", 11f,
                AppearanceTheme.secondaryText(getContext()));
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(2);
        heading.addView(subtitle, subtitleParams);
        block.addView(heading);

        RecyclerView rail = new RecyclerView(getContext());
        rail.setClipToPadding(false);
        rail.setHorizontalScrollBarEnabled(false);
        rail.setOverScrollMode(OVER_SCROLL_NEVER);
        LinearLayoutManager manager =
                new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false);
        manager.setInitialPrefetchItemCount(4);
        rail.setLayoutManager(manager);

        ContinueAdapter adapter = new ContinueAdapter();
        rail.setAdapter(adapter);
        rail.setPadding(0, 0, dp(16), 0);
        block.addView(rail, new LinearLayout.LayoutParams(-1, dp(97) + shelfTitleHeight));

        block.setVisibility(View.GONE);
        return new ContinueShelf(block, rail, adapter);
    }

    private Shelf addShelf(String title, String subtitle, boolean portraitCards) {
        return addShelf(title, subtitle, portraitCards, listener, false, true);
    }

    private Shelf addShelf(
            String title,
            String subtitle,
            boolean portraitCards,
            Listener clickListener,
            boolean showSource,
            boolean prewarmCollections
    ) {
        LinearLayout block = new LinearLayout(getContext());
        block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(-1, -2);
        blockParams.setMargins(0, 0, 0, dp(20));
        body.addView(block, blockParams);

        LinearLayout heading = new LinearLayout(getContext());
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(0, 0, 0, dp(12));

        TextView titleView = text(title, 19f, AppearanceTheme.primaryText(getContext()));
        ZeroChillTypography.apply(titleView, ZeroChillTypography.Role.SECTION);
        titleView.setTextSize(19f);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.addView(titleView);

        TextView subtitleView = text(
                subtitle,
                11f,
                ZeroChillUi.color(getContext(), R.color.zc_text_secondary)
        );
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(2);
        heading.addView(subtitleView, subtitleParams);
        block.addView(heading);

        RecyclerView rail = new RecyclerView(getContext());
        rail.setClipToPadding(false);
        rail.setHorizontalScrollBarEnabled(false);
        rail.setOverScrollMode(OVER_SCROLL_NEVER);
        GridLayoutManager manager =
                new GridLayoutManager(getContext(), 2, RecyclerView.HORIZONTAL, false);
        manager.setInitialPrefetchItemCount(4);
        rail.setLayoutManager(manager);

        RailAdapter adapter = new RailAdapter(
                portraitCards,
                clickListener,
                showSource,
                prewarmCollections
        );
        rail.setAdapter(adapter);
        rail.setPadding(0, 0, dp(16), 0);
        block.addView(rail, new LinearLayout.LayoutParams(
                -1,
                portraitCards ? dp(368) : 2 * (dp(97) + shelfTitleHeight)
        ));

        block.setVisibility(View.GONE);
        return new Shelf(block, rail, adapter);
    }

    private void loadArtwork(ImageView view, NativeContentItem item, boolean hero) {
        boolean fullHeroArtwork = hero && view == heroImage;
        byte[] embedded = EmbeddedBrowseArtwork.get(getContext(), item.url);
        if (embedded != null && embedded.length >= 512) {
            com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> request =
                    Glide.with(view)
                            .load(embedded)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(false)
                            .disallowHardwareConfig()
                            .dontAnimate()
                            .transition(hero ? ThumbnailFades.hero() : ThumbnailFades.thumbnail())
                            .placeholder(artworkPlaceholder())
                            .error(artworkPlaceholder());
            if (fullHeroArtwork) request.fitCenter();
            else request.centerCrop();
            request.into(view);
            return;
        }

        String imageUrl = item.imageUrl == null ? "" : item.imageUrl.trim();
        if (imageUrl.isEmpty()) {
            Glide.with(view).clear(view);
            if (WebVideoSourceRepository.isKaoticUrl(item.url)) {
                view.setImageDrawable(new GradientDrawable(
                        GradientDrawable.Orientation.BR_TL,
                        new int[] {
                                Color.rgb(4, 7, 9),
                                Color.rgb(7, 42, 55),
                                Color.rgb(4, 7, 9)
                        }
                ));
            } else {
                view.setImageDrawable(artworkPlaceholder());
            }
            return;
        }

        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", artworkAgent(imageUrl, item.url));
        if (item.url != null && item.url.startsWith("http")) {
            headers.addHeader("Referer", item.url);
        }
        addXMegaDriveArtworkHeaders(headers, imageUrl, item.url);

        com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> request =
                Glide.with(view)
                        .load(new GlideUrl(imageUrl, headers.build()))
                        .disallowHardwareConfig()
                        .diskCacheStrategy(
                                hero ? DiskCacheStrategy.ALL : DiskCacheStrategy.AUTOMATIC
                        )
                        .disallowHardwareConfig()
                        .dontAnimate()
                        .transition(hero ? ThumbnailFades.hero() : ThumbnailFades.thumbnail())
                        .placeholder(artworkPlaceholder())
                        .error(artworkPlaceholder());
        if (fullHeroArtwork) request.fitCenter();
        else request.centerCrop();
        request.into(view);
    }

    private static String artworkAgent(String imageUrl, String pageUrl) {
        if (isXMegaDriveHost(imageUrl) || isXMegaDriveHost(pageUrl)) {
            String session = XMegaDriveBrowserFetch.sessionUserAgent();
            if (session != null && !session.trim().isEmpty()) return session.trim();
        }
        return USER_AGENT;
    }

    private static void addXMegaDriveArtworkHeaders(
            LazyHeaders.Builder headers,
            String imageUrl,
            String pageUrl
    ) {
        if (!isXMegaDriveHost(imageUrl) && !isXMegaDriveHost(pageUrl)) return;
        headers.addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        try {
            String lookup = imageUrl != null && imageUrl.startsWith("http") ? imageUrl : pageUrl;
            String cookies = CookieManager.getInstance().getCookie(lookup);
            if (cookies == null || cookies.trim().isEmpty()) {
                cookies = CookieManager.getInstance().getCookie("https://www.xmegadrive.com/");
            }
            if (cookies != null && !cookies.trim().isEmpty()) headers.addHeader("Cookie", cookies);
        } catch (Exception ignored) {
        }
    }

    private static boolean isXMegaDriveHost(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        try {
            String host = java.net.URI.create(value.trim()).getHost();
            if (host == null) return false;
            host = host.toLowerCase(java.util.Locale.US);
            return "xmegadrive.com".equals(host) || host.endsWith(".xmegadrive.com");
        } catch (Exception ignored) {
            return false;
        }
    }

    private void loadContinueArtwork(
            ImageView view,
            NativeContentItem item,
            String pageUrl
    ) {
        java.io.File savedFrame = ShowsContinueFrameStore.find(getContext(), pageUrl);
        if (savedFrame == null) {
            loadArtwork(view, item, false);
            return;
        }
        Glide.with(view)
                .load(savedFrame)
                .disallowHardwareConfig()
                .signature(new ObjectKey(
                        savedFrame.lastModified() + ":" + savedFrame.length()
                ))
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(false)
                .disallowHardwareConfig()
                .centerCrop()
                .dontAnimate()
                .transition(ThumbnailFades.thumbnail())
                .placeholder(artworkPlaceholder())
                .error(artworkPlaceholder())
                .into(view);
    }

    private List<NativeContentItem> safe(List<NativeContentItem> items) {
        if (items == null || items.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(items);
    }

    private String formatTimeRemaining(long positionMs, long durationMs) {
        if (durationMs <= 0L) return "";
        long remainingMs = Math.max(0L, durationMs - Math.max(0L, positionMs));
        long totalMinutes = Math.max(1L, (remainingMs + 59_999L) / 60_000L);
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (hours > 0L && minutes > 0L) {
            return String.format(java.util.Locale.US, "%dh %dm left", hours, minutes);
        }
        if (hours > 0L) {
            return String.format(java.util.Locale.US, "%dh left", hours);
        }
        return String.format(java.util.Locale.US, "%dm left", minutes);
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void styleThumbnailCaption(TextView title) {
        title.setTextSize(12f);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        title.setIncludeFontPadding(false);
        androidx.core.widget.TextViewCompat.setLineHeight(title, Math.round(
                16f * getResources().getDisplayMetrics().scaledDensity));
        title.setGravity(Gravity.TOP | Gravity.START);
        title.setPadding(0, dp(6), 0, 0);
        title.setBackground(null);
    }

    private int themeAccent() {
        return AppearanceTheme.accent(getContext());
    }

    private GradientDrawable artworkPlaceholder() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        Color.rgb(27, 40, 51),
                        Color.rgb(16, 26, 34),
                        Color.rgb(10, 16, 21)
                });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class ContinueAdapter extends RecyclerView.Adapter<ContinueHolder> {
        private final List<PlaybackHistoryStore.Item> items = new ArrayList<>();

        ContinueAdapter() {
            setHasStableIds(true);
        }

        void replace(List<PlaybackHistoryStore.Item> next) {
            items.clear();
            if (next != null) {
                int count = Math.min(12, next.size());
                items.addAll(next.subList(0, count));
            }
            notifyDataSetChanged();
        }

        void refreshArtwork(String pageUrl) {
            if (pageUrl == null || pageUrl.isEmpty()) return;
            for (int i = 0; i < items.size(); i++) {
                if (pageUrl.equals(items.get(i).pageUrl)) {
                    notifyItemChanged(i);
                    return;
                }
            }
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).pageUrl.hashCode();
        }

        @NonNull
        @Override
        public ContinueHolder onCreateViewHolder(
                @NonNull android.view.ViewGroup parent,
                int viewType
        ) {
            MaterialCardView card = new MaterialCardView(getContext());
            ZeroChillUi.styleMediaCard(card, R.dimen.zc_radius_medium);
            card.setRadius(0f);
            card.setCardBackgroundColor(Color.TRANSPARENT);
            card.setCardElevation(0f);
            card.setClickable(true);
            card.setFocusable(true);
            ZeroChillMotion.installPressFeedback(card);

            RecyclerView.LayoutParams params =
                    new RecyclerView.LayoutParams(dp(160), dp(91) + shelfTitleHeight);
            params.setMargins(0, dp(3), dp(12), dp(3));
            card.setLayoutParams(params);

            FrameLayout frame = new FrameLayout(getContext());
            card.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

            ImageView image = new ImageView(getContext());
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(new ColorDrawable(Color.rgb(20, 22, 25)));
            GradientDrawable imageCorners = new GradientDrawable();
            imageCorners.setColor(Color.rgb(17, 26, 34));
            imageCorners.setCornerRadius(dp(17));
            image.setBackground(imageCorners);
            image.setClipToOutline(true);
            frame.addView(image, new FrameLayout.LayoutParams(-1, dp(91), Gravity.TOP));

            View shade = new View(getContext());
            shade.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {
                            Color.argb(0, 0, 0, 0),
                            Color.argb(54, 0, 0, 0),
                            Color.argb(244, 0, 0, 0)
                    }
            ));
            frame.addView(shade, new FrameLayout.LayoutParams(-1, dp(91), Gravity.TOP));

            TextView meta = text("", 12f, Color.WHITE);
            meta.setTypeface(null, android.graphics.Typeface.BOLD);
            meta.setPadding(dp(10), dp(6), dp(10), dp(6));
            GradientDrawable metaBackground = new GradientDrawable();
            metaBackground.setColor(Color.argb(205, 8, 10, 12));
            metaBackground.setCornerRadius(dp(8));
            meta.setBackground(metaBackground);
            FrameLayout.LayoutParams metaParams =
                    new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            metaParams.setMargins(dp(10), dp(10), dp(10), 0);
            frame.addView(meta, metaParams);

            TextView title = text("", 16.5f, Color.WHITE);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            styleThumbnailCaption(title);
            frame.addView(title, new FrameLayout.LayoutParams(-1, shelfTitleHeight, Gravity.BOTTOM));

            FrameLayout progressTrack = new FrameLayout(getContext());
            progressTrack.setBackgroundColor(Color.argb(170, 18, 20, 22));
            FrameLayout.LayoutParams trackParams =
                    new FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP);
            trackParams.setMargins(dp(5), dp(86), dp(5), 0);
            frame.addView(progressTrack, trackParams);

            View progressFill = new View(getContext());
            progressFill.setBackgroundColor(AppearanceTheme.accent(getContext()));
            progressFill.setPivotX(0f);
            progressTrack.addView(progressFill, new FrameLayout.LayoutParams(-1, -1));

            return new ContinueHolder(card, image, title, meta, progressTrack, progressFill);
        }

        @Override
        public void onBindViewHolder(@NonNull ContinueHolder holder, int position) {
            PlaybackHistoryStore.Item history = items.get(position);
            holder.image.setTag(R.id.shows_watch_source_page, history.pageUrl);
            NativeContentItem item = new NativeContentItem(
                    NativeContentItem.KIND_MEDIA,
                    history.title,
                    history.pageUrl,
                    history.posterUrl,
                    "",
                    "",
                    ""
            );
            holder.title.setText(history.title);
            String remaining = formatTimeRemaining(history.positionMs, history.durationMs);
            holder.meta.setText(remaining);
            holder.meta.setVisibility(remaining.isEmpty() ? View.GONE : View.VISIBLE);
            holder.card.setContentDescription("Resume " + history.title);
            float progress = history.durationMs <= 0L
                    ? 0f
                    : Math.max(0f, Math.min(1f,
                    history.positionMs / (float) history.durationMs));
            holder.progressFill.setScaleX(progress);
            holder.progressTrack.setVisibility(history.durationMs > 0L ? View.VISIBLE : View.GONE);
            holder.card.setOnClickListener(v -> {
                if (continueListener != null) continueListener.onResume(history, holder.image);
            });
            holder.image.setTag(R.id.shows_watch_source_page, history.pageUrl);
            loadContinueArtwork(holder.image, item, history.pageUrl);
        }

        @Override
        public void onViewRecycled(@NonNull ContinueHolder holder) {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setTag(R.id.shows_watch_source_page, null);
            holder.card.setOnClickListener(null);
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private final class RailAdapter extends RecyclerView.Adapter<RailHolder> {
        private final boolean portrait;
        private final Listener clickListener;
        private final boolean showSource;
        private final boolean prewarmCollections;
        private final List<NativeContentItem> items = new ArrayList<>();

        RailAdapter(
                boolean portrait,
                Listener clickListener,
                boolean showSource,
                boolean prewarmCollections
        ) {
            this.portrait = portrait;
            this.clickListener = clickListener;
            this.showSource = showSource;
            this.prewarmCollections = prewarmCollections;
            setHasStableIds(true);
        }

        void replace(List<NativeContentItem> next) {
            items.clear();
            if (next != null) items.addAll(next);
            notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            NativeContentItem item = items.get(position);
            return (item.kind + "\n" + item.url + "\n" + item.title).hashCode();
        }

        @NonNull
        @Override
        public RailHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            MaterialCardView card = new MaterialCardView(getContext());
            ZeroChillUi.styleMediaCard(card, R.dimen.zc_radius_medium);
            card.setRadius(dp(portrait ? 17 : 0));
            if (!portrait) card.setCardBackgroundColor(Color.TRANSPARENT);
            card.setCardElevation(0f);
            card.setClickable(true);
            card.setFocusable(true);
            ZeroChillMotion.installPressFeedback(card);

            int width = dp(portrait ? 128 : 160);
            int height = portrait ? dp(178) : dp(91) + shelfTitleHeight;
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(width, height);
            params.setMargins(0, dp(3), dp(12), dp(3));
            card.setLayoutParams(params);

            FrameLayout frame = new FrameLayout(getContext());
            card.addView(frame, new MaterialCardView.LayoutParams(-1, -1));

            ImageView image = new ImageView(getContext());
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] { Color.rgb(27, 40, 51), Color.rgb(10, 16, 21) }));
            if (!portrait) {
                GradientDrawable corners = new GradientDrawable();
                corners.setColor(Color.rgb(17, 26, 34));
                corners.setCornerRadius(dp(17));
                image.setBackground(corners);
                image.setClipToOutline(true);
            }
            frame.addView(image, new FrameLayout.LayoutParams(
                    -1, portrait ? -1 : Math.round(width * 9f / 16f)));

            View shade = new View(getContext());
            shade.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {
                            Color.argb(0, 0, 0, 0),
                            Color.argb(36, 0, 0, 0),
                            Color.argb(235, 0, 0, 0)
                    }
            ));
            if (portrait) frame.addView(shade, new FrameLayout.LayoutParams(-1, -1));

            TextView source = text("", 8f, AppearanceTheme.accent(getContext()));
            source.setTypeface(null, android.graphics.Typeface.BOLD);
            source.setGravity(Gravity.CENTER);
            GradientDrawable badge = new GradientDrawable();
            badge.setShape(GradientDrawable.OVAL);
            badge.setColor(Color.argb(190, 9, 16, 23));
            source.setBackground(badge);
            source.setVisibility(showSource ? View.VISIBLE : View.GONE);
            FrameLayout.LayoutParams sourceParams =
                    new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP | Gravity.START);
            sourceParams.setMargins(dp(6), dp(6), 0, 0);
            frame.addView(source, sourceParams);

            TextView title = portrait ? text("", 14.5f, Color.WHITE) : text("", 12f, Color.WHITE);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (portrait) {
                title.setGravity(Gravity.BOTTOM);
                title.setPadding(dp(11), dp(8), dp(11), dp(10));
            } else {
                styleThumbnailCaption(title);
            }
            frame.addView(title, new FrameLayout.LayoutParams(
                    -1, portrait ? -2 : shelfTitleHeight, Gravity.BOTTOM));

            return new RailHolder(card, image, source, title);
        }

        @Override
        public void onBindViewHolder(@NonNull RailHolder holder, int position) {
            NativeContentItem item = items.get(position);
            holder.image.setTag(R.id.shows_watch_source_page, item.url);
            holder.title.setText(item.title);
            String provider = showSource ? WeeklyShowsFeed.sourceLabel(item) : "";
            holder.source.setText(sourceMark(provider));
            holder.source.setContentDescription(provider.isEmpty() ? null : provider + " source");
            holder.source.setVisibility(showSource ? View.VISIBLE : View.GONE);
            holder.card.setContentDescription("Open " + item.title);
            holder.card.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onOpen(item, holder.image);
            });
            holder.image.setTag(R.id.shows_watch_source_page, item.url);
            loadArtwork(holder.image, item, false);
        }

        @Override
        public void onViewRecycled(@NonNull RailHolder holder) {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setTag(R.id.shows_watch_source_page, null);
            holder.card.setOnClickListener(null);
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private String sourceMark(String provider) {
        if ("CrazyShit".equalsIgnoreCase(provider)) return "CS";
        if ("EFukt".equalsIgnoreCase(provider)) return "eF";
        if ("Kaotic".equalsIgnoreCase(provider)) return "K";
        if ("BaddieHub".equalsIgnoreCase(provider)) return "BH";
        if ("XMegaDrive".equalsIgnoreCase(provider)) return "XM";
        return provider.isEmpty() ? "" : provider.substring(0, 1).toUpperCase(java.util.Locale.US);
    }

    private final class Shelf {
        final LinearLayout container;
        final RecyclerView rail;
        final RailAdapter adapter;

        Shelf(LinearLayout container, RecyclerView rail, RailAdapter adapter) {
            this.container = container;
            this.rail = rail;
            this.adapter = adapter;
        }
    }

    private final class ContinueShelf {
        final LinearLayout container;
        final RecyclerView rail;
        final ContinueAdapter adapter;

        ContinueShelf(LinearLayout container, RecyclerView rail, ContinueAdapter adapter) {
            this.container = container;
            this.rail = rail;
            this.adapter = adapter;
        }
    }

    private static final class ContinueHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView image;
        final TextView title;
        final TextView meta;
        final View progressTrack;
        final View progressFill;

        ContinueHolder(
                MaterialCardView card,
                ImageView image,
                TextView title,
                TextView meta,
                View progressTrack,
                View progressFill
        ) {
            super(card);
            this.card = card;
            this.image = image;
            this.title = title;
            this.meta = meta;
            this.progressTrack = progressTrack;
            this.progressFill = progressFill;
        }
    }

    private static final class RailHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView image;
        final TextView source;
        final TextView title;

        RailHolder(MaterialCardView card, ImageView image, TextView source, TextView title) {
            super(card);
            this.card = card;
            this.image = image;
            this.source = source;
            this.title = title;
        }
    }
}
