package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
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
 * Swipe manually at any time, or let a finished clip advance to the next video automatically.
 */
@UnstableApi
public final class ChaosFeedView extends FrameLayout {
    public interface Host {
        void openDetails(NativeContentItem item);

        default void onClearDisplayChanged(boolean clear) {
        }

        default void onVerticalPageChanged(int direction, boolean atTop, boolean userDriven) {
        }
    }

    private static final String PREFS = "chaos_feed";
    private static final String KEY_RECENT = "recent_urls";
    private static final String KEY_HIDDEN = "hidden_urls";
    private static final String KEY_MUTED = "muted";
    private static final int MAX_RECENT = 500;
    private static final int MAX_HIDDEN = 600;
    private static final int LOAD_AHEAD_AT = 5;
    private static final int MAX_QUEUED_AHEAD = 24;
    private static final int MAX_STREAM_CACHE = 32;
    private static final long RECENT_SAVE_DELAY_MS = 750L;
    private static final long STREAM_RETRY_DELAY_MS = 450L;
    private static final long FAILED_CLIP_SKIP_DELAY_MS = 1200L;
    private static final long SWIPE_PREPARE_IDLE_DELAY_MS = 60L;
    private static final long SWIPE_PREPARE_STAGGER_MS = 120L;
    private static final long SWIPE_RELEASE_IDLE_DELAY_MS = 360L;
    private static final long SWIPE_RELEASE_STAGGER_MS = 120L;
    private static final int SHITTOK_MAX_VIDEO_WIDTH = 1920;
    private static final int SHITTOK_MAX_VIDEO_HEIGHT = 1080;
    private static final int SHITTOK_MAX_VIDEO_BITRATE = 8_000_000;
    private static final int NEXT_PRELOAD_MIN_BUFFER_MS = 2_500;
    private static final int NEXT_PRELOAD_MAX_BUFFER_MS = 6_000;
    private static final String SITE = "https://crazyshit.com/";

