package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native media feed opened from a Series or Category card. */
public final class NativeFeedBrowserActivity extends Activity {
    private static final int CREATOR_TAB_ALL = 0;
    private static final int CREATOR_TAB_PICTURES = 1;
    private static final int CREATOR_TAB_VIDEOS = 2;
    private static final int CREATOR_TAB_COUNT = 3;
    private static final long CREATOR_GRID_MORPH_DURATION_MS = 165L;
    private static final long CREATOR_GRID_RELEASE_DURATION_MS = 110L;

    public static final String EXTRA_TITLE = "browser_title";
    public static final String EXTRA_BASE_URL = "browser_base_url";
    public static final String EXTRA_MEME_MODE = "browser_meme_mode";
    public static final String EXTRA_SOURCE = "browser_source";
    public static final String EXTRA_BUNKR_CREATOR_QUERY = "browser_bunkr_creator_query";
    public static final String EXTRA_FAPELLO_PROFILE_URL = "browser_fapello_profile_url";
    public static final String EXTRA_CREATOR_GALLERY_SESSION = "browser_creator_gallery_session";
    public static final String EXTRA_CREATOR_GALLERY_CACHE_KEY = "browser_creator_gallery_cache_key";
    public static final String EXTRA_CREATOR_SEED_NAMES = "browser_creator_seed_names";
    public static final String EXTRA_CREATOR_SEED_URLS = "browser_creator_seed_urls";
    public static final String EXTRA_CREATOR_SEED_IMAGES = "browser_creator_seed_images";
    public static final String EXTRA_CREATOR_AVATAR_PICKER = "browser_creator_avatar_picker";
    public static final String EXTRA_PICKED_AVATAR_URL = "browser_picked_avatar_url";
    public static final String EXTRA_PICKED_AVATAR_REFERER = "browser_picked_avatar_referer";
    public static final String EXTRA_PICKED_AVATAR_FOCUS_X = "browser_picked_avatar_focus_x";
    public static final String EXTRA_PICKED_AVATAR_FOCUS_Y = "browser_picked_avatar_focus_y";
    public static final String EXTRA_PICKED_AVATAR_ZOOM = "browser_picked_avatar_zoom";
    static final String EXTRA_SHITTOK_RETURN_TRANSITION = "browser_shittok_return_transition";
    public static final String EXTRA_NOTIFICATION_FRESH_URLS =
            "browser_notification_fresh_urls";
    public static final String EXTRA_SHOW_DETAILS = "browser_show_details";
    public static final String EXTRA_SHOW_IMAGE_URL = "browser_show_image_url";
    public static final String EXTRA_SHOW_DESCRIPTION = "browser_show_description";
    public static final String EXTRA_SHOW_KIND = "browser_show_kind";
    public static final String SOURCE_CRAZYSHIT = "crazyshit";
    public static final String SOURCE_EFUKT = "efukt";
    public static final String SOURCE_KAOTIC = "kaotic";
    public static final String SOURCE_BUNKR = "bunkr";

    private static final int REQUEST_CREATOR_AVATAR_CROP = 4107;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final CrazyShitRepository repository = new CrazyShitRepository();
    private final EfuktRepository efuktRepository = new EfuktRepository();
    private final WebVideoSourceRepository webVideoRepository = new WebVideoSourceRepository();
    private final BunkrRepository bunkrRepository = new BunkrRepository();
    private final BunkrCreatorGalleryRepository creatorGalleryRepository =
            new BunkrCreatorGalleryRepository();
    private final MemeRepository memeRepository = new MemeRepository();

    private NativeFeedAdapter adapter;
    private BunkrGalleryAdapter bunkrGalleryAdapter;
    private final BunkrGalleryAdapter[] creatorTabAdapters =
            new BunkrGalleryAdapter[CREATOR_TAB_COUNT];
    private final RecyclerView[] creatorTabRecyclers =
            new RecyclerView[CREATOR_TAB_COUNT];
    private String bunkrGallerySessionId;
    private String browserSnapshot = ScreenSnapshotStore.newId();
    private Bundle restoredBrowserState;
    private boolean restoringBrowser;

    private RecyclerView recycler;
    private ViewPager2 creatorTabsPager;
    private TabLayout creatorTabs;
    private CreatorProfileHeader creatorProfile;
    private AppBarLayout creatorAppBar;
    private ShowDetailsHeader showDetailsHeader;
    private AppBarLayout showDetailsAppBar;
    private TabLayoutMediator creatorTabsMediator;
    private SwipeRefreshLayout refresh;
    private View progress;
    private CreatorGallerySkeleton gallerySkeleton;
    private TextView empty;
    private String title;
    private String baseUrl;
    private String source;
    private String creatorQuery;
    private String fapelloProfileUrl;
    private String creatorGalleryCacheKey;
    private ArrayList<String> creatorSeedNames = new ArrayList<>();
    private ArrayList<String> creatorSeedUrls = new ArrayList<>();
    private ArrayList<String> creatorSeedImages = new ArrayList<>();
    private String showImageUrl;
    private String showDescription;
    private String showKind;
    private boolean showDetailsMode;
    private boolean memeMode;
    private boolean creatorAvatarPickerMode;
    private boolean loading;
    private boolean endReached;
    private boolean fapelloFailureShown;
    private int currentPage;
    private int generation;
    private int creatorGalleryColumns;
    private final ArrayList<String> notificationFreshUrls = new ArrayList<>();
    private final Set<String> notificationFreshUrlSet = new LinkedHashSet<>();
    private boolean notificationFreshPendingFocus;
    private String shitTokReturnTransition = "";
    private FrameLayout shitTokTransitionHost;
    private View shitTokTransitionContent;
    private ImageView shitTokReturnSnapshot;
    private View shitTokReturnHeaderMask;
    private ImageView creatorHandoffPreview;
    private float shitTokReturnDownX;
    private float shitTokReturnDownY;
    private boolean shitTokReturnTracking;
    private boolean shitTokReturnFinishing;

    public static Intent create(Activity activity, String title, String baseUrl, boolean memeMode) {
        return create(activity, title, baseUrl, memeMode, SOURCE_CRAZYSHIT);
    }

