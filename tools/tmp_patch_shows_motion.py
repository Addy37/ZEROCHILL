from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text()


def write(path, text):
    (ROOT / path).write_text(text)


def replace_once(path, old, new):
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one match, found {count}: {old[:120]!r}")
    write(path, text.replace(old, new, 1))


# ShowsHubView: preserve the existing one-argument SAM while allowing the tapped artwork
# to be forwarded for geometry-based watch transitions.
path = "app/src/main/java/com/webapp/crazyshit/ShowsHubView.java"
replace_once(path,
'''    interface Listener {\n        void onOpen(NativeContentItem item);\n    }\n\n    interface ContinueListener {\n        void onResume(PlaybackHistoryStore.Item item);\n    }\n''',
'''    interface Listener {\n        void onOpen(NativeContentItem item);\n\n        default void onOpen(NativeContentItem item, View sourceView) {\n            onOpen(item);\n        }\n    }\n\n    interface ContinueListener {\n        void onResume(PlaybackHistoryStore.Item item);\n\n        default void onResume(PlaybackHistoryStore.Item item, View sourceView) {\n            onResume(item);\n        }\n    }\n''')
replace_once(path,
'''            holder.card.setOnClickListener(v -> {\n                if (continueListener != null) continueListener.onResume(history);\n            });\n''',
'''            holder.card.setOnClickListener(v -> {\n                if (continueListener != null) continueListener.onResume(history, holder.image);\n            });\n''')
replace_once(path,
'''            holder.card.setOnClickListener(v -> {\n                if (clickListener != null) clickListener.onOpen(item);\n            });\n''',
'''            holder.card.setOnClickListener(v -> {\n                if (clickListener != null) clickListener.onOpen(item, holder.image);\n            });\n''')

# NativeFeedAdapter: same source-artwork handoff for Shows series episode lists.
path = "app/src/main/java/com/webapp/crazyshit/NativeFeedAdapter.java"
replace_once(path,
'''    public interface Listener {\n        void onOpen(NativeContentItem item);\n        void onLongPress(NativeContentItem item, View anchor);\n        void onComments(NativeContentItem item);\n    }\n''',
'''    public interface Listener {\n        void onOpen(NativeContentItem item);\n\n        default void onOpen(NativeContentItem item, View sourceView) {\n            onOpen(item);\n        }\n\n        void onLongPress(NativeContentItem item, View anchor);\n        void onComments(NativeContentItem item);\n    }\n''')
replace_once(path,
'''        holder.card.setOnClickListener(v -> listener.onOpen(item));\n''',
'''        holder.card.setOnClickListener(v -> listener.onOpen(item, holder.image));\n''')

