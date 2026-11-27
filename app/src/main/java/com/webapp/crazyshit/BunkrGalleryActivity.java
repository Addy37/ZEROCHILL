package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.viewpager2.widget.ViewPager2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Google Photos-style full-screen viewer for a mixed Bunkr album. */
@OptIn(markerClass = UnstableApi.class)
public final class BunkrGalleryActivity extends Activity {
    public static final String EXTRA_SESSION_ID = "bunkr_gallery_session";
    public static final String EXTRA_TITLE = "bunkr_gallery_title";
    public static final String EXTRA_ALBUM_URL = "bunkr_gallery_album_url";
    public static final String EXTRA_CREATOR_QUERY = "bunkr_gallery_creator_query";
    public static final String EXTRA_FAPELLO_PROFILE_URL = "bunkr_gallery_fapello_profile_url";
    public static final String EXTRA_MEDIA_FILTER = "bunkr_gallery_media_filter";
    static final String EXTRA_OMITTED_BLACK_IMAGES = "gallery_omitted_black_images";
    private final java.util.Set<String> omittedBlackImages = new java.util.HashSet<>();
    static final String EXTRA_NOTIFICATION_GALLERY_FALLBACK = "notification_gallery_fallback";
    public static final String EXTRA_INITIAL_URL = "bunkr_gallery_initial_url";
    public static final String EXTRA_INITIAL_POSITION = "bunkr_gallery_initial_position";
    public static final String EXTRA_SHARED_ELEMENT_NAME = "bunkr_gallery_shared_element_name";
    public static final String FILTER_ALL = "all";
    public static final String FILTER_PICTURES = "pictures";
    public static final String FILTER_VIDEOS = "videos";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/139.0 Mobile Safari/537.36";
    private static final int PRELOAD_AHEAD = 5;
    private static final int PRELOAD_BEHIND = 1;

    private final ExecutorService pageIo = Executors.newSingleThreadExecutor();
    private final ExecutorService mediaIo = Executors.newFixedThreadPool(2);
    private final ExecutorService warmIo = Executors.newSingleThreadExecutor();
    private final BunkrRepository repository = new BunkrRepository();
    private final BunkrCreatorGalleryRepository creatorGalleryRepository =
            new BunkrCreatorGalleryRepository();

    private String sessionId;
    private GalleryContextMenu mediaContextMenu;
    private String albumTitle;
    private String albumUrl;
    private ArrayList<String> creatorSeedNames;
    private ArrayList<String> creatorSeedUrls;
    private ArrayList<String> creatorSeedImages;
    private ArrayList<String> creatorAliases;
    private String creatorSessionKey;
    private String creatorQuery;
    private String fapelloProfileUrl;
    private String mediaFilter;
    private String initialUrl;
    private int initialPosition;
    private int currentPage;
    private boolean endReached;
    private boolean loadingMore;
    private boolean fapelloFailureShown;
    private int generation;

    private ViewPager2 pager;
    private BunkrGalleryPagerAdapter adapter;
    private OnlyFapViewerChrome viewerChrome;
    private TextView creatorTitleView;
    private TextView itemTitleView;
    private final Object favoriteWriteLock = new Object();
    private int favoriteReadGeneration;
    private ZeroChillLoadingView initialLoading;
    private ProgressBar loadMoreLoading;
    private boolean chromeVisible = true;
    private boolean restoreChromeAfterLandscape;
    private boolean landscapeFullscreen;
    private boolean manualVideoFullscreen;
    private boolean sensorVideoFullscreen;
    private boolean videoSizeKnown;
    private boolean portraitVideo;
    private SensorMediaOrientationListener videoOrientationListener;
    private View viewerBackdrop;
    private boolean returningToGallery;
    private ExoPlayer player;
    private final PlaybackRecovery playbackRecovery = new PlaybackRecovery();
    private boolean recoveryResumed;
    private int activeVideoPosition = -1;
    private volatile int requestedPhotoPosition = -1;
    private String retriedBunkrPhoto = "";
    private final Set<String> resolvingMedia = new HashSet<>();
    private final Set<String> warmedVideos = new HashSet<>();
    private final Map<String, String> videoReferers = new HashMap<>();
    private int pendingVideoPosition = -1;
    private boolean autoplayInitialSelection;
    private long libraryResumeMs = -1L;
    /** URL that {@link #libraryResumeMs} belongs to. Swipe autoplay must not seek a different clip. */
    private String libraryResumeUrl = "";
    private boolean libraryHoldPause;
    private boolean enrichEromeAlbum;
    private boolean contextNavigationOpen;
    private boolean returnToPlayback;
    private long returnPositionMs = -1L;
    private boolean returnPlaying;
    private int creatorMediaCount = -2;
    private boolean preservingPlayback;
    private String preservedUrl = "";
    private String sharedElementName = "";
    private boolean sharedElementPending;
    private boolean sharedElementStarted;
    private boolean sharedElementCompleted;
    private View sharedElementTarget;
    private int pendingTransitionAutoplayPosition = -1;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        PhoneOrientationPolicy.applyBrowsingOrientation(this);
        videoOrientationListener = new SensorMediaOrientationListener(this, this::onVideoOrientation);
        GalleryMediaTransition.requestWindowFeature(this);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        sessionId = value(getIntent().getStringExtra(EXTRA_SESSION_ID));
        albumTitle = value(getIntent().getStringExtra(EXTRA_TITLE));
        albumUrl = value(getIntent().getStringExtra(EXTRA_ALBUM_URL));
        creatorQuery = value(getIntent().getStringExtra(EXTRA_CREATOR_QUERY));
        fapelloProfileUrl = value(getIntent().getStringExtra(EXTRA_FAPELLO_PROFILE_URL));
        creatorSeedNames = getIntent().getStringArrayListExtra(NativeFeedBrowserActivity.EXTRA_CREATOR_SEED_NAMES);
        creatorSeedUrls = getIntent().getStringArrayListExtra(NativeFeedBrowserActivity.EXTRA_CREATOR_SEED_URLS);
        creatorSeedImages = getIntent().getStringArrayListExtra(NativeFeedBrowserActivity.EXTRA_CREATOR_SEED_IMAGES);
        creatorAliases = getIntent().getStringArrayListExtra(NativeFeedBrowserActivity.EXTRA_CREATOR_ALIASES);
        String favoriteKey = value(getIntent().getStringExtra(
                NativeFeedBrowserActivity.EXTRA_CREATOR_GALLERY_CACHE_KEY));
        creatorSessionKey = CreatorGallerySpec.sessionKey(creatorQuery, fapelloProfileUrl,
                favoriteKey.isEmpty() ? creatorQuery : favoriteKey,
                creatorSeedNames, creatorSeedUrls, creatorAliases);
        ArrayList<String> omitted = getIntent().getStringArrayListExtra(EXTRA_OMITTED_BLACK_IMAGES);
        if (omitted != null) omittedBlackImages.addAll(omitted);
        mediaFilter = value(getIntent().getStringExtra(EXTRA_MEDIA_FILTER));
        if (!FILTER_PICTURES.equals(mediaFilter) && !FILTER_VIDEOS.equals(mediaFilter)) {
            mediaFilter = FILTER_ALL;
        }
        initialUrl = value(getIntent().getStringExtra(EXTRA_INITIAL_URL));
        initialPosition = Math.max(0, getIntent().getIntExtra(EXTRA_INITIAL_POSITION, 0));
        sharedElementName = state == null
                ? value(getIntent().getStringExtra(EXTRA_SHARED_ELEMENT_NAME))
                : "";
        sharedElementPending = !sharedElementName.isEmpty()
                && ZeroChillMotion.animationsEnabled(this);
        if (sharedElementPending) {
            GalleryMediaTransition.configureViewer(
                    this,
                    this::onSharedElementEnterFinished
            );
            postponeEnterTransition();
        }
        autoplayInitialSelection = state == null && !initialUrl.isEmpty();
        libraryResumeMs = getIntent().getLongExtra(PlayerActivity.EXTRA_START_POSITION, -1L);
        libraryResumeUrl = libraryResumeMs > 0L ? initialUrl : "";
        enrichEromeAlbum = state == null && getIntent().getBooleanExtra(LibraryPlayback.EXTRA_ENRICH_ALBUM, false);
        if (albumTitle.isEmpty()) albumTitle = "OnlyFap gallery";

