package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * One routing rule for saved Library media. The original provider decides the player.
 * Shows keeps its related-video player. Everything else stays in its own viewer.
 */
final class LibraryPlayback {
    static final String EXTRA_ENRICH_ALBUM = "library_enrich_erome_album";

    interface Playable {
        CrazyShitRepository.StreamInfo resolve(Context context, NativeContentItem item) throws IOException;
    }

    /** Tests replace this so a click does not wait on a live source. */
    static volatile Playable playable = PlayableSourceRouter::resolve;
    static volatile Executor playbackIo = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "library-open");
        thread.setDaemon(true);
        return thread;
    });

    enum Route {
        SHOWS,
        ONLYFAP_VIDEO,
        GALLERY,
        EROME_ALBUM,
        EROME_MEDIA,
        BUNKR_ALBUM,
        STANDALONE
    }

    private LibraryPlayback() {}

    static Route route(NativeContentItem item) {
        if (item == null || item.url == null || item.url.trim().isEmpty()) return Route.STANDALONE;
        if (isEromeAlbum(item)) return Route.EROME_ALBUM;
        if (isEromeMedia(item)) return Route.EROME_MEDIA;
        if (isBunkrAlbum(item)) return Route.BUNKR_ALBUM;
        if (item.isImage() || item.isMeme() || !item.isVideo()) return Route.GALLERY;
        if (PawchiveRepository.isDirectMedia(item.url)) return Route.GALLERY;
        if (OnlyFapVideoMetadata.applies(item)) return Route.ONLYFAP_VIDEO;
        if (isShowsPage(item.url)) return Route.SHOWS;
        return Route.STANDALONE;
    }

    /** View Album is only meaningful when the saved item is not the whole album. */
    static boolean viewAlbum(int albumSize) {
        return albumSize > 1;
    }

    static String albumAction(int count) {
        return viewAlbum(count) ? "View Album · " + count : "";
    }

    /** Unknown size keeps the action and omits a count. A one-item gallery hides it. */
    static String creatorAction(int count) {
        if (count == 1) return "";
        return count > 1 ? "View Creator · " + count : "View Creator";
    }

    static String bunkrAlbum(NativeContentItem item) {
        if (item == null || item.sourcePageUrl == null) return "";
        return BunkrRepository.isAlbumUrl(item.sourcePageUrl) ? item.sourcePageUrl : "";
    }

    static boolean keepStored(NativeContentItem item) {
        if (item == null) return false;
        return isEromeAlbum(item) || isEromeMedia(item)
                || !item.sourceMediaUrl.isEmpty() || !item.sourcePageUrl.isEmpty();
    }

    static boolean isEromeMedia(NativeContentItem item) {
        if (item == null) return false;
        if (EromeRepository.isDirectVideo(item.url) || EromeRepository.isDirectVideo(item.sourceMediaUrl)) {
            return true;
        }
        if (EromeRepository.isMediaUrl(item.url) || EromeRepository.isMediaUrl(item.sourceMediaUrl)) {
            return true;
        }
        return mediaFragment(item.url) && EromeRepository.isAlbumUrl(item.url);
    }

    static boolean isEromeAlbum(NativeContentItem item) {
        return item != null && EromeRepository.isAlbumUrl(item.url) && !isEromeMedia(item);
    }

    static boolean isBunkrAlbum(NativeContentItem item) {
        return item != null && !item.isVideo() && BunkrRepository.isAlbumUrl(item.url);
    }

    static boolean isShowsPage(String url) {
        if (url == null || url.isEmpty()) return false;
        if (EromeRepository.isEromeHttpUrl(url) || PawchiveRepository.isDirectMedia(url)
                || BunkrRepository.isBunkrUrl(url) || FapelloRepository.isFapelloUrl(url)
                || OnlyHavenRepository.isOnlyHavenUrl(url) || WikiFeetRepository.isWikiFeetUrl(url)) {
            return false;
        }
        return url.contains("crazyshit.com/")
                || WebVideoSourceRepository.isKaoticUrl(url)
                || BaddieHubRepository.isBaddieHubUrl(url)
                || XMegaDriveRepository.isXMegaDriveUrl(url)
                || EfuktRepository.isEfuktUrl(url);
    }

    static NativeContentItem recover(Context context, NativeContentItem hint) {
        if (hint == null || hint.url == null || hint.url.isEmpty()) return hint;
        NativeContentItem saved = null;
        if (context != null) {
            for (FavoriteStore.Item item : FavoriteStore.load(context)) {
                if (hint.url.equals(item.url) && item.content != null) {
                    saved = item.content;
                    break;
                }
            }
            for (PlaybackHistoryStore.Item item : PlaybackHistoryStore.load(context)) {
                if (hint.url.equals(item.pageUrl) && item.content != null) {
                    saved = OnlyFapVideoMetadata.merge(item.content, saved);
                    break;
                }
            }
        }
        return OnlyFapVideoMetadata.merge(hint, saved);
    }

    /** -1 when this URL has no resume point. Completed videos resume at the start. */
    static long resumePosition(Context context, String url) {
        if (context == null || url == null || url.isEmpty()) return -1L;
        for (PlaybackHistoryStore.Item item : PlaybackHistoryStore.load(context)) {
            if (!url.equals(item.pageUrl)) continue;
            if (item.complete) return 0L;
            return item.positionMs > 0L ? item.positionMs : -1L;
        }
        return -1L;
    }

    static String albumOf(NativeContentItem item) {
        if (item == null) return "";
        String fromPage = EromeRepository.canonicalAlbum(item.sourcePageUrl);
        if (!fromPage.isEmpty()) return fromPage;
        return EromeRepository.canonicalAlbum(item.url);
    }

    static void openAlbum(Activity activity, NativeContentItem item) {
        if (activity == null || item == null) return;
        String album = albumOf(item);
        if (album.isEmpty() && EromeRepository.isAlbumUrl(item.url)) album = item.url;
        if (album.isEmpty()) return;
        String title = item.title == null || item.title.trim().isEmpty() ? "Erome album" : item.title;
        NativeContentItem albumItem = isEromeAlbum(item) ? item : new NativeContentItem(
                NativeContentItem.KIND_SERIES, title, album, item.imageUrl, item.views, item.uploader,
                "", "Erome album");
        activity.startActivity(NativeFeedBrowserActivity.createShowDetails(
                activity, albumItem, NativeFeedBrowserActivity.SOURCE_EROME));
    }

    static void openBunkrAlbum(Activity activity, NativeContentItem item) {
        if (activity == null || item == null || item.url == null || item.url.isEmpty()) return;
        activity.startActivity(NativeFeedBrowserActivity.createShowDetails(
                activity, item, NativeFeedBrowserActivity.SOURCE_BUNKR));
    }

    /**
     * Play the saved item immediately. Album siblings, when they exist, are filled in later
     * without replacing this item.
     */
    static void openMedia(Activity activity, NativeContentItem item, long startPositionMs) {
        if (activity == null || item == null || item.url == null || item.url.isEmpty()) return;
        String album = albumOf(item);
        String title = item.title == null || item.title.trim().isEmpty() ? "Erome" : item.title;
        String session = BunkrGallerySessionStore.create(title, album);
        boolean enrich = !album.isEmpty();
        BunkrGallerySessionStore.replace(session, Collections.singletonList(item), 1, !enrich);
        Intent intent = new Intent(activity, BunkrGalleryActivity.class)
                .putExtra(BunkrGalleryActivity.EXTRA_SESSION_ID, session)
                .putExtra(BunkrGalleryActivity.EXTRA_TITLE, title)
                .putExtra(BunkrGalleryActivity.EXTRA_ALBUM_URL, album)
                .putExtra(BunkrGalleryActivity.EXTRA_INITIAL_URL, item.url)
                .putExtra(EXTRA_ENRICH_ALBUM, enrich);
        if (startPositionMs > 0L) intent.putExtra(PlayerActivity.EXTRA_START_POSITION, startPositionMs);
        activity.startActivity(intent);
    }

    private static void openGallery(Activity activity, NativeContentItem item, long start) {
        for (FavoriteStore.Item saved : FavoriteStore.load(activity)) {
            if (saved != null && item.url.equals(saved.url)) {
                SavedGalleryMedia.open(activity, new FavoriteStore.Item(
                        saved.title, saved.url, saved.savedAt, item,
                        saved.mediaFavorite, saved.watchLater, saved.galleryKey), start);
                return;
            }
        }
        SavedGalleryMedia.open(activity, item, start);
    }

    static void open(Activity activity, NativeContentItem item, long start) {
        if (activity == null || item == null || item.url == null || item.url.isEmpty()) return;
        switch (route(item)) {
            case EROME_ALBUM:
                openAlbum(activity, item);
                return;
            case BUNKR_ALBUM:
                openBunkrAlbum(activity, item);
                return;
            case EROME_MEDIA:
                openMedia(activity, item, start);
                return;
            case GALLERY:
                openGallery(activity, item, start);
                return;
            case SHOWS:
                launchPlayer(activity, item, start, true, "");
                return;
            case ONLYFAP_VIDEO:
                launchPlayer(activity, item, start, false, bunkrAlbum(item));
                return;
            case STANDALONE:
            default:
                launchPlayer(activity, item, start, false, bunkrAlbum(item));
        }
    }

    static void openSaved(Activity activity, NativeContentItem raw, long knownPosition, boolean positionKnown) {
        openSaved(activity, null, raw, knownPosition, positionKnown);
    }

    static void openSaved(Activity activity, FavoriteStore.Item saved) {
        if (saved == null) return;
        NativeContentItem raw = saved.content != null ? saved.content
                : OnlyFapVideoMetadata.basic(saved.title, saved.url, "");
        openSaved(activity, saved, raw, -1L, false);
    }

    private static void openSaved(Activity activity, FavoriteStore.Item saved, NativeContentItem raw,
            long knownPosition, boolean positionKnown) {
        if (activity == null || raw == null || raw.url == null || raw.url.trim().isEmpty()) return;
        playbackIo.execute(() -> {
            NativeContentItem item = recover(activity, raw);
            Route destination = route(item);
            long start = positionKnown ? knownPosition : -1L;
            if (!positionKnown && destination != Route.SHOWS) start = resumePosition(activity, item.url);
            long position = start;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (destination == Route.GALLERY && saved != null && saved.content != null) {
                    SavedGalleryMedia.open(activity, new FavoriteStore.Item(
                            saved.title, saved.url, saved.savedAt, item,
                            saved.mediaFavorite, saved.watchLater, saved.galleryKey), position);
                    return;
                }
                open(activity, item, position);
            });
        });
    }

    /**
     * Keep the saved item in place. Album siblings stay in album order and are never
     * replaced with an unrelated recommendation.
     */
    static List<NativeContentItem> albumContext(NativeContentItem current, List<NativeContentItem> album) {
        if (current == null) return Collections.emptyList();
        if (album == null || album.isEmpty()) return Collections.singletonList(current);
        ArrayList<NativeContentItem> ordered = new ArrayList<>();
        boolean placed = false;
        for (NativeContentItem candidate : album) {
            if (candidate == null || candidate.url == null || candidate.url.isEmpty()) continue;
            if (!placed && sameMedia(current, candidate)) {
                ordered.add(current);
                placed = true;
            } else if (!sameMedia(current, candidate)) {
                ordered.add(candidate);
            }
        }
        if (!placed) ordered.add(0, current);
        return ordered;
    }

    static boolean sameMedia(NativeContentItem left, NativeContentItem right) {
        if (left == null || right == null) return false;
        if (left.url != null && left.url.equals(right.url)) return true;
        String leftMedia = mediaKey(left);
        String rightMedia = mediaKey(right);
        if (!leftMedia.isEmpty() && leftMedia.equals(rightMedia)) return true;
        String leftToken = mediaToken(left);
        String rightToken = mediaToken(right);
        return !leftToken.isEmpty() && leftToken.equals(rightToken);
    }

    static int cachedGalleryCount(Context context, Intent gallery) {
        if (gallery == null) return -1;
        String session = gallery.getStringExtra(NativeFeedBrowserActivity.EXTRA_CREATOR_GALLERY_SESSION);
        if (session == null || session.isEmpty()) return -1;
        BunkrGallerySessionStore.Snapshot snapshot = BunkrGallerySessionStore.snapshot(session);
        if ((snapshot == null || snapshot.items.isEmpty()) && context != null) {
            snapshot = BunkrGallerySessionStore.restore(context, session);
        }
        if (snapshot == null || snapshot.items.isEmpty()) return -1;
        return snapshot.items.size();
    }

    private static void launchPlayer(Activity activity, NativeContentItem item, long start,
            boolean shows, String bunkrAlbum) {
        playbackIo.execute(() -> {
            NativeContentItem content = OnlyFapVideoMetadata.restore(activity, item);
            CrazyShitRepository.StreamInfo stream = null;
            Exception resolveError = null;
            try {
                stream = playable.resolve(activity, content);
            } catch (Exception error) {
                resolveError = error;
            }
            CrazyShitRepository.StreamInfo resolved = stream;
            Throwable failure = resolveError;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (resolved == null || resolved.mediaUrl == null || resolved.mediaUrl.isEmpty()) {
                    if (BunkrMaintenanceUi.showIfConfirmed(activity, item.url, failure,
                            () -> launchPlayer(activity, item, start, shows, bunkrAlbum))) return;
                    ZeroChillToast.makeText(activity,
                            "Couldn't resolve this video natively right now.",
                            ZeroChillToast.LENGTH_SHORT).show();
                    return;
                }
                String resolvedPage = resolved.pageUrl == null || resolved.pageUrl.isEmpty()
                        ? item.url : resolved.pageUrl;
                Intent intent = new Intent(activity, VideoDetailActivity.class);
                intent.putExtra(PlayerActivity.EXTRA_MEDIA_URL, resolved.mediaUrl);
                intent.putExtra(PlayerActivity.EXTRA_PAGE_URL, item.url);
                intent.putExtra(PlayerActivity.EXTRA_TITLE, item.title);
                intent.putExtra(VideoDetailActivity.EXTRA_VIEWS, item.views);
                intent.putExtra(VideoDetailActivity.EXTRA_UPLOADER, item.uploader);
                intent.putExtra(VideoDetailActivity.EXTRA_COMMENTS, item.comments);
                intent.putExtra(VideoDetailActivity.EXTRA_SOURCE, playerSource(item, resolvedPage, shows, bunkrAlbum));
                intent.putExtra(VideoDetailActivity.EXTRA_MEDIA_REFERER, resolved.requestReferer);
                OnlyFapVideoMetadata.put(intent, content);
                if (start >= 0L) intent.putExtra(PlayerActivity.EXTRA_START_POSITION, start);
                if (item.imageUrl != null && !item.imageUrl.trim().isEmpty()) {
                    intent.putExtra(VideoDetailActivity.EXTRA_POSTER_URL, item.imageUrl);
                }
                if (shows) {
                    ShowsWatchSession.attach(intent, Collections.singletonList(content), false, "Shows");
                } else if (bunkrAlbum != null && !bunkrAlbum.isEmpty()) {
                    intent.putExtra(VideoDetailActivity.EXTRA_RELATED_FEED_URL, bunkrAlbum);
                    intent.putExtra(VideoDetailActivity.EXTRA_LIBRARY_CONTEXT, true);
                } else if (BunkrRepository.isBunkrUrl(item.url) || !OnlyFapVideoMetadata.applies(content)) {
                    intent.putExtra(VideoDetailActivity.EXTRA_SUPPRESS_RELATED, true);
                }
                try {
                    String agent = android.webkit.WebSettings.getDefaultUserAgent(activity);
                    if (XMegaDriveRepository.isXMegaDriveUrl(resolvedPage)) {
                        String session = XMegaDriveBrowserFetch.sessionUserAgent();
                        if (session != null && !session.isEmpty()) agent = session;
                    }
                    intent.putExtra(PlayerActivity.EXTRA_USER_AGENT, agent);
                } catch (Exception ignored) {
                }
                try {
                    String cookies = android.webkit.CookieManager.getInstance().getCookie(resolved.mediaUrl);
                    if ((cookies == null || cookies.isEmpty())) {
                        cookies = android.webkit.CookieManager.getInstance().getCookie(resolvedPage);
                    }
                    if (cookies != null) intent.putExtra(PlayerActivity.EXTRA_COOKIES, cookies);
                } catch (Exception ignored) {
                }
                activity.startActivity(intent);
            });
        });
    }

    private static String playerSource(NativeContentItem item, String page, boolean shows, String bunkrAlbum) {
        if (!shows && ((bunkrAlbum != null && !bunkrAlbum.isEmpty())
                || BunkrRepository.isBunkrUrl(item.url) || BunkrRepository.isBunkrUrl(page))) {
            return NativeFeedBrowserActivity.SOURCE_BUNKR;
        }
        if (WebVideoSourceRepository.isKaoticUrl(page)) return NativeFeedBrowserActivity.SOURCE_KAOTIC;
        if (BaddieHubRepository.isBaddieHubUrl(page)) return NativeFeedBrowserActivity.SOURCE_BADDIEHUB;
        if (XMegaDriveRepository.isXMegaDriveUrl(page)) return NativeFeedBrowserActivity.SOURCE_XMEGADRIVE;
        if (EfuktRepository.isEfuktUrl(page)) return NativeFeedBrowserActivity.SOURCE_EFUKT;
        return NativeFeedBrowserActivity.SOURCE_CRAZYSHIT;
    }

    private static String mediaKey(NativeContentItem item) {
        if (item.sourceMediaUrl != null && !item.sourceMediaUrl.isEmpty()) return item.sourceMediaUrl;
        return EromeRepository.isMediaUrl(item.url) ? item.url : "";
    }

    private static String mediaToken(NativeContentItem item) {
        String fragment = fragmentOf(item.url);
        if (fragment.startsWith("img-")) fragment = fragment.substring(4);
        if (!fragment.isEmpty()) return fragment;
        String media = item.sourceMediaUrl == null ? "" : item.sourceMediaUrl;
        int slash = media.lastIndexOf('/');
        String name = slash >= 0 ? media.substring(slash + 1) : media;
        int dot = name.indexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        int quality = name.lastIndexOf('_');
        if (quality > 0 && name.substring(quality + 1).matches("\\d+p")) name = name.substring(0, quality);
        return name;
    }

    private static String fragmentOf(String url) {
        if (url == null) return "";
        int hash = url.indexOf('#');
        if (hash < 0 || hash >= url.length() - 1) return "";
        return url.substring(hash + 1).trim();
    }

    private static boolean mediaFragment(String url) {
        if (url == null) return false;
        int hash = url.indexOf('#');
        if (hash < 0 || hash >= url.length() - 1) return false;
        String token = url.substring(hash + 1).trim();
        return token.matches("[A-Za-z0-9_-]{2,80}");
    }
}