# Main Shows hub launches: direct video and Continue Watching open the watch Activity
# immediately. Non-Shows/library paths keep their existing pre-resolution behavior.
path = "app/src/main/java/com/webapp/crazyshit/MainPagerAdapter.java"
replace_once(path,
'''    private void openShowsVideo(NativeContentItem item) {\n        openShowsVideo(item, true);\n    }\n\n    private void openShowsVideo(\n            NativeContentItem item,\n            boolean manualLandscapeFullscreen\n    ) {\n''',
'''    private void openShowsVideo(NativeContentItem item) {\n        openShowsVideo(item, true, null);\n    }\n\n    private void openShowsVideo(\n            NativeContentItem item,\n            boolean manualLandscapeFullscreen\n    ) {\n        openShowsVideo(item, manualLandscapeFullscreen, null);\n    }\n\n    private void openShowsVideo(\n            NativeContentItem item,\n            boolean manualLandscapeFullscreen,\n            android.view.View sourceView\n    ) {\n''')
replace_once(path,
'''        if (item == null || !item.isVideo() || item.url == null || item.url.trim().isEmpty()) {\n            return;\n        }\n\n        final List<NativeContentItem> watchItems = showsWatchItems(item);\n        io.execute(() -> {\n''',
'''        if (item == null || !item.isVideo() || item.url == null || item.url.trim().isEmpty()) {\n            return;\n        }\n\n        final List<NativeContentItem> watchItems = showsWatchItems(item);\n        if (manualLandscapeFullscreen) {\n            launchDeferredShowsVideo(item, watchItems, -1L, sourceView, false);\n            return;\n        }\n        io.execute(() -> {\n''')
replace_once(path,
'''    private void openShowsResume(PlaybackHistoryStore.Item history) {\n        openShowsResume(history, true);\n    }\n\n    private void openShowsResume(\n            PlaybackHistoryStore.Item history,\n            boolean manualLandscapeFullscreen\n    ) {\n''',
'''    private void openShowsResume(PlaybackHistoryStore.Item history) {\n        openShowsResume(history, true, null);\n    }\n\n    private void openShowsResume(\n            PlaybackHistoryStore.Item history,\n            boolean manualLandscapeFullscreen\n    ) {\n        openShowsResume(history, manualLandscapeFullscreen, null);\n    }\n\n    private void openShowsResume(\n            PlaybackHistoryStore.Item history,\n            boolean manualLandscapeFullscreen,\n            android.view.View sourceView\n    ) {\n''')
replace_once(path,
'''        final List<NativeContentItem> watchItems = showsWatchItems(item);\n        io.execute(() -> {\n            NativeContentItem libraryContent = OnlyFapVideoMetadata.restore(activity, item);\n''',
'''        final List<NativeContentItem> watchItems = showsWatchItems(item);\n        if (manualLandscapeFullscreen) {\n            launchDeferredShowsVideo(\n                    item,\n                    watchItems,\n                    history.complete ? 0L : history.positionMs,\n                    sourceView,\n                    true\n            );\n            return;\n        }\n        io.execute(() -> {\n            NativeContentItem libraryContent = OnlyFapVideoMetadata.restore(activity, item);\n''')
# The previous replacement intentionally targets the resume method's first matching block after
# its signature. If it accidentally hit the video method, the unique restore call/count above
# would fail later in compilation; assert the new resume marker exists exactly once.
text = read(path)
if text.count("history.complete ? 0L : history.positionMs") < 2:
    raise RuntimeError("MainPagerAdapter: deferred resume launch was not installed")

# Add the immediate launch helper just before the resume entry point.
marker = '''    private void openShowsResume(PlaybackHistoryStore.Item history) {\n'''
helper = '''    private void launchDeferredShowsVideo(\n            NativeContentItem item,\n            List<NativeContentItem> watchItems,\n            long startPosition,\n            android.view.View sourceView,\n            boolean continueResume\n    ) {\n        android.content.Intent intent =\n                new android.content.Intent(activity, VideoDetailActivity.class);\n        intent.putExtra(PlayerActivity.EXTRA_PAGE_URL, item.url);\n        intent.putExtra(PlayerActivity.EXTRA_TITLE, item.title);\n        intent.putExtra(VideoDetailActivity.EXTRA_VIEWS, item.views);\n        intent.putExtra(VideoDetailActivity.EXTRA_UPLOADER, item.uploader);\n        intent.putExtra(VideoDetailActivity.EXTRA_COMMENTS, item.comments);\n        intent.putExtra(VideoDetailActivity.EXTRA_SOURCE, ShowsWatchLaunch.sourceForPage(item.url));\n        if (startPosition >= 0L) {\n            intent.putExtra(PlayerActivity.EXTRA_START_POSITION, startPosition);\n        }\n        if (continueResume) {\n            intent.putExtra(VideoDetailActivity.EXTRA_SHOWS_CONTINUE_RESUME, true);\n        }\n        if (item.imageUrl != null && !item.imageUrl.trim().isEmpty()) {\n            intent.putExtra(VideoDetailActivity.EXTRA_POSTER_URL, item.imageUrl);\n        }\n        ShowsWatchSession.attach(intent, watchItems, false, "Shows");\n        ShowsPlaybackOrientationPolicy.requireManualLandscapeFullscreen(intent);\n        ShowsWatchLaunch.markDeferred(intent, sourceView);\n        activity.startActivity(intent);\n        activity.overridePendingTransition(0, 0);\n    }\n\n'''
text = read(path)
if text.count(marker) != 1:
    raise RuntimeError("MainPagerAdapter: resume insertion marker mismatch")
write(path, text.replace(marker, helper + marker, 1))