    public static Intent create(
            Activity activity,
            String title,
            String baseUrl,
            boolean memeMode,
            String source
    ) {
        Intent intent = new Intent(activity, NativeFeedBrowserActivity.class);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_BASE_URL, baseUrl);
        intent.putExtra(EXTRA_MEME_MODE, memeMode);
        intent.putExtra(EXTRA_SOURCE, source);
        return intent;
    }

    public static Intent createShowDetails(
            Activity activity,
            NativeContentItem item,
            String source
    ) {
        if (item == null) return create(activity, "Shows", CrazyShitRepository.HOME, false, source);
        Intent intent = create(activity, item.title, item.url, false, source);
        intent.putExtra(EXTRA_SHOW_DETAILS, true);
        intent.putExtra(EXTRA_SHOW_IMAGE_URL, item.imageUrl);
        intent.putExtra(EXTRA_SHOW_DESCRIPTION, item.description);
        intent.putExtra(EXTRA_SHOW_KIND, item.kind);
        return intent;
    }

    public static Intent createCreatorGallery(Activity activity, String title, String query) {
        return createCreatorGallery(activity, title, query, "");
    }

    public static Intent createCreatorGallery(
            Activity activity,
            String title,
            String query,
            String fapelloProfileUrl
    ) {
        return createCreatorGallery(activity, title, query, fapelloProfileUrl, "");
    }

    public static Intent createCreatorGallery(
            Activity activity,
            String title,
            String query,
            String fapelloProfileUrl,
            String gallerySessionId
    ) {
        return createCreatorGallery(
                activity,
                title,
                query,
                fapelloProfileUrl,
                gallerySessionId,
                query,
                null,
                null,
                null
        );
    }

    static Intent createCreatorGallery(
            Activity activity,
            String title,
            String query,
            String fapelloProfileUrl,
            String gallerySessionId,
            String galleryCacheKey,
            ArrayList<String> seedNames,
            ArrayList<String> seedUrls,
            ArrayList<String> seedImages
    ) {
        String cleanQuery = query == null ? "" : query.trim();
        Intent intent = create(
                activity,
                title,
                BunkrRepository.searchUrl(cleanQuery),
                false,
                SOURCE_BUNKR
        );
        intent.putExtra(EXTRA_BUNKR_CREATOR_QUERY, cleanQuery);
        if (FapelloRepository.isModelUrl(fapelloProfileUrl) ||
                OnlyHavenRepository.isOnlyHavenUrl(fapelloProfileUrl)) {
            // Keep the legacy extra key for compatibility. It now carries a known creator
            // profile from either fast source so the gallery can skip redundant discovery.
            intent.putExtra(EXTRA_FAPELLO_PROFILE_URL, fapelloProfileUrl);
        }
        String cleanCacheKey = galleryCacheKey == null || galleryCacheKey.trim().isEmpty()
                ? cleanQuery
                : galleryCacheKey.trim();
        if (!cleanCacheKey.isEmpty()) {
            intent.putExtra(EXTRA_CREATOR_GALLERY_CACHE_KEY, cleanCacheKey);
        }
        if (seedNames != null && !seedNames.isEmpty()) {
            intent.putStringArrayListExtra(EXTRA_CREATOR_SEED_NAMES, seedNames);
        }
        if (seedUrls != null && !seedUrls.isEmpty()) {
            intent.putStringArrayListExtra(EXTRA_CREATOR_SEED_URLS, seedUrls);
        }
        if (seedImages != null && !seedImages.isEmpty()) {
            intent.putStringArrayListExtra(EXTRA_CREATOR_SEED_IMAGES, seedImages);
        }
        if (gallerySessionId != null && !gallerySessionId.trim().isEmpty()) {
            intent.putExtra(EXTRA_CREATOR_GALLERY_SESSION, gallerySessionId.trim());
        }
        return intent;
    }

    public static Intent createCreatorAvatarPicker(
            Activity activity,
            String title,
            String query,
            String fapelloProfileUrl,
            String gallerySessionId
    ) {
        return createCreatorAvatarPicker(
                activity,
                title,
                query,
                fapelloProfileUrl,
                gallerySessionId,
                query,
                null,
                null,
                null
        );
    }

    static Intent createCreatorAvatarPicker(
            Activity activity,
            String title,
            String query,
            String fapelloProfileUrl,
            String gallerySessionId,
            String galleryCacheKey,
            ArrayList<String> seedNames,
            ArrayList<String> seedUrls,
            ArrayList<String> seedImages
    ) {
        Intent intent = createCreatorGallery(
                activity,
                title,
                query,
                fapelloProfileUrl,
                gallerySessionId,
                galleryCacheKey,
                seedNames,
                seedUrls,
                seedImages
        );
        intent.putExtra(EXTRA_CREATOR_AVATAR_PICKER, true);
        return intent;
    }

    static String creatorProfileHint(NativeContentItem item) {
        if (item == null || item.url == null) return "";
        String url = item.url.trim();
        return FapelloRepository.isModelUrl(url) ||
                OnlyHavenRepository.isOnlyHavenUrl(url)
                ? url
                : "";
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title = value(getIntent().getStringExtra(EXTRA_TITLE), "Browse");
        baseUrl = value(getIntent().getStringExtra(EXTRA_BASE_URL), CrazyShitRepository.HOME);
        memeMode = getIntent().getBooleanExtra(EXTRA_MEME_MODE, false);
        creatorAvatarPickerMode =
                getIntent().getBooleanExtra(EXTRA_CREATOR_AVATAR_PICKER, false);
        source = value(getIntent().getStringExtra(EXTRA_SOURCE), SOURCE_CRAZYSHIT);
        creatorQuery = value(getIntent().getStringExtra(EXTRA_BUNKR_CREATOR_QUERY), "");
        fapelloProfileUrl = value(getIntent().getStringExtra(EXTRA_FAPELLO_PROFILE_URL), "");
        creatorGalleryCacheKey = value(
                getIntent().getStringExtra(EXTRA_CREATOR_GALLERY_CACHE_KEY),
                creatorQuery
        );
        ArrayList<String> names = getIntent().getStringArrayListExtra(EXTRA_CREATOR_SEED_NAMES);
        ArrayList<String> urls = getIntent().getStringArrayListExtra(EXTRA_CREATOR_SEED_URLS);
        ArrayList<String> images = getIntent().getStringArrayListExtra(EXTRA_CREATOR_SEED_IMAGES);
        if (names != null) creatorSeedNames = new ArrayList<>(names);
        if (urls != null) creatorSeedUrls = new ArrayList<>(urls);
        if (images != null) creatorSeedImages = new ArrayList<>(images);
        shitTokReturnTransition = value(
                getIntent().getStringExtra(EXTRA_SHITTOK_RETURN_TRANSITION),
                ""
        );
        showDetailsMode = getIntent().getBooleanExtra(EXTRA_SHOW_DETAILS, false);
        showImageUrl = value(getIntent().getStringExtra(EXTRA_SHOW_IMAGE_URL), "");
        showDescription = value(getIntent().getStringExtra(EXTRA_SHOW_DESCRIPTION), "");
        showKind = value(getIntent().getStringExtra(EXTRA_SHOW_KIND), NativeContentItem.KIND_SERIES);
        ArrayList<String> freshUrls =
                getIntent().getStringArrayListExtra(EXTRA_NOTIFICATION_FRESH_URLS);
        if (freshUrls != null) {
            for (String freshUrl : freshUrls) {
                String clean = value(freshUrl, "");
                if (!clean.isEmpty() && notificationFreshUrlSet.add(clean)) {
                    notificationFreshUrls.add(clean);
                }
            }
        }
        notificationFreshPendingFocus = state == null
                ? !notificationFreshUrls.isEmpty()
                : state.getBoolean(
                        "notification_fresh_pending_focus",
                        !notificationFreshUrls.isEmpty()
                );
        bunkrGallerySessionId = value(
                getIntent().getStringExtra(EXTRA_CREATOR_GALLERY_SESSION),
                ""
        );
        if (!creatorQuery.isEmpty()) {
            source = SOURCE_BUNKR;
            baseUrl = BunkrRepository.searchUrl(creatorQuery);
        } else if (BunkrRepository.isAlbumUrl(baseUrl)) source = SOURCE_BUNKR;
        else if (WebVideoSourceRepository.isKaoticUrl(baseUrl)) source = SOURCE_KAOTIC;
        else if (EfuktRepository.isEfuktUrl(baseUrl)) source = SOURCE_EFUKT;
        restoredBrowserState = state;
        if (state != null) {
            browserSnapshot = state.getString("browser_snapshot", browserSnapshot);
            bunkrGallerySessionId = state.getString("gallery_session", bunkrGallerySessionId);
        }
        buildUi();
        if (state == null) {
            BunkrGallerySessionStore.Snapshot warm = isCreatorGallery()
                    ? BunkrGallerySessionStore.snapshot(bunkrGallerySessionId) : null;
            if (warm != null && !warm.items.isEmpty()) {
                showHotCreatorSnapshot(warm);
            } else if (isCreatorGallery() && !bunkrGallerySessionId.isEmpty()) {
                // A persisted hot-gallery id can survive process death. Restore it off the UI
                // thread, paint it immediately, then continue refreshing in the background.
                loading = true;
                progress.setVisibility(View.VISIBLE);
                if (gallerySkeleton != null) gallerySkeleton.setVisibility(View.VISIBLE);
                String restoreId = bunkrGallerySessionId;
                io.execute(() -> {
                    BunkrGallerySessionStore.Snapshot restored =
                            BunkrGallerySessionStore.restore(this, restoreId);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        loading = false;
                        if (restored != null && !restored.items.isEmpty()
                                && creatorGalleryCacheKey.equalsIgnoreCase(restored.creatorQuery)) {
                            showHotCreatorSnapshot(restored);
                        } else {
                            load(false);
                        }
                    });
                });
            } else {
                load(false);
            }
        } else {
            restoreBrowser();
        }
    }

    private void showHotCreatorSnapshot(BunkrGallerySessionStore.Snapshot warm) {
        replaceBunkrItems(warm.items);
        currentPage = warm.currentPage;
        endReached = warm.endReached;
        progress.setVisibility(View.GONE);
        if (gallerySkeleton != null) gallerySkeleton.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        refresh.setRefreshing(false);
        if (!endReached) {
            // Render first. The follow-up request happens after this frame so cached content is
            // never held behind a network refresh.
            recyclerOrCreatorPager().post(() -> load(true));
        }
    }

    private View recyclerOrCreatorPager() {
        if (creatorTabsPager != null) return creatorTabsPager;
        if (recycler != null) return recycler;
        return refresh;
    }

    private void buildUi() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.BLACK);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(6), dp(8), dp(6));
        top.setBackgroundColor(Color.BLACK);

        TextView back = text("‹", 34, Color.WHITE);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finishToShitTok());
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(52)));

        TextView heading = text(isCreatorGallery() ? "OnlyFap" : showDetailsMode ? "Shows" : title, 20, Color.WHITE);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setSingleLine(true);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView options = text("⋮", 28, Color.rgb(220, 220, 226));
        options.setGravity(Gravity.CENTER);
        options.setContentDescription("Feed options");
        options.setOnClickListener(this::showOptions);
        top.addView(options, new LinearLayout.LayoutParams(dp(48), dp(52)));
        shell.addView(top, new LinearLayout.LayoutParams(-1, dp(64)));

        FrameLayout body = new FrameLayout(this);
        if (isCreatorGallery()) {
            CoordinatorLayout coordinator = new CoordinatorLayout(this);
            coordinator.setBackgroundColor(Color.BLACK);
            shell.addView(coordinator, new LinearLayout.LayoutParams(-1, 0, 1f));

            creatorAppBar = new AppBarLayout(this);
            creatorAppBar.setBackgroundColor(Color.BLACK);
            creatorAppBar.setElevation(0f);
            creatorAppBar.setLiftOnScroll(false);
            CoordinatorLayout.LayoutParams appBarParams =
                    new CoordinatorLayout.LayoutParams(-1, -2);
            appBarParams.gravity = Gravity.TOP;
            coordinator.addView(creatorAppBar, appBarParams);

            creatorProfile = new CreatorProfileHeader(this, title, creatorQuery, baseUrl);
            AppBarLayout.LayoutParams profileParams =
                    new AppBarLayout.LayoutParams(-1, dp(184));
            profileParams.setScrollFlags(
                    AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL |
                            AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
            );
            creatorAppBar.addView(creatorProfile, profileParams);

            if (!notificationFreshUrls.isEmpty()) {
                TextView notificationContext = text(
                        notificationFreshUrls.size() == 1
                                ? "FROM UPDATES  •  1 NEW ITEM"
                                : "FROM UPDATES  •  " + notificationFreshUrls.size() + " NEW ITEMS",
                        11,
                        UiPalette.PRIMARY
                );
                notificationContext.setGravity(Gravity.CENTER);
                notificationContext.setTypeface(null, android.graphics.Typeface.BOLD);
                notificationContext.setPadding(dp(12), dp(5), dp(12), dp(7));
                notificationContext.setContentDescription(
                        notificationFreshUrls.size() + " new OnlyFap items from Updates"
                );
                AppBarLayout.LayoutParams notificationParams =
                        new AppBarLayout.LayoutParams(-1, dp(32));
                notificationParams.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL);
                creatorAppBar.addView(notificationContext, notificationParams);
            }

            creatorTabs = new TabLayout(this);
            creatorTabs.setBackgroundColor(Color.BLACK);
            creatorTabs.setSelectedTabIndicatorColor(UiPalette.PRIMARY);
            creatorTabs.setTabTextColors(Color.rgb(174, 174, 182), UiPalette.PRIMARY);
            creatorTabs.setTabMode(TabLayout.MODE_FIXED);
            creatorTabs.setTabGravity(TabLayout.GRAVITY_FILL);
            creatorAppBar.addView(
                    creatorTabs,
                    new AppBarLayout.LayoutParams(-1, dp(48))
            );

            creatorAppBar.addOnOffsetChangedListener((appBar, verticalOffset) -> {
                if (creatorProfile == null) return;
                float progress = Math.min(
                        1f,
                        Math.abs(verticalOffset) / (float) Math.max(1, dp(184))
                );
                creatorProfile.setAlpha(1f - progress);
            });

            CoordinatorLayout.LayoutParams bodyParams =
                    new CoordinatorLayout.LayoutParams(-1, -1);
            bodyParams.setBehavior(new AppBarLayout.ScrollingViewBehavior());
            coordinator.addView(body, bodyParams);
        } else if (showDetailsMode) {
            CoordinatorLayout coordinator = new CoordinatorLayout(this);
            coordinator.setBackgroundColor(Color.BLACK);
            shell.addView(coordinator, new LinearLayout.LayoutParams(-1, 0, 1f));

            showDetailsAppBar = new AppBarLayout(this);
            showDetailsAppBar.setBackgroundColor(Color.BLACK);
            showDetailsAppBar.setElevation(0f);
            showDetailsAppBar.setLiftOnScroll(false);
            CoordinatorLayout.LayoutParams appBarParams =
                    new CoordinatorLayout.LayoutParams(-1, -2);
            appBarParams.gravity = Gravity.TOP;
            coordinator.addView(showDetailsAppBar, appBarParams);

            showDetailsHeader = new ShowDetailsHeader(
                    this,
                    title,
                    showSourceLabel(),
                    showDescription,
                    baseUrl,
                    showImageUrl,
                    () -> {
                        if (showDetailsAppBar != null) {
                            showDetailsAppBar.setExpanded(false, true);
                        }
                        if (recycler != null) recycler.scrollToPosition(0);
                    }
            );
            AppBarLayout.LayoutParams headerParams =
                    new AppBarLayout.LayoutParams(-1, dp(286));
            headerParams.setScrollFlags(
                    AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL |
                            AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
            );
            showDetailsAppBar.addView(showDetailsHeader, headerParams);
            showDetailsAppBar.addOnOffsetChangedListener((appBar, verticalOffset) -> {
                if (showDetailsHeader == null) return;
                float progress = Math.min(
                        1f,
                        Math.abs(verticalOffset) / (float) Math.max(1, dp(286))
                );
                showDetailsHeader.setAlpha(1f - (progress * 0.78f));
            });

            CoordinatorLayout.LayoutParams bodyParams =
                    new CoordinatorLayout.LayoutParams(-1, -1);
            bodyParams.setBehavior(new AppBarLayout.ScrollingViewBehavior());
            coordinator.addView(body, bodyParams);
        } else {
            shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        }

        refresh = new SwipeRefreshLayout(this);
        refresh.setColorSchemeColors(UiPalette.PRIMARY);
        refresh.setOnRefreshListener(this::reload);
        if (showDetailsMode) {
            LinearLayout detailsFeed = new LinearLayout(this);
            detailsFeed.setOrientation(LinearLayout.VERTICAL);
            detailsFeed.setBackgroundColor(Color.BLACK);

            TextView section = text("VIDEOS", 12, UiPalette.PRIMARY);
            section.setTypeface(null, android.graphics.Typeface.BOLD);
            section.setLetterSpacing(0.12f);
            section.setGravity(Gravity.CENTER_VERTICAL);
            section.setPadding(dp(16), dp(9), dp(16), dp(8));
            detailsFeed.addView(section, new LinearLayout.LayoutParams(-1, dp(40)));
            detailsFeed.addView(refresh, new LinearLayout.LayoutParams(-1, 0, 1f));
            body.addView(detailsFeed, new FrameLayout.LayoutParams(-1, -1));
        } else {
            body.addView(refresh, new FrameLayout.LayoutParams(-1, -1));
        }

        if (isBunkr()) {
            if (bunkrGallerySessionId == null || bunkrGallerySessionId.isEmpty()) {
                String warmId = isCreatorGallery()
                        ? BunkrGallerySessionStore.recentCreator(creatorGalleryCacheKey) : null;
                bunkrGallerySessionId = warmId != null ? warmId : isCreatorGallery()
                        ? BunkrGallerySessionStore.createCreator(
                                title,
                                baseUrl,
                                creatorGalleryCacheKey
                        )
                        : BunkrGallerySessionStore.create(title, baseUrl);
                if (isCreatorGallery() && warmId == null) creatorGalleryRepository.reset(
                        bunkrGallerySessionId,
                        creatorQuery,
                        fapelloProfileUrl,
                        title,
                        creatorSeedNames,
                        creatorSeedUrls,
                        creatorSeedImages
                );
            }
            if (isCreatorGallery()) buildCreatorTabs();
            else {
                recycler = createRecycler();
                bunkrGalleryAdapter = createBunkrGalleryAdapter(false);
                recycler.setAdapter(bunkrGalleryAdapter);
                refresh.addView(recycler, new SwipeRefreshLayout.LayoutParams(-1, -1));
                attachGalleryScrollListener(recycler, bunkrGalleryAdapter);
            }
        } else {
            recycler = createRecycler();
            adapter = new NativeFeedAdapter(this, new NativeFeedAdapter.Listener() {
                @Override
                public void onOpen(NativeContentItem item) {
                    if (item == null || item.isSection()) return;
                    if (memeMode || item.isMeme()) openMeme(item);
                    else openVideo(item);
                }

                @Override
                public void onLongPress(NativeContentItem item, View anchor) {
                    if (item == null || item.isSection()) return;
                    showItemMenu(item, anchor);
                }

                @Override
                public void onComments(NativeContentItem item) {
                    if (item == null || item.isSection() || memeMode || !supportsComments()) return;
                    new InlineCommentsDialog(
                            NativeFeedBrowserActivity.this,
                            item.url,
                            item.title,
                            item.comments,
                            null
                    ).show();
                }
            });
            recycler.setAdapter(adapter);
            refresh.addView(recycler, new SwipeRefreshLayout.LayoutParams(-1, -1));
            attachFeedScrollListener(recycler);
        }
        applyLayout();

        if (isCreatorGallery()) {
            gallerySkeleton = new CreatorGallerySkeleton(this);
            body.addView(gallerySkeleton, new FrameLayout.LayoutParams(-1, -1));
        }
        progress = isCreatorGallery()
                ? new ZeroChillLoadingView(this, "Loading gallery...", true)
                : new ZeroChillLoadingView(this, null);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                dp(isCreatorGallery() ? 160 : 72), dp(isCreatorGallery() ? 132 : 72));
        progressParams.gravity = Gravity.CENTER;
        body.addView(progress, progressParams);
        progress.setVisibility(View.GONE);

        empty = text("", 15, Color.rgb(190, 190, 198));
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(28), dp(28), dp(28), dp(28));
        empty.setVisibility(View.GONE);
        empty.setOnClickListener(v -> {
            if (isCreatorGallery()) {
                if (itemCount() == 0) reload();
                else if (!endReached) load(true);
            }
            else openWebsite(baseUrl);
        });
        body.addView(empty, new FrameLayout.LayoutParams(-1, -1));

        if (isCreatorGallery() && !shitTokReturnTransition.isEmpty()) {
            installShitTokReturnSurface(shell);
        } else {
            setContentView(shell);
        }
    }

    private void installShitTokReturnSurface(View content) {
        shitTokTransitionHost = new FrameLayout(this);
        shitTokTransitionHost.setBackgroundColor(Color.BLACK);

        shitTokReturnSnapshot = new ImageView(this);
        shitTokReturnSnapshot.setScaleType(ImageView.ScaleType.FIT_XY);
        shitTokReturnSnapshot.setBackgroundColor(Color.BLACK);
        shitTokReturnSnapshot.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO
        );
        shitTokTransitionHost.addView(
                shitTokReturnSnapshot,
                new FrameLayout.LayoutParams(-1, -1)
        );
        installShitTokReturnHeaderMask();

        shitTokTransitionContent = content;
        shitTokTransitionContent.setElevation(dp(10));
        shitTokTransitionHost.addView(
                shitTokTransitionContent,
                new FrameLayout.LayoutParams(-1, -1)
        );
        setContentView(shitTokTransitionHost);
        refreshShitTokReturnSnapshot(0);
        Bitmap galleryFrame = ShitTokTransitionSnapshotStore.galleryPreview(shitTokReturnTransition);
        if (galleryFrame != null && !galleryFrame.isRecycled()) {
            creatorHandoffPreview = new ImageView(this);
            creatorHandoffPreview.setScaleType(ImageView.ScaleType.FIT_XY);
            creatorHandoffPreview.setImageBitmap(galleryFrame);
            creatorHandoffPreview.setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            shitTokTransitionHost.addView(creatorHandoffPreview,
                    new FrameLayout.LayoutParams(-1, -1));
            // A failed thumbnail request must not leave a frozen preview over an
            // otherwise usable gallery. The normal path removes it on the first ready draw.
            shitTokTransitionHost.postDelayed(() -> {
                if (creatorHandoffPreview != null) clearCreatorHandoffPreview();
            }, 2000L);
            shitTokTransitionHost.getViewTreeObserver().addOnPreDrawListener(
                    new ViewTreeObserver.OnPreDrawListener() {
                        @Override public boolean onPreDraw() {
                            if (creatorHandoffPreview == null) {
                                shitTokTransitionHost.getViewTreeObserver()
                                        .removeOnPreDrawListener(this);
                                return true;
                            }
                            if (creatorGalleryFirstFrameReady()) {
                                shitTokTransitionHost.getViewTreeObserver()
                                        .removeOnPreDrawListener(this);
                                clearCreatorHandoffPreview();
                                return false;
                            }
                            return true;
                        }
                    });
        }
    }

    private void installShitTokReturnHeaderMask() {
        if (shitTokTransitionHost == null || shitTokReturnHeaderMask != null) return;

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), 0, dp(12), 0);
        bar.setBackgroundColor(ZeroChillUi.background(this));
        bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        bar.setClickable(false);
        bar.setFocusable(false);

        TextView titleView = new TextView(this);
        ZeroChillUi.styleTitle(titleView);
        titleView.setTextSize(28f);
        titleView.setSingleLine(true);
        titleView.setText(ZeroChillUi.sectionTitle(this, "ShitTok", 4, 7));
        bar.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView search = new ImageView(this);
        search.setImageResource(R.drawable.ic_nav_search);
        search.setPadding(dp(10), dp(10), dp(10), dp(10));
        search.setColorFilter(Color.WHITE);
        search.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        bar.addView(search, new LinearLayout.LayoutParams(dp(44), dp(48)));

        ImageView more = new ImageView(this);
        more.setImageResource(R.drawable.ic_more_overflow);
        more.setPadding(dp(10), dp(10), dp(10), dp(10));
        more.setColorFilter(Color.WHITE);
        more.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams moreParams =
                new LinearLayout.LayoutParams(dp(44), dp(48));
        moreParams.setMarginStart(dp(2));
        bar.addView(more, moreParams);

        shitTokReturnHeaderMask = bar;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                -1,
                ZeroChillUi.dimension(this, R.dimen.zc_top_bar_height),
                Gravity.TOP
        );
        shitTokTransitionHost.addView(bar, params);
    }

    private void clearCreatorHandoffPreview() {
        if (creatorHandoffPreview == null) return;
        shitTokTransitionHost.removeView(creatorHandoffPreview);
        creatorHandoffPreview = null;
    }

    private boolean creatorGalleryFirstFrameReady() {
        RecyclerView first = creatorTabRecyclers[CREATOR_TAB_ALL];
        BunkrGalleryAdapter galleryAdapter = creatorTabAdapters[CREATOR_TAB_ALL];
        if (first == null || galleryAdapter == null || first.getWidth() == 0) return false;
        if (galleryAdapter.size() == 0) return !loading;
        if (first.getChildCount() == 0) return false;
        View tile = first.getChildAt(0);
        if (!(tile instanceof ViewGroup)) return true;
        View image = ((ViewGroup) tile).getChildAt(0);
        return image instanceof ImageView
                && ((ImageView) image).getDrawable() != null
                && !(((ImageView) image).getDrawable() instanceof ColorDrawable);
    }

    private void refreshShitTokReturnSnapshot(int attempt) {
        if (shitTokReturnSnapshot == null || shitTokReturnTransition.isEmpty()) return;
        Bitmap snapshot = ShitTokTransitionSnapshotStore.snapshot(shitTokReturnTransition);
        if (snapshot != null && !snapshot.isRecycled()) {
            shitTokReturnSnapshot.setImageBitmap(snapshot);
            return;
        }
        if (attempt < 8) {
            shitTokReturnSnapshot.postDelayed(
                    () -> refreshShitTokReturnSnapshot(attempt + 1),
                    40L
            );
        }
    }

    static float shitTokReturnTranslation(float dx, float width) {
        if (width <= 0f) return 0f;
        return Math.max(0f, Math.min(width, dx));
    }

    static boolean shouldCommitShitTokReturn(float dx, float dy, float threshold) {
        return threshold > 0f
                && dx >= threshold
                && Math.abs(dx) > Math.abs(dy) * 1.15f;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event != null && event.getActionMasked() == MotionEvent.ACTION_DOWN
                && creatorHandoffPreview != null) {
            clearCreatorHandoffPreview();
        }
        if (event != null
                && event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN
                && shitTokReturnTracking) {
            cancelShitTokReturnGesture();
            return true;
        }
        if (!canUseShitTokReturnGesture(event)) {
            return super.dispatchTouchEvent(event);
        }

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            shitTokReturnDownX = event.getX();
            shitTokReturnDownY = event.getY();
            shitTokReturnTracking = false;
            return super.dispatchTouchEvent(event);
        }

        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            if (shitTokReturnTracking) cancelShitTokReturnGesture();
            return super.dispatchTouchEvent(event);
        }

        float dx = event.getX() - shitTokReturnDownX;
        float dy = event.getY() - shitTokReturnDownY;

        if (action == MotionEvent.ACTION_MOVE && !shitTokReturnTracking) {
            if (dx > dp(12) && Math.abs(dx) > Math.abs(dy) * 1.15f) {
                shitTokReturnTracking = true;
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
                if (creatorTabsPager != null) creatorTabsPager.setUserInputEnabled(false);
                if (refresh != null) refresh.setEnabled(false);
            } else {
                return super.dispatchTouchEvent(event);
            }
        }

        if (shitTokReturnTracking && action == MotionEvent.ACTION_MOVE) {
            shitTokTransitionContent.animate().cancel();
            shitTokTransitionContent.setTranslationX(
                    shitTokReturnTranslation(dx, shitTokTransitionContent.getWidth())
            );
            return true;
        }

        if (shitTokReturnTracking &&
                (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
            float width = Math.max(1f, shitTokTransitionContent.getWidth());
            float threshold = Math.max(dp(72), width * 0.18f);
            boolean finish = action == MotionEvent.ACTION_UP
                    && shouldCommitShitTokReturn(dx, dy, threshold);
            shitTokReturnTracking = false;
            if (finish) {
                finishShitTokReturnGesture(width);
            } else {
                cancelShitTokReturnGesture();
            }
            return true;
        }

        return shitTokReturnTracking || super.dispatchTouchEvent(event);
    }

    private boolean canUseShitTokReturnGesture(MotionEvent event) {
        if (!isCreatorGallery()
                || shitTokReturnTransition.isEmpty()
                || shitTokTransitionContent == null
                || shitTokReturnFinishing
                || event == null) {
            return false;
        }
        if (event.getPointerCount() > 1) return false;
        return activeCreatorTab() == CREATOR_TAB_ALL;
    }

    private void cancelShitTokReturnGesture() {
        if (shitTokTransitionContent == null) return;
        shitTokReturnTracking = false;
        shitTokTransitionContent.animate().cancel();
        shitTokTransitionContent.animate()
                .translationX(0f)
                .setDuration(ZeroChillMotion.QUICK_MS)
                .withEndAction(this::restoreShitTokReturnInput)
                .start();
    }

    private void finishShitTokReturnGesture(float width) {
        if (shitTokTransitionContent == null) {
            finishToShitTok();
            return;
        }
        shitTokReturnFinishing = true;
        float progress = Math.max(
                0f,
                Math.min(1f, shitTokTransitionContent.getTranslationX() / width)
        );
        long duration = Math.max(80L, Math.min(170L, Math.round((1f - progress) * 170f)));
        shitTokTransitionContent.animate().cancel();
        shitTokTransitionContent.animate()
                .translationX(width)
                .setDuration(duration)
                .withEndAction(this::finishToShitTok)
                .start();
    }

    private void restoreShitTokReturnInput() {
        if (creatorTabsPager != null) creatorTabsPager.setUserInputEnabled(true);
        if (refresh != null) refresh.setEnabled(true);
    }

    private void finishToShitTok() {
        if (isFinishing()) return;
        finish();
        if (!shitTokReturnTransition.isEmpty()) overridePendingTransition(0, 0);
    }

    @Override
    public void onBackPressed() {
        finishToShitTok();
    }

    private RecyclerView createRecycler() {
        RecyclerView next = new RecyclerView(this);
        next.setBackgroundColor(Color.BLACK);
        next.setClipToPadding(false);
        next.setPadding(0, dp(5), 0, dp(18));
        next.setItemAnimator(null);
        return next;
    }

    private BunkrGalleryAdapter createBunkrGalleryAdapter(boolean adaptiveAspectRatios) {
        BunkrGalleryAdapter galleryAdapter = new BunkrGalleryAdapter(
                this,
                new BunkrGalleryAdapter.Listener() {
                    @Override
                    public void onOpen(int position, NativeContentItem item) {
                        if (creatorAvatarPickerMode) {
                            pickCreatorAvatar(item);
                            return;
                        }
                        openBunkrGallery(position, item);
                    }

                    @Override
                    public void onLongPress(NativeContentItem item, View anchor) {
                        if (creatorAvatarPickerMode) return;
                        if (isCreatorGallery()) {
                            anchor.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.LONG_PRESS
                            );
                            GalleryMediaDownloader.download(
                                    NativeFeedBrowserActivity.this,
                                    io,
                                    item,
                                    "",
                                    "",
                                    null
                            );
                        } else {
                            showItemMenu(item, anchor);
                        }
                    }
                },
                adaptiveAspectRatios
        );
        if (isCreatorGallery() && !notificationFreshUrls.isEmpty()) {
            galleryAdapter.setHighlightedUrls(notificationFreshUrls);
        }
        return galleryAdapter;
    }

    private void buildCreatorTabs() {
        creatorGalleryColumns = savedCreatorGalleryColumnCount();
        for (int index = 0; index < CREATOR_TAB_COUNT; index++) {
            creatorTabAdapters[index] = createBunkrGalleryAdapter(true);
        }
        bunkrGalleryAdapter = creatorTabAdapters[CREATOR_TAB_ALL];

        creatorTabsPager = new ViewPager2(this);
        creatorTabsPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        creatorTabsPager.setOffscreenPageLimit(CREATOR_TAB_COUNT - 1);
        creatorTabsPager.setAdapter(new CreatorTabsPagerAdapter());
        refresh.addView(creatorTabsPager, new SwipeRefreshLayout.LayoutParams(-1, -1));
        refresh.setOnChildScrollUpCallback((parent, child) -> {
            RecyclerView active = activeCreatorRecycler();
            return active != null && active.canScrollVertically(-1);
        });

        creatorTabsMediator = new TabLayoutMediator(
                creatorTabs,
                creatorTabsPager,
                (tab, position) -> tab.setText(position == CREATOR_TAB_PICTURES
                        ? "Pictures"
                        : position == CREATOR_TAB_VIDEOS ? "Videos" : "All")
        );
        creatorTabsMediator.attach();
        if (creatorAvatarPickerMode) {
            creatorTabsPager.setCurrentItem(CREATOR_TAB_PICTURES, false);
        }
        updateCreatorTabLabels();
        creatorTabsPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                RecyclerView active = activeCreatorRecycler();
                if (active != null) {
                    recycler = active;
                    BunkrGalleryAdapter activeAdapter = activeCreatorAdapter();
                    if (activeAdapter != null) {
                        int[] range = visibleRange(active.getLayoutManager());
                        activeAdapter.preloadVisible(range[0], range[1]);
                    }
                }
                updateCreatorEmptyState();
                creatorTabsPager.postDelayed(() -> {
                    if (isFinishing()) return;
                    RecyclerView current = activeCreatorRecycler();
                    if (current != null) recycler = current;
                    OledImmersiveUiController.attachBrowser(NativeFeedBrowserActivity.this);
                    FeedMotionController.attach(NativeFeedBrowserActivity.this);
                }, 80L);
            }
        });
    }

    private void attachFeedScrollListener(RecyclerView list) {
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                int[] range = visibleRange(view.getLayoutManager());
                adapter.preloadVisible(range[0], range[1]);
                if (dy > 0 && !loading && !endReached &&
                        range[1] >= Math.max(0, adapter.getItemCount() - 5)) {
                    load(true);
                }
            }
        });
    }

    private void attachGalleryScrollListener(
            RecyclerView list,
            BunkrGalleryAdapter galleryAdapter
    ) {
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                int[] range = visibleRange(view.getLayoutManager());
                galleryAdapter.preloadVisible(range[0], range[1]);
                if (dy > 0 && !loading && !endReached &&
                        range[1] >= Math.max(0, galleryAdapter.getItemCount() - 5)) {
                    load(true);
                }
            }
        });
    }

    private int[] visibleRange(RecyclerView.LayoutManager manager) {
        if (manager instanceof StaggeredGridLayoutManager) {
            StaggeredGridLayoutManager staggered = (StaggeredGridLayoutManager) manager;
            int[] first = staggered.findFirstVisibleItemPositions(null);
            int[] last = staggered.findLastVisibleItemPositions(null);
            return new int[]{minimumPosition(first), maximumPosition(last)};
        }
        if (manager instanceof LinearLayoutManager) {
            LinearLayoutManager linear = (LinearLayoutManager) manager;
            return new int[]{
                    Math.max(0, linear.findFirstVisibleItemPosition()),
                    Math.max(0, linear.findLastVisibleItemPosition())
            };
        }
        return new int[]{0, 0};
    }

    private int minimumPosition(int[] positions) {
        int result = Integer.MAX_VALUE;
        if (positions != null) {
            for (int position : positions) {
                if (position != RecyclerView.NO_POSITION) result = Math.min(result, position);
            }
        }
        return result == Integer.MAX_VALUE ? 0 : result;
    }

    private int maximumPosition(int[] positions) {
        int result = 0;
        if (positions != null) {
            for (int position : positions) result = Math.max(result, position);
        }
        return result;
    }

    private void reload() {
        generation++;
        fapelloFailureShown = false;
        currentPage = 0;
        loading = false;
        endReached = false;
        empty.setVisibility(View.GONE);
        if (isBunkr()) {
            replaceBunkrItems(new ArrayList<>());
            bunkrGallerySessionId = isCreatorGallery()
                    ? BunkrGallerySessionStore.createCreator(
                            title,
                            baseUrl,
                            creatorGalleryCacheKey
                    )
                    : BunkrGallerySessionStore.create(title, baseUrl);
            if (isCreatorGallery()) {
                creatorGalleryRepository.reset(
                        bunkrGallerySessionId,
                        creatorQuery,
                        fapelloProfileUrl,
                        title,
                        creatorSeedNames,
                        creatorSeedUrls,
                        creatorSeedImages
                );
            }
        } else {
            adapter.replace(new ArrayList<>());
        }
        updateShowDetailsHeader();
        load(false);
    }

    private void load(boolean append) {
        if (restoringBrowser || loading || endReached) return;
        loading = true;
        int requestPage = append ? currentPage + 1 : 1;
        int requestGeneration = generation;
        String requestSession = bunkrGallerySessionId;
        if (!append && itemCount() == 0) {
            progress.setVisibility(View.VISIBLE);
            if (gallerySkeleton != null) gallerySkeleton.setVisibility(View.VISIBLE);
        }

        io.execute(() -> {
            try {
                List<NativeContentItem> result;
                BunkrCreatorGalleryRepository.Batch creatorBatch = null;
                List<NativeContentItem> warmedShow = !append && showDetailsMode
                        ? ShowsCollectionWarmCache.get(baseUrl)
                        : null;
                if (warmedShow != null && !warmedShow.isEmpty()) {
                    result = warmedShow;
                } else if (memeMode) {
                    result = memeRepository.fetch(this, requestPage);
                } else if (isCreatorGallery()) {
                    creatorBatch = creatorGalleryRepository.fetchNext(
                            this,
                            requestSession,
                            creatorQuery,
                            fapelloProfileUrl,
                            title,
                            creatorSeedNames,
                            creatorSeedUrls,
                            creatorSeedImages,
                            items -> {
                                BunkrGallerySessionStore.appendPreview(requestSession, items);
                                runOnUiThread(() -> {
                                if (requestGeneration != generation || isFinishing() || isDestroyed()) return;
                                appendBunkrItems(items);
                                if (itemCount() > 0) {
                                    if (progress instanceof ZeroChillLoadingView) {
                                        ((ZeroChillLoadingView) progress).finish();
                                    }
                                    if (gallerySkeleton != null) gallerySkeleton.setVisibility(View.GONE);
                                    empty.setVisibility(View.GONE);
                                }
                                });
                            }
                    );
                    result = creatorBatch.items;
                } else if (isBunkr()) {
                    result = bunkrRepository.fetchAlbum(this, baseUrl, requestPage);
                } else if (isEfukt()) {
                    result = efuktRepository.fetchSeriesFeed(this, baseUrl, requestPage);
                } else if (isKaotic()) {
                    result = webVideoRepository.fetchFeed(
                            this,
                            WebVideoSourceRepository.Source.KAOTIC,
                            baseUrl,
                            requestPage
                    );
                } else {
                    result = repository.fetchFeed(this, baseUrl, requestPage);
                }
                BunkrCreatorGalleryRepository.Batch completedCreatorBatch = creatorBatch;
                runOnUiThread(() -> {
                    if (requestGeneration != generation || isFinishing() || isDestroyed()) return;
                    loading = false;
                    progress.setVisibility(View.GONE);
                    if (gallerySkeleton != null) gallerySkeleton.setVisibility(View.GONE);
                    refresh.setRefreshing(false);
                    int before = itemCount();
                    BunkrGallerySessionStore.Snapshot currentCreator = isCreatorGallery()
                            ? BunkrGallerySessionStore.snapshot(requestSession) : null;
                    if (currentCreator != null) {
                        if (itemCount() != currentCreator.items.size()) {
                            replaceBunkrItems(currentCreator.items);
                        }
                        currentPage = currentCreator.currentPage;
                    } else if (isBunkr()) {
                        if (append) appendBunkrItems(result);
                        else replaceBunkrItems(result);
                    } else if (append) {
                        adapter.append(result);
                    } else {
                        adapter.replace(result);
                    }
                    int added = itemCount() - before;
                    if (currentCreator == null && !result.isEmpty() && (!append || added > 0)) currentPage = requestPage;
                    if (isCreatorGallery()) {
                        endReached = currentCreator != null ? currentCreator.endReached
                                : completedCreatorBatch == null || completedCreatorBatch.endReached;
                    } else if (result.isEmpty() || (append && added == 0) || isEfukt()) {
                        endReached = true;
                    }
                    updateShowDetailsHeader();
                    if (isBunkr() && !isCreatorGallery()) {
                        if (append) {
                            BunkrGallerySessionStore.append(
                                    bunkrGallerySessionId,
                                    result,
                                    currentPage,
                                    endReached
                            );
                        } else {
                            BunkrGallerySessionStore.replace(
                                    bunkrGallerySessionId,
                                    result,
                                    currentPage,
                                    endReached
                            );
                        }
                    }
                    persistBrowser();
                    restoreScrollPositions();
                    empty.setVisibility(View.GONE);
                    if (isCreatorGallery()) {
                        updateCreatorEmptyState();
                        showFapelloFailure(completedCreatorBatch == null
                                ? null
                                : completedCreatorBatch.fapelloFailure);
                    } else if (itemCount() == 0) {
                        empty.setText(isCreatorGallery()
                                ? "No matching pictures or videos loaded.\nTap to try again."
                                : isBunkr()
                                ? "No supported pictures or videos were found.\nTap to open the album."
                                : "Couldn't render this feed natively.\nTap to open the website.");
                        empty.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (requestGeneration != generation || isFinishing() || isDestroyed()) return;
                    loading = false;
                    progress.setVisibility(View.GONE);
                    if (gallerySkeleton != null) gallerySkeleton.setVisibility(View.GONE);
                    refresh.setRefreshing(false);
                    if (isCreatorGallery()) {
                        updateCreatorEmptyState();
                        FapelloSourceException failure = fapelloFailure(e);
                        if (failure != null && itemCount() == 0) {
                            empty.setText(failure.userMessage() + "\nTap to try again.");
                            empty.setVisibility(View.VISIBLE);
                        }
                        if (itemCount() > 0) {
                            Toast.makeText(
                                    this,
                                    "Couldn't load more right now.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    } else if (itemCount() == 0) {
                        empty.setText(isCreatorGallery()
                                ? "Couldn't build this creator gallery.\nTap to try again."
                                : "Couldn't load this feed.\nTap to open the website.");
                        empty.setVisibility(View.VISIBLE);
                    } else {
                        Toast.makeText(this, "Couldn't load more right now.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showFapelloFailure(FapelloSourceException failure) {
        if (failure == null || fapelloFailureShown) return;
        fapelloFailureShown = true;
        if (itemCount() == 0) {
            empty.setText(failure.userMessage() + "\nTap to try again.");
            empty.setVisibility(View.VISIBLE);
        } else {
            Toast.makeText(this, failure.userMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private FapelloSourceException fapelloFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof FapelloSourceException) {
                return (FapelloSourceException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private void replaceBunkrItems(List<NativeContentItem> items) {
        if (!isCreatorGallery()) {
            if (bunkrGalleryAdapter != null) bunkrGalleryAdapter.replace(items);
            return;
        }
        List<NativeContentItem> prioritized = prioritizeNotificationItems(
                items,
                notificationFreshUrls
        );
        int activeTab = activeCreatorTab();
        for (int tab = 0; tab < CREATOR_TAB_COUNT; tab++) {
            if (creatorTabAdapters[tab] != null) {
                creatorTabAdapters[tab].replace(
                        filterCreatorItems(prioritized, tab),
                        tab == activeTab
                );
            }
        }
        updateCreatorTabLabels();
        maybeFocusNotificationFresh(prioritized);
    }

    private void appendBunkrItems(List<NativeContentItem> items) {
        if (!isCreatorGallery()) {
            if (bunkrGalleryAdapter != null) bunkrGalleryAdapter.append(items);
            return;
        }
        if (!notificationFreshUrls.isEmpty() && bunkrGalleryAdapter != null) {
            ArrayList<NativeContentItem> combined = bunkrGalleryAdapter.snapshot();
            if (items != null) combined.addAll(items);
            replaceBunkrItems(combined);
            return;
        }
        int activeTab = activeCreatorTab();
        for (int tab = 0; tab < CREATOR_TAB_COUNT; tab++) {
            if (creatorTabAdapters[tab] != null) {
                creatorTabAdapters[tab].append(
                        filterCreatorItems(items, tab),
                        tab == activeTab
                );
            }
        }
        updateCreatorTabLabels();
    }

    static List<NativeContentItem> prioritizeNotificationItems(
            List<NativeContentItem> items,
            List<String> freshUrls
    ) {
        ArrayList<NativeContentItem> prioritized = new ArrayList<>();
        if (items == null || items.isEmpty()) return prioritized;
        if (freshUrls == null || freshUrls.isEmpty()) {
            prioritized.addAll(items);
            return prioritized;
        }

        LinkedHashSet<String> added = new LinkedHashSet<>();
        for (String freshUrl : freshUrls) {
            if (freshUrl == null || freshUrl.trim().isEmpty()) continue;
            for (NativeContentItem item : items) {
                if (item == null || item.url == null) continue;
                if (freshUrl.trim().equals(item.url) && added.add(item.url)) {
                    prioritized.add(item);
                    break;
                }
            }
        }
        for (NativeContentItem item : items) {
            if (item == null || item.url == null || item.url.isEmpty()) continue;
            if (added.add(item.url)) prioritized.add(item);
        }
        return prioritized;
    }

    private void maybeFocusNotificationFresh(List<NativeContentItem> items) {
        if (!notificationFreshPendingFocus || items == null || items.isEmpty()) return;
        boolean found = false;
        for (NativeContentItem item : items) {
            if (item != null && notificationFreshUrlSet.contains(item.url)) {
                found = true;
                break;
            }
        }
        if (!found) return;
        notificationFreshPendingFocus = false;
        if (creatorTabsPager == null) return;
        creatorTabsPager.post(() -> {
            creatorTabsPager.setCurrentItem(CREATOR_TAB_ALL, false);
            RecyclerView all = creatorTabRecyclers[CREATOR_TAB_ALL];
            if (all != null) all.scrollToPosition(0);
        });
    }

    private ArrayList<NativeContentItem> filterCreatorItems(
            List<NativeContentItem> items,
            int tab
    ) {
        ArrayList<NativeContentItem> filtered = new ArrayList<>();
        if (items == null) return filtered;
        for (NativeContentItem item : items) {
            if (item == null) continue;
            if (tab == CREATOR_TAB_PICTURES && !item.isImage()) continue;
            if (tab == CREATOR_TAB_VIDEOS && !item.isVideo()) continue;
            filtered.add(item);
        }
        return filtered;
    }

    private int activeCreatorTab() {
        if (creatorTabsPager == null) return CREATOR_TAB_ALL;
        return Math.max(
                CREATOR_TAB_ALL,
                Math.min(CREATOR_TAB_VIDEOS, creatorTabsPager.getCurrentItem())
        );
    }

    private RecyclerView activeCreatorRecycler() {
        return creatorTabRecyclers[activeCreatorTab()];
    }

    private BunkrGalleryAdapter activeCreatorAdapter() {
        return creatorTabAdapters[activeCreatorTab()];
    }

    private void updateCreatorTabLabels() {
        if (creatorTabs == null) return;
        String[] labels = {"All", "Pictures", "Videos"};
        for (int tab = 0; tab < CREATOR_TAB_COUNT; tab++) {
            TabLayout.Tab target = creatorTabs.getTabAt(tab);
            if (target == null) continue;
            BunkrGalleryAdapter galleryAdapter = creatorTabAdapters[tab];
            int count = galleryAdapter == null ? 0 : galleryAdapter.getItemCount();
            target.setText(count > 0 ? labels[tab] + "  " + count : labels[tab]);
        }
    }

    private void updateCreatorEmptyState() {
        if (!isCreatorGallery() || empty == null) return;
        BunkrGalleryAdapter active = activeCreatorAdapter();
        if (active != null && active.getItemCount() > 0) {
            empty.setVisibility(View.GONE);
            return;
        }

        int tab = activeCreatorTab();
        if (loading) {
            // The branded loader owns the initial label. If another tab has media
            // and this tab is still empty, show its status after the loader exits.
            if (progress != null && progress.getVisibility() == View.VISIBLE) {
                empty.setVisibility(View.GONE);
            } else {
                empty.setText(tab == CREATOR_TAB_PICTURES ? "Loading pictures..."
                        : tab == CREATOR_TAB_VIDEOS ? "Loading videos..." : "Loading gallery...");
                empty.setVisibility(View.VISIBLE);
            }
            return;
        }
        if (itemCount() == 0) {
            empty.setText(endReached
                    ? "No matching pictures or videos were found.\nTap to try again."
                    : "No matching pictures or videos loaded.\nTap to try again.");
        } else if (tab == CREATOR_TAB_PICTURES) {
            empty.setText(endReached
                    ? "No pictures were found for this creator."
                    : "No pictures loaded yet.\nTap to load more.");
        } else if (tab == CREATOR_TAB_VIDEOS) {
            empty.setText(endReached
                    ? "No videos were found for this creator."
                    : "No videos loaded yet.\nTap to load more.");
        } else {
            empty.setText("No media loaded yet.\nTap to try again.");
        }
        empty.setVisibility(View.VISIBLE);
    }

    private void openVideo(NativeContentItem item) {
        progress.setVisibility(View.VISIBLE);
        final int requestGeneration = generation;
        io.execute(() -> {
            CrazyShitRepository.StreamInfo stream = null;
            try {
                stream = PlayableSourceRouter.resolve(this, item);
            } catch (Exception ignored) {
            }
            CrazyShitRepository.StreamInfo resolved = stream;
            runOnUiThread(() -> {
                if (requestGeneration != generation || isFinishing()) return;
                progress.setVisibility(View.GONE);
                if (resolved == null || resolved.mediaUrl == null || resolved.mediaUrl.isEmpty()) {
                    Toast.makeText(
                            this,
                            "Couldn't resolve this video natively right now.",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }
                Intent intent = new Intent(this, VideoDetailActivity.class);
                intent.putExtra(PlayerActivity.EXTRA_MEDIA_URL, resolved.mediaUrl);
                intent.putExtra(PlayerActivity.EXTRA_PAGE_URL, resolved.pageUrl);
                intent.putExtra(PlayerActivity.EXTRA_TITLE, item.title);
                intent.putExtra(VideoDetailActivity.EXTRA_VIEWS, item.views);
                intent.putExtra(VideoDetailActivity.EXTRA_UPLOADER, item.uploader);
                intent.putExtra(VideoDetailActivity.EXTRA_COMMENTS, item.comments);
                intent.putExtra(VideoDetailActivity.EXTRA_RELATED_FEED_URL, baseUrl);
                intent.putExtra(VideoDetailActivity.EXTRA_SOURCE, source);
                if (item.imageUrl != null && !item.imageUrl.trim().isEmpty()) {
                    intent.putExtra(VideoDetailActivity.EXTRA_POSTER_URL, item.imageUrl);
                }
                intent.putExtra(VideoDetailActivity.EXTRA_MEDIA_REFERER, resolved.requestReferer);
                try {
                    intent.putExtra(PlayerActivity.EXTRA_USER_AGENT, WebSettings.getDefaultUserAgent(this));
                } catch (Exception ignored) {
                }
                try {
                    String cookies = CookieManager.getInstance().getCookie(resolved.mediaUrl);
                    if ((cookies == null || cookies.isEmpty()) && resolved.pageUrl != null) {
                        cookies = CookieManager.getInstance().getCookie(resolved.pageUrl);
                    }
                    if (cookies != null) intent.putExtra(PlayerActivity.EXTRA_COOKIES, cookies);
                } catch (Exception ignored) {
                }
                startActivity(intent);
            });
        });
    }

    private void openMeme(NativeContentItem item) {
        Intent intent = new Intent(this, MemeViewerActivity.class);
        intent.putExtra(MemeViewerActivity.EXTRA_TITLE, item.title);
        intent.putExtra(MemeViewerActivity.EXTRA_PAGE_URL, item.url);
        intent.putExtra(MemeViewerActivity.EXTRA_IMAGE_URL, item.imageUrl);
        startActivity(intent);
    }

    private void pickCreatorAvatar(NativeContentItem item) {
        if (item == null || !item.isImage()) {
            if (creatorTabsPager != null) {
                creatorTabsPager.setCurrentItem(CREATOR_TAB_PICTURES, true);
            }
            Toast.makeText(this, "Choose a picture for the avatar.", Toast.LENGTH_SHORT).show();
            return;
        }
        String imageUrl = value(item.imageUrl, "");
        if (imageUrl.isEmpty()) imageUrl = value(item.url, "");
        if (!isRemoteUrl(imageUrl)) {
            Toast.makeText(this, "That picture cannot be used as an avatar.", Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        String referer = creatorAvatarReferer(item);
        startActivityForResult(
                CreatorAvatarCropActivity.create(this, imageUrl, referer),
                REQUEST_CREATOR_AVATAR_CROP
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CREATOR_AVATAR_CROP
                || resultCode != Activity.RESULT_OK
                || data == null) return;

        String imageUrl = value(data.getStringExtra(CreatorAvatarCropActivity.EXTRA_IMAGE_URL), "");
        String referer = value(data.getStringExtra(CreatorAvatarCropActivity.EXTRA_REFERER), "");
        if (!isRemoteUrl(imageUrl)) return;

        Intent result = new Intent()
                .putExtra(EXTRA_PICKED_AVATAR_URL, imageUrl)
                .putExtra(EXTRA_PICKED_AVATAR_REFERER, referer)
                .putExtra(
                        EXTRA_PICKED_AVATAR_FOCUS_X,
                        data.getFloatExtra(
                                CreatorAvatarCropActivity.EXTRA_FOCUS_X,
                                CreatorAvatarOverrideStore.DEFAULT_FOCUS
                        )
                )
                .putExtra(
                        EXTRA_PICKED_AVATAR_FOCUS_Y,
                        data.getFloatExtra(
                                CreatorAvatarCropActivity.EXTRA_FOCUS_Y,
                                CreatorAvatarOverrideStore.DEFAULT_FOCUS
                        )
                )
                .putExtra(
                        EXTRA_PICKED_AVATAR_ZOOM,
                        data.getFloatExtra(
                                CreatorAvatarCropActivity.EXTRA_ZOOM,
                                CreatorAvatarOverrideStore.DEFAULT_ZOOM
                        )
                );
        setResult(Activity.RESULT_OK, result);
        finish();
    }

    private String creatorAvatarReferer(NativeContentItem item) {
        if (item == null) return "";
        if (WikiFeetRepository.isWikiFeetUrl(item.url)
                && WikiFeetRepository.isWikiFeetUrl(item.uploader)) {
            return value(item.uploader, "");
        }
        if (!FapelloRepository.isPostUrl(item.url)
                && FapelloRepository.isModelUrl(item.uploader)) {
            return value(item.uploader, "");
        }
        if (OnlyHavenRepository.isOnlyHavenUrl(item.uploader)) {
            return value(item.uploader, "");
        }
        return value(item.url, "");
    }

    private boolean isRemoteUrl(String url) {
        if (url == null) return false;
        String clean = url.trim().toLowerCase(java.util.Locale.US);
        return clean.startsWith("https://") || clean.startsWith("http://");
    }

    private void openBunkrGallery(int position, NativeContentItem item) {
        if (item == null || bunkrGalleryAdapter == null) return;
        if (!isCreatorGallery()) BunkrGallerySessionStore.replace(
                bunkrGallerySessionId,
                bunkrGalleryAdapter.snapshot(),
                currentPage,
                endReached
        );
        Intent intent = new Intent(this, BunkrGalleryActivity.class);
        intent.putExtra(BunkrGalleryActivity.EXTRA_SESSION_ID, bunkrGallerySessionId);
        intent.putExtra(BunkrGalleryActivity.EXTRA_TITLE, title);
        intent.putExtra(BunkrGalleryActivity.EXTRA_ALBUM_URL, baseUrl);
        intent.putExtra(BunkrGalleryActivity.EXTRA_CREATOR_QUERY, creatorQuery);
        intent.putExtra(BunkrGalleryActivity.EXTRA_FAPELLO_PROFILE_URL, fapelloProfileUrl);
        intent.putExtra(
                BunkrGalleryActivity.EXTRA_MEDIA_FILTER,
                !isCreatorGallery() || activeCreatorTab() == CREATOR_TAB_ALL
                        ? BunkrGalleryActivity.FILTER_ALL
                        : activeCreatorTab() == CREATOR_TAB_PICTURES
                        ? BunkrGalleryActivity.FILTER_PICTURES
                        : BunkrGalleryActivity.FILTER_VIDEOS
        );
        intent.putExtra(BunkrGalleryActivity.EXTRA_INITIAL_URL, item.url);
        intent.putExtra(BunkrGalleryActivity.EXTRA_INITIAL_POSITION, position);
        startActivity(intent);
    }

    private void showItemMenu(NativeContentItem item, View anchor) {
        if (isBunkr()) {
            PopupMenu menu = new PopupMenu(this, anchor);
            if (item.isVideo()) {
                menu.getMenu().add(Menu.NONE, 1, 0, "Download video");
            }
            menu.getMenu().add(Menu.NONE, 2, 1, "Share");
            menu.getMenu().add(Menu.NONE, 3, 2, "Open item page");
            menu.setOnMenuItemClickListener(clicked -> {
                if (clicked.getItemId() == 1) {
                    VideoDownloadStore.downloadPage(this, item);
                    return true;
                }
                if (clicked.getItemId() == 2) {
                    shareItem(item);
                    return true;
                }
                if (clicked.getItemId() == 3) {
                    openWebsite(item.url);
                    return true;
                }
                return false;
            });
            menu.show();
            return;
        }
        if (!memeMode) {
            showVideoItemMenu(item);
            return;
        }
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(Menu.NONE, 2, 1, "Share");
        menu.getMenu().add(Menu.NONE, 3, 2, "Video details");
        menu.setOnMenuItemClickListener(clicked -> {
            if (clicked.getItemId() == 2) {
                shareItem(item);
                return true;
            }
            if (clicked.getItemId() == 3) {
                openWebsite(item.url);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showVideoItemMenu(NativeContentItem item) {
        String saveTitle = FavoriteStore.contains(this, item.url)
                ? "Remove from Watch Later"
                : "Watch Later";
        ArrayList<VideoActionSheet.Action> actions = new ArrayList<>();
        if (item.isVideo() && item.url != null && !item.url.isEmpty()) {
            actions.add(VideoActionSheet.action(
                    R.drawable.ic_action_comments,
                    "Comments",
                    "Join the ZeroChill conversation",
                    () -> new InlineCommentsDialog(
                            this,
                            item.url,
                            item.title,
                            "",
                            null
                    ).show()
            ));
        }
        actions.add(VideoActionSheet.action(
                R.drawable.ic_action_share,
                "Share",
                "Send the video page",
                () -> shareItem(item)
        ));
        actions.add(VideoActionSheet.action(
                R.drawable.ic_more_account,
                "Video details",
                "Open the ZEROCHILL player, actions, and related videos",
                () -> openVideo(item)
        ));
        actions.add(VideoActionSheet.action(
                R.drawable.ic_more_website,
                "Open source website",
                "Open the original video page",
                () -> openWebsite(item.url)
        ));

        VideoActionSheet.show(
                this,
                item.title,
                VideoActionSheet.section(
                        "SAVE",
                        VideoActionSheet.action(
                                R.drawable.ic_action_download,
                                "Download",
                                "Save this video for offline playback",
                                () -> VideoDownloadStore.downloadPage(this, item)
                        ),
                        VideoActionSheet.action(
                                R.drawable.ic_more_library,
                                saveTitle,
                                "Keep this video in your library",
                                () -> toggleWatchLater(item)
                        )
                ),
                VideoActionSheet.section(
                        "ACTIONS",
                        actions.toArray(new VideoActionSheet.Action[0])
                )
        );
    }

    private void toggleWatchLater(NativeContentItem item) {
        if (FavoriteStore.contains(this, item.url)) {
            FavoriteStore.remove(this, item.url);
            Toast.makeText(this, "Removed from Watch Later.", Toast.LENGTH_SHORT).show();
        } else {
            FavoriteStore.add(this, item.title, item.url);
            Toast.makeText(this, "Saved to Watch Later.", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareItem(NativeContentItem item) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, item.url);
        share.putExtra(Intent.EXTRA_SUBJECT, item.title);
        startActivity(Intent.createChooser(share, "Share"));
    }

    private void showOptions(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        if (!isBunkr()) menu.getMenu().add(Menu.NONE, 1, 0, "View style");
        menu.getMenu().add(Menu.NONE, 2, 1, "Open website");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                showViewStyleDialog();
                return true;
            }
            if (item.getItemId() == 2) {
                openWebsite(baseUrl);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showViewStyleDialog() {
        String[] choices = {"Cards", "List", "Grid", "Posters"};
        int selected = viewMode();
        new AlertDialog.Builder(this)
                .setTitle("View style")
                .setSingleChoiceItems(choices, selected, (dialog, which) -> {
                    getSharedPreferences("app_prefs", MODE_PRIVATE)
                            .edit()
                            .putInt("native_view_collection", which)
                            .apply();
                    applyLayout();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int viewMode() {
        int mode = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getInt("native_view_collection", NativeFeedAdapter.VIEW_LIST);
        if (mode < NativeFeedAdapter.VIEW_CARDS || mode > NativeFeedAdapter.VIEW_POSTERS) {
            return NativeFeedAdapter.VIEW_LIST;
        }
        return mode;
    }

    private void applyLayout() {
        if (isCreatorGallery()) {
            for (RecyclerView creatorRecycler : creatorTabRecyclers) {
                if (creatorRecycler != null) applyCreatorGalleryLayout(creatorRecycler);
            }
            RecyclerView active = activeCreatorRecycler();
            if (active != null) recycler = active;
            return;
        }
        if (recycler == null || (adapter == null && bunkrGalleryAdapter == null)) return;
        RecyclerView.LayoutManager old = recycler.getLayoutManager();
        int position = 0;
        int offset = 0;
        if (old instanceof LinearLayoutManager) {
            LinearLayoutManager lm = (LinearLayoutManager) old;
            position = Math.max(0, lm.findFirstVisibleItemPosition());
            View anchor = lm.findViewByPosition(position);
            if (anchor != null) offset = anchor.getTop() - recycler.getPaddingTop();
        }

        if (isBunkr()) {
            Configuration config = getResources().getConfiguration();
            boolean landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
            int columns;
            if (landscape) columns = config.screenWidthDp >= 900 ? 7 : 5;
            else columns = config.screenWidthDp >= 600 ? 5 : 3;
            GridLayoutManager gallery = new GridLayoutManager(this, columns);
            recycler.setLayoutManager(gallery);
            if (bunkrGalleryAdapter.getItemCount() > 0) {
                int safe = Math.min(position, bunkrGalleryAdapter.getItemCount() - 1);
                gallery.scrollToPositionWithOffset(safe, offset);
            }
            return;
        }

        int mode = viewMode();
        adapter.setViewMode(mode);
        LinearLayoutManager next;
        if (mode == NativeFeedAdapter.VIEW_GRID || mode == NativeFeedAdapter.VIEW_POSTERS) {
            Configuration config = getResources().getConfiguration();
            boolean landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
            int columns = landscape && config.screenWidthDp >= 900 ? 3 : 2;
            GridLayoutManager grid = new GridLayoutManager(this, columns);
            grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int adapterPosition) {
                    return adapter.isSectionAt(adapterPosition) ? columns : 1;
                }
            });
            next = grid;
        } else {
            next = new LinearLayoutManager(this);
        }
        recycler.setLayoutManager(next);
        if (adapter.getItemCount() > 0) {
            int safe = Math.min(position, adapter.getItemCount() - 1);
            next.scrollToPositionWithOffset(safe, offset);
        }
    }

    private void applyCreatorGalleryLayout(RecyclerView list) {
        if (list == null) return;
        RecyclerView.LayoutManager old = list.getLayoutManager();
        int position = 0;
        int offset = 0;
        if (old != null) {
            int[] range = visibleRange(old);
            position = Math.max(0, range[0]);
            View anchor = old.findViewByPosition(position);
            if (anchor != null) offset = anchor.getTop() - list.getPaddingTop();
        }

        StaggeredGridLayoutManager gallery = new StaggeredGridLayoutManager(
                creatorGalleryColumnCount(),
                StaggeredGridLayoutManager.VERTICAL
        );
        gallery.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        list.setLayoutManager(gallery);
        updateCreatorGalleryOverlayDensity(list, gallery.getSpanCount());
        RecyclerView.Adapter<?> listAdapter = list.getAdapter();
        if (listAdapter != null && listAdapter.getItemCount() > 0) {
            int safe = Math.min(position, listAdapter.getItemCount() - 1);
            gallery.scrollToPositionWithOffset(safe, offset);
        }
    }

    private int creatorGalleryColumnCount() {
        if (creatorGalleryColumns > 0) {
            return clamp(
                    creatorGalleryColumns,
                    creatorGalleryMinimumColumns(),
                    creatorGalleryMaximumColumns()
            );
        }
        creatorGalleryColumns = savedCreatorGalleryColumnCount();
        return creatorGalleryColumns;
    }

    private int savedCreatorGalleryColumnCount() {
        int fallback = defaultCreatorGalleryColumnCount();
        int saved = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getInt(creatorGalleryPreferenceKey(), fallback);
        return clamp(saved, creatorGalleryMinimumColumns(), creatorGalleryMaximumColumns());
    }

    private int defaultCreatorGalleryColumnCount() {
        Configuration config = getResources().getConfiguration();
        boolean landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (landscape) return config.screenWidthDp >= 900 ? 7 : 5;
        return config.screenWidthDp >= 600 ? 4 : 2;
    }

    private int creatorGalleryMinimumColumns() {
        return getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2;
    }

    private int creatorGalleryMaximumColumns() {
        Configuration config = getResources().getConfiguration();
        if (config.screenWidthDp >= 900) return 8;
        if (config.screenWidthDp >= 600) return 7;
        return config.orientation == Configuration.ORIENTATION_LANDSCAPE ? 7 : 5;
    }

    private String creatorGalleryPreferenceKey() {
        Configuration config = getResources().getConfiguration();
        String size = config.screenWidthDp >= 600 ? "tablet" : "phone";
        String orientation = config.orientation == Configuration.ORIENTATION_LANDSCAPE
                ? "wide"
                : "tall";
        return "creator_gallery_columns_" + size + "_" + orientation;
    }

    private void changeCreatorGalleryColumns(int delta) {
        int next = clamp(
                creatorGalleryColumnCount() + delta,
                creatorGalleryMinimumColumns(),
                creatorGalleryMaximumColumns()
        );
        if (next == creatorGalleryColumns) return;
        creatorGalleryColumns = next;
        getSharedPreferences("app_prefs", MODE_PRIVATE)
                .edit()
                .putInt(creatorGalleryPreferenceKey(), next)
                .apply();

        for (RecyclerView creatorRecycler : creatorTabRecyclers) {
            if (creatorRecycler == null) continue;
            RecyclerView.LayoutManager manager = creatorRecycler.getLayoutManager();
            updateCreatorGalleryOverlayDensity(creatorRecycler, next);
            if (manager instanceof StaggeredGridLayoutManager) {
                StaggeredGridLayoutManager grid = (StaggeredGridLayoutManager) manager;
                if (grid.getSpanCount() != next) grid.setSpanCount(next);
            } else {
                applyCreatorGalleryLayout(creatorRecycler);
            }
        }
    }

    private void attachCreatorGalleryPinch(RecyclerView list) {
        final float[] accumulatedScale = {1f};
        final int[] startColumns = {creatorGalleryColumnCount()};
        final int[] focusPosition = {RecyclerView.NO_POSITION};
        final int[] focusOffset = {0};
        final boolean[] pinching = {false};
        ScaleGestureDetector detector = new ScaleGestureDetector(
                this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector scaleDetector) {
                        accumulatedScale[0] = 1f;
                        startColumns[0] = creatorGalleryColumnCount();
                        list.animate().cancel();
                        list.stopScroll();
                        View focus = list.findChildViewUnder(
                                scaleDetector.getFocusX(), scaleDetector.getFocusY());
                        focusPosition[0] = focus == null ? RecyclerView.NO_POSITION
                                : list.getChildAdapterPosition(focus);
                        focusOffset[0] = focus == null ? 0 : focus.getTop() - list.getPaddingTop();
                        list.setPivotX(scaleDetector.getFocusX());
                        list.setPivotY(scaleDetector.getFocusY());
                        list.requestDisallowInterceptTouchEvent(true);
                        return true;
                    }

                    @Override
                    public boolean onScale(ScaleGestureDetector scaleDetector) {
                        accumulatedScale[0] = Math.max(0.38f, Math.min(3f,
                                accumulatedScale[0] * scaleDetector.getScaleFactor()));
                        float density = Math.max(creatorGalleryMinimumColumns(),
                                Math.min(creatorGalleryMaximumColumns(),
                                        startColumns[0] / accumulatedScale[0]));
                        int columns = Math.round(density);

                        // Keep the visual surface centered under the user's fingers while the
                        // underlying staggered grid changes density.
                        list.setPivotX(scaleDetector.getFocusX());
                        list.setPivotY(scaleDetector.getFocusY());

                        StaggeredGridLayoutManager grid =
                                (StaggeredGridLayoutManager) list.getLayoutManager();
                        if (grid != null && grid.getSpanCount() != columns) {
                            morphCreatorGridSpanChange(
                                    list,
                                    grid,
                                    columns,
                                    focusPosition[0],
                                    focusOffset[0]
                            );
                        }

                        // Scale the whole surface between integer span counts so thumbnail
                        // size follows the gesture continuously instead of stepping.
                        float remainder = columns / density;
                        list.setScaleX(remainder);
                        list.setScaleY(remainder);
                        return true;
                    }

                    @Override
                    public void onScaleEnd(ScaleGestureDetector scaleDetector) {
                        int next = clamp(Math.round(startColumns[0] / accumulatedScale[0]),
                                creatorGalleryMinimumColumns(), creatorGalleryMaximumColumns());
                        if (next != creatorGalleryColumns) {
                            changeCreatorGalleryColumns(next - creatorGalleryColumns);
                        }
                        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
                            list.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(CREATOR_GRID_RELEASE_DURATION_MS)
                                    .setInterpolator(new android.view.animation.PathInterpolator(
                                            0.20f, 0f, 0.05f, 1f))
                                    .start();
                        } else {
                            list.setScaleX(1f);
                            list.setScaleY(1f);
                        }
                        accumulatedScale[0] = 1f;
                    }
                }
        );

        list.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            private boolean scaling;

            @Override
            public boolean onInterceptTouchEvent(
                    @NonNull RecyclerView view,
                    @NonNull MotionEvent event
            ) {
                if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                    pinching[0] = true;
                    refresh.setEnabled(false);
                    if (creatorTabsPager != null) creatorTabsPager.setUserInputEnabled(false);
                    view.requestDisallowInterceptTouchEvent(true);
                }
                detector.onTouchEvent(event);
                if (pinching[0] || detector.isInProgress()) {
                    scaling = true;
                    view.requestDisallowInterceptTouchEvent(true);
                    if (event.getActionMasked() != MotionEvent.ACTION_UP &&
                            event.getActionMasked() != MotionEvent.ACTION_CANCEL) return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                        event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    scaling = false;
                    pinching[0] = false;
                    refresh.setEnabled(true);
                    if (creatorTabsPager != null) creatorTabsPager.setUserInputEnabled(true);
                    view.requestDisallowInterceptTouchEvent(false);
                }
                return scaling;
            }

            @Override
            public void onTouchEvent(
                    @NonNull RecyclerView view,
                    @NonNull MotionEvent event
            ) {
                detector.onTouchEvent(event);
                if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                        event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    scaling = false;
                    pinching[0] = false;
                    refresh.setEnabled(true);
                    if (creatorTabsPager != null) creatorTabsPager.setUserInputEnabled(true);
                    view.requestDisallowInterceptTouchEvent(false);
                }
            }
        });
    }

    private void updateCreatorGalleryOverlayDensity(RecyclerView list, int columns) {
        if (list == null) return;
        RecyclerView.Adapter<?> listAdapter = list.getAdapter();
        if (listAdapter instanceof BunkrGalleryAdapter) {
            ((BunkrGalleryAdapter) listAdapter).setGridColumns(columns);
        }
    }

    private void pulseCreatorGalleryMorph(RecyclerView list) {
        if (list == null) return;
        RecyclerView.Adapter<?> listAdapter = list.getAdapter();
        if (listAdapter instanceof BunkrGalleryAdapter) {
            ((BunkrGalleryAdapter) listAdapter).pulseGridMorph(list);
        }
    }

    private void morphCreatorGridSpanChange(
            RecyclerView list,
            StaggeredGridLayoutManager grid,
            int columns,
            int focusPosition,
            int focusOffset
    ) {
        if (list == null || grid == null || grid.getSpanCount() == columns) return;

        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) {
            updateCreatorGalleryOverlayDensity(list, columns);
            grid.setSpanCount(columns);
            if (focusPosition != RecyclerView.NO_POSITION) {
                grid.scrollToPositionWithOffset(focusPosition, focusOffset);
            }
            return;
        }

        java.util.HashMap<Integer, CreatorGridSnapshot> before = new java.util.HashMap<>();
        float oldListScaleX = Math.max(0.01f, list.getScaleX());
        float oldListScaleY = Math.max(0.01f, list.getScaleY());

        for (int index = 0; index < list.getChildCount(); index++) {
            View child = list.getChildAt(index);
            int position = list.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION) continue;

            int[] childLocation = new int[2];
            child.getLocationOnScreen(childLocation);
            float visualWidth = Math.max(
                    1f,
                    child.getWidth() * child.getScaleX() * oldListScaleX
            );
            float visualHeight = Math.max(
                    1f,
                    child.getHeight() * child.getScaleY() * oldListScaleY
            );
            float screenCenterX = childLocation[0] + (visualWidth / 2f);
            float screenCenterY = childLocation[1] + (visualHeight / 2f);

            before.put(position, new CreatorGridSnapshot(
                    screenCenterX,
                    screenCenterY,
                    visualWidth
            ));

            // A fast second threshold crossing can interrupt the previous morph. Capture its
            // current visual position above, then clear old properties before the new layout.
            child.animate().cancel();
            child.setTranslationX(0f);
            child.setTranslationY(0f);
            child.setScaleX(1f);
            child.setScaleY(1f);
            child.setRotation(0f);
            child.setAlpha(1f);
        }

        updateCreatorGalleryOverlayDensity(list, columns);

        final android.view.ViewTreeObserver observer = list.getViewTreeObserver();
        observer.addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                android.view.ViewTreeObserver current = list.getViewTreeObserver();
                if (current.isAlive()) current.removeOnPreDrawListener(this);
                if (isFinishing() || isDestroyed() || list.getLayoutManager() != grid) {
                    return true;
                }

                float nextListScaleX = Math.max(0.01f, list.getScaleX());
                float nextListScaleY = Math.max(0.01f, list.getScaleY());
                android.view.animation.PathInterpolator curve =
                        new android.view.animation.PathInterpolator(0.20f, 0f, 0.05f, 1f);

                for (int index = 0; index < list.getChildCount(); index++) {
                    View child = list.getChildAt(index);
                    int position = list.getChildAdapterPosition(child);
                    CreatorGridSnapshot old = before.get(position);
                    if (old == null || child.getWidth() <= 0) continue;

                    int[] childLocation = new int[2];
                    child.getLocationOnScreen(childLocation);
                    float newVisualWidth = Math.max(1f, child.getWidth() * nextListScaleX);
                    float newVisualHeight = Math.max(1f, child.getHeight() * nextListScaleY);
                    float screenCenterX = childLocation[0] + (newVisualWidth / 2f);
                    float screenCenterY = childLocation[1] + (newVisualHeight / 2f);

                    float translationX =
                            (old.screenCenterX - screenCenterX) / nextListScaleX;
                    float translationY =
                            (old.screenCenterY - screenCenterY) / nextListScaleY;
                    float startScale = creatorGridMorphScale(
                            old.visualWidth,
                            newVisualWidth
                    );

                    // Apply the inverse transform before this new layout is ever drawn. The
                    // first rendered frame therefore matches the previous grid exactly.
                    child.setTranslationX(translationX);
                    child.setTranslationY(translationY);
                    child.setScaleX(startScale);
                    child.setScaleY(startScale);
                    child.setRotation(0f);
                    child.setAlpha(1f);
                    child.animate()
                            .translationX(0f)
                            .translationY(0f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(CREATOR_GRID_MORPH_DURATION_MS)
                            .setInterpolator(curve)
                            .start();
                }

                pulseCreatorGalleryMorph(list);
                return true;
            }
        });

        grid.setSpanCount(columns);
        if (focusPosition != RecyclerView.NO_POSITION) {
            grid.scrollToPositionWithOffset(focusPosition, focusOffset);
        }
        list.invalidate();
    }

    static float creatorGridMorphScale(float oldVisualWidth, float newVisualWidth) {
        if (oldVisualWidth <= 0f || newVisualWidth <= 0f) return 1f;
        return Math.max(0.76f, Math.min(1.24f, oldVisualWidth / newVisualWidth));
    }

    private static final class CreatorGridSnapshot {
        final float screenCenterX;
        final float screenCenterY;
        final float visualWidth;

        CreatorGridSnapshot(float screenCenterX, float screenCenterY, float visualWidth) {
            this.screenCenterX = screenCenterX;
            this.screenCenterY = screenCenterY;
            this.visualWidth = visualWidth;
        }
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void openWebsite(String url) {
        Intent intent = new Intent(this, WebFallbackActivity.class);
        intent.putExtra(WebFallbackActivity.EXTRA_URL, url);
        startActivity(intent);
    }

    private boolean isEfukt() {
        return SOURCE_EFUKT.equals(source) || EfuktRepository.isEfuktUrl(baseUrl);
    }

    private boolean isBunkr() {
        return SOURCE_BUNKR.equals(source) || BunkrRepository.isAlbumUrl(baseUrl);
    }

    private boolean isKaotic() {
        return SOURCE_KAOTIC.equals(source) || WebVideoSourceRepository.isKaoticUrl(baseUrl);
    }

    private boolean isCreatorGallery() {
        return creatorQuery != null && !creatorQuery.isEmpty();
    }

    private boolean supportsComments() {
        return true;
    }

    private String showSourceLabel() {
        if (isKaotic()) return "KAOTIC CATEGORY";
        if (isEfukt()) return "EFUKT SERIES";
        if (NativeContentItem.KIND_CATEGORY.equals(showKind)) return "CRAZYSHIT CATEGORY";
        return "CRAZYSHIT SHOW";
    }

    private void updateShowDetailsHeader() {
        if (showDetailsHeader != null) showDetailsHeader.setItemCount(itemCount());
    }

    private int itemCount() {
        return isBunkr()
                ? (bunkrGalleryAdapter == null ? 0 : bunkrGalleryAdapter.getItemCount())
                : (adapter == null ? 0 : adapter.getItemCount());
    }

    private void persistBrowser() {
        if (restoringBrowser) return;
        if (isBunkr()) { BunkrGallerySessionStore.persist(this, bunkrGallerySessionId); return; }
        try {
            org.json.JSONObject value = new org.json.JSONObject().put("page", currentPage).put("end", endReached)
                    .put("items", ContentItemCodec.encodeList(adapter.snapshot(), 2000));
            ScreenSnapshotStore.save(this, browserSnapshot, value);
        } catch (Exception ignored) { }
    }

    private void restoreBrowser() {
        restoringBrowser = true;
        io.execute(() -> {
            BunkrGallerySessionStore.Snapshot gallery = isBunkr()
                    ? BunkrGallerySessionStore.restore(this, bunkrGallerySessionId) : null;
            org.json.JSONObject feed = isBunkr() ? null : ScreenSnapshotStore.read(this, browserSnapshot);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                restoringBrowser = false;
                if (creatorTabsPager != null) creatorTabsPager.setCurrentItem(restoredBrowserState.getInt("tab", 0), false);
                if (gallery != null) {
                    replaceBunkrItems(gallery.items);
                    currentPage = gallery.currentPage; endReached = gallery.endReached;
                    if (gallery.items.isEmpty() && !endReached) { load(false); return; }
                } else if (feed != null) {
                    adapter.replace(ContentItemCodec.decodeList(feed.optJSONArray("items"), 2000));
                    currentPage = feed.optInt("page"); endReached = feed.optBoolean("end");
                    updateShowDetailsHeader();
                } else {
                    if (isBunkr()) {
                        bunkrGallerySessionId = isCreatorGallery()
                                ? BunkrGallerySessionStore.createCreator(
                                        title,
                                        baseUrl,
                                        creatorGalleryCacheKey
                                )
                                : BunkrGallerySessionStore.create(title, baseUrl);
                        if (isCreatorGallery()) creatorGalleryRepository.reset(
                                bunkrGallerySessionId,
                                creatorQuery,
                                fapelloProfileUrl,
                                title,
                                creatorSeedNames,
                                creatorSeedUrls,
                                creatorSeedImages
                        );
                    }
                    load(false); return;
                }
                progress.setVisibility(View.GONE);
                if (creatorTabsPager != null) creatorTabsPager.setCurrentItem(restoredBrowserState.getInt("tab", 0), false);
                restoreScrollPositions();
                updateCreatorEmptyState();
            });
        });
    }

    private void restoreScrollPositions() {
        if (restoredBrowserState == null) return;
        if (isCreatorGallery()) {
            for (int i = 0; i < CREATOR_TAB_COUNT; i++) restoreScroll(creatorTabRecyclers[i], "scroll_" + i);
        } else restoreScroll(recycler, "scroll");
    }

    private void restoreScroll(RecyclerView view, String key) {
        if (view == null || view.getLayoutManager() == null || restoredBrowserState == null) return;
        android.os.Parcelable scroll = restoredBrowserState.getParcelable(key);
        if (scroll != null) {
            view.getLayoutManager().onRestoreInstanceState(scroll);
            restoredBrowserState.remove(key);
            if (isCreatorGallery() && creatorAppBar != null) {
                view.post(() -> {
                    if (!isFinishing() && !isDestroyed() && view == activeCreatorRecycler()) {
                        creatorAppBar.setExpanded(!view.canScrollVertically(-1), false);
                    }
                });
            }
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("browser_snapshot", browserSnapshot);
        state.putString("gallery_session", bunkrGallerySessionId);
        state.putBoolean("notification_fresh_pending_focus", notificationFreshPendingFocus);
        state.putInt("tab", activeCreatorTab());
        if (isCreatorGallery()) {
            for (int i = 0; i < CREATOR_TAB_COUNT; i++) {
                RecyclerView view = creatorTabRecyclers[i];
                if (view != null && view.getLayoutManager() != null)
                    state.putParcelable("scroll_" + i, view.getLayoutManager().onSaveInstanceState());
            }
        } else if (recycler != null && recycler.getLayoutManager() != null)
            state.putParcelable("scroll", recycler.getLayoutManager().onSaveInstanceState());
        persistBrowser();
        super.onSaveInstanceState(state);
    }

    @Override protected void onPause() { persistBrowser(); super.onPause(); }

    @Override
    protected void onResume() {
        super.onResume();
        if (creatorProfile != null) creatorProfile.refresh();
        if (isBunkr() && bunkrGalleryAdapter != null) {
            BunkrGallerySessionStore.Snapshot snapshot =
                    BunkrGallerySessionStore.snapshot(bunkrGallerySessionId);
            if (snapshot != null) {
                if (snapshot.items.size() > bunkrGalleryAdapter.size()) {
                    replaceBunkrItems(snapshot.items);
                }
                currentPage = snapshot.currentPage;
                endReached = snapshot.endReached;
            }
            updateCreatorEmptyState();
        } else if (adapter != null) {
            adapter.refreshPlaybackState();
            updateShowDetailsHeader();
        }
        applyLayout();
    }

    @Override
    protected void onDestroy() {
        generation++;
        if (isFinishing() && !shitTokReturnTransition.isEmpty()) {
            ShitTokTransitionSnapshotStore.remove(shitTokReturnTransition);
        }
        if (adapter != null) adapter.close();
        if (creatorTabsMediator != null) creatorTabsMediator.detach();
        io.shutdownNow();
        super.onDestroy();
    }

    private final class CreatorTabsPagerAdapter
            extends RecyclerView.Adapter<CreatorTabHolder> {
        CreatorTabsPagerAdapter() {
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        @NonNull
        @Override
        public CreatorTabHolder onCreateViewHolder(
                @NonNull ViewGroup parent,
                int viewType
        ) {
            RecyclerView page = createRecycler();
            page.setLayoutParams(new RecyclerView.LayoutParams(-1, -1));
            page.setAdapter(creatorTabAdapters[viewType]);
            creatorTabRecyclers[viewType] = page;
            applyCreatorGalleryLayout(page);
            attachCreatorGalleryPinch(page);
            attachGalleryScrollListener(page, creatorTabAdapters[viewType]);
            if (viewType == activeCreatorTab()) recycler = page;
            return new CreatorTabHolder(page, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull CreatorTabHolder holder, int position) {
            creatorTabRecyclers[position] = holder.recycler;
            if (!restoringBrowser) restoreScroll(holder.recycler, "scroll_" + position);
            if (position == activeCreatorTab()) recycler = holder.recycler;
        }

        @Override
        public void onViewRecycled(@NonNull CreatorTabHolder holder) {
            if (creatorTabRecyclers[holder.tab] == holder.recycler) {
                creatorTabRecyclers[holder.tab] = null;
            }
            holder.recycler.clearOnScrollListeners();
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return CREATOR_TAB_COUNT;
        }
    }

    private static final class CreatorTabHolder extends RecyclerView.ViewHolder {
        final RecyclerView recycler;
        final int tab;

        CreatorTabHolder(RecyclerView recycler, int tab) {
            super(recycler);
            this.recycler = recycler;
            this.tab = tab;
        }
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private String value(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
