package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import java.util.Collections;

/**
 * One routing rule for saved Library media. The original provider decides the player.
 * Shows keeps its related-video player. Everything else stays in its own viewer.
 */
final class LibraryPlayback {
    static final String EXTRA_ENRICH_ALBUM = "library_enrich_erome_album";

    enum Route {
        SHOWS,
        ONLYFAP_VIDEO,
        GALLERY,
        EROME_ALBUM,
        EROME_MEDIA,
        STANDALONE
    }

    private LibraryPlayback() {}

    static Route route(NativeContentItem item) {
        if (item == null || item.url == null || item.url.trim().isEmpty()) return Route.STANDALONE;
        if (isEromeAlbum(item)) return Route.EROME_ALBUM;
        if (isEromeMedia(item)) return Route.EROME_MEDIA;
        if (item.isImage() || item.isMeme() || !item.isVideo()) return Route.GALLERY;
        if (OnlyFapVideoMetadata.applies(item)) return Route.ONLYFAP_VIDEO;
        if (isShowsPage(item.url)) return Route.SHOWS;
        return Route.STANDALONE;
    }

    /** View Album is only meaningful when the saved item is not the whole album. */
    static boolean viewAlbum(int albumSize) {
        return albumSize > 1;
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

    static void openMedia(Activity activity, NativeContentItem item, long startPositionMs) {
        if (activity == null || item == null || item.url == null || item.url.isEmpty()) return;
        String album = albumOf(item);
        boolean knownMedia = EromeRepository.isDirectVideo(item.sourceMediaUrl)
                || EromeRepository.isMediaUrl(item.sourceMediaUrl)
                || (item.isImage() && item.imageUrl != null && !item.imageUrl.isEmpty());
        String title = item.title == null || item.title.trim().isEmpty() ? "Erome" : item.title;
        String session = BunkrGallerySessionStore.create(title, album);
        boolean enrich = !album.isEmpty();
        if (knownMedia || album.isEmpty()) {
            BunkrGallerySessionStore.replace(session, Collections.singletonList(item), 1, !enrich);
        }
        Intent intent = new Intent(activity, BunkrGalleryActivity.class)
                .putExtra(BunkrGalleryActivity.EXTRA_SESSION_ID, session)
                .putExtra(BunkrGalleryActivity.EXTRA_TITLE, title)
                .putExtra(BunkrGalleryActivity.EXTRA_ALBUM_URL, album)
                .putExtra(BunkrGalleryActivity.EXTRA_INITIAL_URL, item.url)
                .putExtra(EXTRA_ENRICH_ALBUM, enrich && knownMedia);
        if (startPositionMs > 0L) intent.putExtra(PlayerActivity.EXTRA_START_POSITION, startPositionMs);
        activity.startActivity(intent);
    }

    private static boolean mediaFragment(String url) {
        if (url == null) return false;
        int hash = url.indexOf('#');
        if (hash < 0 || hash >= url.length() - 1) return false;
        String token = url.substring(hash + 1).trim();
        return token.matches("[A-Za-z0-9_-]{2,80}");
    }
}
