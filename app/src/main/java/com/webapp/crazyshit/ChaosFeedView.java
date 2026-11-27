package com.webapp.crazyshit;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import android.os.Build;
import android.view.TextureView;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Trace;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.TextViewCompat;
import androidx.core.view.ViewCompat;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

import org.json.JSONArray;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-height random video feed used by the Chaos tab.
 * Swipe manually at any time. Automatic progression is an optional session control.
 */
@UnstableApi
public final class ChaosFeedView extends FrameLayout {
    private final SharedPreferences.OnSharedPreferenceChangeListener appearanceListener =
            (preferences, key) -> {
                if (AppearanceTheme.KEY_ACCENT.equals(key)) post(this::refreshVisibleAppearance);
            };

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        AppearanceTheme.prefs(getContext()).registerOnSharedPreferenceChangeListener(appearanceListener);
        refreshVisibleAppearance();
    }

    @Override protected void onDetachedFromWindow() {
        AppearanceTheme.prefs(getContext()).unregisterOnSharedPreferenceChangeListener(appearanceListener);
        super.onDetachedFromWindow();
    }

    private void refreshVisibleAppearance() {
        RecyclerView recycler = pagerRecycler();
        if (recycler == null) return;
        for (int i = 0; i < recycler.getChildCount(); i++) {
            RecyclerView.ViewHolder raw = recycler.getChildViewHolder(recycler.getChildAt(i));
            if (raw instanceof ChaosHolder) ((ChaosHolder) raw).refreshVisualTheme();
        }
    }
    @Override public void onDescendantInvalidated(View child, View target) {
        super.onDescendantInvalidated(child, target);
        if (child == pager) {
            for (int i = 0; i < getChildCount(); i++) {
                if (getChildAt(i) instanceof ShitTokFocusControls) {
                    ((ShitTokFocusControls) getChildAt(i)).invalidateBackdrop();
                }
            }
        }
    }

    public interface Host {
        void openDetails(NativeContentItem item);

        default void onClearDisplayChanged(boolean clear) {
        }

        default void onSourceSelectorVisibilityChanged(boolean visible) {
        }

        default void onTemporaryPinchChanged(boolean pinching) {
        }

        default void onVerticalPageChanged(int direction, boolean atTop, boolean userDriven) {
        }
    }

    /** A legible accent border on the dark fullscreen pill, for every appearance preset. */
    static int fullscreenStrokeColor(int accent) {
        return Color.argb(190, Color.red(accent), Color.green(accent), Color.blue(accent));
    }

    private static final String PREFS = "chaos_feed";
    private static final String KEY_RECENT = "recent_urls";
    private static final String KEY_HIDDEN = "hidden_urls";
    private static final String KEY_MUTED = "muted";
    private static final String KEY_AUTO_SCROLL = "auto_scroll";
    private static final int MAX_RECENT = 500;
    private static final int MAX_HIDDEN = 600;
    private static final int LOAD_AHEAD_AT = 5;
    private static final long UNSEEN_EXPLORATION_MS = 3_800L;
    private static final int MAX_HISTORY_FALLBACK = 16;
    private final ShitTokRunwayPolicy runway = new ShitTokRunwayPolicy();
    private long belowRunwayAt, belowRunwayMs;
    private int physicalTailHits;
    private int modeTailHits, modeMinRunway = Integer.MAX_VALUE, modeRefills, modeDryRefills;
    private long modeBelowAt, modeBelowMs;
    private long admissionApplyCount, admissionApplyTotalMs, admissionApplyMaxMs;
    private final long initializedAt = android.os.SystemClock.elapsedRealtime();
    private boolean firstCandidate, firstAdmitted;
    private volatile int activeAdmissions, inventoryCandidateCount;
    private final java.util.concurrent.atomic.AtomicInteger inventoryResolutions = new java.util.concurrent.atomic.AtomicInteger();
    // Legacy cache misses and admission share URL-resolution capacity. Selected work stays immediate.
    private final java.util.concurrent.Semaphore inventoryResolutionSlots = new java.util.concurrent.Semaphore(3, true);
    private final Runnable recoveryRunnable = () -> { consecutiveDryLoads = 0; requestContinuity(); };
    private static final int MAX_QUEUED_AHEAD = 24;
    private static final int MAX_STREAM_CACHE = 32;
    private static final long RECENT_SAVE_DELAY_MS = 750L;
    private static final long STREAM_RETRY_DELAY_MS = 450L;
    private static final long FAILED_CLIP_SKIP_DELAY_MS = 1200L;
    private static final long SWIPE_PREPARE_IDLE_DELAY_MS = 60L;
    private static final long SWIPE_PREPARE_STAGGER_MS = 120L;
    private static final String SITE = "https://crazyshit.com/";
    private static final ExecutorService RECENT_PERSIST_IO = Executors.newSingleThreadExecutor();

    private final Activity activity;
    private final boolean sourceRequestsEnabled;
    private final Host host;
    private final CrazyShitRepository repository = new CrazyShitRepository();
    private final ExecutorService io = Executors.newFixedThreadPool(6);
    private final ArrayList<NativeContentItem> items = new ArrayList<>();
    private final Set<String> sessionUrls = new HashSet<>();
    private final LinkedHashSet<String> sessionIdentities = new LinkedHashSet<>();
    private final ShitTokSeenStore seenStore;
    private final java.util.concurrent.atomic.AtomicInteger rawCandidates = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger duplicateRejected = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger seenRejected = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger likedRejected = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger likedLookupFailures = new java.util.concurrent.atomic.AtomicInteger();
    private int refillCount, dryRefillCount, lateLikedSelections, minimumRunway = Integer.MAX_VALUE;
    private long refillTotalMs, refillMaxMs;
    private final Deque<String> recentUrls = new ArrayDeque<>();
    private final Set<String> recentSet = new HashSet<>();
    private final LinkedHashSet<String> hiddenUrls = new LinkedHashSet<>();
    private final Set<String> blockedCreatorKeys = new HashSet<>();
    private final Map<String, CrazyShitRepository.StreamInfo> streamCache =
            new LinkedHashMap<String, CrazyShitRepository.StreamInfo>(MAX_STREAM_CACHE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<String, CrazyShitRepository.StreamInfo> eldest
                ) {
                    return size() > MAX_STREAM_CACHE;
                }
            };
    private final ShitTokCreatorMetadataLoader creatorMetadata;
    private final Map<String, Boolean> creatorFavoriteCache = new HashMap<>();
    private final Set<String> resolving = new HashSet<>();
    private final Set<String> unplayable = new HashSet<>();
    private final Set<String> resolveRetried = new HashSet<>();
    private final Set<ChaosHolder> playerHolders =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private ShitTokPlaybackOwner playbackOwner;
    private final ShitTokFrameDiagnostics frameDiagnostics = new ShitTokFrameDiagnostics();
    private final Deque<Runnable> deferredUiApplies = new ArrayDeque<>();
    private volatile int feedGeneration;
    private int plannedPosition = -1;
    private String plannedUrl = "";
    private final Deque<Integer> pendingWarmPreparePositions = new ArrayDeque<>();
    private final Random random = new Random();
    private final ShitTokWarmCategoryCache warmCategories = new ShitTokWarmCategoryCache();
    private final ShitTokCategoryReturnCache returnCategories = new ShitTokCategoryReturnCache();
    private final ShitTokFeedSessionStore feedSessions = new ShitTokFeedSessionStore();
    private final ChaosSourceMixer sourceMixer = new ChaosSourceMixer(repository, random);
    private final Deque<NativeContentItem> orientationPending = new ArrayDeque<>();
    private ShitTokOrientationAdmission orientationAdmission;
    private boolean refillDeferred;
    private volatile boolean verifiedQueueEmpty = true;
    private volatile boolean orientationWorkAllowed = true;
    // Main-thread queue/cache snapshot consumed by the bounded admission worker.
    private volatile boolean continuityWorkNeeded = true;
    private volatile boolean forwardSwipeIntent;
    private float continuityTouchX, continuityTouchY;
    private boolean continuityTouchTracking;
    private final Runnable continuityRunnable = this::maintainContinuity;
    private final ShitTokAspectPriority aspectPriority = new ShitTokAspectPriority();
    private final ShitTokSessionResume sessionResume = new ShitTokSessionResume();

    private ViewPager2 pager;
    private ChaosAdapter adapter;
    private TextView empty;
    private ProgressBar initialProgress;
    private ShitTokCreatorSwipePreview creatorSwipePreview;
    private ImageView creatorSwipeSourceSnapshot;
    private boolean creatorGalleryHandoff;
    private InlineCommentsDialog commentsDialog;
    private ShitTokShareSheet shareSheet;
    private AlertDialog actionDialog;
    private volatile boolean active;
    private volatile boolean hostResumed = true;
    private boolean poolLoading;
    private boolean initialUiDrawn;
    private volatile InventoryBatch inventoryBatch;
    private static final class InventoryBatch {
        volatile boolean cancelled;
        volatile java.util.concurrent.Future<?> harvest;
        void cancel() { cancelled = true; if (harvest != null) harvest.cancel(true); }
    }
    private void cancelInventoryBatch() {
        if (inventoryBatch != null) inventoryBatch.cancel();
        for (NativeContentItem item : orientationAdmission.cancelPending()) sourceMixer.abandon(item);
        inventoryBatch = null;
        activeAdmissions = inventoryCandidateCount = 0;
        poolLoading = false;
    }
    private volatile boolean closed;
    private boolean autoAdvancePending;
    private boolean autoScrollEnabled;
    private int playbackVisit;
    private boolean replaySelectedOnSettle;
    private int autoAdvanceIssuedFrom = -1;
    private int autoAdvanceGeneration;
    private boolean chaosMuted;
    private boolean searchCoverActive;
    private boolean manualFullscreen;
    private boolean landscapeFullscreenDismissed;
    private boolean clearDisplay;
    private ChaosHolder pinchingHolder;
    private int autoAdvanceFrom = -1;
    private int consecutiveDryLoads;
    private volatile int selectedPosition;
    private String selectedUrl = "";
    private int queueUpdateDepth;
    private String queueSelectedUrl = "";
    private int queueSelectedPosition;
    private boolean queueChanged, queueLayoutPending, syncingQueuePager;
    private volatile boolean userPaging;
    private boolean userDragInProgress;
    private boolean settledSelectionPending;
    private int maintenancePosition = -1;
    private boolean maintenanceResolveIssued;
    private final Runnable saveRecentRunnable = this::saveRecentNow;
    private final Runnable playerPrepareMaintenanceRunnable = this::runDeferredPrepareMaintenance;


    public ChaosFeedView(Activity activity, Host host) {
        this(activity, host, true);
    }

    ChaosFeedView(Activity activity, Host host, boolean sourceRequestsEnabled) {
        super(activity);
        this.sourceRequestsEnabled = sourceRequestsEnabled;
        this.activity = activity;
        this.host = host;
        android.os.Handler metadataUi = new android.os.Handler(android.os.Looper.getMainLooper());
        creatorMetadata = new ShitTokCreatorMetadataLoader(activity.getApplicationContext(),
                task -> metadataUi.post(task), this::onCreatorMetadataReady);
        setBackgroundColor(Color.BLACK);
        seenStore = ShitTokSeenStore.get(activity);
        loadRecent();
        loadHidden();
        reloadBlockedCreatorKeys();
        ContentPreference.prefs(activity).registerOnSharedPreferenceChangeListener(preferenceListener);
        postDelayed(sourceRecoveryTick, 2_000L);
        chaosMuted = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_MUTED, false);
        autoScrollEnabled = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTO_SCROLL, false);
        playbackOwner = new ShitTokPlaybackOwner(activity.getApplicationContext(), new ShitTokPlaybackOwner.Callbacks() {
            @Override public void onLeaseChanged(String url) {
                if (closed || isPaging()) return;
                if (pendingCategoryActivation) {
                    // No pending tab may bind/play a surface before handoff.
                    // A newly acquired selected lease can start silent buffering.
                    if (url.equals(pendingCategoryReturnUrl)) post(ChaosFeedView.this::prebufferReturningCategory);
                    return;
                }
                for (ChaosHolder holder : new ArrayList<>(playerHolders)) {
                    if (holder.item != null && url.equals(holder.item.url) && holderAt(holder.boundPosition) == holder) holder.attachLease();
                }
                int position = indexOfUrl(url);
                if (position >= 0) prepareVisible(position);
            }
            @Override public void onLeaseRetired(String url) {
                for (ChaosHolder holder : new ArrayList<>(playerHolders)) {
                    if (holder.item != null && url.equals(holder.item.url)) holder.detachLease();
                }
            }
        });
        playbackOwner.setContinuityFailureListener(url -> {
            if (closed || indexOfUrl(url) <= selectedPosition) return;
            if (unplayable.add(url)) consecutiveDryLoads++;
            requestContinuity();
        });
        playbackOwner.setSpeculationListener(allowed -> {
            orientationWorkAllowed = allowed;
            if (closed) return;
            sourceMixer.setPlaybackProtected(playbackOwner.isStarvationProtected());
            if (!allowed) {
                resetPreferencePreparationHealth();
                cancelSwipePlayerMaintenance();
            } else if (!isPaging()) {
                scheduleSwipePlayerMaintenance(selectedPosition);
                if (refillDeferred || !orientationPending.isEmpty()
                        || sourceMixer.hasPreference(ShitTokContentPreference.get(activity))) loadMorePool();
            }
            requestContinuity();
        });
        playbackOwner.setFirstFrameListener(() -> {
            sourceMixer.onFirstFrame();
            post(this::requestPreferenceCandidates);
        });
        ShitTokSourceMode initialSources = ShitTokSourceMode.get(activity);
        ContentPreference initialPreference = ShitTokContentPreference.get(activity);
        sourceMixer.preferencePreparation.select(initialSources.key(initialPreference));
        ShitTokPoolDiagnostics.beginMode(initialSources, initialPreference);
        orientationAdmission = new ShitTokOrientationAdmission(activity.getApplicationContext());
        orientationAdmission.setCreatorFocused(creatorInventory());
        orientationAdmission.setLandscapeAllowed(
                ShitTokCategoryStrip.Tab.selected(activity) == ShitTokCategoryStrip.Tab.CHAOS);
        buildUi();
        ShitTokPoolDiagnostics.record("feedInit", "at=" + initializedAt);
        initialUiDrawn = !sourceRequestsEnabled;
        if (sourceRequestsEnabled) getViewTreeObserver().addOnDrawListener(new android.view.ViewTreeObserver.OnDrawListener() {
            @Override public void onDraw() {
                if (initialUiDrawn) return;
                initialUiDrawn = true;
                AppPerformance.startup("feed_draw");
                post(() -> {
                    if (getViewTreeObserver().isAlive()) getViewTreeObserver().removeOnDrawListener(this);
                    if (!closed) loadMorePool();
                });
            }
        });
    }

    private void buildUi() {
        pager = new ViewPager2(activity);
        pager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        // Let outgoing/incoming pages draw beneath the pinned header without resizing
        // the paging viewport or touching media fitting and playback ownership.
        setClipChildren(false);
        pager.setClipChildren(false);
        pager.setClipToPadding(false);
        pager.setOffscreenPageLimit(1);
        adapter = new ChaosAdapter();
        pager.setAdapter(adapter);
        addView(pager, new FrameLayout.LayoutParams(-1, -1));

        RecyclerView rv = pagerRecycler();
        if (rv != null) {
            rv.setItemViewCacheSize(3);
            rv.setClipChildren(false);
            rv.setClipToPadding(false);
        }

        initialProgress = new ZeroChillProgressBar(activity);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(48), dp(48));
        pp.gravity = Gravity.CENTER;
        addView(initialProgress, pp);

        empty = new TextView(activity);
        empty.setTextColor(Color.rgb(205, 205, 212));
        empty.setTextSize(15);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(30), dp(30), dp(30), dp(30));
        empty.setText("Loading ShitTok…");
        empty.setVisibility(View.GONE);
        addView(empty, new FrameLayout.LayoutParams(-1, -1));

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageScrolled(int position, float offset, int offsetPixels) {
                RecyclerView recycler = pagerRecycler();
                if (recycler == null) return;
                boolean motion = ZeroChillMotion.animationsEnabled(activity);
                for (int i = 0; i < recycler.getChildCount(); i++) {
                    RecyclerView.ViewHolder raw = recycler.getChildViewHolder(recycler.getChildAt(i));
                    if (raw instanceof ChaosHolder) {
                        ChaosHolder holder = (ChaosHolder) raw;
                        holder.setSwipeAlpha(ShitTokVisualMotion.pageAlpha(
                                holder.getBindingAdapterPosition(), position, offset, motion));
                    }
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                handlePagerScrollState(state);
            }

            @Override
            public void onPageSelected(int position) {
                Trace.beginSection("zc.shittok.page_selected");
                try {
                    selectPage(position);
                } finally {
                    Trace.endSection();
                }
            }

            private void selectPage(int position) {
                if (queueUpdateDepth > 0 || syncingQueuePager) return;
                RecyclerView recycler = pagerRecycler();
                // Adapter changes precede the next layout. Its page indices still describe
                // the old queue, even when such an index happens to fit the new list.
                if (position < 0 || position >= items.size() || position != pager.getCurrentItem()
                        || (queueLayoutPending && recycler != null && recycler.hasPendingAdapterUpdates())) {
                    syncQueuePager();
                    return;
                }
                queueLayoutPending = false;
                if (position != selectedPosition && position >= 0 && position < items.size()
                        && !eligiblePreference(items.get(position), ShitTokContentPreference.get(activity))) {
                    pager.setCurrentItem(selectedPosition, false);
                    return;
                }
                cancelSwipePlayerMaintenance();
                int previousPosition = selectedPosition;
                boolean changed = position != previousPosition;
                if (changed) {
                    ShitTokSmartRanker.leaveSelection();
                    resetTemporaryPinch();
                }
                if (changed) {
                    maintenancePosition = position;
                    consecutiveDryLoads = 0;
                    if (position > previousPosition) runway.forward(android.os.SystemClock.elapsedRealtime());
                }
                if (changed) {
                    playbackVisit++;
                    replaySelectedOnSettle = true;
                    autoAdvanceIssuedFrom = -1;
                }

                resetCreatorSwipePreview();
                if (autoAdvancePending && position != autoAdvanceFrom) {
                    cancelPendingAutoAdvance();
                }
                if (manualFullscreen && position != selectedPosition) exitManualFullscreen();
                selectedPosition = position;
                selectedUrl = items.get(position).url;
                if (changed) creatorMetadata.select(null);
                if (changed) {
                    sessionResume.clear();
                    host.onVerticalPageChanged(
                            position > previousPosition ? 1 : -1,
                            position == 0,
                            active && userDragInProgress
                    );
                }
                settledSelectionPending = true;
                pauseNonSelected(position);
                syncLoadingIndicators();
                requestContinuity();
                if (continuityWorkNeeded) requestMissingContinuity();
                if (!isPaging()) {
                    settleSelectedPage();
                    scheduleSwipePlayerMaintenance(position);
                }
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (searchCoverActive) return true;
        if (fullscreenInputLocked()) return super.dispatchTouchEvent(event);
        // Observe the existing paging slop without consuming or changing pager gestures.
        // ViewPager2 can stay IDLE when dragged beyond its last item.
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            continuityTouchX = event.getX();
            continuityTouchY = event.getY();
            continuityTouchTracking = true;
            forwardSwipeIntent = false;
        } else if (event.getPointerCount() != 1) {
            continuityTouchTracking = false;
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && continuityTouchTracking
                && pager.isUserInputEnabled() && active && hostResumed) {
            float dx = event.getX() - continuityTouchX;
            float dy = event.getY() - continuityTouchY;
            if (isForwardContinuityDrag(dx, dy,
                    ViewConfiguration.get(activity).getScaledPagingTouchSlop())) {
                continuityTouchTracking = false;
                wakeForwardContinuity();
            }
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            continuityTouchTracking = false;
        }
        return super.dispatchTouchEvent(event);
    }

    static boolean isForwardContinuityDrag(float dx, float dy, float slop) {
        return dy < -slop && -dy > Math.abs(dx);
    }

    private void wakeForwardContinuity() {
        if (closed || !active || !hostResumed || usableUpcomingCount() >= desiredRunway()) return;
        if (!forwardSwipeIntent) consecutiveDryLoads = 0;
        forwardSwipeIntent = true;
        if (selectedPosition >= items.size() - 1) { physicalTailHits++; modeTailHits++; }
        requestContinuity();
        // Fetch/probe may start during the drag. Adapter changes and prepares still wait for IDLE.
        if (continuityWorkNeeded && usableUpcomingCount() == items.size() - selectedPosition - 1) loadMorePool();
    }

    private int usableUpcomingCount() {
        int ready = 0;
        for (int i = selectedPosition + 1; i < items.size() && ready < MAX_QUEUED_AHEAD; i++) {
            NativeContentItem item = items.get(i);
            CrazyShitRepository.StreamInfo stream = streamCache.get(item.url);
            if (hiddenUrls.contains(item.url) || isBlockedCreator(item) || unplayable.contains(item.url)
                    || !eligibleVideoAspect(item.aspectRatioHint)
                    || stream == null || stream.mediaUrl == null || stream.mediaUrl.isEmpty()) break;
            ready++;
        }
        return ready;
    }

    private int desiredRunway() { return runway.target(android.os.SystemClock.elapsedRealtime()); }

    private boolean needsContinuity() {
        return runway.needsWork(usableUpcomingCount(), android.os.SystemClock.elapsedRealtime(),
                creatorInventory());
    }

    private void recordRunway(String reason) {
        long now = android.os.SystemClock.elapsedRealtime();
        int usable = usableUpcomingCount(), unresolvedAhead = 0;
        for (int i = selectedPosition + 1; i < items.size(); i++)
            if (!streamCache.containsKey(items.get(i).url)) unresolvedAhead++;
        if (firstAdmitted) minimumRunway = Math.min(minimumRunway, usable);
        if (usable < desiredRunway()) { if (belowRunwayAt == 0) belowRunwayAt = now; }
        else if (belowRunwayAt != 0) { belowRunwayMs += now - belowRunwayAt; belowRunwayAt = 0; }
        if (firstAdmitted) modeMinRunway = Math.min(modeMinRunway, usable);
        if (usable < desiredRunway()) { if (modeBelowAt == 0) modeBelowAt = now; }
        else if (modeBelowAt != 0) { modeBelowMs += now - modeBelowAt; modeBelowAt = 0; }
        String detail = "reason=" + reason + " selected=" + selectedPosition
                + " queued=" + items.size() + " usable=" + usable + " desired=" + desiredRunway()
                + " rapid=" + runway.rapid(now) + " unresolved=" + resolving.size()
                + " unresolvedAhead=" + unresolvedAhead + " candidateQueue=" + inventoryCandidateCount
                + " pendingOrientation=" + orientationPending.size()
                + " selectedState=" + playbackOwner.selectedPlaybackState() + " selectedBufferedMs=" + playbackOwner.selectedBufferedMs()
                + " playbackProtected=" + playbackOwner.isStarvationProtected()
                + " admissionQueue=" + (orientationPending.size() + inventoryCandidateCount) + " activeAdmissions=" + activeAdmissions
                + " activeResolutions=" + (resolving.size() + inventoryResolutions.get())
                + " dry=" + consecutiveDryLoads + " rejected=" + (unplayable.size() + orientationAdmission.rejectedCount())
                + " belowMs=" + (belowRunwayMs + (belowRunwayAt == 0 ? 0 : now - belowRunwayAt))
                + " tailHits=" + physicalTailHits + " minUsable=" + (minimumRunway == Integer.MAX_VALUE ? -1 : minimumRunway)
                + " rawFetched=" + ShitTokPoolDiagnostics.rawFetched() + " offers=" + rawCandidates.get()
                + " duplicate=" + duplicateRejected.get()
                + " seen=" + seenRejected.get() + " liked=" + likedRejected.get()
                + " likedLookupFailures=" + likedLookupFailures.get() + " lateLikedSelections=" + lateLikedSelections + " recentFallback=0"
                + " orientation=" + orientationAdmission.orientationRejectedCount()
                + " unplayable=" + orientationAdmission.unplayableRejectedCount()
                + " activeFetches=" + sourceMixer.activeFetches() + " providers=" + sourceMixer.providerSummary()
                + " dryRefills=" + dryRefillCount + " refills=" + refillCount
                + " refillAvgMs=" + (refillCount == 0 ? 0 : refillTotalMs / refillCount) + " refillMaxMs=" + refillMaxMs
                + " applyAvgMs=" + (admissionApplyCount == 0 ? 0 : admissionApplyTotalMs / admissionApplyCount)
                + " applyMaxMs=" + admissionApplyMaxMs + " creatorMetadataBacklog=" + creatorMetadata.backlog()
                + " " + sourceMixer.preferencePreparation.summary()
                + " " + orientationAdmission.schedulingSummary() + " " + creatorMetadata.cacheSummary()
                + " " + ShitTokPoolDiagnostics.modeSummary()
                + " currentTailHits=" + modeTailHits + " currentBelowMs="
                + (modeBelowMs + (modeBelowAt == 0 ? 0 : now - modeBelowAt))
                + " currentMinUsable=" + (modeMinRunway == Integer.MAX_VALUE ? -1 : modeMinRunway)
                + " currentRefills=" + modeRefills + " currentDryRefills=" + modeDryRefills;
        ShitTokPoolDiagnostics.record("runway", detail);
        ShitTokPoolDiagnostics.snapshot("Uptime ms: " + now + "\n" + detail + "\n" + frameDiagnostics.summary()
                + "\nSeen identities: " + seenStore.seenSize() + " / " + ShitTokSeenStore.SEEN_LIMIT
                + "; liked exclusions: " + seenStore.likedSize() + " / " + ShitTokSeenStore.LIKED_LIMIT
                + "\nProvider IDs: 1=CrazyShit, 2=eFukt, 3=Kaotic, 4=ShitShow, 5=Fapello, 6=OnlyHaven, 7=BaddieHub, 9=Bunkr"
                + "\nselectedState: 1=idle, 2=buffering, 3=ready, 4=ended. Counts describe inventory, not device smoothness.");
    }

    private void requestContinuity() {
        if (searchCoverActive) return;
        continuityWorkNeeded = needsContinuity();
        removeCallbacks(continuityRunnable);
        if (closed || !active || !hostResumed) return;
        recordRunway("request");
        postDelayed(continuityRunnable, SWIPE_PREPARE_IDLE_DELAY_MS);
    }

    private void maintainContinuity() {
        if (searchCoverActive) return;
        if (closed || !active || !hostResumed) return;
        if (isPaging()) {
            requestMissingContinuity();
            return;
        }
        // Failed future clips must not occupy the next page forever. Keep session anti-repeat IDs.
        beginQueueUpdate();
        try {
        boolean removed = false;
        for (int i = items.size() - 1; i > selectedPosition; i--) {
            NativeContentItem item = items.get(i);
            if (!unplayable.contains(item.url) && !hiddenUrls.contains(item.url) && !isBlockedCreator(item)) continue;
            for (ChaosHolder holder : new ArrayList<>(playerHolders)) {
                if (holder.item != null && item.url.equals(holder.item.url)) holder.releasePlayer();
            }
            streamCache.remove(item.url);
            items.remove(i);
            final int removedIndex = i;
            publishQueueChange(() -> adapter.notifyItemRemoved(removedIndex));
            removed = true;
        }
        if (removed) { plannedPosition = -1; syncHolderPositionsAfterQueuePrune(); }
        } finally { endQueueUpdate(); }
        reconcilePlaybackWindow();
        continuityWorkNeeded = needsContinuity();
        // One immediate neighbor may warm during READY recovery, never while current is buffering.
        if (selectedPosition + 1 < items.size()) prepareVisible(selectedPosition + 1);
        if (sourceMixer.hasPreference(ShitTokContentPreference.get(activity))) loadMorePool();
        requestMissingContinuity();
    }

    private void requestMissingContinuity() {
        if (searchCoverActive) return;
        continuityWorkNeeded = needsContinuity();
        if (!continuityWorkNeeded || poolLoading) return;
        if (!creatorInventory() && consecutiveDryLoads >= 4) {
            removeCallbacks(recoveryRunnable);
            postDelayed(recoveryRunnable, 1_000L);
            return;
        }
        for (int i = selectedPosition + 1; i < items.size() && i <= selectedPosition + desiredRunway(); i++) {
            if (!streamCache.containsKey(items.get(i).url) && !unplayable.contains(items.get(i).url)) {
                if (resolving.size() < 3) resolveAt(i);
                if (resolving.size() >= 3) break;
            }
        }
        // Existing unresolved pages cannot prevent independent source production.
        loadMorePool();
    }

    private boolean isPaging() {
        return userPaging || (pager != null && pager.getScrollState() != ViewPager2.SCROLL_STATE_IDLE);
    }

    private void handlePagerScrollState(int state) {
        if (queueUpdateDepth > 0 || syncingQueuePager) return;
        if (searchCoverActive) return;
        if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
            playbackVisit++;
            replaySelectedOnSettle = true;
            userDragInProgress = true;
        }
        else if (state == ViewPager2.SCROLL_STATE_IDLE) userDragInProgress = false;
        userPaging = state != ViewPager2.SCROLL_STATE_IDLE;
        if (userPaging) {
            resetPreferencePreparationHealth();
            resetTemporaryPinch(true);
        }
        ShitTokPoolDiagnostics.record("pager", "state=" + state + " selected=" + selectedPosition);
        playbackOwner.permission(false, chaosMuted);
        if (userPaging) {
            if (state == ViewPager2.SCROLL_STATE_DRAGGING) cancelPendingAutoAdvance();
            cancelSwipePlayerMaintenance();
            // Revoke audio immediately without history/chrome work or decoder teardown.
            for (ChaosHolder holder : playerHolders) silencePlayer(holder.player);
            RecyclerView rv = pagerRecycler();
            if (rv != null) for (int i = 0; i < rv.getChildCount(); i++) {
                RecyclerView.ViewHolder raw = rv.getChildViewHolder(rv.getChildAt(i));
                if (raw instanceof ChaosHolder && !playerHolders.contains(raw))
                    silencePlayer(((ChaosHolder) raw).player);
            }
        } else {
            RecyclerView recycler = pagerRecycler();
            if (recycler != null) for (int i = 0; i < recycler.getChildCount(); i++) {
                RecyclerView.ViewHolder raw = recycler.getChildViewHolder(recycler.getChildAt(i));
                if (raw instanceof ChaosHolder) ((ChaosHolder) raw).setSwipeAlpha(1f);
            }
            applyDeferredUi();
            settleSelectedPage();
            scheduleSwipePlayerMaintenance(selectedPosition);
            requestContinuity();
        }
    }

    private boolean hasSelectedPage() {
        return selectedPosition >= 0 && selectedPosition < items.size();
    }

    /** A queue edit is one URL-preserving selection transition, not a series of pager selections. */
    private void beginQueueUpdate() {
        if (queueUpdateDepth++ != 0) return;
        queueChanged = false;
        queueSelectedPosition = selectedPosition;
        queueSelectedUrl = hasSelectedPage() ? items.get(selectedPosition).url : selectedUrl;
        syncQueueSelection();
    }

    private void syncQueueSelection() {
        int retained = indexOfUrl(queueSelectedUrl);
        selectedPosition = items.isEmpty() ? 0 : retained >= 0 ? retained
                : Math.max(0, Math.min(queueSelectedPosition, items.size() - 1));
        selectedUrl = hasSelectedPage() ? items.get(selectedPosition).url : "";
    }

    private void publishQueueChange(Runnable notification) {
        boolean standalone = queueUpdateDepth == 0;
        if (standalone) beginQueueUpdate();
        try {
            queueChanged = queueLayoutPending = true;
            syncQueueSelection();
            plannedPosition = -1; plannedUrl = "";
            syncHolderPositionsAfterQueuePrune();
            notification.run();
        } finally {
            if (standalone) endQueueUpdate();
        }
    }

    private void endQueueUpdate() {
        if (queueUpdateDepth > 1) { queueUpdateDepth--; return; }
        try {
            syncQueueSelection();
            if (!queueChanged) return;
            cancelSwipePlayerMaintenance();
            if (selectedPosition != queueSelectedPosition || !selectedUrl.equals(queueSelectedUrl)) {
                cancelPendingAutoAdvance();
            }
            if (!selectedUrl.equals(queueSelectedUrl)) {
                sessionResume.clear();
                settledSelectionPending = hasSelectedPage();
            }
            plannedPosition = -1; plannedUrl = "";
            syncHolderPositionsAfterQueuePrune();
            syncQueuePager();
            if (items.isEmpty()) {
                if (!pendingCategoryActivation) playbackOwner.clearSession();
                creatorMetadata.select(null);
                settledSelectionPending = false;
            }
        } finally {
            queueUpdateDepth = 0;
        }
        reconcilePlaybackWindow();
        if (active && hostResumed && !closed) pager.post(() -> {
            if (closed || !active || !hostResumed || isPaging()) return;
            if (items.isEmpty()) loadMorePool();
            else {
                settleSelectedPage();
                scheduleSwipePlayerMaintenance(selectedPosition);
            }
        });
    }

    private void syncQueuePager() {
        if (syncingQueuePager) return;
        syncingQueuePager = true;
        try {
            if (!hasSelectedPage() && !items.isEmpty()) {
                int retained = indexOfUrl(selectedUrl);
                selectedPosition = retained >= 0 ? retained : Math.max(0, Math.min(selectedPosition, items.size() - 1));
                selectedUrl = items.get(selectedPosition).url;
            }
            // ViewPager2 ignores setCurrentItem when itemCount == 0 and otherwise keeps
            // its old currentItem. Reattaching the same adapter resets that empty sentinel.
            if (items.isEmpty() && pager.getCurrentItem() != 0) pager.setAdapter(adapter);
            else pager.setCurrentItem(items.isEmpty() ? 0 : selectedPosition, false);
        } finally { syncingQueuePager = false; }
    }

    private int indexOfUrl(String url) {
        for (int i = 0; i < items.size(); i++) if (url.equals(items.get(i).url)) return i;
        return -1;
    }

    private void reconcilePlaybackWindow() {
        if (searchCoverActive || pendingCategoryActivation) return;
        if (closed || !active || !hostResumed || isPaging() || queueUpdateDepth > 0 || !hasSelectedPage()) return;
        String url = items.get(selectedPosition).url;
        creatorMetadata.select(items.get(selectedPosition));
        AppPerformance.startupItem("owner_select", url, "");
        if (replaySelectedOnSettle) {
            playbackOwner.restartCompleted(url);
            replaySelectedOnSettle = false;
        }
        playbackOwner.select(url,
                selectedPosition + 1 < items.size() ? items.get(selectedPosition + 1).url : null,
                selectedPosition > 0 ? items.get(selectedPosition - 1).url : null,
                ChaosPreloadPolicy.allowsLookAhead(activity));
        playbackOwner.setContinuityNext(ChaosPreloadPolicy.allowsLookAhead(activity)
                && selectedPosition + 1 < items.size() ? items.get(selectedPosition + 1).url : null);
        playbackOwner.permission(true, chaosMuted);
        plannedPosition = selectedPosition;
        plannedUrl = url;
    }

    private void deferUiApply(Runnable apply) {
        if (closed) return;
        if (isPaging()) deferredUiApplies.addLast(apply);
        else apply.run();
    }

    private void applyDeferredUi() {
        if (closed || isPaging()) return;
        while (!deferredUiApplies.isEmpty() && !isPaging()) {
            long started = android.os.SystemClock.elapsedRealtime();
            Trace.beginSection("zc.shittok.refill_apply");
            try { deferredUiApplies.removeFirst().run(); } finally { Trace.endSection(); }
            ShitTokPoolDiagnostics.record("refillApply", "ms=" + (android.os.SystemClock.elapsedRealtime()-started));
        }
    }

    public String playbackDiagnostics() {
        recordRunway("report");
        return playbackOwner.summary() + "\n" + frameDiagnostics.summary() + "\n" + AppPerformance.summary()
                + "\n" + ShitTokPoolDiagnostics.report();
    }

    private void settleSelectedPage() {
        if (closed || !active || !hostResumed || isPaging()) return;
        if (queueUpdateDepth > 0) return;
        if (items.isEmpty()) { selectedPosition = 0; selectedUrl = ""; return; }
        if (!hasSelectedPage()) {
            beginQueueUpdate(); queueChanged = true; endQueueUpdate();
            if (!hasSelectedPage()) return;
        }
        pruneIncompatibleHistory();
        reconcilePlaybackWindow();
        pauseNonSelected(selectedPosition);
        syncLoadingIndicators();
        ChaosHolder holder = holderAt(selectedPosition);
        // A canceled swipe must retain the user's explicit pause.
        if (holder == null || !holder.userPaused) playSelected();
        requestContinuity();
        if (!settledSelectionPending) return;
        settledSelectionPending = false;
        markSeen(selectedPosition);
        if (holder != null) {
            holder.showControlsTemporarily();
            holder.revealSettledControls();
        }
        refreshSelectedLikeStateLater(selectedPosition);
        if (items.size() - selectedPosition <= LOAD_AHEAD_AT) loadMorePool();
    }

    public void setActive(boolean value) {
        if (value) applyContentPreference();
        if (!value && active) { recordRunway("leavingFeed"); pauseRunwayClock(); }
        if (!value) {
            creatorMetadata.select(null);
            resetTemporaryPinch();
            dismissFocusControls();
        }
        if (value && !active) consecutiveDryLoads = 0;
        active = value;
        syncLoadingIndicators();
        if (!value) {
            sourceMixer.stopPreference();
            forwardSwipeIntent = false;
            removeCallbacks(continuityRunnable);
            removeCallbacks(recoveryRunnable);
            cancelInventoryBatch();
        }
        if (value && hostResumed) frameDiagnostics.start(activity); else frameDiagnostics.stop();
        playbackOwner.permission(value && hostResumed && !isPaging() && !closed
                && !pendingCategoryActivation, chaosMuted);
        if (active && hostResumed && !searchCoverActive) {
            /* Category changes are shown by the ShitTok strip, not the OnlyFap notice. */
            setFeedPagingEnabled(true);
            reconcilePlaybackWindow();
            resolveAhead(selectedPosition);
            playSelected();
            syncVisibleChrome();
            refreshSelectedLikeStateLater(selectedPosition);
            if (refillDeferred || items.isEmpty()) loadMorePool();
            requestContinuity();
        } else if (!active || !hostResumed) {
            cancelPendingAutoAdvance();
            cancelSwipePlayerMaintenance();
            pauseAll();
            rememberSelectedPosition();
            releaseAllPlayers();
        }
        if (!active) {
            if (shouldWarmOffTab(hostResumed, items.size() - selectedPosition)) loadMorePool();
            resetCreatorSwipePreview();
            if (clearDisplay) setClearDisplay(false);
            exitManualFullscreen();
        }
    }

    public void onHostResume() {
        if (!hostResumed) consecutiveDryLoads = 0;
        hostResumed = true;
        setFeedPagingEnabled(!searchCoverActive && !pendingCategoryActivation);
        if (active) { applyContentPreference(); /* Category changes are shown by the ShitTok strip, not the OnlyFap notice. */ }
        syncLoadingIndicators();
        orientationWorkAllowed = verifiedQueueEmpty || playbackOwner.allowsSpeculativeWork();
        if (active) frameDiagnostics.start(activity);
        reloadBlockedCreatorKeys();
        if (creatorGalleryHandoff) {
            creatorGalleryHandoff = false;
            resetCreatorSwipePreview();
        }
        if (active && !searchCoverActive) {
            reconcilePlaybackWindow();
            resolveAhead(selectedPosition);
            post(this::activatePendingCategory);
            playSelected();
            syncVisibleChrome();
            refreshVisibleAppearance();
            refreshSelectedLikeStateLater(selectedPosition);
            if (refillDeferred || items.isEmpty()) loadMorePool();
            requestContinuity();
        }
    }

    private void dismissFocusControls() {
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof ShitTokFocusControls) {
                ShitTokFocusControls controls = (ShitTokFocusControls) getChildAt(i);
                controls.bind(null);
                controls.animate().cancel();
                controls.setVisibility(GONE);
            }
        }
    }

    public void onHostPause() {
        ChaosStartupPreloader.stopPreparation();
        if (active) { recordRunway("hostPause"); pauseRunwayClock(); }
        resetTemporaryPinch();
        dismissFocusControls();
        hostResumed = false;
        creatorMetadata.select(null);
        sourceMixer.stopPreference();
        cancelInventoryBatch();
        syncLoadingIndicators();
        forwardSwipeIntent = false;
        removeCallbacks(continuityRunnable);
        removeCallbacks(recoveryRunnable);
        orientationWorkAllowed = false;
        frameDiagnostics.stop();
        playbackOwner.permission(false, chaosMuted);
        cancelPendingAutoAdvance();
        cancelSwipePlayerMaintenance();
        if (!creatorGalleryHandoff) resetCreatorSwipePreview();
        pauseAll();
        rememberSelectedPosition();
        releaseAllPlayers();
        flushRecent();
    }

    /** Search owns input and defers feed work; selected playback keeps its foreground lease. */
    public void onSearchOpened() {
        if (searchCoverActive) return;
        ChaosStartupPreloader.stopPreparation();
        ChaosHolder selected = holderAt(selectedPosition);
        if (selected != null) selected.cancelInteractionUi();
        resetTemporaryPinch();
        dismissFocusControls();
        resetCreatorSwipePreview();
        searchCoverActive = true;
        creatorMetadata.select(null);
        setFeedPagingEnabled(false);
        cancelPendingAutoAdvance();
        cancelSwipePlayerMaintenance();
        sourceMixer.stopPreference();
        cancelInventoryBatch();
        removeCallbacks(continuityRunnable);
        removeCallbacks(recoveryRunnable);
        orientationWorkAllowed = false;
        pauseRunwayClock();
        pauseNonSelected(selectedPosition);
        playbackOwner.setSearchCovered(true);
    }

    public void onSearchClosed() {
        if (!searchCoverActive) return;
        searchCoverActive = false;
        playbackOwner.setSearchCovered(false);
        setFeedPagingEnabled(active && hostResumed && !closed);
        if (!active || !hostResumed || closed) return;
        reconcilePlaybackWindow();
        if (hasSelectedPage() && playbackOwner.stream(items.get(selectedPosition).url) == null)
            playSelected();
        resolveAt(selectedPosition + 1);
        resolveAt(selectedPosition + 2);
        requestContinuity();
    }

    public void onConfigurationChanged() {
        if (!isLandscape()) landscapeFullscreenDismissed = false;
        if (fullscreenInputLocked()) {
            cancelPendingAutoAdvance();
            if (active && clearDisplay) setClearDisplay(false);
        }
        updateSourceControlVisibility();
        setFeedPagingEnabled(active && hostResumed && !searchCoverActive && !pendingCategoryActivation);
        if (shareSheet != null) shareSheet.dismiss();
        if (actionDialog != null) actionDialog.dismiss();
        resetTemporaryPinch();
        resetCreatorSwipePreview();
        if (commentsDialog != null && commentsDialog.isShowing()) commentsDialog.dismiss();
        syncVisibleChrome();
    }

    public void refresh() {
    if (isPaging()) { deferUiApply(this::refresh); return; }
        returnCategories.clear();
        feedSessions.clear();
        cancelHorizontalCategorySlide();
        resetDeferredReturnDiscovery();
        categoryTapAtMs = 0L;
        pendingCategoryReturnUrl = "";
        pendingCategoryActivation = false;
        categoryWaitingVisible = false;
        categorySelectionVersion++;
        removeCallbacks(showCategoryWait);
        setFeedPagingEnabled(!searchCoverActive);
        beginQueueUpdate();
        try {
    resetTemporaryPinch();
    feedGeneration++;
    cancelInventoryBatch();
    runway.reset();
    forwardSwipeIntent = false;
    removeCallbacks(continuityRunnable);
    removeCallbacks(recoveryRunnable);
    continuityWorkNeeded = true;
    deferredUiApplies.clear();
    playbackOwner.clearSession();
    plannedPosition = -1;
    reloadBlockedCreatorKeys();
    cancelSwipePlayerMaintenance();
    resetCreatorSwipePreview();
    pauseAll();
    sessionResume.clear();
    consecutiveDryLoads = 0;
    sourceMixer.resetDeck();
    poolLoading = false;
    cancelPendingAutoAdvance();
    orientationPending.clear();
    refillDeferred = false;
    streamCache.clear();
    creatorMetadata.clear();
    creatorFavoriteCache.clear();
    resolving.clear();
    unplayable.clear();
    resolveRetried.clear();
    // Keep sessionUrls so Refresh cannot immediately deal the same clips back again.
    queueSelectedUrl = ""; queueSelectedPosition = 0;
    items.clear();
    selectedPosition = 0; selectedUrl = "";
    verifiedQueueEmpty = true;
    exitManualFullscreen();
    publishQueueChange(() -> adapter.notifyDataSetChanged());
    initialProgress.setVisibility(View.VISIBLE);
    empty.setVisibility(View.GONE);
    selectedPosition = 0;
    pager.setCurrentItem(0, false);
    } finally { endQueueUpdate(); }
    loadMorePool();
    }

    public void close() {
        returnCategories.clear();
        feedSessions.clear();
        cancelHorizontalCategorySlide();
        resetDeferredReturnDiscovery();
        categoryTapAtMs = 0L;
        pendingCategoryReturnUrl = "";
        pendingCategoryActivation = false;
        categoryWaitingVisible = false;
        categorySelectionVersion++;
        removeCallbacks(showCategoryWait);
        if (shareSheet != null) shareSheet.dismiss();
        if (actionDialog != null) actionDialog.dismiss();
        searchCoverActive = false;
        playbackOwner.setSearchCovered(false);
        resetTemporaryPinch();
        dismissFocusControls();
        cancelPendingAutoAdvance();
        resetCreatorSwipePreview();
        closed = true;
        sourceMixer.stopPreference();
        cancelInventoryBatch();
        forwardSwipeIntent = false;
        removeCallbacks(continuityRunnable);
        removeCallbacks(recoveryRunnable);
        frameDiagnostics.stop();
        feedGeneration++;
        deferredUiApplies.clear();
        poolLoading = false;
        active = false;
        hostResumed = false;
        exitManualFullscreen();
        if (commentsDialog != null && commentsDialog.isShowing()) commentsDialog.dismiss();
        cancelSwipePlayerMaintenance();
        pauseAll();
        sessionResume.clear();
        releaseAllPlayers();
        flushRecent();
        removeCallbacks(saveRecentRunnable);
        if (creatorSwipePreview != null && creatorSwipePreview.getParent() instanceof ViewGroup) {
            ((ViewGroup) creatorSwipePreview.getParent()).removeView(creatorSwipePreview);
        }
        orientationAdmission.close();
        creatorMetadata.close();
        removeCallbacks(sourceRecoveryTick);
        ContentPreference.prefs(activity).unregisterOnSharedPreferenceChangeListener(preferenceListener);
        io.shutdownNow();
    }

    private ContentPreference queuePreference;
    private ShitTokSourceMode queueSources = ShitTokSourceMode.ALL;
    private String invalidatedSelection;
    private boolean pendingCategoryActivation;
    private String pendingCategoryReturnUrl = "";
    private volatile int categorySelectionVersion;
    private boolean categoryWaitingVisible;
    private final Runnable showCategoryWait = () -> {
        if (!closed && pendingCategoryActivation && active && hostResumed) {
            categoryWaitingVisible = true;
            syncLoadingIndicators();
        }
    };
    private long categoryTapAtMs;
    private long deferredReturnDiscoveryUntilMs;
    private String deferredReturnDiscoveryUrl = "";
    private final Runnable resumeReturnDiscovery = this::releaseDeferredReturnDiscovery;

    private void releaseDeferredReturnDiscovery() {
        if (deferredReturnDiscoveryUntilMs == 0L) return;
        deferredReturnDiscoveryUntilMs = 0L;
        deferredReturnDiscoveryUrl = "";
        removeCallbacks(resumeReturnDiscovery);
        if (!closed && active && hostResumed) post(this::loadMorePool);
    }

    private void resetDeferredReturnDiscovery() {
        deferredReturnDiscoveryUntilMs = 0L;
        deferredReturnDiscoveryUrl = "";
        removeCallbacks(resumeReturnDiscovery);
    }
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener preferenceListener =
            (preferences, key) -> { if (ShitTokContentPreference.KEY.equals(key) || ShitTokSourceMode.KEY.equals(key)) post(this::applyContentPreference); };
    private final Runnable sourceRecoveryTick = new Runnable() {
        @Override public void run() {
            if (closed) return;
            if (active && hostResumed && sourceRequestsEnabled && !searchCoverActive) {
                applyContentPreference();
                continuityWorkNeeded = needsContinuity();
                if (continuityWorkNeeded && !items.isEmpty() && playbackOwner.allowsSpeculativeWork())
                    sourceMixer.recoverSources(activity);
                requestPreferenceCandidates();
                if (needsContinuity()) loadMorePool();
                maybePrepareUnopenedPreference();
            } else resetPreferencePreparationHealth();
            postDelayed(this, 2_000L);
        }
    };

    private ImageView departingCategoryFrame;
    private int horizontalCategoryDirection = 1;

    /** Tabs animate only the feed content; the header and dock never move. */
    static int horizontalPreferenceDirection(ContentPreference from, ContentPreference to) {
        return Integer.compare(tabOrder(to), tabOrder(from)) < 0 ? -1 : 1;
    }

    private static int tabOrder(ContentPreference value) {
        if (value == ContentPreference.WOMEN) return 1;
        if (value == ContentPreference.MEN) return 2;
        if (value == ContentPreference.TRANS_WOMEN || value == ContentPreference.TRANS_MEN) return 3;
        if (value == ContentPreference.GROUPS) return 4;
        return 0;
    }

    private void beginHorizontalCategorySlide(ContentPreference from, ContentPreference to) {
        horizontalCategoryDirection = horizontalPreferenceDirection(from, to);
        if (departingCategoryFrame != null) {
            // Rapid tabs keep the one frozen outgoing frame until the latest feed is ready.
            departingCategoryFrame.animate().withEndAction(null).cancel();
            departingCategoryFrame.setTranslationX(0f);
            pager.animate().withEndAction(null).cancel();
            pager.setTranslationX(0f);
            return;
        }
        if (!sourceRequestsEnabled || !ZeroChillMotion.animationsEnabled(activity)
                || !active || !hostResumed || pager.getWidth() <= 0 || !isAttachedToWindow()) return;
        ChaosHolder current = holderAt(selectedPosition);
        View surface = current == null ? null : current.playerView.getVideoSurfaceView();
        String token = ShitTokTransitionSnapshotStore.beginCapture(activity, pager, surface);
        android.graphics.Bitmap image = ShitTokTransitionSnapshotStore.snapshot(token);
        ShitTokTransitionSnapshotStore.remove(token);
        if (image == null) return;
        ImageView snapshot = new ImageView(activity);
        snapshot.setImageBitmap(image);
        snapshot.setScaleType(ImageView.ScaleType.FIT_XY);
        snapshot.setBackgroundColor(Color.BLACK);
        snapshot.setClickable(true);
        snapshot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(snapshot, new FrameLayout.LayoutParams(-1, -1));
        departingCategoryFrame = snapshot;
        initialProgress.bringToFront();
    }

    private void finishHorizontalCategorySlide() {
        final ImageView old = departingCategoryFrame;
        if (old == null) return;
        int width = pager.getWidth();
        if (!ZeroChillMotion.animationsEnabled(activity) || width <= 0 || !isAttachedToWindow()) {
            cancelHorizontalCategorySlide();
            return;
        }
        final int delta = horizontalCategoryDirection * width;
        pager.animate().withEndAction(null).cancel();
        old.animate().withEndAction(null).cancel();
        pager.setTranslationX(delta);
        old.setTranslationX(0f);
        old.animate().translationX(-delta)
                .setDuration(240L).setInterpolator(ZeroChillMotion.EASE_OUT).start();
        pager.animate().translationX(0f)
                .setDuration(240L).setInterpolator(ZeroChillMotion.EASE_OUT)
                .withEndAction(() -> {
                    if (departingCategoryFrame == old) cancelHorizontalCategorySlide();
                }).start();
    }

    private void cancelHorizontalCategorySlide() {
        if (departingCategoryFrame != null) {
            ImageView old = departingCategoryFrame;
            departingCategoryFrame = null;
            old.animate().withEndAction(null).cancel();
            old.setImageDrawable(null);
            removeView(old);
        }
        if (pager != null) {
            pager.animate().withEndAction(null).cancel();
            pager.setTranslationX(0f);
        }
    }

    /** One queue, timestamp, and pause state per preference tab; only one player stays active. */
    private void applyContentPreference() {
        if (closed) return;
        ContentPreference current = ShitTokContentPreference.get(activity);
        ShitTokSourceMode currentSources = ShitTokSourceMode.get(activity);
        updateSourceControlVisibility();
        if (queuePreference == null) {
            queuePreference = current;
            queueSources = currentSources;
            sourceMixer.preferencePreparation.select(currentSources.key(current));
            return;
        }
        if (queuePreference == current && queueSources == currentSources) return;
        String selection = currentSources.key(current);
        if (!selection.equals(invalidatedSelection)) {
            invalidatedSelection = selection;
            resetPreferencePreparationHealth();
            sourceMixer.switchPreference(currentSources, current);
            cancelInventoryBatch();
        }
        if (isPaging()) { deferUiApply(this::applyContentPreference); return; }
        resetDeferredReturnDiscovery();
        categoryTapAtMs = android.os.SystemClock.elapsedRealtime();
        beginQueueUpdate();
        try {
            ContentPreference previous = queuePreference;
            String outgoingSelectedForParking = !pendingCategoryActivation && hasSelectedPage()
                    ? items.get(selectedPosition).url : null;
            // A rapid tap whose feed never activated must not overwrite its earlier checkpoint.
            if (!pendingCategoryActivation && hasSelectedPage()) {
                String outgoingUrl = items.get(selectedPosition).url;
                ExoPlayer outgoingPlayer = playbackOwner.player(outgoingUrl);
                long currentTime = outgoingPlayer == null ? 0L
                        : Math.max(0L, outgoingPlayer.getCurrentPosition());
                // Keep a remembered position if the returning player is still at
                // zero or not yet prepared. Never replace it with an empty lease.
                ShitTokFeedSessionStore.Session earlier =
                        feedSessions.get(queueSources, previous);
                long remembered = earlier != null && outgoingUrl.equals(earlier.selectedUrl)
                        ? earlier.positionMs : 0L;
                long position = currentTime > 0L
                        ? currentTime : Math.max(sessionResume.positionFor(outgoingUrl), remembered);
                if (outgoingPlayer != null) playbackOwner.remember(outgoingUrl, position);
                feedSessions.save(queueSources, previous, items, selectedPosition, position,
                        playbackOwner.isUserPaused(outgoingUrl), streamCache);
            }
            beginHorizontalCategorySlide(previous, current);
            // Preserve a READY outgoing player's media buffer after the transition snapshot.
            // This player stays muted and borrows the third slot until a swipe or short timeout.
            if (outgoingSelectedForParking != null)
                playbackOwner.parkSelectedForCategoryReturn(outgoingSelectedForParking);
            ShitTokFeedSessionStore.Session saved = feedSessions.get(currentSources, current);
            queuePreference = current;
            queueSources = currentSources;
            // A brand-new tab still needs a handoff gate until its first clip is ready.
            pendingCategoryActivation = true;
            pendingCategoryReturnUrl = saved == null ? "" : saved.selectedUrl;
            categorySelectionVersion++;
            resolving.clear();
            removeCallbacks(showCategoryWait);
            categoryWaitingVisible = false;
            playbackOwner.cancelSilentReturnPrebuffer();
            playbackOwner.permission(false, chaosMuted);
            for (ChaosHolder holder : new ArrayList<>(playerHolders)) silencePlayer(holder.player);
            setFeedPagingEnabled(false);
            if (pendingCategoryActivation) postDelayed(showCategoryWait, 160L);
            syncLoadingIndicators();
            orientationAdmission.setCreatorFocused(currentSources.creatorInventory(current));
            orientationAdmission.setLandscapeAllowed(
                    ShitTokCategoryStrip.Tab.selected(activity) == ShitTokCategoryStrip.Tab.CHAOS);
            invalidatedSelection = null;
            ShitTokPoolDiagnostics.beginMode(currentSources, current);
            modeTailHits = modeRefills = modeDryRefills = 0;
            modeMinRunway = Integer.MAX_VALUE; modeBelowAt = modeBelowMs = 0;
            cancelPendingAutoAdvance();
            for (NativeContentItem item : orientationPending) sourceMixer.abandon(item);
            orientationPending.clear();
            consecutiveDryLoads = 0;
            refillDeferred = true;
            continuityWorkNeeded = true;

            // There is no shared historical pager across categories anymore.
            items.clear();
            streamCache.clear();
            sessionUrls.clear();
            sessionIdentities.clear();
            unplayable.clear();
            resolveRetried.clear();
            queueSelectedUrl = "";
            queueSelectedPosition = 0;
            if (saved != null) {
                for (NativeContentItem item : saved.queue) {
                    // These videos were already admitted to THIS category when saved.
                    // Re-running the current discovery filters would silently remove
                    // previously watched clips and shift the saved page index.
                    // User-hidden videos and newly blocked creators remain excluded.
                    if (item == null || item.url == null || hiddenUrls.contains(item.url)
                            || ShitTokBlockedCreatorStore.isBlocked(blockedCreatorKeys, item)) continue;
                    items.add(item);
                    sessionUrls.add(item.url);
                    sessionIdentities.add(ShitTokContentIdentity.page(item.url));
                    CrazyShitRepository.StreamInfo stream = saved.streams.get(item.url);
                    if (stream != null) {
                        streamCache.put(item.url, stream);
                        sessionIdentities.add(ShitTokContentIdentity.media(stream.mediaUrl));
                    }
                }
                if (sessionUrls.contains(saved.selectedUrl)) {
                    queueSelectedUrl = saved.selectedUrl;
                    queueSelectedPosition = Math.max(0, indexOfUrl(saved.selectedUrl));
                    playbackOwner.remember(saved.selectedUrl, saved.positionMs);
                    playbackOwner.setUserPaused(saved.selectedUrl, saved.paused);
                } else {
                    pendingCategoryReturnUrl = "";
                }
            }
            int returnIndex = indexOfUrl(pendingCategoryReturnUrl);
            CrazyShitRepository.StreamInfo returningStream = returnIndex < 0 ? null
                    : streamCache.get(pendingCategoryReturnUrl);
            boolean cachedReturn = returningStream != null
                    && returningStream.mediaUrl != null && !returningStream.mediaUrl.isEmpty();
            int queuedAhead = returnIndex < 0 ? 0 : items.size() - returnIndex - 1;
            if (ShitTokReturnBufferPriority.shouldDeferDiscovery(cachedReturn, queuedAhead)) {
                // Let the selected video buffer before starting optional catalog / orientation work.
                // The deadline runs even if a paused clip never renders a first frame.
                deferredReturnDiscoveryUrl = pendingCategoryReturnUrl;
                deferredReturnDiscoveryUntilMs = android.os.SystemClock.elapsedRealtime()
                        + ShitTokReturnBufferPriority.DISCOVERY_GRACE_MS;
                postDelayed(resumeReturnDiscovery, ShitTokReturnBufferPriority.DISCOVERY_GRACE_MS);
            }
            ShitTokPoolDiagnostics.record("categoryTap",
                    "cached=" + cachedReturn + " queuedAhead=" + queuedAhead
                            + " priority=" + (deferredReturnDiscoveryUntilMs != 0L));
            publishQueueChange(() -> adapter.notifyDataSetChanged());
            sourceMixer.requestPreference(activity, () -> post(this::loadMorePool), false);
            // Allow the remembered video to open the already-cached stream before
            // the horizontal transition completes. It has no audio permission.
            post(this::prebufferReturningCategory);
            post(this::loadMorePool);
            post(this::activatePendingCategory);
        } finally {
            endQueueUpdate();
        }
    }

    /** Prepare one cached return clip without starting playback or a new player pool. */
    private void prebufferReturningCategory() {
        if (closed || !active || !hostResumed || searchCoverActive
                || !pendingCategoryActivation || pendingCategoryReturnUrl.isEmpty()) return;
        String url = pendingCategoryReturnUrl;
        if (indexOfUrl(url) < 0) return;
        CrazyShitRepository.StreamInfo stream = streamCache.get(url);
        if (stream == null || stream.mediaUrl == null || stream.mediaUrl.isEmpty()) return;
        // Reusing a retained READY lease is cheaper than even silent re-preparation.
        boolean reused = playbackOwner.hasPreparedReturnLease(url);
        // One selected lease, no preloaded neighbors. The regular vertical
        // playlist neighbors are acquired when the category becomes active.
        playbackOwner.select(url, null, null, false);
        if (reused) ShitTokPoolDiagnostics.record("categoryRetainedReturnHit", "url=" + url
                + " bufferedMs=" + playbackOwner.selectedBufferedMs());
        if (playbackOwner.prebufferSelectedReturn(url, stream)) {
            ShitTokPoolDiagnostics.record("categoryReturnPrebuffer", url);
        }
        // A pending lease will invoke onLeaseChanged and retry this once ready.
    }

    /** Restore the selected clip before its queued successors, not a fresh random batch. */
    private boolean restoreCategoryCheckpoint(ShitTokCategoryReturnCache.Checkpoint checkpoint) {
        if (checkpoint == null) return false;
        boolean selectedAlreadyVisible = checkpoint.selectedUrl.equals(selectedUrl)
                && hasSelectedPage() && eligiblePreference(items.get(selectedPosition), queuePreference);
        for (ShitTokCategoryReturnCache.Entry entry : checkpoint.entries) {
            if (entry.item == null || entry.item.url == null
                    || !eligiblePreference(entry.item, queuePreference)
                    || !eligibleVideoAspect(entry.item.aspectRatioHint)) continue;
            String url = entry.item.url;
            if (entry.stream != null) streamCache.put(url, entry.stream);
            int existing = indexOfUrl(url);
            // Preserve existing queue positions. Moving an earlier bookmark
            // into the active index can shift the old selection forward and
            // cause the return handoff to skip the saved URL altogether.
            if (existing < 0) {
                // Only re-admit clips the user explicitly left in this category.
                // Other unseen-first/anti-repeat rules are unchanged.
                sessionUrls.remove(url);
                sessionIdentities.remove(ShitTokContentIdentity.page(url));
                if (entry.stream != null)
                    sessionIdentities.remove(ShitTokContentIdentity.media(entry.stream.mediaUrl));
                int before = items.size();
                appendUnique(java.util.Collections.singletonList(entry.item));
                if (items.size() > before)
                    publishQueueChange(() -> adapter.notifyItemRangeInserted(before, items.size() - before));
            }
        }
        if (!checkpoint.entries.isEmpty()) post(this::activatePendingCategory);
        return selectedAlreadyVisible;
    }

    static int firstReadyCategoryIndex(List<NativeContentItem> queue, int after,
            java.util.function.Predicate<NativeContentItem> eligible,
            java.util.function.Predicate<NativeContentItem> ready) {
        for (int i = Math.max(0, after + 1); i < queue.size(); i++)
            if (eligible.test(queue.get(i)) && ready.test(queue.get(i))) return i;
        return -1;
    }

    static int savedCategoryReturnIndex(List<NativeContentItem> queue, String savedUrl,
            java.util.function.Predicate<NativeContentItem> eligible) {
        if (queue == null || savedUrl == null || savedUrl.isEmpty()) return -1;
        for (int i = 0; i < queue.size(); i++)
            if (savedUrl.equals(queue.get(i).url) && eligible.test(queue.get(i))) return i;
        return -1;
    }

    private void activatePendingCategory() {
        if (!pendingCategoryActivation || closed || !active || !hostResumed
                || searchCoverActive || isPaging() || queueUpdateDepth > 0) return;
        // Waiting for an admitted page alone can flash black while its URL still resolves.
        // Prefer a ready video from this category and prepare missing ones in the background.
        int next = -1;
        if (!pendingCategoryReturnUrl.isEmpty()) {
            int savedIndex = savedCategoryReturnIndex(items, pendingCategoryReturnUrl,
                    item -> eligiblePreference(item, queuePreference));
            if (savedIndex >= 0) {
                if (!unplayable.contains(pendingCategoryReturnUrl)) {
                    CrazyShitRepository.StreamInfo savedStream = streamCache.get(pendingCategoryReturnUrl);
                    if (savedStream == null || savedStream.mediaUrl == null || savedStream.mediaUrl.isEmpty()) {
                        resolveAt(savedIndex);
                        return;
                    }
                    next = savedIndex;
                }
            }
            if (next < 0) pendingCategoryReturnUrl = "";
        }
        if (next < 0) next = firstReadyCategoryIndex(items, -1,
                item -> eligiblePreference(item, queuePreference),
                item -> {
                    CrazyShitRepository.StreamInfo stream = streamCache.get(item.url);
                    return stream != null && stream.mediaUrl != null && !stream.mediaUrl.isEmpty();
                });
        if (next < 0) {
            for (int i = 0; i < items.size(); i++) {
                NativeContentItem candidate = items.get(i);
                if (eligiblePreference(candidate, queuePreference)
                        && !unplayable.contains(candidate.url)) {
                    resolveAt(i);
                    break;
                }
            }
            return;
        }
        RecyclerView recycler = pagerRecycler();
        if (recycler != null && recycler.hasPendingAdapterUpdates()) {
            pager.postOnAnimation(this::activatePendingCategory);
            return;
        }
        pendingCategoryActivation = false;
        pendingCategoryReturnUrl = "";
        if (categoryTapAtMs != 0L) {
            ShitTokPoolDiagnostics.record("categoryActivated",
                    "elapsedMs=" + (android.os.SystemClock.elapsedRealtime() - categoryTapAtMs));
        }
        removeCallbacks(showCategoryWait);
        categoryWaitingVisible = false;
        syncLoadingIndicators();
        setFeedPagingEnabled(!searchCoverActive);
        if (next == selectedPosition) {
            // Returning to the same URL requires explicit permission restoration:
            // ViewPager2 emits no selection callback for an unchanged index.
            reconcilePlaybackWindow();
            playSelected();
        } else {
            pager.setCurrentItem(next, false);
        }
        pager.postOnAnimation(this::finishHorizontalCategorySlide);
    }

    private void pruneIncompatibleHistory() {
        ContentPreference mode = ShitTokContentPreference.get(activity);
        if (!creatorInventory() || isPaging()) return;
        beginQueueUpdate();
        try {
        ArrayList<Integer> removed = new ArrayList<>();
        for (int i = selectedPosition - 1; i >= 0; i--) {
            NativeContentItem item = items.get(i);
            if (eligiblePreference(item, mode)) continue;
            releaseFutureHolder(item.url);
            streamCache.remove(item.url);
            items.remove(i);
            selectedPosition--;
            removed.add(i);
        }
        if (removed.isEmpty()) return;
        // Targeted removals retain the current holder/URL lease while shifting its position.
        syncHolderPositionsAfterQueuePrune();
        publishQueueChange(() -> { for (int index : removed) adapter.notifyItemRemoved(index); });
        pager.setCurrentItem(selectedPosition, false);
        plannedPosition = -1;
        } finally { endQueueUpdate(); }
    }

    private void releaseFutureHolder(String url) {
        for (ChaosHolder holder : new ArrayList<>(playerHolders))
            if (holder.item != null && url.equals(holder.item.url)) holder.releasePlayer();
    }

    private int upcomingPreferenceCount() {
        int count = 0;
        ContentPreference mode = ShitTokContentPreference.get(activity);
        for (int i = selectedPosition + 1; i < items.size(); i++)
            if (sourceMixer.matchesPreference(items.get(i), mode)) count++;
        return count;
    }

    private long preferencePreparationHealthySince;

    private void resetPreferencePreparationHealth() {
        preferencePreparationHealthySince = 0L;
        sourceMixer.preferencePreparation.playbackHealthy(false);
    }

    private void maybePrepareUnopenedPreference() {
        ExoPlayer selectedPlayer = hasSelectedPage() ? playbackOwner.player(items.get(selectedPosition).url) : null;
        ChaosHolder selectedHolder = holderAt(selectedPosition);
        if (pendingCategoryActivation || isPaging() || interactionOverlayOpen() || selectedHolder == null
                || selectedHolder.failurePending || selectedHolder.maintenanceFailure
                || deferredReturnDiscoveryUntilMs != 0L || selectedPlayer == null
                || !playbackOwner.allowsSpeculativeWork() || !selectedPlayer.isPlaying()
                || selectedPlayer.getPlaybackState() != Player.STATE_READY
                || playbackOwner.selectedBufferedMs() < 4_000L || usableUpcomingCount() == 0
                || !ChaosPreloadPolicy.allowsLookAhead(activity)) {
            resetPreferencePreparationHealth();
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (preferencePreparationHealthySince == 0L) preferencePreparationHealthySince = now;
        if (now - preferencePreparationHealthySince < 1_500L || poolLoading) return;
        sourceMixer.preferencePreparation.playbackHealthy(true);
        ShitTokSourceMode sources = queueSources;
        Set<String> hidden = new HashSet<>(hiddenUrls);
        Set<String> blocked = new HashSet<>(blockedCreatorKeys);
        Set<String> recent = new HashSet<>(recentSet);
        for (ShitTokCategoryStrip.Tab tab : ShitTokCategoryStrip.Tab.values()) {
            if (tab.preference == queuePreference) continue;
            if (sourceMixer.preparePreference(activity, sources, tab.preference,
                    (mode, candidates, allowed) -> verifyPreferenceStarter(tab, sources, candidates,
                            hidden, blocked, recent, allowed),
                    () -> post(this::loadMorePool))) break;
        }
    }

    private ShitTokPreferencePreparation.Outcome verifyPreferenceStarter(ShitTokCategoryStrip.Tab tab,
            ShitTokSourceMode sources, List<NativeContentItem> candidates,
            Set<String> hidden, Set<String> blocked, Set<String> recent,
            java.util.function.BooleanSupplier allowed) {
        long started = android.os.SystemClock.elapsedRealtime();
        ArrayList<NativeContentItem> fresh = new ArrayList<>();
        for (NativeContentItem item : candidates) {
            String provider = ShitTokAspectPriority.sourceKey(item);
            if (!isMedia(item) || !ShitTokFeedSources.allows(tab, provider) || !sources.allowsSource(provider)
                    || !sourceMixer.matchesStarter(item, sources, tab.preference) || hidden.contains(item.url)
                    || ShitTokBlockedCreatorStore.isBlocked(blocked, item) || shouldDeferHistory(item, recent)) continue;
            fresh.add(item);
            if (fresh.size() == 3) break;
        }
        ShitTokOrientationAdmission.Result result = orientationAdmission.preparePreferenceStarter(fresh, item -> {
            if (!allowed.getAsBoolean() || !inventoryResolutionSlots.tryAcquire())
                throw new ShitTokOrientationAdmission.Deferred();
            long at = android.os.SystemClock.elapsedRealtime();
            try {
                CrazyShitRepository.StreamInfo stream = resolvePlayable(item);
                if (stream != null && (seenStore.seen(item.url, stream.mediaUrl)
                        || seenStore.liked(item.url, stream.mediaUrl))) throw new ShitTokOrientationAdmission.Filtered();
                return stream;
            } finally {
                inventoryResolutionSlots.release();
                ShitTokPoolDiagnostics.record("preferenceStarterResolve",
                        tab.value + " ms=" + (android.os.SystemClock.elapsedRealtime() - at));
            }
        }, allowed, tab == ShitTokCategoryStrip.Tab.CHAOS);
        ShitTokPoolDiagnostics.record("preferenceStarterAdmission",
                tab.value + " ms=" + (android.os.SystemClock.elapsedRealtime() - started)
                        + " eligible=" + result.items.size());
        if (!result.items.isEmpty()) return ShitTokPreferencePreparation.Outcome.READY;
        return result.deferred ? ShitTokPreferencePreparation.Outcome.DEFERRED
                : ShitTokPreferencePreparation.Outcome.MISS;
    }

    private void requestPreferenceCandidates() {
        if (searchCoverActive) return;
        if (closed || !active || !hostResumed || !sourceRequestsEnabled) return;
        if (!needsContinuity()) return;
        sourceMixer.requestPreference(activity, () -> post(() -> {
            if (!closed && active && hostResumed) loadMorePool();
        }));
    }

    private void loadMorePool() {
        if (queueUpdateDepth > 0) { refillDeferred = true; return; }
        if (searchCoverActive) return;
    applyContentPreference();
    if (queuePreference == null) queuePreference = ShitTokContentPreference.get(activity);
    if (queuePreference != ShitTokContentPreference.get(activity) || queueSources != ShitTokSourceMode.get(activity)) return;
    if (deferredReturnDiscoveryUntilMs != 0L) {
        if (android.os.SystemClock.elapsedRealtime() < deferredReturnDiscoveryUntilMs) return;
        resetDeferredReturnDiscovery();
    }
    requestPreferenceCandidates();
    // A late matching result gets a bounded turn through the SAME admission gates even
    // when normal providers already filled the runway or left an orientation reservoir.
    final boolean preferenceBatch = creatorInventory()
            && (sourceMixer.hasPreference(queuePreference) || !orientationPending.isEmpty() || orientationAdmission.hasPending())
            && needsContinuity();
    if (creatorInventory() && !preferenceBatch) {
        ShitTokSourcePool.Health health = sourceMixer.preferenceHealth();
        if (items.isEmpty() && (health == ShitTokSourcePool.Health.EMPTY
                || health == ShitTokSourcePool.Health.FAILED || health == ShitTokSourcePool.Health.UNAVAILABLE)) {
            initialProgress.setVisibility(View.GONE);
            empty.setText("No verified " + queuePreference.label + " clips ready yet. Retrying…");
            empty.setVisibility(View.VISIBLE);
        }
        return;
    }
    // Source production, resolution and portrait inspection are background work. Do not hold
    // the startup handoff behind the first draw/post queue; UI application still waits for idle.
    if (closed || !active || !hostResumed || poolLoading || !sourceRequestsEnabled || !hasReservoirRoom(items.size(), selectedPosition)) return;
    continuityWorkNeeded = needsContinuity();
    boolean continuity = active && hostResumed && continuityWorkNeeded;
    if (!items.isEmpty() && !continuityWorkNeeded && !preferenceBatch) return;
    if (!creatorInventory() && continuity && consecutiveDryLoads >= 4) { requestContinuity(); return; }
    if (!items.isEmpty() && active && !playbackOwner.allowsSpeculativeWork() && !continuity) {
        refillDeferred = true;
        return;
    }
    refillDeferred = false;
    poolLoading = true;
    sourceMixer.preferencePreparation.playbackHealthy(false);
    if (items.isEmpty()) AppPerformance.startup("feed_request");
    final InventoryBatch work = new InventoryBatch();
    inventoryBatch = work;
    if (items.isEmpty()) { initialProgress.setVisibility(View.VISIBLE); empty.setVisibility(View.GONE); }
    final boolean continuityBatch = continuity || items.isEmpty();
    final boolean protectedRefill = continuityBatch;
    recordRunway("refillStart");
    final int admissionLimit = preferenceBatch ? Math.max(1, desiredRunway() - usableUpcomingCount()
            + (items.isEmpty() ? 1 : 0)) : continuityBatch
            ? Math.max(1, desiredRunway() - usableUpcomingCount() + (items.isEmpty() ? 1 : 0))
            : Integer.MAX_VALUE;
    final int batchGeneration = feedGeneration;
    final ContentPreference batchPreference = queuePreference;
    final ShitTokSourceMode batchSources = queueSources;
    final long batchDiagnosticToken = ShitTokPoolDiagnostics.modeToken();
    final int batchPosition = selectedPosition, batchTarget = desiredRunway();
    final ArrayList<NativeContentItem> pendingCandidates = new ArrayList<>(orientationPending);
    orientationPending.clear();
    final Set<String> hiddenSnapshot = new HashSet<>(hiddenUrls);
    final Set<String> recentSnapshot = new HashSet<>(recentSet);
    final Set<String> sessionSnapshot = new HashSet<>(sessionUrls);
    final Set<String> identitySnapshot = new HashSet<>(sessionIdentities);
    final Set<String> blockedSnapshot = new HashSet<>(blockedCreatorKeys);

    final boolean startupBatch = items.isEmpty();
    final boolean historyExplorationSpent = consecutiveDryLoads >= 3;
    io.execute(() -> {
        if (work.cancelled || batchGeneration != feedGeneration || closed) return;
        if (startupBatch) AppPerformance.startup("feed_worker");
        long batchStartedAt = android.os.SystemClock.elapsedRealtime();
        long explorationDeadline = System.nanoTime()
                + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(UNSEEN_EXPLORATION_MS);
        java.util.concurrent.BlockingQueue<NativeContentItem> candidates =
                new java.util.concurrent.ArrayBlockingQueue<>(48);
        java.util.concurrent.BlockingQueue<NativeContentItem> historyFallback =
                new java.util.concurrent.ArrayBlockingQueue<>(MAX_HISTORY_FALLBACK);
        Set<String> offered = java.util.concurrent.ConcurrentHashMap.newKeySet();
        Set<String> resolvedIdentities = java.util.concurrent.ConcurrentHashMap.newKeySet();
        resolvedIdentities.addAll(identitySnapshot);
        java.util.concurrent.atomic.AtomicBoolean productionDone = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicBoolean acceptingCandidates = new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.function.Consumer<List<NativeContentItem>> offer = batch -> {
            ArrayList<NativeContentItem> fresh = new ArrayList<>();
            for (NativeContentItem item : batch) {
                if (work.cancelled || !acceptingCandidates.get() || batchGeneration != feedGeneration || closed || Thread.currentThread().isInterrupted()) {
                    sourceMixer.abandon(item); continue;
                }
                rawCandidates.incrementAndGet();
                if (ShitTokContentPreference.get(activity) != batchPreference || ShitTokSourceMode.get(activity) != batchSources) { sourceMixer.abandon(item); continue; }
                if (!isMedia(item) || !eligiblePreference(item, batchPreference) || hiddenSnapshot.contains(item.url)
                        || ShitTokBlockedCreatorStore.isBlocked(blockedSnapshot, item)) continue;
                // Drain cached negative results rather than returning them to a full
                // raw pool and repeatedly offering them ahead of new creator pages.
                if (preferenceBatch && orientationAdmission.rejectsKnown(item)) {
                    ShitTokPoolDiagnostics.modeEvent(batchDiagnosticToken, "knownRejectedDrained", 1);
                    continue;
                }
                String identity = ShitTokContentIdentity.page(item.url);
                if (sessionSnapshot.contains(item.url) || identitySnapshot.contains(identity)) {
                    duplicateRejected.incrementAndGet(); continue;
                }
                // Partial provider results appear again in the final mix. Do not count this
                // internal replay as a new fetched duplicate or spend another lookup.
                if (!offered.add(identity)) continue;
                if (shouldDeferHistory(item, recentSnapshot)) {
                    if (!historyFallback.offer(item)) sourceMixer.abandon(item);
                } else {
                    fresh.add(item);
                }
            }
            synchronized (candidates) { for (NativeContentItem item : fresh) {
                if (work.cancelled || !acceptingCandidates.get() || batchGeneration != feedGeneration || closed) {
                    sourceMixer.abandon(item); continue;
                }
                if (preferenceBatch && !orientationAdmission.canOffer(item, candidates)) {
                    offered.remove(ShitTokContentIdentity.page(item.url));
                    sourceMixer.returnPreference(batchPreference, item);
                    continue;
                }
                AppPerformance.startupItem("candidate_offered", item.url, "fresh=true");
                if (candidates.offer(item)) activity.runOnUiThread(() -> {
                    if (closed || work.cancelled || inventoryBatch != work || batchGeneration != feedGeneration || firstCandidate) return;
                    firstCandidate = true;
                    AppPerformance.startup("candidate");
                    AppPerformance.record("ShitTok first candidate", android.os.SystemClock.elapsedRealtime() - initializedAt);
                }); else sourceMixer.abandon(item);
            } }
        };
        java.util.concurrent.Future<?> harvest = io.submit(() -> {
            Trace.beginSection("zc.shittok.batch_fetch");
            try {
                if (!batchSources.creatorInventory(batchPreference)) {
                    List<NativeContentItem> recovered = sourceMixer.takeReadyCandidates();
                    offer.accept(recovered);
                    offer.accept(pendingCandidates);
                    if (recovered.isEmpty() && pendingCandidates.isEmpty()) {
                        List<NativeContentItem> mixed = sourceMixer.loadRandomBatch(activity, protectedRefill, offer,
                                UNSEEN_EXPLORATION_MS);
                        offer.accept(mixed); // URL dedup keeps partial results singular.
                    }
                    // Reuse the source decks. Draining watched results frees provider slots
                    // for their next pages without adding work to the swipe path.
                    while (!historyExplorationSpent && !historyFallback.isEmpty() && !work.cancelled && acceptingCandidates.get()
                            && continuityWorkNeeded && batchGeneration == feedGeneration
                            && System.nanoTime() < explorationDeadline
                            && !Thread.currentThread().isInterrupted()) {
                        if (candidates.remainingCapacity() >= ShitTokSourcePool.MAX_READY_PER_SOURCE) {
                            offer.accept(sourceMixer.takeReadyCandidates());
                            sourceMixer.recoverSources(activity);
                        }
                        try { Thread.sleep(75L); }
                        catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); break; }
                    }
                } else {
                    offer.accept(pendingCandidates);
                    // Keep this bounded producer open while admission is running. Previously
                    // a new healthy source result waited behind the entire six-second snapshot.
                    long until = android.os.SystemClock.elapsedRealtime() + ShitTokOrientationAdmission.BATCH_MILLIS;
                    while (!work.cancelled && acceptingCandidates.get() && continuityWorkNeeded
                            && batchGeneration == feedGeneration && !closed
                            && batchPreference == ShitTokContentPreference.get(activity)
                            && batchSources == ShitTokSourceMode.get(activity)
                            && android.os.SystemClock.elapsedRealtime() < until
                            && (historyFallback.isEmpty() || (!historyExplorationSpent
                                    && System.nanoTime() < explorationDeadline))
                            && !Thread.currentThread().isInterrupted()) {
                        if (candidates.remainingCapacity() >= ShitTokSourcePool.MAX_READY_PER_SOURCE)
                            offer.accept(sourceMixer.takePreference(batchPreference, item -> orientationAdmission.canOffer(item, candidates)));
                        sourceMixer.requestPreference(activity, () -> {}, continuityWorkNeeded);
                        try { Thread.sleep(50L); }
                        catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); break; }
                    }
                }
            } finally {
                synchronized (candidates) {
                    NativeContentItem item;
                    while ((item = historyFallback.poll()) != null) {
                        if (work.cancelled || !acceptingCandidates.get() || batchGeneration != feedGeneration
                                || closed || !candidates.offer(item)) sourceMixer.abandon(item);
                    }
                }
                productionDone.set(true);
                ShitTokPoolDiagnostics.record("harvest", "ms=" + (android.os.SystemClock.elapsedRealtime() - batchStartedAt));
                Trace.endSection();
            }
        });
        work.harvest = harvest;
        if (work.cancelled) harvest.cancel(true);
        final int[] publishedFresh = {0};
        ShitTokOrientationAdmission.Result verified = orientationAdmission.admitConcurrent(candidates, productionDone::get,
                item -> {
                    long at = android.os.SystemClock.elapsedRealtime();
                    inventoryResolutionSlots.acquire();
                    inventoryResolutions.incrementAndGet();
                    try {
                        // Recheck after waiting for the shared URL-resolution lane, before any
                        // provider-specific resolver (including WebView validation) is started.
                        if (!continuityWorkNeeded) throw new ShitTokOrientationAdmission.Deferred();
                        AppPerformance.startup("resolve_begin");
                        CrazyShitRepository.StreamInfo startup = ChaosStartupPreloader.takeResolved(item.url);
                        CrazyShitRepository.StreamInfo resolved = startup != null ? startup : resolvePlayable(item);
                        if (resolved != null) {
                            AppPerformance.startup("resolved");
                            if (!resolvedIdentities.add(ShitTokContentIdentity.media(resolved.mediaUrl))) {
                                duplicateRejected.incrementAndGet(); throw new ShitTokOrientationAdmission.Filtered();
                            }
                        }
                        return resolved;
                    }
                    finally {
                        inventoryResolutions.decrementAndGet();
                        inventoryResolutionSlots.release();
                        ShitTokPoolDiagnostics.record("inventoryResolve", "ms=" + (android.os.SystemClock.elapsedRealtime() - at));
                    }
                }, () -> !work.cancelled && batchGeneration == feedGeneration && mayVerifyOrientation(),
                () -> continuityWorkNeeded,
                () -> Math.min(MAX_QUEUED_AHEAD, admissionLimit + Math.max(0, selectedPosition - batchPosition)
                        + Math.max(0, desiredRunway() - batchTarget)), partial -> {
                    if (!work.cancelled && batchGeneration == feedGeneration) sourceMixer.verifiedCandidates(partial.items);
                    activity.runOnUiThread(() -> deferUiApply(() -> timedAdmissionApply("partial", () -> {
                        if (closed || work.cancelled || inventoryBatch != work || batchGeneration != feedGeneration) return;
                        if (!preferenceBatch && !items.isEmpty()
                                && (!continuityBatch || usableUpcomingCount() >= desiredRunway())) return;
                        streamCache.putAll(partial.streams);
                        ArrayList<NativeContentItem> newItems = new ArrayList<>();
                        for (NativeContentItem item : partial.items) {
                            AppPerformance.startupItem("admission_ui", item.url, "recent=" + recentSnapshot.contains(item.url));
                            if (discoveryExcluded(item, partial.streams.get(item.url))) continue;
                            newItems.add(item);
                        }
                        int before = items.size();
                        appendUnique(newItems);
                        int added = items.size() - before;
                        for (int i = before; i < items.size(); i++)
                            publishedFresh[0]++;
                        if (added == 0) return;
                        if (before == 0) ShitTokPoolDiagnostics.record("preferenceFirstAdmission",
                                batchSources.key(batchPreference) + " batchMs="
                                        + (android.os.SystemClock.elapsedRealtime() - batchStartedAt));
                        recordFirstAdmission();
                        publishQueueChange(() -> adapter.notifyItemRangeInserted(before, added));
                        sequenceFilteredFuture();
                        syncLoadingIndicators();
                        empty.setVisibility(View.GONE);
                        reconcilePlaybackWindow();
                        resolveAhead(selectedPosition);
                        if (active && hostResumed) playSelected();
                        post(this::activatePendingCategory);
                        tryPendingAutoAdvance();
                        requestContinuity();
                    })));
                }, count -> {
                    if (inventoryBatch == work) { activeAdmissions = count; inventoryCandidateCount = candidates.size(); }
                });
        ArrayList<NativeContentItem> leftovers = new ArrayList<>(verified.remaining);
        synchronized (candidates) {
            acceptingCandidates.set(false);
            candidates.drainTo(leftovers);
        }
        if (inventoryBatch == work) inventoryCandidateCount = 0;
        harvest.cancel(true);
        List<NativeContentItem> prioritizedFresh = aspectPriority.order(verified.items, random,
                ShitTokCategoryStrip.Tab.selected(activity) == ShitTokCategoryStrip.Tab.CHAOS);

        activity.runOnUiThread(() -> deferUiApply(() -> timedAdmissionApply("final", () -> {
            if (closed || work.cancelled || inventoryBatch != work || batchGeneration != feedGeneration) return;
            poolLoading = false;
            long duration = android.os.SystemClock.elapsedRealtime() - batchStartedAt;
            refillCount++; modeRefills++; refillTotalMs += duration; refillMaxMs = Math.max(refillMaxMs, duration);
            ShitTokPoolDiagnostics.record("refillComplete", "admitted=" + verified.items.size() + " ms=" + duration);
            for (NativeContentItem candidate : leftovers) {
                if (orientationPending.size() >= 48) { sourceMixer.abandon(candidate); continue; }
                orientationPending.addLast(candidate);
            }
            refillDeferred = verified.deferred;
            streamCache.putAll(verified.streams);
            int before = items.size();
            appendUnique(prioritizedFresh);

            int freshAdded = publishedFresh[0] + items.size() - before;
            if (freshAdded == 0 && !verified.deferred) { consecutiveDryLoads++; dryRefillCount++; modeDryRefills++; }
            else if (freshAdded > 0) consecutiveDryLoads = 0;

            int added = items.size() - before;
            if (added > 0 && freshAdded == 0) consecutiveDryLoads = 0;

            if (added > 0) {
                recordFirstAdmission();
                long mutationAt = android.os.SystemClock.elapsedRealtime();
                publishQueueChange(() -> adapter.notifyItemRangeInserted(before, added));
                sequenceFilteredFuture();
                ShitTokPoolDiagnostics.record("adapterInsert", "count=" + added + " ms=" + (android.os.SystemClock.elapsedRealtime()-mutationAt));
                syncLoadingIndicators();
                empty.setVisibility(View.GONE);
                reconcilePlaybackWindow();
                resolveAhead(selectedPosition);
                if (active && hostResumed) playSelected();
                post(this::activatePendingCategory);
                tryPendingAutoAdvance();
            }

            requestContinuity();
            if (sourceMixer.hasPreference(queuePreference) && upcomingPreferenceCount() < desiredRunway())
                post(this::loadMorePool);
            boolean autoAdvanceNeedsNext = (autoAdvancePending && autoAdvanceFrom + 1 >= items.size())
                    || continuityWorkNeeded;
            boolean canResumeVerification = !isPaging() && mayVerifyOrientation();
            if (shouldRefillVerifiedPool(items.size() - selectedPosition, creatorInventory() ? 0 : consecutiveDryLoads,
                    autoAdvanceNeedsNext, verified.deferred, canResumeVerification)) {
                // Yield between bounded batches, including batches rejected by orientation.
                postDelayed(this::loadMorePool, freshAdded == 0 ? 350L : 60L);
            } else if (items.isEmpty() && !verified.deferred) {
                initialProgress.setVisibility(View.GONE);
                empty.setText("No playable videos yet. Retrying…");
                empty.setVisibility(View.VISIBLE);
            } else if (autoAdvancePending && autoAdvanceFrom + 1 >= items.size()) {
                autoAdvancePending = false;
                autoAdvanceFrom = -1;
            }
        })));
    });
}

    private void recordFirstAdmission() {
        if (firstAdmitted) return;
        firstAdmitted = true;
        AppPerformance.startup("admitted");
        AppPerformance.record("ShitTok first admitted / resolved", android.os.SystemClock.elapsedRealtime() - initializedAt);
    }

    private void pauseRunwayClock() {
        if (belowRunwayAt != 0) belowRunwayMs += android.os.SystemClock.elapsedRealtime() - belowRunwayAt;
        belowRunwayAt = 0;
        if (modeBelowAt != 0) modeBelowMs += android.os.SystemClock.elapsedRealtime() - modeBelowAt;
        modeBelowAt = 0;
    }

    private boolean mayVerifyOrientation() {
        return !closed && hostResumed && (verifiedQueueEmpty
                || (active && continuityWorkNeeded)
                || (orientationWorkAllowed && playbackOwner.allowsSpeculativeWork()));
    }

    static boolean shouldRefillVerifiedPool(int remainingItems, int dryLoads,
            boolean autoAdvanceNeedsNext, boolean deferred, boolean canResumeVerification) {
        return (remainingItems < 14 || autoAdvanceNeedsNext) && dryLoads < 4
                && (!deferred || canResumeVerification);
    }

    static boolean shouldPreparePlayer(int position, int selectedPosition) {
        return position >= selectedPosition - 1 && position <= selectedPosition + 1;
    }

    static boolean shouldWarmOffTab(boolean hostResumed, int remainingItems) {
        return hostResumed && remainingItems <= LOAD_AHEAD_AT;
    }

    static boolean hasReservoirRoom(int itemCount, int selectedPosition) {
        return itemCount - selectedPosition < MAX_QUEUED_AHEAD;
    }

    static boolean shouldOpenCreatorGallerySwipe(float dx, float dy, float threshold) {
        return threshold > 0f
                && dx <= -threshold
                && Math.abs(dx) > Math.abs(dy) * 1.20f;
    }

    static float creatorSwipeContentTranslation(float dx, float width) {
        if (width <= 0f) return 0f;
        return Math.max(-width, Math.min(0f, dx));
    }

    static float creatorSwipePreviewTranslation(float dx, float width) {
        if (width <= 0f) return 0f;
        return width + creatorSwipeContentTranslation(dx, width);
    }

    private FrameLayout creatorSwipeHost() {
        View content = activity.findViewById(android.R.id.content);
        return content instanceof FrameLayout ? (FrameLayout) content : null;
    }

    private View creatorSwipeSourceView() {
        FrameLayout hostView = creatorSwipeHost();
        if (hostView == null || hostView.getChildCount() == 0) return null;
        for (int index = 0; index < hostView.getChildCount(); index++) {
            View child = hostView.getChildAt(index);
            if (child != creatorSwipePreview && child != creatorSwipeSourceSnapshot) return child;
        }
        return null;
    }

    private void ensureCreatorSwipePreview() {
        FrameLayout hostView = creatorSwipeHost();
        if (hostView == null) return;
        if (creatorSwipePreview == null) {
            creatorSwipePreview = new ShitTokCreatorSwipePreview(activity);
            creatorSwipePreview.setVisibility(View.GONE);
        }
        if (creatorSwipePreview.getParent() != hostView) {
            if (creatorSwipePreview.getParent() instanceof ViewGroup) {
                ((ViewGroup) creatorSwipePreview.getParent()).removeView(creatorSwipePreview);
            }
            hostView.addView(
                    creatorSwipePreview,
                    new FrameLayout.LayoutParams(-1, -1)
            );
        }
    }

    private void beginEromeSwipePreview(NativeContentItem clip, String transitionToken) {
        ensureCreatorSwipePreview();
        if (creatorSwipePreview == null || clip == null) return;
        creatorSwipePreview.animate().withEndAction(null);
        creatorSwipePreview.animate().cancel();
        creatorSwipePreview.showEromeAlbum(clip.title, clip.uploader);
        FrameLayout hostView = creatorSwipeHost();
        float width = hostView == null ? Math.max(1, getWidth()) : Math.max(1, hostView.getWidth());
        creatorSwipePreview.setTranslationX(width);
        installCreatorSwipeSourceSnapshot(transitionToken);
        creatorSwipePreview.bringToFront();
    }

    private void beginCreatorSwipePreview(String creator, String transitionToken) {
        ensureCreatorSwipePreview();
        if (creatorSwipePreview == null || creator == null || creator.trim().isEmpty()) return;
        String sessionId = ShitTokCreatorGalleryPreloader.sessionId(activity, creator);
        creatorSwipePreview.animate().withEndAction(null);
        creatorSwipePreview.animate().cancel();
        creatorSwipePreview.showCreator(creator, sessionId);
        FrameLayout hostView = creatorSwipeHost();
        float width = hostView == null ? Math.max(1, getWidth()) : Math.max(1, hostView.getWidth());
        creatorSwipePreview.setTranslationX(width);
        installCreatorSwipeSourceSnapshot(transitionToken);
        creatorSwipePreview.bringToFront();
    }

    private void installCreatorSwipeSourceSnapshot(String transitionToken) {
        FrameLayout hostView = creatorSwipeHost();
        View content = creatorSwipeSourceView();
        Bitmap snapshot = ShitTokTransitionSnapshotStore.snapshot(transitionToken);
        if (hostView == null || content == null || snapshot == null || snapshot.isRecycled()) return;

        clearCreatorSwipeSourceSnapshot(false);
        ImageView frozen = new ImageView(activity);
        frozen.setScaleType(ImageView.ScaleType.FIT_XY);
        frozen.setBackgroundColor(Color.BLACK);
        frozen.setImageBitmap(snapshot);
        frozen.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        frozen.setTranslationX(0f);
        creatorSwipeSourceSnapshot = frozen;
        hostView.addView(frozen, new FrameLayout.LayoutParams(-1, -1));

        content.animate().cancel();
        content.setTranslationX(0f);
        content.setAlpha(0f);
        if (creatorSwipePreview != null) creatorSwipePreview.bringToFront();
    }

    private void clearCreatorSwipeSourceSnapshot(boolean restoreContent) {
        ImageView frozen = creatorSwipeSourceSnapshot;
        creatorSwipeSourceSnapshot = null;
        if (frozen != null) {
            frozen.animate().withEndAction(null);
            frozen.animate().cancel();
            if (frozen.getParent() instanceof ViewGroup) {
                ((ViewGroup) frozen.getParent()).removeView(frozen);
            }
            frozen.setImageDrawable(null);
        }
        if (restoreContent) {
            View content = creatorSwipeSourceView();
            if (content != null) {
                content.animate().cancel();
                content.setTranslationX(0f);
                content.setAlpha(1f);
            }
        }
    }

    private void updateCreatorSwipePreview(float dx, View ignoredContent) {
        View content = creatorSwipeSourceView();
        if (creatorSwipePreview == null || content == null) return;
        float width = Math.max(1f, content.getWidth());
        float contentTranslation = creatorSwipeContentTranslation(dx, width);
        if (creatorSwipeSourceSnapshot != null) {
            creatorSwipeSourceSnapshot.animate().cancel();
            creatorSwipeSourceSnapshot.setTranslationX(contentTranslation);
            content.setTranslationX(0f);
            content.setAlpha(0f);
        } else {
            content.setTranslationX(contentTranslation);
            content.setAlpha(1f);
        }
        creatorSwipePreview.setTranslationX(
                creatorSwipePreviewTranslation(dx, width)
        );
    }

    private void cancelCreatorSwipePreview(View ignoredContent) {
        View content = creatorSwipeSourceView();
        ImageView frozen = creatorSwipeSourceSnapshot;
        if (frozen != null) {
            frozen.animate().cancel();
            if (!ZeroChillMotion.animationsEnabled(activity)) {
                frozen.setTranslationX(0f);
                clearCreatorSwipeSourceSnapshot(true);
            } else {
                frozen.animate()
                        .translationX(0f)
                        .setDuration(ZeroChillMotion.QUICK_MS)
                        .withEndAction(() -> clearCreatorSwipeSourceSnapshot(true))
                        .start();
            }
        } else if (content != null) {
            content.animate().cancel();
            content.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(ZeroChillMotion.QUICK_MS)
                    .start();
        }
        if (creatorSwipePreview == null || creatorSwipePreview.getVisibility() != View.VISIBLE) {
            return;
        }
        creatorSwipePreview.animate().cancel();
        FrameLayout hostView = creatorSwipeHost();
        float width = hostView == null ? Math.max(1, getWidth()) : Math.max(1, hostView.getWidth());
        creatorSwipePreview.animate()
                .translationX(width)
                .setDuration(ZeroChillMotion.QUICK_MS)
                .withEndAction(() -> creatorSwipePreview.setVisibility(View.GONE))
                .start();
    }

    private void commitCreatorSwipePreview(
            View ignoredContent,
            String creator,
            String transitionToken,
            boolean eromeAlbum,
            Runnable openGallery
    ) {
        if (openGallery == null) return;
        View content = creatorSwipeSourceView();
        if (content == null || creatorSwipePreview == null) {
            openGallery.run();
            return;
        }
        float width = Math.max(1f, content.getWidth());
        if (!eromeAlbum) {
            creatorSwipePreview.refreshCreator(
                    creator,
                    ShitTokCreatorGalleryPreloader.sessionId(activity, creator)
            );
        }
        content.animate().cancel();
        if (creatorSwipeSourceSnapshot != null) creatorSwipeSourceSnapshot.animate().cancel();
        creatorSwipePreview.animate().cancel();

        View movingSource = creatorSwipeSourceSnapshot != null
                ? creatorSwipeSourceSnapshot
                : content;
        if (!ZeroChillMotion.animationsEnabled(activity)) {
            movingSource.setTranslationX(-width);
            creatorSwipePreview.setTranslationX(0f);
            ShitTokTransitionSnapshotStore.captureGalleryPreview(
                    transitionToken, creatorSwipePreview);
            creatorGalleryHandoff = true;
            openGallery.run();
            return;
        }

        float progress = Math.max(
                0f,
                Math.min(1f, Math.abs(movingSource.getTranslationX()) / width)
        );
        long duration = Math.max(80L, Math.min(170L, Math.round((1f - progress) * 170f)));
        movingSource.animate()
                .translationX(-width)
                .setDuration(duration)
                .start();
        creatorSwipePreview.animate()
                .translationX(0f)
                .setDuration(duration)
                .withEndAction(() -> {
                    ShitTokTransitionSnapshotStore.captureGalleryPreview(
                            transitionToken, creatorSwipePreview);
                    creatorGalleryHandoff = true;
                    openGallery.run();
                })
                .start();
    }

    private void resetCreatorSwipePreview() {
        clearCreatorSwipeSourceSnapshot(true);
        View content = creatorSwipeSourceView();
        if (content != null) {
            content.animate().cancel();
            content.setTranslationX(0f);
            content.setAlpha(1f);
        }
        if (creatorSwipePreview == null) return;
        creatorSwipePreview.animate().cancel();
        creatorSwipePreview.setVisibility(View.GONE);
        FrameLayout hostView = creatorSwipeHost();
        float width = hostView == null ? Math.max(1, getWidth()) : Math.max(1, hostView.getWidth());
        creatorSwipePreview.setTranslationX(width);
    }

    private static boolean isMedia(NativeContentItem item) {
        return item != null
                && NativeContentItem.KIND_MEDIA.equals(item.kind)
                && item.url != null
                && !item.url.isEmpty();
    }

    private void appendUnique(List<NativeContentItem> candidates) {
        if (candidates == null) return;
        for (NativeContentItem item : candidates) {
            if (!hasReservoirRoom(items.size(), selectedPosition)) break;
            if (!eligiblePreference(item, ShitTokContentPreference.get(activity))) continue;
            if (!eligibleVideoAspect(item.aspectRatioHint)) continue;
            if (isBlockedCreator(item)) continue;
            if (discoveryExcluded(item, streamCache.get(item.url))) continue;
            String identity = ShitTokContentIdentity.page(item.url);
            CrazyShitRepository.StreamInfo candidateStream = streamCache.get(item.url);
            String mediaIdentity = candidateStream == null ? "" : ShitTokContentIdentity.media(candidateStream.mediaUrl);
            if (sessionIdentities.contains(identity) || (!mediaIdentity.isEmpty() && sessionIdentities.contains(mediaIdentity))) continue;
            if (!acceptUnique(item, hiddenUrls, sessionUrls)) continue;
            items.add(item);
            sourceMixer.contributed(item);
            sessionIdentities.add(identity);
            if (!mediaIdentity.isEmpty()) sessionIdentities.add(mediaIdentity);
            while (sessionIdentities.size() > 32768) sessionIdentities.remove(sessionIdentities.iterator().next());
            verifiedQueueEmpty = false;
            AppPerformance.startupItem("metadata_start", item.url, "");
            cacheCreatorMetadata(item);
            AppPerformance.startupItem("metadata_end", item.url, "");
            CrazyShitRepository.StreamInfo preloaded = ChaosStartupPreloader.takeResolved(item.url);
            if (preloaded != null) streamCache.put(item.url, preloaded);
        }
    }

    private void sequenceFilteredFuture() {
        if (!creatorInventory() || isPaging()) return;
        beginQueueUpdate();
        try {
        int first = Math.min(items.size(), selectedPosition + 1);
        if (items.size() - first < 2) return;
        ShitTokCreatorDiversity diversity = new ShitTokCreatorDiversity();
        for (int i = Math.max(0, first - ShitTokCreatorDiversity.WINDOW); i < first; i++)
            diversity.remember(items.get(i));
        List<NativeContentItem> ordered = diversity.order(new ArrayList<>(items.subList(first, items.size())));
        boolean moved = false;
        for (int i = 0; i < ordered.size(); i++) {
            int target = first + i, from = items.indexOf(ordered.get(i));
            if (from == target) continue;
            items.add(target, items.remove(from));
            publishQueueChange(() -> adapter.notifyItemMoved(from, target));
            moved = true;
        }
        if (moved) {
            syncHolderPositionsAfterQueuePrune();
            plannedPosition = -1; plannedUrl = "";
        }
        } finally { endQueueUpdate(); }
    }

    private boolean creatorInventory() {
        return ShitTokSourceMode.get(activity).creatorInventory(ShitTokContentPreference.get(activity));
    }

    private boolean eligibleVideoAspect(float aspect) {
        return ShitTokCategoryStrip.Tab.selected(activity) == ShitTokCategoryStrip.Tab.CHAOS
                ? ShitTokOrientation.immersiveEligible(aspect)
                : ShitTokOrientation.eligible(aspect);
    }

    private boolean eligiblePreference(NativeContentItem item, ContentPreference mode) {
        ShitTokSourceMode sources = ShitTokSourceMode.get(activity);
        String provider = ShitTokAspectPriority.sourceKey(item);
        return ShitTokFeedSources.allows(ShitTokCategoryStrip.Tab.selected(activity), provider)
                && sources.allowsSource(provider)
                && (!sources.creatorInventory(mode) || sourceMixer.matchesPreference(item, mode));
    }

    boolean shouldDeferHistory(NativeContentItem item, Set<String> recent) {
        return recent.contains(item.url) || seenStore.seen(item.url, null) || seenStore.liked(item.url, null);
    }

    private boolean discoveryExcluded(NativeContentItem item, CrazyShitRepository.StreamInfo stream) {
        if (!eligiblePreference(item, ShitTokContentPreference.get(activity))) return true;
        // Historical viewing and likes are deferred at admission and remain eligible as fallback.
        return false;
    }

    private void cacheCreatorMetadata(NativeContentItem media) {
        NativeContentItem identity = creatorMetadata.identity(media);
        creatorFavoriteCache.put(media.url, identity != null && CreatorFavoriteStore.contains(activity, identity));
    }

    private void onCreatorMetadataReady(String url) {
        final int generation = feedGeneration;
        deferUiApply(() -> {
            if (closed || generation != feedGeneration) return;
            int resolvedPosition = indexOfUrl(url);
            if (shareSheet != null && shareSheet.isShowing() && shareSheet.isFor(url)
                    && resolvedPosition >= 0)
                shareSheet.updateCanBlock(!blockCreatorName(items.get(resolvedPosition)).isEmpty());
            if (resolvedPosition >= 0 && isBlockedCreator(items.get(resolvedPosition))) {
                removeBlockedCreatorsFromQueue();
                return;
            }
            // Refresh only creator chrome. Never bind a card, detach a lease or prepare a player.
            for (ChaosHolder holder : playerHolders) {
                if (holder.item != null && url.equals(holder.item.url))
                    holder.bindCreatorChrome(holder.item);
            }
        });
    }

    private void timedAdmissionApply(String phase, Runnable apply) {
        long started = android.os.SystemClock.elapsedRealtime();
        Trace.beginSection("zc.shittok.admission_apply");
        try { apply.run(); }
        finally {
            Trace.endSection();
            long duration = android.os.SystemClock.elapsedRealtime() - started;
            admissionApplyCount++;
            admissionApplyTotalMs += duration;
            admissionApplyMaxMs = Math.max(admissionApplyMaxMs, duration);
            ShitTokPoolDiagnostics.record("admissionApply", "phase=" + phase + " ms=" + duration);
        }
    }

    static boolean acceptUnique(NativeContentItem item, Set<String> hidden,
                                Set<String> session) {
        return isMedia(item) && !hidden.contains(item.url) && session.add(item.url);
    }

    private void cancelPendingAutoAdvance() {
        autoAdvancePending = false;
        autoAdvanceFrom = -1;
        autoAdvanceIssuedFrom = -1;
        autoAdvanceGeneration++;
    }

    private void setAutoScrollEnabled(boolean enabled) {
        if (autoScrollEnabled == enabled) return;
        ChaosHolder selectedBeforeToggle = holderAt(selectedPosition);
        boolean pendingFailedClip = autoAdvancePending && selectedBeforeToggle != null
                && selectedBeforeToggle.failurePending;
        autoScrollEnabled = enabled;
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_AUTO_SCROLL, enabled).apply();
        cancelPendingAutoAdvance();
        autoAdvanceIssuedFrom = -1;
        if (pendingFailedClip) requestAutoAdvance(selectedPosition);
        if (!enabled) {
            ChaosHolder selected = holderAt(selectedPosition);
            if (selected != null && selected.player != null
                    && selected.player.getPlaybackState() == Player.STATE_ENDED) {
                selected.handleCompletion(selected.player);
            }
        }
    }

    private void requestAutoAdvance(int fromPosition) {
        if (fullscreenInputLocked() || searchCoverActive || pendingCategoryActivation || interactionOverlayOpen()) return;
        if (!active || !hostResumed || fromPosition != selectedPosition) return;
        // A completion can arrive again while the pager is settling or its queue is loading.
        if (autoAdvanceIssuedFrom == fromPosition
                || (autoAdvancePending && autoAdvanceFrom == fromPosition)) return;
        final int generation = ++autoAdvanceGeneration;
        autoAdvancePending = true;
        autoAdvanceFrom = fromPosition;

        if (fromPosition + 1 < items.size()) {
            if (items.size() - fromPosition <= LOAD_AHEAD_AT) loadMorePool();
            pager.post(new Runnable() {
                @Override public void run() {
                if (generation != autoAdvanceGeneration || !autoAdvancePending
                        || !active || !hostResumed || selectedPosition != fromPosition) return;
                if (fullscreenInputLocked() || pinchingHolder != null || interactionOverlayOpen()) return;
                if (fromPosition + 1 >= items.size()) return;
                RecyclerView recycler = pagerRecycler();
                if (queueUpdateDepth > 0 || (recycler != null && recycler.hasPendingAdapterUpdates())) {
                    pager.postOnAnimation(this);
                    return;
                }
                cancelPendingAutoAdvance();
                autoAdvanceIssuedFrom = fromPosition;
                pager.setCurrentItem(fromPosition + 1, true);
                }
            });
            return;
        }

        loadMorePool();
    }

    private void tryPendingAutoAdvance() {
        if (fullscreenInputLocked() || searchCoverActive || interactionOverlayOpen()) return;
        if (!autoAdvancePending || pinchingHolder != null) return;
        int fromPosition = autoAdvanceFrom;
        if (!active || !hostResumed || selectedPosition != fromPosition) {
            autoAdvancePending = false;
            autoAdvanceFrom = -1;
            return;
        }
        if (fromPosition + 1 < items.size()) {
            cancelPendingAutoAdvance();
            requestAutoAdvance(fromPosition);
        }
    }

    private void resolveAhead(int position) {
        if (searchCoverActive) return;
        if (pendingCategoryActivation) { activatePendingCategory(); return; }
        if (!hostResumed) return;
        int ahead = ChaosPreloadPolicy.allowsLookAhead(activity)
                && playbackOwner.allowsSpeculativeWork() ? 2 : 0;
        if (!active) ahead = Math.min(ahead, 2);
        for (int offset = 0; offset <= ahead; offset++) {
            resolveAt(position + offset);
        }
        if (ahead > 0 && position > 0) resolveAt(position - 1);
        if (ahead > 0 && position > 1) resolveAt(position - 2);
        requestContinuity();
    }

    private boolean shouldResolvePosition(int position) {
        if (position == selectedPosition) return true;
        if (!ChaosPreloadPolicy.allowsLookAhead(activity) || !playbackOwner.allowsSpeculativeWork()) return false;
        return position >= selectedPosition - 2 && position <= selectedPosition + 2;
    }

    private void resolveAt(int position) {
        if (searchCoverActive) return;
        if (closed) return;
        if (position < 0 || position >= items.size()) return;
        NativeContentItem item = items.get(position);
        boolean categoryReturn = pendingCategoryActivation
                && item.url.equals(pendingCategoryReturnUrl);
        boolean continuity = active && hostResumed && position > selectedPosition
                && position <= selectedPosition + desiredRunway() && continuityWorkNeeded;
        if (isPaging() && !continuity && !categoryReturn) return;
        // Bookmarked clips are foreground handoffs even if they sit behind the
        // outgoing selection. They must not depend on speculative prefetch gates.
        if (position != selectedPosition && !playbackOwner.allowsSpeculativeWork()
                && !continuity && !categoryReturn) return;
        if (continuity && resolving.size() >= 3 && !categoryReturn) return;
        if (streamCache.containsKey(item.url)) {
            prepareVisible(position);
            return;
        }
        if (BunkrMaintenance.isKnown(item.url)) {
            ChaosHolder holder = position == selectedPosition ? holderAt(position) : null;
            if (holder != null && !holder.maintenanceFailure) {
                showSelectedMaintenance(holder, position, item, null);
            }
            return;
        }
        if (unplayable.contains(item.url)) {
            if (shouldRetryResolution(item, position)) {
                unplayable.remove(item.url);
            } else {
                prepareVisible(position);
                return;
            }
        }
        if (!resolving.add(item.url)) {
            prepareVisible(position);
            return;
        }
        if (!sourceRequestsEnabled) { resolving.remove(item.url); return; }

        final int resolutionGeneration = feedGeneration;
        final int resolutionCategoryVersion = categorySelectionVersion;
        final int leaseToken = playbackOwner.token(item.url);
        ShitTokPoolDiagnostics.record("sourceStart", item.url);
        io.execute(() -> {
            if (resolutionGeneration != feedGeneration
                    || resolutionCategoryVersion != categorySelectionVersion || closed) return;
            if (position != selectedPosition && !orientationWorkAllowed
                    && !continuity && !categoryReturn) {
                activity.runOnUiThread(() -> {
                    if (resolutionGeneration == feedGeneration
                            && resolutionCategoryVersion == categorySelectionVersion) {
                        resolving.remove(item.url);
                        requestContinuity();
                    }
                });
                return;
            }
            CrazyShitRepository.StreamInfo stream = ChaosStartupPreloader.takeResolved(item.url);
            Throwable resolveError = null;
            Trace.beginSection("zc.shittok.resolve_bg");
            boolean acquired = false;
            try {
                if (position != selectedPosition) {
                    inventoryResolutionSlots.acquire();
                    acquired = true;
                }
                if (resolutionGeneration != feedGeneration
                        || resolutionCategoryVersion != categorySelectionVersion || closed) return;
                if (stream == null) stream = resolvePlayable(item);
            } catch (Exception error) {
                resolveError = error;
            } finally {
                if (acquired) inventoryResolutionSlots.release();
                Trace.endSection();
            }
            CrazyShitRepository.StreamInfo resolved = stream;
            Throwable failure = resolveError;
            activity.runOnUiThread(() -> {
                if (closed || resolutionGeneration != feedGeneration
                        || resolutionCategoryVersion != categorySelectionVersion) return;
                resolving.remove(item.url);
                ShitTokPoolDiagnostics.record("sourceEnd", item.url);
                if (resolved == null || resolved.mediaUrl == null || resolved.mediaUrl.isEmpty()) {
                    if (BunkrMaintenance.isConfirmed(failure) || BunkrMaintenance.isKnown(item.url)) {
                        int current = indexOfUrl(item.url);
                        ChaosHolder holder = current == selectedPosition ? holderAt(current) : null;
                        if (holder != null && playbackOwner.accepts(item.url, leaseToken)) {
                            showSelectedMaintenance(holder, current, item, failure);
                        }
                        requestContinuity();
                        return;
                    }
                    if (shouldRetryResolution(item, position)) {
                        scheduleResolutionRetry(item, position);
                        return;
                    }
                    unplayable.add(item.url);
                    if (indexOfUrl(item.url) > selectedPosition) consecutiveDryLoads++;
                } else {
                    unplayable.remove(item.url);
                    if (indexOfUrl(item.url) > selectedPosition) consecutiveDryLoads = 0;
                    streamCache.put(item.url, resolved);
                }
                if (playbackOwner.accepts(item.url, leaseToken)) prepareVisible(indexOfUrl(item.url));
                else {
                    ShitTokPoolDiagnostics.record("staleResolution", item.url);
                    wakeSelectedFromCache(item.url);
                }
                if (pendingCategoryActivation) post(this::activatePendingCategory);
                requestContinuity();
            });
        });
    }

    private CrazyShitRepository.StreamInfo resolvePlayable(NativeContentItem item)
            throws Exception {
        if (item == null || item.url == null || item.url.isEmpty()) return null;
        return ChaosStartupPreloader.resolveForFeed(activity.getApplicationContext(), item);
    }

    /** A retired request cannot bind a new lease. The current owner may independently
     * consume the URL cache on the next UI turn, after a quick return to that same item. */
    private void wakeSelectedFromCache(String url) {
        pager.post(() -> {
            if (closed || !active || !hostResumed || isPaging() || queueUpdateDepth > 0 || !hasSelectedPage()) return;
            if (!url.equals(items.get(selectedPosition).url)) return;
            playSelected();
        });
    }

    private boolean shouldRetryResolution(NativeContentItem item, int position) {
        if (item == null || item.url == null || item.url.isEmpty()) return false;
        if (!active || !hostResumed || isPaging() || position != selectedPosition) return false;
        if (position < 0 || position >= items.size()) return false;
        if (!item.url.equals(items.get(position).url)) return false;
        return resolveRetried.add(item.url);
    }

    private void scheduleResolutionRetry(NativeContentItem item, int position) {
        if (closed) return;
        ChaosHolder holder = holderAt(position);
        if (holder != null && holder.isBoundTo(item.url, position)) {
            holder.noteResolutionRetry();
            holder.showRetrying();
        }
        pager.postDelayed(() -> {
            if (closed) return;
            if (position < 0 || position >= items.size()) return;
            if (!item.url.equals(items.get(position).url)) return;
            if ((!active || !hostResumed || position != selectedPosition)
                    && !ChaosPreloadPolicy.allowsLookAhead(activity)) {
                resolveRetried.remove(item.url);
                unplayable.add(item.url);
                return;
            }
            unplayable.remove(item.url);
            resolveAt(position);
        }, STREAM_RETRY_DELAY_MS);
    }

    private void prepareVisible(int position) {
        if (searchCoverActive) return;
        if (!active || !hostResumed || isPaging() || closed || position < 0 || position >= items.size()) return;
        NativeContentItem item = items.get(position);
        CrazyShitRepository.StreamInfo stream = streamCache.get(item.url);
        if (position == selectedPosition) AppPerformance.startupItem("feed_prepare", item.url, "cached=" + (stream != null));
        if (stream != null && shouldPreparePlayer(position, selectedPosition)) {
            if (position == selectedPosition) {
                playbackOwner.source(item.url, stream, playbackOwner.token(item.url));
                ChaosHolder holder = holderAt(position);
                if (holder != null) holder.prepare(stream, true);
            } else queueWarmPrepare(position);
        } else if (unplayable.contains(item.url)) {
            ChaosHolder holder = holderAt(position);
            if (holder != null) holder.showResolutionFailureAndSkip(resolveRetried.contains(item.url));
        }
    }

    private void playSelected() {
        if (searchCoverActive || pendingCategoryActivation) return;
        if (!active || !hostResumed || isPaging() || queueUpdateDepth > 0) return;
        if (selectedPosition < 0 || selectedPosition >= items.size()) return;
        if (plannedPosition != selectedPosition || !items.get(selectedPosition).url.equals(plannedUrl)) reconcilePlaybackWindow();
        // Cache hits prepare through resolveAt; async completion prepares through
        // prepareVisible. Dispatching again here repeats the same player work.
        resolveAt(selectedPosition);
    }

    private void pauseNonSelected(int selected) {
        ChaosHolder owner = holderAt(selected);
        // RecyclerView children omit cached/detached holders that still own a player.
        // Pause the registry too, and use holder identity rather than a stale position.
        for (ChaosHolder holder : new ArrayList<>(playerHolders)) {
            if (holder != owner) {
                if (isPaging()) silencePlayer(holder.player);
                else holder.pauseAndRecord();
            }
        }
        RecyclerView rv = pagerRecycler();
        if (rv == null) return;
        for (int i = 0; i < rv.getChildCount(); i++) {
            RecyclerView.ViewHolder raw = rv.getChildViewHolder(rv.getChildAt(i));
            if (raw instanceof ChaosHolder && raw != owner && !playerHolders.contains(raw)) {
                if (isPaging()) silencePlayer(((ChaosHolder) raw).player);
                else ((ChaosHolder) raw).pauseAndRecord();
            }
        }
    }

    /** Silence is immediate; decoder teardown remains on the existing deferred path. */
    private static void silencePlayer(ExoPlayer player) {
        if (player == null) return;
        try { player.setVolume(0f); } catch (Exception ignored) { }
        try { player.pause(); } catch (Exception ignored) { }
    }

    static boolean shouldRunSwipeMaintenance(
            boolean active,
            boolean hostResumed,
            boolean userPaging,
            int scheduledPosition,
            int selectedPosition
    ) {
        return active && hostResumed && !userPaging &&
                scheduledPosition >= 0 && scheduledPosition == selectedPosition;
    }

    private void scheduleSwipePlayerMaintenance(int position) {
        cancelSwipePlayerMaintenance();
        maintenancePosition = position;
        maintenanceResolveIssued = false;
        pendingWarmPreparePositions.clear();
        if (!shouldRunSwipeMaintenance(
                active, hostResumed, userPaging, maintenancePosition, selectedPosition)) {
            return;
        }
        postDelayed(playerPrepareMaintenanceRunnable, SWIPE_PREPARE_IDLE_DELAY_MS);

    }

    private void cancelSwipePlayerMaintenance() {
        removeCallbacks(playerPrepareMaintenanceRunnable);

        pendingWarmPreparePositions.clear();
        maintenanceResolveIssued = false;
    }

    static boolean shouldQueueWarmPlayer(
            boolean active,
            boolean hostResumed,
            boolean userPaging,
            boolean allowsLookAhead,
            int position,
            int selectedPosition
    ) {
        return active && hostResumed && !userPaging && allowsLookAhead &&
                position != selectedPosition && shouldPreparePlayer(position, selectedPosition);
    }

    private void queueWarmPrepare(int position) {
        if (!shouldQueueWarmPlayer(
                active,
                hostResumed,
                userPaging,
                ChaosPreloadPolicy.allowsLookAhead(activity) && (playbackOwner.allowsSpeculativeWork()
                        || (position == selectedPosition + 1 && position < items.size()
                            && playbackOwner.allowsContinuityPreparation(items.get(position).url))),
                position,
                selectedPosition)) {
            return;
        }
        if (maintenancePosition != selectedPosition) {
            maintenancePosition = selectedPosition;
            maintenanceResolveIssued = false;
        }
        if (!pendingWarmPreparePositions.contains(position)) {
            pendingWarmPreparePositions.addLast(position);
        }
        removeCallbacks(playerPrepareMaintenanceRunnable);
        postDelayed(playerPrepareMaintenanceRunnable, SWIPE_PREPARE_STAGGER_MS);
    }

    private void runDeferredPrepareMaintenance() {
        int position = maintenancePosition;
        if (!shouldRunSwipeMaintenance(
                active, hostResumed, userPaging, position, selectedPosition)) {
            return;
        }
        if (!maintenanceResolveIssued) {
            maintenanceResolveIssued = true;
            if (playbackOwner.allowsSpeculativeWork()) resolveAhead(position);
        }

        Integer target = null;
        while (!pendingWarmPreparePositions.isEmpty()) {
            int candidate = pendingWarmPreparePositions.removeFirst();
            if (candidate != selectedPosition &&
                    shouldPreparePlayer(candidate, selectedPosition) && candidate < items.size()
                    && (playbackOwner.allowsSpeculativeWork()
                        || playbackOwner.allowsContinuityPreparation(items.get(candidate).url))) {
                target = candidate;
                break;
            }
        }
        if (target == null) return;

        if (target < items.size()) {
            NativeContentItem item = items.get(target);
            CrazyShitRepository.StreamInfo stream = streamCache.get(item.url);
            if (stream != null) {
                playbackOwner.source(item.url, stream, playbackOwner.token(item.url));
                ChaosHolder holder = holderAt(target);
                if (holder != null) holder.prepare(stream, false);
            }
        }

        if (!pendingWarmPreparePositions.isEmpty() &&
                shouldRunSwipeMaintenance(
                        active, hostResumed, userPaging, position, selectedPosition)) {
            postDelayed(playerPrepareMaintenanceRunnable, SWIPE_PREPARE_STAGGER_MS);
        }
    }

    private void pauseAll() {
        ShitTokSmartRanker.endSession();
        for (ChaosHolder holder : new ArrayList<>(playerHolders)) {
            holder.pauseAndRecord();
        }

        RecyclerView rv = pagerRecycler();
        if (rv == null) return;
        for (int i = 0; i < rv.getChildCount(); i++) {
            RecyclerView.ViewHolder raw = rv.getChildViewHolder(rv.getChildAt(i));
            if (raw instanceof ChaosHolder && !playerHolders.contains(raw)) {
                ((ChaosHolder) raw).pauseAndRecord();
            }
        }
    }

    private void rememberSelectedPosition() {
        for (ChaosHolder holder : playerHolders) {
            if (holder.boundPosition != selectedPosition || holder.item == null ||
                    holder.player == null || !hasSelectedPage() ||
                    !holder.item.url.equals(items.get(selectedPosition).url)) continue;
            try {
                playbackOwner.remember(holder.item.url, holder.player.getCurrentPosition());
                sessionResume.remember(holder.item.url, holder.player.getCurrentPosition(),
                        holder.player.getDuration());
            } catch (Exception ignored) {
            }
            return;
        }
    }

    private void releaseAllPlayers() {
        for (ChaosHolder holder : new ArrayList<>(playerHolders)) {
            holder.releasePlayer();
        }

        RecyclerView rv = pagerRecycler();
        if (rv != null) {
            for (int i = 0; i < rv.getChildCount(); i++) {
                RecyclerView.ViewHolder raw = rv.getChildViewHolder(rv.getChildAt(i));
                if (raw instanceof ChaosHolder) ((ChaosHolder) raw).releasePlayer();
            }
        }
        playbackOwner.release();
        plannedPosition = -1;
    }

    private void refreshSelectedLikeStateLater(int position) {
        if (pager == null || !active || !hostResumed || isPaging()) return;
        ChaosHolder selected = holderAt(position);
        if (selected != null) selected.clearChangedSocialAccount();
        pager.postDelayed(() -> {
            if (!active || !hostResumed || isPaging() || position != selectedPosition) return;
            ChaosHolder holder = holderAt(position);
            if (holder != null) {
                holder.refreshVideoLikeState();
                holder.refreshCommentCount();
            }
        }, 220L);
    }

    private void syncLoadingIndicators() {
        initialProgress.setVisibility((categoryWaitingVisible && pendingCategoryActivation)
                || (items.isEmpty() && (poolLoading || !initialUiDrawn))
                ? View.VISIBLE : View.GONE);
        for (ChaosHolder holder : new ArrayList<>(playerHolders)) holder.syncLoading();
    }

    private void syncVisibleChrome() {
        RecyclerView rv = pagerRecycler();
        if (rv == null) return;
        for (int i = 0; i < rv.getChildCount(); i++) {
            RecyclerView.ViewHolder raw = rv.getChildViewHolder(rv.getChildAt(i));
            if (raw instanceof ChaosHolder) ((ChaosHolder) raw).syncOrientationChrome();
        }
    }

    static String formatPlaybackTime(long millis) {
        return FullscreenPlayerStyle.time(millis);
    }

    private int portraitViewportBottomInset() {
        if (clearDisplay || activity.getResources().getConfiguration().orientation ==
                Configuration.ORIENTATION_LANDSCAPE) {
            return 0;
        }
        // Keep the old resting viewport while leaving the vertical pager full-height so
        // incoming/outgoing pages can pass visibly behind the floating glass navbar.
        return ZeroChillUi.dimension(activity, R.dimen.zc_bottom_nav_height) + dp(8);
    }

    private ChaosHolder holderAt(int position) {
        if (position < 0 || position >= items.size()) return null;
        RecyclerView rv = pagerRecycler();
        if (rv == null) return null;
        RecyclerView.ViewHolder raw = rv.findViewHolderForAdapterPosition(position);
        if (raw instanceof ChaosHolder && ((ChaosHolder) raw).isBoundTo(items.get(position).url, position))
            return (ChaosHolder) raw;
        // Targeted removals can update our URL binding before RecyclerView completes layout.
        for (int i = 0; i < rv.getChildCount(); i++) {
            raw = rv.getChildViewHolder(rv.getChildAt(i));
            if (raw instanceof ChaosHolder && ((ChaosHolder) raw).isBoundTo(items.get(position).url, position))
                return (ChaosHolder) raw;
        }
        return null;
    }

    private RecyclerView pagerRecycler() {
        if (pager == null || pager.getChildCount() == 0) return null;
        View child = pager.getChildAt(0);
        return child instanceof RecyclerView ? (RecyclerView) child : null;
    }

    private void markSeen(int position) {
        if (position < 0 || position >= items.size()) return;
        String url = items.get(position).url;
        if (url == null || url.isEmpty()) return;
        CrazyShitRepository.StreamInfo stream = streamCache.get(url);
        seenStore.watch(url, stream == null ? null : stream.mediaUrl);
        recentUrls.remove(url);
        recentUrls.addFirst(url);
        recentSet.add(url);
        while (recentUrls.size() > MAX_RECENT) {
            String removed = recentUrls.removeLast();
            recentSet.remove(removed);
        }
        removeCallbacks(saveRecentRunnable);
        postDelayed(saveRecentRunnable, RECENT_SAVE_DELAY_MS);
    }

    private void loadRecent() {
        String raw = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_RECENT, "[]");
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length() && recentUrls.size() < MAX_RECENT; i++) {
                String url = array.optString(i, "").trim();
                if (url.isEmpty() || recentSet.contains(url)) continue;
                recentUrls.addLast(url);
                recentSet.add(url);
            }
        } catch (Exception ignored) {
        }
    }

    private void flushRecent() {
        removeCallbacks(saveRecentRunnable);
        saveRecentNow();
    }

    private void saveRecentNow() {
        seenStore.flush();
        ArrayList<String> snapshot = new ArrayList<>(recentUrls);
        Context appContext = activity.getApplicationContext();
        RECENT_PERSIST_IO.execute(() -> {
            JSONArray array = new JSONArray();
            for (String url : snapshot) array.put(url);
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_RECENT, array.toString())
                    .commit();
        });
    }

    private void loadHidden() {
        String raw = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_HIDDEN, "[]");
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length() && hiddenUrls.size() < MAX_HIDDEN; i++) {
                String url = array.optString(i, "").trim();
                if (!url.isEmpty()) hiddenUrls.add(url);
            }
        } catch (Exception ignored) {
        }
    }

    private void saveHidden() {
        JSONArray array = new JSONArray();
        for (String url : hiddenUrls) array.put(url);
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_HIDDEN, array.toString())
                .apply();
    }

    private void reloadBlockedCreatorKeys() {
        blockedCreatorKeys.clear();
        blockedCreatorKeys.addAll(ShitTokBlockedCreatorStore.keys(activity));
    }

    private String blockCreatorName(NativeContentItem item) {
        String name = ShitTokCreatorMetadata.creatorName(item);
        if (!name.isEmpty()) return name;
        NativeContentItem identity = creatorMetadata.identity(item);
        return identity == null ? "" : identity.title.trim();
    }

    private boolean isBlockedCreator(NativeContentItem item) {
        if (blockedCreatorKeys.isEmpty()) return false;
        String key = ShitTokBlockedCreatorStore.keyForName(blockCreatorName(item));
        return !key.isEmpty() && blockedCreatorKeys.contains(key);
    }

    private void blockCreatorFromChaos(NativeContentItem item) {
        String creator = blockCreatorName(item);
        if (creator.isEmpty()) return;
        String key = ShitTokBlockedCreatorStore.blockName(activity, creator);
        if (key.isEmpty()) return;
        blockedCreatorKeys.add(key);
        ZeroChillToast.makeText(
                activity,
                "Blocked " + creator + " from ShitTok.",
                ZeroChillToast.LENGTH_SHORT
        ).show();
        removeBlockedCreatorsFromQueue();
    }

    private void removeBlockedCreatorsFromQueue() {
        if (isPaging()) { deferUiApply(this::removeBlockedCreatorsFromQueue); return; }
        beginQueueUpdate();
        try {
        ShitTokQueuePruner.Result result = ShitTokQueuePruner.removeBlockedCreators(
                items,
                blockedCreatorKeys,
                selectedPosition, this::isBlockedCreator
        );
        if (result.removed.isEmpty()) return;
        queueSelectedUrl = items.isEmpty() ? "" : items.get(result.targetPosition).url;
        queueSelectedPosition = result.targetPosition;
        syncQueueSelection();
        playbackOwner.revokeAudio();
        plannedPosition = -1;

        cancelPendingAutoAdvance();
        cancelSwipePlayerMaintenance();
        resetCreatorSwipePreview();

        Set<String> removedUrls = new HashSet<>();
        for (NativeContentItem removed : result.removed) {
            if (removed != null && removed.url != null && !removed.url.isEmpty()) {
                removedUrls.add(removed.url);
            }
        }
        for (ChaosHolder holder : new ArrayList<>(playerHolders)) {
            if (holder.item != null && removedUrls.contains(holder.item.url)) {
                holder.releasePlayer();
            }
        }
        if (result.selectedRemoved) {
            sessionResume.clear();
            exitManualFullscreen();
        }

        selectedPosition = result.targetPosition;
        publishQueueChange(() -> {
            for (int index = result.removedPositions.size() - 1; index >= 0; index--)
                adapter.notifyItemRemoved(result.removedPositions.get(index));
        });
        syncHolderPositionsAfterQueuePrune();
        if (items.isEmpty()) {
            initialProgress.setVisibility(View.VISIBLE);
            empty.setVisibility(View.GONE);
            pager.setCurrentItem(0, false);
            loadMorePool();
            return;
        }

        initialProgress.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        pager.setCurrentItem(selectedPosition, false);
        markSeen(selectedPosition);
        pauseNonSelected(selectedPosition);
        resolveAhead(selectedPosition);
        playSelected();
        if (items.size() - selectedPosition <= LOAD_AHEAD_AT) loadMorePool();
        } finally { endQueueUpdate(); }
    }

    private void syncHolderPositionsAfterQueuePrune() {
        Set<ChaosHolder> holders = Collections.newSetFromMap(new IdentityHashMap<>());
        holders.addAll(playerHolders);
        RecyclerView recycler = pagerRecycler();
        if (recycler != null) {
            for (int index = 0; index < recycler.getChildCount(); index++) {
                RecyclerView.ViewHolder holder = recycler.getChildViewHolder(recycler.getChildAt(index));
                if (holder instanceof ChaosHolder) holders.add((ChaosHolder) holder);
            }
        }
        for (ChaosHolder holder : holders) {
            if (holder.item == null) continue;
            int position = items.indexOf(holder.item);
            holder.boundPosition = position >= 0 ? position : RecyclerView.NO_POSITION;
            if (position < 0) { silencePlayer(holder.player); holder.releasePlayer(); }
        }
    }

    private void hideFromChaos(NativeContentItem item) {
        if (isPaging()) { deferUiApply(() -> hideFromChaos(item)); return; }
        if (item == null || item.url == null || item.url.isEmpty()) return;
        beginQueueUpdate();
        try {
        hiddenUrls.remove(item.url);
        hiddenUrls.add(item.url);
        while (hiddenUrls.size() > MAX_HIDDEN) {
            String oldest = hiddenUrls.iterator().next();
            hiddenUrls.remove(oldest);
        }
        saveHidden();

        int index = items.indexOf(item);
        if (index < 0) return;
        ChaosHolder holder = holderAt(index);
        if (holder != null) holder.releasePlayer();
        if (index == selectedPosition) sessionResume.clear();
        streamCache.remove(item.url);
        unplayable.remove(item.url);
        resolving.remove(item.url);
        resolveRetried.remove(item.url);
        items.remove(index);
        publishQueueChange(() -> adapter.notifyDataSetChanged());
        ZeroChillToast.makeText(activity, "Won't show this clip again.", ZeroChillToast.LENGTH_SHORT).show();

        if (items.isEmpty()) {
            exitManualFullscreen();
            selectedPosition = 0;
            initialProgress.setVisibility(View.VISIBLE);
            loadMorePool();
            return;
        }

        int target = selectedPosition;
        pager.setCurrentItem(target, false);
        markSeen(target);
        resolveAhead(target);
        playSelected();
        } finally { endQueueUpdate(); }
    }

    static boolean shouldOfferLandscapeFullscreen(float aspectRatio) {
        return aspectRatio > 1.1f;
    }

    static void configurePosterPresentation(ImageView image, boolean horizontal) {
        // Horizontal posters match the FIT-sized video instead of cropping fullscreen.
        // Transparent unused space leaves the approved ambient backdrop visible.
        image.setScaleType(horizontal ? ImageView.ScaleType.FIT_CENTER
                : ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(horizontal ? Color.TRANSPARENT : Color.rgb(20, 20, 22));
    }

    private boolean isLandscape() {
        return activity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    boolean landscapePlayerVisible() {
        return isLandscape() && !landscapeFullscreenDismissed;
    }

    boolean fullscreenInputLocked() {
        return manualFullscreen || landscapePlayerVisible();
    }

    private void setFeedPagingEnabled(boolean enabled) {
        pager.setUserInputEnabled(enabled && !fullscreenInputLocked());
    }

    private void enterManualFullscreen(ChaosHolder holder) {
        if (manualFullscreen || holder == null || !holder.horizontalVideo || !holder.ownsPlayback()) return;
        manualFullscreen = true;
        landscapeFullscreenDismissed = false;
        continuityTouchTracking = false;
        resetTemporaryPinch();
        resetCreatorSwipePreview();
        cancelPendingAutoAdvance();
        setFeedPagingEnabled(false);
        updateSourceControlVisibility();
        PhoneOrientationPolicy.enterSensorFullscreen(activity);
        holder.syncOrientationChrome();
    }

    private void exitManualFullscreen() {
        if (manualFullscreen) exitFullscreenPlayer();
        else resetTemporaryPinch();
    }

    private void exitFullscreenPlayer() {
        if (!fullscreenInputLocked()) return;
        boolean requestedOrientation = manualFullscreen;
        manualFullscreen = false;
        // Keep late rotation callbacks from reopening the player after a quick Back.
        landscapeFullscreenDismissed = true;
        resetTemporaryPinch();
        setFeedPagingEnabled(active && hostResumed && !searchCoverActive
                && !pendingCategoryActivation && !interactionOverlayOpen());
        updateSourceControlVisibility();
        if (requestedOrientation) PhoneOrientationPolicy.exitFullscreenVideo(activity);
        syncVisibleChrome();
    }

    boolean exitSensorFullscreenForBack() {
        if (fullscreenInputLocked()) {
            exitFullscreenPlayer();
            return true;
        }
        if (pinchingHolder != null) {
            resetTemporaryPinch();
            return true;
        }
        if (clearDisplay) {
            setClearDisplay(false);
            return true;
        }
        return false;
    }

    static boolean shouldEnterClearDisplay(float scale) {
        return scale <= 0.78f;
    }

    static boolean shouldExitClearDisplay(float scale) {
        return scale >= 1.22f;
    }

    static boolean shouldShowPausedChrome(boolean userPaused, boolean clearDisplay) {
        return userPaused && !clearDisplay;
    }

    private void setClearDisplay(boolean clear) {
        if (clearDisplay == clear) return;
        clearDisplay = clear;
        updateSourceControlVisibility();
        host.onClearDisplayChanged(clear);

        RecyclerView rv = pagerRecycler();
        if (rv == null) return;
        for (int i = 0; i < rv.getChildCount(); i++) {
            RecyclerView.ViewHolder raw = rv.getChildViewHolder(rv.getChildAt(i));
            if (raw instanceof ChaosHolder) {
                ((ChaosHolder) raw).applyClearDisplay(clear);
            }
        }
    }

    private void resetTemporaryPinch() {
        resetTemporaryPinch(false);
    }

    private void updateSourceControlVisibility() {
        host.onSourceSelectorVisibilityChanged(!clearDisplay && !fullscreenInputLocked()
                && (pinchingHolder == null || !pinchingHolder.pinch.suppressesFeedChrome()));
    }

    private void resetTemporaryPinch(boolean preserveFocusForPaging) {
        // DRAGGING arrives before currentItem changes. Focus belongs to the feed
        // across that drag, including a canceled swipe; selection migrates it later.
        if (pinchingHolder != null
                && (!preserveFocusForPaging || !pinchingHolder.pinch.isFocusMode())) {
            pinchingHolder.pinch.reset();
        }
        // These existing UI transition boundaries also invalidate optional interaction work.
        for (ChaosHolder holder : playerHolders) holder.cancelInteractionUi();
    }

    private void setChaosMuted(boolean muted) {
        chaosMuted = muted;
        playbackOwner.setMuted(muted);
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_MUTED, muted)
                .apply();
        RecyclerView rv = pagerRecycler();
        if (rv == null) return;
        for (int i = 0; i < rv.getChildCount(); i++) {
            RecyclerView.ViewHolder raw = rv.getChildViewHolder(rv.getChildAt(i));
            if (raw instanceof ChaosHolder) ((ChaosHolder) raw).applyMuteState();
        }
    }

    private void share(NativeContentItem item) {
        if (item == null || item.url.isEmpty() || closed) return;
        if (shareSheet != null && shareSheet.isShowing()) return;
        ChaosHolder holder = holderAt(selectedPosition);
        if (holder == null || holder.item == null || !item.url.equals(holder.item.url)) return;
        final NativeContentItem selectedItem = item;
        ShitTokShareSheet.Actions actions = new ShitTokShareSheet.Actions();
        if (fullscreenInputLocked()) {
            actions.download = holder::downloadCurrentVideo;
            actions.mute = () -> setChaosMuted(!chaosMuted);
            actions.muteLabel = chaosMuted ? "Unmute" : "Mute";
        } else {
            actions.autoScrollOn = autoScrollEnabled;
            actions.canBlock = !blockCreatorName(selectedItem).isEmpty();
            actions.notInterested = () -> hideFromChaos(selectedItem);
            actions.autoScroll = () -> setAutoScrollEnabled(!autoScrollEnabled);
            actions.blockCreator = () -> confirmBlockCreator(selectedItem);
        }
        actions.report = () -> showPlaybackReport(holder);
        actions.details = holder::openCurrentDetails;
        cancelPendingAutoAdvance();
        shareSheet = new ShitTokShareSheet(activity, selectedItem, holder.diagnosticStream(), actions);
        shareSheet.setOnDismissListener(dialog -> {
            if (shareSheet == dialog) shareSheet = null;
            resumeAutoAfterOverlay();
        });
        shareSheet.show();
    }

    private boolean interactionOverlayOpen() {
        return (shareSheet != null && shareSheet.isShowing())
                || (actionDialog != null && actionDialog.isShowing())
                || (commentsDialog != null && commentsDialog.isShowing());
    }

    private void resumeAutoAfterOverlay() {
        // Run after an action has had a chance to open comments or a confirmation dialog.
        pager.post(() -> {
            if (closed || interactionOverlayOpen()) return;
            ChaosHolder holder = holderAt(selectedPosition);
            if (holder == null || holder.player == null) return;
            if (fullscreenInputLocked()) {
                holder.armFullscreenAutoHide();
            } else if (holder.failurePending) requestAutoAdvance(selectedPosition);
            else if (holder.player.getPlaybackState() == Player.STATE_ENDED)
                holder.handleCompletion(holder.player);
        });
    }

    private void confirmBlockCreator(NativeContentItem item) {
        String creator = blockCreatorName(item);
        if (creator.isEmpty()) return;
        cancelPendingAutoAdvance();
        actionDialog = new ZeroChillDialog.Builder(activity)
                .setTitle("Block " + creator + "?")
                .setMessage("Hide this creator from ShitTok. You can unblock them in Settings. Favorites, downloads and history are kept.")
                .setPositiveButton("Block Creator", (dialog, which) -> blockCreatorFromChaos(item))
                .setNegativeButton("Cancel", null).create();
        actionDialog.setOnDismissListener(dialog -> {
            if (actionDialog == dialog) actionDialog = null;
            resumeAutoAfterOverlay();
        });
        actionDialog.show();
    }

    private void retryPlayback(ChaosHolder holder, PlaybackException originalError) {
        if (closed || !sourceRequestsEnabled) return;
        if (holder == null || holder.item == null || holder.item.url.isEmpty()) return;
        NativeContentItem retryItem = holder.item;
        String pageUrl = retryItem.url;
        int position = holder.boundPosition;

        holder.showRetrying();
        holder.releasePlayer();
        playbackOwner.invalidateSource(pageUrl);
        final int retryGeneration = feedGeneration;
        final int retryToken = playbackOwner.token(pageUrl);
        streamCache.remove(pageUrl);
        unplayable.remove(pageUrl);
        if (showSelectedMaintenance(holder, position, retryItem, originalError)) return;
        resolveRetried.add(pageUrl);
        resolving.add(pageUrl);

        io.execute(() -> {
            CrazyShitRepository.StreamInfo refreshed = null;
            Throwable refreshError = null;
            try {
                refreshed = resolvePlayable(retryItem);
            } catch (Exception error) {
                refreshError = error;
            }
            CrazyShitRepository.StreamInfo resolved = refreshed;
            Throwable failure = refreshError;
            activity.runOnUiThread(() -> {
                if (closed || retryGeneration != feedGeneration) return;
                resolving.remove(pageUrl);
                boolean valid = resolved != null
                        && resolved.mediaUrl != null
                        && !resolved.mediaUrl.isEmpty();
                boolean ownsRequest = playbackOwner.accepts(pageUrl, retryToken)
                        && !isPaging() && holder.isBoundTo(pageUrl, position);
                if (!valid && (BunkrMaintenance.isConfirmed(failure) || BunkrMaintenance.isKnown(pageUrl))) {
                    if (!ownsRequest || !showSelectedMaintenance(holder, position, retryItem, failure)) {
                        prepareVisible(position);
                        requestContinuity();
                    }
                    return;
                }
                if (valid) {
                    unplayable.remove(pageUrl);
                    streamCache.put(pageUrl, resolved);
                } else {
                    unplayable.add(pageUrl);
                }

                if (!ownsRequest) {
                    prepareVisible(position);
                    return;
                }
                if (!valid) {
                    holder.showPlayerFailureAndSkip(originalError, "Fresh stream lookup failed");
                    return;
                }
                playbackOwner.source(pageUrl, resolved, retryToken);
                holder.prepare(resolved, active && hostResumed && position == selectedPosition);
            });
        });
    }

    private boolean showSelectedMaintenance(
            ChaosHolder holder, int position, NativeContentItem item, Throwable failure
    ) {
        if (closed || !active || !hostResumed || isPaging()
                || position != selectedPosition || holderAt(position) != holder
                || !holder.isBoundTo(item.url, position)) return false;
        if (!BunkrMaintenanceUi.showIfConfirmed(activity, item.url, failure, () -> {
            if (active && hostResumed && position == selectedPosition && holderAt(position) == holder
                    && holder.isBoundTo(item.url, position)) retryPlayback(holder, holder.lastPlaybackError);
        })) return false;
        holder.showMaintenanceFailure();
        return true;
    }

    private void showPlaybackReport(ChaosHolder holder) {
        if (holder == null || holder.item == null) return;
        ShitTokPoolDiagnostics.setEnabled(true);
        String report = ChaosPlaybackDiagnostics.build(
                holder.item,
                holder.diagnosticStream(),
                holder.lastPlaybackError,
                holder.lastFailureStage,
                holder.retryAttempted
        ) + "\nOrientation: " + orientationAdmission.describe(holder.item.url)
                + "\n\n" + playbackDiagnostics();
        cancelPendingAutoAdvance();
        actionDialog = new ZeroChillDialog.Builder(activity)
                .setTitle("Playback report")
                .setMessage(report)
                .setPositiveButton("Copy", (dialog, which) -> copyPlaybackReport(report))
                .setNeutralButton("Share", (dialog, which) -> sharePlaybackReport(report))
                .setNegativeButton("Close", null)
                .create();
        actionDialog.setOnDismissListener(dialog -> {
            if (actionDialog == dialog) actionDialog = null;
            resumeAutoAfterOverlay();
        });
        actionDialog.show();
    }

    private void copyPlaybackReport(String report) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(
                Context.CLIPBOARD_SERVICE
        );
        if (clipboard == null) {
            ZeroChillToast.makeText(activity, "Clipboard isn't available.", ZeroChillToast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("CrazyShit playback report", report));
        ZeroChillToast.makeText(activity, "Playback report copied.", ZeroChillToast.LENGTH_SHORT).show();
    }

    private void sharePlaybackReport(String report) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "CrazyShit playback report");
        share.putExtra(Intent.EXTRA_TEXT, report);
        activity.startActivity(Intent.createChooser(share, "Share playback report"));
    }

    private void toggleSaved(NativeContentItem item, TextView button) {
        if (item == null) return;
        if (FavoriteStore.contains(activity, item.url)) {
            FavoriteStore.remove(activity, item.url);
            ZeroChillToast.makeText(activity, "Removed from Watch Later.", ZeroChillToast.LENGTH_SHORT).show();
        } else {
            FavoriteStore.add(activity, item);
            ShitTokSmartRanker.action(item, ShitTokSmartRanker.Action.FAVORITE);
            ZeroChillToast.makeText(activity, "Saved to Watch Later.", ZeroChillToast.LENGTH_SHORT).show();
        }
        updateSaveButton(item, button);
        ChaosHolder selected = holderAt(selectedPosition);
        if (selected != null && selected.item != null
                && selected.item.url.equals(item.url)) {
            updateSaveButton(item, selected.landscapeSave);
        }
    }

    private void updateSaveButton(NativeContentItem item, TextView button) {
        if (button == null || item == null) return;
        boolean saved = FavoriteStore.contains(activity, item.url);
        button.setCompoundDrawablesWithIntrinsicBounds(
                0,
                saved ? R.drawable.ic_shittok_rail_bookmark_filled : R.drawable.ic_shittok_rail_bookmark,
                0,
                0
        );
        button.setCompoundDrawableTintList(ColorStateList.valueOf(saved ? AppearanceTheme.accent(activity) : Color.WHITE));
        button.setContentDescription(saved ? "Remove from Watch Later" : "Save to Watch Later");
    }

    private void openInlineComments(NativeContentItem item) {
        resetTemporaryPinch();
        if (item == null || item.url == null || item.url.isEmpty()) return;
        SocialContentContextStore.remember(activity, item);
        if (commentsDialog != null && commentsDialog.isShowing()) return;

        pager.animate().cancel();
        pager.setScaleX(1f);
        pager.setScaleY(1f);
        setFeedPagingEnabled(false);
        cancelPendingAutoAdvance();
        commentsDialog = new InlineCommentsDialog(
                activity,
                item.url,
                item.title,
                item.comments,
                new InlineCommentsDialog.ResizeListener() {
                    @Override
                    public void onSheetTopChanged(int topOnScreen) {
                        resizeForComments(topOnScreen);
                    }

                    @Override
                    public void onSheetClosed() {
                        commentsDialog = null;
                        ChaosHolder holder = holderAt(selectedPosition);
                        if (holder != null) holder.refreshCommentCount();
                        restoreAfterComments();
                        resumeAutoAfterOverlay();
                    }
                }
        );
        commentsDialog.show();
    }

    private void resizeForComments(int sheetTopOnScreen) {
        ChaosHolder holder = holderAt(selectedPosition);
        if (holder != null) holder.resizeMediaForComments(sheetTopOnScreen);
    }

    private void restoreAfterComments() {
        if (pager == null) return;
        pager.animate().cancel();
        pager.setScaleX(1f);
        pager.setScaleY(1f);
        ChaosHolder holder = holderAt(selectedPosition);
        if (holder == null) {
            setFeedPagingEnabled(true);
            return;
        }
        holder.restoreMediaAfterComments(() -> {
            setFeedPagingEnabled(true);
            holder.showControlsTemporarily();
        });
    }

    private void preloadReadyComments(NativeContentItem item, int position) {
        // ZeroChill comments are Supabase rows, so no hidden source-site preload is needed.
    }

    private boolean supportsComments(NativeContentItem item) {
        return item != null && item.url != null && !item.url.trim().isEmpty();
    }

    static int visibleCommentCount(List<ZeroChillSocialRepository.Comment> loaded) {
        if (loaded == null || loaded.isEmpty()) return 0;
        int visible = 0;
        for (ZeroChillSocialRepository.Comment comment : loaded) {
            if (comment != null && !comment.deleted()) visible++;
        }
        return visible;
    }

    private void haptic(View view) {
        if (view == null) return;
        if (!activity.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getBoolean("haptics_enabled", true)) return;
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private GlideUrl imageWithHeaders(String imageUrl, String pageUrl) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("Referer", pageUrl == null || pageUrl.isEmpty() ? SITE : pageUrl)
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        try {
            headers.addHeader("User-Agent", WebSettings.getDefaultUserAgent(activity));
        } catch (Exception ignored) {
        }
        try {
            String cookies = CookieManager.getInstance().getCookie(imageUrl);
            if ((cookies == null || cookies.trim().isEmpty()) && pageUrl != null) {
                cookies = CookieManager.getInstance().getCookie(pageUrl);
            }
            if (cookies != null && !cookies.trim().isEmpty()) headers.addHeader("Cookie", cookies);
        } catch (Exception ignored) {
        }
        return new GlideUrl(imageUrl, headers.build());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class ChaosAdapter extends RecyclerView.Adapter<ChaosHolder> {
        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public ChaosHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ChaosHolder(parent);
        }

        @Override
        public void onBindViewHolder(@NonNull ChaosHolder holder, int position) {
            long at = android.os.SystemClock.elapsedRealtime();
            Trace.beginSection("zc.shittok.holder_bind");
            try { holder.bind(items.get(position), position); holder.attachLease(); }
            finally { Trace.endSection(); }
            ShitTokPoolDiagnostics.record("holderBind", "ms=" + (android.os.SystemClock.elapsedRealtime()-at));
            ShitTokPoolDiagnostics.record("bind", "position=" + position);
            if (!closed && active && hostResumed && !isPaging()) pager.post(() -> {
                if (!closed && active && hostResumed && !isPaging()) playSelected();
            });
        }

        @Override
        public void onViewRecycled(@NonNull ChaosHolder holder) {
            holder.resetVisualMotion();
            holder.detachLease();
            playerHolders.remove(holder);
            ShitTokPoolDiagnostics.record("recycle", "position=" + holder.boundPosition);
            super.onViewRecycled(holder);
        }

        @Override
        public void onViewDetachedFromWindow(@NonNull ChaosHolder holder) {
            holder.resetVisualMotion();
            holder.detachLease();
            super.onViewDetachedFromWindow(holder);
        }

        @Override
        public void onViewAttachedToWindow(@NonNull ChaosHolder holder) {
            int position = holder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION && position < items.size()
                    && holder.item == items.get(position)) {
                holder.boundPosition = position;
            }
            super.onViewAttachedToWindow(holder);
            holder.attachLease();
            if (position == selectedPosition && active && hostResumed && !closed && !isPaging()) pager.post(() -> {
                if (!closed && !isPaging()) playSelected();
            });
        }
    }

    private final class ChaosHolder extends RecyclerView.ViewHolder {
        final FrameLayout root;
        final ShitTokDynamicPinchController pinch;
        final FrameLayout mediaLayer;
        final ImageView ambientBackdrop;
        final ShitTokGpuAmbientView liveAmbientBackdrop;
        final PlayerView playerView;
        final ImageView poster;
        final ProgressBar loading;
        final TextView failure;
        final LinearLayout lower;
        final LinearLayout actionRail;
        final FrameLayout fullscreenSurface;
        final FrameLayout fullscreenOverlay;
        final FullscreenVideoGestures fullscreenGestures;
        final ImageView fullscreenPlayPause;
        final TextView fullscreenSpeed;
        final TextView landscapeDescription;
        final ImageView landscapeAvatar;
        final TextView landscapeCreator;
        final TextView landscapeFavorite;
        final LinearLayout landscapeHeader;
        final LinearLayout landscapeActions;
        final View landscapeTopScrim;
        final View landscapeBottomScrim;
        final TextView landscapeTitle;
        final TextView landscapeLike;
        final TextView landscapeSave;
        final TextView landscapeComments;
        final TextView mute;
        ImageView speedBadge;
        final TextView download;
        final TextView share;
        final TextView fullscreen;
        final FrameLayout creatorAvatarControl;
        final FrameLayout avatarRing;
        final ImageView creatorAvatar;
        final TextView creatorFavoriteBadge;
        final FrameLayout creatorFavoriteTarget;
        final TextView title;
        final TextView meta;
        final TextView like;
        final TextView save;
        final TextView comments;
        final ImageView pausePlayOverlay;
        final SeekBar seekBar;
        final LinearLayout progressRow;
        final TextView elapsedTime;
        final TextView remainingTime;
        final ShitTokScrubPreviewView scrubPreviewView;
        ShitTokScrubPreview scrubPreview;
        ShitTokVideoTaps videoTaps;
        ImageView heartOverlay;
        private final Runnable hideHeart = () -> {
            if (heartOverlay != null) heartOverlay.setVisibility(View.GONE);
        };
        private Boolean progressInteractive;
        private Boolean progressPortrait;
        private Boolean progressChromeVisible;
        private long displayedSecond = Long.MIN_VALUE, displayedRemainingSecond = Long.MIN_VALUE;
        private final Runnable progressTick = new Runnable() {
            @Override public void run() {
                if (!active || !hostResumed || boundPosition != selectedPosition
                        || player == null || !player.isPlaying() || scrubbing
                        || clearDisplay || pinch.isActive()
                        || progressRow.getVisibility() != View.VISIBLE
                        || (landscapeFullscreen() && !controlsVisible)) return;
                updateProgress();
                root.postDelayed(this, 250L);
            }
        };
        ExoPlayer player;
        Player.Listener uiPlayerListener;
        int attachedToken = -1;
        NativeContentItem item;
        NativeContentItem creatorIdentity;
        CrazyShitRepository.StreamInfo stream;
        CrazyShitRepository.StreamInfo lastAttemptedStream;
        PlaybackException lastPlaybackError;
        String lastFailureStage = "";
        int boundPosition = -1;
        float swipeAlpha = 1f;
        boolean controlsVisible = true;
        boolean landscapeControlsMounted;
        String landscapeAvatarKey = "";
        boolean userPaused;
        boolean scrubbing;
        ExoPlayer scrubPlayer;
        long scrubStartPosition, pendingScrubPosition;
        boolean scrubWasPlaying;
        boolean everStarted;
        boolean playbackSuspended;
        boolean retryAttempted;
        boolean failurePending;
        boolean maintenanceFailure;
        boolean horizontalVideo;
        String ambientUrl = "";
        boolean aspectSampleRecorded;
        String videoLikeOwner;
        String commentCountOwner;
        boolean videoLiked;
        boolean videoLikePending;
        int videoLikeCount;
        int videoLikeRequestGeneration;
        int commentCount;
        int commentCountRequestGeneration;
        float videoAspectRatio;
        float creatorSwipeDownX;
        float creatorSwipeDownY;
        boolean creatorSwipeTracking;
        boolean creatorSwipeOpensErome;
        String creatorSwipeAlbumUrl = "";
        String creatorSwipeTransitionToken = "";
        final FrameLayout sourceMark;
        final ImageView sourceMarkIcon;
        final TextView sourceMarkGlyph;
        final TextView sourceMarkName;
        final Runnable hideSourceMarkName;
        final LinearLayout titleRow;
        final LinearLayout metaRow;
        final TextView sourceDot;
        final LinearLayout accessPill;
        final ImageView accessIcon;
        final TextView accessLabel;
        final LinearLayout accessChevrons;
        ValueAnimator accessHintAnimator;

        private final Runnable hideControlsRunnable = this::hideControlsNow;
        private final Runnable skipFailedClipRunnable = () -> {
            if (!failurePending || boundPosition != selectedPosition) return;
            requestAutoAdvance(boundPosition);
        };
        ChaosHolder(ViewGroup parent) {
            super(new ShitTokDynamicPinchController.TouchFrame(parent.getContext()));
            root = (FrameLayout) itemView;
            root.setLayoutParams(new RecyclerView.LayoutParams(-1, -1));
            root.setBackgroundColor(Color.BLACK);
            applyViewportInset();

            mediaLayer = new FrameLayout(activity);
            mediaLayer.setBackgroundColor(Color.BLACK);
            root.addView(mediaLayer, new FrameLayout.LayoutParams(-1, -1));

            // A low-cost, blurred still fills the unused space behind FIT-sized
            // horizontal video. This never captures or blurs live video frames.
            ambientBackdrop = new ImageView(activity);
            ambientBackdrop.setTag("shittok_horizontal_ambient");
            ambientBackdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
            ambientBackdrop.setImageAlpha(135);
            ambientBackdrop.setVisibility(View.GONE);
            ambientBackdrop.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                ambientBackdrop.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                        dp(22), dp(22), android.graphics.Shader.TileMode.CLAMP));
            }
            mediaLayer.addView(ambientBackdrop, new FrameLayout.LayoutParams(-1, -1));
            // GPU-mirror of the same texture, no CPU readback or second player.
            // Static thumbnail remains the fallback on unsupported devices.
            liveAmbientBackdrop = new ShitTokGpuAmbientView(activity);
            mediaLayer.addView(liveAmbientBackdrop, new FrameLayout.LayoutParams(-1, -1));

            poster = new ImageView(activity);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            poster.setBackgroundColor(Color.rgb(20, 20, 22));
            poster.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

            playerView = (PlayerView) LayoutInflater.from(activity)
                    .inflate(R.layout.view_video_player_texture, mediaLayer, false);
            playerView.setUseController(false);
            playerView.setControllerAutoShow(false);
            playerView.hideController();
            playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER);
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            playerView.setBackgroundColor(Color.TRANSPARENT);
            playerView.setShutterBackgroundColor(Color.TRANSPARENT);
            playerView.setContentDescription("Play or pause video");
            mediaLayer.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
            // The player's opaque background and shutter must stay behind the poster.
            mediaLayer.addView(poster, new FrameLayout.LayoutParams(-1, -1));

            loading = new ZeroChillProgressBar(activity);
            loading.setContentDescription("Loading video");
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(44), dp(44));
            lp.gravity = Gravity.CENTER;
            mediaLayer.addView(loading, lp);

            failure = new TextView(activity);
            failure.setTextColor(Color.WHITE);
            failure.setTextSize(14);
            failure.setGravity(Gravity.CENTER);
            failure.setText("Couldn't play this one\nSwipe up for the next video");
            failure.setPadding(dp(28), dp(28), dp(28), dp(28));
            failure.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            failure.setVisibility(View.GONE);
            mediaLayer.addView(failure, new FrameLayout.LayoutParams(-1, -1));

            lower = new LinearLayout(activity);
            lower.setOrientation(LinearLayout.HORIZONTAL);
            lower.setGravity(Gravity.BOTTOM);
            lower.setPadding(dp(16), dp(18), dp(10), dp(30));
            lower.setBackgroundColor(Color.TRANSPARENT);
            lower.setClickable(false);
            lower.setLongClickable(false);
            lower.setFocusable(false);
            FrameLayout.LayoutParams lowerParams = new FrameLayout.LayoutParams(-1, -2);
            lowerParams.gravity = Gravity.BOTTOM;
            root.addView(lower, lowerParams);

            LinearLayout copy = new LinearLayout(activity);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setGravity(Gravity.BOTTOM);
            copy.setBackgroundColor(Color.TRANSPARENT);
            lower.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

            LinearLayout titleRow = new LinearLayout(activity);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            this.titleRow = titleRow;

            accessPill = new LinearLayout(activity);
            accessPill.setTag("shittok_access_pill");
            accessPill.setOrientation(LinearLayout.HORIZONTAL);
            accessPill.setGravity(Gravity.CENTER_VERTICAL);
            accessPill.setMinimumHeight(dp(32));
            accessPill.setPadding(dp(10), dp(4), dp(8), dp(4));
            accessPill.setClickable(true);
            accessPill.setFocusable(true);
            accessPill.setVisibility(View.GONE);
            GradientDrawable accessGlass = new GradientDrawable();
            accessGlass.setColor(0xB3101216);
            accessGlass.setCornerRadius(dp(18));
            accessGlass.setStroke(Math.max(1, dp(1)), 0x38FFFFFF);
            accessPill.setBackground(accessGlass);
            accessIcon = new ImageView(activity);
            accessIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            accessIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            accessPill.addView(accessIcon, new LinearLayout.LayoutParams(dp(14), dp(14)));
            accessLabel = new TextView(activity);
            accessLabel.setTextColor(Color.WHITE);
            accessLabel.setTextSize(12.5f);
            accessLabel.setSingleLine(true);
            accessLabel.setEllipsize(TextUtils.TruncateAt.END);
            accessLabel.setIncludeFontPadding(false);
            accessLabel.setMaxWidth(dp(180));
            accessLabel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams accessLabelParams = new LinearLayout.LayoutParams(-2, -2);
            accessLabelParams.leftMargin = dp(6);
            accessLabelParams.rightMargin = dp(2);
            accessPill.addView(accessLabel, accessLabelParams);
            accessChevrons = new LinearLayout(activity);
            accessChevrons.setOrientation(LinearLayout.HORIZONTAL);
            accessChevrons.setGravity(Gravity.CENTER_VERTICAL);
            accessChevrons.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            accessPill.addView(accessChevrons, new LinearLayout.LayoutParams(-2, -2));
            for (int chevron = 0; chevron < 2; chevron++) {
                ImageView mark = new ImageView(activity);
                mark.setImageResource(R.drawable.ic_more_chevron);
                mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(dp(12), dp(12));
                if (chevron == 1) chevronParams.leftMargin = -dp(6);
                accessChevrons.addView(mark, chevronParams);
            }
            LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(-2, -2);
            pillParams.bottomMargin = dp(8);
            copy.addView(accessPill, pillParams);

            title = new ShitTokOverlayTextView(activity);
            title.setTextColor(Color.WHITE);
            title.setTextSize(18);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setMaxLines(3);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setLineSpacing(dp(2), 1f);
            titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
            copy.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));
            ZeroChillMotion.installPressFeedback(accessPill);
            accessPill.setOnClickListener(v -> {
                if (!title.isClickable()) return;
                title.performClick();
            });

            metaRow = new LinearLayout(activity);
            metaRow.setOrientation(LinearLayout.HORIZONTAL);
            metaRow.setGravity(Gravity.CENTER_VERTICAL);
            metaRow.setPadding(0, dp(7), 0, 0);
            meta = new ShitTokOverlayTextView(activity);
            meta.setTextSize(13);
            meta.setSingleLine(true);
            meta.setEllipsize(TextUtils.TruncateAt.END);
            meta.setMaxWidth(dp(220));
            metaRow.addView(meta, new LinearLayout.LayoutParams(-2, -2));
            sourceDot = new TextView(activity);
            sourceDot.setText("·");
            sourceDot.setTextColor(0x99FFFFFF);
            sourceDot.setTextSize(13);
            sourceDot.setGravity(Gravity.CENTER);
            sourceDot.setIncludeFontPadding(false);
            sourceDot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            sourceDot.setVisibility(View.GONE);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(-2, -2);
            dotParams.leftMargin = dp(5);
            dotParams.rightMargin = dp(1);
            metaRow.addView(sourceDot, dotParams);

            sourceMark = new FrameLayout(activity);
            sourceMark.setTag("shittok_source_mark");
            sourceMark.setClickable(true);
            sourceMark.setFocusable(true);
            sourceMark.setVisibility(View.GONE);
            GradientDrawable sourcePlate = new GradientDrawable();
            sourcePlate.setColor(0xD9141418);
            sourcePlate.setCornerRadius(dp(5));
            sourcePlate.setStroke(Math.max(1, dp(1)), 0x33FFFFFF);
            sourceMark.setBackground(sourcePlate);
            sourceMark.setPadding(dp(2), dp(2), dp(2), dp(2));
            sourceMarkIcon = new ImageView(activity);
            sourceMarkIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            sourceMarkIcon.setAdjustViewBounds(true);
            sourceMarkIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            sourceMark.addView(sourceMarkIcon, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
            sourceMarkGlyph = new TextView(activity);
            sourceMarkGlyph.setTextColor(Color.WHITE);
            sourceMarkGlyph.setTypeface(null, android.graphics.Typeface.BOLD);
            sourceMarkGlyph.setGravity(Gravity.CENTER);
            sourceMarkGlyph.setIncludeFontPadding(false);
            sourceMarkGlyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            sourceMark.addView(sourceMarkGlyph, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
            LinearLayout.LayoutParams markParams = sourceBadgeParams();
            metaRow.addView(sourceMark, markParams);
            sourceMarkName = new ShitTokOverlayTextView(activity);
            sourceMarkName.setTextSize(12);
            sourceMarkName.setTypeface(null, android.graphics.Typeface.BOLD);
            sourceMarkName.setSingleLine(true);
            sourceMarkName.setEllipsize(TextUtils.TruncateAt.END);
            sourceMarkName.setMaxWidth(dp(132));
            sourceMarkName.setVisibility(View.GONE);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-2, -2);
            nameParams.leftMargin = dp(4);
            nameParams.gravity = Gravity.CENTER_VERTICAL;
            metaRow.addView(sourceMarkName, nameParams);
            hideSourceMarkName = () -> sourceMarkName.setVisibility(View.GONE);
            sourceMark.setOnClickListener(v -> {
                haptic(v);
                sourceMarkName.removeCallbacks(hideSourceMarkName);
                if (sourceMarkName.getVisibility() == View.VISIBLE) {
                    sourceMarkName.setVisibility(View.GONE);
                    return;
                }
                sourceMarkName.setVisibility(View.VISIBLE);
                sourceMarkName.announceForAccessibility(sourceMarkName.getText());
                sourceMarkName.postDelayed(hideSourceMarkName, 1600L);
            });
            titleRow.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (sourceMark.getParent() != titleRow || sourceMark.getVisibility() != View.VISIBLE) return;
                int trailing = sourceMark.getWidth() + dp(6);
                if (sourceMarkName.getParent() == titleRow && sourceMarkName.getVisibility() == View.VISIBLE) {
                    trailing += sourceMarkName.getWidth() + dp(4);
                }
                int available = Math.max(dp(48), titleRow.getWidth() - trailing);
                if (title.getMaxWidth() != available) title.setMaxWidth(available);
            });
            copy.addView(metaRow, new LinearLayout.LayoutParams(-1, -2));
            metaRow.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                int trailing = dp(6);
                if (sourceDot.getVisibility() == View.VISIBLE) trailing += sourceDot.getWidth();
                if (sourceMark.getParent() == metaRow && sourceMark.getVisibility() == View.VISIBLE) {
                    trailing += sourceMark.getWidth();
                }
                if (sourceMarkName.getParent() == metaRow && sourceMarkName.getVisibility() == View.VISIBLE) {
                    trailing += sourceMarkName.getWidth();
                }
                int available = Math.max(dp(72), metaRow.getWidth() - trailing - dp(8));
                if (meta.getMaxWidth() != available) meta.setMaxWidth(available);
            });

            actionRail = new LinearLayout(activity);
            actionRail.setOrientation(LinearLayout.VERTICAL);
            actionRail.setGravity(Gravity.CENTER);
            actionRail.setTag("shittok_action_rail");
            lower.addView(actionRail, new LinearLayout.LayoutParams(dp(52), -2));

            creatorAvatarControl = new FrameLayout(activity);
            creatorAvatarControl.setTag("shittok_creator_avatar");
            creatorAvatarControl.setVisibility(View.GONE);
            creatorAvatarControl.setClickable(true);
            creatorAvatarControl.setFocusable(true);

            avatarRing = new FrameLayout(activity);
            avatarRing.setBackground(circleDrawable(Color.BLACK, AppearanceTheme.accent(activity), 2));
            avatarRing.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams ringParams =
                    new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            ringParams.topMargin = dp(2);
            creatorAvatarControl.addView(avatarRing, ringParams);

            creatorAvatar = new ImageView(activity);
            creatorAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            creatorAvatar.setImageResource(R.drawable.ic_more_account);
            creatorAvatar.setBackground(circleDrawable(Color.rgb(18, 20, 24), Color.TRANSPARENT, 0));
            creatorAvatar.setClipToOutline(true);
            creatorAvatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams avatarParams =
                    new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER);
            avatarRing.addView(creatorAvatar, avatarParams);

            creatorFavoriteBadge = new TextView(activity);
            creatorFavoriteBadge.setText("+");
            creatorFavoriteBadge.setTextColor(AppearanceTheme.onAccent(activity));
            creatorFavoriteBadge.setTextSize(16);
            creatorFavoriteBadge.setTypeface(null, android.graphics.Typeface.BOLD);
            creatorFavoriteBadge.setGravity(Gravity.CENTER);
            creatorFavoriteBadge.setBackground(circleDrawable(AppearanceTheme.accent(activity), Color.BLACK, 2));
            creatorFavoriteBadge.setTag("shittok_creator_favorite");
            creatorFavoriteBadge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            creatorFavoriteTarget = new FrameLayout(activity);
            creatorFavoriteTarget.setTag("shittok_creator_favorite_target");
            creatorFavoriteTarget.setClickable(true);
            creatorFavoriteTarget.setFocusable(true);
            ZeroChillMotion.installPressFeedback(creatorFavoriteTarget);
            FrameLayout.LayoutParams targetParams =
                    new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            creatorAvatarControl.addView(creatorFavoriteTarget, targetParams);
            FrameLayout.LayoutParams badgeParams =
                    new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER);
            creatorFavoriteTarget.addView(creatorFavoriteBadge, badgeParams);

            FrameLayout.LayoutParams floatingCreatorParams = new FrameLayout.LayoutParams(
                    dp(52), dp(68), Gravity.BOTTOM | Gravity.END);
            floatingCreatorParams.setMargins(0, 0, dp(13), dp(350));
            root.addView(creatorAvatarControl, floatingCreatorParams);

            like = textIconActionButton(
                    R.drawable.ic_shittok_rail_heart,
                    "Like video",
                    "shittok_like"
            );
            prepareCountedAction(like);
            actionRail.addView(like, actionParams());

            save = textIconActionButton(
                    R.drawable.ic_shittok_rail_bookmark,
                    "Save to Watch Later",
                    "shittok_save"
            );
            actionRail.addView(save, actionParams());

            comments = textIconActionButton(
                    R.drawable.ic_shittok_rail_comments,
                    "Open comments",
                    "shittok_comments"
            );
            prepareCountedAction(comments);
            actionRail.addView(comments, actionParams());

            download = textIconActionButton(
                    R.drawable.ic_shittok_rail_download,
                    "Download video",
                    "shittok_download"
            );
            actionRail.addView(download, actionParams());

            mute = muteButton("shittok_mute");
            actionRail.addView(mute, actionParams());

            share = textIconActionButton(
                    R.drawable.ic_shittok_rail_share,
                    "Share video. Long press for more actions",
                    "shittok_share"
            );
            actionRail.addView(share, actionParams());

            fullscreen = textIconActionButton(
                    R.drawable.ic_action_fullscreen,
                    "Watch horizontal video fullscreen",
                    "shittok_fullscreen"
            );
            fullscreen.setText("Full screen");
            fullscreen.setTextColor(Color.WHITE);
            fullscreen.setTextSize(12);
            fullscreen.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_action_fullscreen, 0, 0, 0);
            fullscreen.setCompoundDrawablePadding(dp(5));
            refreshFullscreenAccent(AppearanceTheme.accent(activity));
            fullscreen.setPadding(dp(12), 0, dp(12), 0);
            fullscreen.setVisibility(View.GONE);
            FrameLayout.LayoutParams fullscreenParams =
                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36),
                            Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            fullscreenParams.bottomMargin = dp(142);
            root.addView(fullscreen, fullscreenParams);
            root.addOnLayoutChangeListener((view, left, top, right, bottom,
                                            oldLeft, oldTop, oldRight, oldBottom) -> {
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                    updateFullscreenPromptPosition();
                }
            });
            ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
                layoutPlaybackControls(landscapeFullscreen());
                return insets;
            });

            fullscreenSurface = new FrameLayout(activity);
            fullscreenSurface.setTag("shittok_fullscreen_surface");
            fullscreenSurface.setContentDescription("Fullscreen video");
            fullscreenSurface.setClickable(true);
            fullscreenSurface.setVisibility(View.GONE);
            root.addView(fullscreenSurface, new FrameLayout.LayoutParams(-1, -1));
            fullscreenOverlay = new FrameLayout(activity);
            fullscreenOverlay.setTag("shittok_fullscreen_controls");
            fullscreenOverlay.setVisibility(View.GONE);
            root.addView(fullscreenOverlay, new FrameLayout.LayoutParams(-1, -1));

            landscapeTopScrim = new View(activity);
            landscapeTopScrim.setTag("shittok_landscape_top_scrim");
            landscapeTopScrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            GradientDrawable topScrimBackground = FullscreenPlayerStyle.topScrim();
            landscapeTopScrim.setBackground(topScrimBackground);
            FrameLayout.LayoutParams topScrimParams =
                    new FrameLayout.LayoutParams(-1, dp(FullscreenPlayerStyle.TOP_SCRIM_DP), Gravity.TOP);
            fullscreenOverlay.addView(landscapeTopScrim, topScrimParams);

            landscapeBottomScrim = new View(activity);
            landscapeBottomScrim.setTag("shittok_landscape_bottom_scrim");
            landscapeBottomScrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            GradientDrawable bottomScrimBackground = FullscreenPlayerStyle.bottomScrim();
            landscapeBottomScrim.setBackground(bottomScrimBackground);
            FrameLayout.LayoutParams bottomScrimParams =
                    new FrameLayout.LayoutParams(-1, dp(FullscreenPlayerStyle.BOTTOM_SCRIM_DP), Gravity.BOTTOM);
            fullscreenOverlay.addView(landscapeBottomScrim, bottomScrimParams);
            landscapeTopScrim.setVisibility(View.GONE);
            landscapeBottomScrim.setVisibility(View.GONE);

            landscapeHeader = new LinearLayout(activity);
            landscapeHeader.setGravity(Gravity.TOP);
            landscapeHeader.setTag("shittok_landscape_header");
            TextView back = textIconActionButton(
                    R.drawable.ic_player_back, "Exit fullscreen", "shittok_landscape_back");
            landscapeHeader.addView(back, new LinearLayout.LayoutParams(dp(46), dp(46)));

            LinearLayout heading = new LinearLayout(activity);
            heading.setOrientation(LinearLayout.VERTICAL);
            heading.setPadding(dp(4), dp(3), dp(4), 0);
            landscapeHeader.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));

            landscapeTitle = new TextView(activity);
            landscapeTitle.setTextColor(Color.WHITE);
            landscapeTitle.setTextSize(16);
            landscapeTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            landscapeTitle.setMaxLines(1);
            landscapeTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            heading.addView(landscapeTitle, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout creatorRow = new LinearLayout(activity);
            creatorRow.setGravity(Gravity.CENTER_VERTICAL);
            creatorRow.setMinimumHeight(dp(40));
            landscapeAvatar = new ImageView(activity);
            landscapeAvatar.setImageResource(R.drawable.ic_more_account);
            landscapeAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            landscapeAvatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            creatorRow.addView(landscapeAvatar, new LinearLayout.LayoutParams(dp(26), dp(26)));
            landscapeCreator = new TextView(activity);
            landscapeCreator.setTextColor(Color.WHITE);
            landscapeCreator.setTextSize(13);
            landscapeCreator.setMaxLines(1);
            landscapeCreator.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams creatorName = new LinearLayout.LayoutParams(-2, -2);
            creatorName.setMarginStart(dp(8));
            creatorName.weight = 1f;
            creatorRow.addView(landscapeCreator, creatorName);
            landscapeFavorite = new TextView(activity);
            landscapeFavorite.setTag("shittok_landscape_favorite_creator");
            landscapeFavorite.setGravity(Gravity.CENTER);
            landscapeFavorite.setTextColor(Color.WHITE);
            landscapeFavorite.setTextSize(19);
            landscapeFavorite.setFocusable(true);
            creatorRow.addView(landscapeFavorite, new LinearLayout.LayoutParams(dp(44), dp(44)));
            heading.addView(creatorRow, new LinearLayout.LayoutParams(-1, -2));

            landscapeDescription = new TextView(activity);
            landscapeDescription.setTag("shittok_landscape_description");
            landscapeDescription.setTextColor(Color.WHITE);
            landscapeDescription.setTextSize(12);
            landscapeDescription.setMaxLines(1);
            landscapeDescription.setEllipsize(android.text.TextUtils.TruncateAt.END);
            landscapeDescription.setOnClickListener(v -> {
                if (!interactionOverlayOpen()) {
                    showFullscreenDialog(new ZeroChillDialog.Builder(activity)
                            .setTitle(landscapeTitle.getText())
                            .setMessage(landscapeDescription.getText())
                            .setPositiveButton("Done", null).create());
                }
            });
            heading.addView(landscapeDescription, new LinearLayout.LayoutParams(-1, -2));

            TextView landscapeShare = textIconActionButton(
                    R.drawable.ic_player_more, "More video actions", "shittok_landscape_share");
            landscapeHeader.addView(landscapeShare, new LinearLayout.LayoutParams(dp(46), dp(46)));
            fullscreenOverlay.addView(landscapeHeader, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

            fullscreenPlayPause = new ImageView(activity);
            fullscreenPlayPause.setTag("shittok_fullscreen_play_pause");
            fullscreenPlayPause.setImageResource(R.drawable.ic_player_play);
            fullscreenPlayPause.setImageTintList(ColorStateList.valueOf(Color.WHITE));
            fullscreenPlayPause.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            fullscreenPlayPause.setPadding(dp(17), dp(17), dp(17), dp(17));
            fullscreenPlayPause.setBackground(circleDrawable(0x66000000, AppearanceTheme.accent(activity), 1));
            fullscreenPlayPause.setFocusable(true);
            fullscreenPlayPause.setOnClickListener(v -> {
                haptic(v);
                toggleFocusPlayback();
                showFullscreenControls(true);
            });
            ZeroChillMotion.installPressFeedback(fullscreenPlayPause);
            fullscreenOverlay.addView(fullscreenPlayPause,
                    new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));

            landscapeActions = new LinearLayout(activity);
            landscapeActions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            landscapeActions.setPadding(dp(12), 0, dp(12), 0);
            landscapeActions.setTag("shittok_landscape_actions");
            landscapeLike = textIconActionButton(R.drawable.ic_shittok_rail_heart,
                    "Like video", "shittok_landscape_like");
            landscapeComments = textIconActionButton(R.drawable.ic_shittok_rail_comments,
                    "Open comments", "shittok_landscape_comments");
            landscapeSave = textIconActionButton(R.drawable.ic_shittok_rail_bookmark,
                    "Save to Watch Later", "shittok_landscape_save");
            for (TextView action : new TextView[] {landscapeLike, landscapeComments, landscapeSave}) {
                landscapeActions.addView(action,
                        new LinearLayout.LayoutParams(dp(48), dp(48)));
            }
            fullscreenSpeed = new TextView(activity);
            fullscreenSpeed.setTag("shittok_fullscreen_speed");
            fullscreenSpeed.setText("1×");
            fullscreenSpeed.setTextColor(Color.WHITE);
            fullscreenSpeed.setTextSize(14);
            fullscreenSpeed.setGravity(Gravity.CENTER);
            fullscreenSpeed.setOnClickListener(v -> { haptic(v); showFullscreenSpeedMenu(); });
            fullscreenSpeed.setFocusable(true);
            landscapeActions.addView(fullscreenSpeed, new LinearLayout.LayoutParams(dp(56), dp(48)));
            fullscreenOverlay.addView(landscapeActions,
                    new FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM));
            back.setOnClickListener(v -> {
                haptic(v);
                exitFullscreenPlayer();
                if (activity instanceof NativeMainActivity) {
                    ((NativeMainActivity) activity).onShitTokLandscapePlayerExited();
                }
            });
            landscapeShare.setOnClickListener(v -> { haptic(v); showMoreMenu(v); });
            landscapeFavorite.setOnClickListener(this::favoriteCreator);

            landscapeLike.setOnClickListener(v -> { haptic(v); toggleVideoLike(); showControlsTemporarily(); });
            landscapeSave.setOnClickListener(v -> { haptic(v); toggleSaved(item, save); showControlsTemporarily(); });
            landscapeComments.setOnClickListener(v -> { haptic(v); if (item != null) openInlineComments(item); });

            fullscreenGestures = new FullscreenVideoGestures(fullscreenSurface, () -> player,
                    () -> {
                        if (controlsVisible) hideFullscreenControls();
                        else showFullscreenControls(true);
                    }, () -> landscapeFullscreen() && ownsPlayback() && !scrubbing, false,
                    () -> { updateProgress(); armFullscreenAutoHide(); });
            fullscreenSurface.setOnTouchListener(fullscreenGestures);
            fullscreenSurface.setOnClickListener(v -> {
                if (controlsVisible) hideFullscreenControls();
                else showFullscreenControls(true);
            });

            pausePlayOverlay = new ImageView(activity);
            pausePlayOverlay.setImageResource(R.drawable.ic_shittok_play_overlay);
            pausePlayOverlay.setAlpha(0.72f);
            pausePlayOverlay.setClickable(false);
            pausePlayOverlay.setFocusable(false);
            pausePlayOverlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            pausePlayOverlay.setVisibility(View.GONE);
            FrameLayout.LayoutParams pausePlayParams =
                    new FrameLayout.LayoutParams(dp(82), dp(82), Gravity.CENTER);
            mediaLayer.addView(pausePlayOverlay, pausePlayParams);

            seekBar = new SeekBar(activity);
            seekBar.setMax(1000);
            seekBar.setProgress(0);
            seekBar.setPadding(0, dp(18), 0, 0);
            seekBar.setContentDescription("Video progress");
            seekBar.setProgressTintList(ColorStateList.valueOf(AppearanceTheme.accent(activity)));
            seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.argb(150, 210, 210, 215)));
            seekBar.setThumbTintList(ColorStateList.valueOf(AppearanceTheme.accent(activity)));
            progressRow = new LinearLayout(activity);
            progressRow.setGravity(Gravity.CENTER_VERTICAL);
            progressRow.setTag("shittok_progress_row");
            elapsedTime = progressTimeLabel("shittok_elapsed", "0:00");
            remainingTime = progressTimeLabel("shittok_duration", "–:––");
            progressRow.addView(elapsedTime, new LinearLayout.LayoutParams(-2, -1));
            progressRow.addView(seekBar, new LinearLayout.LayoutParams(0, -1, 1f));
            progressRow.addView(remainingTime, new LinearLayout.LayoutParams(-2, -1));
            FrameLayout.LayoutParams seekParams = new FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM);
            seekParams.setMargins(dp(8), 0, dp(8), dp(1));
            progressRow.setVisibility(View.INVISIBLE);
            // Explicit controls keep touch priority where their bounds meet the hit row.
            root.addView(progressRow, root.indexOfChild(lower), seekParams);
            scrubPreviewView = new ShitTokScrubPreviewView(activity);
            root.addView(scrubPreviewView,
                    new FrameLayout.LayoutParams(dp(152), -2, Gravity.TOP | Gravity.LEFT));
            root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {}
                @Override public void onViewDetachedFromWindow(View v) { cancelInteractionUi(); }
            });
            root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (r - l != or - ol || b - t != ob - ot) cancelInteractionUi();
            });

            // Media3's fitted content frame contains the existing TextureView. Leave
            // PlayerView, touch coordinates, buffering UI and app controls unscaled.
            pinch = new ShitTokDynamicPinchController(
                    (ShitTokDynamicPinchController.TouchFrame) root,
                    playerView.findViewById(androidx.media3.ui.R.id.exo_content_frame),
                    () -> !fullscreenInputLocked() && active && hostResumed && !closed && !isPaging() && !scrubbing
                            && !clearDisplay && (commentsDialog == null || !commentsDialog.isShowing())
                            && player != null && player.isPlaying() && ownsPlayback()
                            && playbackOwner.isAttached(item.url, playerView),
                    pinching -> {
                        if (pinching) {
                            cancelInteractionUi();
                            pinchingHolder = this;
                            creatorSwipeTracking = false;
                            cancelCreatorSwipePreview(root);
                            ShitTokTransitionSnapshotStore.remove(creatorSwipeTransitionToken);
                            creatorSwipeTransitionToken = "";
                            root.removeCallbacks(hideControlsRunnable);
                            root.removeCallbacks(progressTick);
                            setFeedPagingEnabled(false);
                        } else {
                            if (pinchingHolder == this) pinchingHolder = null;
                            setFeedPagingEnabled(true);
                            showControlsTemporarily();
                            // Resume a completion-driven advance only after fingers let go.
                            pager.post(ChaosFeedView.this::tryPendingAutoAdvance);
                        }
                        updateSourceControlVisibility();
                        host.onTemporaryPinchChanged(pinching);
                    },
                    lower, creatorAvatarControl, fullscreen, landscapeTopScrim,
                    landscapeBottomScrim, landscapeHeader, landscapeActions, progressRow,
                    pausePlayOverlay, poster, loading, failure,
                    playerView.findViewById(androidx.media3.ui.R.id.exo_subtitles),
                    playerView.findViewById(androidx.media3.ui.R.id.exo_artwork),
                    playerView.findViewById(androidx.media3.ui.R.id.exo_buffering),
                    playerView.findViewById(androidx.media3.ui.R.id.exo_error_message));

            pinch.setPlaybackToggle(this::toggleFocusPlayback);

            playerView.setOnClickListener(v -> {
                haptic(v);
                if (fullscreenInputLocked()) return;
                if (clearDisplay) {
                    setClearDisplay(false);
                    return;
                }
                if (!controlsVisible) {
                    showControlsTemporarily();
                    return;
                }
                if (player == null) {
                    showControlsTemporarily();
                    return;
                }
                if (player.isPlaying()) {
                    userPaused = true;
                    playbackOwner.setUserPaused(item.url, true);
                    cancelPendingAutoAdvance();
                    player.pause();
                    updateProgress();
                    showControlsPersistent();
                    syncPausedChrome();
                } else {
                    userPaused = false;
                    playbackOwner.setUserPaused(item.url, false);
                    hidePausedChrome();
                    everStarted = true;
                    if (player.getPlaybackState() == Player.STATE_ENDED) {
                        handleCompletion(player);
                    } else {
                        playOwnedPlayer();
                    }
                    showControlsTemporarily();
                }
            });

            playerView.setLongClickable(false);
            videoTaps = new ShitTokVideoTaps(activity, () -> item,
                    () -> !fullscreenInputLocked() && ownsPlayback() && !scrubbing && !pinch.ownsTouchSequence()
                            && !creatorSwipeTracking,
                    () -> playerView.performClick(), this::favoriteFromDoubleTap,
                    this::beginSpeedBoost, () -> {
                        if (player != null) playbackOwner.endSpeedBoost(player);
                    });
            playerView.setOnTouchListener((v, event) -> {
                if (fullscreenInputLocked()) return true;
                // Let View clear its pressed/click state on the synthetic child cancel.
                if (pinch.ownsTouchSequence()) {
                    videoTaps.cancel();
                    return event.getActionMasked() != MotionEvent.ACTION_CANCEL;
                }
                int action = event.getActionMasked();
                videoTaps.touch(event, item);

                if (action == MotionEvent.ACTION_DOWN) {
                    pinch.videoTouchStarted();
                    creatorSwipeDownX = event.getX();
                    creatorSwipeDownY = event.getY();
                    creatorSwipeTracking = false;
                }

                if (event.getPointerCount() > 1) {
                    creatorSwipeTracking = false;
                    cancelCreatorSwipePreview(root);
                    ShitTokTransitionSnapshotStore.remove(creatorSwipeTransitionToken);
                    creatorSwipeTransitionToken = "";
                    setFeedPagingEnabled(true);
                    ViewParentCompat.disallow(v, false);
                    return true;
                }

                if (action == MotionEvent.ACTION_MOVE && !manualFullscreen) {
                    String album = ShitTokEromeGallery.albumUrl(item);
                    String creator = creatorIdentity == null ? "" : creatorIdentity.title;
                    boolean erome = !album.isEmpty();
                    boolean gallery = erome || !creator.isEmpty();
                    float dx = event.getX() - creatorSwipeDownX;
                    float dy = event.getY() - creatorSwipeDownY;
                    if (gallery && dx < -dp(14)
                            && Math.abs(dx) > Math.abs(dy) * 1.20f) {
                        if (!creatorSwipeTracking) {
                            videoTaps.cancel();
                            creatorSwipeTracking = true;
                            creatorSwipeOpensErome = erome;
                            creatorSwipeAlbumUrl = erome ? album : "";
                            View sourceSurface = creatorSwipeSourceView();
                            creatorSwipeTransitionToken =
                                    ShitTokTransitionSnapshotStore.beginCapture(
                                            activity,
                                            sourceSurface == null ? root : sourceSurface,
                                            playerView.getVideoSurfaceView()
                                    );
                            if (erome) beginEromeSwipePreview(item, creatorSwipeTransitionToken);
                            else beginCreatorSwipePreview(creator, creatorSwipeTransitionToken);
                        }
                        setFeedPagingEnabled(false);
                        ViewParentCompat.disallow(v, true);
                    }
                    if (creatorSwipeTracking) {
                        updateCreatorSwipePreview(dx, root);
                        return true;
                    }
                }

                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (creatorSwipeTracking) {
                        float dx = event.getX() - creatorSwipeDownX;
                        float dy = event.getY() - creatorSwipeDownY;
                        float threshold = Math.max(dp(72), root.getWidth() * 0.18f);
                        boolean openGallery = action == MotionEvent.ACTION_UP
                                && shouldOpenCreatorGallerySwipe(dx, dy, threshold);
                        boolean erome = creatorSwipeOpensErome;
                        String album = creatorSwipeAlbumUrl;
                        creatorSwipeTracking = false;
                        creatorSwipeOpensErome = false;
                        creatorSwipeAlbumUrl = "";
                        setFeedPagingEnabled(true);
                        ViewParentCompat.disallow(v, false);
                        if (openGallery && (erome || creatorIdentity != null)) {
                            String creator = creatorIdentity == null ? "" : creatorIdentity.title;
                            haptic(v);
                            ShitTokAccessHint.learned(activity);
                            cancelAccessHint();
                            pauseAndRecord();
                            String transitionToken = creatorSwipeTransitionToken;
                            creatorSwipeTransitionToken = "";
                            commitCreatorSwipePreview(
                                    root,
                                    creator,
                                    transitionToken,
                                    erome,
                                    erome
                                            ? () -> openEromeGallery(item, album, true, transitionToken)
                                            : () -> openCreatorGallery(true, transitionToken)
                            );
                        } else {
                            cancelCreatorSwipePreview(root);
                            ShitTokTransitionSnapshotStore.remove(creatorSwipeTransitionToken);
                            creatorSwipeTransitionToken = "";
                        }
                        return true;
                    }

                    setFeedPagingEnabled(true);
                    ViewParentCompat.disallow(v, false);
                }
                // GestureDetector confirms a single tap only after ruling out a double tap.
                // Parent interception remains available for ordinary vertical paging.
                return true;
            });

            View.OnLongClickListener menuLongPress = v -> {
                haptic(v);
                showMoreMenu(v);
                return true;
            };
            title.setOnLongClickListener(menuLongPress);
            meta.setOnLongClickListener(menuLongPress);
            title.setOnClickListener(v -> {
                String album = ShitTokEromeGallery.albumUrl(item);
                if (!album.isEmpty()) {
                    haptic(v);
                    openEromeGallery(item, album, false, "");
                    return;
                }
                if (creatorIdentity == null) return;
                haptic(v);
                openCreatorGallery(false, "");
            });
            creatorAvatarControl.setOnClickListener(v -> {
                if (creatorIdentity == null) return;
                haptic(v);
                openCreatorGallery(false, "");
            });
            creatorFavoriteTarget.setOnClickListener(this::favoriteCreator);

            fullscreen.setOnClickListener(v -> {
                if (!horizontalVideo) return;
                haptic(v);
                if (manualFullscreen) exitManualFullscreen();
                else enterManualFullscreen(this);
                showControlsTemporarily();
            });
            like.setOnClickListener(v -> {
                haptic(v);
                pulse(like, 1.12f);
                toggleVideoLike();
                showControlsTemporarily();
            });
            save.setOnClickListener(v -> {
                haptic(v);
                toggleSaved(item, save);
                showControlsTemporarily();
            });
            comments.setOnClickListener(v -> {
                haptic(v);
                if (item != null) openInlineComments(item);
            });
            download.setOnClickListener(v -> { haptic(v); downloadCurrentVideo(); });
            share.setOnClickListener(v -> { haptic(v); share(item); });
            share.setOnLongClickListener(v -> { haptic(v); showMoreMenu(v); return true; });
            ViewCompat.addAccessibilityAction(share, "More video actions", (view, args) -> {
                showMoreMenu(view);
                return true;
            });

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (!fromUser || player == null) return;
                    long duration = player.getDuration();
                    if (duration <= 0L || (landscapeFullscreen() && !canSeekFullscreen())) return;
                    long target = ShitTokInteractionPolicy.targetTime(duration, progress, bar.getMax());
                    updateTimeLabels(target, duration);
                    if (scrubbing) {
                        pendingScrubPosition = target;
                        if (!landscapeFullscreen()) showScrubPreview(target, duration);
                    } else player.seekTo(target); // Keyboard/accessibility adjustments commit directly.
                }

                @Override
                public void onStartTrackingTouch(SeekBar bar) {
                    if (videoTaps != null) videoTaps.cancel();
                    if (player == null || player.getDuration() <= 0L
                            || (landscapeFullscreen() && !canSeekFullscreen())) return;
                    fullscreenGestures.cancel();
                    scrubPlayer = player;
                    scrubStartPosition = Math.max(0L, player.getCurrentPosition());
                    pendingScrubPosition = scrubStartPosition;
                    boolean fullscreen = landscapeFullscreen();
                    scrubWasPlaying = !fullscreen && player.getPlayWhenReady();
                    scrubbing = true;
                    if (!fullscreen) player.pause();
                    setFeedPagingEnabled(false);
                    ViewParentCompat.disallow(bar, true);
                    showControlsPersistent();
                    if (!fullscreen) beginScrubPreview();
                }

                @Override
                public void onStopTrackingTouch(SeekBar bar) {
                    endScrubPreview(true, true);
                    updateProgress();
                    if (landscapeFullscreen()) armFullscreenAutoHide();
                    else if (player != null && player.isPlaying()) showControlsTemporarily();
                    else showControlsPersistent();
                }
            });
            // SeekBar invokes onStopTrackingTouch for ACTION_CANCEL too. Cancel first so
            // its normal callback cannot commit a destination the user did not release on.
            seekBar.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    endScrubPreview(false, true);
                    updateProgress();
                }
                return false;
            });
        }

        private void beginScrubPreview() {
            if (videoTaps != null) videoTaps.cancel();
            if (player == null || player.getDuration() <= 0L) return;
            NativeContentItem capturedItem = item;
            CrazyShitRepository.StreamInfo capturedStream = stream;
            int capturedPosition = boundPosition, capturedGeneration = feedGeneration, token = attachedToken;
            if (capturedItem != null && capturedStream != null && ownsPlayback()) {
                if (scrubPreview == null) scrubPreview = new ShitTokScrubPreview(bitmap -> {
                    scrubPreviewView.frame(bitmap);
                    if (scrubbing) scrubPreviewView.position(root, seekBar);
                });
                scrubPreview.begin(capturedItem.url + "\n" + capturedStream.mediaUrl,
                        () -> ShitTokScrubPreview.media3(activity, capturedItem.url, capturedStream),
                        () -> scrubbing && ownsPlayback() && item == capturedItem && stream == capturedStream
                                && boundPosition == capturedPosition && feedGeneration == capturedGeneration
                                && attachedToken == token);
            }
            showScrubPreview(pendingScrubPosition, player.getDuration());
        }

        private void showScrubPreview(long target, long duration) {
            scrubPreviewView.show(root, seekBar, target, duration);
            if (scrubPreview != null) scrubPreview.request(Math.min(target, Math.max(0L, duration - 1L)));
        }

        private void endScrubPreview(boolean commit, boolean restorePlayback) {
            if (!scrubbing) return;
            ExoPlayer scrubbed = scrubPlayer;
            scrubPlayer = null;
            scrubbing = false;
            if (scrubPreview != null) scrubPreview.end();
            scrubPreviewView.hide();
            ViewParentCompat.disallow(seekBar, false);
            setFeedPagingEnabled(true);
            if (scrubbed != null && scrubbed == player && ownsPlayback()) {
                if (commit && (!landscapeFullscreen() || canSeekFullscreen()))
                    scrubbed.seekTo(Math.max(0L,
                        Math.min(pendingScrubPosition, Math.max(0L, scrubbed.getDuration()))));
                if (restorePlayback && scrubWasPlaying) scrubbed.play();
            }
        }

        private void cancelInteractionUi() {
            if (player != null) playbackOwner.endSpeedBoost(player);
            if (speedBadge != null) {
                speedBadge.animate().withEndAction(null).cancel();
                speedBadge.setVisibility(View.GONE);
            }
            if (scrubbing) endScrubPreview(false, true);
            if (videoTaps != null) videoTaps.cancel();
            if (fullscreenGestures != null) fullscreenGestures.cancel();
            root.removeCallbacks(hideHeart);
            if (heartOverlay != null) {
                heartOverlay.animate().withEndAction(null).cancel();
                heartOverlay.setVisibility(View.GONE);
            }
        }

        private void favoriteFromDoubleTap(float x, float y) {
            if (!ownsPlayback() || scrubbing || pinch.ownsTouchSequence()) return;
            if (!ZeroChillAccountRepository.hasStoredSession(activity)) {
                // Keep playback running. The explicit heart button retains its sign-in route.
                ZeroChillToast.makeText(activity, "Sign in to like videos.", ZeroChillToast.LENGTH_SHORT).show();
                return;
            }
            if (!videoLiked && like.isEnabled()) changeVideoLike(true);
            showHeart(x, y);
        }

        private void showHeart(float x, float y) {
            if (heartOverlay == null) {
                heartOverlay = new ImageView(activity);
                heartOverlay.setImageResource(R.drawable.ic_action_heart_filled);
                heartOverlay.setImageTintList(ColorStateList.valueOf(AppearanceTheme.accent(activity)));
                heartOverlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                heartOverlay.setTag("shittok_double_tap_heart");
                root.addView(heartOverlay, new FrameLayout.LayoutParams(dp(88), dp(88)));
            }
            android.graphics.Rect video = new android.graphics.Rect(0, 0, playerView.getWidth(), playerView.getHeight());
            root.offsetDescendantRectToMyCoords(playerView, video);
            heartOverlay.animate().withEndAction(null).cancel();
            root.removeCallbacks(hideHeart);
            heartOverlay.setTranslationX(Math.max(0f, Math.min(root.getWidth() - dp(88), video.left + x - dp(44))));
            heartOverlay.setTranslationY(Math.max(0f, Math.min(root.getHeight() - root.getPaddingBottom() - dp(88),
                    video.top + y - dp(44))));
            heartOverlay.setAlpha(1f);
            heartOverlay.setScaleX(.65f);
            heartOverlay.setScaleY(.65f);
            heartOverlay.setVisibility(View.VISIBLE);
            haptic(playerView);
            if (!ZeroChillMotion.animationsEnabled(activity)) {
                root.postDelayed(hideHeart, 140L);
                return;
            }
            heartOverlay.animate().scaleX(1.12f).scaleY(1.12f).setDuration(110L)
                    .withEndAction(() -> heartOverlay.animate().alpha(0f).scaleX(1f).scaleY(1f)
                            .translationYBy(-dp(20)).setDuration(320L).withEndAction(hideHeart).start()).start();
        }

        private boolean beginSpeedBoost() {
            if (!ownsPlayback() || scrubbing || pinch.ownsTouchSequence() || creatorSwipeTracking
                    || player == null || !playbackOwner.isAttached(item.url, playerView)) return false;
            if (!playbackOwner.beginSpeedBoost(item.url, player, () -> showSpeedBadge(false))) return false;
            showSpeedBadge(true);
            playerView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            return true;
        }

        private void showSpeedBadge(boolean visible) {
            if (visible && speedBadge == null) {
                speedBadge = new ImageView(activity);
                speedBadge.setTag("shittok_speed_badge");
                speedBadge.setImageResource(R.drawable.ic_shittok_fast_forward);
                speedBadge.setImageTintList(ColorStateList.valueOf(Color.WHITE));
                speedBadge.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                speedBadge.setClickable(false);
                speedBadge.setFocusable(false);
                speedBadge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(56), dp(40), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                params.topMargin = Math.max(dp(76), root.getHeight() / 4);
                root.addView(speedBadge, params);
            }
            if (speedBadge == null) return;
            speedBadge.animate().withEndAction(null).cancel();
            if (visible) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) speedBadge.getLayoutParams();
                params.topMargin = Math.max(dp(76), root.getHeight() / 4);
                speedBadge.setLayoutParams(params);
                speedBadge.setAlpha(0f);
                speedBadge.setScaleX(.94f); speedBadge.setScaleY(.94f);
                speedBadge.setVisibility(View.VISIBLE);
                speedBadge.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(100).start();
            } else {
                speedBadge.animate().alpha(0f).setDuration(70)
                        .withEndAction(() -> speedBadge.setVisibility(View.GONE)).start();
            }
        }

        private TextView muteButton(String tag) {
            TextView button = textIconActionButton(R.drawable.ic_shittok_rail_volume_on, "Mute video", tag);
            button.setFocusable(true);
            button.setOnClickListener(v -> {
                haptic(v);
                setChaosMuted(!chaosMuted);
                showControlsTemporarily();
            });
            return button;
        }

        private void syncMuteButtons() {
            mute.setCompoundDrawablesWithIntrinsicBounds(0, chaosMuted
                    ? R.drawable.ic_shittok_rail_volume_off : R.drawable.ic_shittok_rail_volume_on, 0, 0);
            mute.setCompoundDrawableTintList(ColorStateList.valueOf(
                    chaosMuted ? AppearanceTheme.accent(activity) : Color.WHITE));
            mute.setContentDescription(chaosMuted ? "Unmute video" : "Mute video");
        }

        private TextView textIconActionButton(int icon, String description, String tag) {
            TextView button = new TextView(activity);
            button.setGravity(Gravity.CENTER);
            button.setContentDescription(description);
            button.setBackgroundColor(Color.TRANSPARENT);
            button.setPadding(dp(10), dp(2), dp(10), dp(2));
            button.setIncludeFontPadding(false);
            button.setTextSize(10);
            button.setCompoundDrawablePadding(dp(1));
            button.setClickable(true);
            button.setFocusable(false);
            button.setTag(tag);
            button.setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0);
            button.setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
            button.setShadowLayer(dp(3), 0f, dp(1), 0xB9000000);
            ZeroChillMotion.installPressFeedback(button);
            return button;
        }

        private void prepareCountedAction(TextView button) {
            if (button == null) return;
            button.setPadding(dp(10), dp(2), dp(10), dp(2));
            button.setCompoundDrawablePadding(dp(1));
        }

        private LinearLayout.LayoutParams actionParams() {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(48));
            params.setMargins(0, dp(2), 0, dp(2));
            return params;
        }

        private GradientDrawable circleDrawable(int fillColor, int strokeColor, int strokeWidthDp) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(fillColor);
            if (strokeWidthDp > 0 && Color.alpha(strokeColor) > 0) {
                drawable.setStroke(dp(strokeWidthDp), strokeColor);
            }
            return drawable;
        }

        private void bindCreatorChrome(NativeContentItem next) {
            bindCreatorAvatar(next, true);
            boolean creatorClip = creatorIdentity != null;
            String creator = creatorClip ? creatorIdentity.title : "";
            String displayTitle = creatorClip
                    ? creator
                    : (next.title == null || next.title.isEmpty() ? "Random video" : next.title);
            title.setText(displayTitle);
            landscapeTitle.setText(next.title == null || next.title.isEmpty()
                    ? displayTitle : next.title);
            String description = next.description == null ? "" : next.description.trim();
            if (description.contentEquals(landscapeTitle.getText()) || description.equals(creator)) description = "";
            landscapeDescription.setText(description);
            landscapeDescription.setVisibility(description.isEmpty() ? View.GONE : View.VISIBLE);
            landscapeDescription.setContentDescription(description.isEmpty() ? null
                    : description + ". Show full description");
            bindFullscreenCreator();
            boolean eromeClip = !ShitTokEromeGallery.albumUrl(next).isEmpty();
            title.setClickable(creatorClip || eromeClip);
            title.setContentDescription(
                    eromeClip ? "Open Erome album"
                            : creatorClip ? "Open " + creator + " gallery" : displayTitle
            );
            StringBuilder info = new StringBuilder();
            if (!creatorClip) {
                if (next.uploader != null && !next.uploader.isEmpty()) info.append(next.uploader);
                if (next.views != null && !next.views.isEmpty()) {
                    if (info.length() > 0) info.append("  •  ");
                    info.append(next.views).append(" views");
                }
            }
            meta.setText(info);
            if (creatorClip && BaddieHubRepository.isBaddieHubUrl(next.url)) {
                // Creator identity never changes this clip's source, resolver, or saved media.
                meta.setText(BaddieHubRepository.LABEL);
            }
            boolean named = meta.getText() != null && meta.getText().length() > 0;
            meta.setVisibility(named ? View.VISIBLE : View.GONE);
            metaRow.setVisibility(named ? View.VISIBLE : View.GONE);
            bindSourceMark(next, named);
            bindAccessPill(next);
        }

        private void bindAccessPill(NativeContentItem next) {
            cancelAccessHint();
            ShitTokGalleryAccess.Presentation access = ShitTokGalleryAccess.of(next, creatorIdentity);
            if (!access.visible()) {
                accessPill.setVisibility(View.GONE);
                accessPill.setContentDescription(null);
                return;
            }
            int accent = AppearanceTheme.accent(activity);
            accessPill.setVisibility(View.VISIBLE);
            accessLabel.setText(access.label);
            accessIcon.setImageResource(access.kind == ShitTokGalleryAccess.Kind.CREATOR
                    ? R.drawable.ic_more_account : R.drawable.ic_more_view_style);
            accessIcon.setImageTintList(ColorStateList.valueOf(accent));
            tintAccessChevrons(accent);
            accessPill.setContentDescription(access.contentDescription);
            if (ZeroChillMotion.animationsEnabled(activity)
                    && ShitTokAccessHint.noteAppearance(activity, next.url)) {
                nudgeAccessChevrons();
            }
        }

        private void tintAccessChevrons(int accent) {
            ColorStateList tint = ColorStateList.valueOf(accent);
            for (int index = 0; index < accessChevrons.getChildCount(); index++) {
                View child = accessChevrons.getChildAt(index);
                if (child instanceof ImageView) ((ImageView) child).setImageTintList(tint);
            }
        }

        private void nudgeAccessChevrons() {
            cancelAccessHint();
            accessChevrons.setTranslationX(0f);
            float distance = dp(4);
            accessHintAnimator = ValueAnimator.ofFloat(0f, 1f);
            accessHintAnimator.setDuration(780L);
            accessHintAnimator.setInterpolator(ZeroChillMotion.EASE_OUT);
            accessHintAnimator.addUpdateListener(animation -> {
                float t = (float) animation.getAnimatedValue();
                float phase = t < 0.55f ? t / 0.55f : 1f - (t - 0.55f) / 0.45f;
                accessChevrons.setTranslationX(distance * phase);
            });
            accessHintAnimator.start();
        }

        private void cancelAccessHint() {
            if (accessHintAnimator != null) {
                accessHintAnimator.cancel();
                accessHintAnimator = null;
            }
            if (accessChevrons != null) accessChevrons.setTranslationX(0f);
        }

        private LinearLayout.LayoutParams sourceBadgeParams() {
            LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(dp(20), dp(20));
            markParams.gravity = Gravity.CENTER_VERTICAL;
            markParams.leftMargin = dp(6);
            return markParams;
        }

        private void bindSourceMark(NativeContentItem next, boolean besideUsername) {
            sourceMarkName.removeCallbacks(hideSourceMarkName);
            sourceMarkName.setVisibility(View.GONE);
            sourceDot.setVisibility(View.GONE);
            ShitTokSourceMark.Provider provider = ShitTokSourceMark.of(next);
            if (provider == null) {
                sourceMark.setVisibility(View.GONE);
                sourceMark.setContentDescription(null);
                parkTitleFullWidth();
                return;
            }
            LinearLayout row = besideUsername ? metaRow : titleRow;
            if (sourceMark.getParent() != row) {
                detachChrome(sourceMark);
                detachChrome(sourceMarkName);
                row.addView(sourceMark, sourceBadgeParams());
                LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-2, -2);
                nameParams.leftMargin = dp(4);
                nameParams.gravity = Gravity.CENTER_VERTICAL;
                row.addView(sourceMarkName, nameParams);
            } else {
                sourceMark.setLayoutParams(sourceBadgeParams());
            }
            if (besideUsername) parkTitleFullWidth();
            else hugTitleBeforeBadge();
            sourceMark.setVisibility(View.VISIBLE);
            sourceMark.setContentDescription(provider.name + ". Show provider name");
            sourceMarkName.setText(provider.name);
            if (provider.iconRes != 0) {
                sourceMarkIcon.setImageResource(provider.iconRes);
                sourceMarkIcon.setImageTintList(null);
                sourceMarkIcon.setImageAlpha(255);
                sourceMarkIcon.setVisibility(View.VISIBLE);
                sourceMarkGlyph.setVisibility(View.GONE);
            } else {
                sourceMarkIcon.setImageDrawable(null);
                sourceMarkIcon.setVisibility(View.GONE);
                sourceMarkGlyph.setText(provider.monogram);
                sourceMarkGlyph.setTextColor(Color.WHITE);
                sourceMarkGlyph.setTextSize(provider.monogram.length() > 1 ? 7f : 11f);
                sourceMarkGlyph.setVisibility(View.VISIBLE);
            }
        }

        private void parkTitleFullWidth() {
            LinearLayout.LayoutParams titleParams = (LinearLayout.LayoutParams) title.getLayoutParams();
            titleParams.width = 0;
            titleParams.weight = 1f;
            title.setLayoutParams(titleParams);
            title.setMaxWidth(Integer.MAX_VALUE);
        }

        private void hugTitleBeforeBadge() {
            LinearLayout.LayoutParams titleParams = (LinearLayout.LayoutParams) title.getLayoutParams();
            titleParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            titleParams.weight = 0f;
            title.setLayoutParams(titleParams);
        }

        private static void detachChrome(View view) {
            if (view.getParent() instanceof ViewGroup) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
        }

        private void bindFullscreenCreator() {
            if (!landscapeFullscreen() || item == null || boundPosition != selectedPosition) return;
            String name = creatorIdentity == null ? item.uploader : creatorIdentity.title;
            boolean hasCreator = name != null && !name.isBlank();
            landscapeCreator.setText(hasCreator ? name : "");
            ((View) landscapeCreator.getParent()).setVisibility(hasCreator ? View.VISIBLE : View.GONE);
            landscapeAvatar.setVisibility(hasCreator ? View.VISIBLE : View.GONE);
            landscapeFavorite.setVisibility(creatorIdentity == null ? View.GONE : View.VISIBLE);
            String avatar = creatorIdentity == null ? "" : creatorIdentity.imageUrl;
            String referer = creatorIdentity == null ? "" : creatorIdentity.url;
            String key = referer + "\n" + avatar;
            if (!key.equals(landscapeAvatarKey)) {
                landscapeAvatarKey = key;
                Glide.with(landscapeAvatar).clear(landscapeAvatar);
                landscapeAvatar.setImageResource(R.drawable.ic_more_account);
                if (avatar != null && !avatar.isBlank()) {
                    Glide.with(landscapeAvatar).load(imageWithHeaders(avatar, referer)).circleCrop()
                            .placeholder(R.drawable.ic_more_account).error(R.drawable.ic_more_account)
                            .into(landscapeAvatar);
                }
            }
        }

        private void bindCreatorAvatar(NativeContentItem media, boolean creatorClip) {
            Glide.with(creatorAvatar).clear(creatorAvatar);
            creatorAvatar.setImageResource(R.drawable.ic_more_account);
            creatorIdentity = creatorClip
                    ? creatorMetadata.identity(media)
                    : null;
            if (creatorIdentity == null) {
                creatorAvatarControl.setVisibility(View.GONE);
                creatorAvatarControl.setContentDescription(null);
                creatorFavoriteTarget.setContentDescription(null);
                return;
            }

            creatorAvatarControl.setVisibility(!landscapeFullscreen() && !clearDisplay
                    && !pinch.suppressesFeedChrome() ? View.VISIBLE : View.GONE);
            creatorAvatarControl.setContentDescription(
                    "Open " + creatorIdentity.title + " creator gallery"
            );
            String imageUrl = creatorIdentity.imageUrl == null
                    ? ""
                    : creatorIdentity.imageUrl.trim();
            if (!imageUrl.isEmpty()) {
                String referer = creatorIdentity.uploader == null
                        || creatorIdentity.uploader.trim().isEmpty()
                        ? creatorIdentity.url
                        : creatorIdentity.uploader;
                Glide.with(creatorAvatar)
                        .load(imageWithHeaders(imageUrl, referer))
                        .circleCrop()
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .dontAnimate()
                        .transition(SharedArtworkReveal.artwork("shittok-avatar", creatorIdentity.url, true))
                        .placeholder(R.drawable.ic_more_account)
                        .error(R.drawable.ic_more_account)
                        .into(creatorAvatar);
            }
            refreshCreatorFavoriteBadge();
        }

        private void favoriteCreator(View view) {
            if (creatorIdentity == null || CreatorFavoriteStore.contains(activity, creatorIdentity)) return;
            haptic(view);
            CreatorFavoriteStore.toggle(activity, creatorIdentity);
            ShitTokSmartRanker.action(item, ShitTokSmartRanker.Action.CREATOR_FAVORITE);
            refreshCreatorFavoriteBadge();
            pulse(view == landscapeFavorite ? landscapeFavorite : creatorFavoriteBadge, 1.15f);
            ZeroChillToast.makeText(activity,
                    "Added " + creatorIdentity.title + " to Favorite Creators.", ZeroChillToast.LENGTH_SHORT).show();
            showControlsTemporarily();
        }

        private void refreshCreatorFavoriteBadge() {
            if (creatorIdentity == null) return;
            boolean favorite;
            if (isPaging() && item != null) favorite = Boolean.TRUE.equals(creatorFavoriteCache.get(item.url));
            else {
                favorite = CreatorFavoriteStore.contains(activity, creatorIdentity);
                if (item != null) creatorFavoriteCache.put(item.url, favorite);
            }
            landscapeFavorite.setText(favorite ? "✓" : "+");
            landscapeFavorite.setTextColor(AppearanceTheme.accent(activity));
            landscapeFavorite.setEnabled(!favorite);
            creatorFavoriteBadge.setText(favorite ? "✓" : "+");
            creatorFavoriteBadge.setTextSize(favorite ? 13 : 16);
            creatorFavoriteTarget.setClickable(!favorite);
            creatorFavoriteTarget.setFocusable(!favorite);
            creatorFavoriteBadge.setAlpha(favorite ? 0.92f : 1f);
            creatorFavoriteTarget.setContentDescription(
                    favorite
                            ? creatorIdentity.title + " is in Favorite Creators"
                            : "Add " + creatorIdentity.title + " to Favorite Creators"
            );
            landscapeFavorite.setContentDescription(creatorFavoriteTarget.getContentDescription());
        }

        private void refreshFullscreenAccent(int accent) {
            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.argb(195, 9, 13, 20));
            background.setCornerRadius(dp(18));
            background.setStroke(dp(1), fullscreenStrokeColor(accent));
            fullscreen.setBackground(background);
            fullscreen.setCompoundDrawableTintList(ColorStateList.valueOf(accent));
        }

        void refreshVisualTheme() {
            int accent = AppearanceTheme.accent(activity);
            refreshFullscreenAccent(accent);
            fullscreenPlayPause.setBackground(circleDrawable(0x66000000, accent, 1));
            landscapeFavorite.setTextColor(accent);
            avatarRing.setBackground(circleDrawable(Color.BLACK, accent, 2));
            creatorFavoriteBadge.setBackground(circleDrawable(accent, Color.BLACK, 2));
            creatorFavoriteBadge.setTextColor(AppearanceTheme.onAccent(activity));
            seekBar.setProgressTintList(ColorStateList.valueOf(accent));
            seekBar.setThumbTintList(ColorStateList.valueOf(accent));
            updateVideoLikeButton();
            if (item != null) {
                updateSaveButton(item, save);
                updateSaveButton(item, landscapeSave);
            }
            if (heartOverlay != null) heartOverlay.setImageTintList(ColorStateList.valueOf(accent));
            tintAccessChevrons(accent);
            accessIcon.setImageTintList(ColorStateList.valueOf(accent));
            syncMuteButtons();
        }

        void setSwipeAlpha(float alpha) {
            swipeAlpha = alpha;
            // TextureView does not reliably inherit parent alpha on every device.
            // Each layer moves at most 4%, for a combined floor above 92%.
            root.setAlpha(alpha);
            View surface = playerView.getVideoSurfaceView();
            if (surface != null) surface.setAlpha(alpha);
        }

        private void pulse(View view, float peak) {
            view.animate().cancel();
            view.setScaleX(1f);
            view.setScaleY(1f);
            if (!ZeroChillMotion.animationsEnabled(activity)) return;
            view.animate().scaleX(peak).scaleY(peak).setDuration(ZeroChillMotion.QUICK_MS)
                    .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f)
                            .setDuration(ZeroChillMotion.QUICK_MS).start()).start();
        }

        void revealSettledControls() {
            if (pinch.suppressesFeedChrome() || clearDisplay || !ZeroChillMotion.animationsEnabled(activity)) return;
            for (View view : new View[] {lower, creatorAvatarControl, landscapeHeader, landscapeActions}) {
                if (view.getVisibility() != View.VISIBLE) continue;
                view.animate().cancel();
                view.setAlpha(.86f);
                view.setTranslationY(dp(5));
                view.animate().alpha(1f).translationY(0f)
                        .setDuration(ZeroChillMotion.STANDARD_MS)
                        .setInterpolator(ZeroChillMotion.EASE_OUT).start();
            }
        }

        private void resetEntranceOffsets() {
            lower.setTranslationY(0f);
            creatorAvatarControl.setTranslationY(0f);
            landscapeHeader.setTranslationY(0f);
            landscapeActions.setTranslationY(0f);
        }

        void bind(NativeContentItem next, int position) {
            resetVisualMotion();
            detachLease();
            liveAmbientBackdrop.reset();
            playerHolders.add(this);
            root.removeCallbacks(skipFailedClipRunnable);
            root.removeCallbacks(progressTick);
            mediaLayer.animate().cancel();
            mediaLayer.setScaleX(1f);
            mediaLayer.setScaleY(1f);
            mediaLayer.setTranslationY(0f);
            item = next;
            stream = null;
            lastAttemptedStream = null;
            lastPlaybackError = null;
            lastFailureStage = "";
            boundPosition = position;
            everStarted = false;
            playbackSuspended = false;
            controlsVisible = true;
            landscapeControlsMounted = false;
            userPaused = false;
            scrubbing = false;
            retryAttempted = false;
            failurePending = false;
            maintenanceFailure = false;
            horizontalVideo = shouldOfferLandscapeFullscreen(next.aspectRatioHint);
            aspectSampleRecorded = false;
            videoAspectRatio = horizontalVideo ? next.aspectRatioHint : 0f;
            updateAmbientBackdrop();
            videoLiked = false;
            videoLikePending = false;
            videoLikeCount = 0;
            videoLikeRequestGeneration++;
            updateVideoLikeButton();
            commentCount = 0;
            commentCountRequestGeneration++;
            updateCommentButton();
            fullscreen.setVisibility(View.GONE);
            seekBar.setProgress(0);
            updateTimeLabels(0L, 0L);
            seekBar.setEnabled(false);
            progressRow.setAlpha(0f);
            progressRow.setVisibility(View.INVISIBLE);
            progressChromeVisible = null;
            pausePlayOverlay.setVisibility(View.GONE);
            lower.setAlpha(1f);
            lower.setVisibility(View.VISIBLE);
            landscapeHeader.setVisibility(View.GONE);
            landscapeActions.setVisibility(View.GONE);
            applyMuteState();
            bindCreatorChrome(next);
            comments.setVisibility(supportsComments(next) ? View.VISIBLE : View.GONE);
            landscapeComments.setVisibility(supportsComments(next) ? View.VISIBLE : View.GONE);
            updateSaveButton(next, save);
            updateSaveButton(next, landscapeSave);
            syncLoading();
            failure.setText("Couldn't play this one\nSwipe up for the next video");
            failure.setContentDescription("Couldn't play this video");
            failure.setVisibility(View.GONE);
            poster.setVisibility(View.VISIBLE);
            configurePosterPresentation(poster, horizontalVideo);
            Glide.with(poster).clear(poster);
            if (next.imageUrl == null || next.imageUrl.isEmpty()) {
                poster.setImageDrawable(new ColorDrawable(Color.rgb(20, 20, 22)));
            } else {
                // This placeholder stays visible until Media3 renders a real frame.
                // Cropping horizontal thumbnails fills the whole portrait viewport,
                // producing the giant zoom seen when returning to Chaos.
                com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> request =
                        Glide.with(poster).load(imageWithHeaders(next.imageUrl, next.url));
                if (horizontalVideo) request = request.fitCenter();
                else request = request.centerCrop();
                request.diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .dontAnimate()
                        .placeholder(new ColorDrawable(Color.rgb(20, 20, 22)))
                        .error(new ColorDrawable(Color.rgb(20, 20, 22)))
                        .into(poster);
            }
            syncOrientationChrome();
            refreshVisualTheme();
        }

        private void resetVisualMotion() {
            progressRow.animate().withEndAction(null).cancel();
            progressChromeVisible = null;
            for (View view : new View[] {lower, creatorAvatarControl, landscapeHeader, landscapeActions}) {
                view.animate().withEndAction(null).cancel();
                view.setTranslationY(0f);
                view.setAlpha(1f);
            }
            creatorFavoriteBadge.animate().withEndAction(null).cancel();
            creatorFavoriteBadge.setScaleX(1f);
            creatorFavoriteBadge.setScaleY(1f);
            like.animate().withEndAction(null).cancel();
            like.setScaleX(1f);
            like.setScaleY(1f);
            cancelAccessHint();
            setSwipeAlpha(1f);
        }

        void clearChangedSocialAccount() {
            String owner = ZeroChillSessionStore.currentUserId(activity);
            if (!owner.equals(videoLikeOwner)) {
                videoLikeOwner = owner;
                videoLikeRequestGeneration++;
                videoLikePending = false;
                videoLiked = false; videoLikeCount = 0; updateVideoLikeButton();
            }
            if (!owner.equals(commentCountOwner)) {
                commentCountOwner = owner;
                commentCountRequestGeneration++;
                commentCount = 0; updateCommentButton();
            }
        }

        void refreshVideoLikeState() {
            if (item == null || item.url == null || item.url.trim().isEmpty()) return;
            final String summaryOwner = ZeroChillSessionStore.currentUserId(activity);
            clearChangedSocialAccount();
            if (videoLikePending) return;
            String target = item.url;
            int generation = ++videoLikeRequestGeneration;
            ZeroChillSocialRepository.videoLikeState(activity, target, (state, error) ->
                    activity.runOnUiThread(() -> deferUiApply(() -> {
                        if (!summaryOwner.equals(ZeroChillSessionStore.currentUserId(activity))
                                || generation != videoLikeRequestGeneration
                                || item == null
                                || !target.equals(item.url)
                                || state == null
                                || error != null) {
                            return;
                        }
                        if (state.liked && !videoLiked && boundPosition == selectedPosition) lateLikedSelections++;
                        videoLiked = state.liked;
                        if (state.liked) {
                            ShitTokSmartRanker.action(item, ShitTokSmartRanker.Action.LIKE);
                            CrazyShitRepository.StreamInfo likedStream = streamCache.get(target);
                            seenStore.heart(target, likedStream == null ? null : likedStream.mediaUrl);
                        }
                        if (state.countKnown) videoLikeCount = state.count;
                        updateVideoLikeButton();
                    }))
            );
        }

        private void toggleVideoLike() {
            changeVideoLike(false);
        }

        private void changeVideoLike(boolean favoriteOnly) {
            if (item == null || item.url == null || item.url.trim().isEmpty()) return;
            if (!like.isEnabled() || (favoriteOnly && videoLiked)) return;
            if (!ZeroChillAccountRepository.hasStoredSession(activity)) {
                activity.startActivity(new Intent(activity, ZeroChillAccountActivity.class));
                return;
            }

            String target = item.url;
            boolean wasLiked = videoLiked;
            int oldCount = videoLikeCount;
            int request = ++videoLikeRequestGeneration;
            final String owner = ZeroChillSessionStore.currentUserId(activity);
            boolean optimistic = favoriteOnly || BuildConfig.ZEROCHILL_V7_TRAFFIC;
            if (optimistic) {
                videoLiked = !wasLiked;
                videoLikeCount = Math.max(0, oldCount + (wasLiked ? -1 : 1));
                updateVideoLikeButton();
            }
            videoLikePending = true;
            like.setEnabled(false);
            landscapeLike.setEnabled(false);
            ZeroChillSocialRepository.toggleVideoLike(activity, item, wasLiked, (state, error) ->
                    activity.runOnUiThread(() -> {
                        if (request != videoLikeRequestGeneration || item == null || !target.equals(item.url)) return;
                        videoLikePending = false;
                        like.setEnabled(true);
                        landscapeLike.setEnabled(true);
                        if (!owner.equals(ZeroChillSessionStore.currentUserId(activity))) {
                            videoLiked = false; videoLikeCount = 0; updateVideoLikeButton();
                            refreshVideoLikeState();
                            return;
                        }
                        if (error != null || state == null) {
                            if (optimistic) {
                                videoLiked = wasLiked;
                                videoLikeCount = oldCount;
                                updateVideoLikeButton();
                            }
                            ZeroChillToast.makeText(
                                    activity,
                                    error == null ? "Unable to update the like." : error.getMessage(),
                                    ZeroChillToast.LENGTH_LONG
                            ).show();
                            return;
                        }
                        videoLiked = state.liked;
                        if (state.liked) {
                            ShitTokSmartRanker.action(item, ShitTokSmartRanker.Action.LIKE);
                            CrazyShitRepository.StreamInfo likedStream = streamCache.get(target);
                            seenStore.heart(target, likedStream == null ? null : likedStream.mediaUrl);
                        }
                        if (state.countKnown) videoLikeCount = state.count;
                        updateVideoLikeButton();
                    })
            );
        }

        private void updateVideoLikeButton() {
            if (like == null) return;
            like.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    videoLiked ? R.drawable.ic_shittok_rail_heart_filled : R.drawable.ic_shittok_rail_heart,
                    0,
                    0
            );
            like.setCompoundDrawableTintList(ColorStateList.valueOf(
                    videoLiked ? AppearanceTheme.accent(activity) : Color.WHITE
            ));
            like.setText(videoLikeCount > 0 ? String.valueOf(videoLikeCount) : "");
            like.setTextSize(10);
            like.setTextColor(videoLiked ? AppearanceTheme.accent(activity) : Color.WHITE);
            like.setContentDescription(
            videoLiked ? "Unlike video" : "Like video"
            );
            landscapeLike.setCompoundDrawablesWithIntrinsicBounds(
                    0, videoLiked ? R.drawable.ic_shittok_rail_heart_filled
                            : R.drawable.ic_shittok_rail_heart, 0, 0);
            TextViewCompat.setCompoundDrawableTintList(landscapeLike, ColorStateList.valueOf(
                    videoLiked ? AppearanceTheme.accent(activity) : Color.WHITE));
            landscapeLike.setContentDescription(videoLiked ? "Unlike video" : "Like video");
            like.setEnabled(!videoLikePending);
            landscapeLike.setEnabled(!videoLikePending);
        }

        void refreshCommentCount() {
            if (item == null || item.url == null || item.url.trim().isEmpty()
                    || !supportsComments(item)) return;
            String target = item.url;
            clearChangedSocialAccount();
            int generation = ++commentCountRequestGeneration;
            final String summaryOwner = ZeroChillSessionStore.currentUserId(activity);
            ZeroChillSocialRepository.loadCommentCount(activity, target, (count, error) ->
                    activity.runOnUiThread(() -> deferUiApply(() -> {
                        if (!summaryOwner.equals(ZeroChillSessionStore.currentUserId(activity))
                                || generation != commentCountRequestGeneration
                                || item == null
                                || !target.equals(item.url)
                                || count == null
                                || error != null) {
                            return;
                        }
                        commentCount = count;
                        updateCommentButton();
                    }))
            );
        }

        private void updateCommentButton() {
            if (comments == null) return;
            comments.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    R.drawable.ic_shittok_rail_comments,
                    0,
                    0
            );
            comments.setText(commentCount > 0 ? String.valueOf(commentCount) : "");
            comments.setTextSize(10);
            comments.setTextColor(Color.WHITE);
            comments.setContentDescription(
                    commentCount == 1 ? "Open 1 comment"
                            : commentCount > 1 ? "Open " + commentCount + " comments"
                            : "Open comments"
            );
            comments.setEnabled(true);
            landscapeComments.setContentDescription(comments.getContentDescription());
        }

        void resizeMediaForComments(int sheetTopOnScreen) {
            if (root.getHeight() <= 0 || mediaLayer.getWidth() <= 0) return;
            mediaLayer.animate().cancel();
            mediaLayer.setScaleX(1f);
            mediaLayer.setScaleY(1f);
            if (!horizontalVideo || videoAspectRatio <= 1f) {
                mediaLayer.setTranslationY(0f);
                return;
            }

            int[] rootLocation = new int[2];
            root.getLocationOnScreen(rootLocation);
            float available = Math.max(0f, sheetTopOnScreen - rootLocation[1]);
            float viewportHeight = mediaLayer.getHeight();
            float renderedVideoHeight = Math.min(
                    viewportHeight,
                    mediaLayer.getWidth() / videoAspectRatio
            );
            float currentTop = (viewportHeight - renderedVideoHeight) / 2f;
            float targetTop = Math.max(dp(8), (available - renderedVideoHeight) / 2f);
            mediaLayer.setTranslationY(Math.min(0f, targetTop - currentTop));
        }

        void restoreMediaAfterComments(Runnable endAction) {
            mediaLayer.animate().cancel();
            mediaLayer.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(190L)
                    .withEndAction(() -> {
                        mediaLayer.setScaleX(1f);
                        mediaLayer.setScaleY(1f);
                        mediaLayer.setTranslationY(0f);
                        if (endAction != null) endAction.run();
                    })
                    .start();
        }

        void prepare(CrazyShitRepository.StreamInfo nextStream, boolean autoplay) {
            Trace.beginSection("zc.shittok.prepare");
            try {
                prepareMeasured(nextStream, autoplay);
            } finally {
                Trace.endSection();
            }
        }

        private void prepareMeasured(CrazyShitRepository.StreamInfo nextStream, boolean autoplay) {
            if (searchCoverActive || isPaging()) return;
            if (item == null || nextStream == null || nextStream.mediaUrl == null || nextStream.mediaUrl.isEmpty()) return;
            attachLease();
            if (player == null) return;
            if (stream != nextStream) cancelInteractionUi();
            stream = nextStream;
            lastAttemptedStream = nextStream;
            userPaused = playbackOwner.isUserPaused(item.url);
            if (uiPlayerListener != null) {
                if (autoplay && !userPaused && ownsPlayback()) {
                    if (player.getPlaybackState() == Player.STATE_ENDED) handleCompletion(player);
                    else playOwnedPlayer();
                }
                if (player.getPlaybackState() == Player.STATE_READY) {
                    syncLoading();
                }
                syncPoster();
                return;
            }
            ExoPlayer createdPlayer = player;
            final String listenerUrl = item.url;
            final int listenerToken = attachedToken;
            final int listenerGeneration = feedGeneration;
            uiPlayerListener = new Player.Listener() {
                @Override
                public void onVideoSizeChanged(VideoSize videoSize) {
                    if (listenerGeneration != feedGeneration || !validUiCallback(createdPlayer, listenerUrl, listenerToken)
                            || videoSize.width <= 0 || videoSize.height <= 0) return;
                    postOwnerUi(createdPlayer, listenerUrl, listenerToken, () -> {
                        // Media3 VideoSize already contains applied rotation. Format-based admission
                        // accounts for container rotation before this surface is ever attached.
                        videoAspectRatio = ShitTokOrientation.effectiveAspect(
                                videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio, 0);
                        horizontalVideo = shouldOfferLandscapeFullscreen(videoAspectRatio);
                        configurePosterPresentation(poster, horizontalVideo);
                        updateAmbientBackdrop();
                        updateFullscreenPromptPosition();
                        if (!aspectSampleRecorded) {
                            aspectSampleRecorded = true;
                            aspectPriority.record(item, videoAspectRatio);
                        }
                        syncOrientationChrome();
                    });
                }

                private boolean analyticsViewed;
                private long analyticsBufferStart;
                @Override
                public void onRenderedFirstFrame() {
                    if (listenerGeneration != feedGeneration || !validUiCallback(createdPlayer, listenerUrl, listenerToken)) return;
                    postOwnerUi(createdPlayer, listenerUrl, listenerToken, () -> {
                        syncPoster();
                        if (boundPosition != selectedPosition) return;
                        syncLoading();
                        updateLiveAmbientSampling();
                        if (categoryTapAtMs != 0L) {
                            ShitTokPoolDiagnostics.record("categoryFirstFrame",
                                    "elapsedMs=" + (android.os.SystemClock.elapsedRealtime() - categoryTapAtMs)
                                            + " bufferedMs=" + playbackOwner.selectedBufferedMs());
                            categoryTapAtMs = 0L;
                        }
                        if (listenerUrl.equals(deferredReturnDiscoveryUrl)) {
                            releaseDeferredReturnDiscovery();
                        }
                        sourceMixer.onFirstFrame();
                        requestPreferenceCandidates();
                        ShitTokPoolDiagnostics.record("selectedFrame", "position=" + boundPosition);
                    });
                }

                @Override
                public void onPlaybackStateChanged(int state) {
                    if (listenerGeneration != feedGeneration || !validUiCallback(createdPlayer, listenerUrl, listenerToken)) return;
                    final int completionVisit = playbackVisit;
                    final boolean completionOwned = ownsPlayback();
                    postOwnerUi(createdPlayer, listenerUrl, listenerToken, () -> {
                        if (createdPlayer.getPlaybackState() != state
                                || (state == Player.STATE_ENDED && completionVisit != playbackVisit)) return;
                        syncLoading();
                        if (boundPosition == selectedPosition && state == Player.STATE_BUFFERING) {
                            sourceMixer.preferencePreparation.buffering();
                            resetPreferencePreparationHealth();
                            if (analyticsBufferStart == 0) analyticsBufferStart = android.os.SystemClock.elapsedRealtime();
                        }
                        if (state == Player.STATE_READY) {
                            if(analyticsBufferStart!=0 && android.os.SystemClock.elapsedRealtime()-analyticsBufferStart>=2000) AnalyticsTracker.event(activity,"buffering");
                            analyticsBufferStart=0;
                            RatingFeedbackPrompt.recordSuccessfulPlayback(activity, nextStream.mediaUrl);
                            failurePending = false;
                            maintenanceFailure = false;
                            root.removeCallbacks(skipFailedClipRunnable);
                            syncLoading();
                            failure.setVisibility(View.GONE);
                            syncPoster();
                            updateProgress();
                            maybeCompleteStartupHandoff();
                            preloadReadyComments(item, boundPosition);
                        } else if (state == Player.STATE_ENDED) {
                            if (boundPosition == selectedPosition) sessionResume.clear();
                            syncLoading();
                            seekBar.setProgress(1000);
                            updateProgress();
                            if (item != null) {
                                try {
                                    long duration = Math.max(0L, createdPlayer.getDuration());
                                    PlaybackHistoryStore.record(activity, item, duration, duration, true, false);
                                } catch (Exception ignored) { }
                            }
                            // A late end emitted during paging can already carry the new
                            // selection's visit. Keep its history, but never navigate from it.
                            if (completionOwned) handleCompletion(createdPlayer);
                        }
                    });
                }

                @Override
                public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition,
                                                    int reason) {
                    if (landscapeFullscreen() && listenerGeneration == feedGeneration
                            && validUiCallback(createdPlayer, listenerUrl, listenerToken)) updateProgress();
                }

                @Override
                public void onPlaybackParametersChanged(androidx.media3.common.PlaybackParameters parameters) {
                    if (landscapeFullscreen() && listenerGeneration == feedGeneration
                            && validUiCallback(createdPlayer, listenerUrl, listenerToken)) syncFullscreenPlayback();
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (listenerGeneration == feedGeneration && validUiCallback(createdPlayer, listenerUrl, listenerToken)) {
                        if (isPlaying && ownsPlayback()) ShitTokSmartRanker.onViewed(listenerUrl);
                        ShitTokSmartRanker.playback(listenerUrl, isPlaying && ownsPlayback());
                    }
                    if (listenerGeneration != feedGeneration || !validUiCallback(createdPlayer, listenerUrl, listenerToken) || isPaging()) return;
                    if (isPlaying && !ownsPlayback()) {
                        silencePlayer(createdPlayer);
                        return;
                    }
                    if (!isPlaying && boundPosition == selectedPosition) resetPreferencePreparationHealth();
                    updateLiveAmbientSampling();
                    if (isPlaying) {
                        if(!analyticsViewed && boundPosition==selectedPosition) {analyticsViewed=true; AnalyticsTracker.event(activity,"shittok_videos");}
                        playbackSuspended = false;
                        userPaused = false;
                        everStarted = true;
                        hidePausedChrome();
                        if (!landscapeFullscreen()) showControlsTemporarily();
                    } else {
                        updateProgress();
                        if (userPaused) {
                            if (!scrubbing) showControlsPersistent();
                            syncPausedChrome();
                        } else hidePausedChrome();
                    }
                    if (landscapeFullscreen()) {
                        syncFullscreenPlayback();
                        armFullscreenAutoHide();
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    if (listenerGeneration != feedGeneration || !validUiCallback(createdPlayer, listenerUrl, listenerToken)) return;
                    postOwnerUi(createdPlayer, listenerUrl, listenerToken, () -> {
                        if (createdPlayer.getPlayerError() != error
                                || (lastPlaybackError == error && retryAttempted)) return;
                        resetPreferencePreparationHealth();
                        if (playbackOwner.recoverDecoder(createdPlayer, error)) return;
                        if (boundPosition > selectedPosition && item != null && unplayable.contains(item.url)) {
                            requestContinuity();
                            return;
                        }
                        syncLoading();
                        userPaused = false;
                        hidePausedChrome();
                        if(boundPosition==selectedPosition) AnalyticsTracker.failure(activity,"playback_failure",AnalyticsV2Policy.playbackCategory(error.errorCode));
                        lastPlaybackError = error;
                        lastFailureStage = "Player error";
                        if (!retryAttempted) {
                            retryAttempted = true;
                            postOwnerUi(createdPlayer, listenerUrl, listenerToken, () -> {
                                if (createdPlayer.getPlayerError() == error) retryPlayback(ChaosHolder.this, error);
                            });
                        } else showPlayerFailureAndSkip(error, lastFailureStage);
                    });
                }
            };
            createdPlayer.addListener(uiPlayerListener);
            if (createdPlayer.getPlaybackState() == Player.STATE_READY) {
                syncLoading();
                androidx.media3.common.VideoSize size = createdPlayer.getVideoSize();
                uiPlayerListener.onVideoSizeChanged(size);
                uiPlayerListener.onPlaybackStateChanged(Player.STATE_READY);
            }
            syncPoster();
            syncLoading();
            if (autoplay && !userPaused && ownsPlayback()) playOwnedPlayer();
        }

        private void syncPoster() {
            boolean rendered = item != null && playbackOwner.hasRenderedFrame(item.url, playerView);
            poster.setVisibility(rendered && !failurePending ? View.GONE : View.VISIBLE);
        }

        private void updateAmbientBackdrop() {
            boolean usable = horizontalVideo && item != null && item.imageUrl != null
                    && !item.imageUrl.trim().isEmpty();
            if (!usable) {
                Glide.with(ambientBackdrop).clear(ambientBackdrop);
                ambientUrl = "";
                ambientBackdrop.setVisibility(View.GONE);
                return;
            }
            if (!item.imageUrl.equals(ambientUrl)) {
                ambientUrl = item.imageUrl;
                Glide.with(ambientBackdrop).load(imageWithHeaders(item.imageUrl, item.url))
                        .centerCrop().diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .dontAnimate().into(ambientBackdrop);
            }
            ambientBackdrop.setVisibility(landscapeFullscreen() ? View.GONE : View.VISIBLE);
        }

        private boolean canSampleLiveAmbient() {
            View surface = playerView.getVideoSurfaceView();
            boolean selected = !closed && boundPosition == selectedPosition
                    && item != null && holderAt(boundPosition) == this
                    && !isPaging() && !searchCoverActive;
            return ShitTokAmbientSampling.shouldSample(
                    selected, active, hostResumed,
                    player != null && player.isPlaying(), horizontalVideo,
                    landscapeFullscreen(), clearDisplay || pinch.isActive(),
                    surface instanceof TextureView
                            && ((TextureView) surface).isAvailable()
                            && root.isAttachedToWindow(),
                    Build.VERSION.SDK_INT >= 31,
                    liveAmbientBackdrop.hasFailed());
        }

        private void updateLiveAmbientSampling() {
            View surface = playerView.getVideoSurfaceView();
            if (surface instanceof TextureView) {
                liveAmbientBackdrop.bind((TextureView) surface);
            }
            // User pause keeps the most recently rendered GPU blur while everything
            // else (offscreen, tab changes, fullscreen, pinch) still stops the layer.
            boolean holdPaused = userPaused && ShitTokAmbientSampling.shouldSample(
                    !closed && item != null && boundPosition == selectedPosition
                            && holderAt(boundPosition) == this && !isPaging() && !searchCoverActive,
                    active, hostResumed, true, horizontalVideo,
                    landscapeFullscreen(), clearDisplay || pinch.isActive(),
                    surface instanceof TextureView
                            && ((TextureView) surface).isAvailable()
                            && root.isAttachedToWindow(),
                    Build.VERSION.SDK_INT >= 31, liveAmbientBackdrop.hasFailed());
            liveAmbientBackdrop.setMotionEnabled(canSampleLiveAmbient(), holdPaused);
        }

        private void stopLiveAmbient(boolean failure) {
            if (failure) liveAmbientBackdrop.reset();
            else liveAmbientBackdrop.stop();
        }

        private void syncLoading() {
            boolean selected = !closed && active && hostResumed && item != null
                    && boundPosition == selectedPosition && hasSelectedPage()
                    && item.url.equals(items.get(selectedPosition).url) && holderAt(boundPosition) == this;
            boolean waiting = player == null || player.getPlaybackState() == Player.STATE_IDLE
                    || player.getPlaybackState() == Player.STATE_BUFFERING;
            loading.setVisibility(selected && waiting && !failurePending
                    && (player == null || player.getPlayerError() == null) ? View.VISIBLE : View.GONE);
        }

        private boolean validUiCallback(ExoPlayer expected, String url, int token) {
            return player == expected && item != null && item.url.equals(url)
                    && playbackOwner.accepts(url, token) && holderAt(boundPosition) == this
                    && playbackOwner.isAttached(url, playerView);
        }

        private void postOwnerUi(ExoPlayer expected, String url, int token, Runnable action) {
            final int generation = feedGeneration;
            root.post(() -> deferUiApply(() -> {
                if (generation == feedGeneration && validUiCallback(expected, url, token)) action.run();
            }));
        }

        void attachLease() {
            if (item == null || closed || isPaging() || holderAt(boundPosition) != this) return;
            ExoPlayer acquired = playbackOwner.player(item.url);
            if (acquired == null) return;
            if (player != acquired) {
                detachLease();
                player = acquired;
                attachedToken = playbackOwner.token(item.url);
            }
            playbackOwner.attach(item.url, playerView);
            syncPoster();
            syncMuteButtons();
        }

        void detachLease() {
            stopLiveAmbient(false);
            endScrubPreview(false, false);
            cancelInteractionUi();
            pinch.reset();
            if (player != null) {
                if (uiPlayerListener != null) player.removeListener(uiPlayerListener);
                if (item != null) playbackOwner.detach(item.url, playerView);
                else playerView.setPlayer(null);
            }
            player = null;
            uiPlayerListener = null;
            attachedToken = -1;
            stream = null;
            poster.setVisibility(View.VISIBLE);
            syncLoading();
            root.removeCallbacks(progressTick);
        }

        private void maybeCompleteStartupHandoff() {
            if (!ChaosStartupHandoff.isWaiting() || player == null) return;
            if (!active || !hostResumed || boundPosition != selectedPosition) return;
            if (player.getPlaybackState() != Player.STATE_READY) return;
            ChaosStartupHandoff.markFirstChaosPlayerReady();
        }

        private boolean isBoundTo(String pageUrl, int position) {
            return item != null
                    && pageUrl != null
                    && pageUrl.equals(item.url)
                    && boundPosition == position;
        }

        private void noteResolutionRetry() {
            retryAttempted = true;
            lastFailureStage = "Stream resolution retry";
        }

        private void noteResolutionRetryIfNeeded(boolean retried) {
            if (!retried) return;
            retryAttempted = true;
            if (lastFailureStage.isEmpty()) lastFailureStage = "Stream resolution retry";
        }

        private CrazyShitRepository.StreamInfo diagnosticStream() {
            return stream != null ? stream : lastAttemptedStream;
        }

        private void putHeaderIfMissing(Map<String, String> headers, String name, String value) {
            if (headers == null || name == null || value == null || value.trim().isEmpty()) return;
            for (String key : headers.keySet()) {
                if (key != null && name.equalsIgnoreCase(key)) return;
            }
            headers.put(name, value);
        }

        private void openEromeGallery(
                NativeContentItem clip,
                String albumUrl,
                boolean seamless,
                String transitionToken
        ) {
            pinch.reset();
            String album = albumUrl == null || albumUrl.isEmpty()
                    ? ShitTokEromeGallery.albumUrl(clip) : albumUrl;
            if (clip == null || album.isEmpty()) return;
            ShitTokSmartRanker.action(clip, ShitTokSmartRanker.Action.GALLERY);
            String returnToken = transitionToken == null ? "" : transitionToken.trim();
            if (returnToken.isEmpty()) {
                View sourceSurface = creatorSwipeSourceView();
                returnToken = ShitTokTransitionSnapshotStore.beginCapture(
                        activity,
                        sourceSurface == null ? root : sourceSurface,
                        playerView.getVideoSurfaceView()
                );
            }
            rememberSelectedPosition();
            pauseAndRecord();
            NativeContentItem albumItem = new NativeContentItem(
                    NativeContentItem.KIND_SERIES,
                    clip.title == null || clip.title.isEmpty() ? "Erome album" : clip.title,
                    album,
                    clip.imageUrl,
                    "",
                    clip.uploader,
                    "",
                    ""
            );
            Intent intent = NativeFeedBrowserActivity.createShowDetails(
                    activity, albumItem, NativeFeedBrowserActivity.SOURCE_EROME);
            if (!returnToken.isEmpty()) {
                intent.putExtra(
                        NativeFeedBrowserActivity.EXTRA_SHITTOK_RETURN_TRANSITION,
                        returnToken
                );
            }
            creatorGalleryHandoff = true;
            activity.startActivity(intent);
            if (seamless) activity.overridePendingTransition(0, 0);
        }

        private void openCreatorGallery(boolean seamless, String transitionToken) {
            pinch.reset();
            if (item == null) return;
            NativeContentItem identity = creatorIdentity;
            if (identity == null) return;
            ShitTokSmartRanker.action(item, ShitTokSmartRanker.Action.GALLERY);
            String returnToken = transitionToken == null ? "" : transitionToken.trim();
            if (returnToken.isEmpty()) {
                View sourceSurface = creatorSwipeSourceView();
                returnToken = ShitTokTransitionSnapshotStore.beginCapture(
                        activity,
                        sourceSurface == null ? root : sourceSurface,
                        playerView.getVideoSurfaceView()
                );
            }
            pauseAndRecord();
            Intent intent = NativeFeedBrowserActivity.createCreatorGallery(activity, identity);
            if (!returnToken.isEmpty()) {
                intent.putExtra(
                        NativeFeedBrowserActivity.EXTRA_SHITTOK_RETURN_TRANSITION,
                        returnToken
                );
            }
            activity.startActivity(intent);
            if (seamless) activity.overridePendingTransition(0, 0);
        }

        private void showMoreMenu(View anchor) {
            share(item);
        }

        private void downloadCurrentVideo() {
            if (item == null) return;
            if (stream == null || stream.mediaUrl == null || stream.mediaUrl.isEmpty()) {
                VideoDownloadStore.downloadPage(activity, item);
                return;
            }
            String userAgent = "";
            String cookies = "";
            try {
                userAgent = WebSettings.getDefaultUserAgent(activity);
            } catch (Exception ignored) {
            }
            try {
                cookies = CookieManager.getInstance().getCookie(stream.mediaUrl);
                if ((cookies == null || cookies.isEmpty()) && stream.pageUrl != null) {
                    cookies = CookieManager.getInstance().getCookie(stream.pageUrl);
                }
            } catch (Exception ignored) {
            }
            VideoDownloadStore.downloadKnown(
                    activity,
                    item,
                    stream.mediaUrl,
                    userAgent,
                    cookies,
                    stream.requestReferer
            );
        }

        private void openCurrentDetails() {
            if (item == null) return;
            pauseAndRecord();
            host.openDetails(item);
        }

        private boolean ownsPlayback() {
            return !closed && active && hostResumed && !isPaging() && item != null
                    && boundPosition == selectedPosition && boundPosition >= 0
                    && boundPosition < items.size()
                    && item.url.equals(items.get(boundPosition).url)
                    && holderAt(boundPosition) == this;
        }

        private void toggleFocusPlayback() {
            if (player == null || item == null || !ownsPlayback()) return;
            boolean pause = !userPaused && player.getPlayWhenReady();
            userPaused = pause;
            playbackOwner.setUserPaused(item.url, pause);
            if (pause) {
                cancelPendingAutoAdvance();
            } else {
                everStarted = true;
                if (player.getPlaybackState() == Player.STATE_ENDED) player.seekTo(0L);
                playOwnedPlayer();
            }
        }

        private void playOwnedPlayer() {
            if (player == null) return;
            if (scrubbing) return;
            if (!ownsPlayback()) {
                silencePlayer(player);
                return;
            }
            pauseNonSelected(boundPosition);
            playbackSuspended = false;
            playbackOwner.permission(true, chaosMuted);
            playbackOwner.requestPlay(item.url);
        }

        void applyMuteState() {
            syncMuteButtons();
            playbackOwner.permission(active && hostResumed && !closed && !isPaging(), chaosMuted);
            if (player != null && !ownsPlayback()) silencePlayer(player);
        }

        void handleCompletion(ExoPlayer completedPlayer) {
            if (searchCoverActive || player != completedPlayer || failurePending) return;
            if (!ownsPlayback()) {
                silencePlayer(completedPlayer);
                return;
            }
            if (completedPlayer.getPlaybackState() != Player.STATE_ENDED) return;
            if (item != null) ShitTokSmartRanker.completion(item.url, completedPlayer.getDuration());
            ShitTokAutoScrollPolicy.Completion decision = ShitTokAutoScrollPolicy.onCompleted(
                    active, hostResumed, boundPosition == selectedPosition, userPaused,
                    autoScrollEnabled && !fullscreenInputLocked() && !interactionOverlayOpen(), userPaging);
            if (decision == ShitTokAutoScrollPolicy.Completion.ADVANCE) {
                requestAutoAdvance(boundPosition);
            } else if (decision == ShitTokAutoScrollPolicy.Completion.LOOP) {
                completedPlayer.seekTo(0L);
                playOwnedPlayer();
            }
        }

        private void syncPausedChrome() {
            if (pinch.suppressesFeedChrome()) return;
            boolean show = !landscapeFullscreen() && shouldShowPausedChrome(userPaused, clearDisplay);
            pausePlayOverlay.animate().cancel();
            if (show) {
                if (pausePlayOverlay.getVisibility() != View.VISIBLE) {
                    pausePlayOverlay.setAlpha(0f);
                    pausePlayOverlay.setVisibility(View.VISIBLE);
                    pausePlayOverlay.animate()
                            .alpha(0.72f)
                            .setDuration(140L)
                            .start();
                } else {
                    pausePlayOverlay.setAlpha(0.72f);
                }
            } else {
                pausePlayOverlay.setVisibility(View.GONE);
            }
            syncProgressChrome(player == null ? 0L : player.getDuration());
            updateProgress();
            restartProgressTick();
        }

        private void hidePausedChrome() {
            if (pinch.suppressesFeedChrome()) return;
            pausePlayOverlay.animate().cancel();
            pausePlayOverlay.setVisibility(View.GONE);
            syncProgressChrome(player == null ? 0L : player.getDuration());
            restartProgressTick();
        }

        private void syncProgressChrome(long duration) {
            boolean landscape = landscapeFullscreen();
            if (landscape) {
                progressRow.animate().withEndAction(null).cancel();
                progressRow.setAlpha(1f);
                progressRow.setVisibility(View.VISIBLE);
                elapsedTime.setVisibility(View.VISIBLE);
                remainingTime.setVisibility(View.VISIBLE);
            }
            boolean interactive = landscape || scrubbing || shouldShowPausedChrome(userPaused, clearDisplay);
            boolean visible = !clearDisplay && !pinch.suppressesFeedChrome()
                    && (interactive || ShitTokInteractionPolicy.persistentProgress(duration));
            if (!landscape && visible && !Boolean.TRUE.equals(progressChromeVisible)) {
                progressRow.animate().withEndAction(null).cancel();
                progressRow.setVisibility(View.VISIBLE);
                if (ZeroChillMotion.animationsEnabled(activity) && !scrubbing) {
                    progressRow.setAlpha(0f);
                    progressRow.animate().alpha(1f).setDuration(ZeroChillMotion.QUICK_MS).start();
                } else progressRow.setAlpha(1f);
            } else if (!landscape && !visible && !Boolean.FALSE.equals(progressChromeVisible)) {
                progressRow.animate().withEndAction(null).cancel();
                if (ZeroChillMotion.animationsEnabled(activity)) {
                    progressRow.animate().alpha(0f).setDuration(ZeroChillMotion.QUICK_MS)
                            .withEndAction(() -> { if (!scrubbing) progressRow.setVisibility(View.INVISIBLE); })
                            .start();
                } else {
                    progressRow.setAlpha(0f);
                    progressRow.setVisibility(View.INVISIBLE);
                }
            }
            progressChromeVisible = visible;
            int timeVisibility = landscape ? View.VISIBLE : View.GONE;
            elapsedTime.setVisibility(timeVisibility);
            remainingTime.setVisibility(timeVisibility);
            boolean portraitProgress = !landscape;
            if (progressInteractive == null || progressInteractive != interactive
                    || progressPortrait == null || progressPortrait != portraitProgress) {
                progressInteractive = interactive;
                progressPortrait = portraitProgress;
                // Preserve the 48dp hit row while keeping playback's track quiet.
                int trackHeight = interactive ? 4 : 2;
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    seekBar.setMinHeight(dp(trackHeight));
                    seekBar.setMaxHeight(dp(trackHeight));
                }
                if (seekBar.getThumb() != null) seekBar.getThumb().mutate().setAlpha(interactive ? 255 : 0);
                seekBar.setAlpha(interactive ? 1f : .62f);
            }
        }

        private void restartProgressTick() {
            root.removeCallbacks(progressTick);
            if (active && hostResumed && boundPosition == selectedPosition && !clearDisplay
                    && !scrubbing && player != null && player.isPlaying()
                    && progressRow.getVisibility() == View.VISIBLE
                    && (!landscapeFullscreen() || controlsVisible)) root.postDelayed(progressTick, 250L);
        }

        void showControlsTemporarily() {
            showControls(true);
        }

        private void showControlsPersistent() {
            showControls(false);
        }

        private void applyViewportInset() {
            int bottom = portraitViewportBottomInset();
            if (root.getPaddingLeft() == 0 && root.getPaddingTop() == 0 &&
                    root.getPaddingRight() == 0 && root.getPaddingBottom() == bottom) {
                return;
            }
            root.setPadding(0, 0, 0, bottom);
        }

        private boolean portrait() {
            return activity.getResources().getConfiguration().orientation
                    != Configuration.ORIENTATION_LANDSCAPE;
        }

        private boolean landscapeFullscreen() {
            return landscapePlayerVisible();
        }

        private TextView progressTimeLabel(String tag, String initial) {
            TextView label = new TextView(activity);
            label.setTag(tag);
            label.setText(initial);
            label.setTextColor(Color.WHITE);
            label.setTextSize(12);
            label.setGravity(Gravity.CENTER);
            label.setMinWidth(dp(52));
            label.setVisibility(View.GONE);
            return label;
        }

        private int landscapeBottomInset() {
            return FullscreenPlayerStyle.bottomInset(root);
        }

        private void updateFullscreenPromptPosition() {
            if (root.getWidth() <= 0 || root.getHeight() <= 0 || videoAspectRatio <= 0f
                    || landscapeFullscreen()) return;
            FrameLayout.LayoutParams mediaParams = (FrameLayout.LayoutParams) mediaLayer.getLayoutParams();
            int usableHeight = root.getHeight() - root.getPaddingBottom();
            int playerInset = playerView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams
                    ? ((ViewGroup.MarginLayoutParams) playerView.getLayoutParams()).bottomMargin : 0;
            int mediaHeight = usableHeight - mediaParams.topMargin - mediaParams.bottomMargin - playerInset;
            int fittedHeight = Math.min(mediaHeight, Math.round(root.getWidth() / videoAspectRatio));
            int videoBottom = mediaParams.topMargin + (mediaHeight + fittedHeight) / 2;
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) fullscreen.getLayoutParams();
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            int top = videoBottom + dp(8);
            if (params.topMargin != top || params.bottomMargin != 0) {
                params.topMargin = top;
                params.bottomMargin = 0;
                fullscreen.setLayoutParams(params);
            }
        }

        private void layoutPlaybackControls(boolean landscape) {
            androidx.core.view.WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
            androidx.core.graphics.Insets safe = insets == null ? androidx.core.graphics.Insets.NONE
                    : insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
                        | androidx.core.view.WindowInsetsCompat.Type.displayCutout()
                        | androidx.core.view.WindowInsetsCompat.Type.mandatorySystemGestures());
            int safeBottom = landscape ? Math.max(safe.bottom, landscapeBottomInset()) + dp(4) : 0;
            int left = landscape ? safe.left + dp(12) : dp(8);
            int right = landscape ? safe.right + dp(12) : dp(8);
            if (landscape) {
                layOutOverlayChild(landscapeHeader, left, safe.top + dp(4), right, 0);
                layOutOverlayChild(landscapeActions, left, 0, right, safeBottom);
            }
            FrameLayout target = landscape ? fullscreenOverlay : root;
            if (progressRow.getParent() != target) {
                detachChrome(progressRow);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM);
                if (landscape) target.addView(progressRow, params);
                else target.addView(progressRow, root.indexOfChild(lower), params);
                progressChromeVisible = null;
            }
            // Move the portrait track's center up so its bottom meets the video edge.
            layOutOverlayChild(progressRow, left, 0, right, landscape ? safeBottom + dp(48) : -dp(13));
            FrameLayout.LayoutParams progress = (FrameLayout.LayoutParams) progressRow.getLayoutParams();
            elapsedTime.setVisibility(landscape ? View.VISIBLE : View.GONE);
            remainingTime.setVisibility(landscape ? View.VISIBLE : View.GONE);
            seekBar.setPadding(0, landscape ? 0 : dp(18), 0, 0);
            // True fullscreen keeps the landscape media edge-to-edge. Header, scrubber and
            // actions are overlays so none of them reduce the fitted video viewport.
            int reservedBottom = horizontalVideo ? dp(44) : 0;
            int blurExtension = !landscape && reservedBottom > 0
                    ? ShitTokAmbientSampling.portraitBlurExtensionToSeek(
                            reservedBottom,
                            progress.height > 0 ? progress.height : dp(48),
                            progress.bottomMargin,
                            seekBar.getPaddingTop(),
                            seekBar.getPaddingBottom(),
                            dp(2))
                    : 0;
            FrameLayout.LayoutParams media = (FrameLayout.LayoutParams) mediaLayer.getLayoutParams();
            media.topMargin = 0;
            media.bottomMargin = landscape ? 0 : Math.max(0, reservedBottom - blurExtension);
            mediaLayer.setLayoutParams(media);
            // The sharp player, poster and spinner stay in the old viewport.
            // Only the blur layers, which match the media layer, reach the seek track.
            insetPlaybackViewport(blurExtension);
            updateFullscreenPromptPosition();
        }

        private void layOutOverlayChild(View child, int left, int top, int right, int bottom) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) child.getLayoutParams();
            if (params.leftMargin == left && params.topMargin == top
                    && params.rightMargin == right && params.bottomMargin == bottom) return;
            params.setMargins(left, top, right, bottom);
            child.setLayoutParams(params);
        }

        private void insetPlaybackViewport(int inset) {
            insetBottom(playerView, inset);
            insetBottom(poster, inset);
            insetBottom(failure, inset);
            insetBottom(loading, inset);
            insetBottom(pausePlayOverlay, inset);
        }

        private static void insetBottom(View view, int inset) {
            if (!(view.getLayoutParams() instanceof FrameLayout.LayoutParams)) return;
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
            if (params.bottomMargin == inset) return;
            params.bottomMargin = inset;
            view.setLayoutParams(params);
        }

        private void applyControlLayout() {
            boolean landscape = landscapeFullscreen();
            layoutPlaybackControls(landscape);
            fullscreenSurface.setVisibility(landscape ? View.VISIBLE : View.GONE);
            if (!landscape) {
                fullscreenOverlay.animate().withEndAction(null).cancel();
                fullscreenOverlay.setVisibility(View.GONE);
            }
            lower.setVisibility(landscape ? View.GONE : View.VISIBLE);
            creatorAvatarControl.setVisibility(!landscape && creatorIdentity != null
                    ? View.VISIBLE : View.GONE);
            fullscreen.setVisibility(!landscape && horizontalVideo
                    ? View.VISIBLE : View.GONE);
            landscapeTopScrim.setVisibility(landscape ? View.VISIBLE : View.GONE);
            landscapeBottomScrim.setVisibility(landscape ? View.VISIBLE : View.GONE);
            landscapeHeader.setVisibility(landscape ? View.VISIBLE : View.GONE);
            landscapeActions.setVisibility(landscape ? View.VISIBLE : View.GONE);
        }

        void syncOrientationChrome() {
            updateLiveAmbientSampling();
            if (pinch.suppressesFeedChrome()) return;
            updateAmbientBackdrop();
            applyViewportInset();
            boolean landscape = landscapeFullscreen();
            boolean entering = landscape && !landscapeControlsMounted;
            if (!landscape && landscapeControlsMounted) fullscreenGestures.cancel();
            landscapeControlsMounted = landscape;
            if (landscape) {
                if (boundPosition == selectedPosition) setFeedPagingEnabled(false);
                applyControlLayout();
                bindFullscreenCreator();
                pausePlayOverlay.setVisibility(View.GONE);
                if (entering) showFullscreenControls(true);
                else syncFullscreenPlayback();
                return;
            }
            if (clearDisplay) {
                applyClearDisplay(true);
                return;
            }
            showControls(false);
        }

        private void showControls(boolean autoHide) {
            if (pinch.suppressesFeedChrome()) return;
            if (landscapeFullscreen()) {
                showFullscreenControls(autoHide);
                return;
            }
            if (clearDisplay) {
                applyClearDisplay(true);
                return;
            }
            root.removeCallbacks(hideControlsRunnable);
            lower.animate().cancel();
            controlsVisible = true;
            resetEntranceOffsets();
            applyControlLayout();
            lower.setAlpha(1f);
            creatorAvatarControl.setAlpha(1f);
            fullscreen.setAlpha(1f);
            syncPausedChrome();
        }

        private void showFullscreenControls(boolean autoHide) {
            if (!landscapeFullscreen()) return;
            boolean reveal = fullscreenOverlay.getVisibility() != View.VISIBLE || !controlsVisible;
            controlsVisible = true;
            fullscreenOverlay.animate().withEndAction(null).cancel();
            fullscreenOverlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            fullscreenOverlay.setVisibility(View.VISIBLE);
            if (reveal && ZeroChillMotion.animationsEnabled(activity)) {
                fullscreenOverlay.setAlpha(0f);
                fullscreenOverlay.animate().alpha(1f).setDuration(FullscreenPlayerStyle.FADE_MS).start();
            } else {
                fullscreenOverlay.setAlpha(1f);
            }
            syncFullscreenPlayback();
            updateProgress();
            restartProgressTick();
            if (autoHide) armFullscreenAutoHide();
            else root.removeCallbacks(hideControlsRunnable);
        }

        private void hideFullscreenControls() {
            if (!landscapeFullscreen() || !controlsVisible || scrubbing || interactionOverlayOpen()) return;
            controlsVisible = false;
            root.removeCallbacks(hideControlsRunnable);
            root.removeCallbacks(progressTick);
            fullscreenOverlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            fullscreenOverlay.animate().withEndAction(null).cancel();
            if (ZeroChillMotion.animationsEnabled(activity)) {
                fullscreenOverlay.animate().alpha(0f).setDuration(FullscreenPlayerStyle.FADE_MS)
                        .withEndAction(() -> {
                            if (!controlsVisible) fullscreenOverlay.setVisibility(View.INVISIBLE);
                        }).start();
            } else {
                fullscreenOverlay.setAlpha(0f);
                fullscreenOverlay.setVisibility(View.INVISIBLE);
            }
        }

        private void armFullscreenAutoHide() {
            root.removeCallbacks(hideControlsRunnable);
            if (landscapeFullscreen() && controlsVisible && active && hostResumed && !scrubbing
                    && !interactionOverlayOpen() && player != null && player.isPlaying()) {
                root.postDelayed(hideControlsRunnable, 3_200L);
            }
        }

        private boolean canSeekFullscreen() {
            return player != null && ownsPlayback()
                    && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    && player.getDuration() > 0L;
        }

        private void syncFullscreenPlayback() {
            boolean playable = player != null && ownsPlayback();
            boolean playing = playable && player.getPlayWhenReady()
                    && player.getPlaybackState() != Player.STATE_ENDED;
            fullscreenPlayPause.setEnabled(playable);
            fullscreenPlayPause.setImageResource(playing ? R.drawable.ic_player_pause : R.drawable.ic_player_play);
            fullscreenPlayPause.setContentDescription(playing ? "Pause video" : "Play video");
            fullscreenSpeed.setEnabled(playable);
            String speed = fullscreenSpeedLabel(playable ? player.getPlaybackParameters().speed : 1f);
            fullscreenSpeed.setText(speed);
            fullscreenSpeed.setContentDescription("Playback speed " + speed);
        }

        private String fullscreenSpeedLabel(float speed) {
            return java.math.BigDecimal.valueOf((double) speed).stripTrailingZeros().toPlainString() + "×";
        }

        private void showFullscreenSpeedMenu() {
            if (player == null || !ownsPlayback() || !landscapeFullscreen() || interactionOverlayOpen()) return;
            ExoPlayer selected = player;
            float[] speeds = {.5f, .75f, 1f, 1.25f, 1.5f, 2f};
            String[] labels = new String[speeds.length];
            int checked = -1;
            for (int i = 0; i < speeds.length; i++) {
                labels[i] = fullscreenSpeedLabel(speeds[i]);
                if (speeds[i] == selected.getPlaybackParameters().speed) checked = i;
            }
            showFullscreenDialog(new ZeroChillDialog.Builder(activity)
                    .setTitle("Playback speed")
                    .setSingleChoiceItems(labels, checked, (dialog, index) -> {
                        if (landscapeFullscreen() && player == selected && ownsPlayback()) {
                            selected.setPlaybackSpeed(speeds[index]);
                            syncFullscreenPlayback();
                        }
                        dialog.dismiss();
                    }).create());
        }

        private void showFullscreenDialog(AlertDialog fullscreenDialog) {
            root.removeCallbacks(hideControlsRunnable);
            actionDialog = fullscreenDialog;
            actionDialog.setOnDismissListener(dialog -> {
                if (actionDialog == dialog) actionDialog = null;
                armFullscreenAutoHide();
            });
            actionDialog.show();
        }

        void applyClearDisplay(boolean clear) {
            if (clear) cancelInteractionUi();
            applyViewportInset();
            root.removeCallbacks(hideControlsRunnable);
            lower.animate().cancel();
            landscapeTopScrim.animate().cancel();
            landscapeBottomScrim.animate().cancel();
            landscapeHeader.animate().cancel();
            landscapeActions.animate().cancel();
            progressRow.animate().cancel();
            resetEntranceOffsets();
            progressChromeVisible = null;

            if (clear) {
                controlsVisible = false;
                lower.setAlpha(0f);
                creatorAvatarControl.setAlpha(0f);
                fullscreen.setAlpha(0f);
                landscapeTopScrim.setAlpha(0f);
                landscapeBottomScrim.setAlpha(0f);
                landscapeHeader.setAlpha(0f);
                landscapeActions.setAlpha(0f);
                progressRow.setAlpha(0f);
                lower.setVisibility(View.INVISIBLE);
                creatorAvatarControl.setVisibility(View.INVISIBLE);
                fullscreen.setVisibility(View.INVISIBLE);
                landscapeTopScrim.setVisibility(View.INVISIBLE);
                landscapeBottomScrim.setVisibility(View.INVISIBLE);
                landscapeHeader.setVisibility(View.INVISIBLE);
                landscapeActions.setVisibility(View.INVISIBLE);
                pausePlayOverlay.setVisibility(View.GONE);
                progressRow.setVisibility(View.INVISIBLE);
                progressChromeVisible = null;
                return;
            }

            controlsVisible = true;
            lower.setAlpha(1f);
            creatorAvatarControl.setAlpha(1f);
            fullscreen.setAlpha(1f);
            landscapeTopScrim.setAlpha(1f);
            landscapeBottomScrim.setAlpha(1f);
            landscapeHeader.setAlpha(1f);
            landscapeActions.setAlpha(1f);
            progressRow.setAlpha(1f);
            applyControlLayout();
            syncPausedChrome();
            if (!portrait() && player != null && player.isPlaying() && !scrubbing) {
                root.postDelayed(hideControlsRunnable, FullscreenPlayerStyle.HIDE_DELAY_MS);
            }
        }

        private void hideControlsNow() {
            if (landscapeFullscreen() && player != null && player.isPlaying()) hideFullscreenControls();
        }

        private void updateProgress() {
            if (player == null || scrubbing) return;
            long duration = player.getDuration();
            syncProgressChrome(duration);
            long position = Math.max(0L, player.getCurrentPosition());
            if (duration <= 0L) {
                seekBar.setEnabled(false);
                seekBar.setProgress(0);
                updateTimeLabels(0L, 0L);
                return;
            }
            seekBar.setEnabled(!landscapeFullscreen() || canSeekFullscreen());
            int progress = (int) Math.max(0L, Math.min(1000L, (position * 1000L) / duration));
            seekBar.setProgress(progress);
            updateTimeLabels(position, duration);
        }

        private void updateTimeLabels(long position, long duration) {
            long bounded = Math.max(0L, Math.min(position, duration));
            long second = bounded / 1000L, remainingSecond = duration > 0L ? duration / 1000L : -1L;
            if (displayedSecond == second && displayedRemainingSecond == remainingSecond) return;
            displayedSecond = second;
            displayedRemainingSecond = remainingSecond;
            elapsedTime.setText(formatPlaybackTime(bounded));
            remainingTime.setText(duration > 0L ? formatPlaybackTime(duration) : "–:––");
        }

        void showRetrying() {
            failurePending = false;
            maintenanceFailure = false;
            root.removeCallbacks(skipFailedClipRunnable);
            root.removeCallbacks(progressTick);
            syncLoading();
            failure.setText("Trying another link…");
            failure.setContentDescription("Playback failed. Trying another link.");
            failure.setVisibility(View.VISIBLE);
            poster.setVisibility(View.VISIBLE);
        }

        void showMaintenanceFailure() {
            maintenanceFailure = true;
            failurePending = true;
            syncLoading();
            failure.setText(activity.getString(R.string.bunkr_maintenance_title) + "\n"
                    + activity.getString(R.string.bunkr_maintenance_message));
            failure.setContentDescription(failure.getText());
            failure.setVisibility(View.VISIBLE);
            poster.setVisibility(View.VISIBLE);
        }

        void showResolutionFailureAndSkip(boolean retried) {
            retryAttempted = retryAttempted || retried;
            lastFailureStage = "Stream resolution failed";
            lastPlaybackError = null;
            showFinalFailure();
        }

        void showPlayerFailureAndSkip(PlaybackException error, String stage) {
            lastPlaybackError = error;
            lastFailureStage = stage == null ? "Player error" : stage;
            showFinalFailure();
        }

        private void showFinalFailure() {
            RatingFeedbackPrompt.recordPlaybackError(activity);
            syncLoading();
            failurePending = true;
            if (item != null) ShitTokSmartRanker.failed(item.url);
            syncLoading();
            if (item != null && boundPosition > selectedPosition) {
                unplayable.add(item.url);
                requestContinuity();
            }
            String failureMessage = fullscreenInputLocked() ? "Couldn't play this video"
                    : "Couldn't play this one\nMoving to the next video…";
            failure.setText(failureMessage);
            failure.setContentDescription(failureMessage);
            failure.setVisibility(View.VISIBLE);
            poster.setVisibility(View.VISIBLE);
            userPaused = false;
            hidePausedChrome();
            showControlsPersistent();
            root.removeCallbacks(skipFailedClipRunnable);
            root.removeCallbacks(progressTick);
            if (!fullscreenInputLocked()) root.postDelayed(skipFailedClipRunnable, FAILED_CLIP_SKIP_DELAY_MS);
        }

        void pauseAndRecord() {
            endScrubPreview(false, false);
            cancelInteractionUi();
            pinch.reset();
            ExoPlayer paused = player;
            if (item != null) ShitTokSmartRanker.playback(item.url, false);
            if (paused == null) return;
            // History reads/recording must never prevent the outgoing player from stopping.
            silencePlayer(paused);
            // Repeated ownership checks should not rescan history or restart chrome animations.
            if (playbackSuspended) return;
            playbackSuspended = true;
            if (item == null || isPaging()) return;
            hidePausedChrome();
            try {
                long position = Math.max(0L, player.getCurrentPosition());
                long duration = Math.max(0L, player.getDuration());
                if (everStarted || position > 1000L) {
                    PlaybackHistoryStore.record(
                            activity, item, position, duration, false, false);
                }
            } catch (Exception ignored) {
            }
        }

        void releasePlayer() {
            fullscreenGestures.cancel();
            root.removeCallbacks(hideControlsRunnable);
            root.removeCallbacks(skipFailedClipRunnable);
            root.removeCallbacks(progressTick);
            failurePending = false;
            detachLease();
        }

    }
}