    private final Activity activity;
    private final Host host;
    private final CrazyShitRepository repository = new CrazyShitRepository();
    private final ExecutorService io = Executors.newFixedThreadPool(6);
    private final ArrayList<NativeContentItem> items = new ArrayList<>();
    private final Set<String> sessionUrls = new HashSet<>();
    private final Deque<String> recentUrls = new ArrayDeque<>();
    private final Set<String> recentSet = new HashSet<>();
    private final LinkedHashSet<String> hiddenUrls = new LinkedHashSet<>();
    private final Map<String, CrazyShitRepository.StreamInfo> streamCache =
            new LinkedHashMap<String, CrazyShitRepository.StreamInfo>(MAX_STREAM_CACHE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<String, CrazyShitRepository.StreamInfo> eldest
                ) {
                    return size() > MAX_STREAM_CACHE;
                }
            };
    private final Set<String> resolving = new HashSet<>();
    private final Set<String> unplayable = new HashSet<>();
    private final Set<String> resolveRetried = new HashSet<>();
    private final Set<ChaosHolder> playerHolders =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Deque<ExoPlayer> deferredPlayerReleases = new ArrayDeque<>();
    private final Deque<Integer> pendingWarmPreparePositions = new ArrayDeque<>();
    private final Random random = new Random();
    private final ChaosSourceMixer sourceMixer = new ChaosSourceMixer(repository, random);
    private final ShitTokAspectPriority aspectPriority = new ShitTokAspectPriority();
    private final ShitTokSessionResume sessionResume = new ShitTokSessionResume();
    private final ShitTokRenderDiagnostics renderDiagnostics;

    private ViewPager2 pager;
    private ChaosAdapter adapter;
    private TextView empty;
    private ProgressBar initialProgress;
    private ShitTokCreatorSwipePreview creatorSwipePreview;
    private ImageView creatorSwipeSourceSnapshot;
    private boolean creatorGalleryHandoff;
    private InlineCommentsDialog commentsDialog;
    private boolean active;
    private boolean hostResumed = true;
    private boolean poolLoading;
    private List<NativeContentItem> pendingPoolFresh;
    private List<NativeContentItem> pendingPoolRecentFallback;
    private volatile boolean closed;
    private boolean autoAdvancePending;
    private boolean chaosMuted;
    private boolean manualFullscreen;
    private boolean clearDisplay;
    private int autoAdvanceFrom = -1;
    private int consecutiveDryLoads;
    private int selectedPosition;
    private boolean userPaging;
    private boolean userTouchingPager;
    private int creatorWarmAheadPosition = -1;
    private int maintenancePosition = -1;
    private boolean maintenanceResolveIssued;
    private final Runnable saveRecentRunnable = this::saveRecentNow;
    private final Runnable creatorWarmAheadRunnable = this::warmNextCreatorGallery;
    private final Runnable playerPrepareMaintenanceRunnable = this::runDeferredPrepareMaintenance;
    private final Runnable playerReleaseMaintenanceRunnable = this::runDeferredReleaseMaintenance;

    public ChaosFeedView(Activity activity, Host host) {
        super(activity);
        this.activity = activity;
        this.host = host;
        this.renderDiagnostics = new ShitTokRenderDiagnostics(activity);
        setBackgroundColor(Color.BLACK);
        loadRecent();
        loadHidden();
        chaosMuted = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_MUTED, false);
        buildUi();
        loadMorePool();
    }

    private void buildUi() {
        pager = new ViewPager2(activity);
        pager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        pager.setOffscreenPageLimit(3);
        adapter = new ChaosAdapter();
        pager.setAdapter(adapter);
        addView(pager, new FrameLayout.LayoutParams(-1, -1));

        RecyclerView rv = pagerRecycler();
        if (rv != null) {
            rv.setItemViewCacheSize(3);
            rv.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
                @Override
                public boolean onInterceptTouchEvent(
                        @NonNull RecyclerView recyclerView,
                        @NonNull MotionEvent event
                ) {
                    renderDiagnostics.onPagerTouch(
                            event, pager.getScrollState(), selectedPosition);
                    int action = event.getActionMasked();
                    if (action == MotionEvent.ACTION_DOWN) {
                        userTouchingPager = true;
                    } else if (action == MotionEvent.ACTION_UP
                            || action == MotionEvent.ACTION_CANCEL) {
                        userTouchingPager = false;
                        if (pendingPoolFresh != null) runIdleFeedWorkIfSafe();
                    }
                    return false;
                }
            });
        }

        initialProgress = new ProgressBar(activity);
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
            @Override
            public void onPageScrollStateChanged(int state) {
                renderDiagnostics.onPagerState(state, selectedPosition);
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    userPaging = true;
                    cancelSwipePlayerMaintenance();
                } else if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    userPaging = false;
                    runIdleFeedWorkIfSafe();
                }
            }

            @Override
            public void onPageSelected(int position) {
                long pageCallbackStartedNs = renderDiagnostics.nowNs();
                renderDiagnostics.event(
                        "PAGE_SELECTED", position,
                        "from=" + selectedPosition + " items=" + items.size());
                cancelSwipePlayerMaintenance();
                int previousPosition = selectedPosition;
                boolean changed = position != previousPosition;
                if (changed) maintenancePosition = position;

                resetCreatorSwipePreview();
                if (autoAdvancePending && position != autoAdvanceFrom) {
                    autoAdvancePending = false;
                    autoAdvanceFrom = -1;
                }
                if (manualFullscreen && position != selectedPosition) exitManualFullscreen();
                selectedPosition = position;
                if (changed) {
                    sessionResume.clear();
                    host.onVerticalPageChanged(
                            position > previousPosition ? 1 : -1,
                            position == 0,
                            active && userPaging
                    );
                }
                markSeen(position);
                pauseNonSelected(position);
                playSelected();
                if (pager.getScrollState() == ViewPager2.SCROLL_STATE_IDLE
                        && !userTouchingPager) {
                    warmCreatorGalleries(position);
                } else {
                    cancelCreatorWarmAhead();
                }
                ChaosHolder holder = holderAt(position);
                if (holder != null) holder.showControlsTemporarily();
                if (items.size() - position <= LOAD_AHEAD_AT) loadMorePool();
                if (changed && pager.getScrollState() == ViewPager2.SCROLL_STATE_IDLE) {
                    scheduleSwipePlayerMaintenance(position);
                }
                renderDiagnostics.duration(
                        "PAGE_CALLBACK",
                        pageCallbackStartedNs,
                        position,
                        "changed=" + changed);
            }
        });
    }

    public void setActive(boolean value) {
        active = value;
        if (active && hostResumed) {
            pager.setUserInputEnabled(true);
            applyPendingPoolIfIdle();
            resolveAhead(selectedPosition);
            playSelected();
            warmCreatorGalleries(selectedPosition);
            syncVisibleChrome();
        } else {
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
        hostResumed = true;
        if (creatorGalleryHandoff) {
            creatorGalleryHandoff = false;
            resetCreatorSwipePreview();
        }
        if (active) {
            applyPendingPoolIfIdle();
            resolveAhead(selectedPosition);
            playSelected();
            syncVisibleChrome();
        }
    }

    public void onHostPause() {
        hostResumed = false;
        cancelSwipePlayerMaintenance();
        cancelCreatorWarmAhead();
        if (!creatorGalleryHandoff) resetCreatorSwipePreview();
        pauseAll();
        rememberSelectedPosition();
        if (!creatorGalleryHandoff) releaseAllPlayers();
        flushRecent();
    }

    public void onConfigurationChanged() {
        resetCreatorSwipePreview();
        if (commentsDialog != null && commentsDialog.isShowing()) commentsDialog.dismiss();
        syncVisibleChrome();
    }

    public void refresh() {
    cancelSwipePlayerMaintenance();
    cancelCreatorWarmAhead();
    resetCreatorSwipePreview();
    pauseAll();
    sessionResume.clear();
    consecutiveDryLoads = 0;
    sourceMixer.resetDeck();
    poolLoading = false;
    pendingPoolFresh = null;
    pendingPoolRecentFallback = null;
    autoAdvancePending = false;
    autoAdvanceFrom = -1;
    streamCache.clear();
    resolving.clear();
    unplayable.clear();
    resolveRetried.clear();
    // Keep sessionUrls so Refresh cannot immediately deal the same clips back again.
    items.clear();
    exitManualFullscreen();
    adapter.notifyDataSetChanged();
    initialProgress.setVisibility(View.VISIBLE);
    empty.setVisibility(View.GONE);
    selectedPosition = 0;
    pager.setCurrentItem(0, false);
    loadMorePool();
}

    public void close() {
        resetCreatorSwipePreview();
        closed = true;
        renderDiagnostics.close();
        poolLoading = false;
        pendingPoolFresh = null;
        pendingPoolRecentFallback = null;
        active = false;
        hostResumed = false;
        exitManualFullscreen();
        if (commentsDialog != null && commentsDialog.isShowing()) commentsDialog.dismiss();
        cancelSwipePlayerMaintenance();
        cancelCreatorWarmAhead();
        pauseAll();
        sessionResume.clear();
        releaseAllPlayers();
        flushRecent();
        removeCallbacks(saveRecentRunnable);
        if (creatorSwipePreview != null && creatorSwipePreview.getParent() instanceof ViewGroup) {
            ((ViewGroup) creatorSwipePreview.getParent()).removeView(creatorSwipePreview);
        }
        io.shutdownNow();
    }

    private void loadMorePool() {
    if (closed || poolLoading) return;
    poolLoading = true;

    io.execute(() -> {
        List<NativeContentItem> mixed;
        try {
            mixed = sourceMixer.loadRandomBatch(activity);
        } catch (Exception ignored) {
            mixed = Collections.emptyList();
        }

        ArrayList<NativeContentItem> fresh = new ArrayList<>();
        ArrayList<NativeContentItem> recentFallback = new ArrayList<>();
        for (NativeContentItem item : mixed) {
            if (!isMedia(item) || hiddenUrls.contains(item.url)) continue;
            if (recentSet.contains(item.url)) recentFallback.add(item);
            else fresh.add(item);
        }
        List<NativeContentItem> prioritizedFresh = aspectPriority.order(fresh, random);
        List<NativeContentItem> prioritizedRecentFallback =
                aspectPriority.order(recentFallback, random);

        activity.runOnUiThread(() ->
                stageOrApplyLoadedPool(prioritizedFresh, prioritizedRecentFallback));
    });
}

    static boolean shouldApplyLoadedPool(
            boolean closed,
            boolean userPaging,
            boolean userTouchingPager,
            int scrollState
    ) {
        return !closed
                && !userPaging
                && !userTouchingPager
                && scrollState == ViewPager2.SCROLL_STATE_IDLE;
    }

    private void stageOrApplyLoadedPool(
            List<NativeContentItem> prioritizedFresh,
            List<NativeContentItem> prioritizedRecentFallback
    ) {
        if (closed) return;
        if (!shouldApplyLoadedPool(
                closed, userPaging, userTouchingPager, pager.getScrollState())) {
            pendingPoolFresh = prioritizedFresh;
            pendingPoolRecentFallback = prioritizedRecentFallback;
            return;
        }
        applyLoadedPool(prioritizedFresh, prioritizedRecentFallback);
        warmCreatorGalleries(selectedPosition);
    }

    private void runIdleFeedWorkIfSafe() {
        if (!shouldApplyLoadedPool(
                closed, userPaging, userTouchingPager, pager.getScrollState())) {
            return;
        }
        applyPendingPoolIfIdle();
        warmCreatorGalleries(selectedPosition);
        scheduleSwipePlayerMaintenance(selectedPosition);
    }

    private void applyPendingPoolIfIdle() {
        if (pendingPoolFresh == null
                || !shouldApplyLoadedPool(
                        closed, userPaging, userTouchingPager, pager.getScrollState())) {
            return;
        }
        List<NativeContentItem> fresh = pendingPoolFresh;
        List<NativeContentItem> fallback = pendingPoolRecentFallback == null
                ? Collections.emptyList()
                : pendingPoolRecentFallback;
        pendingPoolFresh = null;
        pendingPoolRecentFallback = null;
        applyLoadedPool(fresh, fallback);
    }

    private void applyLoadedPool(
            List<NativeContentItem> prioritizedFresh,
            List<NativeContentItem> prioritizedRecentFallback
    ) {
        if (closed) return;
        poolLoading = false;
        int before = items.size();
        appendUnique(prioritizedFresh);

        int freshAdded = items.size() - before;
        if (freshAdded == 0) consecutiveDryLoads++;
        else consecutiveDryLoads = 0;

        // Previously watched clips stay out of the normal draw. Only recycle them if
        // several broad random batches in a row genuinely cannot produce fresh media.
        if (freshAdded < 4 && consecutiveDryLoads >= 3) {
            appendUnique(prioritizedRecentFallback);
        }

        int added = items.size() - before;
        if (added > 0 && freshAdded == 0) consecutiveDryLoads = 0;

        if (added > 0) {
            adapter.notifyItemRangeInserted(before, added);
            initialProgress.setVisibility(View.GONE);
            empty.setVisibility(View.GONE);
            resolveAhead(selectedPosition);
            if (active && hostResumed) playSelected();
            tryPendingAutoAdvance();
        }

        boolean needsMore = items.size() < 14
                || (autoAdvancePending && autoAdvanceFrom + 1 >= items.size());
        if (needsMore && consecutiveDryLoads < 4) {
            loadMorePool();
        } else if (items.isEmpty()) {
            initialProgress.setVisibility(View.GONE);
            empty.setText("ShitTok couldn't find a playable pool right now.\nPull away and come back to retry.");
            empty.setVisibility(View.VISIBLE);
        } else if (autoAdvancePending && autoAdvanceFrom + 1 >= items.size()) {
            autoAdvancePending = false;
            autoAdvanceFrom = -1;
        }
    }

    static boolean shouldPreparePlayer(int position, int selectedPosition) {
        return position >= selectedPosition && position <= selectedPosition + 2;
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
            Runnable openGallery
    ) {
        if (openGallery == null) return;
        View content = creatorSwipeSourceView();
        if (content == null || creatorSwipePreview == null) {
            openGallery.run();
            return;
        }
        float width = Math.max(1f, content.getWidth());
        creatorSwipePreview.refreshCreator(
                creator,
                ShitTokCreatorGalleryPreloader.sessionId(activity, creator)
        );
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
            if (!acceptUnique(item, hiddenUrls, sessionUrls)) continue;
            items.add(item);
            CrazyShitRepository.StreamInfo preloaded = ChaosStartupPreloader.takeResolved(item.url);
            if (preloaded != null) streamCache.put(item.url, preloaded);
        }
    }

    static boolean acceptUnique(NativeContentItem item, Set<String> hidden,
                                Set<String> session) {
        return isMedia(item) && !hidden.contains(item.url) && session.add(item.url);
    }

    private void requestAutoAdvance(int fromPosition) {
        if (!active || !hostResumed || fromPosition != selectedPosition) return;

        if (fromPosition + 1 < items.size()) {
            autoAdvancePending = false;
            autoAdvanceFrom = -1;
            if (items.size() - fromPosition <= LOAD_AHEAD_AT) loadMorePool();
            pager.post(() -> {
                if (!active || !hostResumed || selectedPosition != fromPosition) return;
                if (fromPosition + 1 >= items.size()) return;
                pager.setCurrentItem(fromPosition + 1, true);
            });
            return;
        }

        autoAdvancePending = true;
        autoAdvanceFrom = fromPosition;
        loadMorePool();
    }

    private void tryPendingAutoAdvance() {
        if (!autoAdvancePending) return;
        int fromPosition = autoAdvanceFrom;
        if (!active || !hostResumed || selectedPosition != fromPosition) {
            autoAdvancePending = false;
            autoAdvanceFrom = -1;
            return;
        }
        if (fromPosition + 1 < items.size()) requestAutoAdvance(fromPosition);
    }

    private void warmCreatorGalleries(int position) {
        if (closed || !active || !hostResumed || position < 0 || position >= items.size()) return;
        ShitTokCreatorGalleryPreloader.warm(activity, items.get(position));

        cancelCreatorWarmAhead();
        creatorWarmAheadPosition = position;
        postDelayed(creatorWarmAheadRunnable, 700L);
    }

    private void cancelCreatorWarmAhead() {
        removeCallbacks(creatorWarmAheadRunnable);
        creatorWarmAheadPosition = -1;
    }

    private void warmNextCreatorGallery() {
        int position = creatorWarmAheadPosition;
        creatorWarmAheadPosition = -1;
        if (closed || !active || !hostResumed || selectedPosition != position) return;
        for (int next = position + 1; next < Math.min(items.size(), position + 8); next++) {
            NativeContentItem candidate = items.get(next);
            if (!ShitTokCreatorMetadata.hasCreator(candidate)) continue;
            ShitTokCreatorGalleryPreloader.warm(activity, candidate);
            break;
        }
    }

    private void resolveAhead(int position) {
        if (!hostResumed) return;
        int ahead = ChaosPreloadPolicy.aheadCount(activity);
        if (!active) ahead = Math.min(ahead, 2);
        for (int offset = 0; offset <= ahead; offset++) {
            resolveAt(position + offset);
        }
        if (ahead > 0 && position > 0) resolveAt(position - 1);
    }

    private boolean shouldResolvePosition(int position) {
        if (position == selectedPosition) return true;
        if (!ChaosPreloadPolicy.allowsLookAhead(activity)) return false;
        return position >= selectedPosition - 1 && position <= selectedPosition + 3;
    }

    private void resolveAt(int position) {
        if (closed) return;
        if (position < 0 || position >= items.size()) return;
        NativeContentItem item = items.get(position);
        if (streamCache.containsKey(item.url)) {
            prepareVisible(position);
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

        io.execute(() -> {
            CrazyShitRepository.StreamInfo stream = ChaosStartupPreloader.takeResolved(item.url);
            try {
                if (stream == null) stream = resolvePlayable(item);
            } catch (Exception ignored) {
            }
            CrazyShitRepository.StreamInfo resolved = stream;
            activity.runOnUiThread(() -> {
                if (closed) return;
                resolving.remove(item.url);
                if (resolved == null || resolved.mediaUrl == null || resolved.mediaUrl.isEmpty()) {
                    if (shouldRetryResolution(item, position)) {
                        scheduleResolutionRetry(item, position);
                        return;
                    }
                    unplayable.add(item.url);
                } else {
                    unplayable.remove(item.url);
                    streamCache.put(item.url, resolved);
                }
                prepareVisible(position);
                if (position == selectedPosition) playSelected();
            });
        });
    }

    private CrazyShitRepository.StreamInfo resolvePlayable(NativeContentItem item)
            throws Exception {
        if (item == null || item.url == null || item.url.isEmpty()) return null;
        return PlayableSourceRouter.resolve(activity, item);
    }

    private boolean shouldRetryResolution(NativeContentItem item, int position) {
        if (item == null || item.url == null || item.url.isEmpty()) return false;
        if (!active || !hostResumed || position != selectedPosition) return false;
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
        if (!active || !hostResumed) return;
        if (userPaging && position != selectedPosition) return;
        ChaosHolder holder = holderAt(position);
        if (holder == null || position < 0 || position >= items.size()) return;
        NativeContentItem item = items.get(position);
        CrazyShitRepository.StreamInfo stream = streamCache.get(item.url);
        if (stream != null) {
            holder.noteResolutionRetryIfNeeded(resolveRetried.contains(item.url));
            if (position == selectedPosition) {
                holder.prepare(stream, true);
            } else if (shouldPreparePlayer(position, selectedPosition)
                    && ChaosPreloadPolicy.allowsLookAhead(activity)) {
                queueWarmPrepare(position);
            } else {
                holder.detachPlayerForDeferredRelease();
            }
        } else if (unplayable.contains(item.url)) {
            holder.showResolutionFailureAndSkip(resolveRetried.contains(item.url));
        }
    }

    private void playSelected() {
        if (!active || !hostResumed) return;
        if (selectedPosition < 0 || selectedPosition >= items.size()) return;
        resolveAt(selectedPosition);
        ChaosHolder holder = holderAt(selectedPosition);
        if (holder == null) return;
        NativeContentItem item = items.get(selectedPosition);
        CrazyShitRepository.StreamInfo stream = streamCache.get(item.url);
        if (stream != null) holder.prepare(stream, true);
    }

    private void pauseNonSelected(int selected) {
        RecyclerView rv = pagerRecycler();
        if (rv == null) return;
        for (int i = 0; i < rv.getChildCount(); i++) {
            RecyclerView.ViewHolder raw = rv.getChildViewHolder(rv.getChildAt(i));
            if (!(raw instanceof ChaosHolder)) continue;
            ChaosHolder holder = (ChaosHolder) raw;
            if (holder.getBindingAdapterPosition() != selected) holder.pauseAndRecord();
        }
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
        postDelayed(playerReleaseMaintenanceRunnable, SWIPE_RELEASE_IDLE_DELAY_MS);
    }

    private void cancelSwipePlayerMaintenance() {
        removeCallbacks(playerPrepareMaintenanceRunnable);
        removeCallbacks(playerReleaseMaintenanceRunnable);
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
                position > selectedPosition && shouldPreparePlayer(position, selectedPosition);
    }

    private void queueWarmPrepare(int position) {
        if (!shouldQueueWarmPlayer(
                active,
                hostResumed,
                userPaging,
                ChaosPreloadPolicy.allowsLookAhead(activity),
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
            resolveAhead(position);
        }

        Integer target = null;
        while (!pendingWarmPreparePositions.isEmpty()) {
            int candidate = pendingWarmPreparePositions.removeFirst();
            if (candidate > selectedPosition &&
                    shouldPreparePlayer(candidate, selectedPosition)) {
                target = candidate;
                break;
            }
        }
        if (target == null) return;

        ChaosHolder holder = holderAt(target);
        if (holder != null && target < items.size()) {
            NativeContentItem item = items.get(target);
            CrazyShitRepository.StreamInfo stream = streamCache.get(item.url);
            if (stream != null && holder.player == null) {
                holder.prepare(stream, false);
            }
        }

        if (!pendingWarmPreparePositions.isEmpty() &&
                shouldRunSwipeMaintenance(
                        active, hostResumed, userPaging, position, selectedPosition)) {
            postDelayed(playerPrepareMaintenanceRunnable, SWIPE_PREPARE_STAGGER_MS);
        }
    }

    private void runDeferredReleaseMaintenance() {
        int position = maintenancePosition;
        if (!shouldRunSwipeMaintenance(
                active, hostResumed, userPaging, position, selectedPosition)) {
            return;
        }

        ExoPlayer detached = deferredPlayerReleases.pollFirst();
        if (detached != null) {
            try {
                detached.release();
            } catch (Exception ignored) {
            }
        } else {
            detachOneDistantPlayer(position);
        }

        if ((!deferredPlayerReleases.isEmpty() || hasDistantPlayer(position)) &&
                shouldRunSwipeMaintenance(
                        active, hostResumed, userPaging, position, selectedPosition)) {
            postDelayed(playerReleaseMaintenanceRunnable, SWIPE_RELEASE_STAGGER_MS);
        }
    }

    private boolean hasDistantPlayer(int selected) {
        for (ChaosHolder holder : playerHolders) {
            int position = holder.boundPosition;
            if (position < 0 || !shouldPreparePlayer(position, selected)) return true;
        }
        return false;
    }

    private boolean detachOneDistantPlayer(int selected) {
        // Detach a stale holder first. The expensive ExoPlayer.release() happens on a later
        // idle slice so RecyclerView recycling and decoder teardown do not stack on one frame.
        for (ChaosHolder holder : new ArrayList<>(playerHolders)) {
            int position = holder.boundPosition;
            if (position < 0 || !shouldPreparePlayer(position, selected)) {
                holder.detachPlayerForDeferredRelease();
                return true;
            }
        }
        return false;
    }

    private void enqueueDeferredPlayerRelease(ExoPlayer player) {
        if (player == null) return;
        deferredPlayerReleases.addLast(player);
        if (active && hostResumed && !userPaging &&
                pager.getScrollState() == ViewPager2.SCROLL_STATE_IDLE) {
            maintenancePosition = selectedPosition;
            removeCallbacks(playerReleaseMaintenanceRunnable);
            postDelayed(playerReleaseMaintenanceRunnable, SWIPE_RELEASE_IDLE_DELAY_MS);
        }
    }

    private void drainDeferredPlayerReleasesNow() {
        removeCallbacks(playerReleaseMaintenanceRunnable);
        while (!deferredPlayerReleases.isEmpty()) {
            ExoPlayer player = deferredPlayerReleases.removeFirst();
            try {
                player.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void pauseAll() {
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
                    holder.player == null || selectedPosition >= items.size() ||
                    !holder.item.url.equals(items.get(selectedPosition).url)) continue;
            try {
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
        drainDeferredPlayerReleasesNow();
    }

    private void syncVisibleChrome() {
        RecyclerView rv = pagerRecycler();
        if (rv == null) return;
        for (int i = 0; i < rv.getChildCount(); i++) {
            RecyclerView.ViewHolder raw = rv.getChildViewHolder(rv.getChildAt(i));
            if (raw instanceof ChaosHolder) ((ChaosHolder) raw).syncOrientationChrome();
        }
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
        RecyclerView rv = pagerRecycler();
        if (rv == null) return null;
        RecyclerView.ViewHolder raw = rv.findViewHolderForAdapterPosition(position);
        return raw instanceof ChaosHolder ? (ChaosHolder) raw : null;
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
        JSONArray array = new JSONArray();
        for (String url : recentUrls) array.put(url);
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RECENT, array.toString())
                .apply();
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

    private void hideFromChaos(NativeContentItem item) {
        if (item == null || item.url == null || item.url.isEmpty()) return;
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
        adapter.notifyDataSetChanged();
        Toast.makeText(activity, "Won't show this clip again.", Toast.LENGTH_SHORT).show();

        if (items.isEmpty()) {
            exitManualFullscreen();
            selectedPosition = 0;
            initialProgress.setVisibility(View.VISIBLE);
            loadMorePool();
            return;
        }

        int target = Math.min(index, items.size() - 1);
        selectedPosition = target;
        pager.setCurrentItem(target, false);
        markSeen(target);
        resolveAhead(target);
        playSelected();
    }

    static boolean shouldOfferLandscapeFullscreen(float aspectRatio) {
        return aspectRatio > 1.1f;
    }

    private void enterManualFullscreen(ChaosHolder holder) {
        if (manualFullscreen || holder == null || !holder.horizontalVideo) return;
        manualFullscreen = true;
        PhoneOrientationPolicy.enterSensorFullscreen(activity);
        holder.syncOrientationChrome();
    }

    private void exitManualFullscreen() {
        if (!manualFullscreen) return;
        manualFullscreen = false;
        PhoneOrientationPolicy.exitFullscreenVideo(activity);
        syncVisibleChrome();
    }

    boolean exitSensorFullscreenForBack() {
        if (clearDisplay) {
            setClearDisplay(false);
            return true;
        }
        if (!manualFullscreen) return false;
        exitManualFullscreen();
        return true;
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

    private void setChaosMuted(boolean muted) {
        chaosMuted = muted;
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
        if (item == null || item.url.isEmpty()) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, item.url);
        share.putExtra(Intent.EXTRA_SUBJECT, item.title);
        activity.startActivity(Intent.createChooser(share, "Share"));
    }

    private void retryPlayback(ChaosHolder holder, PlaybackException originalError) {
        if (closed) return;
        if (holder == null || holder.item == null || holder.item.url.isEmpty()) return;
        NativeContentItem retryItem = holder.item;
        String pageUrl = retryItem.url;
        int position = holder.boundPosition;

        holder.showRetrying();
        holder.releasePlayer();
        streamCache.remove(pageUrl);
        unplayable.remove(pageUrl);
        resolveRetried.add(pageUrl);
        resolving.add(pageUrl);

        io.execute(() -> {
            CrazyShitRepository.StreamInfo refreshed = null;
            try {
                refreshed = resolvePlayable(retryItem);
            } catch (Exception ignored) {
            }
            CrazyShitRepository.StreamInfo resolved = refreshed;
            activity.runOnUiThread(() -> {
                if (closed) return;
                resolving.remove(pageUrl);
                boolean valid = resolved != null
                        && resolved.mediaUrl != null
                        && !resolved.mediaUrl.isEmpty();
                if (valid) {
                    unplayable.remove(pageUrl);
                    streamCache.put(pageUrl, resolved);
                } else {
                    unplayable.add(pageUrl);
                }

                if (!holder.isBoundTo(pageUrl, position)) {
                    prepareVisible(position);
                    return;
                }
                if (!valid) {
                    holder.showPlayerFailureAndSkip(originalError, "Fresh stream lookup failed");
                    return;
                }
                holder.prepare(resolved, active && hostResumed && position == selectedPosition);
            });
        });
    }

    private void showPlaybackReport(ChaosHolder holder) {
        if (holder == null || holder.item == null) return;
        String report = ChaosPlaybackDiagnostics.build(
                holder.item,
                holder.diagnosticStream(),
                holder.lastPlaybackError,
                holder.lastFailureStage,
                holder.retryAttempted
        );
        new AlertDialog.Builder(activity)
                .setTitle("Playback report")
                .setMessage(report)
                .setPositiveButton("Copy", (dialog, which) -> copyPlaybackReport(report))
                .setNeutralButton("Share", (dialog, which) -> sharePlaybackReport(report))
                .setNegativeButton("Close", null)
                .show();
    }

    private void copyPlaybackReport(String report) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(
                Context.CLIPBOARD_SERVICE
        );
        if (clipboard == null) {
            Toast.makeText(activity, "Clipboard isn't available.", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("CrazyShit playback report", report));
        Toast.makeText(activity, "Playback report copied.", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(activity, "Removed from Watch Later.", Toast.LENGTH_SHORT).show();
        } else {
            FavoriteStore.add(activity, item.title, item.url);
            Toast.makeText(activity, "Saved to Watch Later.", Toast.LENGTH_SHORT).show();
        }
        updateSaveButton(item, button);
    }

    private void updateSaveButton(NativeContentItem item, TextView button) {
        if (button == null || item == null) return;
        boolean saved = FavoriteStore.contains(activity, item.url);
        button.setCompoundDrawablesWithIntrinsicBounds(
                0,
                saved ? R.drawable.ic_nav_saved : R.drawable.ic_action_save_outline,
                0,
                0
        );
        button.setCompoundDrawableTintList(ColorStateList.valueOf(saved ? UiPalette.PRIMARY : Color.WHITE));
        button.setContentDescription(saved ? "Remove from Watch Later" : "Save to Watch Later");
    }

    private void openInlineComments(NativeContentItem item) {
        if (item == null || item.url == null || item.url.isEmpty()) return;
        if (!supportsComments(item)) {
            Toast.makeText(
                    activity,
                    "Comments are not available for this source in ShitTok.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        if (commentsDialog != null && commentsDialog.isShowing()) return;

        pager.animate().cancel();
        pager.setScaleX(1f);
        pager.setScaleY(1f);
        pager.setUserInputEnabled(false);
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
                        restoreAfterComments();
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
            pager.setUserInputEnabled(true);
            return;
        }
        holder.restoreMediaAfterComments(() -> {
            pager.setUserInputEnabled(true);
            holder.showControlsTemporarily();
        });
    }

    private void preloadReadyComments(NativeContentItem item, int position) {
        if (item == null || !supportsComments(item)) return;
        if (position != selectedPosition || !active || !hostResumed) return;
        if (!ChaosPreloadPolicy.allowsCommentPreload(activity)) return;
        String url = item.url;
        pager.postDelayed(() -> {
            if (!active || !hostResumed || position != selectedPosition) return;
            if (!ChaosPreloadPolicy.allowsCommentPreload(activity)) return;
            if (selectedPosition < 0 || selectedPosition >= items.size()) return;
            NativeContentItem selected = items.get(selectedPosition);
            if (selected == null || !url.equals(selected.url)) return;
            NativeCommentsLoader.preload(activity, url);
        }, 850L);
    }

    private boolean supportsComments(NativeContentItem item) {
        return item != null
                && !EfuktRepository.isEfuktUrl(item.url)
                && !BunkrRepository.isBunkrUrl(item.url)
                && !FapelloRepository.isFapelloUrl(item.url)
                && !WebVideoSourceRepository.isKaoticUrl(item.url)
                && !OnlyHavenRepository.isOnlyHavenUrl(item.url);
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
            holder.bind(items.get(position), position);
            CrazyShitRepository.StreamInfo stream = streamCache.get(items.get(position).url);
            if (stream != null && active && hostResumed && position == selectedPosition) {
                holder.prepare(stream, true);
            } else if (stream != null && active && hostResumed &&
                    shouldPreparePlayer(position, selectedPosition)) {
                queueWarmPrepare(position);
            } else if (shouldResolvePosition(position)) {
                resolveAt(position);
            }
        }

        @Override
        public void onViewRecycled(@NonNull ChaosHolder holder) {
            holder.pauseAndRecord();
            holder.detachPlayerForDeferredRelease();
            super.onViewRecycled(holder);
        }

        @Override
        public void onViewDetachedFromWindow(@NonNull ChaosHolder holder) {
            holder.pauseAndRecord();
            int position = holder.getBindingAdapterPosition();
            if (!active || position == RecyclerView.NO_POSITION ||
                    Math.abs(position - selectedPosition) > 1) {
                holder.detachPlayerForDeferredRelease();
            }
            super.onViewDetachedFromWindow(holder);
        }
    }

    private final class ChaosHolder extends RecyclerView.ViewHolder {
        final FrameLayout root;
        final FrameLayout mediaLayer;
        final PlayerView playerView;
        final ImageView poster;
        final ProgressBar loading;
        final TextView failure;
        final LinearLayout lower;
        final LinearLayout actionRail;
        final FrameLayout creatorAvatarControl;
        final ImageView creatorAvatar;
        final TextView creatorFavoriteBadge;
        final LinearLayout playbackRail;
        final TextView title;
        final TextView meta;
        final TextView save;
        final TextView comments;
        final TextView mute;
        final ImageView fullscreen;
        final ImageView pausePlayOverlay;
        final SeekBar seekBar;
        ExoPlayer player;
        NativeContentItem item;
        NativeContentItem creatorIdentity;
        CrazyShitRepository.StreamInfo stream;
        CrazyShitRepository.StreamInfo lastAttemptedStream;
        PlaybackException lastPlaybackError;
        String lastFailureStage = "";
        int boundPosition = -1;
        boolean controlsVisible = true;
        boolean userPaused;
        boolean scrubbing;
        boolean everStarted;
        boolean retryAttempted;
        boolean failurePending;
        boolean horizontalVideo;
        boolean aspectSampleRecorded;
        float videoAspectRatio;
        float creatorSwipeDownX;
        float creatorSwipeDownY;
        boolean creatorSwipeTracking;
        String creatorSwipeTransitionToken = "";

        private final Runnable hideControlsRunnable = this::hideControlsNow;
        private final Runnable skipFailedClipRunnable = () -> {
            if (!failurePending || boundPosition != selectedPosition) return;
            requestAutoAdvance(boundPosition);
        };
        ChaosHolder(ViewGroup parent) {
            super(new FrameLayout(parent.getContext()));
            root = (FrameLayout) itemView;
            root.setLayoutParams(new RecyclerView.LayoutParams(-1, -1));
            root.setBackgroundColor(Color.BLACK);
            applyViewportInset();

            mediaLayer = new FrameLayout(activity);
            mediaLayer.setBackgroundColor(Color.BLACK);
            root.addView(mediaLayer, new FrameLayout.LayoutParams(-1, -1));

            poster = new ImageView(activity);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            poster.setBackgroundColor(Color.BLACK);
            poster.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            mediaLayer.addView(poster, new FrameLayout.LayoutParams(-1, -1));

            playerView = (PlayerView) LayoutInflater.from(activity)
                    .inflate(R.layout.view_video_player_texture, mediaLayer, false);
            playerView.setUseController(false);
            playerView.setControllerAutoShow(false);
            playerView.hideController();
            playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            playerView.setBackgroundColor(Color.BLACK);
            playerView.setContentDescription("Play or pause video");
            mediaLayer.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

            loading = new ProgressBar(activity);
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

            title = new TextView(activity);
            title.setTextColor(Color.WHITE);
            title.setTextSize(17);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setMaxLines(3);
            copy.addView(title, new LinearLayout.LayoutParams(-1, -2));

            meta = new TextView(activity);
            meta.setTextColor(Color.rgb(215, 215, 222));
            meta.setTextSize(12);
            meta.setPadding(0, dp(5), 0, 0);
            copy.addView(meta, new LinearLayout.LayoutParams(-1, -2));

            actionRail = new LinearLayout(activity);
            actionRail.setOrientation(LinearLayout.VERTICAL);
            actionRail.setGravity(Gravity.CENTER);
            actionRail.setPadding(dp(4), dp(5), dp(4), dp(5));
            actionRail.setBackground(activity.getDrawable(R.drawable.zc_shittok_control_rail));
            actionRail.setTag("shittok_action_rail");
            lower.addView(actionRail, new LinearLayout.LayoutParams(dp(58), -2));

            creatorAvatarControl = new FrameLayout(activity);
            creatorAvatarControl.setTag("shittok_creator_avatar");
            creatorAvatarControl.setVisibility(View.GONE);
            creatorAvatarControl.setClickable(true);
            creatorAvatarControl.setFocusable(true);

            FrameLayout avatarRing = new FrameLayout(activity);
            avatarRing.setBackground(circleDrawable(Color.BLACK, UiPalette.PRIMARY, 2));
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
            creatorFavoriteBadge.setTextColor(Color.WHITE);
            creatorFavoriteBadge.setTextSize(16);
            creatorFavoriteBadge.setTypeface(null, android.graphics.Typeface.BOLD);
            creatorFavoriteBadge.setGravity(Gravity.CENTER);
            creatorFavoriteBadge.setBackground(circleDrawable(UiPalette.PRIMARY, Color.BLACK, 2));
            creatorFavoriteBadge.setTag("shittok_creator_favorite");
            creatorFavoriteBadge.setClickable(true);
            creatorFavoriteBadge.setFocusable(true);
            FrameLayout.LayoutParams badgeParams =
                    new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            creatorAvatarControl.addView(creatorFavoriteBadge, badgeParams);

            LinearLayout.LayoutParams creatorParams =
                    new LinearLayout.LayoutParams(dp(52), dp(60));
            creatorParams.setMargins(0, 0, 0, dp(2));
            actionRail.addView(creatorAvatarControl, creatorParams);

            save = textIconActionButton(
                    R.drawable.ic_action_save_outline,
                    "Save to Watch Later",
                    "shittok_save"
            );
            actionRail.addView(save, actionParams());

            comments = textIconActionButton(
                    R.drawable.ic_action_comments,
                    "Open comments",
                    "shittok_comments"
            );
            actionRail.addView(comments, actionParams());

            TextView share = textIconActionButton(
                    R.drawable.ic_action_share,
                    "Share video",
                    "shittok_share"
            );
            actionRail.addView(share, actionParams());

            TextView more = textIconActionButton(
                    R.drawable.ic_more_overflow,
                    "More video actions",
                    "shittok_more"
            );
            actionRail.addView(more, actionParams());

            playbackRail = new LinearLayout(activity);
            playbackRail.setOrientation(LinearLayout.VERTICAL);
            playbackRail.setGravity(Gravity.CENTER);
            playbackRail.setPadding(dp(4), dp(4), dp(4), dp(4));
            playbackRail.setBackground(activity.getDrawable(R.drawable.zc_shittok_control_rail));
            playbackRail.setTag("shittok_playback_rail");

            mute = textIconActionButton(
                    chaosMuted ? R.drawable.ic_action_volume_off : R.drawable.ic_action_volume_on,
                    chaosMuted ? "Unmute video" : "Mute video",
                    "shittok_mute"
            );
            playbackRail.addView(mute, actionParams());

            fullscreen = imageActionButton(
                    R.drawable.ic_action_fullscreen,
                    "Watch horizontal video fullscreen",
                    "shittok_fullscreen"
            );
            fullscreen.setVisibility(View.GONE);
            playbackRail.addView(fullscreen, actionParams());

            FrameLayout.LayoutParams playbackParams =
                    new FrameLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT);
            playbackParams.gravity = Gravity.TOP | Gravity.END;
            playbackParams.setMargins(0, dp(14), dp(12), 0);
            root.addView(playbackRail, playbackParams);

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
            seekBar.setProgressTintList(ColorStateList.valueOf(UiPalette.PRIMARY));
            seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.argb(150, 210, 210, 215)));
            seekBar.setThumbTintList(ColorStateList.valueOf(UiPalette.PRIMARY));
            FrameLayout.LayoutParams seekParams = new FrameLayout.LayoutParams(-1, dp(48));
            seekParams.gravity = Gravity.BOTTOM;
            seekParams.setMargins(dp(8), 0, dp(8), dp(1));
            seekBar.setVisibility(View.INVISIBLE);
            root.addView(seekBar, seekParams);

            playerView.setOnClickListener(v -> {
                haptic(v);
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
                    player.pause();
                    updateProgress();
                    showControlsPersistent();
                    syncPausedChrome();
                } else {
                    userPaused = false;
                    hidePausedChrome();
                    everStarted = true;
                    player.play();
                    showControlsTemporarily();
                }
            });

            playerView.setLongClickable(false);
            playerView.setOnTouchListener((v, event) -> {
                int action = event.getActionMasked();

                if (action == MotionEvent.ACTION_DOWN) {
                    creatorSwipeDownX = event.getX();
                    creatorSwipeDownY = event.getY();
                    creatorSwipeTracking = false;
                }

                if (event.getPointerCount() > 1) {
                    creatorSwipeTracking = false;
                    cancelCreatorSwipePreview(root);
                    ShitTokTransitionSnapshotStore.remove(creatorSwipeTransitionToken);
                    creatorSwipeTransitionToken = "";
                    pager.setUserInputEnabled(true);
                    ViewParentCompat.disallow(v, false);
                    return false;
                }

                if (action == MotionEvent.ACTION_MOVE && !manualFullscreen) {
                    String creator = ShitTokCreatorMetadata.creatorName(item);
                    float dx = event.getX() - creatorSwipeDownX;
                    float dy = event.getY() - creatorSwipeDownY;
                    if (!creator.isEmpty() && dx < -dp(14)
                            && Math.abs(dx) > Math.abs(dy) * 1.20f) {
                        if (!creatorSwipeTracking) {
                            creatorSwipeTracking = true;
                            View sourceSurface = creatorSwipeSourceView();
                            creatorSwipeTransitionToken =
                                    ShitTokTransitionSnapshotStore.beginCapture(
                                            activity,
                                            sourceSurface == null ? root : sourceSurface,
                                            playerView.getVideoSurfaceView()
                                    );
                            beginCreatorSwipePreview(creator, creatorSwipeTransitionToken);
                        }
                        pager.setUserInputEnabled(false);
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
                        boolean openCreator = action == MotionEvent.ACTION_UP
                                && shouldOpenCreatorGallerySwipe(dx, dy, threshold);
                        creatorSwipeTracking = false;
                        pager.setUserInputEnabled(true);
                        ViewParentCompat.disallow(v, false);
                        if (openCreator) {
                            String creator = ShitTokCreatorMetadata.creatorName(item);
                            haptic(v);
                            pauseAndRecord();
                            String transitionToken = creatorSwipeTransitionToken;
                            creatorSwipeTransitionToken = "";
                            commitCreatorSwipePreview(
                                    root,
                                    creator,
                                    transitionToken,
                                    () -> openCreatorGallery(true, transitionToken)
                            );
                        } else {
                            cancelCreatorSwipePreview(root);
                            ShitTokTransitionSnapshotStore.remove(creatorSwipeTransitionToken);
                            creatorSwipeTransitionToken = "";
                        }
                        return true;
                    }

                    pager.setUserInputEnabled(true);
                    ViewParentCompat.disallow(v, false);
                }
                return false;
            });

            View.OnLongClickListener menuLongPress = v -> {
                haptic(v);
                showMoreMenu();
                return true;
            };
            title.setOnLongClickListener(menuLongPress);
            meta.setOnLongClickListener(menuLongPress);
            title.setOnClickListener(v -> {
                if (ShitTokCreatorMetadata.creatorName(item).isEmpty()) return;
                haptic(v);
                openCreatorGallery(false, "");
            });
            creatorAvatarControl.setOnClickListener(v -> {
                if (creatorIdentity == null) return;
                haptic(v);
                openCreatorGallery(false, "");
            });
            creatorFavoriteBadge.setOnClickListener(v -> {
                if (creatorIdentity == null
                        || CreatorFavoriteStore.contains(activity, creatorIdentity)) return;
                haptic(v);
                CreatorFavoriteStore.toggle(activity, creatorIdentity);
                refreshCreatorFavoriteBadge();
                Toast.makeText(
                        activity,
                        "Added " + creatorIdentity.title + " to Favorite Creators.",
                        Toast.LENGTH_SHORT
                ).show();
                showControlsTemporarily();
            });

            mute.setOnClickListener(v -> {
                haptic(v);
                setChaosMuted(!chaosMuted);
                showControlsTemporarily();
            });
            fullscreen.setOnClickListener(v -> {
                if (!horizontalVideo) return;
                haptic(v);
                if (manualFullscreen) exitManualFullscreen();
                else enterManualFullscreen(this);
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
            share.setOnClickListener(v -> {
                haptic(v);
                share(item);
                showControlsTemporarily();
            });
            more.setOnClickListener(v -> {
                haptic(v);
                showMoreMenu();
            });

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (!fromUser || player == null) return;
                    long duration = player.getDuration();
                    if (duration <= 0L) return;
                    player.seekTo((duration * progress) / 1000L);
                }

                @Override
                public void onStartTrackingTouch(SeekBar bar) {
                    scrubbing = true;
                    pager.setUserInputEnabled(false);
                    showControlsPersistent();
                }

                @Override
                public void onStopTrackingTouch(SeekBar bar) {
                    scrubbing = false;
                    pager.setUserInputEnabled(true);
                    updateProgress();
                    if (player != null && player.isPlaying()) showControlsTemporarily();
                    else showControlsPersistent();
                }
            });
        }

        private TextView textIconActionButton(int icon, String description, String tag) {
            TextView button = new TextView(activity);
            button.setGravity(Gravity.CENTER);
            button.setContentDescription(description);
            button.setBackgroundColor(Color.TRANSPARENT);
            button.setPadding(dp(12), dp(12), dp(12), dp(12));
            button.setClickable(true);
            button.setFocusable(false);
            button.setTag(tag);
            button.setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0);
            button.setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
            return button;
        }

        private ImageView imageActionButton(int icon, String description, String tag) {
            ImageView button = new ImageView(activity);
            button.setImageResource(icon);
            button.setImageTintList(ColorStateList.valueOf(Color.WHITE));
            button.setScaleType(ImageView.ScaleType.CENTER);
            button.setContentDescription(description);
            button.setBackgroundColor(Color.TRANSPARENT);
            button.setPadding(dp(12), dp(12), dp(12), dp(12));
            button.setClickable(true);
            button.setFocusable(false);
            button.setTag(tag);
            return button;
        }

        private LinearLayout.LayoutParams actionParams() {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(48));
            params.setMargins(0, dp(1), 0, dp(1));
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

        private void bindCreatorAvatar(NativeContentItem media, boolean creatorClip) {
            Glide.with(creatorAvatar).clear(creatorAvatar);
            creatorAvatar.setImageResource(R.drawable.ic_more_account);
            creatorIdentity = creatorClip
                    ? ShitTokCreatorMetadata.creatorIdentity(activity, media)
                    : null;
            if (creatorIdentity == null) {
                creatorAvatarControl.setVisibility(View.GONE);
                creatorAvatarControl.setContentDescription(null);
                creatorFavoriteBadge.setContentDescription(null);
                return;
            }

            creatorAvatarControl.setVisibility(View.VISIBLE);
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
                        .placeholder(R.drawable.ic_more_account)
                        .error(R.drawable.ic_more_account)
                        .into(creatorAvatar);
            }
            refreshCreatorFavoriteBadge();
        }

        private void refreshCreatorFavoriteBadge() {
            if (creatorIdentity == null) return;
            boolean favorite = CreatorFavoriteStore.contains(activity, creatorIdentity);
            creatorFavoriteBadge.setText(favorite ? "✓" : "+");
            creatorFavoriteBadge.setTextSize(favorite ? 13 : 16);
            creatorFavoriteBadge.setClickable(!favorite);
            creatorFavoriteBadge.setFocusable(!favorite);
            creatorFavoriteBadge.setAlpha(favorite ? 0.92f : 1f);
            creatorFavoriteBadge.setContentDescription(
                    favorite
                            ? creatorIdentity.title + " is in Favorite Creators"
                            : "Add " + creatorIdentity.title + " to Favorite Creators"
            );
        }

        void bind(NativeContentItem next, int position) {
            pauseAndRecord();
            detachPlayerForDeferredRelease();
            root.removeCallbacks(skipFailedClipRunnable);
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
            controlsVisible = true;
            userPaused = false;
            scrubbing = false;
            retryAttempted = false;
            failurePending = false;
            horizontalVideo = false;
            aspectSampleRecorded = false;
            videoAspectRatio = 0f;
            fullscreen.setVisibility(View.GONE);
            seekBar.setProgress(0);
            seekBar.setEnabled(false);
            seekBar.setAlpha(0f);
            seekBar.setVisibility(View.INVISIBLE);
            pausePlayOverlay.setVisibility(View.GONE);
            lower.setAlpha(1f);
            lower.setVisibility(View.VISIBLE);
            playbackRail.setAlpha(1f);
            playbackRail.setVisibility(View.VISIBLE);
            applyMuteState();
            String creator = ShitTokCreatorMetadata.creatorName(next);
            boolean creatorClip = !creator.isEmpty();
            bindCreatorAvatar(next, creatorClip);
            String displayTitle = creatorClip
                    ? creator
                    : (next.title == null || next.title.isEmpty() ? "Random video" : next.title);
            title.setText(displayTitle);
            title.setClickable(creatorClip);
            title.setContentDescription(
                    creatorClip ? "Open " + creator + " gallery" : displayTitle
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
            meta.setVisibility(creatorClip ? View.GONE : View.VISIBLE);
            comments.setVisibility(supportsComments(next) ? View.VISIBLE : View.GONE);
            updateSaveButton(next, save);
            loading.setVisibility(View.VISIBLE);
            failure.setText("Couldn't play this one\nSwipe up for the next video");
            failure.setContentDescription("Couldn't play this video");
            failure.setVisibility(View.GONE);
            poster.setVisibility(View.VISIBLE);
            Glide.with(poster).clear(poster);
            if (next.imageUrl == null || next.imageUrl.isEmpty()) {
                poster.setImageDrawable(new ColorDrawable(Color.rgb(20, 20, 22)));
            } else {
                Glide.with(poster)
                        .load(imageWithHeaders(next.imageUrl, next.url))
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .dontAnimate()
                        .placeholder(new ColorDrawable(Color.rgb(20, 20, 22)))
                        .error(new ColorDrawable(Color.rgb(20, 20, 22)))
                        .into(poster);
            }
            syncOrientationChrome();
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
            if (item == null || nextStream == null || nextStream.mediaUrl == null || nextStream.mediaUrl.isEmpty()) return;
            if (stream != null && stream.mediaUrl.equals(nextStream.mediaUrl) && player != null) {
                PlaybackException currentError = player.getPlayerError();
                if (currentError != null) {
                    lastPlaybackError = currentError;
                    lastFailureStage = "Player stopped before ready";
                    if (!retryAttempted) {
                        retryAttempted = true;
                        retryPlayback(this, currentError);
                    } else {
                        showPlayerFailureAndSkip(currentError, lastFailureStage);
                    }
                    return;
                }
                loading.setVisibility(View.GONE);
                applyMuteState();
                userPaused = false;
                hidePausedChrome();
                if (autoplay) {
                    everStarted = true;
                    player.play();
                } else {
                    player.pause();
                }
                maybeCompleteStartupHandoff();
                preloadReadyComments(item, boundPosition);
                return;
            }

            releasePlayer();
            stream = nextStream;
            lastAttemptedStream = nextStream;
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory();
            try {
                http.setUserAgent(WebSettings.getDefaultUserAgent(activity));
            } catch (Exception ignored) {
            }

            Map<String, String> headers = new HashMap<>();
            try {
                headers.putAll(ShitShowPlayableResolver.playbackHeaders(nextStream.mediaUrl));
            } catch (Exception ignored) {
            }
            if (nextStream.pageUrl != null && !nextStream.pageUrl.isEmpty()) {
                putHeaderIfMissing(headers, "Referer", nextStream.requestReferer);
                try {
                    Uri page = Uri.parse(nextStream.pageUrl);
                    if (page.getScheme() != null && page.getHost() != null) {
                        putHeaderIfMissing(headers, "Origin", page.getScheme() + "://" + page.getHost());
                    }
                } catch (Exception ignored) {
                }
            }
            try {
                String cookies = CookieManager.getInstance().getCookie(nextStream.mediaUrl);
                if ((cookies == null || cookies.isEmpty()) && nextStream.pageUrl != null) {
                    cookies = CookieManager.getInstance().getCookie(nextStream.pageUrl);
                }
                if (cookies != null && !cookies.isEmpty()) putHeaderIfMissing(headers, "Cookie", cookies);
            } catch (Exception ignored) {
            }
            if (!headers.isEmpty()) http.setDefaultRequestProperties(headers);

            DefaultMediaSourceFactory sourceFactory = new DefaultMediaSourceFactory(activity)
                    .setDataSourceFactory(ShitTokMediaCache.wrap(activity, http));
            ExoPlayer.Builder playerBuilder = new ExoPlayer.Builder(activity)
                    .setMediaSourceFactory(sourceFactory);
            if (!autoplay) {
                playerBuilder.setLoadControl(
                        new DefaultLoadControl.Builder()
                                .setBufferDurationsMs(
                                        NEXT_PRELOAD_MIN_BUFFER_MS,
                                        NEXT_PRELOAD_MAX_BUFFER_MS,
                                        350,
                                        1_000
                                )
                                .setPrioritizeTimeOverSizeThresholds(true)
                                .build()
                );
            }
            player = playerBuilder.build();
            playerHolders.add(this);
            ExoPlayer createdPlayer = player;
            player.setRepeatMode(Player.REPEAT_MODE_OFF);
            player.setVolume(chaosMuted ? 0f : 1f);
            player.setTrackSelectionParameters(
                    player.getTrackSelectionParameters()
                            .buildUpon()
                            .setMaxVideoSize(SHITTOK_MAX_VIDEO_WIDTH, SHITTOK_MAX_VIDEO_HEIGHT)
                            .setMaxVideoBitrate(SHITTOK_MAX_VIDEO_BITRATE)
                            .build()
            );
            playerView.setPlayer(player);

            MediaItem.Builder media = new MediaItem.Builder().setUri(nextStream.mediaUrl);
            String lowerUrl = nextStream.mediaUrl.toLowerCase(Locale.US);
            if (lowerUrl.contains(".m3u8")) media.setMimeType(MimeTypes.APPLICATION_M3U8);
            else if (lowerUrl.contains(".mpd")) media.setMimeType(MimeTypes.APPLICATION_MPD);
            else if (lowerUrl.contains(".mp4") || lowerUrl.contains(".m4v")) media.setMimeType(MimeTypes.VIDEO_MP4);
            player.setMediaItem(media.build());
            if (autoplay && boundPosition == selectedPosition) {
                long resumePosition = sessionResume.positionFor(item.url);
                if (resumePosition > 0L) player.seekTo(resumePosition);
            }
            player.setPlayWhenReady(autoplay);
            if (autoplay) everStarted = true;
            player.addListener(new Player.Listener() {
                @Override
                public void onVideoSizeChanged(VideoSize videoSize) {
                    if (player != createdPlayer || videoSize.width <= 0 || videoSize.height <= 0) return;
                    float width = videoSize.width * Math.max(0.01f, videoSize.pixelWidthHeightRatio);
                    float height = videoSize.height;
                    videoAspectRatio = width / Math.max(1f, height);
                    horizontalVideo = shouldOfferLandscapeFullscreen(videoAspectRatio);
                    if (!aspectSampleRecorded) {
                        aspectSampleRecorded = true;
                        aspectPriority.record(item, videoAspectRatio);
                    }
                    root.post(ChaosHolder.this::syncOrientationChrome);
                }

                @Override
                public void onPlaybackStateChanged(int state) {
                    if (player != createdPlayer) return;
                    if (state == Player.STATE_READY) {
                        RatingFeedbackPrompt.recordSuccessfulPlayback(activity, nextStream.mediaUrl);
                        failurePending = false;
                        root.removeCallbacks(skipFailedClipRunnable);
                        loading.setVisibility(View.GONE);
                        failure.setVisibility(View.GONE);
                        poster.setVisibility(View.GONE);
                        updateProgress();
                        maybeCompleteStartupHandoff();
                        preloadReadyComments(item, boundPosition);
                    } else if (state == Player.STATE_ENDED) {
                        if (boundPosition == selectedPosition) sessionResume.clear();
                        loading.setVisibility(View.GONE);
                        seekBar.setProgress(1000);
                        if (item != null) {
                            try {
                                long duration = Math.max(0L, createdPlayer.getDuration());
                                PlaybackHistoryStore.record(
                                        activity,
                                        item.title,
                                        item.url,
                                        duration,
                                        duration,
                                        true
                                );
                            } catch (Exception ignored) {
                            }
                        }
                        requestAutoAdvance(boundPosition);
                    }
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (player != createdPlayer) return;
                    if (isPlaying) {
                        userPaused = false;
                        everStarted = true;
                        hidePausedChrome();
                        showControlsTemporarily();
                    } else {
                        updateProgress();
                        if (userPaused) {
                            if (!scrubbing) showControlsPersistent();
                            syncPausedChrome();
                        } else {
                            hidePausedChrome();
                        }
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    if (player != createdPlayer) return;
                    loading.setVisibility(View.GONE);
                    userPaused = false;
                    hidePausedChrome();
                    lastPlaybackError = error;
                    lastFailureStage = "Player error";
                    if (!retryAttempted) {
                        retryAttempted = true;
                        root.post(() -> retryPlayback(ChaosHolder.this, error));
                    } else {
                        showPlayerFailureAndSkip(error, lastFailureStage);
                    }
                }
            });
            createdPlayer.prepare();
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

        private void openCreatorGallery(boolean seamless, String transitionToken) {
            if (item == null) return;
            String creator = ShitTokCreatorMetadata.creatorName(item);
            if (creator.isEmpty()) return;
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
            Intent intent = NativeFeedBrowserActivity.createCreatorGallery(
                    activity,
                    creator,
                    creator,
                    "",
                    ShitTokCreatorGalleryPreloader.sessionId(activity, creator)
            );
            if (!returnToken.isEmpty()) {
                intent.putExtra(
                        NativeFeedBrowserActivity.EXTRA_SHITTOK_RETURN_TRANSITION,
                        returnToken
                );
            }
            activity.startActivity(intent);
            if (seamless) activity.overridePendingTransition(0, 0);
        }

        private void showMoreMenu() {
            if (item == null) return;
            String savedLabel = FavoriteStore.contains(activity, item.url)
                    ? "Remove from Watch Later"
                    : "Watch Later";
            VideoActionSheet.showCompact(
                    activity,
                    item.title,
                    VideoActionSheet.section(
                            "SAVE",
                            VideoActionSheet.action(
                                    R.drawable.ic_action_download,
                                    "Download",
                                    "Save this video for offline playback",
                                    this::downloadCurrentVideo
                            ),
                            VideoActionSheet.action(
                                    R.drawable.ic_more_library,
                                    savedLabel,
                                    "Keep this video in your library",
                                    () -> toggleSaved(item, save)
                            )
                    ),
                    VideoActionSheet.section(
                            "ACTIONS",
                            VideoActionSheet.action(
                                    R.drawable.ic_action_comments,
                                    "Comments",
                                    "Read and reply without leaving ShitTok",
                                    () -> openInlineComments(item)
                            ),
                            VideoActionSheet.action(
                                    R.drawable.ic_action_share,
                                    "Share",
                                    "Send the video page",
                                    () -> share(item)
                            ),
                            VideoActionSheet.action(
                                    R.drawable.ic_more_website,
                                    "Video details",
                                    "View the full video page",
                                    this::openCurrentDetails
                            )
                    ),
                    VideoActionSheet.section(
                            "FEED",
                            VideoActionSheet.action(
                                    R.drawable.ic_action_hide,
                                    "Not interested",
                                    "Hide this clip from your ShitTok feed",
                                    () -> hideFromChaos(item)
                            ),
                            VideoActionSheet.action(
                                    R.drawable.ic_action_report,
                                    "Report problem",
                                    "Tell us what went wrong",
                                    () -> showPlaybackReport(this)
                            )
                    )
            );
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
                    item.title,
                    stream.pageUrl == null || stream.pageUrl.isEmpty() ? item.url : stream.pageUrl,
                    item.imageUrl,
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

        void applyMuteState() {
            mute.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    chaosMuted ? R.drawable.ic_action_volume_off : R.drawable.ic_action_volume_on,
                    0,
                    0
            );
            mute.setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
            mute.setContentDescription(chaosMuted ? "Unmute video" : "Mute video");
            if (player != null) player.setVolume(chaosMuted ? 0f : 1f);
        }

        private void syncPausedChrome() {
            boolean show = shouldShowPausedChrome(userPaused, clearDisplay);
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
            seekBar.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            seekBar.setAlpha(show ? 1f : 0f);
        }

        private void hidePausedChrome() {
            pausePlayOverlay.animate().cancel();
            pausePlayOverlay.setVisibility(View.GONE);
            seekBar.animate().cancel();
            seekBar.setAlpha(0f);
            seekBar.setVisibility(View.INVISIBLE);
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

        void syncOrientationChrome() {
            applyViewportInset();
            if (clearDisplay) {
                applyClearDisplay(true);
                return;
            }
            root.removeCallbacks(hideControlsRunnable);
            lower.animate().cancel();
            playbackRail.animate().cancel();
            seekBar.animate().cancel();

            controlsVisible = true;
            lower.setVisibility(View.VISIBLE);
            playbackRail.setVisibility(View.VISIBLE);
            lower.setAlpha(1f);
            playbackRail.setAlpha(1f);
            fullscreen.setImageResource(manualFullscreen
                    ? R.drawable.ic_action_fullscreen_exit
                    : R.drawable.ic_action_fullscreen);
            fullscreen.setContentDescription(manualFullscreen
                    ? "Exit fullscreen"
                    : "Watch horizontal video fullscreen");
            fullscreen.setVisibility(horizontalVideo ? View.VISIBLE : View.GONE);
            playbackRail.setAlpha(1f);

            syncPausedChrome();

            if (!portrait() && player != null && player.isPlaying() && !scrubbing) {
                root.postDelayed(hideControlsRunnable, 2200L);
            }
        }

        private void showControls(boolean autoHide) {
            if (clearDisplay) {
                applyClearDisplay(true);
                return;
            }
            root.removeCallbacks(hideControlsRunnable);
            lower.animate().cancel();
            playbackRail.animate().cancel();
            seekBar.animate().cancel();
            controlsVisible = true;
            lower.setVisibility(View.VISIBLE);
            playbackRail.setVisibility(View.VISIBLE);
            fullscreen.setVisibility(horizontalVideo ? View.VISIBLE : View.GONE);
            lower.setAlpha(1f);
            playbackRail.setAlpha(1f);
            syncPausedChrome();
            if (autoHide && !scrubbing && !portrait()) {
                root.postDelayed(hideControlsRunnable, 2200L);
            }
        }

        void applyClearDisplay(boolean clear) {
            applyViewportInset();
            root.removeCallbacks(hideControlsRunnable);
            lower.animate().cancel();
            playbackRail.animate().cancel();
            seekBar.animate().cancel();

            if (clear) {
                controlsVisible = false;
                lower.setAlpha(0f);
                playbackRail.setAlpha(0f);
                seekBar.setAlpha(0f);
                lower.setVisibility(View.INVISIBLE);
                playbackRail.setVisibility(View.INVISIBLE);
                pausePlayOverlay.setVisibility(View.GONE);
                seekBar.setVisibility(View.INVISIBLE);
                return;
            }

            controlsVisible = true;
            lower.setAlpha(1f);
            playbackRail.setAlpha(1f);
            seekBar.setAlpha(1f);
            lower.setVisibility(View.VISIBLE);
            playbackRail.setVisibility(View.VISIBLE);
            fullscreen.setVisibility(horizontalVideo ? View.VISIBLE : View.GONE);
            syncPausedChrome();
            if (!portrait() && player != null && player.isPlaying() && !scrubbing) {
                root.postDelayed(hideControlsRunnable, 2200L);
            }
        }

        private void hideControlsNow() {
            if (portrait()) {
                controlsVisible = true;
                lower.setVisibility(View.VISIBLE);
                playbackRail.setVisibility(View.VISIBLE);
                fullscreen.setVisibility(horizontalVideo ? View.VISIBLE : View.GONE);
                lower.setAlpha(1f);
                playbackRail.setAlpha(1f);
                return;
            }
            if (scrubbing || player == null || !player.isPlaying()) return;
            controlsVisible = false;
            lower.animate()
                    .alpha(0f)
                    .setDuration(180L)
                    .withEndAction(() -> {
                        if (!controlsVisible) lower.setVisibility(View.INVISIBLE);
                    })
                    .start();
            playbackRail.animate()
                    .alpha(0f)
                    .setDuration(180L)
                    .withEndAction(() -> {
                        if (!controlsVisible) playbackRail.setVisibility(View.INVISIBLE);
                    })
                    .start();
        }

        private void updateProgress() {
            if (player == null || scrubbing) return;
            long duration = player.getDuration();
            long position = Math.max(0L, player.getCurrentPosition());
            if (duration <= 0L) {
                seekBar.setEnabled(false);
                seekBar.setProgress(0);
                return;
            }
            seekBar.setEnabled(true);
            int progress = (int) Math.max(0L, Math.min(1000L, (position * 1000L) / duration));
            seekBar.setProgress(progress);
        }

        void showRetrying() {
            failurePending = false;
            root.removeCallbacks(skipFailedClipRunnable);
            loading.setVisibility(View.VISIBLE);
            failure.setText("Trying another link…");
            failure.setContentDescription("Playback failed. Trying another link.");
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
            loading.setVisibility(View.GONE);
            failurePending = true;
            failure.setText("Couldn't play this one\nMoving to the next video…");
            failure.setContentDescription("Couldn't play this video. Moving to the next video.");
            failure.setVisibility(View.VISIBLE);
            poster.setVisibility(View.VISIBLE);
            userPaused = false;
            hidePausedChrome();
            showControlsPersistent();
            root.removeCallbacks(skipFailedClipRunnable);
            root.postDelayed(skipFailedClipRunnable, FAILED_CLIP_SKIP_DELAY_MS);
        }

        void pauseAndRecord() {
            if (player == null || item == null) return;
            userPaused = false;
            hidePausedChrome();
            try {
                long position = Math.max(0L, player.getCurrentPosition());
                long duration = Math.max(0L, player.getDuration());
                if (everStarted || position > 1000L) {
                    PlaybackHistoryStore.record(
                            activity, item.title, item.url, position, duration, false);
                }
                player.pause();
            } catch (Exception ignored) {
            }
        }

        void detachPlayerForDeferredRelease() {
            root.removeCallbacks(hideControlsRunnable);
            root.removeCallbacks(skipFailedClipRunnable);
            failurePending = false;
            if (scrubbing) {
                scrubbing = false;
                pager.setUserInputEnabled(true);
            }
            ExoPlayer detached = player;
            if (detached != null) {
                try {
                    detached.pause();
                    playerView.setPlayer(null);
                } catch (Exception ignored) {
                }
                player = null;
                enqueueDeferredPlayerRelease(detached);
            }
            playerHolders.remove(this);
            stream = null;
        }

        void releasePlayer() {
            root.removeCallbacks(hideControlsRunnable);
            root.removeCallbacks(skipFailedClipRunnable);
            failurePending = false;
            if (scrubbing) {
                scrubbing = false;
                pager.setUserInputEnabled(true);
            }
            if (player != null) {
                try {
                    playerView.setPlayer(null);
                    player.release();
                } catch (Exception ignored) {
                }
                player = null;
            }
            playerHolders.remove(this);
            stream = null;
        }
    }
}