# Replace method references with source-aware listener objects only on the Shows hub.
replace_once(path,
'''            page.showsHub = new ShowsHubView(\n                    activity,\n                    this::openShowDetails,\n                    this::openShowsResume,\n                    item -> ShowsCollectionWarmCache.request(activity, item),\n                    this::openShowsVideo\n            );\n''',
'''            page.showsHub = new ShowsHubView(\n                    activity,\n                    this::openShowDetails,\n                    new ShowsHubView.ContinueListener() {\n                        @Override public void onResume(PlaybackHistoryStore.Item item) {\n                            openShowsResume(item);\n                        }\n\n                        @Override public void onResume(\n                                PlaybackHistoryStore.Item item,\n                                android.view.View sourceView\n                        ) {\n                            openShowsResume(item, true, sourceView);\n                        }\n                    },\n                    item -> ShowsCollectionWarmCache.request(activity, item),\n                    new ShowsHubView.Listener() {\n                        @Override public void onOpen(NativeContentItem item) {\n                            openShowsVideo(item);\n                        }\n\n                        @Override public void onOpen(\n                                NativeContentItem item,\n                                android.view.View sourceView\n                        ) {\n                            openShowsVideo(item, true, sourceView);\n                        }\n                    }\n            );\n''')

# Series/collection episode browser: Shows-origin episodes now launch immediately and defer
# stream resolution inside the watch Activity. Other feed types retain the original flow.
path = "app/src/main/java/com/webapp/crazyshit/NativeFeedBrowserActivity.java"
replace_once(path,
'''                @Override\n                public void onOpen(NativeContentItem item) {\n                    if (item == null || item.isSection()) return;\n                    if (memeMode || item.isMeme()) openMeme(item);\n                    else openVideo(item);\n                }\n\n                @Override\n                public void onLongPress(NativeContentItem item, View anchor) {\n''',
'''                @Override\n                public void onOpen(NativeContentItem item) {\n                    if (item == null || item.isSection()) return;\n                    if (memeMode || item.isMeme()) openMeme(item);\n                    else openVideo(item);\n                }\n\n                @Override\n                public void onOpen(NativeContentItem item, View sourceView) {\n                    if (item == null || item.isSection()) return;\n                    if (memeMode || item.isMeme()) openMeme(item);\n                    else openVideo(item, -1L, sourceView);\n                }\n\n                @Override\n                public void onLongPress(NativeContentItem item, View anchor) {\n''')
replace_once(path,
'''    private void openVideo(NativeContentItem item) {\n        openVideo(item, -1L);\n    }\n\n    private void openVideo(NativeContentItem item, long startPosition) {\n        final List<NativeContentItem> watchItems = showDetailsMode && adapter != null\n                ? adapter.snapshot() : java.util.Collections.emptyList();\n        progress.setVisibility(View.VISIBLE);\n''',
'''    private void openVideo(NativeContentItem item) {\n        openVideo(item, -1L, null);\n    }\n\n    private void openVideo(NativeContentItem item, long startPosition) {\n        openVideo(item, startPosition, null);\n    }\n\n    private void openVideo(\n            NativeContentItem item,\n            long startPosition,\n            View sourceView\n    ) {\n        final List<NativeContentItem> watchItems = showDetailsMode && adapter != null\n                ? adapter.snapshot() : java.util.Collections.emptyList();\n        if (showDetailsMode) {\n            Intent intent = new Intent(this, VideoDetailActivity.class);\n            intent.putExtra(PlayerActivity.EXTRA_PAGE_URL, item.url);\n            intent.putExtra(PlayerActivity.EXTRA_TITLE, item.title);\n            intent.putExtra(VideoDetailActivity.EXTRA_VIEWS, item.views);\n            intent.putExtra(VideoDetailActivity.EXTRA_UPLOADER, item.uploader);\n            intent.putExtra(VideoDetailActivity.EXTRA_COMMENTS, item.comments);\n            intent.putExtra(VideoDetailActivity.EXTRA_RELATED_FEED_URL, baseUrl);\n            intent.putExtra(VideoDetailActivity.EXTRA_SOURCE, source);\n            if (startPosition >= 0L) {\n                intent.putExtra(PlayerActivity.EXTRA_START_POSITION, startPosition);\n            }\n            ShowsWatchSession.attach(intent, watchItems,\n                    NativeContentItem.KIND_SERIES.equals(showKind), title);\n            ShowsPlaybackOrientationPolicy.requireManualLandscapeFullscreen(intent);\n            if (item.imageUrl != null && !item.imageUrl.trim().isEmpty()) {\n                intent.putExtra(VideoDetailActivity.EXTRA_POSTER_URL, item.imageUrl);\n            }\n            ShowsWatchLaunch.markDeferred(intent, sourceView);\n            progress.setVisibility(View.GONE);\n            startActivity(intent);\n            overridePendingTransition(0, 0);\n            return;\n        }\n        progress.setVisibility(View.VISIBLE);\n''')

