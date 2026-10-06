package com.webapp.crazyshit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.PlayerControlView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public class VideoDetailActivity extends Activity {
    public static final String EXTRA_VIEWS = "views";
    public static final String EXTRA_UPLOADER = "uploader";
    public static final String EXTRA_COMMENTS = "comments";
    public static final String EXTRA_REOPEN_DETAIL = "reopen_detail";
    public static final String EXTRA_RELATED_FEED_URL = "related_feed_url";
    public static final String EXTRA_SOURCE = "content_source";
    public static final String EXTRA_MEDIA_REFERER = "media_referer";
    public static final String EXTRA_POSTER_URL = "poster_url";
    public static final String EXTRA_SHOWS_ORIGIN = "shows_origin";
    public static final String EXTRA_PLAYBACK_IDENT = "playback_ident";
    public static final String EXTRA_SHOWS_CONTINUE_RESUME = "shows_continue_resume";
    public static final String EXTRA_MANUAL_LANDSCAPE_FULLSCREEN =
            ShowsPlaybackOrientationPolicy.EXTRA_MANUAL_LANDSCAPE_FULLSCREEN;
    public static final String EXTRA_SOCIAL_ENTRY = "social_entry";
    public static final String EXTRA_SOCIAL_FOCUS_COMMENT_ID = "social_focus_comment_id";
    public static final String EXTRA_SOCIAL_AUTO_REPLY = "social_auto_reply";

    private static final String SITE = "https://crazyshit.com/";
    private static final int CONTROL_TIMEOUT_MS = 2500;
    private static final int RELATED_HISTORY_LIMIT = 24;
    private static final int RELATED_THUMBNAIL_WORKERS = 4;
    private static final long RELATED_SLIDE_OUT_MS = 105L;
    private static final long RELATED_SLIDE_IN_MS = 175L;
    private static final long SHOWS_IDENT_MIN_MS =
            ShowsPlaybackSplashView.FIRST_REVEAL_COMPLETE_MS + 50L;
    private static final String THUMB_UA =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final CrazyShitRepository repository = new CrazyShitRepository();
    private final EfuktRepository efuktRepository = new EfuktRepository();
    private final BunkrRepository bunkrRepository = new BunkrRepository();
    private final BaddieHubRepository baddieHubRepository = new BaddieHubRepository();
    private final WebVideoSourceRepository webVideoSourceRepository = new WebVideoSourceRepository();
    private final Map<String, ImageView> relatedImages = new LinkedHashMap<>();
    private final Map<String, String> resolvedRelatedThumbnails = new LinkedHashMap<>();
    private final Set<String> requestedRelatedThumbnails = new HashSet<>();
    private final ArrayDeque<VideoHistoryEntry> relatedHistory = new ArrayDeque<>();

    private FrameLayout root;
    private LinearLayout shell;
    private SwipeMinimizeFrameLayout playerContainer;
    private PlayerView playerView;
    private PlayerControlView fullscreenOverlay;
    private ShowsFullscreenControls fullscreenControls;
    private boolean fullscreenChromeActive;
    private ScrollView detailsScroll;
    private LinearLayout detailsColumn;
    private LinearLayout relatedContainer;
    private TextView titleView;
    private TextView metaView;
    private TextView playerTitleView;
    private TextView videoLikeButton;
    private TextView commentButton;
    private TextView watchLaterButton;
    private ImageButton portraitFullscreenButton;
    private ProgressBar loading;
    private ImageView startupPoster;
    private ProgressBar startupPosterLoading;
    private FrameLayout showsLaunchCurtain;
    private ShowsPlaybackSplashView showsLaunchLoader;
    private SeekBar portraitSeekBar;
    private ExoPlayer player;
    private RenderedThumbnailResolver[] thumbnailResolvers;
    private OnBackInvokedCallback backCallback;
    private FrameLayout relatedBackPreviewLayer;
    private ImageView relatedBackPreviewImage;
    private VideoHistoryEntry relatedBackPreviewEntry;
    private float relatedBackDirection = 1f;

    private String mediaUrl;
    private String pageUrl;
    private String title;
    private String views;
    private String uploader;
    private String comments;
    private String userAgent;
    private String cookies;
    private String relatedFeedUrl;
    private String source;
    private String mediaReferer;
    private String posterUrl;
    private boolean showsOrigin;
    private boolean manualLandscapeFullscreen;
    private boolean playbackIdent;
    private boolean socialEntry;
    private String socialFocusCommentId;
    private boolean socialAutoReply;
    private boolean socialCommentsOpened;
    private InlineCommentsDialog activeComments;
    private boolean socialNavigationPendingReturn;
    private boolean socialReturnPlayWhenReady;
    private boolean videoLiked;
    private int videoLikeCount;
    private String videoLikePageUrl = "";
    private int videoLikeRequestGeneration;
    private int commentCount;
    private String commentCountPageUrl = "";
    private int commentCountRequestGeneration;
    private final PlaybackRecovery playbackRecovery = new PlaybackRecovery();
    private boolean recoveryResumed;
    private long requestedStartPosition;
    private int resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    private boolean failureShown;
    private boolean minimizing;
    private boolean entrancePlayed;
    private boolean startupPosterDismissed;
    private boolean relatedTransitionRunning;
    private boolean portraitVideo;
    private boolean portraitFullscreen;
    private boolean rotatableFullscreen;
    private boolean sensorFullscreen;
    private SensorMediaOrientationListener orientationListener;
    private int thumbnailResolverCursor;
    private int relatedLoadGeneration;
    private int relatedPlayGeneration;
    private boolean portraitSeekScrubbing;
    private Boolean showsPortraitOrientation;
    private boolean showsFirstFrameRendered;
    private boolean showsOrientationSettled;
    private boolean showsCurtainDismissScheduled;
    private int showsTargetOrientation = Configuration.ORIENTATION_UNDEFINED;
    private long showsIdentStartedAt;
    private long lastShowsFramePositionMs = -1L;

    private final Runnable portraitProgressTicker = new Runnable() {
        @Override
        public void run() {
            updatePortraitProgress();
            if (portraitSeekBar != null) {
                portraitSeekBar.postDelayed(this, 350L);
            }
        }
    };
    private final Runnable hidePortraitSeekBar = () -> {
        if (portraitSeekBar == null || portraitSeekScrubbing || !canShowPortraitSeekBar()) return;
        portraitSeekBar.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction(() -> {
                    if (portraitSeekBar != null && portraitSeekBar.getAlpha() == 0f) {
                        portraitSeekBar.setVisibility(View.INVISIBLE);
                    }
                })
                .start();
    };

    private static final class VideoHistoryEntry {
        final String mediaUrl;
        final String pageUrl;
        final String title;
        final String views;
        final String uploader;
        final String comments;
        final String userAgent;
        final String cookies;
        final String mediaReferer;
        final String posterUrl;
        final long positionMs;
        final Bitmap previewBitmap;

        VideoHistoryEntry(
                String mediaUrl,
                String pageUrl,
                String title,
                String views,
                String uploader,
                String comments,
                String userAgent,
                String cookies,
                String mediaReferer,
                String posterUrl,
                long positionMs,
                Bitmap previewBitmap
        ) {
            this.mediaUrl = mediaUrl;
            this.pageUrl = pageUrl;
            this.title = title;
            this.views = views;
            this.uploader = uploader;
            this.comments = comments;
            this.userAgent = userAgent;
            this.cookies = cookies;
            this.mediaReferer = mediaReferer;
            this.posterUrl = posterUrl;
            this.positionMs = positionMs;
            this.previewBitmap = previewBitmap;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        mediaUrl = getIntent().getStringExtra(PlayerActivity.EXTRA_MEDIA_URL);
        pageUrl = getIntent().getStringExtra(PlayerActivity.EXTRA_PAGE_URL);
        title = clean(getIntent().getStringExtra(PlayerActivity.EXTRA_TITLE));
        views = clean(getIntent().getStringExtra(EXTRA_VIEWS));
        uploader = clean(getIntent().getStringExtra(EXTRA_UPLOADER));
        comments = clean(getIntent().getStringExtra(EXTRA_COMMENTS));
        userAgent = clean(getIntent().getStringExtra(PlayerActivity.EXTRA_USER_AGENT));
        cookies = clean(getIntent().getStringExtra(PlayerActivity.EXTRA_COOKIES));
        relatedFeedUrl = clean(getIntent().getStringExtra(EXTRA_RELATED_FEED_URL));
        source = clean(getIntent().getStringExtra(EXTRA_SOURCE));
        mediaReferer = clean(getIntent().getStringExtra(EXTRA_MEDIA_REFERER));
        posterUrl = clean(getIntent().getStringExtra(EXTRA_POSTER_URL));
        showsOrigin = getIntent().getBooleanExtra(EXTRA_SHOWS_ORIGIN, false);
        manualLandscapeFullscreen = getIntent().getBooleanExtra(
                EXTRA_MANUAL_LANDSCAPE_FULLSCREEN,
                false
        );
        playbackIdent = showsOrigin || getIntent().getBooleanExtra(EXTRA_PLAYBACK_IDENT, false);
        socialEntry = getIntent().getBooleanExtra(EXTRA_SOCIAL_ENTRY, false);
        socialFocusCommentId = clean(getIntent().getStringExtra(EXTRA_SOCIAL_FOCUS_COMMENT_ID));
        socialAutoReply = getIntent().getBooleanExtra(EXTRA_SOCIAL_AUTO_REPLY, false);
        socialCommentsOpened = state != null && state.getBoolean("social_comments_opened", false)
                && !state.getBoolean("social_comments_visible", false);
        requestedStartPosition = getIntent().getLongExtra(PlayerActivity.EXTRA_START_POSITION, -1L);

        if (mediaUrl == null || mediaUrl.trim().isEmpty()) {
            finish();
            return;
        }
        if (title.isEmpty()) title = "Video";
        if (pageUrl == null) pageUrl = "";
        if (mediaReferer.isEmpty()) mediaReferer = pageUrl;

        getWindow().setStatusBarColor(Color.rgb(13, 13, 15));
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        PhoneOrientationPolicy.applyBrowsingOrientation(this);
        orientationListener = new SensorMediaOrientationListener(this, this::onPhysicalOrientation);

        buildUi();
        buildPlayer(requestedStartPosition);
        if (!showsOrigin) {
            thumbnailResolvers = new RenderedThumbnailResolver[RELATED_THUMBNAIL_WORKERS];
            for (int i = 0; i < thumbnailResolvers.length; i++) {
                thumbnailResolvers[i] = new RenderedThumbnailResolver(this, this::onThumbnailResolved);
            }
        }
        configureBackHandling();
        applyOrientation(getResources().getConfiguration().orientation);
        if (!showsOrigin) loadRelated();
        root.post(this::playEntranceOnce);
        if (socialEntry && !socialCommentsOpened) {
            socialCommentsOpened = true;
            root.post(() -> {
                if (!isFinishing() && !isDestroyed() && supportsComments()) openComments();
            });
        }
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(oledEnabled() ? Color.BLACK : Color.rgb(13, 13, 15));
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
            if (portraitFullscreen ||
                    getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                view.setPadding(0, 0, 0, 0);
                return insets;
            }
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                );
                left = safe.left;
                top = safe.top;
                right = safe.right;
                bottom = safe.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(left, top, right, bottom);
            return insets;
        });
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        playerContainer = new SwipeMinimizeFrameLayout(this);
        playerContainer.setBackgroundColor(Color.BLACK);
        playerContainer.setPivotY(0f);
        playerContainer.setListener(new SwipeMinimizeFrameLayout.Listener() {
            @Override
            public void onDrag(float distancePx, float progress) {
                if (minimizing) return;
                hideVideoControls();
                playerContainer.setPivotX(playerContainer.getWidth() / 2f);

                float scale = 1f - (0.08f * progress);
                float shift = Math.min(dp(38), distancePx * 0.22f);
                playerContainer.setScaleX(scale);
                playerContainer.setScaleY(scale);
                playerContainer.setTranslationY(shift);
                playerContainer.setAlpha(1f);

                if (detailsScroll != null) {
                    detailsScroll.setAlpha(1f - (0.42f * progress));
                    detailsScroll.setTranslationY(Math.min(dp(12), distancePx * 0.04f));
                }
            }

            @Override
            public void onRelease(boolean minimize, float distancePx) {
                if (minimizing) return;
                if (minimize) finishSwipeMinimize();
                else restoreFromSwipe();
            }
        });
        shell.addView(playerContainer, new LinearLayout.LayoutParams(-1, portraitPlayerHeight()));

        playerView = (PlayerView) getLayoutInflater().inflate(
                R.layout.view_polished_video_player_texture,
                playerContainer,
                false
        );
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(false);
        playerView.setControllerHideOnTouch(true);
        playerView.setControllerShowTimeoutMs(CONTROL_TIMEOUT_MS);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setResizeMode(resizeMode);
        playerContainer.addView(playerView, new FrameLayout.LayoutParams(-1, -1));


        startupPoster = new ImageView(this);
        startupPoster.setBackgroundColor(Color.BLACK);
        startupPoster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        startupPoster.setClickable(false);
        playerContainer.addView(startupPoster, new FrameLayout.LayoutParams(-1, -1));

        startupPosterLoading = new ZeroChillProgressBar(this);
        startupPosterLoading.setClickable(false);
        if (startupPosterLoading.getIndeterminateDrawable() != null) {
            startupPosterLoading.getIndeterminateDrawable().setTint(UiPalette.PRIMARY);
        }
        FrameLayout.LayoutParams startupLoadingParams =
                new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER);
        playerContainer.addView(startupPosterLoading, startupLoadingParams);

        fullscreenOverlay = (PlayerControlView) getLayoutInflater().inflate(
                R.layout.view_shows_fullscreen_overlay, playerContainer, false);
        playerContainer.addView(fullscreenOverlay, new FrameLayout.LayoutParams(-1, -1));
        fullscreenControls = new ShowsFullscreenControls(fullscreenOverlay);
        fullscreenControls.suspend(true);
        fullscreenOverlay.setVisibility(View.GONE);
        ((TextView) fullscreenOverlay.findViewById(R.id.player_title)).setText(title);
        fullscreenOverlay.findViewById(R.id.player_back).setOnClickListener(v -> { haptic(v); handleBack(); });
        fullscreenOverlay.findViewById(R.id.player_menu).setOnClickListener(v -> { haptic(v); showPlayerMenu(); });
        fullscreenOverlay.findViewById(R.id.shows_save).setOnClickListener(v -> {
            haptic(v); toggleWatchLater(); fullscreenControls.show();
        });
        fullscreenOverlay.findViewById(R.id.shows_download).setOnClickListener(v -> {
            haptic(v); downloadCurrentVideo(); fullscreenControls.show();
        });
        fullscreenOverlay.findViewById(R.id.shows_share).setOnClickListener(v -> { haptic(v); sharePage(); });
        fullscreenOverlay.findViewById(R.id.shows_like).setOnClickListener(v -> {
            haptic(v); toggleVideoLike(); fullscreenControls.show();
        });
        fullscreenOverlay.findViewById(R.id.shows_fullscreen_toggle).setOnClickListener(v -> {
            haptic(v); setRotatableFullscreen(!rotatableFullscreen);
        });
        GestureDetector fullscreenTaps = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onSingleTapConfirmed(MotionEvent event) {
                fullscreenControls.onVideoTap();
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent event) {
                if (player != null) {
                    long delta = event.getX() < playerView.getWidth() / 2f ? -10000L : 10000L;
                    long target = Math.max(0L, player.getCurrentPosition() + delta);
                    long duration = player.getDuration();
                    player.seekTo(duration > 0 ? Math.min(duration, target) : target);
                }
                return true;
            }
        });
        playerView.setOnTouchListener((v, event) -> fullscreenChromeActive && fullscreenTaps.onTouchEvent(event));

        View playerBack = playerView.findViewById(R.id.player_back);
        playerTitleView = playerView.findViewById(R.id.player_title);
        portraitFullscreenButton = playerView.findViewById(R.id.player_portrait_fullscreen);
        View playerMenu = playerView.findViewById(R.id.player_menu);
        playerTitleView.setText(title);
        playerBack.setOnClickListener(v -> {
            haptic(v);
            handleBack();
        });
        playerMenu.setOnClickListener(v -> {
            haptic(v);
            showPlayerMenu();
        });
        portraitFullscreenButton.setOnClickListener(v -> {
            haptic(v);
            boolean portraitOrientation = getResources().getConfiguration().orientation
                    != Configuration.ORIENTATION_LANDSCAPE;
            if (portraitVideo && portraitOrientation) {
                setPortraitFullscreen(!portraitFullscreen);
            } else {
                setRotatableFullscreen(!rotatableFullscreen);
            }
        });
        updatePortraitFullscreenButton();
        applyPlayerChrome(getResources().getConfiguration().orientation);
        hideVideoControls();

        detailsScroll = new ScrollView(this);
        detailsScroll.setFillViewport(true);
        applyDetailsBackground();
        shell.addView(detailsScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        detailsColumn = new LinearLayout(this);
        detailsColumn.setOrientation(LinearLayout.VERTICAL);
        detailsColumn.setPadding(dp(16), dp(15), dp(16), dp(30));
        detailsScroll.addView(detailsColumn, new ScrollView.LayoutParams(-1, -2));

        titleView = new TextView(this);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(21);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setLineSpacing(0f, 1.08f);
        detailsColumn.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        metaView = new TextView(this);
        metaView.setTextColor(Color.rgb(165, 165, 174));
        metaView.setTextSize(12);
        metaView.setPadding(0, dp(6), 0, dp(9));
        detailsColumn.addView(metaView, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(2), 0, dp(3));
        detailsColumn.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        videoLikeButton = actionIconButton(
                R.drawable.ic_action_heart_outline,
                this::toggleVideoLike,
                "Like this video"
        );
        actions.addView(videoLikeButton, actionParams());
        if (supportsComments()) {
            commentButton = actionIconButton(
                    R.drawable.ic_action_comments,
                    this::openComments,
                    "Open comments"
            );
            actions.addView(commentButton, actionParams());
        }
        watchLaterButton = actionIconButton(
                FavoriteStore.contains(this, pageUrl)
                        ? R.drawable.ic_nav_saved
                        : R.drawable.ic_action_save_outline,
                this::toggleWatchLater,
                "Save to Watch Later"
        );
        actions.addView(watchLaterButton, actionParams());
        actions.addView(actionIconButton(
                R.drawable.ic_action_share,
                this::sharePage,
                "Share video"
        ), actionParams());

        TextView relatedTitle = new TextView(this);
        relatedTitle.setText("Related videos");
        relatedTitle.setTextColor(Color.WHITE);
        relatedTitle.setTextSize(18);
        relatedTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        relatedTitle.setPadding(dp(2), dp(18), dp(2), dp(8));
        detailsColumn.addView(relatedTitle, new LinearLayout.LayoutParams(-1, -2));

        relatedContainer = new LinearLayout(this);
        relatedContainer.setOrientation(LinearLayout.VERTICAL);
        detailsColumn.addView(relatedContainer, new LinearLayout.LayoutParams(-1, -2));

        loading = new ZeroChillProgressBar(this);
        loading.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(52), dp(52));
        lp.gravity = Gravity.CENTER;
        root.addView(loading, lp);

        if (playbackIdent) installShowsLaunchCurtain();

        updateMetadataUi();
        setContentView(root);
    }

    private void installShowsLaunchCurtain() {
        showsLaunchCurtain = new FrameLayout(this);
        showsLaunchCurtain.setBackgroundColor(Color.BLACK);
        showsLaunchCurtain.setClickable(true);
        showsLaunchCurtain.setFocusable(true);
        showsLaunchCurtain.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        );

        showsLaunchLoader = new ShowsPlaybackSplashView(this);
        FrameLayout.LayoutParams mascotParams = new FrameLayout.LayoutParams(
                dp(ShowsPlaybackSplashView.MASCOT_SIZE_DP),
                dp(ShowsPlaybackSplashView.MASCOT_SIZE_DP),
                Gravity.CENTER
        );
        showsLaunchCurtain.addView(showsLaunchLoader, mascotParams);
        root.addView(showsLaunchCurtain, new FrameLayout.LayoutParams(-1, -1));
        showsIdentStartedAt = SystemClock.uptimeMillis();
    }

    private LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(54), dp(60));
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private void applyDetailsBackground() {
        if (detailsScroll == null) return;
        if (!oledEnabled()) {
            detailsScroll.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {Color.rgb(31, 30, 9), Color.rgb(16, 16, 19), Color.rgb(13, 13, 15)}
            ));
            return;
        }
        boolean glow = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("ambient_feed_glow", true);
        if (!glow) {
            detailsScroll.setBackgroundColor(Color.BLACK);
            return;
        }
        detailsScroll.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {Color.rgb(8, 8, 1), Color.rgb(2, 2, 0), Color.BLACK, Color.BLACK}
        ));
    }

    private boolean oledEnabled() {
        return getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("oled_black_enabled", true);
    }

    private void showStartupPoster() {
        if (startupPoster == null) return;
        if (playbackIdent) {
            startupPosterDismissed = true;
            hideStartupPosterNow();
            return;
        }

        startupPosterDismissed = false;
        startupPoster.animate().cancel();
        startupPoster.setAlpha(1f);
        startupPoster.setVisibility(View.VISIBLE);
        try {
            Glide.with(startupPoster).clear(startupPoster);
        } catch (Exception ignored) {
        }
        startupPoster.setImageDrawable(null);

        if (startupPosterLoading != null) {
            startupPosterLoading.animate().cancel();
            startupPosterLoading.setAlpha(1f);
            startupPosterLoading.setVisibility(View.VISIBLE);
        }

        if (posterUrl != null && !posterUrl.isEmpty()) {
            loadImage(startupPoster, posterUrl, pageUrl);
        }
    }

    private void dismissStartupPoster() {
        if (startupPosterDismissed || startupPoster == null) return;
        startupPosterDismissed = true;

        if (!ZeroChillMotion.animationsEnabled(this)) {
            hideStartupPosterNow();
            return;
        }

        if (startupPosterLoading != null) {
            startupPosterLoading.animate().cancel();
            startupPosterLoading.animate()
                    .alpha(0f)
                    .setDuration(100L)
                    .start();
        }

        startupPoster.animate().cancel();
        startupPoster.animate()
                .alpha(0f)
                .setDuration(140L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(this::hideStartupPosterNow)
                .start();
    }

    private void hideStartupPosterNow() {
        if (startupPoster != null) {
            startupPoster.animate().cancel();
            startupPoster.setAlpha(1f);
            startupPoster.setVisibility(View.GONE);
            try {
                Glide.with(startupPoster).clear(startupPoster);
            } catch (Exception ignored) {
            }
            startupPoster.setImageDrawable(null);
        }
        if (startupPosterLoading != null) {
            startupPosterLoading.animate().cancel();
            startupPosterLoading.setAlpha(1f);
            startupPosterLoading.setVisibility(View.GONE);
        }
    }

    private boolean canAnimateRelatedTransition() {
        return ZeroChillMotion.animationsEnabled(this)
                && !isFinishing()
                && shell != null
                && root != null
                && getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE
                && !portraitFullscreen
                && !rotatableFullscreen;
    }

    private void animateRelatedTransition(boolean forward, Runnable swapContent) {
        if (swapContent == null) return;
        if (!canAnimateRelatedTransition()) {
            swapContent.run();
            return;
        }

        relatedTransitionRunning = true;
        shell.animate().cancel();
        float travel = Math.max(dp(92), root.getWidth() * 0.28f);
        float exitX = forward ? -travel : travel;
        float enterX = -exitX;

        shell.animate()
                .translationX(exitX)
                .alpha(0.94f)
                .setDuration(RELATED_SLIDE_OUT_MS)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    swapContent.run();
                    if (shell == null || isFinishing()) {
                        relatedTransitionRunning = false;
                        return;
                    }
                    shell.animate().cancel();
                    shell.setTranslationX(enterX);
                    shell.setAlpha(0.94f);
                    shell.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(RELATED_SLIDE_IN_MS)
                            .setInterpolator(new DecelerateInterpolator())
                            .withEndAction(() -> relatedTransitionRunning = false)
                            .start();
                })
                .start();
    }

    private void playEntranceOnce() {
        if (entrancePlayed || isFinishing()) return;
        entrancePlayed = true;
        if (playerContainer != null) {
            playerContainer.animate().cancel();
            playerContainer.setPivotX(playerContainer.getWidth() * 0.5f);
            playerContainer.setPivotY(0f);
            playerContainer.setScaleX(0.98f);
            playerContainer.setScaleY(0.98f);
            playerContainer.setAlpha(0.72f);
            playerContainer.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(180L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
        if (detailsScroll != null) {
            detailsScroll.animate().cancel();
            detailsScroll.setAlpha(0.35f);
            detailsScroll.setTranslationY(dp(6));
            detailsScroll.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(25L)
                    .setDuration(190L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private void finishSwipeMinimize() {
        minimizing = true;
        updateSwipeEnabled();
        savePlaybackState(false);
        haptic(playerContainer);

        playerContainer.animate().cancel();
        if (detailsScroll != null) detailsScroll.animate().cancel();

        if (detailsScroll != null) {
            detailsScroll.animate()
                    .alpha(0f)
                    .translationY(dp(10))
                    .setDuration(90L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        playerContainer.animate()
                .scaleX(0.94f)
                .scaleY(0.94f)
                .translationY(dp(28))
                .alpha(0f)
                .setDuration(110L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(this::minimizeToFeed)
                .start();
    }

    private void restoreFromSwipe() {
        playerContainer.animate().cancel();
        playerContainer.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .alpha(1f)
                .setDuration(150L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(this::showVideoControls)
                .start();
        if (detailsScroll != null) {
            detailsScroll.animate().cancel();
            detailsScroll.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(150L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private TextView actionIconButton(int icon, Runnable action, String description) {
        TextView button = new TextView(this);
        button.setText(" ");
        button.setTextColor(Color.TRANSPARENT);
        button.setTextSize(10);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setPadding(dp(6), dp(5), dp(6), dp(5));
        button.setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0);
        button.setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
        button.setCompoundDrawablePadding(dp(1));
        button.setContentDescription(description);
        button.setClickable(true);
        button.setFocusable(true);
        button.setMinWidth(dp(48));
        button.setMinHeight(dp(48));
        ZeroChillMotion.installPressFeedback(button);
        button.setOnClickListener(v -> {
            haptic(v);
            action.run();
        });
        return button;
    }

    private GradientDrawable actionPill() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(oledEnabled() ? Color.rgb(8, 8, 10) : Color.rgb(27, 27, 32));
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), oledEnabled() ? Color.rgb(30, 30, 34) : Color.rgb(55, 55, 63));
        return bg;
    }

    private void buildPlayer(long startPosition) {
        portraitVideo = false;
        if (portraitFullscreen) setPortraitFullscreen(false);
        else updatePortraitFullscreenButton();
        releasePlayer();
        failureShown = false;
        showStartupPoster();

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory();
        if (!userAgent.isEmpty()) httpFactory.setUserAgent(userAgent);

        Map<String, String> headers = new LinkedHashMap<>();
        if (!mediaReferer.isEmpty()) {
            headers.put("Referer", mediaReferer);
            try {
                Uri page = Uri.parse(mediaReferer);
                if (page.getScheme() != null && page.getHost() != null) {
                    headers.put("Origin", page.getScheme() + "://" + page.getHost());
                }
            } catch (Exception ignored) {
            }
        }
        if (!cookies.isEmpty()) headers.put("Cookie", cookies);
        if (!headers.isEmpty()) httpFactory.setDefaultRequestProperties(headers);

        String scheme = Uri.parse(mediaUrl).getScheme();
        DefaultMediaSourceFactory sourceFactory = new DefaultMediaSourceFactory(this)
                .setDataSourceFactory("content".equalsIgnoreCase(scheme)
                        || "file".equalsIgnoreCase(scheme)
                        ? new DefaultDataSource.Factory(this, httpFactory) : httpFactory);
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(sourceFactory)
                .setSeekBackIncrementMs(10_000L)
                .setSeekForwardIncrementMs(10_000L)
                .build();
        playerView.setPlayer(player);
        if (fullscreenControls != null) {
            fullscreenOverlay.setPlayer(player);
            fullscreenControls.bind(player);
        }

        MediaItem.Builder item = new MediaItem.Builder().setUri(mediaUrl);
        String lower = mediaUrl.toLowerCase();
        if (lower.contains(".m3u8")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (lower.contains(".mpd")) item.setMimeType(MimeTypes.APPLICATION_MPD);
        player.setMediaItem(item.build());
        playbackRecovery.bind(player, pageUrl);

        long position = startPosition;
        if (position < 0L && rememberPositionEnabled()) {
            position = getSharedPreferences("player_positions", MODE_PRIVATE)
                    .getLong(positionKey(), 0L);
        }
        if (position > 0L) player.seekTo(position);
        player.setPlayWhenReady(true);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    RatingFeedbackPrompt.recordSuccessfulPlayback(VideoDetailActivity.this, mediaUrl);
                } else if (playbackState == Player.STATE_ENDED) {
                    savePlaybackState(true);
                }
            }

            @Override
            public void onRenderedFirstFrame() {
                if (playbackIdent) {
                    showsFirstFrameRendered = true;
                    hideStartupPosterNow();
                    maybeDismissShowsLaunchCurtain();
                } else {
                    dismissStartupPoster();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                dismissStartupPoster();
                if (!recoverPlayback(error)) showPlaybackFailure();
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                if (videoSize == null || videoSize.width <= 0 || videoSize.height <= 0) return;
                boolean isPortrait = isPortraitVideoSize(
                        videoSize.width,
                        videoSize.height,
                        videoSize.pixelWidthHeightRatio
                );
                if (showsOrigin) {
                    portraitVideo = isPortrait;
                    showsTargetOrientation = isPortrait
                            ? Configuration.ORIENTATION_PORTRAIT
                            : Configuration.ORIENTATION_LANDSCAPE;
                    showsOrientationSettled = orientationMatches(
                            getResources().getConfiguration().orientation,
                            showsTargetOrientation
                    );
                    if (showsPortraitOrientation == null ||
                            showsPortraitOrientation.booleanValue() != isPortrait) {
                        showsPortraitOrientation = isPortrait;
                        PhoneOrientationPolicy.enterShowsFullscreen(
                                VideoDetailActivity.this,
                                isPortrait
                        );
                    }
                    applyOrientation(getResources().getConfiguration().orientation);
                    maybeDismissShowsLaunchCurtain();
                    return;
                }
                if (portraitVideo == isPortrait) return;
                portraitVideo = isPortrait;
                if (portraitVideo) {
                    if (sensorFullscreen || rotatableFullscreen) {
                        sensorFullscreen = false;
                        rotatableFullscreen = false;
                        portraitFullscreen = false;
                        PhoneOrientationPolicy.exitFullscreenVideo(VideoDetailActivity.this);
                        applyOrientation(getResources().getConfiguration().orientation);
                    } else {
                        PhoneOrientationPolicy.applyBrowsingOrientation(VideoDetailActivity.this);
                    }
                    updatePortraitFullscreenButton();
                } else if (portraitFullscreen) {
                    setPortraitFullscreen(false);
                } else {
                    updatePortraitFullscreenButton();
                }
            }
        });
        player.prepare();
    }

    static boolean isPortraitVideoSize(int width, int height, float pixelWidthHeightRatio) {
        if (width <= 0 || height <= 0) return false;
        float ratio = pixelWidthHeightRatio > 0f ? pixelWidthHeightRatio : 1f;
        return height > (width * ratio);
    }

    static boolean orientationMatches(int currentOrientation, int targetOrientation) {
        return targetOrientation != Configuration.ORIENTATION_UNDEFINED &&
                currentOrientation == targetOrientation;
    }

    static float portraitProgressFraction(long positionMs, long durationMs) {
        if (durationMs <= 0L || positionMs <= 0L) return 0f;
        return Math.max(0f, Math.min(1f, positionMs / (float) durationMs));
    }

    static long portraitSeekPosition(int progress, int max, long durationMs) {
        if (max <= 0 || durationMs <= 0L) return 0L;
        int clamped = Math.max(0, Math.min(max, progress));
        return Math.round(durationMs * (clamped / (double) max));
    }

    private boolean canShowPortraitSeekBar() {
        return !showsOrigin
                && getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_LANDSCAPE
                && !portraitFullscreen
                && !rotatableFullscreen;
    }

    private void showPortraitSeekBar() {
        if (portraitSeekBar == null || !canShowPortraitSeekBar()) return;
        portraitSeekBar.removeCallbacks(hidePortraitSeekBar);
        portraitSeekBar.animate().cancel();
        portraitSeekBar.setVisibility(View.VISIBLE);
        portraitSeekBar.animate().alpha(1f).setDuration(120L).start();
        schedulePortraitSeekBarHide();
    }

    private void schedulePortraitSeekBarHide() {
        if (portraitSeekBar == null || portraitSeekScrubbing || !canShowPortraitSeekBar()) return;
        portraitSeekBar.removeCallbacks(hidePortraitSeekBar);
        portraitSeekBar.postDelayed(hidePortraitSeekBar, CONTROL_TIMEOUT_MS);
    }

    private void startPortraitProgressTicker() {
        if (portraitSeekBar == null) return;
        portraitSeekBar.removeCallbacks(portraitProgressTicker);
        updatePortraitProgress();
        portraitSeekBar.postDelayed(portraitProgressTicker, 350L);
    }

    private void stopPortraitProgressTicker() {
        if (portraitSeekBar != null) {
            portraitSeekBar.removeCallbacks(portraitProgressTicker);
            portraitSeekBar.removeCallbacks(hidePortraitSeekBar);
        }
    }

    private void updatePortraitProgress() {
        if (portraitSeekBar == null) return;
        if (!canShowPortraitSeekBar()) {
            portraitSeekBar.animate().cancel();
            portraitSeekBar.setVisibility(View.GONE);
            return;
        }
        if (portraitSeekBar.getVisibility() == View.GONE) {
            portraitSeekBar.setVisibility(View.INVISIBLE);
            portraitSeekBar.setAlpha(0f);
        }
        if (portraitSeekScrubbing) return;

        long position = player == null ? 0L : Math.max(0L, player.getCurrentPosition());
        long duration = player == null ? 0L : Math.max(0L, player.getDuration());
        int progress = Math.round(
                portraitProgressFraction(position, duration) * portraitSeekBar.getMax()
        );
        portraitSeekBar.setProgress(progress);
    }

    private void updateMetadataUi() {
        if (titleView != null) titleView.setText(title);
        if (playerTitleView != null) playerTitleView.setText(title);
        if (fullscreenOverlay != null) {
            ((TextView) fullscreenOverlay.findViewById(R.id.player_title)).setText(title);
        }
        if (metaView != null) {
            ArrayList<String> parts = new ArrayList<>();
            if (!views.isEmpty()) parts.add(views + " views");
            if (!uploader.isEmpty()) parts.add(uploader);
            metaView.setText(TextUtils.join("  •  ", parts));
        }
        refreshVideoLikeState(false);
        refreshCommentCount(false);
        updateWatchLaterButton();
    }

    private void loadRelated() {
        if (showsOrigin || relatedContainer == null) return;
        final int requestGeneration = ++relatedLoadGeneration;
        relatedContainer.removeAllViews();
        TextView loadingText = new TextView(this);
        loadingText.setText("Loading related videos…");
        loadingText.setTextColor(Color.rgb(155, 155, 164));
        loadingText.setTextSize(13);
        loadingText.setPadding(dp(4), dp(10), dp(4), dp(18));
        relatedContainer.addView(loadingText);

        final String excludeUrl = pageUrl;
        io.execute(() -> {
            LinkedHashMap<String, NativeContentItem> merged = new LinkedHashMap<>();
            if (isBunkr()) {
                try {
                    if (!relatedFeedUrl.isEmpty()) {
                        List<NativeContentItem> album = bunkrRepository.fetchAlbum(this, relatedFeedUrl, 1);
                        for (NativeContentItem item : album) {
                            if (item.isVideo() && !item.url.equals(excludeUrl)) {
                                merged.put(item.url, item);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            } else if (isEfukt()) {
                try {
                    String feed = relatedFeedUrl.isEmpty() ? EfuktRepository.SERIES : relatedFeedUrl;
                    List<NativeContentItem> series = efuktRepository.fetchSeriesFeed(this, feed, 1);
                    for (NativeContentItem item : series) {
                        if (!item.url.equals(excludeUrl)) merged.put(item.url, item);
                    }
                } catch (Exception ignored) {
                }
            } else if (isKaotic()) {
                try {
                    List<NativeContentItem> kaotic = webVideoSourceRepository.fetchFeed(
                            this,
                            WebVideoSourceRepository.Source.KAOTIC,
                            1
                    );
                    for (NativeContentItem item : kaotic) {
                        if (item != null && !item.isSection() && !item.url.equals(excludeUrl)) {
                            merged.put(item.url, item);
                        }
                    }
                } catch (Exception ignored) {
                }
            } else if (isBaddieHub()) {
                try {
                    List<NativeContentItem> baddie = baddieHubRepository.fetchRelated(this, excludeUrl);
                    for (NativeContentItem item : baddie) {
                        if (item != null && !item.isSection() && !item.url.equals(excludeUrl)) {
                            merged.put(item.url, item);
                        }
                    }
                } catch (Exception ignored) {
                }
            } else {
                try {
                    List<NativeContentItem> home = repository.fetchFeed(this, CrazyShitRepository.HOME, 1);
                    for (NativeContentItem item : home) {
                        if (!item.url.equals(excludeUrl)) merged.put(item.url, item);
                    }
                } catch (Exception ignored) {
                }
                try {
                    List<NativeContentItem> trending = repository.fetchFeed(this, CrazyShitRepository.TRENDING, 1);
                    for (NativeContentItem item : trending) {
                        if (!item.url.equals(excludeUrl)) merged.putIfAbsent(item.url, item);
                    }
                } catch (Exception ignored) {
                }
            }
            ArrayList<NativeContentItem> result = new ArrayList<>();
            for (NativeContentItem item : merged.values()) {
                result.add(item);
                if (result.size() >= 12) break;
            }
            runOnUiThread(() -> {
                if (isFinishing() || requestGeneration != relatedLoadGeneration) return;
                if (!excludeUrl.equals(pageUrl)) return;
                renderRelated(result);
            });
        });
    }

    void renderContextRelated(List<NativeContentItem> items) {
        if (showsOrigin || isFinishing() || items == null || items.isEmpty()) return;
        relatedLoadGeneration++;
        renderRelated(items);
    }

    private void renderRelated(List<NativeContentItem> items) {
        if (relatedContainer == null) return;
        relatedContainer.removeAllViews();
        clearRelatedImageTargets();
        if (items == null || items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No related videos could be loaded right now.");
            empty.setTextColor(Color.rgb(155, 155, 164));
            empty.setTextSize(13);
            empty.setPadding(dp(4), dp(10), dp(4), dp(18));
            relatedContainer.addView(empty);
            return;
        }
        for (NativeContentItem item : items) {
            relatedContainer.addView(buildRelatedCard(item), marginParams(0, dp(8)));
        }
    }

    private View buildRelatedCard(NativeContentItem item) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(oledEnabled() ? Color.rgb(9, 9, 11) : Color.rgb(23, 23, 27));
        card.setStrokeColor(oledEnabled() ? Color.rgb(29, 29, 33) : Color.rgb(49, 49, 57));
        card.setStrokeWidth(dp(1));
        card.setRadius(dp(18));
        card.setCardElevation(0f);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            haptic(v);
            playRelated(item);
        });

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row, new MaterialCardView.LayoutParams(-1, -2));

        FrameLayout visual = new FrameLayout(this);
        row.addView(visual, new LinearLayout.LayoutParams(dp(146), dp(92)));
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(18, 18, 21));
        visual.addView(image, new FrameLayout.LayoutParams(-1, -1));
        TextView play = new TextView(this);
        play.setText("▶");
        play.setTextColor(Color.WHITE);
        play.setTextSize(18);
        play.setGravity(Gravity.CENTER);
        play.setBackground(new ColorDrawable(Color.argb(120, 0, 0, 0)));
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(38), dp(38));
        pp.gravity = Gravity.CENTER;
        visual.addView(play, pp);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(11), dp(9), dp(10), dp(9));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView name = new TextView(this);
        name.setText(item.title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(14);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(name);

        ArrayList<String> info = new ArrayList<>();
        if (!clean(item.views).isEmpty()) info.add(item.views + " views");
        if (!clean(item.uploader).isEmpty()) info.add(item.uploader);
        TextView meta = new TextView(this);
        meta.setText(TextUtils.join("  •  ", info));
        meta.setTextColor(Color.rgb(160, 160, 170));
        meta.setTextSize(11);
        meta.setMaxLines(1);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        meta.setPadding(0, dp(5), 0, 0);
        copy.addView(meta);

        relatedImages.put(item.url, image);
        String resolvedThumbnail = resolvedRelatedThumbnails.get(item.url);
        if (!clean(resolvedThumbnail).isEmpty()) {
            loadImage(image, resolvedThumbnail, item.url);
        } else if (!clean(item.imageUrl).isEmpty()) {
            loadImage(image, item.imageUrl, item.url);
        }
        requestRelatedThumbnail(item.url);
        return card;
    }

    private void requestRelatedThumbnail(String page) {
        if (page == null || page.isEmpty() || thumbnailResolvers == null || thumbnailResolvers.length == 0) {
            return;
        }
        if (resolvedRelatedThumbnails.containsKey(page)) return;
        if (!requestedRelatedThumbnails.add(page)) return;
        RenderedThumbnailResolver resolver =
                thumbnailResolvers[thumbnailResolverCursor++ % thumbnailResolvers.length];
        resolver.request(page);
    }

    private void onThumbnailResolved(String page, String imageUrl) {
        if (page == null || page.isEmpty()) return;
        if (imageUrl == null || imageUrl.isEmpty()) {
            requestedRelatedThumbnails.remove(page);
            return;
        }
        resolvedRelatedThumbnails.put(page, imageUrl);
        ImageView target = relatedImages.get(page);
        if (target == null) return;
        loadImage(target, imageUrl, page);
    }

    private void clearRelatedImageTargets() {
        for (ImageView image : relatedImages.values()) {
            if (image == null) continue;
            try {
                Glide.with(image).clear(image);
            } catch (Exception ignored) {
            }
        }
        relatedImages.clear();
    }

    private void loadImage(ImageView view, String imageUrl, String referer) {
        Object source = imageUrl.startsWith("file://") ? imageUrl : withHeaders(imageUrl, referer);
        try {
            Glide.with(view)
                    .load(source)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .dontAnimate()
                    .transition(view == startupPoster
                            ? new com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions().dontTransition()
                            : ThumbnailFades.thumbnail())
                    .placeholder(new ColorDrawable(Color.rgb(18, 18, 21)))
                    .error(new ColorDrawable(Color.rgb(18, 18, 21)))
                    .into(view);
        } catch (Exception ignored) {
        }
    }

    private GlideUrl withHeaders(String imageUrl, String referer) {
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", THUMB_UA)
                .addHeader("Referer", referer == null || referer.isEmpty() ? SITE : referer)
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        try {
            String c = CookieManager.getInstance().getCookie(imageUrl);
            if ((c == null || c.isEmpty()) && referer != null) c = CookieManager.getInstance().getCookie(referer);
            if (c != null && !c.isEmpty()) headers.addHeader("Cookie", c);
        } catch (Exception ignored) {
        }
        return new GlideUrl(imageUrl, headers.build());
    }

    private void playRelated(NativeContentItem item) {
        if (item == null || item.url == null || item.url.isEmpty() || relatedTransitionRunning) return;
        loading.setVisibility(View.VISIBLE);
        final int requestGeneration = ++relatedPlayGeneration;
        io.execute(() -> {
            CrazyShitRepository.StreamInfo stream = null;
            try {
                stream = PlayableSourceRouter.resolve(this, item.url);
            } catch (Exception ignored) {
            }
            CrazyShitRepository.StreamInfo resolved = stream;
            runOnUiThread(() -> {
                if (isFinishing() || requestGeneration != relatedPlayGeneration) return;
                loading.setVisibility(View.GONE);
                if (resolved == null || resolved.mediaUrl == null || resolved.mediaUrl.isEmpty()) {
                    ZeroChillToast.makeText(
                            this,
                            "Couldn't resolve that related video natively right now.",
                            ZeroChillToast.LENGTH_SHORT
                    ).show();
                    return;
                }
                pushCurrentVideo();
                savePlaybackState(false);
                Runnable swap = () -> {
                    mediaUrl = resolved.mediaUrl;
                    pageUrl = item.url;
                    mediaReferer = resolved.requestReferer;
                    String resolvedPoster = resolvedRelatedThumbnails.get(item.url);
                    posterUrl = clean(resolvedPoster).isEmpty() ? clean(item.imageUrl) : clean(resolvedPoster);
                    title = clean(item.title).isEmpty() ? resolved.title : item.title;
                    views = clean(item.views);
                    uploader = clean(item.uploader);
                    comments = clean(item.comments);
                    userAgent = defaultUserAgent();
                    cookies = cookiesFor(mediaUrl, pageUrl);
                    requestedStartPosition = -1L;
                    updateMetadataUi();
                    buildPlayer(-1L);
                    if (detailsScroll != null) detailsScroll.smoothScrollTo(0, 0);
                    loadRelated();
                };
                animateRelatedTransition(true, swap);
            });
        });
    }

    private void pushCurrentVideo() {
        if (mediaUrl == null || mediaUrl.isEmpty()) return;
        long position = player == null ? 0L : Math.max(0L, player.getCurrentPosition());
        relatedHistory.addLast(new VideoHistoryEntry(
                mediaUrl,
                pageUrl,
                title,
                views,
                uploader,
                comments,
                userAgent,
                cookies,
                mediaReferer,
                posterUrl,
                position,
                capturePlayerFrame()
        ));
        while (relatedHistory.size() > RELATED_HISTORY_LIMIT) {
            recycleHistoryPreview(relatedHistory.removeFirst());
        }
    }

    private boolean restorePreviousRelatedVideo(boolean animate) {
        if (relatedTransitionRunning || relatedHistory.isEmpty()) return false;
        if (animate && canAnimateRelatedTransition()) {
            animateRelatedTransition(false, this::restorePreviousRelatedVideoNow);
            return true;
        }
        return restorePreviousRelatedVideoNow();
    }

    private boolean restorePreviousRelatedVideoNow() {
        VideoHistoryEntry previous = relatedHistory.pollLast();
        if (previous == null) return false;

        relatedPlayGeneration++;
        savePlaybackState(false);
        loading.setVisibility(View.GONE);
        mediaUrl = previous.mediaUrl;
        pageUrl = previous.pageUrl;
        title = previous.title;
        views = previous.views;
        uploader = previous.uploader;
        comments = previous.comments;
        userAgent = previous.userAgent;
        cookies = previous.cookies;
        mediaReferer = previous.mediaReferer;
        posterUrl = previous.posterUrl;
        requestedStartPosition = previous.positionMs;
        updateMetadataUi();
        buildPlayer(previous.positionMs);
        if (detailsScroll != null) detailsScroll.smoothScrollTo(0, 0);
        loadRelated();
        recycleHistoryPreview(previous);
        return true;
    }

    private Bitmap capturePlayerFrame() {
        TextureView texture = findTextureView(playerView);
        if (texture == null || !texture.isAvailable() || texture.getWidth() <= 0 || texture.getHeight() <= 0) {
            return null;
        }
        int targetWidth = Math.min(texture.getWidth(), 480);
        int targetHeight = Math.max(1, Math.round(texture.getHeight() * (targetWidth / (float) texture.getWidth())));
        Bitmap frame = null;
        try {
            frame = texture.getBitmap(targetWidth, targetHeight);
            if (frame == null) return null;
            Bitmap compact = frame.copy(Bitmap.Config.RGB_565, false);
            if (compact == null) return frame;
            frame.recycle();
            return compact;
        } catch (Throwable ignored) {
            if (frame != null && !frame.isRecycled()) frame.recycle();
            return null;
        }
    }

    private TextureView findTextureView(View candidate) {
        if (candidate instanceof TextureView) return (TextureView) candidate;
        if (!(candidate instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) candidate;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextureView texture = findTextureView(group.getChildAt(i));
            if (texture != null) return texture;
        }
        return null;
    }

    private void recycleHistoryPreview(VideoHistoryEntry entry) {
        if (entry == null || entry.previewBitmap == null || entry.previewBitmap.isRecycled()) return;
        entry.previewBitmap.recycle();
    }

    boolean canPreviewRelatedBack() {
        return !minimizing
                && !portraitFullscreen
                && root != null
                && shell != null
                && getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE
                && !relatedHistory.isEmpty();
    }

    boolean startRelatedBackPreview(boolean fromRight) {
        if (!canPreviewRelatedBack()) return false;
        VideoHistoryEntry previous = relatedHistory.peekLast();
        if (previous == null) return false;

        clearRelatedBackPreviewLayer();
        resetRelatedBackForeground();
        relatedBackPreviewEntry = previous;
        relatedBackDirection = fromRight ? -1f : 1f;

        FrameLayout preview = new FrameLayout(this);
        preview.setBackgroundColor(oledEnabled() ? Color.BLACK : Color.rgb(13, 13, 15));
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        FrameLayout mediaPreview = new FrameLayout(this);
        mediaPreview.setBackgroundColor(Color.BLACK);
        FrameLayout.LayoutParams mediaParams = new FrameLayout.LayoutParams(-1, portraitPlayerHeight());
        mediaParams.topMargin = shell.getPaddingTop();
        preview.addView(mediaPreview, mediaParams);

        Bitmap bitmap = previous.previewBitmap;
        if (bitmap != null && !bitmap.isRecycled()) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setImageBitmap(bitmap);
            mediaPreview.addView(image, new FrameLayout.LayoutParams(-1, -1));
            relatedBackPreviewImage = image;
        }

        LinearLayout destination = new LinearLayout(this);
        destination.setOrientation(LinearLayout.VERTICAL);
        destination.setPadding(dp(16), dp(15), dp(16), dp(30));
        FrameLayout.LayoutParams destinationParams = new FrameLayout.LayoutParams(-1, -1);
        destinationParams.topMargin = shell.getPaddingTop() + portraitPlayerHeight();
        preview.addView(destination, destinationParams);

        TextView destinationTitle = new TextView(this);
        destinationTitle.setText(previous.title == null || previous.title.isEmpty() ? "Previous video" : previous.title);
        destinationTitle.setTextColor(Color.WHITE);
        destinationTitle.setTextSize(21);
        destinationTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        destination.addView(destinationTitle, new LinearLayout.LayoutParams(-1, -2));

        TextView destinationMeta = new TextView(this);
        StringBuilder previewMeta = new StringBuilder();
        if (previous.uploader != null && !previous.uploader.isEmpty()) previewMeta.append(previous.uploader);
        if (previous.views != null && !previous.views.isEmpty()) {
            if (previewMeta.length() > 0) previewMeta.append("  •  ");
            previewMeta.append(previous.views).append(" views");
        }
        destinationMeta.setText(previewMeta);
        destinationMeta.setTextColor(Color.rgb(165, 165, 174));
        destinationMeta.setTextSize(12);
        destinationMeta.setPadding(0, dp(6), 0, dp(18));
        destination.addView(destinationMeta, new LinearLayout.LayoutParams(-1, -2));

        TextView destinationRelated = new TextView(this);
        destinationRelated.setText("Related videos");
        destinationRelated.setTextColor(Color.WHITE);
        destinationRelated.setTextSize(18);
        destinationRelated.setTypeface(null, android.graphics.Typeface.BOLD);
        destination.addView(destinationRelated, new LinearLayout.LayoutParams(-1, -2));

        preview.setAlpha(0.45f);
        preview.setScaleX(0.985f);
        preview.setScaleY(0.985f);
        preview.setTranslationX(-relatedBackDirection * dp(18));
        root.addView(preview, 0, new FrameLayout.LayoutParams(-1, -1));
        relatedBackPreviewLayer = preview;
        return true;
    }

    void updateRelatedBackPreview(float progress, boolean fromRight) {
        if (relatedBackPreviewLayer == null || relatedBackPreviewEntry == null) return;
        float p = Math.max(0f, Math.min(1f, progress));
        float eased = 1f - (1f - p) * (1f - p);
        relatedBackDirection = fromRight ? -1f : 1f;

        shell.animate().cancel();
        shell.setPivotX(fromRight ? shell.getWidth() : 0f);
        shell.setPivotY(shell.getHeight() * 0.5f);
        float travel = Math.max(dp(48), root.getWidth() * 0.24f);
        shell.setTranslationX(relatedBackDirection * travel * eased);
        float foregroundScale = 1f - (0.04f * eased);
        shell.setScaleX(foregroundScale);
        shell.setScaleY(foregroundScale);
        shell.setAlpha(1f - (0.05f * eased));

        relatedBackPreviewLayer.animate().cancel();
        relatedBackPreviewLayer.setAlpha(0.45f + (0.55f * eased));
        float previewScale = 0.985f + (0.015f * eased);
        relatedBackPreviewLayer.setScaleX(previewScale);
        relatedBackPreviewLayer.setScaleY(previewScale);
        relatedBackPreviewLayer.setTranslationX(-relatedBackDirection * dp(18) * (1f - eased));
    }

    void cancelRelatedBackPreview() {
        if (shell != null) {
            shell.animate().cancel();
            shell.animate()
                    .translationX(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(150L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        FrameLayout preview = relatedBackPreviewLayer;
        relatedBackPreviewEntry = null;
        if (preview == null) return;
        preview.animate().cancel();
        preview.animate()
                .alpha(0f)
                .translationX(-relatedBackDirection * dp(18))
                .setDuration(130L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (relatedBackPreviewLayer == preview) clearRelatedBackPreviewLayer();
                })
                .start();
    }

    void commitRelatedBackPreview() {
        VideoHistoryEntry expected = relatedBackPreviewEntry;
        if (expected == null || relatedHistory.peekLast() != expected || shell == null || root == null) {
            abortRelatedBackPreview();
            handleBack();
            return;
        }

        relatedBackPreviewEntry = null;
        shell.animate().cancel();
        shell.animate()
                .translationX(relatedBackDirection * Math.max(dp(64), root.getWidth() * 0.34f))
                .scaleX(0.955f)
                .scaleY(0.955f)
                .alpha(0.9f)
                .setDuration(90L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    clearRelatedBackPreviewLayer();
                    boolean restored = restorePreviousRelatedVideo(false);
                    resetRelatedBackForeground();
                    if (!restored) handleBack();
                })
                .start();
    }

    void abortRelatedBackPreview() {
        relatedBackPreviewEntry = null;
        clearRelatedBackPreviewLayer();
        resetRelatedBackForeground();
    }

    private void clearRelatedBackPreviewLayer() {
        FrameLayout preview = relatedBackPreviewLayer;
        if (preview != null) {
            preview.animate().cancel();
            if (relatedBackPreviewImage != null) relatedBackPreviewImage.setImageDrawable(null);
            if (root != null) root.removeView(preview);
        }
        relatedBackPreviewLayer = null;
        relatedBackPreviewImage = null;
    }

    private void resetRelatedBackForeground() {
        if (shell == null) return;
        shell.animate().cancel();
        shell.setTranslationX(0f);
        shell.setScaleX(1f);
        shell.setScaleY(1f);
        shell.setAlpha(1f);
    }

    private void openComments() {
        if (pageUrl.isEmpty() || !supportsComments()) return;
        SocialContentContextStore.remember(this, new NativeContentItem(NativeContentItem.KIND_MEDIA,
                title, pageUrl, posterUrl, views, uploader, comments));
        if (activeComments != null && activeComments.isShowing()) return;
        showCommentSheet(socialEntry ? socialFocusCommentId : "", socialEntry && socialAutoReply);
        socialFocusCommentId = "";
        socialAutoReply = false;
    }

    private void showCommentSheet(String focusId, boolean reply) {
        final InlineCommentsDialog[] opened = new InlineCommentsDialog[1];
        opened[0] = new InlineCommentsDialog(this, pageUrl, title, comments, focusId, reply,
                new InlineCommentsDialog.ResizeListener() {
                    @Override public void onSheetTopChanged(int top) { }
                    @Override public void onSheetClosed() {
                        if (activeComments == opened[0]) activeComments = null;
                        if (!isFinishing() && !isDestroyed()) refreshCommentCount(true);
                    }
                });
        activeComments = opened[0];
        activeComments.show();
    }

    boolean openSocialCommentsIfCurrent(String targetPageUrl, String commentId, boolean reply) {
        if (!ZeroChillSocialRepository.contentKey(targetPageUrl).equals(ZeroChillSocialRepository.contentKey(pageUrl)) || !supportsComments()) return false;
        if (activeComments != null && activeComments.isShowing()) activeComments.dismiss();
        showCommentSheet(commentId, reply);
        return true;
    }

    void prepareForSocialNavigation() {
        socialNavigationPendingReturn = true;
        socialReturnPlayWhenReady = player != null && player.getPlayWhenReady();
        if (player != null) player.pause();
    }

    private void refreshVideoLikeState(boolean force) {
        if (videoLikeButton == null) return;
        String target = pageUrl == null ? "" : pageUrl.trim();
        if (target.isEmpty()) {
            videoLikeButton.setEnabled(false);
            videoLikeButton.setText(" ");
            videoLikeButton.setTextColor(Color.TRANSPARENT);
            videoLikeButton.setCompoundDrawablesWithIntrinsicBounds(
                    0, R.drawable.ic_action_heart_outline, 0, 0
            );
            videoLikeButton.setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
            return;
        }
        if (!force && target.equals(videoLikePageUrl)) return;

        videoLikePageUrl = target;
        int generation = ++videoLikeRequestGeneration;
        ZeroChillSocialRepository.videoLikeState(this, target, (state, error) ->
                runOnUiThread(() -> {
                    if (generation != videoLikeRequestGeneration || !target.equals(pageUrl)) return;
                    if (error != null || state == null) return;
                    videoLiked = state.liked;
                    videoLikeCount = state.count;
                    updateVideoLikeButton();
                })
        );
    }

    private void updateVideoLikeButton() {
        if (videoLikeButton != null) {
            videoLikeButton.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    videoLiked ? R.drawable.ic_action_heart_filled : R.drawable.ic_action_heart_outline,
                    0,
                    0
            );
            videoLikeButton.setCompoundDrawableTintList(ColorStateList.valueOf(
                    videoLiked ? UiPalette.PRIMARY : Color.WHITE
            ));
            boolean showCount = videoLikeCount > 0;
            videoLikeButton.setText(showCount ? String.valueOf(videoLikeCount) : " ");
            videoLikeButton.setTextColor(showCount
                    ? (videoLiked ? UiPalette.PRIMARY : Color.rgb(238, 238, 242))
                    : Color.TRANSPARENT);
            videoLikeButton.setEnabled(true);
            videoLikeButton.setContentDescription(
                    videoLiked ? "Unlike this video" : "Like this video"
            );
        }
        if (fullscreenControls != null) fullscreenControls.syncLiked(videoLiked, true);
    }

    private void setVideoLikeEnabled(boolean enabled) {
        if (videoLikeButton != null) videoLikeButton.setEnabled(enabled);
        if (fullscreenControls != null) fullscreenControls.syncLiked(videoLiked, enabled);
    }

    private void refreshCommentCount(boolean force) {
        if (commentButton == null) return;
        String target = pageUrl == null ? "" : pageUrl.trim();
        if (target.isEmpty()) {
            commentButton.setText(" ");
            commentButton.setTextColor(Color.TRANSPARENT);
            commentButton.setEnabled(false);
            return;
        }
        if (!force && target.equals(commentCountPageUrl)) return;

        commentCountPageUrl = target;
        commentButton.setText("");
        commentButton.setEnabled(true);
        int generation = ++commentCountRequestGeneration;
        ZeroChillSocialRepository.loadComments(this, target, (loaded, error) ->
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (generation != commentCountRequestGeneration || !target.equals(pageUrl)) return;
                    if (error != null || loaded == null) return;
                    int visible = 0;
                    for (ZeroChillSocialRepository.Comment comment : loaded) {
                        if (comment != null && !comment.deleted()) visible++;
                    }
                    commentCount = visible;
                    updateCommentButton();
                })
        );
    }

    private void updateCommentButton() {
        if (commentButton == null) return;
        commentButton.setCompoundDrawablesWithIntrinsicBounds(
                0, R.drawable.ic_action_comments, 0, 0
        );
        commentButton.setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
        commentButton.setText(String.valueOf(Math.max(0, commentCount)));
        commentButton.setTextColor(Color.rgb(238, 238, 242));
        commentButton.setEnabled(true);
        commentButton.setContentDescription(
                commentCount == 1 ? "Open 1 comment" : "Open " + commentCount + " comments"
        );
    }

    private void updateWatchLaterButton() {
        if (watchLaterButton == null) return;
        boolean saved = pageUrl != null && !pageUrl.isEmpty() && FavoriteStore.contains(this, pageUrl);
        watchLaterButton.setCompoundDrawablesWithIntrinsicBounds(
                0,
                saved ? R.drawable.ic_nav_saved : R.drawable.ic_action_save_outline,
                0,
                0
        );
        watchLaterButton.setCompoundDrawableTintList(ColorStateList.valueOf(
                saved ? UiPalette.PRIMARY : Color.WHITE
        ));
        watchLaterButton.setText(" ");
        watchLaterButton.setTextColor(Color.TRANSPARENT);
        watchLaterButton.setContentDescription(
                saved ? "Remove from Watch Later" : "Save to Watch Later"
        );
    }

    private void toggleVideoLike() {
        if (pageUrl == null || pageUrl.trim().isEmpty()) return;
        if (!ZeroChillAccountRepository.hasStoredSession(this)) {
            startActivity(new Intent(this, ZeroChillAccountActivity.class));
            return;
        }

        String target = pageUrl;
        boolean wasLiked = videoLiked;
        setVideoLikeEnabled(false);
        ZeroChillSocialRepository.toggleVideoLike(this, target, wasLiked, (state, error) ->
                runOnUiThread(() -> {
                    if (!target.equals(pageUrl)) return;
                    setVideoLikeEnabled(true);
                    if (error != null || state == null) {
                        ZeroChillToast.makeText(
                                this,
                                error == null ? "Unable to update the like." : error.getMessage(),
                                ZeroChillToast.LENGTH_LONG
                        ).show();
                        return;
                    }
                    videoLikePageUrl = target;
                    videoLiked = state.liked;
                    videoLikeCount = state.count;
                    updateVideoLikeButton();
                })
        );
    }

    private void toggleWatchLater() {
        if (pageUrl.isEmpty()) return;
        if (FavoriteStore.contains(this, pageUrl)) {
            FavoriteStore.remove(this, pageUrl);
            ZeroChillToast.makeText(this, "Removed from Watch Later.", ZeroChillToast.LENGTH_SHORT).show();
        } else {
            FavoriteStore.add(this, title, pageUrl);
            ZeroChillToast.makeText(this, "Saved to Watch Later.", ZeroChillToast.LENGTH_SHORT).show();
        }
        updateWatchLaterButton();
        if (fullscreenControls != null) fullscreenControls.syncSaved(FavoriteStore.contains(this, pageUrl));
    }

    private void sharePage() {
        String shareUrl = pageUrl.isEmpty() ? mediaUrl : pageUrl;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, shareUrl);
        share.putExtra(Intent.EXTRA_SUBJECT, title);
        startActivity(Intent.createChooser(share, "Share video"));
    }

    private void showPlayerMenu() {
        String saveTitle = FavoriteStore.contains(this, pageUrl)
                ? "Remove from Watch Later"
                : "Watch Later";
        ArrayList<VideoActionSheet.Action> actions = new ArrayList<>();
        if (supportsComments()) {
            actions.add(VideoActionSheet.action(
                    R.drawable.ic_action_comments,
                    "Comments",
                    "Read and reply without leaving the video",
                    this::openComments
            ));
        }
        actions.add(VideoActionSheet.action(
                R.drawable.ic_action_share,
                "Share",
                "Send the video page",
                this::sharePage
        ));
        actions.add(VideoActionSheet.action(
                R.drawable.ic_more_website,
                "Open source website",
                "Open the original video page",
                () -> openWebsite(pageUrl)
        ));
        if (!showsOrigin &&
                getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
            if (portraitVideo) {
                actions.add(VideoActionSheet.action(
                        portraitFullscreen
                                ? R.drawable.ic_action_fullscreen_exit
                                : R.drawable.ic_action_fullscreen,
                        portraitFullscreen ? "Exit portrait fullscreen" : "Portrait fullscreen",
                        portraitFullscreen
                                ? "Return to the video details"
                                : "Fill the screen without rotating",
                        () -> setPortraitFullscreen(!rotatableFullscreen)
                ));
            }
            actions.add(VideoActionSheet.action(
                    R.drawable.ic_action_minimize,
                    "Minimize",
                    "Keep playing while you browse",
                    this::minimizeFromMenu
            ));
            if (!portraitVideo) {
                actions.add(VideoActionSheet.action(
                        R.drawable.ic_action_fullscreen,
                        "Fullscreen",
                        "Allow rotation while the video stays fullscreen",
                        () -> setRotatableFullscreen(true)
                ));
            }
        }

        VideoActionSheet.showCompact(
                this,
                title,
                VideoActionSheet.section(
                        "PLAYBACK",
                        VideoActionSheet.action(
                                R.drawable.ic_action_speed,
                                "Playback speed",
                                "Choose from 0.5× to 2×",
                                this::showSpeedMenu
                        ),
                        VideoActionSheet.action(
                                R.drawable.ic_more_view_style,
                                "Fit / Fill / Zoom",
                                "Choose how the video fills the player",
                                this::showResizeMenu
                        )
                ),
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
                                saveTitle,
                                "Keep this video in your library",
                                this::toggleWatchLater
                        )
                ),
                VideoActionSheet.section(
                        "ACTIONS",
                        actions.toArray(new VideoActionSheet.Action[0])
                )
        );
    }

    private void downloadCurrentVideo() {
        VideoDownloadStore.downloadKnown(
                this,
                title,
                pageUrl,
                "",
                mediaUrl,
                userAgent,
                cookies,
                mediaReferer
        );
    }

    private boolean isEfukt() {
        return NativeFeedBrowserActivity.SOURCE_EFUKT.equals(source) || EfuktRepository.isEfuktUrl(pageUrl);
    }

    private boolean isBunkr() {
        return NativeFeedBrowserActivity.SOURCE_BUNKR.equals(source) || BunkrRepository.isBunkrUrl(pageUrl);
    }

    private boolean isKaotic() {
        return "kaotic".equals(source) || WebVideoSourceRepository.isKaoticUrl(pageUrl);
    }

    private boolean isBaddieHub() {
        return NativeFeedBrowserActivity.SOURCE_BADDIEHUB.equals(source) ||
                BaddieHubRepository.isBaddieHubUrl(pageUrl);
    }

    private boolean supportsComments() {
        return pageUrl != null && !pageUrl.trim().isEmpty();
    }

    private void minimizeFromMenu() {
        minimizing = true;
        minimizeToFeed();
    }

    private void showSpeedMenu() {
        String[] labels = {"0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×"};
        float[] speeds = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
        new ZeroChillDialog.Builder(this)
                .setTitle("Playback speed")
                .setItems(labels, (dialog, which) -> {
                    if (player != null) player.setPlaybackParameters(new PlaybackParameters(speeds[which]));
                })
                .show();
    }

    private void showResizeMenu() {
        String[] labels = {"Fit", "Fill", "Zoom"};
        int[] modes = {
                AspectRatioFrameLayout.RESIZE_MODE_FIT,
                AspectRatioFrameLayout.RESIZE_MODE_FILL,
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        };
        new ZeroChillDialog.Builder(this)
                .setTitle("Video size")
                .setItems(labels, (dialog, which) -> {
                    resizeMode = modes[which];
                    playerView.setResizeMode(resizeMode);
                })
                .show();
    }

    private void showPlaybackFailure() {
        if (failureShown || isFinishing()) return;
        failureShown = true;
        dismissShowsLaunchCurtain(true);
        RatingFeedbackPrompt.recordPlaybackError(this);
        new ZeroChillDialog.Builder(this)
                .setTitle("Couldn't play this stream")
                .setMessage("The native player couldn't continue this video. You can open the normal webpage instead.")
                .setNegativeButton("Close", null)
                .setNeutralButton("Retry", (dialog, which) -> recoverPlayback(null))
                .setPositiveButton("Open page", (dialog, which) -> openWebsite(pageUrl))
                .show();
    }

    private void openWebsite(String url) {
        savePlaybackState(false);
        Intent intent = new Intent(this, WebFallbackActivity.class);
        intent.putExtra(WebFallbackActivity.EXTRA_URL,
                url == null || url.isEmpty() ? CrazyShitRepository.HOME : url);
        startActivity(intent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        abortRelatedBackPreview();
        if (showsOrigin && showsTargetOrientation != Configuration.ORIENTATION_UNDEFINED) {
            showsOrientationSettled = orientationMatches(
                    newConfig.orientation,
                    showsTargetOrientation
            );
        }
        applyOrientation(newConfig.orientation);
        maybeDismissShowsLaunchCurtain();
    }

    private void applyOrientation(int orientation) {
        boolean landscape = orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (landscape) portraitFullscreen = false;
        boolean fullscreen = showsOrigin || landscape || portraitFullscreen || rotatableFullscreen;
        if (detailsScroll != null) {
            detailsScroll.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
            detailsScroll.setAlpha(1f);
            detailsScroll.setTranslationY(0f);
        }
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) playerContainer.getLayoutParams();
        if (fullscreen) {
            params.height = 0;
            params.weight = 1f;
            setFullscreenUi(true);
        } else {
            params.height = portraitPlayerHeight();
            params.weight = 0f;
            setFullscreenUi(false);
        }
        playerContainer.setLayoutParams(params);
        playerContainer.setScaleX(1f);
        playerContainer.setScaleY(1f);
        playerContainer.setTranslationY(0f);
        playerContainer.setAlpha(1f);
        updatePortraitFullscreenButton();
        applyPlayerChrome(orientation);
        syncFullscreenChrome(fullscreen, landscape);
        updateSwipeEnabled();
        updatePortraitProgress();
        if (canShowPortraitSeekBar()) showPortraitSeekBar();
        shell.requestApplyInsets();
    }

    private void syncFullscreenChrome(boolean fullscreen, boolean landscape) {
        if (fullscreenControls == null) return;
        boolean changed = fullscreenChromeActive != fullscreen;
        fullscreenChromeActive = fullscreen;
        playerView.setUseController(!fullscreen);
        if (changed) fullscreenControls.suspend(!fullscreen);
        if (!fullscreen) fullscreenOverlay.setVisibility(View.GONE);
        if (fullscreen) {
            ((TextView) fullscreenOverlay.findViewById(R.id.player_title)).setText(title);
            fullscreenControls.syncSaved(FavoriteStore.contains(this, pageUrl));
            View toggle = fullscreenOverlay.findViewById(R.id.shows_fullscreen_toggle);
            toggle.setVisibility(!landscape && !portraitVideo ? View.VISIBLE : View.GONE);
            fullscreenOverlay.post(() -> fullscreenControls.applyInsets(
                    androidx.core.view.ViewCompat.getRootWindowInsets(fullscreenOverlay)));
        }
    }

    private void showVideoControls() {
        if (fullscreenChromeActive && fullscreenControls != null) fullscreenControls.show();
        else if (playerView != null) playerView.showController();
    }

    private void hideVideoControls() {
        if (fullscreenChromeActive && fullscreenControls != null) fullscreenControls.hide();
        else if (playerView != null) playerView.hideController();
    }

    private void applyPlayerChrome(int orientation) {
        if (playerView == null) return;
        boolean landscape = orientation == Configuration.ORIENTATION_LANDSCAPE;

        View rewind = playerView.findViewById(androidx.media3.ui.R.id.exo_rew);
        View forward = playerView.findViewById(androidx.media3.ui.R.id.exo_ffwd);
        if (rewind != null) rewind.setVisibility(landscape ? View.VISIBLE : View.GONE);
        if (forward != null) forward.setVisibility(landscape ? View.VISIBLE : View.GONE);

        if (playerTitleView != null) {
            playerTitleView.setVisibility(landscape ? View.VISIBLE : View.GONE);
        }

        View centerControls = playerView.findViewById(androidx.media3.ui.R.id.exo_center_controls);
        if (centerControls != null &&
                centerControls.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams centerParams =
                    (FrameLayout.LayoutParams) centerControls.getLayoutParams();
            centerParams.gravity = landscape
                    ? Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
                    : Gravity.CENTER;
            centerParams.bottomMargin = landscape ? dp(62) : 0;
            centerControls.setLayoutParams(centerParams);
        }
    }

    private void maybeDismissShowsLaunchCurtain() {
        if (!playbackIdent || showsLaunchCurtain == null ||
                showsLaunchCurtain.getVisibility() != View.VISIBLE ||
                showsCurtainDismissScheduled ||
                !showsFirstFrameRendered) {
            return;
        }
        if (showsOrigin && (!showsOrientationSettled ||
                showsTargetOrientation == Configuration.ORIENTATION_UNDEFINED)) {
            return;
        }

        showsCurtainDismissScheduled = true;
        long elapsed = Math.max(0L, SystemClock.uptimeMillis() - showsIdentStartedAt);
        long minimumRemaining = Math.max(0L, SHOWS_IDENT_MIN_MS - elapsed);
        long delay = Math.max(90L, minimumRemaining);
        showsLaunchCurtain.postDelayed(() -> {
            showsCurtainDismissScheduled = false;
            if (isFinishing() || showsLaunchCurtain == null ||
                    !showsFirstFrameRendered) {
                return;
            }
            if (showsOrigin && !orientationMatches(
                    getResources().getConfiguration().orientation,
                    showsTargetOrientation
            )) {
                return;
            }
            dismissShowsLaunchCurtain(false);
        }, delay);
    }

    private void dismissShowsLaunchCurtain(boolean immediate) {
        if (showsLaunchCurtain == null ||
                showsLaunchCurtain.getVisibility() != View.VISIBLE) {
            return;
        }
        showsCurtainDismissScheduled = false;

        Runnable finish = () -> {
            if (showsLaunchLoader != null) {
                showsLaunchLoader.stop();
                showsLaunchLoader.setVisibility(View.GONE);
            }
            if (showsLaunchCurtain != null) {
                showsLaunchCurtain.animate().cancel();
                showsLaunchCurtain.setAlpha(1f);
                showsLaunchCurtain.setVisibility(View.GONE);
            }
        };

        if (immediate || !ZeroChillMotion.animationsEnabled(this)) {
            finish.run();
            return;
        }

        showsLaunchCurtain.animate().cancel();
        showsLaunchCurtain.animate()
                .alpha(0f)
                .setDuration(170L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(finish)
                .start();
    }

    private void setPortraitFullscreen(boolean enabled) {
        boolean portraitOrientation = getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_LANDSCAPE;
        portraitFullscreen = enabled && portraitVideo && portraitOrientation;
        rotatableFullscreen = false;
        sensorFullscreen = false;
        if (portraitFullscreen) {
            PhoneOrientationPolicy.enterPortraitFullscreen(this);
        } else {
            PhoneOrientationPolicy.exitFullscreenVideo(this);
        }
        applyOrientation(getResources().getConfiguration().orientation);
        if (playerView != null) showVideoControls();
    }

    private void setRotatableFullscreen(boolean enabled) {
        if (enabled && portraitVideo) {
            setPortraitFullscreen(true);
            return;
        }
        rotatableFullscreen = enabled;
        sensorFullscreen = false;
        if (enabled) {
            if (ShowsPlaybackOrientationPolicy.shouldForceLandscapeOnFullscreen(
                    manualLandscapeFullscreen,
                    showsOrigin,
                    portraitVideo
            )) {
                // Match ShitTok: an explicit fullscreen tap on horizontal media
                // immediately requests sensor-landscape orientation.
                PhoneOrientationPolicy.enterSensorFullscreen(this);
            } else {
                PhoneOrientationPolicy.enterFullscreenVideo(this);
            }
        } else {
            portraitFullscreen = false;
            PhoneOrientationPolicy.exitFullscreenVideo(this);
        }
        applyOrientation(getResources().getConfiguration().orientation);
        if (playerView != null) showVideoControls();
    }

    private void onPhysicalOrientation(SensorMediaOrientationListener.Position position) {
        if (socialEntry && activeComments != null && activeComments.isShowing()) return;
        if (!ShowsPlaybackOrientationPolicy.shouldAutoRotateFromSensor(
                manualLandscapeFullscreen,
                showsOrigin,
                portraitVideo
        )) return;
        if (position == SensorMediaOrientationListener.Position.LANDSCAPE) {
            sensorFullscreen = true;
            rotatableFullscreen = true;
            portraitFullscreen = false;
            PhoneOrientationPolicy.enterSensorFullscreen(this);
            applyOrientation(getResources().getConfiguration().orientation);
        } else if (sensorFullscreen) {
            sensorFullscreen = false;
            rotatableFullscreen = false;
            portraitFullscreen = false;
            PhoneOrientationPolicy.exitFullscreenVideo(this);
            applyOrientation(getResources().getConfiguration().orientation);
        }
    }

    private void updatePortraitFullscreenButton() {
        if (portraitFullscreenButton == null) return;
        if (showsOrigin) {
            portraitFullscreenButton.setVisibility(View.GONE);
            return;
        }
        boolean portraitOrientation = getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_LANDSCAPE;
        portraitFullscreenButton.setVisibility(
                portraitOrientation ? View.VISIBLE : View.GONE
        );
        boolean fullscreen = portraitFullscreen || rotatableFullscreen;
        portraitFullscreenButton.setImageResource(
                fullscreen
                        ? R.drawable.ic_action_fullscreen_exit
                        : R.drawable.ic_action_fullscreen
        );
        portraitFullscreenButton.setContentDescription(
                portraitVideo && portraitOrientation
                        ? (fullscreen ? "Exit portrait fullscreen" : "Fill screen")
                        : (fullscreen ? "Exit fullscreen" : "Fullscreen")
        );
    }

    private void updateSwipeEnabled() {
        if (playerContainer == null) return;
        boolean portrait = getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE;
        boolean enabled = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("swipe_down_minimize", true);
        playerContainer.setSwipeEnabled(
                !showsOrigin && portrait && enabled && !minimizing && !rotatableFullscreen
        );
    }

    private void setFullscreenUi(boolean enabled) {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (enabled) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                }
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(enabled
                    ? View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    : View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private void configureBackHandling() {
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = this::handleBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    backCallback
            );
        }
    }

    private void handleBack() {
        if (showsOrigin) {
            savePlaybackState(false);
            finish();
            return;
        }
        if (portraitFullscreen) {
            setPortraitFullscreen(false);
            return;
        }
        if (rotatableFullscreen) {
            setRotatableFullscreen(false);
            return;
        }
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                && PhoneOrientationPolicy.isPhoneSized(this)) {
            PhoneOrientationPolicy.exitFullscreenVideo(this);
            return;
        }
        if (!socialEntry && restorePreviousRelatedVideo(true)) return;
        if (socialEntry) {
            savePlaybackState(false);
            finish();
            return;
        }
        if (getSharedPreferences("app_prefs", MODE_PRIVATE).getBoolean("minimize_on_back", true)) {
            minimizing = true;
            minimizeToFeed();
        } else {
            savePlaybackState(false);
            finish();
        }
    }

    private void minimizeToFeed() {
        if (isFinishing()) return;
        savePlaybackState(false);
        Intent result = new Intent();
        result.putExtra(PlayerActivity.EXTRA_MINIMIZED, true);
        result.putExtra(PlayerActivity.EXTRA_MEDIA_URL, mediaUrl);
        result.putExtra(PlayerActivity.EXTRA_PAGE_URL, pageUrl);
        result.putExtra(PlayerActivity.EXTRA_TITLE, title);
        result.putExtra(PlayerActivity.EXTRA_USER_AGENT, userAgent);
        result.putExtra(PlayerActivity.EXTRA_COOKIES, cookies);
        result.putExtra(EXTRA_MEDIA_REFERER, mediaReferer);
        result.putExtra(EXTRA_REOPEN_DETAIL, true);
        result.putExtra(EXTRA_VIEWS, views);
        result.putExtra(EXTRA_UPLOADER, uploader);
        result.putExtra(EXTRA_COMMENTS, comments);
        result.putExtra(EXTRA_RELATED_FEED_URL, relatedFeedUrl);
        result.putExtra(EXTRA_SOURCE, source);
        result.putExtra(EXTRA_SHOWS_ORIGIN, showsOrigin);
        if (!posterUrl.isEmpty()) result.putExtra(EXTRA_POSTER_URL, posterUrl);
        if (player != null) result.putExtra(PlayerActivity.EXTRA_START_POSITION, player.getCurrentPosition());
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("social_comments_opened", socialCommentsOpened);
        outState.putBoolean("social_comments_visible", activeComments != null && activeComments.isShowing());
        super.onSaveInstanceState(outState);
    }

    private boolean rememberPositionEnabled() {
        return true;
    }

    private String positionKey() {
        String key = pageUrl == null || pageUrl.isEmpty() ? mediaUrl : pageUrl;
        return "position_" + Integer.toHexString((key == null ? "" : key).hashCode());
    }

    private void savePlaybackState(boolean ended) {
        if (player == null) return;
        long position = Math.max(0L, player.getCurrentPosition());
        long duration = player.getDuration();
        if (duration < 0L) duration = 0L;

        PlaybackHistoryStore.record(
                this,
                title,
                pageUrl,
                posterUrl,
                position,
                duration,
                ended,
                showsOrigin
        );
        updateShowsContinueFrame(ended, position, duration);

        if (!rememberPositionEnabled()) return;
        SharedPreferences prefs = getSharedPreferences("player_positions", MODE_PRIVATE);
        boolean nearlyFinished = duration > 0L && position >= Math.max(0L, duration - 5000L);
        if (ended || nearlyFinished) {
            prefs.edit().remove(positionKey()).apply();
        } else if (position > 3000L) {
            prefs.edit().putLong(positionKey(), position).apply();
        }
    }

    private void updateShowsContinueFrame(boolean ended, long positionMs, long durationMs) {
        if (!showsOrigin || pageUrl == null || pageUrl.trim().isEmpty()) return;
        boolean complete = ended ||
                (durationMs > 0L && positionMs >= (long) (durationMs * 0.95f));
        if (complete) {
            ShowsContinueFrameStore.deleteAsync(this, pageUrl);
            lastShowsFramePositionMs = positionMs;
            return;
        }
        if (positionMs < 5_000L) return;
        if (lastShowsFramePositionMs >= 0L &&
                Math.abs(positionMs - lastShowsFramePositionMs) < 2_000L) {
            return;
        }
        Bitmap frame = capturePlayerFrame();
        if (frame == null) return;
        lastShowsFramePositionMs = positionMs;
        ShowsContinueFrameStore.saveAsync(this, pageUrl, frame);
    }

    boolean isShowsOrigin() {
        return showsOrigin;
    }

    private boolean recoverPlayback(PlaybackException error) {
        if (error == null) failureShown = false;
        return playbackRecovery.recover(this, error, recovered -> {
            mediaUrl = recovered.stream.mediaUrl;
            mediaReferer = recovered.stream.requestReferer;
            try { cookies = clean(CookieManager.getInstance().getCookie(mediaUrl)); } catch (Exception ignored) { }
            buildPlayer(recovered.position);
            player.setPlayWhenReady(recovered.playWhenReady && recoveryResumed);
        }, this::showPlaybackFailure);
    }

    private void releasePlayer() {
        if (fullscreenControls != null) fullscreenControls.release();
        playbackRecovery.cancel();
        portraitSeekScrubbing = false;
        if (portraitSeekBar != null) portraitSeekBar.setProgress(0);
        if (playerView != null) playerView.setPlayer(null);
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    private String defaultUserAgent() {
        try {
            return WebSettings.getDefaultUserAgent(this);
        } catch (Exception e) {
            return THUMB_UA;
        }
    }

    private String cookiesFor(String media, String page) {
        try {
            String value = CookieManager.getInstance().getCookie(media);
            if ((value == null || value.isEmpty()) && page != null) {
                value = CookieManager.getInstance().getCookie(page);
            }
            return value == null ? "" : value;
        } catch (Exception e) {
            return "";
        }
    }

    private LinearLayout.LayoutParams marginParams(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, top, 0, bottom);
        return params;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int portraitPlayerHeight() {
        int width = getResources().getDisplayMetrics().widthPixels;
        return Math.max(dp(190), Math.round(width * 9f / 16f));
    }

    private void haptic(View view) {
        if (view == null) return;
        if (!getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("haptics_enabled", true)) return;
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        recoveryResumed = true;
        if (orientationListener != null) orientationListener.enable();
        if (socialNavigationPendingReturn) {
            socialNavigationPendingReturn = false;
            if (player != null) player.setPlayWhenReady(socialReturnPlayWhenReady);
        }
        updateSwipeEnabled();
        if (detailsScroll != null) applyDetailsBackground();
        refreshVideoLikeState(true);
    }

    @Override
    protected void onPause() {
        if (orientationListener != null) orientationListener.disable();
        stopPortraitProgressTicker();
        super.onPause();
    }

    @Override
    protected void onStop() {
        recoveryResumed = false;
        savePlaybackState(false);
        if (player != null && !isChangingConfigurations()) player.pause();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (activeComments != null) activeComments.dismiss();
        if (orientationListener != null) orientationListener.disable();
        abortRelatedBackPreview();
        dismissShowsLaunchCurtain(true);
        if (portraitFullscreen ||
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setFullscreenUi(false);
        }
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            try {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
            } catch (Exception ignored) {
            }
            backCallback = null;
        }
        relatedPlayGeneration++;
        relatedLoadGeneration++;
        savePlaybackState(false);
        releasePlayer();
        clearRelatedImageTargets();
        if (thumbnailResolvers != null) {
            for (RenderedThumbnailResolver resolver : thumbnailResolvers) {
                if (resolver != null) resolver.close();
            }
        }
        for (VideoHistoryEntry entry : relatedHistory) recycleHistoryPreview(entry);
        relatedHistory.clear();
        requestedRelatedThumbnails.clear();
        io.shutdownNow();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }
}
