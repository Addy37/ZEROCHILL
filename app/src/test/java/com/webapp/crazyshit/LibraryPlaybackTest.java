package com.webapp.crazyshit;

import android.app.Application;
import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 35)
public class LibraryPlaybackTest {
    private static final String ALBUM = "https://www.erome.com/a/AbCd12";
    private static final String CLIP = ALBUM + "#VideoToken";
    private static final String MEDIA = "https://v1.erome.com/123/AbCd12/VideoToken_720p.mp4";
    private final Context context = RuntimeEnvironment.getApplication();

    @Before public void clear() {
        PlaybackHistoryStore.resetForTests();
        context.getSharedPreferences("watch_later", 0).edit().clear().commit();
        context.getSharedPreferences("playback_history", 0).edit().clear().commit();
    }

    @Test public void routesSavedMediaByOriginalSource() {
        assertEquals(LibraryPlayback.Route.EROME_ALBUM, LibraryPlayback.route(album()));
        assertEquals(LibraryPlayback.Route.EROME_MEDIA, LibraryPlayback.route(clip()));
        assertEquals(LibraryPlayback.Route.EROME_MEDIA, LibraryPlayback.route(
                OnlyFapVideoMetadata.basic("Video", MEDIA, "")));
        assertEquals(LibraryPlayback.Route.SHOWS, LibraryPlayback.route(
                OnlyFapVideoMetadata.basic("Show", "https://crazyshit.com/cnt/medias/1", "")));
        assertEquals(LibraryPlayback.Route.ONLYFAP_VIDEO, LibraryPlayback.route(
                OnlyFapVideoMetadata.basic("OnlyFap", "https://fapello.com/video/week/1/", "")));
        assertEquals(LibraryPlayback.Route.GALLERY, LibraryPlayback.route(new NativeContentItem(
                NativeContentItem.KIND_IMAGE, "Picture", "https://pawchive.example/a.jpg",
                "", "", "", "")));
        assertEquals(LibraryPlayback.Route.STANDALONE, LibraryPlayback.route(
                OnlyFapVideoMetadata.basic("Clip", "https://example.invalid/watch/1", "")));
        assertFalse(LibraryPlayback.viewAlbum(1));
        assertTrue(LibraryPlayback.viewAlbum(2));
    }

    @Test public void shitTokEromeClipIsNotTreatedAsShows() {
        assertEquals(LibraryPlayback.Route.EROME_MEDIA, LibraryPlayback.route(clip()));
        assertFalse(LibraryPlayback.isShowsPage(CLIP));
    }

    @Test public void oldUrlOnlyEntryStillOpensTheMatchingViewer() throws Exception {
        org.json.JSONArray items = new org.json.JSONArray().put(new org.json.JSONObject()
                .put("title", "Saved video").put("url", CLIP).put("savedAt", 1L)
                .put("mediaFavorite", false).put("watchLater", true));
        context.getSharedPreferences("watch_later", 0).edit().putString("items", items.toString()).commit();
        FavoriteStore.Item saved = FavoriteStore.load(context).get(0);
        assertNull(saved.content);
        assertEquals(LibraryPlayback.Route.EROME_MEDIA, LibraryPlayback.route(
                OnlyFapVideoMetadata.basic(saved.title, saved.url, "")));
    }

    @Test public void watchLaterKeepsEromeAlbumContextAndLegacyResaveDoesNotDropIt() {
        FavoriteStore.add(context, clip());
        FavoriteStore.Item saved = FavoriteStore.load(context).get(0);
        assertEquals(MEDIA, saved.content.sourceMediaUrl);
        assertEquals(ALBUM, saved.content.sourcePageUrl);
        FavoriteStore.add(context, "Saved video", CLIP);
        FavoriteStore.Item again = FavoriteStore.load(context).get(0);
        assertEquals(MEDIA, again.content.sourceMediaUrl);
        assertEquals(ALBUM, LibraryPlayback.albumOf(again.content));
        NativeContentItem recovered = LibraryPlayback.recover(context,
                OnlyFapVideoMetadata.basic("Saved video", CLIP, ""));
        assertEquals(MEDIA, recovered.sourceMediaUrl);
    }

    @Test public void historyResumesEromeWithoutEnteringShowsContinue() {
        PlaybackHistoryStore.record(context, clip(), 42_000L, 90_000L, false, true);
        PlaybackHistoryStore.awaitPendingWritesForTests();
        PlaybackHistoryStore.resetForTests();
        PlaybackHistoryStore.Item item = PlaybackHistoryStore.load(context).get(0);
        assertEquals(MEDIA, item.content.sourceMediaUrl);
        assertEquals(42_000L, LibraryPlayback.resumePosition(context, CLIP));
        assertTrue(PlaybackHistoryStore.continueWatchingShows(context).isEmpty());
    }

    private static NativeContentItem album() {
        return new NativeContentItem(NativeContentItem.KIND_SERIES, "Album", ALBUM,
                "", "", "creator", "", "2 videos");
    }

    private static NativeContentItem clip() {
        return new NativeContentItem(NativeContentItem.KIND_MEDIA, "Album", CLIP,
                "https://s1.erome.com/123/AbCd12/VideoToken.jpg", "", "creator", "", "0:12")
                .withSourceMedia(MEDIA, ALBUM);
    }
}