# VideoDetailActivity: allow a Shows-origin watch shell to exist before media resolution,
# retain the poster during that work, and morph the tapped artwork into/out of the player.
path = "app/src/main/java/com/webapp/crazyshit/VideoDetailActivity.java"
replace_once(path,
'''    public static final String EXTRA_SHOWS_CONTINUE_RESUME = "shows_continue_resume";\n''',
'''    public static final String EXTRA_SHOWS_CONTINUE_RESUME = "shows_continue_resume";\n    public static final String EXTRA_DEFERRED_SHOWS_RESOLVE = "deferred_shows_resolve";\n''')
replace_once(path,
'''    private boolean showsOrigin;\n    private boolean manualLandscapeFullscreen;\n''',
'''    private boolean showsOrigin;\n    private boolean deferredShowsResolve;\n    private int deferredShowsResolveGeneration;\n    private boolean manualLandscapeFullscreen;\n''')
replace_once(path,
'''        showsOrigin = getIntent().getBooleanExtra(EXTRA_SHOWS_ORIGIN, false);\n        showsSession = showsOrigin ? ShowsWatchSession.from(getIntent()) : null;\n''',
'''        showsOrigin = getIntent().getBooleanExtra(EXTRA_SHOWS_ORIGIN, false);\n        deferredShowsResolve = showsOrigin &&\n                getIntent().getBooleanExtra(EXTRA_DEFERRED_SHOWS_RESOLVE, false);\n        showsSession = showsOrigin ? ShowsWatchSession.from(getIntent()) : null;\n''')
replace_once(path,
'''        if (showsOrigin && state != null) {\n            restoreShowsState(state);\n        }\n\n        if (mediaUrl == null || mediaUrl.trim().isEmpty()) {\n            finish();\n            return;\n        }\n''',
'''        if (showsOrigin && state != null) {\n            restoreShowsState(state);\n        }\n        if (mediaUrl != null && !mediaUrl.trim().isEmpty()) {\n            deferredShowsResolve = false;\n        }\n\n        if ((mediaUrl == null || mediaUrl.trim().isEmpty()) && !deferredShowsResolve) {\n            finish();\n            return;\n        }\n''')
replace_once(path,
'''        buildUi();\n        buildPlayer(requestedStartPosition);\n''',
'''        buildUi();\n        if (deferredShowsResolve) showStartupPoster();\n        else buildPlayer(requestedStartPosition);\n''')
replace_once(path,
'''        if (showsOrigin && showsSession.related(pageUrl).isEmpty() &&\n                !"content".equalsIgnoreCase(Uri.parse(mediaUrl).getScheme()) &&\n                !"file".equalsIgnoreCase(Uri.parse(mediaUrl).getScheme())) loadRelated();\n        root.post(this::playEntranceOnce);\n''',
'''        if (!deferredShowsResolve && showsOrigin && showsSession.related(pageUrl).isEmpty() &&\n                !"content".equalsIgnoreCase(Uri.parse(mediaUrl).getScheme()) &&\n                !"file".equalsIgnoreCase(Uri.parse(mediaUrl).getScheme())) loadRelated();\n        root.post(this::playEntranceOnce);\n        if (deferredShowsResolve) resolveDeferredShowsPlayback();\n''')

# Use the source-artwork geometry when available, otherwise preserve the existing entrance.
replace_once(path,
'''    private void playEntranceOnce() {\n        if (entrancePlayed || isFinishing()) return;\n        entrancePlayed = true;\n        if (playerContainer != null) {\n''',
'''    private void playEntranceOnce() {\n        if (entrancePlayed || isFinishing()) return;\n        entrancePlayed = true;\n        if (showsOrigin && ShowsWatchLaunch.animateEntrance(\n                this, getIntent(), playerContainer, detailsScroll)) {\n            return;\n        }\n        if (playerContainer != null) {\n''')