        if (state != null) {
            sessionId = state.getString("session", sessionId);
            initialUrl = state.getString("current_url", initialUrl);
            initialPosition = state.getInt("current_position", initialPosition);
        }
        BunkrGallerySessionStore.Snapshot snapshot = BunkrGallerySessionStore.snapshot(sessionId);
        if (snapshot == null && !sessionId.isEmpty()) {
            pageIo.execute(() -> {
                BunkrGallerySessionStore.Snapshot restored = BunkrGallerySessionStore.restore(this, sessionId);
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) initializeGallery(restored); });
            });
        } else initializeGallery(snapshot);
    }

    private void initializeGallery(BunkrGallerySessionStore.Snapshot snapshot) {
        if (isCreatorGallery() && snapshot != null && !creatorSessionKey.equals(snapshot.creatorQuery)) {
            snapshot = null;
        }
        if (snapshot == null) {
            sessionId = isCreatorGallery()
                    ? BunkrGallerySessionStore.createCreator(albumTitle, albumUrl, creatorSessionKey)
                    : BunkrGallerySessionStore.create(albumTitle, albumUrl);
            if (isCreatorGallery()) {
                creatorGalleryRepository.reset(
                        sessionId, creatorQuery, fapelloProfileUrl, albumTitle,
                        creatorSeedNames, creatorSeedUrls, creatorSeedImages, creatorAliases);
            }
        } else {
            albumTitle = snapshot.title;
            albumUrl = snapshot.albumUrl;
            currentPage = snapshot.currentPage;
            endReached = snapshot.endReached;
        }

        buildUi();
        applyViewerOrientation(getResources().getConfiguration().orientation);
        if (snapshot == null || snapshot.items.isEmpty()) {
            initialLoading.setVisibility(View.VISIBLE);
            loadInitialPage();
        } else {
            showSnapshot(snapshot);
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        viewerBackdrop = new View(this);
        viewerBackdrop.setBackgroundColor(Color.BLACK);
        root.addView(viewerBackdrop, new FrameLayout.LayoutParams(-1, -1));

        pager = new ViewPager2(this);
        pager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        pager.setOffscreenPageLimit(1);
        adapter = new BunkrGalleryPagerAdapter(
                this,
                new BunkrGalleryPagerAdapter.Listener() {
                    @Override
                    public void onMediaTap(int position, NativeContentItem item) {
                        BunkrGalleryActivity.this.onMediaTap(position, item);
                    }

                    @Override
                    public void onMediaLongPress(int position, NativeContentItem item) {
                        showMediaContextMenu(position, item);
                    }

                    @Override
                    public void onDownloadTap(int position, NativeContentItem item) {
                        downloadGalleryItem(position, item);
                    }

                    @Override
                    public void onResolvedImageFailed(int position, NativeContentItem item) {
                        BunkrGallerySessionStore.clearResolvedUrl(sessionId, item.url);
                        if (position == pager.getCurrentItem() && EromeRepository.isAlbumUrl(item.url)
                                && !item.url.equals(retriedBunkrPhoto)) {
                            retriedBunkrPhoto = item.url;
                            String thumb = EromeRepository.thumbnailFor(
                                    !item.sourceMediaUrl.isEmpty() ? item.sourceMediaUrl : item.imageUrl);
                            if (!thumb.isEmpty()) {
                                adapter.setResolvedUrl(position, thumb);
                                return;
                            }
                        }
                        if (position == pager.getCurrentItem() && BunkrRepository.isBunkrUrl(item.url)
                                && !item.url.equals(retriedBunkrPhoto)) {
                            retriedBunkrPhoto = item.url;
                            resolvePhoto(position);
                        }
                    }

                    @Override public void onVideoBack() { onBackPressed(); }
                    @Override public void onVideoMenu(View anchor) { showMenu(anchor); }
                    @Override public void onVideoFullscreen() { toggleVideoFullscreen(); }
                    @Override public void onVideoSurfaceTap() {
                        if (adapter.hideVideoControls(pager.getCurrentItem())) setChromeVisible(true);
                        else toggleChrome();
                    }
                    @Override public void onArtworkReady(int position) {
                        if (viewerChrome != null && pager.getCurrentItem() == position) viewerChrome.artworkReady();
                    }

                    @Override
                    public void onSharedElementReady(View target) {
                        BunkrGalleryActivity.this.onSharedElementReady(target);
                    }
                }
        );
        adapter.setInitialSharedElement(initialUrl, sharedElementName);
        pager.setAdapter(adapter);
        root.addView(pager, new FrameLayout.LayoutParams(-1, -1));

        viewerChrome = new OnlyFapViewerChrome(this, new OnlyFapViewerChrome.Actions() {
            @Override public void close() { finishViewer(); }
            @Override public void favorite() { toggleCurrentFavorite(); }
            @Override public void collections() {
                NativeContentItem current = adapter.itemAt(pager.getCurrentItem());
                BunkrGallerySessionStore.Snapshot snapshot = BunkrGallerySessionStore.snapshot(sessionId);
                MediaCollectionPicker.show(BunkrGalleryActivity.this, current,
                        snapshot == null ? value(creatorQuery) : snapshot.creatorQuery,
                        BunkrGalleryActivity.this::refreshCurrentFavorite);
            }
            @Override public void playback() { toggleCurrentPlayback(); }
            @Override public void fullscreen() { toggleVideoFullscreen(); }
            @Override public void more(View anchor) { showMenu(anchor); }
            @Override public void hide() { setChromeVisible(false); }
        });
        if (enrichEromeAlbum || savedCreatorGallery() != null) viewerChrome.reserveContextLine();
        root.addView(viewerChrome, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        creatorTitleView = itemTitleView = viewerChrome.creatorLabel();
        adapter.setUnifiedVideoChrome(true);

        initialLoading = new ZeroChillLoadingView(this, "Loading gallery...", true);
        FrameLayout.LayoutParams loadingParams =
                new FrameLayout.LayoutParams(dp(160), dp(132));
        loadingParams.gravity = Gravity.CENTER;
        root.addView(initialLoading, loadingParams);
        initialLoading.setVisibility(View.GONE);

        loadMoreLoading = new ZeroChillProgressBar(this);
        loadMoreLoading.setVisibility(View.GONE);
        FrameLayout.LayoutParams loadMoreParams =
                new FrameLayout.LayoutParams(dp(40), dp(40));
        loadMoreParams.gravity = Gravity.CENTER;
        root.addView(loadMoreLoading, loadMoreParams);

        if (sharedElementPending) {
            viewerChrome.setVisible(false);
        }

        setContentView(root);
        // Even a transparent ColorDrawable makes a ViewGroup an implicit
        // transition group. Keep ancestors background-free so only siblings leave.
        findViewById(android.R.id.content).setBackground(null);
        if (sharedElementPending) {
            root.postDelayed(this::startSharedElementFallback, 900L);
        }

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position != requestedPhotoPosition) retriedBunkrPhoto = "";
                requestedPhotoPosition = position;
                NativeContentItem selected = adapter == null ? null : adapter.itemAt(position);
                if (preservingPlayback) {
                    if (selected != null && preservedUrl.equals(selected.url)) {
                        if (activeVideoPosition != position) adapter.noteActiveVideo(position);
                        activeVideoPosition = position;
                        updateChrome(position);
                    }
                    return;
                }
                NativeContentItem playing = adapter == null ? null : adapter.itemAt(activeVideoPosition);
                boolean keepPlaying = player != null && selected != null && playing != null
                        && selected.url != null && selected.url.equals(playing.url);
                if (keepPlaying) {
                    if (activeVideoPosition != position) adapter.noteActiveVideo(position);
                    activeVideoPosition = position;
                    updateChrome(position);
                    preloadNeighbors(position);
                    return;
                }
                pendingVideoPosition = -1;
                releasePlayer();
                updateChrome(position);
                setChromeVisible(true);
                resolvePhoto(position);
                preloadNeighbors(position);
                maybeAutoplayInitialSelection(position);
                if (position >= Math.max(0, adapter.getItemCount() - 5)) loadMore();
            }
        });
    }


    private void showSnapshot(BunkrGallerySessionStore.Snapshot snapshot) {
        boolean finishInitialLoader = initialLoading.getVisibility() == View.VISIBLE
                && snapshot != null && !snapshot.items.isEmpty();
        adapter.replace(filterMedia(snapshot.items), snapshot.resolvedUrls);
        videoReferers.clear();
        videoReferers.putAll(snapshot.videoReferers);
        if (finishInitialLoader && adapter.getItemCount() > 0) initialLoading.finish();
        else initialLoading.setVisibility(View.GONE);
        currentPage = snapshot.currentPage;
        endReached = snapshot.endReached;
        if (adapter.getItemCount() == 0) {
            updateChrome(0);
            if (isCreatorGallery() && !endReached) {
                initialLoading.setVisibility(View.VISIBLE);
                loadMore();
            }
            return;
        }
        int start = adapter.indexOfUrl(initialUrl);
        if (start < 0) start = Math.min(initialPosition, Math.max(0, adapter.getItemCount() - 1));
        pager.setCurrentItem(start, false);
        requestedPhotoPosition = start;
        updateChrome(start);
        resolvePhoto(start);
        preloadNeighbors(start);
        int initialStart = start;
        pager.post(() -> maybeAutoplayInitialSelection(initialStart));
        if (start >= Math.max(0, adapter.getItemCount() - 5)) loadMore();
        if (enrichEromeAlbum && adapter.getItemCount() <= 1) requestEromeAlbumContext();
    }

    private void loadInitialPage() {
        // An album-specific viewer relies on the already loaded session. Do not
        // send another provider's album URL through the Bunkr fallback loader.
        if (!isCreatorGallery() && EromeRepository.isAlbumUrl(albumUrl)) {
            int requestGeneration = generation;
            pageIo.execute(() -> {
                try {
                    List<NativeContentItem> result = new EromeRepository().fetchAlbum(this, albumUrl);
                    runOnUiThread(() -> {
                        if (requestGeneration != generation || isFinishing()) return;
                        initialLoading.setVisibility(View.GONE);
                        currentPage = result.isEmpty() ? 0 : 1;
                        endReached = true;
                        BunkrGallerySessionStore.replace(sessionId, result, currentPage, endReached);
                        BunkrGallerySessionStore.Snapshot fresh = BunkrGallerySessionStore.snapshot(sessionId);
                        if (fresh != null && !fresh.items.isEmpty()) showSnapshot(fresh);
                        else ZeroChillToast.makeText(this,
                                "No supported pictures or videos were found.",
                                ZeroChillToast.LENGTH_LONG).show();
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        if (requestGeneration != generation || isFinishing()) return;
                        initialLoading.setVisibility(View.GONE);
                        ZeroChillToast.makeText(this,
                                EromePages.AccessException.find(error) != null
                                        ? "Erome asked for verification. Open the album and try again."
                                        : "Couldn't load this album.",
                                ZeroChillToast.LENGTH_LONG).show();
                    });
                }
            });
            return;
        }
        if (albumUrl.isEmpty() && !isCreatorGallery()) {
            initialLoading.setVisibility(View.GONE);
            ZeroChillToast.makeText(this, "This album could not be opened.", ZeroChillToast.LENGTH_SHORT).show();
            return;
        }
        int requestGeneration = generation;
        pageIo.execute(() -> {
            try {
                BunkrCreatorGalleryRepository.Batch creatorBatch = isCreatorGallery()
                        ? creatorGalleryRepository.fetchNext(
                                this, sessionId, creatorQuery, fapelloProfileUrl, albumTitle,
                                creatorSeedNames, creatorSeedUrls, creatorSeedImages, creatorAliases, null)
                        : null;
                List<NativeContentItem> result = creatorBatch == null
                        ? repository.fetchAlbum(this, albumUrl, 1)
                        : creatorBatch.items;
                boolean completed = creatorBatch == null
                        ? result.isEmpty()
                        : creatorBatch.endReached;
                FapelloSourceException fapelloFailure = creatorBatch == null
                        ? null
                        : creatorBatch.fapelloFailure;
                runOnUiThread(() -> {
                    if (requestGeneration != generation || isFinishing()) return;
                    initialLoading.setVisibility(View.GONE);
                    currentPage = result.isEmpty() ? 0 : 1;
                    endReached = completed;
                    if (!isCreatorGallery()) BunkrGallerySessionStore.replace(
                            sessionId,
                            result,
                            currentPage,
                            endReached
                    );
                    BunkrGallerySessionStore.Snapshot fresh =
                            BunkrGallerySessionStore.snapshot(sessionId);
                    if (fresh != null && !fresh.items.isEmpty()) showSnapshot(fresh);
                    if (fapelloFailure != null) showFapelloFailure(fapelloFailure);
                    else if (fresh == null || fresh.items.isEmpty()) ZeroChillToast.makeText(
                            this,
                            isCreatorGallery()
                                    ? "No matching pictures or videos loaded. Try again."
                                    : "No supported pictures or videos were found.",
                            ZeroChillToast.LENGTH_LONG
                    ).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (requestGeneration != generation || isFinishing()) return;
                    initialLoading.setVisibility(View.GONE);
                    FapelloSourceException failure = fapelloFailure(error);
                    ZeroChillToast.makeText(
                            this,
                            failure != null
                                    ? failure.userMessage()
                                    : isCreatorGallery()
                                    ? "Couldn't build this creator gallery. Try again."
                                    : "Couldn't load this album.",
                            ZeroChillToast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void loadMore() {
        if (!isCreatorGallery() && EromeRepository.isAlbumUrl(albumUrl)) return;
        if (loadingMore || endReached ||
                (albumUrl.isEmpty() && !isCreatorGallery()) ||
                (adapter.getItemCount() == 0 && !isCreatorGallery())) return;
        loadingMore = true;
        int requestPage = Math.max(1, currentPage + 1);
        int requestGeneration = generation;
        pageIo.execute(() -> {
            try {
                BunkrCreatorGalleryRepository.Batch creatorBatch = isCreatorGallery()
                        ? creatorGalleryRepository.fetchNext(
                                this, sessionId, creatorQuery, fapelloProfileUrl, albumTitle,
                                creatorSeedNames, creatorSeedUrls, creatorSeedImages, creatorAliases, null)
                        : null;
                List<NativeContentItem> result = creatorBatch == null
                        ? repository.fetchAlbum(this, albumUrl, requestPage)
                        : creatorBatch.items;
                List<NativeContentItem> visibleResult = filterMedia(result);
                boolean completed = creatorBatch != null && creatorBatch.endReached;
                FapelloSourceException fapelloFailure = creatorBatch == null
                        ? null
                        : creatorBatch.fapelloFailure;
                runOnUiThread(() -> {
                    if (requestGeneration != generation || isFinishing()) return;
                    loadingMore = false;
                    boolean finishingInitialLoad = initialLoading.getVisibility() == View.VISIBLE
                            && adapter.getItemCount() == 0;
                    loadMoreLoading.setVisibility(View.GONE);
                    BunkrGallerySessionStore.Snapshot currentCreator = isCreatorGallery()
                            ? BunkrGallerySessionStore.snapshot(sessionId) : null;
                    int added = adapter.append(currentCreator == null ? visibleResult : filterMedia(currentCreator.items));
                    // A pictures-only viewer can receive a page containing only videos.
                    // Advance the shared album cursor using all files, not the visible filter.
                    int albumAdded = isCreatorGallery() ? 0 : BunkrGallerySessionStore.append(
                            sessionId, result, requestPage, false);
                    if (finishingInitialLoad && added > 0) initialLoading.finish();
                    else if (!finishingInitialLoad) initialLoading.setVisibility(View.GONE);
                    if (currentCreator != null) {
                        currentPage = currentCreator.currentPage;
                        endReached = currentCreator.endReached;
                    } else if (isCreatorGallery()) endReached = completed;
                    else if (result.isEmpty() || albumAdded == 0) endReached = true;
                    else currentPage = requestPage;
                    if (currentCreator == null && isCreatorGallery() && added > 0) currentPage = requestPage;
                    if (!isCreatorGallery()) BunkrGallerySessionStore.updatePaging(
                            sessionId, currentPage, endReached);
                    BunkrGallerySessionStore.persist(this, sessionId);
                    updateChrome(pager.getCurrentItem());
                    preloadNeighbors(pager.getCurrentItem());
                    if (fapelloFailure != null) showFapelloFailure(fapelloFailure);
                    if ((isCreatorGallery() || !FILTER_ALL.equals(mediaFilter)) && added == 0 && !endReached) {
                        if (adapter.getItemCount() == 0) {
                            initialLoading.setVisibility(View.VISIBLE);
                        } else {
                            loadMoreLoading.setVisibility(View.VISIBLE);
                        }
                        pager.post(this::loadMore);
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    loadingMore = false;
                    initialLoading.setVisibility(View.GONE);
                    loadMoreLoading.setVisibility(View.GONE);
                    if (adapter.getItemCount() == 0) {
                        ZeroChillToast.makeText(
                                this,
                                "Couldn't load more matching media.",
                                ZeroChillToast.LENGTH_SHORT
                        ).show();
                    }
                });
            }
        });
    }

    private void showFapelloFailure(FapelloSourceException failure) {
        if (failure == null || fapelloFailureShown) return;
        fapelloFailureShown = true;
        ZeroChillToast.makeText(this, failure.userMessage(), ZeroChillToast.LENGTH_LONG).show();
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

    private void onSharedElementReady(View target) {
        if (!sharedElementPending || sharedElementStarted || target == null) return;
        String targetName = ViewCompat.getTransitionName(target);
        if (!sharedElementName.equals(targetName)) return;
        sharedElementTarget = target;
        target.post(() -> {
            if (!sharedElementPending || sharedElementStarted || isFinishing()) return;
            sharedElementStarted = true;
            startPostponedEnterTransition();
        });
    }

    private void startSharedElementFallback() {
        if (!sharedElementPending || sharedElementStarted || isFinishing()) return;
        sharedElementStarted = true;
        startPostponedEnterTransition();
        // With no matching target Android may have no shared transition to finish.
        // Restore normal viewer behavior even in that fallback.
        if (sharedElementTarget == null) {
            getWindow().getDecorView().postDelayed(() -> {
                if (sharedElementPending) onSharedElementEnterFinished();
            }, GalleryMediaTransition.EXPAND_DURATION_MS + 80L);
        }
    }

    private void onSharedElementEnterFinished() {
        if (!sharedElementPending) return;
        sharedElementPending = false;
        if (adapter != null) adapter.finishOpeningTransition();
        sharedElementCompleted = sharedElementTarget != null;
        if (viewerChrome != null) viewerChrome.setVisible(chromeVisible);
        int autoplayPosition = pendingTransitionAutoplayPosition;
        pendingTransitionAutoplayPosition = -1;
        if (autoplayPosition >= 0) {
            maybeAutoplayInitialSelection(autoplayPosition);
        }
    }

    private void finishViewer() {
        if (isFinishing() || returningToGallery) return;
        if (adapter != null) {
            adapter.rememberCurrentPawchiveFrame();
            adapter.stopReveals();
        }
        NativeContentItem current = adapter == null || pager == null
                ? null
                : adapter.itemAt(pager.getCurrentItem());
        boolean sharedReturn = sharedElementCompleted
                && canReturnWithSharedElement(sharedElementName, initialUrl, current);
        if (!sharedReturn) {
            if (sharedElementPending) {
                sharedElementPending = false;
                startPostponedEnterTransition();
            }
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return;
        }

        returningToGallery = true;
        generation++;
        viewerChrome.hold();
        viewerChrome.animate().cancel();
        pager.setUserInputEnabled(false);
        View target = adapter.prepareSharedReturn(pager, pager.getCurrentItem());
        releasePlayer();
        if (target == null) {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            return;
        }
        GalleryMediaTransition.configureReturnSurfaces(this, viewerBackdrop, viewerChrome,
                initialLoading, loadMoreLoading);
        final boolean[] returnStarted = {false};
        Runnable startReturn = () -> {
            if (returnStarted[0]) return;
            returnStarted[0] = true;
            if (isFinishing() || isDestroyed()) return;
            setResult(RESULT_OK);
            finishAfterTransition();
        };
        // Leave the poster drawn before Android captures its return snapshot.
        android.view.ViewTreeObserver.OnPreDrawListener ready = new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                target.getViewTreeObserver().removeOnPreDrawListener(this);
                startReturn.run();
                return true;
            }
        };
        target.getViewTreeObserver().addOnPreDrawListener(ready);
        // A screen-off or window-stop interruption may prevent the next draw.
        target.postDelayed(() -> {
            if (target.getViewTreeObserver().isAlive())
                target.getViewTreeObserver().removeOnPreDrawListener(ready);
            startReturn.run();
        }, 350L);
        target.invalidate();
    }

    static boolean canReturnWithSharedElement(
            String transitionName,
            String initialUrl,
            NativeContentItem current
    ) {
        return transitionName != null
                && !transitionName.isEmpty()
                && initialUrl != null
                && !initialUrl.isEmpty()
                && current != null
                && initialUrl.equals(current.url);
    }

    @Override
    public void onBackPressed() {
        if (mediaContextMenu != null) { mediaContextMenu.dismiss(); return; }
        finishViewer();
    }

    private void maybeAutoplayInitialSelection(int position) {
        if (returningToGallery || isFinishing()) return;
        if (pager != null && position != pager.getCurrentItem()) return;
        NativeContentItem item = adapter == null ? null : adapter.itemAt(position);
        if (!shouldAutoplayGallerySelection(item)) return;
        // The opening shared-element still waits; later swipes start immediately.
        if (shouldDelayInitialAutoplay(
                sharedElementPending,
                autoplayInitialSelection,
                initialUrl,
                item
        )) {
            pendingTransitionAutoplayPosition = position;
            return;
        }
        if (player != null && activeVideoPosition == position) return;
        if (pendingVideoPosition == position) return;
        autoplayInitialSelection = false;
        pendingTransitionAutoplayPosition = -1;
        playVideo(position, item);
    }

    /** Swiped gallery videos start on their own. Images stay images. */
    static boolean shouldAutoplayGallerySelection(NativeContentItem item) {
        return item != null && item.isVideo();
    }

    static boolean shouldAutoplayInitialSelection(
            boolean requested,
            String initialUrl,
            NativeContentItem item
    ) {
        return requested
                && item != null
                && item.isVideo()
                && initialUrl != null
                && !initialUrl.isEmpty()
                && initialUrl.equals(item.url);
    }

    static boolean shouldDelayInitialAutoplay(
            boolean sharedTransitionPending,
            boolean requested,
            String initialUrl,
            NativeContentItem item
    ) {
        return sharedTransitionPending
                && shouldAutoplayInitialSelection(requested, initialUrl, item);
    }

    private void onMediaTap(int position, NativeContentItem item) {
        if (item == null) return;
        if (adapter.isFailed(position)) {
            adapter.setFailed(position, false);
            if (item.isVideo()) playVideo(position, item);
            else resolvePhoto(position, false, false);
            return;
        }
        if (item.isVideo()) playVideo(position, item);
        else toggleChrome();
    }

    private void resolvePhoto(int position) {
        resolvePhoto(position, false);
    }

    private void preloadNeighbors(int position) {
        if (!isCreatorGallery()) return;
        // Resolve the next five items in swipe order; retain the previous one
        // for a reverse swipe. Video byte warm-ups use a separate worker.
        for (int neighbor = position + 1; neighbor <= position + PRELOAD_AHEAD; neighbor++) {
            NativeContentItem item = adapter.itemAt(neighbor);
            if (item == null) continue;
            if (item.isImage()) resolvePhoto(neighbor, true);
            else if (item.isVideo()) preloadVideo(neighbor, item);
        }
        NativeContentItem previous = adapter.itemAt(position - PRELOAD_BEHIND);
        if (previous != null) {
            if (previous.isImage()) resolvePhoto(position - PRELOAD_BEHIND, true);
            else if (previous.isVideo()) preloadVideo(position - PRELOAD_BEHIND, previous);
        }
    }

    private boolean withinPreloadWindow(int position) {
        int distance = position - requestedPhotoPosition;
        return distance >= -PRELOAD_BEHIND && distance <= PRELOAD_AHEAD;
    }

    private void resolvePhoto(int position, boolean prefetch) {
        resolvePhoto(position, prefetch, true);
    }

    private void resolvePhoto(int position, boolean prefetch, boolean useListingOriginal) {
        NativeContentItem item = adapter.itemAt(position);
        if (item == null || !item.isImage() || (prefetch && PawchiveRepository.isDirectMedia(item.url))) return;
        if (!adapter.resolvedUrl(position).isEmpty()) {
            if (prefetch) adapter.preloadImage(position);
            return;
        }
        if (resolvingMedia.contains(item.url)) {
            if (!prefetch) adapter.setLoading(position, true);
            return;
        }
        // An explicit retry asks the primary source again if the listing original failed.
        String directPhoto = useListingOriginal ? FapelloRepository.photoUrlFromItem(item) : "";
        if (directPhoto.isEmpty() && isOnlyHavenDirectImage(item)) directPhoto = item.url;
        if (directPhoto.isEmpty()) directPhoto = PawchiveRepository.original(item);
        if (directPhoto.isEmpty()) directPhoto = EromeRepository.originalImage(item);
        if (!directPhoto.isEmpty()) {
            adapter.setResolvedUrl(position, directPhoto);
            BunkrGallerySessionStore.setResolvedUrl(sessionId, item.url, directPhoto);
            if (prefetch) adapter.preloadImage(position);
            return;
        }
        resolvingMedia.add(item.url);
        if (!prefetch) adapter.setLoading(position, true);
        int requestGeneration = generation;
        mediaIo.execute(() -> {
            if (!withinPreloadWindow(position)) {
                runOnUiThread(() -> {
                    if (requestGeneration == generation && !isFinishing()) {
                        resolvingMedia.remove(item.url);
                        adapter.setLoading(position, false);
                    }
                });
                return;
            }
            try {
                CrazyShitRepository.StreamInfo resolved = PlayableSourceRouter.resolve(this, item);
                if (!hasPlayableStream(resolved)) throw new IOException("Media source returned no playable URL");
                runOnUiThread(() -> {
                    if (!canApplyMediaResult(requestGeneration)) return;
                    resolvingMedia.remove(item.url);
                    adapter.setResolvedUrl(position, resolved.mediaUrl);
                    BunkrGallerySessionStore.setResolvedUrl(
                            sessionId,
                            item.url,
                            resolved.mediaUrl
                    );
                    if (!PawchiveRepository.isDirectMedia(item.url)
                            && position != pager.getCurrentItem() && withinPreloadWindow(position)) {
                        adapter.preloadImage(position);
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!canApplyMediaResult(requestGeneration)) return;
                    resolvingMedia.remove(item.url);
                    GalleryDiagnostics.resolveFailure(item, prefetch);
                    adapter.setLoading(position, false);
                    if (pager.getCurrentItem() == position)
                        adapter.setFailed(position, true);
                });
            }
        });
    }

    static boolean hasPlayableStream(CrazyShitRepository.StreamInfo stream) {
        return stream != null && stream.mediaUrl != null
                && !stream.mediaUrl.trim().isEmpty();
    }

    private boolean canApplyMediaResult(int requestGeneration) {
        return requestGeneration == generation && !isFinishing() && !isDestroyed()
                && pager != null && adapter != null;
    }

    private void preloadVideo(int position, NativeContentItem item) {
        if (PawchiveRepository.isDirectMedia(item.url) || resolvingMedia.contains(item.url)
                || BunkrMaintenance.isKnown(item.url)) return;
        String cached = cachedVideoUrl(position, item);
        if (!cached.isEmpty() || isOnlyHavenDirectVideo(item)) {
            String url = cached.isEmpty() ? item.url : cached;
            String referer = isOnlyHavenDirectVideo(item) ? value(item.uploader)
                    : videoReferers.getOrDefault(item.url, item.url);
            if (BunkrRepository.shouldSkipMediaWarm(item.url, url)) return;
            if (warmedVideos.add(url)) warmIo.execute(() -> {
                if (withinPreloadWindow(position)) {
                    GalleryVideoCache.warm(
                            this,
                            videoHttpFactory(url, referer),
                            url,
                            Math.abs(position - pager.getCurrentItem())
                    );
                } else {
                    runOnUiThread(() -> warmedVideos.remove(url));
                }
            });
            return;
        }
        resolvingMedia.add(item.url);
        int requestGeneration = generation;
        mediaIo.execute(() -> {
            if (!withinPreloadWindow(position)) {
                runOnUiThread(() -> resolvingMedia.remove(item.url));
                return;
            }
            try {
                CrazyShitRepository.StreamInfo resolved = PlayableSourceRouter.resolve(this, item);
                if (!hasPlayableStream(resolved)) throw new IOException("Media source returned no playable URL");
                runOnUiThread(() -> {
                    if (!canApplyMediaResult(requestGeneration)) return;
                    resolvingMedia.remove(item.url);
                    videoReferers.put(item.url, value(resolved.requestReferer));
                    adapter.setResolvedUrl(position, resolved.mediaUrl);
                    BunkrGallerySessionStore.setResolvedVideo(sessionId, item.url,
                            resolved.mediaUrl, resolved.requestReferer);
                    if (withinPreloadWindow(position)
                            && !BunkrRepository.shouldSkipMediaWarm(item.url, resolved.mediaUrl)
                            && warmedVideos.add(resolved.mediaUrl)) {
                        String referer = value(resolved.requestReferer).isEmpty()
                                ? item.url : resolved.requestReferer;
                        warmIo.execute(() -> {
                            if (withinPreloadWindow(position)) {
                                GalleryVideoCache.warm(
                                        this,
                                        videoHttpFactory(resolved.mediaUrl, referer),
                                        resolved.mediaUrl,
                                        Math.abs(position - pager.getCurrentItem())
                                );
                            } else {
                                runOnUiThread(() -> warmedVideos.remove(resolved.mediaUrl));
                            }
                        });
                    }
                    if (pendingVideoPosition == position && pager.getCurrentItem() == position) {
                        pendingVideoPosition = -1;
                        startPlayer(position, item, resolved.mediaUrl, value(resolved.requestReferer));
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!canApplyMediaResult(requestGeneration)) return;
                    resolvingMedia.remove(item.url);
                    if (pendingVideoPosition == position && pager.getCurrentItem() == position) {
                        pendingVideoPosition = -1;
                        showBunkrResolutionFailure(position, item, error);
                    } else if (BunkrMaintenance.isConfirmed(error)) {
                        adapter.refreshMaintenance(position);
                    }
                });
            }
        });
    }

    private void playVideo(int position, NativeContentItem item) {
        if (activeVideoPosition == position && player != null) {
            toggleChrome();
            return;
        }
        if (showVideoMaintenance(position, item, null)) return;
        playbackRecovery.reset();
        releasePlayer();
        String original = PawchiveRepository.original(item);
        if (!original.isEmpty()) {
            startPlayer(position, item, original, PawchiveRepository.ORIGIN);
            return;
        }
        String cached = cachedVideoUrl(position, item);
        if (!cached.isEmpty()) {
            startPlayer(position, item, cached, videoReferers.getOrDefault(item.url, item.url));
            return;
        }
        if (isOnlyHavenDirectVideo(item)) {
            startPlayer(position, item, item.url, value(item.uploader));
            return;
        }
        if (EromeRepository.isDirectVideo(item.sourceMediaUrl)) {
            String referer = EromeRepository.canonicalAlbum(item.url);
            startPlayer(position, item, item.sourceMediaUrl, referer.isEmpty() ? EromeRepository.ORIGIN + "/" : referer);
            return;
        }

        if (resolvingMedia.contains(item.url)) {
            pendingVideoPosition = position;
            adapter.setLoading(position, true);
            return;
        }

        adapter.setLoading(position, true);
        int requestGeneration = generation;
        mediaIo.execute(() -> {
            try {
                CrazyShitRepository.StreamInfo resolved = PlayableSourceRouter.resolve(this, item);
                if (!hasPlayableStream(resolved)) throw new IOException("Media source returned no playable URL");
                runOnUiThread(() -> {
                    if (!canApplyMediaResult(requestGeneration) ||
                            pager.getCurrentItem() != position) return;
                    adapter.setResolvedUrl(position, resolved.mediaUrl);
                    videoReferers.put(item.url, value(resolved.requestReferer));
                    BunkrGallerySessionStore.setResolvedVideo(
                            sessionId,
                            item.url,
                            resolved.mediaUrl,
                            resolved.requestReferer
                    );
                    startPlayer(
                            position,
                            item,
                            resolved.mediaUrl,
                            value(resolved.requestReferer)
                    );
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!canApplyMediaResult(requestGeneration)) return;
                    if (pager.getCurrentItem() == position) {
                        showBunkrResolutionFailure(position, item, error);
                    } else if (BunkrMaintenance.isConfirmed(error)) {
                        adapter.setLoading(position, false);
                    }
                });
            }
        });
    }

    private String cachedVideoUrl(int position, NativeContentItem item) {
        String cached = adapter.resolvedUrl(position);
        if (PawchiveRepository.isDirectMedia(item.url))
            return PawchiveRepository.isDeliveryFor(item.url, cached) ? cached : "";
        if (!cached.isEmpty() && BunkrRepository.isBunkrUrl(item.url)
                && !videoReferers.containsKey(item.url)) {
            adapter.setResolvedUrl(position, "");
            BunkrGallerySessionStore.clearResolvedUrl(sessionId, item.url);
            cached = "";
        }
        if (cached.isEmpty()) {
            videoReferers.remove(item.url);
            if (BunkrRepository.isBunkrUrl(item.url))
                BunkrGallerySessionStore.clearResolvedUrl(sessionId, item.url);
        }
        return cached;
    }

    private boolean showVideoMaintenance(int position, NativeContentItem item, Throwable error) {
        if (item == null) return false;
        if (!BunkrMaintenanceUi.showIfConfirmed(this, item.url, error, () -> {
            if (!canApplyMediaResult(generation) || pager.getCurrentItem() != position
                    || adapter.itemAt(position) != item) return;
            videoReferers.remove(item.url);
            adapter.setResolvedUrl(position, "");
            BunkrGallerySessionStore.clearResolvedUrl(sessionId, item.url);
            playVideo(position, item);
        })) return false;
        adapter.setLoading(position, false);
        return true;
    }

    private void showBunkrResolutionFailure(int position, NativeContentItem item, Throwable error) {
        if (showVideoMaintenance(position, item, error)) return;
        adapter.setFailed(position, true);
        if (item == null || !BunkrRepository.isBunkrUrl(item.url)) {
            ZeroChillToast.makeText(this, "Couldn't play this video.", ZeroChillToast.LENGTH_SHORT).show();
            return;
        }
        BunkrPlaybackDiagnostics.show(
                this,
                BunkrPlaybackDiagnostics.report(
                        "resolve", item.url, "", null, error, Player.STATE_IDLE, 0L
                ),
                item.url
        );
    }

    private void startPlayer(
            int position,
            NativeContentItem item,
            String mediaUrl,
            String requestReferer
    ) {
        if (mediaUrl == null || mediaUrl.isEmpty()) return;
        boolean pawchive = PawchiveRepository.isDirectMedia(item.url);
        if ((pawchive || BunkrRepository.isBunkrUrl(item.url))
                && LegacyVideoFormatSupport.requiresVlc(mediaUrl)) {
            launchLegacyPlayer(position, item, mediaUrl, requestReferer);
            return;
        }
        releasePlayer();

        DefaultHttpDataSource.Factory httpFactory = videoHttpFactory(mediaUrl,
                requestReferer.isEmpty() ? item.url : requestReferer);
        androidx.media3.datasource.DataSource.Factory data = isCreatorGallery() && !pawchive
                ? GalleryVideoCache.factory(this, httpFactory) : httpFactory;
        if (pawchive) {
            data = new androidx.media3.datasource.ResolvingDataSource.Factory(data, spec -> {
                PawchiveMediaRequests.awaitStart();
                return spec;
            });
        }
        if (BunkrRepository.isBunkrUrl(item.url)
                && value(Uri.parse(mediaUrl).getPath()).toLowerCase(java.util.Locale.US).endsWith(".mp4")) {
            data = BunkrMp4DataSource.wrap(data);
        }
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(this)
                .setDataSourceFactory(data);
        if (pawchive) {
            mediaSourceFactory.setLoadErrorHandlingPolicy(new androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy() {
                @Override public long getRetryDelayMsFor(LoadErrorInfo info) {
                    if (info.exception instanceof androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                        int status = ((androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) info.exception).responseCode;
                        if (PawchiveMediaRequests.shouldStopRetrying(status)) return androidx.media3.common.C.TIME_UNSET;
                    }
                    return super.getRetryDelayMsFor(info);
                }
            });
        }
        player = new ExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory).build();
        if (viewerChrome != null) viewerChrome.updatePlayer(player);
        activeVideoPosition = position;
        videoSizeKnown = false;
        if (PhoneOrientationPolicy.isPhoneSized(this)
                && ShowsPlaybackOrientationPolicy.autoRotateEnabled(this)
                && getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE)
            PhoneOrientationPolicy.enterPortraitFullscreen(this);
        adapter.activateVideo(position, player);

        MediaItem.Builder media = new MediaItem.Builder().setUri(mediaUrl);
        String lower = mediaUrl.toLowerCase();
        if (lower.contains(".m3u8")) media.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (lower.contains(".mpd")) media.setMimeType(MimeTypes.APPLICATION_MPD);
        player.setMediaItem(media.build());
        playbackRecovery.bind(player, item.url);
        ExoPlayer startedPlayer = player;
        player.addListener(new Player.Listener() {
            @Override public void onVideoSizeChanged(androidx.media3.common.VideoSize size) {
                if (player != startedPlayer || size.width <= 0 || size.height <= 0) return;
                onVideoDimensions(size);
            }

            @Override
            public void onRenderedFirstFrame() {
                if (player != startedPlayer || returningToGallery || isFinishing()) return;
                adapter.onVideoFirstFrame(position, startedPlayer);
            }

            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                if (player != startedPlayer || returningToGallery || isFinishing()) return;
                adapter.onVideoBuffering(position, startedPlayer,
                        startedPlayer.getPlaybackState() == Player.STATE_BUFFERING && playWhenReady);
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (player != startedPlayer || returningToGallery || isFinishing()) return;
                adapter.onVideoBuffering(position, startedPlayer,
                        playbackState == Player.STATE_BUFFERING && startedPlayer.getPlayWhenReady());
                if (playbackState == Player.STATE_READY) {
                    RatingFeedbackPrompt.recordSuccessfulPlayback(
                            BunkrGalleryActivity.this, mediaUrl);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (player != startedPlayer || !canApplyMediaResult(generation)) return;
                if ((pawchive || BunkrRepository.isBunkrUrl(item.url))
                        && LegacyVideoFormatSupport.retryWithVlc(mediaUrl, error.errorCode)) {
                    launchLegacyPlayer(position, item, mediaUrl, requestReferer);
                    return;
                }
                BunkrGallerySessionStore.clearResolvedUrl(sessionId, item.url);
                videoReferers.remove(item.url);
                adapter.setResolvedUrl(position, "");
                if (BunkrMaintenance.isConfirmed(error) || BunkrMaintenance.isKnown(item.url)) {
                    releasePlayer();
                    showVideoMaintenance(position, item, error);
                    return;
                }
                if (!pawchive && playbackRecovery.recover(BunkrGalleryActivity.this, error, recovered -> {
                    if (!canApplyMediaResult(generation) || pager.getCurrentItem() != position) return;
                    if (recovered == null || !hasPlayableStream(recovered.stream)) {
                        adapter.setFailed(position, true);
                        return;
                    }
                    adapter.setResolvedUrl(position, recovered.stream.mediaUrl);
                    videoReferers.put(item.url, value(recovered.stream.requestReferer));
                    BunkrGallerySessionStore.setResolvedVideo(sessionId, item.url,
                            recovered.stream.mediaUrl, recovered.stream.requestReferer);
                    startPlayer(position, item, recovered.stream.mediaUrl, value(recovered.stream.requestReferer));
                    ExoPlayer resumed = player;
                    if (resumed == null) {
                        adapter.setFailed(position, true);
                        return;
                    }
                    resumed.seekTo(recovered.position);
                    resumed.setPlayWhenReady(recovered.playWhenReady && recoveryResumed);
                }, () -> {
                    if (!canApplyMediaResult(generation) || pager.getCurrentItem() != position) return;
                    if (BunkrMaintenance.isKnown(item.url)) {
                        releasePlayer();
                        showVideoMaintenance(position, item, null);
                        return;
                    }
                    RatingFeedbackPrompt.recordPlaybackError(BunkrGalleryActivity.this);
                    releasePlayer(); adapter.setFailed(position, true);
                    ZeroChillToast.makeText(BunkrGalleryActivity.this, "Couldn't refresh this video. Tap it to retry.", ZeroChillToast.LENGTH_SHORT).show();
                })) return;
                RatingFeedbackPrompt.recordPlaybackError(BunkrGalleryActivity.this);
                releasePlayer();
                adapter.setFailed(position, true);
                ZeroChillToast.makeText(BunkrGalleryActivity.this, "Couldn't continue this video. Tap it to retry.", ZeroChillToast.LENGTH_SHORT).show();
            }
        });
        player.setPlayWhenReady(!libraryHoldPause);
        libraryHoldPause = false;
        player.prepare();
        if (libraryResumeMs > 0L
                && libraryResumeUrl != null
                && !libraryResumeUrl.isEmpty()
                && libraryResumeUrl.equals(item.url)) {
            player.seekTo(libraryResumeMs);
            libraryResumeMs = -1L;
        }
        setChromeVisible(true);
    }

    /** This does not change any resolved media or favorite state. Back returns to the same album. */
    private void launchLegacyPlayer(int position, NativeContentItem item,
            String mediaUrl, String requestReferer) {
        if (!LegacyVideoFormatSupport.validHttpsMedia(mediaUrl)
                || pager == null || pager.getCurrentItem() != position || isFinishing()) {
            if (adapter != null) adapter.setFailed(position, true);
            return;
        }
        releasePlayer();
        adapter.setFailed(position, false);
        startActivity(LegacyVideoPlayerActivity.intent(this, item.url, mediaUrl,
                requestReferer == null || requestReferer.isEmpty() ? item.url : requestReferer));
    }

    private DefaultHttpDataSource.Factory videoHttpFactory(String mediaUrl, String requestReferer) {
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(USER_AGENT);
        Map<String, String> headers = new LinkedHashMap<>();
        String referer = requestReferer;
        if (PawchiveRepository.ORIGIN.equals(referer)) {
            return httpFactory.setUserAgent(PawchiveMediaRequests.USER_AGENT)
                    .setDefaultRequestProperties(java.util.Collections.singletonMap("Referer", referer));
        }
        if (!referer.isEmpty()) {
            headers.put("Referer", referer);
            try {
                Uri parsed = Uri.parse(referer);
                if (parsed.getScheme() != null && parsed.getHost() != null) {
                    headers.put("Origin", parsed.getScheme() + "://" + parsed.getHost());
                }
            } catch (Exception ignored) {
            }
        }
        try {
            String cookies = CookieManager.getInstance().getCookie(mediaUrl);
            if ((cookies == null || cookies.isEmpty()) && !referer.isEmpty()
                    && !BunkrRepository.isBunkrUrl(referer)) {
                cookies = CookieManager.getInstance().getCookie(referer);
            }
            if (cookies != null && !cookies.isEmpty()) headers.put("Cookie", cookies);
        } catch (Exception ignored) {
        }
        if (!headers.isEmpty()) httpFactory.setDefaultRequestProperties(headers);
        return httpFactory;
    }

    private void onVideoDimensions(androidx.media3.common.VideoSize size) {
        boolean portrait = VideoDetailActivity.isPortraitVideoSize(
                size.width, size.height, size.pixelWidthHeightRatio);
        boolean eligibilityChanged = !videoSizeKnown || portraitVideo != portrait;
        portraitVideo = portrait;
        videoSizeKnown = true;
        if (viewerChrome != null) viewerChrome.updateOrientation(!portraitVideo, landscapeFullscreen);
        if (portraitVideo) {
            manualVideoFullscreen = sensorVideoFullscreen = false;
            if (PhoneOrientationPolicy.isPhoneSized(this)
                    && ShowsPlaybackOrientationPolicy.autoRotateEnabled(this))
                PhoneOrientationPolicy.enterPortraitFullscreen(this);
        }
        // Quality switches keep the same orientation choice, including a manual exit.
        if (eligibilityChanged && videoOrientationListener != null) videoOrientationListener.redispatch();
    }

    private void toggleVideoFullscreen() {
        if (player == null || !videoSizeKnown || portraitVideo) return;
        manualVideoFullscreen = !(manualVideoFullscreen || sensorVideoFullscreen);
        sensorVideoFullscreen = false;
        if (manualVideoFullscreen) PhoneOrientationPolicy.enterSensorFullscreen(this);
        else PhoneOrientationPolicy.enterPortraitFullscreen(this);
        adapter.setVideoFullscreen(manualVideoFullscreen);
    }

    private void onVideoOrientation(SensorMediaOrientationListener.Position position) {
        if (player == null || !videoSizeKnown || portraitVideo
                || !ShowsPlaybackOrientationPolicy.autoRotateEnabled(this)) return;
        if (position == SensorMediaOrientationListener.Position.LANDSCAPE) {
            sensorVideoFullscreen = true;
            PhoneOrientationPolicy.enterSensorFullscreen(this);
        } else if (sensorVideoFullscreen || manualVideoFullscreen) {
            sensorVideoFullscreen = manualVideoFullscreen = false;
            PhoneOrientationPolicy.enterPortraitFullscreen(this);
        }
        adapter.setVideoFullscreen(sensorVideoFullscreen || manualVideoFullscreen);
    }

    private void releasePlayer() {
        rememberEromePosition();
        if (player != null) {
            manualVideoFullscreen = sensorVideoFullscreen = videoSizeKnown = false;
            PhoneOrientationPolicy.applyBrowsingOrientation(this);
        }
        playbackRecovery.cancel();
        if (player != null) {
            player.release();
            player = null;
        }
        activeVideoPosition = -1;
        if (adapter != null) adapter.clearActiveVideo();
        if (viewerChrome != null) { viewerChrome.updatePlayer(null); viewerChrome.updateOrientation(false, landscapeFullscreen); }
    }

    private void rememberEromePosition() {
        if (player == null || adapter == null || activeVideoPosition < 0) return;
        NativeContentItem item = adapter.itemAt(activeVideoPosition);
        if (!LibraryPlayback.isEromeMedia(item)) return;
        long position = Math.max(0L, player.getCurrentPosition());
        long duration = Math.max(0L, player.getDuration());
        boolean ended = player.getPlaybackState() == Player.STATE_ENDED;
        PlaybackHistoryStore.record(this, item, position, duration, ended, false);
    }

    private void openEromeAlbum() {
        String album = EromeRepository.canonicalAlbum(albumUrl);
        if (album.isEmpty()) return;
        NativeContentItem item = new NativeContentItem(
                NativeContentItem.KIND_SERIES,
                albumTitle == null || albumTitle.isEmpty() ? "Erome album" : albumTitle,
                album, "", "", "", "", "Erome album");
        openParent(NativeFeedBrowserActivity.createShowDetails(
                this, item, NativeFeedBrowserActivity.SOURCE_EROME));
    }

    private void requestEromeAlbumContext() {
        if (!enrichEromeAlbum || !EromeRepository.isAlbumUrl(albumUrl)) return;
        enrichEromeAlbum = false;
        int requestGeneration = generation;
        String requestedAlbum = albumUrl;
        pageIo.execute(() -> {
            List<NativeContentItem> result;
            try {
                result = new EromeRepository().fetchAlbum(this, requestedAlbum);
            } catch (Exception ignored) {
                return;
            }
            List<NativeContentItem> album = result;
            runOnUiThread(() -> {
                if (requestGeneration != generation || isFinishing() || isDestroyed() || adapter == null || pager == null) {
                    return;
                }
                NativeContentItem current = adapter.itemAt(pager.getCurrentItem());
                if (current == null) return;
                List<NativeContentItem> ordered = LibraryPlayback.albumContext(current, album);
                preservingPlayback = true;
                preservedUrl = current.url == null ? "" : current.url;
                int index = adapter.spliceContext(ordered, current.url);
                if (index >= 0) {
                    ArrayList<NativeContentItem> visible = new ArrayList<>();
                    for (int i = 0; i < adapter.getItemCount(); i++) visible.add(adapter.itemAt(i));
                    BunkrGallerySessionStore.replace(sessionId, visible, 1, true);
                    if (pager.getCurrentItem() != index) pager.setCurrentItem(index, false);
                    endReached = true;
                } else {
                    preservingPlayback = false;
                }
                updateChrome(pager.getCurrentItem());
                pager.post(() -> preservingPlayback = false);
            });
        });
    }

    private void updateChrome(int position) {
        if (viewerChrome == null) return;
        NativeContentItem item = adapter.itemAt(position);
        viewerChrome.updateMedia(item == null ? "" : item.url, item == null ? albumTitle : item.title,
                item == null ? 0 : position + 1, adapter.getItemCount(), item != null && item.isVideo());
        String name = ""; Runnable open = null;
        if (EromeRepository.isAlbumUrl(albumUrl) && LibraryPlayback.viewAlbum(adapter.getItemCount())) {
            name = LibraryPlayback.albumAction(adapter.getItemCount());
            open = this::openEromeAlbum;
        } else if (savedFavoriteImage(item) || (item != null && PawchiveRepository.isDirectMedia(item.url)
                && savedCreatorGallery() != null)) {
            ensureCreatorCount();
            name = LibraryPlayback.creatorAction(creatorMediaCount);
            if (!name.isEmpty() && savedCreatorGallery() != null) open = this::openSavedCreatorGallery;
        }
        viewerChrome.setCreatorAction(name, open);
        if (adapter.hasUsableArtwork(position)) viewerChrome.artworkReady();
        refreshCurrentFavorite();
    }

    private void toggleCurrentPlayback() {
        int position = pager.getCurrentItem();
        NativeContentItem item = adapter.itemAt(position);
        if (item == null || !item.isVideo()) return;
        if (player == null || activeVideoPosition != position) onMediaTap(position, item);
        else if (player.getPlaybackState() == Player.STATE_ENDED) { player.seekTo(0); player.play(); }
        else if (player.getPlayWhenReady()) player.pause(); else player.play();
    }

    private void refreshCurrentFavorite() {
        if (isFinishing() || isDestroyed() || viewerChrome == null || adapter == null || pager == null) return;
        NativeContentItem current = adapter.itemAt(pager.getCurrentItem());
        final int request = ++favoriteReadGeneration;
        if (current == null) return;
        mediaIo.execute(() -> {
            boolean saved = false;
            for (FavoriteStore.Item item : FavoriteStore.mediaFavorites(this))
                if (current.url.equals(item.url)) { saved = true; break; }
            final boolean value = saved;
            runOnUiThread(() -> {
                if (request == favoriteReadGeneration && !isFinishing() && !isDestroyed()
                        && adapter.itemAt(pager.getCurrentItem()) == current) viewerChrome.updateFavorite(value);
            });
        });
    }

    private void toggleCurrentFavorite() {
        if (isFinishing() || isDestroyed() || adapter == null || pager == null) return;
        NativeContentItem current = adapter.itemAt(pager.getCurrentItem());
        if (current == null) return;
        mediaIo.execute(() -> {
            synchronized (favoriteWriteLock) {
                boolean saved = false;
                for (FavoriteStore.Item item : FavoriteStore.mediaFavorites(this))
                    if (current.url.equals(item.url)) { saved = true; break; }
                BunkrGallerySessionStore.Snapshot snapshot = BunkrGallerySessionStore.snapshot(sessionId);
                FavoriteStore.setMediaFavorite(this, current, !saved,
                        snapshot == null ? value(creatorQuery) : snapshot.creatorQuery);
            }
            runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) refreshCurrentFavorite(); });
        });
    }

    private void toggleChrome() {
        setChromeVisible(!chromeVisible);
    }

    private void setChromeVisible(boolean visible) {
        if (returningToGallery) return;
        chromeVisible = visible;
        if (viewerChrome != null) viewerChrome.setVisible(visible && !sharedElementPending);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyViewerOrientation(newConfig.orientation);
        if (adapter != null) adapter.setVideoFullscreen(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE);
    }

    private void applyViewerOrientation(int orientation) {
        boolean landscape = orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (viewerChrome == null) {
            setSystemBars(true);
            return;
        }
        if (landscape && !landscapeFullscreen) {
            landscapeFullscreen = true;
            restoreChromeAfterLandscape = chromeVisible;
            if (chromeVisible) setChromeVisible(false);
        } else if (!landscape && landscapeFullscreen) {
            landscapeFullscreen = false;
            if (restoreChromeAfterLandscape) setChromeVisible(true);
            restoreChromeAfterLandscape = false;
        }
        viewerChrome.updateOrientation(videoSizeKnown && !portraitVideo, landscape);
        setSystemBars(true);
    }

    private void setSystemBars(boolean fullscreen) {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller == null) return;
            int types = WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars();
            if (fullscreen) {
                controller.hide(types);
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            } else {
                controller.show(types);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(fullscreen
                    ? View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    : View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private void showMenu(View anchor) {
        NativeContentItem current = adapter.itemAt(pager.getCurrentItem());
        if (viewerChrome != null) viewerChrome.setMenuOpen(true);
        ZeroChillMenu menu = new ZeroChillMenu(this, anchor);
        menu.setOnDismissListener(() -> { if (viewerChrome != null) viewerChrome.setMenuOpen(false); });
        if (savedFavoriteImage(current)) {
            menu.getMenu().add("Save to collections").setOnMenuItemClickListener(item -> {
                MediaCollectionPicker.show(this, current, value(creatorQuery), this::refreshCurrentFavorite); return true;
            });
            menu.getMenu().add("Share item").setOnMenuItemClickListener(item -> { shareCurrent(); return true; });
            menu.getMenu().add("Open gallery").setEnabled(savedCreatorGallery() != null)
                    .setOnMenuItemClickListener(item -> { openSavedCreatorGallery(); return true; });
            menu.getMenu().add("Remove from favorites").setOnMenuItemClickListener(item -> {
                FavoriteStore.setMediaFavorite(this, current, false);
                ZeroChillToast.makeText(this, "Removed from favorites.", ZeroChillToast.LENGTH_SHORT).show();
                finish();
                return true;
            });
            menu.showAbove();
            return;
        }
        menu.getMenu().add("Save to collections").setEnabled(current != null).setOnMenuItemClickListener(item -> {
            BunkrGallerySessionStore.Snapshot snapshot = BunkrGallerySessionStore.snapshot(sessionId);
            MediaCollectionPicker.show(this, current, snapshot == null ? value(creatorQuery) : snapshot.creatorQuery,
                    this::refreshCurrentFavorite); return true;
        });
        menu.getMenu().add("Share item").setEnabled(current != null).setOnMenuItemClickListener(item -> { shareCurrent(); return true; });
        if (current != null && current.isVideo()) {
            menu.getMenu().add(Menu.NONE, 3, 0, "Download video");
            menu.getMenu().add("Advanced playback options").setEnabled(player != null)
                    .setOnMenuItemClickListener(item -> { setChromeVisible(false); adapter.showVideoControls(pager.getCurrentItem()); return true; });
        }
        menu.getMenu().add(Menu.NONE, 1, 1, "Open item page");
        boolean creatorGallery = isCreatorGallery();
        String parentDestination = PawchiveRepository.isProfile(fapelloProfileUrl) ? fapelloProfileUrl : albumUrl;
        String galleryDestination = creatorGallery
                ? creatorPageDestination(current, parentDestination) : parentDestination;
        menu.getMenu().add(
                Menu.NONE,
                2,
                2,
                creatorGallery ? "Open creator page" : "Open album page"
        ).setEnabled(!galleryDestination.isEmpty());
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 3) {
                downloadCurrentVideo();
                return true;
            }
            if (item.getItemId() == 1) {
                if (current != null) {
                    String destination = PawchiveRepository.pageUrl(current);
                    GalleryDiagnostics.navigation(current, false, destination);
                    openPage(destination);
                }
                return true;
            }
            if (item.getItemId() == 2) {
                GalleryDiagnostics.navigation(current, creatorGallery, galleryDestination);
                openPage(galleryDestination);
                return true;
            }
            return false;
        });
        menu.showAbove();
    }

    private boolean savedFavoriteImage(NativeContentItem item) {
        return item != null && item.isImage()
                && getIntent().getBooleanExtra(SavedGalleryMedia.EXTRA_FAVORITE, false);
    }

    private Intent savedCreatorGallery() {
        Intent gallery = getIntent().getParcelableExtra(SavedGalleryMedia.EXTRA_CREATOR_GALLERY);
        return gallery != null && gallery.getComponent() != null
                && NativeFeedBrowserActivity.class.getName().equals(gallery.getComponent().getClassName())
                ? gallery : null;
    }

    private void ensureCreatorCount() {
        if (creatorMediaCount != -2) return;
        Intent gallery = savedCreatorGallery();
        creatorMediaCount = -1;
        if (gallery == null) return;
        int requestGeneration = generation;
        pageIo.execute(() -> {
            int count = LibraryPlayback.cachedGalleryCount(this, gallery);
            runOnUiThread(() -> {
                if (requestGeneration != generation || isFinishing() || isDestroyed() || pager == null) return;
                creatorMediaCount = count;
                updateChrome(pager.getCurrentItem());
            });
        });
    }

    private void openParent(Intent intent) {
        if (intent == null || contextNavigationOpen) return;
        contextNavigationOpen = true;
        returnToPlayback = true;
        returnPositionMs = player == null ? -1L : Math.max(0L, player.getCurrentPosition());
        returnPlaying = player != null && player.getPlayWhenReady()
                && player.getPlaybackState() != Player.STATE_ENDED;
        startActivity(intent);
    }

    private void openSavedCreatorGallery() {
        Intent gallery = savedCreatorGallery();
        if (gallery != null) openParent(new Intent(gallery));
    }

    private void showMediaContextMenu(int position, NativeContentItem item) {
        mediaIo.execute(() -> {
            boolean favorite = false;
            for (FavoriteStore.Item saved : FavoriteStore.mediaFavorites(this))
                if (saved.url.equals(item.url)) favorite = true;
            final boolean selected = favorite;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || !recoveryResumed || pager.getCurrentItem() != position) return;
                if (mediaContextMenu != null) mediaContextMenu.dismiss();
                if (getSharedPreferences("app_prefs", MODE_PRIVATE).getBoolean("haptics_enabled", true))
                    pager.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                mediaContextMenu = new GalleryContextMenu(this, pager, pager, selected, new Runnable[] {
                    () -> onMediaTap(position, item),
                    () -> mediaIo.execute(() -> {
                        BunkrGallerySessionStore.Snapshot snapshot = BunkrGallerySessionStore.snapshot(sessionId);
                        FavoriteStore.setMediaFavorite(this, item, !selected,
                                snapshot == null ? value(creatorQuery) : snapshot.creatorQuery);
                    }),
                    () -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                            .setType("text/plain").putExtra(Intent.EXTRA_TEXT, PawchiveRepository.pageUrl(item)), "Share")),
                    () -> {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                                getSystemService(CLIPBOARD_SERVICE);
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Media link", PawchiveRepository.pageUrl(item)));
                    },
                    () -> downloadGalleryItem(position, item),
                    () -> {
                        BunkrGallerySessionStore.Snapshot snapshot = BunkrGallerySessionStore.snapshot(sessionId);
                        MediaCollectionPicker.show(this, item, snapshot == null ? value(creatorQuery) : snapshot.creatorQuery,
                                this::refreshCurrentFavorite);
                    }
                }, () -> mediaContextMenu = null);
            });
        });
    }

    private void downloadGalleryItem(int position, NativeContentItem item) {
        if (item == null) return;
        String resolvedUrl = adapter.resolvedUrl(position);
        String referer = item.isVideo()
                ? videoReferers.getOrDefault(item.url, item.url)
                : "";
        GalleryMediaDownloader.download(
                this,
                mediaIo,
                item,
                resolvedUrl,
                referer,
                (mediaUrl, requestReferer) -> {
                    if (!item.isImage()) return;
                    adapter.setResolvedUrl(position, mediaUrl);
                    BunkrGallerySessionStore.setResolvedUrl(
                            sessionId,
                            item.url,
                            mediaUrl
                    );
                }
        );
    }

    private void downloadVideoItem(int position, NativeContentItem item) {
        downloadGalleryItem(position, item);
    }

    private void downloadCurrentVideo() {
        int position = pager.getCurrentItem();
        NativeContentItem item = adapter.itemAt(position);
        if (item == null || !item.isVideo()) {
            ZeroChillToast.makeText(this, "This item is not a video.", ZeroChillToast.LENGTH_SHORT).show();
            return;
        }
        downloadVideoItem(position, item);
    }

    private void shareCurrent() {
        NativeContentItem item = adapter.itemAt(pager.getCurrentItem());
        if (item == null || item.url.isEmpty()) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, PawchiveRepository.pageUrl(item));
        share.putExtra(Intent.EXTRA_SUBJECT, item.title);
        startActivity(Intent.createChooser(share, "Share"));
    }

    /** Mixed creator galleries use a search URL as their parent, not the selected creator's page. */
    static String creatorPageDestination(NativeContentItem item, String parentDestination) {
        if (item == null) return "";
        String profile = item.uploader.trim();
        // OnlyHaven's repository stores the verified creator page in uploader, not the
        // gallery's search URL. Never substitute another provider when that page is missing.
        if (OnlyHavenRepository.isOnlyHavenUrl(profile) || OnlyHavenRepository.isOnlyHavenUrl(item.url)) {
            if (OnlyHavenRepository.isOnlyHavenUrl(profile) && isWebPage(profile)
                    && !profile.equals(item.url) && !profile.equals(item.imageUrl)
                    && !OnlyHavenRepository.isDirectImageUrl(profile)) return profile;
            return "";
        }
        if (FapelloRepository.isFapelloUrl(item.url) && FapelloRepository.isModelUrl(profile)
                && isWebPage(profile)) return profile;
        if (PawchiveRepository.isDirectMedia(item.url) && PawchiveRepository.isProfile(profile)
                && isWebPage(profile)) return profile;
        if (WikiFeetRepository.isXItem(item) && WikiFeetRepository.isXProfileUrl(profile)
                && isWebPage(profile)) return profile;
        // Keep existing album/search navigation for sources without a known item-level profile.
        return parentDestination == null ? "" : parentDestination.trim();
    }

    private static boolean isWebPage(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            return uri.getHost() != null && ("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException ignored) { return false; }
    }

    private void openPage(String url) {
        if (url == null || url.isEmpty()) return;
        Intent intent = new Intent(this, WebFallbackActivity.class);
        intent.putExtra(WebFallbackActivity.EXTRA_URL, url);
        startActivity(intent);
    }

    private boolean isCreatorGallery() {
        return creatorQuery != null && !creatorQuery.isEmpty();
    }

    private ArrayList<NativeContentItem> filterMedia(List<NativeContentItem> items) {
        ArrayList<NativeContentItem> filtered = new ArrayList<>();
        if (items == null) return filtered;
        for (NativeContentItem item : items) {
            if (item == null || WikiFeetRepository.isRetiredItem(item)) continue;
            if (item.isImage() && omittedBlackImages.contains(item.url)) continue;
            if (FILTER_PICTURES.equals(mediaFilter) && !item.isImage()) continue;
            if (FILTER_VIDEOS.equals(mediaFilter) && !item.isVideo()) continue;
            filtered.add(item);
        }
        return filtered;
    }

    @Override
    protected void onResume() {
        super.onResume();
        recoveryResumed = true;
        contextNavigationOpen = false;
        if (viewerChrome != null) { viewerChrome.setVisible(chromeVisible && !sharedElementPending); refreshCurrentFavorite(); }
        if (videoOrientationListener != null) videoOrientationListener.enable();
        if (returnToPlayback) {
            returnToPlayback = false;
            restorePlaybackAfterParent();
        }
    }

    private void restorePlaybackAfterParent() {
        if (pager == null || adapter == null || isFinishing()) return;
        int position = pager.getCurrentItem();
        NativeContentItem item = adapter.itemAt(position);
        if (item == null || !item.isVideo()) return;
        if (!returnPlaying && returnPositionMs <= 0L) return;
        libraryResumeMs = Math.max(0L, returnPositionMs);
        libraryResumeUrl = item.url == null ? "" : item.url;
        libraryHoldPause = !returnPlaying;
        playVideo(position, item);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("session", sessionId);
        if (pager != null && adapter != null) {
            int index = pager.getCurrentItem();
            NativeContentItem item = adapter.itemAt(index);
            state.putInt("current_position", index);
            state.putString("current_url", item == null ? initialUrl : item.url);
        }
        BunkrGallerySessionStore.persist(this, sessionId);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onPause() {
        favoriteReadGeneration++;
        if (viewerChrome != null) viewerChrome.setVisible(false);
        if (mediaContextMenu != null) mediaContextMenu.dismiss();
        if (videoOrientationListener != null) videoOrientationListener.disable();
        recoveryResumed = false;
        BunkrGallerySessionStore.persist(this, sessionId);
        releasePlayer();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (viewerChrome != null) viewerChrome.dispose();
        if (videoOrientationListener != null) videoOrientationListener.disable();
        generation++;
        if (adapter != null) adapter.stopReveals();
        releasePlayer();
        pageIo.shutdownNow();
        mediaIo.shutdownNow();
        warmIo.shutdownNow();
        super.onDestroy();
    }

    private TextView action(String label, int size) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private boolean isOnlyHavenDirectImage(NativeContentItem item) {
        return item != null && item.isImage() &&
                OnlyHavenRepository.isOnlyHavenUrl(item.url) &&
                OnlyHavenRepository.isDirectImageUrl(item.url);
    }

    private boolean isOnlyHavenDirectVideo(NativeContentItem item) {
        if (item == null || !item.isVideo()) return false;
        boolean onlyHaven = OnlyHavenRepository.isOnlyHavenUrl(item.uploader) ||
                value(item.description).toLowerCase(java.util.Locale.US).contains("onlyhaven");
        if (!onlyHaven) return false;
        String lower = value(item.url).toLowerCase(java.util.Locale.US);
        return lower.matches(".*\\.(?:mp4|m3u8|mpd|webm|m4v)(?:\\?.*)?$");
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