# Back from Shows reverses the player toward the original artwork when portrait geometry is
# still valid. Playback/history saving remains exactly where it was.
replace_once(path,
'''            cancelShowsCountdown();\n            finish();\n            return;\n''',
'''            cancelShowsCountdown();\n            Runnable finishShows = () -> {\n                finish();\n                overridePendingTransition(0, 0);\n            };\n            if (!ShowsWatchLaunch.animateExit(\n                    this, getIntent(), playerContainer, detailsScroll, finishShows)) {\n                finishShows.run();\n            }\n            return;\n''')

# Insert deferred resolution just before the existing buildPlayer method.
marker = '''    private void buildPlayer(long startPosition) {\n'''
resolver = '''    private void resolveDeferredShowsPlayback() {\n        if (!deferredShowsResolve || pageUrl == null || pageUrl.trim().isEmpty()) return;\n        final int requestGeneration = ++deferredShowsResolveGeneration;\n        final NativeContentItem requested = currentContent();\n        if (startupPosterLoading != null) startupPosterLoading.setVisibility(View.VISIBLE);\n        if (startupPoster != null) {\n            startupPoster.setClickable(false);\n            startupPoster.setOnClickListener(null);\n        }\n        io.execute(() -> {\n            CrazyShitRepository.StreamInfo stream = null;\n            try {\n                stream = PlayableSourceRouter.resolve(this, requested);\n            } catch (Exception ignored) {\n            }\n            CrazyShitRepository.StreamInfo resolved = stream;\n            runOnUiThread(() -> {\n                if (requestGeneration != deferredShowsResolveGeneration ||\n                        isFinishing() || isDestroyed()) return;\n                if (resolved == null || resolved.mediaUrl == null || resolved.mediaUrl.isEmpty()) {\n                    if (startupPosterLoading != null) startupPosterLoading.setVisibility(View.GONE);\n                    if (startupPoster != null) {\n                        startupPoster.setClickable(true);\n                        startupPoster.setOnClickListener(v -> resolveDeferredShowsPlayback());\n                    }\n                    ZeroChillToast.makeText(\n                            this,\n                            "Couldn't resolve this video natively right now. Tap the player to retry.",\n                            ZeroChillToast.LENGTH_SHORT\n                    ).show();\n                    return;\n                }\n\n                mediaUrl = resolved.mediaUrl;\n                mediaReferer = clean(resolved.requestReferer);\n                if (mediaReferer.isEmpty()) mediaReferer = pageUrl;\n                String resolvedPage = resolved.pageUrl == null || resolved.pageUrl.isEmpty()\n                        ? pageUrl\n                        : resolved.pageUrl;\n                source = ShowsWatchLaunch.sourceForPage(resolvedPage);\n                if (XMegaDriveRepository.isXMegaDriveUrl(resolvedPage)) {\n                    userAgent = clean(XMegaDriveBrowserFetch.sessionUserAgent());\n                }\n                if (userAgent.isEmpty()) userAgent = defaultUserAgent();\n                cookies = cookiesFor(mediaUrl, resolvedPage);\n                deferredShowsResolve = false;\n                if (startupPoster != null) {\n                    startupPoster.setClickable(false);\n                    startupPoster.setOnClickListener(null);\n                }\n\n                getIntent().putExtra(PlayerActivity.EXTRA_MEDIA_URL, mediaUrl)\n                        .putExtra(EXTRA_MEDIA_REFERER, mediaReferer)\n                        .putExtra(EXTRA_SOURCE, source)\n                        .putExtra(PlayerActivity.EXTRA_USER_AGENT, userAgent)\n                        .putExtra(PlayerActivity.EXTRA_COOKIES, cookies)\n                        .putExtra(PlayerActivity.EXTRA_START_POSITION, requestedStartPosition)\n                        .putExtra(EXTRA_DEFERRED_SHOWS_RESOLVE, false);\n\n                buildPlayer(requestedStartPosition);\n                if (showsSession != null && showsSession.related(pageUrl).isEmpty()) {\n                    String scheme = Uri.parse(mediaUrl).getScheme();\n                    if (!"content".equalsIgnoreCase(scheme) && !"file".equalsIgnoreCase(scheme)) {\n                        loadRelated();\n                    }\n                }\n            });\n        });\n    }\n\n'''
text = read(path)
if text.count(marker) != 1:
    raise RuntimeError("VideoDetailActivity: buildPlayer insertion marker mismatch")
write(path, text.replace(marker, resolver + marker, 1))

# Add the reusable transition/deferred-launch helper.
helper_path = ROOT / "app/src/main/java/com/webapp/crazyshit/ShowsWatchLaunch.java"
helper_path.write_text(r'''package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.View;
import android.view.animation.PathInterpolator;

/** Lightweight Shows launch handoff modeled after a shared-thumbnail watch transition. */
final class ShowsWatchLaunch {
    private static final String EXTRA_SOURCE_LEFT = "shows_launch_left";
    private static final String EXTRA_SOURCE_TOP = "shows_launch_top";
    private static final String EXTRA_SOURCE_WIDTH = "shows_launch_width";
    private static final String EXTRA_SOURCE_HEIGHT = "shows_launch_height";
    private static final long ENTER_MS = 260L;
    private static final long DETAILS_MS = 205L;
    private static final long EXIT_MS = 185L;

    private ShowsWatchLaunch() {
    }

    static void markDeferred(Intent intent, View sourceView) {
        if (intent == null) return;
        intent.putExtra(VideoDetailActivity.EXTRA_DEFERRED_SHOWS_RESOLVE, true);
        if (sourceView == null || sourceView.getWidth() <= 0 || sourceView.getHeight() <= 0) return;
        int[] location = new int[2];
        sourceView.getLocationOnScreen(location);
        intent.putExtra(EXTRA_SOURCE_LEFT, location[0]);
        intent.putExtra(EXTRA_SOURCE_TOP, location[1]);
        intent.putExtra(EXTRA_SOURCE_WIDTH, sourceView.getWidth());
        intent.putExtra(EXTRA_SOURCE_HEIGHT, sourceView.getHeight());
    }

    static boolean animateEntrance(
            Activity activity,
            Intent intent,
            View player,
            View details
    ) {
        if (!canAnimate(activity, intent, player)) return false;
        Transform transform = transform(intent, player);
        if (transform == null) return false;

        player.animate().cancel();
        player.setPivotX(0f);
        player.setPivotY(0f);
        player.setScaleX(transform.scaleX);
        player.setScaleY(transform.scaleY);
        player.setTranslationX(transform.translationX);
        player.setTranslationY(transform.translationY);
        player.setAlpha(1f);
        player.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(ENTER_MS)
                .setInterpolator(curve())
                .start();

        if (details != null) {
            details.animate().cancel();
            details.setAlpha(0f);
            details.setTranslationY(dp(activity, 10));
            details.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(70L)
                    .setDuration(DETAILS_MS)
                    .setInterpolator(curve())
                    .start();
        }
        return true;
    }

    static boolean animateExit(
            Activity activity,
            Intent intent,
            View player,
            View details,
            Runnable finish
    ) {
        if (finish == null || !canAnimate(activity, intent, player)) return false;
        Transform transform = transform(intent, player);
        if (transform == null) return false;

        if (details != null) {
            details.animate().cancel();
            details.animate()
                    .alpha(0f)
                    .translationY(dp(activity, 6))
                    .setDuration(105L)
                    .setInterpolator(curve())
                    .start();
        }
        player.animate().cancel();
        player.setPivotX(0f);
        player.setPivotY(0f);
        player.animate()
                .scaleX(transform.scaleX)
                .scaleY(transform.scaleY)
                .translationX(transform.translationX)
                .translationY(transform.translationY)
                .alpha(0.98f)
                .setDuration(EXIT_MS)
                .setInterpolator(curve())
                .withEndAction(finish)
                .start();
        return true;
    }

    static String sourceForPage(String pageUrl) {
        if (WebVideoSourceRepository.isKaoticUrl(pageUrl)) {
            return NativeFeedBrowserActivity.SOURCE_KAOTIC;
        }
        if (BaddieHubRepository.isBaddieHubUrl(pageUrl)) {
            return NativeFeedBrowserActivity.SOURCE_BADDIEHUB;
        }
        if (XMegaDriveRepository.isXMegaDriveUrl(pageUrl)) {
            return NativeFeedBrowserActivity.SOURCE_XMEGADRIVE;
        }
        if (EfuktRepository.isEfuktUrl(pageUrl)) {
            return NativeFeedBrowserActivity.SOURCE_EFUKT;
        }
        return NativeFeedBrowserActivity.SOURCE_CRAZYSHIT;
    }

    static Transform initialTransform(
            int sourceLeft,
            int sourceTop,
            int sourceWidth,
            int sourceHeight,
            int destinationLeft,
            int destinationTop,
            int destinationWidth,
            int destinationHeight
    ) {
        if (sourceWidth <= 0 || sourceHeight <= 0 ||
                destinationWidth <= 0 || destinationHeight <= 0) return null;
        return new Transform(
                sourceWidth / (float) destinationWidth,
                sourceHeight / (float) destinationHeight,
                sourceLeft - destinationLeft,
                sourceTop - destinationTop
        );
    }

    private static Transform transform(Intent intent, View destination) {
        int width = intent.getIntExtra(EXTRA_SOURCE_WIDTH, 0);
        int height = intent.getIntExtra(EXTRA_SOURCE_HEIGHT, 0);
        if (width <= 0 || height <= 0) return null;
        int[] location = new int[2];
        destination.getLocationOnScreen(location);
        return initialTransform(
                intent.getIntExtra(EXTRA_SOURCE_LEFT, 0),
                intent.getIntExtra(EXTRA_SOURCE_TOP, 0),
                width,
                height,
                location[0],
                location[1],
                destination.getWidth(),
                destination.getHeight()
        );
    }

    private static boolean canAnimate(Activity activity, Intent intent, View player) {
        return activity != null && intent != null && player != null &&
                player.getWidth() > 0 && player.getHeight() > 0 &&
                activity.getResources().getConfiguration().orientation !=
                        Configuration.ORIENTATION_LANDSCAPE &&
                ZeroChillMotion.animationsEnabled(activity) &&
                intent.getIntExtra(EXTRA_SOURCE_WIDTH, 0) > 0 &&
                intent.getIntExtra(EXTRA_SOURCE_HEIGHT, 0) > 0;
    }

    private static PathInterpolator curve() {
        return new PathInterpolator(0.2f, 0f, 0f, 1f);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    static final class Transform {
        final float scaleX;
        final float scaleY;
        final float translationX;
        final float translationY;

        Transform(float scaleX, float scaleY, float translationX, float translationY) {
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.translationX = translationX;
            this.translationY = translationY;
        }
    }
}
''')

# Focused math/routing contract test for the shared-thumbnail handoff.
test_path = ROOT / "app/src/test/java/com/webapp/crazyshit/ShowsWatchLaunchTest.java"
test_path.write_text(r'''package com.webapp.crazyshit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ShowsWatchLaunchTest {
    @Test
    public void transformMapsTappedArtworkIntoPlayerBounds() {
        ShowsWatchLaunch.Transform transform = ShowsWatchLaunch.initialTransform(
                18, 420, 320, 180,
                0, 96, 1080, 608
        );

        assertNotNull(transform);
        assertEquals(320f / 1080f, transform.scaleX, 0.0001f);
        assertEquals(180f / 608f, transform.scaleY, 0.0001f);
        assertEquals(18f, transform.translationX, 0.0001f);
        assertEquals(324f, transform.translationY, 0.0001f);
    }

    @Test
    public void transformRejectsMissingGeometry() {
        assertNull(ShowsWatchLaunch.initialTransform(0, 0, 0, 180, 0, 0, 1080, 608));
        assertNull(ShowsWatchLaunch.initialTransform(0, 0, 320, 180, 0, 0, 0, 608));
    }

    @Test
    public void sourceRoutingMatchesShowsProviders() {
        assertEquals(NativeFeedBrowserActivity.SOURCE_XMEGADRIVE,
                ShowsWatchLaunch.sourceForPage("https://www.xmegadrive.com/videos/example"));
        assertEquals(NativeFeedBrowserActivity.SOURCE_EFUKT,
                ShowsWatchLaunch.sourceForPage("https://www.efukt.com/123/title"));
        assertEquals(NativeFeedBrowserActivity.SOURCE_CRAZYSHIT,
                ShowsWatchLaunch.sourceForPage("https://crazyshit.com/cnt/medias/123"));
    }
}
''')

print("Shows motion patch applied")
